// SPDX-License-Identifier: GPL-3.0-only
// Native handoff bridge for pre-warmed standalone ART standby processes.
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <string>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

namespace {

constexpr size_t kMaxPayloadBytes = 256 * 1024;
constexpr size_t kExpectedFds = 3; // broker_fd, stdout_fd, stderr_fd

bool readU32(const std::vector<uint8_t>& buf, size_t& pos, uint32_t& out) {
    if (pos + 4 > buf.size())
        return false;
    out = (uint32_t(buf[pos]) << 24) | (uint32_t(buf[pos + 1]) << 16) |
          (uint32_t(buf[pos + 2]) << 8) | uint32_t(buf[pos + 3]);
    pos += 4;
    return true;
}

bool readStr(const std::vector<uint8_t>& buf, size_t& pos, std::string& out) {
    uint32_t len = 0;
    if (!readU32(buf, pos, len) || pos + len > buf.size())
        return false;
    out.assign(reinterpret_cast<const char*>(buf.data() + pos), len);
    pos += len;
    return true;
}

void throwIo(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck())
        return;
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

void closeFds(const int* fds, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        if (fds[i] >= 0)
            close(fds[i]);
    }
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_shiraz_runtime_WarmPoolRunner_signalReadyNative(JNIEnv*, jclass, jint sockFd) {
    static constexpr char kReady[] = "READY\n";
    ssize_t sent = -1;
#if defined(__APPLE__)
    int noSigPipe = 1;
    setsockopt(sockFd, SOL_SOCKET, SO_NOSIGPIPE, &noSigPipe, sizeof(noSigPipe));
    do {
        sent = send(sockFd, kReady, sizeof(kReady) - 1, 0);
    } while (sent < 0 && errno == EINTR);
#else
    do {
        sent = send(sockFd, kReady, sizeof(kReady) - 1, MSG_NOSIGNAL);
    } while (sent < 0 && errno == EINTR);
#endif
    return sent == ssize_t(sizeof(kReady) - 1) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_shiraz_runtime_WarmPoolRunner_receiveAndPrepareNative(JNIEnv* env, jclass, jint sockFd) {
    std::vector<uint8_t> buf(kMaxPayloadBytes);
    struct iovec iov{};
    iov.iov_base = buf.data();
    iov.iov_len = buf.size();

    alignas(struct cmsghdr) char cmsgBuf[CMSG_SPACE(sizeof(int) * 8)];
    std::memset(cmsgBuf, 0, sizeof(cmsgBuf));

    struct msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = cmsgBuf;
    msg.msg_controllen = sizeof(cmsgBuf);

    ssize_t received = -1;
    do {
        received = recvmsg(sockFd, &msg, 0);
    } while (received < 0 && errno == EINTR);

    if (received == 0) {
        close(sockFd);
        return nullptr; // Clean shutdown from daemon
    }
    if (received < 0) {
        close(sockFd);
        throwIo(env, "WarmPool recvmsg failed");
        return nullptr;
    }

    int fds[8];
    size_t fdCount = 0;
    for (struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg); cmsg != nullptr;
         cmsg = CMSG_NXTHDR(&msg, cmsg)) {
        if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS) {
            const size_t n = (cmsg->cmsg_len - CMSG_LEN(0)) / sizeof(int);
            const int* raw = reinterpret_cast<const int*>(CMSG_DATA(cmsg));
            for (size_t i = 0; i < n && fdCount < 8; ++i)
                fds[fdCount++] = raw[i];
        }
    }

    if ((msg.msg_flags & (MSG_TRUNC | MSG_CTRUNC)) != 0 || fdCount != kExpectedFds) {
        closeFds(fds, fdCount);
        close(sockFd);
        throwIo(env, "WarmPool launch handoff truncated or missing descriptors");
        return nullptr;
    }

    buf.resize(static_cast<size_t>(received));
#if defined(__APPLE__)
    while (buf.size() < kMaxPayloadBytes) {
        uint8_t extra[4096];
        const size_t remaining = kMaxPayloadBytes - buf.size();
        const size_t space = sizeof(extra) < remaining ? sizeof(extra) : remaining;
        ssize_t more = -1;
        do {
            more = recv(sockFd, extra, space, 0);
        } while (more < 0 && errno == EINTR);
        if (more <= 0)
            break;
        buf.insert(buf.end(), extra, extra + more);
    }
#endif
    size_t pos = 0;
    std::string dataDir, appLibDir, cpApk;
    uint32_t envCount = 0, propCount = 0, argCount = 0;
    std::vector<std::pair<std::string, std::string>> envPairs;
    std::vector<std::pair<std::string, std::string>> propPairs;
    std::vector<std::string> activityArgs;

