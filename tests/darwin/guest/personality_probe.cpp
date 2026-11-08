// SPDX-License-Identifier: GPL-3.0-only
#include "guest_errno.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <condition_variable>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
#include <iostream>
#include <mutex>
#include <malloc/malloc.h>
#include <cstring>
#include <cstdlib>
#include <thread>

using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value)
    throw std::runtime_error(message);
}
static void host_returns() {}
static long host_fail() {
  errno = EAGAIN;
  return -1;
}
static void host_barrier() {
  static std::mutex mutex;
  static std::condition_variable condition;
  static unsigned arrived = 0;
  std::unique_lock<std::mutex> lock(mutex);
  if (++arrived == 4)
    condition.notify_all();
  else if (!condition.wait_for(lock, std::chrono::seconds(5),
                               [&] { return arrived == 4; }))
    throw std::runtime_error("guest errno thread barrier timeout");
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: personality-probe fixture-directory");
    bool rejected_object = false, rejected_null = false, rejected_empty = false;
    try {
      Providers invalid;
      int object = 0;
      invalid.add({"libbad_effect.so", {},
                   {{"object", "", reinterpret_cast<uintptr_t>(&object),
                     SymbolKind::Object, "fixture/object", false, true}}, false});
    } catch (const ElfError &) { rejected_object = true; }
    try { (void)explicit_noreturn_export("missing", "", nullptr); }
    catch (const ElfError &) { rejected_null = true; }
    try { (void)explicit_noreturn_closure_export("missing", "", {}); }
    catch (const ElfError &) { rejected_empty = true; }
    require(rejected_object && rejected_null && rejected_empty,
            "nonreturning metadata rejects objects, null pointers and empty closures");
    auto registry = core_personality();
    Provider helper{"libfixture_host.so", {}, {}, false};
    helper.exports.push_back(
        fixed_export("host_fail", "", &host_fail, ErrnoPolicy::MinusOne));
    helper.exports.push_back(fixed_export("host_barrier", "", &host_barrier));
    helper.exports.push_back(explicit_noreturn_export("declared_terminal", "", &host_returns));
    auto cookie = std::make_shared<int>(42);
    helper.exports.push_back(explicit_noreturn_closure_export("captured_terminal", "", [cookie] {
      if (*cookie != 42) _exit(95);
    }));
    cookie.reset();
    registry.add(std::move(helper));
    Provider strict{"libstrict.so", {"STRICT_1"}, {}, false};
    strict.exports.push_back(fixed_export("plain", "", &host_fail));
    strict.exports.push_back(fixed_export("named", "STRICT_1", &host_fail));
    registry.add(std::move(strict));
    Loader loader({argv[1]}, std::move(registry));
    auto *strict_module = loader.link("libstrict.so");
    require(loader.symbol(strict_module, "plain").found &&
                loader.symbol(strict_module, "named", "STRICT_1").found,
            "explicit provider default and exact version lookups");
    for (const char *name : {"plain", "named"}) {
      bool rejected = false;
      try { loader.symbol(strict_module, name, "UNKNOWN_VERSION"); }
      catch (const ElfError &) { rejected = true; }
      require(rejected, "versioned provider rejects unknown named version");
    }
    auto *helper_module = loader.link("libfixture_host.so");
    for (const char *name : {"declared_terminal", "captured_terminal"}) {
      auto terminal = loader.symbol(helper_module, name);
      require(terminal.nonreturning &&
                  !loader.symbol(helper_module, "host_fail").nonreturning,
              "selected provider contract is explicit rather than name-inferred");
      pid_t terminal_child = fork();
      require(terminal_child >= 0, "nonreturning contract child");
      if (!terminal_child) {
        std::signal(SIGABRT, [](int) { _exit(96); });
        invoke(reinterpret_cast<void *>(terminal.address), {});
        _exit(98);
      }
      int terminal_status;
      require(waitpid(terminal_child, &terminal_status, 0) == terminal_child &&
                  WIFEXITED(terminal_status) && WEXITSTATUS(terminal_status) == 96,
              "returning pointer and captured implementations cannot violate the terminal contract");
    }
    auto *module = loader.open("libpersonality.so");
    require(loader.image_count() == 1,
            "personality modules must not load link-only ELF stubs");
    auto call = [&](const char *name) {
      return int(
          invoke(reinterpret_cast<void *>(loader.symbol(module, name).address),
                 {})
              .result_integers[0]);
    };
    errno = ELOOP;
    require(call("memory_test") == 73, "memory/allocation personality");
    require(errno == ELOOP, "host errno preserved by memory adapters");
    auto allocation = [&](const char *name, std::vector<Argument> arguments) {
      errno = ELOOP;
      auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments)
          .result_integers[0];
      require(errno == ELOOP, "managed allocation boundary preserves host errno");
      return result;
    };
    auto integer = [](uint64_t value) { return Argument::integer(value, ArgumentType::Int64); };
    auto checked_errno = [&] {
      require(errno == ELOOP && call("allocation_errno") == 777 && errno == ELOOP,
              "managed allocation success preserves both host and guest errno");
    };
    auto live = [&](uintptr_t pointer, size_t bytes) {
      require(pointer && registered_guest_heap_contains(pointer, bytes),
              "returned allocation is registered before guest return");
      const auto usable = malloc_size(reinterpret_cast<void *>(pointer));
      require(usable >= bytes && registered_guest_heap_contains(pointer, usable) &&
                  !registered_guest_heap_contains(pointer, uint64_t(usable) + 1) &&
                  !registered_guest_heap_contains(pointer, UINT64_MAX),
              "registered extent includes usable storage and refuses crossing/overflow");
    };
    auto release = [&](uintptr_t pointer) {
      require(allocation("allocation_free", {integer(pointer)}) == 73,
              "guest allocator free completes");
      require(!registered_guest_heap_contains(pointer, 1),
              "known free removes the allocation reservation");
      checked_errno();
    };
    auto pointer = uintptr_t(allocation("allocation_malloc", {integer(96)}));
    live(pointer, 96);
    checked_errno();
    uintptr_t transferred[4]{};
    bool transfer_ok[4]{};
    std::vector<std::thread> producers;
    for (int i = 0; i < 4; ++i) producers.emplace_back([&, i] {
      try {
        transferred[i] = allocation("allocation_malloc", {integer(64)});
        live(transferred[i], 64);
        checked_errno();
        transfer_ok[i] = true;
      } catch (...) {}
    });
    for (auto &thread : producers) thread.join();
    for (bool success : transfer_ok) require(success, "guest allocation producer completes");
    std::vector<std::thread> consumers;
    for (int i = 0; i < 4; ++i) consumers.emplace_back([&, i] {
      try { release(transferred[(i + 1) % 4]); }
      catch (...) { transfer_ok[i] = false; }
    });
    for (auto &thread : consumers) thread.join();
    for (bool success : transfer_ok)
      require(success, "managed allocation survives producer TLS exit and another thread frees it");
    auto *payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 96; ++index)
      payload[index] = static_cast<unsigned char>(index * 7 + 3);
    require(!allocation("allocation_realloc", {integer(pointer), integer(UINT64_MAX)}) &&
                call("allocation_errno") == bionic::kENOMEM && errno == ELOOP,
            "unrepresentable realloc size fails before the native allocator");
    live(pointer, 96);
    for (size_t index = 0; index < 96; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "failed realloc retains the old allocation and payload");
    auto previous = pointer;
    pointer = uintptr_t(allocation("allocation_realloc", {integer(pointer), integer(1024 * 1024)}));
    live(pointer, 1024 * 1024);
    checked_errno();
    // A moving realloc may coalesce into a lower block which now contains
    // the old numeric address. The snapshot has no allocation-generation
    // identity, so assert old-address removal only outside the new extent.
    if (pointer != previous &&
        (previous < pointer || previous - pointer >= malloc_size(reinterpret_cast<void *>(pointer))))
      require(!registered_guest_heap_contains(previous, 1),
              "moved realloc removes its previous allocation reservation");
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 96; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "growing realloc preserves the old payload");
    previous = pointer;
    pointer = uintptr_t(allocation("allocation_realloc", {integer(pointer), integer(17)}));
    live(pointer, 17);
    checked_errno();
    if (pointer != previous &&
        (previous < pointer || previous - pointer >= malloc_size(reinterpret_cast<void *>(pointer))))
      require(!registered_guest_heap_contains(previous, 1),
              "shrinking moved realloc removes its previous reservation");
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 17; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "shrinking realloc preserves the retained payload");
    release(pointer);
    require(!allocation("allocation_calloc", {integer(UINT64_MAX), integer(2)}) &&
                call("allocation_errno") == bionic::kENOMEM && errno == ELOOP,
            "calloc multiplication overflow fails before the native allocator");
    pointer = uintptr_t(allocation("allocation_calloc", {integer(8), integer(16)}));
    live(pointer, 128);
    checked_errno();
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 128; ++index)
      require(payload[index] == 0, "managed calloc zeroes the requested payload");
    release(pointer);
    pointer = uintptr_t(allocation("allocation_realloc", {integer(0), integer(48)}));
    live(pointer, 48);
    checked_errno();
    release(pointer);
    // Host-allocated pointers are valid inputs to the existing native-backed
    // personality. They are not described as managed until a successful
    // guest realloc returns them through the managed boundary.
    auto *foreign = static_cast<unsigned char *>(std::malloc(64));
    require(foreign, "host foreign allocation");
    std::memset(foreign, 0x5a, 64);
    auto foreign_address = reinterpret_cast<uintptr_t>(foreign);
    require(!registered_guest_heap_contains(foreign_address, 1),
            "foreign native allocation has no managed reservation");
    pointer = uintptr_t(allocation("allocation_realloc", {integer(foreign_address), integer(4096)}));
    live(pointer, 4096);
    checked_errno();
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 64; ++index)
      require(payload[index] == 0x5a, "foreign realloc retains its original payload");
    release(pointer);
    foreign = static_cast<unsigned char *>(std::malloc(64));
    require(foreign, "host foreign allocation for guest free");
    foreign_address = reinterpret_cast<uintptr_t>(foreign);
    require(allocation("allocation_free", {integer(foreign_address)}) == 73 &&
                !registered_guest_heap_contains(foreign_address, 1),
            "guest free preserves compatibility with a foreign native allocation");
    checked_errno();
    require(call("parse_test") == 73 && errno == ELOOP,
            "guest errno input and strtol sentinel semantics");
    require(call("failure_test") == 73 && errno == ELOOP,
            "Darwin EAGAIN translated to bionic errno");
    require(errno_to_guest(EAGAIN) == bionic::kEAGAIN &&
                errno_to_guest(EDEADLK) == bionic::kEDEADLK &&
                errno_to_guest(ENOSYS) == bionic::kENOSYS &&
                errno_to_guest(ELOOP) == bionic::kELOOP,
            "nonmatching host/guest errno values");
    int results[4]{};
    void *function =
        reinterpret_cast<void *>(loader.symbol(module, "thread_errno").address);
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i)
      threads.emplace_back([&, i] {
        results[i] = int(
            invoke(function, {Argument::integer(700 + i)}).result_integers[0]);
      });
    for (auto &thread : threads)
      thread.join();
    for (int i = 0; i < 4; ++i)
      require(results[i] == 700 + i, "guest errno is isolated per thread");
    size_t personality = 0;
    for (const auto &record : loader.bindings()) {
      if (!record.binding.found || record.binding.abi == "guest-AAPCS64")
        continue;
      ++personality;
      require(record.binding.provider == "libc.so" ||
                  record.binding.provider == "libfixture_host.so",
              "explicit personality provider identity");
    }
    require(personality >= 10,
            "binding evidence must cover the actual imported APIs");
    std::cout
        << "M5_PERSONALITY_PASS memory=1 alloc=1 strtol=1 errno_translation=1 "
           "errno_threads=4 host_errno_preserved=1 nonreturning_contract=1 "
           "managed_allocations=1 realloc_null=1 realloc_payload=1 realloc_failure_retained=1 "
           "calloc_overflow=1 foreign_allocations=1 cross_thread_free=4 link_stubs_executed=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
