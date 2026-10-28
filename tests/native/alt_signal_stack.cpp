// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <signal.h>
#include <sys/mman.h>
#include <atomic>
#include <thread>
namespace {
constexpr size_t size=64*1024;
std::atomic<void*> replacement{nullptr};
void* allocate(){void* p=mmap(nullptr,size,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);return p==MAP_FAILED?nullptr:p;}
}
extern "C" JNIEXPORT jboolean JNICALL Java_AltSignalStackSmoke_replace(JNIEnv*,jclass){
 void* memory=allocate();if(!memory)return false;
 stack_t stack{};stack.ss_sp=memory;stack.ss_size=size;
 if(sigaltstack(&stack,nullptr)){munmap(memory,size);return false;}
 replacement.store(memory);return true;
}
extern "C" JNIEXPORT void JNICALL Java_AltSignalStackSmoke_cleanup(JNIEnv*,jclass){void* memory=replacement.exchange(nullptr);if(memory)munmap(memory,size);}
extern "C" JNIEXPORT jboolean JNICALL Java_AltSignalStackSmoke_attachWithStack(JNIEnv* env,jclass){
 JavaVM* vm=nullptr;if(env->GetJavaVM(&vm)!=JNI_OK)return false;
 bool success=false;
 std::thread worker([&]{
  void* memory=allocate();if(!memory)return;
  stack_t stack{};stack.ss_sp=memory;stack.ss_size=size;
  if(sigaltstack(&stack,nullptr)){munmap(memory,size);return;}
  JNIEnv* threadEnv=nullptr;
  if(vm->AttachCurrentThread(reinterpret_cast<void**>(&threadEnv),nullptr)==JNI_OK){
   stack_t current{};bool preserved=sigaltstack(nullptr,&current)==0&&current.ss_sp==memory;
   if(vm->DetachCurrentThread()==JNI_OK&&sigaltstack(nullptr,&current)==0)success=preserved&&current.ss_sp==memory&&!(current.ss_flags&SS_DISABLE);
  }
  stack={};stack.ss_flags=SS_DISABLE;sigaltstack(&stack,nullptr);munmap(memory,size);
 });worker.join();return success;
}
