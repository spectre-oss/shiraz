// SPDX-License-Identifier: GPL-3.0-only
// End-to-end macOS VideoToolbox (H.264/HEVC) and AudioToolbox Opus verification
// against the linked build/macos/libshiraz-video.so dylib.
#include "shiraz/native_window.h"
#include <AudioToolbox/AudioToolbox.h>
#include <CoreFoundation/CoreFoundation.h>
#include <CoreMedia/CoreMedia.h>
#include <CoreVideo/CoreVideo.h>
#include <VideoToolbox/VideoToolbox.h>
#include <dlfcn.h>
#include <jni.h>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {

// Minimal in-process JNIEnv harness covering the JNI entry points used by libshiraz-video.so.
struct FakeObj {
    enum Kind { kClass, kString, kByteArray, kLongArray, kObjectArray } kind;
    std::string text;
    std::vector<int8_t> bytes;
    std::vector<jlong> longs;
    std::vector<FakeObj*> objs;
};

struct Harness {
    JNINativeInterface_ table{};
    JNIEnv env{};
    std::vector<FakeObj*> allocs;
    std::string lastException;

    Harness() {
        env.functions = &table;
        table.FindClass = [](JNIEnv* e, const char* name) -> jclass {
            auto* h = from(e);
            auto* o = h->make(FakeObj::kClass);
            o->text = name ? name : "";
            return reinterpret_cast<jclass>(o);
        };
        table.ThrowNew = [](JNIEnv* e, jclass, const char* msg) -> jint {
            from(e)->lastException = msg ? msg : "error";
            return 0;
        };
        table.DeleteLocalRef = [](JNIEnv*, jobject) {};
        table.ExceptionCheck = [](JNIEnv* e) -> jboolean {
            return from(e)->lastException.empty() ? JNI_FALSE : JNI_TRUE;
        };
        table.GetStringUTFChars = [](JNIEnv*, jstring s, jboolean* isCopy) -> const char* {
            if (isCopy)
                *isCopy = JNI_FALSE;
            return reinterpret_cast<FakeObj*>(s)->text.c_str();
        };
        table.ReleaseStringUTFChars = [](JNIEnv*, jstring, const char*) {};
        table.NewStringUTF = [](JNIEnv* e, const char* utf) -> jstring {
            auto* o = from(e)->make(FakeObj::kString);
            o->text = utf ? utf : "";
            return reinterpret_cast<jstring>(o);
        };
        table.NewObjectArray = [](JNIEnv* e, jsize len, jclass, jobject init) -> jobjectArray {
            auto* o = from(e)->make(FakeObj::kObjectArray);
            o->objs.assign(static_cast<size_t>(len), reinterpret_cast<FakeObj*>(init));
            return reinterpret_cast<jobjectArray>(o);
        };
        table.SetObjectArrayElement = [](JNIEnv*, jobjectArray arr, jsize i, jobject val) {
            reinterpret_cast<FakeObj*>(arr)->objs[static_cast<size_t>(i)] =
                reinterpret_cast<FakeObj*>(val);
        };
        table.GetArrayLength = [](JNIEnv*, jarray arr) -> jsize {
            auto* o = reinterpret_cast<FakeObj*>(arr);
            if (o->kind == FakeObj::kByteArray)
                return static_cast<jsize>(o->bytes.size());
            if (o->kind == FakeObj::kLongArray)
                return static_cast<jsize>(o->longs.size());
            return static_cast<jsize>(o->objs.size());
        };
        table.GetByteArrayRegion = [](JNIEnv*, jbyteArray arr, jsize start, jsize len, jbyte* buf) {
            auto* o = reinterpret_cast<FakeObj*>(arr);
            std::memcpy(buf, o->bytes.data() + start, static_cast<size_t>(len));
        };
        table.NewByteArray = [](JNIEnv* e, jsize len) -> jbyteArray {
            auto* o = from(e)->make(FakeObj::kByteArray);
            o->bytes.resize(static_cast<size_t>(len));
            return reinterpret_cast<jbyteArray>(o);
        };
        table.SetByteArrayRegion = [](JNIEnv*, jbyteArray arr, jsize start, jsize len,
                                      const jbyte* buf) {
            auto* o = reinterpret_cast<FakeObj*>(arr);
            std::memcpy(o->bytes.data() + start, buf, static_cast<size_t>(len));
        };
        table.NewLongArray = [](JNIEnv* e, jsize len) -> jlongArray {
            auto* o = from(e)->make(FakeObj::kLongArray);
            o->longs.resize(static_cast<size_t>(len));
            return reinterpret_cast<jlongArray>(o);
        };
        table.SetLongArrayRegion = [](JNIEnv*, jlongArray arr, jsize start, jsize len,
                                      const jlong* buf) {
            auto* o = reinterpret_cast<FakeObj*>(arr);
            std::memcpy(o->longs.data() + start, buf, static_cast<size_t>(len) * sizeof(jlong));
        };
    }

