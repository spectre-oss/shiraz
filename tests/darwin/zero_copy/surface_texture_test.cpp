// SPDX-License-Identifier: GPL-3.0-only
// Live ANGLE Metal test for macOS SurfaceTexture:
// 1. Zero-copy IOSurface / MTLTexture import via EGL_METAL_TEXTURE_ANGLE -> GL_TEXTURE_2D.
// 2. CPU-posted ANativeWindow fallback via glTexImage2D.
// 3. Attach / deduplicated update / detach / release lifecycle.
#include "shiraz/native_window.h"
#include <jni.h>
#define EGL_EGL_PROTOTYPES 0
#define GL_GLES_PROTOTYPES 0
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

extern "C" {
jlong Java_android_graphics_SurfaceTexture_nativeCreate(JNIEnv*, jclass, jint, jboolean);
void Java_android_graphics_SurfaceTexture_nativeAttach(JNIEnv*, jclass, jlong, jint);
void Java_android_graphics_SurfaceTexture_nativeDetach(JNIEnv*, jclass, jlong);
jboolean Java_android_graphics_SurfaceTexture_nativeUpdate(JNIEnv*, jclass, jlong, jlong);
void Java_android_graphics_SurfaceTexture_nativeRelease(JNIEnv*, jclass, jlong);
}

namespace {

struct FakeJni {
    JNINativeInterface_ table{};
    JNIEnv env{};
    int exceptions = 0;
    std::string lastMessage;

    static FakeJni* self(JNIEnv* e) {
        return reinterpret_cast<FakeJni*>(reinterpret_cast<char*>(e) - offsetof(FakeJni, env));
    }
    static jclass findClass(JNIEnv*, const char*) {
        return reinterpret_cast<jclass>(0x1);
    }
    static jint throwNew(JNIEnv* e, jclass, const char* msg) {
        auto* f = self(e);
        ++f->exceptions;
        f->lastMessage = msg ? msg : "";
        return 0;
    }
    static void deleteLocalRef(JNIEnv*, jobject) {}

    FakeJni() {
        table.FindClass = findClass;
        table.ThrowNew = throwNew;
        table.DeleteLocalRef = deleteLocalRef;
        env.functions = &table;
    }
};

void* openLib(const std::string& path) {
    void* h = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        std::fprintf(stderr, "dlopen %s failed: %s\n", path.c_str(), dlerror());
        std::exit(2);
    }
    return h;
}

template <typename T> T resolve(void* lib, const char* name) {
    void* p = dlsym(lib, name);
    if (!p) {
        std::fprintf(stderr, "dlsym %s failed: %s\n", name, dlerror());
        std::exit(2);
    }
    return reinterpret_cast<T>(p);
}

bool nearByte(uint8_t a, uint8_t b) {
    return std::abs(int(a) - int(b)) <= 2;
}

} // namespace

