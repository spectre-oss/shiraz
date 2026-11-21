// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/android_bitmap.h"
#include "shiraz/hardware_buffer.h"
#include "shiraz/ndk/imagedecoder.h"
#include "shiraz/ndk/surface_control.h"
#include "shiraz/ndk/system_services.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <unistd.h>
#include <vector>

namespace {
bool collect_bytes(void* ctx, const void* data, size_t size) {
    auto* out = static_cast<std::vector<uint8_t>*>(ctx);
    const auto* p = static_cast<const uint8_t*>(data);
    out->insert(out->end(), p, p + size);
    return true;
}

void write_le32(std::vector<uint8_t>& v, uint32_t x) {
    v.push_back(static_cast<uint8_t>(x & 0xff));
    v.push_back(static_cast<uint8_t>((x >> 8) & 0xff));
    v.push_back(static_cast<uint8_t>((x >> 16) & 0xff));
    v.push_back(static_cast<uint8_t>((x >> 24) & 0xff));
}
} // namespace

int main() {
    // 1. ASurfaceControl + ASurfaceTransaction + Stats callbacks
    ASurfaceControl* root = ASurfaceControl_create(nullptr, "root");
    ASurfaceControl* child = ASurfaceControl_create(root, "child");
    assert(root && child);
    AHardwareBuffer_Desc desc{};
    desc.width = 4;
    desc.height = 4;
    desc.layers = 1;
    desc.format = 1;
    desc.usage = 0x33;
    AHardwareBuffer* hb = nullptr;
    assert(AHardwareBuffer_allocate(&desc, &hb) == 0 && hb != nullptr);

    int commit_calls = 0;
    int complete_calls = 0;
    ASurfaceTransaction* tx = ASurfaceTransaction_create();
    assert(tx);
    ASurfaceTransaction_setVisibility(tx, child, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    ASurfaceTransaction_setZOrder(tx, child, 5);
    ASurfaceTransaction_setBuffer(tx, child, hb, -1);
    ASurfaceTransaction_setPosition(tx, child, 10, 20);
    ASurfaceTransaction_setScale(tx, child, 2.0f, 2.0f);
    ASurfaceTransaction_setBufferAlpha(tx, child, 0.75);
    ASurfaceTransaction_setFrameRate(tx, child, 60.0f, 1);
    ASurfaceTransaction_setOnCommit(tx, &commit_calls,
                                    [](void* ctx, ASurfaceTransactionStats* stats) {
                                        (*static_cast<int*>(ctx))++;
                                        assert(ASurfaceTransactionStats_getLatchTime(stats) > 0);
                                    });
    ASurfaceTransaction_setOnComplete(
        tx, &complete_calls, [](void* ctx, ASurfaceTransactionStats* stats) {
            (*static_cast<int*>(ctx))++;
            ASurfaceControl** list = nullptr;
            size_t count = 0;
            ASurfaceTransactionStats_getASurfaceControls(stats, &list, &count);
            assert(count == 1 && list != nullptr);
            assert(ASurfaceTransactionStats_getAcquireTime(stats, list[0]) > 0);
            ASurfaceTransactionStats_releaseASurfaceControls(list);
        });
    ASurfaceTransaction_apply(tx);
    assert(commit_calls == 1 && complete_calls == 1);
    ASurfaceTransaction_delete(tx);
    AHardwareBuffer_release(hb);
    ASurfaceControl_release(child);
    ASurfaceControl_release(root);

    // 2. AndroidBitmap_compress (PNG, JPEG, WebP) + AImageDecoder round-trip
    constexpr uint32_t W = 8, H = 8;
    std::vector<uint8_t> src_rgba(W * H * 4);
    for (uint32_t y = 0; y < H; ++y) {
        for (uint32_t x = 0; x < W; ++x) {
            size_t idx = (y * W + x) * 4;
            src_rgba[idx + 0] = 200;
            src_rgba[idx + 1] = 80;
            src_rgba[idx + 2] = 40;
            src_rgba[idx + 3] = 255;
        }
    }
    AndroidBitmapInfo binfo{W, H, W * 4, 1, 0};

    // PNG round-trip
    std::vector<uint8_t> png_bytes;
    assert(AndroidBitmap_compress(&binfo, 142671872, src_rgba.data(),
                                  ANDROID_BITMAP_COMPRESS_FORMAT_PNG, 100, &png_bytes,
                                  collect_bytes) == 0);
    assert(png_bytes.size() > 8);
    AImageDecoder* dec = nullptr;
    assert(AImageDecoder_createFromBuffer(png_bytes.data(), png_bytes.size(), &dec) ==
               ANDROID_IMAGE_DECODER_SUCCESS &&
           dec != nullptr);
    const auto* hinfo = AImageDecoder_getHeaderInfo(dec);
    assert(AImageDecoderHeaderInfo_getWidth(hinfo) == static_cast<int32_t>(W));
    assert(AImageDecoderHeaderInfo_getHeight(hinfo) == static_cast<int32_t>(H));
    assert(std::strcmp(AImageDecoderHeaderInfo_getMimeType(hinfo), "image/png") == 0);
    std::vector<uint8_t> decoded(W * H * 4, 0);
    assert(AImageDecoder_decodeImage(dec, decoded.data(), W * 4, decoded.size()) ==
           ANDROID_IMAGE_DECODER_SUCCESS);
    assert(decoded[0] == 200 && decoded[1] == 80 && decoded[2] == 40 && decoded[3] == 255);

    // Test target size + crop
    int32_t sw = 0, sh = 0;
    assert(AImageDecoder_computeSampledSize(dec, 2, &sw, &sh) == ANDROID_IMAGE_DECODER_SUCCESS);
    assert(sw == 4 && sh == 4);
    assert(AImageDecoder_setTargetSize(dec, 4, 4) == ANDROID_IMAGE_DECODER_SUCCESS);
    assert(AImageDecoder_setCrop(dec, ARect{1, 1, 3, 3}) == ANDROID_IMAGE_DECODER_SUCCESS);
    assert(AImageDecoder_getMinimumStride(dec) == 8);
    std::vector<uint8_t> cropped(2 * 8, 0);
    assert(AImageDecoder_decodeImage(dec, cropped.data(), 8, cropped.size()) ==
           ANDROID_IMAGE_DECODER_SUCCESS);
    assert(cropped[0] == 200 && cropped[1] == 80 && cropped[2] == 40);
    AImageDecoder_delete(dec);

    // JPEG round-trip
    std::vector<uint8_t> jpg_bytes;
    assert(AndroidBitmap_compress(&binfo, 142671872, src_rgba.data(),
                                  ANDROID_BITMAP_COMPRESS_FORMAT_JPEG, 95, &jpg_bytes,
                                  collect_bytes) == 0);
    assert(AImageDecoder_createFromBuffer(jpg_bytes.data(), jpg_bytes.size(), &dec) ==
           ANDROID_IMAGE_DECODER_SUCCESS);
    assert(std::strcmp(AImageDecoderHeaderInfo_getMimeType(AImageDecoder_getHeaderInfo(dec)),
                       "image/jpeg") == 0);
    AImageDecoder_delete(dec);

    // WebP lossless round-trip
    std::vector<uint8_t> webp_bytes;
    assert(AndroidBitmap_compress(&binfo, 142671872, src_rgba.data(),
                                  ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSLESS, 100, &webp_bytes,
                                  collect_bytes) == 0);
    assert(AImageDecoder_createFromBuffer(webp_bytes.data(), webp_bytes.size(), &dec) ==
           ANDROID_IMAGE_DECODER_SUCCESS);
    assert(std::strcmp(AImageDecoderHeaderInfo_getMimeType(AImageDecoder_getHeaderInfo(dec)),
                       "image/webp") == 0);
    std::fill(decoded.begin(), decoded.end(), 0);
    assert(AImageDecoder_decodeImage(dec, decoded.data(), W * 4, decoded.size()) ==
           ANDROID_IMAGE_DECODER_SUCCESS);
    assert(decoded[0] == 200 && decoded[1] == 80 && decoded[2] == 40 && decoded[3] == 255);
    AImageDecoder_delete(dec);

    // 3. APerformanceHint + AThermal + AStorageManager + AObbScanner
    APerformanceHintManager* pm = APerformanceHint_getManager();
    assert(pm && APerformanceHint_getPreferredUpdateRateNanos(pm) > 0);
    int32_t tids[1] = {1234};
    APerformanceHintSession* ps = APerformanceHint_createSession(pm, tids, 1, 16666666);
    assert(ps);
    assert(APerformanceHint_updateTargetWorkDuration(ps, 8333333) == 0);
    assert(APerformanceHint_reportActualWorkDuration(ps, 7000000) == 0);
    AWorkDuration* wd = AWorkDuration_create();
    assert(wd);
    AWorkDuration_setActualTotalDurationNanos(wd, 6500000);
    assert(APerformanceHint_reportActualWorkDuration2(ps, wd) == 0);
    AWorkDuration_release(wd);
    APerformanceHint_closeSession(ps);

    AThermalManager* tm = AThermal_acquireManager();
    assert(tm && AThermal_getCurrentThermalStatus(tm) == ATHERMAL_STATUS_NONE);
    assert(std::isfinite(AThermal_getThermalHeadroom(tm, 5)));
    const AThermalHeadroomThreshold* thresholds = nullptr;
    size_t tcount = 0;
    assert(AThermal_getThermalHeadroomThresholds(tm, &thresholds, &tcount) == 0 && tcount > 0);
    AThermal_releaseManager(tm);

    // OBB v1 binary footer parsing
    char obb_path[] = "/tmp/shiraz-obb-XXXXXX";
    int ofd = mkstemp(obb_path);
    assert(ofd >= 0);
    std::vector<uint8_t> obb_data(32, 0xAA);
    std::string pkg = "org.shiraz.ndktest";
    std::vector<uint8_t> footer;
    write_le32(footer, 1);                // sig_version
    write_le32(footer, 42);               // package_version
    write_le32(footer, AOBBINFO_OVERLAY); // flags
    write_le32(footer, static_cast<uint32_t>(pkg.size()));
    footer.insert(footer.end(), pkg.begin(), pkg.end());
    obb_data.insert(obb_data.end(), footer.begin(), footer.end());
    write_le32(obb_data, static_cast<uint32_t>(footer.size()));
    write_le32(obb_data, 0x01039983u);
    assert(write(ofd, obb_data.data(), obb_data.size()) == static_cast<ssize_t>(obb_data.size()));
    close(ofd);

    AObbInfo* obb = AObbScanner_getObbInfo(obb_path);
    assert(obb);
    assert(std::strcmp(AObbInfo_getPackageName(obb), "org.shiraz.ndktest") == 0);
    assert(AObbInfo_getVersion(obb) == 42);
    assert(AObbInfo_getFlags(obb) == AOBBINFO_OVERLAY);
    AObbInfo_delete(obb);
    std::remove(obb_path);

    AStorageManager* sm = AStorageManager_new();
    assert(sm);
    int cb_state = -1;
    AStorageManager_mountObb(
        sm, "/tmp", nullptr, [](const char*, int32_t st, void* u) { *static_cast<int*>(u) = st; },
        &cb_state);
    assert(cb_state == AOBB_STATE_MOUNTED);
    assert(AStorageManager_isObbMounted(sm, "/tmp") == 1);
    assert(std::strcmp(AStorageManager_getMountedObbPath(sm, "/tmp"), "/tmp") == 0);
    AStorageManager_unmountObb(
        sm, "/tmp", 0, [](const char*, int32_t st, void* u) { *static_cast<int*>(u) = st; },
        &cb_state);
    assert(cb_state == AOBB_STATE_UNMOUNTED);
    AStorageManager_delete(sm);
    return 0;
}
