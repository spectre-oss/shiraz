// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <errno.h>
_Static_assert(MREMAP_MAYMOVE == 1 && MREMAP_FIXED == 2, "pinned Android mremap flags");
static char *moved, *blocker, *original;
static size_t page;
static int status;
#define PREPARE(X) do { if (!(X)) { status = __LINE__; return 0; } } while (0)
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
void *prepare_moved_remap(void) {
  page = getpagesize();
  original = mmap(0, 4 * page, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  PREPARE(original != MAP_FAILED);
  original[0] = 0x11; original[page - 1] = 0x12;
  original[page] = 0x22; original[2 * page - 1] = 0x23;
  blocker = mmap(original + 2 * page, 2 * page, PROT_READ | PROT_WRITE,
                 MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
  PREPARE(blocker == original + 2 * page); blocker[0] = 0x55;
  PREPARE(mprotect(original + page, page, PROT_READ) == 0);
  errno = 0;
  PREPARE(mremap(original, 2 * page, 3 * page, 0) == MAP_FAILED && errno == ENOMEM);
  PREPARE(original[0] == 0x11 && original[page] == 0x22 && blocker[0] == 0x55);
  errno = 0;
  PREPARE(mremap(original, 2 * page, 3 * page, MREMAP_MAYMOVE | MREMAP_FIXED, blocker) == MAP_FAILED && errno == EOPNOTSUPP);
  moved = mremap(original, 2 * page, 3 * page, MREMAP_MAYMOVE);
  PREPARE(moved != MAP_FAILED && moved != original);
  PREPARE(moved[0] == 0x11 && moved[page - 1] == 0x12 && moved[page] == 0x22 && moved[2 * page - 1] == 0x23);
  PREPARE(!moved[2 * page] && !moved[3 * page - 1] && blocker[0] == 0x55);
  return moved;
}
int remap_prepare_status(void) { return status; }
int finish_moved_remap(void) {
  errno = 0;
  CHECK(munmap(original, 2 * page) == -1 && errno == EPERM && blocker[0] == 0x55);
  CHECK(mprotect(moved, 3 * page, PROT_READ | PROT_WRITE) == 0);
  moved[page] = 0x44; moved[2 * page] = 0x66;
  CHECK(mremap(moved, 3 * page, page, 0) == moved && moved[0] == 0x11);
  CHECK(munmap(moved, page) == 0 && munmap(blocker, 2 * page) == 0);
  return 0;
}
int run_remap_limits(void) {
  size_t size = getpagesize();
  char *none = mmap(0, 3 * size, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  CHECK(none != MAP_FAILED && mremap(none, 3 * size, size, 0) == none);
  CHECK(mprotect(none, size, PROT_READ | PROT_WRITE) == 0 && !none[0] && munmap(none, size) == 0);
  char *base = mmap(0, 3 * size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  CHECK(base != MAP_FAILED);
  char *occupied = mmap(base + 2 * size, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
  CHECK(occupied == base + 2 * size);
  base[0] = 0x66; base[size] = 0x77; occupied[0] = 0x55;
  CHECK(mprotect(base, size, PROT_NONE) == 0 && mprotect(base + size, size, PROT_READ) == 0);
  errno = 0;
  CHECK(mremap(base, 2 * size, 3 * size, MREMAP_MAYMOVE) == MAP_FAILED && errno == EOPNOTSUPP);
  CHECK(mprotect(base, 2 * size, PROT_READ | PROT_WRITE) == 0 && base[0] == 0x66 && base[size] == 0x77 && occupied[0] == 0x55);
  errno = 0;
  CHECK(mremap(base + 1, 2 * size, 3 * size, MREMAP_MAYMOVE) == MAP_FAILED && errno == EINVAL);
  errno = 0;
  CHECK(mremap(base, 2 * size, (size_t)256 * 1024 * 1024 + size, MREMAP_MAYMOVE) == MAP_FAILED && errno == ENOMEM);
  CHECK(munmap(base, 2 * size) == 0 && munmap(occupied, size) == 0);
  int fd = open("remap-backing", O_CREAT | O_TRUNC | O_RDWR, 0600);
  CHECK(fd >= 0 && ftruncate(fd, 2 * size) == 0);
  char *file = mmap(0, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  CHECK(file != MAP_FAILED); file[0] = 0x7a;
  errno = 0;
  CHECK(mremap(file, size, 2 * size, MREMAP_MAYMOVE) == MAP_FAILED && errno == EOPNOTSUPP && file[0] == 0x7a);
  CHECK(msync(file, size, MS_SYNC) == 0);
  char observed = 0; CHECK(pread(fd, &observed, 1, 0) == 1 && observed == 0x7a);
  CHECK(munmap(file, size) == 0);
  file = mmap(0, size, PROT_READ, MAP_PRIVATE, fd, 0); CHECK(file != MAP_FAILED);
  errno = 0;
  CHECK(mremap(file, size, 2 * size, MREMAP_MAYMOVE) == MAP_FAILED && errno == EOPNOTSUPP && file[0] == 0x7a);
  CHECK(munmap(file, size) == 0 && close(fd) == 0 && unlink("remap-backing") == 0);
  char *shared = mmap(0, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_ANONYMOUS, -1, 0); CHECK(shared != MAP_FAILED);
  shared[0] = 0x11; errno = 0;
  CHECK(mremap(shared, size, 2 * size, MREMAP_MAYMOVE) == MAP_FAILED && errno == EOPNOTSUPP && shared[0] == 0x11);
  CHECK(munmap(shared, size) == 0);
  return 0;
}
int reject_foreign_remap(void *address, size_t size) {
  errno = 0;
  CHECK(mremap(address, size, 2 * size, MREMAP_MAYMOVE) == MAP_FAILED && (errno == EFAULT || errno == EINVAL));
  return 0;
}
