// SPDX-License-Identifier: GPL-3.0-only
#ifndef SHIRAZ_VIRTUAL_FILE_METADATA_H
#define SHIRAZ_VIRTUAL_FILE_METADATA_H
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
// Only explicitly configured virtual backing files have a modeled creation time.
// This describes the guest filesystem view, not host SELinux enforcement.
static int shiraz_virtual_file_time(const char *path, struct timespec *time) {
    const char *epoch = getenv("SHIRAZ_GUEST_METADATA_EPOCH");
    if (!path || !epoch || !*epoch) return 0;
    const char *enforce = getenv("SHIRAZ_GUEST_SELINUX_ENFORCE_FILE");
    const char *context = getenv("SHIRAZ_GUEST_SELINUX_CONTEXT_FILE");
    if (!((enforce && enforce[0] == '/' && !strcmp(path, enforce)) ||
          (context && context[0] == '/' && !strcmp(path, context)))) return 0;
    int saved_errno = errno;
    errno = 0;
    char *end;
    long long seconds = strtoll(epoch, &end, 10);
    int valid = !errno && end != epoch && !*end && seconds > 0 &&
                (long long)(time_t)seconds == seconds;
    errno = saved_errno;
    if (!valid) return 0;
    time->tv_sec = (time_t)seconds;
    time->tv_nsec = 0;
    return 1;
}
#endif
