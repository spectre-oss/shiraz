// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <cstdint>
#include <vector>
#include <limits>
#include <algorithm>
#include <cstring>
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glActiveTexture(JNIEnv*,jclass,jint texture){glActiveTexture(texture);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glAttachShader(JNIEnv*,jclass,jint program,jint shader){glAttachShader(program,shader);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBindBuffer(JNIEnv*,jclass,jint target,jint buffer){glBindBuffer(target,buffer);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBindFramebuffer(JNIEnv*,jclass,jint target,jint framebuffer){glBindFramebuffer(target,framebuffer);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBindRenderbuffer(JNIEnv*,jclass,jint target,jint renderbuffer){glBindRenderbuffer(target,renderbuffer);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBindTexture(JNIEnv*,jclass,jint target,jint texture){glBindTexture(target,texture);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBlendColor(JNIEnv*,jclass,jfloat r,jfloat g,jfloat b,jfloat a){glBlendColor(r,g,b,a);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBlendEquation(JNIEnv*,jclass,jint mode){glBlendEquation(mode);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBlendEquationSeparate(JNIEnv*,jclass,jint rgb,jint alpha){glBlendEquationSeparate(rgb,alpha);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBlendFunc(JNIEnv*,jclass,jint source,jint destination){glBlendFunc(source,destination);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBlendFuncSeparate(JNIEnv*,jclass,jint srgb,jint drgb,jint sa,jint da){glBlendFuncSeparate(srgb,drgb,sa,da);}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glCheckFramebufferStatus(JNIEnv*,jclass,jint target){return glCheckFramebufferStatus(target);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glClear(JNIEnv*,jclass,jint mask){glClear(mask);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glClearColor(JNIEnv*,jclass,jfloat r,jfloat g,jfloat b,jfloat a){glClearColor(r,g,b,a);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glClearDepthf(JNIEnv*,jclass,jfloat depth){glClearDepthf(depth);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glClearStencil(JNIEnv*,jclass,jint stencil){glClearStencil(stencil);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glColorMask(JNIEnv*,jclass,jboolean r,jboolean g,jboolean b,jboolean a){glColorMask(r,g,b,a);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glCompileShader(JNIEnv*,jclass,jint shader){glCompileShader(shader);}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glCreateProgram(JNIEnv*,jclass){return glCreateProgram();}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glCreateShader(JNIEnv*,jclass,jint type){return glCreateShader(type);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glCullFace(JNIEnv*,jclass,jint mode){glCullFace(mode);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteProgram(JNIEnv*,jclass,jint program){glDeleteProgram(program);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteShader(JNIEnv*,jclass,jint shader){glDeleteShader(shader);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDepthFunc(JNIEnv*,jclass,jint function){glDepthFunc(function);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDepthMask(JNIEnv*,jclass,jboolean mask){glDepthMask(mask);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDepthRangef(JNIEnv*,jclass,jfloat near,jfloat far){glDepthRangef(near,far);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDetachShader(JNIEnv*,jclass,jint program,jint shader){glDetachShader(program,shader);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDisable(JNIEnv*,jclass,jint cap){glDisable(cap);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDisableVertexAttribArray(JNIEnv*,jclass,jint index){glDisableVertexAttribArray(index);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDrawArrays(JNIEnv*,jclass,jint mode,jint first,jint count){glDrawArrays(mode,first,count);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glEnable(JNIEnv*,jclass,jint cap){glEnable(cap);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glEnableVertexAttribArray(JNIEnv*,jclass,jint index){glEnableVertexAttribArray(index);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glFinish(JNIEnv*,jclass){glFinish();}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glFlush(JNIEnv*,jclass){glFlush();}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glFramebufferRenderbuffer(JNIEnv*,jclass,jint target,jint attachment,jint renderbufferTarget,jint renderbuffer){glFramebufferRenderbuffer(target,attachment,renderbufferTarget,renderbuffer);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glFramebufferTexture2D(JNIEnv*,jclass,jint target,jint attachment,jint textureTarget,jint texture,jint level){glFramebufferTexture2D(target,attachment,textureTarget,texture,level);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glFrontFace(JNIEnv*,jclass,jint mode){glFrontFace(mode);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGenerateMipmap(JNIEnv*,jclass,jint target){glGenerateMipmap(target);}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glGetError(JNIEnv*,jclass){return glGetError();}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glHint(JNIEnv*,jclass,jint target,jint mode){glHint(target,mode);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glLineWidth(JNIEnv*,jclass,jfloat width){glLineWidth(width);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glLinkProgram(JNIEnv*,jclass,jint program){glLinkProgram(program);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glPixelStorei(JNIEnv*,jclass,jint name,jint value){glPixelStorei(name,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glPolygonOffset(JNIEnv*,jclass,jfloat factor,jfloat units){glPolygonOffset(factor,units);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glRenderbufferStorage(JNIEnv*,jclass,jint target,jint format,jint width,jint height){glRenderbufferStorage(target,format,width,height);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glSampleCoverage(JNIEnv*,jclass,jfloat value,jboolean invert){glSampleCoverage(value,invert);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glScissor(JNIEnv*,jclass,jint x,jint y,jint width,jint height){glScissor(x,y,width,height);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glStencilFunc(JNIEnv*,jclass,jint function,jint reference,jint mask){glStencilFunc(function,reference,mask);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glStencilMask(JNIEnv*,jclass,jint mask){glStencilMask(mask);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glStencilOp(JNIEnv*,jclass,jint fail,jint depthFail,jint pass){glStencilOp(fail,depthFail,pass);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glTexParameterf(JNIEnv*,jclass,jint target,jint name,jfloat value){glTexParameterf(target,name,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glTexParameteri(JNIEnv*,jclass,jint target,jint name,jint value){glTexParameteri(target,name,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform1f(JNIEnv*,jclass,jint location,jfloat x){glUniform1f(location,x);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform2f(JNIEnv*,jclass,jint location,jfloat x,jfloat y){glUniform2f(location,x,y);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform3f(JNIEnv*,jclass,jint location,jfloat x,jfloat y,jfloat z){glUniform3f(location,x,y,z);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform4f(JNIEnv*,jclass,jint location,jfloat x,jfloat y,jfloat z,jfloat w){glUniform4f(location,x,y,z,w);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform1i(JNIEnv*,jclass,jint location,jint x){glUniform1i(location,x);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform2i(JNIEnv*,jclass,jint location,jint x,jint y){glUniform2i(location,x,y);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform3i(JNIEnv*,jclass,jint location,jint x,jint y,jint z){glUniform3i(location,x,y,z);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform4i(JNIEnv*,jclass,jint location,jint x,jint y,jint z,jint w){glUniform4i(location,x,y,z,w);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUseProgram(JNIEnv*,jclass,jint program){glUseProgram(program);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glValidateProgram(JNIEnv*,jclass,jint program){glValidateProgram(program);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glViewport(JNIEnv*,jclass,jint x,jint y,jint width,jint height){glViewport(x,y,width,height);}

namespace {
void fail(JNIEnv* e,const char* message){jclass type=e->FindClass("java/lang/IllegalArgumentException");if(type)e->ThrowNew(type,message);}
bool range(JNIEnv* e,jarray a,jint offset,int64_t count){if(!a||offset<0||count<0||count>1048576||offset>e->GetArrayLength(a)||count>e->GetArrayLength(a)-offset){fail(e,"GL array range");return false;}return true;}
void* direct(JNIEnv* e,jobject value,uint64_t bytes,bool write=false){
 if(!value){fail(e,"null GL buffer");return nullptr;}
 auto* address=static_cast<unsigned char*>(e->GetDirectBufferAddress(value));
 if(!address){fail(e,"GL upload/readback requires a direct buffer");return nullptr;}
 jclass base=e->FindClass("java/nio/Buffer");if(!base)return nullptr;
 jmethodID positionMethod=e->GetMethodID(base,"position","()I");
 if(!positionMethod){e->DeleteLocalRef(base);return nullptr;}
 jint position=e->CallIntMethod(value,positionMethod);
 if(e->ExceptionCheck()){e->DeleteLocalRef(base);return nullptr;}
 jmethodID remainingMethod=e->GetMethodID(base,"remaining","()I");
 if(!remainingMethod){e->DeleteLocalRef(base);return nullptr;}
 jint remaining=e->CallIntMethod(value,remainingMethod);
 if(e->ExceptionCheck()){e->DeleteLocalRef(base);return nullptr;}
 if(write){
  jmethodID readOnlyMethod=e->GetMethodID(base,"isReadOnly","()Z");
  if(!readOnlyMethod){e->DeleteLocalRef(base);return nullptr;}
  bool readOnly=e->CallBooleanMethod(value,readOnlyMethod);
  if(e->ExceptionCheck()){e->DeleteLocalRef(base);return nullptr;}
  if(readOnly){e->DeleteLocalRef(base);fail(e,"read-only GL destination");return nullptr;}
 }
 e->DeleteLocalRef(base);
 int unit=1;const char* names[]={"java/nio/ShortBuffer","java/nio/CharBuffer","java/nio/IntBuffer","java/nio/FloatBuffer","java/nio/LongBuffer","java/nio/DoubleBuffer"};int sizes[]={2,2,4,4,8,8};
 for(int i=0;i<6;i++){jclass type=e->FindClass(names[i]);if(!type)return nullptr;bool match=e->IsInstanceOf(value,type);e->DeleteLocalRef(type);if(match){unit=sizes[i];break;}}
 if(e->ExceptionCheck())return nullptr;
 if(position<0||remaining<0||bytes>uint64_t(remaining)*unit){fail(e,"GL buffer too small");return nullptr;}
 return address+uint64_t(position)*unit;
}
int integerCount(GLenum name){
 switch(name){case GL_VIEWPORT:case GL_SCISSOR_BOX:case GL_COLOR_WRITEMASK:case GL_COLOR_CLEAR_VALUE:case GL_BLEND_COLOR:return 4;
 case GL_MAX_VIEWPORT_DIMS:case GL_DEPTH_RANGE:case GL_ALIASED_LINE_WIDTH_RANGE:case GL_ALIASED_POINT_SIZE_RANGE:return 2;
 case GL_COMPRESSED_TEXTURE_FORMATS:{GLint n=0;glGetIntegerv(GL_NUM_COMPRESSED_TEXTURE_FORMATS,&n);return n;}
 case GL_SHADER_BINARY_FORMATS:{GLint n=0;glGetIntegerv(GL_NUM_SHADER_BINARY_FORMATS,&n);return n;}
 case 0x87ff:{GLint n=0;glGetIntegerv(0x87fe,&n);return n;} // PROGRAM_BINARY_FORMATS (ES3/OES)
 default:return 1;}
}
struct String {JNIEnv* env;jstring value;const char* bytes;String(JNIEnv* e,jstring s):env(e),value(s),bytes(nullptr){if(s)bytes=e->GetStringUTFChars(s,nullptr);else fail(e,"null GL string");}~String(){if(bytes)env->ReleaseStringUTFChars(value,bytes);}};
uint64_t pixelBytes(JNIEnv* e,int width,int height,int format,int type,bool pack){
 if(width<0||height<0||uint64_t(width)*height>64*1024*1024){fail(e,"GL image dimensions");return UINT64_MAX;}
 int channels=0;switch(format){case GL_ALPHA:case GL_LUMINANCE:case 0x1903:case GL_DEPTH_COMPONENT:channels=1;break;case GL_LUMINANCE_ALPHA:case 0x8227:channels=2;break;case GL_RGB:channels=3;break;case GL_RGBA:case 0x80e1:channels=4;break;default:fail(e,"unsupported GL pixel format");return UINT64_MAX;}
 int unit=0;switch(type){case GL_UNSIGNED_BYTE:unit=channels;break;case GL_UNSIGNED_SHORT_5_6_5:case GL_UNSIGNED_SHORT_4_4_4_4:case GL_UNSIGNED_SHORT_5_5_5_1:unit=2;break;case GL_FLOAT:unit=4*channels;break;default:fail(e,"unsupported GL pixel type");return UINT64_MAX;}
 GLint alignment=4;glGetIntegerv(pack?GL_PACK_ALIGNMENT:GL_UNPACK_ALIGNMENT,&alignment);
 // ES3 pixel-store extensions affect client buffer size as well.
 GLint row=0,rows=0,pixels=0,pbo=0;const auto* version=reinterpret_cast<const char*>(glGetString(GL_VERSION));
 if(version&&std::strstr(version,"OpenGL ES 3")){
  glGetIntegerv(pack?0x88ed:0x88ef,&pbo);if(pbo){fail(e,"pixel buffer objects require offset APIs");return UINT64_MAX;}
  glGetIntegerv(pack?0x0d02:0x0cf2,&row);glGetIntegerv(pack?0x0d03:0x0cf3,&rows);glGetIntegerv(pack?0x0d04:0x0cf4,&pixels);
 }
 if(!width||!height)return 0;
 uint64_t stride=(uint64_t(row?row:width)*unit+alignment-1)/alignment*alignment;
 const uint64_t limit=256*1024*1024,rowCount=uint64_t(rows)+height-1,tail=(uint64_t(pixels)+width)*unit;
 if(!stride||stride>limit||rowCount>limit/stride||tail>limit||rowCount*stride>limit-tail){fail(e,"GL image byte limit");return UINT64_MAX;}
 return rowCount*stride+tail;
}
}
extern "C" JNIEXPORT jstring JNICALL Java_android_opengl_GLES20_glGetString(JNIEnv* e,jclass,jint name){const auto* value=glGetString(name);return value?e->NewStringUTF(reinterpret_cast<const char*>(value)):nullptr;}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glShaderSource(JNIEnv* e,jclass,jint shader,jstring value){String text(e,value);if(text.bytes)glShaderSource(shader,1,&text.bytes,nullptr);}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glGetAttribLocation(JNIEnv* e,jclass,jint program,jstring value){String text(e,value);return text.bytes?glGetAttribLocation(program,text.bytes):-1;}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_glGetUniformLocation(JNIEnv* e,jclass,jint program,jstring value){String text(e,value);return text.bytes?glGetUniformLocation(program,text.bytes):-1;}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBindAttribLocation(JNIEnv* e,jclass,jint program,jint index,jstring value){String text(e,value);if(text.bytes)glBindAttribLocation(program,index,text.bytes);}
extern "C" JNIEXPORT jstring JNICALL Java_android_opengl_GLES20_nInfoLog(JNIEnv* e,jclass,jint object,jboolean program){GLint size=0;if(program)glGetProgramiv(object,GL_INFO_LOG_LENGTH,&size);else glGetShaderiv(object,GL_INFO_LOG_LENGTH,&size);if(size<1)return e->NewStringUTF("");if(size>1048576){fail(e,"GL info log limit");return nullptr;}std::vector<char> text(size);if(program)glGetProgramInfoLog(object,size,nullptr,text.data());else glGetShaderInfoLog(object,size,nullptr,text.data());text.back()=0;return e->NewStringUTF(text.data());}
extern "C" JNIEXPORT jint JNICALL Java_android_opengl_GLES20_nIntegerCount(JNIEnv* e,jclass,jint name){int n=integerCount(name);if(n<0||n>4096){fail(e,"GL query element limit");return 0;}return n;}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGetFloatv(JNIEnv* e,jclass,jint name,jfloatArray values,jint offset){int n=integerCount(name);if(n<0||n>4096){fail(e,"GL query element limit");return;}if(!range(e,values,offset,n))return;std::vector<GLfloat> data(std::max(16,n));glGetFloatv(name,data.data());e->SetFloatArrayRegion(values,offset,n,data.data());}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGetIntegerv(JNIEnv* e,jclass,jint name,jintArray values,jint offset){int n=integerCount(name);if(n<0||n>4096){fail(e,"GL query element limit");return;}if(!range(e,values,offset,n))return;std::vector<GLint> data(std::max(16,n));glGetIntegerv(name,data.data());e->SetIntArrayRegion(values,offset,n,data.data());}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGetShaderiv(JNIEnv* e,jclass,jint shader,jint name,jintArray values,jint offset){if(!range(e,values,offset,1))return;GLint value=0;glGetShaderiv(shader,name,&value);e->SetIntArrayRegion(values,offset,1,&value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGetProgramiv(JNIEnv* e,jclass,jint program,jint name,jintArray values,jint offset){int count=name==0x8267?3:1;if(!range(e,values,offset,count))return;GLint value[3]={};glGetProgramiv(program,name,value);e->SetIntArrayRegion(values,offset,count,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBufferData(JNIEnv* e,jclass,jint target,jint bytes,jobject data,jint usage){if(bytes<0){fail(e,"negative GL buffer size");return;}void* value=data?direct(e,data,bytes):nullptr;if(!e->ExceptionCheck())glBufferData(target,bytes,value,usage);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glBufferSubData(JNIEnv* e,jclass,jint target,jint offset,jint bytes,jobject data){if(offset<0||bytes<0){fail(e,"negative GL buffer range");return;}void* value=direct(e,data,bytes);if(value)glBufferSubData(target,offset,bytes,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glTexImage2D(JNIEnv* e,jclass,jint target,jint level,jint internal,jint width,jint height,jint border,jint format,jint type,jobject pixels){uint64_t bytes=pixelBytes(e,width,height,format,type,false);if(e->ExceptionCheck())return;void* value=pixels?direct(e,pixels,bytes):nullptr;if(!e->ExceptionCheck())glTexImage2D(target,level,internal,width,height,border,format,type,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glTexSubImage2D(JNIEnv* e,jclass,jint target,jint level,jint x,jint y,jint width,jint height,jint format,jint type,jobject pixels){uint64_t bytes=pixelBytes(e,width,height,format,type,false);if(e->ExceptionCheck())return;void* value=direct(e,pixels,bytes);if(value)glTexSubImage2D(target,level,x,y,width,height,format,type,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glReadPixels(JNIEnv* e,jclass,jint x,jint y,jint width,jint height,jint format,jint type,jobject pixels){uint64_t bytes=pixelBytes(e,width,height,format,type,true);if(e->ExceptionCheck())return;void* value=direct(e,pixels,bytes,true);if(value)glReadPixels(x,y,width,height,format,type,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_nVertexAttribPointer(JNIEnv* e,jclass,jint index,jint count,jint type,jboolean normalized,jint stride,jint offset){GLint bound=0;glGetIntegerv(GL_ARRAY_BUFFER_BINDING,&bound);if(!bound||count<1||count>4||stride<0||offset<0){fail(e,"vertex pointer requires a bound VBO and valid range");return;}glVertexAttribPointer(index,count,type,normalized,stride,reinterpret_cast<void*>(uintptr_t(offset)));}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_nDrawElementsOffset(JNIEnv* e,jclass,jint mode,jint count,jint type,jint offset){GLint bound=0;glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING,&bound);if(!bound||count<0||offset<0){fail(e,"index offset requires a bound element buffer");return;}glDrawElements(mode,count,type,reinterpret_cast<void*>(uintptr_t(offset)));}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_nDrawElementsBuffer(JNIEnv* e,jclass,jint mode,jint count,jint type,jobject data){GLint bound=0;glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING,&bound);int unit=type==GL_UNSIGNED_BYTE?1:type==GL_UNSIGNED_SHORT?2:type==GL_UNSIGNED_INT?4:0;if(bound||count<0||!unit){fail(e,"client index type/binding/count");return;}void* value=direct(e,data,uint64_t(count)*unit);if(value)glDrawElements(mode,count,type,value);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGenBuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glGenBuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,0);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteBuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glDeleteBuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGenTextures(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glGenTextures(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,0);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteTextures(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glDeleteTextures(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGenFramebuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glGenFramebuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,0);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteFramebuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glDeleteFramebuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glGenRenderbuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glGenRenderbuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,0);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glDeleteRenderbuffers(JNIEnv* e,jclass,jint count,jintArray values,jint offset){if(!range(e,values,offset,count))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glDeleteRenderbuffers(count,reinterpret_cast<GLuint*>(data+offset));e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform1fv(JNIEnv* e,jclass,jint location,jint count,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*1))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniform1fv(location,count,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform2fv(JNIEnv* e,jclass,jint location,jint count,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*2))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniform2fv(location,count,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform3fv(JNIEnv* e,jclass,jint location,jint count,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*3))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniform3fv(location,count,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform4fv(JNIEnv* e,jclass,jint location,jint count,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*4))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniform4fv(location,count,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform1iv(JNIEnv* e,jclass,jint location,jint count,jintArray values,jint offset){if(!range(e,values,offset,int64_t(count)*1))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glUniform1iv(location,count,data+offset);e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform2iv(JNIEnv* e,jclass,jint location,jint count,jintArray values,jint offset){if(!range(e,values,offset,int64_t(count)*2))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glUniform2iv(location,count,data+offset);e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform3iv(JNIEnv* e,jclass,jint location,jint count,jintArray values,jint offset){if(!range(e,values,offset,int64_t(count)*3))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glUniform3iv(location,count,data+offset);e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniform4iv(JNIEnv* e,jclass,jint location,jint count,jintArray values,jint offset){if(!range(e,values,offset,int64_t(count)*4))return;jint* data=e->GetIntArrayElements(values,nullptr);if(!data)return;glUniform4iv(location,count,data+offset);e->ReleaseIntArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniformMatrix2fv(JNIEnv* e,jclass,jint location,jint count,jboolean transpose,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*4))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniformMatrix2fv(location,count,transpose,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniformMatrix3fv(JNIEnv* e,jclass,jint location,jint count,jboolean transpose,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*9))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniformMatrix3fv(location,count,transpose,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT void JNICALL Java_android_opengl_GLES20_glUniformMatrix4fv(JNIEnv* e,jclass,jint location,jint count,jboolean transpose,jfloatArray values,jint offset){if(!range(e,values,offset,int64_t(count)*16))return;jfloat* data=e->GetFloatArrayElements(values,nullptr);if(!data)return;glUniformMatrix4fv(location,count,transpose,data+offset);e->ReleaseFloatArrayElements(values,data,JNI_ABORT);}
extern "C" JNIEXPORT jstring JNICALL Java_android_opengl_GLES20_nActive(JNIEnv* e,jclass,jint program,jint index,jboolean uniform,jintArray size,jintArray type){if(!range(e,size,0,1)||!range(e,type,0,1))return nullptr;GLint length=0;glGetProgramiv(program,uniform?GL_ACTIVE_UNIFORM_MAX_LENGTH:GL_ACTIVE_ATTRIBUTE_MAX_LENGTH,&length);if(length<1)length=1;if(length>1048576){fail(e,"active GL name length");return nullptr;}std::vector<char> name(length);GLint count=0;GLenum kind=0;if(uniform)glGetActiveUniform(program,index,length,nullptr,&count,&kind,name.data());else glGetActiveAttrib(program,index,length,nullptr,&count,&kind,name.data());name.back()=0;jint k=kind;e->SetIntArrayRegion(size,0,1,&count);e->SetIntArrayRegion(type,0,1,&k);return e->NewStringUTF(name.data());}
