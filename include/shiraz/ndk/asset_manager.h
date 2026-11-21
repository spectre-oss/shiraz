// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <jni.h>
#include <sys/types.h>

#if defined(__APPLE__)
using shiraz_off64_t = int64_t;
#else
using shiraz_off64_t = off64_t;
#endif

struct AAssetManager;
struct AAssetDir;
struct AAsset;

enum {
    AASSET_MODE_UNKNOWN = 0,
    AASSET_MODE_RANDOM = 1,
    AASSET_MODE_STREAMING = 2,
    AASSET_MODE_BUFFER = 3,
};

extern "C" {
AAssetManager* AAssetManager_fromJava(JNIEnv* env, jobject assetManager);
AAssetDir* AAssetManager_openDir(AAssetManager* mgr, const char* dirName);
AAsset* AAssetManager_open(AAssetManager* mgr, const char* filename, int mode);
const char* AAssetDir_getNextFileName(AAssetDir* assetDir);
void AAssetDir_rewind(AAssetDir* assetDir);
void AAssetDir_close(AAssetDir* assetDir);
int AAsset_read(AAsset* asset, void* buf, size_t count);
off_t AAsset_seek(AAsset* asset, off_t offset, int whence);
shiraz_off64_t AAsset_seek64(AAsset* asset, shiraz_off64_t offset, int whence);
void AAsset_close(AAsset* asset);
const void* AAsset_getBuffer(AAsset* asset);
off_t AAsset_getLength(AAsset* asset);
shiraz_off64_t AAsset_getLength64(AAsset* asset);
off_t AAsset_getRemainingLength(AAsset* asset);
shiraz_off64_t AAsset_getRemainingLength64(AAsset* asset);
int AAsset_openFileDescriptor(AAsset* asset, off_t* outStart, off_t* outLength);
int AAsset_openFileDescriptor64(AAsset* asset, shiraz_off64_t* outStart, shiraz_off64_t* outLength);
int AAsset_isAllocated(AAsset* asset);

// Shiraz directory-backed AAssetManager helpers for standalone native tests and apps.
AAssetManager* shiraz_asset_manager_from_dir(const char* root_dir);
void shiraz_asset_manager_destroy(AAssetManager* mgr);
}
