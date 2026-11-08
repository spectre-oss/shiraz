// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/wideformat.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <locale.h>
#include <string>
#include <vector>
#include <xlocale.h>

namespace shiraz::guest {
namespace {
constexpr size_t limit = size_t(1) << 18, byte_limit = size_t(1) << 20;
using Text = std::vector<uint32_t>;
[[noreturn]] void fail(int error, const char *message) { throw GuestFormatError(message, error); }
void valid(uint32_t character) {
  if (character > 0x10ffff || (character >= 0xd800 && character <= 0xdfff)) fail(EILSEQ, "wide output is not Unicode");
}
void append(Text &out, uint32_t character) {
  valid(character);
  if (out.size() == limit) fail(EOVERFLOW, "wide formatted output limit");
  out.push_back(character);
}
void ascii(Text &out, const std::string &text) {
  if (text.size() > limit - out.size()) fail(EOVERFLOW, "wide formatted output limit");
  for (unsigned char c : text) {
    if (c >= 128) fail(EILSEQ, "non-ASCII private-C numeric formatting");
    out.push_back(c);
  }
}
uint32_t format_character(const uint32_t *format, size_t index) {
  if (index >= limit) fail(EOVERFLOW, "wide format input limit");
  return format[index];
}
Text wide_string(const uint32_t *text, int precision) {
  static const uint32_t null_text[] = {'(', 'n', 'u', 'l', 'l', ')', 0};
  if (!text) text = null_text;
  Text value;
  size_t maximum = precision < 0 ? limit : size_t(precision);
  for (size_t i = 0; i < maximum; ++i) {
    if (!text[i]) return value;
    append(value, text[i]);
  }
  if (precision < 0) fail(EOVERFLOW, "wide string argument limit");
  return value;
}
Text narrow_string(const char *text, int precision) {
  if (!text) text = "(null)";
  Text value;
  size_t index = 0, maximum = precision < 0 ? limit : size_t(precision);
  while (value.size() < maximum) {
    if (index == byte_limit) fail(EOVERFLOW, "multibyte wide string limit");
    uint8_t first = uint8_t(text[index]);
    if (!first) return value;
    if (first < 128) { append(value, first); ++index; continue; }
    // Android printf_common.h's precision scan passes MB_CUR_MAX to mbrlen.
    // Its C locale reports1 even though unbounded conversion remains UTF8.
    if (precision >= 0 && !current_locale_utf8()) fail(EILSEQ, "C precision scan cannot consume multibyte character");
    unsigned bytes;
    uint32_t character, minimum;
    if ((first & 0xe0) == 0xc0) { bytes = 2; character = first & 31; minimum = 0x80; }
    else if ((first & 0xf0) == 0xe0) { bytes = 3; character = first & 15; minimum = 0x800; }
    else if ((first & 0xf8) == 0xf0) { bytes = 4; character = first & 7; minimum = 0x10000; }
    else fail(EILSEQ, "invalid UTF8 wide string");
    if (bytes > byte_limit - index) fail(EOVERFLOW, "multibyte wide string limit");
    for (unsigned i = 1; i < bytes; ++i) {
      uint8_t byte = uint8_t(text[index + i]);
      if ((byte & 0xc0) != 0x80) fail(EILSEQ, "incomplete/invalid UTF8 wide string");
      character = (character << 6) | (byte & 63);
    }
    if (character < minimum) fail(EILSEQ, "overlong UTF8 wide string");
    append(value, character); index += bytes;
  }
  if (precision < 0) fail(EOVERFLOW, "wide string argument limit");
  return value;
}
void padded(Text &out, const Text &text, int width, bool left) {
  size_t padding = size_t(width) > text.size() ? size_t(width) - text.size() : 0;
  if (text.size() > limit - out.size() || padding > limit - out.size() - text.size()) fail(EOVERFLOW, "wide formatted output limit");
  if (!left) out.insert(out.end(), padding, ' ');
  out.insert(out.end(), text.begin(), text.end());
  if (left) out.insert(out.end(), padding, ' ');
}
std::string integer(uint64_t magnitude, bool negative, bool signed_value,
                    unsigned radix, bool upper, bool pointer, int width, int precision,
                    bool alternate, bool zero, bool left, bool plus, bool space) {
  std::string digits;
  const char *alphabet = upper ? "0123456789ABCDEF" : "0123456789abcdef";
  uint64_t original = magnitude;
  if (magnitude || precision != 0) do { digits += alphabet[magnitude % radix]; magnitude /= radix; } while (magnitude);
  std::reverse(digits.begin(), digits.end());
  if (precision > 0 && size_t(precision) > digits.size()) digits.insert(0, size_t(precision) - digits.size(), '0');
  std::string prefix;
  if (signed_value) { if (negative) prefix = "-"; else if (plus) prefix = "+"; else if (space) prefix = " "; }
  if (pointer || (alternate && original && (radix == 16 || radix == 2))) prefix += radix == 2 ? (upper ? "0B" : "0b") : (upper ? "0X" : "0x");
  if (alternate && radix == 8 && (digits.empty() || digits[0] != '0')) digits.insert(0, 1, '0');
  size_t padding = size_t(width) > prefix.size() + digits.size() ? size_t(width) - prefix.size() - digits.size() : 0;
  if (padding + prefix.size() + digits.size() > limit) fail(EOVERFLOW, "wide integer output limit");
  if (left) return prefix + digits + std::string(padding, ' ');
  if (zero && precision < 0) return prefix + std::string(padding, '0') + digits;
  return std::string(padding, ' ') + prefix + digits;
}
locale_t numeric_locale() {
  static locale_t locale = ::newlocale(LC_ALL_MASK, "C", nullptr);
  if (!locale) fail(ENOENT, "host C numeric locale unavailable");
  return locale;
}
std::string real(double value, char conversion, const std::string &flags, int width, int precision) {
  std::string spec = "%" + flags;
  if (width) spec += std::to_string(width);
  if (precision >= 0) spec += "." + std::to_string(precision);
  spec += conversion;
  int size = ::snprintf_l(nullptr, 0, numeric_locale(), spec.c_str(), value);
  if (size < 0) fail(errno ? errno : EILSEQ, "wide numeric formatting failed");
  if (size_t(size) > limit) fail(EOVERFLOW, "wide floating output limit");
  std::vector<char> output(size_t(size) + 1);
  if (::snprintf_l(output.data(), output.size(), numeric_locale(), spec.c_str(), value) != size)
    fail(errno ? errno : EIO, "wide numeric formatting changed");
  return {output.data(), size_t(size)};
}
Text render(const uint32_t *format, const std::function<uint64_t(ArgumentType)> &next) {
  if (!format) fail(EFAULT, "null wide format");
  Text output;
  size_t cursor = 0;
  unsigned conversions = 0;
  auto at = [&] { return format_character(format, cursor); };
  for (;;) {
    uint32_t current = at();
    if (!current) break;
    if (current != '%') { append(output, current); ++cursor; continue; }
    ++cursor;
    if (at() == '%') { append(output, '%'); ++cursor; continue; }
    if (++conversions > 4096) fail(EOVERFLOW, "wide conversion count limit");
    bool left = false, zero = false, alternate = false, plus = false, space = false;
    std::string flags;
    while (at() && at() < 128 && std::strchr("-+ #0'", int(at()))) {
      char flag = char(at()); ++cursor;
      left |= flag == '-'; zero |= flag == '0'; alternate |= flag == '#'; plus |= flag == '+'; space |= flag == ' ';
      // Android14 explicitly ignores grouping, so never use host grouping.
      if (flag != '\'' && flags.find(flag) == std::string::npos) flags += flag;
    }
    auto field = [&](bool precision) -> int {
      if (at() == '*') {
        ++cursor;
        int value = int32_t(next(ArgumentType::Int32));
        if (precision && value < 0) return -1;
        if (value < -int(limit) || value > int(limit)) fail(EOVERFLOW, "wide field limit");
        if (value < 0) { left = true; if (flags.find('-') == std::string::npos) flags += '-'; value = -value; }
        return value;
      }
      unsigned value = 0;
      while (at() >= '0' && at() <= '9') {
        unsigned digit = at() - '0'; ++cursor;
        if (value > (limit - digit) / 10) fail(EOVERFLOW, "wide field limit");
        value = value * 10 + digit;
      }
      if (at() == '$') fail(ENOTSUP, "positional wide format unsupported");
      return int(value);
    };
    int width = field(false), precision = -1;
    if (at() == '.') { ++cursor; precision = field(true); }
    std::string length;
    if (at() && at() < 128 && std::strchr("hljztLq", int(at()))) {
      length += char(at()); ++cursor;
      if ((length == "h" || length == "l") && at() == uint32_t(length[0])) { length += char(at()); ++cursor; }
    }
    uint32_t conversion = at();
    if (!conversion) fail(EINVAL, "truncated wide format");
    ++cursor;
    if (conversion == 'C' || conversion == 'S') { if (!length.empty() && length != "l") fail(ENOTSUP, "wide character/string length unsupported"); length = "l"; conversion += 'a' - 'A'; }
    if (conversion == 'c' || conversion == 's') {
      if (!length.empty() && length != "l") fail(ENOTSUP, "wide character/string length unsupported");
      Text text;
      if (conversion == 's') {
        auto address = next(ArgumentType::Int64);
        text = length == "l" ? wide_string(reinterpret_cast<const uint32_t *>(address), precision)
                             : narrow_string(reinterpret_cast<const char *>(address), precision);
      } else {
        uint32_t character = uint32_t(next(ArgumentType::Int32));
        if (length.empty() && character >= 128) fail(EILSEQ, "wide %c requires a single-byte character");
        append(text, character);
      }
      padded(output, text, width, left);
    } else if (conversion < 128 && std::strchr("diouxXbBpDUO", int(conversion))) {
      if (conversion == 'D' || conversion == 'U' || conversion == 'O') { length = "l"; conversion += 'a' - 'A'; }
      bool pointer = conversion == 'p', signed_value = conversion == 'd' || conversion == 'i';
      bool wide_integer = pointer || length == "l" || length == "ll" || length == "j" || length == "z" || length == "t" || length == "q";
      if (!wide_integer && !length.empty() && length != "h" && length != "hh") fail(ENOTSUP, "wide integer length unsupported");
      if (pointer && !length.empty()) fail(ENOTSUP, "wide pointer length unsupported");
      uint64_t value = next(wide_integer ? ArgumentType::Int64 : ArgumentType::Int32);
      if (!wide_integer) {
        if (length == "hh") value = signed_value ? uint64_t(int64_t(int8_t(value))) : uint8_t(value);
        else if (length == "h") value = signed_value ? uint64_t(int64_t(int16_t(value))) : uint16_t(value);
        else value = signed_value ? uint64_t(int64_t(int32_t(value))) : uint32_t(value);
      }
      bool negative = signed_value && int64_t(value) < 0;
      if (negative) value = ~value + 1;
      unsigned radix = conversion == 'o' ? 8 : conversion == 'x' || conversion == 'X' || pointer ? 16 : conversion == 'b' || conversion == 'B' ? 2 : 10;
      ascii(output, integer(value, negative, signed_value, radix, conversion == 'X' || conversion == 'B', pointer,
                            width, precision, alternate, zero, left, plus, space));
    } else if (conversion < 128 && std::strchr("aAeEfFgG", int(conversion))) {
      if (!length.empty() && length != "l") fail(ENOTSUP, "binary128 wide format unsupported");
      uint64_t bits = next(ArgumentType::Double);
      double value; std::memcpy(&value, &bits, sizeof(value));
      ascii(output, real(value, char(conversion), flags, width, precision));
    } else fail(ENOTSUP, "unsupported wide format conversion");
  }
  return output;
}
}
std::vector<ProviderExport> wideformat_exports() {
  std::vector<ProviderExport> result;
  for (bool list : {false, true}) {
    void *entry = closure([list](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      auto *output = cursor.read<uint32_t *>();
      size_t size = cursor.read<size_t>();
      const auto *format = cursor.read<const uint32_t *>();
      HostErrno preserve;
      int value = -1, failure = 0;
      try {
        if (!size) fail(EINVAL, "wide formatted buffer size is zero");
        if (!output) fail(EFAULT, "null wide formatted buffer");
        Text text;
        if (list) {
          const auto *arguments = cursor.read<const GuestVaList *>();
          if (!arguments) fail(EFAULT, "null wide va_list");
          VaListCursor reader(*arguments);
          text = render(format, [&](ArgumentType type) { return reader.next(type); });
        } else text = render(format, [&](ArgumentType type) {
          return cursor.next(type == ArgumentType::Int32 ? 4 : 8, type == ArgumentType::Double);
        });
        // Pinned Android vswprintf encodes then mbsrtowcs-decodes the result;
        // an embedded NUL ends copied output, but ret counts all formatted chars.
        size_t visible = size_t(std::find(text.begin(), text.end(), 0) - text.begin());
        size_t copied = std::min(visible, size - 1);
        if (copied) std::memcpy(output, text.data(), copied * sizeof(uint32_t));
        output[copied] = 0;
        if (visible >= size) failure = EOVERFLOW;
        else value = int(text.size());
      } catch (const GuestFormatError &error) { failure = error.error; }
        catch (const std::invalid_argument &) { failure = EINVAL; }
        catch (const std::bad_alloc &) { failure = ENOMEM; }
      if (failure) *guest_errno_location() = errno_to_guest(failure);
      return_value(capture, value);
    });
    result.push_back({list ? "vswprintf" : "swprintf", "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
                      "wideformat/unsigned32-aapcs", false});
  }
  return result;
}
}
