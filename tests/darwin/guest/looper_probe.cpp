// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/looper.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <iostream>
#include <stdexcept>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cerrno>
#include <csignal>
#include <fcntl.h>
#include <mutex>
#include <pthread.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
extern "C" bool shiraz_looper_test_current_fds(int* output);
static void require(bool condition, const char* message) {
    if (!condition)
        throw std::runtime_error(message);
}
static Argument number(uint64_t value) {
    return Argument::integer(value, ArgumentType::Int64);
}
static void ignore_signal(int) {}
extern "C" uintptr_t looper_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        return domain->route(site, target, sp).target;
    } catch (...) {
        std::abort();
    }
}
int main(int argc, char** argv) {
    try {
        require(argc == 2 || (argc == 3 && (std::string(argv[2]) == "active-shutdown" ||
                                            std::string(argv[2]) == "stuck-registration")),
                "fixture directory [mode]");
        const bool active_shutdown = argc == 3 && std::string(argv[2]) == "active-shutdown";
        const bool stuck_registration = argc == 3 && std::string(argv[2]) == "stuck-registration";
        Loader* owner = nullptr;
        std::function<void()> close_looper;
        std::mutex validation_mutex;
        std::condition_variable validation_changed;
        bool block_validation = false, validation_entered = false, validation_release = false;
        bool try_reentrant_stop = false, reentrant_refused = false;
        auto validate = [&](const void* pointer) {
            std::unique_lock<std::mutex> lock(validation_mutex);
            if (try_reentrant_stop) {
                try {
                    close_looper();
                } catch (const ElfError&) {
                    reentrant_refused = true;
                }
            }
            if (block_validation) {
                validation_entered = true;
                validation_changed.notify_all();
                if (!validation_changed.wait_for(lock, std::chrono::seconds(10),
                                                 [&] { return validation_release; }))
                    return false;
            }
            return owner && owner->contains_code(pointer);
        };
        auto lifecycle = std::make_shared<Lifecycle>(validate);
        auto entries = looper_exports(validate, lifecycle, &close_looper);
        std::shared_ptr<AdmissionDomain> expected_domain;
        bool observed_domain = false;
        std::mutex active_mutex;
        std::condition_variable active_changed;
        bool block_observer = false, observer_entered = false, observer_release = false;
        auto observe = closure([&](Capture&) {
            std::unique_lock<std::mutex> lock(active_mutex);
            observed_domain = AdmissionDomain::current_invocation() == expected_domain;
            if (block_observer) {
                observer_entered = true;
                active_changed.notify_all();
                if (!active_changed.wait_for(lock, std::chrono::seconds(10),
                                             [&] { return observer_release; }))
                    std::abort();
            }
        });
        entries.push_back({"looper_fixture_observe", "", reinterpret_cast<uintptr_t>(observe),
                           SymbolKind::Function, "fixture/observe-domain", false});
        Providers providers;
        providers.add({"libandroid.so", {}, entries, true});
        Loader loader({argv[1]}, std::move(providers), Loader::Mode::AdmissionFixture,
                      uintptr_t(&looper_validate));
        owner = &loader;
        loader.add_preunmap_hook(close_looper);
        lifecycle->attach(loader);
        auto* module = loader.open("looper-fixture.so");
        auto domain = loader.admission_domain();
        expected_domain = domain;
        auto find = [&](const char* name) {
            for (auto& entry : entries)
                if (entry.name == name)
                    return reinterpret_cast<void*>(entry.address);
            throw std::runtime_error(name);
        };
        auto call = [&](const char* name, std::vector<Argument> args = {}) {
            return int64_t(invoke(find(name), args).result_integers[0]);
        };
        auto poll_entry =
            reinterpret_cast<void*>(loader.symbol(module, "looper_fixture_poll").address);
        auto callback = loader.symbol(module, "looper_fixture_callback").address;
        auto poll = [&](int timeout, int* fd, int* events, void** data) {
            return int64_t(invoke_admitted(domain, poll_entry,
                                           {number(uint32_t(timeout)), number(uintptr_t(fd)),
                                            number(uintptr_t(events)), number(uintptr_t(data))})
                               .result_integers[0]);
        };
        require(!call("ALooper_forThread"), "no initial looper");
        auto loop = call("ALooper_prepare", {number(1)});
        require(loop > 0 && call("ALooper_forThread") == loop &&
                    call("ALooper_prepare", {number(0)}) == loop,
                "thread looper identity");
        auto events = event_exports();
        uintptr_t eventfd = 0;
        for (const auto& entry : events)
            if (entry.name == "eventfd")
                eventfd = entry.address;
        require(eventfd, "eventfd export");
        int counter = int(invoke(reinterpret_cast<void*>(eventfd), {number(0), number(0x800)})
                              .result_integers[0]);
        require(counter >= 0, "virtual eventfd");
        int fields[3]{};
        require(call("ALooper_addFd", {number(loop), number(counter), number(4), number(1),
                                       number(uintptr_t(&main)), number(uintptr_t(fields))}) == -1,
                "host callback refused");
        require(call("ALooper_addFd", {number(loop), number(counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))}) == 1,
                "register admitted callback");
        require(lifecycle->retained_thread_callbacks() == 1, "callback watch retains namespace");
        uint64_t one = 1;
        require(event_descriptor_write(counter, &one, 8) == 8, "signal virtual eventfd");
        int result = int(call("ALooper_pollOnce", {number(100), number(0), number(0), number(0)}));
        require(result == -2 && fields[0] == counter && (fields[1] & 1) && fields[2] == 1 &&
                    observed_domain,
                "host-origin poll enters the callback's admission domain");
        require(!lifecycle->retained_thread_callbacks(),
                "removed callback watch releases namespace");
        require(event_descriptor_read(counter, &one, 8) == 8, "drain eventfd");
        require(call("ALooper_removeFd", {number(loop), number(counter)}) == 0,
                "zero callback removes watch");
        {
            std::lock_guard<std::mutex> lock(validation_mutex);
            try_reentrant_stop = true;
        }
        require(call("ALooper_addFd", {number(loop), number(counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))}) == 1,
                "registration survives refused reentrant stop");
        {
            std::lock_guard<std::mutex> lock(validation_mutex);
            try_reentrant_stop = false;
        }
        require(reentrant_refused &&
                    call("ALooper_removeFd", {number(loop), number(counter)}) == 1 &&
                    !lifecycle->retained_thread_callbacks(),
                "reentrant stop was refused without leaking retention");
        require(call("ALooper_addFd", {number(loop), number(counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))}) == 1,
                "register callback for concurrent removal");
        {
            std::lock_guard<std::mutex> lock(active_mutex);
            block_observer = true;
        }
        std::atomic<bool> removal_ok{false};
        std::thread remover([&] {
            {
                std::unique_lock<std::mutex> lock(active_mutex);
                if (!active_changed.wait_for(lock, std::chrono::seconds(10),
                                             [&] { return observer_entered; }))
                    return;
            }
            if (active_shutdown) {
                try {
                    lifecycle->shutdown();
                } catch (const ElfError& error) {
                    removal_ok =
                        std::string(error.what()) ==
                            "guest thread-local callbacks still retain namespace at shutdown" &&
                        lifecycle->retained_thread_callbacks() == 1;
                }
            } else {
                removal_ok = call("ALooper_removeFd", {number(loop), number(counter)}) == 1 &&
                             lifecycle->retained_thread_callbacks() == 1;
            }
            {
                std::lock_guard<std::mutex> lock(active_mutex);
                observer_release = true;
            }
            active_changed.notify_all();
        });
        one = 1;
        require(event_descriptor_write(counter, &one, 8) == 8,
                "signal callback for concurrent removal");
        result = int(call("ALooper_pollOnce", {number(100), number(0), number(0), number(0)}));
        remover.join();
        require(result == -2 && removal_ok && !lifecycle->retained_thread_callbacks(),
                "selected callback retains namespace through concurrent removal");
        if (active_shutdown) {
            std::cout << "M5_LOOPER_ACTIVE_SHUTDOWN_REFUSED callback_retained=1\n" << std::flush;
            std::_Exit(0);
        }
        require(event_descriptor_read(counter, &one, 8) == 8, "drain concurrent callback event");
        require(call("ALooper_addFd", {number(loop), number(counter), number(77), number(1),
                                       number(0), number(uintptr_t(fields))}) == 1,
                "register ident watch");
        one = 1;
        require(event_descriptor_write(counter, &one, 8) == 8, "signal ident watch");
        int fd = -1, ready = 0;
        void* data = nullptr;
        result = -1;
        for (int i = 0; i < 4 && result == -1; ++i)
            result = int(poll(100, &fd, &ready, &data));
        require(result == 77 && fd == counter && (ready & 1) && data == fields,
                "ident readiness and data");
        int alias = event_descriptor_dup(counter);
        int replacement = int(invoke(reinterpret_cast<void*>(eventfd), {number(0), number(0x800)})
                                  .result_integers[0]);
        require(alias >= 0 && replacement >= 0 &&
                    event_descriptor_dup2(replacement, counter) == counter,
                "replace watched descriptor while old alias survives");
        require(call("ALooper_addFd", {number(loop), number(counter), number(88), number(1),
                                       number(0), number(uintptr_t(fields))}) == 1,
                "replace watch for reused descriptor");
        one = 1;
        require(event_descriptor_write(replacement, &one, 8) == 8, "signal replacement counter");
        result = -1;
        fd = -1;
        ready = 0;
        data = nullptr;
        for (int i = 0; i < 4 && result == -1; ++i)
            result = int(poll(100, &fd, &ready, &data));
        require(result == 88 && fd == counter && (ready & 1),
                "old ready alias does not mask new watch");
        require(call("ALooper_removeFd", {number(loop), number(counter)}) == 1,
                "remove replacement watch");
        require(!event_descriptor_close(counter) && !event_descriptor_close(alias) &&
                    !event_descriptor_close(replacement),
                "close counters");
        call("ALooper_wake", {number(loop)});
        result = int(poll(100, nullptr, nullptr, nullptr));
        require(result == -1, "explicit wake");
        require(poll(0, nullptr, nullptr, nullptr) == -3, "timeout");
        struct sigaction action{}, previous{};
        action.sa_handler = ignore_signal;
        sigemptyset(&action.sa_mask);
        require(!sigaction(SIGUSR1, &action, &previous), "install test signal handler");
        std::atomic<bool> stop_signals{false};
        auto target = pthread_self();
        std::thread interrupter([&] {
            while (!stop_signals.load()) {
                pthread_kill(target, SIGUSR1);
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
        });
        result = int(poll(1000, nullptr, nullptr, nullptr));
        stop_signals = true;
        interrupter.join();
        sigaction(SIGUSR1, &previous, nullptr);
        require(result == -1, "interrupted poll returns wake");
        std::mutex mutex;
        std::condition_variable changed;
        int phase = 0;
        int64_t worker_loop = 0;
        std::thread worker([&] {
            auto worker_value = call("ALooper_prepare", {number(1)});
            std::unique_lock<std::mutex> lock(mutex);
            worker_loop = worker_value;
            phase = 1;
            changed.notify_one();
            changed.wait(lock, [&] { return phase == 2; });
        });
        {
            std::unique_lock<std::mutex> lock(mutex);
            changed.wait(lock, [&] { return phase == 1; });
        }
        bool worker_valid = worker_loop > 0 && worker_loop != loop;
        if (worker_valid)
            call("ALooper_acquire", {number(worker_loop)});
        {
            std::lock_guard<std::mutex> lock(mutex);
            phase = 2;
        }
        changed.notify_one();
        worker.join();
        require(worker_valid, "distinct thread looper");
        int retained_counter =
            int(invoke(reinterpret_cast<void*>(eventfd), {number(0), number(0x800)})
                    .result_integers[0]);
        require(retained_counter >= 0 &&
                    call("ALooper_addFd", {number(worker_loop), number(retained_counter), number(9),
                                           number(1), number(0), number(0)}) == 1,
                "acquired looper survives owning thread exit");
        call("ALooper_release", {number(worker_loop)});
        require(call("ALooper_addFd", {number(worker_loop), number(retained_counter), number(9),
                                       number(1), number(0), number(0)}) == -1,
                "released looper token expires");
        require(!event_descriptor_close(retained_counter), "close retained test counter");
        int shutdown_counter =
            int(invoke(reinterpret_cast<void*>(eventfd), {number(0), number(0x800)})
                    .result_integers[0]);
        require(
            shutdown_counter >= 0 &&
                call("ALooper_addFd", {number(loop), number(shutdown_counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))}) == 1 &&
                lifecycle->retained_thread_callbacks() == 1,
            "shutdown watch is retained");
        int main_fds[3]{};
        require(shiraz_looper_test_current_fds(main_fds), "capture main looper descriptors");
        std::mutex wait_mutex;
        std::condition_variable wait_changed;
        bool waiter_ready = false, waiter_done = false, closer_done = false;
        int64_t wait_result = 0;
        int waiter_fds[3]{};
        std::thread waiter([&] {
            auto wait_loop = call("ALooper_prepare", {number(1)});
            {
                std::lock_guard<std::mutex> lock(wait_mutex);
                waiter_ready = wait_loop > 0 && shiraz_looper_test_current_fds(waiter_fds);
                wait_changed.notify_all();
            }
            auto value =
                call("ALooper_pollOnce", {number(uint32_t(-1)), number(0), number(0), number(0)});
            {
                std::lock_guard<std::mutex> lock(wait_mutex);
                wait_result = value;
                waiter_done = true;
                wait_changed.notify_all();
            }
        });
        {
            std::unique_lock<std::mutex> lock(wait_mutex);
            require(
                wait_changed.wait_for(lock, std::chrono::seconds(10), [&] { return waiter_ready; }),
                "shutdown waiter ready");
        }
        std::atomic<bool> stop_waker{false};
        std::thread waker([&] {
            while (!stop_waker.load())
                call("ALooper_wake", {number(loop)});
        });
        {
            std::lock_guard<std::mutex> lock(validation_mutex);
            block_validation = true;
        }
        std::atomic<int64_t> pending_result{0};
        std::thread pending_add([&] {
            pending_result =
                call("ALooper_addFd", {number(loop), number(shutdown_counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))});
        });
        {
            std::unique_lock<std::mutex> lock(validation_mutex);
            require(validation_changed.wait_for(lock, std::chrono::seconds(10),
                                                [&] { return validation_entered; }),
                    "pending registration entered validator");
        }
        std::atomic<bool> shutdown_ok{false}, shutdown_timed_out{false};
        std::thread closer([&] {
            try {
                lifecycle->shutdown();
                shutdown_ok = true;
            } catch (const ElfError& error) {
                shutdown_timed_out = std::string(error.what()) ==
                                     "timed out waiting for guest looper watch registration";
            }
            {
                std::lock_guard<std::mutex> lock(wait_mutex);
                closer_done = true;
            }
            wait_changed.notify_all();
        });
        auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
        while (call("ALooper_forThread") && std::chrono::steady_clock::now() < deadline)
            std::this_thread::yield();
        bool closed_to_new_watches = !call("ALooper_forThread");
        if (stuck_registration) {
            std::unique_lock<std::mutex> lock(wait_mutex);
            if (!wait_changed.wait_for(lock, std::chrono::seconds(8),
                                       [&] { return closer_done && waiter_done; }))
                std::abort();
            require(shutdown_timed_out && !shutdown_ok && wait_result == -4 &&
                        lifecycle->retained_thread_callbacks() >= 1,
                    "stuck registration fails closed and wakes the poller");
        }
        {
            std::lock_guard<std::mutex> lock(validation_mutex);
            validation_release = true;
        }
        validation_changed.notify_all();
        pending_add.join();
        closer.join();
        stop_waker = true;
        waker.join();
        {
            std::unique_lock<std::mutex> lock(wait_mutex);
            if (!wait_changed.wait_for(lock, std::chrono::seconds(10), [&] { return waiter_done; }))
                std::abort();
        }
        waiter.join();
        if (stuck_registration) {
            require(closed_to_new_watches && pending_result == -1,
                    "timed-out registration cannot publish");
            std::cout << "M5_LOOPER_REGISTRATION_TIMEOUT_FAIL_CLOSED poller_woken=1\n"
                      << std::flush;
            std::_Exit(0);
        }
        require(
            closed_to_new_watches && shutdown_ok && pending_result == -1 && wait_result == -4 &&
                !lifecycle->retained_thread_callbacks() && !call("ALooper_forThread") &&
                call("ALooper_addFd", {number(loop), number(shutdown_counter), number(4), number(1),
                                       number(callback), number(uintptr_t(fields))}) == -1,
            "shutdown retires watches, wakes pollers and refuses new watches");
        for (int fd : main_fds) {
            errno = 0;
            require(fcntl(fd, F_GETFD) == -1 && errno == EBADF,
                    "main looper descriptor remains open");
        }
        for (int fd : waiter_fds) {
            errno = 0;
            require(fcntl(fd, F_GETFD) == -1 && errno == EBADF,
                    "waiting looper descriptor remains open");
        }
        close_looper();
        require(!event_descriptor_close(shutdown_counter), "close shutdown counter");
        std::cout << "M5_LOOPER_PASS admitted_guest_callback=1 virtual_eventfd=1 ident=1 wake=1 "
                     "timeout=1 shutdown=1\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
