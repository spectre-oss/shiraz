// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <semaphore.h>
#include <stdint.h>
#include <string.h>
#define CHECK(value) do { if (!(value)) return __LINE__; } while (0)
static pthread_rwlock_t rw = PTHREAD_RWLOCK_INITIALIZER;
static sem_t ready, release_reader, start;
static int counter, mirror;
static void *reader(void *unused) {
  (void)unused;
  if (pthread_rwlock_rdlock(&rw) || sem_post(&ready) || sem_wait(&release_reader) ||
      pthread_rwlock_unlock(&rw)) return (void *)1;
  return 0;
}
static void *try_reader(void *unused) {
  (void)unused;
  return pthread_rwlock_tryrdlock(&rw) == EBUSY ? 0 : (void *)1;
}
static void *worker(void *opaque) {
  uintptr_t index = (uintptr_t)opaque;
  if (sem_wait(&start)) return (void *)1;
  for (int i = 0; i < 1000; ++i) {
    if (index < 2) {
      if (pthread_rwlock_wrlock(&rw)) return (void *)2;
      ++counter; mirror = counter * 3;
    } else {
      if (pthread_rwlock_rdlock(&rw)) return (void *)3;
      if (mirror != counter * 3) return (void *)4;
    }
    if (pthread_rwlock_unlock(&rw)) return (void *)5;
  }
  return 0;
}
int run_rwsem(void) {
  errno = EDOM;
  struct { uint64_t before; sem_t value; uint64_t after; } guarded;
  memset(&guarded, 0xa5, sizeof(guarded));
  CHECK(sem_init(&guarded.value, 0, SEM_VALUE_MAX) == 0);
  int count = -1;
  CHECK(sem_getvalue(&guarded.value, &count) == 0 && count == SEM_VALUE_MAX);
  CHECK(sem_post(&guarded.value) == -1 && errno == EOVERFLOW);
  CHECK(sem_trywait(&guarded.value) == 0 && sem_post(&guarded.value) == 0 &&
        sem_destroy(&guarded.value) == 0);
  CHECK(guarded.before == 0xa5a5a5a5a5a5a5a5ULL && guarded.after == 0xa5a5a5a5a5a5a5a5ULL);
  CHECK(sem_wait(&guarded.value) == -1 && errno == EINVAL);
  CHECK(sem_init(&guarded.value, 0, UINT32_MAX) == -1 && errno == EINVAL);
  CHECK(sem_init(&guarded.value, 1, 0) == -1 && errno == ENOTSUP);
  struct { uint32_t before; sem_t value; uint32_t after; } aligned4;
  memset(&aligned4, 0xa5, sizeof(aligned4));
  for (int i = 0; i < 128; ++i)
    CHECK(sem_init(&aligned4.value, 0, 1) == 0 && sem_wait(&aligned4.value) == 0 &&
          sem_trywait(&aligned4.value) == -1 && errno == EAGAIN &&
          sem_destroy(&aligned4.value) == 0);
  CHECK(aligned4.before == 0xa5a5a5a5 && aligned4.after == 0xa5a5a5a5);
  CHECK(sem_init(&ready, 0, 0) == 0 && sem_init(&release_reader, 0, 0) == 0 &&
        sem_init(&start, 0, 0) == 0);
  pthread_t thread;
  void *result = (void *)1;
  CHECK(pthread_rwlock_rdlock(&rw) == 0 && pthread_rwlock_rdlock(&rw) == 0);
  CHECK(pthread_create(&thread, 0, reader, 0) == 0 && sem_wait(&ready) == 0);
  CHECK(pthread_rwlock_trywrlock(&rw) == EBUSY && pthread_rwlock_destroy(&rw) == EBUSY);
  CHECK(sem_post(&release_reader) == 0 && pthread_join(thread, &result) == 0 && !result);
  CHECK(pthread_rwlock_unlock(&rw) == 0 && pthread_rwlock_unlock(&rw) == 0);
  CHECK(pthread_rwlock_wrlock(&rw) == 0 && pthread_create(&thread, 0, try_reader, 0) == 0 &&
        pthread_join(thread, &result) == 0 && !result && pthread_rwlock_unlock(&rw) == 0);
  pthread_t threads[4];
  for (uintptr_t i = 0; i < 4; ++i) CHECK(pthread_create(&threads[i], 0, worker, (void *)i) == 0);
  for (int i = 0; i < 40000; ++i) CHECK(sem_post(&start) == 0);
  for (int i = 0; i < 4; ++i) CHECK(pthread_join(threads[i], &result) == 0 && !result);
  CHECK(counter == 2000 && mirror == 6000 && sem_getvalue(&start, &count) == 0 && count == 39996);
  CHECK(sem_destroy(&ready) == 0 && sem_destroy(&release_reader) == 0 && sem_destroy(&start) == 0);
  CHECK(pthread_rwlock_destroy(&rw) == 0 && pthread_rwlock_rdlock(&rw) == EINVAL);
  struct { uint64_t before; pthread_rwlock_t value; uint64_t after; } dynamic;
  memset(&dynamic, 0xa5, sizeof(dynamic));
  CHECK(pthread_rwlock_init(&dynamic.value, 0) == 0 && pthread_rwlock_wrlock(&dynamic.value) == 0 &&
        pthread_rwlock_unlock(&dynamic.value) == 0 && pthread_rwlock_destroy(&dynamic.value) == 0);
  CHECK(dynamic.before == 0xa5a5a5a5a5a5a5a5ULL && dynamic.after == 0xa5a5a5a5a5a5a5a5ULL);
  return 0;
}
