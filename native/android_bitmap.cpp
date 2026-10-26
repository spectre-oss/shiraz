// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/android_bitmap.h"
#include <algorithm>
#include <array>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace {
constexpr int bad = -1, exception = -2, allocation = -3;
constexpr size_t budget = 128 * 1024 * 1024;
struct Pixels {
    jobject bitmap = nullptr;
    jintArray array = nullptr;
    bool alpha = false;
    unsigned locks = 1;
    std::vector<unsigned char> bytes;
};
std::mutex mutex;
std::vector<std::unique_ptr<Pixels>> locked;
size_t used = 0;
int describe(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info, jintArray* pixels) {
    if (!env || !bitmap) return bad;
    if (env->ExceptionCheck()) return exception;
    jclass cls = env->FindClass("android/graphics/Bitmap");
    if (!cls) return exception;
    if (!env->IsInstanceOf(bitmap, cls)) { env->DeleteLocalRef(cls); return bad; }
    jfieldID width = env->GetFieldID(cls, "width", "I");
    jfieldID height = width ? env->GetFieldID(cls, "height", "I") : nullptr;
    jfieldID field = height ? env->GetFieldID(cls, "pixels", "[I") : nullptr;
    jmethodID row = field ? env->GetMethodID(cls, "getRowBytes", "()I") : nullptr;
    if (!row) { env->DeleteLocalRef(cls); return exception; }
    int w = env->GetIntField(bitmap, width), h = env->GetIntField(bitmap, height);
    auto array = static_cast<jintArray>(env->GetObjectField(bitmap, field));
    int stride = env->CallIntMethod(bitmap, row);
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) { if (array) env->DeleteLocalRef(array); return exception; }
    if (!array || w <= 0 || h <= 0 || w > 8192 || h > 8192 ||
        static_cast<int64_t>(w) * h > 32 * 1024 * 1024 ||
        env->GetArrayLength(array) != static_cast<int64_t>(w) * h ||
        (stride != w && stride != w * 4)) {
        if (array) env->DeleteLocalRef(array);
        return bad;
    }
    // Android 8.1 reserves flags; RGBA pixels use premultiplied sRGB bytes.
    *info = {static_cast<uint32_t>(w), static_cast<uint32_t>(h),
             static_cast<uint32_t>(stride), stride == w ? 8 : 1, 0};
    if (pixels) *pixels = array; else env->DeleteLocalRef(array);
    return 0;
}
unsigned char premul(unsigned value, unsigned alpha) { return (value * alpha + 127) / 255; }
uint32_t decode(const unsigned char* p) {
    unsigned a = p[3];
    auto channel = [a](unsigned c) { return a ? std::min(255u, (c * 255 + a / 2) / a) : 0u; };
    return (a << 24) | (channel(p[0]) << 16) | (channel(p[1]) << 8) | channel(p[2]);
}
void encode(uint32_t color, unsigned char* p) {
    unsigned a = color >> 24;
    p[0] = premul((color >> 16) & 255, a);
    p[1] = premul((color >> 8) & 255, a);
    p[2] = premul(color & 255, a);
    p[3] = a;
}
}
extern "C" int AndroidBitmap_getInfo(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info) {
    if (!info) return bad;
    return describe(env, bitmap, info, nullptr);
}
extern "C" int AndroidBitmap_lockPixels(JNIEnv* env, jobject bitmap, void** address) {
    if (!env || !bitmap || !address) return bad;
    if (env->ExceptionCheck()) return exception;
    std::lock_guard<std::mutex> guard(mutex);
    for (auto& entry : locked) if (env->IsSameObject(bitmap, entry->bitmap)) {
        if (entry->locks == 65535) return bad;
        ++entry->locks; *address = entry->bytes.data(); return 0;
    }
    AndroidBitmapInfo info{}; jintArray source = nullptr;
    int result = describe(env, bitmap, &info, &source);
    if (result) return result;
    jclass bitmapClass=env->GetObjectClass(bitmap);
    jmethodID synchronize=bitmapClass?env->GetMethodID(bitmapClass,"shirazPrepareNativePixels","()V"):nullptr;
    if(synchronize)env->CallVoidMethod(bitmap,synchronize);
    if(bitmapClass)env->DeleteLocalRef(bitmapClass);
    if(env->ExceptionCheck()){env->DeleteLocalRef(source);return exception;}
    size_t bytes = static_cast<size_t>(info.stride) * info.height;
    if (locked.size() == 64 || bytes > budget - used) { env->DeleteLocalRef(source); return allocation; }
    std::unique_ptr<Pixels> entry;
    try {
        entry = std::make_unique<Pixels>();
        entry->bytes.resize(bytes);
        locked.reserve(locked.size() + 1);
    } catch (const std::bad_alloc&) { env->DeleteLocalRef(source); return allocation; }
    entry->alpha = info.format == 8;
    entry->bitmap = env->NewGlobalRef(bitmap);
    if (entry->bitmap) entry->array = static_cast<jintArray>(env->NewGlobalRef(source));
    env->DeleteLocalRef(source);
    if (!entry->array) {
        if (entry->bitmap) env->DeleteGlobalRef(entry->bitmap);
        return env->ExceptionCheck() ? exception : allocation;
    }
    std::array<jint, 1024> chunk;
    const size_t count = static_cast<size_t>(info.width) * info.height;
    for (size_t offset = 0; offset < count; offset += chunk.size()) {
        size_t n = std::min(chunk.size(), count - offset);
        env->GetIntArrayRegion(entry->array, offset, n, chunk.data());
        if (env->ExceptionCheck()) {
            env->DeleteGlobalRef(entry->bitmap); env->DeleteGlobalRef(entry->array); return exception;
        }
        for (size_t i = 0; i < n; ++i) {
            if (entry->alpha) entry->bytes[offset + i] = static_cast<uint32_t>(chunk[i]) >> 24;
            else encode(static_cast<uint32_t>(chunk[i]), entry->bytes.data() + (offset + i) * 4);
        }
    }
    *address = entry->bytes.data(); used += bytes; locked.push_back(std::move(entry));
    return 0;
}
extern "C" int AndroidBitmap_unlockPixels(JNIEnv* env, jobject bitmap) {
    if (!env || !bitmap) return bad;
    // The caller must clear its JNI exception before attempting a writeback.
    if (env->ExceptionCheck()) return exception;
    std::lock_guard<std::mutex> guard(mutex);
    auto it = std::find_if(locked.begin(), locked.end(), [&](const auto& p) { return env->IsSameObject(bitmap, p->bitmap); });
    if (it == locked.end()) return bad;
    auto& entry = **it;
    if (--entry.locks) return 0;
    std::array<jint, 1024> chunk;
    const size_t count = entry.bytes.size() / (entry.alpha ? 1 : 4);
    for (size_t offset = 0; offset < count; offset += chunk.size()) {
        size_t n = std::min(chunk.size(), count - offset);
        env->GetIntArrayRegion(entry.array, offset, n, chunk.data());
        if (env->ExceptionCheck()) break;
        for (size_t i = 0; i < n; ++i) {
            if (entry.alpha) chunk[i] = static_cast<uint32_t>(entry.bytes[offset + i]) << 24;
            else {
                unsigned char previous[4]; encode(static_cast<uint32_t>(chunk[i]), previous);
                const auto* p = entry.bytes.data() + (offset + i) * 4;
                // Preserve exact straight-alpha colors for unmodified pixels.
                if (!std::equal(previous, previous + 4, p)) chunk[i] = decode(p);
            }
        }
        env->SetIntArrayRegion(entry.array, offset, n, chunk.data());
        if (env->ExceptionCheck()) break;
    }
    used -= entry.bytes.size();
    env->DeleteGlobalRef(entry.bitmap); env->DeleteGlobalRef(entry.array);
    locked.erase(it);
    return env->ExceptionCheck() ? exception : 0;
}
