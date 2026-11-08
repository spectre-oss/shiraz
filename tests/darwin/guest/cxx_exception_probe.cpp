// SPDX-License-Identifier: GPL-3.0-only
// Purpose-built guest exception/LSDA test using genuine APK libc++ bytes.
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <cstdio>
#include <filesystem>
#include <memory>
#include <mutex>
#include <sys/resource.h>

using namespace shiraz::guest;
namespace {
void require(bool value, const char *message) {
  if (!value) throw ElfError(message);
}
}
int main(int argc, char **argv) {
  try {
    const bool basic = argc == 4 && std::string(argv[3]) == "basic";
    const bool escape = argc == 4 && std::string(argv[3]) == "escape";
    require(argc == 3 || basic || escape,
            "usage: cxx-exception-probe fixture-directory unchanged-apk-lib-directory [basic|escape]");
    Loader *active = nullptr;
    std::shared_ptr<const CodeValidator> code;
    auto validate = [&](const void *pointer) {
      auto current = std::atomic_load(&code);
      return current && (*current)(pointer);
    };
    auto mutex = std::make_shared<std::recursive_mutex>();
    auto lifecycle = std::make_shared<Lifecycle>(validate);
    auto signals = std::make_shared<SignalRuntime>(validate);
    auto cleanup = std::make_shared<CleanupRuntime>(validate);
    auto refresh = [&] {
      std::atomic_store(&code, std::make_shared<const CodeValidator>(active->code_validator()));
    };
    auto providers = dynamic_personality(
        native_personality(validate, lifecycle, signals, cleanup),
        {[&] { return active; }, mutex, refresh});
    Loader loader({std::filesystem::canonical(argv[1]), std::filesystem::canonical(argv[2])},
                  std::move(providers));
    active = &loader;
    lifecycle->attach(loader);
    signals->attach(loader);
    cleanup->attach(loader);
    auto *fixture = loader.link("libcxx-exception-fixture.so");
    refresh();
    // Reject any fake personality or host C++ ABI fallback before execution.
    const char *required[] = {
        "__cxa_allocate_exception", "__cxa_throw", "__cxa_begin_catch",
        "__cxa_end_catch", "__cxa_rethrow", "__gxx_personality_v0", "_ZTIi", "_ZTId"};
    unsigned verified = 0;
    for (const char *name : required) {
      if (basic && std::string(name) == "__cxa_rethrow") continue;
      bool found = false;
      for (const auto &record : loader.bindings()) {
        if (record.module != "libcxx-exception-fixture.so" || record.symbol != name) continue;
        require(record.binding.provider == "libc++_shared.so" &&
                    record.binding.abi == "guest-AAPCS64",
                "C++ exception ABI/typeinfo must bind unchanged guest libc++");
        found = true;
      }
      require(found, "fixture lacks required real C++ exception ABI relocation");
      ++verified;
    }
    if (!basic) {
      for (const char *name : {"_Unwind_Resume"}) {
        const auto resume = loader.symbol(fixture, name);
        require(resume.found && resume.provider == "libcxx-exception-fixture.so" &&
                    resume.abi == "guest-AAPCS64" &&
                    loader.contains_code(reinterpret_cast<void *>(resume.address)),
                "cleanup resume must execute the linked real guest unwinder");
      }
    }
    loader.open("libcxx-exception-fixture.so");
    prepare_guest_tls();
    const char *tests[] = {"guest_exception_nested", "guest_exception_rethrow",
                           "guest_exception_double", "guest_exception_catch_all"};
    for (const char *name : tests) {
      if (basic && std::string(name) == "guest_exception_rethrow") continue;
      auto entry = loader.symbol(fixture, name);
      require(loader.contains_code(reinterpret_cast<void *>(entry.address)),
              "exception fixture entry must be genuine guest code");
      auto result = invoke(reinterpret_cast<void *>(entry.address), {});
      if (int32_t(result.result_integers[0])) {
        std::fprintf(stderr, "M5_CXX_EXCEPTION_CASE_FAIL name=%s result=%d\n", name,
                     int32_t(result.result_integers[0]));
        throw ElfError("real guest exception/LSDA roundtrip failed");
      }
    }
    if (basic) {
      std::printf("M5_CXX_EXCEPTION_BASIC_PASS cases=3 real_abi_bindings=%u nested_frames=3 "
                  "raii_cleanup=0 rethrow=0 catch_selection=1 catch_all=1 "
                  "full_exception_gate=0 host_exception_crossing=0 app_execution=0\n", verified);
    } else std::printf("M5_CXX_EXCEPTION_PASS cases=4 real_abi_bindings=%u nested_frames=3 "
                "raii_cleanup=1 rethrow=1 catch_selection=1 catch_all=1 "
                "host_exception_crossing=0 app_execution=0\n", verified);
    if (escape) {
      // This process is the harness-owned disposable child. Suppress its core
      // file only; do not alter host-wide settings or install signal handlers.
      struct rlimit no_core{0, 0};
      require(setrlimit(RLIMIT_CORE, &no_core) == 0, "disable escape-child core file");
      auto entry = loader.symbol(fixture, "guest_exception_escape");
      require(loader.contains_code(reinterpret_cast<void *>(entry.address)),
              "escape entry must be genuine guest code");
      std::printf("M5_CXX_EXCEPTION_ESCAPE_ENTER\n");
      std::fflush(stdout);
      std::fflush(stderr);
      try {
        invoke(reinterpret_cast<void *>(entry.address), {});
        std::fprintf(stderr, "M5_CXX_EXCEPTION_HOST_RETURNED\n");
        return 3;
      } catch (...) {
        std::fprintf(stderr, "M5_CXX_EXCEPTION_HOST_CAUGHT\n");
        return 4;
      }
    }
  } catch (const std::exception &failure) {
    std::fprintf(stderr, "M5_CXX_EXCEPTION_FAIL reason=%s\n", failure.what());
    return 1;
  }
}