int main(int argc, char** argv) {
    if (argc < 2) {
        std::fprintf(stderr, "usage: %s <macos-build-dir>\n", argv[0]);
        return 2;
    }
    const std::string dir = argv[1];
    void* gles = openLib(dir + "/libGLESv2.dylib");
    void* shim = openLib(dir + "/libEGL_shim.so");

    auto pGetDisplay = resolve<PFNEGLGETDISPLAYPROC>(shim, "eglGetDisplay");
    auto pInitialize = resolve<PFNEGLINITIALIZEPROC>(shim, "eglInitialize");
    auto pBindAPI = resolve<PFNEGLBINDAPIPROC>(shim, "eglBindAPI");
    auto pChooseConfig = resolve<PFNEGLCHOOSECONFIGPROC>(shim, "eglChooseConfig");
    auto pCreateContext = resolve<PFNEGLCREATECONTEXTPROC>(shim, "eglCreateContext");
    auto pCreatePbuffer = resolve<PFNEGLCREATEPBUFFERSURFACEPROC>(shim, "eglCreatePbufferSurface");
    auto pCreateWindow = resolve<PFNEGLCREATEWINDOWSURFACEPROC>(shim, "eglCreateWindowSurface");
    auto pMakeCurrent = resolve<PFNEGLMAKECURRENTPROC>(shim, "eglMakeCurrent");
    auto pSwapBuffers = resolve<PFNEGLSWAPBUFFERSPROC>(shim, "eglSwapBuffers");
    auto pDestroySurface = resolve<PFNEGLDESTROYSURFACEPROC>(shim, "eglDestroySurface");
    auto pDestroyContext = resolve<PFNEGLDESTROYCONTEXTPROC>(shim, "eglDestroyContext");
    auto pTerminate = resolve<PFNEGLTERMINATEPROC>(shim, "eglTerminate");

    auto pGenTextures = resolve<PFNGLGENTEXTURESPROC>(gles, "glGenTextures");
    auto pGenFramebuffers = resolve<PFNGLGENFRAMEBUFFERSPROC>(gles, "glGenFramebuffers");
    auto pBindFramebuffer = resolve<PFNGLBINDFRAMEBUFFERPROC>(gles, "glBindFramebuffer");
    auto pFramebufferTexture2D =
        resolve<PFNGLFRAMEBUFFERTEXTURE2DPROC>(gles, "glFramebufferTexture2D");
    auto pCheckFramebufferStatus =
        resolve<PFNGLCHECKFRAMEBUFFERSTATUSPROC>(gles, "glCheckFramebufferStatus");
    auto pDeleteFramebuffers = resolve<PFNGLDELETEFRAMEBUFFERSPROC>(gles, "glDeleteFramebuffers");
    auto pClearColor = resolve<PFNGLCLEARCOLORPROC>(gles, "glClearColor");
    auto pClear = resolve<PFNGLCLEARPROC>(gles, "glClear");
    auto pReadPixels = resolve<PFNGLREADPIXELSPROC>(gles, "glReadPixels");
    auto pGetError = resolve<PFNGLGETERRORPROC>(gles, "glGetError");
    auto pViewport = resolve<PFNGLVIEWPORTPROC>(gles, "glViewport");

    EGLDisplay dpy = pGetDisplay(EGL_DEFAULT_DISPLAY);
    assert(dpy != EGL_NO_DISPLAY);
    EGLint major = 0, minor = 0;
    assert(pInitialize(dpy, &major, &minor) == EGL_TRUE);
    assert(pBindAPI(EGL_OPENGL_ES_API) == EGL_TRUE);

    const EGLint cfgAttrs[] = {EGL_SURFACE_TYPE,
                               EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
                               EGL_RENDERABLE_TYPE,
                               EGL_OPENGL_ES3_BIT,
                               EGL_RED_SIZE,
                               8,
                               EGL_GREEN_SIZE,
                               8,
                               EGL_BLUE_SIZE,
                               8,
                               EGL_ALPHA_SIZE,
                               8,
                               EGL_NONE};
    EGLConfig cfg = nullptr;
    EGLint numCfg = 0;
    assert(pChooseConfig(dpy, cfgAttrs, &cfg, 1, &numCfg) == EGL_TRUE && numCfg >= 1);

    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext consumerCtx = pCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttrs);
    EGLContext producerCtx = pCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttrs);
    assert(consumerCtx != EGL_NO_CONTEXT && producerCtx != EGL_NO_CONTEXT);

    const EGLint pbAttrs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    EGLSurface pb = pCreatePbuffer(dpy, cfg, pbAttrs);
    assert(pb != EGL_NO_SURFACE);

    FakeJni jni;

    // Without a current EGL context, attach must throw IllegalStateException cleanly.
    jlong stNoCtx = Java_android_graphics_SurfaceTexture_nativeCreate(&jni.env, nullptr, 0, false);
    Java_android_graphics_SurfaceTexture_nativeAttach(&jni.env, nullptr, stNoCtx, 1);
    assert(jni.exceptions == 1);
    Java_android_graphics_SurfaceTexture_nativeRelease(&jni.env, nullptr, stNoCtx);
    jni.exceptions = 0;

    // 1. Produce a real zero-copy IOSurface frame via ANativeWindow + ANGLE Metal.
    ANativeWindow* zcWin = shiraz_window_create(16, 16);
    assert(zcWin != nullptr);
    EGLSurface winSurf = pCreateWindow(dpy, cfg, zcWin, nullptr);
    assert(winSurf != EGL_NO_SURFACE);
    assert(pMakeCurrent(dpy, winSurf, winSurf, producerCtx) == EGL_TRUE);
    pViewport(0, 0, 16, 16);
    pClearColor(0x20 / 255.0f, 0x80 / 255.0f, 0xd0 / 255.0f, 1.0f);
    pClear(GL_COLOR_BUFFER_BIT);
    assert(pSwapBuffers(dpy, winSurf) == EGL_TRUE);

    // 2. Consume it zero-copy in SurfaceTexture on the consumer context.
    assert(pMakeCurrent(dpy, pb, pb, consumerCtx) == EGL_TRUE);
    GLuint texName = 0;
    pGenTextures(1, &texName);
    assert(texName != 0);

    jlong st = Java_android_graphics_SurfaceTexture_nativeCreate(&jni.env, nullptr, 0, false);
    Java_android_graphics_SurfaceTexture_nativeAttach(&jni.env, nullptr, st, jint(texName));
    assert(jni.exceptions == 0);

    const jboolean zcUpdated = Java_android_graphics_SurfaceTexture_nativeUpdate(
        &jni.env, nullptr, st, reinterpret_cast<jlong>(zcWin));
    assert(jni.exceptions == 0);
    assert(zcUpdated == JNI_TRUE);

    // Deduplicated update without a newer frame must return JNI_FALSE.
    const jboolean zcSecond = Java_android_graphics_SurfaceTexture_nativeUpdate(
        &jni.env, nullptr, st, reinterpret_cast<jlong>(zcWin));
    assert(jni.exceptions == 0);
    assert(zcSecond == JNI_FALSE);

    GLuint fbo = 0;
    pGenFramebuffers(1, &fbo);
    pBindFramebuffer(GL_FRAMEBUFFER, fbo);
    pFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texName, 0);
    assert(pCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    uint8_t px[4] = {};
    pReadPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    assert(nearByte(px[0], 0x20) && nearByte(px[1], 0x80) && nearByte(px[2], 0xd0) &&
           px[3] == 0xff);
    std::printf("PASS surface_texture zero-copy iosurface rgba=(%u,%u,%u,%u)\n", px[0], px[1],
                px[2], px[3]);

    // 3. CPU-posted ANativeWindow fallback path (e.g. Canvas / ImageReader).
    ANativeWindow* cpuWin = shiraz_window_create(8, 8);
    assert(cpuWin != nullptr);
    ANativeWindow_Buffer buf{};
    assert(ANativeWindow_lock(cpuWin, &buf, nullptr) == 0);
    auto* dst = static_cast<uint32_t*>(buf.bits);
    for (int y = 0; y < buf.height; ++y) {
        for (int x = 0; x < buf.width; ++x) {
            // ANativeWindow RGBA_8888 memory bytes [R,G,B,A] = [0x33, 0xaa, 0x55, 0xff]
            dst[y * buf.stride + x] = 0xff55aa33u;
        }
    }
    assert(ANativeWindow_unlockAndPost(cpuWin) == 0);

    GLuint cpuTex = 0;
    pGenTextures(1, &cpuTex);
    jlong stCpu = Java_android_graphics_SurfaceTexture_nativeCreate(&jni.env, nullptr, 0, false);
    Java_android_graphics_SurfaceTexture_nativeAttach(&jni.env, nullptr, stCpu, jint(cpuTex));
    assert(jni.exceptions == 0);
    const jboolean cpuUpdated = Java_android_graphics_SurfaceTexture_nativeUpdate(
        &jni.env, nullptr, stCpu, reinterpret_cast<jlong>(cpuWin));
    assert(jni.exceptions == 0);
    assert(cpuUpdated == JNI_TRUE);

    pFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, cpuTex, 0);
    assert(pCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    std::memset(px, 0, sizeof(px));
    pReadPixels(4, 4, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    assert(nearByte(px[0], 0x33) && nearByte(px[1], 0xaa) && nearByte(px[2], 0x55) &&
           px[3] == 0xff);
    std::printf("PASS surface_texture cpu-fallback rgba=(%u,%u,%u,%u)\n", px[0], px[1], px[2],
                px[3]);

    pBindFramebuffer(GL_FRAMEBUFFER, 0);
    pDeleteFramebuffers(1, &fbo);
    Java_android_graphics_SurfaceTexture_nativeDetach(&jni.env, nullptr, st);
    Java_android_graphics_SurfaceTexture_nativeRelease(&jni.env, nullptr, st);
    Java_android_graphics_SurfaceTexture_nativeDetach(&jni.env, nullptr, stCpu);
    Java_android_graphics_SurfaceTexture_nativeRelease(&jni.env, nullptr, stCpu);
    assert(jni.exceptions == 0);
    assert(pGetError() == GL_NO_ERROR);

    pMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    pDestroySurface(dpy, winSurf);
    pDestroySurface(dpy, pb);
    pDestroyContext(dpy, producerCtx);
    pDestroyContext(dpy, consumerCtx);
    pTerminate(dpy);
    ANativeWindow_release(zcWin);
    ANativeWindow_release(cpuWin);

    std::printf("MACOS_SURFACE_TEXTURE_PASS\n");
    return 0;
}
