// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum android_LogPriority {
    ANDROID_LOG_UNKNOWN = 0,
    ANDROID_LOG_DEFAULT = 1,
    ANDROID_LOG_VERBOSE = 2,
    ANDROID_LOG_DEBUG = 3,
    ANDROID_LOG_INFO = 4,
    ANDROID_LOG_WARN = 5,
    ANDROID_LOG_ERROR = 6,
    ANDROID_LOG_FATAL = 7,
    ANDROID_LOG_SILENT = 8,
} android_LogPriority;

typedef enum log_id {
    LOG_ID_MIN = 0,
    LOG_ID_MAIN = 0,
    LOG_ID_RADIO = 1,
    LOG_ID_EVENTS = 2,
    LOG_ID_SYSTEM = 3,
    LOG_ID_CRASH = 4,
    LOG_ID_STATS = 5,
    LOG_ID_SECURITY = 6,
    LOG_ID_KERNEL = 7,
    LOG_ID_MAX = 8,
    LOG_ID_DEFAULT = 0x7FFFFFFF,
} log_id_t;

struct __android_log_message {
    size_t struct_size;
    int32_t buffer_id;
    int32_t priority;
    const char* tag;
    const char* file;
    uint32_t line;
    const char* message;
};

typedef void (*__android_logger_function)(const struct __android_log_message* log_message);
typedef void (*__android_aborter_function)(const char* abort_message);

int __android_log_write(int prio, const char* tag, const char* text);
int __android_log_print(int prio, const char* tag, const char* fmt, ...);
int __android_log_vprint(int prio, const char* tag, const char* fmt, va_list ap);
void __android_log_assert(const char* cond, const char* tag, const char* fmt, ...);
int __android_log_buf_write(int bufID, int prio, const char* tag, const char* text);
int __android_log_buf_print(int bufID, int prio, const char* tag, const char* fmt, ...);

int __android_log_is_loggable(int prio, const char* tag, int default_prio);
int __android_log_is_loggable_len(int prio, const char* tag, size_t len, int default_prio);
int32_t __android_log_set_minimum_priority(int32_t priority);
int32_t __android_log_get_minimum_priority(void);

void __android_log_set_logger(__android_logger_function logger);
void __android_log_stderr_logger(const struct __android_log_message* log_message);
void __android_log_logd_logger(const struct __android_log_message* log_message);
void __android_log_write_log_message(struct __android_log_message* log_message);

void __android_log_set_aborter(__android_aborter_function aborter);
void __android_log_default_aborter(const char* abort_message);
void __android_log_call_aborter(const char* abort_message);
void __android_log_set_default_tag(const char* tag);

#ifdef __cplusplus
}
#endif
