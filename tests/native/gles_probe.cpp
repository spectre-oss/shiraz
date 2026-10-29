// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <iostream>
extern "C" jint Java_android_app_ActivityManager_nativeGlesVersion(JNIEnv*, jclass);
int main() {
    auto query=reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(eglGetProcAddress("eglQueryDevicesEXT"));
    auto platform=reinterpret_cast<PFNEGLGETPLATFORMDISPLAYEXTPROC>(eglGetProcAddress("eglGetPlatformDisplayEXT"));
    EGLDeviceEXT devices[8]; EGLint count=0;
    if(!query||!platform||!query(8,devices,&count)||!count)return 1;
    EGLDisplay display=platform(EGL_PLATFORM_DEVICE_EXT,devices[0],nullptr);
    if(!eglInitialize(display,nullptr,nullptr)||!eglBindAPI(EGL_OPENGL_ES_API))return 2;
    const EGLint attrs[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};
    EGLContext context=eglCreateContext(display,EGL_NO_CONFIG_KHR,EGL_NO_CONTEXT,attrs);
    if(context==EGL_NO_CONTEXT||!eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,context))return 3;
    // Probe with another client API selected, while a live ES context still exists.
    if(!eglBindAPI(EGL_OPENGL_API))return 4;
    int errors=0;
    if(Java_android_app_ActivityManager_nativeGlesVersion(nullptr,nullptr)!=0x30000){std::cerr<<"GLES probe failed\n";++errors;}
    if(eglQueryAPI()!=EGL_OPENGL_API){std::cerr<<"Probe changed caller API\n";++errors;}
    eglBindAPI(EGL_OPENGL_ES_API);
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    if(!eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,context)){std::cerr<<"Probe invalidated live display/context\n";++errors;}
    for(int i=0;i<8&&!errors;++i){
        if(Java_android_app_ActivityManager_nativeGlesVersion(nullptr,nullptr)!=0x30000||eglQueryAPI()!=EGL_OPENGL_ES_API||eglGetCurrentContext()!=context)++errors;
        EGLint version=0;if(!eglQueryContext(display,context,EGL_CONTEXT_CLIENT_VERSION,&version)||version!=3)++errors;
    }
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    eglDestroyContext(display,context);eglTerminate(display);
    if(errors)return 5;
    std::cout<<"GLES_PROBE_PRESERVES_DISPLAY_CONTEXT_API_OK\n";
}
