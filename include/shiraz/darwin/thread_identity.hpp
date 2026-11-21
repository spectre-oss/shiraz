// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdint.h>

// One process-wide Darwin-to-Android thread identity namespace. Functions
// return zero or a host errno value; outputs are untouched on failure. If the
// image is initialized on Darwin's main thread, that thread maps to getpid().
// A first load on another thread retains stable mappings without guessing the
// leader's identity.
extern "C" {
int shiraz_android_tid_current(int* result) noexcept;
int shiraz_android_tid_for_native(uint64_t native, int* result) noexcept;
int shiraz_android_tid_to_native(int tid, uint64_t* result) noexcept;
}
