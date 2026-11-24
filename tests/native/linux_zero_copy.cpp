// SPDX-License-Identifier: GPL-3.0-only
// End-to-end Linux GBM/DMA-BUF zero-copy test:
// 1. EGL producer (libEGL_shim.so) -> independent EGL consumer via WindowQueue +
//    EGL_LINUX_DMA_BUF_EXT + EGL_ANDROID_native_fence_sync with zero CPU pixel reads.
// 2. Release-on-supersede slot reuse across multiple frames + live geometry resize.
// 3. On-demand CPU materialization via shiraz_window_copy_argb_revision.
// 4. SurfaceTexture zero-copy DMA-BUF acquisition + CPU-posted fallback + cleanup.
#include "shiraz/hardware_buffer.h"
#include "shiraz/linux/window_queue.h"
#include "shiraz/native_window.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <cassert>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <poll.h>
#include <string>
#include <unistd.h>
#include <vector>

extern "C" {
jlong Java_android_graphics_SurfaceTexture_nativeCreate(JNIEnv*, jclass, jint, jboolean);
void Java_android_graphics_SurfaceTexture_nativeAttach(JNIEnv*, jclass, jlong, jint);
void Java_android_graphics_SurfaceTexture_nativeDetach(JNIEnv*, jclass, jlong);
jboolean Java_android_graphics_SurfaceTexture_nativeUpdate(JNIEnv*, jclass, jlong, jlong);
void Java_android_graphics_SurfaceTexture_nativeRelease(JNIEnv*, jclass, jlong);
}

namespace {

constexpr EGLenum kEglSyncNativeFenceAndroid = 0x3144;
constexpr EGLint kEglSyncNativeFenceFdAndroid = 0x3145;

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

bool hasExt(const char* list, const char* name) {
    if (!list)
        return false;
    const size_t len = std::strlen(name);
    for (const char* at = std::strstr(list, name); at; at = std::strstr(at + 1, name)) {
        if ((at == list || at[-1] == ' ') && (at[len] == ' ' || at[len] == 0))
            return true;
    }
    return false;
}

uint32_t sampleDmaBufPixel(EGLDisplay display, AHardwareBuffer* buffer, int32_t width,
                           int32_t height, int32_t fenceFd, bool hasNativeFence,
                           PFNEGLCREATEIMAGEKHRPROC createImage,
                           PFNEGLDESTROYIMAGEKHRPROC destroyImage,
                           PFNGLEGLIMAGETARGETTEXTURE2DOESPROC targetTex,
                           PFNEGLCREATESYNCKHRPROC createSync, PFNEGLDESTROYSYNCKHRPROC destroySync,
                           PFNEGLWAITSYNCKHRPROC waitSync) {
    if (fenceFd >= 0) {
        bool waited = false;
        if (hasNativeFence) {
            const EGLint attribs[] = {kEglSyncNativeFenceFdAndroid, fenceFd, EGL_NONE};
            EGLSyncKHR sync = createSync(display, kEglSyncNativeFenceAndroid, attribs);
            if (sync != EGL_NO_SYNC_KHR) {
                fenceFd = -1;
                waitSync(display, sync, 0);
                destroySync(display, sync);
                waited = true;
            }
        }
        if (!waited && fenceFd >= 0) {
            pollfd pf{fenceFd, POLLIN, 0};
            while (poll(&pf, 1, 1000) < 0 && errno == EINTR) {
            }
            close(fenceFd);
        }
    }

    uint32_t fourcc = 0, pitch = 0;
    int dmaFd = shiraz_hardware_buffer_export(buffer, &fourcc, &pitch);
    assert(dmaFd >= 0);
    const EGLint dmaAttribs[] = {EGL_WIDTH,
                                 width,
                                 EGL_HEIGHT,
                                 height,
                                 EGL_LINUX_DRM_FOURCC_EXT,
                                 EGLint(fourcc),
                                 EGL_DMA_BUF_PLANE0_FD_EXT,
                                 dmaFd,
                                 EGL_DMA_BUF_PLANE0_OFFSET_EXT,
                                 0,
                                 EGL_DMA_BUF_PLANE0_PITCH_EXT,
                                 EGLint(pitch),
                                 EGL_NONE};
    EGLImageKHR image =
        createImage(display, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, dmaAttribs);
    close(dmaFd);
    assert(image != EGL_NO_IMAGE_KHR);

    GLuint tex = 0, fbo = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    targetTex(GL_TEXTURE_2D, image);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    assert(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);

    uint8_t rgba[4] = {};
    glReadPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    assert(glGetError() == GL_NO_ERROR);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);
    glDeleteTextures(1, &tex);
    destroyImage(display, image);
    return (uint32_t(rgba[3]) << 24) | (uint32_t(rgba[0]) << 16) | (uint32_t(rgba[1]) << 8) |
           rgba[2];
}

} // namespace

