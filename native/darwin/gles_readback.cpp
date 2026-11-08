// SPDX-License-Identifier: GPL-3.0-only
// Linked into libshiraz-gles2 and libshiraz-gles1 on macOS only, so that the
// app's GLES20.glReadPixels / GL10.glReadPixels (native/gles2_jni.cpp,
// native/gles1_jni.cpp, unchanged) bind to this in-image definition instead of
// ANGLE's (two-level namespace; the export list keeps it private).
//
// ANGLE chromium/8127 (Metal) returns zeros with no GL error from
// glReadPixels while another context renders on another thread, unless the
// reading context's work has completed first (build/macos/m4/race/:
// directfbo 0/10 without, 10/10 with glFinish). glReadPixels is synchronous by
// specification, so the extra glFinish only makes ANGLE wait as a conforming
// driver must. Native APK code calling glReadPixels directly (M5) does not go
// through here.
#include <dlfcn.h>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <GLES2/gl2.h>

#ifndef SHIRAZ_GLES_READBACK_LIBRARY
#error SHIRAZ_GLES_READBACK_LIBRARY names the ANGLE GLES library that provides glReadPixels
#endif

namespace {
using ReadPixels = void (*)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void*);
using Finish = void (*)();
template <typename Fn> Fn angle(const char* name) {
    // Already loaded: this library links it, so dyld mapped it first.
    static void* const library = dlopen(SHIRAZ_GLES_READBACK_LIBRARY, RTLD_NOLOAD | RTLD_LAZY);
    void* entry = library ? dlsym(library, name) : nullptr;
    if (!entry) {
        std::fprintf(stderr, "shiraz: %s has no %s: %s\n", SHIRAZ_GLES_READBACK_LIBRARY, name, dlerror());
        std::abort();
    }
    return reinterpret_cast<Fn>(entry);
}
// M4b zero-copy windows render into IOSurface-backed Metal textures. There ANGLE's
// glFinish is not enough: after another context's swap on another thread, the
// first glReadPixels of the window still returned zeros (build/macos/m4b/phase1,
// impl-log section 4; a re-read moments later was correct). An EGL fence with a
// client wait makes the CPU wait for the GPU's completion of this context's work
// before the read. Only in zero-copy mode, so SHIRAZ_EGL_ZERO_COPY=0 keeps the
// M4a path unchanged.
void zeroCopyFence() {
    using Mode = int32_t (*)();
    static const Mode mode = reinterpret_cast<Mode>(dlsym(RTLD_DEFAULT, "shiraz_zero_copy_mode"));
    if (!mode || mode() == 0) return;
    using GetProc = void* (*)(const char*);
    using CurrentDisplay = void* (*)();
    using CreateSync = void* (*)(void*, unsigned, const intptr_t*);
    using ClientWait = int32_t (*)(void*, void*, int32_t, uint64_t);
    using DestroySync = unsigned (*)(void*, void*);
    static const GetProc getProc = reinterpret_cast<GetProc>(dlsym(RTLD_DEFAULT, "eglGetProcAddress"));
    if (!getProc) return;
    static const CurrentDisplay current = reinterpret_cast<CurrentDisplay>(getProc("eglGetCurrentDisplay"));
    static const CreateSync create = reinterpret_cast<CreateSync>(getProc("eglCreateSync"));
    static const ClientWait wait = reinterpret_cast<ClientWait>(getProc("eglClientWaitSync"));
    static const DestroySync destroy = reinterpret_cast<DestroySync>(getProc("eglDestroySync"));
    if (!current || !create || !wait || !destroy) return;
    void* display = current();
    if (!display) return;
    const intptr_t none[] = {0x3038 /* EGL_NONE */};
    void* sync = create(display, 0x30F9 /* EGL_SYNC_FENCE */, none);
    if (!sync) return;
    wait(display, sync, 0x0001 /* EGL_SYNC_FLUSH_COMMANDS_BIT */, ~uint64_t(0) /* EGL_FOREVER */);
    destroy(display, sync);
}
}

extern "C" void glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void* pixels) {
    static const Finish finish = angle<Finish>("glFinish");
    static const ReadPixels read = angle<ReadPixels>("glReadPixels");
    finish();
    zeroCopyFence();
    read(x, y, width, height, format, type, pixels);
    // M4b counters (libandroid, resolved at run time): an app readback is a CPU pixel read, not ours.
    using Count = void (*)(const char*, uint64_t);
    static const Count count = reinterpret_cast<Count>(dlsym(RTLD_DEFAULT, "shiraz_observer_reads_add"));
    if (count) count("app-glreadpixels", uint64_t(width > 0 ? width : 0) * uint64_t(height > 0 ? height : 0) * 4);
    static const bool trace = [] { const char* v = std::getenv("SHIRAZ_TRACE_ZERO_COPY"); return v && *v == '1'; }();
    if (trace) {
        using GetIntegerv = void (*)(GLenum, GLint*);
        static const GetIntegerv get = angle<GetIntegerv>("glGetIntegerv");
        GLint fbo = -1, viewport[4] = {0, 0, 0, 0};
        get(GL_FRAMEBUFFER_BINDING, &fbo); get(GL_VIEWPORT, viewport);
        const unsigned char* p = static_cast<const unsigned char*>(pixels);
        std::fprintf(stderr, "SHIRAZ_ZC_APP_READ at=%d,%d fbo=%d viewport=%d,%d,%d,%d first=%02x%02x%02x%02x\n", x, y, fbo,
                     viewport[0], viewport[1], viewport[2], viewport[3], p ? p[0] : 0, p ? p[1] : 0, p ? p[2] : 0, p ? p[3] : 0);
    }
}
