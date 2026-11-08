// SPDX-License-Identifier: GPL-3.0-only
// One unchanged AArch64 JNI ELF runs on actual bionic and Darwin NativeBridge.
// Compiler-rt emutls is linked unchanged into this fixture; native libc services
// resolve at runtime. Trap-only link declarations are never shipped with it.
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/auxv.h>
#include <unistd.h>

extern int __cxa_thread_atexit_impl(void (*)(void *), void *, void *);
extern void differential_corrupt_guard(void);
void *__dso_handle = &__dso_handle;

#define CHECK(condition) do { if (!(condition)) return __LINE__; } while (0)

static __thread int initialized = 1234;
static __thread int zero_value;
static __thread int phase;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t condition = PTHREAD_COND_INITIALIZER;
static unsigned ready;
struct ThreadCase {
  unsigned id, events, trace;
  int error;
  uintptr_t address;
};
static struct ThreadCase threads[2];

static void record(struct ThreadCase *item, unsigned digit, int valid) {
  item->events++;
  item->trace = item->trace * 10 + digit;
  if (!valid) item->error = 1;
}
static void first_destructor(void *opaque) {
  struct ThreadCase *item = opaque;
  record(item, 1, phase == 2 && initialized == 1235 + (int)item->id &&
                       zero_value == 7 + (int)item->id);
  phase = 3;
}
static void nested_destructor(void *opaque) {
  struct ThreadCase *item = opaque;
  record(item, 3, phase == 1 && initialized == 1235 + (int)item->id);
  phase = 2;
}
static void second_destructor(void *opaque) {
  struct ThreadCase *item = opaque;
  record(item, 2, phase == 0 && zero_value == 7 + (int)item->id);
  phase = 1;
  if (__cxa_thread_atexit_impl(nested_destructor, item, __dso_handle))
    item->error = 2;
}
static void *worker(void *opaque) {
  struct ThreadCase *item = opaque;
  if (initialized != 1234 || zero_value || phase) item->error = 3;
  initialized += 1 + (int)item->id;
  zero_value = 7 + (int)item->id;
  item->address = (uintptr_t)&initialized;
  if (__cxa_thread_atexit_impl(first_destructor, item, __dso_handle) ||
      __cxa_thread_atexit_impl(second_destructor, item, __dso_handle))
    item->error = 4;
  if (pthread_mutex_lock(&mutex)) { item->error = 5; return 0; }
  ++ready;
  if (ready == 2 && pthread_cond_broadcast(&condition)) item->error = 6;
  while (ready != 2)
    if (pthread_cond_wait(&condition, &mutex)) { item->error = 7; break; }
  if (initialized != 1235 + (int)item->id || zero_value != 7 + (int)item->id)
    item->error = 8;
  if (pthread_mutex_unlock(&mutex)) item->error = 9;
  return 0;
}
static int tls_checks(void) {
  pthread_t native_threads[2];
  CHECK(initialized == 1234 && zero_value == 0 && phase == 0);
  memset(threads, 0, sizeof(threads));
  ready = 0;
  for (unsigned i = 0; i < 2; ++i) {
    threads[i].id = i;
    CHECK(pthread_create(&native_threads[i], 0, worker, &threads[i]) == 0);
  }
  for (unsigned i = 0; i < 2; ++i) {
    CHECK(pthread_join(native_threads[i], 0) == 0);
    CHECK(!threads[i].error && threads[i].events == 3 && threads[i].trace == 231);
  }
  CHECK(threads[0].address && threads[1].address &&
        threads[0].address != threads[1].address &&
        threads[0].address != (uintptr_t)&initialized &&
        threads[1].address != (uintptr_t)&initialized);
  CHECK(initialized == 1234 && zero_value == 0 && phase == 0);
  return 0;
}

