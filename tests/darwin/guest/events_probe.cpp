// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <future>
#include <iostream>
#include <pthread.h>
#include <signal.h>
#include <thread>
using namespace shiraz::guest;
namespace {
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
volatile sig_atomic_t seen = 0;
void signal_handler(int) { ++seen; }
struct Shutdown { ~Shutdown() { event_descriptors_shutdown(); } };
struct Signals {
  sigset_t previous{};
  struct sigaction action{};
  Signals() {
    struct sigaction replacement{}; replacement.sa_handler = signal_handler;
    sigemptyset(&replacement.sa_mask);
    require(!sigaction(SIGUSR1, &replacement, &action), "install test signal handler");
    sigset_t blocked; sigemptyset(&blocked); sigaddset(&blocked, SIGUSR1);
    require(!pthread_sigmask(SIG_BLOCK, &blocked, &previous), "block native signal before ppoll");
  }
  ~Signals() { pthread_sigmask(SIG_SETMASK, &previous, nullptr); sigaction(SIGUSR1, &action, nullptr); }
};
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: events-probe fixture-directory");
    Shutdown shutdown;
    auto base = file_personality();
    Providers providers;
    for (const auto &entry : base.modules()) {
      Provider provider = entry.second;
      if (entry.first == "libc.so") {
        auto events = event_exports(); provider.exports.insert(provider.exports.end(), events.begin(), events.end());
      }
      providers.add(std::move(provider));
    }
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libevents.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0]);
    };
    bool atomic_dup3 = true;
    for (const char *name : {"events_counter", "events_aliases", "events_poll_pipe", "events_dup3", "events_ppoll", "events_explicit_limits"}) {
      errno = ELOOP;
      int result = call(name);
      if (std::string(name) == "events_dup3" && result == 74) atomic_dup3 = false;
      else if (result != 73) throw std::runtime_error(std::string(name) + " case=" + std::to_string(result));
      require(errno == ELOOP, "event adapters preserve host errno");
    }
    int fd = call("events_make_blocking"); require(fd >= 0, "blocking counter creation");
    auto finish = [&](std::future<int> &operation) {
      if (operation.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
        (void)event_descriptor_close(fd);
        if (operation.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
          std::fputs("events-probe: blocked operation did not cancel\n", stderr);
          std::abort();
        }
        (void)operation.get();
        throw std::runtime_error("eventfd blocking operation exceeded deadline");
      }
      return operation.get();
    };
    auto reader = std::async(std::launch::async, [&] { return call("events_read_expected", {Argument::integer(fd), Argument::integer(7, ArgumentType::Int64)}); });
    require(reader.wait_for(std::chrono::milliseconds(30)) == std::future_status::timeout, "real empty read blocks");
    int written = call("events_write_value", {Argument::integer(fd), Argument::integer(7, ArgumentType::Int64)});
    if (written != 73) (void)event_descriptor_close(fd);
    int read_result = finish(reader);
    require(written == 73 && read_result == 73,
            "kernel readiness wakes blocking guest read");
    require(call("events_write_value", {Argument::integer(fd), Argument::integer(-2, ArgumentType::Int64)}) == 73, "fill event counter");
    auto writer = std::async(std::launch::async, [&] { return call("events_write_value", {Argument::integer(fd), Argument::integer(1, ArgumentType::Int64)}); });
    require(writer.wait_for(std::chrono::milliseconds(30)) == std::future_status::timeout, "real full write blocks");
    read_result = call("events_read_expected", {Argument::integer(fd), Argument::integer(-2, ArgumentType::Int64)});
    if (read_result != 73) (void)event_descriptor_close(fd);
    written = finish(writer);
    require(read_result == 73 && written == 73,
            "read releases blocked writer");
    require(call("events_read_expected", {Argument::integer(fd), Argument::integer(1, ArgumentType::Int64)}) == 73 &&
                call("events_close", {Argument::integer(fd)}) == 73, "consume delayed write and close");
    {
      Signals signals;
      require(!pthread_kill(pthread_self(), SIGUSR1), "queue blocked native signal");
      errno = ELOOP;
      require(call("events_ppoll_signal") == 73 && seen == 1, "ppoll atomically unmasks pending signal");
      require(errno == ELOOP, "ppoll preserves host errno");
      sigset_t current; require(!pthread_sigmask(SIG_SETMASK, nullptr, &current) && sigismember(&current, SIGUSR1),
                              "ppoll restores original native signal mask");
    }
    std::cout << "M5_EVENTS_PASS eventfd_counter=1 semaphore=1 io_hooks=1 aliases=1 dup3_atomic="
              << atomic_dup3 << " poll_flags=1 blocking_io=1 ppoll_atomic_signal=1 inotify_supported=0 rdhup_supported=0 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "events-probe: " << error.what() << '\n';
    return 1;
  }
}
