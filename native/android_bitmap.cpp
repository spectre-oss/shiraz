// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/android_bitmap.h"
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <vector>

namespace {
constexpr int bad = -1, exception = -2, allocation = -3;
constexpr size_t budget = 128 * 1024 * 1024;
struct Pixels {
    jobject bitmap = nullptr;
    jintArray array = nullptr;
    unsigned depth = 4;
    unsigned locks = 1;
    std::vector<unsigned char> bytes;
};
std::mutex mutex;
std::vector<std::unique_ptr<Pixels>> locked;
size_t used = 0;
int describe(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info, jintArray* pixels) {
    if (!env || !bitmap)
        return bad;
    if (env->ExceptionCheck())
        return exception;
    jclass cls = env->FindClass("android/graphics/Bitmap");
    if (!cls)
        return exception;
    if (!env->IsInstanceOf(bitmap, cls)) {
        env->DeleteLocalRef(cls);
        return bad;
    }
    jfieldID width = env->GetFieldID(cls, "width", "I");
    jfieldID height = width ? env->GetFieldID(cls, "height", "I") : nullptr;
    jfieldID field = height ? env->GetFieldID(cls, "pixels", "[I") : nullptr;
    jmethodID row = field ? env->GetMethodID(cls, "getRowBytes", "()I") : nullptr;
    if (!row) {
        env->DeleteLocalRef(cls);
        return exception;
    }
    int w = env->GetIntField(bitmap, width), h = env->GetIntField(bitmap, height);
    auto array = static_cast<jintArray>(env->GetObjectField(bitmap, field));
    int stride = env->CallIntMethod(bitmap, row);
    env->DeleteLocalRef(cls);
    if (env->ExceptionCheck()) {
        if (array)
            env->DeleteLocalRef(array);
        return exception;
    }
    if (!array || w <= 0 || h <= 0 || w > 8192 || h > 8192 ||
        static_cast<int64_t>(w) * h > 32 * 1024 * 1024 ||
        env->GetArrayLength(array) != static_cast<int64_t>(w) * h ||
        (stride != w && stride != w * 2 && stride != w * 4)) {
        if (array)
            env->DeleteLocalRef(array);
        return bad;
    }
    // Android 8.1 reserves flags; RGBA pixels use premultiplied sRGB bytes,
    // RGB_565 uses native-order 16-bit words (ANDROID_BITMAP_FORMAT_RGB_565 = 4).
    int format = stride == w ? 8 : stride == w * 2 ? 4 : 1;
    *info = {static_cast<uint32_t>(w), static_cast<uint32_t>(h), static_cast<uint32_t>(stride),
             format, 0};
    if (pixels)
        *pixels = array;
    else
        env->DeleteLocalRef(array);
    return 0;
}
unsigned char premul(unsigned value, unsigned alpha) {
    return (value * alpha + 127) / 255;
}
uint16_t pack565(uint32_t color) {
    return static_cast<uint16_t>(((color >> 19) & 31) << 11 | ((color >> 10) & 63) << 5 |
                                 ((color >> 3) & 31));
}
uint32_t unpack565(uint16_t v) {
    unsigned r = v >> 11, g = (v >> 5) & 63, b = v & 31;
    return 0xff000000u | (r << 3 | r >> 2) << 16 | (g << 2 | g >> 4) << 8 | (b << 3 | b >> 2);
}
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
} // namespace
extern "C" int AndroidBitmap_getInfo(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info) {
    if (!info)
        return bad;
    return describe(env, bitmap, info, nullptr);
}
extern "C" int AndroidBitmap_lockPixels(JNIEnv* env, jobject bitmap, void** address) {
    if (!env || !bitmap || !address)
        return bad;
    if (env->ExceptionCheck())
        return exception;
    std::lock_guard<std::mutex> guard(mutex);
    for (auto& entry : locked)
        if (env->IsSameObject(bitmap, entry->bitmap)) {
            if (entry->locks == 65535)
                return bad;
            ++entry->locks;
            *address = entry->bytes.data();
            return 0;
        }
    AndroidBitmapInfo info{};
    jintArray source = nullptr;
    int result = describe(env, bitmap, &info, &source);
    if (result)
        return result;
    jclass bitmapClass = env->GetObjectClass(bitmap);
    jmethodID synchronize =
        bitmapClass ? env->GetMethodID(bitmapClass, "shirazPrepareNativePixels", "()V") : nullptr;
    if (synchronize)
        env->CallVoidMethod(bitmap, synchronize);
    if (bitmapClass)
        env->DeleteLocalRef(bitmapClass);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(source);
        return exception;
    }
    size_t bytes = static_cast<size_t>(info.stride) * info.height;
    if (locked.size() == 64 || bytes > budget - used) {
        env->DeleteLocalRef(source);
        return allocation;
    }
    std::unique_ptr<Pixels> entry;
    try {
        entry = std::make_unique<Pixels>();
        entry->bytes.resize(bytes);
        locked.reserve(locked.size() + 1);
    } catch (const std::bad_alloc&) {
        env->DeleteLocalRef(source);
        return allocation;
    }
    entry->depth = info.format == 8 ? 1 : info.format == 4 ? 2 : 4;
    entry->bitmap = env->NewGlobalRef(bitmap);
    if (entry->bitmap)
        entry->array = static_cast<jintArray>(env->NewGlobalRef(source));
    env->DeleteLocalRef(source);
    if (!entry->array) {
        if (entry->bitmap)
            env->DeleteGlobalRef(entry->bitmap);
        return env->ExceptionCheck() ? exception : allocation;
    }
    std::array<jint, 1024> chunk;
    const size_t count = static_cast<size_t>(info.width) * info.height;
    for (size_t offset = 0; offset < count; offset += chunk.size()) {
        size_t n = std::min(chunk.size(), count - offset);
        env->GetIntArrayRegion(entry->array, offset, n, chunk.data());
        if (env->ExceptionCheck()) {
            env->DeleteGlobalRef(entry->bitmap);
            env->DeleteGlobalRef(entry->array);
            return exception;
        }
        for (size_t i = 0; i < n; ++i) {
            if (entry->depth == 1)
                entry->bytes[offset + i] = static_cast<uint32_t>(chunk[i]) >> 24;
            else if (entry->depth == 2) {
                uint16_t v = pack565(static_cast<uint32_t>(chunk[i]));
                std::memcpy(entry->bytes.data() + (offset + i) * 2, &v, 2);
            } else
                encode(static_cast<uint32_t>(chunk[i]), entry->bytes.data() + (offset + i) * 4);
        }
    }
    *address = entry->bytes.data();
    used += bytes;
    locked.push_back(std::move(entry));
    return 0;
}
extern "C" int AndroidBitmap_unlockPixels(JNIEnv* env, jobject bitmap) {
    if (!env || !bitmap)
        return bad;
    // The caller must clear its JNI exception before attempting a writeback.
    if (env->ExceptionCheck())
        return exception;
    std::lock_guard<std::mutex> guard(mutex);
    auto it = std::find_if(locked.begin(), locked.end(),
                           [&](const auto& p) { return env->IsSameObject(bitmap, p->bitmap); });
    if (it == locked.end())
        return bad;
    auto& entry = **it;
    if (--entry.locks)
        return 0;
    std::array<jint, 1024> chunk;
    const size_t count = entry.bytes.size() / entry.depth;
    for (size_t offset = 0; offset < count; offset += chunk.size()) {
        size_t n = std::min(chunk.size(), count - offset);
        env->GetIntArrayRegion(entry.array, offset, n, chunk.data());
        if (env->ExceptionCheck())
            break;
        for (size_t i = 0; i < n; ++i) {
            if (entry.depth == 1)
                chunk[i] = static_cast<uint32_t>(entry.bytes[offset + i]) << 24;
            else if (entry.depth == 2) {
                uint16_t v;
                std::memcpy(&v, entry.bytes.data() + (offset + i) * 2, 2);
                if (v != pack565(static_cast<uint32_t>(chunk[i])))
                    chunk[i] = static_cast<jint>(unpack565(v));
            } else {
                unsigned char previous[4];
                encode(static_cast<uint32_t>(chunk[i]), previous);
                const auto* p = entry.bytes.data() + (offset + i) * 4;
                // Preserve exact straight-alpha colors for unmodified pixels.
                if (!std::equal(previous, previous + 4, p))
                    chunk[i] = decode(p);
            }
        }
        env->SetIntArrayRegion(entry.array, offset, n, chunk.data());
        if (env->ExceptionCheck())
            break;
    }
    used -= entry.bytes.size();
    env->DeleteGlobalRef(entry.bitmap);
    env->DeleteGlobalRef(entry.array);
    locked.erase(it);
    return env->ExceptionCheck() ? exception : 0;
}

