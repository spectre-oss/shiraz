// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <sched.h>
#include <sys/auxv.h>
#include <unistd.h>
// Compile these against pinned bionic headers. The native implementation's
// explicit numeric dispatch must never drift to Darwin's selector values.
_Static_assert(_SC_ARG_MAX == 0 && _SC_CLK_TCK == 6 && _SC_OPEN_MAX == 11, "bionic base selectors");
_Static_assert(_SC_IOV_MAX == 38 && _SC_PAGESIZE == 39 && _SC_PAGE_SIZE == 40, "bionic page/iov selectors");
_Static_assert(_SC_THREAD_DESTRUCTOR_ITERATIONS == 74 && _SC_THREAD_KEYS_MAX == 75 &&
               _SC_THREAD_STACK_MIN == 76 && _SC_THREAD_THREADS_MAX == 77, "bionic thread selectors");
_Static_assert(_SC_NPROCESSORS_CONF == 96 && _SC_NPROCESSORS_ONLN == 97 &&
               _SC_PHYS_PAGES == 98 && _SC_AVPHYS_PAGES == 99, "bionic CPU/memory selectors");
_Static_assert(PTHREAD_STACK_MIN == 16384 && SEM_VALUE_MAX == 0x3fffffff, "guest backing service limits");
_Static_assert(AT_PAGESZ == 6 && AT_UID == 11 && AT_EUID == 12 && AT_GID == 13 && AT_EGID == 14 &&
               AT_HWCAP == 16 && AT_CLKTCK == 17 && AT_SECURE == 23 && AT_RANDOM == 25 && AT_HWCAP2 == 26,
               "Linux auxiliary vector selectors");
_Static_assert(HWCAP_FP == 1 && HWCAP_ASIMD == 2 && HWCAP_ATOMICS == 256 && HWCAP_CPUID == 2048 &&
               HWCAP_SVE == (1u << 22) && HWCAP_PACA == (1u << 30) && HWCAP_PACG == (1ul << 31), "Linux HWCAP bits");
_Static_assert(HWCAP2_BTI == (1u << 17) && HWCAP2_MTE == (1u << 18) && HWCAP2_SME == (1u << 23), "Linux HWCAP2 bits");
long system_conf(int selector, int *error) {
  errno = 73;
  long value = sysconf(selector);
  *error = errno;
  return value;
}
unsigned long system_aux(unsigned long selector, int *error) {
  errno = 73;
  unsigned long value = getauxval(selector);
  *error = errno;
  return value;
}
int system_identifiers(unsigned long *output) {
  errno = 73;
  output[0] = (unsigned)getpid();
  output[1] = getuid();
  output[2] = geteuid();
  return errno;
}
int system_yield(void) { errno = 73; return sched_yield() ? -1 : errno; }
