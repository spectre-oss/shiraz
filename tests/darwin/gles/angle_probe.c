// SPDX-License-Identifier: GPL-3.0-only
// ANGLE (Metal) EGL/GLES probe for the installed .local/lib/angle dylibs, run by
// python3 scripts/bootstrap_angle.py check. It dlopens <dir>/libEGL.dylib by
// absolute path (no link-time dependency and no DYLD_* variables), so libEGL
// must find libGLESv2 next to itself and libGLESv1_CM must resolve
// @rpath/libGLESv2.dylib through its own @loader_path rpath. Checks: the
// default display is ANGLE's Metal renderer; ES 3.0, ES 2.0, ES 1.1 (through
// libGLESv1_CM's own exports) and an explicit Metal platform display each clear
// a pbuffer and read back the exact colour; a surfaceless ES3 context becomes
// current; every loaded EGL/GLES image comes from <dir>. It also prints what
// egl_shim.cpp's first eglGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA)
// attempt returns (ANGLE rejects it; egl_shim falls back to eglGetDisplay).
//
// usage: angle_probe <dir containing libEGL.dylib, libGLESv2.dylib, libGLESv1_CM.dylib>
#define EGL_EGL_PROTOTYPES 0
#define GL_GLES_PROTOTYPES 0
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/eglext_angle.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <limits.h>
#include <mach-o/dyld.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef EGL_PLATFORM_SURFACELESS_MESA
#define EGL_PLATFORM_SURFACELESS_MESA 0x31DD
#endif

static char lib_dir[PATH_MAX];
static int failures;

static PFNEGLGETPROCADDRESSPROC pGetProcAddress;
static PFNEGLGETERRORPROC pGetError;
static PFNEGLGETDISPLAYPROC pGetDisplay;
static PFNEGLINITIALIZEPROC pInitialize;
static PFNEGLTERMINATEPROC pTerminate;
static PFNEGLQUERYSTRINGPROC pQueryString;
static PFNEGLCHOOSECONFIGPROC pChooseConfig;
static PFNEGLCREATEPBUFFERSURFACEPROC pCreatePbuffer;
static PFNEGLCREATECONTEXTPROC pCreateContext;
static PFNEGLMAKECURRENTPROC pMakeCurrent;
static PFNEGLDESTROYCONTEXTPROC pDestroyContext;
static PFNEGLDESTROYSURFACEPROC pDestroySurface;
static PFNEGLBINDAPIPROC pBindAPI;
static PFNEGLGETPLATFORMDISPLAYPROC pGetPlatformDisplay;          /* EGL 1.5 core */
static PFNEGLGETPLATFORMDISPLAYEXTPROC pGetPlatformDisplayEXT;    /* what egl_shim.cpp uses */

typedef const GLubyte *(*GetStringFn)(GLenum);
typedef void (*ClearColorFn)(GLfloat, GLfloat, GLfloat, GLfloat);
typedef void (*ClearFn)(GLbitfield);
typedef void (*ReadPixelsFn)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *);
typedef GLenum (*GetErrorFn)(void);
typedef void (*ViewportFn)(GLint, GLint, GLsizei, GLsizei);

struct GLFns { GetStringFn GetString; ClearColorFn ClearColor; ClearFn Clear; ReadPixelsFn ReadPixels; GetErrorFn GetError; };

static void *must(void *lib, const char *name) {
    void *p = dlsym(lib, name);
    if (!p) { fprintf(stderr, "FATAL: dlsym(%s): %s\n", name, dlerror()); exit(2); }
    return p;
}

/* Every loaded EGL/GLES image must live in lib_dir: a copy found anywhere else
 * (the ANGLE build tree, a host framework) means the install is not self-contained. */
static void print_loaded_images(const char *tag) {
    printf("[%s] loaded GL/EGL images:\n", tag);
    size_t n_dir = strlen(lib_dir);
    for (uint32_t i = 0; i < _dyld_image_count(); i++) {
        const char *n = _dyld_get_image_name(i);
        if (!strstr(n, "EGL") && !strstr(n, "GLES")) continue;
        char real[PATH_MAX];
        const char *r = realpath(n, real) ? real : n;
        int inside = strncmp(r, lib_dir, n_dir) == 0 && r[n_dir] == '/';
        printf("    %s%s\n", n, inside ? "" : "  <- outside the library directory");
        if (!inside) failures++;
    }
}

static int has_ext(const char *exts, const char *name) {
    size_t n = strlen(name);
    for (const char *p = exts; p && (p = strstr(p, name)); p += n)
        if ((p == exts || p[-1] == ' ') && (p[n] == ' ' || p[n] == 0)) return 1;
    return 0;
}

