// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#if defined(__APPLE__)
#include "shiraz/darwin/window_queue.h"
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <string>
#else
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#if defined(__linux__)
#include "shiraz/linux/window_queue.h"
#include <cerrno>
#include <poll.h>
#include <unistd.h>
#endif
#endif
#include <jni.h>
#include <dlfcn.h>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {
#if defined(__APPLE__)
// macOS ANGLE Metal exposes EGL_ANGLE_metal_texture_client_buffer (EGL_METAL_TEXTURE_ANGLE)
// and GL_OES_EGL_image on GL_TEXTURE_2D (used by the M4b zero-copy IOSurface queue).
// Acquire queued IOSurface/MTLTexture slots zero-copy via EGLImage -> GL_TEXTURE_2D,
// and fall back to glTexImage2D for CPU-posted ANativeWindow frames (Canvas / ImageReader).
typedef unsigned int GLuint;
typedef unsigned int GLenum;
typedef int GLint;
typedef int GLsizei;
using DarwinEglDisplay = void*;
using DarwinEglContext = void*;
using DarwinEglImage = void*;
using DarwinEglSync = void*;
using DarwinEglInt = int32_t;
using DarwinEglAttrib = intptr_t;
using DarwinEglBool = unsigned int;

constexpr GLenum kGlNoError = 0;
constexpr GLenum kGlTexture2D = 0x0DE1;
constexpr GLenum kGlTextureBinding2D = 0x8069;
constexpr GLenum kGlRgba = 0x1908;
constexpr GLenum kGlUnsignedByte = 0x1401;
constexpr GLenum kGlTextureMagFilter = 0x2800;
constexpr GLenum kGlTextureMinFilter = 0x2801;
constexpr GLenum kGlTextureWrapS = 0x2802;
constexpr GLenum kGlTextureWrapT = 0x2803;
constexpr GLint kGlLinear = 0x2601;
constexpr GLint kGlClampToEdge = 0x812F;
constexpr DarwinEglInt kEglNone = 0x3038;
constexpr DarwinEglInt kEglMetalTextureAngle = 0x34A7;
constexpr DarwinEglInt kEglSyncMetalSharedEventAngle = 0x34D8;
constexpr DarwinEglInt kEglSyncMetalSharedEventObjectAngle = 0x34D9;
constexpr DarwinEglInt kEglSyncMetalSharedEventSignalValueLoAngle = 0x34DA;
constexpr DarwinEglInt kEglSyncMetalSharedEventSignalValueHiAngle = 0x34DB;
constexpr DarwinEglInt kEglSyncMetalSharedEventSignaledAngle = 0x34DC;
constexpr DarwinEglInt kEglSyncCondition = 0x30F8;

struct DarwinApi {
    DarwinEglContext (*getCurrentContext)() = nullptr;
    DarwinEglDisplay (*getCurrentDisplay)() = nullptr;
    void* (*getProcAddress)(const char*) = nullptr;
    DarwinEglImage (*createImage)(DarwinEglDisplay, DarwinEglContext, unsigned int, void*,
                                  const DarwinEglInt*) = nullptr;
    DarwinEglBool (*destroyImage)(DarwinEglDisplay, DarwinEglImage) = nullptr;
    DarwinEglSync (*createSync)(DarwinEglDisplay, unsigned int, const DarwinEglAttrib*) = nullptr;
    DarwinEglBool (*destroySync)(DarwinEglDisplay, DarwinEglSync) = nullptr;
    DarwinEglBool (*waitSync)(DarwinEglDisplay, DarwinEglSync, DarwinEglInt) = nullptr;
    void (*imageTargetTexture)(GLenum, void*) = nullptr;
    void (*bindTexture)(GLenum, GLuint) = nullptr;
    void (*deleteTextures)(GLsizei, const GLuint*) = nullptr;
    void (*texImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum,
                       const void*) = nullptr;
    void (*texParameteri)(GLenum, GLenum, GLint) = nullptr;
    GLenum (*getError)() = nullptr;
    void (*getIntegerv)(GLenum, GLint*) = nullptr;
    void (*flush)() = nullptr;

