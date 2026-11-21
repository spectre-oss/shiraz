// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/macho_map_resolver.hpp"
#include <dlfcn.h>
#include <cerrno>
#include <fcntl.h>
#include <mach-o/dyld.h>
#include <memory>
#include <unistd.h>

namespace shiraz::guest {
enum class NativeImageCatalogStatus { Complete, Unavailable, Changed, Limit, OutOfMemory };
namespace native_image_detail {
inline bool read(uintptr_t address, void* buffer, size_t size) {
    if (!address || !size || size > UINTPTR_MAX - address)
        return false;
    mach_vm_size_t copied = 0;
    return mach_vm_read_overwrite(mach_task_self(), address, size,
                                  reinterpret_cast<uintptr_t>(buffer), &copied) == KERN_SUCCESS &&
           copied == size;
}
inline bool path(const char* address, std::string& out) {
    uintptr_t cursor = reinterpret_cast<uintptr_t>(address);
    const size_t page = getpagesize();
    out.clear();
    while (out.size() < 4096) {
        char chunk[64];
        size_t count = std::min({sizeof(chunk), page - cursor % page, 4096 - out.size()});
        if (!read(cursor, chunk, count))
            return false;
        size_t length = strnlen(chunk, count);
        out.append(chunk, length);
        if (length != count)
            return !out.empty();
        cursor += count;
    }
    return false;
}
struct Pin {
    void* image = nullptr;
    int fd = -1;
    uintptr_t header = 0;
    struct stat identity{};
    std::vector<uint8_t> commands;
    ~Pin() {
        if (fd >= 0)
            ::close(fd);
        if (image)
            ::dlclose(image);
    }
    bool current() const {
        struct stat now{};
        if (::fstat(fd, &now) || now.st_dev != identity.st_dev || now.st_ino != identity.st_ino ||
            now.st_size != identity.st_size ||
            now.st_mtimespec.tv_sec != identity.st_mtimespec.tv_sec ||
            now.st_mtimespec.tv_nsec != identity.st_mtimespec.tv_nsec ||
            now.st_ctimespec.tv_sec != identity.st_ctimespec.tv_sec ||
            now.st_ctimespec.tv_nsec != identity.st_ctimespec.tv_nsec)
            return false;
        std::vector<uint8_t> copy(commands.size());
        return read(header, copy.data(), copy.size()) && copy == commands;
    }
};
} // namespace native_image_detail

// Per-snapshot registration. RTLD_NOLOAD pins existing non-cache libraries;
// release the catalog after its maps snapshot is assembled. It never loads an
// absent library. Safe Mach reads tolerate disappearing dyld metadata; changed
// image lists reject the entire registration. This does not freeze all VM maps.
struct NativeImageCatalog {
    NativeImageCatalogStatus status = NativeImageCatalogStatus::Unavailable;
    std::vector<MachoMapResolver> images;
    bool resolve(const ProcessRegion& region, ProcMapRecord& out) const {
        if (status != NativeImageCatalogStatus::Complete)
            return false;
        for (const auto& image : images)
            if (image(region, out))
                return true;
        return false;
    }
};
inline NativeImageCatalog snapshot_native_images(uint64_t cache_start, uint64_t cache_size) {
    using namespace native_image_detail;
    NativeImageCatalog result;
    if (!cache_start || !cache_size || cache_size > UINT64_MAX - cache_start)
        return result;
    try {
        const uint32_t count = _dyld_image_count();
        if (!count || count > 8192) {
            result.status = NativeImageCatalogStatus::Limit;
            return result;
        }
        std::vector<const mach_header*> original;
        size_t command_bytes = 0;
        for (uint32_t i = 0; i < count; ++i) {
            auto header = _dyld_get_image_header(i);
            original.push_back(header);
            uintptr_t address = reinterpret_cast<uintptr_t>(header);
            if (!address)
                return {};
            if (address >= cache_start && address < cache_start + cache_size)
                continue;
            if (result.images.size() >= 128) {
                result.images.clear();
                result.status = NativeImageCatalogStatus::Limit;
                return result;
            }
            std::string name;
            if (!path(_dyld_get_image_name(i), name))
                return {};
            auto pin = std::make_shared<Pin>();
            pin->image = i == 0 ? ::dlopen(nullptr, RTLD_LAZY)
                                : ::dlopen(name.c_str(), RTLD_LAZY | RTLD_NOLOAD);
            if (!pin->image)
                return {};
            pin->header = address;
            mach_header_64 h{};
            if (!read(address, &h, sizeof(h)) || h.magic != MH_MAGIC_64 || !h.ncmds ||
                h.ncmds > 4096 || h.sizeofcmds > (1u << 20))
                return {};
            command_bytes += sizeof(h) + h.sizeofcmds;
            if (command_bytes > (16u << 20)) {
                result.images.clear();
                result.status = NativeImageCatalogStatus::Limit;
                return result;
            }
            pin->commands.resize(sizeof(h) + h.sizeofcmds);
            if (!read(address, pin->commands.data(), pin->commands.size()) ||
                std::memcmp(pin->commands.data(), &h, sizeof(h)))
                return {};
            size_t cursor = sizeof(h);
            std::array<uint8_t, 16> uuid{};
            unsigned uuids = 0;
            for (unsigned j = 0; j < h.ncmds; ++j) {
                if (pin->commands.size() - cursor < sizeof(load_command))
                    return {};
                load_command c;
                std::memcpy(&c, pin->commands.data() + cursor, sizeof(c));
                if (c.cmdsize < sizeof(c) || c.cmdsize > pin->commands.size() - cursor)
                    return {};
                if (c.cmd == LC_UUID) {
                    if (c.cmdsize != sizeof(uuid_command) || ++uuids != 1)
                        return {};
                    std::memcpy(uuid.data(), pin->commands.data() + cursor + 8, 16);
                }
                cursor += c.cmdsize;
            }
            if (cursor != pin->commands.size() || uuids != 1)
                return {};
            char canonical[4096];
            if (!::realpath(name.c_str(), canonical))
                return {};
            pin->fd = ::open(canonical, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
            if (pin->fd < 0 || ::fstat(pin->fd, &pin->identity) ||
                !S_ISREG(pin->identity.st_mode) || pin->identity.st_size < int64_t(sizeof(h)) ||
                pin->identity.st_size > (64 << 20))
                return {};
            std::vector<uint8_t> file(size_t(pin->identity.st_size));
            size_t done = 0;
            while (done < file.size()) {
                ssize_t n = ::pread(pin->fd, file.data() + done, file.size() - done, off_t(done));
                if (n < 0 && errno == EINTR)
                    continue;
                if (n <= 0)
                    return {};
                done += size_t(n);
            }
            MachoFileLayout layout;
            if (!parse_macho_file_layout(file.data(), file.size(), uint32_t(h.cputype),
                                         uint32_t(h.cpusubtype), uuid.data(), getpagesize(),
                                         layout) ||
                pin->commands.size() > file.size() - layout.slice_offset ||
                std::memcmp(pin->commands.data(), file.data() + layout.slice_offset,
                            pin->commands.size()) ||
                !pin->current())
                return {};
            int64_t slide = _dyld_get_image_vmaddr_slide(i);
            bool header_matches = false;
            for (const auto& s : layout.segments)
                if (s.file_offset == layout.slice_offset && s.file_size >= pin->commands.size()) {
                    uint64_t expected;
                    if (slide >= 0) {
                        if (s.vmaddr > UINT64_MAX - uint64_t(slide))
                            return {};
                        expected = s.vmaddr + uint64_t(slide);
                    } else {
                        uint64_t amount = uint64_t(-(slide + 1)) + 1;
                        if (s.vmaddr < amount)
                            return {};
                        expected = s.vmaddr - amount;
                    }
                    header_matches |= expected == address;
                }
            if (!header_matches)
                return {};
            MachoFileIdentity identity{canonical, uint32_t(pin->identity.st_dev),
                                       uint64_t(pin->identity.st_ino),
                                       uint64_t(pin->identity.st_size)};
            result.images.emplace_back(std::move(layout), std::move(identity), slide,
                                       [pin] { return pin->current(); });
        }
        bool same = count == _dyld_image_count();
        for (uint32_t i = 0; same && i < count; ++i)
            same = original[i] == _dyld_get_image_header(i);
        if (!same) {
            result.images.clear();
            result.status = NativeImageCatalogStatus::Changed;
            return result;
        }
        result.status = NativeImageCatalogStatus::Complete;
        return result;
    } catch (const std::bad_alloc&) {
        return {NativeImageCatalogStatus::OutOfMemory, {}};
    }
}
} // namespace shiraz::guest
