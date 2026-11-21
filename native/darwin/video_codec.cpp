// SPDX-License-Identifier: GPL-3.0-only
// macOS VideoToolbox video decoder backing shiraz.runtime.NativeVideo.
#include "shiraz/native_window.h"
#include <CoreFoundation/CoreFoundation.h>
#include <CoreMedia/CoreMedia.h>
#include <CoreVideo/CoreVideo.h>
#include <VideoToolbox/VideoToolbox.h>
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

extern "C" bool shiraz_opus_available();

namespace {

constexpr int kMaxSlots = 16;
constexpr size_t kMaxInFlight = 32;
constexpr size_t kMaxPacketBytes = 8 * 1024 * 1024;

bool traceEnabled() {
    static const bool enabled = std::getenv("SHIRAZ_TRACE_VIDEO") != nullptr;
    return enabled;
}

bool traceCounter(unsigned& count) {
    ++count;
    return traceEnabled() && (count <= 8 || count % 120 == 0);
}

void fail(JNIEnv* env, const char* message) {
    std::fprintf(stderr, "SHIRAZ_VIDEO_ERROR %s\n", message);
    if (!env)
        return;
    jclass c = env->FindClass("java/lang/IllegalStateException");
    if (c) {
        env->ThrowNew(c, message);
        env->DeleteLocalRef(c);
    }
}

void registerSupplementalDecoders() {
    static std::once_flag once;
    std::call_once(once, [] {
        VTRegisterSupplementalVideoDecoderIfAvailable(kCMVideoCodecType_VP9);
        VTRegisterSupplementalVideoDecoderIfAvailable(kCMVideoCodecType_AV1);
    });
}

CFDictionaryRef makeDestinationAttributes() {
    int32_t pixelFormat = kCVPixelFormatType_32BGRA;
    CFNumberRef formatNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, &pixelFormat);
    CFDictionaryRef ioSurfaceProps =
        CFDictionaryCreate(kCFAllocatorDefault, nullptr, nullptr, 0, &kCFTypeDictionaryKeyCallBacks,
                           &kCFTypeDictionaryValueCallBacks);
    const void* keys[2] = {kCVPixelBufferPixelFormatTypeKey, kCVPixelBufferIOSurfacePropertiesKey};
    const void* values[2] = {formatNum, ioSurfaceProps};
    CFDictionaryRef attrs =
        CFDictionaryCreate(kCFAllocatorDefault, keys, values, 2, &kCFTypeDictionaryKeyCallBacks,
                           &kCFTypeDictionaryValueCallBacks);
    CFRelease(formatNum);
    CFRelease(ioSurfaceProps);
    return attrs;
}

bool probeSimpleCodec(CMVideoCodecType codecType) {
    registerSupplementalDecoders();
    CMVideoFormatDescriptionRef desc = nullptr;
    if (CMVideoFormatDescriptionCreate(kCFAllocatorDefault, codecType, 64, 64, nullptr, &desc) !=
            noErr ||
        !desc) {
        return false;
    }
    CFDictionaryRef dstAttrs = makeDestinationAttributes();
    VTDecompressionSessionRef session = nullptr;
    OSStatus status = VTDecompressionSessionCreate(kCFAllocatorDefault, desc, nullptr, dstAttrs,
                                                   nullptr, &session);
    if (session) {
        VTDecompressionSessionInvalidate(session);
        CFRelease(session);
    }
    CFRelease(dstAttrs);
    CFRelease(desc);
    return status == noErr;
}

bool codecAvailable(const char* mime) {
    registerSupplementalDecoders();
    if (std::strcmp(mime, "video/avc") == 0)
        return true;
    if (std::strcmp(mime, "video/hevc") == 0)
        return true;
    if (std::strcmp(mime, "video/x-vnd.on2.vp9") == 0) {
        static const bool ok = probeSimpleCodec(kCMVideoCodecType_VP9);
        return ok;
    }
    if (std::strcmp(mime, "video/av01") == 0) {
        static const bool ok = VTIsHardwareDecodeSupported(kCMVideoCodecType_AV1) &&
                               probeSimpleCodec(kCMVideoCodecType_AV1);
        return ok;
    }
    return false;
}

