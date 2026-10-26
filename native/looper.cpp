// SPDX-License-Identifier: GPL-3.0-only
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <unistd.h>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <mutex>
#include <new>
#include <unordered_map>

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

extern "C" void ALooper_release(ALooper* looper);
namespace {
struct ThreadLooper {
    ALooper* value = nullptr;
    ~ThreadLooper() { ALooper_release(value); }
};
thread_local ThreadLooper current;
constexpr int event_input = 1, event_output = 2, event_error = 4, event_hangup = 8;
constexpr int poll_wake = -1, poll_callback = -2, poll_timeout = -3, poll_error = -4;
}

extern "C" ALooper* ALooper_forThread() { return current.value; }
extern "C" ALooper* ALooper_prepare(int options) {
    if (current.value) return current.value;
    ALooper* looper = new (std::nothrow) ALooper(options);
    if (!looper) return nullptr;
    if (looper->wake_fd < 0) { delete looper; return nullptr; }
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
    if (!looper || looper->wake_fd < 0) return;
    uint64_t count = 1;
    (void)write(looper->wake_fd, &count, sizeof(count));
}
extern "C" int ALooper_addFd(ALooper* looper, int fd, int ident, int events,
                               ALooper::Callback callback, void* data) {
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
}
extern "C" int ALooper_removeFd(ALooper* looper, int fd) {
    if (!looper) return -1;
    std::lock_guard<std::mutex> guard(looper->mutex);
    if (!looper->watches.erase(fd)) return 0;
    epoll_ctl(looper->poll_fd, EPOLL_CTL_DEL, fd, nullptr);
    ALooper_wake(looper);
    return 1;
}
extern "C" int ALooper_pollOnce(int timeout_ms, int* out_fd, int* out_events, void** out_data) {
    if (out_fd) *out_fd = -1;
    if (out_events) *out_events = 0;
    if (out_data) *out_data = nullptr;
    ALooper* looper = current.value;
    if (!looper) return poll_error;
    epoll_event event{};
    int ready;
    do { ready = epoll_wait(looper->poll_fd, &event, 1, timeout_ms); }
    while (ready < 0 && errno == EINTR);
    if (ready == 0) return poll_timeout;
    if (ready < 0) return poll_error;
    if (event.data.fd == looper->wake_fd) {
        uint64_t count;
        (void)read(looper->wake_fd, &count, sizeof(count));
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
}
