// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/fonts.hpp"
#include "guest_errno.hpp"
#include "guest_io.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <dirent.h>
#include <fcntl.h>
#include <map>
#include <limits>
#include <mutex>
#include <sys/ioctl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
#include <dlfcn.h>
#include <libproc.h>
#include <mach/mach_vm.h>
#include <mach-o/dyld.h>
#include <mach-o/dyld_images.h>
#include "shiraz/darwin/guest/cache_file_catalog.hpp"
#include "shiraz/darwin/guest/cache_text_catalog.hpp"
#include "shiraz/darwin/process_arguments.hpp"
#include "shiraz/darwin/guest/process_regions.hpp"
#include <string>
#include <vector>
#endif

namespace shiraz::guest {
namespace {
namespace b = bionic;
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
// Compare ranges only. Cache text metadata identifies logical image text, not
// the VM region's backing file, inode or full data mapping. This never emits
// paths, UUIDs or addresses and is not a procfs provider.
void probe_cache_text_coverage(const std::vector<ProcessRegion>& regions, uintptr_t cache_start,
                               size_t cache_size) {
    const char* value = std::getenv("SHIRAZ_PROBE_CACHE_CATALOG_FD");
    if (!value)
        return;
    auto fail_probe = [](const char* why) {
        std::fprintf(stderr, "SHIRAZ_PROBE_CACHE_TEXT status=%s\n", why);
    };
    char* end = nullptr;
    errno = 0;
    long raw_fd = std::strtol(value, &end, 10);
    if (errno || !end || *end || end == value || raw_fd < 0 || raw_fd > INT_MAX) {
        fail_probe("bad_fd");
        return;
    }
    int fd = int(raw_fd), flags = ::fcntl(fd, F_GETFL);
    struct stat file{};
    constexpr size_t header = 20, record = 16, max_records = 65536;
    if (flags < 0 || (flags & O_ACCMODE) != O_RDONLY || ::fstat(fd, &file) ||
        !S_ISREG(file.st_mode) || file.st_nlink != 0 || file.st_size < int64_t(header) ||
        uint64_t(file.st_size) > header + max_records * record) {
        fail_probe("bad_catalog");
        return;
    }
    try {
        std::vector<uint8_t> bytes(size_t(file.st_size));
        size_t used = 0;
        while (used < bytes.size()) {
            ssize_t got = ::pread(fd, bytes.data() + used, bytes.size() - used, off_t(used));
            if (got < 0 && errno == EINTR)
                continue;
            if (got <= 0) {
                fail_probe("read_failed");
                return;
            }
            used += size_t(got);
        }
        uint32_t count = 0;
        std::memcpy(&count, bytes.data() + 16, sizeof(count));
        if (!count || count > max_records || bytes.size() != header + size_t(count) * record) {
            fail_probe("bad_catalog");
            return;
        }
        task_dyld_info_data_t task{};
        mach_msg_type_number_t words = TASK_DYLD_INFO_COUNT;
        constexpr size_t required = offsetof(dyld_all_image_infos, sharedCacheUUID) + 16;
        dyld_all_image_infos dyld{};
        mach_vm_size_t copied = 0;
        if (::task_info(mach_task_self(), TASK_DYLD_INFO, reinterpret_cast<task_info_t>(&task),
                        &words) != KERN_SUCCESS ||
            task.all_image_info_size < required ||
            mach_vm_read_overwrite(mach_task_self(), task.all_image_info_addr, required,
                                   reinterpret_cast<mach_vm_address_t>(&dyld),
                                   &copied) != KERN_SUCCESS ||
            copied != required || dyld.version < 13) {
            fail_probe("dyld_info_unavailable");
            return;
        }
        auto catalog = parse_cache_text_catalog(bytes.data(), bytes.size(), dyld.sharedCacheUUID,
                                                dyld.sharedCacheSlide, cache_start, cache_size);
        if (catalog.status != CacheCatalogStatus::Ok) {
            fail_probe(catalog.status == CacheCatalogStatus::UuidMismatch  ? "uuid_mismatch"
                       : catalog.status == CacheCatalogStatus::OutOfRange  ? "bad_span"
                       : catalog.status == CacheCatalogStatus::OutOfMemory ? "out_of_memory"
                                                                           : "bad_catalog");
            return;
        }
        uint64_t total = 0, covered = 0, executable = 0, executable_covered = 0;
        unsigned full = 0, partial = 0, none = 0, executable_regions = 0;
        for (const auto& region : regions) {
            uint64_t amount = 0;
            for (const auto& span : catalog.spans) {
                if (span.end <= region.start)
                    continue;
                if (span.start >= region.end)
                    break;
                amount += std::min(span.end, region.end) - std::max(span.start, region.start);
            }
            uint64_t length = region.end - region.start;
            if (length > UINT64_MAX - total || amount > UINT64_MAX - covered) {
                fail_probe("overflow");
                return;
            }
            total += length;
            covered += amount;
            if (amount == length)
                ++full;
            else if (amount)
                ++partial;
            else
                ++none;
            if (region.protection & VM_PROT_EXECUTE) {
                if (length > UINT64_MAX - executable || amount > UINT64_MAX - executable_covered) {
                    fail_probe("overflow");
                    return;
                }
                ++executable_regions;
                executable += length;
                executable_covered += amount;
            }
        }
        auto percent = [](uint64_t part, uint64_t whole) -> unsigned {
            return whole ? unsigned((static_cast<long double>(part) * 100.0L) /
                                    static_cast<long double>(whole))
                         : 0;
        };
        std::fprintf(stderr,
                     "SHIRAZ_PROBE_CACHE_TEXT status=ok regions=%zu full=%u partial=%u none=%u "
                     "coverage_pct=%u exec_regions=%u exec_coverage_pct=%u\n",
                     regions.size(), full, partial, none, percent(covered, total),
                     executable_regions, percent(executable_covered, executable));
    } catch (const std::bad_alloc&) {
        fail_probe("out_of_memory");
    }
}
// Candidate on-disk cache mappings are checked against live VM addresses and
// kernel object offsets. Address/UUID matches alone do not establish backing.
void probe_cache_file_coverage(const std::vector<ProcessRegion>& regions, uintptr_t cache_start,
                               size_t cache_size) {
    const char* value = std::getenv("SHIRAZ_PROBE_CACHE_FILE_CATALOG_FD");
    if (!value)
        return;
    auto fail_probe = [](const char* why) {
        std::fprintf(stderr, "SHIRAZ_PROBE_CACHE_FILES status=%s\n", why);
    };
    char* end = nullptr;
    errno = 0;
    long raw_fd = std::strtol(value, &end, 10);
    if (errno || !end || *end || end == value || raw_fd < 0 || raw_fd > INT_MAX) {
        fail_probe("bad_fd");
        return;
    }
    int fd = int(raw_fd), flags = ::fcntl(fd, F_GETFL);
    struct stat file{};
    constexpr size_t header = 20, record = 40, max_records = 4096;
    if (flags < 0 || (flags & O_ACCMODE) != O_RDONLY || ::fstat(fd, &file) ||
        !S_ISREG(file.st_mode) || file.st_nlink != 0 || file.st_size < int64_t(header) ||
        uint64_t(file.st_size) > header + max_records * record) {
        fail_probe("bad_catalog");
        return;
    }
    try {
        std::vector<uint8_t> bytes(size_t(file.st_size));
        size_t used = 0;
        while (used < bytes.size()) {
            ssize_t got = ::pread(fd, bytes.data() + used, bytes.size() - used, off_t(used));
            if (got < 0 && errno == EINTR)
                continue;
            if (got <= 0) {
                fail_probe("read_failed");
                return;
            }
            used += size_t(got);
        }
        task_dyld_info_data_t task{};
        mach_msg_type_number_t words = TASK_DYLD_INFO_COUNT;
        constexpr size_t required = offsetof(dyld_all_image_infos, sharedCacheUUID) + 16;
        dyld_all_image_infos dyld{};
        mach_vm_size_t copied = 0;
        if (::task_info(mach_task_self(), TASK_DYLD_INFO, reinterpret_cast<task_info_t>(&task),
                        &words) != KERN_SUCCESS ||
            task.all_image_info_size < required ||
            mach_vm_read_overwrite(mach_task_self(), task.all_image_info_addr, required,
                                   reinterpret_cast<mach_vm_address_t>(&dyld),
                                   &copied) != KERN_SUCCESS ||
            copied != required || dyld.version < 13) {
            fail_probe("dyld_info_unavailable");
            return;
        }
        auto catalog = parse_cache_file_catalog(bytes.data(), bytes.size(), dyld.sharedCacheUUID,
                                                dyld.sharedCacheSlide, cache_start, cache_size);
        if (catalog.status != CacheFileCatalogStatus::Ok) {
            fail_probe(catalog.status == CacheFileCatalogStatus::UuidMismatch  ? "uuid_mismatch"
                       : catalog.status == CacheFileCatalogStatus::OutOfRange  ? "bad_span"
                       : catalog.status == CacheFileCatalogStatus::Overlap     ? "overlap"
                       : catalog.status == CacheFileCatalogStatus::OutOfMemory ? "out_of_memory"
                                                                               : "bad_catalog");
            return;
        }
        uint64_t total = 0, contained_bytes = 0, offset_bytes = 0;
        unsigned contained = 0, offset_match = 0, partial = 0, none = 0, zero_offset = 0;
        for (const auto& region : regions) {
            uint64_t length = region.end - region.start;
            if (length > UINT64_MAX - total) {
                fail_probe("overflow");
                return;
            }
            total += length;
            if (!region.object_offset)
                ++zero_offset;
            const CacheFileMapping* match = nullptr;
            bool overlaps = false;
            for (const auto& mapping : catalog.mappings) {
                if (mapping.end <= region.start)
                    continue;
                if (mapping.start >= region.end)
                    break;
                overlaps = true;
                if (region.start >= mapping.start && region.end <= mapping.end) {
                    match = &mapping;
                    break;
                }
            }
            if (!match) {
                if (overlaps)
                    ++partial;
                else
                    ++none;
                continue;
            }
            ++contained;
            contained_bytes += length;
            uint64_t displacement = region.start - match->start;
            if (displacement <= UINT64_MAX - match->file_offset &&
                region.object_offset == match->file_offset + displacement) {
                ++offset_match;
                offset_bytes += length;
            }
        }
        auto percent = [](uint64_t part, uint64_t whole) -> unsigned {
            return whole ? unsigned((static_cast<long double>(part) * 100.0L) /
                                    static_cast<long double>(whole))
                         : 0;
        };
        std::fprintf(
            stderr,
            "SHIRAZ_PROBE_CACHE_FILES status=ok regions=%zu contained=%u partial=%u none=%u "
            "kernel_offset_match=%u kernel_offset_zero=%u contained_pct=%u offset_match_pct=%u\n",
            regions.size(), contained, partial, none, offset_match, zero_offset,
            percent(contained_bytes, total), percent(offset_bytes, total));
    } catch (const std::bad_alloc&) {
        fail_probe("out_of_memory");
    }
}
void probe_maps_capabilities() {
    if (!std::getenv("SHIRAZ_PROBE_MAP_CAPABILITIES"))
        return;
    static std::atomic_flag once = ATOMIC_FLAG_INIT;
    if (once.test_and_set(std::memory_order_relaxed))
        return;
    int saved = errno;
    unsigned images_before = _dyld_image_count();
    auto snapshot = snapshot_process_regions(CurrentProcessRegionQuery{});
    unsigned named = 0, external = 0, external_unnamed = 0, denied = 0;
    using CacheRange = const void* (*)(size_t*);
    auto cache_range =
        reinterpret_cast<CacheRange>(dlsym(RTLD_DEFAULT, "_dyld_get_shared_cache_range"));
    size_t cache_size = 0;
    uintptr_t cache_start = cache_range ? reinterpret_cast<uintptr_t>(cache_range(&cache_size)) : 0;
    bool cache_valid = cache_start && cache_size && cache_size <= UINTPTR_MAX - cache_start;
    unsigned unnamed_in_cache = 0, unnamed_overlap = 0, unnamed_outside = 0,
             unnamed_unclassified = 0;
    std::vector<ProcessRegion> cache_regions;
    bool collecting_cache = std::getenv("SHIRAZ_PROBE_CACHE_CATALOG_FD") != nullptr ||
                            std::getenv("SHIRAZ_PROBE_CACHE_FILE_CATALOG_FD") != nullptr;
    bool cache_collection_ok = true;
    for (const auto& region : snapshot.regions) {
        char path[4096];
        errno = 0;
        int found = proc_regionfilename(getpid(), region.start, path, sizeof(path));
        if (found > 0)
            ++named;
        else if (errno == EACCES || errno == EPERM)
            ++denied;
        if (region.external_pager) {
            ++external;
            if (found <= 0) {
                ++external_unnamed;
                if (!cache_valid)
                    ++unnamed_unclassified;
                else if (region.start >= cache_start && region.end <= cache_start + cache_size) {
                    ++unnamed_in_cache;
                    if (collecting_cache && cache_collection_ok) {
                        try {
                            cache_regions.push_back(region);
                        } catch (const std::bad_alloc&) {
                            cache_regions.clear();
                            cache_collection_ok = false;
                        }
                    }
                } else if (region.start < cache_start + cache_size && region.end > cache_start)
                    ++unnamed_overlap;
                else
                    ++unnamed_outside;
            }
        }
    }
    std::fprintf(stderr,
                 "SHIRAZ_PROBE_MAP_CAPABILITY complete=%d regions=%zu named=%u external=%u "
                 "external_unnamed=%u denied=%u\n",
                 snapshot.status == RegionSnapshotStatus::Complete, snapshot.regions.size(), named,
                 external, external_unnamed, denied);
    std::fprintf(stderr,
                 "SHIRAZ_PROBE_MAP_CACHE valid=%d unnamed_in_cache=%u unnamed_overlap=%u "
                 "unnamed_outside=%u unnamed_unclassified=%u\n",
                 cache_valid, unnamed_in_cache, unnamed_overlap, unnamed_outside,
                 unnamed_unclassified);
    if (collecting_cache && !cache_collection_ok) {
        if (std::getenv("SHIRAZ_PROBE_CACHE_CATALOG_FD"))
            std::fprintf(stderr, "SHIRAZ_PROBE_CACHE_TEXT status=out_of_memory\n");
        if (std::getenv("SHIRAZ_PROBE_CACHE_FILE_CATALOG_FD"))
            std::fprintf(stderr, "SHIRAZ_PROBE_CACHE_FILES status=out_of_memory\n");
    } else if (snapshot.status == RegionSnapshotStatus::Complete && cache_valid &&
               cache_regions.size() == unnamed_in_cache) {
        probe_cache_text_coverage(cache_regions, cache_start, cache_size);
        probe_cache_file_coverage(cache_regions, cache_start, cache_size);
    }
    if (collecting_cache)
        std::fprintf(stderr, "SHIRAZ_PROBE_CACHE_IMAGES count_unchanged=%d\n",
                     images_before == _dyld_image_count());
    errno = saved;
}
void probe_path(const char* operation, const char* path) {
    if (!std::getenv("SHIRAZ_PROBE_FS"))
        return;
    static std::atomic<unsigned> count{0};
    if (count.fetch_add(1, std::memory_order_relaxed) >= 128)
        return;
    int saved = errno;
    const char* kind = "other";
    if (!path)
        kind = "null";
    else if (strnlen(path, 4096) == 4096)
        kind = "too_long";
    else if (!std::strncmp(path, "/proc/", 6) || !std::strcmp(path, "/proc")) {
        if (!std::strcmp(path, "/proc/self/maps"))
            kind = "proc_self_maps";
        else if (!std::strcmp(path, "/proc/self/status"))
            kind = "proc_self_status";
        else if (!std::strcmp(path, "/proc/self/cmdline"))
            kind = "proc_self_cmdline";
        else if (!std::strcmp(path, "/proc/cpuinfo"))
            kind = "proc_cpuinfo";
        else if (!std::strcmp(path, "/proc/meminfo"))
            kind = "proc_meminfo";
        else if (!std::strcmp(path, "/proc/version"))
            kind = "proc_version";
        else if (!std::strcmp(path, "/proc/mounts") || !std::strcmp(path, "/proc/self/mounts"))
            kind = "proc_mounts";
        else if (!std::strcmp(path, "/proc/self/mountinfo"))
            kind = "proc_self_mountinfo";
        else if (!std::strcmp(path, "/proc/self/stat"))
            kind = "proc_self_stat";
        else if (!std::strcmp(path, "/proc/self/statm"))
            kind = "proc_self_statm";
        else if (!std::strcmp(path, "/proc/self/exe"))
            kind = "proc_self_exe";
        else if (!std::strcmp(path, "/proc/self/cgroup"))
            kind = "proc_self_cgroup";
        else if (!std::strcmp(path, "/proc/self/auxv"))
            kind = "proc_self_auxv";
        else if (!std::strcmp(path, "/proc/self/environ"))
            kind = "proc_self_environ";
        else if (!std::strcmp(path, "/proc/self/attr/current"))
            kind = "proc_self_security_context";
        else if (!std::strcmp(path, "/proc/self/fd") || !std::strncmp(path, "/proc/self/fd/", 14))
            kind = "proc_self_fd";
        else if (!std::strcmp(path, "/proc/self/task") ||
                 !std::strncmp(path, "/proc/self/task/", 16))
            kind = "proc_self_task";
        else if (!std::strncmp(path, "/proc/net/", 10) ||
                 !std::strncmp(path, "/proc/self/net/", 15))
            kind = "proc_net";
        else if (!std::strncmp(path, "/proc/sys/", 10))
            kind = "proc_sys";
        else if (!std::strncmp(path, "/proc/self/", 11))
            kind = "proc_self_other";
        else if (!std::strncmp(path, "/proc/thread-self/", 18))
            kind = "proc_thread_self";
        else {
            // Classify only our own numeric pid, never print arbitrary guest paths.
            char self[32];
            int written = std::snprintf(self, sizeof(self), "/proc/%ld/", long(getpid()));
            if (written > 0 && size_t(written) < sizeof(self) &&
                !std::strncmp(path, self, size_t(written))) {
                const char* suffix = path + written;
                if (!std::strcmp(suffix, "maps"))
                    kind = "proc_pid_maps";
                else if (!std::strcmp(suffix, "cmdline"))
                    kind = "proc_pid_cmdline";
                else if (!std::strcmp(suffix, "status"))
                    kind = "proc_pid_status";
                else
                    kind = "proc_pid_other";
            } else if (path[6] >= '0' && path[6] <= '9')
                kind = "proc_numeric_other";
            else
                kind = "proc_other";
        }
    } else if (!std::strncmp(path, "/system/", 8) || !std::strcmp(path, "/system"))
        kind = "system";
    else if (!std::strncmp(path, "/data/", 6) || !std::strcmp(path, "/data"))
        kind = "android_data";
    else if (!std::strncmp(path, "/sys/", 5) || !std::strcmp(path, "/sys"))
        kind = "sys";
    else if (!std::strncmp(path, "/dev/", 5))
        kind = "dev";
    else if (path[0] != '/')
        kind = "relative";
    std::fprintf(stderr, "SHIRAZ_PROBE_FS operation=%s kind=%s\n", operation, kind);
    errno = saved;
}
// A rejected open must not dereference an arbitrary guest pointer merely for
// diagnostics. Copy through the kernel and never print the resulting path.
void probe_rejected_open_path(const char* path) {
    if (!std::getenv("SHIRAZ_PROBE_FS") || !path)
        return;
    int saved = errno;
    std::array<char, 4096> copy{};
    uintptr_t base = reinterpret_cast<uintptr_t>(path);
    size_t offset = 0;
    while (offset < copy.size() && offset <= UINTPTR_MAX - base) {
        mach_vm_size_t received = 0;
        // A bytewise read cannot cross an unreadable page after a terminator.
        if (mach_vm_read_overwrite(mach_task_self(), base + offset, 1,
                                   reinterpret_cast<mach_vm_address_t>(copy.data() + offset),
                                   &received) != KERN_SUCCESS ||
            received != 1)
            break;
        if (!copy[offset++]) {
            probe_path("open_rejected_flags", copy.data());
            break;
        }
    }
    errno = saved;
}
// Disposable diagnostics only: verify actual argument memory, then expose it
// through either an unlinked snapshot or a probe-only live descriptor. Neither
// path is the production procfs provider.
int probe_cmdline_snapshot(const char* path, int flags, bool& handled) {
    handled = false;
    bool live = std::getenv("SHIRAZ_PROBE_CMDLINE_LIVE") != nullptr;
    if ((!live && !std::getenv("SHIRAZ_PROBE_CMDLINE_SNAPSHOT")) || !path ||
        strnlen(path, 4096) == 4096 || std::strcmp(path, "/proc/self/cmdline"))
        return -1;
    handled = true;
    if ((flags & O_ACCMODE) != O_RDONLY || (flags & ~(O_CLOEXEC | O_NONBLOCK))) {
        errno = EOPNOTSUPP;
        return -1;
    }
    const char* directory = std::getenv("TMPDIR");
    const char* expected = std::getenv("SHIRAZ_PROBE_EXPECTED_PROCESS_NAME");
    if (!directory || !expected) {
        errno = ENOSYS;
        return -1;
    }
    size_t length = strnlen(expected, 4096);
    size_t directory_length = strnlen(directory, 4096);
    if (!length || length == 4096 || !directory_length || directory_length > 4000) {
        errno = EINVAL;
        return -1;
    }
    std::vector<char> arguments(1024 * 1024);
    size_t argument_bytes = 0;
    int read_error =
        shiraz_process_arguments_read(0, arguments.data(), arguments.size(), &argument_bytes);
    if (read_error) {
        errno = read_error;
        return -1;
    }
    if (argument_bytes < length + 1 || std::memcmp(arguments.data(), expected, length) ||
        arguments[length] != '\0') {
        errno = EINVAL;
        return -1;
    }
    for (size_t i = length + 1; i < argument_bytes; ++i)
        if (arguments[i] != '\0') {
            errno = EINVAL;
            return -1;
        }
    if (live) {
        int result = probe_process_arguments_open(flags);
        if (result >= 0)
            std::fprintf(stderr, "SHIRAZ_PROBE_CMDLINE_LIVE used=1\n");
        return result;
    }
    std::string temporary = std::string(directory) + "/cmdline-probe.XXXXXX";
    int writable = ::mkstemp(temporary.data());
    if (writable < 0)
        return -1;
    size_t written = 0;
    while (written < argument_bytes) {
        ssize_t count = ::write(writable, arguments.data() + written, argument_bytes - written);
        if (count < 0 && errno == EINTR)
            continue;
        if (count <= 0) {
            int error = count < 0 ? errno : EIO;
            ::unlink(temporary.c_str());
            ::close(writable);
            errno = error;
            return -1;
        }
        written += size_t(count);
    }
    int readable = ::open(temporary.c_str(), O_RDONLY | (flags & (O_CLOEXEC | O_NONBLOCK)));
    int error = errno;
    ::unlink(temporary.c_str());
    ::close(writable);
    errno = error;
    if (readable >= 0)
        std::fprintf(stderr, "SHIRAZ_PROBE_CMDLINE_SNAPSHOT used=1\n");
    return readable;
}
// Deliberately empty counterfactual, not a maps implementation. This lets an
// isolated test distinguish an unavailable file from a parser/content fault.
int probe_empty_maps(const char* path, int flags, bool& handled) {
    handled = false;
    if (!std::getenv("SHIRAZ_PROBE_EMPTY_MAPS") || !path || strnlen(path, 4096) == 4096 ||
        std::strcmp(path, "/proc/self/maps"))
        return -1;
    handled = true;
    if ((flags & O_ACCMODE) != O_RDONLY || (flags & ~(O_CLOEXEC | O_NONBLOCK))) {
        errno = EOPNOTSUPP;
        return -1;
    }
    const char* directory = std::getenv("TMPDIR");
    if (!directory || strnlen(directory, 4001) > 4000) {
        errno = EINVAL;
        return -1;
    }
    std::string temporary = std::string(directory) + "/empty-maps-probe.XXXXXX";
    int writable = ::mkstemp(temporary.data());
    if (writable < 0)
        return -1;
    int readable = ::open(temporary.c_str(), O_RDONLY | (flags & (O_CLOEXEC | O_NONBLOCK)));
    int error = errno;
    ::unlink(temporary.c_str());
    ::close(writable);
    errno = error;
    if (readable >= 0)
        std::fprintf(stderr, "SHIRAZ_PROBE_EMPTY_MAPS used=1\n");
    return readable;
}
#endif
template <class T, size_t Offset, size_t Width> T get(const void* buffer) {
    static_assert(sizeof(T) == Width);
    T value;
    std::memcpy(&value, static_cast<const uint8_t*>(buffer) + Offset, Width);
    return value;
}
template <class T, size_t Offset, size_t Width> void put(void* buffer, T value) {
    static_assert(sizeof(T) == Width);
    std::memcpy(static_cast<uint8_t*>(buffer) + Offset, &value, Width);
}
#define GET(K, F, T, P) get<T, b::k##K##_##F, b::k##K##_##F##Width>(P)
#define PUT(K, F, T, P, V) put<T, b::k##K##_##F, b::k##K##_##F##Width>(P, static_cast<T>(V))
int fail(int error) {
    errno = error;
    return -1;
}
// The runtime-owned sysfs skeleton contains only its three directory nodes.
// Its synthetic metadata describes this view, not the host's or a phone's
// devices. Descendants and enumeration remain explicitly unsupported.
bool sysfs_name(const char* path, const char* name) {
    size_t n = std::strlen(name);
    if (std::strncmp(path, name, n))
        return false;
    while (path[n] == '/')
        ++n;
    return path[n] == '\0';
}
int sysfs_node(int fd, const char* path) {
    if (!path)
        return 0; // The ordinary path validator supplies EFAULT.
    if (strnlen(path, 4096) == 4096)
        return 0;
    if (path[0] == '/') {
        if (sysfs_name(path, "/sys"))
            return 1;
        if (sysfs_name(path, "/sys/devices"))
            return 2;
        if (sysfs_name(path, "/sys/devices/virtual"))
            return 3;
        return 0;
    }
    int parent = sysfs_directory_node(fd);
    if (parent <= 0)
        return parent;
    if (sysfs_name(path, "."))
        return parent;
    if (parent > 1 && sysfs_name(path, ".."))
        return parent - 1;
    if (parent == 1 && sysfs_name(path, "devices"))
        return 2;
    if (parent == 2 && sysfs_name(path, "virtual"))
        return 3;
    errno = ENOSYS;
    return -1;
}
int sysfs_stat(int node, void* output) {
    if (!output)
        return fail(EFAULT);
    std::array<uint8_t, b::kStatSize> bytes{};
    PUT(Stat, st_ino, uint64_t, bytes.data(), node);
    PUT(Stat, st_mode, uint32_t, bytes.data(), b::kS_IFDIR | 0555);
    PUT(Stat, st_nlink, uint32_t, bytes.data(), 2);
    PUT(Stat, st_blksize, int32_t, bytes.data(), 4096);
    std::memcpy(output, bytes.data(), bytes.size());
    return 0;
}
int reject_sysfs_mutation(int fd, const char* path) {
    if (path && path[0] == '/')
        return 0;
    int node = sysfs_directory_node(fd);
    if (node < 0)
        return -1;
    return node ? fail(EROFS) : 0;
}
bool path_ok(const char* path) {
    if (!path) {
        errno = EFAULT;
        return false;
    }
    size_t length = strnlen(path, 4096);
    if (length >= 4096) {
        errno = ENAMETOOLONG;
        return false;
    }
    size_t component = 0;
    for (size_t i = 0; i < length; ++i) {
        component = path[i] == '/' ? 0 : component + 1;
        if (component > 255) {
            errno = ENAMETOOLONG;
            return false;
        }
    }
    for (const char* root : {"/proc", "/sys", "/system", "/data", "/dev/ashmem"}) {
        size_t size = std::strlen(root);
        if (!std::strncmp(path, root, size) && (path[size] == 0 || path[size] == '/')) {
            errno = ENOSYS;
            return false; // Synthetic Android/Linux views are a separate provider.
        }
    }
    return true;
}
int at_fd(int fd) {
    return fd == b::kAT_FDCWD ? AT_FDCWD : fd;
}
int whence(int guest, bool sparse = true) {
    if (guest == b::kSEEK_SET)
        return SEEK_SET;
    if (guest == b::kSEEK_CUR)
        return SEEK_CUR;
    if (guest == b::kSEEK_END)
        return SEEK_END;
    if (sparse && guest == b::kSEEK_DATA)
        return SEEK_DATA;
    if (sparse && guest == b::kSEEK_HOLE)
        return SEEK_HOLE;
    return fail(EINVAL);
}
mode_t permissions(uint32_t mode) {
    static_assert(b::kS_IRWXU == S_IRWXU && b::kS_IRWXG == S_IRWXG && b::kS_IRWXO == S_IRWXO &&
                  b::kS_ISUID == S_ISUID && b::kS_ISGID == S_ISGID && b::kS_ISVTX == S_ISVTX);
    return static_cast<mode_t>(mode & 07777u);
}
uint64_t device(dev_t dev) {
    uint64_t ma = major(dev), mi = minor(dev);
    return ((ma & 0xfffu) << 8) | ((ma & 0xfffff000u) << 32) | (mi & 0xffu) |
           ((mi & 0xffffff00u) << 12);
}
int stat_mode(mode_t mode) {
    int type = mode & S_IFMT;
    int guest = type == S_IFREG    ? b::kS_IFREG
                : type == S_IFDIR  ? b::kS_IFDIR
                : type == S_IFLNK  ? b::kS_IFLNK
                : type == S_IFCHR  ? b::kS_IFCHR
                : type == S_IFBLK  ? b::kS_IFBLK
                : type == S_IFIFO  ? b::kS_IFIFO
                : type == S_IFSOCK ? b::kS_IFSOCK
                                   : 0;
    return guest ? guest | (mode & 07777u) : fail(EOPNOTSUPP);
}
int stat_result(const struct stat& host, void* output) {
    int mode = stat_mode(host.st_mode);
    if (mode == -1)
        return -1;
    std::array<uint8_t, b::kStatSize> bytes{};
    PUT(Stat, st_dev, uint64_t, bytes.data(), device(host.st_dev));
    PUT(Stat, st_ino, uint64_t, bytes.data(), host.st_ino);
    PUT(Stat, st_mode, uint32_t, bytes.data(), mode);
    PUT(Stat, st_nlink, uint32_t, bytes.data(), host.st_nlink);
    PUT(Stat, st_uid, uint32_t, bytes.data(), host.st_uid);
    PUT(Stat, st_gid, uint32_t, bytes.data(), host.st_gid);
    PUT(Stat, st_rdev, uint64_t, bytes.data(), device(host.st_rdev));
    PUT(Stat, st_size, int64_t, bytes.data(), host.st_size);
    PUT(Stat, st_blksize, int32_t, bytes.data(), host.st_blksize);
    PUT(Stat, st_blocks, int64_t, bytes.data(), host.st_blocks);
    auto time = [&](size_t offset, const timespec& value) {
        PUT(Timespec, tv_sec, int64_t, bytes.data() + offset, value.tv_sec);
        PUT(Timespec, tv_nsec, int64_t, bytes.data() + offset, value.tv_nsec);
    };
    time(b::kStat_st_atim, host.st_atimespec);
    time(b::kStat_st_mtim, host.st_mtimespec);
    time(b::kStat_st_ctim, host.st_ctimespec);
    std::memcpy(output, bytes.data(), bytes.size());
    return 0;
}
struct Flag {
    int guest, host;
};
const Flag open_flags[] = {
    {int(b::kO_SYNC), O_SYNC},         {int(b::kO_DSYNC), O_DSYNC},
    {int(b::kO_CREAT), O_CREAT},       {int(b::kO_EXCL), O_EXCL},
    {int(b::kO_NOCTTY), O_NOCTTY},     {int(b::kO_TRUNC), O_TRUNC},
    {int(b::kO_APPEND), O_APPEND},     {int(b::kO_NONBLOCK), O_NONBLOCK},
    {int(b::kO_CLOEXEC), O_CLOEXEC},   {int(b::kO_DIRECTORY), O_DIRECTORY},
    {int(b::kO_NOFOLLOW), O_NOFOLLOW},
};
int open_flags_to_host(int flags) {
    int access = flags & b::kO_ACCMODE;
    int host = access == b::kO_RDONLY   ? O_RDONLY
               : access == b::kO_WRONLY ? O_WRONLY
               : access == b::kO_RDWR   ? O_RDWR
                                        : -1;
    if (host == -1)
        return fail(EINVAL);
    int remaining = flags & ~int(b::kO_ACCMODE | b::kO_LARGEFILE);
    for (const auto& bit : open_flags)
        if (bit.guest && (remaining & bit.guest) == bit.guest) {
            host |= bit.host;
            remaining &= ~bit.guest;
        }
    if (remaining)
        return fail(EOPNOTSUPP);
    return host;
}
int status_flags_to_guest(int flags) {
    int access = flags & O_ACCMODE;
    int guest = access == O_WRONLY ? b::kO_WRONLY : access == O_RDWR ? b::kO_RDWR : b::kO_RDONLY;
    int remaining = flags & ~O_ACCMODE;
    for (const auto& bit : open_flags)
        if (bit.host && (remaining & bit.host) == bit.host) {
            guest |= bit.guest;
            remaining &= ~bit.host;
        }
    return guest | b::kO_LARGEFILE;
}
int at_flags(int flags, std::initializer_list<Flag> mapping, int ignored = 0) {
    int remaining = flags & ~ignored, host = 0;
    for (const auto& bit : mapping)
        if (remaining & bit.guest) {
            host |= bit.host;
            remaining &= ~bit.guest;
        }
    return remaining ? fail(EOPNOTSUPP) : host;
}
struct Directory {
    DIR* host = nullptr;
    int sysfs_fd = -1;
    std::shared_ptr<void> sysfs_lease;
    std::array<uint8_t, b::kDirentSize> entry{};
    ~Directory() {
        if (host)
            ::closedir(host);
        if (sysfs_fd >= 0) {
            int saved = errno;
            (void)sysfs_directory_close_owned(sysfs_fd, sysfs_lease);
            errno = saved;
        }
    }
};
class FileState {
    MapsStream::Factory maps_;
    std::mutex mutex_;
    std::map<void*, std::unique_ptr<Directory>> directories_;
    Directory* directory(void* handle) {
        auto found = directories_.find(handle);
        if (found == directories_.end()) {
            errno = EBADF;
            return nullptr;
        }
        return found->second.get();
    }

