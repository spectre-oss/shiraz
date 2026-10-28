// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include "android_profile_properties.h"

// Dart's Android OS version query reads this property directly from Bionic.
// The host runtime supplies the same value used by android.os.Build.DISPLAY.
int __system_property_get(const char *name, char *value) {
    if (name && value) {
        const char *configured = NULL;
        if (strcmp(name, "ro.build.display.id") == 0)
            configured = getenv("SHIRAZ_ANDROID_DISPLAY_ID");
        else if (strcmp(name, "ro.build.version.release") == 0)
            configured = getenv("SHIRAZ_ANDROID_RELEASE");
        else if (strcmp(name, "ro.build.version.sdk") == 0)
            configured = "27";
        if (!configured) {
            for (size_t i = 0; i < sizeof(shiraz_profile_properties)/sizeof(shiraz_profile_properties[0]); ++i)
                if (strcmp(name, shiraz_profile_properties[i].name) == 0) {
                    configured = getenv(shiraz_profile_properties[i].environment);
                    break;
                }
        }
        if (configured) {
            size_t length = strlen(configured);
            if (length > 0 && length < 92) {
                memcpy(value, configured, length + 1);
                return (int)length;
            }
        }
    }
    int (*next)(const char *, char *) = dlsym(RTLD_NEXT, "__system_property_get");
    if (next) return next(name, value);
    if (value) *value = 0;
    return 0;
}

// Bionic's dlsym searches bionic_* host exports for process FFI symbols.
// These use the host allocator already backing Bionic's libc compatibility.
void *bionic_malloc(size_t size) { return malloc(size); }
void *bionic_calloc(size_t count, size_t size) { return calloc(count, size); }
void *bionic_realloc(void *pointer, size_t size) { return realloc(pointer, size); }
void bionic_free(void *pointer) { free(pointer); }

// Opt-in Android virtual-device metadata. This is not host SELinux enforcement.
// Java's libcore performs these reads in the host process rather than in the ARM guest.
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <unistd.h>
#include "virtual_file_metadata.h"
static const char *virtual_android_path(const char *path) {
    const char *variable = path && !strcmp(path, "/sys/fs/selinux/enforce") ? "SHIRAZ_GUEST_SELINUX_ENFORCE_FILE" : path && !strcmp(path, "/proc/self/attr/current") ? "SHIRAZ_GUEST_SELINUX_CONTEXT_FILE" : NULL;
    const char *file = variable ? getenv(variable) : NULL;
    if (file && file[0] == '/') return file;
    return path;
}
int stat64(const char *path, struct stat64 *info) {
    int (*next)(const char *, struct stat64 *) = dlsym(RTLD_NEXT, "stat64");
    if (!next) { errno = ENOSYS; return -1; }
    const char *mapped = virtual_android_path(path);
    int result = next(mapped, info);
    struct timespec time;
    if (result == 0 && shiraz_virtual_file_time(mapped, &time))
        info->st_atim = info->st_mtim = info->st_ctim = time;
    return result;
}
int lstat64(const char *path, struct stat64 *info) {
    int (*next)(const char *, struct stat64 *) = dlsym(RTLD_NEXT, "lstat64");
    if (!next) { errno = ENOSYS; return -1; }
    const char *mapped = virtual_android_path(path);
    int result = next(mapped, info);
    struct timespec time;
    if (result == 0 && shiraz_virtual_file_time(mapped, &time))
        info->st_atim = info->st_mtim = info->st_ctim = time;
    return result;
}
int access(const char *path, int mode) {
    const char *mapped = virtual_android_path(path);
    if (mapped != path && (mode & W_OK)) { errno = EROFS; return -1; }
    int (*next)(const char *, int) = dlsym(RTLD_NEXT, "access");
    if (!next) { errno = ENOSYS; return -1; }
    return next(mapped, mode);
}
int open64(const char *path, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) || ((flags & O_TMPFILE) == O_TMPFILE)) {
        va_list args; va_start(args, flags); mode = va_arg(args, int); va_end(args);
    }
    const char *mapped = virtual_android_path(path);
    if (mapped != path && ((flags & O_ACCMODE) != O_RDONLY || (flags & (O_CREAT|O_TRUNC|O_APPEND)))) { errno = EROFS; return -1; }
    int (*next)(const char *, int, ...) = dlsym(RTLD_NEXT, "open64");
    if (!next) { errno = ENOSYS; return -1; }
    return next(mapped, flags, mode);
}
