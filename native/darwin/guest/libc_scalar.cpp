// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/libc_scalar.hpp"
#include "guest_errno.hpp"
#include <algorithm>
#include <array>
#include <climits>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cwchar>
#include <cstring>
#include <new>
#include <memory>
#include <mutex>
#include <string>
#include <unistd.h>

namespace shiraz::guest {
namespace {
bool guest_error_to_host(int guest, int &host) {
  if (!guest) { host = 0; return true; }
  for (const auto &error : bionic::errors)
    if (error.guest == guest) { host = error.host; return true; }
  return false;
}
void error_text(int guest, char *buffer, size_t size) {
  int host = 0;
  if (!guest) { std::snprintf(buffer, size, "Success"); return; }
  if (guest_error_to_host(guest, host) && !::strerror_r(host, buffer, size)) return;
  // Unknown/unmapped Linux numbers retain their guest number; passing that
  // number to Darwin strerror would name an unrelated host failure.
  std::snprintf(buffer, size, "Unknown error %d", guest);
}
int error_text_copy(int guest, char *buffer, size_t size) {
  if (!buffer && size) return errno_to_guest(EFAULT);
  char text[1024]; error_text(guest, text, sizeof(text));
  size_t length = std::strlen(text);
  if (size) {
    size_t copied = std::min(length, size - 1);
    std::memcpy(buffer, text, copied); buffer[copied] = 0;
  }
  // Android's POSIX strerror_r preserves errno and returns ERANGE after
  // writing a terminated prefix; unknown numbers otherwise return success.
  return length >= size ? errno_to_guest(ERANGE) : 0;
}
char *error_text_pointer(int guest) {
  static thread_local std::array<char, 1024> text{};
  error_text(guest, text.data(), text.size());
  return text.data();
}
void print_error(const char *prefix) {
  char text[1024]; error_text(*guest_errno_location(), text, sizeof(text));
  ::flockfile(stderr);
  if (prefix && *prefix) {
    (void)std::fwrite(prefix, 1, std::strlen(prefix), stderr);
    (void)std::fwrite(": ", 1, 2, stderr);
  }
  (void)std::fwrite(text, 1, std::strlen(text), stderr);
  (void)std::fputc('\n', stderr);
  ::funlockfile(stderr);
}
// Android rand/srand use the default BSD random/srandom generator, not Darwin
// rand's LCG. Independent guest state implements the pinned degree31/separation3
// recurrence and Park-Miller seeding; no native process PRNG state is changed.
class GuestRandom {
  std::mutex mutex_;
  std::array<uint32_t, 31> words_{};
  unsigned front_ = 3, back_ = 0;
  uint32_t step() {
    uint32_t value = words_[front_] + words_[back_];
    words_[front_] = value;
    front_ = (front_ + 1) % words_.size();
    back_ = (back_ + 1) % words_.size();
    return value >> 1;
  }
  void seed_unlocked(uint32_t seed) {
    words_[0] = seed;
    for (size_t i = 1; i < words_.size(); ++i) {
      int32_t previous; std::memcpy(&previous, &words_[i - 1], sizeof(previous));
      int64_t value = (int64_t(previous) * 16807) % INT32_MAX;
      if (value <= 0) value += INT32_MAX;
      words_[i] = uint32_t(value);
    }
    front_ = 3; back_ = 0;
    for (unsigned i = 0; i < 310; ++i) (void)step();
  }
public:
  GuestRandom() { seed_unlocked(1); }
  int next() { std::lock_guard<std::mutex> lock(mutex_); return int(step()); }
  void seed(unsigned value) { std::lock_guard<std::mutex> lock(mutex_); seed_unlocked(value); }
};
[[noreturn]] void sort_failure(const char *reason) noexcept {
  std::fprintf(stderr, "SHIRAZ_GUEST_FATAL qsort %s\n", reason);
  std::abort();
}
struct SortContext {
  const void *function;
  size_t comparisons = 0, limit;
};
int sort_compare(void *opaque, const void *left, const void *right) noexcept {
  auto &context = *static_cast<SortContext *>(opaque);
  if (++context.comparisons > context.limit) sort_failure("comparison bound exceeded");
  try {
    auto result = invoke(const_cast<void *>(context.function),
        {Argument::integer(int64_t(reinterpret_cast<uintptr_t>(left)), ArgumentType::Int64),
         Argument::integer(int64_t(reinterpret_cast<uintptr_t>(right)), ArgumentType::Int64)});
    return int32_t(result.result_integers[0]);
  } catch (...) { sort_failure("comparator failed"); }
}
ProviderExport sort_export(CodeValidator validate) {
  void *code = closure([validate = std::move(validate)](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *base = cursor.read<void *>();
    size_t count = cursor.read<size_t>(), size = cursor.read<size_t>();
    const void *function = cursor.read<const void *>();
    HostErrno preserve;
    if (count > (1u << 20) || (count && (!base || !size)) ||
        (size && count > (256u << 20) / size)) sort_failure("array bound invalid");
    if (count * size > UINTPTR_MAX - reinterpret_cast<uintptr_t>(base))
      sort_failure("array address overflow");
    if (count < 2) return;
    if (!function || !validate || !validate(function)) sort_failure("callback is not guest code");
    SortContext context{function, 0, count * 128 + 1024};
    // Darwin qsort_r's comparator is context FIRST, unlike glibc's variant.
    ::qsort_r(base, count, size, &context, &sort_compare);
  });
  return {"qsort", "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
          "qsort/validated-guest-comparator/bounded", false};
}
static_assert(sizeof(int) == 4 && sizeof(long) == 8 && sizeof(long long) == 8 &&
              sizeof(size_t) == 8 && sizeof(void *) == 8,
              "scalar provider requires Darwin arm64 LP64");
// Darwin wchar_t is signed32; Android arm64 wchar_t is unsigned32. Do not
// reinterpret guest wide buffers as native wchar_t buffers, even for strings.
static_assert(sizeof(wchar_t) == 4 && alignof(wchar_t) == 4 &&
              WCHAR_MIN == INT32_MIN && WCHAR_MAX == INT32_MAX,
              "wide scalar conversion requires Darwin signed32 wchar");
static_assert(sizeof(uint32_t) == 4 && alignof(uint32_t) == 4,
              "guest wide code unit ABI");
size_t wide_length(const uint32_t *text) {
  size_t length = 0;
  while (text[length]) ++length;
  return length;
}
uint32_t *wide_find(const uint32_t *text, uint32_t value) {
  for (;;) {
    if (*text == value) return const_cast<uint32_t *>(text);
    if (!*text++) return nullptr;
  }
}
int wide_compare(const uint32_t *left, const uint32_t *right) {
  while (*left && *left == *right) { ++left; ++right; }
  return *left < *right ? -1 : *left > *right ? 1 : 0;
}
uint32_t *wide_memory_find(const uint32_t *text, uint32_t value, size_t count) {
  for (size_t i = 0; i < count; ++i)
    if (text[i] == value) return const_cast<uint32_t *>(text + i);
  return nullptr;
}
int wide_memory_compare(const uint32_t *left, const uint32_t *right, size_t count) {
  for (size_t i = 0; i < count; ++i)
    if (left[i] != right[i]) return left[i] < right[i] ? -1 : 1;
  return 0;
}
uint32_t *wide_memory_move(uint32_t *destination, const uint32_t *source, size_t count) {
  if (count > SIZE_MAX / sizeof(uint32_t)) { errno = EOVERFLOW; return nullptr; }
  return static_cast<uint32_t *>(std::memmove(destination, source, count * sizeof(uint32_t)));
}
template <typename Result, typename Convert>
Result wide_number(const uint32_t *text, uint32_t **end, Convert convert) {
  if (end) *end = const_cast<uint32_t *>(text);
  // Preserve one native code unit per guest code unit so end-pointer offsets
  // remain exact. The native signed wchar domain excludes guest high-bit code
  // units; refuse those rather than changing their meaning through a cast.
  try {
    std::wstring copy;
    for (size_t i = 0;; ++i) {
      if (i >= (1u << 20)) { errno = E2BIG; return Result(0); }
      uint32_t value = text[i];
      if (value > INT32_MAX) { errno = EILSEQ; return Result(0); }
      if (!value) break;
      copy.push_back(static_cast<wchar_t>(value));
    }
    wchar_t *native_end = const_cast<wchar_t *>(copy.c_str());
    Result result = convert(copy.c_str(), &native_end);
    if (!native_end) native_end = const_cast<wchar_t *>(copy.c_str());
    if (end) *end = const_cast<uint32_t *>(text) + (native_end - copy.c_str());
    return result;
  } catch (const std::bad_alloc &) { }
  errno = ENOMEM;
  return Result(0);
}
long wide_long(const uint32_t *text, uint32_t **end, int base) {
  return wide_number<long>(text, end, [base](const wchar_t *s, wchar_t **e) { return std::wcstol(s, e, base); });
}
unsigned long wide_ulong(const uint32_t *text, uint32_t **end, int base) {
  return wide_number<unsigned long>(text, end, [base](const wchar_t *s, wchar_t **e) { return std::wcstoul(s, e, base); });
}
long long wide_longlong(const uint32_t *text, uint32_t **end, int base) {
  return wide_number<long long>(text, end, [base](const wchar_t *s, wchar_t **e) { return std::wcstoll(s, e, base); });
}
unsigned long long wide_ulonglong(const uint32_t *text, uint32_t **end, int base) {
  return wide_number<unsigned long long>(text, end, [base](const wchar_t *s, wchar_t **e) { return std::wcstoull(s, e, base); });
}
float wide_float(const uint32_t *text, uint32_t **end) {
  return wide_number<float>(text, end, &std::wcstof);
}
double wide_double(const uint32_t *text, uint32_t **end) {
  return wide_number<double>(text, end, &std::wcstod);
}
int aligned_allocate(void **result, size_t alignment, size_t size) {
  // Unlike malloc, POSIX returns an error number and does not modify errno or
  // the caller's result pointer on failure. Darwin and bionic error values
  // differ, so this is not an ordinary errno-returning alias.
  if (!result || alignment < sizeof(void *) || (alignment & (alignment - 1)))
    return errno_to_guest(EINVAL);
  void *allocation = nullptr;
  int error = ::posix_memalign(&allocation, alignment, size);
  if (error) return errno_to_guest(error);
  *result = allocation;
  return 0;
}
long path_configuration(const char *path, int name) {
  // android-14.0.0_r1 bionic/libc/include/unistd.h defines these numeric
  // selectors independently of Darwin. Unknown/unsupported names are errors.
  int host;
  switch (name) {
  case 0: host = _PC_FILESIZEBITS; break;
  case 1: host = _PC_LINK_MAX; break;
  case 2: host = _PC_MAX_CANON; break;
  case 3: host = _PC_MAX_INPUT; break;
  case 4: host = _PC_NAME_MAX; break;
  case 5: host = _PC_PATH_MAX; break;
  case 6: host = _PC_PIPE_BUF; break;
#ifdef _PC_2_SYMLINKS
  case 7: host = _PC_2_SYMLINKS; break;
#endif
#ifdef _PC_ALLOC_SIZE_MIN
  case 8: host = _PC_ALLOC_SIZE_MIN; break;
#endif
#ifdef _PC_REC_INCR_XFER_SIZE
  case 9: host = _PC_REC_INCR_XFER_SIZE; break;
#endif
#ifdef _PC_REC_MAX_XFER_SIZE
  case 10: host = _PC_REC_MAX_XFER_SIZE; break;
#endif
#ifdef _PC_REC_MIN_XFER_SIZE
  case 11: host = _PC_REC_MIN_XFER_SIZE; break;
#endif
#ifdef _PC_REC_XFER_ALIGN
  case 12: host = _PC_REC_XFER_ALIGN; break;
#endif
#ifdef _PC_SYMLINK_MAX
  case 13: host = _PC_SYMLINK_MAX; break;
#endif
  case 14: host = _PC_CHOWN_RESTRICTED; break;
  case 15: host = _PC_NO_TRUNC; break;
  case 16: host = _PC_VDISABLE; break;
#ifdef _PC_ASYNC_IO
  case 17: host = _PC_ASYNC_IO; break;
#endif
#ifdef _PC_PRIO_IO
  case 18: host = _PC_PRIO_IO; break;
#endif
#ifdef _PC_SYNC_IO
  case 19: host = _PC_SYNC_IO; break;
#endif
  default: errno = EINVAL; return -1;
  }
  return ::pathconf(path, host);
}
} // namespace
std::vector<ProviderExport> libc_scalar_exports(CodeValidator validate) {
  std::vector<ProviderExport> result;
  auto add = [&](auto function, const char *name,
                 ErrnoPolicy policy = ErrnoPolicy::None) {
    result.push_back(fixed_export(name, "LIBC", function, policy));
  };
  add(&std::atoi, "atoi");
  add(&std::atol, "atol");
  add(&std::atoll, "atoll");
  add(&std::atof, "atof", ErrnoPolicy::Observe);
  add(&std::strtof, "strtof", ErrnoPolicy::Observe);
  add(&std::strtod, "strtod", ErrnoPolicy::Observe);
  add(&std::strspn, "strspn");
  add(&std::strcspn, "strcspn");
  add(&::strdup, "strdup", ErrnoPolicy::Null);
  add(&error_text_pointer, "strerror");
  add(&error_text_copy, "strerror_r");
  add(&print_error, "perror");
  add(&wide_length, "wcslen");
  add(&wide_find, "wcschr");
  add(&wide_compare, "wcscmp");
  add(&wide_memory_find, "wmemchr");
  add(&wide_memory_compare, "wmemcmp");
  add(&wide_memory_move, "wmemmove", ErrnoPolicy::Observe);
  add(&wide_long, "wcstol", ErrnoPolicy::Observe);
  add(&wide_ulong, "wcstoul", ErrnoPolicy::Observe);
  add(&wide_longlong, "wcstoll", ErrnoPolicy::Observe);
  add(&wide_ulonglong, "wcstoull", ErrnoPolicy::Observe);
  add(&wide_float, "wcstof", ErrnoPolicy::Observe);
  add(&wide_double, "wcstod", ErrnoPolicy::Observe);
  add(&::isatty, "isatty", ErrnoPolicy::Observe);
  add(&::getcwd, "getcwd", ErrnoPolicy::Null);
  add(&::realpath, "realpath", ErrnoPolicy::Null);
  add(static_cast<int (*)(const char *)>(&std::remove), "remove", ErrnoPolicy::MinusOne);
  // -1 with errno=0 means an indeterminate path limit, not a failed call.
  add(&path_configuration, "pathconf", ErrnoPolicy::Observe);
  // A missing variable is a successful null result and leaves errno alone.
  add(&std::getenv, "getenv");
  add(&aligned_allocate, "posix_memalign");
  result.push_back(sort_export(std::move(validate)));
  auto random = std::make_shared<GuestRandom>();
  for (bool seed : {false, true}) {
    void *code = closure([random, seed](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      HostErrno preserve;
      if (seed) random->seed(cursor.read<unsigned>());
      else return_value(capture, random->next());
    });
    result.push_back({seed ? "srand" : "rand", "LIBC", reinterpret_cast<uintptr_t>(code),
                      SymbolKind::Function, "random/bionic-bsd-degree31/private-state", false});
  }
  return result;
}
} // namespace shiraz::guest
