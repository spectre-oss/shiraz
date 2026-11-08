// SPDX-License-Identifier: GPL-3.0-only
// native/egl_shim.cpp on ANGLE (Metal), loaded the way a windowed Shiraz run
// loads it: SDL has already dlopened ANGLE's libGLESv2 (SDL_VIDEO_GL_DRIVER)
// when libEGL_shim and libshiraz-gles2 arrive, so libGLESv2 precedes the shim
// in dyld's image list. The shim finds the real glBindFramebuffer with
// dlsym(RTLD_NEXT), which must still reach libGLESv2 through the shim's own
// (weak) dependency. Checks, on an ES 3 and an ES 1 context over a Shiraz
// ANativeWindow (the GLSurfaceView path of native/from_surface.cpp):
//  - the default display is ANGLE's Metal renderer;
//  - eglMakeCurrent binds the shim's window framebuffer, and a clear reaches
//    the window after eglSwapBuffers (readback through shiraz_window_copy_argb);
//  - framebuffer 0 from each route (GLES20.glBindFramebuffer's JNI entry in
//    libshiraz-gles2, the shim's exported glBindFramebuffer and
//    bionic_glBindFramebuffer, eglGetProcAddress("glBindFramebuffer")) is the
//    window framebuffer, while ANGLE's own glBindFramebuffer(0) is not (ES 1:
//    the shim's framebuffer uses the OES entry points, egl_shim_gles1.inc);
//  - a resize through ANativeWindow_setBuffersGeometry is honoured.
// M4b zero copy (default; SHIRAZ_EGL_ZERO_COPY=0 keeps the readback path): the
// window colour attachment is a GL_RENDERBUFFER over an IOSurface slot, the
// app's texture namespace is untouched (first glGenTextures = 1, deleting
// names 1..64 keeps frames exact), and a slot's size is latched at dequeue: the
// frame drawn into the pre-resize slot is dropped, never stretched, and a
// mid-frame bind of 0 after a resize does not re-dequeue (DESIGN-v2 C4, C5).
//
// usage: SHIRAZ_EGL_LIBRARY=<dir>/libEGL.dylib egl_shim_window <dir>
//   <dir> holds libGLESv2.dylib, libEGL_shim.so and libshiraz-gles2.so.
#include "shiraz/native_window.h"
#define EGL_EGL_PROTOTYPES 0
#define GL_GLES_PROTOTYPES 0
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {
int failures = 0;
void check(bool ok, const char* what) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok) ++failures;
}
void* open(const std::string& path) {
    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) { std::fprintf(stderr, "dlopen %s: %s\n", path.c_str(), dlerror()); std::exit(2); }
    return handle;
}
template <typename T> T sym(void* handle, const char* name) {
    void* value = dlsym(handle, name);
    if (!value) { std::fprintf(stderr, "dlsym %s: %s\n", name, dlerror()); std::exit(2); }
    return reinterpret_cast<T>(value);
}
int imageIndex(const char* leaf) {
    for (uint32_t i = 0; i < _dyld_image_count(); ++i) {
        const char* name = _dyld_get_image_name(i);
        const char* slash = std::strrchr(name, '/');
        if (std::strcmp(slash ? slash + 1 : name, leaf) == 0) return int(i);
    }
    return -1;
}
using Bind = void (*)(GLenum, GLuint);
using JniBind = void (*)(void*, void*, int, int);
struct Gl {
    PFNGLGETINTEGERVPROC getIntegerv; PFNGLCLEARCOLORPROC clearColor; PFNGLCLEARPROC clear;
    PFNGLGENFRAMEBUFFERSPROC genFramebuffers; PFNGLGETSTRINGPROC getString; PFNGLGETERRORPROC getError;
    PFNGLVIEWPORTPROC viewport; Bind angleBind;
};
PFNGLREADPIXELSPROC readPixels = nullptr; PFNGLFINISHPROC finishGl = nullptr;
GLint binding(const Gl& gl) { GLint value = -1; gl.getIntegerv(GL_FRAMEBUFFER_BINDING, &value); return value; }
uint32_t argb(float r, float g, float b) {
    return 0xff000000u | (uint32_t(r * 255.f + .5f) << 16) | (uint32_t(g * 255.f + .5f) << 8) | uint32_t(b * 255.f + .5f);
}
bool clearAndSwap(const Gl& gl, EGLDisplay display, EGLSurface surface, PFNEGLSWAPBUFFERSPROC swap, ANativeWindow* window,
                  int width, int height, float r, float g, float b) {
    gl.viewport(0, 0, width, height);
    gl.clearColor(r, g, b, 1.f);
    gl.clear(GL_COLOR_BUFFER_BIT);
    // The producer reads its own frame back before the swap (app glReadPixels, gles_readback.cpp order).
    uint8_t rgba[4] = {0, 0, 0, 0};
    finishGl(); readPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    const uint32_t read = 0xff000000u * (rgba[3] == 255) | uint32_t(rgba[0]) << 16 | uint32_t(rgba[1]) << 8 | rgba[2];
    if (read != argb(r, g, b)) { std::printf("     glReadPixels before swap 0x%08x (a=%u) expected 0x%08x\n", read, rgba[3], argb(r, g, b)); return false; }
    if (!swap(display, surface)) return false;
    std::vector<uint32_t> pixels(size_t(width) * height);
    if (shiraz_window_copy_argb(window, pixels.data(), pixels.size()) != 0) return false;
    for (uint32_t pixel : pixels) if (pixel != argb(r, g, b)) { std::printf("     pixel 0x%08x expected 0x%08x\n", pixel, argb(r, g, b)); return false; }
    return true;
}
} // namespace