    ~Harness() {
        for (auto* o : allocs)
            delete o;
    }

    static Harness* from(JNIEnv* e) {
        return reinterpret_cast<Harness*>(reinterpret_cast<char*>(e) - offsetof(Harness, env));
    }

    FakeObj* make(FakeObj::Kind k) {
        auto* o = new FakeObj{k, {}, {}, {}, {}};
        allocs.push_back(o);
        return o;
    }

    jstring makeString(const std::string& s) {
        auto* o = make(FakeObj::kString);
        o->text = s;
        return reinterpret_cast<jstring>(o);
    }

    jbyteArray makeBytes(const std::vector<uint8_t>& data) {
        auto* o = make(FakeObj::kByteArray);
        o->bytes.resize(data.size());
        if (!data.empty())
            std::memcpy(o->bytes.data(), data.data(), data.size());
        return reinterpret_cast<jbyteArray>(o);
    }

    void clearException() {
        lastException.clear();
    }
};

void require(bool cond, const char* msg) {
    if (!cond)
        throw std::runtime_error(msg);
}

struct EncodedVideo {
    std::vector<uint8_t> configAnnexB;
    std::vector<std::vector<uint8_t>> framesAnnexB;
};

void appendStartCode(std::vector<uint8_t>& dst) {
    dst.push_back(0);
    dst.push_back(0);
    dst.push_back(0);
    dst.push_back(1);
}

