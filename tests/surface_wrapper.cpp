// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <jni.h>
extern "C" ANativeWindow* ANativeWindow_fromSurface(JNIEnv*,jobject);
extern "C" jobject ANativeWindow_toSurface(JNIEnv*,ANativeWindow*);
extern "C" JNIEXPORT jobject JNICALL Java_SurfaceSmoke_wrap(JNIEnv* env,jclass,jobject source){
    ANativeWindow* window=ANativeWindow_fromSurface(env,source);
    if(!window)return nullptr;
    jobject result=ANativeWindow_toSurface(env,window);
    ANativeWindow_release(window);
    return result;
}
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <cstdio>
namespace {EGLDisplay display;EGLContext context;EGLSurface surface;
GLuint shader(GLenum type,const char* source){GLuint s=glCreateShader(type);glShaderSource(s,1,&source,nullptr);glCompileShader(s);return s;}}
extern "C" JNIEXPORT jint JNICALL Java_SurfaceSmoke_glBegin(JNIEnv*,jclass){
 display=eglGetDisplay(EGL_DEFAULT_DISPLAY);if(!eglInitialize(display,nullptr,nullptr))return 0;
 eglBindAPI(EGL_OPENGL_ES_API);EGLint attrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_NONE};EGLConfig config;EGLint count;
 if(!eglChooseConfig(display,attrs,&config,1,&count)||!count)return 0;
 EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE},pa[]={EGL_WIDTH,2,EGL_HEIGHT,2,EGL_NONE};
 context=eglCreateContext(display,config,EGL_NO_CONTEXT,ca);surface=eglCreatePbufferSurface(display,config,pa);
 if(!eglMakeCurrent(display,surface,surface,context))return 0;
 std::printf("SURFACE_TEXTURE_RENDERER %s\n",glGetString(GL_RENDERER));
 GLuint texture;glGenTextures(1,&texture);return texture;
}
extern "C" JNIEXPORT jint JNICALL Java_SurfaceSmoke_glSample(JNIEnv*,jclass,jint texture){
 GLuint vs=shader(GL_VERTEX_SHADER,"attribute vec2 position;varying vec2 uv;void main(){gl_Position=vec4(position,0.,1.);uv=position*.5+.5;}");
 GLuint fs=shader(GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 uv;uniform samplerExternalOES image;void main(){gl_FragColor=texture2D(image,uv);}");
 GLuint program=glCreateProgram();glAttachShader(program,vs);glAttachShader(program,fs);glBindAttribLocation(program,0,"position");glLinkProgram(program);glUseProgram(program);
 glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_EXTERNAL_OES,GLuint(texture));glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
 glUniform1i(glGetUniformLocation(program,"image"),0);GLfloat vertices[]={-1,-1,1,-1,-1,1,1,1};glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,vertices);glEnableVertexAttribArray(0);glViewport(0,0,2,2);glDrawArrays(GL_TRIANGLE_STRIP,0,4);
 unsigned char pixel[4]={};glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);GLenum error=glGetError();
 glDisableVertexAttribArray(0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);
 if(error)return 0;
 return jint((uint32_t(pixel[3])<<24)|(uint32_t(pixel[0])<<16)|(uint32_t(pixel[1])<<8)|pixel[2]);
}
extern "C" JNIEXPORT void JNICALL Java_SurfaceSmoke_glEnd(JNIEnv*,jclass){eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(display,surface);eglDestroyContext(display,context);eglTerminate(display);}
