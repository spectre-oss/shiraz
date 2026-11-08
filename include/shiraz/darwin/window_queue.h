/* SPDX-License-Identifier: GPL-3.0-only */
/*
 * macOS only (M4b, build/macos/m4b/design/DESIGN-v2.md section 3): an ANativeWindow
 * buffer queue of IOSurface-backed slots. The EGL shim renders app GLES frames
 * into a dequeued slot and queues it at swap; the Skia consumer on SDL's ANGLE
 * context samples the newest queued slot. No CPU copy on that path. CPU readers
 * (PixelCopy, exports, frame sinks) materialise the newest slot on demand.
 *
 * Sync: each window owns one frame event and one release event (MTLSharedEvent),
 * each with its own monotonic counter and a process-unique serial. A slot handed
 * out by dequeue carries the release event and the value the producer must GPU-
 * wait for before writing; a slot handed out by acquire carries the frame event
 * and the value the consumer must GPU-wait for before sampling. Value 0 = no wait.
 * Implemented in native/darwin/window_queue.inc and native/darwin/shared_event.mm
 * (both in libandroid). The Linux header include/shiraz/native_window.h is unchanged.
 */
#ifndef SHIRAZ_DARWIN_WINDOW_QUEUE_H
#define SHIRAZ_DARWIN_WINDOW_QUEUE_H
#include <stddef.h>
#include <stdint.h>
#include "shiraz/native_window.h"
#ifdef __cplusplus
extern "C" {
#endif

#define SHIRAZ_SLOT_BOTTOM_UP 1u
#define SHIRAZ_EVENT_FRAME 0
#define SHIRAZ_EVENT_RELEASE 1
#define SHIRAZ_WINDOW_MAX_SLOTS 4

typedef struct ShirazWindowSlot {
    uint32_t index; uint64_t generation;
    void* surface;            /* IOSurfaceRef, +0, valid while held */
    void* texture;            /* id<MTLTexture>, +0, valid while held; EGL_METAL_TEXTURE_ANGLE client buffer */
    int32_t width, height; uint32_t bytes_per_row, fourcc;
    void* event; uint64_t event_serial; uint64_t value;  /* wait until event >= value; value 0 = no wait */
    uint64_t revision; uint32_t flags;                   /* SHIRAZ_SLOT_BOTTOM_UP */
} ShirazWindowSlot;

/* Device and events. set_device: the first caller records; a different device -> -EXDEV. */
int32_t  shiraz_window_set_device(void* mtlDevice);
/* Takes ownership of EVENT's +1 reference. The first adopter per (window, kind) wins; a later
 * adopter's reference is released. Returns the owned event (+0) and its serial. */
void*    shiraz_window_adopt_event(ANativeWindow*, int kind, void* event, uint64_t* serial);
/* The owned event (+0) or null, and its serial. */
void*    shiraz_window_event(ANativeWindow*, int kind, uint64_t* serial);
uint64_t shiraz_window_next_value(ANativeWindow*, int kind);   /* monotonic per window per kind, never reset */
int32_t  shiraz_event_signaled(void* event, uint64_t value);   /* signaledValue >= value */
/* CPU wait (listener + semaphore). 0, or -ETIMEDOUT. */
int32_t  shiraz_event_cpu_wait(void* event, uint64_t value, int32_t timeout_ms);

/* Producer. */
int32_t shiraz_window_connect_producer(ANativeWindow*, uint64_t producer);  /* -EBUSY if another live producer */
void    shiraz_window_disconnect_producer(ANativeWindow*, uint64_t producer);
int32_t shiraz_window_dequeue_slot(ANativeWindow*, uint64_t producer, ShirazWindowSlot*); /* -EPIPE abandoned, -EWOULDBLOCK pin timeout */
int32_t shiraz_window_queue_slot(ANativeWindow*, uint64_t producer, uint32_t index, uint64_t generation,
                                 int32_t width, int32_t height, uint64_t frame_value); /* -EAGAIN resized */
/* Atomic: 0 = queued (or -EAGAIN-dropped) + next dequeued; -EWOULDBLOCK = NOT queued, cur stays DEQUEUED. */
int32_t shiraz_window_queue_and_dequeue(ANativeWindow*, uint64_t producer, uint32_t index, uint64_t generation,
                                        int32_t width, int32_t height, uint64_t frame_value, ShirazWindowSlot* next);
int32_t shiraz_window_cancel_slot(ANativeWindow*, uint64_t producer, uint32_t index);
/* 1 with REASON "export" | "frame-sink" | "offscreen" when CPU frames are required, else 0. */
int32_t shiraz_window_cpu_frames_required(ANativeWindow*, const char** reason);
void    shiraz_window_set_offscreen(ANativeWindow*);

/* Consumer. */
int32_t shiraz_window_acquire_slot(ANativeWindow*, uint64_t consumer, uint64_t frame_serial, ShirazWindowSlot*);
         /* -ENODATA: CPU newer or nothing queued (see shiraz_window_nodata_reason); -EBUSY: other consumer;
            -EPIPE: abandoned; -EINPROGRESS: the acquired slot has an unmarked sampling frame */
const char* shiraz_window_nodata_reason(ANativeWindow*);  /* "nodata-cpu-newer" | "nodata-nothing-queued" */
int32_t shiraz_window_mark_sampled(ANativeWindow*, uint64_t consumer, uint32_t index, uint64_t generation,
                                   uint64_t release_value);  /* release_value already committed by glFlush */
int32_t shiraz_window_consumer_detach(ANativeWindow*, uint64_t consumer); /* after consumer glFinish: frees ACQUIRED with value 0 */
void    shiraz_window_abandon(ANativeWindow*);
// SHIRAZ_TRACE_ZERO_COPY=1: one ordered "SHIRAZ_ZC_EVENT <name> window=<p> t_ns=<steady>" line (gates).
void    shiraz_zero_copy_event(const char* name, ANativeWindow* window);
int32_t shiraz_window_abandoned(ANativeWindow*);

/* CPU readers: copies the newest frame as compositor ARGB and reports the revision actually copied. */
int32_t shiraz_window_copy_argb_revision(ANativeWindow*, uint32_t* pixels, size_t count, uint64_t* revision);

/* Counters (H2). Sites are short static strings. */
void     shiraz_cpu_pixel_reads_add(const char* site, uint64_t bytes);
void     shiraz_observer_reads_add(const char* site, uint64_t bytes);
/* Named event counters (swaps, queued, gpu_draws, cpu_publishes, consumer_fallbacks, pin_drops, ...). */
void     shiraz_zero_copy_count(const char* name, uint64_t amount);
/* Millisecond samples for p50/p90/p99 (swap_ms, gl_blocked_ms, materialize_ms). */
void     shiraz_zero_copy_sample(const char* name, double ms);
uint64_t shiraz_cpu_pixel_reads_total(void);
void     shiraz_zero_copy_counters(char* out, size_t size); /* one line, all sites */
/* SHIRAZ_EGL_ZERO_COPY: 0 = readback, 1 = auto (default), 2 = require. */
int32_t  shiraz_zero_copy_mode(void);
/* Under require: prints SHIRAZ_RUNTIME_FATAL zero-copy reason=<reason> and aborts. Otherwise returns. */
void     shiraz_zero_copy_fatal(const char* reason);

#ifdef __cplusplus
}
#endif
#endif
