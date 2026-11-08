// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <atomic>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
struct Output { int count = 0; int values[64]{}; int errors = 0; };
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: lifecycle-probe fixture-directory");
    Loader *current = nullptr;
    auto lifecycle = std::make_shared<Lifecycle>([&](const void *p) { return current && current->contains_code(p); });
    Providers providers;
    auto base = native_personality();
    for (const auto &pair : base.modules()) {
      Provider module = pair.second;
      if (module.soname == "libc.so") {
        auto entries = lifecycle->exports();
        module.exports.insert(module.exports.end(), entries.begin(), entries.end());
      }
      providers.add(std::move(module));
    }
    auto loader = std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]}, std::move(providers));
    current = loader.get();
    lifecycle->attach(*loader);
    auto *module = loader->open("liblifecycle.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return invoke(reinterpret_cast<void *>(loader->symbol(module, name).address), arguments).result_integers[0];
    };
    require(lifecycle->pending() == 1 && loader->constructor_count() == 1,
            "real constructor registered guest destructor");
    Output first;
    call("lifecycle_stage", {pointer(&first)});
    call("lifecycle_finalize_a");
    const int expected[] = {5, 6, 3, 1, 0};
    require(!first.errors && first.count == 5 && std::equal(std::begin(expected), std::end(expected), first.values),
            "per-DSO LIFO including recursive finalization and registration");
    call("lifecycle_finalize_a");
    require(first.count == 5 && lifecycle->pending() == 2, "finalized callbacks never rerun");
    call("lifecycle_finalize_b");
    require(first.count == 6 && first.values[5] == 2 && lifecycle->pending() == 1, "other DSO remains isolated");
    call("lifecycle_finalize_all");
    call("lifecycle_finalize_all");
    require(first.count == 7 && first.values[6] == 4 && !lifecycle->pending(), "plain atexit callback runs once");
    auto exports = lifecycle->exports();
    auto find = [&](const char *name) {
      for (const auto &entry : exports) if (entry.name == name) return reinterpret_cast<void *>(entry.address);
      throw std::runtime_error("lifecycle test export missing");
    };
    auto *registration = find("__cxa_atexit");
    require(int(int32_t(invoke(registration, {pointer(nullptr), pointer(nullptr), pointer(nullptr)}).result_integers[0])) == -1 &&
                int(int32_t(invoke(registration, {pointer(reinterpret_cast<void *>(1)), pointer(nullptr), pointer(nullptr)}).result_integers[0])) == -1,
            "null and nonresident callback registrations fail");
    auto callback = reinterpret_cast<void *>(call("lifecycle_callback"));
    auto dso = reinterpret_cast<void *>(call("lifecycle_key_a"));
    std::atomic<int> successes{0};
    std::vector<std::thread> workers;
    for (int i = 0; i < 4; ++i) workers.emplace_back([&] {
      for (int j = 0; j < 8; ++j)
        if (!int(int32_t(invoke(registration, {pointer(callback), pointer(reinterpret_cast<void *>(7)), pointer(dso)}).result_integers[0]))) ++successes;
    });
    for (auto &worker : workers) worker.join();
    require(successes == 32 && lifecycle->pending() == 32, "concurrent native threads register guest callbacks");
    call("lifecycle_finalize_a");
    require(first.count == 39 && !lifecycle->pending(), "all concurrent registrations finalized once");
    Output after_unmap;
    call("lifecycle_shutdown_stage", {pointer(&after_unmap)});
    require(lifecycle->pending() == 2, "shutdown callbacks are pending");
    loader.reset();
    current = nullptr;
    require(lifecycle->closed() && !lifecycle->pending() && after_unmap.count == 2 &&
                after_unmap.values[0] == 4 && after_unmap.values[1] == 9 && !after_unmap.errors,
            "Loader drains guest callbacks before image unmap");
    lifecycle->shutdown();
    lifecycle->finalize();
    require(after_unmap.count == 2, "closed registry remains idempotent");
    require(int(int32_t(invoke(registration, {pointer(callback), pointer(nullptr), pointer(dso)}).result_integers[0])) == -1,
            "late process-resident adapter cannot register an unmapped callback");
    std::cout << "M5_LIFECYCLE_PASS constructor=1 lifo=1 per_dso=1 reentrant=1 plain=1 concurrent=32 before_unmap=1 late_rejected=1 host_atexit=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
