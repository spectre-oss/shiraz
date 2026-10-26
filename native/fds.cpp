// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <atomic>
#include <cerrno>
#include <fcntl.h>
#include <limits>
#include <new>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace {
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
jfieldID field(JNIEnv* env) {
    static std::atomic<jfieldID> cached{nullptr};
    auto id = cached.load(); if (id) return id;
    jclass cls = env->FindClass("java/io/FileDescriptor"); if (!cls) return nullptr;
    id = env->GetFieldID(cls, "descriptor", "I");
    if (!id) {
        jthrowable problem = env->ExceptionOccurred(); env->ExceptionClear();
        jclass missing = env->FindClass("java/lang/NoSuchFieldError");
        if (!missing) { if (problem) env->DeleteLocalRef(problem); env->DeleteLocalRef(cls); return nullptr; }
        bool expected = problem && env->IsInstanceOf(problem, missing); env->DeleteLocalRef(missing);
        if (!expected) {
            if (problem) env->Throw(problem); else error(env, "java/lang/IllegalStateException", "Unknown descriptor layout");
            if (problem) env->DeleteLocalRef(problem);
            env->DeleteLocalRef(cls); return nullptr;
        }
        env->DeleteLocalRef(problem); id = env->GetFieldID(cls, "fd", "I");
    }
    env->DeleteLocalRef(cls); if (id) cached.store(id); return id;
}
jint number(JNIEnv* env, jobject object, jfieldID id) {
    if (!object) { error(env, "java/lang/NullPointerException", "File descriptor"); return -1; }
    return id ? env->GetIntField(object, id) : -1;
}
bool empty(JNIEnv* env, jobject target, jfieldID id) {
    if (!target) { error(env, "java/lang/NullPointerException", "File descriptor"); return false; }
    if (!id) return false;
    if (number(env, target, id) >= 0) { error(env, "java/lang/IllegalStateException", "Target descriptor already owns a file"); return false; }
    return !env->ExceptionCheck();
}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_adopt(JNIEnv* env, jclass, jobject target, jint source) {
    auto id = field(env); if (!empty(env, target, id)) return;
    int flags = source < 0 ? -1 : fcntl(source, F_GETFD);
    if (flags < 0 || fcntl(source, F_SETFD, flags | FD_CLOEXEC) < 0) { error(env, "java/lang/IllegalArgumentException", "Invalid descriptor to adopt"); return; }
    env->SetIntField(target, id, source);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_open(JNIEnv* env, jclass, jobject target, jbyteArray path, jint mode) {
    try {
        auto id = field(env); if (!empty(env, target, id)) return;
        if (!path) { error(env, "java/lang/NullPointerException", "File path"); return; }
        auto length = env->GetArrayLength(path);
        if (length < 1 || length > 1048576 || (mode & ~0x3e000000) || !(mode & 0x30000000)) { error(env, "java/lang/IllegalArgumentException", "Invalid file path or mode"); return; }
        std::string filename(static_cast<size_t>(length), '\0'); env->GetByteArrayRegion(path, 0, length, reinterpret_cast<jbyte*>(&filename[0]));
        if (env->ExceptionCheck()) return;
        if (filename.find('\0') != std::string::npos) { error(env, "java/lang/IllegalArgumentException", "NUL in file path"); return; }
        int access = mode & 0x30000000;
        int flags = O_CLOEXEC | (access == 0x10000000 ? O_RDONLY : access == 0x20000000 ? O_WRONLY : O_RDWR);
        if (mode & 0x08000000) flags |= O_CREAT;
        if (mode & 0x04000000) flags |= O_TRUNC;
        if (mode & 0x02000000) flags |= O_APPEND;
        int fd = ::open(filename.c_str(), flags, 0600);
        if (fd < 0) { error(env, "java/io/FileNotFoundException", "Cannot open requested file"); return; }
        env->SetIntField(target, id, fd);
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate file path"); }
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_duplicate(JNIEnv* env, jclass, jobject target, jobject source) {
    auto id = field(env); if (!empty(env, target, id)) return;
    int original = number(env, source, id); if (env->ExceptionCheck()) return;
    int fd = original < 0 ? -1 : fcntl(original, F_DUPFD_CLOEXEC, 0);
    if (fd < 0) { error(env, "java/io/IOException", "Cannot duplicate descriptor"); return; }
    env->SetIntField(target, id, fd);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_duplicateNumber(JNIEnv* env, jclass, jobject target, jint source) {
    auto id = field(env); if (!empty(env, target, id)) return;
    int fd = source < 0 ? -1 : fcntl(source, F_DUPFD_CLOEXEC, 0);
    if (fd < 0) { error(env, "java/io/IOException", "Cannot duplicate descriptor"); return; }
    env->SetIntField(target, id, fd);
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeFd_number(JNIEnv* env, jclass, jobject object) { return number(env, object, field(env)); }
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeFd_detach(JNIEnv* env, jclass, jobject object) {
    auto id = field(env); int fd = number(env, object, id);
    if (id && object) env->SetIntField(object, id, -1);
    return fd;
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_close(JNIEnv* env, jclass, jobject object) {
    auto id = field(env); int fd = number(env, object, id); if (env->ExceptionCheck()) return;
    if (fd < 0) return;
    env->SetIntField(object, id, -1); // Invalidate before close, including Linux EINTR cases.
    if (::close(fd) < 0 && errno != EINTR) error(env, "java/io/IOException", "Cannot close descriptor");
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeFd_closeRawQuietly(JNIEnv*, jclass, jint fd) { if (fd >= 0) ::close(fd); }
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeFd_size(JNIEnv* env, jclass, jobject object) {
    int fd = number(env, object, field(env)); if (env->ExceptionCheck()) return -1;
    struct stat info{};
    if (fd < 0 || fstat(fd, &info)) { error(env, "java/io/IOException", "Cannot stat descriptor"); return -1; }
    return S_ISREG(info.st_mode) ? static_cast<jlong>(info.st_size) : -1;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeFd_readRegion(JNIEnv* env, jclass, jobject object, jlong offset, jlong length) {
    try {
        int fd = number(env, object, field(env)); if (env->ExceptionCheck()) return nullptr;
        if (fd < 0) { error(env, "java/io/IOException", "Descriptor is closed"); return nullptr; }
        if (offset < 0 || length < 0 || length > 16 * 1024 * 1024 || offset > std::numeric_limits<jlong>::max() - length) {
            error(env, "java/lang/IllegalArgumentException", "Invalid or oversized descriptor region"); return nullptr;
        }
        std::vector<jbyte> bytes(static_cast<size_t>(length)); size_t done = 0;
        while (done < bytes.size()) {
            ssize_t count = pread(fd, bytes.data() + done, bytes.size() - done, offset + static_cast<jlong>(done));
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) { error(env, count == 0 ? "java/io/EOFException" : "java/io/IOException", "Cannot read descriptor region"); return nullptr; }
            done += static_cast<size_t>(count);
        }
        jbyteArray result = env->NewByteArray(static_cast<jsize>(length));
        if (result && length) env->SetByteArrayRegion(result, 0, static_cast<jsize>(length), bytes.data());
        return result;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate descriptor region"); return nullptr; }
}
