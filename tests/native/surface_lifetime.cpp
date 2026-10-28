// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <jni.h>
#include <cassert>
#include <cstring>
#include <initializer_list>
#include <cstdlib>
#include <new>

namespace { int liveAllocations = 0, failAfter = -1; }
void* operator new(std::size_t size) {
    if (failAfter == 0) throw std::bad_alloc();
    if (failAfter > 0) --failAfter;
    void* result = std::malloc(size ? size : 1);
    if (!result) throw std::bad_alloc();
    ++liveAllocations;
    return result;
}
void operator delete(void* value) noexcept {
    if (value) { --liveAllocations; std::free(value); }
}
void operator delete(void* value, std::size_t) noexcept { ::operator delete(value); }

extern "C" jlong Java_android_view_Surface_nativeEglCreateContext(JNIEnv*, jclass);
extern "C" ANativeWindow* ANativeWindow_fromSurface(JNIEnv*, jobject);
namespace {
enum Failure { Symbols, Devices, Initialize, Bind, Context, None } failure;
int handles = 0, displays = 0, references = 0;
EGLBoolean initialize(EGLDisplay, EGLint*, EGLint*) {
    if (failure == Initialize) return EGL_FALSE;
    ++displays; return EGL_TRUE;
}
EGLBoolean terminate(EGLDisplay) { --displays; return EGL_TRUE; }
EGLBoolean bind(EGLenum) { return failure != Bind; }
EGLContext createContext(EGLDisplay, EGLConfig, EGLContext, const EGLint*) {
    return failure == Context ? EGL_NO_CONTEXT : reinterpret_cast<EGLContext>(1);
}
EGLBoolean destroyContext(EGLDisplay, EGLContext) { return EGL_TRUE; }
EGLBoolean query(EGLint, EGLDeviceEXT* devices, EGLint* count) {
    *count = failure == Devices ? 0 : 1;
    devices[0] = reinterpret_cast<EGLDeviceEXT>(1); return EGL_TRUE;
}
EGLDisplay platform(EGLenum, void*, const EGLAttrib*) { return reinterpret_cast<EGLDisplay>(1); }
EGLSurface createWindow(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*) { return EGL_NO_SURFACE; }
EGLBoolean destroySurface(EGLDisplay, EGLSurface) { return EGL_TRUE; }
EGLBoolean make(EGLDisplay, EGLSurface, EGLSurface, EGLContext) { return EGL_TRUE; }
EGLBoolean swap(EGLDisplay, EGLSurface) { return EGL_TRUE; }
void* proc(const char* name) {
#define ENTRY(n, f) if (!std::strcmp(name, n)) return reinterpret_cast<void*>(f)
    ENTRY("eglGetProcAddress", proc);
    ENTRY("eglInitialize", initialize);
    ENTRY("eglTerminate", terminate);
    ENTRY("eglBindAPI", bind);
    ENTRY("eglCreateContext", createContext);
    ENTRY("eglDestroyContext", destroyContext);
    ENTRY("eglQueryDevicesEXT", query);
    ENTRY("eglGetPlatformDisplay", platform);
    ENTRY("eglCreateWindowSurface", createWindow);
    ENTRY("eglDestroySurface", destroySurface);
    ENTRY("eglMakeCurrent", make);
    ENTRY("eglSwapBuffers", swap);
#undef ENTRY
    return nullptr;
}
jclass getClass(JNIEnv*, jobject) { ++references; return reinterpret_cast<jclass>(1); }
void deleteRef(JNIEnv*, jobject) { --references; }
jmethodID getMethod(JNIEnv*, jclass, const char*, const char*) { return nullptr; }
jboolean exception(JNIEnv*) { return JNI_FALSE; }
}
extern "C" void* __wrap_dlopen(const char*, int) { ++handles; return reinterpret_cast<void*>(1); }
extern "C" int __wrap_dlclose(void*) { --handles; return 0; }
extern "C" void* __wrap_dlsym(void*, const char* name) { return failure == Symbols ? nullptr : proc(name); }
int main() {
    // Warm the registry's persistent bucket storage, then fail each allocation
    // from the window object through its pixel buffers and registry node.
    ANativeWindow* warm = shiraz_window_create(2, 2);
    assert(warm);
    ANativeWindow_release(warm);
    const int baseline = liveAllocations;
    for (int allocation = 0; allocation < 4; ++allocation) {
        failAfter = allocation;
        ANativeWindow* window = shiraz_window_create(2, 2);
        failAfter = -1;
        assert(!window);
        assert(liveAllocations == baseline);
    }

    // Each retry must relinquish both the loader reference and display initialization.
    for (Failure mode : {Symbols, Devices, Initialize, Bind, Context}) {
        failure = mode;
        for (int i = 0; i < 1000; ++i) {
            assert(Java_android_view_Surface_nativeEglCreateContext(nullptr, nullptr) == 0);
            assert(handles == 0);
            assert(displays == 0);
        }
    }
    failure = None;
    for (int i = 0; i < 1000; ++i) {
        assert(Java_android_view_Surface_nativeEglCreateContext(nullptr, nullptr) != 0);
        assert(handles == 1 && displays == 1);
    }
    // Attached native threads need explicit local-reference cleanup even when
    // method lookup fails; there is no Java native-method return to clean up.
    JNINativeInterface_ functions{};
    functions.GetObjectClass = getClass;
    functions.GetMethodID = getMethod;
    functions.DeleteLocalRef = deleteRef;
    functions.ExceptionCheck = exception;
    JNIEnv env{&functions};
    for (int i = 0; i < 1000; ++i) {
        assert(!ANativeWindow_fromSurface(&env, reinterpret_cast<jobject>(1)));
        assert(references == 0);
    }
}