    bool ok = readStr(buf, pos, dataDir) && readStr(buf, pos, appLibDir) &&
              readStr(buf, pos, cpApk) && readU32(buf, pos, envCount) && envCount <= 256;
    if (ok) {
        envPairs.reserve(envCount);
        for (uint32_t i = 0; i < envCount && ok; ++i) {
            std::string k, v;
            ok = readStr(buf, pos, k) && readStr(buf, pos, v);
            if (ok)
                envPairs.emplace_back(std::move(k), std::move(v));
        }
    }
    ok = ok && readU32(buf, pos, propCount) && propCount <= 128;
    if (ok) {
        propPairs.reserve(propCount);
        for (uint32_t i = 0; i < propCount && ok; ++i) {
            std::string k, v;
            ok = readStr(buf, pos, k) && readStr(buf, pos, v);
            if (ok)
                propPairs.emplace_back(std::move(k), std::move(v));
        }
    }
    ok = ok && readU32(buf, pos, argCount) && argCount <= 64;
    if (ok) {
        activityArgs.reserve(argCount);
        for (uint32_t i = 0; i < argCount && ok; ++i) {
            std::string arg;
            ok = readStr(buf, pos, arg);
            if (ok)
                activityArgs.emplace_back(std::move(arg));
        }
    }
    ok = ok && pos == buf.size();
    if (!ok) {
        closeFds(fds, fdCount);
        close(sockFd);
        throwIo(env, "WarmPool malformed launch payload");
        return nullptr;
    }

    const int brokerFd = fds[0];
    const int stdoutFd = fds[1];
    const int stderrFd = fds[2];

    if (dup2(stdoutFd, STDOUT_FILENO) < 0 || dup2(stderrFd, STDERR_FILENO) < 0) {
        closeFds(fds, fdCount);
        close(sockFd);
        throwIo(env, "WarmPool dup2 stdio failed");
        return nullptr;
    }
    if (stdoutFd > 2 && stdoutFd != brokerFd)
        close(stdoutFd);
    if (stderrFd > 2 && stderrFd != brokerFd && stderrFd != stdoutFd)
        close(stderrFd);
    if (sockFd >= 0 && sockFd != brokerFd && sockFd > 2)
        close(sockFd);

    // Android ART's java.io.FileDescriptor initializes static `out` and `err` via dup(1)/dup(2),
    // so update those duplicated descriptors as well so Java System.out/System.err/Log write to
    // the launched app's log files rather than the standby log.
    if (jclass fdCls = env->FindClass("java/io/FileDescriptor")) {
        jfieldID fdField = env->GetFieldID(fdCls, "descriptor", "I");
        jfieldID outField = env->GetStaticFieldID(fdCls, "out", "Ljava/io/FileDescriptor;");
        jfieldID errField = env->GetStaticFieldID(fdCls, "err", "Ljava/io/FileDescriptor;");
        if (fdField && outField && errField) {
            if (jobject outObj = env->GetStaticObjectField(fdCls, outField)) {
                int jOutFd = env->GetIntField(outObj, fdField);
                if (jOutFd > 2 && jOutFd != brokerFd)
                    dup2(STDOUT_FILENO, jOutFd);
                env->DeleteLocalRef(outObj);
            }
            if (jobject errObj = env->GetStaticObjectField(fdCls, errField)) {
                int jErrFd = env->GetIntField(errObj, fdField);
                if (jErrFd > 2 && jErrFd != brokerFd)
                    dup2(STDERR_FILENO, jErrFd);
                env->DeleteLocalRef(errObj);
            }
        }
        env->DeleteLocalRef(fdCls);
    }
    if (env->ExceptionCheck())
        env->ExceptionClear();

    int flags = fcntl(brokerFd, F_GETFD);
    if (flags >= 0)
        fcntl(brokerFd, F_SETFD, flags & ~FD_CLOEXEC);

    if (!dataDir.empty() && chdir(dataDir.c_str()) != 0) {
        throwIo(env, "WarmPool chdir dataDir failed");
        return nullptr;
    }

    for (const auto& kv : envPairs) {
        if (!kv.first.empty())
            setenv(kv.first.c_str(), kv.second.c_str(), 1);
    }
    const std::string brokerFdStr = std::to_string(brokerFd);
    setenv("SHIRAZ_BROKER_FD", brokerFdStr.c_str(), 1);

    if (!appLibDir.empty()) {
        setenv("BIONIC_LD_LIBRARY_PATH", appLibDir.c_str(), 1);
        using ParsePathFn = void (*)(const char*, const char*);
        void* sym = dlsym(RTLD_DEFAULT, "dl_parse_library_path");
        if (!sym) {
            for (const char* libName : {"libdl_bio.so.0", "libdl_bio.so"}) {
                void* handle = dlopen(libName, RTLD_LAZY | RTLD_NOLOAD);
                if (handle) {
                    sym = dlsym(handle, "dl_parse_library_path");
                    dlclose(handle);
                    if (sym)
                        break;
                }
            }
        }
        if (sym) {
            auto parsePath = reinterpret_cast<ParsePathFn>(sym);
            parsePath(appLibDir.c_str(), ":");
        }
    }

    const jsize totalStrings = static_cast<jsize>(3 + propPairs.size() * 2 + activityArgs.size());
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass)
        return nullptr;
    jobjectArray result = env->NewObjectArray(totalStrings, stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!result)
        return nullptr;

    jsize idx = 0;
    auto putStr = [&](const std::string& s) {
        jstring js = env->NewStringUTF(s.c_str());
        if (js) {
            env->SetObjectArrayElement(result, idx++, js);
            env->DeleteLocalRef(js);
        }
    };
    putStr(cpApk);
    putStr(appLibDir);
    putStr(std::to_string(propPairs.size()));
    for (const auto& kv : propPairs) {
        putStr(kv.first);
        putStr(kv.second);
    }
    for (const auto& arg : activityArgs) {
        putStr(arg);
    }
    return result;
}
