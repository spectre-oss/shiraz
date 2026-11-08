// SPDX-License-Identifier: GPL-3.0-only
// This fixture emits inline atomics, without a compiler-rt helper dependency.
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <stdint.h>
#include <string.h>
#define CHECK(value) do { if (!(value)) return __LINE__; } while (0)
static pthread_key_t key;
static int destroyed;
struct Work { int index, error, destructors; pthread_t self; };
static void destructor(void *opaque) {
  struct Work *work = opaque;
  if (pthread_getspecific(key)) work->error = 1;
  if (++work->destructors == 1) {
    if (pthread_setspecific(key, work)) work->error = 2;
  } else __atomic_fetch_add(&destroyed, 1, __ATOMIC_RELEASE);
}
static void *worker(void *opaque) {
  struct Work *work = opaque;
  work->self = pthread_self();
  errno = 700 + work->index;
  if (pthread_join(work->self, 0) != EDEADLK) work->error = 3;
  if (pthread_getspecific(key) || pthread_setspecific(key, work) ||
      pthread_getspecific(key) != work || errno != 700 + work->index)
    work->error = 4;
  return work;
}
int run_threads(void) {
  struct { uint64_t before; pthread_attr_t value; uint64_t after; } attribute;
  memset(&attribute, 0xa5, sizeof(attribute));
  errno = EDOM;
  CHECK(pthread_attr_init(&attribute.value) == 0);
  CHECK(attribute.before == 0xa5a5a5a5a5a5a5a5ULL &&
        attribute.after == 0xa5a5a5a5a5a5a5a5ULL);
  int state = -1;
  CHECK(pthread_attr_getdetachstate(&attribute.value, &state) == 0 &&
        state == PTHREAD_CREATE_JOINABLE);
  CHECK(pthread_attr_setdetachstate(&attribute.value, 123) == EINVAL && errno == EDOM);
  CHECK(pthread_attr_setstacksize(&attribute.value, 4096) == EINVAL);
  CHECK(pthread_attr_setstacksize(&attribute.value, 2 * 1024 * 1024) == 0);
  size_t size = 0;
  CHECK(pthread_attr_getstacksize(&attribute.value, &size) == 0 && size == 2 * 1024 * 1024);
  CHECK(pthread_attr_setguardsize(&attribute.value, 32768) == 0 &&
        pthread_attr_getguardsize(&attribute.value, &size) == 0 && size == 32768);
  struct { uint32_t before; pthread_key_t value; uint32_t after; } guarded;
  memset(&guarded, 0xa5, sizeof(guarded));
  CHECK(pthread_key_create(&guarded.value, destructor) == 0 &&
        guarded.before == 0xa5a5a5a5 && guarded.after == 0xa5a5a5a5);
  key = guarded.value;
  struct Work work[4] = {{0}};
  pthread_t threads[4];
  for (int i = 0; i < 4; ++i) {
    work[i].index = i;
    CHECK(pthread_create(&threads[i], &attribute.value, worker, &work[i]) == 0);
  }
  CHECK(pthread_getspecific(key) == 0 && errno == EDOM);
  for (int i = 0; i < 4; ++i) {
    void *result = 0;
    CHECK(pthread_join(threads[i], &result) == 0 && result == &work[i]);
    CHECK(pthread_equal(work[i].self, threads[i]) && work[i].error == 0 &&
          work[i].destructors == 2);
  }
  CHECK(__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) == 4);
  CHECK(pthread_attr_setdetachstate(&attribute.value, PTHREAD_CREATE_DETACHED) == 0 &&
        pthread_attr_getdetachstate(&attribute.value, &state) == 0 &&
        state == PTHREAD_CREATE_DETACHED);
  struct Work detached = {5, 0, 0, 0};
  pthread_t detached_id;
  CHECK(pthread_create(&detached_id, &attribute.value, worker, &detached) == 0);
  unsigned remaining = 100000000;
  while (__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) < 5 && --remaining)
    __asm__ volatile("yield");
  CHECK(remaining && detached.error == 0 && detached.destructors == 2);
  // Convert a joinable thread to detached and wait for its key destructor.
  struct Work converted = {6, 0, 0, 0};
  CHECK(pthread_create(&detached_id, 0, worker, &converted) == 0 &&
        pthread_detach(detached_id) == 0);
  remaining = 100000000;
  while (__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) < 6 && --remaining)
    __asm__ volatile("yield");
  CHECK(remaining && converted.error == 0 && converted.destructors == 2);
  CHECK(pthread_attr_destroy(&attribute.value) == 0);
  CHECK(attribute.before == 0xa5a5a5a5a5a5a5a5ULL &&
        attribute.after == 0xa5a5a5a5a5a5a5a5ULL);
  CHECK(pthread_key_delete(key) == 0 && pthread_key_delete(key) == EINVAL);
  CHECK(pthread_setspecific(key, work) == EINVAL && pthread_getspecific(key) == 0 && errno == EDOM);
  CHECK(pthread_create(&threads[0], 0, (void *(*)(void *))1, 0) == EINVAL);
  CHECK(pthread_join((pthread_t)1, 0) == ESRCH);
  return 0;
}

