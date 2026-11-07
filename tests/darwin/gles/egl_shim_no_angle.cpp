// SPDX-License-Identifier: GPL-3.0-only
// libEGL_shim on macOS without ANGLE (no libGLESv2.dylib beside it), run from
// a working directory that holds a planted libEGL_mesa_host.so.1:
//  - with SHIRAZ_EGL_LIBRARY unset, relative or naming a missing file, the
//    shim loads no EGL library at all; dyld would search the cwd for the
//    Linux leaf-name fallback, so the planted library must not load;
//  - eglSwapBuffers and the shim's glBindFramebuffer return instead of
//    calling the null weak imports of libGLESv2 (they used to crash, SIGSEGV).
// The make rule copies the shim and libandroid into an ANGLE-free directory.
#include "shiraz/native_window.h"
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <string>

namespace {
int failures = 0;
void check(bool ok, const char* what) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok) ++failures;
}
}

int main(int argc, char** argv) {
    if (argc != 3) { std::fprintf(stderr, "usage: %s ANGLE-free-library-dir egl-library-value|-\n", argv[0]); return 2; }
    const std::string dir = argv[1];
    if (std::string(argv[2]) == "-") unsetenv("SHIRAZ_EGL_LIBRARY");
    else setenv("SHIRAZ_EGL_LIBRARY", argv[2], 1);
    void* shim = dlopen((dir + "/libEGL_shim.so").c_str(), RTLD_NOW | RTLD_LOCAL);
    check(shim != nullptr, "the shim loads without ANGLE");
    if (!shim) { std::printf("%s\n", dlerror()); return 1; }
    check(dlopen("@rpath/libGLESv2.dylib", RTLD_NOLOAD) == nullptr, "no libGLESv2 is loaded");
    using Make = unsigned (*)(void*, void*, void*, void*);
    using Swap = unsigned (*)(void*, void*);
    using CreateWindow = void* (*)(void*, void*, void*, const int*);
    using Bind = void (*)(unsigned, unsigned);
    using Proc = void* (*)(const char*);
    auto createWindow = reinterpret_cast<CreateWindow>(dlsym(shim, "eglCreateWindowSurface"));
    auto make = reinterpret_cast<Make>(dlsym(shim, "eglMakeCurrent"));
    auto swap = reinterpret_cast<Swap>(dlsym(shim, "eglSwapBuffers"));
    auto proc = reinterpret_cast<Proc>(dlsym(shim, "eglGetProcAddress"));
    auto display = reinterpret_cast<void* (*)(int)>(dlsym(shim, "eglGetDisplay"));
    check(createWindow && make && swap && proc && display, "shim entry points");
    if (failures) return 1;
    check(display(0) == nullptr, "eglGetDisplay finds no EGL library (EGL_NO_DISPLAY)");
    check(proc("eglInitialize") == nullptr, "eglGetProcAddress forwards nothing");
    ANativeWindow* window = shiraz_window_create(4, 4);
    void* surface = createWindow(nullptr, nullptr, window, nullptr);
    check(make(nullptr, surface, surface, reinterpret_cast<void*>(1)) == 0, "eglMakeCurrent fails");
    check(swap(nullptr, surface) == 0, "eglSwapBuffers returns EGL_FALSE");
    auto bind = reinterpret_cast<Bind>(proc("glBindFramebuffer"));
    check(bind != nullptr, "eglGetProcAddress(glBindFramebuffer) is the shim's router");
    if (bind) bind(0x8D40 /* GL_FRAMEBUFFER */, 0);
    check(true, "the shim's glBindFramebuffer returned");
    std::printf("RESULT failures=%d\n", failures);
    return failures ? 1 : 0;
}
