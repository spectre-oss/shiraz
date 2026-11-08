// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/scan.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include <algorithm>
#include <array>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <functional>
#include <new>
#include <string>
#include <xlocale.h>

namespace shiraz::guest {
namespace {
constexpr size_t input_bound = 16u << 20, format_bound = 65536, width_bound = 1u << 20;
static_assert(sizeof(short) == 2 && sizeof(int) == 4 && sizeof(long) == 8 &&
              sizeof(long long) == 8 && sizeof(void *) == 8 &&
              sizeof(float) == 4 && sizeof(double) == 8,
              "typed scanf bridge requires Darwin arm64 LP64 scalar widths");
struct ScanError { int error; };
bool space(uint8_t value) {
  return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\v' || value == '\f';
}
enum class Length { None, Byte, Short, Long, LongLong, Max, Size, Difference, Quad };
template <typename Value>
void store(void *destination, Value value) { std::memcpy(destination, &value, sizeof(value)); }
void count_store(void *destination, Length length, size_t value) {
  switch (length) {
  case Length::Byte: store(destination, int8_t(value)); break;
  case Length::Short: store(destination, int16_t(value)); break;
  case Length::None: store(destination, int32_t(value)); break;
  case Length::Quad: throw ScanError{ENOTSUP};
  default: store(destination, int64_t(value)); break;
  }
}
struct Conversion { bool matched; size_t consumed; int error = 0; };
template <typename Value>
Conversion native_number(const char *input, locale_t locale, const std::string &format,
                         void *destination, bool suppress) {
  int consumed = -1;
  if (suppress) {
    std::string suppressed = format;
    suppressed.insert(1, "*");
    errno = 0;
    (void)::sscanf_l(input, locale, suppressed.c_str(), &consumed);
    int error = errno;
    return {consumed >= 0, consumed >= 0 ? size_t(consumed) : 0, error};
  }
  Value value{};
  errno = 0;
  int result = ::sscanf_l(input, locale, format.c_str(), &value, &consumed);
  int error = errno;
  if (result != 1 || consumed < 0) return {false, 0, error};
  store(destination, value);
  return {true, size_t(consumed), error};
}
Conversion number(const char *input, locale_t locale, char kind, Length length,
                  size_t width, void *destination, bool suppress) {
  std::string format = "%";
  if (width) format += std::to_string(width);
  bool floating = std::strchr("aAeEfFgG", kind);
  if (floating) {
    if (length == Length::Quad) throw ScanError{ENOTSUP};
    if (length != Length::None && length != Length::Long) throw ScanError{EINVAL};
    if (length == Length::Long) format += 'l';
    format += kind; format += "%n";
    if (length == Length::Long) return native_number<double>(input, locale, format, destination, suppress);
    return native_number<float>(input, locale, format, destination, suppress);
  }
  if (kind == 'p') {
    if (length != Length::None) throw ScanError{EINVAL};
    format += "p%n";
    return native_number<void *>(input, locale, format, destination, suppress);
  }
  switch (length) {
  case Length::Byte: format += "hh"; break;
  case Length::Short: format += 'h'; break;
  case Length::Long: format += 'l'; break;
  case Length::LongLong: case Length::Max: case Length::Size: case Length::Difference:
    format += "ll"; break;
  case Length::Quad: throw ScanError{ENOTSUP};
  case Length::None: break;
  }
  format += kind; format += "%n";
  bool sign = kind == 'd' || kind == 'i';
  switch (length) {
  case Length::Byte:
    return sign ? native_number<signed char>(input, locale, format, destination, suppress)
                : native_number<unsigned char>(input, locale, format, destination, suppress);
  case Length::Short:
    return sign ? native_number<short>(input, locale, format, destination, suppress)
                : native_number<unsigned short>(input, locale, format, destination, suppress);
  case Length::None:
    return sign ? native_number<int>(input, locale, format, destination, suppress)
                : native_number<unsigned>(input, locale, format, destination, suppress);
  case Length::Long:
    return sign ? native_number<long>(input, locale, format, destination, suppress)
                : native_number<unsigned long>(input, locale, format, destination, suppress);
  default:
    return sign ? native_number<long long>(input, locale, format, destination, suppress)
                : native_number<unsigned long long>(input, locale, format, destination, suppress);
  }
}
int scan(const char *input, const char *format, const std::function<void *()> &next, int &failure_code) {
  int assigned = 0;
  try {
    if (!input || !format) throw ScanError{EFAULT};
    size_t input_size = ::strnlen(input, input_bound + 1);
    size_t format_size = ::strnlen(format, format_bound + 1);
    if (input_size > input_bound || format_size > format_bound) throw ScanError{E2BIG};
    // A real permanent C locale isolates scanf from mutable host process or
    // guest locale handles; Android's numeric scanf grammar uses '.' decimal.
    static locale_t locale = [] {
      int previous = errno;
      locale_t value = ::newlocale(LC_ALL_MASK, "C", nullptr);
      if (value) errno = previous;
      return value;
    }();
    if (!locale) throw ScanError{ENOMEM};
    size_t consumed = 0, position = 0, conversions = 0;
    auto input_failure = [&] { return assigned ? assigned : EOF; };
    while (position < format_size) {
      uint8_t byte = uint8_t(format[position++]);
      if (space(byte)) {
        while (position < format_size && space(uint8_t(format[position]))) ++position;
        while (consumed < input_size && space(uint8_t(input[consumed]))) ++consumed;
        continue;
      }
      if (byte != '%') {
        if (consumed == input_size) return input_failure();
        if (uint8_t(input[consumed]) != byte) return assigned;
        ++consumed; continue;
      }
      if (++conversions > 4096) throw ScanError{E2BIG};
      if (position < format_size && format[position] == '%') {
        ++position;
        if (consumed == input_size) return input_failure();
        if (input[consumed] != '%') return assigned;
        ++consumed; continue;
      }
      bool suppress = position < format_size && format[position] == '*';
      if (suppress) ++position;
      size_t width = 0;
      bool has_width = false;
      while (position < format_size && format[position] >= '0' && format[position] <= '9') {
        has_width = true;
        unsigned digit = unsigned(format[position++] - '0');
        if (width > (width_bound - digit) / 10) throw ScanError{E2BIG};
        width = width * 10 + digit;
      }
      if (has_width && !width) throw ScanError{EINVAL};
      Length length = Length::None;
      if (position < format_size) {
        switch (format[position]) {
        case 'h': ++position; length = Length::Short;
          if (position < format_size && format[position] == 'h') { ++position; length = Length::Byte; } break;
        case 'l': ++position; length = Length::Long;
          if (position < format_size && format[position] == 'l') { ++position; length = Length::LongLong; } break;
        case 'j': ++position; length = Length::Max; break;
        case 'z': ++position; length = Length::Size; break;
        case 't': ++position; length = Length::Difference; break;
        case 'L': ++position; length = Length::Quad; break;
        default: break;
        }
      }
      if (position == format_size) throw ScanError{EINVAL};
      char kind = format[position++];
      if (kind == '$' || kind == 'm' || kind == 'b' || kind == 'S' || kind == 'C') throw ScanError{ENOTSUP};
      if (length == Length::Quad || ((kind == 's' || kind == 'c' || kind == '[') && length == Length::Long))
        throw ScanError{ENOTSUP};
      std::array<bool, 256> set{};
      if (kind == '[') {
        bool inverse = position < format_size && format[position] == '^';
        if (inverse) ++position;
        bool first = true, closed = false;
        int previous = -1;
        while (position < format_size) {
          uint8_t member = uint8_t(format[position++]);
          if (member == ']' && !first) { closed = true; break; }
          first = false;
          if (member == '-' && previous >= 0 && position < format_size && format[position] != ']') {
            uint8_t finish = uint8_t(format[position++]);
            if (finish < previous) throw ScanError{EINVAL};
            for (unsigned i = unsigned(previous); i <= finish; ++i) set[i] = true;
            previous = finish;
          } else { set[member] = true; previous = member; }
        }
        if (!closed) throw ScanError{EINVAL};
        if (inverse) for (auto &entry : set) entry = !entry;
        set[0] = false;
      }
      if (kind == 'n') {
        if (!suppress) {
          void *destination = next();
          if (!destination) throw ScanError{EFAULT};
          count_store(destination, length, consumed);
        }
        continue;
      }
      bool text = kind == 's' || kind == 'c' || kind == '[';
      if (text && length != Length::None) throw ScanError{EINVAL};
      if (!text && !std::strchr("diouxXaAeEfFgGp", kind)) throw ScanError{EINVAL};
      if (kind != 'c' && kind != '[')
        while (consumed < input_size && space(uint8_t(input[consumed]))) ++consumed;
      if (consumed == input_size) return input_failure();
      void *destination = nullptr;
      if (!suppress) {
        destination = next();
        if (!destination) throw ScanError{EFAULT};
      }
      if (text) {
        size_t available = input_size - consumed;
        size_t limit = width ? width : kind == 'c' ? 1 : available;
        limit = std::min(limit, available);
        size_t count = 0;
        while (count < limit && (kind == 'c' || (kind == 's' ? !space(uint8_t(input[consumed + count])) : set[uint8_t(input[consumed + count])]))) ++count;
        if (!count) return assigned;
        if (!suppress) {
          std::memcpy(destination, input + consumed, count);
          if (kind != 'c') static_cast<char *>(destination)[count] = 0;
          ++assigned;
        }
        consumed += count;
      } else {
        auto result = number(input + consumed, locale, kind, length, width, destination, suppress);
        if (result.error) failure_code = result.error;
        if (!result.matched) return assigned;
        if (!result.consumed || result.consumed > input_size - consumed) throw ScanError{EIO};
        consumed += result.consumed;
        if (!suppress) ++assigned;
      }
    }
    return assigned;
  } catch (const ScanError &error) { failure_code = error.error; }
    catch (const std::bad_alloc &) { failure_code = ENOMEM; }
    catch (const std::invalid_argument &) { failure_code = EINVAL; }
  return assigned ? assigned : EOF;
}
ProviderExport export_scan(const char *name, bool list) {
  void *code = closure([list](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const char *input = cursor.read<const char *>();
    const char *format = cursor.read<const char *>();
    HostErrno preserve;
    int result = EOF;
    int failure = 0;
    try {
      if (list) {
        const auto *arguments = cursor.read<const GuestVaList *>();
        if (!arguments) throw ScanError{EFAULT};
        GuestVaList copy; std::memcpy(&copy, arguments, sizeof(copy));
        VaListCursor reader(copy);
        result = scan(input, format, [&] { return reinterpret_cast<void *>(reader.next(ArgumentType::Int64)); }, failure);
      } else {
        result = scan(input, format, [&] { return cursor.read<void *>(); }, failure);
      }
    } catch (const ScanError &error) { failure = error.error; }
      catch (const std::invalid_argument &) { failure = EINVAL; }
      catch (const std::bad_alloc &) { failure = ENOMEM; }
    if (failure) *guest_errno_location() = errno_to_guest(failure);
    return_value(capture, result);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
          "scanf/aapcs64-output-pointers/c-locale", false};
}
} // namespace
std::vector<ProviderExport> scan_exports() {
  return {export_scan("sscanf", false), export_scan("vsscanf", true)};
}
} // namespace shiraz::guest
