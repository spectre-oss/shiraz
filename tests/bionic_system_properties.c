// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct prop_info;

extern int __system_properties_init(void);
extern const struct prop_info* __system_property_find(const char* name);
extern const struct prop_info* __system_property_find_nth(unsigned n);
extern int __system_property_read(const struct prop_info* pi, char* name, char* value);
extern void __system_property_read_callback(const struct prop_info* pi,
                                            void (*callback)(void* cookie, const char* name,
                                                             const char* value, uint32_t serial),
                                            void* cookie);
extern int __system_property_get(const char* name, char* value);
extern int __system_property_foreach(void (*propfn)(const struct prop_info* pi, void* cookie),
                                     void* cookie);
extern uint32_t __system_property_serial(const struct prop_info* pi);
extern uint32_t __system_property_area_serial(void);
extern int __system_property_set(const char* key, const char* value);

struct bionic_prop_layout {
    uint32_t serial;
    char value[92];
    char name[128];
};

struct callback_capture {
    int calls;
    char name[128];
    char value[92];
    uint32_t serial;
};

static void read_callback(void* cookie, const char* name, const char* value, uint32_t serial) {
    struct callback_capture* cap = (struct callback_capture*)cookie;
    cap->calls++;
    snprintf(cap->name, sizeof(cap->name), "%s", name ? name : "");
    snprintf(cap->value, sizeof(cap->value), "%s", value ? value : "");
    cap->serial = serial;
}

struct foreach_capture {
    unsigned count;
    int saw_sdk;
    int saw_model;
    int saw_security_patch;
};

static void foreach_callback(const struct prop_info* pi, void* cookie) {
    struct foreach_capture* cap = (struct foreach_capture*)cookie;
    char name[32] = {0};
    char value[92] = {0};
    int len = __system_property_read(pi, name, value);
    assert(len >= 0);
    assert((size_t)len == strlen(value));
    cap->count++;
    if (strcmp(name, "ro.build.version.sdk") == 0 && strcmp(value, "27") == 0)
        cap->saw_sdk = 1;
    if (strcmp(name, "ro.product.model") == 0 && strcmp(value, "Shiraz Pixel") == 0)
        cap->saw_model = 1;
    if (strcmp(name, "ro.build.version.security_patch") == 0 && strcmp(value, "2026-01-01") == 0)
        cap->saw_security_patch = 1;
}

static void* worker_thread(void* arg) {
    unsigned expected_count = *(unsigned*)arg;
    for (int iter = 0; iter < 100; ++iter) {
        char value[92] = {0};
        assert(__system_property_get("ro.build.version.sdk", value) == 2);
        assert(strcmp(value, "27") == 0);
        unsigned n = 0;
        while (__system_property_find_nth(n) != NULL)
            ++n;
        assert(n == expected_count);
        const struct prop_info* model = __system_property_find("ro.product.model");
        assert(model != NULL);
        assert(__system_property_read(model, NULL, value) == (int)strlen("Shiraz Pixel"));
        assert(strcmp(value, "Shiraz Pixel") == 0);
    }
    return NULL;
}

