// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK liblog.so implementation with custom logger/aborter hooks,
// minimum priority filtering, default tags, and formatted output.
#include "shiraz/ndk/log.h"

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace {

std::mutex gLogMu;
std::atomic<int32_t> gMinPriority{ANDROID_LOG_DEFAULT};
std::string gDefaultTag = "shiraz";
__android_logger_function gLogger = __android_log_stderr_logger;
__android_aborter_function gAborter = __android_log_default_aborter;

char priorityChar(int32_t prio) {
    switch (prio) {
    case ANDROID_LOG_VERBOSE:
        return 'V';
    case ANDROID_LOG_DEBUG:
        return 'D';
    case ANDROID_LOG_INFO:
        return 'I';
    case ANDROID_LOG_WARN:
        return 'W';
    case ANDROID_LOG_ERROR:
        return 'E';
    case ANDROID_LOG_FATAL:
        return 'F';
    case ANDROID_LOG_SILENT:
        return 'S';
    default:
        return '?';
    }
}

std::string formatV(const char* fmt, va_list ap) {
    if (!fmt) {
        return "";
    }
    va_list copy;
    va_copy(copy, ap);
    const int needed = std::vsnprintf(nullptr, 0, fmt, copy);
    va_end(copy);
    if (needed <= 0) {
        return "";
    }
    std::string out(static_cast<size_t>(needed), '\0');
    std::vsnprintf(out.data(), out.size() + 1, fmt, ap);
    return out;
}

std::string resolveTag(const char* tag) {
    if (tag && tag[0] != '\0') {
        return std::string(tag);
    }
    std::lock_guard<std::mutex> lock(gLogMu);
    return gDefaultTag;
}

} // namespace

extern "C" {

void __android_log_stderr_logger(const struct __android_log_message* msg) {
    if (!msg) {
        return;
    }
    const char* tag = msg->tag ? msg->tag : "shiraz";
    const char* text = msg->message ? msg->message : "";
    if (msg->file && msg->file[0] != '\0') {
        std::fprintf(stderr, "%c/%s (%s:%u): %s\n", priorityChar(msg->priority), tag, msg->file,
                     msg->line, text);
    } else {
        std::fprintf(stderr, "%c/%s: %s\n", priorityChar(msg->priority), tag, text);
    }
    std::fflush(stderr);
}

void __android_log_logd_logger(const struct __android_log_message* msg) {
    // On desktop hosts without Android logd, route logd messages to stderr logger.
    __android_log_stderr_logger(msg);
}

void __android_log_write_log_message(struct __android_log_message* msg) {
    if (!msg || msg->struct_size != sizeof(struct __android_log_message)) {
        return;
    }
    if (msg->buffer_id < LOG_ID_MIN ||
        (msg->buffer_id >= LOG_ID_MAX && msg->buffer_id != LOG_ID_DEFAULT)) {
        return;
    }
    const std::string tag = resolveTag(msg->tag);
    struct __android_log_message resolved = *msg;
    resolved.tag = tag.c_str();
    if (!__android_log_is_loggable(resolved.priority, resolved.tag, ANDROID_LOG_INFO)) {
        return;
    }
    __android_logger_function fn = nullptr;
    {
        std::lock_guard<std::mutex> lock(gLogMu);
        fn = gLogger;
    }
    if (fn) {
        fn(&resolved);
    }
}

int __android_log_buf_write(int bufID, int prio, const char* tag, const char* text) {
    if (!text) {
        return -1;
    }
    struct __android_log_message msg = {
        sizeof(struct __android_log_message), bufID, prio, tag, nullptr, 0, text,
    };
    __android_log_write_log_message(&msg);
    return static_cast<int>(std::strlen(text));
}

int __android_log_write(int prio, const char* tag, const char* text) {
    return __android_log_buf_write(LOG_ID_MAIN, prio, tag, text);
}

int __android_log_vprint(int prio, const char* tag, const char* fmt, va_list ap) {
    const std::string formatted = formatV(fmt, ap);
    return __android_log_write(prio, tag, formatted.c_str());
}

int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    const int ret = __android_log_vprint(prio, tag, fmt, ap);
    va_end(ap);
    return ret;
}

int __android_log_buf_print(int bufID, int prio, const char* tag, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    const std::string formatted = formatV(fmt, ap);
    va_end(ap);
    return __android_log_buf_write(bufID, prio, tag, formatted.c_str());
}

void __android_log_assert(const char* cond, const char* tag, const char* fmt, ...) {
    std::string msg;
    if (fmt) {
        va_list ap;
        va_start(ap, fmt);
        msg = formatV(fmt, ap);
        va_end(ap);
    } else if (cond) {
        msg = std::string("Assertion failed: ") + cond;
    } else {
        msg = "Unspecified assertion failed";
    }
    __android_log_write(ANDROID_LOG_FATAL, tag, msg.c_str());
    __android_log_call_aborter(msg.c_str());
}

int __android_log_is_loggable_len(int prio, const char* /*tag*/, size_t /*len*/, int default_prio) {
    if (prio == ANDROID_LOG_SILENT) {
        return 0;
    }
    int32_t minPrio = gMinPriority.load();
    if (minPrio == ANDROID_LOG_DEFAULT || minPrio == ANDROID_LOG_UNKNOWN) {
        minPrio = default_prio;
    }
    return (prio >= minPrio) ? 1 : 0;
}

int __android_log_is_loggable(int prio, const char* tag, int default_prio) {
    return __android_log_is_loggable_len(prio, tag, tag ? std::strlen(tag) : 0, default_prio);
}

int32_t __android_log_set_minimum_priority(int32_t priority) {
    return gMinPriority.exchange(priority);
}

int32_t __android_log_get_minimum_priority(void) {
    return gMinPriority.load();
}

void __android_log_set_logger(__android_logger_function logger) {
    std::lock_guard<std::mutex> lock(gLogMu);
    gLogger = logger ? logger : __android_log_stderr_logger;
}

void __android_log_set_aborter(__android_aborter_function aborter) {
    std::lock_guard<std::mutex> lock(gLogMu);
    gAborter = aborter ? aborter : __android_log_default_aborter;
}

void __android_log_default_aborter(const char* abort_message) {
    if (abort_message) {
        std::fprintf(stderr, "FATAL ABORT: %s\n", abort_message);
        std::fflush(stderr);
    }
    std::abort();
}

void __android_log_call_aborter(const char* abort_message) {
    __android_aborter_function fn = nullptr;
    {
        std::lock_guard<std::mutex> lock(gLogMu);
        fn = gAborter;
    }
    if (fn) {
        fn(abort_message);
    } else {
        __android_log_default_aborter(abort_message);
    }
}

void __android_log_set_default_tag(const char* tag) {
    std::lock_guard<std::mutex> lock(gLogMu);
    gDefaultTag = (tag && tag[0] != '\0') ? std::string(tag) : std::string("shiraz");
}

} // extern "C"