EncodedVideo encodeSolidFrames(CMVideoCodecType codec, int width, int height, uint8_t b, uint8_t g,
                               uint8_t r, int count) {
    struct Ctx {
        CMVideoCodecType codec;
        EncodedVideo out;
    } ctx{codec, {}};

    auto cb = [](void* refCon, void*, OSStatus status, VTEncodeInfoFlags,
                 CMSampleBufferRef sample) {
        if (status != noErr || !sample)
            return;
        auto* c = static_cast<Ctx*>(refCon);
        CMFormatDescriptionRef fmt = CMSampleBufferGetFormatDescription(sample);
        if (c->out.configAnnexB.empty() && fmt) {
            size_t count = 0;
            if (c->codec == kCMVideoCodecType_H264) {
                CMVideoFormatDescriptionGetH264ParameterSetAtIndex(fmt, 0, nullptr, nullptr, &count,
                                                                   nullptr);
                for (size_t i = 0; i < count; ++i) {
                    const uint8_t* ptr = nullptr;
                    size_t len = 0;
                    if (CMVideoFormatDescriptionGetH264ParameterSetAtIndex(
                            fmt, i, &ptr, &len, nullptr, nullptr) == noErr) {
                        appendStartCode(c->out.configAnnexB);
                        c->out.configAnnexB.insert(c->out.configAnnexB.end(), ptr, ptr + len);
                    }
                }
            } else if (c->codec == kCMVideoCodecType_HEVC) {
                CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(fmt, 0, nullptr, nullptr, &count,
                                                                   nullptr);
                for (size_t i = 0; i < count; ++i) {
                    const uint8_t* ptr = nullptr;
                    size_t len = 0;
                    if (CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
                            fmt, i, &ptr, &len, nullptr, nullptr) == noErr) {
                        appendStartCode(c->out.configAnnexB);
                        c->out.configAnnexB.insert(c->out.configAnnexB.end(), ptr, ptr + len);
                    }
                }
            }
        }
        CMBlockBufferRef block = CMSampleBufferGetDataBuffer(sample);
        if (!block)
            return;
        size_t totalLen = 0;
        char* dataPtr = nullptr;
        if (CMBlockBufferGetDataPointer(block, 0, nullptr, &totalLen, &dataPtr) != noErr)
            return;
        std::vector<uint8_t> frame;
        size_t offset = 0;
        while (offset + 4 <= totalLen) {
            uint32_t nalLen = (uint32_t(uint8_t(dataPtr[offset])) << 24) |
                              (uint32_t(uint8_t(dataPtr[offset + 1])) << 16) |
                              (uint32_t(uint8_t(dataPtr[offset + 2])) << 8) |
                              uint32_t(uint8_t(dataPtr[offset + 3]));
            offset += 4;
            if (offset + nalLen > totalLen)
                break;
            appendStartCode(frame);
            frame.insert(frame.end(), reinterpret_cast<const uint8_t*>(dataPtr + offset),
                         reinterpret_cast<const uint8_t*>(dataPtr + offset + nalLen));
            offset += nalLen;
        }
        if (!frame.empty())
            c->out.framesAnnexB.push_back(std::move(frame));
    };

    VTCompressionSessionRef session = nullptr;
    OSStatus status = VTCompressionSessionCreate(kCFAllocatorDefault, width, height, codec, nullptr,
                                                 nullptr, nullptr, cb, &ctx, &session);
    require(status == noErr && session != nullptr, "VTCompressionSessionCreate failed");
    VTSessionSetProperty(session, kVTCompressionPropertyKey_RealTime, kCFBooleanTrue);
    VTSessionSetProperty(session, kVTCompressionPropertyKey_AllowFrameReordering, kCFBooleanFalse);
    VTCompressionSessionPrepareToEncodeFrames(session);

    for (int i = 0; i < count; ++i) {
        CVPixelBufferRef pb = nullptr;
        require(CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA,
                                    nullptr, &pb) == kCVReturnSuccess,
                "CVPixelBufferCreate failed");
        CVPixelBufferLockBaseAddress(pb, 0);
        auto* base = static_cast<uint8_t*>(CVPixelBufferGetBaseAddress(pb));
        size_t stride = CVPixelBufferGetBytesPerRow(pb);
        for (int y = 0; y < height; ++y) {
            uint8_t* row = base + size_t(y) * stride;
            for (int x = 0; x < width; ++x) {
                row[x * 4 + 0] = b;
                row[x * 4 + 1] = g;
                row[x * 4 + 2] = r;
                row[x * 4 + 3] = 0xff;
            }
        }
        CVPixelBufferUnlockBaseAddress(pb, 0);
        CMTime pts = CMTimeMake(i * 33333, 1000000);
        status = VTCompressionSessionEncodeFrame(session, pb, pts, kCMTimeInvalid, nullptr, nullptr,
                                                 nullptr);
        CVPixelBufferRelease(pb);
        require(status == noErr, "VTCompressionSessionEncodeFrame failed");
    }
    VTCompressionSessionCompleteFrames(session, kCMTimeInvalid);
    VTCompressionSessionInvalidate(session);
    CFRelease(session);
    return ctx.out;
}

