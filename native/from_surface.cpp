// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#if defined(__APPLE__)
// macOS EGL is ANGLE (Metal) behind libEGL_shim, both loaded at run time
// (docs/macos_final_goal.md, M4). libandroid itself never links them, so only
// the handful of EGL types and constants the dispatch table names are needed.
#include <cstdint>
#include <thread>
typedef unsigned int EGLBoolean;typedef unsigned int EGLenum;typedef int32_t EGLint;
typedef void* EGLDisplay;typedef void* EGLContext;typedef void* EGLSurface;typedef void* EGLConfig;typedef void* EGLNativeWindowType;
#define EGL_NO_DISPLAY ((EGLDisplay)0)
#define EGL_NO_CONTEXT ((EGLContext)0)
#define EGL_NO_SURFACE ((EGLSurface)0)
#define EGL_NONE 0x3038
#define EGL_RENDERABLE_TYPE 0x3040
#define EGL_OPENGL_ES_BIT 0x0001
#define EGL_OPENGL_ES_API 0x30A0
#define EGL_PLATFORM_ANGLE_ANGLE 0x3202
#define EGL_PLATFORM_ANGLE_TYPE_ANGLE 0x3203
#define EGL_PLATFORM_ANGLE_TYPE_METAL_ANGLE 0x3489
#define EGL_PLATFORM_ANGLE_DISPLAY_KEY_ANGLE 0x34DC
#define SHIRAZ_SURFACE_DISPLAY_KEY 0x53484952 // "SHIR": any nonzero value distinct from the default (0)
#define EGL_CONTEXT_MAJOR_VERSION 0x3098
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#else
#include <EGL/egl.h>
#include <EGL/eglext.h>
#endif
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
    return shiraz_window_resize(reinterpret_cast<ANativeWindow*>(window), width, height) == 0;
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
    if (type) env->DeleteLocalRef(type);
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
    void* library = nullptr;
    EGLBoolean (*terminate)(EGLDisplay) = nullptr;
    bool initialized = false;
    // Failed attempts own all partial state. The successful backend stays loaded
    // for process lifetime because native callers may retain its entry points.
    ~Egl() {
        if (ready) return;
        if (initialized && terminate) terminate(display);
        if (library) dlclose(library);
    }
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
#if defined(__APPLE__)
    // ANGLE rejects an ES 1 context without a config (EGL_BAD_MATCH), even with
    // EGL_KHR_no_config_context; ES 2/3 contexts keep EGL_NO_CONFIG as on Linux.
    EGLConfig es1Config = nullptr;
#endif
};
std::mutex eglMutex;
std::unordered_map<ANativeWindow*, EGLSurface> surfaces;
Egl* egl() {
    static Egl backend;
    std::lock_guard<std::mutex> guard(eglMutex);
    if (backend.ready) return &backend;
    Egl state;
#if defined(__APPLE__)
    // The shim beside this library (build/macos); it reaches ANGLE through
    // SHIRAZ_EGL_LIBRARY. dyld resolves @loader_path against libandroid.so.
    void* library = dlopen("@loader_path/libEGL_shim.so", RTLD_NOW | RTLD_LOCAL);
#else
    void* library = dlopen("libEGL_shiraz.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!library) library = dlopen("libEGL.so.1", RTLD_NOW | RTLD_LOCAL);
#endif
    if (!library) return nullptr;
    state.library = library;
    state.terminate = reinterpret_cast<EGLBoolean (*)(EGLDisplay)>(dlsym(library, "eglTerminate"));
    state.proc = reinterpret_cast<void* (*)(const char*)>(dlsym(library, "eglGetProcAddress"));
    state.initialize = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLint*, EGLint*)>(dlsym(library, "eglInitialize"));
    state.bind = reinterpret_cast<EGLBoolean (*)(EGLenum)>(dlsym(library, "eglBindAPI"));
    state.createContext = reinterpret_cast<EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*)>(dlsym(library, "eglCreateContext"));
    state.destroyContext = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLContext)>(dlsym(library, "eglDestroyContext"));
    if (!state.terminate || !state.proc || !state.initialize || !state.bind || !state.createContext || !state.destroyContext) return nullptr;
    state.createWindow = reinterpret_cast<EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*)>(state.proc("eglCreateWindowSurface"));
    state.destroySurface = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface)>(state.proc("eglDestroySurface"));
    state.make = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)>(state.proc("eglMakeCurrent"));
    state.swap = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface)>(state.proc("eglSwapBuffers"));
