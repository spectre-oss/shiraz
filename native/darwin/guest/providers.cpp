// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/providers.hpp"
#include "guest_errno.hpp"
#include <cstdlib>
#include <cstring>
#include <malloc/malloc.h>

namespace shiraz::guest {
int *guest_errno_location() {
  static thread_local int value = 0;
  return &value;
}
int errno_to_guest(int host) {
  if (!host)
    return 0;
  for (const auto &pair : bionic::errors)
    if (pair.host == host)
      return pair.guest;
  return bionic::kEIO;
}
namespace {
void *memory_find(const void *pointer, int value, size_t length) {
  return const_cast<void *>(std::memchr(pointer, value, length));
}
char *string_find(const char *pointer, int value) {
  return const_cast<char *>(std::strchr(pointer, value));
}
char *string_rfind(const char *pointer, int value) {
  return const_cast<char *>(std::strrchr(pointer, value));
}
char *string_substring(const char *haystack, const char *needle) {
  return const_cast<char *>(std::strstr(haystack, needle));
}
} // namespace
Providers core_personality() {
  Provider libc{"libc.so", {"LIBC", "LIBC_O"}, {}, false};
  auto add = [&](auto function, const char *name,
                 ErrnoPolicy policy = ErrnoPolicy::None) {
    libc.exports.push_back(fixed_export(name, "LIBC", function, policy));
  };
  add(&guest_errno_location, "__errno");
  add(&std::memcpy, "memcpy");
  add(&std::memmove, "memmove");
  add(&std::memset, "memset");
  add(&std::memcmp, "memcmp");
  add(&memory_find, "memchr");
  add(&std::strlen, "strlen");
  add(&::strnlen, "strnlen");
  add(&std::strcmp, "strcmp");
  add(&std::strncmp, "strncmp");
  add(&std::strcpy, "strcpy");
  add(&std::strncpy, "strncpy");
  add(&std::strcat, "strcat");
  add(&std::strncat, "strncat");
  add(&string_find, "strchr");
  add(&string_rfind, "strrchr");
  add(&string_substring, "strstr");
  add(&std::malloc, "malloc", ErrnoPolicy::Null);
  add(&std::calloc, "calloc", ErrnoPolicy::Null);
  add(&std::realloc, "realloc", ErrnoPolicy::Observe);
  add(&std::abort, "abort");
  add(&std::free, "free");
  add(&::malloc_size, "malloc_usable_size");
  add(&std::strtol, "strtol", ErrnoPolicy::Observe);
  add(&std::strtoul, "strtoul", ErrnoPolicy::Observe);
  add(&std::strtoll, "strtoll", ErrnoPolicy::Observe);
  add(&std::strtoull, "strtoull", ErrnoPolicy::Observe);
  Providers registry;
  registry.add(std::move(libc));
  return registry;
}
} // namespace shiraz::guest