CMVideoCodecType codecTypeForMime(const std::string& mime) {
    if (mime == "video/avc")
        return kCMVideoCodecType_H264;
    if (mime == "video/hevc")
        return kCMVideoCodecType_HEVC;
    if (mime == "video/x-vnd.on2.vp9")
        return kCMVideoCodecType_VP9;
    if (mime == "video/av01")
        return kCMVideoCodecType_AV1;
    return 0;
}

// Returns true if the buffer begins with an Annex-B 3-byte or 4-byte start code.
bool hasAnnexBStartCode(const uint8_t* data, size_t size) {
    if (size >= 4 && data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1)
        return true;
    if (size >= 3 && data[0] == 0 && data[1] == 0 && data[2] == 1)
        return true;
    return false;
}

struct NalSlice {
    const uint8_t* data;
    size_t size;
};

// Split an Annex-B byte stream into individual NAL payloads (without start codes).
std::vector<NalSlice> splitAnnexB(const uint8_t* data, size_t size) {
    std::vector<NalSlice> nals;
    size_t i = 0;
    auto findStart = [&](size_t pos, size_t& scLen) -> size_t {
        for (size_t p = pos; p + 2 < size; ++p) {
            if (data[p] == 0 && data[p + 1] == 0) {
                if (data[p + 2] == 1) {
                    scLen = 3;
                    return p;
                }
                if (p + 3 < size && data[p + 2] == 0 && data[p + 3] == 1) {
                    scLen = 4;
                    return p;
                }
            }
        }
        scLen = 0;
        return size;
    };
    size_t scLen = 0;
    size_t start = findStart(0, scLen);
    while (start < size) {
        i = start + scLen;
        size_t nextScLen = 0;
        size_t next = findStart(i, nextScLen);
        size_t end = next;
        while (end > i && data[end - 1] == 0)
            --end;
        if (end > i)
            nals.push_back({data + i, end - i});
        start = next;
        scLen = nextScLen;
    }
    return nals;
}

void appendLengthPrefixedNal(std::vector<uint8_t>& out, const uint8_t* data, size_t size) {
    uint32_t len = static_cast<uint32_t>(size);
    out.push_back(static_cast<uint8_t>((len >> 24) & 0xff));
    out.push_back(static_cast<uint8_t>((len >> 16) & 0xff));
    out.push_back(static_cast<uint8_t>((len >> 8) & 0xff));
    out.push_back(static_cast<uint8_t>(len & 0xff));
    out.insert(out.end(), data, data + size);
}

struct DecodedFrame {
    CVPixelBufferRef buffer = nullptr;
    int64_t ptsUs = 0;
};

struct Decoder {
    std::string mime;
    CMVideoCodecType codecType = 0;
    int width = 0;
    int height = 0;

    CMVideoFormatDescriptionRef formatDesc = nullptr;
    VTDecompressionSessionRef session = nullptr;

    // Stored parameter sets for Annex-B H.264 / HEVC streams.
    std::vector<uint8_t> vps;
    std::vector<uint8_t> sps;
    std::vector<uint8_t> pps;
    bool usingRawBoxConfig = false;

    std::mutex mutex;
    std::deque<DecodedFrame> ready;
    DecodedFrame held[kMaxSlots]{};
    size_t inFlight = 0;
    bool eosReceived = false;
    bool eosSent = false;
    std::string errorMessage;

    unsigned published = 0;
    unsigned queued = 0;
    unsigned polled = 0;
    unsigned released = 0;
    std::vector<uint32_t> pixels;

