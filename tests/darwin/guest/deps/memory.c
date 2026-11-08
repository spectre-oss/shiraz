// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#define CHECK(value) do { if (!(value)) return __LINE__; } while (0)
int run_memory(void) {
  size_t page = getpagesize();
  CHECK(page == 16384);
  char *data = mmap(0, 3 * page, PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  CHECK(data != MAP_FAILED && (uintptr_t)data >= (1ULL << 32));
  for (size_t i = 0; i < 3 * page; ++i)
    CHECK(data[i] == 0);
  data[0] = 'a'; data[page] = 'b'; data[2 * page] = 'c';
  CHECK(mprotect(data + page, page, PROT_READ) == 0);
  CHECK(data[page] == 'b');
  CHECK(mprotect(data + page, page, PROT_NONE) == 0);
  CHECK(mprotect(data + page, page, PROT_READ | PROT_WRITE) == 0);
  CHECK(data[page] == 'b');
  CHECK(mmap(data + page, page, PROT_READ | PROT_WRITE,
             MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) == data + page);
  CHECK(data[0] == 'a' && data[page] == 0 && data[2 * page] == 'c');
  errno = 0;
  CHECK(mmap(data + page, page, PROT_READ, MAP_FIXED | MAP_PRIVATE, -1, 0) ==
        MAP_FAILED && errno == EBADF);
  CHECK(data[0] == 'a' && data[page] == 0 && data[2 * page] == 'c');
  errno = 0;
  CHECK(mprotect(data + 4096, page, PROT_READ) == -1 && errno == EINVAL);
  errno = 0;
  CHECK(mprotect(data, page, PROT_EXEC) == -1 && errno == EOPNOTSUPP);
  CHECK(munmap(data + page, 1) == 0);
  errno = 0;
  CHECK(mprotect(data, 3 * page, PROT_READ) == -1 && errno == EPERM);
  data[0] = 'x'; data[2 * page] = 'y';
  CHECK(munmap(data, page) == 0 && munmap(data + 2 * page, page) == 0);
  errno = 0;
  CHECK(munmap(data, page) == -1 && errno == EPERM);
  errno = 0;
  CHECK(mmap(0, 0, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) == MAP_FAILED &&
        errno == EINVAL);
  errno = 0;
  CHECK(mmap(0, page, PROT_READ | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) ==
        MAP_FAILED && errno == EOPNOTSUPP);
  errno = 0;
  CHECK(mmap(0, page, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS | 0x40000, -1, 0) ==
        MAP_FAILED && errno == EOPNOTSUPP);
  errno = 0;
  CHECK(mmap((void*)0x10000, page, PROT_READ,
             MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) == MAP_FAILED &&
        errno == EINVAL);
  errno = 0;
  CHECK(mmap(0, page, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE,
             -1, 0) == MAP_FAILED && errno == EOPNOTSUPP);
  int fd = open("mapped.bin", O_CREAT | O_EXCL | O_RDWR | O_CLOEXEC, 0600);
  CHECK(fd >= 0 && ftruncate(fd, 3 * page) == 0);
  CHECK(pwrite(fd, "file", 4, page) == 4);
  char *shared = mmap64(0, page, PROT_READ | PROT_WRITE, MAP_SHARED, fd, page);
  CHECK(shared != MAP_FAILED && memcmp(shared, "file", 4) == 0);
  memcpy(shared, "sync", 4);
  CHECK(msync(shared, page, MS_SYNC) == 0);
  char buffer[4];
  CHECK(pread(fd, buffer, 4, page) == 4 && memcmp(buffer, "sync", 4) == 0);
  errno = 0;
  CHECK(msync(shared, page, MS_SYNC | MS_ASYNC) == -1 && errno == EINVAL);
  char *copy = mmap(0, page, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, page);
  CHECK(copy != MAP_FAILED && memcmp(copy, "sync", 4) == 0);
  memcpy(copy, "copy", 4);
  CHECK(msync(copy, page, MS_SYNC) == 0);
  CHECK(pread(fd, buffer, 4, page) == 4 && memcmp(buffer, "sync", 4) == 0);
  errno = 0;
  CHECK(mmap(0, page, PROT_READ, MAP_PRIVATE, fd, 4096) == MAP_FAILED && errno == EINVAL);
  CHECK(close(fd) == 0 && memcmp(copy, "copy", 4) == 0 &&
        memcmp(shared, "sync", 4) == 0);
  CHECK(munmap(copy, page) == 0 && munmap(shared, page) == 0 && unlink("mapped.bin") == 0);
  for (int i = 0; i < 128; ++i) {
    data = mmap(0, 1, PROT_READ | PROT_WRITE,
                MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    CHECK(data != MAP_FAILED && data[0] == 0);
    data[0] = 'z';
    CHECK(munmap(data, 1) == 0);
  }
  void *reserved[4];
  for (int i = 0; i < 4; ++i) {
    reserved[i] = mmap(0, 256ULL << 20, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    CHECK(reserved[i] != MAP_FAILED);
  }
  errno = 0;
  CHECK(mmap(0, page, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0) == MAP_FAILED &&
        errno == ENOMEM);
  for (int i = 0; i < 4; ++i)
    CHECK(munmap(reserved[i], 256ULL << 20) == 0);
  return 0;
}
