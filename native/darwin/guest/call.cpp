// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cstddef>
#include <cstring>
#include <stdexcept>

namespace shiraz::guest {
extern "C" void shiraz_guest_invoke_asm(CallFrame *, void *, const void *,
                                        uint64_t);
static_assert(offsetof(CallFrame, vectors) == 64);
static_assert(offsetof(CallFrame, result_integers) == 192);
static_assert(offsetof(CallFrame, result_vectors) == 208);

Argument Argument::integer(int64_t value, ArgumentType type) {
  if (type == ArgumentType::Float || type == ArgumentType::Double)
    throw std::invalid_argument("integer argument requires integer type");
  return {type, uint64_t(value)};
}
Argument Argument::real(double value) {
  uint64_t bits;
  std::memcpy(&bits, &value, sizeof(value));
  return {ArgumentType::Double, bits};
}
Argument Argument::real(float value) {
  uint32_t bits;
  std::memcpy(&bits, &value, sizeof(value));
  return {ArgumentType::Float, bits};
}
double CallFrame::double_result() const {
  double value;
  std::memcpy(&value, result_vectors[0], sizeof(value));
  return value;
}
CallFrame invoke(void *entry, const std::vector<Argument> &arguments) {
  if (!entry || arguments.size() > 258)
    throw std::invalid_argument("invalid guest call size/entry");
  CallFrame frame;
  std::vector<uint64_t> stack;
  unsigned integer = 0, vector = 0;
  for (const auto &argument : arguments) {
    bool floating = argument.type == ArgumentType::Float ||
                    argument.type == ArgumentType::Double;
    if (floating && vector < 8) {
      std::memcpy(frame.vectors[vector++], &argument.bits,
                  argument.type == ArgumentType::Float ? 4 : 8);
    } else if (!floating && integer < 8) {
      frame.integers[integer++] = argument.bits;
    } else {
      // AAPCS64 stack arguments occupy eight-byte slots, including narrow
      // values.
      stack.push_back(argument.bits);
    }
  }
  if (stack.size() % 2)
    stack.push_back(0);
  CaptureBoundary transition;
  if (!guest_tls_prepared()) prepare_guest_tls();
  uintptr_t sp;
  __asm__ volatile("mov %0, sp" : "=r"(sp));
  // invoke.S reserves 32 bytes for its saved registers before copying the
  // rounded argument area. This bounds that outgoing frame, not the guest's
  // later stack use or the host frames already entered to reach this check.
  const uint64_t reserved = 32 + stack.size() * sizeof(uint64_t);
  if ((sp & 15) || reserved > sp || !prepared_guest_stack_contains(sp) ||
      !prepared_guest_stack_contains(sp - reserved))
    throw std::invalid_argument("guest call stack outside prepared native extent");
  shiraz_guest_invoke_asm(&frame, entry, stack.data(),
                          stack.size() * sizeof(uint64_t));
  return frame;
}
} // namespace shiraz::guest
