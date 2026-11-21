// SPDX-License-Identifier: GPL-3.0-only
#include <android/set_abort_message.h>
#include <errno.h>
#include <linux/random.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <sys/timerfd.h>
#include <syslog.h>
#include <unistd.h>
extern int __cxa_atexit(void (*)(void*), void*, void*);
_Static_assert(__NR_getpid == 172 && __NR_getppid == 173 && __NR_getuid == 174 &&
                   __NR_geteuid == 175 && __NR_getgid == 176 && __NR_getegid == 177 &&
                   __NR_gettid == 178,
               "Linux AArch64 process syscall ABI");
_Static_assert(__NR_getrandom == 278 && __NR_futex == 98 && GRND_NONBLOCK == 1 &&
                   GRND_RANDOM == 2 && GRND_INSECURE == 4,
               "Linux AArch64 random/futex ABI");
_Static_assert(LOG_PID == 1 && LOG_PERROR == 32 && LOG_LOCAL3 == (19 << 3), "guest syslog ABI");
_Static_assert(__NR_timerfd_create == 85 && __NR_timerfd_settime == 86 &&
                   __NR_timerfd_gettime == 87 && sizeof(struct itimerspec) == 32 &&
                   TFD_TIMER_ABSTIME == 1,
               "guest timerfd ABI");
int process_timerfd(void) {
    int fd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
    if (fd < 0)
        return 1;
    struct itimerspec setting = {{0, 0}, {0, 1}}, current;
    uint64_t ticks = 0;
    int result = timerfd_settime(fd, TFD_TIMER_ABSTIME, &setting, 0) ||
                 timerfd_gettime(fd, &current) ||
                 read(fd, &ticks, sizeof(ticks)) != (ssize_t)sizeof(ticks) || ticks != 1;
    return close(fd) || result ? 2 : 0;
}
long process_call(long number, int* error) {
    errno = 73;
    long result = syscall(number);
    *error = errno;
    return result;
}
struct signal_work {
    int ready, release;
    long tid;
};
static void* signal_worker(void* opaque) {
    struct signal_work* work = opaque;
    work->tid = syscall(__NR_gettid);
    __atomic_store_n(&work->ready, 1, __ATOMIC_RELEASE);
    unsigned remaining = 100000000;
    while (!__atomic_load_n(&work->release, __ATOMIC_ACQUIRE) && --remaining)
        __asm__ volatile("yield");
    return 0;
}
int process_thread_signal(void) {
    struct signal_work work = {0, 0, 0};
    pthread_t thread;
    if (pthread_create(&thread, 0, signal_worker, &work))
        return 1;
    unsigned remaining = 100000000;
    while (!__atomic_load_n(&work.ready, __ATOMIC_ACQUIRE) && --remaining)
        __asm__ volatile("yield");
    if (!remaining) {
        __atomic_store_n(&work.release, 1, __ATOMIC_RELEASE);
        pthread_join(thread, 0);
        return 4;
    }
    int live = work.tid > 0 && syscall(__NR_tgkill, getpid(), work.tid, 0) == 0;
    __atomic_store_n(&work.release, 1, __ATOMIC_RELEASE);
    if (pthread_join(thread, 0))
        return 2;
    errno = 0;
    int dead = syscall(__NR_tgkill, getpid(), work.tid, 0) == -1 && errno == ESRCH;
    return live && dead ? 0 : 3;
}
long process_random(void* buffer, unsigned long size, unsigned flags, int* error) {
    errno = 73;
    long result = syscall(__NR_getrandom, buffer, size, flags);
    *error = errno;
    return result;
}
int process_log(void) {
    char ident[] = "guest-copy";
    errno = 73;
    openlog(ident, LOG_PID | LOG_PERROR, LOG_LOCAL3);
    ident[0] = 'X';
    syslog(LOG_INFO, "scalar=%d float=%.2f text=%s %%", -31, 2.75, "guest");
    closelog();
    return errno;
}
int process_log_bad(void) {
    errno = 73;
    openlog("invalid", 0x4000, LOG_USER);
    return errno;
}
__attribute__((noreturn)) void process_abort(void) {
    char message[] = "owned guest abort";
    android_set_abort_message(message);
    message[0] = 'X';
    android_set_abort_message("later message must not replace first");
    abort();
}
static int notify_fd = -1, dso;
static void notify(void* unused) {
    (void)unused;
    char marker = 'F';
    if (notify_fd >= 0)
        (void)write(notify_fd, &marker, 1);
}
int process_prepare_exit(int fd) {
    notify_fd = fd;
    return __cxa_atexit(notify, 0, &dso);
}
__attribute__((noreturn)) void process_exit(int status, int fast) {
    if (fast)
        _exit(status);
    exit(status);
}
