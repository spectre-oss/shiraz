// SPDX-License-Identifier: GPL-3.0-only
#include <cerrno>
#include <cstddef>
#include <limits>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

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

#include <jni.h>
extern "C" JNIEXPORT jint JNICALL Java_android_os_MemoryFile_nativeCreate(JNIEnv* env,jclass,jstring name,jint length){
 const char* text=name?env->GetStringUTFChars(name,nullptr):nullptr;
 if(name&&!text)return -1;
 int fd=length==0?memfd_create(text?text:"shiraz-shared",MFD_CLOEXEC|MFD_ALLOW_SEALING):ASharedMemory_create(text,size_t(length));
 if(length==0&&fd>=0&&fcntl(fd,F_ADD_SEALS,F_SEAL_GROW|F_SEAL_SHRINK)<0){close(fd);fd=-1;}
 if(text)env->ReleaseStringUTFChars(name,text);
 if(fd<0){jclass type=env->FindClass("java/io/IOException");if(type){env->ThrowNew(type,"Cannot allocate shared MemoryFile");env->DeleteLocalRef(type);}}
 return fd;
}
