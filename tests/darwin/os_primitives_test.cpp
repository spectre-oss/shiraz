// SPDX-License-Identifier: GPL-3.0-only
// Verifies macOS OS primitives implemented in native/host_process.cpp,
// native/ipc_spawn.cpp, native/fds.cpp, and native/sqlite.cpp:
//   - Clocks (CLOCK_UPTIME_RAW, CLOCK_MONOTONIC, CPU clocks)
//   - Process information (PID, Darwin->Android TID, UID/GID lookup, start time,
//     thread priority round-trip, sysctl+Mach memory info, statvfs read-only)
//   - Process spawn (posix_spawn with POSIX_SPAWN_CLOEXEC_DEFAULT fd isolation)
//   - File descriptor helpers (open, dup CLOEXEC, size, detach/adopt, close)
//   - SQLite native bridge linked against macOS SDK libsqlite3
#include <jni.h>

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

extern "C" {
JNIEXPORT jint JNICALL Java_android_os_Process_myPid(JNIEnv*, jclass);
JNIEXPORT jint JNICALL Java_android_os_Process_myTid(JNIEnv*, jclass);
JNIEXPORT jint JNICALL Java_android_os_Process_myUid(JNIEnv*, jclass);
JNIEXPORT jint JNICALL Java_android_os_Process_hostUid(JNIEnv*, jclass);
JNIEXPORT jboolean JNICALL Java_android_os_Process_is64Bit(JNIEnv*, jclass);
JNIEXPORT jlong JNICALL Java_android_os_Process_getStartUptimeMillis(JNIEnv*, jclass);
JNIEXPORT jlong JNICALL Java_android_os_Process_getElapsedCpuTime(JNIEnv*, jclass);
JNIEXPORT jint JNICALL Java_android_os_Process_getUidForName(JNIEnv*, jclass, jstring);
JNIEXPORT jint JNICALL Java_android_os_Process_getGidForName(JNIEnv*, jclass, jstring);
JNIEXPORT void JNICALL Java_android_os_Process_setThreadPriority(JNIEnv*, jclass, jint);
JNIEXPORT jint JNICALL Java_android_os_Process_getThreadPriority(JNIEnv*, jclass, jint);
JNIEXPORT jlong JNICALL Java_android_os_SystemClock_uptimeMillis(JNIEnv*, jclass);
JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtime(JNIEnv*, jclass);
JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtimeNanos(JNIEnv*, jclass);
JNIEXPORT jlong JNICALL Java_android_os_SystemClock_threadCpuNanos(JNIEnv*, jclass);
JNIEXPORT jboolean JNICALL Java_android_os_Environment_nativeIsReadOnly(JNIEnv*, jclass,
                                                                        jbyteArray);
JNIEXPORT jlongArray JNICALL Java_android_app_ActivityManager_nativeMemoryInfo(JNIEnv*, jclass);

JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeIpc_spawnNative(JNIEnv*, jclass, jobjectArray,
                                                                 jint);
JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeIpc_waitFor(JNIEnv*, jclass, jint);

JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_open(JNIEnv*, jclass, jobject, jbyteArray,
                                                         jint);
JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_duplicate(JNIEnv*, jclass, jobject, jobject);
JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_adopt(JNIEnv*, jclass, jobject, jint);
JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeFd_number(JNIEnv*, jclass, jobject);
JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeFd_detach(JNIEnv*, jclass, jobject);
JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_close(JNIEnv*, jclass, jobject);
JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeFd_size(JNIEnv*, jclass, jobject);

JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_open(JNIEnv*, jclass, jbyteArray, jint);
JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSqlite_close(JNIEnv*, jclass, jlong);
JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_prepare(JNIEnv*, jclass, jlong, jstring);
JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSqlite_finalizeStatement(JNIEnv*, jclass, jlong);
JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSqlite_execute(JNIEnv*, jclass, jlong, jlong,
                                                                 jobjectArray, jint);
}

namespace {

enum class ObjKind { String, ByteArray, LongArray, ObjectArray, Fd };

struct FakeObj {
    ObjKind kind;
    std::string bytes;
    std::u16string utf16;
    std::vector<jlong> longs;
    std::vector<FakeObj*> items;
    jint fd = -1;
};

struct FakeJniState {
    bool threw = false;
    std::string lastException;
    std::vector<FakeObj*> owned;

