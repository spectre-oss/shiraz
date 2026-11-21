// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "guest_io.hpp"
#include "shiraz/darwin/guest/process_arguments_stream.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <climits>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <poll.h>
#include <signal.h>
#include <sys/event.h>
#include <sys/select.h>
#include <time.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr uint64_t maximum = UINT64_MAX - 1;
constexpr size_t descriptor_limit = 4096, poll_limit = 4096;
static_assert(std::atomic<uint8_t>::is_always_lock_free,
              "ordinary signal-handler IO requires lock-free descriptor hints");
alignas(64) std::atomic<uint8_t> descriptor_hints[event_descriptor_bound]{};
bool hinted(int fd) noexcept {
    return fd >= 0 && fd < event_descriptor_bound &&
           descriptor_hints[fd].load(std::memory_order_acquire) != 0;
}
void set_hint(int fd, bool value) noexcept {
    descriptor_hints[fd].store(uint8_t(value), std::memory_order_release);
}
constexpr uint16_t in = 1, pri = 2, out = 4, error = 8, hup = 16, invalid = 32, rdnorm = 64,
                   rdband = 128, wrnorm = 256, wrband = 512;
constexpr uint16_t supported =
    in | pri | out | error | hup | invalid | rdnorm | rdband | wrnorm | wrband;
