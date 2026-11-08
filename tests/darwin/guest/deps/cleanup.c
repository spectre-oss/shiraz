// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
_Static_assert(sizeof(__pthread_cleanup_t) == 24 && _Alignof(__pthread_cleanup_t) == 8 &&
               offsetof(__pthread_cleanup_t, __cleanup_prev) == 0 &&
               offsetof(__pthread_cleanup_t, __cleanup_routine) == 8 &&
               offsetof(__pthread_cleanup_t, __cleanup_arg) == 16,
               "pinned bionic arm64 cleanup record ABI");
struct Trace { int value, bad; __pthread_cleanup_t *previous; };
struct Argument { struct Trace *trace; int value, nested; };
static void record(void *opaque) {
  struct Argument *argument = opaque;
  if (argument->nested) {
    __pthread_cleanup_t nested;
    struct Argument child = {argument->trace, 9, 0};
    __pthread_cleanup_push(&nested, record, &child);
    if (nested.__cleanup_prev != argument->trace->previous) argument->trace->bad = 1;
    __pthread_cleanup_pop(&nested, 1);
  }
  argument->trace->value = argument->trace->value * 10 + argument->value;
}
int cleanup_balanced(void) {
  __pthread_cleanup_t first, second;
  struct Trace trace = {0};
  struct Argument a = {&trace, 1, 0}, b = {&trace, 2, 1};
  errno = 73;
  __pthread_cleanup_push(&first, record, &a);
  if (first.__cleanup_prev || first.__cleanup_routine != record || first.__cleanup_arg != &a) return 1;
  trace.previous = &first;
  __pthread_cleanup_push(&second, record, &b);
  if (second.__cleanup_prev != &first) return 2;
  __pthread_cleanup_pop(&second, 1);
  __pthread_cleanup_pop(&first, 1);
  if (trace.value != 921 || trace.bad || errno != 73) return 3;
  __pthread_cleanup_push(&first, record, &a);
  __pthread_cleanup_pop(&first, 0);
  if (trace.value != 921) return 4;
  __pthread_cleanup_push(&first, 0, 0);
  __pthread_cleanup_pop(&first, 0);
  pthread_cleanup_push(record, &a);
  pthread_cleanup_pop(1);
  return trace.value == 9211 && errno == 73 ? 73 : 5;
}
struct Frame { __pthread_cleanup_t cleanup; struct Trace trace; struct Argument argument; };
_Static_assert(sizeof(struct Frame) <= 512 && _Alignof(struct Frame) <= 16,
               "native probe cleanup frame storage");
extern int __cxa_thread_atexit_impl(void (*)(void *), void *, void *);
static int late_callbacks;
static void late_cleanup(void *unused) {
  (void)unused;
  struct Trace trace = {0};
  struct Argument argument = {&trace, 5, 0};
  pthread_cleanup_push(record, &argument);
  pthread_cleanup_pop(1);
  __atomic_fetch_add(&late_callbacks, trace.value == 5 ? 1 : -100, __ATOMIC_RELAXED);
}
int cleanup_thread_stage(int stage, void *storage) {
  struct Frame *frame = storage;
  if (!stage) {
    frame->trace.value = frame->trace.bad = 0;
    frame->trace.previous = 0;
    frame->argument.trace = &frame->trace;
    frame->argument.value = 7; frame->argument.nested = 0;
    __pthread_cleanup_push(&frame->cleanup, record, &frame->argument);
    return frame->cleanup.__cleanup_prev ? 1 : 73;
  }
  __pthread_cleanup_pop(&frame->cleanup, 1);
  if (frame->trace.value != 7) return 2;
  return __cxa_thread_atexit_impl(late_cleanup, 0, 0) == 0 ? 73 : 3;
}
int cleanup_late_count(void) { return __atomic_load_n(&late_callbacks, __ATOMIC_RELAXED); }
int cleanup_bad_order(void) {
  __pthread_cleanup_t a, b;
  __pthread_cleanup_push(&a, 0, 0);
  __pthread_cleanup_push(&b, 0, 0);
  __pthread_cleanup_pop(&a, 0);
  return 1;
}
int cleanup_bad_metadata(void) {
  __pthread_cleanup_t a;
  __pthread_cleanup_push(&a, 0, 0);
  a.__cleanup_arg = (void *)1;
  __pthread_cleanup_pop(&a, 0);
  return 1;
}
int cleanup_bad_routine(void) {
  __pthread_cleanup_t a;
  __pthread_cleanup_push(&a, (__pthread_cleanup_func_t)(uintptr_t)1, 0);
  return 1;
}
int cleanup_null_execute(void) {
  __pthread_cleanup_t a;
  __pthread_cleanup_push(&a, 0, 0);
  __pthread_cleanup_pop(&a, 1);
  return 1;
}
int cleanup_duplicate(void) {
  __pthread_cleanup_t a;
  __pthread_cleanup_push(&a, 0, 0);
  __pthread_cleanup_push(&a, 0, 0);
  return 1;
}
int cleanup_underflow(void) {
  __pthread_cleanup_t a = {0};
  __pthread_cleanup_pop(&a, 0);
  return 1;
}
int cleanup_over_budget(void) {
  static __pthread_cleanup_t records[4097];
  for (int i = 0; i < 4097; ++i) __pthread_cleanup_push(records + i, 0, 0);
  return 1;
}
int cleanup_unbalanced(void) {
  static __pthread_cleanup_t record;
  __pthread_cleanup_push(&record, 0, 0);
  return 73;
}