// Key iteration is guest-owned: no test depends on an order between keys.
// A reset on the final permitted pass must not cause a fifth callback.
static pthread_key_t repeat_key, deleted_key, self_deleted_key;
static int deleted_callbacks;
struct KeyWork {
  int error, repeat_calls, child_calls, self_deleted_calls;
  pthread_key_t child_key;
};
static void child_key_destructor(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_getspecific(work->child_key)) work->error = 101;
  ++work->child_calls;
}
static void repeat_key_destructor(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_getspecific(repeat_key)) work->error = 102;
  if (++work->repeat_calls == 1) {
    if (pthread_key_create(&work->child_key, child_key_destructor) ||
        pthread_setspecific(work->child_key, work)) work->error = 103;
  }
  if (pthread_setspecific(repeat_key, work)) work->error = 104;
}
static void deleted_key_destructor(void *opaque) {
  (void)opaque;
  __atomic_add_fetch(&deleted_callbacks, 1, __ATOMIC_RELAXED);
}
static void self_deleted_key_destructor(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_getspecific(self_deleted_key) || pthread_key_delete(self_deleted_key) ||
      pthread_setspecific(self_deleted_key, work) != EINVAL ||
      pthread_getspecific(self_deleted_key)) work->error = 105;
  ++work->self_deleted_calls;
}
static void *key_worker(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_getspecific(repeat_key) || pthread_setspecific(repeat_key, work) ||
      pthread_getspecific(repeat_key) != work) work->error = 106;
  return work;
}
static void *deleted_key_worker(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_setspecific(deleted_key, work) || pthread_key_delete(deleted_key) ||
      pthread_getspecific(deleted_key) || pthread_setspecific(deleted_key, work) != EINVAL)
    work->error = 107;
  return work;
}
static void *self_deleted_key_worker(void *opaque) {
  struct KeyWork *work = opaque;
  if (pthread_setspecific(self_deleted_key, work)) work->error = 108;
  return work;
}
int run_key_lifecycle(void) {
  CHECK(PTHREAD_DESTRUCTOR_ITERATIONS == 4);
  CHECK(pthread_key_create(&repeat_key, repeat_key_destructor) == 0);
  struct KeyWork work[4] = {{0}};
  pthread_t threads[4];
  for (int i = 0; i < 4; ++i)
    CHECK(pthread_create(&threads[i], 0, key_worker, &work[i]) == 0);
  CHECK(pthread_getspecific(repeat_key) == 0);
  for (int i = 0; i < 4; ++i) {
    void *returned = 0;
    CHECK(pthread_join(threads[i], &returned) == 0 && returned == &work[i]);
    CHECK(work[i].error == 0 && work[i].repeat_calls == PTHREAD_DESTRUCTOR_ITERATIONS &&
          work[i].child_calls == 1);
    CHECK(pthread_key_delete(work[i].child_key) == 0);
  }
  CHECK(pthread_key_delete(repeat_key) == 0);
  struct KeyWork deleted = {0};
  CHECK(pthread_key_create(&deleted_key, deleted_key_destructor) == 0);
  CHECK(pthread_create(&threads[0], 0, deleted_key_worker, &deleted) == 0 &&
        pthread_join(threads[0], 0) == 0);
  CHECK(deleted.error == 0 && __atomic_load_n(&deleted_callbacks, __ATOMIC_RELAXED) == 0);
  struct KeyWork self_deleted = {0};
  CHECK(pthread_key_create(&self_deleted_key, self_deleted_key_destructor) == 0);
  CHECK(pthread_create(&threads[0], 0, self_deleted_key_worker, &self_deleted) == 0 &&
        pthread_join(threads[0], 0) == 0);
  CHECK(self_deleted.error == 0 && self_deleted.self_deleted_calls == 1);
  CHECK(pthread_key_delete(self_deleted_key) == EINVAL);
  return 0;
}

// Two independent guest registries may issue identical numeric keys. Values
// belong to their key identity as well as the current host thread.
static pthread_key_t namespace_key;
int key_namespace_create(void) { return pthread_key_create(&namespace_key, 0); }
int key_namespace_set(void *value) { return pthread_setspecific(namespace_key, value); }
void *key_namespace_get(void) { return pthread_getspecific(namespace_key); }
int key_namespace_delete(void) { return pthread_key_delete(namespace_key); }
