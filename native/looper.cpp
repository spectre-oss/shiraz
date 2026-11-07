// SPDX-License-Identifier: GPL-3.0-only
#if defined(__APPLE__)
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>
#include <fcntl.h>
#else
#include <sys/epoll.h>
#include <sys/eventfd.h>
#endif
#include <unistd.h>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <mutex>
#include <new>
#include <unordered_map>

#if defined(__APPLE__)
// kqueue backend: EVFILT_READ/EVFILT_WRITE knotes per fd (level-triggered like
// epoll) and one EVFILT_USER knote (ident 0, EV_CLEAR) as the wake source.
struct ALooper {
    using Callback = int (*)(int, int, void*);
    struct Watch { int ident, events; Callback callback; void* data; };
    int poll_fd = -1, options = 0, last_fd = -1;
    bool wake_ready = false;
    std::atomic<unsigned> refs{1};
    std::mutex mutex;
    std::unordered_map<int, Watch> watches;
    explicit ALooper(int opts) : options(opts) {
        poll_fd = kqueue();
        if (poll_fd < 0) return;
        if (fcntl(poll_fd, F_SETFD, FD_CLOEXEC)) return;
        struct kevent change;
        EV_SET(&change, 0, EVFILT_USER, EV_ADD | EV_CLEAR, 0, 0, nullptr);
        wake_ready = !kevent(poll_fd, &change, 1, nullptr, 0, nullptr);
    }
    ~ALooper() { if (poll_fd >= 0) close(poll_fd); }
};
#else
struct ALooper {
    using Callback = int (*)(int, int, void*);
    struct Watch { int ident, events; Callback callback; void* data; };
    int poll_fd = -1, wake_fd = -1, options = 0;
    std::atomic<unsigned> refs{1};
    std::mutex mutex;
    std::unordered_map<int, Watch> watches;
    explicit ALooper(int opts) : options(opts) {
        poll_fd = epoll_create1(EPOLL_CLOEXEC);
        if (poll_fd < 0) return;
        wake_fd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
        if (wake_fd < 0) return;
        epoll_event event{};
        event.events = EPOLLIN;
        event.data.fd = wake_fd;
        if (epoll_ctl(poll_fd, EPOLL_CTL_ADD, wake_fd, &event)) {
            close(wake_fd); wake_fd = -1;
        }
    }
    ~ALooper() { if (wake_fd >= 0) close(wake_fd); if (poll_fd >= 0) close(poll_fd); }
};
#endif

extern "C" void ALooper_release(ALooper* looper);
namespace {
struct ThreadLooper {
    ALooper* value = nullptr;
    ~ThreadLooper() { ALooper_release(value); }
};
thread_local ThreadLooper current;
constexpr int event_input = 1, event_output = 2, event_error = 4, event_hangup = 8;
constexpr int poll_wake = -1, poll_callback = -2, poll_timeout = -3, poll_error = -4;
#if defined(__APPLE__)
// Moves fd from the filter set `from` to `to` (event_input/event_output bits),
// like EPOLL_CTL_ADD/MOD/DEL. A failed add is rolled back to `from`.
bool kqueue_apply(int queue, int fd, int from, int to, bool rollback = true) {
    struct kevent changes[2], results[2];
    const short filters[2] = {EVFILT_READ, EVFILT_WRITE};
    const int masks[2] = {event_input, event_output};
    int count = 0;
    for (int i = 0; i < 2; i++) {
        if (to & masks[i]) EV_SET(&changes[count++], fd, filters[i], EV_ADD | EV_RECEIPT, 0, 0, nullptr);
        else if (from & masks[i]) EV_SET(&changes[count++], fd, filters[i], EV_DELETE | EV_RECEIPT, 0, 0, nullptr);
    }
    if (!count) return true;
    const int done = kevent(queue, changes, count, results, count, nullptr);
    if (done < 0) return false;
    int failure = 0;
    for (int i = 0; i < done; i++) {
        if (!(results[i].flags & EV_ERROR) || !results[i].data) continue;
        const bool removing = results[i].flags & EV_DELETE;
        if (removing && (results[i].data == ENOENT || results[i].data == EBADF)) continue;
        failure = static_cast<int>(results[i].data);
    }
    if (!failure) return true;
    if (rollback) kqueue_apply(queue, fd, to, from, false);
    errno = failure;
    return false;
}
int kqueue_flags(const struct kevent& event) {
    if (event.flags & EV_ERROR) return event_error;
    int flags = 0;
    if (event.filter == EVFILT_READ && (!(event.flags & EV_EOF) || event.data > 0)) flags |= event_input;
    if (event.filter == EVFILT_WRITE) flags |= event_output;
    if (event.flags & EV_EOF) flags |= event.fflags ? event_hangup | event_error : event_hangup;
    return flags;
}
#endif
}

