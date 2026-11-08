// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"
namespace shiraz::guest {
// Validate common AArch64 .eh_frame_hdr/CIE/FDE metadata and return function
// entries for code provenance. This does not register ELF unwind info with dyld.
std::vector<uint64_t> unwind_function_entries(const Elf &elf,
                                            const std::vector<uint8_t> &bytes);
} // namespace shiraz::guest
