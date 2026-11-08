// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/quad.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include <algorithm>
#include <cfenv>
#include <climits>
#include <cstring>
#include <new>
#include <map>
#include <mutex>
#include <string>
#include <unicode/uchar.h>

namespace shiraz::guest {
namespace {
constexpr size_t input_limit = 65536, digit_limit = 16384;
constexpr unsigned bit_limit = 262144;
constexpr int minimum_exponent = -16382, maximum_exponent = 16383;
constexpr int subnormal_quantum = -16494;
struct Limit {};
class Big {
  std::vector<uint32_t> limbs_;
  void trim() { while (!limbs_.empty() && !limbs_.back()) limbs_.pop_back(); }
public:
  Big() = default;
  explicit Big(uint32_t value) { if (value) limbs_.push_back(value); }
  bool zero() const { return limbs_.empty(); }
  unsigned bits() const {
    return zero() ? 0 : unsigned((limbs_.size() - 1) * 32 + 32 - __builtin_clz(limbs_.back()));
  }
  void multiply(uint32_t value) {
    if (!value) { limbs_.clear(); return; }
    uint64_t carry = 0;
    for (auto &limb : limbs_) { uint64_t product = uint64_t(limb) * value + carry; limb = uint32_t(product); carry = product >> 32; }
    if (carry) limbs_.push_back(uint32_t(carry));
    if (bits() > bit_limit) throw Limit{};
  }
  void add(uint32_t value) {
    uint64_t carry = value;
    for (size_t i = 0; carry && i < limbs_.size(); ++i) { carry += limbs_[i]; limbs_[i] = uint32_t(carry); carry >>= 32; }
    if (carry) limbs_.push_back(uint32_t(carry));
    if (bits() > bit_limit) throw Limit{};
  }
  void add(const Big &other) {
    size_t count = std::max(limbs_.size(), other.limbs_.size());
    limbs_.resize(count, 0);
    uint64_t carry = 0;
    for (size_t i = 0; i < count; ++i) {
      uint64_t value = uint64_t(limbs_[i]) + (i < other.limbs_.size() ? other.limbs_[i] : 0) + carry;
      limbs_[i] = uint32_t(value); carry = value >> 32;
    }
    if (carry) limbs_.push_back(uint32_t(carry));
    if (bits() > bit_limit) throw Limit{};
  }
  void multiply(const Big &other) {
    if (zero() || other.zero()) { limbs_.clear(); return; }
    if (bits() + other.bits() > bit_limit) throw Limit{};
    std::vector<uint32_t> product(limbs_.size() + other.limbs_.size(), 0);
    for (size_t i = 0; i < limbs_.size(); ++i) {
      uint64_t carry = 0;
      for (size_t j = 0; j < other.limbs_.size(); ++j) {
        uint64_t value = uint64_t(limbs_[i]) * other.limbs_[j] + product[i + j] + carry;
        product[i + j] = uint32_t(value); carry = value >> 32;
      }
      product[i + other.limbs_.size()] = uint32_t(carry);
    }
    limbs_ = std::move(product); trim();
  }
  // Return whether any discarded bit was set, for directed fixed-point bounds.
  bool shift_right(unsigned count) {
    if (!count || zero()) return false;
    unsigned words = count / 32, remainder = count % 32;
    bool discarded = false;
    for (size_t i = 0; i < std::min<size_t>(words, limbs_.size()); ++i) discarded |= limbs_[i] != 0;
    if (words >= limbs_.size()) { limbs_.clear(); return discarded; }
    if (remainder) discarded |= (limbs_[words] & ((uint32_t(1) << remainder) - 1)) != 0;
    limbs_.erase(limbs_.begin(), limbs_.begin() + words);
    if (remainder) for (size_t i = 0; i < limbs_.size(); ++i)
      limbs_[i] = (limbs_[i] >> remainder) | (i + 1 < limbs_.size() ? limbs_[i + 1] << (32 - remainder) : 0);
    trim(); return discarded;
  }
  uint32_t divide_small(uint32_t divisor) {
    uint64_t remainder = 0;
    for (size_t i = limbs_.size(); i; --i) {
      uint64_t value = (remainder << 32) | limbs_[i - 1];
      limbs_[i - 1] = uint32_t(value / divisor); remainder = value % divisor;
    }
    trim(); return uint32_t(remainder);
  }
  void set_bit(unsigned bit) {
    if (bit >= bit_limit) throw Limit{};
    if (limbs_.size() <= bit / 32) limbs_.resize(bit / 32 + 1, 0);
    limbs_[bit / 32] |= uint32_t(1) << (bit % 32);
  }
  void shift(unsigned count) {
    if (zero() || !count) return;
    if (count > bit_limit || bits() > bit_limit - count) throw Limit{};
    unsigned words = count / 32, remainder = count % 32;
    limbs_.insert(limbs_.begin(), words, 0);
    uint64_t carry = 0;
    for (size_t i = words; i < limbs_.size(); ++i) {
      uint64_t value = (uint64_t(limbs_[i]) << remainder) | carry;
      limbs_[i] = uint32_t(value); carry = value >> 32;
    }
    if (carry) limbs_.push_back(uint32_t(carry));
  }
  int compare(const Big &other) const {
    if (limbs_.size() != other.limbs_.size()) return limbs_.size() < other.limbs_.size() ? -1 : 1;
    for (size_t i = limbs_.size(); i; --i)
      if (limbs_[i - 1] != other.limbs_[i - 1]) return limbs_[i - 1] < other.limbs_[i - 1] ? -1 : 1;
    return 0;
  }
  void subtract(const Big &other) {
    uint64_t borrow = 0;
    for (size_t i = 0; i < limbs_.size(); ++i) {
      uint64_t value = uint64_t(i < other.limbs_.size() ? other.limbs_[i] : 0) + borrow;
      uint64_t original = limbs_[i];
      limbs_[i] = uint32_t(original - value); borrow = original < value;
    }
    if (borrow) throw Limit{};
    trim();
  }
};
using U128 = unsigned __int128;
struct Bits { uint64_t low = 0, high = 0; };
static_assert(sizeof(Bits) == 16 && __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__);
struct Result { Bits bits; size_t consumed = 0; int error = 0; };
struct FloatingEnvironment {
  fenv_t value;
  bool valid = std::fegetenv(&value) == 0;
  ~FloatingEnvironment() { if (valid) std::fesetenv(&value); }
};
uint64_t sign_bit(bool negative) { return negative ? UINT64_C(0x8000000000000000) : 0; }
Bits infinity(bool negative) { return {0, sign_bit(negative) | UINT64_C(0x7fff000000000000)}; }
Bits largest(bool negative) { return {UINT64_MAX, sign_bit(negative) | UINT64_C(0x7ffeffffffffffff)}; }
bool outward(bool negative, int mode) { return (mode == FE_UPWARD && !negative) || (mode == FE_DOWNWARD && negative); }
Bits overflow(bool negative, int mode) { return mode == FE_TONEAREST || outward(negative, mode) ? infinity(negative) : largest(negative); }
Bits tiny(bool negative, int mode) { return {outward(negative, mode) ? 1u : 0u, sign_bit(negative)}; }
int hex_digit(uint32_t c) {
  if (c >= '0' && c <= '9') return int(c - '0');
  if (c >= 'a' && c <= 'f') return int(c - 'a' + 10);
  if (c >= 'A' && c <= 'F') return int(c - 'A' + 10);
  return -1;
}
bool ascii_space(uint32_t c) { return c == ' ' || (c >= '\t' && c <= '\r'); }
uint32_t lower(uint32_t c) { return c >= 'A' && c <= 'Z' ? c + ('a' - 'A') : c; }
template<class Read> bool word(const Read &read, size_t at, const char *text) {
  for (size_t i = 0; text[i]; ++i) if (lower(read(at + i)) != uint32_t(text[i])) return false;
  return true;
}
Big magnitude(const std::string &digits, bool hexadecimal) {
  Big value;
  unsigned group = hexadecimal ? 7 : 9;
  for (size_t i = 0; i < digits.size();) {
    unsigned count = unsigned(std::min<size_t>(group, digits.size() - i));
    uint32_t factor = 1, chunk = 0, radix = hexadecimal ? 16 : 10;
    for (unsigned j = 0; j < count; ++j) { factor *= radix; chunk = chunk * radix + uint32_t(hex_digit(uint8_t(digits[i++]))); }
    value.multiply(factor); value.add(chunk);
  }
  return value;
}
U128 quotient(Big &remainder, const Big &divisor) {
  if (remainder.compare(divisor) < 0) return 0;
  unsigned difference = remainder.bits() - divisor.bits();
  if (difference > 113) throw Limit{};
  U128 value = 0;
  for (int i = int(difference); i >= 0; --i) {
    Big shifted = divisor; shifted.shift(unsigned(i));
    if (remainder.compare(shifted) >= 0) { remainder.subtract(shifted); value |= U128(1) << unsigned(i); }
  }
  return value;
}
Bits rational(Big numerator, Big denominator, int64_t binary_shift, bool negative, int mode, int &error) {
  if (numerator.zero()) return {0, sign_bit(negative)};
  int exponent = int(numerator.bits()) - int(denominator.bits());
  if (exponent >= 0) { Big scaled = denominator; scaled.shift(unsigned(exponent)); if (numerator.compare(scaled) < 0) --exponent; }
  else { Big scaled = numerator; scaled.shift(unsigned(-exponent)); if (scaled.compare(denominator) < 0) --exponent; }
  int64_t actual_exponent = int64_t(exponent) + binary_shift;
  if (actual_exponent > maximum_exponent) { error = ERANGE; return overflow(negative, mode); }
  if (actual_exponent < subnormal_quantum - 1) { error = ERANGE; return tiny(negative, mode); }
  int quantum = actual_exponent >= minimum_exponent ? int(actual_exponent) - 112 : subnormal_quantum;
  int64_t scale = binary_shift - quantum;
  if (scale >= 0) numerator.shift(unsigned(scale)); else denominator.shift(unsigned(-scale));
  Big remainder = numerator;
  U128 significand = quotient(remainder, denominator);
  bool inexact = !remainder.zero();
  bool increment = outward(negative, mode) && inexact;
  if (mode == FE_TONEAREST && inexact) {
    Big twice = remainder; twice.shift(1);
    int relation = twice.compare(denominator);
    increment = relation > 0 || (relation == 0 && bool(significand & 1));
  }
  if (increment) ++significand;
  if (actual_exponent < minimum_exponent) {
    // Tininess is detected after rounding; an exactly representable subnormal
    // needs no ERANGE, whereas any inexact result remaining tiny does.
    if (inexact && significand < (U128(1) << 112)) error = ERANGE;
    if (significand == (U128(1) << 112)) return {0, sign_bit(negative) | (UINT64_C(1) << 48)};
    return {uint64_t(significand), sign_bit(negative) | uint64_t(significand >> 64)};
  }
  if (significand == (U128(1) << 113)) { significand >>= 1; ++actual_exponent; }
  if (actual_exponent > maximum_exponent) { error = ERANGE; return overflow(negative, mode); }
  return {uint64_t(significand), sign_bit(negative) | (uint64_t(actual_exponent + 16383) << 48) |
          (uint64_t(significand >> 64) & UINT64_C(0x0000ffffffffffff))};
}
template<class Read> Result parse(const Read &read, size_t start, int mode) {
  size_t cursor = start;
  bool negative = read(cursor) == '-';
  if (negative || read(cursor) == '+') ++cursor;
  if (word(read, cursor, "inf")) {
    cursor += 3; if (word(read, cursor, "inity")) cursor += 5;
    return {infinity(negative), cursor, 0};
  }
  if (word(read, cursor, "nan")) {
    cursor += 3;
    if (read(cursor) == '(') {
      size_t payload = cursor + 1;
      while ((lower(read(payload)) >= 'a' && lower(read(payload)) <= 'z') ||
             (read(payload) >= '0' && read(payload) <= '9') || read(payload) == '_') ++payload;
      if (read(payload) == ')') cursor = payload + 1;
    }
    return {{0, sign_bit(negative) | UINT64_C(0x7fff800000000000)}, cursor, 0};
  }
  bool hexadecimal = false;
  if (read(cursor) == '0' && lower(read(cursor + 1)) == 'x') {
    size_t after = cursor + 2;
    if (read(after) == '.') ++after;
    if (hex_digit(read(after)) >= 0) { hexadecimal = true; cursor += 2; }
  }
  std::string digits;
  size_t fractional = 0, count = 0;
  bool point = false;
  for (;;) {
    uint32_t c = read(cursor);
    int value = hexadecimal ? hex_digit(c) : c >= '0' && c <= '9' ? int(c - '0') : -1;
    if (value >= 0) {
      digits += char(c); ++count; if (point) ++fractional; ++cursor;
    } else if (c == '.' && !point) { point = true; ++cursor; }
    else break;
  }
  if (!count) return {};
  int64_t exponent = 0;
  if (lower(read(cursor)) == (hexadecimal ? 'p' : 'e')) {
    size_t marker = cursor++, after_sign = cursor;
    bool minus = read(cursor) == '-';
    if (minus || read(cursor) == '+') ++cursor;
    after_sign = cursor;
    while (read(cursor) >= '0' && read(cursor) <= '9') {
      exponent = std::min<int64_t>(1000000, exponent * 10 + int64_t(read(cursor) - '0')); ++cursor;
    }
    if (cursor == after_sign) cursor = marker;
    else if (minus) exponent = -exponent;
  }
  size_t first = digits.find_first_not_of('0');
  if (first == std::string::npos) return {{0, sign_bit(negative)}, cursor, 0};
  digits.erase(0, first);
  size_t zeros = 0;
  while (digits.back() == '0') { digits.pop_back(); ++zeros; }
  exponent += int64_t(zeros) * (hexadecimal ? 4 : 1) - int64_t(fractional) * (hexadecimal ? 4 : 1);
  if (digits.size() > digit_limit) throw Limit{};
  Result result{{}, cursor, 0};
  if (!hexadecimal) {
    int64_t order = int64_t(digits.size()) + exponent - 1;
    if (order > 5000) { result.bits = overflow(negative, mode); result.error = ERANGE; return result; }
    if (order < -5000) { result.bits = tiny(negative, mode); result.error = ERANGE; return result; }
  }
  Big numerator = magnitude(digits, hexadecimal), denominator(1);
  if (!hexadecimal) {
    if (exponent >= 0) for (int64_t i = 0; i < exponent; ++i) numerator.multiply(5);
    else for (int64_t i = 0; i < -exponent; ++i) denominator.multiply(5);
  }
  result.bits = rational(std::move(numerator), std::move(denominator), exponent, negative, mode, result.error);
  return result;
}
Result convert(const void *input, bool wide, int mode) {
  auto raw = [&](size_t index) -> uint32_t {
    if (index >= input_limit) throw Limit{};
    return wide ? static_cast<const uint32_t *>(input)[index] : uint8_t(static_cast<const char *>(input)[index]);
  };
  size_t start = 0;
  while (wide ? (raw(start) <= 0x10ffff && u_hasBinaryProperty(UChar32(raw(start)), UCHAR_WHITE_SPACE)) : ascii_space(raw(start))) ++start;
  auto read = [&](size_t index) -> uint32_t {
    auto character = raw(index);
    if (!wide || index < start || !character) return character;
    // Android14 wcstod.cpp first limits its numeric span to this exact ASCII
    // whitelist before parsefloat/strtold, including its omitted b/c/d hex digits.
    // Git blob75a59f554cb55a1a563cab0024248334503d1d28, SHA256
    // ca5ec7a599b08b35422a4bd09a9ac8d48d4c08f460fbd1430487efccba410a3e.
    // wcstold explicitly instantiates that same wcstod<long double> template:
    // https://raw.githubusercontent.com/aosp-mirror/platform_bionic/android-14.0.0_r1/libc/bionic/wcstod.cpp
    static constexpr char allowed[] = "-+0123456789.xXeEpP()nNaAiIfFtTyY";
    return character < 128 && std::strchr(allowed, int(character)) ? character : 0;
  };
  return parse(read, start, mode);
}
Big divide_big(Big &remainder, const Big &divisor) {
  if (divisor.zero()) throw Limit{};
  Big value;
  if (remainder.compare(divisor) < 0) return value;
  int difference = int(remainder.bits()) - int(divisor.bits());
  for (int bit = difference; bit >= 0; --bit) {
    Big shifted = divisor; shifted.shift(unsigned(bit));
    if (remainder.compare(shifted) >= 0) { remainder.subtract(shifted); value.set_bit(unsigned(bit)); }
  }
  return value;
}
Big fixed_product(const Big &left, const Big &right, unsigned precision, bool upper) {
  Big value = left; value.multiply(right);
  if (value.shift_right(precision) && upper) value.add(1);
  return value;
}
struct Interval { Big lower, upper; };
// For 0<=a/b<=1/3, ln((b+a)/(b-a))=2*sum(y^(2k+1)/(2k+1)).
// Every fixed-point multiply/divide rounds outwards. After n terms, the true
// remaining tail is <= 2*y^(2n+1)/((2n+1)*(1-y*y)) <= 3*y^(2n+1)/(2n+1).
// Thus integer endpoint bounds enclose the exact logarithm, not an estimate.
Interval logarithm_interval(const Big &a, const Big &b, unsigned precision) {
  if (a.zero()) return {};
  Big remainder = a; remainder.shift(precision);
  Big y_lower = divide_big(remainder, b), y_upper = y_lower;
  if (!remainder.zero()) y_upper.add(1);
  Big square_lower = fixed_product(y_lower, y_lower, precision, false);
  Big square_upper = fixed_product(y_upper, y_upper, precision, true);
  Big power_lower = y_lower, power_upper = y_upper, lower_sum, upper_sum;
  for (unsigned k = 0; k < precision + 16; ++k) {
    uint32_t odd = 2 * k + 1;
    Big term_lower = power_lower, term_upper = power_upper;
    term_lower.divide_small(odd);
    if (term_upper.divide_small(odd)) term_upper.add(1);
    lower_sum.add(term_lower); upper_sum.add(term_upper);
    power_lower = fixed_product(power_lower, square_lower, precision, false);
    power_upper = fixed_product(power_upper, square_upper, precision, true);
    if (power_upper.compare(Big(1)) <= 0) {
      lower_sum.multiply(2); upper_sum.multiply(2);
      Big tail = power_upper; tail.multiply(3);
      if (tail.divide_small(odd + 2)) tail.add(1);
      upper_sum.add(tail);
      return {std::move(lower_sum), std::move(upper_sum)};
    }
  }
  throw Limit{};
}
Interval logarithm_two(unsigned precision) {
  static std::mutex mutex;
  static std::map<unsigned, Interval> cache;
  { std::lock_guard<std::mutex> lock(mutex); auto found = cache.find(precision); if (found != cache.end()) return found->second; }
  auto value = logarithm_interval(Big(1), Big(3), precision);
  std::lock_guard<std::mutex> lock(mutex);
  return cache.emplace(precision, std::move(value)).first->second;
}
Big significand_bits(uint64_t low, uint64_t high) {
  Big value(uint32_t(high >> 32));
  value.shift(32); value.add(uint32_t(high));
  value.shift(32); value.add(uint32_t(low >> 32));
  value.shift(32); value.add(uint32_t(low));
  return value;
}
Bits logarithm_endpoint(const Big &numerator, const Big &denominator, int exponent, int mode) {
  Big value = denominator;
  value.multiply(uint32_t(exponent < 0 ? -exponent : exponent));
  bool negative = false;
  if (exponent >= 0) value.add(numerator);
  else if (value.compare(numerator) >= 0) { value.subtract(numerator); negative = !value.zero(); }
  else { Big positive = numerator; positive.subtract(value); value = std::move(positive); }
  int error = 0;
  auto result = rational(std::move(value), denominator, 0, negative, mode, error);
  if (error) throw Limit{}; // Positive finite binary128 inputs cannot yield tiny/huge log2.
  return result;
}
struct MathResult { Bits bits; int error = 0, exceptions = 0; };
MathResult logarithm(Bits input, int mode) {
  unsigned field = unsigned((input.high >> 48) & 0x7fff);
  uint64_t fraction_high = input.high & UINT64_C(0x0000ffffffffffff);
  bool fraction = input.low || fraction_high;
  if (field == 0x7fff && fraction) {
    bool signaling = !(input.high & UINT64_C(0x0000800000000000));
    input.high |= UINT64_C(0x0000800000000000);
    return {input, 0, signaling ? FE_INVALID : 0};
  }
  if (!field && !fraction) return {infinity(true), ERANGE, FE_DIVBYZERO};
  if (input.high >> 63) return {{0, UINT64_C(0x7fff800000000000)}, EDOM, FE_INVALID};
  if (field == 0x7fff) return {input};
  Big mantissa = significand_bits(input.low, fraction_high), one;
  one.set_bit(112);
  int exponent;
  if (field) { mantissa.set_bit(112); exponent = int(field) - 16383; }
  else {
    unsigned highest = mantissa.bits() - 1;
    exponent = int(highest) + subnormal_quantum;
    mantissa.shift(112 - highest);
  }
  if (!mantissa.compare(one)) {
    int error = 0;
    return {rational(Big(uint32_t(exponent < 0 ? -exponent : exponent)), Big(1), 0, exponent < 0, mode, error)};
  }
  Big a = mantissa; a.subtract(one);
  Big b = mantissa; b.add(one);
  // At most8192 fractional bits and8208 series terms per refinement. No
  // approximate answer escapes: matching rounded interval endpoints prove the
  // exact result's rounding. Exhaustion is explicit EOVERFLOW/qNaN.
  for (unsigned precision = 256; precision <= 8192; precision *= 2) {
    auto value = logarithm_interval(a, b, precision), two = logarithm_two(precision);
    auto lower_bound = logarithm_endpoint(value.lower, two.upper, exponent, mode);
    auto upper_bound = logarithm_endpoint(value.upper, two.lower, exponent, mode);
    if (lower_bound.low == upper_bound.low && lower_bound.high == upper_bound.high)
      return {lower_bound, 0, FE_INEXACT};
  }
  throw Limit{};
}
struct MathEnvironment {
  fenv_t value;
  bool valid = std::fegetenv(&value) == 0;
  int exceptions = 0;
  ~MathEnvironment() {
    if (valid) { std::fesetenv(&value); if (exceptions) std::feraiseexcept(exceptions); }
  }
};
}
std::vector<ProviderExport> quad_exports() {
  std::vector<ProviderExport> result;
  for (bool wide : {false, true}) for (bool localized : {false, true}) {
    auto *entry = closure([wide, localized](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      const void *input = cursor.read<const void *>();
      void *end = cursor.read<void *>();
      void *locale = localized ? cursor.read<void *>() : nullptr;
      HostErrno preserve;
      FloatingEnvironment floating;
      Result value;
      try {
        bool ignored;
        int mode = std::fegetround();
        if (!floating.valid) value.error = EINVAL;
        else if (!input) value.error = EFAULT;
        else if (localized && !locale_is_utf8(locale, ignored)) value.error = EINVAL;
        else if (mode != FE_TONEAREST && mode != FE_TOWARDZERO && mode != FE_UPWARD && mode != FE_DOWNWARD) value.error = EINVAL;
        else value = convert(input, wide, mode);
      } catch (const Limit &) { value = {{}, 0, EOVERFLOW}; }
        catch (const std::bad_alloc &) { value = {{}, 0, ENOMEM}; }
      if (end) {
        const auto *position = input ? static_cast<const uint8_t *>(input) + value.consumed * (wide ? 4 : 1) : nullptr;
        std::memcpy(end, &position, sizeof(position));
      }
      if (value.error) *guest_errno_location() = errno_to_guest(value.error);
      std::memcpy(capture.frame.result_vectors[0], &value.bits, sizeof(value.bits));
    });
    result.push_back({wide ? localized ? "wcstold_l" : "wcstold" : localized ? "strtold_l" : "strtold",
      "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function, "quad/exact-rational/q0", false});
  }
  return result;
}
ProviderExport quad_log2_export() {
  auto *entry = closure([](Capture &capture) {
    Bits input; std::memcpy(&input, capture.frame.vectors[0], sizeof(input));
    HostErrno preserve;
    MathEnvironment environment;
    MathResult result{{0, UINT64_C(0x7fff800000000000)}, EINVAL, FE_INVALID};
    try {
      int mode = std::fegetround();
      if (environment.valid && (mode == FE_TONEAREST || mode == FE_TOWARDZERO || mode == FE_UPWARD || mode == FE_DOWNWARD))
        result = logarithm(input, mode);
    } catch (const Limit &) { result = {{0, UINT64_C(0x7fff800000000000)}, EOVERFLOW, FE_INVALID}; }
      catch (const std::bad_alloc &) { result = {{0, UINT64_C(0x7fff800000000000)}, ENOMEM, FE_INVALID}; }
    if (result.error) *guest_errno_location() = errno_to_guest(result.error);
    environment.exceptions = result.exceptions;
    std::memcpy(capture.frame.result_vectors[0], &result.bits, sizeof(result.bits));
  });
  return {"log2l", "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
          "quad/rigorous-ln-interval/q0", false};
}
}
