// SPDX-License-Identifier: GPL-3.0-only
extern int leaf_state, selected(void), leaf_probe(void), versioned_api(void),
    legacy(void);
__asm__(".symver legacy,versioned_api@FIXTURE_OLD");
volatile int left_state;
__attribute__((constructor)) static void initialize(void) {
  left_state = leaf_state + 10;
}
int left_value(void) {
  return selected() + leaf_probe() + legacy() + versioned_api();
}
