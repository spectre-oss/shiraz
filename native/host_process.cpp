// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <signal.h>
#include <sys/resource.h>
#if defined(__APPLE__)
#include <crt_externs.h>
#include <libproc.h>
#include <mach/mach.h>
#include <malloc/malloc.h>
#include <pthread.h>
#include <sys/proc_info.h>
#include <sys/sysctl.h>
#include <sys/time.h>
#include <atomic>
#include <cmath>
#include <unordered_map>
#else
#include <sys/prctl.h>
#endif
#include <cerrno>
#if defined(__APPLE__)
#else
#include <sys/syscall.h>
#endif
#include <time.h>
#include <unistd.h>
#include <sys/statvfs.h>
#include <string>
#include <new>
#include <fstream>
#include <sstream>
#include <limits>
#if defined(__APPLE__)
#else
#include <malloc.h>
#endif

#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <mutex>
#include <grp.h>
#include <pwd.h>
#include <vector>
#include <charconv>

namespace {
jint lookupHostIdentity(JNIEnv* env, jstring name, bool group) {
    if (!name) {
        env->ThrowNew(env->FindClass("java/lang/NullPointerException"), "name");
        return -1;
    }
    const char* chars = env->GetStringUTFChars(name, nullptr);
    if (!chars) return -1;
    std::string value(chars);
    env->ReleaseStringUTFChars(name, chars);
    if (value.empty() || value.size() > 4096) return -1;
    if (value[0] >= '0' && value[0] <= '9') {
        jint numeric;
        auto parsed = std::from_chars(value.data(), value.data()+value.size(), numeric);
        return parsed.ec == std::errc() && parsed.ptr == value.data()+value.size() ? numeric : -1;
    }
    for (size_t size = 4096; size <= 1024*1024; size *= 2) {
        std::vector<char> storage(size);
        int error;
        if (group) {
            struct group entry{}, *result = nullptr;
            error = getgrnam_r(value.c_str(), &entry, storage.data(), storage.size(), &result);
            if (!error) return result && entry.gr_gid <= unsigned(INT32_MAX) ? jint(entry.gr_gid) : -1;
        } else {
            struct passwd entry{}, *result = nullptr;
            error = getpwnam_r(value.c_str(), &entry, storage.data(), storage.size(), &result);
            if (!error) return result && entry.pw_uid <= unsigned(INT32_MAX) ? jint(entry.pw_uid) : -1;
        }
        if (error != ERANGE) return -1;
    }
    return -1;
}
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_getUidForName(JNIEnv* env,jclass,jstring name) { return lookupHostIdentity(env,name,false); }
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_getGidForName(JNIEnv* env,jclass,jstring name) { return lookupHostIdentity(env,name,true); }
namespace {
char* processArguments=nullptr;size_t processArgumentBytes=0;std::mutex processNameMutex;
#if defined(__APPLE__)
// dyld keeps the original argv for every image, including dlopen'd libraries.
// Darwin has no loader-prefix launch form, so argv[0] begins the writable area ps reads.
__attribute__((constructor)) void captureProcessArguments() {
    int argc=*_NSGetArgc();char** argv=*_NSGetArgv();
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
    processArguments=argv[0];processArgumentBytes=size;
}
// Darwin has no /proc/self/cmdline. Copy argv in the /proc layout (each argument
// NUL-terminated) at load time, before setArgV0 can overwrite the argv area.
std::string* processCommandLine=nullptr;
__attribute__((constructor)) void captureProcessCommandLine() {
    int argc=*_NSGetArgc();char** argv=*_NSGetArgv();
    if(argc<=0||argc>4096||!argv)return;
    try{
        std::string line;
        for(int i=0;i<argc;i++){
            if(!argv[i])return;
            size_t size=strnlen(argv[i],1024*1024);
            if(size==1024*1024||line.size()+size+1>1024*1024)return;
            line.append(argv[i],size);line.push_back('\0');
        }
        processCommandLine=new std::string(std::move(line));
    }catch(const std::bad_alloc&){}
}
#else
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
#endif
}
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_setArgV0(JNIEnv* env,jclass,jstring name){
    if(!name){jclass c=env->FindClass("java/lang/NullPointerException");if(c)env->ThrowNew(c,"process name");return;}
    const char* value=env->GetStringUTFChars(name,nullptr);if(!value)return;
    size_t size=strlen(value);std::lock_guard<std::mutex> lock(processNameMutex);
    if(!size||size>255||!processArguments||size>=processArgumentBytes){env->ReleaseStringUTFChars(name,value);jclass c=env->FindClass("java/lang/IllegalArgumentException");if(c)env->ThrowNew(c,"Process name exceeds argv storage");return;}
    memset(processArguments,0,processArgumentBytes);memcpy(processArguments,value,size);
    env->ReleaseStringUTFChars(name,value);
}
#if defined(__APPLE__)
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_ServiceProcess_nativeCommandLine(JNIEnv* env,jclass){
    if(!processCommandLine||processCommandLine->empty()){jclass c=env->FindClass("java/io/IOException");if(c)env->ThrowNew(c,"Cannot read Darwin process arguments");return nullptr;}
    jbyteArray result=env->NewByteArray(jsize(processCommandLine->size()));
    if(result)env->SetByteArrayRegion(result,0,jsize(processCommandLine->size()),reinterpret_cast<const jbyte*>(processCommandLine->data()));
    return result;
}
#endif

extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_myPid(JNIEnv*, jclass) { return jint(getpid()); }
#if defined(__APPLE__)
namespace {
void unsupportedOnce(std::atomic<bool>& reported, const char* line) { if (!reported.exchange(true)) fprintf(stderr, "SHIRAZ_UNSUPPORTED %s\n", line); }
// Darwin thread IDs are system-wide 64-bit counters that are never reused.
jint currentThreadId() {
    static std::atomic<bool> reported{false};
    uint64_t id = 0;
    if (pthread_threadid_np(nullptr, &id) || id == 0 || id > uint64_t(INT32_MAX)) { unsupportedOnce(reported, "thread-id: Darwin thread ID exceeds the Android jint range"); return -1; }
    return jint(id);
}
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_myTid(JNIEnv*, jclass) { return currentThreadId(); }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Process_getStartUptimeMillis(JNIEnv*, jclass) {
    // Same basis as Linux /proc/self/stat field 22: milliseconds since boot, sleep included.
    proc_bsdinfo info{};
    if (proc_pidinfo(getpid(), PROC_PIDTBSDINFO, 0, &info, sizeof(info)) != int(sizeof(info))) return 0;
    timeval boot{}; size_t length = sizeof(boot); int name[] = {CTL_KERN, KERN_BOOTTIME};
    if (sysctl(name, 2, &boot, &length, nullptr, 0) || length != sizeof(boot)) return 0;
    const int64_t start = int64_t(info.pbi_start_tvsec) * 1000 + int64_t(info.pbi_start_tvusec) / 1000;
    const int64_t booted = int64_t(boot.tv_sec) * 1000 + int64_t(boot.tv_usec) / 1000;
    return start > booted ? jlong(start - booted) : 0;
}
#else
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
#endif
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
#if defined(__APPLE__)
// Linux CLOCK_MONOTONIC stops during sleep (Darwin CLOCK_UPTIME_RAW); Linux
// CLOCK_BOOTTIME keeps counting through sleep (Darwin CLOCK_MONOTONIC).
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_uptimeMillis(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_UPTIME_RAW) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtime(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_MONOTONIC) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtimeNanos(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_MONOTONIC); }
#else
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_uptimeMillis(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_MONOTONIC) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtime(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_BOOTTIME) / 1000000; }
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtimeNanos(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_BOOTTIME); }
#endif
extern "C" JNIEXPORT jlong JNICALL Java_android_os_SystemClock_threadCpuNanos(JNIEnv* env, jclass) { return clockNanos(env, CLOCK_THREAD_CPUTIME_ID); }