__attribute__((noinline)) static uintptr_t stack_guard(void) {
  uintptr_t base;
  __asm__ volatile("mrs %0, tpidr_el0" : "=r"(base));
  return *(volatile uintptr_t *)(base + 5 * sizeof(uintptr_t));
}
static int guard_checks(void) {
  uintptr_t first = stack_guard();
  CHECK(first && stack_guard() == first);
  return 0;
}

static unsigned long observed_hwcap, observed_hwcap2, observed_page_size;
static int cpu_checks(void) {
  errno = 73;
  observed_hwcap = getauxval(AT_HWCAP);
  CHECK((observed_hwcap & (HWCAP_FP | HWCAP_ASIMD)) == (HWCAP_FP | HWCAP_ASIMD));
  CHECK(getauxval(AT_HWCAP) == observed_hwcap && errno == 73);
  uint64_t lanes[2] = {0, 0};
  __asm__ volatile("movi v0.4s, #7\n"
                   "movi v1.4s, #9\n"
                   "add v0.4s, v0.4s, v1.4s\n"
                   "str q0, [%0]" : : "r"(lanes) : "v0", "v1", "memory");
  CHECK(lanes[0] == UINT64_C(0x0000001000000010) && lanes[1] == lanes[0]);

  observed_hwcap2 = getauxval(AT_HWCAP2);
  CHECK(getauxval(AT_HWCAP2) == observed_hwcap2 && errno == 73);
  observed_page_size = getauxval(AT_PAGESZ);
  CHECK(observed_page_size >= 4096 && !(observed_page_size & (observed_page_size - 1)));
  CHECK((unsigned long)sysconf(_SC_PAGESIZE) == observed_page_size);
  CHECK(getauxval(AT_UID) == getuid() && getauxval(AT_EUID) == geteuid());
  CHECK(getauxval(AT_CLKTCK) > 0);
  errno = 73;
  CHECK(getauxval(~0ul) == 0 && errno == ENOENT);
  return 0;
}

static volatile sig_atomic_t signal_number, signal_calls, signal_errno;
__attribute__((no_stack_protector)) static void signal_handler(int number) {
  int saved = errno;
  signal_errno = saved;
  signal_number = number;
  signal_calls++;
  errno = EINTR;
  // POSIX handlers themselves preserve errno when they need that guarantee.
  errno = saved;
}
static int signal_checks(void) {
  struct sigaction action = {0}, original_action;
  sigset_t original_mask, blocked, pending, after;
  int failure = 0, installed = 0, have_mask = 0;
#define VERIFY(condition) do { if (!(condition)) { failure = __LINE__; goto cleanup; } } while (0)
  signal_number = signal_calls = signal_errno = 0;
  VERIFY(sigemptyset(&action.sa_mask) == 0);
  action.sa_handler = signal_handler;
  action.sa_flags = SA_RESTART;
  VERIFY(sigaction(SIGUSR2, &action, &original_action) == 0);
  installed = 1;
  VERIFY(pthread_sigmask(SIG_SETMASK, 0, &original_mask) == 0);
  have_mask = 1;
  VERIFY(sigemptyset(&blocked) == 0 && sigaddset(&blocked, SIGUSR2) == 0);
  errno = 73;
  VERIFY(pthread_sigmask(SIG_BLOCK, &blocked, 0) == 0 && errno == 73);
  VERIFY(raise(SIGUSR2) == 0 && errno == 73 && signal_calls == 0);
  VERIFY(sigpending(&pending) == 0 && sigismember(&pending, SIGUSR2) == 1);
  // Force only our owned signal unblocked, regardless of ART's initial mask.
  VERIFY(pthread_sigmask(SIG_UNBLOCK, &blocked, 0) == 0);
  VERIFY(signal_calls == 1 && signal_number == SIGUSR2 && signal_errno == 73 && errno == 73);
  VERIFY(pthread_sigmask(SIG_SETMASK, &original_mask, 0) == 0);
  VERIFY(pthread_sigmask(SIG_SETMASK, 0, &after) == 0);
  VERIFY(sigismember(&after, SIGUSR2) == sigismember(&original_mask, SIGUSR2));
cleanup:
  if (have_mask && pthread_sigmask(SIG_SETMASK, &original_mask, 0) && !failure)
    failure = __LINE__;
  if (installed && sigaction(SIGUSR2, &original_action, 0) && !failure)
    failure = __LINE__;
#undef VERIFY
  return failure;
}

