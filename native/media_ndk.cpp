// SPDX-License-Identifier: GPL-3.0-only
// NDK format storage and explicit codec-unavailable fallback. Java MediaCodec
// remains the OS decoder path; native callers may fall back to their own codecs.
#include "shiraz/ndk/media_container.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <map>
#include <new>
#include <string>
#include <sys/types.h>
#include <type_traits>
#include <variant>
#include <vector>

struct AMediaFormatRect {
    int32_t left = 0;
    int32_t top = 0;
    int32_t right = 0;
    int32_t bottom = 0;
};

struct AMediaFormat {
    using Value = std::variant<int32_t, int64_t, float, double, size_t, AMediaFormatRect,
                               std::string, std::vector<uint8_t>>;
    std::map<std::string, Value> values;
    std::string display;
};
struct AMediaCodec;
struct ANativeWindow;
struct AMediaCrypto;
struct AMediaCodecCryptoInfo;
struct AMediaCodecBufferInfo {
    int32_t offset, size;
    int64_t presentationTimeUs;
    uint32_t flags;
};
namespace {
constexpr int invalid = -10004;
template <class T> bool get(AMediaFormat* f, const char* key, T* out) {
    if (!f || !key || !out)
        return false;
    auto it = f->values.find(key);
    if (it == f->values.end())
        return false;
    auto* value = std::get_if<T>(&it->second);
    if (!value)
        return false;
    *out = *value;
    return true;
}
template <class T> void set(AMediaFormat* f, const char* key, T value) {
    if (!f || !key)
        return;
    try {
        f->values.insert_or_assign(key, std::move(value));
    } catch (const std::bad_alloc&) {
    }
}
} // namespace
extern "C" {
AMediaFormat* AMediaFormat_new() {
    return new (std::nothrow) AMediaFormat;
}
int AMediaFormat_delete(AMediaFormat* f) {
    delete f;
    return 0;
}
bool AMediaFormat_getInt32(AMediaFormat* f, const char* k, int32_t* v) {
    return get(f, k, v);
}
bool AMediaFormat_getInt64(AMediaFormat* f, const char* k, int64_t* v) {
    return get(f, k, v);
}
bool AMediaFormat_getFloat(AMediaFormat* f, const char* k, float* v) {
    return get(f, k, v);
}
bool AMediaFormat_getDouble(AMediaFormat* f, const char* k, double* v) {
    return get(f, k, v);
}
bool AMediaFormat_getSize(AMediaFormat* f, const char* k, size_t* v) {
    return get(f, k, v);
}
bool AMediaFormat_getRect(AMediaFormat* f, const char* k, int32_t* left, int32_t* top,
                          int32_t* right, int32_t* bottom) {
    if (!left || !top || !right || !bottom) {
        return false;
    }
    AMediaFormatRect r{};
    if (!get(f, k, &r)) {
        return false;
    }
    *left = r.left;
    *top = r.top;
    *right = r.right;
    *bottom = r.bottom;
    return true;
}
void AMediaFormat_setInt32(AMediaFormat* f, const char* k, int32_t v) {
    set(f, k, v);
}
void AMediaFormat_setInt64(AMediaFormat* f, const char* k, int64_t v) {
    set(f, k, v);
}
void AMediaFormat_setFloat(AMediaFormat* f, const char* k, float v) {
    set(f, k, v);
}
void AMediaFormat_setDouble(AMediaFormat* f, const char* k, double v) {
    set(f, k, v);
}
void AMediaFormat_setSize(AMediaFormat* f, const char* k, size_t v) {
    set(f, k, v);
}
void AMediaFormat_setRect(AMediaFormat* f, const char* k, int32_t left, int32_t top, int32_t right,
                          int32_t bottom) {
    set(f, k, AMediaFormatRect{left, top, right, bottom});
}
void AMediaFormat_setString(AMediaFormat* f, const char* k, const char* v) {
    if (v)
        try {
            set(f, k, std::string(v));
        } catch (const std::bad_alloc&) {
        }
}
void AMediaFormat_setBuffer(AMediaFormat* f, const char* k, const void* p, size_t n) {
    if ((!p && n) || n > 16 * 1024 * 1024)
        return;
    try {
        std::vector<uint8_t> v(n);
        if (n)
            std::copy_n(static_cast<const uint8_t*>(p), n, v.begin());
        set(f, k, std::move(v));
    } catch (const std::bad_alloc&) {
    }
}
bool AMediaFormat_getString(AMediaFormat* f, const char* k, const char** out) {
    if (!f || !k || !out)
        return false;
    auto it = f->values.find(k);
    if (it == f->values.end())
        return false;
    auto* v = std::get_if<std::string>(&it->second);
    if (!v)
        return false;
    *out = v->c_str();
    return true;
}
bool AMediaFormat_getBuffer(AMediaFormat* f, const char* k, void** out, size_t* size) {
    if (!f || !k || !out || !size)
        return false;
    auto it = f->values.find(k);
    if (it == f->values.end())
        return false;
    auto* v = std::get_if<std::vector<uint8_t>>(&it->second);
    if (!v)
        return false;
    *out = v->data();
    *size = v->size();
    return true;
}
media_status_t AMediaFormat_copy(AMediaFormat* to, AMediaFormat* from) {
    if (!to || !from) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    try {
        to->values = from->values;
        to->display.clear();
        return AMEDIA_OK;
    } catch (const std::bad_alloc&) {
        return AMEDIA_ERROR_UNKNOWN;
    }
}
void AMediaFormat_clear(AMediaFormat* f) {
    if (!f) {
        return;
    }
    f->values.clear();
    f->display.clear();
}
const char* AMediaFormat_toString(AMediaFormat* f) {
    if (!f)
        return nullptr;
    try {
        f->display = "{";
        for (const auto& pair : f->values) {
            if (f->display.size() > 1)
                f->display += ", ";
            f->display += pair.first + ":";
            std::visit(
                [&](const auto& v) {
                    using T = std::decay_t<decltype(v)>;
                    if constexpr (std::is_same_v<T, std::string>)
                        f->display += v;
                    else if constexpr (std::is_same_v<T, std::vector<uint8_t>>)
                        f->display += "buffer(" + std::to_string(v.size()) + ")";
                    else if constexpr (std::is_same_v<T, AMediaFormatRect>)
                        f->display += "rect(" + std::to_string(v.left) + "," +
                                      std::to_string(v.top) + "," + std::to_string(v.right) + "," +
                                      std::to_string(v.bottom) + ")";
                    else
                        f->display += std::to_string(v);
                },
                pair.second);
        }
        f->display += "}";
        return f->display.c_str();
    } catch (const std::bad_alloc&) {
        return nullptr;
    }
}
// Returning null is the NDK contract when no codec can be instantiated. Never
// report success for configuration, queueing or output without a decoder.
AMediaCodec* AMediaCodec_createCodecByName(const char*) {
    return nullptr;
}
AMediaCodec* AMediaCodec_createDecoderByType(const char*) {
    return nullptr;
}
AMediaCodec* AMediaCodec_createEncoderByType(const char*) {
    return nullptr;
}
int AMediaCodec_delete(AMediaCodec*) {
    return invalid;
}
int AMediaCodec_configure(AMediaCodec*, const AMediaFormat*, ANativeWindow*, AMediaCrypto*,
                          uint32_t) {
    return invalid;
}
int AMediaCodec_start(AMediaCodec*) {
    return invalid;
}
int AMediaCodec_stop(AMediaCodec*) {
    return invalid;
}
int AMediaCodec_flush(AMediaCodec*) {
    return invalid;
}
ssize_t AMediaCodec_dequeueInputBuffer(AMediaCodec*, int64_t) {
    return invalid;
}
ssize_t AMediaCodec_dequeueOutputBuffer(AMediaCodec*, AMediaCodecBufferInfo*, int64_t) {
    return invalid;
}
uint8_t* AMediaCodec_getInputBuffer(AMediaCodec*, size_t, size_t* size) {
    if (size)
        *size = 0;
    return nullptr;
}
uint8_t* AMediaCodec_getOutputBuffer(AMediaCodec*, size_t, size_t* size) {
    if (size)
        *size = 0;
    return nullptr;
}
AMediaFormat* AMediaCodec_getOutputFormat(AMediaCodec*) {
    return nullptr;
}
AMediaFormat* AMediaCodec_getInputFormat(AMediaCodec*) {
    return nullptr;
}
int AMediaCodec_queueInputBuffer(AMediaCodec*, size_t, size_t, size_t, uint64_t, uint32_t) {
    return invalid;
}
int AMediaCodec_queueSecureInputBuffer(AMediaCodec*, size_t, size_t, AMediaCodecCryptoInfo*,
                                       uint64_t, uint32_t) {
    return invalid;
}
int AMediaCodec_releaseOutputBuffer(AMediaCodec*, size_t, bool) {
    return invalid;
}
int AMediaCodec_releaseOutputBufferAtTime(AMediaCodec*, size_t, int64_t) {
    return invalid;
}
int AMediaCodec_setParameters(AMediaCodec*, const AMediaFormat*) {
    return invalid;
}
media_status_t AMediaCodec_setOutputSurface(AMediaCodec*, ANativeWindow*) {
    return invalid;
}
media_status_t AMediaCodec_createInputSurface(AMediaCodec*, ANativeWindow** surface) {
    if (surface) {
        *surface = nullptr;
    }
    return invalid;
}
media_status_t AMediaCodec_createPersistentInputSurface(ANativeWindow** surface) {
    if (!surface) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    *surface = nullptr;
    return AMEDIA_ERROR_UNSUPPORTED;
}
media_status_t AMediaCodec_setInputSurface(AMediaCodec*, ANativeWindow*) {
    return invalid;
}
media_status_t AMediaCodec_signalEndOfInputStream(AMediaCodec*) {
    return invalid;
}
media_status_t AMediaCodec_setAsyncNotifyCallback(AMediaCodec*, AMediaCodecOnAsyncNotifyCallback,
                                                  void*) {
    return invalid;
}
media_status_t AMediaCodec_getName(AMediaCodec*, char** out_name) {
    if (out_name) {
        *out_name = nullptr;
    }
    return invalid;
}
void AMediaCodec_releaseName(AMediaCodec*, char* name) {
    std::free(name);
}
}
