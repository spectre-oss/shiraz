// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "guest_errno.hpp"
#include "guest_io.hpp"
#include <atomic>
#include <chrono>
#include <csignal>
#include <filesystem>
#include <future>
#include <iostream>
#include <pthread.h>
#include <thread>
using namespace shiraz::guest;
static int (*post_signal)(void *);
static void *signal_object;
static volatile sig_atomic_t signal_result;
static void post_handler(int) { signal_result = post_signal(signal_object); }
static void interrupt_handler(int) {}
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: rwsem-probe fixture-directory");
    Loader *current = nullptr;
    Loader loader({std::filesystem::canonical(argv[1])}, native_personality(
        [&](const void *code) { return current && current->contains_code(code); }));
    current = &loader;
    auto *module = loader.open("librwsem.so");
    require(loader.image_count() == 1, "actual rwlock/semaphore providers");
    auto call = [&](const char *name, std::vector<Argument> args) {
      return int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), args)
                     .result_integers[0]);
    };
    errno = ELOOP;
    int result = call("run_rwsem", {});
    if (result) throw std::runtime_error("guest rwlock/semaphore failed at line " + std::to_string(result));
    require(errno == ELOOP, "providers preserve host errno");
    alignas(4) uint32_t storage[bionic::kSemaphoreSize / 4]{};
    require(call("sem_init", {pointer(storage), Argument::integer(0), Argument::integer(0)}) == 0,
            "signal-test semaphore");
    post_signal = reinterpret_cast<int (*)(void *)>(loader.symbol(module, "sem_post").address);
    signal_object = storage;
    // A single pointer argument/int result has the same Apple/AAPCS64 register
    // convention. Call the generated adapter directly; no allocating invoke()
    // argument vector or other test scaffolding runs in the signal handler.
    struct sigaction action{}, previous_post{}, previous_interrupt{};
    sigemptyset(&action.sa_mask);
    action.sa_handler = post_handler;
    require(sigaction(SIGUSR1, &action, &previous_post) == 0, "post signal handler");
    signal_result = -2;
    errno = ELOOP;
    raise(SIGUSR1);
    require(signal_result == 0 && errno == ELOOP && call("sem_trywait", {pointer(storage)}) == 0,
            "actual signal handler posts one guest token");
    action.sa_handler = interrupt_handler;
    require(sigaction(SIGUSR2, &action, &previous_interrupt) == 0, "interrupt signal handler");
    auto wait = reinterpret_cast<int (*)(void *)>(loader.symbol(module, "sem_wait").address);
    std::promise<int> completed;
    auto future = completed.get_future();
    std::atomic<bool> entered{false};
    std::thread worker([&] {
      guest_errno_location();
      entered = true;
      int value = wait(storage);
      completed.set_value(value == -1 ? *guest_errno_location() : 0);
    });
    while (!entered) std::this_thread::yield();
    for (int attempt = 0; attempt < 1000 &&
         future.wait_for(std::chrono::milliseconds(2)) != std::future_status::ready; ++attempt)
      pthread_kill(worker.native_handle(), SIGUSR2);
    bool interrupted = future.wait_for(std::chrono::milliseconds(1)) == std::future_status::ready;
    if (!interrupted) post_signal(storage);
    worker.join();
    require(interrupted && future.get() == bionic::kEINTR, "blocked guest semaphore wait returns EINTR");
    require(call("sem_destroy", {pointer(storage)}) == 0 &&
                call("sem_init", {pointer(storage), Argument::integer(0),
                     Argument::integer(bionic::kSEM_VALUE_MAX)}) == 0,
            "full-range signal semaphore");
    *guest_errno_location() = 0;
    signal_result = 0;
    errno = ELOOP;
    raise(SIGUSR1);
    require(signal_result == -1 && *guest_errno_location() == bionic::kEOVERFLOW && errno == ELOOP,
            "overflow from a signal preserves host errno and reports guest errno");
    require(call("sem_destroy", {pointer(storage)}) == 0, "signal semaphore cleanup");
    sigaction(SIGUSR1, &previous_post, nullptr);
    sigaction(SIGUSR2, &previous_interrupt, nullptr);
    std::cout << "M5_RWSEM_PASS readers=2 writers=2 counter=2000 exclusion=1 guards=1 "
                 "guest_max=1 posts=40000 signal_post=1 interrupted_wait=1 reuse=128\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
