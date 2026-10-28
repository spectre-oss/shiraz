// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <GLES/gl.h>
#include <cstdint>
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glFlush(JNIEnv*,jobject){ glFlush(); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glFinish(JNIEnv*,jobject){ glFinish(); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glClear(JNIEnv*,jobject,jint mask){ glClear(mask); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glClearColor(JNIEnv*,jobject,jfloat red,jfloat green,jfloat blue,jfloat alpha){ glClearColor(red,green,blue,alpha); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glColor4f(JNIEnv*,jobject,jfloat red,jfloat green,jfloat blue,jfloat alpha){ glColor4f(red,green,blue,alpha); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glBindTexture(JNIEnv*,jobject,jint target,jint texture){ glBindTexture(target,texture); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glBlendFunc(JNIEnv*,jobject,jint source,jint destination){ glBlendFunc(source,destination); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glEnable(JNIEnv*,jobject,jint capability){ glEnable(capability); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glEnableClientState(JNIEnv*,jobject,jint array){ glEnableClientState(array); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glLoadIdentity(JNIEnv*,jobject){ glLoadIdentity(); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glMatrixMode(JNIEnv*,jobject,jint mode){ glMatrixMode(mode); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glOrthof(JNIEnv*,jobject,jfloat left,jfloat right,jfloat bottom,jfloat top,jfloat near,jfloat far){ glOrthof(left,right,bottom,top,near,far); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glTexParameterf(JNIEnv*,jobject,jint target,jint name,jfloat value){ glTexParameterf(target,name,value); }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glViewport(JNIEnv*,jobject,jint x,jint y,jint width,jint height){ glViewport(x,y,width,height); }
namespace {
void fail(JNIEnv* env,const char* message) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),message); }
void* buffer(JNIEnv* env,jobject object,uint64_t required) {
 if(!object){fail(env,"null GL buffer");return nullptr;}
 auto* address=static_cast<unsigned char*>(env->GetDirectBufferAddress(object));
 if(!address){fail(env,"GLES1 currently requires direct buffers");return nullptr;}
 int bytes=1;
 const char* types[]={"java/nio/ShortBuffer","java/nio/CharBuffer","java/nio/IntBuffer","java/nio/FloatBuffer","java/nio/LongBuffer","java/nio/DoubleBuffer"};
 const int sizes[]={2,2,4,4,8,8};
 for(int i=0;i<6;i++){jclass type=env->FindClass(types[i]);bool match=env->IsInstanceOf(object,type);env->DeleteLocalRef(type);if(match){bytes=sizes[i];break;}}
 jclass type=env->FindClass("java/nio/Buffer");
 jint position=env->CallIntMethod(object,env->GetMethodID(type,"position","()I"));
 jint remaining=env->CallIntMethod(object,env->GetMethodID(type,"remaining","()I"));env->DeleteLocalRef(type);
 if(env->ExceptionCheck())return nullptr;
 if(position<0||remaining<0||required>uint64_t(remaining)*bytes){fail(env,"GL buffer too small");return nullptr;}
 return address+uint64_t(position)*bytes;
}
void textures(JNIEnv* env,jint count,jintArray array,jint offset,bool create) {
 if(!array||count<0||offset<0||offset>env->GetArrayLength(array)||count>env->GetArrayLength(array)-offset){fail(env,"texture array range");return;}
 jint* values=env->GetIntArrayElements(array,nullptr);if(!values)return;
 if(create)glGenTextures(count,reinterpret_cast<GLuint*>(values+offset));else glDeleteTextures(count,reinterpret_cast<GLuint*>(values+offset));
 env->ReleaseIntArrayElements(array,values,create?0:JNI_ABORT);
}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glGenTextures(JNIEnv* e,jobject,jint n,jintArray a,jint o){textures(e,n,a,o,true);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glDeleteTextures(JNIEnv* e,jobject,jint n,jintArray a,jint o){textures(e,n,a,o,false);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glDrawElements(JNIEnv* e,jobject,jint mode,jint count,jint type,jobject indices){
 if(count<0||(type!=GL_UNSIGNED_BYTE&&type!=GL_UNSIGNED_SHORT)){fail(e,"index type/count");return;}
 void* data=buffer(e,indices,uint64_t(count)*(type==GL_UNSIGNED_SHORT?2:1));if(data)glDrawElements(mode,count,type,data);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_nativeglVertexPointer(JNIEnv* e,jobject,jint size,jint type,jint stride,jobject pointer){
 if(size<2||size>4||stride<0){fail(e,"vertex size/stride");return;}
 void* data=buffer(e,pointer,0);if(data)glVertexPointer(size,type,stride,data);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_nativeglTexCoordPointer(JNIEnv* e,jobject,jint size,jint type,jint stride,jobject pointer){
 if(size<2||size>4||stride<0){fail(e,"texture coordinate size/stride");return;}
 void* data=buffer(e,pointer,0);if(data)glTexCoordPointer(size,type,stride,data);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostGL10_glReadPixels(JNIEnv* e,jobject,jint x,jint y,jint width,jint height,jint format,jint type,jobject pixels){
 if(width<0||height<0||format!=GL_RGBA||type!=GL_UNSIGNED_BYTE){fail(e,"GLES1 readback supports RGBA unsigned bytes");return;}
 GLint alignment=4;glGetIntegerv(GL_PACK_ALIGNMENT,&alignment);
 uint64_t row=uint64_t(width)*4;uint64_t stride=(row+alignment-1)/alignment*alignment;
 uint64_t needed=height?stride*uint64_t(height-1)+row:0;
 void* data=buffer(e,pixels,needed);if(data)glReadPixels(x,y,width,height,format,type,data);
}
#include <vector>
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLUtils_uploadNative(JNIEnv* e,jclass,jint target,jint level,jint x,jint y,jint width,jint height,jint format,jint type,jintArray pixels,jboolean premultiplied,jboolean sub){
 if(width<0||height<0||!pixels||uint64_t(width)*height>uint64_t(e->GetArrayLength(pixels))){fail(e,"bitmap dimensions");return;}
 int bytes=0;
 if(type==GL_UNSIGNED_BYTE)bytes=format==GL_RGBA?4:format==GL_RGB?3:format==GL_ALPHA?1:0;
 if(type==GL_UNSIGNED_SHORT_5_6_5&&format==GL_RGB)bytes=2;
 if(!bytes){fail(e,"unsupported bitmap texture format/type");return;}
 size_t count=size_t(width)*height;
 std::vector<unsigned char> data(count*bytes);
 jint* source=e->GetIntArrayElements(pixels,nullptr);if(!source)return;
 for(size_t i=0;i<count;i++){
  uint32_t value=source[i];unsigned a=value>>24,r=(value>>16)&255,g=(value>>8)&255,b=value&255;
  if(premultiplied){r=(r*a+127)/255;g=(g*a+127)/255;b=(b*a+127)/255;}
  unsigned char* p=data.data()+i*bytes;
  if(bytes==1)*p=a;
  else if(bytes==2){uint16_t packed=((r>>3)<<11)|((g>>2)<<5)|(b>>3);p[0]=packed&255;p[1]=packed>>8;}
  else{p[0]=r;p[1]=g;p[2]=b;if(bytes==4)p[3]=a;}
 }
 e->ReleaseIntArrayElements(pixels,source,JNI_ABORT);
 GLint alignment=4;glGetIntegerv(GL_UNPACK_ALIGNMENT,&alignment);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
 if(sub)glTexSubImage2D(target,level,x,y,width,height,format,type,data.data());
 else glTexImage2D(target,level,format,width,height,0,format,type,data.data());
 glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);
}
