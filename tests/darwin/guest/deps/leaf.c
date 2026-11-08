// SPDX-License-Identifier: GPL-3.0-only
volatile int leaf_state;
static volatile int local_shadow = 5;
__attribute__((constructor)) static void initialize(void) { leaf_state = 21; }
__attribute__((noinline)) int shadowed(void) { return local_shadow; }
int leaf_probe(void) { return shadowed() + leaf_state; }
int selected(void) { return 33; }
int old_api(void) { return 1; }
int new_api(void) { return 2; }
#ifndef NO_OLD_VERSION
__asm__(".symver old_api,versioned_api@FIXTURE_OLD");
#endif
__asm__(".symver new_api,versioned_api@@FIXTURE_1");
