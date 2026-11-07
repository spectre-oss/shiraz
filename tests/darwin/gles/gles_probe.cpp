// SPDX-License-Identifier: GPL-3.0-only
// macOS port of tests/native/gles_probe.cpp: the reqGlEsVersion probe
// (native/gles_version.cpp, unchanged) must leave the caller's display, context
// and client API intact. Two setup steps differ on ANGLE Metal, and each is
// asserted instead of skipped:
// - ANGLE has no eglQueryDevicesEXT (EGL_EXT_device_enumeration), so the live
//   context lives on eglGetDisplay(EGL_DEFAULT_DISPLAY), which is also the
//   display the probe falls back to: the probe and the caller share one display.
// - ANGLE accepts only EGL_OPENGL_ES_API. The Linux probe selects EGL_OPENGL_API
//   to check that the probe restores the caller's API; here eglBindAPI must
//   fail with EGL_BAD_PARAMETER and leave EGL_OPENGL_ES_API bound. If ANGLE ever
//   accepts EGL_OPENGL_API, the Linux check runs instead.
// Linked like build/macos/libshiraz-host.so: EGL calls go to libEGL_shim, which
// reaches ANGLE through SHIRAZ_EGL_LIBRARY.
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <iostream>
extern "C" jint Java_android_app_ActivityManager_nativeGlesVersion(JNIEnv*, jclass);
int main() {
    if (eglGetProcAddress("eglQueryDevicesEXT")) std::cerr << "note: eglQueryDevicesEXT is present; the Linux probe could run unchanged\n";
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) return 1;
    if (!eglInitialize(display, nullptr, nullptr) || !eglBindAPI(EGL_OPENGL_ES_API)) return 2;
    const EGLint attrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, attrs);
    if (context == EGL_NO_CONTEXT || !eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) return 3;
    // Probe with another client API selected where the EGL accepts one.
    const bool desktop = eglBindAPI(EGL_OPENGL_API);
    const EGLint bindError = desktop ? EGL_SUCCESS : eglGetError();
    if (!desktop && (bindError != EGL_BAD_PARAMETER || eglQueryAPI() != EGL_OPENGL_ES_API)) {
        std::cerr << "eglBindAPI(EGL_OPENGL_API) failed with 0x" << std::hex << bindError << " api=0x" << eglQueryAPI() << "\n";
        return 4;
    }
    const EGLenum callerApi = desktop ? EGL_OPENGL_API : EGL_OPENGL_ES_API;
    std::cout << "GLES_PROBE_CALLER_API " << (desktop ? "opengl" : "opengl_es (EGL_OPENGL_API rejected with EGL_BAD_PARAMETER)") << "\n";
    int errors = 0;
    if (Java_android_app_ActivityManager_nativeGlesVersion(nullptr, nullptr) != 0x30000) { std::cerr << "GLES probe failed\n"; ++errors; }
    if (eglQueryAPI() != callerApi) { std::cerr << "Probe changed caller API\n"; ++errors; }
    eglBindAPI(EGL_OPENGL_ES_API);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (!eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) { std::cerr << "Probe invalidated live display/context\n"; ++errors; }
    for (int i = 0; i < 8 && !errors; ++i) {
        if (Java_android_app_ActivityManager_nativeGlesVersion(nullptr, nullptr) != 0x30000 || eglQueryAPI() != EGL_OPENGL_ES_API || eglGetCurrentContext() != context) ++errors;
        EGLint version = 0;
        if (!eglQueryContext(display, context, EGL_CONTEXT_CLIENT_VERSION, &version) || version != 3) ++errors;
    }
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglTerminate(display);
    if (errors) return 5;
    std::cout << "GLES_PROBE_PRESERVES_DISPLAY_CONTEXT_API_OK\n";
}
