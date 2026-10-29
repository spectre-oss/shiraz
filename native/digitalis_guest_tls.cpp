// SPDX-License-Identifier: GPL-3.0-only
// Experimental AOSP16 guest TLS storage for the pinned Digitalis guest bundle.
#include <cstdint>
#include <cstdlib>
#include <cstdio>
#include "guest_stack_environment.h"
#include <cstring>
#include <unistd.h>
#include <sys/syscall.h>
#include <pthread.h>
#include <cerrno>
namespace berberis {
struct GuestBionicThreadStorage;
static pthread_mutex_t thread_registry_lock=PTHREAD_MUTEX_INITIALIZER;
static GuestBionicThreadStorage* thread_registry=nullptr;
static thread_local GuestBionicThreadStorage* current_storage=nullptr;
static pthread_once_t fork_handlers=PTHREAD_ONCE_INIT;
static void PrepareThreadRegistryFork(){pthread_mutex_lock(&thread_registry_lock);}
static void ParentThreadRegistryFork(){pthread_mutex_unlock(&thread_registry_lock);}
static void ChildThreadRegistryFork();
static void InstallThreadRegistryFork(){pthread_atfork(PrepareThreadRegistryFork,ParentThreadRegistryFork,ChildThreadRegistryFork);}
struct GuestBionicThreadStorage {
 alignas(16) unsigned char pthread_record[4096]{};
 alignas(16) unsigned char tls_record[32768]{};
 pthread_t host_thread{};
 GuestBionicThreadStorage* next=nullptr;
 GuestBionicThreadStorage() {
  const int tid=static_cast<int>(syscall(SYS_gettid));
  const int pid=getpid();
  std::memcpy(pthread_record+16,&tid,sizeof(tid));
  std::memcpy(pthread_record+20,&pid,sizeof(pid));
  void* ptr=tls_record;
  // bionic_tls member offset verified from the pinned linker's init routine.
  std::memcpy(pthread_record+760,&ptr,sizeof(ptr));
  host_thread=pthread_self();
  pthread_once(&fork_handlers,InstallThreadRegistryFork);
  pthread_mutex_lock(&thread_registry_lock);
  next=thread_registry; thread_registry=this; current_storage=this;
  pthread_mutex_unlock(&thread_registry_lock);
 }
 ~GuestBionicThreadStorage() {
  pthread_mutex_lock(&thread_registry_lock);
  for(auto** item=&thread_registry;*item;item=&(*item)->next) {
   if(*item==this){*item=next;break;}
  }
  current_storage=nullptr;
  pthread_mutex_unlock(&thread_registry_lock);
 }
};
static void ChildThreadRegistryFork(){
 thread_registry=current_storage;
 if(current_storage){current_storage->next=nullptr;current_storage->host_thread=pthread_self();}
 pthread_mutex_unlock(&thread_registry_lock);
}
thread_local GuestBionicThreadStorage storage;
// Handles returned by pthread_create and guest pthread_self have different
// representations. Resolve only live registrations, without dereferencing input.
template<class F> static int WithGuestThread(pthread_t handle,F operation){
 pthread_mutex_lock(&thread_registry_lock);
 int result=ESRCH;
 for(auto* item=thread_registry;item;item=item->next){
  if(handle==reinterpret_cast<pthread_t>(item->pthread_record)||handle==item->host_thread){
   result=operation(item->host_thread);break;
  }
 }
 pthread_mutex_unlock(&thread_registry_lock);
 return result;
}
int ShirazGuestSetThreadName(pthread_t handle,const char* name){return WithGuestThread(handle,[&](pthread_t host){return name?pthread_setname_np(host,name):EINVAL;});}
int ShirazGuestGetThreadName(pthread_t handle,char* output,size_t size){return WithGuestThread(handle,[&](pthread_t host){return output?pthread_getname_np(host,output,size):EINVAL;});}
// Bionic's sched_param contains a 32-bit priority. Copy it into the host type;
// never pass guest pthread_self records to glibc as pthread_t handles.
int ShirazGuestSetSchedParam(pthread_t handle,int policy,const int32_t* priority){
 if(!priority)return EINVAL;
 sched_param parameter{};parameter.sched_priority=*priority;
 return WithGuestThread(handle,[&](pthread_t host){return pthread_setschedparam(host,policy,&parameter);});
}
int ShirazGuestGetSchedParam(pthread_t handle,int* policy,int32_t* priority){
 if(!policy||!priority)return EINVAL;
 return WithGuestThread(handle,[&](pthread_t host){sched_param parameter{};int value=0;int result=pthread_getschedparam(host,&value,&parameter);if(!result){*policy=value;*priority=parameter.sched_priority;}return result;});
}
int ShirazGuestSetSchedPriority(pthread_t handle,int priority){return WithGuestThread(handle,[&](pthread_t host){return pthread_setschedprio(host,priority);});}

void ShirazRefreshGuestThreadIdentity() {
 const int tid=static_cast<int>(syscall(SYS_gettid));
 const int pid=getpid();
 std::memcpy(storage.pthread_record+16,&tid,sizeof(tid));
 std::memcpy(storage.pthread_record+20,&pid,sizeof(pid));
}
void* ShirazGuestPthread(){return storage.pthread_record;}
void* ShirazGuestBionicTls(){return storage.tls_record;}
}

namespace berberis {
unsigned long ShirazAttachedGuestStack(unsigned long top, size_t stack_size) {
  const char* enabled = std::getenv("SHIRAZ_GUEST_STACK_ENVIRONMENT");
  if (!enabled || std::strcmp(enabled, "1")) return top;
  size_t capacity = stack_size / 4;
  if (capacity > 65536) capacity = 65536;
  capacity &= ~size_t(15);
  auto result = shiraz_stack_environment(reinterpret_cast<void*>(top - capacity),
      capacity, environ);
  if (!result) {
    std::fprintf(stderr, "Shiraz: environment exceeds guest stack reservation\n");
    std::abort();
  }
  return result;
}
}
