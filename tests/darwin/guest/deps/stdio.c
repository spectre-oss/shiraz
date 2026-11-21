// SPDX-License-Identifier: GPL-3.0-only
#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <bits/struct_file.h>
_Static_assert(sizeof(FILE) == 152, "pinned bionic LP64 compatibility FILE");
_Static_assert(_Alignof(FILE) == 8, "pinned bionic FILE alignment");
_Static_assert(SEEK_SET == 0 && SEEK_CUR == 1 && SEEK_END == 2, "stdio origin ABI");
#define CHECK(X)                                                                                   \
    do {                                                                                           \
        if (!(X))                                                                                  \
            return __LINE__;                                                                       \
    } while (0)
static int scan_list(FILE* file, const char* format, ...) {
    va_list arguments;
    va_start(arguments, format);
    int result = vfscanf(file, format, arguments);
    va_end(arguments);
    return result;
}
static int run_stream_scan(void) {
    FILE* file = fopen("scan-data", "w+");
    CHECK(file);
    const char data[] = " -17 0xff 3.25 alpha!\n1 2 3 4 5 6 7 8 9 10 11 12?";
    CHECK(fwrite(data, 1, sizeof(data) - 1, file) == sizeof(data) - 1 && !fseek(file, 0, SEEK_SET));
    int number = 0, count = -1;
    unsigned hex = 0;
    double real = 0;
    char word[8] = {0};
    CHECK(fscanf(file, " %d %x %lf %5s%n", &number, &hex, &real, word, &count) == 4);
    CHECK(number == -17 && hex == 255 && real == 3.25 && !strcmp(word, "alpha") && count == 20);
    CHECK(fgetc(file) == '!' && fgetc(file) == '\n');
    int values[12] = {0};
    CHECK(scan_list(file, "%d %d %d %d %d %d %d %d %d %d %d %d", &values[0], &values[1], &values[2],
                    &values[3], &values[4], &values[5], &values[6], &values[7], &values[8],
                    &values[9], &values[10], &values[11]) == 12);
    for (int i = 0; i < 12; ++i)
        CHECK(values[i] == i + 1);
    CHECK(fgetc(file) == '?');
    CHECK(!fseek(file, 0, SEEK_SET));
    long before = ftell(file);
    long double quad = 0;
    const char* unsupported = "%Lf";
    errno = 0;
    CHECK(fscanf(file, unsupported, &quad) == EOF && errno == EOPNOTSUPP && ftell(file) == before);
    const char* allocating = "%ms";
    char* allocated = 0;
    errno = 0;
    CHECK(fscanf(file, allocating, &allocated) == EOF && errno == EOPNOTSUPP &&
          ftell(file) == before && !allocated);
    CHECK(!fclose(file) && !unlink("scan-data"));
    int descriptors[2];
    CHECK(!pipe(descriptors));
    const char bytes[] = {'A', 0, 'B', '!'};
    CHECK(write(descriptors[1], bytes, sizeof(bytes)) == (ssize_t)sizeof(bytes));
    CHECK(!close(descriptors[1]));
    file = fdopen(descriptors[0], "r");
    CHECK(file);
    char binary[3] = {};
    CHECK(fscanf(file, "%3c", binary) == 1 && !memcmp(binary, bytes, 3));
    CHECK(fgetc(file) == '!' && fgetc(file) == EOF && feof(file));
    CHECK(!fclose(file));
    return 0;
}
int run_stdio(void) {
    FILE* file = fopen("stdio-data", "w+e");
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
    int scan_result = run_stream_scan();
    if (scan_result)
        return scan_result;
    CHECK(unlink("stdio-data") == 0);
    return 0;
}
