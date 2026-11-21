// SPDX-License-Identifier: GPL-3.0-only
#include <stddef.h>
#include <stdint.h>
#if defined(ASSET_LINK_ONLY)
#define TRAP __builtin_trap()
void* AAssetManager_open(void* m, const char* n, int mode) {
    (void)m;
    (void)n;
    (void)mode;
    TRAP;
}
const void* AAsset_getBuffer(void* a) {
    (void)a;
    TRAP;
}
int64_t AAsset_getLength64(void* a) {
    (void)a;
    TRAP;
}
int AAsset_read(void* a, void* b, size_t n) {
    (void)a;
    (void)b;
    (void)n;
    TRAP;
}
void AAsset_close(void* a) {
    (void)a;
    TRAP;
}
#else
extern void* AAssetManager_open(void*, const char*, int);
extern const void* AAsset_getBuffer(void*);
extern int64_t AAsset_getLength64(void*);
extern int AAsset_read(void*, void*, size_t);
extern void AAsset_close(void*);
#define CHECK(c)                                                                                   \
    do {                                                                                           \
        if (!(c))                                                                                  \
            return __LINE__;                                                                       \
    } while (0)
static int check_asset(void* manager, const char* name) {
    void* asset = AAssetManager_open(manager, name, 2);
    CHECK(asset);
    CHECK(AAsset_getLength64(asset) == 10);
    const unsigned char* buffer = AAsset_getBuffer(asset);
    CHECK(buffer);
    for (int i = 0; i < 10; ++i)
        CHECK(buffer[i] == (unsigned char)('0' + i));
    unsigned char first[3];
    CHECK(AAsset_read(asset, first, sizeof(first)) == 3);
    for (int i = 0; i < 3; ++i)
        CHECK(first[i] == (unsigned char)('0' + i));
    CHECK(AAsset_getLength64(asset) == 10);
    AAsset_close(asset);
    return 0;
}
int guest_asset_check(void* manager) {
    CHECK(!AAsset_getBuffer(0));
    CHECK(AAsset_getLength64(0) == 0);
    int result = check_asset(manager, "stored.txt");
    if (result)
        return result;
    return check_asset(manager, "compressed.txt");
}
#endif
