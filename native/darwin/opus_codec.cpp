// SPDX-License-Identifier: GPL-3.0-only
// macOS AudioToolbox Opus decoder backing shiraz.runtime.NativeVideo.
#include <AudioToolbox/AudioToolbox.h>
#include <jni.h>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <vector>

namespace {

constexpr double kOpusSampleRate = 48000.0;
constexpr UInt32 kMaxOpusFramesPerPacket = 5760; // 120 ms at 48 kHz (RFC 6716)

AudioStreamBasicDescription opusInputDesc(int channels) {
    AudioStreamBasicDescription desc{};
    desc.mSampleRate = kOpusSampleRate;
    desc.mFormatID = kAudioFormatOpus;
    desc.mFormatFlags = 0;
    desc.mBytesPerPacket = 0;
    desc.mFramesPerPacket = 0;
    desc.mBytesPerFrame = 0;
    desc.mChannelsPerFrame = static_cast<UInt32>(channels);
    desc.mBitsPerChannel = 0;
    return desc;
}

AudioStreamBasicDescription pcmOutputDesc(int channels) {
    AudioStreamBasicDescription desc{};
    desc.mSampleRate = kOpusSampleRate;
    desc.mFormatID = kAudioFormatLinearPCM;
    desc.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked;
    desc.mBytesPerPacket = static_cast<UInt32>(channels * 2);
    desc.mFramesPerPacket = 1;
    desc.mBytesPerFrame = static_cast<UInt32>(channels * 2);
    desc.mChannelsPerFrame = static_cast<UInt32>(channels);
    desc.mBitsPerChannel = 16;
    return desc;
}

// RFC 6716 §3.1 + §3.2: exact number of 48 kHz samples encoded in an Opus packet.
int opusPacketSamples48k(const unsigned char* data, size_t size) {
    if (!data || size < 1)
        return -1;
    const unsigned char toc = data[0];
    const int config = toc >> 3;
    int frameSamples = 0;
    if (config < 12) {
        static constexpr int kSilk[4] = {480, 960, 1920, 2880};
        frameSamples = kSilk[config & 3];
    } else if (config < 16) {
        static constexpr int kHybrid[2] = {480, 960};
        frameSamples = kHybrid[config & 1];
    } else {
        static constexpr int kCelt[4] = {120, 240, 480, 960};
        frameSamples = kCelt[config & 3];
    }
    int frameCount = 1;
    switch (toc & 3) {
    case 0:
        frameCount = 1;
        break;
    case 1:
    case 2:
        frameCount = 2;
        break;
    case 3:
        if (size < 2)
            return -1;
        frameCount = data[1] & 0x3f;
        if (frameCount < 1 || frameCount > 48)
            return -1;
        break;
    }
    int total = frameSamples * frameCount;
    if (total < 120 || total > static_cast<int>(kMaxOpusFramesPerPacket))
        return -1;
    return total;
}

struct OpusDecoderState {
    AudioConverterRef converter = nullptr;
    int channels = 0;
    int skip = 0;
    ~OpusDecoderState() {
        if (converter)
            AudioConverterDispose(converter);
    }
};

struct InputFeed {
    const unsigned char* data = nullptr;
    UInt32 size = 0;
    UInt32 frames = 0;
    bool consumed = false;
    AudioStreamPacketDescription packetDesc{};
};

OSStatus opusInputProc(AudioConverterRef, UInt32* ioNumberDataPackets, AudioBufferList* ioData,
                       AudioStreamPacketDescription** outDataPacketDescription, void* inUserData) {
    auto* feed = static_cast<InputFeed*>(inUserData);
    if (!feed || feed->consumed || *ioNumberDataPackets == 0) {
        *ioNumberDataPackets = 0;
        ioData->mBuffers[0].mData = nullptr;
        ioData->mBuffers[0].mDataByteSize = 0;
        return noErr;
    }
    feed->consumed = true;
    *ioNumberDataPackets = 1;
    ioData->mBuffers[0].mData = const_cast<unsigned char*>(feed->data);
    ioData->mBuffers[0].mDataByteSize = feed->size;
    feed->packetDesc.mStartOffset = 0;
    feed->packetDesc.mVariableFramesInPacket = feed->frames;
    feed->packetDesc.mDataByteSize = feed->size;
    if (outDataPacketDescription)
        *outDataPacketDescription = &feed->packetDesc;
    return noErr;
}

template <class F> auto checked(JNIEnv* env, F f) -> decltype(f()) {
    try {
        return f();
    } catch (const std::exception& e) {
        jclass c = env->FindClass("java/lang/IllegalStateException");
        if (c) {
            env->ThrowNew(c, e.what());
            env->DeleteLocalRef(c);
        }
        return {};
    }
}

} // namespace

