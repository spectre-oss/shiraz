// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: dynamic-probe fixture-directory");
    Loader *current = nullptr;
    auto mutex = std::make_shared<std::recursive_mutex>();
    size_t publications = 0;
    auto providers = dynamic_personality(native_personality([&](const void *p) {
      return current && current->contains_code(p);
    }), {[&] { return current; }, mutex, [&] { ++publications; }});
    Loader loader({argv[1]}, std::move(providers));
    current = &loader;
    auto *services = loader.link("libdl.so");
    auto service = [&](const char *name, const std::vector<Argument> &arguments) {
      return invoke(reinterpret_cast<void *>(loader.symbol(services, name).address), arguments).result_integers[0];
    };
    auto error = [&] {
      auto *value = reinterpret_cast<const char *>(service("dlerror", {}));
      return value ? std::string(value) : std::string{};
    };
    require(error().empty(), "new thread has no dlerror");
    require(!service("dlopen", {pointer("libmissing-dynamic.so"), Argument::integer(6)}) &&
                !error().empty() && error().empty() && !loader.image_count(),
            "NOLOAD missing object neither loads nor initializes");
    void *handle = reinterpret_cast<void *>(service("dlopen", {pointer("libdynamic.so"), Argument::integer(2)}));
    require(handle && publications == 1 && loader.constructor_count() == 1,
            "real guest dlopen links, publishes and initializes");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      auto address = service("dlsym", {pointer(handle), pointer(name)});
      require(address, "guest handle export lookup");
      return int(int32_t(invoke(reinterpret_cast<void *>(address), arguments).result_integers[0]));
    };
    require(call("dynamic_value") == 107 && call("dynamic_roundtrip") == 107,
            "guest calls libdl adapters and invokes real constructor state");
    require(call("dynamic_walk", {Argument::integer(0)}) == 1 &&
                call("dynamic_walk", {Argument::integer(23)}) == 1,
            "original PHDRs, load bias, AAPCS callback, early stop and recursive NOLOAD");
    require(!service("dlsym", {pointer(nullptr), pointer("dynamic_value")}) && !error().empty(),
            "local module absent from global namespace");
    auto again = service("dlopen", {pointer("libdynamic.so"), Argument::integer(0x106)});
    require(again == reinterpret_cast<uintptr_t>(handle) &&
                service("dlsym", {pointer(nullptr), pointer("dynamic_value")}),
            "NOLOAD GLOBAL promotes existing guest object and retains handle");
    require(!service("dlclose", {pointer(handle)}) && call("dynamic_value") == 107,
            "one reference release preserves remaining handle");
    auto scope = service("dlopen", {pointer(nullptr), Argument::integer(2)});
    require(scope && service("dlsym", {Argument::integer(scope, ArgumentType::Int64), pointer("dynamic_value")}),
            "dlopen null returns guest global scope handle");
    require(!service("dlclose", {Argument::integer(scope, ArgumentType::Int64)}), "close namespace scope handle");
    require(!service("dlsym", {pointer(handle), pointer("main_thread_missing")}), "set main thread error");
    std::atomic<bool> isolated{false};
    std::thread worker([&] {
      bool clean = error().empty();
      service("dlsym", {pointer(handle), pointer("worker_thread_missing")});
      auto message = error();
      isolated = clean && message.find("worker_thread_missing") != std::string::npos && error().empty();
    });
    worker.join();
    require(isolated && error().find("main_thread_missing") != std::string::npos && error().empty(),
            "per-thread consuming dlerror");
    require(!service("dlsym", {Argument::integer(-1, ArgumentType::Int64), pointer("dynamic_value")}) &&
                error().find("caller provenance") != std::string::npos,
            "RTLD_NEXT explicitly refused without caller provenance");
    require(!service("dlsym", {pointer(nullptr), pointer("mach_task_self")}) && !error().empty(),
            "host symbols never escape into guest namespace");
    auto resident = service("dlsym", {pointer(handle), pointer("dynamic_value")});
    require(!service("dlclose", {pointer(handle)}) &&
                !service("dlsym", {pointer(handle), pointer("dynamic_value")}) && !error().empty(),
            "last close invalidates logical handle");
    require(int(invoke(reinterpret_cast<void *>(resident), {}).result_integers[0]) == 107 && loader.image_count() == 1,
            "code stays resident for Loader lifetime");
    auto replacement = service("dlopen", {pointer("libdynamic.so"), Argument::integer(6)});
    require(replacement && replacement != reinterpret_cast<uintptr_t>(handle) && loader.constructor_count() == 1,
            "closed token never aliases replacement and constructors do not rerun");
    require(!service("dlclose", {Argument::integer(replacement, ArgumentType::Int64)}), "close replacement handle");
    current = nullptr;
    require(!service("dlopen", {pointer("libdynamic.so"), Argument::integer(2)}) &&
                error().find("unavailable") != std::string::npos,
            "lifetime hook fails closed after namespace detachment");
    std::cout << "M5_DYNAMIC_PASS guest_namespace=1 constructors=1 phdr_snapshot=1 callback=1 reentrant=1 refs=1 errors=2 host_fallback=0 unload=resident next=unsupported\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