std::vector<std::vector<uint8_t>> encodeOpusTone(int channels, int packetCount) {
    AudioStreamBasicDescription inDesc{};
    inDesc.mSampleRate = 48000.0;
    inDesc.mFormatID = kAudioFormatLinearPCM;
    inDesc.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked;
    inDesc.mChannelsPerFrame = static_cast<UInt32>(channels);
    inDesc.mBitsPerChannel = 16;
    inDesc.mFramesPerPacket = 1;
    inDesc.mBytesPerFrame = static_cast<UInt32>(channels * 2);
    inDesc.mBytesPerPacket = static_cast<UInt32>(channels * 2);

    AudioStreamBasicDescription outDesc{};
    outDesc.mSampleRate = 48000.0;
    outDesc.mFormatID = kAudioFormatOpus;
    outDesc.mChannelsPerFrame = static_cast<UInt32>(channels);
    outDesc.mFramesPerPacket = 960; // 20 ms

    AudioConverterRef conv = nullptr;
    require(AudioConverterNew(&inDesc, &outDesc, &conv) == noErr && conv != nullptr,
            "AudioConverterNew PCM->Opus failed");

    constexpr int kFrameSamples = 960;
    std::vector<int16_t> pcm(size_t(kFrameSamples) * packetCount * channels);
    for (int i = 0; i < kFrameSamples * packetCount; ++i) {
        int16_t s = static_cast<int16_t>(16000.0 * std::sin(2.0 * M_PI * 440.0 * i / 48000.0));
        for (int c = 0; c < channels; ++c)
            pcm[size_t(i) * channels + c] = s;
    }

    struct Feed {
        const int16_t* data;
        size_t totalFrames;
        size_t posFrames;
        int channels;
    } feed{pcm.data(), size_t(kFrameSamples) * packetCount, 0, channels};

    auto proc = [](AudioConverterRef, UInt32* ioNumberDataPackets, AudioBufferList* ioData,
                   AudioStreamPacketDescription**, void* user) -> OSStatus {
        auto* f = static_cast<Feed*>(user);
        size_t rem = f->totalFrames - f->posFrames;
        if (rem == 0) {
            *ioNumberDataPackets = 0;
            ioData->mBuffers[0].mData = nullptr;
            ioData->mBuffers[0].mDataByteSize = 0;
            return noErr;
        }
        UInt32 give = static_cast<UInt32>(std::min<size_t>(*ioNumberDataPackets, rem));
        ioData->mBuffers[0].mData = const_cast<int16_t*>(f->data + f->posFrames * f->channels);
        ioData->mBuffers[0].mDataByteSize = give * static_cast<UInt32>(f->channels * 2);
        f->posFrames += give;
        *ioNumberDataPackets = give;
        return noErr;
    };

    std::vector<std::vector<uint8_t>> packets;
    for (int p = 0; p < packetCount; ++p) {
        std::vector<uint8_t> outBuf(4096);
        AudioBufferList outList{};
        outList.mNumberBuffers = 1;
        outList.mBuffers[0].mNumberChannels = static_cast<UInt32>(channels);
        outList.mBuffers[0].mData = outBuf.data();
        outList.mBuffers[0].mDataByteSize = static_cast<UInt32>(outBuf.size());
        AudioStreamPacketDescription pktDesc{};
        UInt32 outPkts = 1;
        OSStatus st =
            AudioConverterFillComplexBuffer(conv, proc, &feed, &outPkts, &outList, &pktDesc);
        if (st != noErr || outPkts == 0)
            break;
        packets.emplace_back(outBuf.begin() + pktDesc.mStartOffset,
                             outBuf.begin() + pktDesc.mStartOffset + pktDesc.mDataByteSize);
    }
    AudioConverterDispose(conv);
    return packets;
}

} // namespace

