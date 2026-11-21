// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/events.hpp"
#include "maps_descriptor.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/stdio.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <future>
#include <iostream>
#include <pthread.h>
#include <signal.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
namespace {
void require(bool condition, const char* message) {
    if (!condition)
        throw std::runtime_error(message);
}
volatile sig_atomic_t seen = 0;
void signal_handler(int) {
    ++seen;
}
struct Shutdown {
    ~Shutdown() {
        event_descriptors_shutdown();
    }
};
struct Signals {
    sigset_t previous{};
    struct sigaction action{};
    Signals() {
        struct sigaction replacement{};
        replacement.sa_handler = signal_handler;
        sigemptyset(&replacement.sa_mask);
        require(!sigaction(SIGUSR1, &replacement, &action), "install test signal handler");
        sigset_t blocked;
        sigemptyset(&blocked);
        sigaddset(&blocked, SIGUSR1);
        require(!pthread_sigmask(SIG_BLOCK, &blocked, &previous),
                "block native signal before ppoll");
    }
    ~Signals() {
        pthread_sigmask(SIG_SETMASK, &previous, nullptr);
        sigaction(SIGUSR1, &action, nullptr);
    }
};
} // namespace
int main(int argc, char** argv) {
    try {
        require(argc == 2, "usage: events-probe fixture-directory");
        Shutdown shutdown;
        maps_tests();
        auto base = stdio_personality();
        Providers providers;
        for (const auto& entry : base.modules()) {
            Provider provider = entry.second;
            if (entry.first == "libc.so") {
                auto events = event_exports();
                provider.exports.insert(provider.exports.end(), events.begin(), events.end());
            }
            providers.add(std::move(provider));
        }
        Loader loader({argv[1]}, std::move(providers));
        auto* module = loader.open("libevents.so");
        auto call = [&](const char* name, const std::vector<Argument>& arguments = {}) {
            return int(
                invoke(reinterpret_cast<void*>(loader.symbol(module, name).address), arguments)
                    .result_integers[0]);
        };
        {
            int maps = process_maps_open(
                [](std::string& out) {
                    out = "1000-2000 r--p 00000000 00:00 0 \n";
                    return 0;
                },
                O_CLOEXEC);
            require(maps >= 0, "maps descriptor for guest syscall checks");
            auto integer = [](uint64_t value) {
                return Argument::integer(value, ArgumentType::Int64);
            };
            alignas(16) unsigned char output[256]{};
            require(call("fstat", {integer(maps), integer(reinterpret_cast<uintptr_t>(output))}) ==
                        0,
                    "guest fstat uses synthetic maps metadata");
            require(call("fstatvfs",
                         {integer(maps), integer(reinterpret_cast<uintptr_t>(output))}) == -1 &&
                        *guest_errno_location() == errno_to_guest(ENOTSUP),
                    "maps filesystem metadata explicitly unsupported");
            for (const char* name : {"fsync", "fdatasync", "ftruncate", "ftruncate64"})
                require(call(name, {integer(maps), integer(0)}) == -1 &&
                            *guest_errno_location() == errno_to_guest(EINVAL),
                        "maps sync/truncate cannot reach host token");
            require(!event_descriptor_close(maps), "maps syscall descriptor cleanup");
        }
        {
            int directory = sysfs_directory_open(3, 0);
            int counter = call("events_make_blocking");
            require(directory >= 0 && counter >= 0, "open cross-kind descriptors");
            auto lease = sysfs_directory_lease(directory);
            require(bool(lease), "retain directory description");
            int alias = event_descriptor_dup(directory);
            require(alias >= 0 && event_descriptor_dup2(counter, directory) == directory,
                    "replace directory alias with counter");
            errno = 0;
            require(sysfs_directory_close_owned(directory, lease) == -1 && errno == EBADF,
                    "stale directory lease cannot close replacement");
            require(sysfs_directory_node(alias) == 3 && sysfs_directory_node(directory) == 0,
                    "replaced descriptor retains correct kind");
            require(call("events_write_value", {Argument::integer(directory),
                                                Argument::integer(9, ArgumentType::Int64)}) == 73 &&
                        call("events_read_expected", {Argument::integer(counter),
                                                      Argument::integer(9, ArgumentType::Int64)}) ==
                            73,
                    "counter aliases remain shared after replacement");
            require(event_descriptor_dup2(alias, counter) == counter &&
                        sysfs_directory_node(counter) == 3,
                    "replace counter with directory alias");
            char byte;
            errno = 0;
            require(event_descriptor_read(counter, &byte, 1) == -1 && errno == EISDIR,
                    "directory alias does not expose token");
            require(!event_descriptor_close(alias) && !event_descriptor_close(counter) &&
                        !event_descriptor_close(directory),
                    "close cross-kind descriptors");
        }
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
        {
            int proc = probe_process_arguments_open(O_CLOEXEC);
            require(proc >= 0, "open process argument description");
            int alias = event_descriptor_dup(proc);
            require(alias >= 0, "duplicate process argument description");
            require(call("events_process_poll",
                         {Argument::integer(proc), Argument::integer(alias)}) == 73,
                    "process descriptor guest poll and shared status flags");
            char first[4]{}, second[4]{};
            require(event_descriptor_read(proc, first, 2) == 2 && !std::memcmp(first, argv[0], 2),
                    "initial live read");
            require(event_descriptor_read(alias, second, 2) == 2 &&
                        !std::memcmp(second, argv[0] + 2, 2),
                    "duplicate shares offset");
            ssize_t result = -1;
            require(probe_process_arguments_pread(alias, second, 2, 0, result) && result == 2 &&
                        !std::memcmp(second, argv[0], 2),
                    "positioned live read");
            char saved = argv[0][0];
            argv[0][0] = saved == 'X' ? 'Y' : 'X';
            require(probe_process_arguments_pread(alias, second, 1, 0, result) && result == 1 &&
                        second[0] == argv[0][0],
                    "reads current argument bytes");
            argv[0][0] = saved;
            int64_t position = -1;
            require(probe_process_arguments_seek(proc, 0, 1, position) && position == 4,
                    "pread preserves shared offset");
            require(probe_process_arguments_seek(proc, INT64_MAX, 0, position) &&
                        position == INT64_MAX &&
                        call("events_process_poll",
                             {Argument::integer(proc), Argument::integer(alias)}) == 73,
                    "process descriptor remains poll-ready at EOF");
            require(probe_process_arguments_seek(proc, 4, 0, position) && position == 4,
                    "restore shared offset");
            errno = 0;
            require(event_descriptor_write(proc, first, 1) == -1 && errno == EBADF,
                    "read-only description");
            errno = 0;
            require(probe_process_arguments_pwrite(proc, result) && result == -1 && errno == EBADF,
                    "positioned write rejected");
            int counter = call("events_make_blocking");
            require(counter >= 0 && event_descriptor_dup2(proc, counter) == counter,
                    "replace event with process descriptor");
            require(event_descriptor_read(counter, second, 1) == 1 && second[0] == argv[0][4],
                    "cross-kind alias offset");
            int other = call("events_make_blocking");
            require(other >= 0 && event_descriptor_dup2(other, alias) == alias,
                    "replace process with event descriptor");
            require(
                call("events_write_value",
                     {Argument::integer(alias), Argument::integer(9, ArgumentType::Int64)}) == 73 &&
                    call("events_read_expected", {Argument::integer(other),
                                                  Argument::integer(9, ArgumentType::Int64)}) == 73,
                "cross-kind event aliases remain coherent");
            require(!event_descriptor_close(alias) && !event_descriptor_close(other) &&
                        !event_descriptor_close(counter) && !event_descriptor_close(proc),
                    "close cross-kind aliases");
            std::cout << "M5_PROCESS_ARGUMENTS_DESCRIPTOR_PASS live=1 shared_offset=1 "
                         "cross_kind_dup2=1\n";
        }
#endif
        bool atomic_dup3 = true;
        for (const char* name :
             {"events_counter", "events_stdio_handoff", "events_aliases", "events_poll_pipe",
              "events_dup3", "events_ppoll", "events_explicit_limits"}) {
            errno = ELOOP;
            int result = call(name);
            if (std::string(name) == "events_dup3" && result == 74)
                atomic_dup3 = false;
            else if (result != 73)
                throw std::runtime_error(std::string(name) + " case=" + std::to_string(result));
            require(errno == ELOOP, "event adapters preserve host errno");
        }
        {
            int pipefd[2];
            require(!::pipe(pipefd), "create duplication race pipe");
            require(!::fcntl(pipefd[0], F_SETFL, O_NONBLOCK), "nonblocking race pipe");
            int slot = ::dup(pipefd[0]), destination = ::dup(pipefd[0]);
            int counter = call("events_make_blocking");
            require(slot >= 0 && destination >= 0 && counter >= 0,
                    "create duplication race descriptors");
            std::atomic<bool> bad{false}, start{false};
            auto check = [&](int descriptor) {
                if (descriptor < 0) {
                    bad = true;
                    return;
                }
                if (!event_descriptor_tracked(descriptor)) {
                    char byte;
                    errno = 0;
                    if (::read(descriptor, &byte, 1) != -1 || errno != EAGAIN)
                        bad = true;
                }
            };
            std::thread replacer([&] {
                while (!start.load(std::memory_order_acquire))
                    std::this_thread::yield();
                for (int i = 0; i < 12000 && !bad; ++i)
                    if (event_descriptor_dup2(counter, slot) != slot ||
                        event_descriptor_dup2(pipefd[0], slot) != slot)
                        bad = true;
            });
            start.store(true, std::memory_order_release);
            for (int i = 0; i < 12000 && !bad; ++i) {
                int copy = event_descriptor_dup(slot);
                check(copy);
                if (copy >= 0 && event_descriptor_close(copy))
                    bad = true;
                copy = call("events_dup_cloexec", {Argument::integer(slot)});
                check(copy);
                if (copy >= 0 && event_descriptor_close(copy))
                    bad = true;
                if (event_descriptor_dup2(slot, destination) != destination)
                    bad = true;
                else
                    check(destination);
            }
            replacer.join();
            require(!bad, "concurrent duplication must not expose untracked virtual token");
            require(!event_descriptor_close(slot) && !event_descriptor_close(destination) &&
                        !event_descriptor_close(counter) && !::close(pipefd[0]) &&
                        !::close(pipefd[1]),
                    "close duplication race descriptors");
        }
        int fd = call("events_make_blocking");
        require(fd >= 0, "blocking counter creation");
        auto finish = [&](std::future<int>& operation) {
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
        auto reader = std::async(std::launch::async, [&] {
            return call("events_read_expected",
                        {Argument::integer(fd), Argument::integer(7, ArgumentType::Int64)});
        });
        require(reader.wait_for(std::chrono::milliseconds(30)) == std::future_status::timeout,
                "real empty read blocks");
        int written = call("events_write_value",
                           {Argument::integer(fd), Argument::integer(7, ArgumentType::Int64)});
        if (written != 73)
            (void)event_descriptor_close(fd);
        int read_result = finish(reader);
        require(written == 73 && read_result == 73, "kernel readiness wakes blocking guest read");
        require(call("events_write_value",
                     {Argument::integer(fd), Argument::integer(-2, ArgumentType::Int64)}) == 73,
                "fill event counter");
        auto writer = std::async(std::launch::async, [&] {
            return call("events_write_value",
                        {Argument::integer(fd), Argument::integer(1, ArgumentType::Int64)});
        });
        require(writer.wait_for(std::chrono::milliseconds(30)) == std::future_status::timeout,
                "real full write blocks");
        read_result = call("events_read_expected",
                           {Argument::integer(fd), Argument::integer(-2, ArgumentType::Int64)});
        if (read_result != 73)
            (void)event_descriptor_close(fd);
        written = finish(writer);
        require(read_result == 73 && written == 73, "read releases blocked writer");
        require(call("events_read_expected",
                     {Argument::integer(fd), Argument::integer(1, ArgumentType::Int64)}) == 73 &&
                    call("events_close", {Argument::integer(fd)}) == 73,
                "consume delayed write and close");
        {
            Signals signals;
            require(!pthread_kill(pthread_self(), SIGUSR1), "queue blocked native signal");
            errno = ELOOP;
            require(call("events_ppoll_signal") == 73 && seen == 1,
                    "ppoll atomically unmasks pending signal");
            require(errno == ELOOP, "ppoll preserves host errno");
            sigset_t current;
            require(!pthread_sigmask(SIG_SETMASK, nullptr, &current) &&
                        sigismember(&current, SIGUSR1),
                    "ppoll restores original native signal mask");
        }
        std::cout
            << "M5_EVENTS_PASS eventfd_counter=1 semaphore=1 io_hooks=1 aliases=1 dup3_atomic="
            << atomic_dup3
            << " poll_flags=1 blocking_io=1 ppoll_atomic_signal=1 inotify_supported=0 "
               "rdhup_supported=0 host_errno_preserved=1\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "events-probe: " << error.what() << '\n';
        return 1;
    }
}
