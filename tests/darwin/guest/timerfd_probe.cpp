// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/looper.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cerrno>
#include <chrono>
#include <cstring>
#include <iostream>
#include <thread>
#include <time.h>
using namespace shiraz::guest;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
static Argument n(uint64_t value) {
    return Argument::integer(value, ArgumentType::Int64);
}
struct TimeSpec {
    int64_t seconds, nanos;
};
struct TimerSpec {
    TimeSpec interval, value;
};
struct EpollEvent {
    uint32_t events, padding;
    uint64_t data;
};
int main() {
    try {
        auto entries = event_exports();
        auto address = [&](const char* name) {
            for (auto& entry : entries)
                if (entry.name == name)
                    return reinterpret_cast<void*>(entry.address);
            throw std::runtime_error(name);
        };
        auto call = [&](const char* name, std::vector<Argument> args = {}) {
            return int64_t(invoke(address(name), args).result_integers[0]);
        };
        int fd = int(call("timerfd_create", {n(1), n(0x80800)}));
        require(fd >= 0, "create nonblocking monotonic timer");
        TimerSpec now{}, old{}, setting{};
        uint64_t ticks = 0;
        require(!call("timerfd_gettime", {n(fd), n(uintptr_t(&now))}) && !now.value.seconds &&
                    !now.value.nanos,
                "initially disarmed");
        require(event_descriptor_read(fd, &ticks, 8) == -1 && errno == EAGAIN,
                "disarmed read EAGAIN");
        require(event_descriptor_read(fd, &ticks, 4) == -1 && errno == EINVAL,
                "short read rejected");
        require(event_descriptor_write(fd, &ticks, 8) == -1 && errno == EINVAL,
                "timer is not writable");
        EpollEvent interest{1, 0, 42}, observed{};
        int queue = event_epoll_create();
        require(queue >= 0 && !event_epoll_control(queue, 1, fd, &interest),
                "timer epoll registration");
        setting.value = {0, 30000000};
        require(
            !call("timerfd_settime", {n(fd), n(0), n(uintptr_t(&setting)), n(uintptr_t(&old))}) &&
                !old.value.seconds && !old.value.nanos,
            "arm one shot with old state");
        require(event_epoll_wait(queue, &observed, 1, 1000) == 1 && observed.data == 42 &&
                    (observed.events & 1),
                "one-shot becomes readable");
        require(event_descriptor_read(fd, &ticks, 8) == 8 && ticks == 1,
                "one-shot expiration count");
        require(!call("timerfd_gettime", {n(fd), n(uintptr_t(&now))}) && !now.value.seconds &&
                    !now.value.nanos,
                "one-shot disarms");
        setting.interval = {0, 10000000};
        setting.value = {0, 10000000};
        require(!call("timerfd_settime", {n(fd), n(0), n(uintptr_t(&setting)), n(0)}),
                "arm periodic timer");
        std::this_thread::sleep_for(std::chrono::milliseconds(65));
        require(event_descriptor_read(fd, &ticks, 8) == 8 && ticks >= 3,
                "periodic overruns counted");
        require(!call("timerfd_gettime", {n(fd), n(uintptr_t(&now))}) &&
                    now.interval.nanos == 10000000 && now.value.nanos > 0,
                "periodic remaining time");
        TimerSpec disarm{};
        require(
            !call("timerfd_settime", {n(fd), n(0), n(uintptr_t(&disarm)), n(uintptr_t(&old))}) &&
                old.interval.nanos == 10000000,
            "disarm reports old interval");
        require(event_descriptor_read(fd, &ticks, 8) == -1 && errno == EAGAIN,
                "rearm resets unread ticks");
        timespec native{};
        require(!clock_gettime(CLOCK_UPTIME_RAW, &native), "monotonic clock");
        setting = {};
        setting.value = {native.tv_sec, native.tv_nsec + 30000000};
        if (setting.value.nanos >= 1000000000) {
            ++setting.value.seconds;
            setting.value.nanos -= 1000000000;
        }
        require(!call("timerfd_settime", {n(fd), n(1), n(uintptr_t(&setting)), n(0)}),
                "absolute monotonic timer");
        require(event_epoll_wait(queue, &observed, 1, 1000) == 1 &&
                    event_descriptor_read(fd, &ticks, 8) == 8 && ticks == 1,
                "absolute timer expires");
        auto loop_entries = looper_exports([](const void*) { return false; });
        auto loop_call = [&](const char* name, std::vector<Argument> args = {}) {
            for (const auto& entry : loop_entries)
                if (entry.name == name)
                    return int64_t(
                        invoke(reinterpret_cast<void*>(entry.address), args).result_integers[0]);
            throw std::runtime_error(name);
        };
        auto loop = loop_call("ALooper_prepare", {n(1)});
        require(loop > 0 &&
                    loop_call("ALooper_addFd", {n(loop), n(fd), n(73), n(1), n(0), n(0)}) == 1,
                "add timer to Android looper");
        setting = {};
        setting.value = {0, 30000000};
        require(!call("timerfd_settime", {n(fd), n(0), n(uintptr_t(&setting)), n(0)}),
                "arm looper timer");
        int ready_fd = -1, ready_events = 0;
        void* ready_data = nullptr;
        int64_t loop_result = -1;
        for (int i = 0; i < 4 && loop_result == -1; ++i)
            loop_result = loop_call("ALooper_pollOnce",
                                    {n(1000), n(uintptr_t(&ready_fd)), n(uintptr_t(&ready_events)),
                                     n(uintptr_t(&ready_data))});
        require(loop_result == 73 && ready_fd == fd && (ready_events & 1) &&
                    event_descriptor_read(fd, &ticks, 8) == 8 && ticks == 1,
                "Android looper observes real timer expiry");
        require(loop_call("ALooper_removeFd", {n(loop), n(fd)}) == 1, "remove timer from looper");
        setting.value = {0, 1000000000};
        require(call("timerfd_settime", {n(fd), n(0), n(uintptr_t(&setting)), n(0)}) == -1 &&
                    *guest_errno_location() == 22,
                "invalid nanoseconds rejected");
        require(call("timerfd_create", {n(7), n(0)}) == -1 && *guest_errno_location() == 95,
                "boottime explicitly unsupported");
        int real = int(call("timerfd_create", {n(0), n(0x800)}));
        require(real >= 0, "realtime relative timer supported");
        setting = {};
        setting.value = {1, 0};
        require(call("timerfd_settime", {n(real), n(1), n(uintptr_t(&setting)), n(0)}) == -1 &&
                    *guest_errno_location() == 95,
                "absolute realtime explicitly unsupported");
        require(call("timerfd_settime", {n(real), n(2), n(uintptr_t(&setting)), n(0)}) == -1 &&
                    *guest_errno_location() == 95,
                "clock-change cancellation unsupported");
        int alias = event_descriptor_dup(fd);
        require(alias >= 0 && !event_descriptor_close(fd), "timer alias remains after close");
        setting = {};
        setting.value = {0, 20000000};
        require(!call("timerfd_settime", {n(alias), n(0), n(uintptr_t(&setting)), n(0)}),
                "arm through alias");
        require(event_epoll_wait(queue, &observed, 1, 1000) == 1 &&
                    event_descriptor_read(alias, &ticks, 8) == 8 && ticks == 1,
                "watch survives original descriptor close");
        require(!event_descriptor_close(alias) && !event_descriptor_close(real) &&
                    !event_descriptor_close(queue),
                "close timer descriptors");
        int blocking = int(call("timerfd_create", {n(1), n(0)}));
        require(blocking >= 0, "blocking timer");
        setting = {};
        setting.value = {0, 30000000};
        require(!call("timerfd_settime", {n(blocking), n(0), n(uintptr_t(&setting)), n(0)}),
                "arm blocking timer");
        ssize_t bytes = 0;
        uint64_t worker_ticks = 0;
        std::thread reader([&] { bytes = event_descriptor_read(blocking, &worker_ticks, 8); });
        reader.join();
        require(bytes == 8 && worker_ticks == 1 && !event_descriptor_close(blocking),
                "blocking read wakes on expiration");
        std::cout << "M5_TIMERFD_PASS one_shot=1 periodic=1 monotonic_absolute=1 alias=1 epoll=1 "
                     "blocking=1 unsupported=explicit\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
