// SPDX-License-Identifier: GPL-3.0-only
#include <stdio.h>
#include <stdint.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <bits/struct_file.h>
_Static_assert(sizeof(FILE) == 152, "pinned bionic LP64 compatibility FILE");
_Static_assert(_Alignof(FILE) == 8, "pinned bionic FILE alignment");
_Static_assert(SEEK_SET == 0 && SEEK_CUR == 1 && SEEK_END == 2,
               "stdio origin ABI");
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
int run_stdio(void) {
  FILE *file = fopen("stdio-data", "w+e");
  CHECK(file);
  CHECK(fcntl(fileno(file), F_GETFD) & FD_CLOEXEC);
  CHECK(fwrite("hello\n", 2, 3, file) == 3 && fflush(file) == 0);
  CHECK(ftell(file) == 6 && fseek(file, 0, SEEK_SET) == 0);
  char buffer[16] = {0};
  CHECK(fgets(buffer, sizeof(buffer), file) == buffer && !strcmp(buffer, "hello\n"));
  CHECK(fgetc(file) == EOF && feof(file) && !ferror(file));
  CHECK(ungetc('x', file) == 'x' && !feof(file) && getc(file) == 'x');
  CHECK(fseeko(file, INT64_C(4294967300), SEEK_SET) == 0);
  CHECK(ftello(file) == INT64_C(4294967300));
  CHECK(fclose(file) == 0);
  errno = 0;
  CHECK(fclose(file) == EOF && errno == EBADF);
  file = fopen("stdio-data", "r");
  CHECK(file && fread(buffer, 1, 6, file) == 6 && !memcmp(buffer, "hello\n", 6));
  errno = 0;
  CHECK(fwrite(buffer, (size_t)-1, 2, file) == 0 && errno == EOVERFLOW);
  CHECK(fclose(file) == 0);
  errno = 0;
  CHECK(!fopen("stdio-data", "w+x") && errno == EEXIST);
  errno = 0;
  CHECK(!fopen("stdio-data", "rZ") && errno == EINVAL);
  errno = 0;
  CHECK(!fopen("/proc/self/maps", "r") && errno == ENOSYS);
  CHECK(fileno(stdin) == 0 && fileno(stdout) == 1 && fileno(stderr) == 2);
  CHECK(fflush(0) == 0);
  errno = 0;
  CHECK(fclose(stderr) == EOF && errno == EOPNOTSUPP && fileno(stderr) == 2);
  CHECK(unlink("stdio-data") == 0);
  return 0;
}
