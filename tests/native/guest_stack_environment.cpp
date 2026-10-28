// SPDX-License-Identifier: GPL-3.0-only
#include "../../native/guest_stack_environment.h"
#include <cassert>
#include <array>
int main() {
 alignas(16) std::array<unsigned char, 4096> memory;
 memory.fill(0xa5);
 const char* env[] = {"FIXTURE_ONE=hello", "FIXTURE_TWO=/fixture/path", nullptr};
 auto sp = shiraz_stack_environment(memory.data(), memory.size(), env);
 assert(sp && !(sp & 15));
 auto entries = reinterpret_cast<uintptr_t*>(sp + 16);
 assert(!entries[2]);
 for (unsigned i=0; i<2; ++i) {
   assert(entries[i] > sp && entries[i] < reinterpret_cast<uintptr_t>(memory.data()+memory.size()));
   assert(!strcmp(reinterpret_cast<char*>(entries[i]), env[i]));
   assert(*reinterpret_cast<char*>(entries[i]-1)==0);
 }
 assert(memory.front()==0xa5);
 auto before=memory;
 assert(!shiraz_stack_environment(memory.data(), 32, env));
 assert(memory==before);
 assert(!shiraz_stack_environment(memory.data(), 4095, env));
 assert(memory==before);
 const char* empty[]={nullptr};
 assert(shiraz_stack_environment(memory.data(),memory.size(),empty));
}