int main(int argc, char** argv) {
    if (argc != 2 || !std::getenv("SHIRAZ_EGL_LIBRARY")) {
        std::fprintf(stderr, "usage: SHIRAZ_EGL_LIBRARY=<dir>/libEGL.dylib %s <dir>\n", argv[0]);
        return 2;
    }
    const std::string dir = argv[1];
    // SDL's order in a windowed run: ANGLE's GLES library first.
    void* gles = open(dir + "/libGLESv2.dylib");
    void* shim = open(dir + "/libEGL_shim.so");
    void* jni = open(dir + "/libshiraz-gles2.so");
    int glesIndex = imageIndex("libGLESv2.dylib"), shimIndex = imageIndex("libEGL_shim.so");
    std::printf("image order libGLESv2.dylib=%d libEGL_shim.so=%d libshiraz-gles2.so=%d\n", glesIndex, shimIndex, imageIndex("libshiraz-gles2.so"));
    check(glesIndex >= 0 && shimIndex > glesIndex, "libGLESv2 is loaded before libEGL_shim (RTLD_NEXT worst case)");

    Gl gl{sym<PFNGLGETINTEGERVPROC>(gles, "glGetIntegerv"), sym<PFNGLCLEARCOLORPROC>(gles, "glClearColor"),
          sym<PFNGLCLEARPROC>(gles, "glClear"), sym<PFNGLGENFRAMEBUFFERSPROC>(gles, "glGenFramebuffers"),
          sym<PFNGLGETSTRINGPROC>(gles, "glGetString"), sym<PFNGLGETERRORPROC>(gles, "glGetError"),
          sym<PFNGLVIEWPORTPROC>(gles, "glViewport"), sym<Bind>(gles, "glBindFramebuffer")};
    readPixels = sym<PFNGLREADPIXELSPROC>(gles, "glReadPixels"); finishGl = sym<PFNGLFINISHPROC>(gles, "glFinish");
    auto getProc = sym<PFNEGLGETPROCADDRESSPROC>(shim, "eglGetProcAddress");
    auto getDisplay = sym<PFNEGLGETDISPLAYPROC>(shim, "eglGetDisplay");
    auto initialize = sym<PFNEGLINITIALIZEPROC>(shim, "eglInitialize");
    auto bindApi = sym<PFNEGLBINDAPIPROC>(shim, "eglBindAPI");
    auto createContext = sym<PFNEGLCREATECONTEXTPROC>(shim, "eglCreateContext");
    auto chooseConfig = sym<PFNEGLCHOOSECONFIGPROC>(shim, "eglChooseConfig");
    auto destroyContext = sym<PFNEGLDESTROYCONTEXTPROC>(shim, "eglDestroyContext");
    auto createWindow = reinterpret_cast<PFNEGLCREATEWINDOWSURFACEPROC>(getProc("eglCreateWindowSurface"));
    auto makeCurrent = reinterpret_cast<PFNEGLMAKECURRENTPROC>(getProc("eglMakeCurrent"));
    auto swap = reinterpret_cast<PFNEGLSWAPBUFFERSPROC>(getProc("eglSwapBuffers"));
    auto destroySurface = reinterpret_cast<PFNEGLDESTROYSURFACEPROC>(getProc("eglDestroySurface"));
    Bind procBind = reinterpret_cast<Bind>(getProc("glBindFramebuffer"));
    check(createWindow && makeCurrent && swap && destroySurface && procBind, "shim eglGetProcAddress returns its window entry points");
    if (failures) return 1;

    EGLDisplay display = getDisplay(EGL_DEFAULT_DISPLAY);
    check(display != EGL_NO_DISPLAY && initialize(display, nullptr, nullptr) && bindApi(EGL_OPENGL_ES_API), "default display initializes (ES API)");
    if (failures) return 1;
    struct Route { const char* name; Bind bind; };
    auto jniBind = sym<JniBind>(jni, "Java_android_opengl_GLES20_glBindFramebuffer");
    static JniBind jniTarget = jniBind;
    const Route routes[] = {
        {"GLES20.glBindFramebuffer JNI (libshiraz-gles2)", [](GLenum target, GLuint framebuffer) { jniTarget(nullptr, nullptr, int(target), int(framebuffer)); }},
        {"shim glBindFramebuffer export", sym<Bind>(shim, "glBindFramebuffer")},
        {"shim bionic_glBindFramebuffer", sym<Bind>(shim, "bionic_glBindFramebuffer")},
        {"eglGetProcAddress(glBindFramebuffer)", procBind},
    };
    const char* zeroCopyEnv = std::getenv("SHIRAZ_EGL_ZERO_COPY");
    const bool zeroCopy = !(zeroCopyEnv && std::strcmp(zeroCopyEnv, "0") == 0);
    std::printf("zero copy %s\n", zeroCopy ? "on (iosurface slots)" : "off (readback)");
    auto attachmentParameter = sym<PFNGLGETFRAMEBUFFERATTACHMENTPARAMETERIVPROC>(gles, "glGetFramebufferAttachmentParameteriv");
    auto genTextures = sym<PFNGLGENTEXTURESPROC>(gles, "glGenTextures");
    auto deleteTextures = sym<PFNGLDELETETEXTURESPROC>(gles, "glDeleteTextures");
    auto bindTexture = sym<PFNGLBINDTEXTUREPROC>(gles, "glBindTexture");
    for (EGLint version : {3, 1}) {
        std::printf("== ES %d context\n", version);
        // As native/from_surface.cpp does on macOS: ES 3 without a config
        // (EGL_KHR_no_config_context), ES 1 with an EGL_OPENGL_ES_BIT config,
        // because ANGLE answers EGL_BAD_MATCH for a config-less ES 1 context.
        const EGLint attributes[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
        EGLConfig config = nullptr;
        if (version == 1) {
            const EGLint es1[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT, EGL_NONE};
            EGLint count = 0;
            check(chooseConfig(display, es1, &config, 1, &count) && count == 1, "ES 1 config chosen");
        }
        EGLContext context = createContext(display, config, EGL_NO_CONTEXT, attributes);
        check(context != EGL_NO_CONTEXT, "context created");
        if (context == EGL_NO_CONTEXT) continue;
        ANativeWindow* window = shiraz_window_create(4, 4);
        EGLSurface surface = createWindow(display, nullptr, reinterpret_cast<EGLNativeWindowType>(window), nullptr);
        check(surface != EGL_NO_SURFACE, "window surface created on a Shiraz ANativeWindow");
        // The surface holds its own reference until eglDestroySurface.
        if (surface != EGL_NO_SURFACE) ANativeWindow_release(window);
        bool current = surface != EGL_NO_SURFACE && makeCurrent(display, surface, surface, context);
        check(current, "eglMakeCurrent on the window surface");
        if (!current) { destroyContext(display, context); continue; }
        std::printf("     GL_RENDERER=%s\n     GL_VERSION=%s\n", gl.getString(GL_RENDERER), gl.getString(GL_VERSION));
        check(std::strstr(reinterpret_cast<const char*>(gl.getString(GL_RENDERER)), "ANGLE Metal Renderer") != nullptr, "renderer is ANGLE Metal");
        const GLint windowFramebuffer = binding(gl);
        std::printf("     window framebuffer=%d\n", windowFramebuffer);
        check(windowFramebuffer > 0, "eglMakeCurrent bound the shim's window framebuffer");
        if (zeroCopy && version == 3) {
            GLint type = 0;
            attachmentParameter(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, &type);
            check(type == GL_RENDERBUFFER, "zero copy: window colour attachment is a GL_RENDERBUFFER");
            GLuint first = 0; genTextures(1, &first);
            std::printf("     first glGenTextures -> %u\n", first);
            check(first == 1, "zero copy: the app's first texture name is 1 (no shim texture names)");
            std::vector<GLuint> names(64); for (GLuint i = 0; i < 64; ++i) names[i] = i + 1;
            deleteTextures(64, names.data());
            bindTexture(GL_TEXTURE_2D, 7); bindTexture(GL_TEXTURE_2D, 0);
            check(clearAndSwap(gl, display, surface, swap, window, 4, 4, 0.f, 0.f, 1.f), "zero copy: frames exact after deleting textures 1..64 and binding a hard-coded name");
        }
        check(clearAndSwap(gl, display, surface, swap, window, 4, 4, 1.f, 0.f, 0.f), "clear reaches the window after eglSwapBuffers");
        // The test's own ANGLE calls: in an ES 1 context ANGLE accepts only
        // the OES framebuffer entry points.
        Bind angleBind = version == 1 ? reinterpret_cast<Bind>(getProc("glBindFramebufferOES")) : gl.angleBind;
        auto genFramebuffers = version == 1 ? reinterpret_cast<PFNGLGENFRAMEBUFFERSPROC>(getProc("glGenFramebuffersOES")) : gl.genFramebuffers;
        GLuint other = 0; genFramebuffers(1, &other);
        check(other != 0, "a second framebuffer object");
        int width = 4;
        for (const Route& route : routes) {
            angleBind(GL_FRAMEBUFFER, other);
            check(binding(gl) == GLint(other), "ANGLE bound the second framebuffer");
            route.bind(GL_FRAMEBUFFER, 0);
            const GLint bound = binding(gl);
            std::printf("     %s(0) -> %d\n", route.name, bound);
            check(bound == windowFramebuffer, route.name);
            width += 2;
            check(ANativeWindow_setBuffersGeometry(window, width, width + 1, 1) == 0, "window resized");
            route.bind(GL_FRAMEBUFFER, 0);
            if (zeroCopy) {
                if (version == 3) {
                    GLint name = 0, previousRb = 0, rbWidth = 0;
                    attachmentParameter(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &name);
                    auto getRb = sym<PFNGLGETRENDERBUFFERPARAMETERIVPROC>(gles, "glGetRenderbufferParameteriv");
                    auto bindRb = sym<PFNGLBINDRENDERBUFFERPROC>(gles, "glBindRenderbuffer");
                    gl.getIntegerv(GL_RENDERBUFFER_BINDING, &previousRb);
                    bindRb(GL_RENDERBUFFER, GLuint(name)); getRb(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &rbWidth); bindRb(GL_RENDERBUFFER, GLuint(previousRb));
                    check(rbWidth == width - 2, "zero copy: a bind of 0 after a resize keeps the dequeued slot (no re-dequeue mid-frame)");
                }
                // The frame drawn into the pre-resize slot is dropped at queue (-EAGAIN), never stretched.
                gl.viewport(0, 0, width, width + 1); gl.clearColor(1.f, 0.f, 1.f, 1.f); gl.clear(GL_COLOR_BUFFER_BIT);
                check(swap(display, surface) == EGL_TRUE, "zero copy: the swap of the pre-resize slot succeeds (frame dropped)");
                std::vector<uint32_t> pixels(size_t(width) * (width + 1));
                bool stretched = false;
                if (shiraz_window_copy_argb(window, pixels.data(), pixels.size()) == 0) for (uint32_t pixel : pixels) if (pixel == argb(1.f, 0.f, 1.f)) stretched = true;
                check(!stretched, "zero copy: the dropped pre-resize frame is not presented");
                route.bind(GL_FRAMEBUFFER, 0);
            }
            check(clearAndSwap(gl, display, surface, swap, window, width, width + 1, 0.f, 1.f, 0.f), "resized window frame after the routed bind");
        }
        angleBind(GL_FRAMEBUFFER, 0);
        std::printf("     ANGLE glBindFramebuffer(0) -> %d\n", binding(gl));
        check(binding(gl) == 0, "ANGLE's own glBindFramebuffer(0) is not routed (control)");
        check(gl.getError() == GL_NO_ERROR, "no GL error");
        makeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        destroySurface(display, surface);
        check(!shiraz_window_known(window), "eglDestroySurface released the window");
        destroyContext(display, context);
    }
    std::printf("RESULT failures=%d\n", failures);
    return failures ? 1 : 0;
}
