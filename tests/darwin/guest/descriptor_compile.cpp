// SPDX-License-Identifier: GPL-3.0-only
// Compile-time parser checks: no host executable or signature is produced.
#include "shiraz/darwin/guest/descriptor.hpp"
#include <array>
using shiraz::guest::valid_method_descriptor;
using shiraz::guest::valid_method_shorty;
static_assert(valid_method_shorty("V"));
static_assert(valid_method_shorty("LZBCSIJFDL"));
static_assert(!valid_method_shorty(""));
static_assert(!valid_method_shorty("IV"));
static_assert(!valid_method_shorty("[I"));
static_assert(!valid_method_shorty(std::string_view("V\0I", 3)));
static_assert(!valid_method_shorty(std::string_view("\0", 1)));
static_assert(valid_method_descriptor("()V"));
static_assert(valid_method_descriptor("(ZBCSIJFDLjava/lang/Object;[[D)[I"));
static_assert(valid_method_descriptor("(Lsome/(odd)name;)V"));
static_assert(!valid_method_descriptor("(V)V"));
static_assert(!valid_method_descriptor("()[V"));
static_assert(!valid_method_descriptor("(L;)V"));
static_assert(!valid_method_descriptor("(Ljava.lang.Object;)V"));
static_assert(!valid_method_descriptor("(L/a;)V"));
static_assert(!valid_method_descriptor("(La/;)V"));
static_assert(!valid_method_descriptor("(La//b;)V"));
static_assert(!valid_method_descriptor("(L[a;)V"));
static_assert(!valid_method_descriptor("(I)Vx"));
static_assert(!valid_method_descriptor("(I"));
static_assert(!valid_method_descriptor(std::string_view("(La\0b;)V", 8)));
constexpr auto array_descriptor(unsigned dimensions) {
  std::array<char, 260> text{};
  text[0] = '(';
  for (unsigned i = 0; i < dimensions; ++i) text[i + 1] = '[';
  text[dimensions + 1] = 'I';
  text[dimensions + 2] = ')';
  text[dimensions + 3] = 'V';
  return text;
}
constexpr auto maximum_array = array_descriptor(255);
constexpr auto oversized_array = array_descriptor(256);
static_assert(valid_method_descriptor({maximum_array.data(), 259}));
static_assert(!valid_method_descriptor({oversized_array.data(), 260}));
constexpr auto parameters(char kind, unsigned count) {
  std::array<char, 259> text{};
  text[0] = '(';
  for (unsigned i = 0; i < count; ++i) text[i + 1] = kind;
  text[count + 1] = ')';
  text[count + 2] = 'V';
  return text;
}
constexpr auto maximum_ints = parameters('I', 255);
constexpr auto oversized_ints = parameters('I', 256);
constexpr auto maximum_longs = parameters('J', 127);
constexpr auto oversized_longs = parameters('J', 128);
static_assert(valid_method_descriptor({maximum_ints.data(), 258}));
static_assert(!valid_method_descriptor({oversized_ints.data(), 259}));
static_assert(valid_method_descriptor({maximum_longs.data(), 130}));
static_assert(!valid_method_descriptor({oversized_longs.data(), 131}));
constexpr auto mixed_parameters(unsigned longs) {
  std::array<char, 133> text{};
  text[0] = '(';
  for (unsigned i = 0; i < longs; ++i) text[i + 1] = 'J';
  text[longs + 1] = 'I';
  text[longs + 2] = ')';
  text[longs + 3] = 'V';
  return text;
}
constexpr auto exact_slots = mixed_parameters(127);
constexpr auto excess_slots = mixed_parameters(128);
static_assert(valid_method_descriptor({exact_slots.data(), 131}));
static_assert(!valid_method_descriptor({excess_slots.data(), 132}));
constexpr auto array_parameters() {
  std::array<char, 513> text{};
  text[0] = '(';
  for (unsigned i = 0; i < 255; ++i) {
    text[2 * i + 1] = '[';
    text[2 * i + 2] = 'J';
  }
  text[511] = ')';
  text[512] = 'V';
  return text;
}
constexpr auto long_arrays = array_parameters();
static_assert(valid_method_descriptor({long_arrays.data(), long_arrays.size()}));
constexpr auto shorty_parameters(char kind, unsigned count) {
  std::array<char, 257> text{};
  text[0] = 'V';
  for (unsigned i = 0; i < count; ++i) text[i + 1] = kind;
  return text;
}
constexpr auto shorty_ints = shorty_parameters('I', 255);
constexpr auto shorty_excess_ints = shorty_parameters('I', 256);
constexpr auto shorty_longs = shorty_parameters('J', 127);
constexpr auto shorty_excess_longs = shorty_parameters('J', 128);
static_assert(valid_method_shorty({shorty_ints.data(), 256}));
static_assert(!valid_method_shorty({shorty_excess_ints.data(), 257}));
static_assert(valid_method_shorty({shorty_longs.data(), 128}));
static_assert(!valid_method_shorty({shorty_excess_longs.data(), 129}));
