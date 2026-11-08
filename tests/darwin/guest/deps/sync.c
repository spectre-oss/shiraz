// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#define CHECK(value) do { if (!(value)) return __LINE__; } while (0)
static pthread_mutex_t gate = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t ready_condition = PTHREAD_COND_INITIALIZER;
static pthread_cond_t go_condition = PTHREAD_COND_INITIALIZER;
static pthread_once_t once = PTHREAD_ONCE_INIT;
static int ready, go, counter, once_count, once_value, arrived;
static void initialize(void) {
  unsigned attempts = 100000000;
  while (__atomic_load_n(&arrived, __ATOMIC_ACQUIRE) != 4 && --attempts)
    __asm__ volatile("yield");
  ++once_count;
  once_value = attempts ? 42 : -1;
}
static void *worker(void *unused) {
  (void)unused;
  __atomic_fetch_add(&arrived, 1, __ATOMIC_RELEASE);
  if (pthread_once(&once, initialize) || once_value != 42) return (void *)1;
  if (pthread_mutex_lock(&gate)) return (void *)2;
  ++ready;
  pthread_cond_signal(&ready_condition);
  while (!go)
    if (pthread_cond_wait(&go_condition, &gate)) return (void *)3;
  pthread_mutex_unlock(&gate);
  for (int i = 0; i < 1000; ++i) {
    if (pthread_mutex_lock(&gate)) return (void *)4;
    ++counter;
    if (pthread_mutex_unlock(&gate)) return (void *)5;
  }
  return 0;
}
int run_sync(void) {
  errno = EDOM;
  pthread_t threads[4];
  for (int i = 0; i < 4; ++i) CHECK(pthread_create(&threads[i], 0, worker, 0) == 0);
  CHECK(pthread_mutex_lock(&gate) == 0);
  struct timespec deadline;
  CHECK(clock_gettime(CLOCK_REALTIME, &deadline) == 0);
  deadline.tv_sec += 5;
  while (ready != 4) CHECK(pthread_cond_timedwait(&ready_condition, &gate, &deadline) == 0);
  CHECK(pthread_cond_destroy(&go_condition) == EBUSY);
  CHECK(pthread_mutex_destroy(&gate) == EBUSY);
  go = 1;
  CHECK(pthread_cond_broadcast(&go_condition) == 0 && pthread_mutex_unlock(&gate) == 0);
  for (int i = 0; i < 4; ++i) {
    void *result = (void *)1;
    CHECK(pthread_join(threads[i], &result) == 0 && !result);
  }
  CHECK(counter == 4000 && once_count == 1 && once_value == 42 && errno == EDOM);
  CHECK(pthread_once(&once, initialize) == 0 && once_count == 1);
  struct { uint64_t before; pthread_mutex_t value; uint64_t after; } mutex;
  struct { uint64_t before; pthread_mutexattr_t value; uint64_t after; } mattr;
  struct { uint64_t before; pthread_cond_t value; uint64_t after; } cond;
  struct { uint64_t before; pthread_condattr_t value; uint64_t after; } cattr;
  memset(&mutex, 0xa5, sizeof(mutex)); memset(&mattr, 0xa5, sizeof(mattr));
  memset(&cond, 0xa5, sizeof(cond)); memset(&cattr, 0xa5, sizeof(cattr));
  CHECK(pthread_mutexattr_init(&mattr.value) == 0);
  CHECK(pthread_mutexattr_settype(&mattr.value, PTHREAD_MUTEX_ERRORCHECK) == 0);
  int type;
  CHECK(pthread_mutexattr_gettype(&mattr.value, &type) == 0 && type == PTHREAD_MUTEX_ERRORCHECK);
  CHECK(pthread_mutexattr_setpshared(&mattr.value, PTHREAD_PROCESS_SHARED) == ENOTSUP);
  CHECK(pthread_mutexattr_setpshared(&mattr.value, PTHREAD_PROCESS_PRIVATE) == 0);
  CHECK(pthread_mutex_init(&mutex.value, &mattr.value) == 0);
  CHECK(pthread_mutex_lock(&mutex.value) == 0 && pthread_mutex_lock(&mutex.value) == EDEADLK &&
        pthread_mutex_trylock(&mutex.value) == EBUSY);
  CHECK(pthread_mutex_destroy(&mutex.value) == EBUSY);
  CHECK(pthread_condattr_init(&cattr.value) == 0);
  CHECK(pthread_condattr_setclock(&cattr.value, CLOCK_MONOTONIC) == 0);
  clockid_t clock;
  CHECK(pthread_condattr_getclock(&cattr.value, &clock) == 0 && clock == CLOCK_MONOTONIC);
  CHECK(pthread_condattr_setpshared(&cattr.value, PTHREAD_PROCESS_SHARED) == ENOTSUP);
  CHECK(pthread_cond_init(&cond.value, &cattr.value) == 0);
  struct { uint64_t before; struct timespec value; uint64_t after; } stamp;
  memset(&stamp, 0xa5, sizeof(stamp));
  CHECK(clock_gettime(CLOCK_MONOTONIC, &stamp.value) == 0);
  CHECK(stamp.before == 0xa5a5a5a5a5a5a5a5ULL && stamp.after == 0xa5a5a5a5a5a5a5a5ULL);
  deadline = stamp.value;
  deadline.tv_nsec += 20000000;
  if (deadline.tv_nsec >= 1000000000) { ++deadline.tv_sec; deadline.tv_nsec -= 1000000000; }
  CHECK(pthread_cond_timedwait(&cond.value, &mutex.value, &deadline) == ETIMEDOUT);
  struct timespec end;
  CHECK(clock_gettime(CLOCK_MONOTONIC, &end) == 0);
  CHECK((end.tv_sec - stamp.value.tv_sec) * 1000000000LL + end.tv_nsec - stamp.value.tv_nsec >= 10000000);
  CHECK(pthread_mutex_unlock(&mutex.value) == 0 && pthread_mutex_unlock(&mutex.value) == EPERM);
  CHECK(pthread_mutex_lock(&mutex.value) == 0);
  deadline.tv_sec = -1; deadline.tv_nsec = 0;
  CHECK(pthread_cond_timedwait(&cond.value, &mutex.value, &deadline) == ETIMEDOUT);
  pthread_cond_t static_monotonic = PTHREAD_COND_INITIALIZER_MONOTONIC_NP;
  CHECK(pthread_cond_timedwait(&static_monotonic, &mutex.value, &deadline) == ETIMEDOUT &&
        pthread_cond_destroy(&static_monotonic) == 0);
  deadline.tv_nsec = 1000000000;
  CHECK(pthread_cond_timedwait(&cond.value, &mutex.value, &deadline) == EINVAL);
  CHECK(pthread_mutex_unlock(&mutex.value) == 0);
  CHECK(pthread_cond_destroy(&cond.value) == 0 && pthread_cond_signal(&cond.value) == EINVAL);
  CHECK(pthread_mutex_destroy(&mutex.value) == 0 && pthread_mutex_lock(&mutex.value) == EINVAL);
  CHECK(pthread_mutex_init(&mutex.value, 0) == 0 && pthread_mutex_destroy(&mutex.value) == 0);
  CHECK(pthread_mutexattr_destroy(&mattr.value) == 0 && pthread_condattr_destroy(&cattr.value) == 0);
#define GUARDS(v) ((v).before == 0xa5a5a5a5a5a5a5a5ULL && (v).after == 0xa5a5a5a5a5a5a5a5ULL)
  CHECK(GUARDS(mutex) && GUARDS(mattr) && GUARDS(cond) && GUARDS(cattr));
  pthread_mutex_t recursive = PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP;
  CHECK(pthread_mutex_lock(&recursive) == 0 && pthread_mutex_lock(&recursive) == 0 &&
        pthread_mutex_unlock(&recursive) == 0 && pthread_mutex_unlock(&recursive) == 0 &&
        pthread_mutex_destroy(&recursive) == 0);
  pthread_mutex_t errorcheck = PTHREAD_ERRORCHECK_MUTEX_INITIALIZER_NP;
  CHECK(pthread_mutex_lock(&errorcheck) == 0 && pthread_mutex_lock(&errorcheck) == EDEADLK &&
        pthread_mutex_unlock(&errorcheck) == 0 && pthread_mutex_destroy(&errorcheck) == 0);
  CHECK(pthread_mutex_lock(&gate) == 0);
  deadline.tv_sec = -1; deadline.tv_nsec = 0;
  CHECK(pthread_cond_timedwait(&ready_condition, &gate, &deadline) == ETIMEDOUT &&
        pthread_mutex_unlock(&gate) == 0);
  CHECK(pthread_cond_destroy(&ready_condition) == 0 && pthread_cond_destroy(&go_condition) == 0 &&
        pthread_mutex_destroy(&gate) == 0 && errno == EDOM);
  return 0;
}
