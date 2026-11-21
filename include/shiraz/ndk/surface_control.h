// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"
#include <cstddef>
#include <cstdint>
#include <jni.h>

struct ASurfaceControl;
struct ASurfaceTransaction;
struct ASurfaceTransactionStats;
struct ASurfaceTexture;

using ASurfaceTransaction_OnComplete = void (*)(void* context, ASurfaceTransactionStats* stats);
using ASurfaceTransaction_OnCommit = void (*)(void* context, ASurfaceTransactionStats* stats);

enum {
    ASURFACE_TRANSACTION_TRANSPARENCY_TRANSPARENT = 0,
    ASURFACE_TRANSACTION_TRANSPARENCY_TRANSLUCENT = 1,
    ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE = 2,
    ASURFACE_TRANSACTION_VISIBILITY_HIDE = 0,
    ASURFACE_TRANSACTION_VISIBILITY_SHOW = 1,
};

extern "C" {
ASurfaceControl* ASurfaceControl_create(ASurfaceControl* parent, const char* debug_name);
ASurfaceControl* ASurfaceControl_createFromWindow(ANativeWindow* parent, const char* debug_name);
void ASurfaceControl_acquire(ASurfaceControl* surface_control);
void ASurfaceControl_release(ASurfaceControl* surface_control);
ASurfaceControl* ASurfaceControl_fromJava(JNIEnv* env, jobject surfaceControlObj);

ASurfaceTransaction* ASurfaceTransaction_create();
void ASurfaceTransaction_delete(ASurfaceTransaction* transaction);
void ASurfaceTransaction_apply(ASurfaceTransaction* transaction);
void ASurfaceTransaction_setVisibility(ASurfaceTransaction* transaction,
                                       ASurfaceControl* surface_control, int8_t visibility);
void ASurfaceTransaction_setZOrder(ASurfaceTransaction* transaction,
                                   ASurfaceControl* surface_control, int32_t z_order);
void ASurfaceTransaction_setBuffer(ASurfaceTransaction* transaction,
                                   ASurfaceControl* surface_control, AHardwareBuffer* buffer,
                                   int acquire_fence_fd);
void ASurfaceTransaction_setGeometry(ASurfaceTransaction* transaction,
                                     ASurfaceControl* surface_control, const ARect& source,
                                     const ARect& destination, int32_t transform);
void ASurfaceTransaction_setCrop(ASurfaceTransaction* transaction, ASurfaceControl* surface_control,
                                 const ARect& crop);
void ASurfaceTransaction_setPosition(ASurfaceTransaction* transaction,
                                     ASurfaceControl* surface_control, int32_t x, int32_t y);
void ASurfaceTransaction_setScale(ASurfaceTransaction* transaction,
                                  ASurfaceControl* surface_control, float xScale, float yScale);
void ASurfaceTransaction_setBufferTransform(ASurfaceTransaction* transaction,
                                            ASurfaceControl* surface_control, int32_t transform);
void ASurfaceTransaction_setBufferTransparency(ASurfaceTransaction* transaction,
                                               ASurfaceControl* surface_control,
                                               int8_t transparency);
void ASurfaceTransaction_setDamageRegion(ASurfaceTransaction* transaction,
                                         ASurfaceControl* surface_control, const ARect rects[],
                                         uint32_t count);
void ASurfaceTransaction_setDesiredPresentTime(ASurfaceTransaction* transaction,
                                               int64_t desiredPresentTime);
void ASurfaceTransaction_setBufferAlpha(ASurfaceTransaction* transaction,
                                        ASurfaceControl* surface_control, double alpha);
void ASurfaceTransaction_setBufferDataSpace(ASurfaceTransaction* transaction,
                                            ASurfaceControl* surface_control, int32_t data_space);
void ASurfaceTransaction_setColor(ASurfaceTransaction* transaction,
                                  ASurfaceControl* surface_control, float r, float g, float b,
                                  float alpha, int32_t dataspace);
void ASurfaceTransaction_setFrameRate(ASurfaceTransaction* transaction,
                                      ASurfaceControl* surface_control, float frameRate,
                                      int8_t compatibility);
void ASurfaceTransaction_setFrameRateWithChangeStrategy(ASurfaceTransaction* transaction,
                                                        ASurfaceControl* surface_control,
                                                        float frameRate, int8_t compatibility,
                                                        int8_t changeFrameRateStrategy);
void ASurfaceTransaction_clearFrameRate(ASurfaceTransaction* transaction,
                                        ASurfaceControl* surface_control);
void ASurfaceTransaction_setOnComplete(ASurfaceTransaction* transaction, void* context,
                                       ASurfaceTransaction_OnComplete func);
void ASurfaceTransaction_setOnCommit(ASurfaceTransaction* transaction, void* context,
                                     ASurfaceTransaction_OnCommit func);
void ASurfaceTransaction_reparent(ASurfaceTransaction* transaction,
                                  ASurfaceControl* surface_control, ASurfaceControl* new_parent);
void ASurfaceTransaction_setEnableBackPressure(ASurfaceTransaction* transaction,
                                               ASurfaceControl* surface_control,
                                               bool enableBackPressure);

int64_t ASurfaceTransactionStats_getLatchTime(ASurfaceTransactionStats* stats);
void ASurfaceTransactionStats_getASurfaceControls(ASurfaceTransactionStats* stats,
                                                  ASurfaceControl*** outASurfaceControls,
                                                  size_t* outASurfaceControlsSize);
void ASurfaceTransactionStats_releaseASurfaceControls(ASurfaceControl** surface_controls);
int64_t ASurfaceTransactionStats_getAcquireTime(ASurfaceTransactionStats* stats,
                                                ASurfaceControl* surface_control);
int ASurfaceTransactionStats_getPreviousReleaseFenceFd(ASurfaceTransactionStats* stats,
                                                       ASurfaceControl* surface_control);

ASurfaceTexture* ASurfaceTexture_fromSurfaceTexture(JNIEnv* env, jobject surfacetexture);
void ASurfaceTexture_release(ASurfaceTexture* st);
ANativeWindow* ASurfaceTexture_acquireANativeWindow(ASurfaceTexture* st);
int ASurfaceTexture_attachToGLContext(ASurfaceTexture* st, uint32_t texName);
int ASurfaceTexture_detachFromGLContext(ASurfaceTexture* st);
int ASurfaceTexture_updateTexImage(ASurfaceTexture* st);
void ASurfaceTexture_getTransformMatrix(ASurfaceTexture* st, float mtx[16]);
int64_t ASurfaceTexture_getTimestamp(ASurfaceTexture* st);
}