    void* resolveEgl(const char* sym) {
        if (void* p = dlsym(RTLD_DEFAULT, sym))
            return p;
        const char* const candidates[] = {"libEGL_shim.so", "libEGL.dylib", "libGLESv2.dylib"};
        for (const char* name : candidates) {
            if (void* lib = dlopen(name, RTLD_NOW | RTLD_NOLOAD)) {
                if (void* p = dlsym(lib, sym))
                    return p;
            }
        }
        return nullptr;
    }

    template <typename Fn> void resolveProc(Fn& out, const char* sym) {
        void* p = getProcAddress ? getProcAddress(sym) : nullptr;
        if (!p)
            p = resolveEgl(sym);
        out = reinterpret_cast<Fn>(p);
    }

    void refresh() {
        if (ready())
            return;
        getCurrentContext =
            reinterpret_cast<DarwinEglContext (*)()>(resolveEgl("eglGetCurrentContext"));
        getCurrentDisplay =
            reinterpret_cast<DarwinEglDisplay (*)()>(resolveEgl("eglGetCurrentDisplay"));
        getProcAddress = reinterpret_cast<void* (*)(const char*)>(resolveEgl("eglGetProcAddress"));
        resolveProc(createImage, "eglCreateImageKHR");
        resolveProc(destroyImage, "eglDestroyImageKHR");
        resolveProc(createSync, "eglCreateSync");
        resolveProc(destroySync, "eglDestroySync");
        resolveProc(waitSync, "eglWaitSync");
        resolveProc(imageTargetTexture, "glEGLImageTargetTexture2DOES");
        resolveProc(bindTexture, "glBindTexture");
        resolveProc(deleteTextures, "glDeleteTextures");
        resolveProc(texImage2D, "glTexImage2D");
        resolveProc(texParameteri, "glTexParameteri");
        resolveProc(getError, "glGetError");
        resolveProc(getIntegerv, "glGetIntegerv");
        resolveProc(flush, "glFlush");
    }

    bool ready() const {
        return getCurrentContext && getCurrentDisplay && bindTexture && deleteTextures &&
               texImage2D && texParameteri && getError && getIntegerv;
    }

    bool zeroCopyReady() const {
        return ready() && createImage && destroyImage && imageTargetTexture;
    }
};

DarwinApi& darwinApi() {
    static DarwinApi value;
    value.refresh();
    return value;
}

struct Texture {
    GLuint name = 0;
    bool attached;
    DarwinEglContext context = nullptr;
    DarwinEglDisplay display = nullptr;
    uint64_t revision = 0;
    uint64_t frameSerial = 0;
    ANativeWindow* consumerWindow = nullptr;
    struct SlotImage {
        uint64_t generation = 0;
        DarwinEglImage image = nullptr;
    } images[SHIRAZ_WINDOW_MAX_SLOTS];
    std::vector<uint32_t> pixels;
    Texture(GLuint value, bool enabled) : name(value), attached(enabled) {}
};

uint64_t darwinConsumerToken(const Texture* t) {
    return reinterpret_cast<uint64_t>(t);
}

