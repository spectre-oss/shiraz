// SPDX-License-Identifier: GPL-3.0-only
volatile int right_state;
__attribute__((constructor)) static void initialize(void) { right_state = 9; }
int selected(void) { return 22; }
int right_marker(void) { return 7; }