static int count_words(const char *s) {
    int c = 0, in = 0;
    for (; s && *s; s++) { if (*s != ' ' && !in) { c++; in = 1; } else if (*s == ' ') in = 0; }
    return c;
}

static void print_display_info(EGLDisplay d) {
    printf("EGL_VENDOR=%s\n", pQueryString(d, EGL_VENDOR));
    printf("EGL_VERSION=%s\n", pQueryString(d, EGL_VERSION));
    printf("EGL_CLIENT_APIS=%s\n", pQueryString(d, EGL_CLIENT_APIS));
    const char *ext = pQueryString(d, EGL_EXTENSIONS);
    printf("EGL_EXTENSIONS count=%d\n", count_words(ext));
    const char *want[] = {"EGL_KHR_surfaceless_context", "EGL_KHR_no_config_context",
                          "EGL_ANGLE_iosurface_client_buffer", "EGL_ANGLE_metal_shared_event_sync",
                          "EGL_KHR_image_base", "EGL_ANDROID_native_fence_sync",
                          "EGL_KHR_fence_sync", "EGL_KHR_gl_texture_2D_image",
                          "EGL_ANGLE_metal_texture_client_buffer", "EGL_ANGLE_device_metal",
                          "EGL_ANGLE_display_texture_share_group", "EGL_EXT_device_query"};
    for (size_t i = 0; i < sizeof want / sizeof *want; i++)
        printf("  %-40s %s\n", want[i], has_ext(ext, want[i]) ? "yes" : "NO");
    printf("EGL_EXTENSIONS full: %s\n", ext);
}

