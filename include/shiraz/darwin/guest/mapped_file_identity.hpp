// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <libproc.h>
#include <new>
#include <string>
#include <sys/proc_info.h>
#include <sys/stat.h>
#include <unistd.h>

namespace shiraz::guest {
enum class MappedFileStatus { Verified, Unavailable, Mismatch, Invalid, KernelError, OutOfMemory };
enum class MappedPathStatus { Unresolved, Present, Deleted };
struct MappedFileIdentity {
    MappedFileStatus status = MappedFileStatus::Invalid;
    uint32_t device = 0;
    uint64_t inode = 0;
    uint16_t links = 0;
    std::string path;
    MappedPathStatus path_status = MappedPathStatus::Unresolved;
};
struct CurrentMappedFileQuery {
    int operator()(uint64_t address, proc_regionwithpathinfo& info) const {
        return proc_pidinfo(getpid(), PROC_PIDREGIONPATHINFO, address, &info, sizeof(info));
    }
};

// Recover live vnode identity for a range with independently known provenance.
// A containing parent region is allowed, but its vnode must match the expected
// file. No vnode does NOT prove anonymity. No file offset or sharing policy is
// inferred here: COW can destroy the offset and submaps obscure sharing flags.
// The kernel name can select a surviving hard-link alias or retain an unlinked
// alias. Verified refers to vnode identity; consumers must check path_status
// separately. Present is checked by stat, Deleted requires zero links, and
// Unresolved must not be emitted as a verified maps pathname. Queries do not
// freeze concurrent renames; this is not necessarily the original mmap name.
template <typename Query = CurrentMappedFileQuery>
MappedFileIdentity mapped_file_identity(uint64_t start, uint64_t end, uint64_t device,
                                        uint64_t inode, Query query = {}) {
    struct PreserveErrno {
        int saved = errno;
        ~PreserveErrno() {
            errno = saved;
        }
    } preserve;
    if (start >= end || device > UINT32_MAX || !inode)
        return {};
    proc_regionwithpathinfo info{};
    if (query(start, info) != int(sizeof(info)))
        return {MappedFileStatus::KernelError, 0, 0, 0, {}};
    const auto& region = info.prp_prinfo;
    if (!region.pri_size || region.pri_size > UINT64_MAX - region.pri_address)
        return {};
    if (start < region.pri_address || end > region.pri_address + region.pri_size)
        return {MappedFileStatus::Mismatch, 0, 0, 0, {}};
    const auto& st = info.prp_vip.vip_vi.vi_stat;
    if (!st.vst_ino)
        return {MappedFileStatus::Unavailable, 0, 0, 0, {}};
    if (st.vst_dev != device || st.vst_ino != inode)
        return {MappedFileStatus::Mismatch, 0, 0, 0, {}};
    const char* path = info.prp_vip.vip_path;
    size_t length = strnlen(path, sizeof(info.prp_vip.vip_path));
    if (length == sizeof(info.prp_vip.vip_path))
        return {};
    if (!length)
        return {MappedFileStatus::Unavailable, 0, 0, 0, {}};
    if (path[0] != '/')
        return {};
    MappedPathStatus path_status = MappedPathStatus::Unresolved;
    if (!st.vst_nlink)
        path_status = MappedPathStatus::Deleted;
    else {
        struct stat named{};
        if (!::stat(path, &named) && uint32_t(named.st_dev) == st.vst_dev &&
            uint64_t(named.st_ino) == st.vst_ino)
            path_status = MappedPathStatus::Present;
    }
    try {
        return {MappedFileStatus::Verified, st.vst_dev, st.vst_ino, st.vst_nlink,
                std::string(path, length),  path_status};
    } catch (const std::bad_alloc&) {
        return {MappedFileStatus::OutOfMemory, 0, 0, 0, {}};
    }
}
} // namespace shiraz::guest
