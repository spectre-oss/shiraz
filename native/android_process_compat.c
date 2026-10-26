// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

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
