// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/process_regions.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"

namespace shiraz::guest {
// Last-resort guest view of an UNOWNED host range. Addresses/access permissions
// are actual VM facts. The explicit host label exposes no guest inode/path or
// original file offset, and must never replace a guest-owned mapping's metadata.
// Darwin has no exact Linux MAP_PRIVATE/MAP_SHARED equivalent for every native
// mapping: use current inheritance when available, and current object sharing
// for non-inherited ranges. This is a documented host projection, not recovery
// of the original mmap flags. It grants no access or memory ownership.
inline bool project_host_mapping(const ProcessRegion& region, ProcMapRecord& output) {
    constexpr int access = VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE;
    if (region.start >= region.end || (region.protection & ~access) ||
        (region.protection & region.maximum_protection) != region.protection)
        return false;
    bool object_shared = false;
    switch (region.share_mode) {
    case SM_SHARED:
    case SM_TRUESHARED:
    case SM_SHARED_ALIASED:
        object_shared = true;
        break;
    case SM_PRIVATE:
    case SM_COW:
    case SM_PRIVATE_ALIASED:
    case SM_EMPTY:
        break;
    default:
        return false;
    }
    bool shared;
    switch (region.inheritance) {
    case VM_INHERIT_COPY:
        shared = false;
        break;
    case VM_INHERIT_SHARE:
        shared = true;
        break;
    case VM_INHERIT_NONE:
        shared = object_shared;
        break;
    default:
        return false;
    }
    ProcMapRecord record;
    record.start = region.start;
    record.end = region.end;
    record.protection = (region.protection & VM_PROT_READ ? 1 : 0) |
                        (region.protection & VM_PROT_WRITE ? 2 : 0) |
                        (region.protection & VM_PROT_EXECUTE ? 4 : 0);
    record.shared = shared;
    record.name_kind = ProcMapName::KernelLabel;
    record.name = region.external_pager ? "[shiraz:host-external]" : "[shiraz:host-memory]";
    output = std::move(record);
    return true;
}
} // namespace shiraz::guest
