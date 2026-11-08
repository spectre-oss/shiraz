// SPDX-License-Identifier: GPL-3.0-only
// Single-thread reference-drop test using the real bridge and ART sigchain.
// Does not invoke UnloadNativeBridge inside a multithreaded ART runtime.
#include <nativebridge/native_bridge.h>
#include "sigchain.h"
#include <dlfcn.h>
#include <atomic>
#include <mach-o/dyld.h>
#include <pthread.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>

static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static std::vector<std::string> images() {
  const auto count = _dyld_image_count();
  require(count && count <= 1024, "image count bound");
  std::vector<std::string> result;
  for (uint32_t index = 0; index < count; ++index) {
    auto path = _dyld_get_image_name(index);
    require(path && std::strlen(path) < 4096, "image path bound");
    result.emplace_back(path);
  }
  require(_dyld_image_count() == count, "image inventory changed");
  return result;
}
static void emit_inventory() {
  auto paths = images();
  require(paths == images(), "stable end-of-main image inventory");
  std::printf("M5_NATIVE_CLOSURE_BEGIN count=%zu snapshot=end-of-main\n", paths.size());
  for (size_t index = 0; index < paths.size(); ++index) {
    std::printf("M5_NATIVE_CLOSURE_PATH index=%zu hex=", index);
    for (unsigned char byte : paths[index]) std::printf("%02x", unsigned(byte));
    std::putchar('\n');
  }
  std::printf("M5_NATIVE_CLOSURE_END count=%zu\n", paths.size());
}
static std::atomic<unsigned> foreign_deliveries{0};
static_assert(std::atomic<unsigned>::is_always_lock_free);
static void foreign_handler(int number) {
  if (number == SIGWINCH) foreign_deliveries.fetch_add(1, std::memory_order_relaxed);
}
int main(int argc, char **argv) {
  try {
    const bool foreign_owner = argc == 4 && !std::strcmp(argv[3], "--foreign-owner");
    const bool exhaust_keys = argc == 4 && !std::strcmp(argv[3], "--exhaust-keys");
    require(argc == 3 || foreign_owner || exhaust_keys,
            "usage: signal-lifetime-probe bridge sigchain [--foreign-owner|--exhaust-keys]");
    require(!setenv("SHIRAZ_GUEST_SIGNAL_CHAIN_CHECK", "1", 1), "probe environment");
    // Keep sigchain resident: its kernel handler still owns the disposition.
    auto chain = dlopen(argv[2], RTLD_NOW | RTLD_GLOBAL);
    require(chain, "load exact sigchain");
    auto add = reinterpret_cast<decltype(&art::AddSpecialSignalHandlerFn)>(
        dlsym(chain, "AddSpecialSignalHandlerFn"));
    require(add, "sigchain registration entry");
    auto owner = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    require(owner, "load exact bridge");
    auto table = static_cast<android::NativeBridgeCallbacks *>(dlsym(owner, "NativeBridgeItf"));
    auto snapshot = reinterpret_cast<uint64_t (*)()>(
        dlsym(owner, "shiraz_guest_signal_probe_snapshot"));
    require(table && table->version >= 2 && table->getSignalHandler && snapshot,
            "real bridge callbacks and snapshot");
    if (foreign_owner) {
      struct sigaction action{}, actual{};
      action.sa_handler = foreign_handler;
      sigemptyset(&action.sa_mask);
      require(!sigaction(SIGWINCH, &action, nullptr), "install foreign fixture action");
      require(!table->getSignalHandler(SIGWINCH), "refuse managed handler with foreign owner");
      require(!sigaction(SIGWINCH, nullptr, &actual) && actual.sa_handler == foreign_handler,
              "foreign host action remains installed");
      require(!raise(SIGWINCH) && foreign_deliveries.load(std::memory_order_relaxed) == 1 &&
                  snapshot() == 0,
              "foreign handler still receives signal without bridge dispatch");
      emit_inventory();
      std::puts("M5_SIGNAL_OWNER_REFUSAL_PASS foreign_handler_preserved=1 callback_advertised=0");
      return 0;
    }
    Dl_info before{};
    require(dladdr(reinterpret_cast<void *>(snapshot), &before), "initial bridge ownership");
    std::vector<pthread_key_t> keys;
    if (exhaust_keys) {
      // Exhaust only this subprocess's remaining keys after loading both
      // libraries, before any chain callback is registered or delivered.
      keys.reserve(65536);
      bool exhausted = false;
      for (unsigned index = 0; index < 65536; ++index) {
        pthread_key_t key;
        const int error = pthread_key_create(&key, nullptr);
        if (error == EAGAIN) { exhausted = true; break; }
        require(!error, "unexpected pthread key allocation error");
        keys.push_back(key);
      }
      require(exhausted, "pthread key exhaustion fixture bound");
    }
    for (int number = 1; number < NSIG; ++number) {
      auto handler = table->getSignalHandler(number);
      if (!handler) continue;
      require(number == SIGWINCH, "only fixture signal advertised");
      art::SigchainAction action{};
      action.sc_sigaction = handler;
      sigfillset(&action.sc_mask);
      action.sc_flags = art::SIGCHAIN_ALLOW_NORETURN;
      add(number, &action);
    }
    const uint64_t prefix = uint64_t(NSIG - 1) << 48;
    require(snapshot() == (prefix | (uint64_t(2) << 32) | (uint64_t(1) << 16) | 1),
            "real special handler claims then forwards to captured native action");
    require(!dlclose(owner), "drop owner bridge reference");
    Dl_info after{};
    require(dladdr(reinterpret_cast<void *>(snapshot), &after) &&
                before.dli_fbase == after.dli_fbase,
            "callback image remains resident after owner reference drop");
    require(!raise(SIGWINCH), "signal after reference drop");
    require(snapshot() == (prefix | (uint64_t(3) << 32) | (uint64_t(1) << 16) | 2),
            "retained special and native fallback callbacks survive reference drop");
    for (auto key : keys) require(!pthread_key_delete(key), "release fixture-owned keys");
    emit_inventory();
    std::puts("M5_SIGNAL_LIFETIME_PASS claimed=1 declined=2 forwarded=2 owner_reference_dropped=1");
    if (exhaust_keys)
      std::printf("M5_SIGNAL_KEY_EXHAUSTION_PASS keys=%zu first_signal_delivered=1\n", keys.size());
  } catch (const std::exception &error) {
    std::fprintf(stderr, "FAIL %s\n", error.what());
    return 1;
  }
}
