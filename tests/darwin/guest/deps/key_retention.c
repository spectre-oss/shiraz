// SPDX-License-Identifier: GPL-3.0-only
// First-party real ELF pthread-key callback fixture; not an APK library.
extern int pthread_key_create(unsigned *, void (*)(void *));
extern int pthread_key_delete(unsigned);
extern int pthread_setspecific(unsigned, const void *);
static unsigned key;
static void (*observer)(void *);
static volatile int *finalized;
static int rearm;
static void destroy(void *value) {
  observer(value);
  if (rearm && pthread_setspecific(key, value)) __builtin_trap();
}
int key_retention_create(void (*callback)(void *), volatile int *counter, int repeat) {
  observer = callback;
  finalized = counter;
  rearm = repeat;
  return pthread_key_create(&key, destroy);
}
int key_retention_set(void *value) { return pthread_setspecific(key, value); }
int key_retention_delete(void) { return pthread_key_delete(key); }
__attribute__((destructor)) static void finish(void) { if (finalized) ++*finalized; }
