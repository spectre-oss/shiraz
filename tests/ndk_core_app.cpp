// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/asset_manager.h"
#include "shiraz/ndk/configuration.h"
#include "shiraz/ndk/input.h"
#include "shiraz/ndk/native_activity.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

extern "C" {
ALooper* ALooper_prepare(int opts);
int ALooper_pollAll(int timeoutMillis, int* outFd, int* outEvents, void** outData);
uint32_t AFont_getAxisTag(const void*, uint32_t);
float AFont_getAxisValue(const void*, uint32_t);
}

int main() {
    // 1. AConfiguration
    AConfiguration* cfg1 = AConfiguration_new();
    AConfiguration* cfg2 = AConfiguration_new();
    assert(cfg1 && cfg2);
    AConfiguration_fromAssetManager(cfg1, nullptr);
    assert(AConfiguration_getSdkVersion(cfg1) == 35);
    AConfiguration_setLanguage(cfg1, "fa");
    AConfiguration_setCountry(cfg1, "IR");
    AConfiguration_setDensity(cfg1, ACONFIGURATION_DENSITY_XXHIGH);
    AConfiguration_setOrientation(cfg1, ACONFIGURATION_ORIENTATION_LAND);
    AConfiguration_setScreenWidthDp(cfg1, 840);
    AConfiguration_setScreenHeightDp(cfg1, 412);
    AConfiguration_setSmallestScreenWidthDp(cfg1, 412);
    AConfiguration_copy(cfg2, cfg1);
    assert(AConfiguration_diff(cfg1, cfg2) == 0);
    char lang[2]{}, country[2]{};
    AConfiguration_getLanguage(cfg2, lang);
    AConfiguration_getCountry(cfg2, country);
    assert(lang[0] == 'f' && lang[1] == 'a');
    assert(country[0] == 'I' && country[1] == 'R');
    AConfiguration_setOrientation(cfg2, ACONFIGURATION_ORIENTATION_PORT);
    AConfiguration_setDensity(cfg2, ACONFIGURATION_DENSITY_HIGH);
    int32_t diff = AConfiguration_diff(cfg1, cfg2);
    assert((diff & ACONFIGURATION_ORIENTATION) != 0);
    assert((diff & ACONFIGURATION_DENSITY) != 0);
    assert((diff & ACONFIGURATION_LOCALE) == 0);
    AConfiguration_delete(cfg1);
    AConfiguration_delete(cfg2);

    // 2. AInputQueue + AInputEvent + ALooper_pollAll
    ALooper* looper = ALooper_prepare(1);
    assert(looper);
    AInputQueue* queue = shiraz_input_queue_create();
    assert(queue);
    AInputQueue_attachLooper(queue, looper, 42, nullptr, nullptr);
    assert(AInputQueue_hasEvents(queue) == 0);
    assert(shiraz_input_queue_push_key(queue, 3, 0x101, 0, 29, 30, 1, 0, 0, 1000, 1010) == 0);
    ShirazMotionPointer ptrs[2]{};
    ptrs[0].id = 7;
    ptrs[0].x = 123.5f;
    ptrs[0].y = 456.25f;
    ptrs[0].pressure = 0.8f;
    ptrs[1].id = 9;
    ptrs[1].x = 200.0f;
    ptrs[1].y = 300.0f;
    assert(shiraz_input_queue_push_motion(queue, 4, 0x1002, 2, 0, 0, 1, 1, 2000, 2050, ptrs, 2) ==
           0);
    int fd = -1, events = 0;
    void* data = nullptr;
    int ident = -1;
    for (int tries = 0; tries < 4 && ident == -1; ++tries)
        ident = ALooper_pollAll(100, &fd, &events, &data);
    assert(ident == 42);
    assert(AInputQueue_hasEvents(queue) == 1);

    AInputEvent* ev = nullptr;
    assert(AInputQueue_getEvent(queue, &ev) == 0 && ev != nullptr);
    assert(AInputEvent_getType(ev) == AINPUT_EVENT_TYPE_KEY);
    assert(AKeyEvent_getKeyCode(ev) == 29);
    assert(AKeyEvent_getScanCode(ev) == 30);
    assert(AKeyEvent_getEventTime(ev) == 1010);
    assert(AInputQueue_preDispatchEvent(queue, ev) == 0);
    AInputQueue_finishEvent(queue, ev, 1);
    assert(shiraz_input_queue_last_handled(queue) == 1);

    assert(AInputQueue_getEvent(queue, &ev) == 0 && ev != nullptr);
    assert(AInputEvent_getType(ev) == AINPUT_EVENT_TYPE_MOTION);
    assert(AMotionEvent_getPointerCount(ev) == 2);
    assert(AMotionEvent_getPointerId(ev, 0) == 7);
    assert(AMotionEvent_getX(ev, 0) == 123.5f);
    assert(AMotionEvent_getY(ev, 0) == 456.25f);
    assert(AMotionEvent_getAxisValue(ev, AMOTION_EVENT_AXIS_PRESSURE, 0) == 0.8f);
    assert(AMotionEvent_getPointerId(ev, 1) == 9);
    AInputQueue_finishEvent(queue, ev, 1);
    assert(AInputQueue_hasEvents(queue) == 0);
    AInputQueue_detachLooper(queue);
    shiraz_input_queue_destroy(queue);

    // 3. AAssetManager / AAssetDir / AAsset
    char tmp_dir[] = "/tmp/shiraz-ndk-assets-XXXXXX";
    assert(mkdtemp(tmp_dir) != nullptr);
    std::string file_a = std::string(tmp_dir) + "/alpha.txt";
    std::string file_b = std::string(tmp_dir) + "/beta.bin";
    {
        FILE* f = std::fopen(file_a.c_str(), "wb");
        assert(f);
        std::fwrite("hello-shiraz-ndk", 1, 16, f);
        std::fclose(f);
        FILE* f2 = std::fopen(file_b.c_str(), "wb");
        assert(f2);
        std::fwrite("0123456789", 1, 10, f2);
        std::fclose(f2);
    }
    AAssetManager* mgr = shiraz_asset_manager_from_dir(tmp_dir);
    assert(mgr);
    AAssetDir* dir = AAssetManager_openDir(mgr, "");
    assert(dir);
    const char* first = AAssetDir_getNextFileName(dir);
    const char* second = AAssetDir_getNextFileName(dir);
    assert(first && std::strcmp(first, "alpha.txt") == 0);
    assert(second && std::strcmp(second, "beta.bin") == 0);
    assert(AAssetDir_getNextFileName(dir) == nullptr);
    AAssetDir_rewind(dir);
    assert(std::strcmp(AAssetDir_getNextFileName(dir), "alpha.txt") == 0);
    AAssetDir_close(dir);

    AAsset* asset = AAssetManager_open(mgr, "alpha.txt", AASSET_MODE_BUFFER);
    assert(asset);
    assert(AAsset_getLength(asset) == 16);
    assert(AAsset_getRemainingLength(asset) == 16);
    assert(AAsset_isAllocated(asset) == 1);
    char buf[16]{};
    assert(AAsset_read(asset, buf, 5) == 5);
    assert(std::memcmp(buf, "hello", 5) == 0);
    assert(AAsset_getRemainingLength(asset) == 11);
    assert(AAsset_seek(asset, 6, SEEK_SET) == 6);
    assert(AAsset_read(asset, buf, 6) == 6);
    assert(std::memcmp(buf, "shiraz", 6) == 0);
    assert(std::memcmp(AAsset_getBuffer(asset), "hello-shiraz-ndk", 16) == 0);
    off_t fd_start = -1, fd_len = -1;
    int afd = AAsset_openFileDescriptor(asset, &fd_start, &fd_len);
    assert(afd >= 0 && fd_start == 0 && fd_len == 16);
    close(afd);
    AAsset_close(asset);
    // Reject path traversal.
    assert(AAssetManager_open(mgr, "../etc/passwd", AASSET_MODE_BUFFER) == nullptr);

    // 4. ANativeActivity
    ANativeActivity* act = shiraz_native_activity_create(tmp_dir, tmp_dir, tmp_dir, mgr);
    assert(act && act->assetManager == mgr && act->sdkVersion == 35);
    ANativeActivity_setWindowFormat(act, 2);
    ANativeActivity_setWindowFlags(act, 0x05, 0x01);
    ANativeActivity_showSoftInput(act, 1);
    assert(shiraz_native_activity_window_format(act) == 2);
    assert(shiraz_native_activity_window_flags(act) == 0x04);
    assert(shiraz_native_activity_soft_input_visible(act) == 1);
    ANativeActivity_hideSoftInput(act, 0);
    assert(shiraz_native_activity_soft_input_visible(act) == 0);
    ANativeActivity_finish(act);
    assert(shiraz_native_activity_is_finished(act) == 1);
    shiraz_native_activity_destroy(act);

    shiraz_asset_manager_destroy(mgr);
    std::remove(file_a.c_str());
    std::remove(file_b.c_str());
    rmdir(tmp_dir);

    assert(AFont_getAxisTag(nullptr, 0) == 0);
    assert(AFont_getAxisValue(nullptr, 0) == 0.0f);
    return 0;
}
