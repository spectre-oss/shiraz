// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>

static int comparisons;
static uintptr_t expected_tls;
static int compare(const void* a, const void* b) {
    uintptr_t tls;
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(tls));
    if (tls != expected_tls)
        return 0;
    ++comparisons;
    int x = *(const int*)a, y = *(const int*)b;
    return (x > y) - (x < y);
}
int admission_sort_run(int search, uintptr_t tls) {
    int values[] = {1, 3, 5, 7, 9};
    comparisons = 0;
    expected_tls = tls;
    errno = 73;
    if (search) {
        int key = 7;
        if (bsearch(&key, values, 5, sizeof(int), compare) != values + 3)
            return 1;
        key = 4;
        if (bsearch(&key, values, 5, sizeof(int), compare))
            return 2;
    } else {
        int unsorted[] = {5, 1, 9, 3, 7};
        qsort(unsorted, 5, sizeof(int), compare);
        for (int i = 0; i < 5; ++i)
            if (unsorted[i] != values[i])
                return 3;
    }
    return comparisons && errno == 73 ? 0 : 4;
}