extern "C" ALooper* ALooper_forThread() { return current.value; }
extern "C" ALooper* ALooper_prepare(int options) {
    if (current.value) return current.value;
    ALooper* looper = new (std::nothrow) ALooper(options);
    if (!looper) return nullptr;
#if defined(__APPLE__)
    if (!looper->wake_ready) { delete looper; return nullptr; }
#else
    if (looper->wake_fd < 0) { delete looper; return nullptr; }
#endif
    current.value = looper;
    return looper;
}
extern "C" void ALooper_acquire(ALooper* looper) {
    if (looper) looper->refs.fetch_add(1, std::memory_order_relaxed);
}
extern "C" void ALooper_release(ALooper* looper) {
    if (looper && looper->refs.fetch_sub(1, std::memory_order_acq_rel) == 1) delete looper;
}
extern "C" void ALooper_wake(ALooper* looper) {
#if defined(__APPLE__)
    if (!looper || !looper->wake_ready) return;
    struct kevent change;
    EV_SET(&change, 0, EVFILT_USER, 0, NOTE_TRIGGER, 0, nullptr);
    kevent(looper->poll_fd, &change, 1, nullptr, 0, nullptr);
#else
    if (!looper || looper->wake_fd < 0) return;
    uint64_t count = 1;
    [[maybe_unused]] const auto written = write(looper->wake_fd, &count, sizeof(count));
#endif
}
extern "C" int ALooper_addFd(ALooper* looper, int fd, int ident, int events,
                               ALooper::Callback callback, void* data) {
#if defined(__APPLE__)
    if (!looper || fd < 0 || fd == looper->poll_fd || !(events & (event_input | event_output)) ||
        (events & ~(event_input | event_output)) || (!callback && !(looper->options & 1))) return -1;
    std::lock_guard<std::mutex> guard(looper->mutex);
    const auto found = looper->watches.find(fd);
    const int previous = found == looper->watches.end() ? 0 : found->second.events;
    if (!kqueue_apply(looper->poll_fd, fd, previous, events)) return -1;
    try { looper->watches[fd] = {ident, events, callback, data}; }
    catch (const std::bad_alloc&) { kqueue_apply(looper->poll_fd, fd, events, 0); return -1; }
    ALooper_wake(looper);
    return 1;
#else
    if (!looper || fd < 0 || fd == looper->wake_fd || !(events & (event_input | event_output)) ||
        (events & ~(event_input | event_output)) || (!callback && !(looper->options & 1))) return -1;
    epoll_event event{};
    if (events & event_input) event.events |= EPOLLIN;
    if (events & event_output) event.events |= EPOLLOUT;
    event.data.fd = fd;
    std::lock_guard<std::mutex> guard(looper->mutex);
    const int operation = looper->watches.count(fd) ? EPOLL_CTL_MOD : EPOLL_CTL_ADD;
    if (epoll_ctl(looper->poll_fd, operation, fd, &event)) return -1;
    try { looper->watches[fd] = {ident, events, callback, data}; }
    catch (const std::bad_alloc&) { epoll_ctl(looper->poll_fd, EPOLL_CTL_DEL, fd, nullptr); return -1; }
    ALooper_wake(looper);
    return 1;
#endif
}
extern "C" int ALooper_removeFd(ALooper* looper, int fd) {
    if (!looper) return -1;
    std::lock_guard<std::mutex> guard(looper->mutex);
    if (!looper->watches.erase(fd)) return 0;
#if defined(__APPLE__)
    kqueue_apply(looper->poll_fd, fd, event_input | event_output, 0, false);
#else
    epoll_ctl(looper->poll_fd, EPOLL_CTL_DEL, fd, nullptr);
#endif
    ALooper_wake(looper);
    return 1;
}
extern "C" int ALooper_pollOnce(int timeout_ms, int* out_fd, int* out_events, void** out_data) {
    if (out_fd) *out_fd = -1;
    if (out_events) *out_events = 0;
    if (out_data) *out_data = nullptr;
    ALooper* looper = current.value;
    if (!looper) return poll_error;
#if defined(__APPLE__)
    // Fetch a batch so a fd ready for both input and output reports one combined
    // callback like epoll. Fd knotes are level-triggered: the ones not consumed
    // here stay queued. A wake in the batch is consumed (EV_CLEAR) and returned.
    // Ready fds are served round-robin (next fd above the last one served), as
    // epoll's ready-list rotation does; kqueue keeps batch order stable.
    struct kevent events[16];
    timespec timeout{}, *limit = nullptr;
    if (timeout_ms >= 0) { timeout.tv_sec = timeout_ms / 1000; timeout.tv_nsec = (timeout_ms % 1000) * 1000000L; limit = &timeout; }
    int ready;
    do { ready = kevent(looper->poll_fd, nullptr, 0, events, 16, limit); }
    while (ready < 0 && errno == EINTR);
    if (ready == 0) return poll_timeout;
    if (ready < 0) return poll_error;
    for (int i = 0; i < ready; i++) if (events[i].filter == EVFILT_USER) return poll_wake;
    int fd = -1, lowest = -1;
    for (int i = 0; i < ready; i++) {
        const int candidate = static_cast<int>(events[i].ident);
        if (lowest < 0 || candidate < lowest) lowest = candidate;
        if (candidate > looper->last_fd && (fd < 0 || candidate < fd)) fd = candidate;
    }
    if (fd < 0) fd = lowest;
    looper->last_fd = fd;
    int flags = 0;
    for (int i = 0; i < ready; i++) if (static_cast<int>(events[i].ident) == fd) flags |= kqueue_flags(events[i]);
    ALooper::Watch watch{};
    {
        std::lock_guard<std::mutex> guard(looper->mutex);
        auto found = looper->watches.find(fd);
        if (found == looper->watches.end()) return poll_wake;
        watch = found->second;
    }
    if (watch.callback) {
        if (!watch.callback(fd, flags, watch.data)) ALooper_removeFd(looper, fd);
        return poll_callback;
    }
    if (out_fd) *out_fd = fd;
    if (out_events) *out_events = flags;
    if (out_data) *out_data = watch.data;
    return watch.ident;
#else
    epoll_event event{};
    int ready;
    do { ready = epoll_wait(looper->poll_fd, &event, 1, timeout_ms); }
    while (ready < 0 && errno == EINTR);
    if (ready == 0) return poll_timeout;
    if (ready < 0) return poll_error;
    if (event.data.fd == looper->wake_fd) {
        uint64_t count;
        [[maybe_unused]] const auto bytes = read(looper->wake_fd, &count, sizeof(count));
        return poll_wake;
    }
    ALooper::Watch watch{};
    {
        std::lock_guard<std::mutex> guard(looper->mutex);
        auto found = looper->watches.find(event.data.fd);
        if (found == looper->watches.end()) return poll_wake;
        watch = found->second;
    }
    int flags = ((event.events & EPOLLIN) ? event_input : 0) |
                ((event.events & EPOLLOUT) ? event_output : 0) |
                ((event.events & EPOLLERR) ? event_error : 0) |
                ((event.events & EPOLLHUP) ? event_hangup : 0);
    if (watch.callback) {
        if (!watch.callback(event.data.fd, flags, watch.data)) ALooper_removeFd(looper, event.data.fd);
        return poll_callback;
    }
    if (out_fd) *out_fd = event.data.fd;
    if (out_events) *out_events = flags;
    if (out_data) *out_data = watch.data;
    return watch.ident;
#endif
}
