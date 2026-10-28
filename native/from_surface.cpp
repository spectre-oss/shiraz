// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <jni.h>
#include <mutex>
#include <unordered_map>

extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeCreateWindow(JNIEnv*, jclass, jint width, jint height) {
    return reinterpret_cast<jlong>(shiraz_window_create(width, height));
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_view_Surface_nativeResizeWindow(JNIEnv*, jclass, jlong window, jint width, jint height) {
    return ANativeWindow_setBuffersGeometry(reinterpret_cast<ANativeWindow*>(window), width, height, 1) == 0;
}
extern "C" JNIEXPORT void JNICALL Java_android_view_Surface_nativeReleaseWindow(JNIEnv*, jclass, jlong window) {
    ANativeWindow_release(reinterpret_cast<ANativeWindow*>(window));
}
extern "C" JNIEXPORT jint JNICALL Java_android_view_Surface_nativeExportWindow(JNIEnv*, jclass, jlong window) {
    return shiraz_window_export(reinterpret_cast<ANativeWindow*>(window));
}
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeImportWindow(JNIEnv*, jclass, jint descriptor) {
    return reinterpret_cast<jlong>(shiraz_window_import(descriptor));
}
extern "C" ANativeWindow* ANativeWindow_fromSurface(JNIEnv* env, jobject surface) {
    if (!env || !surface) return nullptr;
    jclass type = env->GetObjectClass(surface);
    jmethodID method = type ? env->GetMethodID(type, "shirazNativeWindow", "()J") : nullptr;
    if (!method || env->ExceptionCheck()) return nullptr;
    jlong window = env->CallLongMethod(surface, method);
    if (!window || env->ExceptionCheck()) return nullptr;
    auto* nativeWindow = reinterpret_cast<ANativeWindow*>(window);
    ANativeWindow_acquire(nativeWindow);
    return nativeWindow;
}
extern "C" jobject ANativeWindow_toSurface(JNIEnv* env, ANativeWindow* window) {
    if (!env || !window || !shiraz_window_known(window)) return nullptr;
    jclass type = env->FindClass("android/view/Surface");
    if (!type) return nullptr;
    jmethodID constructor = env->GetMethodID(type, "<init>", "(JII)V");
    if (!constructor) { env->DeleteLocalRef(type); return nullptr; }
    ANativeWindow_acquire(window);
    jobject surface = env->NewObject(type, constructor, reinterpret_cast<jlong>(window),
                                    ANativeWindow_getWidth(window), ANativeWindow_getHeight(window));
    env->DeleteLocalRef(type);
    if (!surface || env->ExceptionCheck()) { ANativeWindow_release(window); return nullptr; }
    return surface;
}
namespace {
struct Egl {
    void* (*proc)(const char*) = nullptr;
    EGLBoolean (*initialize)(EGLDisplay, EGLint*, EGLint*) = nullptr;
    EGLBoolean (*bind)(EGLenum) = nullptr;
    EGLContext (*createContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint*) = nullptr;
    EGLBoolean (*destroyContext)(EGLDisplay, EGLContext) = nullptr;
    EGLSurface (*createWindow)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*) = nullptr;
    EGLBoolean (*destroySurface)(EGLDisplay, EGLSurface) = nullptr;
    EGLBoolean (*make)(EGLDisplay, EGLSurface, EGLSurface, EGLContext) = nullptr;
    EGLBoolean (*swap)(EGLDisplay, EGLSurface) = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext root = EGL_NO_CONTEXT;
    bool ready = false;
};
std::mutex eglMutex;
std::unordered_map<ANativeWindow*, EGLSurface> surfaces;
Egl* egl() {
    static Egl state;
    std::lock_guard<std::mutex> guard(eglMutex);
    if (state.ready) return &state;
    void* library = dlopen("libEGL_shiraz.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!library) library = dlopen("libEGL.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!library) return nullptr;
    state.proc = reinterpret_cast<void* (*)(const char*)>(dlsym(library, "eglGetProcAddress"));
    state.initialize = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLint*, EGLint*)>(dlsym(library, "eglInitialize"));
    state.bind = reinterpret_cast<EGLBoolean (*)(EGLenum)>(dlsym(library, "eglBindAPI"));
    state.createContext = reinterpret_cast<EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*)>(dlsym(library, "eglCreateContext"));
    state.destroyContext = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLContext)>(dlsym(library, "eglDestroyContext"));
    if (!state.proc || !state.initialize || !state.bind || !state.createContext || !state.destroyContext) return nullptr;
    state.createWindow = reinterpret_cast<EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*)>(state.proc("eglCreateWindowSurface"));
    state.destroySurface = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface)>(state.proc("eglDestroySurface"));
    state.make = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)>(state.proc("eglMakeCurrent"));
    state.swap = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface)>(state.proc("eglSwapBuffers"));
    auto query = reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(state.proc("eglQueryDevicesEXT"));
    auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(state.proc("eglGetPlatformDisplay"));
    if (!state.createWindow || !state.destroySurface || !state.make || !state.swap || !query || !platform) return nullptr;
    EGLDeviceEXT devices[8]; EGLint count = 0;
    if (!query(8, devices, &count) || count < 1) return nullptr;
    state.display = platform(EGL_PLATFORM_DEVICE_EXT, devices[count - 1], nullptr);
    if (!state.initialize(state.display, nullptr, nullptr) || !state.bind(EGL_OPENGL_ES_API)) return nullptr;
    const EGLint attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    state.root = state.createContext(state.display, nullptr, EGL_NO_CONTEXT, attribs);
    if (state.root == EGL_NO_CONTEXT) return nullptr;
    state.ready = true;
    return &state;
}
EGLSurface windowSurface(Egl* e, ANativeWindow* window) {
    std::lock_guard<std::mutex> guard(eglMutex);
    auto found = surfaces.find(window);
    if (found != surfaces.end()) return found->second;
    EGLSurface surface = e->createWindow(e->display, nullptr, reinterpret_cast<EGLNativeWindowType>(window), nullptr);
    if (surface != EGL_NO_SURFACE) surfaces[window] = surface;
    return surface;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeEglCreateContext(JNIEnv*, jclass) {
    Egl* e = egl();
    if (!e) return 0;
    const EGLint attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    EGLContext context = e->createContext(e->display, nullptr, e->root, attribs);
    return context == EGL_NO_CONTEXT ? 0 : reinterpret_cast<jlong>(context);
}
extern "C" JNIEXPORT void JNICALL Java_android_view_Surface_nativeEglDestroyContext(JNIEnv*, jclass, jlong context) {
    Egl* e = egl();
    if (e && context) e->destroyContext(e->display, reinterpret_cast<EGLContext>(context));
}
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeEglCreateContextVersion(JNIEnv*, jclass, jint version) {
    Egl* e = egl();
    if (!e || version < 1 || version > 3) return 0;
    const EGLint attribs[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
    // ES1 fixed-function state cannot share the ES3 root's objects.
    EGLContext context = e->createContext(e->display, nullptr, version==1?EGL_NO_CONTEXT:e->root, attribs);
    if(std::getenv("SHIRAZ_TRACE_EGL"))std::fprintf(stderr,"Shiraz EGL context version=%d handle=%p\n",version,context);
    return context == EGL_NO_CONTEXT ? 0 : reinterpret_cast<jlong>(context);
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_view_Surface_nativeEglMakeCurrent(JNIEnv*, jclass, jlong window, jlong context) {
    Egl* e = egl();
    if (!e) return JNI_FALSE;
    if (!window || !context) return e->make(e->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT) ? JNI_TRUE : JNI_FALSE;
    EGLSurface surface = windowSurface(e, reinterpret_cast<ANativeWindow*>(window));
    if (surface == EGL_NO_SURFACE) return JNI_FALSE;
    return e->make(e->display, surface, surface, reinterpret_cast<EGLContext>(context)) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_view_Surface_nativeEglSwap(JNIEnv*, jclass, jlong window) {
    Egl* e = egl();
    if (!e || !window) return JNI_FALSE;
    EGLSurface surface = windowSurface(e, reinterpret_cast<ANativeWindow*>(window));
    return surface != EGL_NO_SURFACE && e->swap(e->display, surface) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL Java_android_view_Surface_nativeEglForgetWindow(JNIEnv*, jclass, jlong window) {
    if (!window) return;
    EGLSurface surface = EGL_NO_SURFACE;
    {
        std::lock_guard<std::mutex> guard(eglMutex);
        auto found = surfaces.find(reinterpret_cast<ANativeWindow*>(window));
        if (found == surfaces.end()) return;
        surface = found->second;
        surfaces.erase(found);
    }
    if (Egl* e = egl()) e->destroySurface(e->display, surface);
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_view_Surface_nativePostArgb(JNIEnv* env, jclass, jlong window, jintArray pixels) {
    if (!window || !pixels) return JNI_FALSE;
    jsize count = env->GetArrayLength(pixels);
    jint* data = env->GetIntArrayElements(pixels, nullptr);
    if (!data) return JNI_FALSE;
    int posted = shiraz_window_post_argb(reinterpret_cast<ANativeWindow*>(window), reinterpret_cast<const uint32_t*>(data), static_cast<uint64_t>(count));
    env->ReleaseIntArrayElements(pixels, data, JNI_ABORT);
    return posted == 0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_view_Surface_nativeCopyArgb(JNIEnv* env, jclass, jlong window, jintArray pixels) {
    if (!window || !pixels) return JNI_FALSE;
    jsize count = env->GetArrayLength(pixels);
    jint* data = env->GetIntArrayElements(pixels, nullptr);
    if (!data) return JNI_FALSE;
    int copied = shiraz_window_copy_argb(reinterpret_cast<ANativeWindow*>(window), reinterpret_cast<uint32_t*>(data), static_cast<uint64_t>(count));
    env->ReleaseIntArrayElements(pixels, data, copied == 0 ? 0 : JNI_ABORT);
    return copied == 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeWindowSize(JNIEnv*,jclass,jlong value){auto* window=reinterpret_cast<ANativeWindow*>(value);return (jlong(ANativeWindow_getWidth(window))<<32)|uint32_t(ANativeWindow_getHeight(window));}
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeWindowRevision(JNIEnv*,jclass,jlong value){return shiraz_window_revision(reinterpret_cast<ANativeWindow*>(value));}
