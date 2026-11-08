// SPDX-License-Identifier: GPL-3.0-only
// Executes a first-party guest ELF only; never OsmAnd or Android acceptance.
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <sys/sysctl.h>
using namespace shiraz::guest;

namespace {
constexpr uint64_t sentinel15 = 0x1122334455667788ULL;
constexpr uint64_t sentinel17 = 0x8877665544332211ULL;
struct alignas(16) Capture {
  uint64_t old_value, x15, x16, x17, data_pointer;
  uint64_t sp_delta, lr_delta, nzcv_before, nzcv_after, padding;
  uint64_t fpcr_before, fpcr_after, fpsr_before, fpsr_after;
  std::array<uint8_t, 16> vector0, vector31;
};
static_assert(offsetof(Capture, nzcv_before) == 56 &&
              offsetof(Capture, fpcr_before) == 80 &&
              offsetof(Capture, vector0) == 112 &&
              offsetof(Capture, vector31) == 128 && sizeof(Capture) == 144);
struct alignas(16) GuardedWord {
  uint64_t before = 0x0123456789abcdefULL;
  uint32_t value = 0;
  uint32_t after_word = 0xdeadbeef;
  uint64_t after = 0xfedcba9876543210ULL;
};
static_assert(offsetof(GuardedWord, value) == 8 &&
              offsetof(GuardedWord, after_word) == 12);
void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
bool lse_available() {
  int32_t value = 0;
  size_t size = sizeof(value);
  return !sysctlbyname("hw.optional.arm.FEAT_LSE", &value, &size, nullptr, 0) &&
         size == sizeof(value) && value == 1;
}
uint32_t word(const unsigned char *address) {
  uint32_t result;
  std::memcpy(&result, address, sizeof(result));
  return result;
}
void verify_body(uintptr_t address) {
  const auto *bytes = reinterpret_cast<const unsigned char *>(address);
  const std::array<uint32_t, 12> expected = {
      0xd503245f, 0, 0, 0x34000070, 0xb8e00020, 0xd65f03c0,
      0x2a0003f0, 0x885ffc20, 0x0b100011, 0x880ffc31, 0x35ffffaf, 0xd65f03c0};
  for (size_t index = 0; index < expected.size(); ++index) {
    uint32_t actual = word(bytes + index * 4);
    if (index == 1)
      require((actual & 0x9f00001f) == 0x90000010, "exact ADRP x16 shape");
    else if (index == 2)
      require((actual & 0xffc003ff) == 0x39400210, "exact LDRB w16,[x16,#imm] shape");
    else
      require(actual == expected[index], "executed helper matches exact recognized instruction body");
  }
}
void verify_cas_body(uintptr_t address, unsigned ordering) {
  const auto *bytes = reinterpret_cast<const unsigned char *>(address);
  const std::array<uint32_t, 3> operation = {0xc8a07c41, 0xc8e07c41, 0xc8e0fc41};
  const std::array<uint32_t, 13> expected = {
      0xd503245f, 0, 0, 0x34000070, operation.at(ordering), 0xd65f03c0,
      0xaa0003f0, ordering ? 0xc85ffc40u : 0xc85f7c40u, 0xeb10001f,
      0x54000061, ordering == 2 ? 0xc811fc41u : 0xc8117c41u,
      0x35ffff91, 0xd65f03c0};
  for (size_t index = 0; index < expected.size(); ++index) {
    uint32_t actual = word(bytes + index * 4);
    if (index == 1)
      require((actual & 0x9f00001f) == 0x90000010, "CAS ADRP shape");
    else if (index == 2)
      require((actual & 0xffc003ff) == 0x39400210, "CAS LDRB shape");
    else
      require(actual == expected[index], "CAS exact recognized body");
  }
}
uint64_t compare_flags(uint64_t value, uint64_t expected) {
  uint64_t difference = value - expected;
  return ((difference >> 63) << 31) | (uint64_t(difference == 0) << 30) |
         (uint64_t(value >= expected) << 29) |
         (uint64_t(((value ^ expected) & (value ^ difference)) >> 63) << 28);
}
unsigned verify_cas(Loader &loader, Loader::Module *module) {
  const std::array<const char *, 3> names = {
      "atomic_effects_cas64", "atomic_effects_casa64", "atomic_effects_casal64"};
  const std::array<std::array<uint64_t, 3>, 6> cases = {{{0, 0, 1},
      {7, 7, 9}, {7, 8, 9}, {0xffffffffffffffffULL, 0xffffffffffffffffULL, 0},
      {0x8000000000000000ULL, 1, 5}, {1, 0x8000000000000000ULL, 5}}};
  auto capture = loader.symbol(module, "atomic_effects_cas_capture");
  unsigned completed = 0;
  for (unsigned ordering = 0; ordering < names.size(); ++ordering) {
    verify_cas_body(loader.symbol(module, names[ordering]).address, ordering);
    for (unsigned lse = 0; lse < 2; ++lse) for (const auto &test : cases) {
      std::array<uint64_t, 3> data = {0x0123456789abcdefULL, test[0], 0xfedcba9876543210ULL};
      Capture result{};
      auto returned = invoke(reinterpret_cast<void *>(capture.address),
          {pointer(&data[1]), Argument::integer(test[1]), Argument::integer(test[2]),
           pointer(&result), Argument::integer(lse), Argument::integer(ordering)});
      bool matches = test[0] == test[1];
      require(returned.result_integers[0] == 0 && result.old_value == test[0] &&
                  data[1] == (matches ? test[2] : test[0]), "CAS full-width match/mismatch effects");
      require(data[0] == 0x0123456789abcdefULL && data[2] == 0xfedcba9876543210ULL,
              "CAS writes exactly eight bytes");
      require(result.x15 == sentinel15 && result.x16 == (lse ? 1 : test[1]) &&
                  result.x17 == (lse || !matches ? sentinel17 : 0),
              "CAS LSE, initial mismatch and successful LL/SC have distinct effects");
      require(result.data_pointer == reinterpret_cast<uintptr_t>(&data[1]) &&
                  result.padding == test[2] && !result.sp_delta && !result.lr_delta,
              "CAS preserves data base, desired value, SP and exact LR");
      require(result.nzcv_after == (lse ? result.nzcv_before : compare_flags(test[0], test[1])) &&
                  result.fpcr_after == result.fpcr_before && result.fpsr_after == result.fpsr_before,
              "CAS LSE preserves NZCV while LL/SC leaves actual compare flags");
      for (size_t index = 0; index < 16; ++index)
        require(result.vector0[index] == 0x5a && result.vector31[index] == 0xa5,
                "CAS representative vector registers preserved");
      ++completed;
    }
  }
  return completed;
}
} // namespace

