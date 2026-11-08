// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include <algorithm>
#include <array>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <pthread.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char *text) {
  if (!value)
    throw std::runtime_error(text);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value),
                           ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: signals-probe fixture-directory");
    Loader *current = nullptr;
    auto runtime = std::make_shared<SignalRuntime>(
        [&](const void *p) { return current && current->contains_code(p); });
    auto base = native_personality();
    Providers providers;
    for (const auto &pair : base.modules()) {
      Provider module = pair.second;
      if (module.soname == "libc.so") {
        auto additions = runtime->exports();
        module.exports.erase(
            std::remove_if(module.exports.begin(), module.exports.end(),
                           [&](const auto &entry) {
                             return std::any_of(
                                 additions.begin(), additions.end(),
                                 [&](const auto &addition) {
                                   return entry.name == addition.name;
                                 });
                           }),
            module.exports.end());
        module.exports.insert(module.exports.end(), additions.begin(),
                              additions.end());
      }
      providers.add(std::move(module));
    }
    auto loader = std::make_unique<Loader>(
        std::vector<std::filesystem::path>{argv[1]}, std::move(providers));
    current = loader.get();
    runtime->attach(*loader);
    auto *module = loader->open("libsignals.so");
    auto call = [&](const char *name, const std::vector<Argument> &args = {}) {
      errno = 61;
      int result = int(int32_t(
          invoke(reinterpret_cast<void *>(loader->symbol(module, name).address),
                 args)
              .result_integers[0]));
      require(errno == 61, "signal/wait adapter preserves native errno");
      return result;
    };
    require(call("signals_sets") == 1, "guest eight-byte signal sets");
    int notification[2];
    require(!pipe(notification), "handler evidence pipe");
    struct sigaction original{};
    require(!sigaction(SIGUSR1, nullptr, &original),
            "snapshot native disposition");
    // Own the native signal only if it is not already an ART/native custom
    // handler. A normal standalone probe starts with default disposition.
    require(original.sa_handler == SIG_DFL || original.sa_handler == SIG_IGN,
            "probe signal is available without native chaining conflict");
    int error = 0;
    require(!call("signals_install",
                  {Argument::integer(0), Argument::integer(notification[1]),
                   pointer(&error)}) &&
                error == 73,
            "install translated basic guest handler");
    unsigned long flags = 0, mask = 0;
    require(!call("signals_query",
                  {pointer(&flags), pointer(&mask), pointer(&error)}) &&
                flags == 0x10000000 && mask == (1ul << 11) && error == 73,
            "guest sigaction flags and mask roundtrip");
    errno = 61;
    require(!pthread_kill(pthread_self(), SIGUSR1) && errno == 61,
            "deliver native signal to prepared guest thread");
    unsigned char delivered = 0;
    require(read(notification[0], &delivered, 1) == 1 && delivered == 10,
            "AAPCS callback receives Linux signal number");
    int count = 0;
    require(call("signals_observed", {pointer(&count)}) == 10 && count == 1,
            "actual guest signal callback ran");
    require(!call("signals_install",
                  {Argument::integer(1), Argument::integer(notification[1]),
                   pointer(&error)}) &&
                error == 73,
            "signal() uses the translated owned callback adapter");
    std::thread prepared([] {
      prepare_guest_tls();
      if (pthread_kill(pthread_self(), SIGUSR1))
        std::abort();
    });
    prepared.join();
    require(read(notification[0], &delivered, 1) == 1 && delivered == 10 &&
                call("signals_observed", {pointer(&count)}) == 10 && count == 2,
            "callback works on another prepared guest thread");
    // Exercise actual per-thread blocking and pending delivery through guest ABI.
    uint64_t selected = uint64_t(1) << 9;
    struct { uint64_t left, value, right; } old_mask{0x12345678, 0, 0x87654321};
    auto mask_call = [&](int which, int how, const uint64_t *set, uint64_t *old) {
      return call("signals_mask", {Argument::integer(which), Argument::integer(how),
                  pointer(set), pointer(old), pointer(&error)});
    };
    for (int which : {0, 1}) {
      require(!mask_call(which, 99, nullptr, &old_mask.value) && error == 73,
              "null input queries mask without interpreting how");
      require(!mask_call(which, 0, &selected, nullptr) && error == 73,
              "guest blocks only its installed signal");
      require(!call("signals_raise", {Argument::integer(10), pointer(&error)}) && error == 73,
              "raise targets the current native thread");
      uint64_t pending = 0;
      require(!call("signals_pending", {pointer(&pending), pointer(&error)}) &&
                  (pending & selected) && error == 73,
              "blocked guest signal is actually pending");
      require(call("signals_observed", {pointer(&count)}) == 10 && count == 2 + which,
              "blocked handler has not run");
      require(!mask_call(which, 2, &old_mask.value, nullptr) && error == 73,
              "restore mask delivers pending handler");
      require(read(notification[0], &delivered, 1) == 1 && delivered == 10 &&
                  call("signals_observed", {pointer(&count)}) == 10 && count == 3 + which,
              "pending handler runs once after restoring original mask");
      require(old_mask.left == 0x12345678 && old_mask.right == 0x87654321,
              "mask translation writes exactly eight bytes");
    }
    uint64_t unowned = uint64_t(1) << 11, realtime = uint64_t(1) << 40;
    require(mask_call(0, 0, &unowned, nullptr) == -1 && error == 16,
            "unowned native signal mask remains unchanged");
    require(mask_call(1, 0, &unowned, nullptr) == 16 && error == 73,
            "pthread mask errors return guest error code without changing errno");
    require(mask_call(0, 0, &realtime, nullptr) == -1 && error == 95,
            "unsupported realtime mask fails explicitly");
    require(mask_call(1, 99, &selected, nullptr) == 22 && error == 73,
            "invalid pthread mask operation returns EINVAL");
    require(!mask_call(0, 0, &selected, &old_mask.value), "temporary unbalanced guest mask");
    bool refused_mask_shutdown = false;
    try { runtime->shutdown(); } catch (const ElfError &) { refused_mask_shutdown = true; }
    require(refused_mask_shutdown, "cannot shut down with a live modified native thread mask");
    require(!mask_call(0, 2, &old_mask.value, nullptr), "restore mask after rejected shutdown");
    require(call("signals_mask_in_handler", {pointer(&error)}) == 1 && error == 73 &&
                read(notification[0], &delivered, 1) == 1 && delivered == 10,
            "mask API reentry fails explicitly without locking in a handler");
    int worker_mask_result = -1, worker_mask_error = 0;
    std::thread masked_worker([&] {
      prepare_guest_tls();
      worker_mask_result = call("signals_mask", {Argument::integer(1), Argument::integer(0),
                           pointer(&selected), pointer(nullptr), pointer(&worker_mask_error)});
      // No restoration is needed after this native thread ceases to exist.
      // The runtime's lease must disappear before the final shutdown below.
    });
    masked_worker.join();
    require(worker_mask_result == 0 && worker_mask_error == 73,
            "a guest-owned signal mask can belong to an exiting native worker");
    pid_t unprepared = fork();
    require(unprepared >= 0, "unprepared native delivery child");
    if (!unprepared) {
      std::thread other([] { pthread_kill(pthread_self(), SIGUSR1); });
      other.join();
      ::_exit(99);
    }
    int unprepared_status = 0;
    require(waitpid(unprepared, &unprepared_status, 0) == unprepared &&
                WIFEXITED(unprepared_status) &&
                WEXITSTATUS(unprepared_status) == 138,
            "unprepared native delivery fails without lazy guest TLS "
            "initialization");
    require(call("signals_install",
                 {Argument::integer(2), Argument::integer(notification[1]),
                  pointer(&error)}) == -1 &&
                error == 95,
            "SIGINFO/ucontext unsupported explicitly");
    require(call("signals_install",
                 {Argument::integer(3), Argument::integer(notification[1]),
                  pointer(&error)}) == -1 &&
                error == 95,
            "guest alternate-stack support not fabricated");
    require(!call("signals_kill", {Argument::integer(getpid()),
                                   Argument::integer(0), pointer(&error)}) &&
                error == 73,
            "real kill permission probe");
    require(call("signals_kill", {Argument::integer(getpid()),
                                  Argument::integer(16), pointer(&error)}) ==
                    -1 &&
                error == 95,
            "unmapped Linux signal explicitly rejected");
    for (int which = 0; which < 5; ++which)
      require(call("signals_unsupported",
                   {Argument::integer(which), pointer(&error)}) == -1 &&
                  error == 38,
              "unsafe fork/vfork/guest exec are not host aliases");
    pid_t child = fork();
    require(child >= 0, "native child for guest waitid");
    if (!child)
      ::_exit(37);
    alignas(8) std::array<unsigned char, 128> info{};
    require(!call("signals_waitid", {Argument::integer(child),
                                     pointer(info.data()), pointer(&error)}) &&
                error == 73,
            "translated waitid WNOWAIT");
    auto field = [&](size_t offset) {
      int value;
      std::memcpy(&value, info.data() + offset, 4);
      return value;
    };
    require(field(0) == 17 && field(8) == 1 && field(16) == child &&
                field(24) == 37,
            "Linux child siginfo layout/status");
    int status = 0;
    require(call("signals_waitpid", {Argument::integer(child), pointer(&status),
                                     Argument::integer(0), pointer(&error)}) ==
                    child &&
                status == (37 << 8) && error == 73,
            "waitpid reaps previously observed child");
    child = fork();
    require(child >= 0, "native signaled child");
    if (!child) {
      signal(SIGUSR2, SIG_DFL);
      raise(SIGUSR2);
      ::_exit(99);
    }
    require(call("signals_waitpid", {Argument::integer(child), pointer(&status),
                                     Argument::integer(0), pointer(&error)}) ==
                    child &&
                (status & 0x7f) == 12,
            "native termination signal translated to Linux");
    child = fork();
    require(child >= 0, "native wait4 child");
    if (!child)
      ::_exit(43);
    long rss = 0;
    require(call("signals_wait4", {Argument::integer(child), pointer(&status),
                                   pointer(&rss), pointer(&error)}) == child &&
                status == (43 << 8) && rss >= 0 && rss < (1l << 30) &&
                error == 73,
            "wait4 status and Linux KiB rusage");
    int gate[2];
    require(!pipe(gate), "stopped child gate");
    child = fork();
    require(child >= 0, "native stopped child");
    if (!child) {
      close(gate[1]);
      raise(SIGSTOP);
      char byte;
      while (read(gate[0], &byte, 1) < 0 && errno == EINTR) {
      }
      ::_exit(44);
    }
    close(gate[0]);
    require(call("signals_waitpid", {Argument::integer(child), pointer(&status),
                                     Argument::integer(2), pointer(&error)}) ==
                    child &&
                status == ((19 << 8) | 0x7f),
            "Linux stopped status and WUNTRACED");
    require(!call("signals_kill", {Argument::integer(child),
                                   Argument::integer(18), pointer(&error)}),
            "translate SIGCONT");
    require(call("signals_waitpid", {Argument::integer(child), pointer(&status),
                                     Argument::integer(8), pointer(&error)}) ==
                    child &&
                status == 0xffff,
            "Darwin continued encoding becomes Linux ffff");
    require(write(gate[1], "x", 1) == 1, "release continued child");
    close(gate[1]);
    require(call("signals_waitpid", {Argument::integer(child), pointer(&status),
                                     Argument::integer(0), pointer(&error)}) ==
                    child &&
                status == (44 << 8),
            "reap continued child");
    child = fork();
    require(child >= 0, "setsid isolated child");
    if (!child)
      ::_exit(call("signals_setsid") == getpid() ? 0 : 99);
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) &&
                WEXITSTATUS(status) == 0,
            "real setsid runs only in isolated child");
    loader.reset();
    current = nullptr;
    struct sigaction restored{};
    require(!sigaction(SIGUSR1, nullptr, &restored) &&
                restored.sa_handler == original.sa_handler,
            "native handler restored before ELF unmap");
    close(notification[0]);
    close(notification[1]);
    runtime->shutdown();
    std::cout
        << "M5_SIGNALS_PASS callback=basic_aapcs native_thread=prepared "
           "masks=translated pending=1 mask_ownership=1 mask_shutdown_guard=1 mask_thread_exit=1 mask_handler_rejected=1 waits=translated wait4_kib=1 preunmap=restored "
           "siginfo=unsupported fork_exec=unsupported\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
