// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <signal.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/wait.h>
#include <unistd.h>
_Static_assert(sizeof(sigset_t) == 8 && sizeof(struct sigaction) == 32 &&
                   sizeof(siginfo_t) == 128,
               "bionic signal layouts");
_Static_assert(offsetof(struct sigaction, sa_handler) == 8 &&
                   offsetof(struct sigaction, sa_mask) == 16 &&
                   offsetof(struct sigaction, sa_restorer) == 24,
               "bionic action offsets");
_Static_assert(offsetof(siginfo_t, si_pid) == 16 &&
                   offsetof(siginfo_t, si_uid) == 20 &&
                   offsetof(siginfo_t, si_status) == 24,
               "bionic child siginfo offsets");
_Static_assert(sizeof(struct rusage) == 144 &&
                   offsetof(struct rusage, ru_maxrss) == 32,
               "bionic wait4 usage layout");
_Static_assert(SIGUSR1 == 10 && SIGUSR2 == 12 && SIGCHLD == 17 &&
                   SIGSTOP == 19 && SIGCONT == 18,
               "Linux signal numbers");
_Static_assert(SA_RESTART == 0x10000000 && SA_SIGINFO == 4 &&
                   SA_ONSTACK == 0x08000000 && WCONTINUED == 8 &&
                   WNOWAIT == 0x01000000,
               "Linux signal/wait flags");
static volatile sig_atomic_t observed, count, mask_probe_enabled, mask_probe_result;
static int notification = -1;
static void record(int number) {
  observed = number;
  ++count;
  if (mask_probe_enabled) {
    int saved = errno;
    sigset_t old;
    errno = 73;
    mask_probe_result = sigprocmask(SIG_BLOCK, 0, &old) == -1 && errno == ENOTSUP;
    errno = saved;
  }
  if (notification >= 0) {
    unsigned char value = (unsigned char)number;
    (void)write(notification, &value, 1);
  }
}
int signals_install(int mode, int fd, int *error) {
  notification = fd;
  errno = 73;
  int result;
  if (mode == 1)
    result = signal(SIGUSR1, record) == SIG_ERR ? -1 : 0;
  else {
    struct sigaction action = {0};
    sigemptyset(&action.sa_mask);
    sigaddset(&action.sa_mask, SIGUSR2);
    action.sa_handler = record;
    action.sa_flags = mode == 2   ? SA_SIGINFO
                      : mode == 3 ? SA_ONSTACK
                      : mode == 4 ? SA_RESETHAND
                                  : SA_RESTART;
    result = sigaction(SIGUSR1, &action, 0);
  }
  *error = errno;
  return result;
}
int signals_observed(int *calls) {
  *calls = count;
  return observed;
}
int signals_query(unsigned long *flags, unsigned long *mask, int *error) {
  struct sigaction action = {0};
  errno = 73;
  int result = sigaction(SIGUSR1, 0, &action);
  if (!result) {
    *flags = (unsigned)action.sa_flags;
    *mask = *(unsigned long *)&action.sa_mask;
  }
  *error = errno;
  return result;
}
int signals_sets(void) {
  sigset_t set;
  *(unsigned long *)&set = ~0ul;
  if (sigemptyset(&set) || *(unsigned long *)&set)
    return -1;
  if (sigaddset(&set, SIGUSR1) || sigismember(&set, SIGUSR1) != 1)
    return -2;
  if (sigdelset(&set, SIGUSR1) || sigismember(&set, SIGUSR1))
    return -3;
  return 1;
}
int signals_kill(int pid, int number, int *error) {
  errno = 73;
  int result = kill(pid, number);
  *error = errno;
  return result;
}
int signals_waitpid(int pid, int *status, int flags, int *error) {
  errno = 73;
  int result = waitpid(pid, status, flags);
  *error = errno;
  return result;
}
int signals_wait4(int pid, int *status, long *rss, int *error) {
  struct rusage usage = {0};
  errno = 73;
  int result = wait4(pid, status, 0, &usage);
  if (result > 0)
    *rss = usage.ru_maxrss;
  *error = errno;
  return result;
}
int signals_waitid(int pid, void *output, int *error) {
  errno = 73;
  int result = waitid(P_PID, (id_t)pid, output, WEXITED | WNOWAIT);
  *error = errno;
  return result;
}
int signals_unsupported(int which, int *error) {
  char *args[] = {(char *)"guest", 0};
  errno = 73;
  int result;
  if (which == 0)
    result = fork();
  else if (which == 1)
    result = vfork();
  else if (which == 2)
    result = execv("/system/bin/guest", args);
  else if (which == 3)
    result = execvp("guest", args);
  else
    result = execve("/system/bin/guest", args, args);
  *error = errno;
  return result;
}
int signals_setsid(void) { return setsid(); }

_Static_assert(SIG_BLOCK == 0 && SIG_UNBLOCK == 1 && SIG_SETMASK == 2,
               "bionic signal-mask operations");
int signals_mask(int which, int how, const sigset_t *set, sigset_t *old, int *error) {
  errno = 73;
  int result = which ? pthread_sigmask(how, set, old) : sigprocmask(how, set, old);
  *error = errno;
  return result;
}
int signals_pending(sigset_t *set, int *error) {
  errno = 73;
  int result = sigpending(set);
  *error = errno;
  return result;
}
int signals_raise(int number, int *error) {
  errno = 73;
  int result = raise(number);
  *error = errno;
  return result;
}

int signals_mask_in_handler(int *error) {
  errno = 73;
  mask_probe_result = 0;
  mask_probe_enabled = 1;
  int result = raise(SIGUSR1);
  mask_probe_enabled = 0;
  *error = errno;
  return result ? -1 : mask_probe_result;
}