int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: atomic-effects-probe fixture-directory");
    require(lse_available(), "both-path verification requires actual host FEAT_LSE");
    Loader loader({argv[1]});
    auto *module = loader.open("libatomic-effects.so");
    require(loader.image_count() == 1 && loader.constructor_count() == 0,
            "dependency-free genuine ELF without fabricated providers");
    auto helper = loader.symbol(module, "atomic_effects_add32");
    auto capture = loader.symbol(module, "atomic_effects_capture");
    require(helper.kind == SymbolKind::Function && capture.kind == SymbolKind::Function,
            "fixture exports actual guest functions");
    verify_body(helper.address);
    const std::array<std::array<uint32_t, 2>, 5> cases = {{{0, 0}, {7, 9},
        {0xffffffffu, 1}, {0x80000000u, 0x80000000u}, {0x12345678u, 0xfedcba98u}}};
    unsigned completed = 0;
    for (unsigned lse = 0; lse < 2; ++lse) for (const auto &test : cases) {
      GuardedWord data;
      data.value = test[0];
      Capture result{};
      auto returned = invoke(reinterpret_cast<void *>(capture.address),
          {pointer(&data.value), Argument::integer(test[1]), pointer(&result), Argument::integer(lse)});
      uint32_t sum = uint32_t(test[0] + test[1]);
      require(returned.result_integers[0] == 0, "guest wrapper returns normally");
      require(result.old_value == uint64_t(test[0]) && data.value == sum,
              "32-bit atomic returns old value zero-extended and stores wrapped sum");
      require(data.before == 0x0123456789abcdefULL && data.after_word == 0xdeadbeef &&
                  data.after == 0xfedcba9876543210ULL,
              "RMW touches exactly four bytes with adjacent guards unchanged");
      require(result.x15 == (lse ? sentinel15 : 0) &&
                  result.x16 == uint64_t(lse ? 1 : test[1]) &&
                  result.x17 == (lse ? sentinel17 : uint64_t(sum)),
              "LSE and LLSC have distinct explicit scalar clobbers");
      require(result.data_pointer == reinterpret_cast<uintptr_t>(&data.value) &&
                  !result.sp_delta && !result.lr_delta,
              "memory base, SP and exact LR continuation preserved");
      require(result.nzcv_before == 0xa0000000 && result.nzcv_after == result.nzcv_before &&
                  result.fpcr_after == result.fpcr_before && result.fpsr_after == result.fpsr_before,
              "integer/FP control state preserved");
      for (size_t index = 0; index < 16; ++index)
        require(result.vector0[index] == 0x5a && result.vector31[index] == 0xa5,
                "representative 128-bit vector registers preserved");
      ++completed;
    }
    unsigned cas_completed = verify_cas(loader, module);
    std::cout << "M5_ATOMIC_EFFECTS_PASS guest_elf=1 exact_body=1 branches=2 cases=" << completed
              << " cas_orderings=3 cas_cases=" << cas_completed << " cas_rmw_bytes=8 cas_contention=0"
              << " rmw_bytes=4 adjacent_guards=1 zero_extend=1 overflow=1 distinct_clobbers=1"
                 " sp_lr=1 nzcv=1 fp_control=1 vector_samples=2 android_execution=0 app_execution=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
