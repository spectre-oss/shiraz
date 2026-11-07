// SPDX-License-Identifier: GPL-3.0-only
#include <cerrno>
#include <cstddef>
#include <limits>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#if defined(__APPLE__)
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <libproc.h>
#include <stdlib.h>
#include <sys/proc_info.h>
// Darwin has no memfd or seals. A region is a POSIX shm object unlinked at
// once; Darwin accepts exactly one ftruncate per object, which gives the
// GROW|SHRINK seals by construction. fstat reports the size rounded up to the
// VM page (16 KiB on Apple Silicon) and zero st_dev/st_ino, so the requested
// size is encoded in the random object name. The kernel keeps that name after
// unlink and reports it through proc_pidfdinfo for every descriptor of the
// object, in this process and in any process that inherits or receives it.
namespace {
constexpr char digits[]="0123456789abcdefghijklmnopqrstuv", prefix[]="/shz";
constexpr size_t field=13, prefixLength=sizeof prefix-1, nameLength=prefixLength+2*field; // 30 <= PSHMNAMLEN
constexpr uint64_t pageSlack=16384; // Largest Darwin VM page; st_size is rounded up to it.
void encode(char* out,uint64_t value){for(size_t i=field;i-->0;value>>=5)out[i]=digits[value&31];}
bool decode(const char* in,uint64_t& value){
 value=0;
 for(size_t i=0;i<field;++i){
  const char* d=in[i]?std::strchr(digits,in[i]):nullptr;
  if(!d||(i==0&&d-digits>=16))return false; // The leading digit holds only bits 60-63.
  value=(value<<5)|uint64_t(d-digits);
 }
 return true;
}
int create_region(size_t size){
 for(int attempt=0;attempt<64;++attempt){
  char name[nameLength+1];uint64_t nonce=0;arc4random_buf(&nonce,sizeof nonce);
  std::memcpy(name,prefix,prefixLength);encode(name+prefixLength,nonce);encode(name+prefixLength+field,uint64_t(size));name[nameLength]=0;
  int fd=shm_open(name,O_RDWR|O_CREAT|O_EXCL,0600);
  if(fd<0){if(errno==EEXIST)continue;return -1;}
  shm_unlink(name);
  if(fcntl(fd,F_SETFD,FD_CLOEXEC)<0||ftruncate(fd,off_t(size))<0){int error=errno;close(fd);errno=error;return -1;}
  return fd;
 }
 errno=EEXIST;return -1;
}
// Like F_GET_SEALS: EBADF for a closed descriptor, EINVAL for one that is not shm.
bool region(int fd,pshm_fdinfo& info){
 if(fcntl(fd,F_GETFD)<0)return false;
 if(proc_pidfdinfo(getpid(),fd,PROC_PIDFDPSHMINFO,&info,int(sizeof info))!=int(sizeof info)){errno=EINVAL;return false;}
 info.pshminfo.pshm_name[sizeof info.pshminfo.pshm_name-1]=0;return true;
}
size_t region_size(const pshm_fdinfo& info){
 const char* name=info.pshminfo.pshm_name;const off_t actual=info.pshminfo.pshm_stat.vst_size;uint64_t nonce=0,requested=0;
 if(actual<0)return 0;
 if(std::strlen(name)==nameLength&&!std::strncmp(name,prefix,prefixLength)&&decode(name+prefixLength,nonce)&&decode(name+prefixLength+field,requested)&&
    requested<=uint64_t(actual)&&uint64_t(actual)-requested<pageSlack)return size_t(requested);
 return size_t(actual); // Foreign shm object: only the page-rounded size is known.
}
// One SHIRAZ_UNSUPPORTED line per process for write removal (see ASharedMemory_setProt).
std::mutex protMutex;bool protWarned=false;
}
#endif

#if defined(__APPLE__)
extern "C" int ASharedMemory_create(const char* name, size_t size) {
    (void)name; // Darwin shm names are capped at 31 bytes and carry the size instead.
    if (!size) { errno = EINVAL; return -EINVAL; }
    if (size > size_t(std::numeric_limits<off_t>::max())) { errno = EINVAL; return -1; }
    return create_region(size);
}

extern "C" size_t ASharedMemory_getSize(int fd) {
    pshm_fdinfo info {};
    return region(fd, info) ? region_size(info) : 0;
}

