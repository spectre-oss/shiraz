// SPDX-License-Identifier: GPL-3.0-only
#include <stdint.h>
struct Observation {
    uintptr_t expected_tls, seen_tls, self;
    uint32_t events, calls;
};
void admission_audio_queue(void* self, struct Observation* out) {
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(out->seen_tls));
    out->self = (uintptr_t)self;
    ++out->calls;
}
void admission_audio_play(void* self, struct Observation* out, uint32_t events) {
    admission_audio_queue(self, out);
    out->events = events;
}
