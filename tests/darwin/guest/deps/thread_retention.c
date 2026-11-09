// SPDX-License-Identifier: GPL-3.0-only
// First-party real ELF async pthread entry fixture, not APK code.
typedef unsigned long long guest_thread;
extern int pthread_create(guest_thread *, const void *, void *(*)(void *), void *);
extern int pthread_join(guest_thread, void **);
static guest_thread child;
static void (*observer)(void *);
static volatile int *finalized;
void *thread_retention_entry(void *value) { observer(value); return value; }
void thread_retention_bind_finalizer(volatile int *value) { finalized = value; }
int thread_retention_invalid(void) {
  guest_thread unused;
  return pthread_create(&unused, 0, (void *(*)(void *))(unsigned long)0x1000, 0);
}
int thread_retention_start(void (*callback)(void *), void *value) {
  observer = callback;
  return pthread_create(&child, 0, thread_retention_entry, value);
}
int thread_retention_join(void *expected) {
  void *result = 0;
  int error = pthread_join(child, &result);
  return error ? error : result != expected;
}
__attribute__((destructor)) static void finish(void) { if (finalized) ++*finalized; }
