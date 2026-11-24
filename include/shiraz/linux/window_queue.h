/* SPDX-License-Identifier: GPL-3.0-only */
/*
 * Linux zero-copy ANativeWindow buffer queue backed by GBM/DMA-BUF AHardwareBuffers
 * and kernel sync_file fences (EGL_ANDROID_native_fence_sync).
 *
 * Producer (native/linux/egl_shim_dmabuf.inc) renders app GLES frames into a
 * dequeued AHardwareBuffer slot via EGL_LINUX_DMA_BUF_EXT and queues it on
 * eglSwapBuffers with a kernel sync_file frame fence (no glReadPixels, no CPU copy).
 * Consumers (Skia Ganesh in native/linux/gpu_dmabuf.inc and SurfaceTexture in
 * native/surface_texture.cpp) acquire the newest queued slot and GPU-wait on its
 * frame fence before sampling via EGL_LINUX_DMA_BUF_EXT. CPU readers (PixelCopy,
 * screenshots, software Canvas) materialize the newest slot on demand via
 * AHardwareBuffer_lock().
 */
#ifndef SHIRAZ_LINUX_WINDOW_QUEUE_H
#define SHIRAZ_LINUX_WINDOW_QUEUE_H
#include <stddef.h>
#include <stdint.h>
#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"
#ifdef __cplusplus
extern "C" {
#endif

#define SHIRAZ_SLOT_BOTTOM_UP 1u
#define SHIRAZ_WINDOW_MAX_SLOTS 4

typedef struct ShirazWindowSlot {
    uint32_t index;
    uint64_t generation;
    AHardwareBuffer* buffer; /* +0 borrowed while slot is held (DEQUEUED or ACQUIRED) */
    int32_t width, height;
    uint32_t stride_bytes; /* row pitch in bytes */
    uint32_t drm_fourcc;   /* DRM fourcc from GBM export (e.g. DRM_FORMAT_ABGR8888) */
    int32_t fence_fd;      /* owned CLOEXEC sync_file fd to wait on (>= 0), or -1; caller closes */
    uint64_t revision;
    uint32_t flags; /* SHIRAZ_SLOT_BOTTOM_UP */
} ShirazWindowSlot;

/* Producer. */
int32_t shiraz_window_connect_producer(ANativeWindow*,
                                       uint64_t producer); /* -EBUSY if another live producer */
void shiraz_window_disconnect_producer(ANativeWindow*, uint64_t producer);
int32_t shiraz_window_dequeue_slot(ANativeWindow*, uint64_t producer,
                                   ShirazWindowSlot*); /* -EPIPE abandoned, -EWOULDBLOCK pinned */
/* Takes ownership of frame_fence_fd (always closes it on return or error). */
int32_t shiraz_window_queue_slot(ANativeWindow*, uint64_t producer, uint32_t index,
                                 uint64_t generation, int32_t width, int32_t height,
                                 int32_t frame_fence_fd); /* -EAGAIN resized */
/* Atomic queue + dequeue next. Takes ownership of frame_fence_fd on all return paths. */
int32_t shiraz_window_queue_and_dequeue(ANativeWindow*, uint64_t producer, uint32_t index,
                                        uint64_t generation, int32_t width, int32_t height,
                                        int32_t frame_fence_fd, ShirazWindowSlot* next);
int32_t shiraz_window_cancel_slot(ANativeWindow*, uint64_t producer, uint32_t index);
/* 1 with REASON "export" | "frame-sink" | "offscreen" when CPU frames are required, else 0. */
int32_t shiraz_window_cpu_frames_required(ANativeWindow*, const char** reason);
void shiraz_window_set_offscreen(ANativeWindow*);

/* Consumer. */
int32_t shiraz_window_acquire_slot(ANativeWindow*, uint64_t consumer, uint64_t frame_serial,
                                   ShirazWindowSlot*);
const char* shiraz_window_nodata_reason(ANativeWindow*);
/* Takes ownership of release_fence_fd (always closes it on return or error). */
int32_t shiraz_window_mark_sampled(ANativeWindow*, uint64_t consumer, uint32_t index,
                                   uint64_t generation, int32_t release_fence_fd);
int32_t shiraz_window_consumer_detach(ANativeWindow*, uint64_t consumer);
void shiraz_window_abandon(ANativeWindow*);
int32_t shiraz_window_abandoned(ANativeWindow*);

/* CPU readers: copies newest frame as compositor ARGB and reports copied revision. */
int32_t shiraz_window_copy_argb_revision(ANativeWindow*, uint32_t* pixels, size_t count,
                                         uint64_t* revision);

/* Telemetry & mode control (matching macOS SHIRAZ_EGL_ZERO_COPY / SHIRAZ_TRACE_ZERO_COPY). */
void shiraz_zero_copy_event(const char* name, ANativeWindow* window);
void shiraz_cpu_pixel_reads_add(const char* site, uint64_t bytes);
void shiraz_observer_reads_add(const char* site, uint64_t bytes);
void shiraz_zero_copy_count(const char* name, uint64_t amount);
void shiraz_zero_copy_sample(const char* name, double ms);
uint64_t shiraz_cpu_pixel_reads_total(void);
void shiraz_zero_copy_counters(char* out, size_t size);
/* SHIRAZ_EGL_ZERO_COPY: 0 = readback, 1 = auto (default), 2 = require. */
int32_t shiraz_zero_copy_mode(void);
void shiraz_zero_copy_fatal(const char* reason);

#ifdef __cplusplus
}
#endif
#endif
