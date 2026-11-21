// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/process_regions.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"

namespace shiraz::guest {
// Guest /proc projection of an inaccessible host reservation. Invoke only for
// ranges already reconciled as UNOWNED by the guest memory manager. Preserve
// the real range and permissions, but expose no guest file identity: a native
// backing vnode is not an Android mmap file. The explicit label is neither an
// anonymous-mapping claim nor a recovered native pathname/offset. In particular,
// retained dyld ranges may still have an external pager after losing all access.
// This does not authorize access, unmapping or protection changes to host ranges.
inline bool resolve_host_reserved_mapping(const ProcessRegion& region, ProcMapRecord& output) {
    if (region.start >= region.end || region.protection != VM_PROT_NONE ||
        region.maximum_protection != VM_PROT_NONE || region.inheritance != VM_INHERIT_COPY)
        return false;
    ProcMapRecord record;
    record.start = region.start;
    record.end = region.end;
    record.name_kind = ProcMapName::KernelLabel;
    record.name = "[shiraz:host-reserved]";
    output = std::move(record);
    return true;
}
} // namespace shiraz::guest
