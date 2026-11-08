// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/call.hpp"

namespace shiraz::guest {
// AAPCS64 va_list representation. Never reinterpret as Darwin's char* va_list.
struct GuestVaList {
  uint64_t stack, integer_top, vector_top;
  int32_t integer_offset, vector_offset;
};
static_assert(sizeof(GuestVaList) == 32);
class VaListCursor {
public:
  explicit VaListCursor(const GuestVaList &list);
  // Types are the promoted argument types, obtained from the call contract.
  uint64_t next(ArgumentType type);

private:
  GuestVaList list_;
};
} // namespace shiraz::guest
