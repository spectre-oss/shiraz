/* SPDX-License-Identifier: GPL-3.0-only */
#ifndef SHIRAZ_NATIVE_WINDOW_H
#define SHIRAZ_NATIVE_WINDOW_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
/* Android NDK-compatible native-window buffer layout; APIs are a limited subset. */
typedef struct ANativeWindow ANativeWindow;
typedef struct ARect { int32_t left, top, right, bottom; } ARect;
typedef struct ANativeWindow_Buffer {
    int32_t width, height, stride, format;
    void* bits;
    uint32_t reserved[6];
} ANativeWindow_Buffer;
/* Host-only factory, not an Android API. RGBA8888 byte order. */
ANativeWindow* shiraz_window_create(int32_t width, int32_t height);
/* Host layout resize: also updates the dimensions restored by a producer's 0,0 request. */
int32_t shiraz_window_resize(ANativeWindow*, int32_t width, int32_t height);
/* Host transport: export returns an owned CLOEXEC fd or negative errno. Import duplicates it. */
int shiraz_window_export(ANativeWindow*);
ANativeWindow* shiraz_window_import(int descriptor);
/* Copies posted pixels as compositor ARGB8888. Returns -EINVAL for small output. */
int32_t shiraz_window_copy_argb(ANativeWindow*, uint32_t* output, uint64_t capacity);
/* Host CPU path posts complete straight-alpha ARGB without an RGBA round trip. */
int32_t shiraz_window_post_argb(ANativeWindow*, const uint32_t* pixels, uint64_t count);
/* Posted-buffer revision; changes on successful post/geometry reset, zero for null. */
uint64_t shiraz_window_revision(ANativeWindow*);
int32_t shiraz_window_known(ANativeWindow*);
void ANativeWindow_acquire(ANativeWindow*);
void ANativeWindow_release(ANativeWindow*);
int32_t ANativeWindow_getWidth(ANativeWindow*);
int32_t ANativeWindow_getHeight(ANativeWindow*);
int32_t ANativeWindow_getFormat(ANativeWindow*);
int32_t ANativeWindow_setBuffersGeometry(ANativeWindow*, int32_t width, int32_t height, int32_t format);
int32_t ANativeWindow_lock(ANativeWindow*, ANativeWindow_Buffer*, ARect* dirty);
int32_t ANativeWindow_unlockAndPost(ANativeWindow*);
#ifdef __cplusplus
}
#endif
#endif