#include <csetjmp>
#include <cstdio>
#include <jpeglib.h>
#include <png.h>
#include <webp/encode.h>

namespace {
struct CompressJpegErr {
    jpeg_error_mgr pub;
    jmp_buf env;
};

void compress_jpeg_error_exit(j_common_ptr cinfo) {
    auto* err = reinterpret_cast<CompressJpegErr*>(cinfo->err);
    longjmp(err->env, 1);
}

struct PngSink {
    void* user = nullptr;
    AndroidBitmap_CompressWriteFunc fn = nullptr;
    bool ok = true;
};

void png_write_cb(png_structp png_ptr, png_bytep data, png_size_t length) {
    auto* sink = static_cast<PngSink*>(png_get_io_ptr(png_ptr));
    if (!sink || !sink->fn || !sink->fn(sink->user, data, length)) {
        if (sink)
            sink->ok = false;
        png_error(png_ptr, "write callback failed");
    }
}

void png_flush_cb(png_structp) {}

void to_straight_rgba(const AndroidBitmapInfo& info, const uint8_t* pixels,
                      std::vector<uint8_t>& out) {
    out.resize(static_cast<size_t>(info.width) * static_cast<size_t>(info.height) * 4);
    for (uint32_t y = 0; y < info.height; ++y) {
        const uint8_t* row = pixels + static_cast<size_t>(y) * info.stride;
        uint8_t* dst = out.data() + static_cast<size_t>(y) * info.width * 4;
        for (uint32_t x = 0; x < info.width; ++x) {
            if (info.format == 4) {
                uint16_t v = 0;
                std::memcpy(&v, row + x * 2, 2);
                uint32_t c = unpack565(v);
                dst[x * 4 + 0] = static_cast<uint8_t>((c >> 16) & 255);
                dst[x * 4 + 1] = static_cast<uint8_t>((c >> 8) & 255);
                dst[x * 4 + 2] = static_cast<uint8_t>(c & 255);
                dst[x * 4 + 3] = 255;
            } else if (info.format == 8) {
                dst[x * 4 + 0] = 0;
                dst[x * 4 + 1] = 0;
                dst[x * 4 + 2] = 0;
                dst[x * 4 + 3] = row[x];
            } else {
                uint32_t c = decode(row + x * 4);
                dst[x * 4 + 0] = static_cast<uint8_t>((c >> 16) & 255);
                dst[x * 4 + 1] = static_cast<uint8_t>((c >> 8) & 255);
                dst[x * 4 + 2] = static_cast<uint8_t>(c & 255);
                dst[x * 4 + 3] = static_cast<uint8_t>(c >> 24);
            }
        }
    }
}
} // namespace

