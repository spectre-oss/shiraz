// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/va_list.hpp"
#include <cstring>
#include <stdexcept>
#include <pthread.h>

namespace shiraz::guest {
VaListCursor::VaListCursor(const GuestVaList &list, uint64_t stack_bytes)
    : list_(list), stack_base_(list.stack), stack_bytes_(stack_bytes),
      stack_thread_(stack_bytes ? reinterpret_cast<uintptr_t>(pthread_self()) : 0) {
  if (list.integer_offset < -64 || list.integer_offset > 0 ||
      list.integer_offset % 8 || list.vector_offset < -128 ||
      list.vector_offset > 0 || list.vector_offset % 16 || !list.stack ||
      list.stack % 8 ||
      (list.integer_offset &&
       (list.integer_top < uint64_t(-list.integer_offset) ||
        list.integer_top % 8)) ||
      (list.vector_offset && (list.vector_top < uint64_t(-list.vector_offset) ||
                              list.vector_top % 16)))
    throw std::invalid_argument("invalid guest va_list layout");
  if (stack_bytes > UINT64_MAX - stack_base_)
    throw std::invalid_argument("guest va_list stack extent overflow");
}
uint64_t VaListCursor::next(ArgumentType type) {
  if (type == ArgumentType::Float || type == ArgumentType::Int8 ||
      type == ArgumentType::Int16)
    throw std::invalid_argument(
        "guest va_list requires default-promoted types");
  if (type != ArgumentType::Int32 && type != ArgumentType::Int64 &&
      type != ArgumentType::Double)
    throw std::invalid_argument("unsupported guest va_list type");
  bool real = type == ArgumentType::Double;
  uint64_t pointer;
  if (real && list_.vector_offset < 0) {
    pointer = list_.vector_top - uint64_t(-list_.vector_offset);
    list_.vector_offset += 16;
  } else if (!real && list_.integer_offset < 0) {
    pointer = list_.integer_top - uint64_t(-list_.integer_offset);
    list_.integer_offset += 8;
  } else {
    pointer = list_.stack;
    if (pointer > UINT64_MAX - 8)
      throw std::invalid_argument("guest va_list stack overflow");
    const uint64_t width = type == ArgumentType::Int32 ? 4 : 8;
    if (stack_bytes_ && (stack_thread_ != reinterpret_cast<uintptr_t>(pthread_self()) ||
        pointer < stack_base_ || pointer - stack_base_ > stack_bytes_ ||
        width > stack_bytes_ - (pointer - stack_base_)))
      throw std::invalid_argument("guest va_list stack read outside validated extent");
    list_.stack += 8;
  }
  // The runtime shares an address space with trusted app code. This cursor
  // validates representation and an optional producer-validated stack span,
  // not arbitrary register-save or caller-owned pointer accessibility.
  uint64_t value = 0;
  std::memcpy(&value, reinterpret_cast<const void *>(pointer),
              type == ArgumentType::Int32 ? 4 : 8);
  return value;
}
} // namespace shiraz::guest
