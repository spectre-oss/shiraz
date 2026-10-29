// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <cerrno>
#include <sys/inotify.h>
#include <unistd.h>

namespace {
void failure(JNIEnv* env,const char* message){
 jclass type=env->FindClass("java/lang/IllegalStateException");
 if(type){env->ThrowNew(type,message);env->DeleteLocalRef(type);}
}
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_FileObserver_nativeOpen(JNIEnv* env,jclass,jobjectArray paths,jint mask){
 if(!paths||!(mask&IN_ALL_EVENTS))return -1;
 int fd=inotify_init1(IN_NONBLOCK|IN_CLOEXEC);
 if(fd<0){failure(env,"Cannot initialize filesystem observer");return -1;}
 bool installed=false;
 for(jsize i=0;i<env->GetArrayLength(paths);i++){
  auto path=static_cast<jstring>(env->GetObjectArrayElement(paths,i));
  if(!path){close(fd);return -1;}
  const char* text=env->GetStringUTFChars(path,nullptr);
  if(!text){env->DeleteLocalRef(path);close(fd);return -1;}
  if(inotify_add_watch(fd,text,static_cast<unsigned>(mask)&IN_ALL_EVENTS)>=0)installed=true;
  env->ReleaseStringUTFChars(path,text);env->DeleteLocalRef(path);
 }
 if(!installed){close(fd);return -1;}
 return fd;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_android_os_FileObserver_nativeRead(JNIEnv* env,jclass,jint fd){
 char buffer[65536];ssize_t count;
 do{count=read(fd,buffer,sizeof(buffer));}while(count<0&&errno==EINTR);
 if(count<0&&(errno==EAGAIN||errno==EWOULDBLOCK))return nullptr;
 if(count<=0){failure(env,"Cannot read filesystem observer");return nullptr;}
 jbyteArray result=env->NewByteArray(static_cast<jsize>(count));
 if(result)env->SetByteArrayRegion(result,0,static_cast<jsize>(count),reinterpret_cast<const jbyte*>(buffer));
 return result;
}
extern "C" JNIEXPORT void JNICALL Java_android_os_FileObserver_nativeClose(JNIEnv*,jclass,jint fd){if(fd>=0)close(fd);}
