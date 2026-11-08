// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/va_list.hpp"
#include <cstring>
#include <stdexcept>

namespace shiraz::guest {
VaListCursor::VaListCursor(const GuestVaList &list) : list_(list) {
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
    list_.stack += 8;
  }
  // The runtime shares an address space with trusted app code. This cursor
  // validates representation, not arbitrary guest pointer accessibility.
  uint64_t value = 0;
  std::memcpy(&value, reinterpret_cast<const void *>(pointer),
              type == ArgumentType::Int32 ? 4 : 8);
  return value;
}
} // namespace shiraz::guest
