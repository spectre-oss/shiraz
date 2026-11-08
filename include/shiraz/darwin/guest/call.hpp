// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include <vector>

namespace shiraz::guest {
enum class ArgumentType { Int8, Int16, Int32, Int64, Float, Double };
struct Argument {
  ArgumentType type;
  uint64_t bits;
  static Argument integer(int64_t value,
                          ArgumentType type = ArgumentType::Int32);
  static Argument real(double value);
  static Argument real(float value);
};
struct alignas(16) CallFrame {
  uint64_t integers[8] = {};
  alignas(16) uint8_t vectors[8][16] = {};
  uint64_t result_integers[2] = {};
  alignas(16) uint8_t result_vectors[2][16] = {};
  double double_result() const;
};
// AAPCS64 scalar-call entry. No aggregates, binary128 or JNI signature
// inference. The caller supplies the actual promoted types for variadic
// arguments.
CallFrame invoke(void *entry, const std::vector<Argument> &arguments);
} // namespace shiraz::guest
