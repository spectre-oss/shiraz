// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ipc_channel.hpp"
#include <jni.h>
#include <new>
#include <sys/socket.h>
#include <unistd.h>
namespace {
void error(JNIEnv* env,const char* type,const char* message){
    if(env->ExceptionCheck())return;
    jclass cls=env->FindClass(type);if(cls){env->ThrowNew(cls,message);env->DeleteLocalRef(cls);}
}
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeIpc_pair(JNIEnv* env,jclass){
    int sockets[2];if(shiraz::ipc_pair(sockets)){error(env,"java/io/IOException","Cannot create IPC socketpair");return nullptr;}
    jintArray result=env->NewIntArray(2);jint values[]={sockets[0],sockets[1]};
    if(result)env->SetIntArrayRegion(result,0,2,values);
    if(!result||env->ExceptionCheck()){close(sockets[0]);close(sockets[1]);return nullptr;}return result;
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeIpc_send(JNIEnv* env,jclass,jint socket,jbyteArray bytes,jintArray descriptors) try {
    if(!bytes||!descriptors){error(env,"java/lang/NullPointerException","IPC payload and descriptors");return;}
    jsize size=env->GetArrayLength(bytes),count=env->GetArrayLength(descriptors);
    if(size>16*1024*1024||count>128){error(env,"java/lang/IllegalArgumentException","IPC packet limit");return;}
    std::vector<uint8_t> payload(size);std::vector<jint> fds(count);
    if(size)env->GetByteArrayRegion(bytes,0,size,reinterpret_cast<jbyte*>(payload.data()));
    if(count)env->GetIntArrayRegion(descriptors,0,count,fds.data());
    if(env->ExceptionCheck())return;
    if(shiraz::ipc_send(socket,payload.data(),payload.size(),fds.data(),fds.size()))error(env,"java/io/IOException","IPC send failed");
} catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","IPC packet allocation");}
extern "C" JNIEXPORT jobject JNICALL Java_shiraz_runtime_NativeIpc_receive(JNIEnv* env,jclass,jint socket){
    shiraz::IpcPacket packet;
    if(shiraz::ipc_receive(socket,packet)){error(env,"java/io/IOException","IPC channel closed or invalid packet");return nullptr;}
    jbyteArray bytes=env->NewByteArray(packet.bytes.size());if(!bytes)return nullptr;
    if(!packet.bytes.empty())env->SetByteArrayRegion(bytes,0,packet.bytes.size(),reinterpret_cast<const jbyte*>(packet.bytes.data()));
    jintArray fds=env->ExceptionCheck()?nullptr:env->NewIntArray(packet.descriptors.size());
    if(fds&&!packet.descriptors.empty())env->SetIntArrayRegion(fds,0,packet.descriptors.size(),packet.descriptors.data());
    jclass type=env->ExceptionCheck()?nullptr:env->FindClass("shiraz/runtime/NativeIpc$Packet");
    jmethodID constructor=type?env->GetMethodID(type,"<init>","([B[III)V"):nullptr;
    jobject result=constructor?env->NewObject(type,constructor,bytes,fds,packet.pid,packet.uid):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(fds)env->DeleteLocalRef(fds);
    env->DeleteLocalRef(bytes);
    if(result&&!env->ExceptionCheck())packet.descriptors.clear(); // ownership moves to Java Packet
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeIpc_shutdown(JNIEnv*,jclass,jint socket){shutdown(socket,SHUT_RDWR);}
