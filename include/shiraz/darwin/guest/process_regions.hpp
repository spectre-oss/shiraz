// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>
#include <limits>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <new>
#include <utility>
#include <vector>

namespace shiraz::guest {
// Raw Darwin VM facts only. No Linux permissions, file path or procfs identity
// is inferred from these records. The process can change while this walks it.
struct ProcessRegion {
    uint64_t start, end, object_offset;
    vm_prot_t protection, maximum_protection;
    unsigned char share_mode;
    bool external_pager;
    vm_inherit_t inheritance = -1; // Unknown for manually constructed records.
};
enum class RegionSnapshotStatus { Complete, Limit, KernelError, Malformed, OutOfMemory };
struct RegionSnapshot {
    RegionSnapshotStatus status = RegionSnapshotStatus::Malformed;
    std::vector<ProcessRegion> regions;
};
struct CurrentProcessRegionQuery {
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t& depth,
                             vm_region_submap_info_data_64_t& info,
                             mach_msg_type_number_t& count) const noexcept {
        return mach_vm_region_recurse(mach_task_self(), &address, &size, &depth,
                                      reinterpret_cast<vm_region_recurse_info_t>(&info), &count);
    }
};

// A failed or bounded-out walk never returns a partial list. Query is injectable
// so malformed kernel replies and submap transitions can be tested independently.
template <typename Query>
RegionSnapshot snapshot_process_regions(Query query, size_t limit = 16384) {
    RegionSnapshot result;
    if (!limit || limit > 65536) {
        result.status = RegionSnapshotStatus::Limit;
        return result;
    }
    try {
        std::vector<ProcessRegion> pending;
        pending.reserve(limit);
        mach_vm_address_t cursor = 0;
        natural_t depth = 0;
        for (;;) {
            mach_vm_address_t address = cursor;
            mach_vm_size_t size = 0;
            vm_region_submap_info_data_64_t info{};
            for (;;) {
                mach_msg_type_number_t count = VM_REGION_SUBMAP_INFO_V0_COUNT_64;
                auto status = query(address, size, depth, info, count);
                if (status == KERN_INVALID_ADDRESS) {
                    result.status = RegionSnapshotStatus::Complete;
                    result.regions = std::move(pending);
                    return result;
                }
                if (status != KERN_SUCCESS) {
                    result.status = RegionSnapshotStatus::KernelError;
                    return result;
                }
                if (count != VM_REGION_SUBMAP_INFO_V0_COUNT_64 || !size ||
                    size > UINT64_MAX - address || depth > 32) {
                    result.status = RegionSnapshotStatus::Malformed;
                    return result;
                }
                if (!info.is_submap)
                    break;
                if (depth == 32) {
                    result.status = RegionSnapshotStatus::Malformed;
                    return result;
                }
                // Region APIs report the enclosing submap's start, which may precede
                // the address we asked about. Descend at the requested cursor; starting
                // at the submap boundary can revisit an earlier, unrelated leaf.
                if (address < cursor) {
                    if (cursor - address >= size) {
                        result.status = RegionSnapshotStatus::Malformed;
                        return result;
                    }
                    address = cursor;
                }
                ++depth;
            }
            uint64_t offset = info.offset;
            if (address < cursor) {
                uint64_t delta = cursor - address;
                if (delta >= size || delta > UINT64_MAX - offset) {
                    result.status = RegionSnapshotStatus::Malformed;
                    return result;
                }
                address = cursor;
                size -= delta;
                offset += delta;
            }
            if (pending.size() == limit) {
                result.status = RegionSnapshotStatus::Limit;
                return result;
            }
            pending.push_back({address, address + size, offset, info.protection,
                               info.max_protection, info.share_mode, info.external_pager != 0,
                               info.inheritance});
            cursor = address + size;
        }
    } catch (const std::bad_alloc&) {
        result.status = RegionSnapshotStatus::OutOfMemory;
        return result;
    }
}
} // namespace shiraz::guest