int main(int argc, char** argv) {
    try {
        require(argc >= 2, "usage: test-video-codec /path/to/libshiraz-video.so");
        void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
        if (!lib) {
            std::cerr << "dlopen failed: " << dlerror() << "\n";
            return 1;
        }
        auto loadSym = [&](const char* name) {
            void* p = dlsym(lib, name);
            if (!p)
                throw std::runtime_error(std::string("missing symbol: ") + name);
            return p;
        };
        auto fnCodecs = reinterpret_cast<jobjectArray (*)(JNIEnv*, jclass)>(
            loadSym("Java_shiraz_runtime_NativeVideo_nativeCodecs"));
        auto fnOpen = reinterpret_cast<jlong (*)(JNIEnv*, jclass, jstring, jint, jint, jbyteArray)>(
            loadSym("Java_shiraz_runtime_NativeVideo_open"));
        auto fnCanQueue = reinterpret_cast<jboolean (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_NativeVideo_canQueue"));
        auto fnQueue =
            reinterpret_cast<void (*)(JNIEnv*, jclass, jlong, jbyteArray, jlong, jboolean)>(
                loadSym("Java_shiraz_runtime_NativeVideo_queue"));
        auto fnPoll = reinterpret_cast<jlongArray (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_NativeVideo_poll"));
        auto fnRelease = reinterpret_cast<void (*)(JNIEnv*, jclass, jlong, jint, jlong, jboolean)>(
            loadSym("Java_shiraz_runtime_NativeVideo_releaseFrame"));
        auto fnClose = reinterpret_cast<void (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_NativeVideo_close"));
        auto fnOpusOpen = reinterpret_cast<jlong (*)(JNIEnv*, jclass, jbyteArray)>(
            loadSym("Java_shiraz_runtime_NativeVideo_opusOpen"));
        auto fnOpusDecode = reinterpret_cast<jbyteArray (*)(JNIEnv*, jclass, jlong, jbyteArray)>(
            loadSym("Java_shiraz_runtime_NativeVideo_opusDecode"));
        auto fnOpusClose = reinterpret_cast<void (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_NativeVideo_opusClose"));

        Harness h;
        // 1. Check advertised codecs include video/avc, video/hevc, audio/opus.
        auto* codecsArr = reinterpret_cast<FakeObj*>(fnCodecs(&h.env, nullptr));
        require(codecsArr != nullptr, "nativeCodecs returned null");
        bool hasAvc = false, hasHevc = false, hasOpus = false;
        std::cout << "MACOS_VIDEO_CODECS";
        for (auto* s : codecsArr->objs) {
            std::cout << " " << s->text;
            if (s->text == "video/avc")
                hasAvc = true;
            if (s->text == "video/hevc")
                hasHevc = true;
            if (s->text == "audio/opus")
                hasOpus = true;
        }
        std::cout << "\n";
        require(hasAvc && hasHevc && hasOpus, "expected video/avc, video/hevc, audio/opus");

        // 2. Round-trip H.264 and HEVC through VideoToolbox encode -> libshiraz-video decode ->
        // ANativeWindow.
        auto testVideoCodec = [&](const char* mime, CMVideoCodecType vtCodec, uint8_t b, uint8_t g,
                                  uint8_t r) {
            constexpr int kW = 64, kH = 64;
            auto encoded = encodeSolidFrames(vtCodec, kW, kH, b, g, r, 3);
            require(!encoded.configAnnexB.empty() && encoded.framesAnnexB.size() == 3,
                    "encoder produced no Annex-B frames");

            jlong dec = fnOpen(&h.env, nullptr, h.makeString(mime), kW, kH,
                               h.makeBytes(encoded.configAnnexB));
            require(dec != 0 && h.lastException.empty(), "NativeVideo.open failed");
            require(fnCanQueue(&h.env, nullptr, dec) == JNI_TRUE, "canQueue should be true");

            for (size_t i = 0; i < encoded.framesAnnexB.size(); ++i) {
                fnQueue(&h.env, nullptr, dec, h.makeBytes(encoded.framesAnnexB[i]),
                        static_cast<jlong>(i * 33333),
                        i + 1 == encoded.framesAnnexB.size() ? JNI_TRUE : JNI_FALSE);
                require(h.lastException.empty(), "NativeVideo.queue threw");
            }

            ANativeWindow* win = shiraz_window_create(kW, kH);
            require(win != nullptr, "shiraz_window_create failed");

            int framesDecoded = 0;
            bool sawEos = false;
            std::vector<uint32_t> readback(kW * kH, 0);
            for (int iter = 0; iter < 50 && !sawEos; ++iter) {
                auto* info = reinterpret_cast<FakeObj*>(fnPoll(&h.env, nullptr, dec));
                require(h.lastException.empty(), "NativeVideo.poll threw");
                if (!info) {
                    std::this_thread::sleep_for(std::chrono::milliseconds(5));
                    continue;
                }
                if (info->longs[0] < 0) {
                    require(info->longs[3] == 16, "EOS slot must be 16");
                    sawEos = true;
                    break;
                }
                require(info->longs[0] == kW && info->longs[1] == kH, "decoded dimensions");
                fnRelease(&h.env, nullptr, dec, static_cast<jint>(info->longs[3]),
                          reinterpret_cast<jlong>(win), JNI_TRUE);
                require(h.lastException.empty(), "NativeVideo.releaseFrame threw");
                require(shiraz_window_copy_argb(win, readback.data(), readback.size()) == 0,
                        "shiraz_window_copy_argb failed");
                ++framesDecoded;
            }
            require(framesDecoded == 3, "expected 3 decoded video frames");
            require(sawEos, "expected EOS signal");

            uint32_t center = readback[size_t(kH / 2) * kW + (kW / 2)];
            int dr = std::abs(int((center >> 16) & 0xff) - int(r));
            int dg = std::abs(int((center >> 8) & 0xff) - int(g));
            int db = std::abs(int(center & 0xff) - int(b));
            require(dr < 45 && dg < 45 && db < 45, "decoded center pixel color mismatch");
            std::cout << "MACOS_VIDEO_ROUNDTRIP mime=" << mime << " frames=" << framesDecoded
                      << " center=0x" << std::hex << center << std::dec << "\n";

            ANativeWindow_release(win);
            fnClose(&h.env, nullptr, dec);
        };

        testVideoCodec("video/avc", kCMVideoCodecType_H264, 0x20, 0xd0, 0x30);  // green-ish
        testVideoCodec("video/hevc", kCMVideoCodecType_HEVC, 0xd0, 0x30, 0x20); // blue-ish

        // 3. Round-trip Opus through AudioToolbox encode -> libshiraz-video opusDecode.
        auto opusPackets = encodeOpusTone(2, 5);
        require(opusPackets.size() == 5, "Opus encoder failed");

        std::vector<uint8_t> opusHead(19, 0);
        std::memcpy(opusHead.data(), "OpusHead", 8);
        opusHead[8] = 1;    // version
        opusHead[9] = 2;    // stereo
        opusHead[10] = 120; // pre-skip = 120 samples
        opusHead[11] = 0;
        jlong opus = fnOpusOpen(&h.env, nullptr, h.makeBytes(opusHead));
        require(opus != 0 && h.lastException.empty(), "opusOpen failed");

        int16_t peak = 0;
        size_t totalBytes = 0;
        for (size_t i = 0; i < opusPackets.size(); ++i) {
            auto* pcmObj = reinterpret_cast<FakeObj*>(
                fnOpusDecode(&h.env, nullptr, opus, h.makeBytes(opusPackets[i])));
            require(pcmObj != nullptr && h.lastException.empty(), "opusDecode failed");
            size_t len = pcmObj->bytes.size();
            if (i == 0 && len != (960 - 120) * 2 * 2) {
                std::cerr << "Opus packet 0: inSize=" << opusPackets[0].size() << " toc=0x"
                          << std::hex << int(opusPackets[0][0]) << std::dec
                          << " decodedBytes=" << len << " expected=" << ((960 - 120) * 2 * 2)
                          << "\n";
                require(false, "Opus pre-skip trimming mismatch");
            } else if (i > 0 && len != 960 * 2 * 2) {
                std::cerr << "Opus packet " << i << ": decodedBytes=" << len << "\n";
                require(false, "Opus frame length mismatch");
            }
            totalBytes += len;
            const auto* samples = reinterpret_cast<const int16_t*>(pcmObj->bytes.data());
            for (size_t s = 0; s < len / 2; ++s) {
                int v = std::abs(int(samples[s]));
                if (v > peak)
                    peak = static_cast<int16_t>(v);
            }
        }
        require(peak > 1000, "decoded Opus PCM should contain audible tone");
        std::cout << "MACOS_OPUS_ROUNDTRIP packets=" << opusPackets.size()
                  << " bytes=" << totalBytes << " peak=" << peak << "\n";
        fnOpusClose(&h.env, nullptr, opus);

        // Reject bad Opus header.
        h.clearException();
        std::vector<uint8_t> badHead(19, 0);
        jlong bad = fnOpusOpen(&h.env, nullptr, h.makeBytes(badHead));
        require(bad == 0 && !h.lastException.empty(), "malformed OpusHead must throw");
        h.clearException();

        dlclose(lib);
        std::cout << "MACOS_VIDEO_CODEC_PASS\n";
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "test-video-codec FAILED: " << e.what() << "\n";
        return 1;
    }
}
