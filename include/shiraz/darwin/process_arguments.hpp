// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>

// Read the contiguous argv memory observed when this image loads. Bytes are
// read live on each call. If another component changed argv or environ before
// image load, the captured extent is not authenticated and may include data
// outside the original arguments, including environment data.
// The final argument must terminate within 4096 bytes or capture is unavailable.
// Returns a host errno value. *read_bytes is changed only on success. This is
// a building block, not a procfs file descriptor or an Android identity claim.
extern "C" int shiraz_process_arguments_read(uint64_t offset, void* destination, size_t capacity,
                                             size_t* read_bytes) noexcept;
// Internal host use only: the same unverified extent used by the reader.
// Outputs are changed only on success and the extent is invalid after fork.
extern "C" int shiraz_process_arguments_extent(uintptr_t* start, size_t* size) noexcept;
