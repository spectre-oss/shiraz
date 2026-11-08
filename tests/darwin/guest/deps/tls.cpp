// SPDX-License-Identifier: GPL-3.0-only
// Genuine compiler-emulated TLS. The unchanged upstream runtime is another ELF.
#include <pthread.h>
#include <stdint.h>
extern "C" {
int __cxa_thread_atexit_impl(void (*)(void *), void *, void *);
void *__dso_handle = &__dso_handle;
int __cxa_thread_atexit(void (*function)(void *), void *object, void *dso) {
  return __cxa_thread_atexit_impl(function, object, dso);
}
extern __thread int cross_module_tls;
int peer_tls_value();
}
static __thread int initialized = 1234, zero, phase;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t condition = PTHREAD_COND_INITIALIZER;
static int ready, finished, events, errors;
static uintptr_t addresses[4][3];
static void record(bool valid) {
  pthread_mutex_lock(&mutex);
  if (!valid) ++errors;
  ++events;
  pthread_mutex_unlock(&mutex);
}
struct Holder {
  int id;
  Holder() : id(-1) {}
  ~Holder();
};
alignas(128) static thread_local Holder first;
static thread_local Holder second;
static void nested_destructor(void *opaque) {
  int id = int(uintptr_t(opaque));
  record(phase == 1 && initialized == 1234 + id && cross_module_tls == 41 + id);
  phase = 2;
}
Holder::~Holder() {
  if (this == &second) {
    record(phase == 0 && zero == id + 7);
    phase = 1;
    if (__cxa_thread_atexit_impl(nested_destructor, (void *)uintptr_t(id), __dso_handle))
      record(false);
  } else {
    record(this == &first && phase == 2 && initialized == 1234 + id &&
           zero == id + 7 && peer_tls_value() == 41 + id);
    pthread_mutex_lock(&mutex);
    ++finished;
    pthread_mutex_unlock(&mutex);
  }
}
extern "C" int tls_exercise(int id) {
  if (id < 0 || id >= 4 || initialized != 1234 || zero || phase || peer_tls_value() != 41)
    return 1;
  first.id = id; second.id = id;
  if (uintptr_t(&first) % 128) return 2;
  initialized += id; zero = id + 7; cross_module_tls += id;
  pthread_mutex_lock(&mutex);
  addresses[id][0] = uintptr_t(&initialized);
  addresses[id][1] = uintptr_t(&first);
  addresses[id][2] = uintptr_t(&cross_module_tls);
  if (++ready == 4) pthread_cond_broadcast(&condition);
  while (ready != 4) pthread_cond_wait(&condition, &mutex);
  pthread_mutex_unlock(&mutex);
  return initialized == 1234 + id && zero == id + 7 && peer_tls_value() == 41 + id ? 0 : 3;
}
extern "C" int tls_summary() {
  if (ready != 4 || finished != 4 || events != 12 || errors) return 1;
  for (int i = 0; i < 4; ++i)
    for (int j = 0; j < i; ++j)
      for (int field = 0; field < 3; ++field)
        if (!addresses[i][field] || addresses[i][field] == addresses[j][field]) return 2;
  return 0;
}
static void *worker(void *opaque) { return (void *)uintptr_t(tls_exercise(int(uintptr_t(opaque)))); }
extern "C" int tls_guest_threads() {
  ready = finished = events = errors = 0;
  pthread_t threads[4];
  for (uintptr_t i = 0; i < 4; ++i)
    if (pthread_create(&threads[i], nullptr, worker, (void *)i)) return 1;
  for (int i = 0; i < 4; ++i) {
    void *result = (void *)1;
    if (pthread_join(threads[i], &result) || result) return 2;
  }
  return tls_summary();
}

// Late native cleanup callbacks may register more guest cleanup. These exact
// counts must be visible after native join, including final native TSD rounds.
static int late_events;
static void tls_late_nested(void *) {
  __atomic_add_fetch(&late_events, 1, __ATOMIC_SEQ_CST);
}
extern "C" void tls_late_destructor(void *) {
  __atomic_add_fetch(&late_events, 1, __ATOMIC_SEQ_CST);
  if (__cxa_thread_atexit_impl(tls_late_nested, nullptr, __dso_handle))
    __atomic_add_fetch(&late_events, 100, __ATOMIC_SEQ_CST);
}
extern "C" int tls_late_summary(int expected) {
  int actual = __atomic_load_n(&late_events, __ATOMIC_SEQ_CST);
  return actual == expected ? 0 : 1000 + actual;
}

// This storage is first touched by a fresh native worker only in its final
// native TSD callback. Function-local TLS avoids initializing the ordinary
// Holder pair as a side effect of this independently tested lifetime.
struct alignas(128) LateFirstStorage {
  uint64_t templated, zero;
};
static LateFirstStorage &late_first_storage() {
  static thread_local LateFirstStorage value = {0x123456789abcdef0ULL, 0};
  return value;
}
static int late_first_events, late_first_errors, late_first_uses;
static uintptr_t late_first_address;
static void late_first_record(bool valid) {
  if (!valid) __atomic_add_fetch(&late_first_errors, 1, __ATOMIC_SEQ_CST);
  __atomic_add_fetch(&late_first_events, 1, __ATOMIC_SEQ_CST);
}
static bool late_first_valid() {
  auto &value = late_first_storage();
  return uintptr_t(&value) == late_first_address && uintptr_t(&value) % 128 == 0 &&
         value.templated == 0xfedcba9876543210ULL && value.zero == 73;
}
static void late_first_nested(void *) { late_first_record(late_first_valid()); }
struct LateFirstHolder {
  ~LateFirstHolder() {
    late_first_record(late_first_valid());
    if (__cxa_thread_atexit_impl(late_first_nested, nullptr, __dso_handle))
      __atomic_add_fetch(&late_first_errors, 1, __ATOMIC_SEQ_CST);
  }
};
extern "C" int tls_late_first_use() {
  auto &value = late_first_storage();
  if (value.templated != 0x123456789abcdef0ULL || value.zero || uintptr_t(&value) % 128)
    return 1;
  static thread_local LateFirstHolder holder;
  (void)holder;
  late_first_address = uintptr_t(&value);
  value.templated = 0xfedcba9876543210ULL;
  value.zero = 73;
  __atomic_add_fetch(&late_first_uses, 1, __ATOMIC_SEQ_CST);
  return 0;
}
extern "C" int tls_late_first_summary() {
  return __atomic_load_n(&late_first_uses, __ATOMIC_SEQ_CST) == 1 &&
                 __atomic_load_n(&late_first_events, __ATOMIC_SEQ_CST) == 2 &&
                 __atomic_load_n(&late_first_errors, __ATOMIC_SEQ_CST) == 0
             ? 0 : 1;
}