  public:
    explicit FileState(MapsStream::Factory maps) : maps_(std::move(maps)) {}
    int open(int fd, const char* path, int flags, uint32_t mode, bool relative) {
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
        probe_path("open", path);
        if (std::getenv("SHIRAZ_PROBE_MAP_CAPABILITIES") && path && strnlen(path, 4096) < 4096 &&
            !std::strcmp(path, "/proc/self/maps"))
            probe_maps_capabilities();
#endif
        int native = open_flags_to_host(flags);
        if (native == -1)
            return -1;
        if (maps_ && path && strnlen(path, 4096) < 4096 && !std::strcmp(path, "/proc/self/maps"))
            return process_maps_open(maps_, native);
        int node = sysfs_node(relative ? fd : b::kAT_FDCWD, path);
        if (node < 0)
            return -1;
        if (node) {
            if ((flags & b::kO_CREAT) && (flags & b::kO_EXCL))
                return fail(EEXIST);
            if (flags & (b::kO_CREAT | b::kO_TRUNC))
                return fail(EISDIR);
            return sysfs_directory_open(unsigned(node), flags);
        }
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
        bool snapshot = false;
        int snapshot_fd = probe_cmdline_snapshot(path, native, snapshot);
        if (snapshot)
            return snapshot_fd;
        int maps_fd = probe_empty_maps(path, native, snapshot);
        if (snapshot)
            return maps_fd;
#endif
        bool font = false;
        int special = openGuestFont(path, native, font);
        if (font)
            return special;
        if (!path_ok(path))
            return -1;
        if (relative)
            return flags & b::kO_CREAT ? ::openat(at_fd(fd), path, native, permissions(mode))
                                       : ::openat(at_fd(fd), path, native);
        return flags & b::kO_CREAT ? ::open(path, native, permissions(mode)) : ::open(path, native);
    }
    int stat(const char* path, void* output) {
        return stat_at(b::kAT_FDCWD, path, output, 0);
    }
    int lstat(const char* path, void* output) {
        return stat_at(b::kAT_FDCWD, path, output, b::kAT_SYMLINK_NOFOLLOW);
    }
    int fstat(int fd, void* output) {
        if (!output)
            return fail(EFAULT);
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        if (node)
            return sysfs_stat(node, output);
        int process_result;
        struct stat process_stat{};
        if (process_file_stat(fd, process_stat, process_result))
            return process_result < 0 ? -1 : stat_result(process_stat, output);
        struct stat host;
        return ::fstat(fd, &host) == -1 ? -1 : stat_result(host, output);
    }
    int stat_at(int fd, const char* path, void* output, int flags) {
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
        probe_path("stat", path);
#endif
        if (!output)
            return fail(EFAULT);
        int native = at_flags(flags, {{int(b::kAT_SYMLINK_NOFOLLOW), AT_SYMLINK_NOFOLLOW}},
                              b::kAT_EMPTY_PATH);
        if (native == -1)
            return -1;
        if (path && !*path && (flags & b::kAT_EMPTY_PATH) && fd != b::kAT_FDCWD)
            return fstat(fd, output);
        int node = sysfs_node(fd, path);
        if (node < 0)
            return -1;
        if (node)
            return sysfs_stat(node, output);
        bool font = false;
        int special = openGuestFont(path, O_RDONLY, font);
        if (font) {
            if (special < 0)
                return -1;
            int result = fstat(special, output), error = errno;
            ::close(special);
            errno = error;
            return result;
        }
        if (!path_ok(path))
            return -1;
        if (!*path && (flags & b::kAT_EMPTY_PATH)) {
            if (fd != b::kAT_FDCWD)
                return fstat(fd, output);
            path = ".";
        }
        struct stat host;
        return ::fstatat(at_fd(fd), path, &host, native) == -1 ? -1 : stat_result(host, output);
    }
    int access(const char* path, int mode) {
        return access_at(b::kAT_FDCWD, path, mode, 0);
    }
    int access_at(int fd, const char* path, int mode, int flags) {
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
        probe_path("access", path);
#endif
        static_assert(b::kR_OK == R_OK && b::kW_OK == W_OK && b::kX_OK == X_OK && b::kF_OK == F_OK);
        if (mode & ~(R_OK | W_OK | X_OK))
            return fail(EINVAL);
        int native = at_flags(flags, {{int(b::kAT_SYMLINK_NOFOLLOW), AT_SYMLINK_NOFOLLOW},
                                      {int(b::kAT_EACCESS), AT_EACCESS}});
        if (native == -1)
            return -1;
        int node = sysfs_node(fd, path);
        if (node < 0)
            return -1;
        if (node)
            return mode & W_OK ? fail(EROFS) : 0;
        bool font = false;
        int special = openGuestFont(path, O_RDONLY, font);
        if (font) {
            if (special < 0)
                return -1;
            ::close(special);
            if (mode & W_OK)
                return fail(EROFS);
            if (mode & X_OK)
                return fail(EACCES);
            return 0;
        }
        return path_ok(path) ? ::faccessat(at_fd(fd), path, mode, native) : -1;
    }
    int mkdir(const char* path, uint32_t mode) {
        if (!path_ok(path))
            return -1;
        return ::mkdir(path, permissions(mode));
    }
    int mkdir_at(int fd, const char* path, uint32_t mode) {
        if (reject_sysfs_mutation(fd, path))
            return -1;
        if (!path_ok(path))
            return -1;
        return ::mkdirat(at_fd(fd), path, permissions(mode));
    }
    int unlink(const char* path) {
        return unlink_at(b::kAT_FDCWD, path, 0);
    }
    int rmdir(const char* path) {
        return unlink_at(b::kAT_FDCWD, path, b::kAT_REMOVEDIR);
    }
    int unlink_at(int fd, const char* path, int flags) {
        if (reject_sysfs_mutation(fd, path))
            return -1;
        if (!path_ok(path))
            return -1;
        int native = at_flags(flags, {{int(b::kAT_REMOVEDIR), AT_REMOVEDIR}});
        return native == -1 ? -1 : ::unlinkat(at_fd(fd), path, native);
    }
    int rename(const char* old, const char* path) {
        return rename_at(b::kAT_FDCWD, old, b::kAT_FDCWD, path);
    }
    int rename_at(int oldfd, const char* old, int fd, const char* path) {
        if (reject_sysfs_mutation(oldfd, old) || reject_sysfs_mutation(fd, path))
            return -1;
        if (!path_ok(old) || !path_ok(path))
            return -1;
        return ::renameat(at_fd(oldfd), old, at_fd(fd), path);
    }
    int chmod(const char* path, uint32_t mode) {
        if (!path_ok(path))
            return -1;
        return ::chmod(path, permissions(mode));
    }
    int fchmod(int fd, uint32_t mode) {
        if (reject_sysfs_mutation(fd, nullptr))
            return -1;
        return ::fchmod(fd, permissions(mode));
    }
    int chmod_at(int fd, const char* path, uint32_t mode, int flags) {
        if (reject_sysfs_mutation(fd, path))
            return -1;
        if (!path_ok(path))
            return -1;
        int native = at_flags(flags, {{int(b::kAT_SYMLINK_NOFOLLOW), AT_SYMLINK_NOFOLLOW}});
        return native == -1 ? -1 : ::fchmodat(at_fd(fd), path, permissions(mode), native);
    }
    int chdir(const char* path) {
        return path_ok(path) ? ::chdir(path) : -1;
    }
    int statvfs(const char* path, void* output) {
        if (!path_ok(path))
            return -1;
        if (!output)
            return fail(EFAULT);
        struct statfs host;
        if (::statfs(path, &host) == -1)
            return -1;
        errno = 0;
        long names = ::pathconf(path, _PC_NAME_MAX);
        if (names == -1)
            return errno ? -1 : fail(EOPNOTSUPP);
        return statvfs_result(host, names, output);
    }
    int fstatvfs(int fd, void* output) {
        if (!output)
            return fail(EFAULT);
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        if (node)
            return fail(ENOSYS);
        int process_result;
        if (process_file_reject(fd, ENOTSUP, process_result))
            return process_result;
        struct statfs host;
        if (::fstatfs(fd, &host) == -1)
            return -1;
        errno = 0;
        long names = ::fpathconf(fd, _PC_NAME_MAX);
        if (names == -1)
            return errno ? -1 : fail(EOPNOTSUPP);
        return statvfs_result(host, names, output);
    }
    static int statvfs_result(const struct statfs& host, long names, void* output) {
        // Darwin statvfs counters are narrower than statfs counters. Keep the full
        // 64-bit values and serialize the guest layout instead of copying either.
        static_assert(sizeof(host.f_blocks) == sizeof(uint64_t) &&
                      sizeof(host.f_files) == sizeof(uint64_t));
        std::array<uint8_t, b::kStatvfsSize> result{};
        uint64_t flags = 0;
        for (const auto& bit :
             std::initializer_list<Flag>{{int(b::kST_RDONLY), MNT_RDONLY},
                                         {int(b::kST_NOSUID), MNT_NOSUID},
                                         {int(b::kST_NODEV), MNT_NODEV},
                                         {int(b::kST_NOEXEC), MNT_NOEXEC},
                                         {int(b::kST_SYNCHRONOUS), MNT_SYNCHRONOUS},
                                         {int(b::kST_NOATIME), MNT_NOATIME}})
            if (host.f_flags & bit.host)
                flags |= bit.guest;
        uint64_t id = uint32_t(host.f_fsid.val[0]) | (uint64_t(uint32_t(host.f_fsid.val[1])) << 32);
        PUT(Statvfs, f_bsize, uint64_t, result.data(), host.f_bsize);
        PUT(Statvfs, f_frsize, uint64_t, result.data(), host.f_bsize);
        PUT(Statvfs, f_blocks, uint64_t, result.data(), host.f_blocks);
        PUT(Statvfs, f_bfree, uint64_t, result.data(), host.f_bfree);
        PUT(Statvfs, f_bavail, uint64_t, result.data(), host.f_bavail);
        PUT(Statvfs, f_files, uint64_t, result.data(), host.f_files);
        PUT(Statvfs, f_ffree, uint64_t, result.data(), host.f_ffree);
        PUT(Statvfs, f_favail, uint64_t, result.data(), host.f_ffree);
        PUT(Statvfs, f_fsid, uint64_t, result.data(), id);
        PUT(Statvfs, f_flag, uint64_t, result.data(), flags);
        PUT(Statvfs, f_namemax, uint64_t, result.data(), names);
        std::memcpy(output, result.data(), result.size());
        return 0;
    }
    int utimes(const char* path, const void* times) {
        if (!path_ok(path))
            return -1;
        timeval native[2];
        if (times)
            for (int i = 0; i < 2; ++i) {
                auto* value = static_cast<const uint8_t*>(times) + i * b::kTimevalSize;
                int64_t micros = GET(Timeval, tv_usec, int64_t, value);
                if (micros < 0 || micros >= 1000000)
                    return fail(EINVAL);
                native[i].tv_sec = GET(Timeval, tv_sec, int64_t, value);
                native[i].tv_usec = static_cast<suseconds_t>(micros);
            }
        return ::utimes(path, times ? native : nullptr);
    }
    int symlink(const char* target, const char* path) {
        if (!path_ok(target) || !path_ok(path))
            return -1;
        return ::symlink(target, path);
    }
    int link(const char* old, const char* path) {
        if (!path_ok(old) || !path_ok(path))
            return -1;
        return ::link(old, path);
    }
    ssize_t readlink(const char* path, char* output, size_t size) {
        if (!path_ok(path))
            return -1;
        return ::readlink(path, output, size);
    }
    int truncate(const char* path, int64_t size) {
        if (!path_ok(path))
            return -1;
        return ::truncate(path, size);
    }
    int64_t lseek(int fd, int64_t offset, int origin) {
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        if (node)
            return fail(ENOSYS);
        int64_t probe_result;
        if (process_file_seek(fd, offset, origin, probe_result))
            return probe_result;

        int native = whence(origin);
        return native == -1 ? -1 : ::lseek(fd, offset, native);
    }
    int64_t fcntl(Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        int fd = cursor.read<int>(), command = cursor.read<int>();
        if (command == b::kF_GETFD) {
            int value = ::fcntl(fd, F_GETFD);
            return value == -1 ? -1 : (value & FD_CLOEXEC ? b::kFD_CLOEXEC : 0);
        }
        if (command == b::kF_SETFD) {
            int value = cursor.read<int>();
            if (value & ~b::kFD_CLOEXEC)
                return fail(EINVAL);
            return ::fcntl(fd, F_SETFD, value & b::kFD_CLOEXEC ? FD_CLOEXEC : 0);
        }
        if (command == b::kF_GETFL) {
            int value = ::fcntl(fd, F_GETFL);
            return value == -1 ? -1 : status_flags_to_guest(value);
        }
        if (command == b::kF_SETFL) {
            int value = cursor.read<int>();
            if (open_flags_to_host(value) == -1)
                return -1;
            int flags = ::fcntl(fd, F_GETFL);
            if (flags == -1)
                return -1;
            flags &= ~(O_APPEND | O_NONBLOCK);
            if (value & b::kO_APPEND)
                flags |= O_APPEND;
            if (value & b::kO_NONBLOCK)
                flags |= O_NONBLOCK;
            return ::fcntl(fd, F_SETFL, flags);
        }
        if (command == b::kF_DUPFD || command == b::kF_DUPFD_CLOEXEC)
            return ::fcntl(fd, command == b::kF_DUPFD ? F_DUPFD : F_DUPFD_CLOEXEC,
                           cursor.read<int>());
        if (command == b::kF_GETLK || command == b::kF_SETLK || command == b::kF_SETLKW) {
            void* guest = cursor.read<void*>();
            if (!guest)
                return fail(EFAULT);
            int type = GET(Flock, l_type, int16_t, guest),
                origin = GET(Flock, l_whence, int16_t, guest);
            struct flock host{};
            host.l_type = type == b::kF_RDLCK   ? F_RDLCK
                          : type == b::kF_WRLCK ? F_WRLCK
                          : type == b::kF_UNLCK ? F_UNLCK
                                                : -1;
            int native = whence(origin, false);
            if (host.l_type == -1 || native == -1)
                return fail(EINVAL);
            host.l_whence = native;
            host.l_start = GET(Flock, l_start, int64_t, guest);
            host.l_len = GET(Flock, l_len, int64_t, guest);
            host.l_pid = GET(Flock, l_pid, int32_t, guest);
            int result = ::fcntl(fd,
                                 command == b::kF_GETLK   ? F_GETLK
                                 : command == b::kF_SETLK ? F_SETLK
                                                          : F_SETLKW,
                                 &host);
            if (result != -1 && command == b::kF_GETLK) {
                std::array<uint8_t, b::kFlockSize> output{};
                PUT(Flock, l_type, int16_t, output.data(),
                    host.l_type == F_RDLCK   ? b::kF_RDLCK
                    : host.l_type == F_WRLCK ? b::kF_WRLCK
                                             : b::kF_UNLCK);
                PUT(Flock, l_whence, int16_t, output.data(),
                    host.l_whence == SEEK_SET   ? b::kSEEK_SET
                    : host.l_whence == SEEK_CUR ? b::kSEEK_CUR
                                                : b::kSEEK_END);
                PUT(Flock, l_start, int64_t, output.data(), host.l_start);
                PUT(Flock, l_len, int64_t, output.data(), host.l_len);
                PUT(Flock, l_pid, int32_t, output.data(), host.l_pid);
                std::memcpy(guest, output.data(), output.size());
            }
            return result;
        }
        return fail(ENOSYS);
    }
    int ioctl(Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        int fd = cursor.read<int>();
        uint32_t request = cursor.read<uint32_t>();
        if (request == b::kFIOCLEX || request == b::kFIONCLEX) {
            int old = ::fcntl(fd, F_GETFD);
            return old == -1
                       ? -1
                       : ::fcntl(fd, F_SETFD,
                                 request == b::kFIOCLEX ? old | FD_CLOEXEC : old & ~FD_CLOEXEC);
        }
        if (request == b::kFIONREAD || request == b::kFIONBIO) {
            int32_t* value = cursor.read<int32_t*>();
            if (!value)
                return fail(EFAULT);
            int host = request == b::kFIONBIO ? *value : 0;
            int result = ::ioctl(fd, request == b::kFIONBIO ? FIONBIO : FIONREAD, &host);
            if (result != -1 && request == b::kFIONREAD)
                *value = host;
            return result;
        }
        return fail(ENOTTY);
    }
    void* create_directory(const char* path, int fd, bool from_fd) {
        int node = from_fd ? sysfs_directory_node(fd) : sysfs_node(b::kAT_FDCWD, path);
        if (node < 0)
            return nullptr;
        if (!from_fd && !node && !path_ok(path))
            return nullptr;
        std::lock_guard<std::mutex> lock(mutex_);
        if (directories_.size() >= 4096) {
            errno = EMFILE;
            return nullptr;
        }
        auto entry = std::make_unique<Directory>();
        void* key = entry.get();
        // Reserve bookkeeping before fdopendir takes ownership of the caller's fd.
        directories_.emplace(key, std::move(entry));
        if (node) {
            std::shared_ptr<void> lease;
            int owned = from_fd ? fd
                                : sysfs_directory_open(unsigned(node),
                                                       int(b::kO_RDONLY | b::kO_CLOEXEC), &lease);
            if (owned < 0) {
                int error = errno;
                directories_.erase(key);
                errno = error;
                return nullptr;
            }
            if (from_fd)
                lease = sysfs_directory_lease(owned);
            if (!lease) {
                int error = errno;
                directories_.erase(key);
                errno = error;
                return nullptr;
            }
            directories_.at(key)->sysfs_fd = owned;
            directories_.at(key)->sysfs_lease = std::move(lease);
            return key;
        }
        DIR* host = from_fd ? ::fdopendir(fd) : ::opendir(path);
        if (!host) {
            int error = errno;
            directories_.erase(key);
            errno = error;
            return nullptr;
        }
        directories_.at(key)->host = host;
        return key;
    }
    void* opendir(const char* path) {
        return create_directory(path, -1, false);
    }
    void* fdopendir(int fd) {
        return create_directory(nullptr, fd, true);
    }
    int closedir(void* handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        if (!value)
            return -1;
        if (value->sysfs_fd >= 0) {
            int result = sysfs_directory_close_owned(value->sysfs_fd, value->sysfs_lease),
                error = errno;
            value->sysfs_fd = -1;
            directories_.erase(handle);
            errno = error;
            return result;
        }
        int result = ::closedir(value->host), error = errno;
        value->host = nullptr;
        directories_.erase(handle);
        errno = error;
        return result;
    }
    void* readdir(void* handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        if (!value)
            return nullptr;
        if (value->sysfs_fd >= 0) {
            errno = ENOSYS;
            return nullptr;
        }
        auto* host = ::readdir(value->host);
        if (!host)
            return nullptr;
        size_t length = strnlen(host->d_name, sizeof(host->d_name));
        if (length >= b::kDirent_d_nameWidth) {
            errno = ENAMETOOLONG;
            return nullptr;
        }
        long offset = ::telldir(value->host);
        if (offset == -1) {
            if (!errno)
                errno = EIO;
            return nullptr;
        }
        value->entry.fill(0);
        PUT(Dirent, d_ino, uint64_t, value->entry.data(), host->d_ino);
        PUT(Dirent, d_off, int64_t, value->entry.data(), offset);
        PUT(Dirent, d_reclen, uint16_t, value->entry.data(),
            (b::kDirent_d_name + length + 1 + 7) & ~size_t(7));
        uint8_t type = host->d_type == DT_REG    ? b::kDT_REG
                       : host->d_type == DT_DIR  ? b::kDT_DIR
                       : host->d_type == DT_LNK  ? b::kDT_LNK
                       : host->d_type == DT_FIFO ? b::kDT_FIFO
                       : host->d_type == DT_CHR  ? b::kDT_CHR
                       : host->d_type == DT_BLK  ? b::kDT_BLK
                       : host->d_type == DT_SOCK ? b::kDT_SOCK
                                                 : b::kDT_UNKNOWN;
        PUT(Dirent, d_type, uint8_t, value->entry.data(), type);
        std::memcpy(value->entry.data() + b::kDirent_d_name, host->d_name, length + 1);
        errno = 0;
        return value->entry.data();
    }
    int dirfd(void* handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        return value ? (value->sysfs_fd >= 0 ? value->sysfs_fd : ::dirfd(value->host)) : -1;
    }
    int64_t telldir(void* handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        if (value && value->sysfs_fd >= 0)
            return fail(ENOSYS);
        return value ? ::telldir(value->host) : -1;
    }
    void seekdir(void* handle, int64_t location) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        if (value && value->sysfs_fd >= 0) {
            errno = ENOSYS;
            return;
        }
        if (value)
            ::seekdir(value->host, location);
    }
    void rewinddir(void* handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = directory(handle);
        if (value && value->sysfs_fd >= 0) {
            errno = ENOSYS;
            return;
        }
        if (value)
            ::rewinddir(value->host);
    }
    int utimens(int fd, const char* path, const void* times, int flags, bool only_fd) {
        if (reject_sysfs_mutation(fd, only_fd ? nullptr : path))
            return -1;
        int native = at_flags(flags, {{int(b::kAT_SYMLINK_NOFOLLOW), AT_SYMLINK_NOFOLLOW}});
        if (native == -1 || (!only_fd && !path_ok(path)))
            return -1;
        timespec host[2];
        if (times)
            for (int i = 0; i < 2; ++i) {
                auto* value = static_cast<const uint8_t*>(times) + i * b::kTimespecSize;
                int64_t nanos = GET(Timespec, tv_nsec, int64_t, value);
                host[i].tv_sec = GET(Timespec, tv_sec, int64_t, value);
                host[i].tv_nsec = nanos == b::kUTIME_NOW    ? UTIME_NOW
                                  : nanos == b::kUTIME_OMIT ? UTIME_OMIT
                                                            : nanos;
                if (nanos != b::kUTIME_NOW && nanos != b::kUTIME_OMIT &&
                    (nanos < 0 || nanos >= 1000000000))
                    return fail(EINVAL);
            }
        return only_fd ? ::futimens(fd, times ? host : nullptr)
                       : ::utimensat(at_fd(fd), path, times ? host : nullptr, native);
    }
};
template <class R, class... Args>
ProviderExport method_export(const char* name, std::shared_ptr<FileState> state,
                             R (FileState::*method)(Args...),
                             ErrnoPolicy policy = ErrnoPolicy::MinusOne) {
    auto* code = closure([state, method, policy](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        std::tuple<Args...> args{cursor.read<Args>()...};
        HostErrno preserve;
        if constexpr (std::is_void_v<R>) {
            if (policy != ErrnoPolicy::None)
                throw ElfError("void file adapter requires no errno result policy");
            std::apply([&](Args... values) { ((*state).*method)(values...); }, args);
        } else {
            R result =
                std::apply([&](Args... values) { return ((*state).*method)(values...); }, args);
            int error = errno;
            bool failed = false;
            if constexpr (std::is_pointer_v<R>)
                failed = policy == ErrnoPolicy::Null && !result;
            else if constexpr (std::is_integral_v<R>)
                failed = policy == ErrnoPolicy::MinusOne && result == R(-1);
            if ((policy == ErrnoPolicy::Observe && error) || failed)
                *guest_errno_location() = errno_to_guest(error ? error : EIO);
            return_value(capture, result);
        }
    });
    return {
        name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function, "file/translated",
        false};
}
ssize_t vector_io(int fd, const void* input, int count, bool writing) {
    struct Vector {
        void* data;
        uint64_t size;
    };
    static_assert(sizeof(Vector) == 16);
    if (count < 0 || count > 1024) {
        errno = EINVAL;
        return -1;
    }
    if (!input && count) {
        errno = EFAULT;
        return -1;
    }
    std::vector<Vector> vectors(size_t(count), Vector{});
    size_t total = 0;
    for (int i = 0; i < count; ++i) {
        std::memcpy(&vectors[size_t(i)], static_cast<const uint8_t*>(input) + size_t(i) * 16, 16);
        auto& v = vectors[size_t(i)];
        if ((!v.data && v.size) || v.size > (64u << 20) - total) {
            errno = v.data ? EINVAL : EFAULT;
            return -1;
        }
        total += v.size;
    }
    std::vector<uint8_t> bytes(total);
    size_t offset = 0;
    if (writing)
        for (auto& v : vectors) {
            if (v.size)
                std::memcpy(bytes.data() + offset, v.data, size_t(v.size));
            offset += v.size;
        }
    ssize_t result = writing ? event_descriptor_write(fd, bytes.data(), bytes.size())
                             : event_descriptor_read(fd, bytes.data(), bytes.size());
    if (!writing && result > 0) {
        offset = 0;
        size_t remaining = size_t(result);
        for (auto& v : vectors) {
            size_t n = std::min(size_t(v.size), remaining);
            if (n)
                std::memcpy(v.data, bytes.data() + offset, n);
            remaining -= n;
            offset += n;
            if (!remaining)
                break;
        }
    }
    return result;
}
} // namespace
std::vector<ProviderExport> file_exports(MapsStream::Factory maps) {
    auto state = std::make_shared<FileState>(std::move(maps));
    std::vector<ProviderExport> result;
    auto method = [&](const char* name, auto member, ErrnoPolicy policy = ErrnoPolicy::MinusOne) {
        result.push_back(method_export(name, state, member, policy));
    };
    auto fixed = [&](const char* name, auto function, ErrnoPolicy policy = ErrnoPolicy::MinusOne) {
        result.push_back(fixed_export(name, "LIBC", function, policy));
    };
    for (const char* name : {"open", "open64", "openat", "openat64", "__open_2", "__openat_2"}) {
        bool relative = std::strstr(name, "openat"), checked = name[0] == '_';
        auto* code = closure([state, relative, checked](Capture& capture) {
            CallCursor cursor(capture, Convention::Aapcs64);
            int fd = relative ? cursor.read<int>() : int(b::kAT_FDCWD);
            const char* path = cursor.read<const char*>();
            int flags = cursor.read<int>();
            HostErrno preserve;
            int value = -1;
            bool needs_mode = (flags & b::kO_CREAT) || ((flags & b::kO_TMPFILE) == b::kO_TMPFILE);
            if (checked && needs_mode) {
                std::fprintf(stderr, "SHIRAZ_GUEST_FORTIFY open requires a mode argument\n");
                std::abort();
            }
            if (open_flags_to_host(flags) != -1)
                value = state->open(fd, path, flags, needs_mode ? cursor.read<uint32_t>() : 0,
                                    relative);
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
            else if (flags == 0x200000 && std::getenv("SHIRAZ_PROBE_OPATH_MISSING")) {
                // Counterfactual only: report absent paths instead of unsupported
                // O_PATH to see whether callers' failure handling changes startup.
                // No descriptor is opened, and the production bridge lacks this path.
                static std::atomic_flag once = ATOMIC_FLAG_INIT;
                if (!once.test_and_set(std::memory_order_relaxed))
                    std::fprintf(stderr, "SHIRAZ_PROBE_OPATH_MISSING active=1\n");
                errno = ENOENT;
            } else if (std::getenv("SHIRAZ_PROBE_FILE_SYSCALLS")) {
                static std::atomic<unsigned> rejected_flags{0};
                if (rejected_flags.fetch_add(1, std::memory_order_relaxed) < 128) {
                    int saved_errno = errno;
                    probe_rejected_open_path(path);
                    std::fprintf(stderr, "SHIRAZ_PROBE_OPEN_FLAGS flags=0x%x error=%d\n",
                                 unsigned(flags), saved_errno);
                    errno = saved_errno;
                }
            }
#endif
            if (value == -1)
                *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
            return_value(capture, value);
        });
        result.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
                          "file/open-arguments", false});
    }
    for (const char* name : {"fcntl", "fcntl64", "ioctl"}) {
        bool ioctl = !std::strcmp(name, "ioctl");
        void* code = closure([state, ioctl](Capture& capture) {
            HostErrno preserve;
            int value;
            int64_t command_value = 0;
            if (ioctl) {
                if (!events_ioctl(capture, value))
                    value = state->ioctl(capture);
            } else {
                if (events_fcntl(capture, command_value))
                    value = int(command_value);
                else
                    value = int(state->fcntl(capture));
            }
            if (value == -1)
                *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
            return_value(capture, value);
        });
        result.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
                          "file/command-arguments", false});
    }
    method("stat", &FileState::stat);
    method("stat64", &FileState::stat);
    method("lstat", &FileState::lstat);
    method("lstat64", &FileState::lstat);
    method("fstat", &FileState::fstat);
    method("fstat64", &FileState::fstat);
    method("fstatat", &FileState::stat_at);
    method("fstatat64", &FileState::stat_at);
    method("access", &FileState::access);
    method("faccessat", &FileState::access_at);
    method("mkdir", &FileState::mkdir);
    method("mkdirat", &FileState::mkdir_at);
    method("rmdir", &FileState::rmdir);
    method("unlink", &FileState::unlink);
    method("unlinkat", &FileState::unlink_at);
    method("rename", &FileState::rename);
    method("renameat", &FileState::rename_at);
    method("chmod", &FileState::chmod);
    method("fchmod", &FileState::fchmod);
    method("fchmodat", &FileState::chmod_at);
    method("chdir", &FileState::chdir);
    method("utimes", &FileState::utimes);
    method("statvfs", &FileState::statvfs);
    method("statvfs64", &FileState::statvfs);
    method("fstatvfs", &FileState::fstatvfs);
    method("fstatvfs64", &FileState::fstatvfs);
    method("symlink", &FileState::symlink);
    method("link", &FileState::link);
    method("readlink", &FileState::readlink);
    method("truncate", &FileState::truncate);
    method("truncate64", &FileState::truncate);
    method("lseek", &FileState::lseek);
    method("lseek64", &FileState::lseek);
    method("opendir", &FileState::opendir, ErrnoPolicy::Null);
    method("fdopendir", &FileState::fdopendir, ErrnoPolicy::Null);
    method("closedir", &FileState::closedir);
    method("readdir", &FileState::readdir, ErrnoPolicy::Observe);
    method("readdir64", &FileState::readdir, ErrnoPolicy::Observe);
    method("dirfd", &FileState::dirfd);
    method("telldir", &FileState::telldir);
    method("seekdir", &FileState::seekdir, ErrnoPolicy::None);
    method("rewinddir", &FileState::rewinddir, ErrnoPolicy::None);
    fixed("read", &event_descriptor_read);
    fixed(
        "__read_chk", +[](int fd, void* buffer, size_t count, size_t size) -> ssize_t {
            if (count > size || count > size_t(std::numeric_limits<ssize_t>::max())) {
                std::fprintf(stderr, "SHIRAZ_GUEST_FORTIFY invalid read count\n");
                std::abort();
            }
            return event_descriptor_read(fd, buffer, count);
        });
    fixed("write", &event_descriptor_write);
    fixed(
        "readv",
        +[](int fd, const void* input, int count) { return vector_io(fd, input, count, false); });
    fixed(
        "writev",
        +[](int fd, const void* input, int count) { return vector_io(fd, input, count, true); });
    auto positioned_read = +[](int fd, void* buffer, size_t count, int64_t offset) -> ssize_t {
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        if (node)
            return fail(EISDIR);
        ssize_t result;
        if (process_file_pread(fd, buffer, count, offset, result))
            return result;

        return ::pread(fd, buffer, count, offset);
    };
    auto positioned_write =
        +[](int fd, const void* buffer, size_t count, int64_t offset) -> ssize_t {
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        if (node)
            return fail(EBADF);
        ssize_t result;
        if (process_file_pwrite(fd, result))
            return result;

        return ::pwrite(fd, buffer, count, offset);
    };
    fixed("pread", positioned_read);
    fixed("pread64", positioned_read);
    fixed("pwrite", positioned_write);
    fixed("pwrite64", positioned_write);
    fixed("pipe", &::pipe);
    fixed("close", &event_descriptor_close);
    auto descriptor_sync = +[](int fd) {
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        int process_result;
        if (process_file_reject(fd, EINVAL, process_result))
            return process_result;
        return node ? fail(ENOSYS) : ::fsync(fd);
    };
    auto descriptor_truncate = +[](int fd, off_t size) {
        int node = sysfs_directory_node(fd);
        if (node < 0)
            return -1;
        int process_result;
        if (process_file_reject(fd, EINVAL, process_result))
            return process_result;
        return node ? fail(EINVAL) : ::ftruncate(fd, size);
    };
    fixed("fsync", descriptor_sync);
    fixed("fdatasync", descriptor_sync);
    fixed("ftruncate", descriptor_truncate);
    fixed("ftruncate64", descriptor_truncate);
    fixed("dup", &event_descriptor_dup);
    fixed("dup2", &event_descriptor_dup2);
    for (bool fd_only : {false, true}) {
        void* code = closure([state, fd_only](Capture& capture) {
            CallCursor cursor(capture, Convention::Aapcs64);
            int fd = cursor.read<int>();
            const char* path = fd_only ? nullptr : cursor.read<const char*>();
            const void* times = cursor.read<const void*>();
            int flags = fd_only ? 0 : cursor.read<int>();
            HostErrno preserve;
            int value = state->utimens(fd, path, times, flags, fd_only);
            if (value == -1)
                *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
            return_value(capture, value);
        });
        result.push_back({fd_only ? "futimens" : "utimensat", "LIBC",
                          reinterpret_cast<uintptr_t>(code), SymbolKind::Function, "file/timespec",
                          false});
    }
    return result;
}
Providers file_personality(MapsStream::Factory maps) {
    auto base = core_personality();
    Providers registry;
    for (const auto& pair : base.modules()) {
        Provider provider = pair.second;
        if (pair.first == "libc.so") {
            auto files = file_exports(maps);
            provider.exports.insert(provider.exports.end(), std::make_move_iterator(files.begin()),
                                    std::make_move_iterator(files.end()));
        }
        registry.add(std::move(provider));
    }
    return registry;
}
} // namespace shiraz::guest
