// SPDX-License-Identifier: GPL-3.0-only
#include <android/set_abort_message.h>
#include <errno.h>
#include <linux/random.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <syslog.h>
#include <unistd.h>
extern int __cxa_atexit(void (*)(void *), void *, void *);
_Static_assert(__NR_getpid == 172 && __NR_getppid == 173 && __NR_getuid == 174 &&
               __NR_geteuid == 175 && __NR_getgid == 176 && __NR_getegid == 177 && __NR_gettid == 178,
               "Linux AArch64 process syscall ABI");
_Static_assert(__NR_getrandom == 278 && __NR_futex == 98 && GRND_NONBLOCK == 1 &&
               GRND_RANDOM == 2 && GRND_INSECURE == 4, "Linux AArch64 random/futex ABI");
_Static_assert(LOG_PID == 1 && LOG_PERROR == 32 && LOG_LOCAL3 == (19 << 3), "guest syslog ABI");
long process_call(long number, int *error) {
  errno = 73;
  long result = syscall(number);
  *error = errno;
  return result;
}
long process_random(void *buffer, unsigned long size, unsigned flags, int *error) {
  errno = 73;
  long result = syscall(__NR_getrandom, buffer, size, flags);
  *error = errno;
  return result;
}
int process_log(void) {
  char ident[] = "guest-copy";
  errno = 73;
  openlog(ident, LOG_PID | LOG_PERROR, LOG_LOCAL3);
  ident[0] = 'X';
  syslog(LOG_INFO, "scalar=%d float=%.2f text=%s %%", -31, 2.75, "guest");
  closelog();
  return errno;
}
int process_log_bad(void) {
  errno = 73;
  openlog("invalid", 0x4000, LOG_USER);
  return errno;
}
__attribute__((noreturn)) void process_abort(void) {
  char message[] = "owned guest abort";
  android_set_abort_message(message);
  message[0] = 'X';
  android_set_abort_message("later message must not replace first");
  abort();
}
static int notify_fd = -1, dso;
static void notify(void *unused) {
  (void)unused;
  char marker = 'F';
  if (notify_fd >= 0) (void)write(notify_fd, &marker, 1);
}
int process_prepare_exit(int fd) {
  notify_fd = fd;
  return __cxa_atexit(notify, 0, &dso);
}
__attribute__((noreturn)) void process_exit(int status, int fast) {
  if (fast) _exit(status);
  exit(status);
}
