// SPDX-License-Identifier: GPL-3.0-only
// First-party callback lifetime fixture, never an APK library.
extern int __cxa_thread_atexit_impl(void (*)(void *), void *, void *);
static void (*observer)(void *);
static volatile int *finalized;
static void destroy(void *object) { observer(object); }
int retention_register(void (*callback)(void *), void *object) {
  observer = callback;
  return __cxa_thread_atexit_impl(destroy, object, 0);
}
void retention_bind_finalizer(volatile int *value) { finalized = value; }
__attribute__((destructor)) static void finish(void) { if (finalized) ++*finalized; }