// Darwin cannot remove write access from an existing shm object, and an
// unlinked object cannot be reopened read-only, so new writable mmap()s and
// other holders of the descriptor could not be refused. Write removal therefore
// fails explicitly with ENOTSUP instead of reporting a guarantee it lacks.
extern "C" int ASharedMemory_setProt(int fd, int prot) {
    if (prot & ~(PROT_READ | PROT_WRITE | PROT_EXEC)) { errno = EINVAL; return -1; }
    pshm_fdinfo info {};
    if (!region(fd, info)) return -1;
    if (prot & PROT_WRITE) return 0;
    std::lock_guard<std::mutex> lock(protMutex);
    if (!protWarned) {
        protWarned = true;
        std::fputs("SHIRAZ_UNSUPPORTED ASharedMemory_setProt write removal: Darwin shm cannot refuse writable mappings\n", stderr);
    }
    errno = ENOTSUP;
    return -1;
}
#else
// Kernel seals carry the restrictions across dup(), SCM_RIGHTS and processes.
// Match Android libcutils' memfd backend, including preservation of existing
// writable mappings when future writes are sealed.
extern "C" int ASharedMemory_create(const char* name, size_t size) {
    if (!size) { errno = EINVAL; return -EINVAL; }
    if (size > size_t(std::numeric_limits<off_t>::max())) { errno = EINVAL; return -1; }
    int fd = memfd_create(name ? name : "shiraz-shared", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0) return -1;
    if (ftruncate(fd, off_t(size)) < 0 ||
        fcntl(fd, F_ADD_SEALS, F_SEAL_GROW | F_SEAL_SHRINK) < 0) {
        int error = errno;
        close(fd);
        errno = error;
        return -1;
    }
    return fd;
}

extern "C" size_t ASharedMemory_getSize(int fd) {
    struct stat info {};
    if (fcntl(fd, F_GET_SEALS) < 0 || fstat(fd, &info) < 0 || info.st_size < 0) return 0;
    return size_t(info.st_size);
}

extern "C" int ASharedMemory_setProt(int fd, int prot) {
    if (prot & ~(PROT_READ | PROT_WRITE | PROT_EXEC)) { errno = EINVAL; return -1; }
    int seals = fcntl(fd, F_GET_SEALS);
    if (seals < 0) return -1;
    if (prot & PROT_WRITE) {
        if (seals & (F_SEAL_WRITE | F_SEAL_FUTURE_WRITE)) { errno = EINVAL; return -1; }
        return 0;
    }
    // Like Android's memfd backend, only write removal is kernel-enforced;
    // read/execute masks are not represented by memfd seals.
    return fcntl(fd, F_ADD_SEALS, F_SEAL_FUTURE_WRITE);
}

#endif
#include <jni.h>
#if defined(__APPLE__)
extern "C" JNIEXPORT jint JNICALL Java_android_os_MemoryFile_nativeCreate(JNIEnv* env,jclass,jstring name,jint length){
 const char* text=name?env->GetStringUTFChars(name,nullptr):nullptr;
 if(name&&!text)return -1;
 // A single ftruncate(0) fixes an empty object's size, matching GROW|SHRINK seals.
 int fd=length==0?create_region(0):ASharedMemory_create(text,size_t(length));
 if(text)env->ReleaseStringUTFChars(name,text);
 if(fd<0){jclass type=env->FindClass("java/io/IOException");if(type){env->ThrowNew(type,"Cannot allocate shared MemoryFile");env->DeleteLocalRef(type);}}
 return fd;
}
#else
extern "C" JNIEXPORT jint JNICALL Java_android_os_MemoryFile_nativeCreate(JNIEnv* env,jclass,jstring name,jint length){
 const char* text=name?env->GetStringUTFChars(name,nullptr):nullptr;
 if(name&&!text)return -1;
 int fd=length==0?memfd_create(text?text:"shiraz-shared",MFD_CLOEXEC|MFD_ALLOW_SEALING):ASharedMemory_create(text,size_t(length));
 if(length==0&&fd>=0&&fcntl(fd,F_ADD_SEALS,F_SEAL_GROW|F_SEAL_SHRINK)<0){close(fd);fd=-1;}
 if(text)env->ReleaseStringUTFChars(name,text);
 if(fd<0){jclass type=env->FindClass("java/io/IOException");if(type){env->ThrowNew(type,"Cannot allocate shared MemoryFile");env->DeleteLocalRef(type);}}
 return fd;
}
#endif
