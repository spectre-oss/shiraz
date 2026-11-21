// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
struct rusage;
namespace shiraz::guest {
// Linux selector numbers are translated explicitly. No Linux selector or
// syscall number is forwarded into Darwin, and no synthetic ELF/vDSO is exposed.
std::vector<ProviderExport> system_exports();
// Copy a Darwin rusage into the pinned Bionic LP64 layout, including the
// Darwin-bytes to Linux-KiB maximum-RSS unit conversion.
void copy_guest_rusage(const ::rusage& source, void* output) noexcept;
} // namespace shiraz::guest
