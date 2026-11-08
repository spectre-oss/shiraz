// SPDX-License-Identifier: GPL-3.0-only
#include <sys/vfs.h>
#include <fcntl.h>
#include <unistd.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
_Static_assert(sizeof(struct statfs) == 120 && sizeof(struct statfs64) == 120 &&
               offsetof(struct statfs, f_flags) == 80 && sizeof(((struct statfs *)0)->f_blocks) == 8,
               "Android arm64 statfs layout");
_Static_assert(NFS_SUPER_MAGIC == 0x6969 && MSDOS_SUPER_MAGIC == 0x4d44 && EXFAT_SUPER_MAGIC == 0x2011bab0 &&
               ISOFS_SUPER_MAGIC == 0x9660 && UDF_SUPER_MAGIC == 0x15013346 && EXT2_SUPER_MAGIC == 0xef53,
               "pinned Linux filesystem format codes");
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
static int unchanged(const void *bytes, size_t size) {
  for (size_t i = 0; i < size; ++i) if (((const unsigned char *)bytes)[i] != 0xa5) return 0;
  return 1;
}
int run_fs_stats(int representable) {
  int fd = open(".", O_RDONLY | O_DIRECTORY); CHECK(fd >= 0);
  struct statfs path, descriptor;
  memset(&path, 0xa5, sizeof(path)); memset(&descriptor, 0xa5, sizeof(descriptor));
  errno = 0;
  int status = statfs(".", &path);
  if (!representable) CHECK(status == -1 && errno == EOPNOTSUPP && unchanged(&path, sizeof(path)));
  else CHECK(status == 0 && path.f_type && path.f_bsize && path.f_frsize == path.f_bsize && path.f_namelen && (path.f_flags & 0x20));
  errno = 0;
  status = fstatfs(fd, &descriptor);
  if (!representable) CHECK(status == -1 && errno == EOPNOTSUPP && unchanged(&descriptor, sizeof(descriptor)));
  else CHECK(status == 0 && descriptor.f_type == path.f_type && descriptor.f_bsize == path.f_bsize &&
              !memcmp(&descriptor.f_fsid, &path.f_fsid, sizeof(path.f_fsid)));
  struct statfs64 large; memset(&large, 0xa5, sizeof(large)); errno = 0;
  status = statfs64(".", &large);
  if (!representable) CHECK(status == -1 && errno == EOPNOTSUPP && unchanged(&large, sizeof(large)));
  else CHECK(status == 0 && large.f_type == path.f_type && large.f_bsize == path.f_bsize);
  errno = 0; CHECK(fstatfs(-1, &descriptor) == -1 && errno == EBADF);
  errno = 0; CHECK(statfs("/proc/self/maps", &path) == -1 && errno == ENOSYS);
  int (*query)(const char *, struct statfs *) = statfs;
  errno = 0; CHECK(query(".", 0) == -1 && errno == EFAULT);
  CHECK(close(fd) == 0); return 0;
}
