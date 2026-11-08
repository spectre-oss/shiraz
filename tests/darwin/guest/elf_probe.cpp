// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <mach-o/dyld.h>
#include <stdexcept>
#include <sys/mman.h>
#include <vector>

using namespace shiraz::guest;
extern "C" int shiraz_test_other_stack(void *, int (*)(void *), void *);
static int reject_other_stack(void *entry) {
  try {
    invoke(entry, {});
  } catch (const std::invalid_argument &error) {
    return std::strcmp(error.what(), "guest call stack outside prepared native extent") == 0;
  } catch (...) {
    return 0;
  }
  return 0;
}
static void require(bool value, const char *label) {
  if (!value)
    throw std::runtime_error(label);
}
static double host_va_callback(int count, const GuestVaList *args) noexcept {
  try {
    if (count < 0 || count > 32 || !args)
      return std::numeric_limits<double>::quiet_NaN();
    VaListCursor cursor(*args);
    double sum = 0;
    for (int i = 1; i <= count; ++i) {
      int32_t integer = static_cast<int32_t>(cursor.next(ArgumentType::Int32));
      uint64_t bits = cursor.next(ArgumentType::Double);
      double real;
      std::memcpy(&real, &bits, sizeof(real));
      sum += i * (integer + real);
    }
    return sum;
  } catch (...) {
    return std::numeric_limits<double>::quiet_NaN();
  }
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: elf-probe fixture.so");
    std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
    require(bool(file), "fixture open");
    auto length = file.tellg();
    require(length > 0 && length <= (256 << 20), "fixture length");
    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    file.seekg(0);
    require(bool(file.read(reinterpret_cast<char *>(bytes.data()), length)),
            "fixture read");
    const auto parsed = parse_elf(bytes, 16384);
    for (uint32_t type : {257u, 1025u, 1026u, 1027u}) {
      bool found = false;
      for (const auto &relocation : parsed.relocations)
        found |= relocation.type == type;
      require(found, "fixture must exercise all four relocation kinds");
    }
    uint32_t dyld_count = _dyld_image_count();
    Image first(bytes), second(bytes);
    require(_dyld_image_count() == dyld_count,
            "ELF must not be registered with dyld");
    require(*static_cast<long *>(first.symbol("guest_state")) == 0,
            "initial BSS zero");
    require(first.initialize() == 1 && first.initialize() == 0,
            "constructor exactly once");
    require(invoke(first.symbol("guest_read"), {}).result_integers[0] == 237,
            "relocations/BSS/constructor");
    require(*static_cast<long *>(second.symbol("guest_state")) == 0,
            "second image isolation");
    require(second.initialize() == 1, "second constructor");
    constexpr size_t other_stack_bytes = 256 * 1024;
    void *other_stack = mmap(nullptr, other_stack_bytes, PROT_READ | PROT_WRITE,
                             MAP_PRIVATE | MAP_ANON, -1, 0);
    require(other_stack != MAP_FAILED, "temporary alternate stack allocation");
    void *other_top = static_cast<uint8_t *>(other_stack) + other_stack_bytes - 16;
    require(!prepared_guest_stack_contains(reinterpret_cast<uintptr_t>(other_top)),
            "alternate stack lies outside the prepared pthread extent");
    int rejected = shiraz_test_other_stack(other_top, &reject_other_stack, first.symbol("guest_leaf"));
    require(munmap(other_stack, other_stack_bytes) == 0, "temporary alternate stack cleanup");
    require(rejected == 1 && !capture_boundary_token(),
            "real alternate-stack invocation refuses before guest entry and restores boundary");
    std::vector<Argument> twelve;
    for (int i = 1; i <= 12; ++i)
      twelve.push_back(Argument::integer(i % 2 ? i : -i));
    auto integers = invoke(first.symbol("guest_twelve"), twelve);
    require(static_cast<int64_t>(integers.result_integers[0]) == -78,
            "12 integer stack slots");
    void *scratch = mmap(nullptr, 16384, PROT_READ | PROT_WRITE | PROT_EXEC,
                         MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0);
    require(scratch != MAP_FAILED, "nested JIT write test reservation");
    {
      JitWriteScope outer;
      closure([](Capture &) {});
      // Creating a nested closure must not restore execution mode prematurely.
      *static_cast<volatile uint32_t *>(scratch) = 0xd503201f;
    }
    require(munmap(scratch, 16384) == 0, "nested JIT write cleanup");
    void *thunk = closure([&](Capture &capture) {
      CallCursor cursor(capture, Convention::Apple);
      std::vector<Argument> arguments;
      for (int i = 0; i < 12; ++i)
        arguments.push_back(Argument::integer(cursor.read<int>()));
      auto result = invoke(first.symbol("guest_twelve"), arguments);
      capture.frame.result_integers[0] = result.result_integers[0];
    });
    using Twelve =
        long (*)(int, int, int, int, int, int, int, int, int, int, int, int);
    Twelve host_call;
    std::memcpy(&host_call, &thunk, sizeof(host_call));
    require(host_call(1, -2, 3, -4, 5, -6, 7, -8, 9, -10, 11, -12) == -78,
            "generated Apple-to-AAPCS64 closure");
    std::vector<Argument> mixed;
    double expected = 0;
    for (int i = 1; i <= 24; ++i) {
      if (i % 2 == 0) {
        if (i == 4)
          mixed.push_back(Argument::real(float(i + 0.25)));
        else
          mixed.push_back(Argument::real(i + 0.25));
        expected += i * (i + 0.25);
      } else {
        auto type = i == 3   ? ArgumentType::Int8
                    : i == 5 ? ArgumentType::Int16
                             : ArgumentType::Int32;
        mixed.push_back(Argument::integer(-i, type));
        expected -= i * i;
      }
    }
    require(invoke(first.symbol("guest_mixed"), mixed).double_result() ==
                expected,
            "24 mixed narrow/integer/FP arguments");
    std::vector<Argument> variadic{Argument::integer(12)};
    expected = 0;
    for (int i = 1; i <= 12; ++i) {
      variadic.push_back(Argument::integer(-i));
      variadic.push_back(Argument::real(i + 0.5));
      expected += i * 0.5;
    }
    require(invoke(first.symbol("guest_variadic"), variadic).double_result() ==
                expected,
            "AAPCS64 variadic register saves and stack overflow");
    std::vector<Argument> callback{
        Argument::integer(reinterpret_cast<uintptr_t>(&host_va_callback),
                          ArgumentType::Int64),
        Argument::integer(12),
    };
    callback.insert(callback.end(), variadic.begin() + 1, variadic.end());
    require(invoke(first.symbol("guest_callback"), callback).double_result() ==
                expected,
            "guest-to-host AAPCS64 va_list conversion");
    std::cout << "M5_STACK_ENTRY_PASS alternate_stack_rejected=1 boundary_restored=1\n";
    std::cout << "M5_ELF_ABI_PASS images=2 constructors=2 integer12=1 "
                 "mixed24=1 variadic25=1 callback_va_list=1 closure=1 dyld=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
