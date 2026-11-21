// SPDX-License-Identifier: GPL-3.0-only

__attribute__((aligned(16384))) volatile long counter = 3;
static volatile long* const pointer = &counter;
long shared_page_read(void) {
    return *pointer;
}
long shared_page_increment(void) {
    return ++*pointer;
}
