// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <iostream>
#include <mutex>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
namespace {
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
void abort_exit(int) { _exit(97); }
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: cleanup-probe fixture-directory");
    Loader *active = nullptr;
    CodeValidator validate = [&](const void *code) { return active && active->contains_code(code); };
    CleanupRuntime cleanup(validate);
    auto base = core_personality(); Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") {
        auto callbacks = cleanup.exports();
        provider.exports.insert(provider.exports.end(), callbacks.begin(), callbacks.end());
        auto tls = tls_exports(validate);
        provider.exports.insert(provider.exports.end(), tls.begin(), tls.end());
      }
      providers.add(std::move(provider));
    }
    Loader loader({argv[1]}, std::move(providers)); active = &loader;
    cleanup.attach(loader);
    auto *module = loader.open("libcleanup.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0]);
    };
    errno = ELOOP;
    require(call("cleanup_balanced") == 73 && !cleanup.pending() && errno == ELOOP,
            "real LIFO callbacks, nested/reused records, ignored pop and errno");
    std::mutex mutex; std::condition_variable condition;
    unsigned arrived = 0; bool release = false;
    std::atomic<bool> passed{true};
    std::thread workers[4];
    for (auto &worker : workers) worker = std::thread([&] {
      prepare_guest_tls();
      alignas(16) uint64_t frame[64]{};
      auto storage = Argument::integer(int64_t(reinterpret_cast<uintptr_t>(frame)), ArgumentType::Int64);
      bool good = call("cleanup_thread_stage", {Argument::integer(0), storage}) == 73;
      {
        std::unique_lock<std::mutex> lock(mutex);
        ++arrived; condition.notify_all();
        if (!condition.wait_for(lock, std::chrono::seconds(5), [&] { return release; })) good = false;
      }
      good = call("cleanup_thread_stage", {Argument::integer(1), storage}) == 73 && good;
      cleanup_require_empty();
      if (!good) passed = false;
    });
    bool all_arrived, all_pending;
    {
      std::unique_lock<std::mutex> lock(mutex);
      all_arrived = condition.wait_for(lock, std::chrono::seconds(5), [&] { return arrived == 4; });
      all_pending = cleanup.pending() == 4;
      release = true; condition.notify_all();
    }
    for (auto &worker : workers) worker.join();
    require(all_arrived && all_pending && passed && !cleanup.pending(), "four independent cleanup stacks");
    require(call("cleanup_late_count") == 4, "real late TLS destructors use live cleanup state");
    const char *failures[] = {"cleanup_bad_order", "cleanup_bad_metadata", "cleanup_bad_routine",
      "cleanup_null_execute", "cleanup_duplicate", "cleanup_underflow", "cleanup_over_budget", "cleanup_unbalanced"};
    for (const char *name : failures) {
      auto entry = reinterpret_cast<void *>(loader.symbol(module, name).address);
      pid_t child = fork(); require(child >= 0, "cleanup rejection child");
      if (!child) {
        std::signal(SIGABRT, abort_exit);
        (void)invoke(entry, {});
        if (std::string(name) == "cleanup_unbalanced") cleanup_require_empty();
        _exit(1);
      }
      int status;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "cleanup invariant rejected explicitly");
    }
    {
      auto entry = reinterpret_cast<void *>(loader.symbol(module, "cleanup_unbalanced").address);
      pid_t child = fork(); require(child >= 0, "cleanup unmap rejection child");
      if (!child) {
        std::signal(SIGABRT, abort_exit);
        (void)invoke(entry, {});
        loader.~Loader(); // Must reject active records before unmapping images.
        _exit(1);
      }
      int status;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "Loader pre-unmap guard rejects pending cleanup records");
    }
    auto finalized=std::make_shared<std::atomic<bool>>(false);
    {
      Loader *scope=nullptr;
      CodeValidator resident=[&](const void *p){return scope && scope->contains_code(p);};
      CleanupRuntime callbacks(resident);auto base=core_personality();Providers entries;
      for(const auto &pair:base.modules()){
        auto provider=pair.second;
        if(pair.first=="libc.so"){
          auto records=callbacks.exports(),tls=tls_exports(resident);
          provider.exports.insert(provider.exports.end(),records.begin(),records.end());
          provider.exports.insert(provider.exports.end(),tls.begin(),tls.end());
        }
        entries.add(std::move(provider));
      }
      Loader finalizing({argv[1]},std::move(entries));scope=&finalizing;callbacks.attach(finalizing);
      auto *image=finalizing.open("libcleanup.so");auto *entry=reinterpret_cast<void *>(finalizing.symbol(image,"cleanup_balanced").address);
      finalizing.set_finalizer([entry,finalized]{
        if(invoke(entry,{}).result_integers[0]!=73)throw ElfError("cleanup callback unavailable during DSO finalization");
        finalized->store(true);
      });
    }
    require(finalized->load(),"DSO finalizers can use balanced cleanup before its shutdown guard");
    cleanup_require_empty();
    cleanup.shutdown();
    std::cout << "M5_CLEANUP_PASS record_abi=24 lifo=1 nested_callback=1 suppressed_pop=1 threads=4 late_tls=4 finalizer_phase=1 fatal_rejections=9 pthread_exit_supported=0 cancellation_supported=0 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "cleanup-probe: " << error.what() << '\n'; return 1;
  }
}
