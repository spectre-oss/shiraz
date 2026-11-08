// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/multibyte.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include <algorithm>
#include <array>
#include <cstring>

// Behavioral reference: Android14 bionic/{wchar,mbrtoc32,c32rtomb}.cpp.
// These adapters implement the pinned behavior independently, including its
// documented-in-tests differences from generic POSIX locale expectations.

namespace shiraz::guest {
namespace {
constexpr size_t illegal = size_t(-1), incomplete = size_t(-2);
// Pinned bionic mbstate_t stores its pending UTF-8 prefix in __seq[4]; the
// LP64 reserved[4] bytes remain untouched. Access bytes, including unaligned
// caller state, rather than aliasing Darwin's unrelated native mbstate_t.
using State = std::array<uint8_t, 8>;
thread_local State decode_state{}, length_state{}, legacy_state{}, string_state{}, encode_state{}, output_state{};
bool initial(const void *state) {
  uint8_t bytes[4]; std::memcpy(bytes, state, 4);
  return !(bytes[0] | bytes[1] | bytes[2] | bytes[3]);
}
void reset(void *state) {
  const uint8_t zero[4]{}; std::memcpy(state, zero, 4);
}
size_t fail(void *state, int error = EILSEQ) {
  reset(state); errno = error; return illegal;
}
size_t decode(uint32_t *output, const char *input, size_t count, void *state) {
  uint8_t prefix[4]; std::memcpy(prefix, state, 4);
  if (prefix[3]) return fail(state, EINVAL);
  if (!input) { input = ""; count = 1; output = nullptr; }
  // Android14 compatibility: zero available bytes returns 0, not -2.
  if (!count) return 0;
  size_t present = prefix[2] ? 3 : prefix[1] ? 2 : prefix[0] ? 1 : 0;
  uint8_t lead = present ? prefix[0] : uint8_t(input[0]);
  if (!present && lead < 0x80) {
    if (output) *output = lead;
    return lead ? 1 : 0;
  }
  unsigned length;
  uint32_t value, minimum;
  if ((lead & 0xe0) == 0xc0) { length = 2; value = lead & 31; minimum = 0x80; }
  else if ((lead & 0xf0) == 0xe0) { length = 3; value = lead & 15; minimum = 0x800; }
  else if ((lead & 0xf8) == 0xf0) { length = 4; value = lead & 7; minimum = 0x10000; }
  else return fail(state);
  if (present >= length) return fail(state);
  for (size_t i = 1; i < present; ++i)
    if ((prefix[i] & 0xc0) != 0x80) return fail(state);
  size_t needed = length - present;
  size_t consumed = std::min(count, needed);
  for (size_t i = 0; i < consumed; ++i) {
    uint8_t byte = uint8_t(input[i]);
    if (present + i && (byte & 0xc0) != 0x80) return fail(state);
    prefix[present + i] = byte;
  }
  if (consumed < needed) {
    std::memcpy(state, prefix, 4);
    return incomplete;
  }
  for (unsigned i = 1; i < length; ++i) value = (value << 6) | (prefix[i] & 63);
  if (value < minimum || (value >= 0xd800 && value <= 0xdfff) || value > 0x10ffff)
    return fail(state);
  if (output) *output = value;
  reset(state);
  return value ? needed : 0;
}
size_t encode(char *output, uint32_t value, void *state) {
  if (!output) { reset(state); return 1; }
  if (!value) { output[0] = 0; reset(state); return 1; }
  if (!initial(state)) return fail(state);
  unsigned length;
  uint8_t tag;
  if (value < 0x80) { output[0] = char(value); return 1; }
  if (value <= 0x7ff) { length = 2; tag = 0xc0; }
  else if (value <= 0xffff) { length = 3; tag = 0xe0; }
  else if (value <= 0x1fffff) { length = 4; tag = 0xf0; }
  else { errno = EILSEQ; return illegal; }
  // Android14 c32rtomb accepts surrogate/above-Unicode values in this range,
  // while its decoder rejects them. Keep this pinned ABI behavior explicit.
  for (unsigned i = length - 1; i; --i) {
    output[i] = char(0x80 | (value & 63)); value >>= 6;
  }
  output[0] = char(tag | value);
  return length;
}
int state_initial(const void *state) { return !state || initial(state); }
size_t restart_character(uint32_t *output, const char *input, size_t count, void *state) {
  return decode(output, input, count, state ? state : decode_state.data());
}
size_t restart_length(const char *input, size_t count, void *state) {
  return decode(nullptr, input, count, state ? state : length_state.data());
}
int legacy_character(uint32_t *output, const char *input, size_t count) {
  if (!input) { reset(legacy_state.data()); return 0; }
  size_t result = decode(output, input, count, legacy_state.data());
  if (result == incomplete) { errno = EILSEQ; return -1; }
  return result == illegal ? -1 : int(result);
}
size_t restart_output(char *output, uint32_t value, void *state) {
  return encode(output, value, state ? state : encode_state.data());
}
size_t input_string(uint32_t *output, const char **source, size_t input_limit,
                    size_t output_limit, void *opaque) {
  void *state = opaque ? opaque : string_state.data();
  if (!source || !*source) return fail(state, EINVAL);
  const char *begin = *source;
  if (input_limit && !initial(state) && uint8_t(begin[0]) < 0x80)
    return fail(state);
  size_t consumed = 0, produced = 0;
  while (consumed < input_limit && (!output || produced < output_limit)) {
    uint32_t value;
    size_t result = decode(&value, begin + consumed, input_limit - consumed, state);
    if (result == illegal || result == incomplete) {
      if (output) *source = begin + (result == incomplete ? input_limit : consumed);
      // Android14 mbsnrtowcs treats incomplete input as EILSEQ, clears state,
      // and, for a real output buffer, advances src to the supplied input end.
      return fail(state);
    }
    if (!result) {
      if (output) { output[produced] = 0; *source = nullptr; }
      reset(state); return produced;
    }
    if (output) output[produced] = value;
    consumed += result; ++produced;
  }
  if (output) *source = begin + consumed;
  reset(state);
  return produced;
}
size_t unbounded_input(uint32_t *output, const char **source, size_t limit, void *state) {
  return input_string(output, source, SIZE_MAX, limit, state);
}
size_t output_string(char *output, const uint32_t **source, size_t input_limit,
                     size_t output_limit, void *opaque) {
  void *state = opaque ? opaque : output_state.data();
  if (!source || !*source) return fail(state, EINVAL);
  if (!initial(state)) return fail(state);
  const uint32_t *begin = *source;
  size_t consumed = 0, produced = 0;
  while (consumed < input_limit && (!output || produced < output_limit)) {
    char encoded[4];
    uint32_t value = begin[consumed];
    size_t result = encode(encoded, value, state);
    if (result == illegal) {
      if (output) *source = begin + consumed;
      return result;
    }
    if (output && result > output_limit - produced) break;
    if (produced > SIZE_MAX - result) return fail(state, EOVERFLOW);
    if (output) std::memcpy(output + produced, encoded, result);
    if (!value) {
      if (output) *source = nullptr;
      return produced;
    }
    produced += result; ++consumed;
  }
  if (output) *source = begin + consumed;
  return produced;
}
size_t unbounded_output(char *output, const uint32_t **source, size_t limit, void *state) {
  return output_string(output, source, SIZE_MAX, limit, state);
}
uint32_t byte_to_wide(int value) {
  if (value == -1) return UINT32_MAX;
  char input = char(uint8_t(value));
  State state{};
  uint32_t output = 0;
  size_t result = decode(&output, &input, 1, state.data());
  return result <= 1 ? output : UINT32_MAX;
}
int wide_to_byte(uint32_t value) {
  if (value == UINT32_MAX) return -1;
  State state{}; char output[4];
  return encode(output, value, state.data()) == 1 ? uint8_t(output[0]) : -1;
}
size_t maximum_character_bytes() { return current_locale_utf8() ? 4 : 1; }
} // namespace
std::vector<ProviderExport> multibyte_exports() {
  std::vector<ProviderExport> result;
  auto add = [&](auto function, const char *name) {
    result.push_back(fixed_export(name, "LIBC", function, ErrnoPolicy::Observe));
  };
  add(&state_initial, "mbsinit");
  add(&restart_character, "mbrtowc");
  add(&restart_length, "mbrlen");
  add(&legacy_character, "mbtowc");
  add(&restart_output, "wcrtomb");
  add(&input_string, "mbsnrtowcs");
  add(&unbounded_input, "mbsrtowcs");
  add(&output_string, "wcsnrtombs");
  add(&unbounded_output, "wcsrtombs");
  add(&byte_to_wide, "btowc");
  add(&wide_to_byte, "wctob");
  add(&maximum_character_bytes, "__ctype_get_mb_cur_max");
  return result;
}
} // namespace shiraz::guest
