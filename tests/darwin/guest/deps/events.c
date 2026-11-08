// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <time.h>
#include <unistd.h>
_Static_assert(sizeof(struct pollfd) == 8 && offsetof(struct pollfd, fd) == 0 &&
               offsetof(struct pollfd, events) == 4 && offsetof(struct pollfd, revents) == 6 &&
               sizeof(nfds_t) == 4 && sizeof(sigset_t) == 8 && sizeof(struct timespec) == 16,
               "pinned bionic event/poll ABI");
_Static_assert(EFD_SEMAPHORE == 1 && EFD_NONBLOCK == 0x800 && EFD_CLOEXEC == 0x80000 &&
               POLLIN == 1 && POLLOUT == 4 && POLLWRNORM == 0x100 && POLLRDHUP == 0x2000,
               "pinned bionic event flags");
int events_counter(void) {
  int fd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
  if (fd < 0) return 1;
  uint64_t value = 0;
  struct pollfd descriptor = {fd, POLLIN | POLLOUT, 0};
  if (poll(&descriptor, 1, 0) != 1 || descriptor.revents != POLLOUT) return 2;
  errno = 0;
  if (eventfd_read(fd, &value) != -1 || errno != EAGAIN) return 3;
  if (eventfd_write(fd, 3) || eventfd_write(fd, 4)) return 4;
  if (poll(&descriptor, 1, 0) != 1 || descriptor.revents != (POLLIN | POLLOUT)) return 5;
  unsigned char bytes[16] = {0};
  if (read(fd, bytes, sizeof(bytes)) != 8) return 6;
  memcpy(&value, bytes, sizeof(value));
  if (value != 7 || bytes[8]) return 7;
  errno = 0;
  if (write(fd, &value, 7) != -1 || errno != EINVAL) return 8;
  errno = 0;
  if (eventfd_write(fd, UINT64_MAX) != -1 || errno != EINVAL) return 9;
  if (eventfd_write(fd, UINT64_MAX - 1)) return 10;
  if (poll(&descriptor, 1, 0) != 1 || descriptor.revents != POLLIN) return 11;
  errno = 0;
  if (eventfd_write(fd, 1) != -1 || errno != EAGAIN) return 12;
  if (eventfd_read(fd, &value) || value != UINT64_MAX - 1 || close(fd)) return 13;
  fd = eventfd(2, EFD_SEMAPHORE | EFD_NONBLOCK);
  if (fd < 0 || eventfd_read(fd, &value) || value != 1 || eventfd_read(fd, &value) || value != 1) return 14;
  errno = 0;
  if (eventfd_read(fd, &value) != -1 || errno != EAGAIN || close(fd)) return 15;
  return 73;
}
int events_aliases(void) {
  int fd = eventfd(1, EFD_NONBLOCK | EFD_CLOEXEC);
  if (fd < 0 || (fcntl(fd, F_GETFL) & O_ACCMODE) != O_RDWR || !(fcntl(fd, F_GETFD) & FD_CLOEXEC)) return 1;
  int alias = dup(fd);
  if (alias < 0 || (fcntl(alias, F_GETFD) & FD_CLOEXEC)) return 2;
  if (fcntl(alias, F_SETFL, O_RDWR) || (fcntl(fd, F_GETFL) & O_NONBLOCK)) return 3;
  if (fcntl(fd, F_SETFL, O_RDWR | O_NONBLOCK)) return 4;
  uint64_t value;
  if (eventfd_read(alias, &value) || value != 1) return 5;
  int duplicate = fcntl(fd, F_DUPFD_CLOEXEC, 0);
  if (duplicate < 0 || !(fcntl(duplicate, F_GETFD) & FD_CLOEXEC) || close(fd)) return 6;
  if (eventfd_write(alias, 9) || eventfd_read(duplicate, &value) || value != 9) return 7;
  int pipefd[2];
  if (pipe(pipefd) || dup2(alias, pipefd[0]) != pipefd[0]) return 8;
  if (eventfd_write(pipefd[0], 13) || eventfd_read(alias, &value) || value != 13) return 9;
  if (close(pipefd[0]) || close(pipefd[1]) || close(alias) || close(duplicate)) return 10;
  return 73;
}
int events_poll_pipe(void) {
  int pipefd[2];
  if (pipe(pipefd)) return 1;
  struct pollfd descriptors[3] = {{pipefd[0], POLLIN, 73}, {pipefd[1], POLLWRNORM, 73}, {-1, -1, 73}};
  if (poll(descriptors, 3, 0) != 1 || descriptors[0].revents ||
      descriptors[1].revents != POLLWRNORM || descriptors[2].revents) return 2;
  char byte = 'x', result = 0;
  if (write(pipefd[1], &byte, 1) != 1 || poll(descriptors, 1, 0) != 1 ||
      descriptors[0].revents != POLLIN || read(pipefd[0], &result, 1) != 1 || result != 'x') return 3;
  if (close(pipefd[1]) || poll(descriptors, 1, 0) != 1 || !(descriptors[0].revents & POLLHUP)) return 4;
  if (close(pipefd[0])) return 5;
  struct pollfd missing = {0x7fffffff, POLLIN, 0};
  if (poll(&missing, 1, 0) != 1 || missing.revents != POLLNVAL) return 6;
  return 73;
}
int events_dup3(void) {
  int source = eventfd(1, EFD_NONBLOCK), target = eventfd(2, EFD_NONBLOCK);
  if (source < 0 || target < 0) return 1;
  uint64_t value = 0;
  int result = dup3(source, target, O_CLOEXEC);
  if (result == -1 && errno == ENOSYS) {
    if (eventfd_read(source, &value) || value != 1 || eventfd_read(target, &value) || value != 2 ||
        close(source) || close(target)) return 2;
    return 74; // Explicit older-host capability boundary, not emulated success.
  }
  if (result != target || !(fcntl(target, F_GETFD) & FD_CLOEXEC) ||
      eventfd_read(target, &value) || value != 1) return 3;
  errno = 0;
  if (dup3(source, source, 0) != -1 || errno != EINVAL) return 4;
  errno = 0;
  if (dup3(source, target, O_NONBLOCK) != -1 || errno != EINVAL) return 5;
  if (eventfd_write(source, 7) || eventfd_read(target, &value) || value != 7) return 6;
  int protected_target = eventfd(11, EFD_NONBLOCK);
  if (protected_target < 0) return 7;
  errno = 0;
  if (dup3(-1, protected_target, 0) != -1 || errno != EBADF ||
      eventfd_read(protected_target, &value) || value != 11) return 8;
  if (close(protected_target)) return 9;
  int pipefd[2];
  if (pipe(pipefd) || dup3(pipefd[0], target, O_CLOEXEC) != target ||
      !(fcntl(target, F_GETFD) & FD_CLOEXEC)) return 10;
  char byte = 'z', observed = 0;
  if (write(pipefd[1], &byte, 1) != 1 || read(target, &observed, 1) != 1 || observed != byte) return 11;
  if (close(pipefd[0]) || close(pipefd[1]) || close(target) || close(source)) return 12;
  return 73;
}
int events_ppoll(void) {
  struct timespec timeout = {0, 1000000};
  if (ppoll(0, 0, &timeout, 0) || timeout.tv_sec || timeout.tv_nsec != 1000000) return 1;
  timeout.tv_nsec = 1000000000;
  errno = 0;
  if (ppoll(0, 0, &timeout, 0) != -1 || errno != EINVAL) return 2;
  timeout.tv_nsec = 0;
  int fd = eventfd(1, EFD_NONBLOCK);
  struct pollfd descriptor = {fd, POLLIN, 0};
  if (fd < 0 || ppoll(&descriptor, 1, &timeout, 0) != 1 || descriptor.revents != POLLIN || close(fd)) return 3;
  return 73;
}
int events_explicit_limits(void) {
  errno = 0;
  if (inotify_init() != -1 || errno != ENOSYS) return 1;
  errno = 0;
  if (inotify_init1(IN_NONBLOCK) != -1 || errno != ENOSYS) return 2;
  struct pollfd descriptor = {0, POLLRDHUP, 0};
  errno = 0;
  if (poll(&descriptor, 1, 0) != -1 || errno != ENOTSUP) return 3;
  return 73;
}
int events_make_blocking(void) { return eventfd(0, 0); }
int events_read_expected(int fd, uint64_t expected) {
  uint64_t value;
  return eventfd_read(fd, &value) || value != expected ? 1 : 73;
}
int events_write_value(int fd, uint64_t value) { return eventfd_write(fd, value) ? 1 : 73; }
int events_close(int fd) { return close(fd) ? 1 : 73; }
int events_ppoll_signal(void) {
  struct timespec timeout = {1, 0};
  sigset_t mask = {0};
  errno = 0;
  return ppoll(0, 0, &timeout, &mask) == -1 && errno == EINTR ? 73 : 1;
}
