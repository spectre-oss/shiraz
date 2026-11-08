// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/fortify.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>
namespace shiraz::guest {
[[noreturn]] void fortify_failure(const char *operation) {
  std::fprintf(stderr, "SHIRAZ_GUEST_FORTIFY bounds failure: %s\n", operation);
  std::abort();
}
namespace {
void bounds(size_t count, size_t available, const char *operation) {
  if (count > available) fortify_failure(operation);
}
void *copy(void *to, const void *from, size_t count, size_t available) {
  bounds(count, available, "memcpy"); return std::memcpy(to, from, count);
}
void *move(void *to, const void *from, size_t count, size_t available) {
  bounds(count, available, "memmove"); return std::memmove(to, from, count);
}
void *fill(void *to, int value, size_t count, size_t available) {
  bounds(count, available, "memset"); return std::memset(to, value, count);
}
size_t length(const char *text, size_t available) {
  size_t result = strnlen(text, available);
  if (result == available) fortify_failure("strlen");
  return result;
}
char *string_copy(char *to, const char *from, size_t available) {
  size_t size = length(from, available);
  std::memcpy(to, from, size + 1); return to;
}
char *string_ncopy(char *to, const char *from, size_t count, size_t available) {
  bounds(count, available, "strncpy"); return std::strncpy(to, from, count);
}
char *string_ncopy2(char *to, const char *from, size_t count, size_t available, size_t source) {
  bounds(count, available, "strncpy");
  for (size_t i = 0; i < count; ++i) {
    if (i == source) fortify_failure("strncpy source");
    if (!(to[i] = from[i])) { std::memset(to + i + 1, 0, count - i - 1); break; }
  }
  return to;
}
char *string_cat(char *to, const char *from, size_t available) {
  size_t used = length(to, available);
  string_copy(to + used, from, available - used); return to;
}
}
std::vector<ProviderExport> fortify_exports() {
  return {fixed_export("__memcpy_chk", "LIBC", &copy),
          fixed_export("__memmove_chk", "LIBC", &move),
          fixed_export("__memset_chk", "LIBC", &fill),
          fixed_export("__strlen_chk", "LIBC", &length),
          fixed_export("__strcpy_chk", "LIBC", &string_copy),
          fixed_export("__strncpy_chk", "LIBC", &string_ncopy),
          fixed_export("__strncpy_chk2", "LIBC", &string_ncopy2),
          fixed_export("__strcat_chk", "LIBC", &string_cat)};
}
}
