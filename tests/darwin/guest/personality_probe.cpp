// SPDX-License-Identifier: GPL-3.0-only
#include "guest_errno.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include <condition_variable>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
#include <iostream>
#include <mutex>
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
           "errno_threads=4 host_errno_preserved=1 nonreturning_contract=1 link_stubs_executed=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