extern "C" bool shiraz_opus_available() {
    static const bool available = [] {
        AudioStreamBasicDescription inDesc = opusInputDesc(1);
        AudioStreamBasicDescription outDesc = pcmOutputDesc(1);
        AudioConverterRef conv = nullptr;
        OSStatus status = AudioConverterNew(&inDesc, &outDesc, &conv);
        if (conv)
            AudioConverterDispose(conv);
        return status == noErr;
    }();
    return available;
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeVideo_opusOpen(JNIEnv* env, jclass,
                                                                            jbyteArray config) {
    return checked(env, [&]() -> jlong {
        if (!shiraz_opus_available() || !config || env->GetArrayLength(config) != 19)
            throw std::invalid_argument(
                "OS Opus decoder or mapping-family-zero header unavailable");
        unsigned char bytes[19];
        env->GetByteArrayRegion(config, 0, 19, reinterpret_cast<jbyte*>(bytes));
        if (env->ExceptionCheck())
            return 0;
        if (std::memcmp(bytes, "OpusHead", 8) != 0 || bytes[8] > 15 || bytes[9] < 1 ||
            bytes[9] > 2 || bytes[18] != 0 || bytes[16] != 0 || bytes[17] != 0)
            throw std::invalid_argument("Unsupported Opus header");

        auto state = std::make_unique<OpusDecoderState>();
        state->channels = bytes[9];
        state->skip = bytes[10] | (int(bytes[11]) << 8);

        AudioStreamBasicDescription inDesc = opusInputDesc(state->channels);
        AudioStreamBasicDescription outDesc = pcmOutputDesc(state->channels);
        OSStatus status = AudioConverterNew(&inDesc, &outDesc, &state->converter);
        if (status != noErr || !state->converter)
            throw std::runtime_error("AudioToolbox Opus initialization failed");
        UInt32 primeMethod = kConverterPrimeMethod_None;
        AudioConverterSetProperty(state->converter, kAudioConverterPrimeMethod, sizeof(primeMethod),
                                  &primeMethod);
        AudioConverterPrimeInfo primeInfo{0, 0};
        AudioConverterSetProperty(state->converter, kAudioConverterPrimeInfo, sizeof(primeInfo),
                                  &primeInfo);
        return reinterpret_cast<jlong>(state.release());
    });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_shiraz_runtime_NativeVideo_opusDecode(JNIEnv* env, jclass, jlong handle, jbyteArray packet) {
    return checked(env, [&]() -> jbyteArray {
        auto* state = reinterpret_cast<OpusDecoderState*>(handle);
        if (!state || !state->converter || !packet)
            throw std::invalid_argument("Opus input");
        jsize size = env->GetArrayLength(packet);
        if (size < 1 || size > 65536)
            throw std::invalid_argument("Opus packet bounds");
        std::vector<unsigned char> bytes(static_cast<size_t>(size));
        env->GetByteArrayRegion(packet, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck())
            return nullptr;

        int expectedFrames = opusPacketSamples48k(bytes.data(), bytes.size());
        if (expectedFrames <= 0)
            throw std::runtime_error("OS Opus rejected malformed TOC");

        std::vector<int16_t> pcm(static_cast<size_t>(expectedFrames) * state->channels);
        AudioBufferList outList{};
        outList.mNumberBuffers = 1;
        outList.mBuffers[0].mNumberChannels = static_cast<UInt32>(state->channels);
        outList.mBuffers[0].mData = pcm.data();
        outList.mBuffers[0].mDataByteSize = static_cast<UInt32>(pcm.size() * sizeof(int16_t));

        InputFeed feed{};
        feed.data = bytes.data();
        feed.size = static_cast<UInt32>(bytes.size());
        feed.frames = static_cast<UInt32>(expectedFrames);

        UInt32 outFrames = static_cast<UInt32>(expectedFrames);
        OSStatus status = AudioConverterFillComplexBuffer(state->converter, opusInputProc, &feed,
                                                          &outFrames, &outList, nullptr);
        if (status != noErr && outFrames == 0)
            throw std::runtime_error("OS Opus rejected packet");

        int frames = static_cast<int>(outFrames);
        int skip = frames < state->skip ? frames : state->skip;
        state->skip -= skip;
        int length = (frames - skip) * state->channels * 2;
        jbyteArray result = env->NewByteArray(length);
        if (result && length > 0) {
            env->SetByteArrayRegion(
                result, 0, length,
                reinterpret_cast<const jbyte*>(pcm.data() + skip * state->channels));
        }
        return result;
    });
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_opusClose(JNIEnv*, jclass,
                                                                            jlong handle) {
    delete reinterpret_cast<OpusDecoderState*>(handle);
}