    FakeObj* makeBytes(const std::string& data) {
        auto* o = new FakeObj{ObjKind::ByteArray, data, {}, {}, {}, -1};
        owned.push_back(o);
        return o;
    }
    FakeObj* makeString(const std::string& ascii) {
        std::u16string u16(ascii.begin(), ascii.end());
        auto* o = new FakeObj{ObjKind::String, ascii, std::move(u16), {}, {}, -1};
        owned.push_back(o);
        return o;
    }
    FakeObj* makeLongArray(jsize n) {
        auto* o = new FakeObj{ObjKind::LongArray, {}, {}, std::vector<jlong>(size_t(n), 0), {}, -1};
        owned.push_back(o);
        return o;
    }
    FakeObj* makeObjArray(const std::vector<FakeObj*>& items) {
        auto* o = new FakeObj{ObjKind::ObjectArray, {}, {}, {}, items, -1};
        owned.push_back(o);
        return o;
    }
    FakeObj* makeFd(jint fd = -1) {
        auto* o = new FakeObj{ObjKind::Fd, {}, {}, {}, {}, fd};
        owned.push_back(o);
        return o;
    }
    ~FakeJniState() {
        for (auto* o : owned)
            delete o;
    }
};

FakeJniState* gState = nullptr;

jclass JNICALL fakeFindClass(JNIEnv*, const char*) {
    return reinterpret_cast<jclass>(1);
}
void JNICALL fakeDeleteLocalRef(JNIEnv*, jobject) {}
jboolean JNICALL fakeExceptionCheck(JNIEnv*) {
    return gState->threw ? JNI_TRUE : JNI_FALSE;
}
void JNICALL fakeExceptionClear(JNIEnv*) {
    gState->threw = false;
    gState->lastException.clear();
}
jint JNICALL fakeThrowNew(JNIEnv*, jclass, const char* msg) {
    gState->threw = true;
    gState->lastException = msg ? msg : "";
    return 0;
}
jmethodID JNICALL fakeGetMethodID(JNIEnv*, jclass, const char*, const char*) {
    return reinterpret_cast<jmethodID>(1);
}
jfieldID JNICALL fakeGetFieldID(JNIEnv*, jclass, const char*, const char*) {
    return reinterpret_cast<jfieldID>(1);
}
jint JNICALL fakeGetIntField(JNIEnv*, jobject obj, jfieldID) {
    auto* o = reinterpret_cast<FakeObj*>(obj);
    assert(o && o->kind == ObjKind::Fd);
    return o->fd;
}
void JNICALL fakeSetIntField(JNIEnv*, jobject obj, jfieldID, jint value) {
    auto* o = reinterpret_cast<FakeObj*>(obj);
    assert(o && o->kind == ObjKind::Fd);
    o->fd = value;
}
const char* JNICALL fakeGetStringUTFChars(JNIEnv*, jstring str, jboolean*) {
    auto* o = reinterpret_cast<FakeObj*>(str);
    assert(o && o->kind == ObjKind::String);
    return o->bytes.c_str();
}
void JNICALL fakeReleaseStringUTFChars(JNIEnv*, jstring, const char*) {}
jsize JNICALL fakeGetStringLength(JNIEnv*, jstring str) {
    auto* o = reinterpret_cast<FakeObj*>(str);
    assert(o && o->kind == ObjKind::String);
    return static_cast<jsize>(o->utf16.size());
}
void JNICALL fakeGetStringRegion(JNIEnv*, jstring str, jsize start, jsize len, jchar* buf) {
    auto* o = reinterpret_cast<FakeObj*>(str);
    assert(o && o->kind == ObjKind::String);
    std::memcpy(buf, o->utf16.data() + start, static_cast<size_t>(len) * sizeof(jchar));
}
jsize JNICALL fakeGetArrayLength(JNIEnv*, jarray arr) {
    auto* o = reinterpret_cast<FakeObj*>(arr);
    assert(o);
    if (o->kind == ObjKind::ByteArray)
        return static_cast<jsize>(o->bytes.size());
    if (o->kind == ObjKind::LongArray)
        return static_cast<jsize>(o->longs.size());
    if (o->kind == ObjKind::ObjectArray)
        return static_cast<jsize>(o->items.size());
    assert(false);
    return 0;
}
void JNICALL fakeGetByteArrayRegion(JNIEnv*, jbyteArray arr, jsize start, jsize len, jbyte* buf) {
    auto* o = reinterpret_cast<FakeObj*>(arr);
    assert(o && o->kind == ObjKind::ByteArray);
    std::memcpy(buf, o->bytes.data() + start, static_cast<size_t>(len));
}
jobject JNICALL fakeGetObjectArrayElement(JNIEnv*, jobjectArray arr, jsize idx) {
    auto* o = reinterpret_cast<FakeObj*>(arr);
    assert(o && o->kind == ObjKind::ObjectArray);
    return reinterpret_cast<jobject>(o->items[static_cast<size_t>(idx)]);
}
jlongArray JNICALL fakeNewLongArray(JNIEnv*, jsize len) {
    return reinterpret_cast<jlongArray>(gState->makeLongArray(len));
}
void JNICALL fakeSetLongArrayRegion(JNIEnv*, jlongArray arr, jsize start, jsize len,
                                    const jlong* buf) {
    auto* o = reinterpret_cast<FakeObj*>(arr);
    assert(o && o->kind == ObjKind::LongArray);
    std::memcpy(o->longs.data() + start, buf, static_cast<size_t>(len) * sizeof(jlong));
}

void testClocks(JNIEnv* env) {
    const jlong up0 = Java_android_os_SystemClock_uptimeMillis(env, nullptr);
    const jlong el0 = Java_android_os_SystemClock_elapsedRealtime(env, nullptr);
    const jlong elNs0 = Java_android_os_SystemClock_elapsedRealtimeNanos(env, nullptr);
    const jlong threadCpu0 = Java_android_os_SystemClock_threadCpuNanos(env, nullptr);
    const jlong procCpu0 = Java_android_os_Process_getElapsedCpuTime(env, nullptr);
    assert(!gState->threw);
    assert(up0 > 0);
    assert(el0 > 0);
    assert(elNs0 > 0);
    assert(threadCpu0 >= 0);
    assert(procCpu0 >= 0);

    volatile uint64_t sink = 0;
    for (int i = 0; i < 200000; ++i)
        sink += static_cast<uint64_t>(i);
    (void)sink;
    usleep(10000);

    const jlong up1 = Java_android_os_SystemClock_uptimeMillis(env, nullptr);
    const jlong el1 = Java_android_os_SystemClock_elapsedRealtime(env, nullptr);
    const jlong elNs1 = Java_android_os_SystemClock_elapsedRealtimeNanos(env, nullptr);
    const jlong threadCpu1 = Java_android_os_SystemClock_threadCpuNanos(env, nullptr);
    const jlong procCpu1 = Java_android_os_Process_getElapsedCpuTime(env, nullptr);
    assert(!gState->threw);
    assert(up1 >= up0 + 5);
    assert(el1 >= el0 + 5);
    assert(elNs1 > elNs0);
    assert(threadCpu1 >= threadCpu0);
    assert(procCpu1 >= procCpu0);
    std::printf("PASS darwin clocks uptime_delta=%lldms elapsed_delta=%lldms\n",
                static_cast<long long>(up1 - up0), static_cast<long long>(el1 - el0));
}

void testProcessInfo(JNIEnv* env) {
    assert(Java_android_os_Process_myPid(env, nullptr) == static_cast<jint>(getpid()));
    assert(Java_android_os_Process_myUid(env, nullptr) == static_cast<jint>(getuid()));
    assert(Java_android_os_Process_hostUid(env, nullptr) == static_cast<jint>(getuid()));
    assert(Java_android_os_Process_is64Bit(env, nullptr) == JNI_TRUE);
    const jint tid = Java_android_os_Process_myTid(env, nullptr);
    assert(tid > 0);
    const jlong startUptime = Java_android_os_Process_getStartUptimeMillis(env, nullptr);
    assert(startUptime >= 0);

    assert(Java_android_os_Process_getUidForName(
               env, nullptr, reinterpret_cast<jstring>(gState->makeString("root"))) == 0);
    assert(Java_android_os_Process_getGidForName(
               env, nullptr, reinterpret_cast<jstring>(gState->makeString("wheel"))) == 0);
    assert(!gState->threw);

    Java_android_os_Process_setThreadPriority(env, nullptr, 5);
    assert(!gState->threw);
    assert(Java_android_os_Process_getThreadPriority(env, nullptr, 0) == 5);
    assert(Java_android_os_Process_getThreadPriority(env, nullptr, tid) == 5);
    Java_android_os_Process_setThreadPriority(env, nullptr, 0);
    assert(!gState->threw);

    jlongArray memArr = Java_android_app_ActivityManager_nativeMemoryInfo(env, nullptr);
    assert(memArr && !gState->threw);
    auto* mem = reinterpret_cast<FakeObj*>(memArr);
    assert(mem->longs.size() >= 2);
    assert(mem->longs[0] > 0);                                   // totalMem
    assert(mem->longs[1] > 0 && mem->longs[1] <= mem->longs[0]); // availMem

    jboolean ro = Java_android_os_Environment_nativeIsReadOnly(
        env, nullptr, reinterpret_cast<jbyteArray>(gState->makeBytes("/tmp")));
    assert(!gState->threw && ro == JNI_FALSE);
    std::printf("PASS darwin process_info pid=%d tid=%d total_mem_mb=%lld\n",
                static_cast<int>(getpid()), static_cast<int>(tid),
                static_cast<long long>(mem->longs[0] / (1024 * 1024)));
}

void testProcessSpawn(JNIEnv* env) {
    int socks[2] = {-1, -1};
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, socks) == 0);

    // Extra pipe WITHOUT FD_CLOEXEC: POSIX_SPAWN_CLOEXEC_DEFAULT must close it in child.
    int canary[2] = {-1, -1};
    assert(pipe(canary) == 0);
    // Ensure write end is > 3 and does NOT have FD_CLOEXEC.
    int writeEnd = fcntl(canary[1], F_DUPFD, 10);
    assert(writeEnd >= 10);
    close(canary[1]);
    canary[1] = writeEnd;
    assert((fcntl(canary[1], F_GETFD) & FD_CLOEXEC) == 0);

    auto* cmd = gState->makeObjArray({
        gState->makeBytes("/bin/sh"),
        gState->makeBytes("-c"),
        gState->makeBytes("printf 'SHIRAZ_SPAWN_OK' >&3; sleep 0.05"),
    });
    const jint pid = Java_shiraz_runtime_NativeIpc_spawnNative(
        env, nullptr, reinterpret_cast<jobjectArray>(cmd), socks[1]);
    assert(pid > 0 && !gState->threw);
    close(socks[1]);
    // Close parent's write end of canary pipe. Because child did not inherit it,
    // read() on canary[0] must return EOF (0) immediately even while child sleeps.
    close(canary[1]);
    char dummy = 0;
    assert(read(canary[0], &dummy, 1) == 0);
    close(canary[0]);

    char reply[32] = {};
    ssize_t n = read(socks[0], reply, sizeof(reply) - 1);
    assert(n == 15);
    assert(std::strcmp(reply, "SHIRAZ_SPAWN_OK") == 0);
    close(socks[0]);

    const jint exitCode = Java_shiraz_runtime_NativeIpc_waitFor(env, nullptr, pid);
    assert(exitCode == 0 && !gState->threw);
    std::printf("PASS darwin process_spawn pid=%d cloexec_isolated=1\n", static_cast<int>(pid));
}

