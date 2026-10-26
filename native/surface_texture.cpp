// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <dlfcn.h>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {
struct Api {
 void* library=nullptr;
 PFNEGLGETCURRENTCONTEXTPROC context=nullptr;
 PFNEGLGETCURRENTDISPLAYPROC display=nullptr;
 PFNEGLCREATEIMAGEKHRPROC create=nullptr;
 PFNEGLDESTROYIMAGEKHRPROC destroy=nullptr;
 PFNGLEGLIMAGETARGETTEXTURE2DOESPROC target=nullptr;
 Api() {
  library=dlopen("libEGL_shiraz.so.1",RTLD_NOW|RTLD_LOCAL);
  if(!library)library=dlopen("libEGL_shim.so",RTLD_NOW|RTLD_LOCAL);
  if(!library)library=dlopen("libEGL.so.1",RTLD_NOW|RTLD_LOCAL);
  if(!library)return;
  context=reinterpret_cast<PFNEGLGETCURRENTCONTEXTPROC>(dlsym(library,"eglGetCurrentContext"));
  display=reinterpret_cast<PFNEGLGETCURRENTDISPLAYPROC>(dlsym(library,"eglGetCurrentDisplay"));
  auto proc=reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(dlsym(library,"eglGetProcAddress"));
  if(proc){create=reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(proc("eglCreateImageKHR"));destroy=reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(proc("eglDestroyImageKHR"));target=reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(proc("glEGLImageTargetTexture2DOES"));}
 }
 bool ready()const{return context&&display&&create&&destroy&&target;}
};
Api& api(){static Api value;return value;}
struct Texture {
 GLuint name=0,source=0;bool attached;
 EGLContext context=EGL_NO_CONTEXT;EGLDisplay display=EGL_NO_DISPLAY;EGLImageKHR image=EGL_NO_IMAGE_KHR;
 unsigned updates=0;uint64_t revision=0;int width=0,height=0;std::vector<uint32_t> pixels;
 Texture(GLuint value,bool enabled):name(value),attached(enabled){}
};
void fail(JNIEnv* env,const char* message){jclass cls=env->FindClass("java/lang/IllegalStateException");if(cls){env->ThrowNew(cls,message);env->DeleteLocalRef(cls);}}
bool current(JNIEnv* env,Texture* t){
 Api& a=api();if(!a.ready()||a.context()==EGL_NO_CONTEXT){fail(env,"SurfaceTexture requires a current EGL context");return false;}
 if(t->context!=EGL_NO_CONTEXT&&(t->context!=a.context()||t->display!=a.display())){fail(env,"SurfaceTexture belongs to another EGL context");return false;}
 t->context=a.context();t->display=a.display();return true;
}
void imageRelease(Texture* t){if(t->image!=EGL_NO_IMAGE_KHR){api().destroy(t->display,t->image);t->image=EGL_NO_IMAGE_KHR;}if(t->source){glDeleteTextures(1,&t->source);t->source=0;}t->revision=0;t->width=t->height=0;}
}
extern "C" JNIEXPORT jlong JNICALL Java_android_graphics_SurfaceTexture_nativeCreate(JNIEnv*,jclass,jint name,jboolean attached){return reinterpret_cast<jlong>(new Texture(GLuint(name),attached));}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeAttach(JNIEnv* env,jclass,jlong value,jint name){
 auto* t=reinterpret_cast<Texture*>(value);if(t->attached){fail(env,"SurfaceTexture is already attached");return;}
 if(!current(env,t))return;
 glBindTexture(GL_TEXTURE_EXTERNAL_OES,GLuint(name));
 if(glGetError()!=GL_NO_ERROR){fail(env,"Cannot attach external texture name");return;}
 t->name=GLuint(name);t->attached=true;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeDetach(JNIEnv* env,jclass,jlong value){
 auto* t=reinterpret_cast<Texture*>(value);if(!t->attached){fail(env,"SurfaceTexture is already detached");return;}
 // Construction may happen without an EGL context; no GL objects exist yet.
 if(t->context!=EGL_NO_CONTEXT){if(!current(env,t))return;imageRelease(t);if(t->name)glDeleteTextures(1,&t->name);}
 t->name=0;t->attached=false;t->context=EGL_NO_CONTEXT;t->display=EGL_NO_DISPLAY;
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_graphics_SurfaceTexture_nativeUpdate(JNIEnv* env,jclass,jlong value,jlong window){
 auto* t=reinterpret_cast<Texture*>(value);auto* w=reinterpret_cast<ANativeWindow*>(window);
 if(!t->attached){fail(env,"SurfaceTexture is detached");return false;}if(!current(env,t))return false;
 uint64_t revision=shiraz_window_revision(w);if(!revision||revision==t->revision)return false;
 int width=ANativeWindow_getWidth(w),height=ANativeWindow_getHeight(w);
 if(width<1||height<1||width>8192||height>8192||uint64_t(width)*height>16*1024*1024){fail(env,"Invalid SurfaceTexture frame dimensions");return false;}
 t->pixels.resize(size_t(width)*height);
 if(shiraz_window_copy_argb(w,t->pixels.data(),t->pixels.size())||revision!=shiraz_window_revision(w))return false;
 for(auto& p:t->pixels)p=(p&0xff00ff00u)|((p&255)<<16)|((p>>16)&255);
 // Reserve the external name before allocating the private upload texture.
 glBindTexture(GL_TEXTURE_EXTERNAL_OES,t->name);
 const char* version=reinterpret_cast<const char*>(glGetString(GL_VERSION));
 bool es3=version&&strstr(version,"OpenGL ES 3");
 GLint unpack=0,row=0,skipRows=0,skipPixels=0;
 if(es3){glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING,&unpack);glGetIntegerv(GL_UNPACK_ROW_LENGTH,&row);glGetIntegerv(GL_UNPACK_SKIP_ROWS,&skipRows);glGetIntegerv(GL_UNPACK_SKIP_PIXELS,&skipPixels);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);}
 GLint binding=0,alignment=4;glGetIntegerv(GL_TEXTURE_BINDING_2D,&binding);glGetIntegerv(GL_UNPACK_ALIGNMENT,&alignment);
 glPixelStorei(GL_UNPACK_ALIGNMENT,4);
 bool resized=width!=t->width||height!=t->height;
 if(resized){imageRelease(t);glGenTextures(1,&t->source);}
 glBindTexture(GL_TEXTURE_2D,t->source);
 if(resized){glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,t->pixels.data());}
 else glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,t->pixels.data());
 glBindTexture(GL_TEXTURE_2D,GLuint(binding));glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);
 if(es3){glBindBuffer(GL_PIXEL_UNPACK_BUFFER,GLuint(unpack));glPixelStorei(GL_UNPACK_ROW_LENGTH,row);glPixelStorei(GL_UNPACK_SKIP_ROWS,skipRows);glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipPixels);}
 if(resized){const EGLint attrs[]={EGL_GL_TEXTURE_LEVEL_KHR,0,EGL_IMAGE_PRESERVED_KHR,EGL_TRUE,EGL_NONE};t->image=api().create(t->display,t->context,EGL_GL_TEXTURE_2D_KHR,reinterpret_cast<EGLClientBuffer>(uintptr_t(t->source)),attrs);}
 if(t->image==EGL_NO_IMAGE_KHR){fail(env,"Cannot create SurfaceTexture EGL image");return false;}
 glBindTexture(GL_TEXTURE_EXTERNAL_OES,t->name);api().target(GL_TEXTURE_EXTERNAL_OES,t->image);
 if(glGetError()!=GL_NO_ERROR){fail(env,"Cannot bind SurfaceTexture external image");return false;}
 t->width=width;t->height=height;t->revision=revision;if(std::getenv("SHIRAZ_TRACE_VIDEO")&&++t->updates<=8)std::fprintf(stderr,"SHIRAZ_TEXTURE_FRAME updates=%u revision=%llu\n",t->updates,static_cast<unsigned long long>(revision));return true;
}
extern "C" JNIEXPORT void JNICALL Java_android_graphics_SurfaceTexture_nativeRelease(JNIEnv*,jclass,jlong value){
 auto* t=reinterpret_cast<Texture*>(value);if(!t)return;
 if(t->image!=EGL_NO_IMAGE_KHR)api().destroy(t->display,t->image);
 // GL names owned by a different context are reclaimed with that context.
 if(t->context!=EGL_NO_CONTEXT&&api().context()==t->context&&api().display()==t->display){if(t->source)glDeleteTextures(1,&t->source);}
 delete t;
}
