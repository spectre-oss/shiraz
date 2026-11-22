// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>

extern "C" {

// Returns the supported Linux Landlock ABI version (> 0) when running on a
// Linux kernel with Landlock enabled, or 0 when Landlock is unavailable or on
// non-Linux hosts.
int shiraz_sandbox_landlock_abi_version(void);

// Applies an irrevocable Linux Landlock filesystem policy restricting the
// calling thread (and future children) to read-only access under readOnlyPaths
// and read-write access under readWritePaths. Sets PR_SET_NO_NEW_PRIVS first.
//
// Returns 0 on success, -ENOSYS when Landlock is unavailable, or negative errno
// on invalid arguments or kernel rejection.
int shiraz_sandbox_apply_landlock(const char* const* readOnlyPaths, size_t readOnlyCount,
                                  const char* const* readWritePaths, size_t readWriteCount);

} // extern "C"