    void destroySession() {
        if (session) {
            VTDecompressionSessionWaitForAsynchronousFrames(session);
            VTDecompressionSessionInvalidate(session);
            CFRelease(session);
            session = nullptr;
        }
        if (formatDesc) {
            CFRelease(formatDesc);
            formatDesc = nullptr;
        }
    }

    ~Decoder() {
        std::fprintf(stderr, "SHIRAZ_VIDEO_SUMMARY input=%u output=%u released=%u rendered=%u\n",
                     queued, polled, released, published);
        destroySession();
        std::lock_guard<std::mutex> lock(mutex);
        for (auto& f : ready) {
            if (f.buffer)
                CVPixelBufferRelease(f.buffer);
        }
        ready.clear();
        for (auto& f : held) {
            if (f.buffer) {
                CVPixelBufferRelease(f.buffer);
                f.buffer = nullptr;
            }
        }
    }
};

void decompressionCallback(void* decompressionOutputRefCon, void*, OSStatus status,
                           VTDecodeInfoFlags infoFlags, CVImageBufferRef imageBuffer,
                           CMTime presentationTimeStamp, CMTime) {
    auto* d = static_cast<Decoder*>(decompressionOutputRefCon);
    if (!d)
        return;
    std::lock_guard<std::mutex> lock(d->mutex);
    if (d->inFlight > 0)
        --d->inFlight;
    if (status != noErr) {
        if (d->errorMessage.empty()) {
            d->errorMessage =
                "VideoToolbox decode failed (OSStatus=" + std::to_string(int(status)) + ")";
        }
        return;
    }
    if (!imageBuffer || (infoFlags & kVTDecodeInfo_FrameDropped))
        return;
    int64_t ptsUs = 0;
    if (CMTIME_IS_NUMERIC(presentationTimeStamp) && presentationTimeStamp.timescale > 0) {
        ptsUs = (presentationTimeStamp.value * 1000000LL) / presentationTimeStamp.timescale;
    }
    CVPixelBufferRetain(imageBuffer);
    d->ready.push_back({imageBuffer, ptsUs});
}

bool createSessionForFormatDesc(Decoder* d, CMVideoFormatDescriptionRef desc, std::string& err) {
    CFDictionaryRef dstAttrs = makeDestinationAttributes();
    VTDecompressionOutputCallbackRecord cb{};
    cb.decompressionOutputCallback = decompressionCallback;
    cb.decompressionOutputRefCon = d;

    VTDecompressionSessionRef newSession = nullptr;
    OSStatus status = VTDecompressionSessionCreate(kCFAllocatorDefault, desc, nullptr, dstAttrs,
                                                   &cb, &newSession);
    CFRelease(dstAttrs);
    if (status != noErr || !newSession) {
        err = "VTDecompressionSessionCreate failed (OSStatus=" + std::to_string(int(status)) + ")";
        return false;
    }
    d->destroySession();
    d->formatDesc = desc;
    d->session = newSession;

    if (traceEnabled()) {
        Boolean usingHw = false;
        CFBooleanRef hwProp = nullptr;
        if (VTSessionCopyProperty(newSession,
                                  kVTDecompressionPropertyKey_UsingHardwareAcceleratedVideoDecoder,
                                  kCFAllocatorDefault, &hwProp) == noErr &&
            hwProp) {
            usingHw = CFBooleanGetValue(hwProp);
            CFRelease(hwProp);
        }
        std::fprintf(stderr, "SHIRAZ_VIDEO_ACTIVE decoder=VideoToolbox mime=%s hardware=%d\n",
                     d->mime.c_str(), usingHw ? 1 : 0);
    }
    return true;
}

