// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <signal.h>
#include <sys/resource.h>
#include <cerrno>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <sys/statvfs.h>
#include <string>
#include <new>
#include <fstream>
#include <sstream>
#include <limits>
#include <malloc.h>

#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <mutex>
namespace {
char* processArguments=nullptr;size_t processArgumentBytes=0;std::mutex processNameMutex;
// glibc supplies the original argv to ELF constructors, including dlopen constructors.
__attribute__((constructor)) void captureProcessArguments(int argc,char** argv,char**) {
    if(argc<=0||argc>4096||!argv||!argv[0])return;
    char* end=argv[0];
    for(int i=0;i<argc;i++){
        if(argv[i]!=end)return;
        size_t size=strnlen(argv[i],1024*1024);
        if(size==1024*1024)return;
        end=argv[i]+size+1;
    }
    size_t size=static_cast<size_t>(end-argv[0]);
    if(size>1024*1024)return;
    // Direct ELF-loader launches leave loader options before libc's argv[0].
    // Include that prefix only when the kernel's end agrees with our validated argv.
    char* beginning=argv[0];char stat[8192];FILE* file=fopen("/proc/self/stat","r");
    if(file){if(fgets(stat,sizeof(stat),file)){char* tail=strrchr(stat,')');
      if(tail){char* save=nullptr;char* token=strtok_r(tail+2," ",&save);uintptr_t start=0,finish=0;
        for(int field=3;token&&field<=49;field++,token=strtok_r(nullptr," ",&save)){
          if(field==48)start=strtoull(token,nullptr,10);
          if(field==49)finish=strtoull(token,nullptr,10);
        }
        if(finish==reinterpret_cast<uintptr_t>(end)&&start<=reinterpret_cast<uintptr_t>(argv[0])&&finish>start&&finish-start<=1024*1024){beginning=reinterpret_cast<char*>(start);size=finish-start;}
      }
    }fclose(file);}
    processArguments=beginning;processArgumentBytes=size;
}
}
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_setArgV0(JNIEnv* env,jclass,jstring name){
    if(!name){jclass c=env->FindClass("java/lang/NullPointerException");if(c)env->ThrowNew(c,"process name");return;}
    const char* value=env->GetStringUTFChars(name,nullptr);if(!value)return;
    size_t size=strlen(value);std::lock_guard<std::mutex> lock(processNameMutex);
    if(!size||size>255||!processArguments||size>=processArgumentBytes){env->ReleaseStringUTFChars(name,value);jclass c=env->FindClass("java/lang/IllegalArgumentException");if(c)env->ThrowNew(c,"Process name exceeds argv storage");return;}
    memset(processArguments,0,processArgumentBytes);memcpy(processArguments,value,size);
    env->ReleaseStringUTFChars(name,value);
}

extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_myPid(JNIEnv*, jclass) { return jint(getpid()); }
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_myTid(JNIEnv*, jclass) { return jint(syscall(SYS_gettid)); }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Process_getStartUptimeMillis(JNIEnv*, jclass) {
    // /proc/self/stat field 22 is the process start time in clock ticks since boot.
    FILE* file = fopen("/proc/self/stat", "r");
    if (!file) return 0;
    char stat[8192];
    bool read = fgets(stat, sizeof(stat), file) != nullptr;
    fclose(file);
    if (!read) return 0;
    char* tail = strrchr(stat, ')');
    if (!tail || tail[1] != ' ') return 0;
    char* save = nullptr;
    char* token = strtok_r(tail + 2, " ", &save);
    for (int field = 3; token && field <= 22; ++field, token = strtok_r(nullptr, " ", &save)) {
        if (field == 22) {
            const long ticks = sysconf(_SC_CLK_TCK);
            return ticks > 0 ? static_cast<jlong>(strtoull(token, nullptr, 10)) * 1000 / ticks : 0;
        }
    }
    return 0;
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_myUid(JNIEnv*, jclass) { return jint(getuid()); }
extern "C" JNIEXPORT jboolean JNICALL Java_android_os_Process_is64Bit(JNIEnv*, jclass) { return sizeof(void*) == 8 ? JNI_TRUE : JNI_FALSE; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Process_getElapsedCpuTime(JNIEnv* env, jclass) {
    timespec value{};
    if (clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &value)) {
        jclass type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, "Cannot read host process CPU clock"); env->DeleteLocalRef(type); }
        return 0;
    }
    return jlong(value.tv_sec) * 1000 + value.tv_nsec / 1000000;
}