#if defined(__APPLE__)
    // ANGLE's Metal backend has no EGL_EXT_device_query. Take a Metal display
    // of this backend's own: ANGLE keys displays by their attributes, so
    // EGL_PLATFORM_ANGLE_DISPLAY_KEY_ANGLE keeps it apart from the default
    // display, which SDL's Cocoa EGL renderer uses and eglTerminates when
    // presentation ends (that killed this root context and every later
    // eglCreateContext with EGL_BAD_DISPLAY; build/macos/m4/fix/pa5). As on
    // Linux, app contexts need no objects from SDL's context.
    using PlatformDisplay = EGLDisplay (*)(EGLenum, void*, const EGLint*);
    auto platform = reinterpret_cast<PlatformDisplay>(state.proc("eglGetPlatformDisplayEXT"));
    if (!state.createWindow || !state.destroySurface || !state.make || !state.swap || !platform) return nullptr;
    const EGLint displayAttributes[] = {EGL_PLATFORM_ANGLE_TYPE_ANGLE, EGL_PLATFORM_ANGLE_TYPE_METAL_ANGLE,
                                        EGL_PLATFORM_ANGLE_DISPLAY_KEY_ANGLE, SHIRAZ_SURFACE_DISPLAY_KEY, EGL_NONE};
    state.display = platform(EGL_PLATFORM_ANGLE_ANGLE, nullptr, displayAttributes);
    if (state.display == EGL_NO_DISPLAY) return nullptr;
#else
    auto query = reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(state.proc("eglQueryDevicesEXT"));
    auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(state.proc("eglGetPlatformDisplay"));
    if (!state.createWindow || !state.destroySurface || !state.make || !state.swap || !query || !platform) return nullptr;
    EGLDeviceEXT devices[8]; EGLint count = 0;
    if (!query(8, devices, &count) || count < 1) return nullptr;
    state.display = platform(EGL_PLATFORM_DEVICE_EXT, devices[count - 1], nullptr);
#endif
    if (!state.initialize(state.display, nullptr, nullptr)) return nullptr;
#if defined(__APPLE__)
    // The display is never terminated: ANGLE returns this same display to any
    // later caller with these attributes, and eglTerminate would end it for all.
    auto choose = reinterpret_cast<EGLBoolean (*)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*)>(dlsym(library, "eglChooseConfig"));
    const EGLint es1[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT, EGL_NONE};
    EGLint configs = 0;
    if (!choose || !choose(state.display, es1, &state.es1Config, 1, &configs) || configs < 1) state.es1Config = nullptr;
#else
    state.initialized = true;
#endif
    if (!state.bind(EGL_OPENGL_ES_API)) return nullptr;
    const EGLint attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    state.root = state.createContext(state.display, nullptr, EGL_NO_CONTEXT, attribs);
    if (state.root == EGL_NO_CONTEXT) return nullptr;
#if defined(__APPLE__)
    // ANGLE (Metal) completes display setup on the display's first
    // eglMakeCurrent, and GL calls on a second context can race it: two
    // GLSurfaceView threads building their window framebuffers concurrently
    // got GL_INVALID_ENUM / GL_INVALID_VALUE from glRenderbufferStorage and an
    // incomplete framebuffer (build/macos/m4/integrate/diag/directfbo.cpp;
    // no failure once one context had been current first). Make the root
    // current once, on a helper thread so the caller's binding is untouched,
    // before eglMutex lets any context of this backend be created.
    bool warmed = false;
    std::thread([&] {
        warmed = state.make(state.display, EGL_NO_SURFACE, EGL_NO_SURFACE, state.root);
        if (warmed) state.make(state.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }).join();
    if (!warmed) { state.destroyContext(state.display, state.root); return nullptr; }
#endif
    state.ready = true;
    backend = state;
    return &backend;
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
#if defined(__APPLE__)
    // Unshared, as an app asks with EGL_NO_CONTEXT; an explicit share_context
    // goes through nativeEglCreateSharedContext. Sharing the root put every app
    // context in one share group, and two ANGLE Metal contexts of one group
    // deadlocked reading back on two GL threads (build/macos/m4/race/,
    // build/macos/m4/fix/f2/).
    EGLContext context = e->createContext(e->display, nullptr, EGL_NO_CONTEXT, attribs);
#else
    EGLContext context = e->createContext(e->display, nullptr, e->root, attribs);
#endif
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
#if defined(__APPLE__)
    EGLContext context = e->createContext(e->display, version==1?e->es1Config:nullptr, EGL_NO_CONTEXT, attribs);
#else
    EGLContext context = e->createContext(e->display, nullptr, version==1?EGL_NO_CONTEXT:e->root, attribs);
#endif
    if(std::getenv("SHIRAZ_TRACE_EGL"))std::fprintf(stderr,"Shiraz EGL context version=%d handle=%p\n",version,context);
    return context == EGL_NO_CONTEXT ? 0 : reinterpret_cast<jlong>(context);
}
#if defined(__APPLE__)
// An app context sharing objects with another app context (HostEgl10
// eglCreateContext's share_context). App contexts are otherwise unshared here.
extern "C" JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeEglCreateSharedContext(JNIEnv*, jclass, jint version, jlong share) {
    Egl* e = egl();
    if (!e || version < 1 || version > 3 || !share) return 0;
    const EGLint attribs[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
    EGLContext context = e->createContext(e->display, version==1?e->es1Config:nullptr, reinterpret_cast<EGLContext>(share), attribs);
    if(std::getenv("SHIRAZ_TRACE_EGL"))std::fprintf(stderr,"Shiraz EGL context version=%d share=%p handle=%p\n",version,reinterpret_cast<void*>(share),context);
    return context == EGL_NO_CONTEXT ? 0 : reinterpret_cast<jlong>(context);
}
#endif
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