bool rebuildH264Session(Decoder* d, std::string& err) {
    if (d->sps.empty() || d->pps.empty())
        return true; // Wait until both SPS and PPS are present.
    const uint8_t* ptrs[2] = {d->sps.data(), d->pps.data()};
    const size_t sizes[2] = {d->sps.size(), d->pps.size()};
    CMVideoFormatDescriptionRef desc = nullptr;
    OSStatus status = CMVideoFormatDescriptionCreateFromH264ParameterSets(kCFAllocatorDefault, 2,
                                                                          ptrs, sizes, 4, &desc);
    if (status != noErr || !desc) {
        err = "Invalid H.264 parameter sets (OSStatus=" + std::to_string(int(status)) + ")";
        return false;
    }
    if (!createSessionForFormatDesc(d, desc, err)) {
        CFRelease(desc);
        return false;
    }
    return true;
}

bool rebuildHevcSession(Decoder* d, std::string& err) {
    if (d->vps.empty() || d->sps.empty() || d->pps.empty())
        return true; // Wait until VPS, SPS, and PPS are present.
    const uint8_t* ptrs[3] = {d->vps.data(), d->sps.data(), d->pps.data()};
    const size_t sizes[3] = {d->vps.size(), d->sps.size(), d->pps.size()};
    CMVideoFormatDescriptionRef desc = nullptr;
    OSStatus status = CMVideoFormatDescriptionCreateFromHEVCParameterSets(
        kCFAllocatorDefault, 3, ptrs, sizes, 4, nullptr, &desc);
    if (status != noErr || !desc) {
        err = "Invalid HEVC parameter sets (OSStatus=" + std::to_string(int(status)) + ")";
        return false;
    }
    if (!createSessionForFormatDesc(d, desc, err)) {
        CFRelease(desc);
        return false;
    }
    return true;
}