int main() {
    AHardwareBuffer_Desc probe{4, 4, 1, 1, 0x303, 0, 0, 0};
    if (!AHardwareBuffer_isSupported(&probe)) {
        std::puts("LINUX_ZERO_COPY_SKIPPED no-gbm-device");
        return 0;
    }

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major = 0, minor = 0;
    assert(display != EGL_NO_DISPLAY && eglInitialize(display, &major, &minor));
    assert(eglBindAPI(EGL_OPENGL_ES_API));

    const char* eglExts = eglQueryString(display, EGL_EXTENSIONS);
    if (!hasExt(eglExts, "EGL_EXT_image_dma_buf_import")) {
        std::puts("LINUX_ZERO_COPY_SKIPPED no-egl-dmabuf");
        eglTerminate(display);
        return 0;
    }

    EGLint cfgAttribs[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE,
                           EGL_OPENGL_ES2_BIT, EGL_NONE};
    EGLConfig config = nullptr;
    EGLint count = 0;
    assert(eglChooseConfig(display, cfgAttribs, &config, 1, &count) && count == 1);

    EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext prodCtx = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);
    EGLContext consCtx = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);
    assert(prodCtx != EGL_NO_CONTEXT && consCtx != EGL_NO_CONTEXT);

    EGLint pbAttribs[] = {EGL_WIDTH, 4, EGL_HEIGHT, 4, EGL_NONE};
    EGLSurface consSurface = eglCreatePbufferSurface(display, config, pbAttribs);
    assert(consSurface != EGL_NO_SURFACE);

    auto createImage =
        reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
    auto destroyImage =
        reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
    auto targetTex = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
        eglGetProcAddress("glEGLImageTargetTexture2DOES"));
    auto createSync =
        reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(eglGetProcAddress("eglCreateSyncKHR"));
    auto destroySync =
        reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(eglGetProcAddress("eglDestroySyncKHR"));
    auto waitSync = reinterpret_cast<PFNEGLWAITSYNCKHRPROC>(eglGetProcAddress("eglWaitSyncKHR"));
    auto dupFenceFd = reinterpret_cast<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>(
        eglGetProcAddress("eglDupNativeFenceFDANDROID"));
    assert(createImage && destroyImage && targetTex);
    const bool hasNativeFence = createSync && destroySync && waitSync && dupFenceFd &&
                                hasExt(eglExts, "EGL_ANDROID_native_fence_sync") &&
                                hasExt(eglExts, "EGL_KHR_wait_sync");

    // 1. Producer -> Consumer zero-copy DMA-BUF queue across 3 colors (Red, Green, Blue).
    ANativeWindow* window = shiraz_window_create(16, 16);
    assert(window != nullptr);
    EGLSurface prodSurface = eglCreateWindowSurface(
        display, config, reinterpret_cast<EGLNativeWindowType>(window), nullptr);
    assert(prodSurface != EGL_NO_SURFACE);

    const uint64_t consumerToken = 0x5445535400000001ull;
    const struct {
        float r, g, b;
        uint32_t expectedArgb;
    } frames[] = {
        {1.0f, 0.0f, 0.0f, 0xffff0000u},
        {0.0f, 1.0f, 0.0f, 0xff00ff00u},
        {0.0f, 0.0f, 1.0f, 0xff0000ffu},
    };

    const uint64_t readsBefore = shiraz_cpu_pixel_reads_total();
    for (uint64_t i = 0; i < 3; ++i) {
        assert(eglMakeCurrent(display, prodSurface, prodSurface, prodCtx));
        glViewport(0, 0, 16, 16);
        glClearColor(frames[i].r, frames[i].g, frames[i].b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        assert(eglSwapBuffers(display, prodSurface));

        assert(eglMakeCurrent(display, consSurface, consSurface, consCtx));
        ShirazWindowSlot slot{};
        assert(shiraz_window_acquire_slot(window, consumerToken, i + 1, &slot) == 0);
        assert(slot.buffer != nullptr && slot.width == 16 && slot.height == 16);
        const uint32_t sampled = sampleDmaBufPixel(
            display, slot.buffer, slot.width, slot.height, slot.fence_fd, hasNativeFence,
            createImage, destroyImage, targetTex, createSync, destroySync, waitSync);
        assert(sampled == frames[i].expectedArgb);

        int32_t releaseFd = -1;
        if (hasNativeFence) {
            EGLSyncKHR relSync = createSync(display, kEglSyncNativeFenceAndroid, nullptr);
            if (relSync != EGL_NO_SYNC_KHR) {
                glFlush();
                releaseFd = dupFenceFd(display, relSync);
                destroySync(display, relSync);
            }
        }
        assert(shiraz_window_mark_sampled(window, consumerToken, slot.index, slot.generation,
                                          releaseFd) == 0);
    }
    // Zero CPU pixel reads during the entire GPU producer -> GPU consumer pipeline!
    assert(shiraz_cpu_pixel_reads_total() == readsBefore);

    // 2. Live resize mid-stream -> producer reallocates DMA-BUF slot at 24x12.
    assert(ANativeWindow_setBuffersGeometry(window, 24, 12, 1) == 0);
    assert(eglMakeCurrent(display, prodSurface, prodSurface, prodCtx));
    auto bindFbo =
        reinterpret_cast<PFNGLBINDFRAMEBUFFERPROC>(eglGetProcAddress("glBindFramebuffer"));
    assert(bindFbo != nullptr);
    bindFbo(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, 24, 12);
    glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    assert(eglSwapBuffers(display, prodSurface));

    assert(eglMakeCurrent(display, consSurface, consSurface, consCtx));
    ShirazWindowSlot resizedSlot{};
    assert(shiraz_window_acquire_slot(window, consumerToken, 4, &resizedSlot) == 0);
    assert(resizedSlot.width == 24 && resizedSlot.height == 12);
    const uint32_t resizedPixel = sampleDmaBufPixel(
        display, resizedSlot.buffer, resizedSlot.width, resizedSlot.height, resizedSlot.fence_fd,
        hasNativeFence, createImage, destroyImage, targetTex, createSync, destroySync, waitSync);
    assert(resizedPixel == 0xffff00ffu);
    assert(shiraz_window_mark_sampled(window, consumerToken, resizedSlot.index,
                                      resizedSlot.generation, -1) == 0);
    assert(shiraz_cpu_pixel_reads_total() == readsBefore);

    // 3. On-demand CPU materialization (PixelCopy / screenshot path).
    std::vector<uint32_t> cpuPixels(24 * 12, 0);
    uint64_t copiedRev = 0;
    assert(shiraz_window_copy_argb_revision(window, cpuPixels.data(), cpuPixels.size(),
                                            &copiedRev) == 0);
    assert(copiedRev > 0 && cpuPixels[0] == 0xffff00ffu && cpuPixels.back() == 0xffff00ffu);
    assert(shiraz_cpu_pixel_reads_total() == readsBefore + 1);

    assert(shiraz_window_consumer_detach(window, consumerToken) == 0);
    assert(eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT));
    assert(eglDestroySurface(display, prodSurface));
    ANativeWindow_release(window);

    // 4. SurfaceTexture zero-copy DMA-BUF acquisition + deduplication + CPU fallback.
    ANativeWindow* stWindow = shiraz_window_create(16, 16);
    assert(stWindow != nullptr);
    EGLSurface stProdSurface = eglCreateWindowSurface(
        display, config, reinterpret_cast<EGLNativeWindowType>(stWindow), nullptr);
    assert(stProdSurface != EGL_NO_SURFACE);

    assert(eglMakeCurrent(display, stProdSurface, stProdSurface, prodCtx));
    glViewport(0, 0, 16, 16);
    glClearColor(0.0f, 1.0f, 1.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    assert(eglSwapBuffers(display, stProdSurface));

    assert(eglMakeCurrent(display, consSurface, consSurface, consCtx));
    GLuint extTex = 0;
    glGenTextures(1, &extTex);
    FakeJni jni;
    const jlong stHandle = Java_android_graphics_SurfaceTexture_nativeCreate(
        &jni.env, nullptr, jint(extTex), JNI_TRUE);
    assert(stHandle != 0 && jni.exceptions == 0);

    const uint64_t readsBeforeSt = shiraz_cpu_pixel_reads_total();
    assert(Java_android_graphics_SurfaceTexture_nativeUpdate(
               &jni.env, nullptr, stHandle, reinterpret_cast<jlong>(stWindow)) == JNI_TRUE);
    assert(jni.exceptions == 0);
    // SurfaceTexture zero-copy path must NOT trigger CPU pixel reads!
    assert(shiraz_cpu_pixel_reads_total() == readsBeforeSt);
    // Deduplicated update at same revision returns false.
    assert(Java_android_graphics_SurfaceTexture_nativeUpdate(
               &jni.env, nullptr, stHandle, reinterpret_cast<jlong>(stWindow)) == JNI_FALSE);

    // CPU-posted ANativeWindow frame still works seamlessly via fallback.
    ANativeWindow_Buffer cpuBuf{};
    assert(ANativeWindow_lock(stWindow, &cpuBuf, nullptr) == 0);
    std::memset(cpuBuf.bits, 0x7f, size_t(cpuBuf.stride) * size_t(cpuBuf.height) * 4);
    assert(ANativeWindow_unlockAndPost(stWindow) == 0);
    assert(Java_android_graphics_SurfaceTexture_nativeUpdate(
               &jni.env, nullptr, stHandle, reinterpret_cast<jlong>(stWindow)) == JNI_TRUE);
    assert(jni.exceptions == 0);

    Java_android_graphics_SurfaceTexture_nativeDetach(&jni.env, nullptr, stHandle);
    Java_android_graphics_SurfaceTexture_nativeRelease(&jni.env, nullptr, stHandle);
    assert(jni.exceptions == 0);

    assert(eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT));
    assert(eglDestroySurface(display, stProdSurface));
    assert(eglDestroySurface(display, consSurface));
    assert(eglDestroyContext(display, prodCtx));
    assert(eglDestroyContext(display, consCtx));
    assert(eglTerminate(display));
    ANativeWindow_release(stWindow);

    std::printf("LINUX_ZERO_COPY_OK native_fence=%d\n", hasNativeFence ? 1 : 0);
    return 0;
}