/* Returns 1 on exact readback match. */
static int render_test(EGLDisplay d, const char *label, EGLint renderable, EGLint client_version,
                       const struct GLFns *gl, int surfaceless) {
    printf("--- %s (EGL_CONTEXT_CLIENT_VERSION=%d%s) ---\n", label, client_version,
           surfaceless ? ", surfaceless" : "");
    EGLint cfg_attrs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, renderable,
                          EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                          EGL_NONE};
    EGLConfig cfg; EGLint ncfg = 0;
    if (!pChooseConfig(d, cfg_attrs, &cfg, 1, &ncfg) || ncfg < 1) {
        printf("eglChooseConfig failed: n=%d err=0x%04x\n", ncfg, pGetError()); return 0;
    }
    EGLSurface surf = EGL_NO_SURFACE;
    if (!surfaceless) {
        EGLint pb[] = {EGL_WIDTH, 64, EGL_HEIGHT, 64, EGL_NONE};
        surf = pCreatePbuffer(d, cfg, pb);
        if (surf == EGL_NO_SURFACE) { printf("eglCreatePbufferSurface failed: 0x%04x\n", pGetError()); return 0; }
    }
    pBindAPI(EGL_OPENGL_ES_API);
    EGLint ctx_attrs[] = {EGL_CONTEXT_CLIENT_VERSION, client_version, EGL_NONE};
    EGLContext ctx = pCreateContext(d, cfg, EGL_NO_CONTEXT, ctx_attrs);
    if (ctx == EGL_NO_CONTEXT) { printf("eglCreateContext failed: 0x%04x\n", pGetError()); return 0; }
    if (!pMakeCurrent(d, surf, surf, ctx)) { printf("eglMakeCurrent failed: 0x%04x\n", pGetError()); return 0; }
    printf("GL_VENDOR=%s\n", gl->GetString(GL_VENDOR));
    printf("GL_RENDERER=%s\n", gl->GetString(GL_RENDERER));
    const char *renderer = (const char *)gl->GetString(GL_RENDERER);
    const char *version = (const char *)gl->GetString(GL_VERSION);
    printf("GL_VERSION=%s\n", version);
    char want_version[32];
    snprintf(want_version, sizeof want_version, "OpenGL ES %s",
             client_version == 1 ? "1.1" : client_version == 2 ? "2.0" : "3.0");
    /* ANGLE may report a higher ES version than requested for ES2. */
    int version_ok = version && (strncmp(version, want_version, strlen(want_version)) == 0 ||
                                 (client_version == 2 && strncmp(version, "OpenGL ES 3.", 12) == 0));
    int metal = renderer && strstr(renderer, "ANGLE Metal Renderer") != NULL;
    if (!version_ok) printf("GL_VERSION does not start with %s\n", want_version);
    if (!metal) printf("GL_RENDERER is not ANGLE's Metal renderer\n");
    if (client_version >= 2) printf("GL_SHADING_LANGUAGE_VERSION=%s\n", gl->GetString(GL_SHADING_LANGUAGE_VERSION));
    int ok = version_ok && metal;
    if (surfaceless) {
        /* No default framebuffer: only report that make-current worked. */
        GLenum e = gl->GetError();
        printf("surfaceless make-current ok, glGetError=0x%04x\n", e);
        ok = ok && e == GL_NO_ERROR;
    } else {
        gl->ClearColor(0x12 / 255.0f, 0x34 / 255.0f, 0x56 / 255.0f, 1.0f);
        gl->Clear(GL_COLOR_BUFFER_BIT);
        unsigned char px[4] = {0};
        gl->ReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
        GLenum e = gl->GetError();
        int match = px[0] == 0x12 && px[1] == 0x34 && px[2] == 0x56 && px[3] == 0xff && e == GL_NO_ERROR;
        printf("readback=0x%02x%02x%02x%02x glGetError=0x%04x expected=0x123456ff %s\n",
               px[0], px[1], px[2], px[3], e, match ? "MATCH" : "MISMATCH");
        ok = ok && match;
    }
    pMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    pDestroyContext(d, ctx);
    if (surf != EGL_NO_SURFACE) pDestroySurface(d, surf);
    return ok;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: %s <angle lib dir>\n", argv[0]); return 2; }
    if (!realpath(argv[1], lib_dir)) { perror(argv[1]); return 2; }
    char path[PATH_MAX + 32];
    snprintf(path, sizeof path, "%s/libEGL.dylib", lib_dir);
    printf("DYLD_LIBRARY_PATH=%s DYLD_FALLBACK_LIBRARY_PATH=%s\n",
           getenv("DYLD_LIBRARY_PATH") ? getenv("DYLD_LIBRARY_PATH") : "(unset)",
           getenv("DYLD_FALLBACK_LIBRARY_PATH") ? getenv("DYLD_FALLBACK_LIBRARY_PATH") : "(unset)");
    void *egl = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!egl) { printf("dlopen(%s) failed: %s\n", path, dlerror()); return 1; }
    printf("dlopen(%s) ok\n", path);
    print_loaded_images("after dlopen libEGL");

    pGetProcAddress = (PFNEGLGETPROCADDRESSPROC)must(egl, "eglGetProcAddress");
    pGetError = (PFNEGLGETERRORPROC)must(egl, "eglGetError");
    pGetDisplay = (PFNEGLGETDISPLAYPROC)must(egl, "eglGetDisplay");
    pInitialize = (PFNEGLINITIALIZEPROC)must(egl, "eglInitialize");
    pTerminate = (PFNEGLTERMINATEPROC)must(egl, "eglTerminate");
    pQueryString = (PFNEGLQUERYSTRINGPROC)must(egl, "eglQueryString");
    pChooseConfig = (PFNEGLCHOOSECONFIGPROC)must(egl, "eglChooseConfig");
    pCreatePbuffer = (PFNEGLCREATEPBUFFERSURFACEPROC)must(egl, "eglCreatePbufferSurface");
    pCreateContext = (PFNEGLCREATECONTEXTPROC)must(egl, "eglCreateContext");
    pMakeCurrent = (PFNEGLMAKECURRENTPROC)must(egl, "eglMakeCurrent");
    pDestroyContext = (PFNEGLDESTROYCONTEXTPROC)must(egl, "eglDestroyContext");
    pDestroySurface = (PFNEGLDESTROYSURFACEPROC)must(egl, "eglDestroySurface");
    pBindAPI = (PFNEGLBINDAPIPROC)must(egl, "eglBindAPI");
    pGetPlatformDisplay = (PFNEGLGETPLATFORMDISPLAYPROC)must(egl, "eglGetPlatformDisplay");
    pGetPlatformDisplayEXT = (PFNEGLGETPLATFORMDISPLAYEXTPROC)pGetProcAddress("eglGetPlatformDisplayEXT");

    printf("client extensions (EGL_NO_DISPLAY): %s\n", pQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS));

    /* GLES2/3 entry points through eglGetProcAddress (as Skia/egl_shim would). */
    struct GLFns gl2 = {
        (GetStringFn)pGetProcAddress("glGetString"), (ClearColorFn)pGetProcAddress("glClearColor"),
        (ClearFn)pGetProcAddress("glClear"), (ReadPixelsFn)pGetProcAddress("glReadPixels"),
        (GetErrorFn)pGetProcAddress("glGetError")};
    if (!gl2.GetString || !gl2.ClearColor || !gl2.Clear || !gl2.ReadPixels || !gl2.GetError) {
        printf("eglGetProcAddress for core GL functions returned NULL\n"); return 1;
    }


    /* 1. egl_shim.cpp first attempt: eglGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA,nullptr,nullptr). */
    printf("=== eglGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA=0x31DD) ===\n");
    if (pGetPlatformDisplayEXT) {
        EGLDisplay sd = pGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA, NULL, NULL);
        printf("result=%p eglGetError=0x%04x\n", sd, pGetError());
        if (sd != EGL_NO_DISPLAY) {
            EGLint ma = 0, mi = 0; EGLBoolean r = pInitialize(sd, &ma, &mi);
            printf("  eglInitialize=%d %d.%d err=0x%04x\n", r, ma, mi, pGetError());
        }
    } else printf("eglGetProcAddress(eglGetPlatformDisplayEXT)=NULL\n");
    {
        EGLDisplay sd = pGetPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, NULL, NULL);
        printf("core eglGetPlatformDisplay(0x31DD) result=%p eglGetError=0x%04x\n", sd, pGetError());
    }

    /* 2. Default display, no env forcing. */
    printf("=== eglGetDisplay(EGL_DEFAULT_DISPLAY) ===\n");
    EGLDisplay d = pGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major = 0, minor = 0;
    if (d == EGL_NO_DISPLAY || !pInitialize(d, &major, &minor)) {
        printf("default display init failed: d=%p err=0x%04x\n", d, pGetError()); return 1;
    }
    printf("eglInitialize ok: %d.%d\n", major, minor);
    print_display_info(d);
    print_loaded_images("after eglInitialize");
    failures += !render_test(d, "ES3 pbuffer", EGL_OPENGL_ES3_BIT, 3, &gl2, 0);
    failures += !render_test(d, "ES2 pbuffer", EGL_OPENGL_ES2_BIT, 2, &gl2, 0);
    failures += !render_test(d, "ES3 surfaceless", EGL_OPENGL_ES3_BIT, 3, &gl2, 1);

    /* 3. ES1 through libGLESv1_CM.dylib's own exports. */
    snprintf(path, sizeof path, "%s/libGLESv1_CM.dylib", lib_dir);
    void *gles1 = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!gles1) { printf("dlopen(%s) failed: %s\n", path, dlerror()); failures++; }
    else {
        struct GLFns gl1 = {(GetStringFn)must(gles1, "glGetString"), (ClearColorFn)must(gles1, "glClearColor"),
                            (ClearFn)must(gles1, "glClear"), (ReadPixelsFn)must(gles1, "glReadPixels"),
                            (GetErrorFn)must(gles1, "glGetError")};
        Dl_info info;
        if (dladdr((void *)gl1.ClearColor, &info)) printf("libGLESv1_CM glClearColor resolves into %s\n", info.dli_fname);
        print_loaded_images("after dlopen libGLESv1_CM");
        failures += !render_test(d, "ES1 pbuffer via libGLESv1_CM", EGL_OPENGL_ES_BIT, 1, &gl1, 0);
    }
    pTerminate(d);

    /* 4. Explicit ANGLE platform, Metal type. */
    printf("=== eglGetPlatformDisplayEXT(EGL_PLATFORM_ANGLE_ANGLE, TYPE=METAL) ===\n");
    EGLint pattrs[] = {EGL_PLATFORM_ANGLE_TYPE_ANGLE, EGL_PLATFORM_ANGLE_TYPE_METAL_ANGLE, EGL_NONE};
    EGLDisplay md = pGetPlatformDisplayEXT
                        ? pGetPlatformDisplayEXT(EGL_PLATFORM_ANGLE_ANGLE, (void *)EGL_DEFAULT_DISPLAY, pattrs)
                        : EGL_NO_DISPLAY;
    printf("result=%p err=0x%04x\n", md, pGetError());
    if (md != EGL_NO_DISPLAY && pInitialize(md, &major, &minor)) {
        printf("eglInitialize ok: %d.%d vendor=%s\n", major, minor, pQueryString(md, EGL_VENDOR));
        failures += !render_test(md, "ES3 pbuffer (explicit Metal)", EGL_OPENGL_ES3_BIT, 3, &gl2, 0);
        pTerminate(md);
    } else { printf("explicit Metal init failed: 0x%04x\n", pGetError()); failures++; }

    print_loaded_images("at exit");
    printf("RESULT failures=%d\n", failures);
    return failures ? 1 : 0;
}