struct Failure {
    int error;
};
template <typename T = int> T failure(int value) {
    errno = value;
    return T(-1);
}
[[noreturn]] void invariant() {
    std::fputs("SHIRAZ_GUEST_FATAL eventfd readiness invariant\n", stderr);
    std::abort();
}
struct Pipe {
    int read = -1, write = -1;
    Pipe() {
        int descriptors[2];
        if (::pipe(descriptors))
            throw Failure{errno};
        read = descriptors[0];
        write = descriptors[1];
        for (int fd : descriptors)
            if (::fcntl(fd, F_SETFD, FD_CLOEXEC) || ::fcntl(fd, F_SETFL, O_NONBLOCK)) {
                int saved = errno;
                ::close(read);
                ::close(write);
                read = write = -1;
                throw Failure{saved};
            }
    }
    ~Pipe() {
        int saved = errno;
        if (read >= 0)
            ::close(read);
        if (write >= 0)
            ::close(write);
        errno = saved;
    }
    Pipe(const Pipe&) = delete;
    Pipe& operator=(const Pipe&) = delete;
};
void marker(Pipe& pipe, bool& present, bool wanted) {
    if (present == wanted)
        return;
    uint8_t byte = 1;
    ssize_t result;
    do {
        result = wanted ? ::write(pipe.write, &byte, 1) : ::read(pipe.read, &byte, 1);
    } while (result < 0 && errno == EINTR);
    if (result != 1)
        invariant();
    present = wanted;
}
struct Token {
    int descriptor = -1;
    Token() {
        descriptor = ::kqueue();
        if (descriptor < 0)
            throw Failure{errno};
        if (::fcntl(descriptor, F_SETFD, FD_CLOEXEC)) {
            int saved = errno;
            ::close(descriptor);
            descriptor = -1;
            throw Failure{saved};
        }
    }
    ~Token() {
        int saved = errno;
        if (descriptor >= 0)
            ::close(descriptor);
        errno = saved;
    }
    Token(const Token&) = delete;
    Token& operator=(const Token&) = delete;
};
// All virtual descriptor kinds must share this registry so dup2 can atomically
// replace one kind with another while keeping aliases and close state coherent.
struct Description {
    size_t aliases = 0;
    virtual ~Description() = default;
    virtual void close_last() = 0;
};
struct Counter : Description {
    std::mutex mutex;
    // Public aliases are empty kernel queues, never readiness pipes. A raw IO
    // race with dup2/fd reuse therefore cannot consume private marker bytes.
    Token token;
    Pipe readable, writable;
    bool read_marker = false, write_marker = false, semaphore, closed = false, timer = false;
    uint64_t value;
    int status;
    std::vector<std::weak_ptr<Pipe>> writers;
    Counter(unsigned initial, int flags, bool is_timer = false)
        : semaphore(!is_timer && (flags & 1)), timer(is_timer), value(initial),
          status(int(b::kO_RDWR) | (flags & int(b::kO_NONBLOCK))) {
        update();
    }
    void update() {
        marker(readable, read_marker, value != 0 || closed);
        marker(writable, write_marker, (!timer && value < maximum) || closed);
    }
    void wake_writers() {
        for (auto it = writers.begin(); it != writers.end();) {
            if (auto pipe = it->lock()) {
                uint8_t byte = 1;
                ssize_t result;
                do {
                    result = ::write(pipe->write, &byte, 1);
                } while (result < 0 && errno == EINTR);
                if (result != 1 && !(result < 0 && errno == EAGAIN))
                    invariant();
                ++it;
            } else
                it = writers.erase(it);
        }
    }
    void close_last() override {
        std::lock_guard<std::mutex> lock(mutex);
        closed = true;
        update();
        wake_writers();
    }
};
std::atomic<unsigned> timer_count{0};
struct TimerPermit {
    ~TimerPermit() {
        timer_count.fetch_sub(1, std::memory_order_relaxed);
    }
};
int64_t timer_now() {
    timespec now{};
    if (::clock_gettime(CLOCK_UPTIME_RAW, &now))
        throw Failure{errno};
    if (now.tv_sec < 0 || uint64_t(now.tv_sec) > uint64_t(INT64_MAX / 1000000000))
        throw Failure{EOVERFLOW};
    auto value = __int128(now.tv_sec) * 1000000000 + now.tv_nsec;
    if (value > INT64_MAX)
        throw Failure{EOVERFLOW};
    return int64_t(value);
}
struct Timer final : Counter {
    std::shared_ptr<TimerPermit> permit;
    std::condition_variable changed;
    std::thread worker;
    int clock;
    int64_t deadline = 0, interval = 0;
    bool armed = false;
    Timer(int which, int flags, std::shared_ptr<TimerPermit> reservation)
        : Counter(0, flags, true), permit(std::move(reservation)), clock(which) {}
    void advance(int64_t now) {
        if (!armed || now < deadline)
            return;
        uint64_t count = 1;
        if (interval)
            count += uint64_t((now - deadline) / interval);
        value = count > UINT64_MAX - value ? UINT64_MAX : value + count;
        if (!interval)
            armed = false;
        else {
            auto next = __int128(deadline) + __int128(count) * interval;
            if (next > INT64_MAX)
                armed = false;
            else
                deadline = int64_t(next);
        }
        update();
    }
    void run() noexcept {
        try {
            std::unique_lock<std::mutex> lock(mutex);
            while (!closed) {
                // Once readable, accumulate further periodic overruns lazily on the
                // next read/gettime instead of waking a worker at every interval.
                if (!armed || value) {
                    changed.wait(lock);
                    continue;
                }
                auto now = timer_now();
                advance(now);
                if (armed && !value)
                    changed.wait_for(lock, std::chrono::nanoseconds(std::min<int64_t>(
                                               deadline - now, 86400LL * 1000000000)));
            }
        } catch (...) {
            std::fputs("SHIRAZ_GUEST_FATAL timerfd worker failure\n", stderr);
            std::abort();
        }
    }
    void close_last() override {
        // A read or poll that already retained this description continues after
        // the last fd closes, as it holds an open-file-description reference.
        // Destruction stops the worker once those in-flight references drain.
    }
    ~Timer() override {
        {
            std::lock_guard<std::mutex> lock(mutex);
            closed = true;
            update();
        }
        changed.notify_all();
        if (worker.joinable())
            worker.join();
    }
};
// A token is an owned, non-readable kernel descriptor. All aliases live in
// the same registry as eventfd, so dup2 cannot expose the token as host IO.
struct SysfsDirectory final : Description {
    Token token;
    const unsigned node;
    std::mutex mutex;
    int status;
    bool closed = false;
    SysfsDirectory(unsigned value, int flags)
        : node(value), status(int(b::kO_RDONLY | b::kO_LARGEFILE) |
                              (flags & int(b::kO_NONBLOCK | b::kO_DIRECTORY | b::kO_NOFOLLOW))) {}
    void close_last() override {
        std::lock_guard<std::mutex> lock(mutex);
        closed = true;
    }
};
// One shared open-file description for bounded, read-only process views.
struct ProcessFile final : Description {
    Token token;
    Pipe readable, writable;
    struct Stream {
        ProcessArgumentsStream arguments;
        std::unique_ptr<MapsStream> maps;
        int read(void* p, size_t n, size_t* out) {
            return maps ? maps->read(p, n, out) : arguments.read(p, n, out);
        }
        int pread(int64_t o, void* p, size_t n, size_t* out) {
            return maps ? maps->pread(o, p, n, out) : arguments.pread(o, p, n, out);
        }
        int seek(int64_t o, int w, int64_t* out) {
            return maps ? maps->seek(o, w, out) : arguments.seek(o, w, out);
        }
    } stream;
    std::mutex mutex;
    bool read_marker = false, write_marker = false, closed = false;
    int status;
    explicit ProcessFile(int flags)
        : status(int(b::kO_RDONLY | b::kO_LARGEFILE) |
                 ((flags & O_NONBLOCK) ? int(b::kO_NONBLOCK) : 0)) {
        marker(readable, read_marker, true);
        marker(writable, write_marker, true);
    }
    void close_last() override {
        std::lock_guard<std::mutex> lock(mutex);
        closed = true;
    }
};
struct Registry {
    const pid_t process = ::getpid();
    std::mutex mutex;
    std::map<int, std::shared_ptr<Description>> descriptors;
};
Registry& registry() {
    static auto* value = new Registry;
    return *value;
}
bool same_process() {
    return registry().process == ::getpid();
}
std::shared_ptr<Description> lookup_description(int fd) {
    if (!hinted(fd))
        return nullptr;
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    auto found = table.descriptors.find(fd);
    return found == table.descriptors.end() ? nullptr : found->second;
}
std::shared_ptr<Counter> lookup(int fd) {
    return std::dynamic_pointer_cast<Counter>(lookup_description(fd));
}
int duplicate_locked(Registry& table, int fd, const std::shared_ptr<Description>& description,
                     int minimum, bool cloexec) {
    if (table.descriptors.size() >= descriptor_limit)
        return failure(EMFILE);
    if (minimum >= event_descriptor_bound)
        return failure(EINVAL);
    int result = ::fcntl(fd, cloexec ? F_DUPFD_CLOEXEC : F_DUPFD, minimum);
    if (result < 0)
        return result;
    if (result >= event_descriptor_bound) {
        ::close(result);
        return failure(EMFILE);
    }
    set_hint(result, true);
    try {
        table.descriptors.emplace(result, description);
    } catch (const std::bad_alloc&) {
        ::close(result);
        set_hint(result, false);
        return failure(ENOMEM);
    }
    ++description->aliases;
    return result;
}
int create_counter(unsigned initial, int flags) {
    if (in_guest_signal_handler())
        return failure(ENOTSUP);
    if (!same_process())
        return failure(ENOTSUP);
    if (flags & ~int(1 | b::kO_NONBLOCK | b::kO_CLOEXEC))
        return failure(EINVAL);
    try {
        auto counter = std::make_shared<Counter>(initial, flags);
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        return duplicate_locked(table, counter->token.descriptor, counter, 0,
                                flags & b::kO_CLOEXEC);
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int timer_create(int clock, int flags) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if (clock != 0 && clock != 1)
        return failure(clock == 7 ? ENOTSUP : EINVAL);
    if (flags & ~int(b::kO_NONBLOCK | b::kO_CLOEXEC))
        return failure(EINVAL);
    auto count = timer_count.load(std::memory_order_relaxed);
    do {
        if (count >= 128)
            return failure(EMFILE);
    } while (!timer_count.compare_exchange_weak(count, count + 1, std::memory_order_relaxed));
    std::shared_ptr<TimerPermit> permit;
    try {
        permit = std::make_shared<TimerPermit>();
    } catch (const std::bad_alloc&) {
        timer_count.fetch_sub(1, std::memory_order_relaxed);
        return failure(ENOMEM);
    }
    try {
        auto timer = std::make_shared<Timer>(clock, flags, std::move(permit));
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        return duplicate_locked(table, timer->token.descriptor, timer, 0, flags & b::kO_CLOEXEC);
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    } catch (const std::system_error&) {
        return failure(EAGAIN);
    }
}
bool timer_duration(int64_t seconds, int64_t nanos, int64_t& result) {
    if (seconds < 0 || nanos < 0 || nanos >= 1000000000) {
        errno = EINVAL;
        return false;
    }
    auto total = __int128(seconds) * 1000000000 + nanos;
    if (total > INT64_MAX) {
        errno = EOVERFLOW;
        return false;
    }
    result = int64_t(total);
    return true;
}
void timer_snapshot(const Timer& timer, int64_t now, void* output) {
    int64_t remaining = timer.armed && timer.deadline > now ? timer.deadline - now : 0;
    int64_t fields[4] = {timer.interval / 1000000000, timer.interval % 1000000000,
                         remaining / 1000000000, remaining % 1000000000};
    std::memcpy(output, fields, sizeof(fields));
}
int timer_settime(int fd, int flags, const void* input, void* old_output) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if (!input)
        return failure(EFAULT);
    if (flags & ~3)
        return failure(EINVAL);
    if (flags & 2)
        return failure(ENOTSUP);
    int64_t fields[4];
    std::memcpy(fields, input, sizeof(fields));
    int64_t interval = 0, value = 0;
    if (!timer_duration(fields[0], fields[1], interval) ||
        !timer_duration(fields[2], fields[3], value))
        return -1;
    auto timer = std::dynamic_pointer_cast<Timer>(lookup_description(fd));
    if (!timer)
        return failure(::fcntl(fd, F_GETFD) < 0 ? EBADF : EINVAL);
    if (timer->clock == 0 && (flags & 1) && value)
        return failure(ENOTSUP);
    try {
        std::lock_guard<std::mutex> lock(timer->mutex);
        if (timer->closed)
            return failure(EBADF);
        auto now = timer_now();
        if (value && !(flags & 1) && value > INT64_MAX - now)
            return failure(EOVERFLOW);
        if (value && !timer->worker.joinable())
            timer->worker = std::thread([target = timer.get()] { target->run(); });
        timer->advance(now);
        if (old_output)
            timer_snapshot(*timer, now, old_output);
        timer->interval = interval;
        timer->deadline = (flags & 1) ? value : now + value;
        timer->armed = value != 0;
        timer->value = 0;
        timer->update();
        timer->advance(now);
        timer->changed.notify_all();
        return 0;
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::system_error&) {
        return failure(EAGAIN);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int timer_gettime(int fd, void* output) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if (!output)
        return failure(EFAULT);
    auto timer = std::dynamic_pointer_cast<Timer>(lookup_description(fd));
    if (!timer)
        return failure(::fcntl(fd, F_GETFD) < 0 ? EBADF : EINVAL);
    try {
        std::lock_guard<std::mutex> lock(timer->mutex);
        if (timer->closed)
            return failure(EBADF);
        auto now = timer_now();
        timer->advance(now);
        timer_snapshot(*timer, now, output);
        return 0;
    } catch (const Failure& error) {
        return failure(error.error);
    }
}
int wait_readable(int fd) {
    pollfd descriptor{fd, POLLIN, 0};
    int result = ::poll(&descriptor, 1, -1);
    return result < 0 ? -1 : 0;
}
ssize_t counter_read(const std::shared_ptr<Counter>& counter, void* buffer, size_t count) {
    if (count < 8)
        return failure<ssize_t>(EINVAL);
    if (!buffer)
        return failure<ssize_t>(EFAULT);
    for (;;) {
        {
            std::lock_guard<std::mutex> lock(counter->mutex);
            if (counter->closed)
                return failure<ssize_t>(EBADF);
            if (counter->timer) {
                try {
                    static_cast<Timer*>(counter.get())->advance(timer_now());
                } catch (const Failure& error) {
                    return failure<ssize_t>(error.error);
                }
            }
            if (counter->value) {
                uint64_t result = counter->semaphore ? 1 : counter->value;
                counter->value -= result;
                counter->update();
                counter->wake_writers();
                if (counter->timer)
                    static_cast<Timer*>(counter.get())->changed.notify_all();
                std::memcpy(buffer, &result, sizeof(result));
                return 8;
            }
            if (counter->status & b::kO_NONBLOCK)
                return failure<ssize_t>(EAGAIN);
        }
        if (wait_readable(counter->readable.read))
            return -1;
    }
}
ssize_t counter_write(const std::shared_ptr<Counter>& counter, const void* buffer, size_t count) {
    if (counter->timer)
        return failure<ssize_t>(EINVAL);
    if (count < 8)
        return failure<ssize_t>(EINVAL);
    if (!buffer)
        return failure<ssize_t>(EFAULT);
    uint64_t value;
    std::memcpy(&value, buffer, sizeof(value));
    if (value == UINT64_MAX)
        return failure<ssize_t>(EINVAL);
    std::shared_ptr<Pipe> waiter;
    try {
        for (;;) {
            {
                std::lock_guard<std::mutex> lock(counter->mutex);
                if (counter->closed)
                    return failure<ssize_t>(EBADF);
                if (value <= maximum - counter->value) {
                    counter->value += value;
                    counter->update();
                    return 8;
                }
                if (counter->status & b::kO_NONBLOCK)
                    return failure<ssize_t>(EAGAIN);
                if (!waiter) {
                    counter->writers.erase(
                        std::remove_if(counter->writers.begin(), counter->writers.end(),
                                       [](const auto& entry) { return entry.expired(); }),
                        counter->writers.end());
                    if (counter->writers.size() >= 128)
                        return failure<ssize_t>(EAGAIN);
                    waiter = std::make_shared<Pipe>();
                    counter->writers.push_back(waiter);
                }
            }
            if (wait_readable(waiter->read))
                return -1;
            uint8_t bytes[128];
            while (::read(waiter->read, bytes, sizeof(bytes)) > 0) {
            }
        }
    } catch (const Failure& error) {
        return failure<ssize_t>(error.error);
    } catch (const std::bad_alloc&) {
        return failure<ssize_t>(ENOMEM);
    }
}
int convenience_read(int fd, uint64_t* value) {
    ssize_t result = event_descriptor_read(fd, value, 8);
    if (result == 8)
        return 0;
    if (result < 0 && errno)
        *guest_errno_location() = errno_to_guest(errno);
    return -1;
}
int convenience_write(int fd, uint64_t value) {
    ssize_t result = event_descriptor_write(fd, &value, 8);
    if (result == 8)
        return 0;
    if (result < 0 && errno)
        *guest_errno_location() = errno_to_guest(errno);
    return -1;
}
struct GuestPoll {
    int32_t fd;
    uint16_t events, result;
};
static_assert(sizeof(GuestPoll) == 8);
short native_events(uint16_t value) {
    short result = 0;
    if (value & in)
        result |= POLLIN;
    if (value & pri)
        result |= POLLPRI;
    if (value & out)
        result |= POLLOUT;
    if (value & rdnorm)
        result |= POLLRDNORM;
    if (value & rdband)
        result |= POLLRDBAND;
    if (value & wrnorm)
        result |= POLLWRNORM;
    if (value & wrband)
        result |= POLLWRBAND;
    return result;
}
uint16_t guest_events(short value, uint16_t requested) {
    uint16_t result = 0;
    if (value & POLLIN)
        result |= requested & in;
    if (value & POLLPRI)
        result |= requested & pri;
    if (value & POLLOUT)
        result |= requested & out;
    if (value & POLLRDNORM)
        result |= requested & rdnorm;
    if (value & POLLRDBAND)
        result |= requested & rdband;
    if (value & POLLWRNORM)
        result |= requested & wrnorm;
    if (value & POLLWRBAND)
        result |= requested & wrband;
    if (value & POLLERR)
        result |= error;
    if (value & POLLHUP)
        result |= hup;
    if (value & POLLNVAL)
        result |= invalid;
    return result;
}
struct PollEntry {
    GuestPoll guest{};
    std::shared_ptr<Counter> counter;
    std::shared_ptr<ProcessFile> arguments;
    size_t first = 0, second = SIZE_MAX;
};
struct PollSet {
    void* output;
    std::vector<PollEntry> entries;
    std::vector<pollfd> native;
    PollSet(void* address, uint32_t count) : output(address) {
        if (count > poll_limit)
            throw Failure{EINVAL};
        if (count && !address)
            throw Failure{EFAULT};
        entries.reserve(count);
        native.reserve(size_t(count) * 2);
        for (uint32_t i = 0; i < count; ++i) {
            GuestPoll guest;
            std::memcpy(&guest, static_cast<uint8_t*>(address) + i * 8, 8);
            if (guest.fd >= 0 && (guest.events & ~supported))
                throw Failure{ENOTSUP};
            guest.result = 0;
            auto description = guest.fd >= 0 ? lookup_description(guest.fd) : nullptr;
            PollEntry entry;
            entry.guest = guest;
            entry.first = native.size();
            entry.counter = std::dynamic_pointer_cast<Counter>(description);
            entry.arguments = std::dynamic_pointer_cast<ProcessFile>(description);
            if (description && !entry.counter && !entry.arguments)
                throw Failure{ENOTSUP};
            if (entry.counter) {
                short read = guest.events & (in | rdnorm) ? POLLIN : 0;
                native.push_back({entry.counter->readable.read, read, 0});
                entry.second = native.size();
                short write = guest.events & (out | wrnorm) ? POLLIN : 0;
                native.push_back({entry.counter->writable.read, write, 0});
            } else if (entry.arguments) {
                short read = guest.events & (in | rdnorm) ? POLLIN : 0;
                native.push_back({entry.arguments->readable.read, read, 0});
                entry.second = native.size();
                short write = guest.events & (out | wrnorm) ? POLLIN : 0;
                native.push_back({entry.arguments->writable.read, write, 0});
            } else
                native.push_back({guest.fd, native_events(guest.events), 0});
            entries.push_back(std::move(entry));
        }
    }
    int publish() {
        int ready = 0;
        for (size_t i = 0; i < entries.size(); ++i) {
            auto& entry = entries[i];
            uint16_t result = 0;
            if (entry.counter) {
                std::lock_guard<std::mutex> lock(entry.counter->mutex);
                if (entry.counter->closed)
                    result = invalid;
                else {
                    if (native[entry.first].revents & POLLIN)
                        result |= entry.guest.events & (in | rdnorm);
                    if (native[entry.second].revents & POLLIN)
                        result |= entry.guest.events & (out | wrnorm);
                }
            } else if (entry.arguments) {
                std::lock_guard<std::mutex> lock(entry.arguments->mutex);
                if (entry.arguments->closed)
                    result = invalid;
                else {
                    if (native[entry.first].revents & POLLIN)
                        result |= entry.guest.events & (in | rdnorm);
                    if (native[entry.second].revents & POLLIN)
                        result |= entry.guest.events & (out | wrnorm);
                }
            } else
                result = guest_events(native[entry.first].revents, entry.guest.events);
            entry.guest.result = result;
            std::memcpy(static_cast<uint8_t*>(output) + i * 8 + 6, &result, 2);
            ready += result != 0;
        }
        return ready;
    }
};
int guest_poll(void* descriptors, uint32_t count, int timeout) {
    if (in_guest_signal_handler())
        return failure(ENOTSUP);
    if (!same_process())
        return failure(ENOTSUP);
    try {
        PollSet set(descriptors, count);
        if (::poll(set.native.data(), nfds_t(set.native.size()), timeout) < 0)
            return -1;
        return set.publish();
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int guest_mask(const void* guest, sigset_t& native) {
    uint64_t bits;
    std::memcpy(&bits, guest, 8);
    const int signals[] = {SIGHUP,  SIGINT,  SIGQUIT, SIGILL,  SIGTRAP,   SIGABRT, SIGBUS,
                           SIGFPE,  SIGKILL, SIGUSR1, SIGSEGV, SIGUSR2,   SIGPIPE, SIGALRM,
                           SIGTERM, 0,       SIGCHLD, SIGCONT, SIGSTOP,   SIGTSTP, SIGTTIN,
                           SIGTTOU, SIGURG,  SIGXCPU, SIGXFSZ, SIGVTALRM, SIGPROF, SIGWINCH,
                           SIGIO,   0,       SIGSYS};
    if (bits >> 31)
        return failure(ENOTSUP); // No fake Linux realtime signals.
    sigemptyset(&native);
    for (unsigned i = 0; i < 31; ++i)
        if (bits & (uint64_t(1) << i)) {
            if (!signals[i])
                return failure(ENOTSUP);
            if (sigaddset(&native, signals[i]))
                return -1;
        }
    return 0;
}
int guest_ppoll(void* descriptors, uint32_t count, const void* timeout, const void* mask) {
    if (in_guest_signal_handler())
        return failure(ENOTSUP);
    if (!same_process())
        return failure(ENOTSUP);
    try {
        PollSet set(descriptors, count);
        timespec duration{}, *wait = nullptr;
        if (timeout) {
            int64_t words[2];
            std::memcpy(words, timeout, 16);
            if (words[0] < 0 || words[1] < 0 || words[1] >= 1000000000)
                return failure(EINVAL);
            duration.tv_sec = words[0];
            duration.tv_nsec = words[1];
            wait = &duration;
        }
        sigset_t native_mask;
        if (mask && guest_mask(mask, native_mask))
            return -1;
        fd_set reads, writes, exceptions;
        FD_ZERO(&reads);
        FD_ZERO(&writes);
        FD_ZERO(&exceptions);
        int highest = -1;
        bool invalid_fd = false;
        for (const auto& entry : set.entries)
            if (entry.guest.fd >= 0 &&
                (!(entry.guest.events & (in | pri | out | rdnorm | wrnorm)) ||
                 (entry.guest.events & (rdband | wrband))))
                return failure(ENOTSUP);
        for (auto& entry : set.native) {
            if (entry.fd < 0)
                continue;
            if (::fcntl(entry.fd, F_GETFD) < 0) {
                if (errno == EBADF) {
                    invalid_fd = true;
                    continue;
                }
                return -1;
            }
            if (entry.fd >= FD_SETSIZE)
                return failure(ENOTSUP);
            if (entry.events & (POLLIN | POLLRDNORM))
                FD_SET(entry.fd, &reads);
            if (entry.events & (POLLOUT | POLLWRNORM))
                FD_SET(entry.fd, &writes);
            if (entry.events & POLLPRI)
                FD_SET(entry.fd, &exceptions);
            highest = std::max(highest, entry.fd);
        }
        // pselect applies the translated signal mask atomically during the wait;
        // never emulate ppoll with the racy sigprocmask()+poll sequence.
        timespec zero{};
        if (invalid_fd)
            wait = &zero;
        int result = ::pselect(highest + 1, &reads, &writes, &exceptions, wait,
                               mask ? &native_mask : nullptr);
        if (result < 0)
            return -1;
        if (::poll(set.native.data(), nfds_t(set.native.size()), 0) < 0)
            return -1;
        return set.publish();
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int unsupported_inotify() {
    return failure(ENOSYS);
}
int unsupported_inotify_flags(int) {
    return failure(ENOSYS);
}
int unsupported_inotify_add(int, const char*, uint32_t) {
    return failure(ENOSYS);
}
int unsupported_inotify_remove(int, uint32_t) {
    return failure(ENOSYS);
}
struct GuestEpollEvent {
    uint32_t events, padding;
    uint64_t data;
};
static_assert(sizeof(GuestEpollEvent) == 16 && offsetof(GuestEpollEvent, data) == 8);
struct EpollWatch {
    int fd;
    uint32_t events;
    uint64_t data, id;
    bool active = true;
    std::shared_ptr<Counter> counter;
    struct Filter {
        int fd;
        int16_t type;
        uint32_t bit;
    };
    std::vector<Filter> filters;
    std::vector<int> owned_filters;
    ~EpollWatch() {
        int saved = errno;
        for (int fd : owned_filters)
            ::close(fd);
        errno = saved;
    }
};
struct Epoll {
    int descriptor;
    bool closed = false;
    std::mutex mutex;
    // A recycled fd can name a new counter while its old description remains
    // watched through another alias. Keep both Linux epoll keys distinct.
    std::map<std::pair<int, uintptr_t>, std::shared_ptr<EpollWatch>> watches;
    std::vector<std::shared_ptr<EpollWatch>> identities;
};
auto& epolls_mutex = *new std::mutex;
auto& epolls = *new std::map<int, std::shared_ptr<Epoll>>;
std::shared_ptr<Epoll> epoll_lookup(int fd) {
    std::lock_guard<std::mutex> lock(epolls_mutex);
    auto it = epolls.find(fd);
    return it == epolls.end() ? nullptr : it->second;
}
int epoll_create_flags(int flags) {
    if (!same_process() || in_guest_signal_handler())
        return failure(ENOTSUP);
    if (flags & ~0x80000)
        return failure(EINVAL);
    try {
        auto value = std::make_shared<Epoll>();
        std::lock_guard<std::mutex> lock(epolls_mutex);
        if (epolls.size() >= 256)
            return failure(EMFILE);
        int fd = ::kqueue();
        if (fd < 0)
            return -1;
        if (::fcntl(fd, F_SETFD, flags ? FD_CLOEXEC : 0)) {
            int error = errno;
            ::close(fd);
            return failure(error);
        }
        value->descriptor = fd;
        try {
            epolls.emplace(fd, value);
        } catch (...) {
            ::close(fd);
            throw;
        }
        return fd;
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int epoll_create_legacy(int size) {
    return size > 0 ? epoll_create_flags(0) : failure(EINVAL);
}
int install_epoll_watch(Epoll& queue, const EpollWatch& watch, bool remove = false) {
    for (size_t i = 0; i < watch.filters.size(); ++i) {
        auto f = watch.filters[i];
        struct kevent change{};
        unsigned flags = remove ? EV_DELETE : EV_ADD | EV_ENABLE;
        if (!remove && (watch.events & 0x80000000u))
            flags |= EV_CLEAR;
        if (!remove && (watch.events & 0x40000000u))
            flags |= EV_DISPATCH;
        EV_SET(&change, uintptr_t(f.fd), f.type, flags, 0, 0,
               reinterpret_cast<void*>((watch.id << 2) | i));
        if (::kevent(queue.descriptor, &change, 1, nullptr, 0, nullptr) &&
            !(remove && (errno == ENOENT || errno == EBADF)))
            return -1;
    }
    return 0;
}
int epoll_control(int epfd, int operation, int fd, const void* input) {
    if (!same_process() || in_guest_signal_handler())
        return failure(ENOTSUP);
    // Serialize registration against guest close and dup2 replacement, so a
    // stale watch cannot be published for a newly reused descriptor number.
    std::lock_guard<std::mutex> registry_lock(epolls_mutex);
    auto found_queue = epolls.find(epfd);
    if (found_queue == epolls.end())
        return failure(EBADF);
    auto queue = found_queue->second;
    if (fd == epfd)
        return failure(EINVAL);
    if (operation != 1 && operation != 2 && operation != 3)
        return failure(EINVAL);
    if (operation != 2 && !input)
        return failure(EFAULT);
    if (epolls.count(fd))
        return failure(ENOTSUP); // Nested event loops need cycle/ownership handling.
    if (::fcntl(fd, F_GETFD) < 0)
        return -1;
    GuestEpollEvent event{};
    if (input && operation != 2)
        std::memcpy(&event, input, 16);
    if (event.events & ~(1u | 4u | 8u | 16u | 0x2000u | 0x80000000u | 0x40000000u))
        return failure(ENOTSUP);
    auto counter = lookup(fd);
    if (!counter && hinted(fd) && lookup_description(fd))
        return failure(EPERM);
    try {
        std::lock_guard<std::mutex> lock(queue->mutex);
        if (queue->closed)
            return failure(EBADF);
        const auto key = std::make_pair(fd, reinterpret_cast<uintptr_t>(counter.get()));
        auto previous = queue->watches.find(key);
        if (operation == 1 && previous != queue->watches.end())
            return failure(EEXIST);
        if (operation != 1 && previous == queue->watches.end())
            return failure(ENOENT);
        if (operation == 2) {
            if (install_epoll_watch(*queue, *previous->second, true))
                return -1;
            previous->second->active = false;
            queue->identities[size_t(previous->second->id - 1)].reset();
            queue->watches.erase(previous);
            return 0;
        }
        if (queue->watches.size() >= 4096 || queue->identities.size() >= 65536)
            return failure(ENOSPC);
        auto watch = std::make_shared<EpollWatch>();
        watch->fd = fd;
        watch->events = event.events;
        watch->data = event.data;
        watch->counter = counter;
        watch->id = queue->identities.size() + 1;
        // Each virtual watch needs distinct kqueue filter descriptors. Otherwise
        // two aliases of one counter overwrite each other's kevent udata.
        watch->owned_filters.reserve(2);
        watch->filters.reserve(2);
        auto filter_fd = [&](int original) {
            if (!counter)
                return original;
            int copy = ::fcntl(original, F_DUPFD_CLOEXEC, 0);
            if (copy < 0)
                throw Failure{errno};
            watch->owned_filters.push_back(copy);
            return copy;
        };
        if (event.events & (1 | 0x2000 | 16 | 8))
            watch->filters.push_back(
                {filter_fd(counter ? counter->readable.read : fd), EVFILT_READ, 1});
        if (event.events & 4)
            watch->filters.push_back({filter_fd(counter ? counter->writable.read : fd),
                                      int16_t(counter ? EVFILT_READ : EVFILT_WRITE), 4});
        queue->identities.push_back(watch);
        std::shared_ptr<EpollWatch> old =
            previous == queue->watches.end() ? nullptr : previous->second;
        if (!old) {
            try {
                queue->watches.emplace(key, watch);
            } catch (...) {
                queue->identities.pop_back();
                throw;
            }
        } // Allocate before changing kernel subscriptions.
        if (old && install_epoll_watch(*queue, *old, true)) {
            watch->active = false;
            queue->identities[size_t(watch->id - 1)].reset();
            return -1;
        }
        if (install_epoll_watch(*queue, *watch)) {
            int error = errno;
            install_epoll_watch(*queue, *watch, true);
            watch->active = false;
            queue->identities[size_t(watch->id - 1)].reset();
            if (old)
                install_epoll_watch(*queue, *old);
            else
                queue->watches.erase(key);
            return failure(error);
        }
        if (old) {
            old->active = false;
            queue->identities[size_t(old->id - 1)].reset();
        }
        queue->watches.find(key)->second = watch;
        return 0;
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int epoll_wait_events(int fd, void* output, int capacity, int timeout) {
    if (!output)
        return failure(EFAULT);
    if (capacity <= 0 || capacity > 4096)
        return failure(EINVAL);
    if (!same_process() || in_guest_signal_handler())
        return failure(ENOTSUP);
    auto queue = epoll_lookup(fd);
    if (!queue)
        return failure(EBADF);
    try {
        std::vector<struct kevent> events(static_cast<size_t>(capacity));
        timespec wait{timeout / 1000, (timeout % 1000) * 1000000L};
        int count = ::kevent(fd, nullptr, 0, events.data(), int(events.size()),
                             timeout < 0 ? nullptr : &wait);
        if (count < 0)
            return -1;
        std::lock_guard<std::mutex> lock(queue->mutex);
        if (queue->closed)
            return failure(EBADF);
        std::map<uint64_t, GuestEpollEvent> ready;
        for (int i = 0; i < count; ++i) {
            auto& event = events[size_t(i)];
            uint64_t encoded = uintptr_t(event.udata), id = encoded >> 2;
            unsigned slot = encoded & 3;
            if (!id || id > queue->identities.size())
                continue;
            auto watch = queue->identities[size_t(id - 1)];
            if (!watch || !watch->active || slot >= watch->filters.size())
                continue;
            if (!ready.count(id) && ready.size() >= size_t(capacity))
                continue;
            uint32_t bits = watch->filters[slot].bit & watch->events;
            if (event.flags & EV_EOF)
                bits |= 16u | (watch->events & 0x2000u);
            if (event.flags & EV_ERROR)
                bits |= 8;
            if (!bits)
                continue;
            auto& value = ready[id];
            value.events |= bits;
            value.data = watch->data;
        }
        size_t n = 0;
        for (auto& entry : ready) {
            std::memcpy(static_cast<uint8_t*>(output) + 16 * n++, &entry.second, 16);
            auto watch = queue->identities[size_t(entry.first - 1)];
            if (watch->events & 0x40000000u)
                for (auto f : watch->filters) {
                    struct kevent change{};
                    EV_SET(&change, uintptr_t(f.fd), f.type, EV_DISABLE, 0, 0, nullptr);
                    ::kevent(fd, &change, 1, nullptr, 0, nullptr);
                }
        }
        return int(n);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
// Caller holds epolls_mutex, including across the kernel fd replacement.
void epoll_forget_locked(int fd, const Description* description = nullptr) {
    auto own = epolls.find(fd);
    if (own != epolls.end()) {
        std::lock_guard<std::mutex> qlock(own->second->mutex);
        own->second->closed = true;
        epolls.erase(own);
    }
    for (auto& entry : epolls) {
        std::lock_guard<std::mutex> qlock(entry.second->mutex);
        for (auto found = entry.second->watches.begin(); found != entry.second->watches.end();) {
            auto& watch = found->second;
            bool matches = description ? watch->counter.get() == description
                                       : watch->fd == fd && !watch->counter;
            if (!matches) {
                ++found;
                continue;
            }
            install_epoll_watch(*entry.second, *watch, true);
            watch->active = false;
            entry.second->identities[size_t(watch->id - 1)].reset();
            found = entry.second->watches.erase(found);
        }
    }
}
int guest_select(int count, void* read, void* write, void* except, void* timeout) {
    if (count < 0 || count > 1024)
        return failure(EINVAL);
    try {
        std::vector<GuestPoll> polls;
        int64_t limit[2]{};
        auto present = [](void* set, int fd) {
            return set && (static_cast<uint8_t*>(set)[fd / 8] & (1u << (fd % 8)));
        };
        for (int fd = 0; fd < count; ++fd) {
            uint16_t bits = (present(read, fd) ? 1 : 0) | (present(write, fd) ? 4 : 0) |
                            (present(except, fd) ? 2 : 0);
            if (bits)
                polls.push_back({fd, bits, 0});
        }
        if (timeout) {
            int64_t fields[2];
            std::memcpy(fields, timeout, 16);
            if (fields[0] < 0 || fields[1] < 0 || fields[1] >= 1000000)
                return failure(EINVAL);
            limit[0] = fields[0];
            limit[1] = fields[1] * 1000;
        }
        timespec before{}, after{};
        ::clock_gettime(CLOCK_UPTIME_RAW, &before);
        int result =
            guest_ppoll(polls.data(), uint32_t(polls.size()), timeout ? limit : nullptr, nullptr);
        int saved = errno;
        if (timeout) {
            ::clock_gettime(CLOCK_UPTIME_RAW, &after);
            int64_t seconds = limit[0] - (after.tv_sec - before.tv_sec),
                    nanos = limit[1] - (after.tv_nsec - before.tv_nsec);
            if (nanos < 0) {
                --seconds;
                nanos += 1000000000;
            }
            int64_t left[2] = {seconds < 0 ? 0 : seconds, seconds < 0 ? 0 : nanos / 1000};
            std::memcpy(timeout, left, 16);
        }
        if (result < 0)
            return failure(saved);
        for (auto p : polls)
            if (p.result & 32)
                return failure(EBADF);
        for (void* set : {read, write, except})
            if (set)
                std::memset(set, 0, 128);
        int ready = 0;
        for (auto p : polls) {
            auto set = [&](void* output, bool value) {
                if (output && value) {
                    static_cast<uint8_t*>(output)[p.fd / 8] |= uint8_t(1u << (p.fd % 8));
                    ++ready;
                }
            };
            set(read, (p.events & 1) && (p.result & (1 | 8 | 16)));
            set(write, (p.events & 4) && (p.result & (4 | 8)));
            set(except, (p.events & 2) && (p.result & 2));
        }
        return ready;
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
} // namespace
int event_epoll_create() {
    return epoll_create_flags(0x80000);
}
int event_epoll_control(int queue, int operation, int fd, const void* event) {
    return epoll_control(queue, operation, fd, event);
}
int event_epoll_wait(int queue, void* events, int capacity, int timeout_ms) {
    return epoll_wait_events(queue, events, capacity, timeout_ms);
}
int event_epoll_remove_data(int descriptor, uint64_t data) {
    if (!same_process() || in_guest_signal_handler())
        return failure(ENOTSUP);
    std::lock_guard<std::mutex> lock(epolls_mutex);
    auto queue = epolls.find(descriptor);
    if (queue == epolls.end())
        return failure(EBADF);
    std::lock_guard<std::mutex> qlock(queue->second->mutex);
    for (auto it = queue->second->watches.begin(); it != queue->second->watches.end(); ++it) {
        auto& watch = it->second;
        if (watch->data != data)
            continue;
        if (install_epoll_watch(*queue->second, *watch, true))
            return -1;
        watch->active = false;
        queue->second->identities[size_t(watch->id - 1)].reset();
        queue->second->watches.erase(it);
        return 1;
    }
    return 0;
}
bool event_descriptor_tracked(int fd) noexcept {
    return hinted(fd);
}
int sysfs_directory_open(unsigned node, int flags, std::shared_ptr<void>* lease) {
    if (node < 1 || node > 3)
        return failure(EINVAL);
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if ((flags & int(b::kO_ACCMODE)) != int(b::kO_RDONLY))
        return failure(EISDIR);
    try {
        auto directory = std::make_shared<SysfsDirectory>(node, flags);
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        int fd = duplicate_locked(table, directory->token.descriptor, directory, 0,
                                  flags & int(b::kO_CLOEXEC));
        if (fd >= 0 && lease)
            *lease = directory;
        return fd;
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
int sysfs_directory_node(int fd) {
    if (!hinted(fd))
        return 0;
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    auto directory = std::dynamic_pointer_cast<SysfsDirectory>(lookup_description(fd));
    return directory ? int(directory->node) : 0;
}
std::shared_ptr<void> sysfs_directory_lease(int fd) {
    if (!hinted(fd)) {
        errno = EBADF;
        return {};
    }
    if (in_guest_signal_handler() || !same_process()) {
        errno = ENOTSUP;
        return {};
    }
    auto directory = std::dynamic_pointer_cast<SysfsDirectory>(lookup_description(fd));
    if (!directory)
        errno = EBADF;
    return directory;
}
int sysfs_directory_close_owned(int fd, const std::shared_ptr<void>& lease) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if (!lease)
        return failure(EBADF);
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    auto found = table.descriptors.find(fd);
    if (found == table.descriptors.end() || found->second.get() != lease.get())
        return failure(EBADF);
    auto description = found->second;
    table.descriptors.erase(found);
    if (!--description->aliases)
        description->close_last();
    int result = ::close(fd);
    set_hint(fd, false);
    return result;
}
ssize_t event_descriptor_read(int fd, void* buffer, size_t count) {
    if (!hinted(fd))
        return ::read(fd, buffer, count);
    if (in_guest_signal_handler())
        return failure<ssize_t>(ENOTSUP);
    if (!same_process())
        return failure<ssize_t>(ENOTSUP);
    auto description = lookup_description(fd);
    if (std::dynamic_pointer_cast<SysfsDirectory>(description))
        return failure<ssize_t>(EISDIR);
    if (auto counter = std::dynamic_pointer_cast<Counter>(description))
        return counter_read(counter, buffer, count);
    if (auto arguments = std::dynamic_pointer_cast<ProcessFile>(description)) {
        if (!buffer && count)
            return failure<ssize_t>(EFAULT);
        size_t amount = 0;
        int error = arguments->stream.read(buffer, count, &amount);
        return error ? failure<ssize_t>(error) : ssize_t(amount);
    }
    return description ? failure<ssize_t>(ENOTSUP) : ::read(fd, buffer, count);
}
ssize_t event_descriptor_write(int fd, const void* buffer, size_t count) {
    if (!hinted(fd))
        return ::write(fd, buffer, count);
    if (in_guest_signal_handler())
        return failure<ssize_t>(ENOTSUP);
    if (!same_process())
        return failure<ssize_t>(ENOTSUP);
    auto description = lookup_description(fd);
    if (auto counter = std::dynamic_pointer_cast<Counter>(description))
        return counter_write(counter, buffer, count);
    return description ? failure<ssize_t>(EBADF) : ::write(fd, buffer, count);
}
int event_descriptor_close(int fd) {
    std::unique_lock<std::mutex> epoll_lock(epolls_mutex, std::defer_lock);
    if (same_process() && !in_guest_signal_handler())
        epoll_lock.lock();
    if (!hinted(fd)) {
        if (epoll_lock.owns_lock())
            epoll_forget_locked(fd);
        return ::close(fd);
    }
    if (in_guest_signal_handler())
        return failure(ENOTSUP);
    // Close is safe in a fork child, but inherited virtual counter operations
    // cannot use a copied process-local registry/mutex/counter as Linux sharing.
    if (!same_process()) {
        int result = ::close(fd);
        set_hint(fd, false);
        return result;
    }
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    auto found = table.descriptors.find(fd);
    if (found != table.descriptors.end()) {
        auto counter = found->second;
        if (counter->aliases == 1)
            epoll_forget_locked(fd, counter.get());
        table.descriptors.erase(found);
        if (!--counter->aliases)
            counter->close_last();
    } else
        epoll_forget_locked(fd);
    int result = ::close(fd);
    set_hint(fd, false);
    return result;
}
int event_descriptor_dup(int fd) {
    if (same_process() && !in_guest_signal_handler() && epoll_lookup(fd))
        return failure(ENOTSUP);
    if (in_guest_signal_handler())
        return hinted(fd) ? failure(ENOTSUP) : ::dup(fd);
    if (!same_process())
        return hinted(fd) ? failure(ENOTSUP) : ::dup(fd);
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    auto found = table.descriptors.find(fd);
    return found == table.descriptors.end() ? ::dup(fd)
                                            : duplicate_locked(table, fd, found->second, 0, false);
}
static int duplicate_to(int source, int destination, bool atomic, int native_flags) {
    auto native = [&] {
        if (!atomic)
            return ::dup2(source, destination);
        if (__builtin_available(macOS 27.0, *))
            return ::dup3(source, destination, native_flags);
        return failure(ENOSYS);
    };
    if (in_guest_signal_handler() || !same_process())
        return hinted(source) || hinted(destination) ? failure(ENOTSUP) : native();
    std::lock_guard<std::mutex> epoll_lock(epolls_mutex);
    if (epolls.count(source) || epolls.count(destination))
        return failure(ENOTSUP);
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    if (source == destination)
        return native();
    auto old = table.descriptors.find(source), target = table.descriptors.find(destination);
    std::shared_ptr<Description> description =
        old == table.descriptors.end() ? nullptr : old->second;
    if (description && destination < 0)
        return failure(EBADF);
    if (description && destination >= event_descriptor_bound)
        return failure(EINVAL);
    std::map<int, std::shared_ptr<Description>> prepared;
    if (description && target == table.descriptors.end()) {
        if (table.descriptors.size() >= descriptor_limit)
            return failure(EMFILE);
        try {
            prepared.emplace(destination, description);
        } catch (const std::bad_alloc&) {
            return failure(ENOMEM);
        }
    }
    // Publish the conservative hint before the kernel replacement. An
    // interrupted signal handler rejects a transition rather than locking.
    bool previous_hint = hinted(destination);
    if (description)
        set_hint(destination, true);
    int result = native();
    if (result < 0) {
        if (description)
            set_hint(destination, previous_hint);
        return result;
    }
    // For tracked virtual descriptors the registry tells us whether the old
    // open description survives through an alias. An existing epoll watch must
    // continue to observe that description while an alias remains. Ordinary
    // host descriptors have no such identity information here, so leave their
    // prior behavior unchanged rather than guessing about hidden aliases.
    if (target != table.descriptors.end() && target->second->aliases == 1)
        epoll_forget_locked(destination, target->second.get());
    if (target != table.descriptors.end()) {
        auto previous = target->second;
        if (!--previous->aliases && previous != description)
            previous->close_last();
        if (description)
            target->second = description;
        else
            table.descriptors.erase(target);
    } else if (description)
        table.descriptors.insert(prepared.extract(destination));
    if (description)
        ++description->aliases;
    if (destination >= 0 && destination < event_descriptor_bound)
        set_hint(destination, bool(description));
    return result;
}
int event_descriptor_dup2(int source, int destination) {
    return duplicate_to(source, destination, false, 0);
}
int event_descriptor_dup3(int source, int destination, int guest_flags) {
    if (guest_flags & ~int(b::kO_CLOEXEC))
        return failure(EINVAL);
    if (source == destination)
        return failure(EINVAL);
    // Avoid invoking OS availability machinery from an async guest handler.
    if (in_guest_signal_handler())
        return failure(ENOTSUP);
    return duplicate_to(source, destination, true, guest_flags & b::kO_CLOEXEC ? O_CLOEXEC : 0);
}
bool events_fcntl(Capture& capture, int64_t& result) {
    CallCursor cursor(capture, Convention::Aapcs64);
    int fd = cursor.read<int>(), command = cursor.read<int>();
    // Serialize ordinary duplication too: an untracked fd can be replaced by a
    // virtual one between an unlocked hint check and the native fcntl call.
    if ((command == b::kF_DUPFD || command == b::kF_DUPFD_CLOEXEC) && !in_guest_signal_handler() &&
        same_process()) {
        int minimum = cursor.read<int>();
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        auto current = table.descriptors.find(fd);
        result =
            current == table.descriptors.end()
                ? ::fcntl(fd, command == b::kF_DUPFD_CLOEXEC ? F_DUPFD_CLOEXEC : F_DUPFD, minimum)
                : duplicate_locked(table, fd, current->second, minimum,
                                   command == b::kF_DUPFD_CLOEXEC);
        return true;
    }
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure(ENOTSUP);
        return true;
    }
    auto description = lookup_description(fd);
    if (!description)
        return false;
    auto counter = std::dynamic_pointer_cast<Counter>(description);
    if (command == b::kF_GETFD) {
        int flags = ::fcntl(fd, F_GETFD);
        result = flags < 0 ? -1 : flags & FD_CLOEXEC ? b::kFD_CLOEXEC : 0;
    } else if (command == b::kF_SETFD) {
        int flags = cursor.read<int>();
        result = flags & ~int(b::kFD_CLOEXEC)
                     ? failure(EINVAL)
                     : ::fcntl(fd, F_SETFD, flags & b::kFD_CLOEXEC ? FD_CLOEXEC : 0);
    } else if (command == b::kF_GETFL) {
        if (counter) {
            std::lock_guard<std::mutex> lock(counter->mutex);
            result = counter->status;
        } else if (auto directory = std::dynamic_pointer_cast<SysfsDirectory>(description)) {
            std::lock_guard<std::mutex> lock(directory->mutex);
            result = directory->status;
        } else {
            if (auto arguments = std::dynamic_pointer_cast<ProcessFile>(description)) {
                std::lock_guard<std::mutex> lock(arguments->mutex);
                result = arguments->status;
            } else
                result = failure(ENOTSUP);
        }
    } else if (command == b::kF_SETFL) {
        int flags = cursor.read<int>();
        int allowed = int(b::kO_ACCMODE | b::kO_NONBLOCK | b::kO_APPEND | b::kO_LARGEFILE);
        if (std::dynamic_pointer_cast<SysfsDirectory>(description))
            allowed |= int(b::kO_DIRECTORY | b::kO_NOFOLLOW);
        if (flags & ~allowed)
            result = failure(ENOTSUP);
        else if (counter) {
            std::lock_guard<std::mutex> lock(counter->mutex);
            counter->status = int(b::kO_RDWR) | (flags & int(b::kO_NONBLOCK | b::kO_APPEND));
            result = 0;
        } else if (auto directory = std::dynamic_pointer_cast<SysfsDirectory>(description)) {
            std::lock_guard<std::mutex> lock(directory->mutex);
            directory->status = (directory->status & ~int(b::kO_NONBLOCK | b::kO_APPEND)) |
                                (flags & int(b::kO_NONBLOCK | b::kO_APPEND));
            result = 0;
        } else if (auto arguments = std::dynamic_pointer_cast<ProcessFile>(description)) {
            std::lock_guard<std::mutex> lock(arguments->mutex);
            arguments->status =
                int(b::kO_RDONLY | b::kO_LARGEFILE) | (flags & int(b::kO_NONBLOCK | b::kO_APPEND));
            result = 0;
        } else
            result = failure(ENOTSUP);
    } else
        result = failure(ENOTSUP);
    return true;
}
bool events_ioctl(Capture& capture, int& result) {
    CallCursor cursor(capture, Convention::Aapcs64);
    int fd = cursor.read<int>();
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure(ENOTSUP);
        return true;
    }
    auto description = lookup_description(fd);
    if (!description)
        return false;
    auto counter = std::dynamic_pointer_cast<Counter>(description);
    if (!counter) {
        result = failure(ENOTTY);
        return true;
    }
    uint32_t request = cursor.read<uint32_t>();
    if (request == b::kFIOCLEX || request == b::kFIONCLEX) {
        int flags = ::fcntl(fd, F_GETFD);
        result = flags < 0
                     ? -1
                     : ::fcntl(fd, F_SETFD,
                               request == b::kFIOCLEX ? flags | FD_CLOEXEC : flags & ~FD_CLOEXEC);
    } else if (request == b::kFIONBIO) {
        const void* argument = cursor.read<const void*>();
        if (!argument)
            result = failure(EFAULT);
        else {
            int32_t value;
            std::memcpy(&value, argument, 4);
            std::lock_guard<std::mutex> lock(counter->mutex);
            counter->status = value ? counter->status | int(b::kO_NONBLOCK)
                                    : counter->status & ~int(b::kO_NONBLOCK);
            result = 0;
        }
    } else
        result = failure(ENOTTY);
    return true;
}
void event_descriptors_shutdown() {
    if (!same_process())
        return;
    auto& table = registry();
    std::lock_guard<std::mutex> lock(table.mutex);
    for (auto& entry : table.descriptors) {
        ::close(entry.first);
        if (!--entry.second->aliases)
            entry.second->close_last();
        set_hint(entry.first, false);
    }
    table.descriptors.clear();
}
bool process_file_reject(int fd, int error, int& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure(ENOTSUP);
        return true;
    }
    if (!std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd)))
        return false;
    result = failure(error);
    return true;
}
int process_maps_open(MapsStream::Factory factory, int flags) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    if ((flags & O_ACCMODE) != O_RDONLY || (flags & ~(O_CLOEXEC | O_NONBLOCK)))
        return failure(EINVAL);
    try {
        auto description = std::make_shared<ProcessFile>(flags);
        description->stream.maps = std::make_unique<MapsStream>(std::move(factory));
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        return duplicate_locked(table, description->token.descriptor, description, 0,
                                flags & O_CLOEXEC);
    } catch (const Failure& e) {
        return failure(e.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
bool process_file_stat(int fd, struct stat& out, int& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure(ENOTSUP);
        return true;
    }
    auto description = std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd));
    if (!description)
        return false;
    if (!description->stream.maps) {
        result = failure(ENOTSUP);
        return true;
    }
    struct stat identity{};
    if (::fstat(description->token.descriptor, &identity)) {
        result = -1;
        return true;
    }
    out = {};
    out.st_dev = identity.st_dev;
    out.st_ino = identity.st_ino;
    out.st_mode = S_IFREG | 0444;
    out.st_nlink = 1;
    out.st_uid = getuid();
    out.st_gid = getgid();
    out.st_blksize = getpagesize();
    result = 0;
    return true;
}
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
int probe_process_arguments_open(int native_flags) {
    if (in_guest_signal_handler() || !same_process())
        return failure(ENOTSUP);
    try {
        auto description = std::make_shared<ProcessFile>(native_flags);
        auto& table = registry();
        std::lock_guard<std::mutex> lock(table.mutex);
        return duplicate_locked(table, description->token.descriptor, description, 0,
                                native_flags & O_CLOEXEC);
    } catch (const Failure& error) {
        return failure(error.error);
    } catch (const std::bad_alloc&) {
        return failure(ENOMEM);
    }
}
#endif
bool process_file_pread(int fd, void* buffer, size_t count, int64_t offset, ssize_t& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure<ssize_t>(ENOTSUP);
        return true;
    }
    auto arguments = std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd));
    if (!arguments)
        return false;
    if (!buffer && count) {
        result = failure<ssize_t>(EFAULT);
        return true;
    }
    size_t amount = 0;
    int error = arguments->stream.pread(offset, buffer, count, &amount);
    result = error ? failure<ssize_t>(error) : ssize_t(amount);
    return true;
}
bool process_file_pwrite(int fd, ssize_t& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure<ssize_t>(ENOTSUP);
        return true;
    }
    if (!std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd)))
        return false;
    result = failure<ssize_t>(EBADF);
    return true;
}
bool process_file_seek(int fd, int64_t offset, int origin, int64_t& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure<int64_t>(ENOTSUP);
        return true;
    }
    auto arguments = std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd));
    if (!arguments)
        return false;
    int64_t value = 0;
    int error = arguments->stream.seek(offset, origin, &value);
    result = error ? failure<int64_t>(error) : value;
    return true;
}
#ifdef SHIRAZ_OUTER_SANDBOX_PROBE
bool probe_process_arguments_fstat(int fd, int& result) {
    if (!hinted(fd))
        return false;
    if (in_guest_signal_handler() || !same_process()) {
        result = failure(ENOTSUP);
        return true;
    }
    if (!std::dynamic_pointer_cast<ProcessFile>(lookup_description(fd)))
        return false;
    result = failure(ENOTSUP);
    return true;
}
#endif
std::vector<ProviderExport> event_exports() {
    (void)registry(); // Initialize VM ownership before any guest callback runs.
    return {
        fixed_export("dup3", "LIBC", &event_descriptor_dup3, ErrnoPolicy::MinusOne),
        fixed_export("eventfd", "LIBC", &create_counter, ErrnoPolicy::MinusOne),
        fixed_export("timerfd_create", "LIBC", &timer_create, ErrnoPolicy::MinusOne),
        fixed_export("timerfd_settime", "LIBC", &timer_settime, ErrnoPolicy::MinusOne),
        fixed_export("timerfd_gettime", "LIBC", &timer_gettime, ErrnoPolicy::MinusOne),
        fixed_export("eventfd_read", "LIBC", &convenience_read),
        fixed_export("eventfd_write", "LIBC", &convenience_write),
        fixed_export("poll", "LIBC", &guest_poll, ErrnoPolicy::MinusOne),
        fixed_export("ppoll", "LIBC", &guest_ppoll, ErrnoPolicy::MinusOne),
        fixed_export("select", "LIBC", &guest_select, ErrnoPolicy::MinusOne),
        fixed_export("epoll_create", "LIBC", &epoll_create_legacy, ErrnoPolicy::MinusOne),
        fixed_export("epoll_create1", "LIBC", &epoll_create_flags, ErrnoPolicy::MinusOne),
        fixed_export("epoll_ctl", "LIBC", &epoll_control, ErrnoPolicy::MinusOne),
        fixed_export("epoll_wait", "LIBC", &epoll_wait_events, ErrnoPolicy::MinusOne),
        fixed_export("inotify_init", "LIBC", &unsupported_inotify, ErrnoPolicy::MinusOne),
        fixed_export("inotify_init1", "LIBC", &unsupported_inotify_flags, ErrnoPolicy::MinusOne),
        fixed_export("inotify_add_watch", "LIBC", &unsupported_inotify_add, ErrnoPolicy::MinusOne),
        fixed_export("inotify_rm_watch", "LIBC", &unsupported_inotify_remove,
                     ErrnoPolicy::MinusOne)};
}
} // namespace shiraz::guest
