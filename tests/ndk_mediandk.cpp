// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N3: complete libmediandk.so
// (AMediaFormat, AMediaCodec boundaries, AMediaMuxer, AMediaExtractor WAV & MP4, AMediaDataSource).
#include "shiraz/ndk/media_container.h"

#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unistd.h>
#include <vector>

extern "C" {
struct AMediaCodecBufferInfo {
    int32_t offset;
    int32_t size;
    int64_t presentationTimeUs;
    uint32_t flags;
};

AMediaFormat* AMediaFormat_new();
int AMediaFormat_delete(AMediaFormat* f);
const char* AMediaFormat_toString(AMediaFormat* f);
bool AMediaFormat_getInt32(AMediaFormat* f, const char* k, int32_t* v);
bool AMediaFormat_getInt64(AMediaFormat* f, const char* k, int64_t* v);
bool AMediaFormat_getString(AMediaFormat* f, const char* k, const char** out);
bool AMediaFormat_getBuffer(AMediaFormat* f, const char* k, void** out, size_t* size);
void AMediaFormat_setInt32(AMediaFormat* f, const char* k, int32_t v);
void AMediaFormat_setString(AMediaFormat* f, const char* k, const char* v);
void AMediaFormat_setBuffer(AMediaFormat* f, const char* k, const void* p, size_t n);
}

