// SPDX-License-Identifier: GPL-3.0-only
// Compiled against pinned bionic headers; all operations use guest ABI imports.
#define _GNU_SOURCE 1
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/time.h>
#include <unistd.h>
extern ssize_t __read_chk(int, void *, size_t, size_t);
_Static_assert(sizeof(struct statvfs) == sizeof(struct statvfs64),
               "LP64 statvfs aliases share layout");
#define SAME_VFS_FIELD(field)                                                   \
  _Static_assert(__builtin_offsetof(struct statvfs, field) ==                     \
                     __builtin_offsetof(struct statvfs64, field),                \
                 "LP64 statvfs alias field " #field)
SAME_VFS_FIELD(f_bsize);
SAME_VFS_FIELD(f_frsize);
SAME_VFS_FIELD(f_blocks);
SAME_VFS_FIELD(f_bfree);
SAME_VFS_FIELD(f_bavail);
SAME_VFS_FIELD(f_files);
SAME_VFS_FIELD(f_ffree);
SAME_VFS_FIELD(f_favail);
SAME_VFS_FIELD(f_fsid);
SAME_VFS_FIELD(f_flag);
SAME_VFS_FIELD(f_namemax);
#define CHECK(test)                                                            \
  do {                                                                         \
    if (!(test))                                                               \
      return __LINE__;                                                         \
  } while (0)
struct guarded_stat {
  uint64_t before[2];
  struct stat value;
  uint64_t after[2];
};
static int stat_guards(struct guarded_stat *value) {
  return value->before[0] == 0xa5a5a5a5a5a5a5a5ULL &&
         value->before[1] == 0xa5a5a5a5a5a5a5a5ULL &&
         value->after[0] == 0xa5a5a5a5a5a5a5a5ULL &&
         value->after[1] == 0xa5a5a5a5a5a5a5a5ULL;
}
int run_io(const char *root) {
  int directory = open(root, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
  CHECK(directory >= 0);
  struct {
    uint64_t before;
    struct statvfs value;
    uint64_t after;
  } volume;
  memset(&volume, 0xa5, sizeof(volume));
  CHECK(statvfs(root, &volume.value) == 0);
  CHECK(volume.before == 0xa5a5a5a5a5a5a5a5ULL &&
        volume.after == 0xa5a5a5a5a5a5a5a5ULL &&
        volume.value.f_bsize > 0 && volume.value.f_frsize > 0 &&
        volume.value.f_blocks >= volume.value.f_bfree &&
        volume.value.f_bfree >= volume.value.f_bavail &&
        volume.value.f_namemax >= 255);
  struct statvfs64 volume64;
  CHECK(statvfs64(root, &volume64) == 0 &&
        volume64.f_blocks == volume.value.f_blocks &&
        volume64.f_fsid == volume.value.f_fsid);
  CHECK(fstatvfs(directory, &volume.value) == 0 &&
        fstatvfs64(directory, &volume64) == 0 &&
        volume.value.f_blocks == volume64.f_blocks);
  errno = 0;
  CHECK(fstatvfs(-1, &volume.value) == -1 && errno == EBADF);
  int fd = openat(directory, "data.bin",
                  O_CREAT | O_EXCL | O_RDWR | O_CLOEXEC | O_LARGEFILE, 0640);
  CHECK(fd >= 0);
  CHECK(write(fd, "abcdef", 6) == 6);
  struct guarded_stat value;
  memset(&value, 0xa5, sizeof(value));
  CHECK(fstat(fd, &value.value) == 0 && stat_guards(&value));
  CHECK(value.value.st_size == 6 && (value.value.st_mode & 0777) == 0640 &&
        S_ISREG(value.value.st_mode));
  CHECK(value.value.st_mtim.tv_nsec >= 0 &&
        value.value.st_mtim.tv_nsec < 1000000000);
  struct stat relative;
  CHECK(fstatat(directory, "data.bin", &relative, 0) == 0 &&
        relative.st_ino == value.value.st_ino);
  CHECK(fstatat(fd, "", &relative, AT_EMPTY_PATH) == 0 &&
        relative.st_size == 6);
  CHECK((fcntl(fd, F_GETFD) & FD_CLOEXEC) != 0);
  CHECK(fcntl(fd, F_SETFD, 0) == 0 && fcntl(fd, F_GETFD) == 0);
  CHECK(ioctl(fd, FIOCLEX) == 0 && (fcntl(fd, F_GETFD) & FD_CLOEXEC));
  CHECK(ioctl(fd, FIONCLEX) == 0 && !(fcntl(fd, F_GETFD) & FD_CLOEXEC));
  int original = fcntl(fd, F_GETFL);
  CHECK(original >= 0 && (original & O_ACCMODE) == O_RDWR);
  CHECK(fcntl(fd, F_SETFL, original | O_APPEND | O_NONBLOCK) == 0);
  CHECK((fcntl(fd, F_GETFL) & (O_APPEND | O_NONBLOCK)) ==
        (O_APPEND | O_NONBLOCK));
  CHECK(lseek(fd, 0, SEEK_SET) == 0 && write(fd, "gh", 2) == 2 &&
        lseek(fd, 0, SEEK_CUR) == 8);
  CHECK(fcntl(fd, F_SETFL, original) == 0);
  CHECK(pwrite(fd, "XY", 2, 2) == 2);
  char data[32];
  CHECK(pread(fd, data, 8, 0) == 8 && memcmp(data, "abXYefgh", 8) == 0);
  CHECK(ftruncate(fd, 16) == 0 && lseek(fd, 0, SEEK_END) == 16);
  memset(data, 1, sizeof(data));
  CHECK(pread(fd, data, 8, 8) == 8);
  for (int i = 0; i < 8; ++i)
    CHECK(data[i] == 0);
  CHECK(ftruncate(fd, 4) == 0);
  struct {
    uint64_t before;
    struct flock value;
    uint64_t after;
  } lock;
  memset(&lock, 0xa5, sizeof(lock));
  lock.value.l_type = F_WRLCK;
  lock.value.l_whence = SEEK_SET;
  lock.value.l_start = 0;
  lock.value.l_len = 2;
  lock.value.l_pid = 0;
  CHECK(fcntl(fd, F_SETLK, &lock.value) == 0);
  lock.value.l_type = F_WRLCK;
  CHECK(fcntl(fd, F_GETLK, &lock.value) == 0 && lock.value.l_type == F_UNLCK);
  CHECK(lock.before == 0xa5a5a5a5a5a5a5a5ULL &&
        lock.after == 0xa5a5a5a5a5a5a5a5ULL);
  lock.value.l_type = F_UNLCK;
  CHECK(fcntl(fd, F_SETLKW, &lock.value) == 0);
  struct timespec times[2] = {{1234567890, 123456789}, {1234567891, 987654321}};
  CHECK(utimensat(directory, "data.bin", times, 0) == 0);
  CHECK(fstat(fd, &value.value) == 0 &&
        value.value.st_atim.tv_nsec == 123456789 &&
        value.value.st_mtim.tv_sec == 1234567891 &&
        value.value.st_mtim.tv_nsec == 987654321);
  times[0].tv_nsec = UTIME_NOW;
  times[1].tv_nsec = UTIME_OMIT;
  CHECK(futimens(fd, times) == 0);
  CHECK(fstat(fd, &value.value) == 0 &&
        value.value.st_mtim.tv_sec == 1234567891 &&
        value.value.st_mtim.tv_nsec == 987654321 &&
        value.value.st_atim.tv_sec > 1234567890);
  times[0].tv_nsec = 1000000000;
  errno = 0;
  CHECK(futimens(fd, times) == -1 && errno == EINVAL);
  CHECK(symlink("data.bin", "alias") == 0);
  CHECK(lstat("alias", &relative) == 0 && S_ISLNK(relative.st_mode) &&
        relative.st_size == 8);
  CHECK(stat("alias", &relative) == 0 && relative.st_ino == value.value.st_ino);
  CHECK(readlink("alias", data, sizeof(data)) == 8 &&
        memcmp(data, "data.bin", 8) == 0);
  errno = 0;
  CHECK(open("alias", O_RDONLY | O_NOFOLLOW) == -1 && errno == ELOOP);
  errno = 0;
  CHECK(openat(directory, "data.bin", O_CREAT | O_EXCL | O_RDWR, 0600) == -1 &&
        errno == EEXIST);
  CHECK(rename("data.bin", "renamed") == 0);
  errno = 0;
  CHECK(access("data.bin", F_OK) == -1 && errno == ENOENT);
  CHECK(faccessat(directory, "renamed", R_OK | W_OK, AT_EACCESS) == 0);
  CHECK(access("renamed", R_OK | W_OK) == 0 && fchmod(fd, 0600) == 0 &&
        chmod("renamed", 0644) == 0);
  CHECK(fstat(fd, &relative) == 0 && (relative.st_mode & 0777) == 0644);
  CHECK(fchmodat(directory, "renamed", 0600, 0) == 0);
  CHECK(fstat(fd, &relative) == 0 && (relative.st_mode & 0777) == 0600);
  errno = 0;
  CHECK(fchmodat(directory, "renamed", 0600, AT_REMOVEDIR) == -1 &&
        errno == EOPNOTSUPP);
  struct timeval micros[2] = {{1234567892, 123456}, {1234567893, 654321}};
  CHECK(utimes("renamed", micros) == 0 && fstat(fd, &relative) == 0);
  CHECK(relative.st_atim.tv_sec == micros[0].tv_sec &&
        relative.st_atim.tv_nsec == 123456000 &&
        relative.st_mtim.tv_sec == micros[1].tv_sec &&
        relative.st_mtim.tv_nsec == 654321000);
  micros[0].tv_usec = 1000000;
  errno = 0;
  CHECK(utimes("renamed", micros) == -1 && errno == EINVAL);
  CHECK(link("renamed", "hard") == 0 && fstat(fd, &relative) == 0 &&
        relative.st_nlink == 2);
  CHECK(unlink("hard") == 0 && unlink("alias") == 0);
  CHECK(mkdirat(directory, "sub", 0750) == 0 &&
        fstatat(directory, "sub", &relative, 0) == 0 &&
        S_ISDIR(relative.st_mode));
  CHECK(unlinkat(directory, "sub", AT_REMOVEDIR) == 0);
  CHECK(mkdir("empty", 0700) == 0 && rmdir("empty") == 0);
  CHECK(mkdir("cwd", 0700) == 0 && chdir("cwd") == 0);
  int cwd_file = open("../renamed", O_RDONLY);
  CHECK(cwd_file >= 0 && close(cwd_file) == 0);
  CHECK(chdir(root) == 0 && rmdir("cwd") == 0);
  int pipefd[2];
  CHECK(pipe(pipefd) == 0);
  CHECK(fcntl(pipefd[0], F_SETFL, O_NONBLOCK) == 0);
  errno = 0;
  CHECK(read(pipefd[0], data, 1) == -1 && errno == EAGAIN);
  CHECK(write(pipefd[1], "ok", 2) == 2);
  int available = -1;
  CHECK(ioctl(pipefd[0], FIONREAD, &available) == 0 && available == 2);
  CHECK(__read_chk(pipefd[0], data, 2, sizeof(data)) == 2 &&
        memcmp(data, "ok", 2) == 0);
  CHECK(close(pipefd[0]) == 0 && close(pipefd[1]) == 0);
  DIR *stream = opendir(".");
  CHECK(stream && dirfd(stream) >= 0);
  struct dirent *entry = readdir(stream);
  CHECK(entry);
  char first[256], second[256];
  memcpy(first, entry->d_name, strlen(entry->d_name) + 1);
  long location = telldir(stream);
  entry = readdir(stream);
  CHECK(entry);
  memcpy(second, entry->d_name, strlen(entry->d_name) + 1);
  seekdir(stream, location);
  entry = readdir(stream);
  CHECK(entry && strcmp(entry->d_name, second) == 0);
  rewinddir(stream);
  entry = readdir(stream);
  CHECK(entry && strcmp(entry->d_name, first) == 0);
  rewinddir(stream);
  int found = 0;
  errno = 0;
  while ((entry = readdir(stream))) {
    CHECK(entry->d_reclen <= sizeof(struct dirent) &&
          entry->d_reclen % 8 == 0 &&
          entry->d_reclen >= strlen(entry->d_name) + 20);
    if (!strcmp(entry->d_name, "renamed")) {
      CHECK(entry->d_type == DT_REG);
      found = 1;
    }
  }
  CHECK(found && errno == 0 && closedir(stream) == 0);
  errno = 0;
  CHECK(readdir(stream) == 0 && errno == EBADF);
  int copy = fcntl(directory, F_DUPFD_CLOEXEC, 0);
  CHECK(copy >= 0 && (fcntl(copy, F_GETFD) & FD_CLOEXEC));
  stream = fdopendir(copy);
  CHECK(stream && closedir(stream) == 0);
  errno = 0;
  CHECK(fcntl(copy, F_GETFD) == -1 && errno == EBADF);
  errno = 0;
  CHECK(open("/proc/self/maps", O_RDONLY) == -1 && errno == ENOSYS);
  errno = 0;
  CHECK(open("renamed", O_DIRECT | O_RDONLY) == -1 && errno == EOPNOTSUPP);
  errno = 0;
  CHECK(fcntl(fd, 0x7ffffffe) == -1 && errno == ENOSYS);
  errno = 0;
  CHECK(ioctl(fd, 0x1234) == -1 && errno == ENOTTY);
  CHECK(close(fd) == 0 && unlinkat(directory, "renamed", 0) == 0 &&
        close(directory) == 0);
  return 0;
}
