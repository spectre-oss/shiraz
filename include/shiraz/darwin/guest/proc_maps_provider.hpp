// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/cache_map_resolver.hpp"
#include "shiraz/darwin/guest/host_reserved_map.hpp"
#include "shiraz/darwin/guest/host_map_projection.hpp"
#include "shiraz/darwin/guest/internal_map_resolver.hpp"
#include "shiraz/darwin/guest/maps_stream.hpp"
#include "shiraz/darwin/guest/native_image_catalog.hpp"
#include "shiraz/darwin/guest/proc_maps_assemble.hpp"
#include <mach-o/dyld_images.h>

namespace shiraz::guest {
// Borrow the launcher capability; duplicate before inspection and never consume
// its shared seek position. Only an unlinked, read-only regular file is accepted.
inline bool read_proc_maps_catalog(int descriptor, std::vector<uint8_t>& output) {
    if (descriptor < 3)
        return false;
    int copy = ::fcntl(descriptor, F_DUPFD_CLOEXEC, 3);
    if (copy < 0)
        return false;
    struct Close {
        int fd;
        ~Close() {
            ::close(fd);
        }
    } close{copy};
    struct stat before{}, after{};
    int flags = ::fcntl(copy, F_GETFL);
    if (flags < 0 || (flags & O_ACCMODE) != O_RDONLY || ::fstat(copy, &before) ||
        !S_ISREG(before.st_mode) || before.st_nlink || before.st_size < 28 ||
        before.st_size > (8 << 20))
        return false;
    std::vector<uint8_t> candidate(size_t(before.st_size));
    size_t done = 0;
    while (done < candidate.size()) {
        ssize_t n = ::pread(copy, candidate.data() + done, candidate.size() - done, off_t(done));
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            return false;
        done += size_t(n);
    }
    if (::fstat(copy, &after) || before.st_size != after.st_size || before.st_ino != after.st_ino ||
        before.st_dev != after.st_dev || before.st_mtimespec.tv_sec != after.st_mtimespec.tv_sec ||
        before.st_mtimespec.tv_nsec != after.st_mtimespec.tv_nsec)
        return false;
    output = std::move(candidate);
    return true;
}
struct ProcessCacheIdentity {
    uint64_t start = 0, size = 0, slide = 0;
    std::array<uint8_t, 16> uuid{};
    static bool capture(ProcessCacheIdentity& out) {
        task_dyld_info_data_t task{};
        mach_msg_type_number_t words = TASK_DYLD_INFO_COUNT;
        constexpr size_t required = offsetof(dyld_all_image_infos, sharedCacheUUID) + 16;
        dyld_all_image_infos info{};
        if (task_info(mach_task_self(), TASK_DYLD_INFO, reinterpret_cast<task_info_t>(&task),
                      &words) != KERN_SUCCESS ||
            task.all_image_info_size < required ||
            !native_image_detail::read(task.all_image_info_addr, &info, required) ||
            info.version < 13)
            return false;
        using Range = const void* (*)(size_t*);
        auto range = reinterpret_cast<Range>(::dlsym(RTLD_DEFAULT, "_dyld_get_shared_cache_range"));
        if (!range)
            return false;
        size_t size = 0;
        uint64_t start = reinterpret_cast<uintptr_t>(range(&size));
        if (!start || !size || size > UINT64_MAX - start)
            return false;
        ProcessCacheIdentity result;
        result.start = start;
        result.size = size;
        result.slide = info.sharedCacheSlide;
        std::memcpy(result.uuid.data(), info.sharedCacheUUID, 16);
        out = result;
        return true;
    }
    bool current() const {
        ProcessCacheIdentity now;
        return capture(now) && now.start == start && now.size == size && now.slide == slide &&
               now.uuid == uuid;
    }
};

// A malformed/mismatched cache catalog gives no factory. The supplied memory
// snapshot belongs to this guest runtime, not another runtime or a global list.
// Each stream refresh pins current native images while assembling all VM ranges;
// it preserves incomplete/stale failures instead of exposing partial text.
inline MapsStream::Factory proc_maps_factory(const std::vector<uint8_t>& cache_bytes,
                                             MemorySnapshot owned) {
    if (!owned)
        return {};
    ProcessCacheIdentity cache;
    if (!ProcessCacheIdentity::capture(cache))
        return {};
    auto catalog =
        parse_named_cache_catalog(cache_bytes.data(), cache_bytes.size(), cache.uuid.data(),
                                  cache.slide, cache.start, cache.size);
    if (catalog.status != CacheFileCatalogStatus::Ok)
        return {};
    return [catalog = std::move(catalog), cache,
            owned = std::move(owned)](std::string& output) -> int {
        try {
            if (!cache.current())
                return EAGAIN;
            auto images = snapshot_native_images(cache.start, cache.size);
            switch (images.status) {
            case NativeImageCatalogStatus::Complete:
                break;
            case NativeImageCatalogStatus::Changed:
                return EAGAIN;
            case NativeImageCatalogStatus::Limit:
                return EFBIG;
            case NativeImageCatalogStatus::OutOfMemory:
                return ENOMEM;
            default:
                return ENOTSUP;
            }
            auto owners = owned();
            auto host = snapshot_process_regions(CurrentProcessRegionQuery{});
            if (host.status == RegionSnapshotStatus::OutOfMemory)
                return ENOMEM;
            if (host.status == RegionSnapshotStatus::Limit)
                return EFBIG;
            if (host.status != RegionSnapshotStatus::Complete)
                return EIO;
            CacheMapResolver cached(catalog, cache.start, cache.size,
                                    [cache] { return cache.current(); });
            auto maps = assemble_proc_maps(
                host, owners, [&](const ProcessRegion& region, ProcMapRecord& record) {
                    if (region.start >= cache.start && region.end <= cache.start + cache.size)
                        return cached(region, record) || project_host_mapping(region, record);
                    return resolve_internal_mapping(region, record) ||
                           images.resolve(region, record) ||
                           resolve_host_reserved_mapping(region, record) ||
                           project_host_mapping(region, record);
                });
            if (!cache.current())
                return EAGAIN;
            switch (maps.status) {
            case ProcMapsAssemblyStatus::Complete:
                output = std::move(maps.text);
                return 0;
            case ProcMapsAssemblyStatus::Incomplete:
                return ENOTSUP;
            case ProcMapsAssemblyStatus::Stale:
                return EAGAIN;
            case ProcMapsAssemblyStatus::Limit:
                return EFBIG;
            case ProcMapsAssemblyStatus::OutOfMemory:
                return ENOMEM;
            default:
                return EIO;
            }
        } catch (const std::bad_alloc&) {
            return ENOMEM;
        } catch (...) {
            return EIO;
        }
    };
}
} // namespace shiraz::guest
