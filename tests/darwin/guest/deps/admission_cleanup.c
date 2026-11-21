// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <pthread.h>
#include <stdint.h>
struct CleanupObservation {
    int calls;
    int tls_seen;
};
void admission_cleanup_callback(void* opaque) {
    struct CleanupObservation* observation = opaque;
    uintptr_t tls;
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(tls));
    if (tls)
        observation->tls_seen++;
    observation->calls++;
    if (observation->calls == 1) {
        __pthread_cleanup_t nested;
        __pthread_cleanup_push(&nested, admission_cleanup_callback, observation);
        __pthread_cleanup_pop(&nested, 1);
    }
}
int admission_cleanup_run(void) {
    __pthread_cleanup_t record;
    struct CleanupObservation observation = {0};
    errno = 73;
    __pthread_cleanup_push(&record, admission_cleanup_callback, &observation);
    __pthread_cleanup_pop(&record, 1);
    return observation.calls == 2 && observation.tls_seen == 2 && errno == 73 ? 73 : -1;
}