void testFdHelpers(JNIEnv* env) {
    char pathTemplate[] = "/tmp/shiraz_darwin_fd_XXXXXX";
    int tmp = mkstemp(pathTemplate);
    assert(tmp >= 0);
    const char payload[] = "shiraz-darwin-fd-test";
    assert(write(tmp, payload, sizeof(payload) - 1) == static_cast<ssize_t>(sizeof(payload) - 1));
    close(tmp);

    auto* fd1 = gState->makeFd();
    // 0x10000000 = O_RDONLY
    Java_shiraz_runtime_NativeFd_open(env, nullptr, reinterpret_cast<jobject>(fd1),
                                      reinterpret_cast<jbyteArray>(gState->makeBytes(pathTemplate)),
                                      0x10000000);
    assert(!gState->threw);
    const jint raw1 =
        Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd1));
    assert(raw1 >= 0);
    assert((fcntl(raw1, F_GETFD) & FD_CLOEXEC) != 0);
    const jlong sz =
        Java_shiraz_runtime_NativeFd_size(env, nullptr, reinterpret_cast<jobject>(fd1));
    assert(sz == static_cast<jlong>(sizeof(payload) - 1));

    auto* fd2 = gState->makeFd();
    Java_shiraz_runtime_NativeFd_duplicate(env, nullptr, reinterpret_cast<jobject>(fd2),
                                           reinterpret_cast<jobject>(fd1));
    assert(!gState->threw);
    const jint raw2 =
        Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd2));
    assert(raw2 >= 0 && raw2 != raw1);
    assert((fcntl(raw2, F_GETFD) & FD_CLOEXEC) != 0);

    const jint detached =
        Java_shiraz_runtime_NativeFd_detach(env, nullptr, reinterpret_cast<jobject>(fd2));
    assert(detached == raw2);
    assert(Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd2)) == -1);

    auto* fd3 = gState->makeFd();
    Java_shiraz_runtime_NativeFd_adopt(env, nullptr, reinterpret_cast<jobject>(fd3), detached);
    assert(!gState->threw);
    assert(Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd3)) ==
           detached);

    Java_shiraz_runtime_NativeFd_close(env, nullptr, reinterpret_cast<jobject>(fd1));
    Java_shiraz_runtime_NativeFd_close(env, nullptr, reinterpret_cast<jobject>(fd3));
    assert(!gState->threw);
    assert(Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd1)) == -1);
    assert(Java_shiraz_runtime_NativeFd_number(env, nullptr, reinterpret_cast<jobject>(fd3)) == -1);
    unlink(pathTemplate);
    std::printf("PASS darwin fd_helpers size=%lld cloexec=1\n", static_cast<long long>(sz));
}

