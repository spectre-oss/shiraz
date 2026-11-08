// SPDX-License-Identifier: GPL-3.0-only
// Original implementation. Register/storage ABI checked against pinned
// android-14.0.0_r1 setjmp.h and libc/arch-arm64/bionic/setjmp.S. The opaque
// register layout follows its 32-word capacity without adopting Android's
// shadow-call-stack register or exposing a Darwin sigset_t as guest sigset_t.
#include "shiraz/darwin/guest/jump.hpp"
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <pthread.h>
#include <signal.h>

namespace shiraz::guest {
namespace {
constexpr uint64_t kMagic = 0x5348525a4a4d5000ull;
std::atomic<uint64_t> next_thread{1};
uint64_t thread_cookie() {
  static thread_local const uint64_t value = [] {
    uint64_t result = next_thread.fetch_add(1, std::memory_order_relaxed);
    if (!result) std::abort();
    return result;
  }();
  return value;
}
[[noreturn]] void fail(const char *reason) {
  std::fprintf(stderr, "SHIRAZ_GUEST_FATAL nonlocal jump: %s\n", reason);
  std::abort();
}
uint64_t *buffer(void *address) {
  if (!address || reinterpret_cast<uintptr_t>(address) % 8) fail("invalid buffer address/alignment");
  return static_cast<uint64_t *>(address);
}
uint64_t checksum(const uint64_t *words) {
  static const uint64_t secret = [] {
    uint64_t result;
    arc4random_buf(&result, sizeof(result));
    return result;
  }();
  // Corruption detection bound to this process and buffer address; this is
  // not a sandbox against a guest with arbitrary native-memory access.
  uint64_t value = secret ^ reinterpret_cast<uintptr_t>(words);
  for (unsigned i = 0; i < 32; ++i) {
    if (i == 24) continue;
    value ^= words[i];
    value *= 0x100000001b3ull;
    value = (value << 17) | (value >> 47);
  }
  return value;
}
} // namespace
extern "C" [[noreturn]] void shiraz_guest_jump_bad_buffer() { fail("invalid buffer address/alignment"); }
extern "C" void shiraz_guest_jump_record(void *address, int save_mask) noexcept {
  HostErrno preserve;
  auto *words = buffer(address);
  uint64_t token = capture_boundary_token();
  if (!token) fail("setjmp outside an active guest invocation");
  words[0] = kMagic | (save_mask ? 1 : 0);
  words[1] = 0;
  if (save_mask) {
    static_assert(sizeof(sigset_t) <= sizeof(words[1]));
    sigset_t mask{};
    if (pthread_sigmask(SIG_SETMASK, nullptr, &mask)) fail("cannot save native signal mask");
    std::memcpy(&words[1], &mask, sizeof(mask));
  }
  words[14] = 0; // Never save, restore, or overwrite Darwin's reserved x18.
  words[25] = thread_cookie();
  words[26] = token;
  words[27] = reinterpret_cast<uintptr_t>(address);
  words[28] = words[29] = words[30] = words[31] = 0;
  words[24] = checksum(words);
}
extern "C" void shiraz_guest_jump_validate(void *address, uintptr_t current_sp) noexcept {
  HostErrno preserve;
  auto *words = buffer(address);
  if ((words[0] & ~uint64_t(1)) != kMagic || words[27] != reinterpret_cast<uintptr_t>(address) ||
      words[24] != checksum(words)) fail("uninitialized, copied or corrupted jump buffer");
  if (words[25] != thread_cookie()) fail("jump belongs to another thread");
  if (!words[26] || words[26] != capture_boundary_token())
    fail("jump would cross a native ABI/provider frame or expired guest invocation");
  if ((words[3] & 15) || words[3] < current_sp || !words[2])
    fail("invalid or expired guest stack context");
  if (words[0] & 1) {
    sigset_t mask{};
    std::memcpy(&mask, &words[1], sizeof(mask));
    if (pthread_sigmask(SIG_SETMASK, &mask, nullptr)) fail("cannot restore native signal mask");
  }
}
extern "C" void shiraz_guest_setjmp();
extern "C" void shiraz_guest__setjmp();
extern "C" void shiraz_guest_sigsetjmp();
extern "C" void shiraz_guest_longjmp();
std::vector<ProviderExport> jump_exports() {
  std::vector<ProviderExport> result;
  auto add = [&](const char *name, void (*entry)()) {
    result.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
                      "direct-AAPCS64/guest-context-native-mask-boundary-checked", false});
  };
  add("setjmp", &shiraz_guest_setjmp);
  add("_setjmp", &shiraz_guest__setjmp);
  add("sigsetjmp", &shiraz_guest_sigsetjmp);
  for (const char *name : {"longjmp", "_longjmp", "siglongjmp"}) add(name, &shiraz_guest_longjmp);
  // No unverified __longjmp_chk alias: its public contract is not in the
  // pinned bionic header or required by the inspected OsmAnd imports.
  return result;
}
} // namespace shiraz::guest
