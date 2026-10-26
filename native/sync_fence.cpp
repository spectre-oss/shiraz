// SPDX-License-Identifier: GPL-3.0-only
#include <cerrno>
#include <cstring>
#include <linux/sync_file.h>
#include <sys/ioctl.h>
#include <poll.h>
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