static int format_checks(void) {
  char output[192];
  const char *expected = "-7/-4294967301/99/2a/1.25/abc/Z/0x1234";
  CHECK(snprintf(output, sizeof(output), "%d/%lld/%zu/%x/%.2f/%.*s/%c/%p",
                 -7, -4294967301LL, (size_t)99, 42u, 1.25, 3, "abcdef", 'Z',
                 (void *)(uintptr_t)0x1234) == (int)strlen(expected));
  CHECK(!strcmp(output, expected));
  expected = "12345678910:1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0";
  CHECK(snprintf(output, sizeof(output), "%d%d%d%d%d%d%d%d%d%d:%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                 1,2,3,4,5,6,7,8,9,10, 1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0) == (int)strlen(expected));
  CHECK(!strcmp(output, expected));
  char short_output[5] = {'?', '?', '?', '?', '?'};
  CHECK(snprintf(short_output, sizeof(short_output), "%s:%d", "abcdef", 42) == 9);
  CHECK(!strcmp(short_output, "abcd"));
  CHECK(snprintf(0, 0, "%s/%d", "abc", 17) == 6);
  char untouched = 'x';
  CHECK(snprintf(&untouched, 0, "abc") == 3 && untouched == 'x');
  CHECK(snprintf(&untouched, 1, "abc") == 3 && untouched == 0);
  CHECK(snprintf(output, sizeof(output), "%.3s", "abcdef") == 3 && !strcmp(output, "abc"));
  CHECK(snprintf(output, sizeof(output), "%*.*s", 6, 3, "abcdef") == 6 && !strcmp(output, "   abc"));
  CHECK(snprintf(output, sizeof(output), "a%cb", 0) == 3 && output[0] == 'a' && output[1] == 0 && output[2] == 'b' && output[3] == 0);
  return 0;
}

JNIEXPORT jstring Java_BionicDifferentialFixture_nRun(JNIEnv *env, jclass klass) {
  (void)klass;
  static const char *names[] = {"tls", "guard", "cpu", "signals", "format"};
  int (*checks[])(void) = {tls_checks, guard_checks, cpu_checks, signal_checks, format_checks};
  char report[768];
  for (unsigned index = 0; index < sizeof(checks) / sizeof(checks[0]); ++index) {
    int result = checks[index]();
    if (result) {
      snprintf(report, sizeof(report), "{\"schema\":1,\"status\":\"fail\",\"case\":\"%s\",\"line\":%d}", names[index], result);
      return (*env)->NewStringUTF(env, report);
    }
  }
  snprintf(report, sizeof(report),
           "{\"schema\":1,\"status\":\"pass\",\"contracts\":{\"tls_threads\":2,\"tls_destructors\":6,"
           "\"tls_isolation\":true,\"tls_nested_lifo\":true,\"guard_nonzero\":true,\"cpu_semantics\":true,"
           "\"signal_delivery\":true,\"signal_mask_pending\":true,\"signal_errno_preserved\":true,\"format_cases\":9},"
           "\"observations\":{\"hwcap\":\"0x%lx\",\"hwcap2\":\"0x%lx\",\"page_size\":%lu}}",
           observed_hwcap, observed_hwcap2, observed_page_size);
  return (*env)->NewStringUTF(env, report);
}
JNIEXPORT void Java_BionicDifferentialFixture_nCorruptGuard(JNIEnv *env, jclass klass) {
  (void)env;
  (void)klass;
  differential_corrupt_guard();
}
