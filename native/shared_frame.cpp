// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/shared_frame.hpp"
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <pthread.h>
#include <stdexcept>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#if defined(__APPLE__)
#include <atomic>
#include <cstdio>
#include <os/os_sync_wait_on_address.h>
#include <signal.h>
#endif
namespace shiraz {
namespace {
constexpr uint64_t magic=0x53485a4652414d31ULL, maxPixels=16*1024*1024;
#if defined(__APPLE__)
// Darwin has no robust process-shared mutex. The lock word holds the owner's
// pid (0 = free); waiters use the shared address wait and take over the lock
// when the owning process no longer exists, matching EOWNERDEAD recovery.
struct Header {uint64_t signature;std::atomic<uint32_t> owner;uint32_t reserved;uint64_t revision;int32_t width,height;};
static_assert(std::atomic<uint32_t>::is_always_lock_free,"Shared frame lock word");
// 16 KiB keeps the mapping a whole number of Apple Silicon pages.
constexpr size_t offset=16384, bytes=offset+maxPixels*sizeof(uint32_t);
#else
struct Header {uint64_t signature;pthread_mutex_t mutex;uint64_t revision;int32_t width,height;};
constexpr size_t offset=4096, bytes=offset+maxPixels*sizeof(uint32_t);
#endif
static_assert(sizeof(Header)<=offset,"Shared frame header");
bool valid(int w,int h){return w>0&&h>0&&w<=8192&&h<=8192&&uint64_t(w)*h<=maxPixels;}
#if defined(__APPLE__)
bool process_gone(uint32_t pid){return pid&&kill(pid_t(pid),0)!=0&&errno==ESRCH;}
struct Lock {
 Header* h;
 explicit Lock(Header* value):h(value){
  const uint32_t self=uint32_t(getpid());
  for(;;){
   uint32_t current=0;
   if(h->owner.compare_exchange_weak(current,self,std::memory_order_acquire,std::memory_order_relaxed))return;
   if(current&&current!=self&&process_gone(current)&&h->owner.compare_exchange_strong(current,self,std::memory_order_acquire,std::memory_order_relaxed)){
    h->width=h->height=0;h->revision=0;return;
   }
   if(current&&os_sync_wait_on_address_with_timeout(&h->owner,current,sizeof(uint32_t),OS_SYNC_WAIT_ON_ADDRESS_SHARED,OS_CLOCK_MACH_ABSOLUTE_TIME,50000000)<0&&errno!=ETIMEDOUT&&errno!=EINTR&&errno!=EFAULT)
    throw std::runtime_error("Shared frame lock failed");
  }
 }
 ~Lock(){h->owner.store(0,std::memory_order_release);os_sync_wake_by_address_any(&h->owner,sizeof(uint32_t),OS_SYNC_WAKE_BY_ADDRESS_SHARED);}
};
int create_descriptor(){
 static std::atomic<unsigned> sequence{0};
 for(int attempt=0;attempt<64;++attempt){
  char name[32];
  std::snprintf(name,sizeof name,"/shz-%d-%u-%x",int(getpid()),sequence.fetch_add(1),unsigned(arc4random()));
  int fd=shm_open(name,O_RDWR|O_CREAT|O_EXCL,0600);
  if(fd<0){if(errno==EEXIST)continue;return -1;}
  shm_unlink(name);
  if(fcntl(fd,F_SETFD,FD_CLOEXEC)){close(fd);return -1;}
  return fd;
 }
 errno=EEXIST;return -1;
}
#else
struct Lock {
 Header* h;
 explicit Lock(Header* value):h(value){
  int result=pthread_mutex_lock(&h->mutex);
  if(result==EOWNERDEAD){h->width=h->height=0;h->revision=0;if(pthread_mutex_consistent(&h->mutex)){pthread_mutex_unlock(&h->mutex);throw std::runtime_error("Shared frame recovery failed");}}
  else if(result)throw std::runtime_error("Shared frame lock failed");
 }
 ~Lock(){pthread_mutex_unlock(&h->mutex);}
};
#endif
}
std::unique_ptr<SharedFrame> SharedFrame::create(){
#if defined(__APPLE__)
 // POSIX shm on Darwin: unlinked at once, sized once (no seals exist).
 int fd=create_descriptor();
 if(fd<0)throw std::runtime_error("Shared frame shm failed");
 void* mapping=MAP_FAILED;
 try{
  if(ftruncate(fd,bytes))throw std::runtime_error("Shared frame size failed");
  mapping=mmap(nullptr,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);
  if(mapping==MAP_FAILED)throw std::runtime_error("Shared frame mmap failed");
  auto* h=static_cast<Header*>(mapping);
  new(&h->owner) std::atomic<uint32_t>(0);
  h->signature=magic;
  return std::unique_ptr<SharedFrame>(new SharedFrame(fd,mapping));
 }catch(...){if(mapping!=MAP_FAILED)munmap(mapping,bytes);close(fd);throw;}
#else
 int fd=memfd_create("shiraz-window",MFD_CLOEXEC|MFD_ALLOW_SEALING);
 if(fd<0)throw std::runtime_error("Shared frame memfd failed");
 void* mapping=MAP_FAILED;
 try{
  if(ftruncate(fd,bytes)||fcntl(fd,F_ADD_SEALS,F_SEAL_SHRINK|F_SEAL_GROW|F_SEAL_SEAL))throw std::runtime_error("Shared frame size/seals failed");
  mapping=mmap(nullptr,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);
  if(mapping==MAP_FAILED)throw std::runtime_error("Shared frame mmap failed");
  auto* h=static_cast<Header*>(mapping);pthread_mutexattr_t attr;
  if(pthread_mutexattr_init(&attr))throw std::runtime_error("Shared frame mutex attributes failed");
  int result=pthread_mutexattr_setpshared(&attr,PTHREAD_PROCESS_SHARED);
  if(!result)result=pthread_mutexattr_setrobust(&attr,PTHREAD_MUTEX_ROBUST);
  if(!result)result=pthread_mutex_init(&h->mutex,&attr);
  pthread_mutexattr_destroy(&attr);
  if(result)throw std::runtime_error("Shared frame process mutex failed");
  h->signature=magic;
  return std::unique_ptr<SharedFrame>(new SharedFrame(fd,mapping));
 }catch(...){if(mapping!=MAP_FAILED)munmap(mapping,bytes);close(fd);throw;}
#endif
}
std::unique_ptr<SharedFrame> SharedFrame::import(int descriptor){
 int fd=fcntl(descriptor,F_DUPFD_CLOEXEC,0);if(fd<0)throw std::invalid_argument("Shared frame descriptor");
 void* mapping=MAP_FAILED;
 try{
#if defined(__APPLE__)
  // Darwin shm can only be sized once, so a matching size cannot change later.
  struct stat st{};
  if(fstat(fd,&st)||st.st_size!=off_t(bytes))throw std::invalid_argument("Shared frame descriptor layout");
#else
  struct stat st{};int seals=fcntl(fd,F_GET_SEALS);
  if(fstat(fd,&st)||st.st_size!=off_t(bytes)||seals<0||(seals&(F_SEAL_SHRINK|F_SEAL_GROW))!=(F_SEAL_SHRINK|F_SEAL_GROW))throw std::invalid_argument("Shared frame descriptor layout");
#endif
  mapping=mmap(nullptr,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);
  if(mapping==MAP_FAILED)throw std::runtime_error("Shared frame import mmap failed");
  if(static_cast<Header*>(mapping)->signature!=magic)throw std::invalid_argument("Shared frame signature");
  return std::unique_ptr<SharedFrame>(new SharedFrame(fd,mapping));
 }catch(...){if(mapping!=MAP_FAILED)munmap(mapping,bytes);close(fd);throw;}
}
SharedFrame::~SharedFrame(){munmap(mapping_,bytes);close(descriptor_);}
int SharedFrame::duplicate()const{int fd=fcntl(descriptor_,F_DUPFD_CLOEXEC,0);if(fd<0)throw std::runtime_error("Shared frame descriptor duplication");return fd;}
uint64_t SharedFrame::post(int width,int height,const uint32_t* pixels,uint64_t count,bool allowResize){
 if(!valid(width,height)||count!=uint64_t(width)*height||!pixels)throw std::invalid_argument("Shared frame pixels/geometry");
 auto* h=static_cast<Header*>(mapping_);Lock lock(h);
 if(!allowResize&&h->revision&&(h->width!=width||h->height!=height))return 0;
 std::memcpy(static_cast<char*>(mapping_)+offset,pixels,count*sizeof(uint32_t));
 h->width=width;h->height=height;if(++h->revision==0)++h->revision;return h->revision;
}
bool SharedFrame::read(int& width,int& height,uint64_t& revision,std::vector<uint32_t>& pixels,bool onlyIfChanged){
 auto* h=static_cast<Header*>(mapping_);Lock lock(h);
 if(!h->revision||(onlyIfChanged&&revision==h->revision))return false;
 if(!valid(h->width,h->height))throw std::runtime_error("Shared frame corrupt geometry");
 pixels.resize(size_t(h->width)*h->height);
 std::memcpy(pixels.data(),static_cast<char*>(mapping_)+offset,pixels.size()*sizeof(uint32_t));
 width=h->width;height=h->height;revision=h->revision;return true;
}
}
