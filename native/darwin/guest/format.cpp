// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/format.hpp"
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include <stdexcept>
#include <vector>
namespace shiraz::guest {
namespace {
constexpr size_t limit = 1u << 20;
[[noreturn]] void unsupported(const char *message, int error = ENOTSUP) { throw GuestFormatError(message, error); }
}
std::string format_guest(const char *format,
                         const std::function<uint64_t(ArgumentType)> &next) {
  if (!format) unsupported("null guest format", EFAULT);
  if (strnlen(format, limit) == limit) unsupported("unbounded guest format", EOVERFLOW);
  std::string output;
  unsigned conversions = 0;
  auto integer = [&] { return int32_t(next(ArgumentType::Int32)); };
  for (size_t cursor = 0; format[cursor];) {
    if (format[cursor] != '%') {
      if (output.size() == limit) unsupported("guest formatted output limit", EOVERFLOW);
      output += format[cursor++]; continue;
    }
    size_t start = cursor++;
    if (format[cursor] == '%') {
      if (output.size() == limit) unsupported("guest formatted output limit", EOVERFLOW);
      output += '%'; ++cursor; continue;
    }
    if (++conversions > 4096) unsupported("guest format conversion limit", EOVERFLOW);
    while (format[cursor] && std::strchr("-+ #0'", format[cursor])) ++cursor;
    int stars[2]{}; unsigned star_count = 0;
    int precision = -1;
    auto field = [&](bool is_precision) {
      if (format[cursor] == '*') {
        int value = integer();
        if ((!is_precision && value < -int(limit)) || value > int(limit)) unsupported("guest format field limit", EOVERFLOW);
        if (is_precision) precision = value;
        stars[star_count++] = value; ++cursor;
      } else {
        unsigned value = 0;
        while (format[cursor] >= '0' && format[cursor] <= '9') {
          unsigned digit = unsigned(format[cursor++] - '0');
          if (value > (limit - digit) / 10) unsupported("guest format field limit", EOVERFLOW);
          value = value * 10 + digit;
        }
        if (is_precision) precision = int(value);
      }
      if (format[cursor] == '$') unsupported("positional guest format unsupported");
    };
    field(false);
    if (format[cursor] == '.') { ++cursor; field(true); }
    std::string length;
    if (format[cursor] && std::strchr("hljztL", format[cursor])) {
      length += format[cursor++];
      if ((length == "h" || length == "l") && format[cursor] == length[0]) length += format[cursor++];
    }
    char conversion = format[cursor];
    if (!conversion) unsupported("truncated guest format", EINVAL);
    ++cursor;
    std::string spec(format + start, cursor - start);
    auto emit = [&](auto value) {
      auto print = [&](char *buffer, size_t size) {
        if (star_count == 2) return std::snprintf(buffer, size, spec.c_str(), stars[0], stars[1], value);
        if (star_count == 1) return std::snprintf(buffer, size, spec.c_str(), stars[0], value);
        return std::snprintf(buffer, size, spec.c_str(), value);
      };
      int size = print(nullptr, 0);
      if (size < 0) unsupported("guest format conversion failed", errno ? errno : EILSEQ);
      if (size_t(size) > limit - output.size()) unsupported("guest formatted output limit", EOVERFLOW);
      std::vector<char> buffer(size_t(size) + 1);
      if (print(buffer.data(), buffer.size()) != size) unsupported("guest format changed during conversion", EIO);
      output.append(buffer.data(), size_t(size));
    };
    if (std::strchr("diouxX", conversion)) {
      bool sign = conversion == 'd' || conversion == 'i';
      if (length.empty() || length == "h" || length == "hh") {
        uint32_t value = uint32_t(next(ArgumentType::Int32));
        if (sign) emit(int32_t(value)); else emit(value);
      } else if (length == "ll" || length == "j") {
        uint64_t value = next(ArgumentType::Int64);
        if (sign) emit(static_cast<long long>(value)); else emit(static_cast<unsigned long long>(value));
      } else if (length == "l" || length == "z" || length == "t") {
        uint64_t value = next(ArgumentType::Int64);
        if (sign) emit(static_cast<long>(value)); else emit(static_cast<unsigned long>(value));
      } else unsupported("invalid guest integer format length");
    } else if (std::strchr("aAeEfFgG", conversion)) {
      if (!length.empty() && length != "l") unsupported("binary128 guest format unsupported");
      uint64_t bits = next(ArgumentType::Double); double value; std::memcpy(&value, &bits, 8); emit(value);
    } else if (conversion == 'c') {
      if (!length.empty() && length != "l") unsupported("invalid guest character length");
      if (length == "l") emit(uint32_t(next(ArgumentType::Int32))); else emit(integer());
    } else if (conversion == 's' && length.empty()) {
      const char *value = reinterpret_cast<const char *>(next(ArgumentType::Int64));
      if (!value) value = "(null)";
      size_t bound = precision < 0 ? limit : size_t(precision);
      if (precision < 0 && strnlen(value, bound) == bound) unsupported("unbounded guest format string argument", EOVERFLOW);
      emit(value);
    } else if (conversion == 's' && length == "l") {
      static_assert(sizeof(wchar_t) == 4);
      const auto *value = reinterpret_cast<const wchar_t *>(next(ArgumentType::Int64));
      if (!value) value = L"(null)";
      // Each wide character contributes at most one visible character; the
      // host conversion still applies byte precision and its current locale.
      if (precision < 0 && wcsnlen(value, limit) == limit)
        unsupported("unbounded guest wide string argument", EOVERFLOW);
      emit(value);
    } else if (conversion == 'p' && length.empty()) {
      emit(reinterpret_cast<void *>(next(ArgumentType::Int64)));
    } else unsupported("unsupported guest format conversion");
  }
  return output;
}
}
