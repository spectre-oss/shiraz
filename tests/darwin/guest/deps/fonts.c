// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>
int fonts_stdio(const char *path, const char *mode, char *output, int size,
                int *error) {
  errno = 73;
  FILE *stream = fopen(path, mode);
  if (!stream) {
    *error = errno;
    return -1;
  }
  int count = (int)fread(output, 1, (size_t)size, stream);
  int failed = ferror(stream);
  fclose(stream);
  *error = errno;
  return failed ? -1 : count;
}
int fonts_open(const char *path, int flags, char *output, int size,
               int *error) {
  errno = 73;
  int fd = open(path, flags, 0600);
  if (fd < 0) {
    *error = errno;
    return -1;
  }
  int result = (int)read(fd, output, (size_t)size);
  close(fd);
  *error = errno;
  return result;
}

int fonts_metadata(const char *path, long expected_size) {
  struct stat info;
  if (stat(path, &info) || !S_ISREG(info.st_mode) || info.st_size != expected_size)
    return 1;
  if (access(path, F_OK) || access(path, R_OK)) return 2;
  errno = 0;
  if (access(path, W_OK) != -1 || errno != EROFS) return 3;
  errno = 0;
  if (access(path, X_OK) != -1 || errno != EACCES) return 4;
  return 0;
}