namespace {
jlong clockNanos(JNIEnv* env, clockid_t clock) {
    timespec value{};
    if (clock_gettime(clock, &value)) {
        jclass type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, "Cannot read host monotonic/CPU clock"); env->DeleteLocalRef(type); }
        return 0;
    }
    return jlong(value.tv_sec) * 1000000000 + value.tv_nsec;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_uptimeMillis(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_MONOTONIC) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtime(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_BOOTTIME) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtimeNanos(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_BOOTTIME); }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_threadCpuNanos(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_THREAD_CPUTIME_ID); }

namespace {
void processError(JNIEnv* env, const char* kind, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass(kind);
    if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}
}
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_setThreadPriority(JNIEnv* env, jclass, jint priority) {
    if (priority < -20 || priority > 19) { processError(env, "java/lang/IllegalArgumentException", "Linux priority range is -20 through 19"); return; }
    if (setpriority(PRIO_PROCESS, id_t(syscall(SYS_gettid)), priority) && errno != EACCES && errno != EPERM)
        processError(env, "java/lang/SecurityException", "Linux rejected the thread priority change");
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_getThreadPriority(JNIEnv* env, jclass, jint tid) {
    if (tid != syscall(SYS_gettid)) { processError(env, "java/lang/UnsupportedOperationException", "Priority queries currently support only the calling host thread"); return 0; }
    errno = 0; int value = getpriority(PRIO_PROCESS, id_t(tid));
    if (value == -1 && errno) processError(env, "java/lang/IllegalStateException", "Cannot read thread priority");
    return value;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_os_Environment_nativeIsReadOnly(JNIEnv* env, jclass, jbyteArray path) {
    try {
        if (!path) { processError(env, "java/lang/NullPointerException", "Filesystem path"); return JNI_FALSE; }
        jsize length = env->GetArrayLength(path);
        if (length < 1 || length > 1048576) { processError(env, "java/lang/IllegalArgumentException", "Invalid filesystem path length"); return JNI_FALSE; }
        std::string filename(static_cast<size_t>(length), '\0');
        env->GetByteArrayRegion(path, 0, length, reinterpret_cast<jbyte*>(&filename[0]));
        if (env->ExceptionCheck()) return JNI_FALSE;
        if (filename.find('\0') != std::string::npos) { processError(env, "java/lang/IllegalArgumentException", "NUL in filesystem path"); return JNI_FALSE; }
        struct statvfs info{};
        if (statvfs(filename.c_str(), &info)) { processError(env, "java/lang/IllegalStateException", "Cannot query primary filesystem"); return JNI_FALSE; }
        return (info.f_flag & ST_RDONLY) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::bad_alloc&) { processError(env, "java/lang/OutOfMemoryError", "Cannot allocate filesystem path"); return JNI_FALSE; }
}

// Linux reports reclaimable memory separately from immediately free pages.
extern "C" JNIEXPORT jlongArray JNICALL Java_android_app_ActivityManager_nativeMemoryInfo(JNIEnv* env,jclass) {
    try {
    std::ifstream input("/proc/meminfo");std::string line; jlong total=-1,available=-1;
    for(int lines=0;lines<256&&std::getline(input,line);++lines){
        if(line.size()>1024){processError(env,"java/lang/IllegalStateException","Invalid Linux memory counters");return nullptr;}
        std::istringstream row(line);std::string key,unit;unsigned long long kb;
        if(!(row>>key)|| (key!="MemTotal:"&&key!="MemAvailable:"))continue;
        if(!(row>>kb>>unit)||unit!="kB"||kb>static_cast<unsigned long long>(std::numeric_limits<jlong>::max()/1024)){processError(env,"java/lang/IllegalStateException","Invalid Linux memory counter");return nullptr;}
        if(key=="MemTotal:")total=jlong(kb*1024);else available=jlong(kb*1024);
    }
    if(total<=0||available<0||available>total){processError(env,"java/lang/IllegalStateException","Linux memory information unavailable");return nullptr;}
    jlong values[]={total,available};jlongArray result=env->NewLongArray(2);if(result)env->SetLongArrayRegion(result,0,2,values);return result;
    } catch (const std::bad_alloc&) {processError(env,"java/lang/OutOfMemoryError","Cannot read Linux memory counters");return nullptr;}
}
namespace {
jlong heapBytes(size_t a,size_t b=0){const auto max=static_cast<size_t>(std::numeric_limits<jlong>::max());return a>max||b>max-a?std::numeric_limits<jlong>::max():jlong(a+b);}
}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapAllocatedSize(JNIEnv*,jclass){const auto info=mallinfo2();return heapBytes(info.uordblks,info.hblkhd);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapSize(JNIEnv*,jclass){const auto info=mallinfo2();return heapBytes(info.arena,info.hblkhd);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapFreeSize(JNIEnv*,jclass){return heapBytes(mallinfo2().fordblks);}

extern "C" JNIEXPORT void JNICALL Java_android_os_Process_killSelf(JNIEnv*,jclass){kill(getpid(),SIGKILL);}
