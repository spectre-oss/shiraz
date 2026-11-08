// SPDX-License-Identifier: GPL-3.0-only
#include "audio_decode.hpp"
#include <AudioToolbox/AudioToolbox.h>
#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>
#include <limits>
#include <mutex>
#include <stdexcept>
#include <string>

namespace shiraz::audio {
namespace {
[[noreturn]] void malformed(const char *message) { throw std::invalid_argument(message); }
void check(OSStatus status, const char *operation) {
    if (status != noErr) throw std::invalid_argument(std::string(operation) + " failed (CoreAudio " + std::to_string(status) + ")");
}
uint64_t number(const std::vector<uint8_t>& bytes, size_t offset, unsigned width) {
    if (offset > bytes.size() || width > bytes.size() - offset) malformed("Truncated Ogg field");
    uint64_t value = 0;
    for (unsigned i = 0; i < width; ++i) value |= uint64_t(bytes[offset + i]) << (i * 8);
    return value;
}
uint64_t oggFrames(const std::vector<uint8_t>& bytes) {
    size_t position = 0;
    uint32_t serial = 0, sequence = 0;
    bool first = true, continued = false, ended = false;
    uint64_t frames = 0;
    while (position < bytes.size()) {
        if (ended || bytes.size() - position < 27 || std::memcmp(bytes.data() + position, "OggS", 4) || bytes[position + 4])
            malformed("Malformed Ogg page header");
        unsigned flags = bytes[position + 5], segments = bytes[position + 26];
        if ((flags & ~7u) || bool(flags & 1) != continued || bool(flags & 2) != first || !segments)
            malformed("Unsupported Ogg page continuation or stream boundary");
        uint32_t pageSerial = uint32_t(number(bytes, position + 14, 4));
        uint32_t pageSequence = uint32_t(number(bytes, position + 18, 4));
        if (first) { serial = pageSerial; if (pageSequence) malformed("Ogg stream does not start at sequence zero"); }
        if (pageSerial != serial || pageSequence != sequence++) malformed("Multiplexed or discontinuous Ogg stream");
        if (bytes.size() - position < 27 + segments) malformed("Truncated Ogg lacing table");
        size_t size = 27 + segments;
        for (unsigned i = 0; i < segments; ++i) size += bytes[position + 27 + i];
        if (size > bytes.size() - position) malformed("Truncated Ogg page payload");
        uint32_t crc = 0;
        for (size_t i = 0; i < size; ++i) {
            crc ^= uint32_t(i >= 22 && i < 26 ? 0 : bytes[position + i]) << 24;
            for (unsigned bit = 0; bit < 8; ++bit) crc = (crc << 1) ^ ((crc & 0x80000000u) ? 0x04c11db7u : 0);
        }
        if (crc != number(bytes, position + 22, 4)) malformed("Ogg page checksum mismatch");
        continued = bytes[position + 27 + segments - 1] == 255;
        ended = flags & 4;
        if (ended) {
            if (continued) malformed("Unterminated final Ogg packet");
            frames = number(bytes, position + 6, 8);
            if (!frames || frames == UINT64_MAX) malformed("Invalid final Ogg sample count");
        }
        position += size;
        first = false;
    }
    if (!ended) malformed("Ogg stream lacks a complete end page");
    return frames;
}
std::shared_ptr<Clip> vorbis(const std::vector<uint8_t>& bytes) {
    // Unchanged stb_vorbis initializes a process-global CRC lookup table when
    // opening a decoder. Serialize its use, including concurrent SoundPools.
    static std::mutex decoderMutex;
    std::lock_guard<std::mutex> serialized(decoderMutex);
    uint64_t expected = oggFrames(bytes);
    int error = 0;
    // The decoder's codebook/setup allocations also need a hard bound, not
    // only its final PCM. The upstream arena API prevents fallback heap use.
    std::vector<char> arena(16 * 1024 * 1024);
    stb_vorbis_alloc allocation{arena.data(), int(arena.size())};
    std::unique_ptr<stb_vorbis, decltype(&stb_vorbis_close)> decoder(
        stb_vorbis_open_memory(bytes.data(), int(bytes.size()), &error, &allocation), &stb_vorbis_close);
    if (!decoder) malformed("Unsupported or malformed Ogg Vorbis sound");
    auto info = stb_vorbis_get_info(decoder.get());
    if (info.channels < 1 || info.channels > 2 || info.sample_rate < 8000 || info.sample_rate > 192000 ||
            expected > maxDecodedSamples / unsigned(info.channels)) malformed("Ogg sound exceeds decoded shape or size");
    if (stb_vorbis_stream_length_in_samples(decoder.get()) != expected) malformed("Ogg sample count mismatch");
    auto clip = std::make_shared<Clip>();
    clip->rate = int(info.sample_rate); clip->channels = info.channels;
    clip->samples.resize(size_t(expected) * info.channels);
    size_t frames = 0;
    while (frames < expected) {
        int count = stb_vorbis_get_samples_float_interleaved(decoder.get(), info.channels,
            clip->samples.data() + frames * info.channels, int((expected - frames) * info.channels));
        if (count <= 0) malformed("Truncated Ogg Vorbis sound");
        frames += unsigned(count);
    }
    float extra[2];
    if (stb_vorbis_get_samples_float_interleaved(decoder.get(), info.channels, extra, info.channels) ||
            stb_vorbis_get_error(decoder.get()) != VORBIS__no_error) malformed("Ogg Vorbis decoder did not reach clean EOF");
    return clip;
}
struct Memory { const std::vector<uint8_t>& bytes; };
OSStatus readMemory(void *opaque, SInt64 position, UInt32 requested, void *output, UInt32 *actual) {
    auto &bytes = static_cast<Memory *>(opaque)->bytes;
    *actual = 0;
    if (position < 0) return kAudioFilePositionError;
    if (uint64_t(position) >= bytes.size()) return noErr;
    size_t count = std::min<size_t>(requested, bytes.size() - size_t(position));
    if (count) std::memcpy(output, bytes.data() + size_t(position), count);
    *actual = UInt32(count);
    return noErr;
}
SInt64 sizeMemory(void *opaque) { return SInt64(static_cast<Memory *>(opaque)->bytes.size()); }
struct AudioFile {
    AudioFileID file = nullptr;
    ExtAudioFileRef extended = nullptr;
    ~AudioFile() { if (extended) ExtAudioFileDispose(extended); if (file) AudioFileClose(file); }
};
}
std::shared_ptr<Clip> darwinDecode(const std::vector<uint8_t>& bytes) {
    if (bytes.empty() || bytes.size() > maxEncodedBytes) malformed("Invalid encoded sound size");
    if (bytes.size() >= 4 && !std::memcmp(bytes.data(), "OggS", 4)) return vorbis(bytes);
    Memory memory{bytes};
    AudioFile file;
    check(AudioFileOpenWithCallbacks(&memory, readMemory, nullptr, sizeMemory, nullptr, 0, &file.file), "Open encoded sound");
    check(ExtAudioFileWrapAudioFileID(file.file, false, &file.extended), "Wrap encoded sound");
    AudioStreamBasicDescription input{};
    UInt32 size = sizeof(input);
    check(ExtAudioFileGetProperty(file.extended, kExtAudioFileProperty_FileDataFormat, &size, &input), "Read encoded sound format");
    if (!std::isfinite(input.mSampleRate) || input.mSampleRate < 8000 || input.mSampleRate > 192000 ||
            input.mChannelsPerFrame < 1 || input.mChannelsPerFrame > 2 || std::floor(input.mSampleRate) != input.mSampleRate)
        malformed("Unsupported decoded sound shape");
    AudioStreamBasicDescription output{};
    output.mSampleRate = input.mSampleRate;
    output.mFormatID = kAudioFormatLinearPCM;
    output.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagsNativeEndian | kAudioFormatFlagIsPacked;
    output.mBytesPerPacket = output.mBytesPerFrame = input.mChannelsPerFrame * sizeof(float);
    output.mFramesPerPacket = 1; output.mChannelsPerFrame = input.mChannelsPerFrame; output.mBitsPerChannel = 32;
    check(ExtAudioFileSetProperty(file.extended, kExtAudioFileProperty_ClientDataFormat, sizeof(output), &output), "Set decoded sound format");
    auto clip = std::make_shared<Clip>();
    clip->rate = int(input.mSampleRate); clip->channels = int(input.mChannelsPerFrame);
    std::array<float, 8192> block{};
    for (;;) {
        UInt32 frames = 4096;
        AudioBufferList buffers{};
        buffers.mNumberBuffers = 1;
        buffers.mBuffers[0] = {output.mChannelsPerFrame, UInt32(block.size() * sizeof(float)), block.data()};
        check(ExtAudioFileRead(file.extended, &frames, &buffers), "Decode sound");
        if (!frames) break;
        size_t samples = size_t(frames) * clip->channels;
        if (samples > maxDecodedSamples - clip->samples.size()) malformed("Decoded sound exceeds storage limit");
        clip->samples.insert(clip->samples.end(), block.begin(), block.begin() + samples);
    }
    return clip;
}
}
