// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>
// Reserve an immutable environment vector and strings above the initial SP of
// an attached guest thread. This is not a complete execve argv/auxv frame.
// Return zero without writing when the environment exceeds the supplied region.
inline uintptr_t shiraz_stack_environment(void* region, size_t capacity,
                                         const char* const* environment) {
  size_t count = 0, strings = 1;
  if (!region || !environment || capacity < 32) return 0;
  while (environment[count]) {
    size_t length = strnlen(environment[count], capacity);
    if (length == capacity || strings > capacity - length - 1) return 0;
    strings += length + 1;
    if (++count > capacity / sizeof(uintptr_t)) return 0;
  }
  size_t pointers = (count + 1) * sizeof(uintptr_t);
  if (pointers > capacity || strings > capacity - pointers) return 0;
  size_t used = pointers + strings;
  if (used > capacity - 31) return 0;
  used = (used + 31) & ~size_t(15); // leading zero padding and 16-byte alignment
  uintptr_t end = reinterpret_cast<uintptr_t>(region) + capacity;
  if ((end & 15) != 0) return 0;
  unsigned char* base = reinterpret_cast<unsigned char*>(end - used);
  std::memset(base, 0, used);
  auto vector = reinterpret_cast<uintptr_t*>(base + 16);
  char* destination = reinterpret_cast<char*>(base + 16 + pointers + 1);
  for (size_t i = 0; i < count; ++i) {
    size_t length = std::strlen(environment[i]) + 1;
    vector[i] = reinterpret_cast<uintptr_t>(destination);
    std::memcpy(destination, environment[i], length);
    destination += length;
  }
  return reinterpret_cast<uintptr_t>(base);
}
