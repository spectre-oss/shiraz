// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <mach/mach.h>
#include <mach/mach_vm.h>

namespace shiraz::guest::detail {
inline bool native_mapping(uintptr_t address, size_t bytes, vm_prot_t required, vm_prot_t forbidden) {
  if (!bytes || bytes > UINTPTR_MAX - address) return false;
  const uintptr_t end = address + bytes;
  uintptr_t cursor = address;
  // A BSS array can straddle file-backed and anonymous zero-fill mappings.
  // Prove every adjacent leaf, rejecting gaps and any protection mismatch.
  for (unsigned maps = 0; maps < 64 && cursor < end; ++maps) {
    natural_t depth = 0;
    bool covered = false;
    for (unsigned attempt = 0; attempt < 32; ++attempt) {
      mach_vm_address_t region_start = cursor;
      mach_vm_size_t region_size = 0;
      vm_region_submap_info_data_64_t region{};
      mach_msg_type_number_t region_count = VM_REGION_SUBMAP_INFO_V0_COUNT_64;
      const auto status = mach_vm_region_recurse(mach_task_self(), &region_start, &region_size,
                                                 &depth,
                                                 reinterpret_cast<vm_region_recurse_info_t>(&region),
                                                 &region_count);
      if (status != KERN_SUCCESS || region_count != VM_REGION_SUBMAP_INFO_V0_COUNT_64 ||
          depth > 32 || !region_size || region_size > UINT64_MAX - region_start ||
          cursor < region_start || cursor - region_start >= region_size) return false;
      if (region.is_submap) { ++depth; continue; }
      if ((region.protection & required) != required || (region.protection & forbidden)) return false;
      const auto next = region_start + region_size;
      cursor = next < end ? uintptr_t(next) : end;
      covered = true;
      break;
    }
    if (!covered) return false;
  }
  return cursor == end;
}
} // namespace shiraz::guest::detail
