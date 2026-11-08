// SPDX-License-Identifier: GPL-3.0-only
#include <stddef.h>
namespace std { struct nothrow_t {}; extern const nothrow_t nothrow; }
void *operator new(size_t);
void *operator new[](size_t);
void *operator new(size_t, const std::nothrow_t &) noexcept;
void *operator new[](size_t, const std::nothrow_t &) noexcept;
void operator delete(void *) noexcept;
void operator delete[](void *) noexcept;
void operator delete(void *, const std::nothrow_t &) noexcept;
void operator delete[](void *, const std::nothrow_t &) noexcept;
static int constructions;
struct Value {
  int value, inverse;
  Value() : value(73), inverse(~73) { __atomic_add_fetch(&constructions, 1, __ATOMIC_RELAXED); }
};
extern "C" int cxx_static_value(void) {
  static Value object;
  return object.inverse == ~object.value ? object.value : -1;
}
extern "C" int cxx_static_constructions(void) { return __atomic_load_n(&constructions, __ATOMIC_RELAXED); }
extern "C" void *cxx_new(size_t size) { return ::operator new(size); }
extern "C" void *cxx_new_array(size_t size) { return ::operator new[](size); }
extern "C" void *cxx_nothrow(size_t size) { return ::operator new(size, std::nothrow); }
extern "C" void *cxx_nothrow_array(size_t size) { return ::operator new[](size, std::nothrow); }
extern "C" void cxx_delete(void *pointer) { ::operator delete(pointer); }
extern "C" void cxx_delete_array(void *pointer) { ::operator delete[](pointer); }
extern "C" void cxx_delete_nothrow(void *pointer) { ::operator delete(pointer, std::nothrow); }
extern "C" void cxx_delete_nothrow_array(void *pointer) { ::operator delete[](pointer, std::nothrow); }
