// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include <array>
#include <atomic>
#include <cstring>
#include <cstdlib>
#include <limits.h>
#include <filesystem>
#include <iostream>
#include <mutex>
#include <set>
#include <thread>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
static Loader *resident;
static std::atomic<int> late_errors{0}, late_callbacks{0}, host_late_calls{0};
static uintptr_t late_registration, late_destructor, late_first_use;
static pthread_key_t late_key;
static constexpr int late_rounds = PTHREAD_DESTRUCTOR_ITERATIONS;
// The owner stays published until join: native TSD callbacks may run after
// host language TLS bookkeeping has already been destroyed.
static std::mutex allocations_mutex;
static pthread_t allocations_owner = nullptr;
static std::set<uintptr_t> owned_allocations;
static size_t allocation_calls, free_calls;
static bool tracking_this_thread() {
  return allocations_owner && pthread_equal(allocations_owner, pthread_self());
}
static void *tracked_malloc(size_t size) {
  void *result = std::malloc(size);
  std::lock_guard<std::mutex> lock(allocations_mutex);
  if (result && tracking_this_thread()) {
    owned_allocations.insert(reinterpret_cast<uintptr_t>(result));
    ++allocation_calls;
  }
  return result;
}
static void *tracked_calloc(size_t count, size_t size) {
  void *result = std::calloc(count, size);
  std::lock_guard<std::mutex> lock(allocations_mutex);
  if (result && tracking_this_thread()) {
    owned_allocations.insert(reinterpret_cast<uintptr_t>(result));
    ++allocation_calls;
  }
  return result;
}
static void *tracked_realloc(void *pointer, size_t size) {
  uintptr_t old = reinterpret_cast<uintptr_t>(pointer);
  void *result = std::realloc(pointer, size);
  std::lock_guard<std::mutex> lock(allocations_mutex);
  if (result || !size) free_calls += owned_allocations.erase(old);
  if (result && tracking_this_thread()) {
    owned_allocations.insert(reinterpret_cast<uintptr_t>(result));
    ++allocation_calls;
  }
  return result;
}
static void tracked_free(void *pointer) {
  {
    std::lock_guard<std::mutex> lock(allocations_mutex);
    free_calls += owned_allocations.erase(reinterpret_cast<uintptr_t>(pointer));
  }
  std::free(pointer);
}
static Providers allocation_tracking_personality() {
  auto original = native_personality(
      [](const void *code) { return resident && resident->contains_code(code); });
  Providers result;
  unsigned replacements = 0;
  for (const auto &module : original.modules()) {
    Provider provider = module.second;
    if (provider.soname == "libc.so") for (auto &entry : provider.exports) {
      if (entry.name == "malloc") {
        entry = fixed_export("malloc", entry.version, &tracked_malloc, ErrnoPolicy::Null);
        ++replacements;
      } else if (entry.name == "calloc") {
        entry = fixed_export("calloc", entry.version, &tracked_calloc, ErrnoPolicy::Null);
        ++replacements;
      } else if (entry.name == "realloc") {
        entry = fixed_export("realloc", entry.version, &tracked_realloc, ErrnoPolicy::Observe);
        ++replacements;
      } else if (entry.name == "free") {
        entry = fixed_export("free", entry.version, &tracked_free);
        ++replacements;
      }
    }
    result.add(std::move(provider));
  }
  if (replacements != 4) throw std::runtime_error("four explicit allocation adapters required");
  return result;
}
struct HostLateCleanup {
  bool armed = false;
  ~HostLateCleanup() {
    if (!armed) return;
    GuestTlsCleanupScope cleanup;
    prepare_guest_tls();
    if (!guest_tls_prepared()) ++late_errors;
    auto result = invoke(reinterpret_cast<void *>(late_registration), {
        Argument::integer(late_destructor, ArgumentType::Int64),
        Argument::integer(0, ArgumentType::Int64), Argument::integer(0, ArgumentType::Int64)});
    if (int(result.result_integers[0])) ++late_errors;
    ++host_late_calls;
  }
};
static thread_local HostLateCleanup host_late_cleanup;
static void late_host_cleanup(void *) {
  GuestTlsCleanupScope cleanup;
  prepare_guest_tls();
  if (!guest_tls_prepared()) ++late_errors;
  auto result = invoke(reinterpret_cast<void *>(late_registration), {
      Argument::integer(late_destructor, ArgumentType::Int64),
      Argument::integer(0, ArgumentType::Int64), Argument::integer(0, ArgumentType::Int64)});
  if (int(result.result_integers[0])) ++late_errors;
  int round = ++late_callbacks;
  if (round == late_rounds) {
    auto first = invoke(reinterpret_cast<void *>(late_first_use), {});
    if (int(first.result_integers[0])) ++late_errors;
  }
  if (round < late_rounds && pthread_setspecific(late_key, reinterpret_cast<void *>(1)))
    ++late_errors;
}
static pthread_key_t unowned_key;
static void *unowned_entry;
static std::atomic<int> unowned_rounds{0}, unowned_refusals{0}, unowned_executed{0};
static void unowned_host_cleanup(void *) {
  const int round = ++unowned_rounds;
  try { (void)invoke(unowned_entry, {}); }
  catch (const std::runtime_error &error) {
    if (!std::strcmp(error.what(), "unsupported guest entry during unowned native thread teardown") &&
        !guest_tls_prepared()) ++unowned_refusals;
  }
  if (round < late_rounds && pthread_setspecific(unowned_key, reinterpret_cast<void *>(1)))
    std::abort();
}
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: tls-probe fixture-directory");
    resident = new Loader({std::filesystem::canonical(argv[1])}, allocation_tracking_personality());
    auto &loader = *resident;
    auto *module = loader.open("libtls-fixture.so");
    require(loader.image_count() == 3,
            "genuine guest fixture, peer DSO and unchanged compiler-rt execute");
    auto exercise = loader.symbol(module, "tls_exercise").address;
    std::array<int, 4> results{};
    std::array<uintptr_t, 4> bases{}, guards{};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      prepare_guest_tls();
      auto *slots = static_cast<uintptr_t *>(guest_tls_base());
      bases[i] = reinterpret_cast<uintptr_t>(slots);
      guards[i] = slots[5];
      slots[10] = uintptr_t(i + 17);
      results[i] = int(invoke(reinterpret_cast<void *>(exercise), {Argument::integer(i)})
                         .result_integers[0]);
      if (slots[10] != uintptr_t(i + 17)) results[i] = -1;
    });
    for (auto &thread : threads) thread.join();
    for (int i = 0; i < 4; ++i) {
      require(results[i] == 0 && bases[i] && bases[i] % 16 == 0 && guards[i] &&
                  guards[i] == guards[0], "isolated TLS storage and nonzero process guard");
      for (int j = 0; j < i; ++j) require(bases[i] != bases[j], "distinct concurrent TLS blocks");
    }
    auto call = [&](const char *name) {
      return int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), {})
                     .result_integers[0]);
    };
    require(call("tls_summary") == 0, "host-created threads: C++ TLS LIFO/destructor registration and storage lifetime");
    require(call("tls_guest_threads") == 0, "guest-created threads: real compiler-emulated TLS and destructors");
    auto registration = loader.symbol(module, "__cxa_thread_atexit_impl").address;
    auto result = invoke(reinterpret_cast<void *>(registration), {
        Argument::integer(1, ArgumentType::Int64), Argument::integer(0, ArgumentType::Int64),
        Argument::integer(0, ArgumentType::Int64)});
    require(int(result.result_integers[0]) == -1 && *guest_errno_location() == errno_to_guest(EINVAL),
            "thread destructor rejects non-guest function pointers");
    late_registration = registration;
    late_destructor = loader.symbol(module, "tls_late_destructor").address;
    late_first_use = loader.symbol(module, "tls_late_first_use").address;
    require(pthread_key_create(&late_key, &late_host_cleanup) == 0, "late native key");
    std::thread late_worker([&] {
      {
        std::lock_guard<std::mutex> lock(allocations_mutex);
        allocations_owner = pthread_self();
      }
      prepare_guest_tls();
      if (pthread_setspecific(late_key, reinterpret_cast<void *>(1))) ++late_errors;
    });
    late_worker.join();
    size_t late_allocations, late_frees, late_outstanding;
    {
      std::lock_guard<std::mutex> lock(allocations_mutex);
      allocations_owner = nullptr;
      late_allocations = allocation_calls;
      late_frees = free_calls;
      late_outstanding = owned_allocations.size();
    }
    std::cerr << "M5_TLS_LATE_ALLOCATIONS allocated=" << late_allocations
              << " released=" << late_frees << " outstanding=" << late_outstanding << '\n';
    require(call("tls_late_first_summary") == 0,
            "last native round first-use emutls preserves template, zero, alignment and nested destructors");
    require(late_allocations >= 2 && late_outstanding == 0,
            "last native round first-use emutls frees actual array and object allocations before join");
    require(pthread_key_delete(late_key) == 0, "delete late native key");
    require(late_callbacks.load() == late_rounds && late_errors.load() == 0, "late native key callback has prepared live TLS");
    late_registration = registration;
    late_destructor = loader.symbol(module, "tls_late_destructor").address;
    std::thread host_late_worker([] {
      host_late_cleanup.armed = true;
      prepare_guest_tls();
    });
    host_late_worker.join();
    require(host_late_calls.load() == 1 && late_errors.load() == 0,
            "scoped host TLS destructor can reenter and register guest cleanup");
    int late_summary = int(invoke(reinterpret_cast<void *>(loader.symbol(module, "tls_late_summary").address),
                                 {Argument::integer(2 * (late_rounds + 1))}).result_integers[0]);
    std::cerr << "M5_TLS_LATE_SUMMARY result=" << late_summary << " expected=" << 2 * (late_rounds + 1) << '\n';
    require(late_summary == 0, "late cleanup drains nested guest destructors before thread join");
    unowned_entry = closure([](Capture &) { ++unowned_executed; });
    require(pthread_key_create(&unowned_key, unowned_host_cleanup) == 0, "unowned native key");
    std::thread unowned_worker([] {
      if (pthread_setspecific(unowned_key, reinterpret_cast<void *>(1))) std::abort();
    });
    unowned_worker.join();
    require(pthread_key_delete(unowned_key) == 0 && unowned_rounds == late_rounds &&
                unowned_refusals == late_rounds && unowned_executed == 0,
            "unowned native teardown refuses before guest code or TLS allocation in every round");
    std::cout << "M5_TLS_UNOWNED_EXIT_REFUSAL_PASS rounds=" << late_rounds << " guest_entered=0 tls_prepared=0\n";
    auto failure_binding = loader.symbol(module, "__stack_chk_fail");
    require(failure_binding.nonreturning && failure_binding.kind == SymbolKind::Function &&
                failure_binding.abi == "fixed/nonreturning-noargs",
            "selected stack-check provider exposes explicit nonreturning contract");
    require(!loader.symbol(module, "tls_late_summary").nonreturning &&
                !loader.symbol(module, "__cxa_thread_atexit_impl").nonreturning,
            "ordinary guest and provider functions do not infer nonreturning behavior");
    auto failure = failure_binding.address;
    pid_t child = fork();
    require(child >= 0, "stack failure child");
    if (!child) {
      std::signal(SIGABRT, [](int) { _exit(97); });
      invoke(reinterpret_cast<void *>(failure), {});
      _exit(98);
    }
    int status;
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
            "stack-check failure aborts instead of reporting success");
    std::cout << "M5_TLS_PASS emutls=upstream peer_dso=1 templates=1 zero=1 alignment=128 "
                 "threads=8 destructors=24 lifo=1 register_during_exit=1 isolated_blocks=1 guard_nonzero=1 "
                 "late_native_key_rounds=4 scoped_late_reentry=1 late_nested_destructors=10 "
                 "late_first_emutls=1 late_first_allocations_balanced=1 late_first_destructors=2 "
                 "nonreturning_contract=1 tpidr_rewritten=0 pt_tls=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