int main(int argc, char** argv) {
    assert(__system_properties_init() == 0);
    assert(__system_property_area_serial() == 0);

    // Check expected ABI matches compiled target architecture.
#if defined(__aarch64__)
    const char* expected_abi = "arm64-v8a";
#elif defined(__x86_64__)
    const char* expected_abi = "x86_64";
#elif defined(__arm__)
    const char* expected_abi = "armeabi-v7a";
#elif defined(__i386__)
    const char* expected_abi = "x86";
#else
    const char* expected_abi = "unknown";
#endif

    // Preserve errno across lookup/get/find_nth/read/read_callback.
    errno = EBUSY;
    char value[92];
    memset(value, 'Z', sizeof(value));
    assert(__system_property_get("ro.build.version.sdk", value) == 2);
    assert(strcmp(value, "27") == 0);
    assert(errno == EBUSY);

    assert(__system_property_get("ro.product.cpu.abi", value) == (int)strlen(expected_abi));
    assert(strcmp(value, expected_abi) == 0);
    assert(__system_property_get("ro.build.version.release", value) == 5);
    assert(strcmp(value, "8.1.0") == 0);
    assert(__system_property_get("ro.build.display.id", value) == (int)strlen("Shiraz Test"));
    assert(strcmp(value, "Shiraz Test") == 0);
    assert(__system_property_get("ro.product.model", value) == (int)strlen("Shiraz Pixel"));
    assert(strcmp(value, "Shiraz Pixel") == 0);
    assert(__system_property_get("ro.build.version.security_patch", value) == 10);
    assert(strcmp(value, "2026-01-01") == 0);
    assert(errno == EBUSY);

    // Nonexistent and NULL lookups return empty string / NULL without modifying errno.
    value[0] = 'X';
    value[1] = 'Y';
    assert(__system_property_get("shiraz.nonexistent", value) == 0);
    assert(value[0] == '\0');
    assert(__system_property_get(NULL, value) == 0);
    assert(value[0] == '\0');
    assert(__system_property_find("shiraz.nonexistent") == NULL);
    assert(__system_property_find(NULL) == NULL);
    assert(errno == EBUSY);

    // Bounded enumeration with __system_property_find_nth & consistency with find/read/serial.
    unsigned nth_count = 0;
    const struct prop_info* pi = NULL;
    while ((pi = __system_property_find_nth(nth_count)) != NULL) {
        char name[32];
        memset(name, '?', sizeof(name));
        memset(value, '?', sizeof(value));
        int len = __system_property_read(pi, name, value);
        assert(len >= 0);
        assert((size_t)len == strlen(value));
        assert(name[0] != '\0');
        assert(__system_property_find(name) == pi);
        assert(__system_property_serial(pi) == ((uint32_t)len << 24));
        // Verify Bionic short-property binary layout prefix.
        const struct bionic_prop_layout* raw = (const struct bionic_prop_layout*)pi;
        assert(raw->serial == ((uint32_t)len << 24));
        assert(strcmp(raw->value, value) == 0);
        assert(strcmp(raw->name, name) == 0);
        nth_count++;
    }
    assert(nth_count >= 11);
    assert(__system_property_find_nth(nth_count) == NULL);
    assert(__system_property_find_nth(9999U) == NULL);
    assert(errno == EBUSY);

    // __system_property_read_callback delivers exact name, value, and serial.
    const struct prop_info* sdk = __system_property_find("ro.build.version.sdk");
    assert(sdk != NULL);
    struct callback_capture cap = {0};
    __system_property_read_callback(sdk, read_callback, &cap);
    assert(cap.calls == 1);
    assert(strcmp(cap.name, "ro.build.version.sdk") == 0);
    assert(strcmp(cap.value, "27") == 0);
    assert(cap.serial == (2U << 24));
    assert(errno == EBUSY);

    // Invalid/NULL handle handling.
    struct bionic_prop_layout fake = {0};
    assert(__system_property_read(NULL, NULL, value) == 0);
    assert(__system_property_serial(NULL) == 0);
    assert(__system_property_serial((const struct prop_info*)&fake) == 0);
    __system_property_read_callback((const struct prop_info*)&fake, read_callback, &cap);
    assert(cap.calls == 1);
    errno = 0;
    assert(__system_property_read((const struct prop_info*)&fake, NULL, value) == -1);
    assert(errno == EINVAL);

    // __system_property_foreach visits every property and rejects NULL callback.
    struct foreach_capture each = {0};
    assert(__system_property_foreach(foreach_callback, &each) == 0);
    assert(each.count == nth_count);
    assert(each.saw_sdk == 1 && each.saw_model == 1 && each.saw_security_patch == 1);
    errno = 0;
    assert(__system_property_foreach(NULL, NULL) == -1);
    assert(errno == EINVAL);

    // Unprivileged __system_property_set fails closed with EACCES.
    errno = 0;
    assert(__system_property_set("ro.build.version.sdk", "99") == -1);
    assert(errno == EACCES);

    // Verify both bionic_* and unprefixed exports are resolvable from libc_bio.so.0 via dlsym.
    if (argc == 2) {
        void* handle = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
        assert(handle != NULL);
        const char* names[] = {
            "__system_properties_init",        "__system_property_find",
            "__system_property_find_nth",      "__system_property_read",
            "__system_property_read_callback", "__system_property_get",
            "__system_property_foreach",       "__system_property_serial",
            "__system_property_area_serial",   "__system_property_set",
        };
        for (size_t i = 0; i < sizeof(names) / sizeof(names[0]); ++i) {
            char wrapped[128];
            snprintf(wrapped, sizeof(wrapped), "bionic_%s", names[i]);
            void* plain_sym = dlsym(handle, names[i]);
            void* wrap_sym = dlsym(handle, wrapped);
            assert(plain_sym != NULL);
            assert(wrap_sym != NULL);
            assert(plain_sym == wrap_sym);
        }
        dlclose(handle);
    }

    // Concurrent multi-threaded lookup/enumeration safety.
    pthread_t threads[4];
    for (int i = 0; i < 4; ++i)
        assert(pthread_create(&threads[i], NULL, worker_thread, &nth_count) == 0);
    for (int i = 0; i < 4; ++i)
        assert(pthread_join(threads[i], NULL) == 0);

    puts("BIONIC_SYSTEM_PROPERTIES_OK");
    return 0;
}
