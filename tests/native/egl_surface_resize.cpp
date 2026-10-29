// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <EGL/egl.h>
#include <dlfcn.h>
extern "C" JNIEXPORT jboolean JNICALL Java_EglSurfaceResizeSmoke_currentSize(JNIEnv*,jclass,jint width,jint height){
 void* lib=dlopen("libEGL_shiraz.so.1",RTLD_NOW|RTLD_LOCAL);if(!lib)return JNI_FALSE;
 auto current=reinterpret_cast<PFNEGLGETCURRENTSURFACEPROC>(dlsym(lib,"eglGetCurrentSurface"));
 auto display=reinterpret_cast<PFNEGLGETCURRENTDISPLAYPROC>(dlsym(lib,"eglGetCurrentDisplay"));
 auto query=reinterpret_cast<PFNEGLQUERYSURFACEPROC>(dlsym(lib,"eglQuerySurface"));
 EGLint w=0,h=0;bool valid=current&&display&&query&&query(display(),current(EGL_DRAW),EGL_WIDTH,&w)&&query(display(),current(EGL_DRAW),EGL_HEIGHT,&h)&&w==width&&h==height;
 dlclose(lib);return valid?JNI_TRUE:JNI_FALSE;
}
