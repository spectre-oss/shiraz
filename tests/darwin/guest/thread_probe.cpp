// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <algorithm>
#include <atomic>
#include <csignal>
#include <filesystem>
#include <iostream>
#include <pthread.h>
#include <thread>
#include <set>
#include <string>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
// Detached threads can finish host key-destructor epilogues after notifying the
// guest. Match NativeBridge's process-resident image lifetime rather than racing
// an ELF unload against their final return instructions.
static Loader *resident = nullptr;
static Loader *other_resident = nullptr;
static void unprepared_refusal(int) { std::_Exit(0); }
static void foreign_stack_refusal(int) { (void)shiraz_guest_cached_pthread_self(); std::_Exit(2); }
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: thread-probe fixture-directory");
    const auto tables = cached_pthread_self_tables();
    require(tables.rows == 4096, "cached self table row bound");
    uintptr_t first = UINTPTR_MAX, last = 0;
    for (const auto &table : tables.tables) {
      require(table.address && table.bytes == 32768 && table.bytes <= UINTPTR_MAX - table.address,
              "cached self complete table extent");
      const uintptr_t end = table.address + uintptr_t(table.bytes);
      require(!cached_self_tables_disjoint_stack(table.address, end) &&
                  !cached_self_tables_disjoint_stack(table.address - 1, table.address + 1) &&
                  !cached_self_tables_disjoint_stack(end - 1, end + 1),
              "cache overlap refused without using cache memory as an actual stack");
      first = std::min(first, table.address);last = std::max(last, end);
    }
    require(!cached_self_tables_disjoint_stack(0, 1) &&
                !cached_self_tables_disjoint_stack(2, 2) &&
                !cached_self_tables_disjoint_stack(3, 2) &&
                cached_self_tables_disjoint_stack(first - 1, first) &&
                cached_self_tables_disjoint_stack(last, last + 1),
            "cached table exclusion checks empty ranges and exact boundaries");
    const auto native_high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
    require(cached_self_tables_disjoint_stack(native_high - pthread_get_stacksize_np(pthread_self()), native_high),
            "actual prepared stack is disjoint from all complete cache tables");
    const auto mrs_tables = mrs_access_tables();
    static_assert(sizeof(mrs_tables.tables) / sizeof(mrs_tables.tables[0]) == 5);
    require(mrs_tables.rows == 4096, "MRS accessor table row bound");
    std::set<std::string> mrs_roles;
    uintptr_t mrs_first = UINTPTR_MAX, mrs_last = 0;
    for (const auto &table : mrs_tables.tables) {
      require(table.role && mrs_roles.insert(table.role).second,
              "MRS accessor table roles are nonnull and unique");
      require(table.address > 1 && table.bytes == 32768 && table.bytes < UINTPTR_MAX - table.address,
              "MRS accessor complete table extent with room for edge probes");
      const uintptr_t end = table.address + uintptr_t(table.bytes);
      require(!mrs_access_tables_disjoint_stack(table.address, end) &&
                  !mrs_access_tables_disjoint_stack(table.address + 1, end - 1) &&
                  !mrs_access_tables_disjoint_stack(table.address, table.address + 1) &&
                  !mrs_access_tables_disjoint_stack(end - 1, end) &&
                  !mrs_access_tables_disjoint_stack(table.address - 1, table.address + 1) &&
                  !mrs_access_tables_disjoint_stack(end - 1, end + 1),
              "every MRS table refuses whole, interior and boundary-crossing overlap");
      if (std::string(table.role) == "bases")
        require(cached_self_tables_disjoint_stack(table.address, end) &&
                    !mrs_access_tables_disjoint_stack(table.address, end),
                "fifth MRS bases table adds exclusion beyond the four cached-self tables");
      for (const auto &other : mrs_tables.tables) {
        if (&table == &other) continue;
        require(other.bytes <= UINTPTR_MAX - other.address &&
                    (end <= other.address || other.address + other.bytes <= table.address),
                "MRS accessor table extents are pairwise disjoint");
      }
      mrs_first = std::min(mrs_first, table.address);
      mrs_last = std::max(mrs_last, end);
    }
    require(mrs_roles == std::set<std::string>({"owners", "keys", "low", "high", "bases"}),
            "MRS accessor snapshot includes exactly the five read tables");
    require(mrs_first > 1 && mrs_last < UINTPTR_MAX &&
                !mrs_access_tables_disjoint_stack(0, 1) &&
                !mrs_access_tables_disjoint_stack(0, UINTPTR_MAX) &&
                !mrs_access_tables_disjoint_stack(2, 2) &&
                !mrs_access_tables_disjoint_stack(3, 2) &&
                !mrs_access_tables_disjoint_stack(UINTPTR_MAX, 0) &&
                mrs_access_tables_disjoint_stack(mrs_first - 1, mrs_first) &&
                mrs_access_tables_disjoint_stack(mrs_last, mrs_last + 1),
            "MRS table exclusion refuses invalid bounds and admits adjacent outside-union ranges");
    require(mrs_access_tables_disjoint_stack(native_high - pthread_get_stacksize_np(pthread_self()), native_high),
            "actual native stack is disjoint from all five complete MRS accessor tables");
    // Extent predicates only: these synthetic overlap ranges are never used as
    // actual stacks, and the snapshot grants no row-lifetime or consumer proof.
    std::cout << "M5_MRS_TABLE_EXCLUSION_PASS tables=5 rows=4096 predicate_only=1 "
                 "execution_claim=0 consumer_proof=0\n";
    const pid_t child = fork();
    require(child >= 0, "fork unprepared identity check");
    if (!child) {
      if (guest_tls_prepared() || std::signal(SIGTRAP, unprepared_refusal) == SIG_ERR) std::_Exit(1);
      (void)shiraz_guest_cached_pthread_self();
      std::_Exit(2);
    }
    int status = 0;
    pid_t waited;
    do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    require(waited == child && WIFEXITED(status) && WEXITSTATUS(status) == 0,
            "unprepared pthread identity must trap rather than return a value");
    std::atomic<unsigned> identity_errors{0};
    std::atomic<unsigned> identity_ready{0};
    uintptr_t native_keys[4]{};
    std::thread identity_workers[4];
    for (unsigned index = 0; index < 4; ++index) identity_workers[index] = std::thread([&, index] {
      prepare_guest_tls();
      uintptr_t key;
      asm volatile("mrs %0, tpidrro_el0" : "=r"(key));
      native_keys[index] = key;
      ++identity_ready;
      while (identity_ready < 4) std::this_thread::yield();
      const auto expected = reinterpret_cast<uintptr_t>(pthread_self());
      for (unsigned i = 0; i < 5000; ++i) {
        errno = ELOOP;
        *guest_errno_location() = EINVAL;
        if (shiraz_guest_cached_pthread_self() != expected) ++identity_errors;
        if (errno != ELOOP || *guest_errno_location() != EINVAL) ++identity_errors;
        uintptr_t observed;
        asm volatile("mrs %0, tpidrro_el0" : "=r"(observed));
        if (observed != key) ++identity_errors;
        if (!(i % 16)) std::this_thread::yield();
      }
    });
    for (auto &worker : identity_workers) worker.join();
    require(!identity_errors, "cached identity matches native workers and preserves errnos");
    require(std::set<uintptr_t>(native_keys, native_keys + 4).size() == 4 &&
                std::set<uintptr_t>(native_keys, native_keys + 4).count(0) == 0,
            "live native read-only TLS keys are unique and nonzero");
    prepare_guest_tls();
    std::atomic<uintptr_t> foreign_low{0};
    std::atomic<bool> foreign_stop{false};
    std::thread parked([&] {
      prepare_guest_tls();
      auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
      auto size = pthread_get_stacksize_np(pthread_self());
      if (size < 192 * 1024) std::_Exit(1);
      foreign_low = high - size;
      while (!foreign_stop) std::this_thread::yield();
    });
    while (!foreign_low) std::this_thread::yield();
    const pid_t alternate = fork();
    require(alternate >= 0, "fork foreign-stack identity check");
    if (!alternate) {
      stack_t stack{};stack.ss_sp = reinterpret_cast<void *>(foreign_low + 64 * 1024);stack.ss_size = 64 * 1024;
      struct sigaction action{};action.sa_handler = foreign_stack_refusal;action.sa_flags = SA_ONSTACK;
      sigemptyset(&action.sa_mask);
      if (sigaltstack(&stack, nullptr) || sigaction(SIGUSR1, &action, nullptr) ||
          std::signal(SIGTRAP, unprepared_refusal) == SIG_ERR) std::_Exit(1);
      raise(SIGUSR1);std::_Exit(3);
    }
    do { waited = waitpid(alternate, &status, 0); } while (waited < 0 && errno == EINTR);
    foreign_stop = true;parked.join();
    require(waited == alternate && WIFEXITED(status) && WEXITSTATUS(status) == 0,
            "SP inside another prepared thread stack must not return that thread's identity");
    std::cout << "M5_SELF_CACHE_PASS workers=4 checks=20000 native_identity=1 errnos=1 unprepared_trap=1 foreign_stack_trap=1 native_keys=unique-stable-observed\n";
    resident = new Loader({std::filesystem::canonical(argv[1])}, native_personality(
        [](const void *code) { return resident && resident->contains_code(code); }));
    auto &loader = *resident;
    auto *module = loader.open("libthread.so");
    require(loader.image_count() == 1, "thread APIs use explicit providers");
    auto call = [&](const char *name, std::vector<Argument> arguments) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments)
          .result_integers[0];
    };
    auto self = loader.symbol(module, "pthread_self");
    require(self.abi == "prepared/pthread-self-noargs" &&
                self.address == reinterpret_cast<uintptr_t>(&shiraz_guest_cached_pthread_self),
            "pthread_self binds the prepared-stack no-argument identity adapter");
    errno = ELOOP;
    require(call("self_identity", {}) == reinterpret_cast<uintptr_t>(pthread_self()) &&
                errno == ELOOP && *guest_errno_location() == 731,
            "pthread_self returns the native thread identity and preserves both errnos");
    other_resident = new Loader({std::filesystem::canonical(argv[1])}, native_personality(
        [](const void *code) { return other_resident && other_resident->contains_code(code); }));
    auto *other_module = other_resident->open("libthread.so");
    auto other_call = [&](const char *name, std::vector<Argument> arguments) {
      return invoke(reinterpret_cast<void *>(other_resident->symbol(other_module, name).address), arguments)
          .result_integers[0];
    };
    int namespace_values[2] = {71, 73};
    require(int(call("key_namespace_create", {})) == 0 &&
                int(other_call("key_namespace_create", {})) == 0 &&
                !call("key_namespace_get", {}) && !other_call("key_namespace_get", {}),
            "independent loader key registries begin empty on the same thread");
    require(int(call("key_namespace_set", {pointer(&namespace_values[0])})) == 0 &&
                int(other_call("key_namespace_set", {pointer(&namespace_values[1])})) == 0 &&
                call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[0]) &&
                other_call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[1]),
            "guest key values include registry identity rather than only a numeric key");
    require(int(call("key_namespace_delete", {})) == 0 &&
                other_call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[1]) &&
                int(other_call("key_namespace_delete", {})) == 0,
            "deleting a key in one guest registry cannot clear another registry value");
    errno = ELOOP;
    int result = int(call("run_native_attributes", {}));
    if (result) throw std::runtime_error("guest native attributes failed at line " + std::to_string(result));
    require(errno == ELOOP, "native attribute snapshot preserves host errno");
    result = int(call("run_threads", {}));
    if (result) throw std::runtime_error("guest thread failed at line " + std::to_string(result));
    require(errno == ELOOP, "pthread providers preserve host errno");
    struct { uint32_t before = 0xabcdef01, key = 0, after = 0x12345678; } guarded;
    require(int(call("pthread_key_create", {pointer(&guarded.key), pointer(nullptr)})) == 0 &&
                guarded.before == 0xabcdef01 && guarded.after == 0x12345678,
            "key creation writes only four bytes");
    int value = 73;
    uint64_t dirty_key = 0xabababab00000000ULL | guarded.key;
    require(int(call("pthread_setspecific", {Argument::integer(dirty_key, ArgumentType::Int64), pointer(&value)})) == 0 &&
                call("pthread_getspecific", {Argument::integer(dirty_key, ArgumentType::Int64)}) ==
                    reinterpret_cast<uintptr_t>(&value),
            "key arguments ignore unspecified upper 32 bits");
    require(int(call("pthread_key_delete", {Argument::integer(guarded.key)})) == 0,
            "key cleanup");
    result = int(call("run_key_lifecycle", {}));
    if (result) throw std::runtime_error("guest key lifecycle failed at line " + std::to_string(result));
    require(errno == ELOOP, "key cleanup providers preserve host errno");
    std::cout << "M5_THREAD_PASS create=6 join=4 identity=1 errno=1 attr_guards=1 "
                 "key_guards=1 key_width=1 destructors=12 detached=2 callback_validation=1 "
                 "key_iteration_threads=4 key_iterations=4 key_clear_before_callback=1 "
                 "key_reset_final_pass=1 key_create_in_destructor=1 key_delete_suppresses=1 "
                 "key_delete_in_destructor=1 key_namespaces=2 self_noargs=1 self_errnos=1 "
                 "attr_snapshot_workers=6 attr_detached_snapshot=2 native_detach_unknown=1 "
                 "attr_guard_set_known=7 attr_snapshot_create_refused=1 custom_stack=unsupported\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
