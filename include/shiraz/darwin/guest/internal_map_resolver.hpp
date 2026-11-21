// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/process_regions.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"

namespace shiraz::guest {
// Describe current internal VM backing, not the originating image's pathname.
// The recursive query's external-pager bit includes the shadow chain. A COW
// file region is therefore excluded even when its top object is internal.
// Inheritance distinguishes shared anonymous maps from private allocations;
// unfamiliar combinations remain unresolved rather than guessing from refcounts.
inline bool resolve_internal_mapping(const ProcessRegion& region, ProcMapRecord& output) {
    if (region.start >= region.end || region.external_pager ||
        (region.protection & ~(VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE)))
        return false;
    bool shared = false;
    if (region.inheritance == VM_INHERIT_COPY) {
        if (region.share_mode != SM_PRIVATE && region.share_mode != SM_COW &&
            region.share_mode != SM_EMPTY && region.share_mode != SM_PRIVATE_ALIASED &&
            region.protection != VM_PROT_NONE)
            return false;
    } else if (region.inheritance == VM_INHERIT_SHARE) {
        switch (region.share_mode) {
        case SM_PRIVATE:
        case SM_EMPTY:
        case SM_SHARED:
        case SM_TRUESHARED:
        case SM_PRIVATE_ALIASED:
        case SM_SHARED_ALIASED:
            shared = true;
            break;
        default:
            return false;
        }
    } else
        return false;
    ProcMapRecord record;
    record.start = region.start;
    record.end = region.end;
    record.protection = (region.protection & VM_PROT_READ ? 1 : 0) |
                        (region.protection & VM_PROT_WRITE ? 2 : 0) |
                        (region.protection & VM_PROT_EXECUTE ? 4 : 0);
    record.shared = shared;
    output = std::move(record);
    return true;
}
} // namespace shiraz::guest
