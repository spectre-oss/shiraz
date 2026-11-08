// SPDX-License-Identifier: GPL-3.0-only
// First-party guest execution of the production parser, not ART integration.
#include "shiraz/darwin/guest/descriptor.hpp"
#include <initializer_list>
namespace {
__attribute__((noinline)) bool validate(const volatile char *input, unsigned length,
                                       bool shorty) {
  char bytes[4096];
  if (length >= sizeof(bytes)) return false;
  // Volatile input reads prevent replacing execution with constexpr results.
  for (unsigned i = 0; i < length; ++i) bytes[i] = input[i];
  std::string_view text(bytes, length);
  return shorty ? shiraz::guest::valid_method_shorty(text)
                : shiraz::guest::valid_method_descriptor(text);
}
struct Case { const char *text; unsigned length; bool shorty, expected; };
}
extern "C" int guest_descriptor_cases() {
  static const Case cases[] = {
      {"()V", 3, false, true}, {"(J[D)Ljava/lang/Object;", 23, false, true},
      {"(V)V", 4, false, false}, {"()[V", 4, false, false},
      {"(L;)V", 5, false, false}, {"(La.b;)V", 8, false, false},
      {"(L/a;)V", 7, false, false}, {"(La/;)V", 7, false, false},
      {"(La//b;)V", 9, false, false}, {"(La\0b;)V", 8, false, false},
      {"(I)Vx", 5, false, false}, {"(I", 2, false, false},
      {"V", 1, true, true}, {"LZBCSIJFDL", 10, true, true},
      {"IV", 2, true, false}, {"[I", 2, true, false},
      {"V\0I", 3, true, false}, {"\0", 1, true, false},
  };
  unsigned index = 0;
  for (const auto &test : cases) {
    ++index;
    if (validate(test.text, test.length, test.shorty) != test.expected) return index;
  }
  volatile char text[1024];
  // Rank 255 succeeds, rank 256 fails, including a valid scalar element type.
  for (unsigned rank : {255u, 256u}) {
    text[0] = '(';
    for (unsigned i = 0; i < rank; ++i) text[i + 1] = '[';
    text[rank + 1] = 'I'; text[rank + 2] = ')'; text[rank + 3] = 'V';
    ++index;
    if (validate(text, rank + 4, false) != (rank == 255)) return index;
  }
  // Slot limits count wide primitives twice, but arrays remain references.
  for (char kind : {'I', 'J'}) {
    for (unsigned count : {127u, 128u, 255u, 256u}) {
      text[0] = 'V';
      for (unsigned i = 0; i < count; ++i) text[i + 1] = kind;
      ++index;
      if (validate(text, count + 1, true) != (count * (kind == 'J' ? 2 : 1) <= 255))
        return index;
    }
  }
  if (index != 28) return 1000;
#ifdef SHIRAZ_DESCRIPTOR_FORCE_FAILURE
  return 1; // The existing host probe must reject this constructor result.
#else
  return 0;
#endif
}
