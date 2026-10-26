// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <string>

namespace {
bool extension(EGLDisplay display, const char* name) {
    const char* text = eglQueryString(display, EGL_EXTENSIONS);
    return text && std::string(" ").append(text).append(" ").find(std::string(" ") + name + " ") != std::string::npos;
}
jint contextVersion(EGLDisplay display) {
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) return 0;
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint configAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
    EGLConfig config = nullptr;
    EGLint count = 0;
    bool chosen = eglChooseConfig(display, configAttribs, &config, 1, &count) && count > 0;
    const EGLint contextAttribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 0, EGL_NONE};
    EGLContext context = chosen ? eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs) : EGL_NO_CONTEXT;
    if (context == EGL_NO_CONTEXT && extension(display, "EGL_KHR_no_config_context"))
        context = eglCreateContext(display, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, contextAttribs);
    bool created = context != EGL_NO_CONTEXT;
    if (created) eglDestroyContext(display, context);
    eglTerminate(display);
    return created ? 0x30000 : 0;
}
}

extern "C" JNIEXPORT jint JNICALL Java_android_app_ActivityManager_nativeGlesVersion(JNIEnv*, jclass) {
    auto query = reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(eglGetProcAddress("eglQueryDevicesEXT"));
    auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(eglGetProcAddress("eglGetPlatformDisplay"));
    if (query && platform) {
        EGLDeviceEXT devices[8];
        EGLint count = 0;
        if (query(8, devices, &count)) {
            for (EGLint i = 0; i < count; ++i) {
                jint version = contextVersion(platform(EGL_PLATFORM_DEVICE_EXT, devices[i], nullptr));
                if (version != 0) return version;
            }
        }
    }
    if (platform) {
        jint version = contextVersion(platform(EGL_PLATFORM_SURFACELESS_MESA, nullptr, nullptr));
        if (version != 0) return version;
    }
    return contextVersion(eglGetDisplay(EGL_DEFAULT_DISPLAY));
}