void testSqlite(JNIEnv* env) {
    // 0x10000000 = OPEN_CREATE | READWRITE
    const jlong db = Java_shiraz_runtime_NativeSqlite_open(
        env, nullptr, reinterpret_cast<jbyteArray>(gState->makeBytes(":memory:")), 0x10000000);
    assert(db > 0 && !gState->threw);

    const jlong createStmt = Java_shiraz_runtime_NativeSqlite_prepare(
        env, nullptr, db,
        reinterpret_cast<jstring>(
            gState->makeString("CREATE TABLE kv (id INTEGER PRIMARY KEY, v INTEGER)")));
    assert(createStmt > 0 && !gState->threw);
    Java_shiraz_runtime_NativeSqlite_execute(env, nullptr, db, createStmt, nullptr, 0);
    assert(!gState->threw);
    Java_shiraz_runtime_NativeSqlite_finalizeStatement(env, nullptr, createStmt);

    const jlong insStmt = Java_shiraz_runtime_NativeSqlite_prepare(
        env, nullptr, db,
        reinterpret_cast<jstring>(gState->makeString("INSERT INTO kv (v) VALUES (42)")));
    assert(insStmt > 0 && !gState->threw);
    const jlong rowId =
        Java_shiraz_runtime_NativeSqlite_execute(env, nullptr, db, insStmt, nullptr, 1);
    assert(rowId == 1 && !gState->threw);
    Java_shiraz_runtime_NativeSqlite_finalizeStatement(env, nullptr, insStmt);

    const jlong selStmt = Java_shiraz_runtime_NativeSqlite_prepare(
        env, nullptr, db,
        reinterpret_cast<jstring>(gState->makeString("SELECT v FROM kv WHERE id = 1")));
    assert(selStmt > 0 && !gState->threw);
    const jlong val =
        Java_shiraz_runtime_NativeSqlite_execute(env, nullptr, db, selStmt, nullptr, 3);
    assert(val == 42 && !gState->threw);
    Java_shiraz_runtime_NativeSqlite_finalizeStatement(env, nullptr, selStmt);

    Java_shiraz_runtime_NativeSqlite_close(env, nullptr, db);
    assert(!gState->threw);
    std::printf("PASS darwin sqlite rowid=%lld value=%lld\n", static_cast<long long>(rowId),
                static_cast<long long>(val));
}

} // namespace

