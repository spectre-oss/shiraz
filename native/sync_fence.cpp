// SPDX-License-Identifier: GPL-3.0-only
#include <cerrno>
#include <cstring>
#if defined(__APPLE__)
#include <fcntl.h>
#include <mutex>
#include <new>
#include <system_error>
#include <thread>
#include <vector>
#include <sys/stat.h>
#include <unistd.h>
#else
#include <linux/sync_file.h>
#include <sys/ioctl.h>
#endif
#include <poll.h>
#if defined(__APPLE__)
// Darwin has no sync_file. A Shiraz fence is the read end of a close-on-exec pipe
// that turns readable once signalled: its producer writes a byte or closes the write
// end. Consumers only poll and never read, so a signalled fence stays signalled.
namespace {
bool ready(int fd) {
    pollfd descriptor{fd, POLLIN, 0};
    int result;
    do { result = poll(&descriptor, 1, 0); } while (result < 0 && errno == EINTR);
    return result > 0 && !(descriptor.revents & (POLLERR | POLLNVAL));
}
// Non-blocking ends; the write end never raises SIGPIPE after every reader is gone.
bool fence_pipe(int ends[2]) {
    if (pipe(ends)) return false;
    for (int fd : {ends[0], ends[1]})
        if (fcntl(fd, F_SETFD, FD_CLOEXEC) < 0 || fcntl(fd, F_SETFL, O_NONBLOCK) < 0 ||
            (fd == ends[1] && fcntl(fd, F_SETNOSIGPIPE, 1) < 0)) {
            const int error = errno; close(ends[0]); close(ends[1]); errno = error; return false;
        }
    return true;
}
void signal_fence(int write_end) { char value = 1; [[maybe_unused]] const auto written = write(write_end, &value, 1); close(write_end); }
// Mirror SYNC_IOC_MERGE failures: a bad first fd fails the ioctl (EBADF), a first fd
// that is not a fence rejects the request (ENOTTY), and a bad second fd is not found (ENOENT).
int check(int fd, int bad, int foreign) {
    struct stat info;
    if (fd < 0 || fstat(fd, &info)) { errno = bad; return -1; }
    if (!S_ISFIFO(info.st_mode) || (fcntl(fd, F_GETFL) & O_ACCMODE) == O_WRONLY) { errno = foreign; return -1; }
    return 0;
}
// One worker waits for every pending merge instead of one thread per fence.
class Merger {
    struct Entry { int inputs[2]; int output; };
    std::mutex mutex;
    std::vector<Entry> entries;
    int wake[2]{-1, -1};
    void run() {
        std::vector<pollfd> descriptors;
        std::vector<size_t> owners;
        for (;;) {
            descriptors.assign(1, pollfd{wake[0], POLLIN, 0}); owners.clear();
            {
                std::lock_guard<std::mutex> lock(mutex);
                for (size_t i = 0; i < entries.size(); ++i)
                    for (size_t k = 0; k < 2; ++k)
                        if (entries[i].inputs[k] >= 0) { descriptors.push_back({entries[i].inputs[k], POLLIN, 0}); owners.push_back(i * 2 + k); }
            }
            int result;
            do { result = poll(descriptors.data(), descriptors.size(), -1); } while (result < 0 && errno == EINTR);
            std::lock_guard<std::mutex> lock(mutex);
            if (descriptors[0].revents) { char drain[64]; while (read(wake[0], drain, sizeof(drain)) > 0) {} }
            // add() only appends, so the indices captured above remain valid here.
            for (size_t j = 1; j < descriptors.size(); ++j) if (descriptors[j].revents) {
                int& input = entries[owners[j - 1] / 2].inputs[owners[j - 1] % 2]; close(input); input = -1;
            }
            for (size_t i = entries.size(); i > 0; --i) if (entries[i - 1].inputs[0] < 0 && entries[i - 1].inputs[1] < 0) {
                signal_fence(entries[i - 1].output); entries.erase(entries.begin() + i - 1);
            }
        }
    }
public:
    Merger() {
        if (!fence_pipe(wake)) throw std::system_error(errno, std::generic_category());
        try { std::thread([this] { run(); }).detach(); } catch (...) { close(wake[0]); close(wake[1]); throw; }
    }
    void add(int first, int second, int output) {
        std::lock_guard<std::mutex> lock(mutex); entries.push_back({{first, second}, output});
        char value = 1; [[maybe_unused]] const auto written = write(wake[1], &value, 1);
    }
};
// Leaked on purpose: its detached worker may still run during static destruction.
Merger& merger() { static Merger* result = new Merger; return *result; }
}
extern "C" int sync_merge(const char* name, int first, int second) {
    (void)name; // Darwin fences have no SYNC_IOC_FILE_INFO to report a name through.
    if (check(first, EBADF, ENOTTY) || check(second, ENOENT, ENOENT)) return -1;
    int inputs[2]{-1, -1}, ends[2];
    if (!fence_pipe(ends)) return -1;
    // The merged fence holds its own references, as the kernel's merged sync_file does.
    for (int k = 0; k < 2; ++k) {
        const int fd = k ? second : first;
        if (!ready(fd) && (inputs[k] = fcntl(fd, F_DUPFD_CLOEXEC, 0)) < 0) {
            const int error = errno; if (inputs[0] >= 0) close(inputs[0]); close(ends[0]); close(ends[1]); errno = error; return -1;
        }
    }
    if (inputs[0] < 0 && inputs[1] < 0) { signal_fence(ends[1]); return ends[0]; }
    int error = 0;
    try { merger().add(inputs[0], inputs[1], ends[1]); return ends[0]; }
    catch (const std::system_error& failure) { error = failure.code().value(); }
    catch (const std::bad_alloc&) { error = ENOMEM; }
    for (int fd : inputs) if (fd >= 0) close(fd);
    close(ends[0]); close(ends[1]); errno = error; return -1;
}
#else
// Android sync fences are Linux sync_file descriptors; preserve the kernel's
// synchronization and ownership instead of substituting a signaled event.
extern "C" int sync_merge(const char* name, int first, int second) {
    sync_merge_data data{};
    if (name) std::strncpy(data.name, name, sizeof(data.name) - 1);
    data.fd2 = second;
    int result;
    do { result = ioctl(first, SYNC_IOC_MERGE, &data); } while (result < 0 && errno == EINTR);
    return result < 0 ? -1 : data.fence;
}
#endif
extern "C" int sync_wait(int fd, int timeout) {
    if (fd < 0) { errno = EINVAL; return -1; }
    pollfd descriptor{fd, POLLIN, 0};
    int result;
    do { result = poll(&descriptor, 1, timeout); } while (result < 0 && errno == EINTR);
    if (!result) { errno = ETIME; return -1; }
    if (result < 0) return -1;
    if (descriptor.revents & (POLLERR | POLLNVAL)) { errno = EINVAL; return -1; }
    return 0;
}