namespace {
void processError(JNIEnv* env, const char* kind, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass(kind);
    if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}
}
#if defined(__APPLE__)
namespace {
// Darwin has no per-thread nice value. Map Linux nice -20..19 onto the
// SCHED_OTHER pthread range (15..47, default 31 on this host) and remember the
// exact nice per thread, so values the mapping merges still round-trip.
struct ThreadNice { int nice, priority; };
std::mutex threadNiceMutex; std::unordered_map<uint64_t, ThreadNice> threadNice;
struct PriorityRange { int low, base, high; };
PriorityRange priorityRange() { const int low = sched_get_priority_min(SCHED_OTHER), high = sched_get_priority_max(SCHED_OTHER); return {low, (low + high) / 2, high}; }
int darwinPriority(int nice, PriorityRange r) { return nice <= 0 ? r.base + int(std::lround(double(-nice) * (r.high - r.base) / 20)) : r.base - int(std::lround(double(nice) * (r.base - r.low) / 19)); }
int linuxNice(int priority, PriorityRange r) {
    const int nice = priority >= r.base ? -int(std::lround(double(priority - r.base) * 20 / (r.high - r.base))) : int(std::lround(double(r.base - priority) * 19 / (r.base - r.low)));
    return nice < -20 ? -20 : nice > 19 ? 19 : nice;
}
bool threadBasePriority(uint64_t id, int& priority) {
    proc_threadinfo info{};
    if (proc_pidinfo(getpid(), PROC_PIDTHREADID64INFO, id, &info, sizeof(info)) != int(sizeof(info))) return false;
    priority = info.pth_priority; return true;
}
}
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_setThreadPriority(JNIEnv* env, jclass, jint priority) {
    if (priority < -20 || priority > 19) { processError(env, "java/lang/IllegalArgumentException", "Linux priority range is -20 through 19"); return; }
    const PriorityRange range = priorityRange();
    if (range.low < 0 || range.high - range.base < 1 || range.base - range.low < 1) { processError(env, "java/lang/SecurityException", "Darwin thread priority range unavailable"); return; }
    int policy = 0; sched_param param{};
    int error = pthread_getschedparam(pthread_self(), &policy, &param);
    // Like Linux nice on a real-time thread: keep the value, leave the policy alone.
    if (!error && policy == SCHED_OTHER) { param.sched_priority = darwinPriority(priority, range); error = pthread_setschedparam(pthread_self(), SCHED_OTHER, &param); }
    if (error) { if (error != EACCES && error != EPERM) processError(env, "java/lang/SecurityException", "Darwin rejected the thread priority change"); return; }
    uint64_t id = 0; int current = 0;
    if (pthread_threadid_np(nullptr, &id) || !threadBasePriority(id, current)) return;
    try {
        std::lock_guard<std::mutex> lock(threadNiceMutex);
        if (threadNice.size() >= 1024) for (auto entry = threadNice.begin(); entry != threadNice.end();) { int unused; entry = threadBasePriority(entry->first, unused) ? std::next(entry) : threadNice.erase(entry); }
        threadNice[id] = {priority, current};
    } catch (const std::bad_alloc&) { processError(env, "java/lang/OutOfMemoryError", "Cannot record thread priority"); }
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_getThreadPriority(JNIEnv* env, jclass, jint tid) {
    if (tid < 0) { processError(env, "java/lang/IllegalArgumentException", "Invalid thread ID"); return 0; }
    // Darwin reads threads of this process only; Linux getpriority also accepted other PIDs.
    uint64_t id = uint64_t(tid); int current = 0;
    if ((!id && pthread_threadid_np(nullptr, &id)) || !threadBasePriority(id, current)) { processError(env, "java/lang/IllegalArgumentException", "Cannot read thread priority"); return -1; }
    std::lock_guard<std::mutex> lock(threadNiceMutex);
    auto entry = threadNice.find(id);
    return entry != threadNice.end() && entry->second.priority == current ? entry->second.nice : linuxNice(current, priorityRange());
}
#else
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_setThreadPriority(JNIEnv* env, jclass, jint priority) {
    if (priority < -20 || priority > 19) { processError(env, "java/lang/IllegalArgumentException", "Linux priority range is -20 through 19"); return; }
    if (setpriority(PRIO_PROCESS, id_t(syscall(SYS_gettid)), priority) && errno != EACCES && errno != EPERM)
        processError(env, "java/lang/SecurityException", "Linux rejected the thread priority change");
}
extern "C" JNIEXPORT jint JNICALL Java_android_os_Process_getThreadPriority(JNIEnv* env, jclass, jint tid) {
    if (tid < 0) { processError(env, "java/lang/IllegalArgumentException", "Invalid thread ID"); return 0; }
    errno = 0; int value = getpriority(PRIO_PROCESS, id_t(tid));
    if (value == -1 && errno) processError(env, "java/lang/IllegalArgumentException", "Cannot read thread priority");
    return value;
}
#endif

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

#if defined(__APPLE__)
// Darwin counterpart of MemAvailable: free pages (speculative included) plus
// inactive pages the pager reclaims first.
extern "C" JNIEXPORT jlongArray JNICALL Java_android_app_ActivityManager_nativeMemoryInfo(JNIEnv* env,jclass) {
    uint64_t memory=0;size_t length=sizeof(memory);int name[]={CTL_HW,HW_MEMSIZE};
    if(sysctl(name,2,&memory,&length,nullptr,0)||length!=sizeof(memory)||memory>uint64_t(std::numeric_limits<jlong>::max())){processError(env,"java/lang/IllegalStateException","Darwin memory information unavailable");return nullptr;}
    mach_port_t host=mach_host_self();vm_size_t page=0;vm_statistics64_data_t stats{};mach_msg_type_number_t count=HOST_VM_INFO64_COUNT;
    const bool read=host_page_size(host,&page)==KERN_SUCCESS&&host_statistics64(host,HOST_VM_INFO64,reinterpret_cast<host_info64_t>(&stats),&count)==KERN_SUCCESS;
    mach_port_deallocate(mach_task_self(),host);
    const uint64_t pages=uint64_t(stats.free_count)+stats.inactive_count;
    if(!read||!page||pages>uint64_t(std::numeric_limits<jlong>::max())/page){processError(env,"java/lang/IllegalStateException","Invalid Darwin memory counter");return nullptr;}
    jlong total=jlong(memory),available=jlong(pages*page);
    if(total<=0||available<0||available>total){processError(env,"java/lang/IllegalStateException","Darwin memory information unavailable");return nullptr;}
    jlong values[]={total,available};jlongArray result=env->NewLongArray(2);if(result)env->SetLongArrayRegion(result,0,2,values);return result;
}
#else
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
#endif
namespace {
jlong heapBytes(size_t a,size_t b=0){const auto max=static_cast<size_t>(std::numeric_limits<jlong>::max());return a>max||b>max-a?std::numeric_limits<jlong>::max():jlong(a+b);}
#if defined(__APPLE__)
// All malloc zones: size_in_use matches uordblks+hblkhd, size_allocated matches arena+hblkhd.
malloc_statistics_t heapStatistics(){malloc_statistics_t stats{};malloc_zone_statistics(nullptr,&stats);return stats;}
#endif
}
#if defined(__APPLE__)
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapAllocatedSize(JNIEnv*,jclass){return heapBytes(heapStatistics().size_in_use);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapSize(JNIEnv*,jclass){return heapBytes(heapStatistics().size_allocated);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapFreeSize(JNIEnv*,jclass){const auto stats=heapStatistics();return heapBytes(stats.size_allocated>stats.size_in_use?stats.size_allocated-stats.size_in_use:0);}
#else
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapAllocatedSize(JNIEnv*,jclass){const auto info=mallinfo2();return heapBytes(info.uordblks,info.hblkhd);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapSize(JNIEnv*,jclass){const auto info=mallinfo2();return heapBytes(info.arena,info.hblkhd);}
extern "C" JNIEXPORT jlong JNICALL Java_android_os_Debug_getNativeHeapFreeSize(JNIEnv*,jclass){return heapBytes(mallinfo2().fordblks);}
#endif

extern "C" JNIEXPORT void JNICALL Java_android_os_Process_killSelf(JNIEnv*,jclass){kill(getpid(),SIGKILL);}

#if defined(__APPLE__)
// Darwin has no reversible PR_SET_DUMPABLE: PT_DENY_ATTACH is one-way and would
// also block later debuggable launches. The reversible part is core dumps: false
// lowers the soft RLIMIT_CORE to 0 and true restores the saved value. Debugger
// attach cannot be blocked; that gap is reported once and the call continues,
// because ActivityRunner calls this on every launch and an exception would stop
// every non-debuggable app.
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_shirazSetDumpable(JNIEnv* env,jclass,jboolean enabled) {
    static std::atomic<bool> reported{false};
    static std::atomic<bool> saved{false};
    static rlim_t savedCore=0;
    struct rlimit core {};
    if(getrlimit(RLIMIT_CORE,&core)!=0){
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Cannot configure application dumpability");
        return;
    }
    if(!enabled){
        if(!saved.exchange(true))savedCore=core.rlim_cur;
        core.rlim_cur=0;
    }else if(saved.exchange(false)){
        core.rlim_cur=savedCore;
    }else return;
    if(setrlimit(RLIMIT_CORE,&core)!=0){
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Cannot configure application dumpability");
        return;
    }
    if(!enabled)unsupportedOnce(reported,"PR_SET_DUMPABLE: core dumps disabled; Darwin cannot block same-user debugger attach reversibly");
}
#else
extern "C" JNIEXPORT void JNICALL Java_android_os_Process_shirazSetDumpable(JNIEnv* env,jclass,jboolean enabled) {
    if(prctl(PR_SET_DUMPABLE, enabled ? 1 : 0, 0, 0, 0)!=0)
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Cannot configure application dumpability");
}
#endif