int main() {
    FakeJniState state;
    gState = &state;

    JNINativeInterface_ table{};
    table.FindClass = fakeFindClass;
    table.DeleteLocalRef = fakeDeleteLocalRef;
    table.ExceptionCheck = fakeExceptionCheck;
    table.ExceptionClear = fakeExceptionClear;
    table.ThrowNew = fakeThrowNew;
    table.GetMethodID = fakeGetMethodID;
    table.GetFieldID = fakeGetFieldID;
    table.GetIntField = fakeGetIntField;
    table.SetIntField = fakeSetIntField;
    table.GetStringUTFChars = fakeGetStringUTFChars;
    table.ReleaseStringUTFChars = fakeReleaseStringUTFChars;
    table.GetStringLength = fakeGetStringLength;
    table.GetStringRegion = fakeGetStringRegion;
    table.GetArrayLength = fakeGetArrayLength;
    table.GetByteArrayRegion = fakeGetByteArrayRegion;
    table.GetObjectArrayElement = fakeGetObjectArrayElement;
    table.NewLongArray = fakeNewLongArray;
    table.SetLongArrayRegion = fakeSetLongArrayRegion;
    JNIEnv_ env{&table};

    testClocks(&env);
    testProcessInfo(&env);
    testProcessSpawn(&env);
    testFdHelpers(&env);
    testSqlite(&env);

    std::puts("MACOS_OS_PRIMITIVES_PASS");
    return 0;
}