bool createSessionFromBoxConfig(Decoder* d, const char* atomName, const uint8_t* data, size_t size,
                                std::string& err) {
    CFStringRef key =
        CFStringCreateWithCString(kCFAllocatorDefault, atomName, kCFStringEncodingASCII);
    CFDataRef atomData = CFDataCreate(kCFAllocatorDefault, data, static_cast<CFIndex>(size));
    const void* atomKeys[1] = {key};
    const void* atomVals[1] = {atomData};
    CFDictionaryRef atoms =
        CFDictionaryCreate(kCFAllocatorDefault, atomKeys, atomVals, 1,
                           &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
    const void* extKeys[1] = {kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms};
    const void* extVals[1] = {atoms};
    CFDictionaryRef exts =
        CFDictionaryCreate(kCFAllocatorDefault, extKeys, extVals, 1, &kCFTypeDictionaryKeyCallBacks,
                           &kCFTypeDictionaryValueCallBacks);
    CMVideoFormatDescriptionRef desc = nullptr;
    OSStatus status = CMVideoFormatDescriptionCreate(kCFAllocatorDefault, d->codecType, d->width,
                                                     d->height, exts, &desc);
    CFRelease(exts);
    CFRelease(atoms);
    CFRelease(atomData);
    CFRelease(key);
    if (status != noErr || !desc) {
        err = "Invalid codec box config (OSStatus=" + std::to_string(int(status)) + ")";
        return false;
    }
    if (!createSessionForFormatDesc(d, desc, err)) {
        CFRelease(desc);
        return false;
    }
    d->usingRawBoxConfig = true;
    return true;
}

bool ingestConfig(Decoder* d, const uint8_t* data, size_t size, std::string& err) {
    if (!data || size == 0)
        return true;
    if (d->codecType == kCMVideoCodecType_H264) {
        if (hasAnnexBStartCode(data, size)) {
            bool changed = false;
            for (const auto& nal : splitAnnexB(data, size)) {
                uint8_t nalType = nal.data[0] & 0x1f;
                if (nalType == 7) {
                    d->sps.assign(nal.data, nal.data + nal.size);
                    changed = true;
                } else if (nalType == 8) {
                    d->pps.assign(nal.data, nal.data + nal.size);
                    changed = true;
                }
            }
            if (changed)
                return rebuildH264Session(d, err);
            return true;
        }
        if (size >= 7 && data[0] == 1)
            return createSessionFromBoxConfig(d, "avcC", data, size, err);
        return true;
    }
    if (d->codecType == kCMVideoCodecType_HEVC) {
        if (hasAnnexBStartCode(data, size)) {
            bool changed = false;
            for (const auto& nal : splitAnnexB(data, size)) {
                if (nal.size < 2)
                    continue;
                uint8_t nalType = (nal.data[0] >> 1) & 0x3f;
                if (nalType == 32) {
                    d->vps.assign(nal.data, nal.data + nal.size);
                    changed = true;
                } else if (nalType == 33) {
                    d->sps.assign(nal.data, nal.data + nal.size);
                    changed = true;
                } else if (nalType == 34) {
                    d->pps.assign(nal.data, nal.data + nal.size);
                    changed = true;
                }
            }
            if (changed)
                return rebuildHevcSession(d, err);
            return true;
        }
        if (size >= 23 && data[0] == 1)
            return createSessionFromBoxConfig(d, "hvcC", data, size, err);
        return true;
    }
    return true;
}

bool checkPendingError(JNIEnv* env, Decoder* d) {
    std::string err;
    {
        std::lock_guard<std::mutex> lock(d->mutex);
        if (!d->errorMessage.empty()) {
            err = d->errorMessage;
            d->errorMessage.clear();
        }
    }
    if (!err.empty()) {
        fail(env, err.c_str());
        return true;
    }
    return false;
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL Java_shiraz_runtime_NativeVideo_nativeCodecs(JNIEnv* env,
                                                                                       jclass) {
    registerSupplementalDecoders();
    std::vector<const char*> supported;
    for (const char* mime : {"video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01"}) {
        if (codecAvailable(mime))
            supported.push_back(mime);
    }
    if (shiraz_opus_available())
        supported.push_back("audio/opus");
    if (traceEnabled()) {
        for (const char* mime : supported)
            std::fprintf(stderr, "SHIRAZ_SYSTEM_CODEC mime=%s\n", mime);
    }
    jclass str = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(supported.size()), str, nullptr);
    env->DeleteLocalRef(str);
    for (size_t i = 0; i < supported.size(); ++i) {
        jstring s = env->NewStringUTF(supported[i]);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return result;
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeVideo_open(JNIEnv* env, jclass,
                                                                        jstring mime, jint width,
                                                                        jint height,
                                                                        jbyteArray config) {
    registerSupplementalDecoders();
    if (width < 1 || height < 1 || width > 8192 || height > 8192 ||
        int64_t(width) * height > 16 * 1024 * 1024) {
        fail(env, "Video dimensions exceed supported bounds");
        return 0;
    }
    if (!mime) {
        fail(env, "Video MIME type is null");
        return 0;
    }
    const char* text = env->GetStringUTFChars(mime, nullptr);
    if (!text)
        return 0;
    std::string type = text;
    env->ReleaseStringUTFChars(mime, text);

    CMVideoCodecType codecType = codecTypeForMime(type);
    if (!codecType || !codecAvailable(type.c_str())) {
        fail(env, "No VideoToolbox decoder for video format");
        return 0;
    }

    auto* d = new Decoder;
    d->mime = type;
    d->codecType = codecType;
    d->width = width;
    d->height = height;

    std::fprintf(stderr, "SHIRAZ_VIDEO preferred_decoder=VideoToolbox mime=%s\n", type.c_str());

    std::string err;
    if (codecType == kCMVideoCodecType_VP9 || codecType == kCMVideoCodecType_AV1) {
        CMVideoFormatDescriptionRef desc = nullptr;
        OSStatus status = CMVideoFormatDescriptionCreate(kCFAllocatorDefault, codecType, width,
                                                         height, nullptr, &desc);
        if (status != noErr || !desc || !createSessionForFormatDesc(d, desc, err)) {
            if (desc && d->formatDesc != desc)
                CFRelease(desc);
            fail(env, err.empty() ? "VideoToolbox session creation failed" : err.c_str());
            delete d;
            return 0;
        }
    } else if (config && env->GetArrayLength(config) > 0) {
        jsize size = env->GetArrayLength(config);
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        env->GetByteArrayRegion(config, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck() || !ingestConfig(d, bytes.data(), bytes.size(), err)) {
            if (!err.empty())
                fail(env, err.c_str());
            delete d;
            return 0;
        }
    }

    return reinterpret_cast<jlong>(d);
}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeVideo_canQueue(JNIEnv* env, jclass,
                                                                               jlong handle) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    if (!d)
        return JNI_FALSE;
    if (checkPendingError(env, d))
        return JNI_FALSE;
    std::lock_guard<std::mutex> lock(d->mutex);
    return (d->inFlight + d->ready.size() < kMaxInFlight) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_queue(
    JNIEnv* env, jclass, jlong handle, jbyteArray bytes, jlong timeUs, jboolean eos) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    if (!d) {
        fail(env, "Invalid video decoder handle");
        return;
    }
    if (checkPendingError(env, d))
        return;

    jsize size = bytes ? env->GetArrayLength(bytes) : 0;
    if (size > 0) {
        if (static_cast<size_t>(size) > kMaxPacketBytes) {
            fail(env, "Video packet exceeds input limit");
            return;
        }
        if (traceCounter(d->queued)) {
            std::fprintf(stderr, "SHIRAZ_VIDEO_INPUT packet=%u bytes=%d pts=%lld\n", d->queued,
                         int(size), static_cast<long long>(timeUs));
        }

        std::vector<uint8_t> raw(static_cast<size_t>(size));
        env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(raw.data()));
        if (env->ExceptionCheck())
            return;

        std::vector<uint8_t> payload;
        const uint8_t* sampleBytes = raw.data();
        size_t sampleSize = raw.size();
        std::string err;

        if (d->codecType == kCMVideoCodecType_H264 || d->codecType == kCMVideoCodecType_HEVC) {
            if (hasAnnexBStartCode(raw.data(), raw.size())) {
                bool paramChanged = false;
                auto nals = splitAnnexB(raw.data(), raw.size());
                payload.reserve(raw.size() + nals.size() * 4);
                for (const auto& nal : nals) {
                    if (d->codecType == kCMVideoCodecType_H264) {
                        uint8_t nalType = nal.data[0] & 0x1f;
                        if (nalType == 7) {
                            if (d->sps.size() != nal.size ||
                                std::memcmp(d->sps.data(), nal.data, nal.size) != 0) {
                                d->sps.assign(nal.data, nal.data + nal.size);
                                paramChanged = true;
                            }
                            continue;
                        }
                        if (nalType == 8) {
                            if (d->pps.size() != nal.size ||
                                std::memcmp(d->pps.data(), nal.data, nal.size) != 0) {
                                d->pps.assign(nal.data, nal.data + nal.size);
                                paramChanged = true;
                            }
                            continue;
                        }
                    } else {
                        if (nal.size >= 2) {
                            uint8_t nalType = (nal.data[0] >> 1) & 0x3f;
                            if (nalType == 32) {
                                if (d->vps.size() != nal.size ||
                                    std::memcmp(d->vps.data(), nal.data, nal.size) != 0) {
                                    d->vps.assign(nal.data, nal.data + nal.size);
                                    paramChanged = true;
                                }
                                continue;
                            }
                            if (nalType == 33) {
                                if (d->sps.size() != nal.size ||
                                    std::memcmp(d->sps.data(), nal.data, nal.size) != 0) {
                                    d->sps.assign(nal.data, nal.data + nal.size);
                                    paramChanged = true;
                                }
                                continue;
                            }
                            if (nalType == 34) {
                                if (d->pps.size() != nal.size ||
                                    std::memcmp(d->pps.data(), nal.data, nal.size) != 0) {
                                    d->pps.assign(nal.data, nal.data + nal.size);
                                    paramChanged = true;
                                }
                                continue;
                            }
                        }
                    }
                    appendLengthPrefixedNal(payload, nal.data, nal.size);
                }

                if (paramChanged || !d->session) {
                    bool ok = (d->codecType == kCMVideoCodecType_H264) ? rebuildH264Session(d, err)
                                                                       : rebuildHevcSession(d, err);
                    if (!ok) {
                        fail(env, err.c_str());
                        return;
                    }
                }
                if (payload.empty()) {
                    sampleBytes = nullptr;
                    sampleSize = 0;
                } else {
                    sampleBytes = payload.data();
                    sampleSize = payload.size();
                }
            }
        }

        if (sampleSize > 0) {
            if (!d->session || !d->formatDesc) {
                fail(env, "VideoToolbox decoder missing codec parameter sets");
                return;
            }
            CMBlockBufferRef block = nullptr;
            OSStatus status = CMBlockBufferCreateWithMemoryBlock(kCFAllocatorDefault, nullptr,
                                                                 sampleSize, kCFAllocatorDefault,
                                                                 nullptr, 0, sampleSize, 0, &block);
            if (status != noErr || !block) {
                fail(env, "CMBlockBuffer allocation failed");
                return;
            }
            status = CMBlockBufferReplaceDataBytes(sampleBytes, block, 0, sampleSize);
            if (status != noErr) {
                CFRelease(block);
                fail(env, "CMBlockBuffer copy failed");
                return;
            }
            CMSampleTimingInfo timing{};
            timing.duration = kCMTimeInvalid;
            timing.presentationTimeStamp =
                timeUs < 0 ? kCMTimeInvalid : CMTimeMake(timeUs, 1000000);
            timing.decodeTimeStamp = kCMTimeInvalid;

            CMSampleBufferRef sample = nullptr;
            status = CMSampleBufferCreateReady(kCFAllocatorDefault, block, d->formatDesc, 1, 1,
                                               &timing, 1, &sampleSize, &sample);
            CFRelease(block);
            if (status != noErr || !sample) {
                fail(env, "CMSampleBuffer creation failed");
                return;
            }

            {
                std::lock_guard<std::mutex> lock(d->mutex);
                ++d->inFlight;
            }
            VTDecodeFrameFlags flags = kVTDecodeFrame_EnableAsynchronousDecompression;
            VTDecodeInfoFlags infoFlags = 0;
            status =
                VTDecompressionSessionDecodeFrame(d->session, sample, flags, nullptr, &infoFlags);
            CFRelease(sample);
            if (status != noErr) {
                {
                    std::lock_guard<std::mutex> lock(d->mutex);
                    if (d->inFlight > 0)
                        --d->inFlight;
                }
                std::string msg =
                    "VideoToolbox rejected input (OSStatus=" + std::to_string(int(status)) + ")";
                fail(env, msg.c_str());
                return;
            }
        }
    }

    if (eos) {
        if (d->session)
            VTDecompressionSessionWaitForAsynchronousFrames(d->session);
        std::lock_guard<std::mutex> lock(d->mutex);
        d->eosReceived = true;
    }
}

extern "C" JNIEXPORT jlongArray JNICALL Java_shiraz_runtime_NativeVideo_poll(JNIEnv* env, jclass,
                                                                             jlong handle) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    if (!d)
        return nullptr;
    if (checkPendingError(env, d))
        return nullptr;

    jlong values[4]{};
    {
        std::lock_guard<std::mutex> lock(d->mutex);
        int slot = 0;
        while (slot < kMaxSlots && d->held[slot].buffer != nullptr)
            ++slot;
        if (slot == kMaxSlots)
            return nullptr;

        if (!d->ready.empty()) {
            DecodedFrame frame = d->ready.front();
            d->ready.pop_front();
            d->held[slot] = frame;
            values[0] = static_cast<jlong>(CVPixelBufferGetWidth(frame.buffer));
            values[1] = static_cast<jlong>(CVPixelBufferGetHeight(frame.buffer));
            values[2] = static_cast<jlong>(frame.ptsUs);
            values[3] = slot;
            if (traceCounter(d->polled)) {
                std::fprintf(stderr, "SHIRAZ_VIDEO_OUTPUT frame=%u pts=%lld slot=%d\n", d->polled,
                             static_cast<long long>(values[2]), slot);
            }
        } else if (d->eosReceived && d->inFlight == 0 && !d->eosSent) {
            d->eosSent = true;
            values[0] = -1;
            values[1] = -1;
            values[2] = 0;
            values[3] = kMaxSlots;
        } else {
            return nullptr;
        }
    }

    jlongArray result = env->NewLongArray(4);
    if (result)
        env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_releaseFrame(
    JNIEnv* env, jclass, jlong handle, jint slot, jlong window, jboolean render) {
    auto* d = reinterpret_cast<Decoder*>(handle);
    if (!d || slot < 0 || slot >= kMaxSlots) {
        fail(env, "No decoded frame to release");
        return;
    }

    CVPixelBufferRef buf = nullptr;
    {
        std::lock_guard<std::mutex> lock(d->mutex);
        buf = d->held[slot].buffer;
        if (!buf) {
            fail(env, "No decoded frame to release");
            return;
        }
        d->held[slot].buffer = nullptr;
    }

    if (traceCounter(d->released)) {
        std::fprintf(stderr, "SHIRAZ_VIDEO_RELEASE frame=%u render=%d\n", d->released, int(render));
    }

    if (render) {
        int w = static_cast<int>(CVPixelBufferGetWidth(buf));
        int h = static_cast<int>(CVPixelBufferGetHeight(buf));
        if (w < 1 || h < 1 || w > 8192 || h > 8192 || int64_t(w) * h > 16 * 1024 * 1024) {
            CVPixelBufferRelease(buf);
            fail(env, "Invalid decoded video frame dimensions");
            return;
        }
        if (CVPixelBufferLockBaseAddress(buf, kCVPixelBufferLock_ReadOnly) != kCVReturnSuccess) {
            CVPixelBufferRelease(buf);
            fail(env, "Cannot lock decoded CVPixelBuffer");
            return;
        }
        const auto* src = static_cast<const uint8_t*>(CVPixelBufferGetBaseAddress(buf));
        size_t stride = CVPixelBufferGetBytesPerRow(buf);
        d->pixels.resize(size_t(w) * h);
        for (int y = 0; y < h; ++y) {
            std::memcpy(d->pixels.data() + size_t(y) * w, src + size_t(y) * stride, size_t(w) * 4);
        }
        CVPixelBufferUnlockBaseAddress(buf, kCVPixelBufferLock_ReadOnly);

        auto* native = reinterpret_cast<ANativeWindow*>(window);
        if (!native ||
            ((ANativeWindow_getWidth(native) != w || ANativeWindow_getHeight(native) != h) &&
             ANativeWindow_setBuffersGeometry(native, w, h, 1)) ||
            shiraz_window_post_argb(native, d->pixels.data(), d->pixels.size())) {
            CVPixelBufferRelease(buf);
            fail(env, "Cannot publish decoded video frame");
            return;
        }
        if (traceCounter(d->published)) {
            std::fprintf(stderr, "SHIRAZ_VIDEO_FRAME published=%u hash=%08x\n", d->published,
                         d->pixels.empty() ? 0u : d->pixels[d->pixels.size() / 2]);
        }
    }

    CVPixelBufferRelease(buf);
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_close(JNIEnv*, jclass,
                                                                        jlong handle) {
    delete reinterpret_cast<Decoder*>(handle);
}
