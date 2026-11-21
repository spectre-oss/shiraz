// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/native_window.h"
#include <cstddef>
#include <cstdint>
#include <jni.h>

struct AAssetManager;
struct AInputQueue;
struct ANativeActivity;
struct ANativeActivityCallbacks;

struct ANativeActivity {
    ANativeActivityCallbacks* callbacks;
    JavaVM* vm;
    JNIEnv* env;
    jobject clazz;
    const char* internalDataPath;
    const char* externalDataPath;
    int32_t sdkVersion;
    void* instance;
    AAssetManager* assetManager;
    const char* obbPath;
};

struct ANativeActivityCallbacks {
    void (*onStart)(ANativeActivity* activity);
    void (*onResume)(ANativeActivity* activity);
    void* (*onSaveInstanceState)(ANativeActivity* activity, size_t* outSize);
    void (*onPause)(ANativeActivity* activity);
    void (*onStop)(ANativeActivity* activity);
    void (*onDestroy)(ANativeActivity* activity);
    void (*onWindowFocusChanged)(ANativeActivity* activity, int hasFocus);
    void (*onNativeWindowCreated)(ANativeActivity* activity, ANativeWindow* window);
    void (*onNativeWindowResized)(ANativeActivity* activity, ANativeWindow* window);
    void (*onNativeWindowRedrawNeeded)(ANativeActivity* activity, ANativeWindow* window);
    void (*onNativeWindowDestroyed)(ANativeActivity* activity, ANativeWindow* window);
    void (*onInputQueueCreated)(ANativeActivity* activity, AInputQueue* queue);
    void (*onInputQueueDestroyed)(ANativeActivity* activity, AInputQueue* queue);
    void (*onContentRectChanged)(ANativeActivity* activity, const ARect* rect);
    void (*onConfigurationChanged)(ANativeActivity* activity);
    void (*onLowMemory)(ANativeActivity* activity);
};

extern "C" {
void ANativeActivity_finish(ANativeActivity* activity);
void ANativeActivity_setWindowFormat(ANativeActivity* activity, int32_t format);
void ANativeActivity_setWindowFlags(ANativeActivity* activity, uint32_t addFlags,
                                    uint32_t removeFlags);
void ANativeActivity_showSoftInput(ANativeActivity* activity, uint32_t flags);
void ANativeActivity_hideSoftInput(ANativeActivity* activity, uint32_t flags);

// Shiraz host lifecycle helpers for embedding ANativeActivity instances.
ANativeActivity* shiraz_native_activity_create(const char* internal_path, const char* external_path,
                                               const char* obb_path, AAssetManager* assets);
void shiraz_native_activity_destroy(ANativeActivity* activity);
int shiraz_native_activity_is_finished(const ANativeActivity* activity);
int32_t shiraz_native_activity_window_format(const ANativeActivity* activity);
uint32_t shiraz_native_activity_window_flags(const ANativeActivity* activity);
int shiraz_native_activity_soft_input_visible(const ANativeActivity* activity);
}