extern "C" int32_t AndroidBitmap_getDataSpace(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != 0)
        return 0;
    return info.format == 8 ? 0 : 142671872 /* ADATASPACE_SRGB */;
}

extern "C" int AndroidBitmap_compress(const AndroidBitmapInfo* info, int32_t, const void* pixels,
                                      int32_t format, int32_t quality, void* userContext,
                                      AndroidBitmap_CompressWriteFunc fn) {
    if (!info || !pixels || !fn || info->width == 0 || info->height == 0 || info->width > 16384 ||
        info->height > 16384 || quality < 0 || quality > 100)
        return bad;
    size_t min_bpp = info->format == 8 ? 1 : info->format == 4 ? 2 : 4;
    if (info->stride < info->width * min_bpp)
        return bad;
    std::vector<uint8_t> rgba;
    try {
        to_straight_rgba(*info, static_cast<const uint8_t*>(pixels), rgba);
    } catch (const std::bad_alloc&) {
        return allocation;
    }

    if (format == ANDROID_BITMAP_COMPRESS_FORMAT_PNG) {
        png_structp png_ptr =
            png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
        if (!png_ptr)
            return allocation;
        png_infop info_ptr = png_create_info_struct(png_ptr);
        if (!info_ptr) {
            png_destroy_write_struct(&png_ptr, nullptr);
            return allocation;
        }
        PngSink sink{userContext, fn, true};
        if (setjmp(png_jmpbuf(png_ptr))) {
            png_destroy_write_struct(&png_ptr, &info_ptr);
            return -4;
        }
        png_set_write_fn(png_ptr, &sink, png_write_cb, png_flush_cb);
        png_set_IHDR(png_ptr, info_ptr, info->width, info->height, 8, PNG_COLOR_TYPE_RGBA,
                     PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);
        png_write_info(png_ptr, info_ptr);
        std::vector<png_bytep> rows(info->height);
        for (uint32_t y = 0; y < info->height; ++y)
            rows[y] = rgba.data() + static_cast<size_t>(y) * info->width * 4;
        png_write_image(png_ptr, rows.data());
        png_write_end(png_ptr, nullptr);
        png_destroy_write_struct(&png_ptr, &info_ptr);
        return sink.ok ? 0 : -4;
    }

    if (format == ANDROID_BITMAP_COMPRESS_FORMAT_JPEG) {
        jpeg_compress_struct cinfo{};
        CompressJpegErr jerr{};
        cinfo.err = jpeg_std_error(&jerr.pub);
        jerr.pub.error_exit = compress_jpeg_error_exit;
        unsigned char* out_buf = nullptr;
        unsigned long out_size = 0;
        if (setjmp(jerr.env)) {
            jpeg_destroy_compress(&cinfo);
            std::free(out_buf);
            return -4;
        }
        jpeg_create_compress(&cinfo);
        jpeg_mem_dest(&cinfo, &out_buf, &out_size);
        cinfo.image_width = info->width;
        cinfo.image_height = info->height;
        cinfo.input_components = 3;
        cinfo.in_color_space = JCS_RGB;
        jpeg_set_defaults(&cinfo);
        jpeg_set_quality(&cinfo, quality, TRUE);
        jpeg_start_compress(&cinfo, TRUE);
        std::vector<uint8_t> rgb_row(static_cast<size_t>(info->width) * 3);
        while (cinfo.next_scanline < cinfo.image_height) {
            const uint8_t* src =
                rgba.data() + static_cast<size_t>(cinfo.next_scanline) * info->width * 4;
            for (uint32_t x = 0; x < info->width; ++x) {
                rgb_row[x * 3 + 0] = src[x * 4 + 0];
                rgb_row[x * 3 + 1] = src[x * 4 + 1];
                rgb_row[x * 3 + 2] = src[x * 4 + 2];
            }
            JSAMPROW row_ptr = rgb_row.data();
            jpeg_write_scanlines(&cinfo, &row_ptr, 1);
        }
        jpeg_finish_compress(&cinfo);
        jpeg_destroy_compress(&cinfo);
        bool ok = out_buf && out_size > 0 && fn(userContext, out_buf, out_size);
        std::free(out_buf);
        return ok ? 0 : -4;
    }

    if (format == ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSY ||
        format == ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSLESS) {
        uint8_t* out_buf = nullptr;
        size_t out_size = 0;
        if (format == ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSLESS) {
            out_size = WebPEncodeLosslessRGBA(rgba.data(), static_cast<int>(info->width),
                                              static_cast<int>(info->height),
                                              static_cast<int>(info->width * 4), &out_buf);
        } else {
            out_size = WebPEncodeRGBA(
                rgba.data(), static_cast<int>(info->width), static_cast<int>(info->height),
                static_cast<int>(info->width * 4), static_cast<float>(quality), &out_buf);
        }
        if (!out_buf || out_size == 0)
            return -4;
        bool ok = fn(userContext, out_buf, out_size);
        WebPFree(out_buf);
        return ok ? 0 : -4;
    }

    return bad;
}

extern "C" int AndroidBitmap_getHardwareBuffer(JNIEnv* env, jobject bitmap,
                                               struct AHardwareBuffer** outBuffer) {
    if (!env || !bitmap || !outBuffer)
        return bad;
    *outBuffer = nullptr;
    // Shiraz Java Bitmaps are software pixel arrays; hardware-backed Bitmaps are unsupported.
    return -5 /* ANDROID_BITMAP_RESULT_UNSUPPORTED */;
}
