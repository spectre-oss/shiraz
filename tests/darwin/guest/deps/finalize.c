// SPDX-License-Identifier: GPL-3.0-only
// Purpose-built init/fini fixture, never an application library.
#ifdef FINALIZE_LINK_HOST
// Link-time declarations only. Runtime must select the explicit host provider.
void finalize_record(int value) { (void)value; __builtin_trap(); }
#else
extern void finalize_record(int);
extern int __cxa_atexit(void (*)(void *), void *, void *);
extern void __cxa_finalize(void *);
static int live, selected_key, pending_key;
#ifdef FINALIZE_ROOT
enum { base = 200 };
extern int finalize_dep_alive(void);
extern void finalize_dep_ping(void);
int finalize_root_alive(void) { return live; }
void finalize_root_selected(void) { __cxa_finalize(&selected_key); }
void *finalize_root_dso_key(void) { return &selected_key; }
#else
enum { base = 100 };
int finalize_dep_alive(void) { return live; }
void finalize_dep_ping(void) { finalize_record(live ? 190 : -190); }
#endif
static void selected(void *unused) { (void)unused; finalize_record(base + 30); }
static void pending(void *unused) { (void)unused; finalize_record(base + 31); }
static void late(void *unused) { (void)unused; finalize_record(base + 32); }
__attribute__((constructor)) static void initialize(void) {
#ifdef FINALIZE_ROOT
  if (!finalize_dep_alive()) __builtin_trap();
#endif
  live = 1;
  finalize_record(base + 1);
  if (__cxa_atexit(selected, 0, &selected_key) ||
      __cxa_atexit(pending, 0, &pending_key)) __builtin_trap();
}
__attribute__((destructor(200))) static void array_high(void) {
  finalize_record(base + 12);
#ifdef FINALIZE_ROOT
  finalize_dep_ping();
#endif
}
__attribute__((destructor(101))) static void array_low(void) {
  finalize_record(base + 11);
  __cxa_finalize(&selected_key);
  __cxa_finalize(&selected_key);
  if (__cxa_atexit(late, 0, &pending_key)) __builtin_trap();
}
// Select this export with the linker's -fini guest_fini option.
void guest_fini(void) { finalize_record(base + 20); live = 0; }
#endif
