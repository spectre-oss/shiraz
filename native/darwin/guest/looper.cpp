// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/looper.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include <atomic>
#include <cerrno>
#include <chrono>
#include <climits>
#include <condition_variable>
#include <cstdlib>
#include <fcntl.h>
#include <map>
#include <mutex>
#include <thread>
#include <unistd.h>

namespace shiraz::guest {
namespace {
struct Event {
    uint32_t events, padding;
    uint64_t data;
};
static_assert(sizeof(Event) == 16 && offsetof(Event, data) == 8);
struct Watch {
    int fd, ident, events;
    uintptr_t callback, data;
    uint64_t id;
    std::shared_ptr<void> retention;
    std::shared_ptr<AdmissionDomain> admission;
    bool namespace_owned;
};
struct Loop {
    int queue = -1, read_end = -1, write_end = -1, options = 0;
    uintptr_t token = 0;
    std::mutex mutex;
    std::map<int, Watch> watches;
    uint64_t next = 1;
    size_t active_polls = 0;
    bool stopping = false;
    void close_locked() noexcept {
        int saved = errno;
        if (read_end >= 0)
            event_descriptor_close(read_end);
        if (write_end >= 0)
            event_descriptor_close(write_end);
        if (queue >= 0)
            event_descriptor_close(queue);
        read_end = write_end = queue = -1;
        errno = saved;
    }
    ~Loop() {
        close_locked();
    }
    void wake_locked() const {
        if (write_end < 0)
            return;
        const unsigned char byte = 1;
        ssize_t result;
        do {
            result = ::write(write_end, &byte, 1);
        } while (result < 0 && errno == EINTR);
    }
    void wake() {
        std::lock_guard<std::mutex> lock(mutex);
        if (!stopping)
            wake_locked();
    }
};
struct State {
    struct Retained {
        std::shared_ptr<Loop> loop;
        unsigned count;
    };
    std::mutex mutex;
    std::map<uintptr_t, std::weak_ptr<Loop>> loops;
    std::map<uintptr_t, Retained> retained;
    std::function<bool(const void*)> validate;
    std::shared_ptr<Lifecycle> lifecycle;
    bool closing = false;
    size_t active_adds = 0;
    std::map<std::thread::id, size_t> adding;
    std::condition_variable adds_drained;
    uint64_t id;
};
std::atomic<uint64_t> next_state{1}, next_loop{0x10001};
uint64_t reserve_id(std::atomic<uint64_t>& next, uint64_t increment) {
    auto value = next.load(std::memory_order_relaxed);
    while (value && value <= uint64_t(INTPTR_MAX) - increment) {
        if (next.compare_exchange_weak(value, value + increment, std::memory_order_relaxed))
            return value;
    }
    return 0;
}
thread_local std::map<uint64_t, std::shared_ptr<Loop>> current;
std::shared_ptr<Loop> lookup(const std::shared_ptr<State>& state, uintptr_t token) {
    std::lock_guard<std::mutex> lock(state->mutex);
    if (state->closing)
        return {};
    auto found = state->loops.find(token);
    if (found == state->loops.end())
        return {};
    auto loop = found->second.lock();
    if (!loop)
        state->loops.erase(found);
    return loop;
}
std::shared_ptr<Loop> prepare(const std::shared_ptr<State>& state, int options) {
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->closing)
            return {};
    }
    auto found = current.find(state->id);
    if (found != current.end())
        return found->second;
    if (options & ~1)
        return {};
    auto loop = std::make_shared<Loop>();
    loop->options = options;
    loop->queue = event_epoll_create();
    if (loop->queue < 0)
        return {};
    int pipes[2];
    if (::pipe(pipes))
        return {};
    loop->read_end = pipes[0];
    loop->write_end = pipes[1];
    for (int fd : pipes)
        if (::fcntl(fd, F_SETFD, FD_CLOEXEC) || ::fcntl(fd, F_SETFL, O_NONBLOCK))
            return {};
    Event wake{1, 0, 0};
    if (event_epoll_control(loop->queue, 1, loop->read_end, &wake))
        return {};
    auto token = reserve_id(next_loop, 16);
    if (!token)
        return {};
    loop->token = uintptr_t(token);
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->closing)
            return {};
        for (auto it = state->loops.begin(); it != state->loops.end();)
            if (it->second.expired())
                it = state->loops.erase(it);
            else
                ++it;
        if (state->loops.size() >= 4096)
            return {};
        state->loops.emplace(loop->token, loop);
    }
    current.emplace(state->id, loop);
    return loop;
}
int add(const std::shared_ptr<State>& state, const std::shared_ptr<Loop>& loop, int fd, int ident,
        int events, uintptr_t callback, uintptr_t data) {
    if (!loop || fd < 0 || !(events & 3) || (events & ~3) ||
        (!callback && (!(loop->options & 1) || ident < 0)))
        return -1;
    struct AddGuard {
        std::shared_ptr<State> state;
        std::thread::id thread;
        ~AddGuard() {
            std::lock_guard<std::mutex> lock(state->mutex);
            auto found = state->adding.find(thread);
            if (found == state->adding.end() || !found->second || !state->active_adds)
                std::abort();
            if (!--found->second)
                state->adding.erase(found);
            if (!--state->active_adds)
                state->adds_drained.notify_all();
        }
    };
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->closing || state->active_adds >= 4096)
            return -1;
        ++state->adding[std::this_thread::get_id()];
        ++state->active_adds;
    }
    AddGuard adding{state, std::this_thread::get_id()};
    std::shared_ptr<void> retention;
    std::shared_ptr<AdmissionDomain> admission;
    if (callback) {
        if (state->lifecycle) {
            retention = state->lifecycle->retain_thread_callback();
            if (!retention)
                return -1;
        }
        if (!state->validate || !state->validate(reinterpret_cast<void*>(callback)))
            return -1;
        if (state->lifecycle)
            admission = state->lifecycle->callback_admission();
    }
    // Allocate before taking the loop lock so failed allocation or teardown
    // never drops a lifecycle token while holding that lock.
    std::map<int, Watch> prepared;
    prepared.emplace(fd, Watch{fd, ident, events, callback, data, 0, std::move(retention),
                               std::move(admission), bool(state->lifecycle)});
    Watch retired{};
    std::unique_lock<std::mutex> state_lock(state->mutex);
    if (state->closing)
        return -1;
    std::unique_lock<std::mutex> lock(loop->mutex);
    auto old = loop->watches.find(fd);
    if (loop->next == UINT64_MAX || (old == loop->watches.end() && loop->watches.size() >= 4096))
        return -1;
    uint64_t id = loop->next++;
    Event event{uint32_t((events & 1 ? 1 : 0) | (events & 2 ? 4 : 0)), 0, id};
    prepared.begin()->second.id = id;
    int operation = old == loop->watches.end() ? 1 : 3;
    if (event_epoll_control(loop->queue, operation, fd, &event)) {
        if (operation != 3 || errno != ENOENT || event_epoll_control(loop->queue, 1, fd, &event)) {
            lock.unlock();
            state_lock.unlock();
            return -1;
        }
        // The number now refers to a different open description. Retire the old
        // registration by its unique payload, not by the recycled number.
        if (event_epoll_remove_data(loop->queue, old->second.id) < 0) {
            (void)event_epoll_remove_data(loop->queue, id);
            lock.unlock();
            state_lock.unlock();
            return -1;
        }
    }
    if (old == loop->watches.end())
        loop->watches.insert(prepared.extract(fd));
    else {
        retired = std::move(old->second);
        old->second = std::move(prepared.begin()->second);
    }
    loop->wake_locked();
    lock.unlock();
    state_lock.unlock();
    return 1;
}
int remove(const std::shared_ptr<Loop>& loop, int fd, uint64_t expected = 0) {
    if (!loop)
        return -1;
    Watch retired{};
    std::unique_lock<std::mutex> lock(loop->mutex);
    auto found = loop->watches.find(fd);
    if (found == loop->watches.end() || (expected && found->second.id != expected))
        return 0;
    if (event_epoll_remove_data(loop->queue, found->second.id) < 0)
        return -1;
    retired = std::move(found->second);
    loop->watches.erase(found);
    loop->wake_locked();
    lock.unlock();
    return 1;
}
int poll_once(const std::shared_ptr<State>& state, int timeout, int* out_fd, int* out_events,
              void** out_data) {
    if (out_fd)
        *out_fd = -1;
    if (out_events)
        *out_events = 0;
    if (out_data)
        *out_data = nullptr;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->closing)
            return -4;
    }
    auto found = current.find(state->id);
    if (found == current.end())
        return -4;
    auto loop = found->second;
    {
        std::lock_guard<std::mutex> lock(loop->mutex);
        if (loop->stopping || loop->active_polls == SIZE_MAX)
            return -4;
        ++loop->active_polls;
    }
    struct PollGuard {
        std::shared_ptr<Loop> loop;
        ~PollGuard() {
            std::lock_guard<std::mutex> lock(loop->mutex);
            if (!loop->active_polls)
                std::abort();
            if (!--loop->active_polls && loop->stopping)
                loop->close_locked();
        }
    } poll_guard{loop};
    Event ready[16]{};
    int count = event_epoll_wait(loop->queue, ready, 16, timeout);
    int wait_error = errno;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->closing)
            return -4;
    }
    if (count < 0)
        return wait_error == EINTR ? -1 : -4;
    if (!count)
        return -3;
    for (int i = 0; i < count; ++i)
        if (!ready[i].data) {
            // A producer can keep waking concurrently; never let draining the pipe
            // starve delivery of the other ready descriptors indefinitely.
            unsigned char bytes[128];
            for (unsigned n = 0; n < 32; ++n)
                if (::read(loop->read_end, bytes, sizeof(bytes)) <= 0)
                    break;
        }
    for (int i = 0; i < count; ++i) {
        if (!ready[i].data)
            continue;
        Watch watch{};
        bool present = false;
        {
            std::lock_guard<std::mutex> lock(loop->mutex);
            for (const auto& entry : loop->watches)
                if (entry.second.id == ready[i].data) {
                    watch = entry.second;
                    present = true;
                    break;
                }
        }
        if (!present)
            continue;
        int events = (ready[i].events & 1 ? 1 : 0) | (ready[i].events & 4 ? 2 : 0) |
                     (ready[i].events & 8 ? 4 : 0) | (ready[i].events & 16 ? 8 : 0);
        if (!watch.callback) {
            if (out_fd)
                *out_fd = watch.fd;
            if (out_events)
                *out_events = events;
            if (out_data)
                *out_data = reinterpret_cast<void*>(watch.data);
            return watch.ident;
        }
        if (!state->validate || !state->validate(reinterpret_cast<void*>(watch.callback)))
            return -4;
        std::vector<Argument> arguments{Argument::integer(uint32_t(watch.fd)),
                                        Argument::integer(uint32_t(events)),
                                        Argument::integer(watch.data, ArgumentType::Int64)};
        auto domain =
            watch.namespace_owned ? watch.admission : AdmissionDomain::current_invocation();
        auto result =
            domain ? invoke_admitted(domain, reinterpret_cast<void*>(watch.callback), arguments)
                   : invoke(reinterpret_cast<void*>(watch.callback), arguments);
        if (!int32_t(result.result_integers[0]))
            (void)remove(loop, watch.fd, watch.id);
        return -2;
    }
    return -1;
}
} // namespace
#ifdef SHIRAZ_LOOPER_TEST_HOOKS
extern "C" bool shiraz_looper_test_current_fds(int* output) {
    if (!output || current.size() != 1)
        return false;
    auto loop = current.begin()->second;
    std::lock_guard<std::mutex> lock(loop->mutex);
    if (loop->queue < 0 || loop->read_end < 0 || loop->write_end < 0)
        return false;
    output[0] = loop->queue;
    output[1] = loop->read_end;
    output[2] = loop->write_end;
    return true;
}
#endif
std::vector<ProviderExport> looper_exports(std::function<bool(const void*)> validate,
                                           std::shared_ptr<Lifecycle> lifecycle,
                                           std::function<void()>* shutdown) {
    if (!validate)
        throw ElfError("looper requires guest code validation");
    auto state = std::make_shared<State>();
    state->validate = std::move(validate);
    state->lifecycle = std::move(lifecycle);
    state->id = reserve_id(next_state, 1);
    if (!state->id)
        throw ElfError("looper state identity exhausted");
    if (shutdown)
        *shutdown = [state] {
            std::vector<std::shared_ptr<Loop>> loops;
            {
                std::unique_lock<std::mutex> lock(state->mutex);
                if (state->adding.count(std::this_thread::get_id()))
                    throw ElfError("cannot stop a looper during watch registration");
                state->closing = true;
                // Wake already blocked pollers even if a registration later times out.
                for (const auto& entry : state->loops)
                    if (auto loop = entry.second.lock())
                        loop->wake();
                if (!state->adds_drained.wait_for(lock, std::chrono::seconds(5),
                                                  [&] { return !state->active_adds; }))
                    throw ElfError("timed out waiting for guest looper watch registration");
                loops.reserve(state->loops.size());
                for (const auto& entry : state->loops)
                    if (auto loop = entry.second.lock())
                        loops.push_back(std::move(loop));
            }
            for (const auto& loop : loops) {
                std::map<int, Watch> retired;
                {
                    std::lock_guard<std::mutex> lock(loop->mutex);
                    for (const auto& entry : loop->watches)
                        if (event_epoll_remove_data(loop->queue, entry.second.id) < 0)
                            throw ElfError("cannot retire guest looper watch");
                    retired.swap(loop->watches);
                    loop->stopping = true;
                    loop->wake_locked();
                    if (!loop->active_polls)
                        loop->close_locked();
                }
            }
            // Keep local loop references until after dropping the state lock.
            {
                std::lock_guard<std::mutex> lock(state->mutex);
                state->retained.clear();
                state->loops.clear();
            }
        };
    const char* names[] = {"ALooper_prepare",  "ALooper_forThread", "ALooper_acquire",
                           "ALooper_release",  "ALooper_addFd",     "ALooper_removeFd",
                           "ALooper_pollOnce", "ALooper_wake"};
    std::vector<ProviderExport> result;
    for (unsigned operation = 0; operation < 8; ++operation) {
        void* entry = closure([state, operation](Capture& capture) {
            CallCursor cursor(capture, Convention::Aapcs64);
            HostErrno preserve;
            int64_t value = 0;
            try {
                if (operation == 0) {
                    auto loop = prepare(state, cursor.read<int>());
                    value = loop ? int64_t(loop->token) : 0;
                } else if (operation == 1) {
                    std::lock_guard<std::mutex> lock(state->mutex);
                    auto found = current.find(state->id);
                    value = state->closing || found == current.end()
                                ? 0
                                : int64_t(found->second->token);
                } else if (operation == 2 || operation == 3) {
                    auto token = cursor.read<uintptr_t>();
                    auto loop = lookup(state, token);
                    if (loop) {
                        std::lock_guard<std::mutex> lock(state->mutex);
                        auto found = state->retained.find(token);
                        if (state->closing) {
                            return_value(capture, value);
                            return;
                        }
                        if (operation == 2) {
                            if (found == state->retained.end())
                                state->retained.emplace(token, State::Retained{loop, 1});
                            else if (found->second.count != UINT_MAX)
                                ++found->second.count;
                            else
                                std::abort();
                        } else if (found != state->retained.end() && !--found->second.count)
                            state->retained.erase(found);
                    }
                } else if (operation == 4) {
                    auto loop = lookup(state, cursor.read<uintptr_t>());
                    int fd = cursor.read<int>();
                    int ident = cursor.read<int>();
                    int events = cursor.read<int>();
                    auto callback = cursor.read<uintptr_t>();
                    auto data = cursor.read<uintptr_t>();
                    value = add(state, loop, fd, ident, events, callback, data);
                } else if (operation == 5) {
                    auto loop = lookup(state, cursor.read<uintptr_t>());
                    value = remove(loop, cursor.read<int>());
                } else if (operation == 6) {
                    int timeout = cursor.read<int>();
                    auto* fd = cursor.read<int*>();
                    auto* events = cursor.read<int*>();
                    auto** data = cursor.read<void**>();
                    value = poll_once(state, timeout, fd, events, data);
                } else {
                    auto loop = lookup(state, cursor.read<uintptr_t>());
                    if (loop)
                        loop->wake();
                }
            } catch (...) {
                value = operation == 6 ? -4 : operation == 4 || operation == 5 ? -1 : 0;
            }
            return_value(capture, value);
        });
        result.push_back({names[operation], "", reinterpret_cast<uintptr_t>(entry),
                          SymbolKind::Function, "looper/guest-descriptors-validated-callback",
                          false});
    }
    return result;
}
} // namespace shiraz::guest
