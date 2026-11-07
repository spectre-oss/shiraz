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
}

extern "C" void glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void* pixels) {
    static const Finish finish = angle<Finish>("glFinish");
    static const ReadPixels read = angle<ReadPixels>("glReadPixels");
    finish();
    read(x, y, width, height, format, type, pixels);
}
