// SPDX-License-Identifier: GPL-3.0-only
// Native collector tests only; never Android/bionic execution evidence.
#include "bionic_reference_child.h"
#include <errno.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>

static int require(int condition, const char *message) {
  if (!condition) { fprintf(stderr, "FAIL reference child: %s (errno=%d)\n", message, errno); return 0; }
  return 1;
}
int main(int argc, char **argv) {
  if (argc == 2) {
    if (!strcmp(argv[1], "exit134")) _exit(134);
    if (!strcmp(argv[1], "abort")) {
      struct rlimit limit = {0, 0}; (void)setrlimit(RLIMIT_CORE, &limit);
      signal(SIGABRT, SIG_DFL); abort();
    }
    if (!strcmp(argv[1], "output")) {
      const char text[] = "stdout\n"; (void)write(STDOUT_FILENO, text, sizeof(text) - 1);
      (void)write(STDERR_FILENO, "stderr\n", 7); _exit(0);
    }
    if (!strcmp(argv[1], "overflow")) {
      char bytes[4096]; memset(bytes, 'x', sizeof(bytes));
      for (;;) if (write(STDOUT_FILENO, bytes, sizeof(bytes)) < 0) _exit(0);
    }
    if (!strcmp(argv[1], "timeout")) { for (;;) pause(); }
    return 90;
  }
  char executable[PATH_MAX];
  if (!realpath(argv[0], executable)) return 1;
  struct shiraz_reference_child result;
  char *command[] = {executable, "exit134", NULL};
  if (!require(!shiraz_reference_collect(command, 2000, &result) && result.returncode == 134 &&
               !result.timed_out, "exit134 remains an exit")) return 1;
  command[1] = "abort";
  if (!require(!shiraz_reference_collect(command, 2000, &result) && result.returncode == -SIGABRT &&
               !result.timed_out, "SIGABRT comes from actual wait status")) return 1;
  command[1] = "output";
  if (!require(!shiraz_reference_collect(command, 2000, &result) && result.returncode == 0 &&
               result.output_size == 14 && !memcmp(result.output, "stdout\nstderr\n", 14),
               "stdout and stderr are collected")) return 1;
  command[1] = "overflow";
  errno = 0;
  if (!require(shiraz_reference_collect(command, 2000, &result) == -1 && errno == EOVERFLOW,
               "output overflow fails and reaps")) return 1;
  errno = 0;
  if (!require(waitpid(-1, NULL, WNOHANG) == -1 && errno == ECHILD,
               "overflow leaves no owned child")) return 1;
  command[1] = "timeout";
  if (!require(!shiraz_reference_collect(command, 50, &result) && result.timed_out &&
               result.returncode == -SIGKILL, "timeout kills and reaps owned child")) return 1;
  errno = 0;
  if (!require(waitpid(-1, NULL, WNOHANG) == -1 && errno == ECHILD,
               "timeout leaves no owned child")) return 1;
  char *missing[] = {"/shiraz-m5-missing-executable", NULL};
  if (!require(!shiraz_reference_collect(missing, 2000, &result) && result.returncode == 127,
               "exec failure is explicit exit127")) return 1;
  char excessive[4098]; memset(excessive, 'x', sizeof(excessive)); excessive[4097] = 0;
  char *long_argv[] = {executable, excessive, NULL};
  if (!require(shiraz_reference_collect(long_argv, 100, &result) == -1 && errno == E2BIG,
               "argument bytes bounded before fork")) return 1;
  char *bad[] = {"relative", NULL};
  if (!require(shiraz_reference_collect(bad, 100, &result) == -1 && errno == EINVAL,
               "relative executable rejected")) return 1;
  struct sigaction old_action, ignored;
  memset(&ignored, 0, sizeof(ignored)); ignored.sa_handler = SIG_IGN;
  if (!require(sigaction(SIGCHLD, &ignored, &old_action) == 0, "install ignored SIGCHLD")) return 1;
  int rejected = shiraz_reference_collect(command, 100, &result) == -1 && errno == ENOTSUP;
  if (!require(sigaction(SIGCHLD, &old_action, NULL) == 0 && rejected,
               "auto-reaping rejected before fork")) return 1;
  puts("M5_REFERENCE_COLLECTOR_PASS native_only=1 exit134_distinct=1 raw_sigabrt=1 merged_output=1 overflow_reaped=1 timeout_reaped=1");
  return 0;
}