namespace {

void testFormatAndCodecBoundaries() {
    AMediaFormat* f = AMediaFormat_new();
    assert(f != nullptr);

    AMediaFormat_setDouble(f, "frame-rate-precise", 59.94);
    AMediaFormat_setSize(f, "max-input-size", 65536u);
    AMediaFormat_setRect(f, "crop", 0, 0, 1920, 1080);

    double d = 0.0;
    assert(AMediaFormat_getDouble(f, "frame-rate-precise", &d));
    assert(std::fabs(d - 59.94) < 1e-6);

    size_t sz = 0;
    assert(AMediaFormat_getSize(f, "max-input-size", &sz));
    assert(sz == 65536u);

    int32_t l = -1, t = -1, r = -1, b = -1;
    assert(AMediaFormat_getRect(f, "crop", &l, &t, &r, &b));
    assert(l == 0 && t == 0 && r == 1920 && b == 1080);

    const char* str = AMediaFormat_toString(f);
    assert(str != nullptr && std::strstr(str, "rect(0,0,1920,1080)") != nullptr);

    AMediaFormat* copy = AMediaFormat_new();
    assert(AMediaFormat_copy(copy, f) == AMEDIA_OK);
    size_t copySz = 0;
    assert(AMediaFormat_getSize(copy, "max-input-size", &copySz) && copySz == 65536u);

    AMediaFormat_clear(f);
    assert(!AMediaFormat_getSize(f, "max-input-size", &sz));

    AMediaFormat_delete(copy);
    AMediaFormat_delete(f);

    // Codec hardware boundaries return explicit errors, never fake success.
    ANativeWindow* surf = nullptr;
    assert(AMediaCodec_createPersistentInputSurface(&surf) == AMEDIA_ERROR_UNSUPPORTED);
    assert(surf == nullptr);
    char* name = nullptr;
    assert(AMediaCodec_getName(nullptr, &name) == AMEDIA_ERROR_INVALID_PARAMETER);
    assert(AMediaCodec_getInputFormat(nullptr) == nullptr);
    AMediaCodec_releaseName(nullptr, nullptr);
}

void testMp4MuxAndExtractRoundTrip() {
    char tmpPath[] = "/tmp/shiraz_ndk_mux_XXXXXX";
    const int fd = ::mkstemp(tmpPath);
    assert(fd >= 0);

    AMediaMuxer* muxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    assert(muxer != nullptr);
    assert(AMediaMuxer_setOrientationHint(muxer, 90) == AMEDIA_OK);
    assert(AMediaMuxer_setLocation(muxer, 37.4220f, -122.0841f) == AMEDIA_OK);

    // Track 0: H.264 video
    AMediaFormat* vfmt = AMediaFormat_new();
    AMediaFormat_setString(vfmt, "mime", "video/avc");
    AMediaFormat_setInt32(vfmt, "width", 1280);
    AMediaFormat_setInt32(vfmt, "height", 720);
    const uint8_t spsPps[6] = {0x01, 0x64, 0x00, 0x1F, 0xFF, 0xE1};
    AMediaFormat_setBuffer(vfmt, "csd-0", spsPps, sizeof(spsPps));
    const ssize_t vTrack = AMediaMuxer_addTrack(muxer, vfmt);
    assert(vTrack == 0);
    AMediaFormat_delete(vfmt);

    // Track 1: AAC audio
    AMediaFormat* afmt = AMediaFormat_new();
    AMediaFormat_setString(afmt, "mime", "audio/mp4a-latm");
    AMediaFormat_setInt32(afmt, "sample-rate", 48000);
    AMediaFormat_setInt32(afmt, "channel-count", 2);
    const ssize_t aTrack = AMediaMuxer_addTrack(muxer, afmt);
    assert(aTrack == 1);
    AMediaFormat_delete(afmt);

    assert(AMediaMuxer_start(muxer) == AMEDIA_OK);

    const uint8_t vFrame0[4] = {0xAA, 0xBB, 0xCC, 0xDD};
    const uint8_t vFrame1[3] = {0x11, 0x22, 0x33};
    const uint8_t vFrame2[5] = {0xDE, 0xAD, 0xBE, 0xEF, 0x42};
    AMediaCodecBufferInfo info{};

    info = {0, 4, 0, AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC};
    assert(AMediaMuxer_writeSampleData(muxer, static_cast<size_t>(vTrack), vFrame0, &info) ==
           AMEDIA_OK);
    info = {0, 3, 33333, 0};
    assert(AMediaMuxer_writeSampleData(muxer, static_cast<size_t>(vTrack), vFrame1, &info) ==
           AMEDIA_OK);
    info = {0, 5, 66666, AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC};
    assert(AMediaMuxer_writeSampleData(muxer, static_cast<size_t>(vTrack), vFrame2, &info) ==
           AMEDIA_OK);

    const uint8_t aFrame0[4] = {0x55, 0x66, 0x77, 0x88};
    info = {0, 4, 20000, AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC};
    assert(AMediaMuxer_writeSampleData(muxer, static_cast<size_t>(aTrack), aFrame0, &info) ==
           AMEDIA_OK);

    assert(AMediaMuxer_stop(muxer) == AMEDIA_OK);
    assert(AMediaMuxer_delete(muxer) == AMEDIA_OK);

    // Extract back from the same file path.
    AMediaExtractor* ex = AMediaExtractor_new();
    assert(ex != nullptr);
    assert(AMediaExtractor_setDataSource(ex, tmpPath) == AMEDIA_OK);
    assert(AMediaExtractor_getTrackCount(ex) == 2);

    AMediaFormat* fileFmt = AMediaExtractor_getFileFormat(ex);
    assert(fileFmt != nullptr);
    const char* fileMime = nullptr;
    assert(AMediaFormat_getString(fileFmt, "mime", &fileMime));
    assert(std::strcmp(fileMime, "video/mp4") == 0);
    AMediaFormat_delete(fileFmt);

    AMediaFormat* outV = AMediaExtractor_getTrackFormat(ex, 0);
    assert(outV != nullptr);
    const char* vMime = nullptr;
    int32_t w = 0, h = 0;
    void* csd = nullptr;
    size_t csdLen = 0;
    assert(AMediaFormat_getString(outV, "mime", &vMime) && std::strcmp(vMime, "video/avc") == 0);
    assert(AMediaFormat_getInt32(outV, "width", &w) && w == 1280);
    assert(AMediaFormat_getInt32(outV, "height", &h) && h == 720);
    assert(AMediaFormat_getBuffer(outV, "csd-0", &csd, &csdLen) && csdLen == sizeof(spsPps));
    assert(std::memcmp(csd, spsPps, sizeof(spsPps)) == 0);
    AMediaFormat_delete(outV);

    AMediaFormat* outA = AMediaExtractor_getTrackFormat(ex, 1);
    assert(outA != nullptr);
    const char* aMime = nullptr;
    int32_t sr = 0, ch = 0;
    assert(AMediaFormat_getString(outA, "mime", &aMime) &&
           std::strcmp(aMime, "audio/mp4a-latm") == 0);
    assert(AMediaFormat_getInt32(outA, "sample-rate", &sr) && sr == 48000);
    assert(AMediaFormat_getInt32(outA, "channel-count", &ch) && ch == 2);
    AMediaFormat_delete(outA);

    assert(AMediaExtractor_selectTrack(ex, 0) == AMEDIA_OK);
    assert(AMediaExtractor_selectTrack(ex, 1) == AMEDIA_OK);

    // Samples should arrive ordered by PTS: v0 (0), a0 (20000), v1 (33333), v2 (66666).
    uint8_t buf[32];
    assert(AMediaExtractor_getSampleTrackIndex(ex) == 0);
    assert(AMediaExtractor_getSampleTime(ex) == 0);
    assert(AMediaExtractor_getSampleSize(ex) == 4);
    assert((AMediaExtractor_getSampleFlags(ex) & AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC) != 0);
    assert(AMediaExtractor_readSampleData(ex, buf, sizeof(buf)) == 4);
    assert(std::memcmp(buf, vFrame0, 4) == 0);

    assert(AMediaExtractor_advance(ex));
    assert(AMediaExtractor_getSampleTrackIndex(ex) == 1);
    assert(AMediaExtractor_getSampleTime(ex) == 20000);
    assert(AMediaExtractor_readSampleData(ex, buf, sizeof(buf)) == 4);
    assert(std::memcmp(buf, aFrame0, 4) == 0);

    assert(AMediaExtractor_advance(ex));
    assert(AMediaExtractor_getSampleTrackIndex(ex) == 0);
    assert(AMediaExtractor_getSampleTime(ex) == 33333);
    assert((AMediaExtractor_getSampleFlags(ex) & AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC) == 0);

    assert(AMediaExtractor_advance(ex));
    assert(AMediaExtractor_getSampleTrackIndex(ex) == 0);
    assert(AMediaExtractor_getSampleTime(ex) == 66666);
    assert((AMediaExtractor_getSampleFlags(ex) & AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC) != 0);
    assert(AMediaExtractor_readSampleData(ex, buf, sizeof(buf)) == 5);
    assert(std::memcmp(buf, vFrame2, 5) == 0);
    assert(!AMediaExtractor_advance(ex));

    // Seek on video track only.
    assert(AMediaExtractor_unselectTrack(ex, 1) == AMEDIA_OK);
    assert(AMediaExtractor_seekTo(ex, 50000, AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC) == AMEDIA_OK);
    assert(AMediaExtractor_getSampleTime(ex) == 0);
    assert(AMediaExtractor_seekTo(ex, 50000, AMEDIAEXTRACTOR_SEEK_NEXT_SYNC) == AMEDIA_OK);
    assert(AMediaExtractor_getSampleTime(ex) == 66666);
    assert(AMediaExtractor_getCachedDuration(ex) > 0);

    assert(AMediaExtractor_delete(ex) == AMEDIA_OK);
    ::close(fd);
    ::unlink(tmpPath);
}

struct MemorySourceState {
    std::vector<uint8_t> bytes;
    bool closed = false;
};

ssize_t memReadAt(void* ud, int64_t offset, void* buffer, size_t size) {
    auto* st = static_cast<MemorySourceState*>(ud);
    if (offset < 0 || static_cast<size_t>(offset) >= st->bytes.size()) {
        return 0;
    }
    const size_t avail = st->bytes.size() - static_cast<size_t>(offset);
    const size_t n = std::min(avail, size);
    std::memcpy(buffer, st->bytes.data() + offset, n);
    return static_cast<ssize_t>(n);
}

ssize_t memGetSize(void* ud) {
    return static_cast<ssize_t>(static_cast<MemorySourceState*>(ud)->bytes.size());
}

void memClose(void* ud) {
    static_cast<MemorySourceState*>(ud)->closed = true;
}

void writeLe16(std::vector<uint8_t>& v, uint16_t x) {
    v.push_back(static_cast<uint8_t>(x & 0xFF));
    v.push_back(static_cast<uint8_t>((x >> 8) & 0xFF));
}

void writeLe32(std::vector<uint8_t>& v, uint32_t x) {
    v.push_back(static_cast<uint8_t>(x & 0xFF));
    v.push_back(static_cast<uint8_t>((x >> 8) & 0xFF));
    v.push_back(static_cast<uint8_t>((x >> 16) & 0xFF));
    v.push_back(static_cast<uint8_t>((x >> 24) & 0xFF));
}

void testWavWithCustomDataSource() {
    // Build a valid 16-bit mono 44100 Hz WAV with 4410 frames (100 ms).
    const uint32_t sampleRate = 44100;
    const uint16_t channels = 1;
    const uint16_t bps = 16;
    const uint32_t numFrames = 4410;
    const uint32_t dataBytes = numFrames * channels * (bps / 8);

    MemorySourceState state;
    auto& w = state.bytes;
    w.insert(w.end(), {'R', 'I', 'F', 'F'});
    writeLe32(w, 36 + dataBytes);
    w.insert(w.end(), {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
    writeLe32(w, 16);
    writeLe16(w, 1); // PCM
    writeLe16(w, channels);
    writeLe32(w, sampleRate);
    writeLe32(w, sampleRate * channels * (bps / 8));
    writeLe16(w, static_cast<uint16_t>(channels * (bps / 8)));
    writeLe16(w, bps);
    w.insert(w.end(), {'d', 'a', 't', 'a'});
    writeLe32(w, dataBytes);
    for (uint32_t i = 0; i < numFrames; ++i) {
        writeLe16(w, static_cast<uint16_t>(i & 0xFFFF));
    }

    AMediaDataSource* src = AMediaDataSource_new();
    assert(src != nullptr);
    AMediaDataSource_setUserdata(src, &state);
    AMediaDataSource_setReadAt(src, memReadAt);
    AMediaDataSource_setGetSize(src, memGetSize);
    AMediaDataSource_setClose(src, memClose);

    AMediaExtractor* ex = AMediaExtractor_new();
    assert(AMediaExtractor_setDataSourceCustom(ex, src) == AMEDIA_OK);
    assert(AMediaExtractor_getTrackCount(ex) == 1);

    AMediaFormat* tf = AMediaExtractor_getTrackFormat(ex, 0);
    const char* mime = nullptr;
    int32_t sr = 0;
    int64_t durUs = 0;
    assert(AMediaFormat_getString(tf, "mime", &mime) && std::strcmp(mime, "audio/raw") == 0);
    assert(AMediaFormat_getInt32(tf, "sample-rate", &sr) && sr == 44100);
    assert(AMediaFormat_getInt64(tf, "durationUs", &durUs) && durUs == 100000);
    AMediaFormat_delete(tf);

    assert(AMediaExtractor_selectTrack(ex, 0) == AMEDIA_OK);
    std::vector<uint8_t> buf(4096);
    size_t totalRead = 0;
    do {
        const ssize_t n = AMediaExtractor_readSampleData(ex, buf.data(), buf.size());
        assert(n > 0);
        totalRead += static_cast<size_t>(n);
    } while (AMediaExtractor_advance(ex));
    assert(totalRead == dataBytes);

    assert(AMediaExtractor_delete(ex) == AMEDIA_OK);
    AMediaDataSource_delete(src);
    assert(state.closed);
}

} // namespace

int main() {
    testFormatAndCodecBoundaries();
    testMp4MuxAndExtractRoundTrip();
    testWavWithCustomDataSource();
    std::printf("ndk_mediandk: ALL TESTS PASSED\n");
    return 0;
}
