// SPDX-License-Identifier: GPL-3.0-only
#include <stddef.h>
#include <stdarg.h>
#include <string.h>
extern void *__memcpy_chk(void *, const void *, size_t, size_t);
extern void *__memmove_chk(void *, const void *, size_t, size_t);
extern void *__memset_chk(void *, int, size_t, size_t);
extern size_t __strlen_chk(const char *, size_t);
extern char *__strcpy_chk(char *, const char *, size_t);
extern char *__strncpy_chk(char *, const char *, size_t, size_t);
extern char *__strncpy_chk2(char *, const char *, size_t, size_t, size_t);
extern char *__strcat_chk(char *, const char *, size_t);
extern int __vsnprintf_chk(char *, size_t, int, size_t, const char *, va_list);
extern int __vsprintf_chk(char *, int, size_t, const char *, va_list);
extern int __snprintf_chk(char *, size_t, int, size_t, const char *, ...);
static int bounded(char *out, size_t count, size_t size, const char *format, ...) {
  va_list list; va_start(list, format);
  int result = __vsnprintf_chk(out, count, 0, size, format, list);
  va_end(list); return result;
}
static int unbounded(char *out, size_t size, const char *format, ...) {
  va_list list; va_start(list, format);
  int result = __vsprintf_chk(out, 0, size, format, list);
  va_end(list); return result;
}
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
int run_fortify(void) {
  char out[32]; volatile size_t n = 4;
  CHECK(__memset_chk(out, 0x5a, n, sizeof(out)) == out && out[3] == 0x5a);
  CHECK(__memcpy_chk(out, "abcd", n, sizeof(out)) == out && !memcmp(out,"abcd",4));
  CHECK(__memmove_chk(out+1, out, n, sizeof(out)-1) == out+1 && !memcmp(out,"aabcd",5));
  CHECK(__strcpy_chk(out, "abc", sizeof(out)) == out && __strlen_chk(out, 4) == 3);
  CHECK(__strncpy_chk(out,"x",4,sizeof(out)) == out && out[0]=='x' && !out[3]);
  CHECK(__strncpy_chk2(out,"abc",6,sizeof(out),4) == out && !memcmp(out,"abc\0\0\0",6));
  CHECK(__strcpy_chk(out,"a",sizeof(out)) == out && __strcat_chk(out,"bc",sizeof(out)) == out && !strcmp(out,"abc"));
  CHECK(bounded(out, sizeof(out), sizeof(out), "%d/%s/%.2f", 7,"x",1.5)==8 && !strcmp(out,"7/x/1.50"));
  CHECK(bounded(out, 5, sizeof(out), "%s", "abcdef")==6 && !strcmp(out,"abcd"));
  CHECK(unbounded(out,sizeof(out),"%d/%.1f",9,2.5)==5 && !strcmp(out,"9/2.5"));
  CHECK(__snprintf_chk(out,sizeof(out),0,sizeof(out),"%d/%.1f",8,3.5)==5 && !strcmp(out,"8/3.5"));
  return 0;
}
