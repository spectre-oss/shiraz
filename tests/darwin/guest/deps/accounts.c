// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <grp.h>
#include <pwd.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/file.h>
_Static_assert(sizeof(uid_t) == 4 && sizeof(gid_t) == 4,
               "bionic account identity widths");
_Static_assert(sizeof(struct passwd) == 48 &&
                   offsetof(struct passwd, pw_uid) == 16 &&
                   offsetof(struct passwd, pw_gecos) == 24 &&
                   offsetof(struct passwd, pw_dir) == 32 &&
                   offsetof(struct passwd, pw_shell) == 40,
               "bionic passwd layout");
_Static_assert(sizeof(struct group) == 32 &&
                   offsetof(struct group, gr_gid) == 16 &&
                   offsetof(struct group, gr_mem) == 24,
               "bionic group layout");
_Static_assert(LOCK_SH == 1 && LOCK_EX == 2 && LOCK_NB == 4 && LOCK_UN == 8,
               "bionic advisory lock constants");
int accounts_passwd(uint32_t uid, void *record, char *buffer, size_t size,
                    void **result, int *error) {
  errno = 73;
  int value = getpwuid_r(uid, record, buffer, size, (struct passwd **)result);
  *error = errno;
  return value;
}
void *accounts_group(uint32_t gid, int *error) {
  errno = 73;
  struct group *value = getgrgid(gid);
  *error = errno;
  return value;
}
int accounts_lock(int fd, int operation, int *error) {
  errno = 73;
  int result = flock(fd, operation);
  *error = errno;
  return result;
}
