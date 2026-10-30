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
struct GuestThreadRecord {
 void* pthread_record;
 pthread_t host_thread;
 GuestThreadRecord* next;
 bool managed=false, detached=false, exited=false, creation_pending=false;
};
static void RemoveThreadLocked(GuestThreadRecord*);
static pthread_mutex_t thread_registry_lock=PTHREAD_MUTEX_INITIALIZER;
static GuestThreadRecord* thread_registry=nullptr;
static GuestThreadRecord* fork_orphans=nullptr;
static thread_local GuestThreadRecord* current_storage=nullptr;
static pthread_once_t fork_handlers=PTHREAD_ONCE_INIT;
static void PrepareThreadRegistryFork(){pthread_mutex_lock(&thread_registry_lock);}
static void ParentThreadRegistryFork(){pthread_mutex_unlock(&thread_registry_lock);}
static void ChildThreadRegistryFork();
static void InstallThreadRegistryFork(){pthread_atfork(PrepareThreadRegistryFork,ParentThreadRegistryFork,ChildThreadRegistryFork);}
struct GuestBionicThreadStorage {
 alignas(16) unsigned char pthread_record[4096]{};
 alignas(16) unsigned char tls_record[32768]{};
 GuestThreadRecord* record=nullptr;
 GuestBionicThreadStorage() {
  const int tid=static_cast<int>(syscall(SYS_gettid));
  const int pid=getpid();
  std::memcpy(pthread_record+16,&tid,sizeof(tid));
  std::memcpy(pthread_record+20,&pid,sizeof(pid));
  void* ptr=tls_record;
  // bionic_tls member offset verified from the pinned linker's init routine.
  std::memcpy(pthread_record+760,&ptr,sizeof(ptr));
  record=new GuestThreadRecord{pthread_record,pthread_self(),nullptr};
  pthread_once(&fork_handlers,InstallThreadRegistryFork);
  pthread_mutex_lock(&thread_registry_lock);
  record->next=thread_registry; thread_registry=record; current_storage=record;
  pthread_mutex_unlock(&thread_registry_lock);
 }
 ~GuestBionicThreadStorage() {
  pthread_mutex_lock(&thread_registry_lock);
  record->exited=true;
  current_storage=nullptr;
  if(!record->managed || (record->detached && !record->creation_pending))RemoveThreadLocked(record);
  pthread_mutex_unlock(&thread_registry_lock);
 }
};
// Only the small identity mapping is retained until join. Bionic's TLS records
// remain in host TLS so pthread-key destructors can still use them during exit.
static void RemoveThreadLocked(GuestThreadRecord* record) {
 for(auto** item=&thread_registry;*item;item=&(*item)->next) {
  if(*item==record){*item=record->next;delete record;return;}
 }
}
static thread_local GuestBionicThreadStorage storage;
static void ChildThreadRegistryFork(){
 // Defer freeing other threads' inherited records until outside the atfork hook.
 auto* item=thread_registry;
 while(item){
  auto* next=item->next;
  if(item!=current_storage){item->next=fork_orphans;fork_orphans=item;}
  item=next;
 }
 thread_registry=current_storage;
 if(current_storage){current_storage->next=nullptr;current_storage->host_thread=pthread_self();}
 pthread_mutex_unlock(&thread_registry_lock);
}
// Called before the creation semaphore is posted, so even a thread that exits
// before pthread_create returns retains its handle until join or detach.
void* ShirazRetainCreatedGuestThread(){
 (void)storage;
 pthread_mutex_lock(&thread_registry_lock);
 storage.record->managed=true;
 storage.record->creation_pending=true;
 pthread_mutex_unlock(&thread_registry_lock);
 return storage.record;
}
pthread_t ShirazCompleteGuestThreadCreation(void* cookie,bool detached){
 pthread_mutex_lock(&thread_registry_lock);
 pthread_t result=0;
 for(auto* item=thread_registry;item;item=item->next){
  if(item==cookie && item->managed && item->creation_pending){
   result=reinterpret_cast<pthread_t>(item->pthread_record);
   item->creation_pending=false;
   item->detached=item->detached || detached;
   if(item->exited && item->detached)RemoveThreadLocked(item);
   break;
  }
 }
 pthread_mutex_unlock(&thread_registry_lock);
 return result;
}
int ShirazGuestJoinThread(pthread_t handle,void** value){
 pthread_mutex_lock(&thread_registry_lock);
 GuestThreadRecord* found=nullptr;
 for(auto* item=thread_registry;item;item=item->next)
  if(handle==reinterpret_cast<pthread_t>(item->pthread_record)){found=item;break;}
 if(!found || found->detached || !found->managed){
  int result=found?EINVAL:ESRCH;
  pthread_mutex_unlock(&thread_registry_lock);return result;
 }
 pthread_t host=found->host_thread;
 pthread_mutex_unlock(&thread_registry_lock);
 // Never hold the registry mutex while waiting for the thread's TLS destructors.
 int result=pthread_join(host,value);
 if(!result){
  pthread_mutex_lock(&thread_registry_lock);
  RemoveThreadLocked(found);
  pthread_mutex_unlock(&thread_registry_lock);
 }
 return result;
}
int ShirazGuestDetachThread(pthread_t handle){
 pthread_mutex_lock(&thread_registry_lock);
 int result=ESRCH;
 for(auto* item=thread_registry;item;item=item->next){
  if(handle!=reinterpret_cast<pthread_t>(item->pthread_record))continue;
  result=item->detached?EINVAL:pthread_detach(item->host_thread);
  if(!result){item->detached=true;if(item->exited && !item->creation_pending)RemoveThreadLocked(item);}
  break;
 }
 pthread_mutex_unlock(&thread_registry_lock);
 return result;
}
// Resolve only live registrations without dereferencing arbitrary guest handles.
template<class F> static int WithGuestThread(pthread_t handle,F operation){
 pthread_mutex_lock(&thread_registry_lock);
 int result=ESRCH;
 for(auto* item=thread_registry;item;item=item->next){
  if(!item->exited && (handle==reinterpret_cast<pthread_t>(item->pthread_record) || handle==item->host_thread)){
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
 while(fork_orphans){auto* item=fork_orphans;fork_orphans=item->next;delete item;}
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
