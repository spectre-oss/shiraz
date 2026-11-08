// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/locale.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <climits>
#include <cstring>
#include <cstdlib>
#include <cwchar>
#include <cwctype>
#include <locale.h>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <xlocale.h>
#include <unicode/uchar.h>

namespace shiraz::guest {
namespace {
// Android 14 libc/bionic/locale.cpp accepts these five locale names and keeps
// only the UTF-8-vs-C distinction, including for valid category subsets/base.
// https://android.googlesource.com/platform/bionic/+/refs/tags/android-14.0.0_r1/libc/bionic/locale.cpp
constexpr int android_all_mask = 0x1fbf; // categories0..5,7..12; LC_ALL=6 is not a mask bit.
constexpr size_t maximum_active = 4096, maximum_identities = size_t(1) << 20;
constexpr size_t string_limit = size_t(1) << 20;
void *global_handle() { return reinterpret_cast<void *>(UINTPTR_MAX); }
struct Token { uint64_t value = 0x53484c4f43414c45ull; };
struct Locale { bool utf8; explicit Locale(bool value) : utf8(value) {} };
struct Selection { void *identity = global_handle(); std::shared_ptr<Locale> locale; };
thread_local Selection selection;
struct GuestLconv {
  std::array<char *, 10> strings{};
  std::array<uint8_t, 14> numeric{};
  std::array<uint8_t, 2> padding{};
};
static_assert(sizeof(GuestLconv) == 96 && offsetof(GuestLconv, numeric) == 80);
struct LocaleConvention {
  GuestLconv value;
  std::array<std::string, 10> strings;
};
thread_local LocaleConvention convention;
class Locales {
  std::mutex mutex_;
  std::map<void *, std::shared_ptr<Locale>> locales_;
  std::vector<std::unique_ptr<Token>> identities_;
  std::atomic<bool> global_utf8_{true};
  locale_t c_, utf8_;
  bool name(const char *text, bool &utf8) {
    if (!text) { errno = EINVAL; return false; }
    size_t size = strnlen(text, 128);
    if (size == 128) { errno = ENOENT; return false; }
    if (!std::strcmp(text, "C") || !std::strcmp(text, "POSIX")) { utf8 = false; return true; }
    if (!*text || !std::strcmp(text, "C.UTF-8") || !std::strcmp(text, "en_US.UTF-8")) { utf8 = true; return true; }
    errno = ENOENT; return false;
  }
  void *create(bool utf8) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (locales_.size() == maximum_active || identities_.size() == maximum_identities) { errno = ENOMEM; return nullptr; }
    auto token = std::make_unique<Token>();
    auto locale = std::make_shared<Locale>(utf8);
    void *identity = token.get();
    identities_.push_back(std::move(token));
    locales_.emplace(identity, std::move(locale));
    return identity;
  }
public:
  Locales() : c_(::newlocale(LC_ALL_MASK, "C", nullptr)),
              utf8_(::newlocale(LC_ALL_MASK, "C.UTF-8", nullptr)) {
    if (!c_ || !utf8_) {
      if (c_) ::freelocale(c_);
      if (utf8_) ::freelocale(utf8_);
      throw ElfError("host lacks required real C/C.UTF-8 locale data");
    }
  }
  bool current() const { return selection.locale ? selection.locale->utf8 : global_utf8_.load(); }
  bool explicit_locale(const void *identity, bool &utf8) {
    if (identity == global_handle()) { utf8 = global_utf8_.load(); return true; }
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = locales_.find(const_cast<void *>(identity));
    if (found == locales_.end()) { errno = EINVAL; return false; }
    utf8 = found->second->utf8;
    return true;
  }
  locale_t host(const void *identity) {
    bool utf8;
    if (!explicit_locale(identity, utf8)) return nullptr;
    return utf8 ? utf8_ : c_;
  }
  locale_t current_host() { return current() ? utf8_ : c_; }
  char *set(int category, const char *text) {
    if (category < 0 || category > 12) { errno = EINVAL; return nullptr; }
    bool utf8;
    if (text) {
      if (!name(text, utf8)) return nullptr;
      global_utf8_ = utf8;
    }
    static thread_local char result[8];
    std::strcpy(result, global_utf8_ ? "C.UTF-8" : "C");
    return result;
  }
  void *make(int mask, const char *text, void *base) {
    if (mask & ~android_all_mask) { errno = EINVAL; return nullptr; }
    bool utf8;
    if (!name(text, utf8)) return nullptr;
    if (base) {
      bool ignored;
      if (base == global_handle() || !explicit_locale(base, ignored)) { errno = EINVAL; return nullptr; }
    }
    return create(utf8);
  }
  void *duplicate(void *identity) {
    bool utf8;
    return explicit_locale(identity, utf8) ? create(utf8) : nullptr;
  }
  void *use(void *identity) {
    void *previous = selection.identity;
    if (!identity) return previous;
    if (identity == global_handle()) { selection = Selection{}; return previous; }
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = locales_.find(identity);
    if (found == locales_.end()) { errno = EINVAL; return nullptr; }
    selection.identity = identity; selection.locale = found->second;
    return previous;
  }
  void free(void *identity) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = locales_.find(identity);
    if (found == locales_.end()) { errno = EINVAL; return; }
    if (found->second.use_count() != 1) { errno = EBUSY; return; }
    locales_.erase(found);
  }
  void *conventions() {
    const auto *source = ::localeconv_l(current_host());
    if (!source) { errno = EINVAL; return nullptr; }
    const char *strings[] = {source->decimal_point, source->thousands_sep, source->grouping,
      source->int_curr_symbol, source->currency_symbol, source->mon_decimal_point,
      source->mon_thousands_sep, source->mon_grouping, source->positive_sign, source->negative_sign};
    for (size_t i = 0; i < 10; ++i) {
      if (!strings[i] || strnlen(strings[i], 1024) == 1024) { errno = EOVERFLOW; return nullptr; }
      convention.strings[i] = strings[i];
      convention.value.strings[i] = convention.strings[i].data();
    }
    const char values[] = {source->int_frac_digits, source->frac_digits, source->p_cs_precedes,
      source->p_sep_by_space, source->n_cs_precedes, source->n_sep_by_space, source->p_sign_posn,
      source->n_sign_posn, source->int_p_cs_precedes, source->int_p_sep_by_space,
      source->int_n_cs_precedes, source->int_n_sep_by_space, source->int_p_sign_posn, source->int_n_sign_posn};
    for (size_t i = 0; i < 14; ++i)
      convention.value.numeric[i] = values[i] == CHAR_MAX ? 255 : uint8_t(values[i]);
    return &convention.value;
  }
};
Locales &locales() {
  // Shared Android process locale, retained through late guest TLS cleanup.
  static auto *value = new Locales;
  return *value;
}
bool narrow(const char *text) {
  if (!text) { errno = EFAULT; return false; }
  if (strnlen(text, string_limit) == string_limit) { errno = EOVERFLOW; return false; }
  return true;
}
bool wide(const uint32_t *text) {
  if (!text) { errno = EFAULT; return false; }
  for (size_t i = 0; i < string_limit / 4; ++i) {
    if (!text[i]) return true;
  }
  errno = EOVERFLOW; return false;
}
int collate(const char *left, const char *right, void *identity) {
  bool ignored;
  // Android string_l.cpp ignores locale; its strcoll implementation is strcmp.
  return locales().explicit_locale(identity, ignored) && narrow(left) && narrow(right) ? std::strcmp(left, right) : 0;
}
size_t transform(char *output, const char *input, size_t size, void *identity) {
  bool ignored;
  if (!locales().explicit_locale(identity, ignored) || !narrow(input)) return 0;
  if ((!output && size) || size > string_limit) { errno = size > string_limit ? EOVERFLOW : EFAULT; return 0; }
  size_t length = std::strlen(input);
  if (size) { size_t copied = std::min(length, size - 1); std::memcpy(output, input, copied); output[copied] = 0; }
  return length;
}
int wide_collate(const uint32_t *left, const uint32_t *right, void *identity) {
  bool ignored;
  if (!locales().explicit_locale(identity, ignored) || !wide(left) || !wide(right)) return 0;
  while (*left && *left == *right) { ++left; ++right; }
  return *left < *right ? -1 : *left != *right;
}
size_t wide_transform(uint32_t *output, const uint32_t *input, size_t size, void *identity) {
  bool ignored;
  if (!locales().explicit_locale(identity, ignored) || !wide(input)) return 0;
  if ((!output && size) || size > string_limit / 4) { errno = size > string_limit / 4 ? EOVERFLOW : EFAULT; return 0; }
  size_t length = 0; while (input[length]) ++length;
  if (size) { size_t copied = std::min(length, size - 1); std::memcpy(output, input, copied * 4); output[copied] = 0; }
  return length;
}
int classify(uint32_t character, void *identity, UProperty property) {
  bool ignored;
  if (!locales().explicit_locale(identity, ignored)) return 0;
  // Android14 wctype.cpp deliberately ignores locale and uses ICU properties
  // even when uselocale/newlocale selected C. Invalid code points and WEOF have
  // no properties; do not sign-truncate arbitrary guest high-bit values.
  // The existing host dependency is ICU78.3; Android14 shipped ICU73. This
  // implements the same operations with current pinned Unicode data, not an
  // assertion that every Unicode assignment/property matches Android14 data.
  return character <= 0x10ffff ? int(u_hasBinaryProperty(UChar32(character), property)) : 0;
}
uint32_t case_map(uint32_t character, void *identity, bool upper) {
  bool ignored;
  if (!locales().explicit_locale(identity, ignored) || character > 0x10ffff) return character;
  return uint32_t(upper ? u_toupper(UChar32(character)) : u_tolower(UChar32(character)));
}
template<class R, class... Args>
ProviderExport adapter(const char *name, R (*function)(Args...)) {
  auto *code = closure([function](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    int failure = 0;
    if constexpr (std::is_void_v<R>) {
      try { std::apply(function, args); failure = errno; }
      catch (const std::bad_alloc &) { failure = ENOMEM; }
    } else {
      R result{};
      try { result = std::apply(function, args); failure = errno; }
      catch (const std::bad_alloc &) { failure = ENOMEM; }
      return_value(capture, result);
    }
    if (failure) *guest_errno_location() = errno_to_guest(failure);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function, "locale/android-opaque", false};
}
}
bool current_locale_utf8() { return locales().current(); }
bool locale_is_utf8(const void *identity, bool &value) { return locales().explicit_locale(identity, value); }
size_t locale_strftime(char *output, size_t size, const char *format,
                       const std::tm &time, const void *identity) {
  auto host = identity ? locales().host(identity) : locales().current_host();
  if (!host || !narrow(format)) return 0;
  if ((!output && size) || size > string_limit) { errno = size > string_limit ? EOVERFLOW : EFAULT; return 0; }
  return ::strftime_l(output, size, format, &time, host);
}
std::vector<ProviderExport> locale_exports() {
  (void)locales();
  std::vector<ProviderExport> result;
  auto add = [&](const char *name, auto function) { result.push_back(adapter(name, function)); };
  add("setlocale", +[](int c, const char *n) { return locales().set(c, n); });
  add("newlocale", +[](int m, const char *n, void *b) { return locales().make(m, n, b); });
  add("duplocale", +[](void *l) { return locales().duplicate(l); });
  add("uselocale", +[](void *l) { return locales().use(l); });
  add("freelocale", +[](void *l) { locales().free(l); });
  add("localeconv", +[]() { return locales().conventions(); });
  add("strcoll_l", &collate); add("strxfrm_l", &transform);
  add("wcscoll_l", &wide_collate); add("wcsxfrm_l", &wide_transform);
  add("strcoll", +[](const char *a, const char *b) { return collate(a, b, selection.identity); });
  add("strxfrm", +[](char *a, const char *b, size_t n) { return transform(a, b, n, selection.identity); });
  add("wcscoll", +[](const uint32_t *a, const uint32_t *b) { return wide_collate(a, b, selection.identity); });
  add("wcsxfrm", +[](uint32_t *a, const uint32_t *b, size_t n) { return wide_transform(a, b, n, selection.identity); });
#define CLASSIFY(NAME, PROPERTY) add(#NAME "_l", +[](uint32_t c, void *l) -> int { return classify(c, l, PROPERTY); });
  CLASSIFY(iswalnum, UCHAR_POSIX_ALNUM) CLASSIFY(iswalpha, UCHAR_ALPHABETIC)
  CLASSIFY(iswblank, UCHAR_POSIX_BLANK) CLASSIFY(iswgraph, UCHAR_POSIX_GRAPH)
  CLASSIFY(iswlower, UCHAR_LOWERCASE) CLASSIFY(iswprint, UCHAR_POSIX_PRINT)
  CLASSIFY(iswspace, UCHAR_WHITE_SPACE) CLASSIFY(iswupper, UCHAR_UPPERCASE)
  CLASSIFY(iswxdigit, UCHAR_POSIX_XDIGIT)
#undef CLASSIFY
  add("iswcntrl_l", +[](uint32_t c, void *l) -> int { bool ignored; return locales().explicit_locale(l, ignored) && c <= 0x10ffff && u_charType(UChar32(c)) == U_CONTROL_CHAR; });
  add("iswdigit_l", +[](uint32_t c, void *l) -> int { bool ignored; return locales().explicit_locale(l, ignored) && c <= 0x10ffff && u_isdigit(UChar32(c)); });
  add("iswpunct_l", +[](uint32_t c, void *l) -> int { bool ignored; return locales().explicit_locale(l, ignored) && c <= 0x10ffff && u_ispunct(UChar32(c)); });
  add("towlower_l", +[](uint32_t c, void *l) { return case_map(c, l, false); });
  add("towupper_l", +[](uint32_t c, void *l) { return case_map(c, l, true); });
  add("strtoll_l", +[](const char *s, char **e, int b, void *l) -> long long { auto n = locales().host(l); return n && narrow(s) ? ::strtoll_l(s, e, b, n) : 0; });
  add("strtoull_l", +[](const char *s, char **e, int b, void *l) -> unsigned long long { auto n = locales().host(l); return n && narrow(s) ? ::strtoull_l(s, e, b, n) : 0; });
  add("strtol_l", +[](const char *s, char **e, int b, void *l) -> long { auto n = locales().host(l); return n && narrow(s) ? ::strtol_l(s, e, b, n) : 0; });
  add("strtoul_l", +[](const char *s, char **e, int b, void *l) -> unsigned long { auto n = locales().host(l); return n && narrow(s) ? ::strtoul_l(s, e, b, n) : 0; });
  add("strtod_l", +[](const char *s, char **e, void *l) -> double { auto n = locales().host(l); return n && narrow(s) ? ::strtod_l(s, e, n) : 0; });
  add("strtof_l", +[](const char *s, char **e, void *l) -> float { auto n = locales().host(l); return n && narrow(s) ? ::strtof_l(s, e, n) : 0; });
  return result;
}
}