void darwinReleaseImages(Texture* t) {
    DarwinApi& a = darwinApi();
    for (auto& slot : t->images) {
        if (slot.image && a.destroyImage && t->display)
            a.destroyImage(t->display, slot.image);
        slot.image = nullptr;
        slot.generation = 0;
    }
    if (t->consumerWindow) {
        shiraz_window_consumer_detach(t->consumerWindow, darwinConsumerToken(t));
        ANativeWindow_release(t->consumerWindow);
        t->consumerWindow = nullptr;
    }
    t->revision = 0;
}
#else
struct Api {
    void* library = nullptr;
    PFNEGLGETCURRENTCONTEXTPROC context = nullptr;
    PFNEGLGETCURRENTDISPLAYPROC display = nullptr;
    PFNEGLQUERYSTRINGPROC queryString = nullptr;
    PFNEGLCREATEIMAGEKHRPROC create = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC destroy = nullptr;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC target = nullptr;
    PFNEGLCREATESYNCKHRPROC createSync = nullptr;
    PFNEGLDESTROYSYNCKHRPROC destroySync = nullptr;
    PFNEGLWAITSYNCKHRPROC waitSync = nullptr;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dupFenceFd = nullptr;
    Api() {
        library = dlopen("libEGL_shiraz.so.1", RTLD_NOW | RTLD_LOCAL);
        if (!library)
            library = dlopen("libEGL_shim.so", RTLD_NOW | RTLD_LOCAL);
        if (!library)
            library = dlopen("libEGL.so.1", RTLD_NOW | RTLD_LOCAL);
        if (!library)
            return;
        context =
            reinterpret_cast<PFNEGLGETCURRENTCONTEXTPROC>(dlsym(library, "eglGetCurrentContext"));
        display =
            reinterpret_cast<PFNEGLGETCURRENTDISPLAYPROC>(dlsym(library, "eglGetCurrentDisplay"));
        queryString = reinterpret_cast<PFNEGLQUERYSTRINGPROC>(dlsym(library, "eglQueryString"));
        auto proc = reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(dlsym(library, "eglGetProcAddress"));
        if (proc) {
            create = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(proc("eglCreateImageKHR"));
            destroy = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(proc("eglDestroyImageKHR"));
            target = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
                proc("glEGLImageTargetTexture2DOES"));
            createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(proc("eglCreateSyncKHR"));
            destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(proc("eglDestroySyncKHR"));
            waitSync = reinterpret_cast<PFNEGLWAITSYNCKHRPROC>(proc("eglWaitSyncKHR"));
            dupFenceFd = reinterpret_cast<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>(
                proc("eglDupNativeFenceFDANDROID"));
        }
    }
    bool ready() const {
        return context && display && create && destroy && target;
    }
    bool hasExtension(EGLDisplay dpy, const char* name) const {
        if (!queryString || dpy == EGL_NO_DISPLAY)
            return false;
        const char* list = queryString(dpy, EGL_EXTENSIONS);
        if (!list)
            return false;
        const size_t len = std::strlen(name);
        for (const char* at = std::strstr(list, name); at; at = std::strstr(at + 1, name)) {
            if ((at == list || at[-1] == ' ') && (at[len] == ' ' || at[len] == 0))
                return true;
        }
        return false;
    }
};
Api& api() {
    static Api value;
    return value;
}
struct Texture {
    GLuint name = 0, source = 0;
    bool attached;
    EGLContext context = EGL_NO_CONTEXT;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLImageKHR image = EGL_NO_IMAGE_KHR;
    unsigned updates = 0;
    uint64_t revision = 0;
    int width = 0, height = 0;
    std::vector<uint32_t> pixels;
#if defined(__linux__)
    uint64_t frameSerial = 0;
    ANativeWindow* consumerWindow = nullptr;
    struct SlotImage {
        uint64_t generation = 0;
        EGLImageKHR image = EGL_NO_IMAGE_KHR;
    } slotImages[SHIRAZ_WINDOW_MAX_SLOTS];
#endif
    Texture(GLuint value, bool enabled) : name(value), attached(enabled) {}
};
#if defined(__linux__)
uint64_t linuxConsumerToken(const Texture* t) {
    return reinterpret_cast<uint64_t>(t);
}
void linuxReleaseSlots(Texture* t) {
    for (auto& slot : t->slotImages) {
        if (slot.image != EGL_NO_IMAGE_KHR && t->display != EGL_NO_DISPLAY)
            api().destroy(t->display, slot.image);
        slot.image = EGL_NO_IMAGE_KHR;
        slot.generation = 0;
    }
    if (t->consumerWindow) {
        shiraz_window_consumer_detach(t->consumerWindow, linuxConsumerToken(t));
        ANativeWindow_release(t->consumerWindow);
        t->consumerWindow = nullptr;
    }
}
#endif
#endif
void fail(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}
#if defined(__APPLE__)
bool current(JNIEnv* env, Texture* t) {
    DarwinApi& a = darwinApi();
    if (!a.ready() || !a.getCurrentContext()) {
        fail(env, "SurfaceTexture requires a current EGL context");
        return false;
    }
    if (t->context &&
        (t->context != a.getCurrentContext() || t->display != a.getCurrentDisplay())) {
        fail(env, "SurfaceTexture belongs to another EGL context");
        return false;
    }
    t->context = a.getCurrentContext();
    t->display = a.getCurrentDisplay();
    return true;
}
#else
bool current(JNIEnv* env, Texture* t) {
    Api& a = api();
    if (!a.ready() || a.context() == EGL_NO_CONTEXT) {
        fail(env, "SurfaceTexture requires a current EGL context");
        return false;
    }
    if (t->context != EGL_NO_CONTEXT && (t->context != a.context() || t->display != a.display())) {
        fail(env, "SurfaceTexture belongs to another EGL context");
        return false;
    }
    t->context = a.context();
    t->display = a.display();
    return true;
}
void imageRelease(Texture* t) {
#if defined(__linux__)
    linuxReleaseSlots(t);
#endif
    if (t->image != EGL_NO_IMAGE_KHR) {
        api().destroy(t->display, t->image);
        t->image = EGL_NO_IMAGE_KHR;
    }
    if (t->source) {
        glDeleteTextures(1, &t->source);
        t->source = 0;
    }
    t->revision = 0;
    t->width = t->height = 0;
}
#endif
} // namespace
extern "C" JNIEXPORT jlong JNICALL
Java_android_graphics_SurfaceTexture_nativeCreate(JNIEnv*, jclass, jint name, jboolean attached) {
    return reinterpret_cast<jlong>(new Texture(GLuint(name), attached));
}
#if defined(__APPLE__)
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeAttach(JNIEnv* env,
                                                                                    jclass,
                                                                                    jlong value,
                                                                                    jint name) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (t->attached) {
        fail(env, "SurfaceTexture is already attached");
        return;
    }
    if (!current(env, t))
        return;
    DarwinApi& a = darwinApi();
    GLint previous = 0;
    a.getIntegerv(kGlTextureBinding2D, &previous);
    a.bindTexture(kGlTexture2D, GLuint(name));
    const GLenum err = a.getError();
    a.bindTexture(kGlTexture2D, static_cast<GLuint>(previous));
    if (err != kGlNoError) {
        fail(env, "Cannot attach SurfaceTexture texture name");
        return;
    }
    t->name = GLuint(name);
    t->attached = true;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeDetach(JNIEnv* env,
                                                                                    jclass,
                                                                                    jlong value) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (!t->attached) {
        fail(env, "SurfaceTexture is already detached");
        return;
    }
    if (t->context) {
        if (!current(env, t))
            return;
        darwinReleaseImages(t);
        if (t->name)
            darwinApi().deleteTextures(1, &t->name);
    }
    t->name = 0;
    t->attached = false;
    t->context = nullptr;
    t->display = nullptr;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_android_graphics_SurfaceTexture_nativeUpdate(JNIEnv* env, jclass, jlong value, jlong window) {
    auto* t = reinterpret_cast<Texture*>(value);
    auto* w = reinterpret_cast<ANativeWindow*>(window);
    if (!t->attached) {
        fail(env, "SurfaceTexture is detached");
        return JNI_FALSE;
    }
    if (!current(env, t) || !w)
        return JNI_FALSE;
    DarwinApi& a = darwinApi();
    // 1. Prefer zero-copy IOSurface / MTLTexture slot import via EGL_METAL_TEXTURE_ANGLE.
    if (a.zeroCopyReady() && shiraz_zero_copy_mode() != 0) {
        ShirazWindowSlot slot{};
        const int32_t acq =
            shiraz_window_acquire_slot(w, darwinConsumerToken(t), ++t->frameSerial, &slot);
        if (acq == 0 && slot.texture && slot.index < SHIRAZ_WINDOW_MAX_SLOTS) {
            if (t->consumerWindow != w) {
                if (t->consumerWindow) {
                    shiraz_window_consumer_detach(t->consumerWindow, darwinConsumerToken(t));
                    ANativeWindow_release(t->consumerWindow);
                }
                ANativeWindow_acquire(w);
                t->consumerWindow = w;
            }
            const bool changed = !slot.revision || slot.revision != t->revision;
            if (changed) {
                if (slot.event && slot.value && !shiraz_event_signaled(slot.event, slot.value)) {
                    if (a.createSync && a.waitSync && a.destroySync) {
                        const DarwinEglAttrib attrs[] = {
                            kEglSyncMetalSharedEventObjectAngle,
                            reinterpret_cast<DarwinEglAttrib>(slot.event),
                            kEglSyncMetalSharedEventSignalValueLoAngle,
                            static_cast<DarwinEglAttrib>(slot.value & 0xffffffffu),
                            kEglSyncMetalSharedEventSignalValueHiAngle,
                            static_cast<DarwinEglAttrib>(slot.value >> 32),
                            kEglSyncCondition,
                            kEglSyncMetalSharedEventSignaledAngle,
                            kEglNone};
                        if (DarwinEglSync sync =
                                a.createSync(t->display, kEglSyncMetalSharedEventAngle, attrs)) {
                            a.waitSync(t->display, sync, 0);
                            a.destroySync(t->display, sync);
                        }
                    } else {
                        shiraz_event_cpu_wait(slot.event, slot.value, 16);
                    }
                }
                auto& cached = t->images[slot.index];
                if (cached.generation != slot.generation || !cached.image) {
                    if (cached.image)
                        a.destroyImage(t->display, cached.image);
                    const DarwinEglInt none[] = {kEglNone};
                    cached.image = a.createImage(t->display, nullptr, kEglMetalTextureAngle,
                                                 slot.texture, none);
                    cached.generation = cached.image ? slot.generation : 0;
                }
                if (cached.image) {
                    GLint previous = 0;
                    a.getIntegerv(kGlTextureBinding2D, &previous);
                    a.bindTexture(kGlTexture2D, t->name);
                    a.imageTargetTexture(kGlTexture2D, cached.image);
                    a.texParameteri(kGlTexture2D, kGlTextureMinFilter, kGlLinear);
                    a.texParameteri(kGlTexture2D, kGlTextureMagFilter, kGlLinear);
                    a.texParameteri(kGlTexture2D, kGlTextureWrapS, kGlClampToEdge);
                    a.texParameteri(kGlTexture2D, kGlTextureWrapT, kGlClampToEdge);
                    a.bindTexture(kGlTexture2D, static_cast<GLuint>(previous));
                    if (a.flush)
                        a.flush();
                    shiraz_window_mark_sampled(w, darwinConsumerToken(t), slot.index,
                                               slot.generation, 0);
                    t->revision = slot.revision;
                    return JNI_TRUE;
                }
            }
            shiraz_window_mark_sampled(w, darwinConsumerToken(t), slot.index, slot.generation, 0);
            if (!changed)
                return JNI_FALSE;
        }
    }
    // 2. CPU-backed ANativeWindow frame upload (Canvas, ImageReader, VideoToolbox CPU copies).
    const uint64_t revision = shiraz_window_revision(w);
    if (!revision || revision == t->revision)
        return JNI_FALSE;
    const int width = ANativeWindow_getWidth(w);
    const int height = ANativeWindow_getHeight(w);
    if (width < 1 || height < 1 || width > 8192 || height > 8192 ||
        uint64_t(width) * height > 16 * 1024 * 1024) {
        fail(env, "Invalid SurfaceTexture frame dimensions");
        return JNI_FALSE;
    }
    t->pixels.resize(size_t(width) * height);
    if (shiraz_window_copy_argb(w, t->pixels.data(), t->pixels.size()) ||
        revision != shiraz_window_revision(w))
        return JNI_FALSE;
    for (auto& p : t->pixels)
        p = (p & 0xff00ff00u) | ((p & 255) << 16) | ((p >> 16) & 255);
    GLint previous = 0;
    a.getIntegerv(kGlTextureBinding2D, &previous);
    a.bindTexture(kGlTexture2D, t->name);
    a.texImage2D(kGlTexture2D, 0, kGlRgba, width, height, 0, kGlRgba, kGlUnsignedByte,
                 t->pixels.data());
    a.texParameteri(kGlTexture2D, kGlTextureMinFilter, kGlLinear);
    a.texParameteri(kGlTexture2D, kGlTextureMagFilter, kGlLinear);
    a.texParameteri(kGlTexture2D, kGlTextureWrapS, kGlClampToEdge);
    a.texParameteri(kGlTexture2D, kGlTextureWrapT, kGlClampToEdge);
    a.bindTexture(kGlTexture2D, static_cast<GLuint>(previous));
    t->revision = revision;
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeRelease(JNIEnv*,
                                                                                     jclass,
                                                                                     jlong value) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (!t)
        return;
    darwinReleaseImages(t);
    delete t;
}
#else
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeAttach(JNIEnv* env,
                                                                                    jclass,
                                                                                    jlong value,
                                                                                    jint name) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (t->attached) {
        fail(env, "SurfaceTexture is already attached");
        return;
    }
    if (!current(env, t))
        return;
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, GLuint(name));
    if (glGetError() != GL_NO_ERROR) {
        fail(env, "Cannot attach external texture name");
        return;
    }
    t->name = GLuint(name);
    t->attached = true;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeDetach(JNIEnv* env,
                                                                                    jclass,
                                                                                    jlong value) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (!t->attached) {
        fail(env, "SurfaceTexture is already detached");
        return;
    }
    // Construction may happen without an EGL context; no GL objects exist yet.
    if (t->context != EGL_NO_CONTEXT) {
        if (!current(env, t))
            return;
        imageRelease(t);
        if (t->name)
            glDeleteTextures(1, &t->name);
    }
    t->name = 0;
    t->attached = false;
    t->context = EGL_NO_CONTEXT;
    t->display = EGL_NO_DISPLAY;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_android_graphics_SurfaceTexture_nativeUpdate(JNIEnv* env, jclass, jlong value, jlong window) {
    auto* t = reinterpret_cast<Texture*>(value);
    auto* w = reinterpret_cast<ANativeWindow*>(window);
    if (!t->attached) {
        fail(env, "SurfaceTexture is detached");
        return false;
    }
    if (!current(env, t))
        return false;
    uint64_t revision = shiraz_window_revision(w);
    if (!revision || revision == t->revision)
        return false;
#if defined(__linux__)
    // Zero-copy Linux DMA-BUF path: acquire queued GBM/DMA-BUF slot directly into
    // GL_TEXTURE_EXTERNAL_OES via EGL_LINUX_DMA_BUF_EXT + EGL_ANDROID_native_fence_sync.
    if (shiraz_zero_copy_mode() != 0 &&
        api().hasExtension(t->display, "EGL_EXT_image_dma_buf_import")) {
        ShirazWindowSlot slot{};
        const uint64_t token = linuxConsumerToken(t);
        int32_t acq = shiraz_window_acquire_slot(w, token, ++t->frameSerial, &slot);
        if (acq == 0 && slot.buffer) {
            int32_t fenceFd = slot.fence_fd;
            slot.fence_fd = -1;
            if (t->consumerWindow != w) {
                linuxReleaseSlots(t);
                ANativeWindow_acquire(w);
                t->consumerWindow = w;
            }
            bool ok = true;
            auto& slotImg = t->slotImages[slot.index];
            if (slotImg.generation != slot.generation) {
                if (slotImg.image != EGL_NO_IMAGE_KHR) {
                    api().destroy(t->display, slotImg.image);
                    slotImg.image = EGL_NO_IMAGE_KHR;
                }
                uint32_t fourcc = 0, pitch = 0;
                int dmaFd = shiraz_hardware_buffer_export(slot.buffer, &fourcc, &pitch);
                if (dmaFd >= 0) {
                    const EGLint dmaAttribs[] = {EGL_WIDTH,
                                                 EGLint(slot.width),
                                                 EGL_HEIGHT,
                                                 EGLint(slot.height),
                                                 EGL_LINUX_DRM_FOURCC_EXT,
                                                 EGLint(fourcc),
                                                 EGL_DMA_BUF_PLANE0_FD_EXT,
                                                 dmaFd,
                                                 EGL_DMA_BUF_PLANE0_OFFSET_EXT,
                                                 0,
                                                 EGL_DMA_BUF_PLANE0_PITCH_EXT,
                                                 EGLint(pitch),
                                                 EGL_NONE};
                    slotImg.image = api().create(t->display, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT,
                                                 nullptr, dmaAttribs);
                    close(dmaFd);
                }
                slotImg.generation = slotImg.image == EGL_NO_IMAGE_KHR ? 0 : slot.generation;
                if (slotImg.image == EGL_NO_IMAGE_KHR)
                    ok = false;
            }
            const bool hasFence = api().createSync && api().destroySync && api().waitSync &&
                                  api().dupFenceFd &&
                                  api().hasExtension(t->display, "EGL_ANDROID_native_fence_sync") &&
                                  api().hasExtension(t->display, "EGL_KHR_wait_sync");
            if (ok && fenceFd >= 0) {
                bool waitedGpu = false;
                if (hasFence) {
                    constexpr EGLenum kNativeFence = 0x3144;
                    constexpr EGLint kNativeFenceFd = 0x3145;
                    const EGLint attribs[] = {kNativeFenceFd, fenceFd, EGL_NONE};
                    EGLSyncKHR sync = api().createSync(t->display, kNativeFence, attribs);
                    if (sync != EGL_NO_SYNC_KHR) {
                        fenceFd = -1;
                        api().waitSync(t->display, sync, 0);
                        api().destroySync(t->display, sync);
                        waitedGpu = true;
                    }
                }
                if (!waitedGpu && fenceFd >= 0) {
                    pollfd pf{fenceFd, POLLIN, 0};
                    while (poll(&pf, 1, 1000) < 0 && errno == EINTR) {
                    }
                    close(fenceFd);
                    fenceFd = -1;
                }
            } else if (fenceFd >= 0) {
                close(fenceFd);
                fenceFd = -1;
            }
            if (ok) {
                glBindTexture(GL_TEXTURE_EXTERNAL_OES, t->name);
                api().target(GL_TEXTURE_EXTERNAL_OES, slotImg.image);
                if (glGetError() != GL_NO_ERROR)
                    ok = false;
            }
            int32_t releaseFd = -1;
            if (ok && hasFence) {
                constexpr EGLenum kNativeFence = 0x3144;
                EGLSyncKHR sync = api().createSync(t->display, kNativeFence, nullptr);
                if (sync != EGL_NO_SYNC_KHR) {
                    glFlush();
                    releaseFd = api().dupFenceFd(t->display, sync);
                    api().destroySync(t->display, sync);
                }
            }
            if (ok && releaseFd < 0)
                glFlush();
            shiraz_window_mark_sampled(w, token, slot.index, slot.generation, releaseFd);
            if (ok) {
                t->width = slot.width;
                t->height = slot.height;
                t->revision = slot.revision;
                shiraz_zero_copy_count("surface_texture_zero_copy", 1);
                if (std::getenv("SHIRAZ_TRACE_VIDEO") && ++t->updates <= 8)
                    std::fprintf(stderr,
                                 "SHIRAZ_TEXTURE_FRAME zero_copy=1 updates=%u revision=%llu\n",
                                 t->updates, static_cast<unsigned long long>(slot.revision));
                return true;
            }
        }
    }
#endif
    int width = ANativeWindow_getWidth(w), height = ANativeWindow_getHeight(w);
    if (width < 1 || height < 1 || width > 8192 || height > 8192 ||
        uint64_t(width) * height > 16 * 1024 * 1024) {
        fail(env, "Invalid SurfaceTexture frame dimensions");
        return false;
    }
    t->pixels.resize(size_t(width) * height);
    if (shiraz_window_copy_argb(w, t->pixels.data(), t->pixels.size()) ||
        revision != shiraz_window_revision(w))
        return false;
    for (auto& p : t->pixels)
        p = (p & 0xff00ff00u) | ((p & 255) << 16) | ((p >> 16) & 255);
    // Reserve the external name before allocating the private upload texture.
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, t->name);
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    bool es3 = version && strstr(version, "OpenGL ES 3");
    GLint unpack = 0, row = 0, skipRows = 0, skipPixels = 0;
    if (es3) {
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &unpack);
        glGetIntegerv(GL_UNPACK_ROW_LENGTH, &row);
        glGetIntegerv(GL_UNPACK_SKIP_ROWS, &skipRows);
        glGetIntegerv(GL_UNPACK_SKIP_PIXELS, &skipPixels);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
    }
    GLint binding = 0, alignment = 4;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &binding);
    glGetIntegerv(GL_UNPACK_ALIGNMENT, &alignment);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    bool resized =
        !t->source || t->image == EGL_NO_IMAGE_KHR || width != t->width || height != t->height;
    if (resized) {
        imageRelease(t);
        glGenTextures(1, &t->source);
    }
    glBindTexture(GL_TEXTURE_2D, t->source);
    if (resized) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                     t->pixels.data());
    } else
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE,
                        t->pixels.data());
    glBindTexture(GL_TEXTURE_2D, GLuint(binding));
    glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
    if (es3) {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, GLuint(unpack));
        glPixelStorei(GL_UNPACK_ROW_LENGTH, row);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
    }
    if (resized) {
        const EGLint attrs[] = {EGL_GL_TEXTURE_LEVEL_KHR, 0, EGL_IMAGE_PRESERVED_KHR, EGL_TRUE,
                                EGL_NONE};
        t->image = api().create(t->display, t->context, EGL_GL_TEXTURE_2D_KHR,
                                reinterpret_cast<EGLClientBuffer>(uintptr_t(t->source)), attrs);
    }
    if (t->image == EGL_NO_IMAGE_KHR) {
        fail(env, "Cannot create SurfaceTexture EGL image");
        return false;
    }
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, t->name);
    api().target(GL_TEXTURE_EXTERNAL_OES, t->image);
    if (glGetError() != GL_NO_ERROR) {
        fail(env, "Cannot bind SurfaceTexture external image");
        return false;
    }
    t->width = width;
    t->height = height;
    t->revision = revision;
    if (std::getenv("SHIRAZ_TRACE_VIDEO") && ++t->updates <= 8)
        std::fprintf(stderr, "SHIRAZ_TEXTURE_FRAME updates=%u revision=%llu\n", t->updates,
                     static_cast<unsigned long long>(revision));
    return true;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeRelease(JNIEnv*,
                                                                                     jclass,
                                                                                     jlong value) {
    auto* t = reinterpret_cast<Texture*>(value);
    if (!t)
        return;
#if defined(__linux__)
    linuxReleaseSlots(t);
#endif
    if (t->image != EGL_NO_IMAGE_KHR)
        api().destroy(t->display, t->image);
    // GL names owned by a different context are reclaimed with that context.
    if (t->context != EGL_NO_CONTEXT && api().context() == t->context &&
        api().display() == t->display) {
        if (t->source)
            glDeleteTextures(1, &t->source);
    }
    delete t;
}
#endif
