// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/hardware_buffer.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <cassert>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <sys/socket.h>
#include <unistd.h>
extern "C" int sync_merge(const char*, int, int);
extern "C" int sync_wait(int, int);
int main() {
    assert(sync_merge("invalid", -1, -1) == -1 && sync_wait(-1, 0) == -1);
    AHardwareBuffer_Desc desc{4, 4, 1, 1, 0x333, 0, 0, 0};
    AHardwareBuffer* buffer = nullptr;
    assert(AHardwareBuffer_allocate(nullptr, &buffer) == -EINVAL && !buffer);
    auto invalid = desc; invalid.usage |= 1 << 14;
    assert(AHardwareBuffer_allocate(&invalid, &buffer) == -EINVAL && !buffer);
    invalid = desc; invalid.width = 0xffffffff;
    assert(AHardwareBuffer_allocate(&invalid, &buffer) == -EINVAL && !buffer);
    assert(AHardwareBuffer_allocate(&desc, &buffer) == 0 && buffer);
    AHardwareBuffer_Desc actual{}; AHardwareBuffer_describe(buffer, &actual);
    assert(actual.width == 4 && actual.height == 4 && actual.stride >= 4);
    void* pixels = nullptr;
    assert(AHardwareBuffer_lock(buffer, 0x30, -1, nullptr, &pixels) == 0);
    auto* rgba = static_cast<unsigned char*>(pixels);
    for (unsigned y = 0; y < 4; ++y) for (unsigned x = 0; x < 4; ++x) {
        auto* p = rgba + (y * actual.stride + x) * 4; p[0] = 51; p[1] = 102; p[2] = 153; p[3] = 255;
    }
    void* blocked = nullptr;
    assert(AHardwareBuffer_lock(buffer, 3, -1, nullptr, &blocked) == -EBUSY && !blocked);
    int fence = 99; assert(AHardwareBuffer_unlock(buffer, &fence) == 0 && fence == -1);
    assert(AHardwareBuffer_unlock(buffer, nullptr) == -EINVAL);
    int pair[2]; assert(socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0, pair) == 0);
    assert(AHardwareBuffer_sendHandleToUnixSocket(buffer, pair[0]) == 0);
    AHardwareBuffer* imported = nullptr;
    assert(AHardwareBuffer_recvHandleFromUnixSocket(pair[1], &imported) == 0 && imported);
    assert(AHardwareBuffer_lock(imported, 3, -1, nullptr, &pixels) == 0);
    assert(std::memcmp(pixels, "\x33\x66\x99\xff", 4) == 0);
    void* another = nullptr; assert(AHardwareBuffer_lock(imported, 3, -1, nullptr, &another) == 0 && another == pixels);
    assert(AHardwareBuffer_unlock(imported, nullptr) == 0 && AHardwareBuffer_unlock(imported, nullptr) == 0);
    // Malformed IPC must fail without producing an object.
    assert(send(pair[0], "bad", 3, 0) == 3);
    AHardwareBuffer* rejected = nullptr;
    assert(AHardwareBuffer_recvHandleFromUnixSocket(pair[1], &rejected) == -EINVAL && !rejected);
    close(pair[0]); close(pair[1]);
    auto query = reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(eglGetProcAddress("eglQueryDevicesEXT"));
    auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(eglGetProcAddress("eglGetPlatformDisplay"));
    auto create = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
    auto destroy = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
    auto client = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(eglGetProcAddress("eglGetNativeClientBufferANDROID"));
    auto target = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(eglGetProcAddress("glEGLImageTargetTexture2DOES"));
    assert(query && platform && create && destroy && client && target);
    EGLDeviceEXT devices[8]; EGLint count = 0; assert(query(8, devices, &count));
    EGLDisplay display = EGL_NO_DISPLAY;
    for (int i = 0; i < count; ++i) {
        auto candidate = platform(EGL_PLATFORM_DEVICE_EXT, devices[i], nullptr);
        if (eglInitialize(candidate, nullptr, nullptr)) {
            const char* extensions = eglQueryString(candidate, EGL_EXTENSIONS);
            if (extensions && std::strstr(extensions, "EGL_EXT_image_dma_buf_import")) { display = candidate; break; }
            eglTerminate(candidate);
        }
    }
    assert(display != EGL_NO_DISPLAY && eglBindAPI(EGL_OPENGL_ES_API));
    assert(std::strstr(eglQueryString(display, EGL_EXTENSIONS), "EGL_ANDROID_image_native_buffer"));
    assert(client(nullptr) == nullptr && eglGetError() == EGL_BAD_PARAMETER);
    EGLint attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, attribs);
    assert(context != EGL_NO_CONTEXT && eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context));
    EGLImageKHR image = create(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, client(imported), nullptr);
    assert(image != EGL_NO_IMAGE_KHR);
    GLuint texture = 0, framebuffer = 0; glGenTextures(1, &texture); glBindTexture(GL_TEXTURE_2D, texture);
    target(GL_TEXTURE_2D, image); assert(glGetError() == GL_NO_ERROR);
    glGenFramebuffers(1, &framebuffer); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    assert(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE);
    unsigned char sample[4]{}; glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, sample);
    assert(std::memcmp(sample, "\x33\x66\x99\xff", 4) == 0);
    glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); glFinish();
    assert(AHardwareBuffer_lock(buffer, 3, -1, nullptr, &pixels) == 0);
    assert(std::memcmp(pixels, "\xff\x00\x00\xff", 4) == 0);
    assert(AHardwareBuffer_unlock(buffer, nullptr) == 0);
    auto createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(eglGetProcAddress("eglCreateSyncKHR"));
    auto destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(eglGetProcAddress("eglDestroySyncKHR"));
    auto duplicateFence = reinterpret_cast<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>(eglGetProcAddress("eglDupNativeFenceFDANDROID"));
    assert(createSync && destroySync && duplicateFence);
    EGLSyncKHR sync = createSync(display, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
    assert(sync != EGL_NO_SYNC_KHR); glFlush();
    int first = duplicateFence(display, sync); assert(first >= 0);
    int merged = sync_merge("shiraz-test", first, first); assert(merged >= 0 && sync_wait(merged, 1000) == 0);
    close(first); close(merged); assert(destroySync(display, sync));
    glDeleteFramebuffers(1, &framebuffer); glDeleteTextures(1, &texture); assert(destroy(display, image));
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context); eglTerminate(display);
    AHardwareBuffer_release(imported); AHardwareBuffer_release(buffer);
    std::puts("HARDWARE_BUFFER_CPU_IPC_GPU_ROUNDTRIP_OK");
}
