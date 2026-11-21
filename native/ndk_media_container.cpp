// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK AMediaDataSource, AMediaExtractor (WAV & ISO-BMFF/MP4),
// and AMediaMuxer (ISO-BMFF/MP4) implementations.
#include "shiraz/ndk/media_container.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <sys/stat.h>
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
bool AMediaFormat_getInt32(AMediaFormat* f, const char* k, int32_t* v);
bool AMediaFormat_getInt64(AMediaFormat* f, const char* k, int64_t* v);
bool AMediaFormat_getString(AMediaFormat* f, const char* k, const char** out);
bool AMediaFormat_getBuffer(AMediaFormat* f, const char* k, void** out, size_t* size);
void AMediaFormat_setInt32(AMediaFormat* f, const char* k, int32_t v);
void AMediaFormat_setInt64(AMediaFormat* f, const char* k, int64_t v);
void AMediaFormat_setString(AMediaFormat* f, const char* k, const char* v);
void AMediaFormat_setBuffer(AMediaFormat* f, const char* k, const void* p, size_t n);
}

struct AMediaDataSource {
    void* userdata = nullptr;
    AMediaDataSourceReadAt readAt = nullptr;
    AMediaDataSourceGetSize getSize = nullptr;
    AMediaDataSourceClose closeFn = nullptr;
    AMediaDataSourceGetAvailableSize getAvailableSize = nullptr;
    bool closed = false;
    std::mutex mu;
};

namespace {

constexpr uint32_t kFourCC(char a, char b, char c, char d) {
    return (static_cast<uint32_t>(static_cast<uint8_t>(a)) << 24) |
           (static_cast<uint32_t>(static_cast<uint8_t>(b)) << 16) |
           (static_cast<uint32_t>(static_cast<uint8_t>(c)) << 8) |
           static_cast<uint32_t>(static_cast<uint8_t>(d));
}

uint16_t readBe16(const uint8_t* p) {
    return static_cast<uint16_t>((static_cast<uint16_t>(p[0]) << 8) | p[1]);
}

uint32_t readBe32(const uint8_t* p) {
    return (static_cast<uint32_t>(p[0]) << 24) | (static_cast<uint32_t>(p[1]) << 16) |
           (static_cast<uint32_t>(p[2]) << 8) | static_cast<uint32_t>(p[3]);
}

uint64_t readBe64(const uint8_t* p) {
    return (static_cast<uint64_t>(readBe32(p)) << 32) | readBe32(p + 4);
}

uint16_t readLe16(const uint8_t* p) {
    return static_cast<uint16_t>(p[0] | (static_cast<uint16_t>(p[1]) << 8));
}

uint32_t readLe32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

void writeBe16(std::vector<uint8_t>& out, uint16_t v) {
    out.push_back(static_cast<uint8_t>((v >> 8) & 0xFF));
    out.push_back(static_cast<uint8_t>(v & 0xFF));
}

void writeBe32(std::vector<uint8_t>& out, uint32_t v) {
    out.push_back(static_cast<uint8_t>((v >> 24) & 0xFF));
    out.push_back(static_cast<uint8_t>((v >> 16) & 0xFF));
    out.push_back(static_cast<uint8_t>((v >> 8) & 0xFF));
    out.push_back(static_cast<uint8_t>(v & 0xFF));
}

void writeBe64(std::vector<uint8_t>& out, uint64_t v) {
    writeBe32(out, static_cast<uint32_t>((v >> 32) & 0xFFFFFFFFu));
    writeBe32(out, static_cast<uint32_t>(v & 0xFFFFFFFFu));
}

void writeFourCC(std::vector<uint8_t>& out, const char tag[4]) {
    out.push_back(static_cast<uint8_t>(tag[0]));
    out.push_back(static_cast<uint8_t>(tag[1]));
    out.push_back(static_cast<uint8_t>(tag[2]));
    out.push_back(static_cast<uint8_t>(tag[3]));
}

void writeBox(std::vector<uint8_t>& out, const char tag[4], const std::vector<uint8_t>& payload) {
    const uint64_t total = 8u + payload.size();
    if (total <= std::numeric_limits<uint32_t>::max()) {
        writeBe32(out, static_cast<uint32_t>(total));
        writeFourCC(out, tag);
    } else {
        writeBe32(out, 1u);
        writeFourCC(out, tag);
        writeBe64(out, total + 8u);
    }
    out.insert(out.end(), payload.begin(), payload.end());
}

void writeFullBox(std::vector<uint8_t>& out, const char tag[4], uint8_t version, uint32_t flags,
                  const std::vector<uint8_t>& body) {
    std::vector<uint8_t> payload;
    payload.reserve(4 + body.size());
    payload.push_back(version);
    payload.push_back(static_cast<uint8_t>((flags >> 16) & 0xFF));
    payload.push_back(static_cast<uint8_t>((flags >> 8) & 0xFF));
    payload.push_back(static_cast<uint8_t>(flags & 0xFF));
    payload.insert(payload.end(), body.begin(), body.end());
    writeBox(out, tag, payload);
}

struct SampleEntry {
    uint64_t offset = 0;
    uint32_t size = 0;
    int64_t ptsUs = 0;
    uint32_t flags = 0;
};

struct ExtractorTrack {
    AMediaFormat* format = nullptr;
    std::vector<SampleEntry> samples;
    bool selected = false;
    size_t cursor = 0;

    ~ExtractorTrack() {
        if (format) {
            AMediaFormat_delete(format);
        }
    }
    ExtractorTrack() = default;
    ExtractorTrack(const ExtractorTrack&) = delete;
    ExtractorTrack& operator=(const ExtractorTrack&) = delete;
    ExtractorTrack(ExtractorTrack&& o) noexcept
        : format(o.format), samples(std::move(o.samples)), selected(o.selected), cursor(o.cursor) {
        o.format = nullptr;
    }
    ExtractorTrack& operator=(ExtractorTrack&& o) noexcept {
        if (this != &o) {
            if (format) {
                AMediaFormat_delete(format);
            }
            format = o.format;
            samples = std::move(o.samples);
            selected = o.selected;
            cursor = o.cursor;
            o.format = nullptr;
        }
        return *this;
    }
};

struct ByteReader {
    int fd = -1;
    bool ownsFd = false;
    int64_t baseOffset = 0;
    int64_t length = 0;
    AMediaDataSource* custom = nullptr;

    void reset() {
        if (ownsFd && fd >= 0) {
            ::close(fd);
        }
        fd = -1;
        ownsFd = false;
        baseOffset = 0;
        length = 0;
        custom = nullptr;
    }

    ~ByteReader() {
        reset();
    }

    int64_t size() const {
        if (custom) {
            if (custom->getSize) {
                return static_cast<int64_t>(custom->getSize(custom->userdata));
            }
            return -1;
        }
        return length;
    }

    ssize_t readAt(int64_t offset, void* buf, size_t count) const {
        if (offset < 0) {
            return -1;
        }
        if (custom) {
            if (!custom->readAt) {
                return -1;
            }
            return custom->readAt(custom->userdata, offset, buf, count);
        }
        if (fd < 0) {
            return -1;
        }
        if (length >= 0) {
            if (offset >= length) {
                return 0;
            }
            const uint64_t avail = static_cast<uint64_t>(length - offset);
            if (count > avail) {
                count = static_cast<size_t>(avail);
            }
        }
        uint8_t* dst = static_cast<uint8_t*>(buf);
        size_t total = 0;
        while (total < count) {
            const ssize_t n =
                ::pread(fd, dst + total, count - total,
                        static_cast<off_t>(baseOffset + offset + static_cast<int64_t>(total)));
            if (n < 0) {
                return -1;
            }
            if (n == 0) {
                break;
            }
            total += static_cast<size_t>(n);
        }
        return static_cast<ssize_t>(total);
    }

    bool readExact(int64_t offset, void* buf, size_t count) const {
        return readAt(offset, buf, count) == static_cast<ssize_t>(count);
    }
};

bool parseWav(const ByteReader& reader, int64_t totalSize, AMediaFormat* fileFormat,
              std::vector<ExtractorTrack>& tracks) {
    if (totalSize < 44) {
        return false;
    }
    uint8_t hdr[12];
    if (!reader.readExact(0, hdr, 12)) {
        return false;
    }
    if (std::memcmp(hdr, "RIFF", 4) != 0 || std::memcmp(hdr + 8, "WAVE", 4) != 0) {
        return false;
    }

    bool hasFmt = false;
    bool hasData = false;
    uint16_t audioFmt = 0;
    uint16_t numChannels = 0;
    uint32_t sampleRate = 0;
    uint16_t blockAlign = 0;
    uint16_t bitsPerSample = 0;
    int64_t dataOffset = 0;
    uint32_t dataSize = 0;

    int64_t pos = 12;
    while (pos + 8 <= totalSize) {
        uint8_t chdr[8];
        if (!reader.readExact(pos, chdr, 8)) {
            break;
        }
        const uint32_t chunkSize = readLe32(chdr + 4);
        const int64_t bodyPos = pos + 8;
        if (bodyPos + static_cast<int64_t>(chunkSize) > totalSize) {
            break;
        }
        if (std::memcmp(chdr, "fmt ", 4) == 0 && chunkSize >= 16) {
            uint8_t fmt[16];
            if (!reader.readExact(bodyPos, fmt, 16)) {
                return false;
            }
            audioFmt = readLe16(fmt + 0);
            numChannels = readLe16(fmt + 2);
            sampleRate = readLe32(fmt + 4);
            blockAlign = readLe16(fmt + 12);
            bitsPerSample = readLe16(fmt + 14);
            hasFmt = true;
        } else if (std::memcmp(chdr, "data", 4) == 0) {
            dataOffset = bodyPos;
            dataSize = chunkSize;
            hasData = true;
        }
        pos = bodyPos + static_cast<int64_t>(chunkSize) + (chunkSize & 1u);
    }

    if (!hasFmt || !hasData || numChannels == 0 || sampleRate == 0) {
        return false;
    }
    if (blockAlign == 0) {
        blockAlign = static_cast<uint16_t>(numChannels * (bitsPerSample / 8u));
        if (blockAlign == 0) {
            return false;
        }
    }

    AMediaFormat_setString(fileFormat, "mime", "audio/wav");

    ExtractorTrack track;
    track.format = AMediaFormat_new();
    AMediaFormat_setString(track.format, "mime", "audio/raw");
    AMediaFormat_setInt32(track.format, "channel-count", static_cast<int32_t>(numChannels));
    AMediaFormat_setInt32(track.format, "sample-rate", static_cast<int32_t>(sampleRate));
    int32_t pcmEncoding = 2; // ENCODING_PCM_16BIT
    if (audioFmt == 3 && bitsPerSample == 32) {
        pcmEncoding = 4; // ENCODING_PCM_FLOAT
    } else if (bitsPerSample == 8) {
        pcmEncoding = 3; // ENCODING_PCM_8BIT
    }
    AMediaFormat_setInt32(track.format, "pcm-encoding", pcmEncoding);

    const uint64_t totalFrames = dataSize / blockAlign;
    const int64_t durationUs =
        static_cast<int64_t>((totalFrames * 1000000ULL) / static_cast<uint64_t>(sampleRate));
    AMediaFormat_setInt64(track.format, "durationUs", durationUs);

    // Split PCM into ~20ms packets (aligned to blockAlign).
    uint32_t framesPerChunk = sampleRate / 50u;
    if (framesPerChunk == 0) {
        framesPerChunk = 1024u;
    }
    const uint32_t bytesPerChunk = framesPerChunk * blockAlign;
    uint32_t consumed = 0;
    uint64_t frameIndex = 0;
    while (consumed < dataSize) {
        const uint32_t rem = dataSize - consumed;
        const uint32_t sz = std::min(rem, bytesPerChunk);
        SampleEntry s;
        s.offset = static_cast<uint64_t>(dataOffset + consumed);
        s.size = sz;
        s.ptsUs =
            static_cast<int64_t>((frameIndex * 1000000ULL) / static_cast<uint64_t>(sampleRate));
        s.flags = AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC;
        track.samples.push_back(s);
        consumed += sz;
        frameIndex += sz / blockAlign;
    }

    tracks.push_back(std::move(track));
    return true;
}

struct Mp4BoxHeader {
    uint32_t type = 0;
    int64_t bodyOffset = 0;
    int64_t bodySize = 0;
    int64_t nextOffset = 0;
};

bool readBoxHeader(const ByteReader& reader, int64_t pos, int64_t end, Mp4BoxHeader* out) {
    if (pos + 8 > end) {
        return false;
    }
    uint8_t h[8];
    if (!reader.readExact(pos, h, 8)) {
        return false;
    }
    const uint32_t size32 = readBe32(h);
    const uint32_t type = readBe32(h + 4);
    int64_t hdrSize = 8;
    int64_t boxSize = size32;
    if (size32 == 1) {
        if (pos + 16 > end) {
            return false;
        }
        uint8_t ext[8];
        if (!reader.readExact(pos + 8, ext, 8)) {
            return false;
        }
        boxSize = static_cast<int64_t>(readBe64(ext));
        hdrSize = 16;
    } else if (size32 == 0) {
        boxSize = end - pos;
    }
    if (boxSize < hdrSize || pos + boxSize > end) {
        return false;
    }
    out->type = type;
    out->bodyOffset = pos + hdrSize;
    out->bodySize = boxSize - hdrSize;
    out->nextOffset = pos + boxSize;
    return true;
}

struct StscEntry {
    uint32_t firstChunk = 0;
    uint32_t samplesPerChunk = 0;
    uint32_t sampleDescIndex = 0;
};

struct SttsEntry {
    uint32_t sampleCount = 0;
    uint32_t sampleDelta = 0;
};

struct CttsEntry {
    uint32_t sampleCount = 0;
    int32_t sampleOffset = 0;
};

std::string mimeFromFourCC(uint32_t fourcc, uint32_t handler) {
    switch (fourcc) {
    case kFourCC('a', 'v', 'c', '1'):
    case kFourCC('a', 'v', 'c', '3'):
        return "video/avc";
    case kFourCC('h', 'v', 'c', '1'):
    case kFourCC('h', 'e', 'v', '1'):
        return "video/hevc";
    case kFourCC('m', 'p', '4', 'v'):
        return "video/mp4v-es";
    case kFourCC('v', 'p', '0', '9'):
        return "video/x-vnd.on2.vp9";
    case kFourCC('a', 'v', '0', '1'):
        return "video/av01";
    case kFourCC('m', 'p', '4', 'a'):
        return "audio/mp4a-latm";
    case kFourCC('O', 'p', 'u', 's'):
        return "audio/opus";
    case kFourCC('f', 'L', 'a', 'C'):
        return "audio/flac";
    case kFourCC('r', 'a', 'w', ' '):
    case kFourCC('s', 'o', 'w', 't'):
    case kFourCC('t', 'w', 'o', 's'):
    case kFourCC('l', 'p', 'c', 'm'):
        return "audio/raw";
    default:
        if (handler == kFourCC('v', 'i', 'd', 'e')) {
            return "video/mp4";
        }
        if (handler == kFourCC('s', 'o', 'u', 'n')) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }
}

bool parseMp4Track(const ByteReader& reader, int64_t trakStart, int64_t trakEnd,
                   ExtractorTrack* outTrack) {
    uint32_t timescale = 1000000;
    uint64_t mediaDuration = 0;
    uint32_t handlerType = 0;
    uint32_t sampleFourCC = 0;
    std::string explicitMime;
    int32_t width = 0;
    int32_t height = 0;
    int32_t channels = 0;
    int32_t sampleRate = 0;
    std::vector<uint8_t> csd0;

    std::vector<SttsEntry> stts;
    std::vector<CttsEntry> ctts;
    std::vector<uint32_t> stss;
    bool hasStss = false;
    std::vector<StscEntry> stsc;
    std::vector<uint32_t> sampleSizes;
    uint32_t constantSampleSize = 0;
    uint32_t sampleCount = 0;
    std::vector<uint64_t> chunkOffsets;

    auto parseStbl = [&](int64_t start, int64_t end) {
        int64_t p = start;
        Mp4BoxHeader b;
        while (readBoxHeader(reader, p, end, &b)) {
            std::vector<uint8_t> buf(
                static_cast<size_t>(std::min<int64_t>(b.bodySize, 16 * 1024 * 1024)));
            if (!buf.empty() && !reader.readExact(b.bodyOffset, buf.data(), buf.size())) {
                p = b.nextOffset;
                continue;
            }
            if (b.type == kFourCC('s', 't', 's', 'd') && buf.size() >= 16) {
                const uint32_t entryCount = readBe32(buf.data() + 4);
                if (entryCount >= 1 && buf.size() >= 16) {
                    const uint32_t entrySize = readBe32(buf.data() + 8);
                    sampleFourCC = readBe32(buf.data() + 12);
                    const size_t entryStart = 8;
                    const size_t entryEnd =
                        std::min(buf.size(), entryStart + static_cast<size_t>(entrySize));
                    size_t childStart = entryEnd;
                    if (handlerType == kFourCC('v', 'i', 'd', 'e') && entryStart + 86 <= entryEnd) {
                        width = readBe16(buf.data() + entryStart + 32);
                        height = readBe16(buf.data() + entryStart + 34);
                        childStart = entryStart + 86;
                    } else if (handlerType == kFourCC('s', 'o', 'u', 'n') &&
                               entryStart + 36 <= entryEnd) {
                        channels = readBe16(buf.data() + entryStart + 24);
                        sampleRate =
                            static_cast<int32_t>(readBe32(buf.data() + entryStart + 32) >> 16);
                        childStart = entryStart + 36;
                    }
                    // Scan child boxes inside sample entry for avcC / hvcC / esds / shrz (exact
                    // mime).
                    size_t cp = childStart;
                    while (cp + 8 <= entryEnd) {
                        const uint32_t csz = readBe32(buf.data() + cp);
                        const uint32_t ctype = readBe32(buf.data() + cp + 4);
                        if (csz < 8 || cp + csz > entryEnd) {
                            break;
                        }
                        if (ctype == kFourCC('a', 'v', 'c', 'C') ||
                            ctype == kFourCC('h', 'v', 'c', 'C') ||
                            ctype == kFourCC('e', 's', 'd', 's')) {
                            csd0.assign(buf.begin() + static_cast<ptrdiff_t>(cp + 8),
                                        buf.begin() + static_cast<ptrdiff_t>(cp + csz));
                        } else if (ctype == kFourCC('s', 'h', 'r', 'z')) {
                            explicitMime.assign(reinterpret_cast<const char*>(buf.data() + cp + 8),
                                                csz - 8);
                        }
                        cp += csz;
                    }
                }
            } else if (b.type == kFourCC('s', 't', 't', 's') && buf.size() >= 8) {
                const uint32_t count = readBe32(buf.data() + 4);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 8 <= buf.size(); ++i) {
                    SttsEntry e;
                    e.sampleCount = readBe32(buf.data() + 8 + i * 8);
                    e.sampleDelta = readBe32(buf.data() + 12 + i * 8);
                    stts.push_back(e);
                }
            } else if (b.type == kFourCC('c', 't', 't', 's') && buf.size() >= 8) {
                const uint32_t count = readBe32(buf.data() + 4);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 8 <= buf.size(); ++i) {
                    CttsEntry e;
                    e.sampleCount = readBe32(buf.data() + 8 + i * 8);
                    e.sampleOffset = static_cast<int32_t>(readBe32(buf.data() + 12 + i * 8));
                    ctts.push_back(e);
                }
            } else if (b.type == kFourCC('s', 't', 's', 's') && buf.size() >= 8) {
                hasStss = true;
                const uint32_t count = readBe32(buf.data() + 4);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 4 <= buf.size(); ++i) {
                    stss.push_back(readBe32(buf.data() + 8 + i * 4));
                }
            } else if (b.type == kFourCC('s', 't', 's', 'c') && buf.size() >= 8) {
                const uint32_t count = readBe32(buf.data() + 4);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 12 <= buf.size(); ++i) {
                    StscEntry e;
                    e.firstChunk = readBe32(buf.data() + 8 + i * 12);
                    e.samplesPerChunk = readBe32(buf.data() + 12 + i * 12);
                    e.sampleDescIndex = readBe32(buf.data() + 16 + i * 12);
                    stsc.push_back(e);
                }
            } else if (b.type == kFourCC('s', 't', 's', 'z') && buf.size() >= 12) {
                constantSampleSize = readBe32(buf.data() + 4);
                sampleCount = readBe32(buf.data() + 8);
                if (constantSampleSize == 0) {
                    sampleSizes.reserve(sampleCount);
                    for (uint32_t i = 0; i < sampleCount && 12 + (i + 1) * 4 <= buf.size(); ++i) {
                        sampleSizes.push_back(readBe32(buf.data() + 12 + i * 4));
                    }
                }
            } else if (b.type == kFourCC('s', 't', 'c', 'o') && buf.size() >= 8) {
                const uint32_t count = readBe32(buf.data() + 4);
                chunkOffsets.reserve(count);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 4 <= buf.size(); ++i) {
                    chunkOffsets.push_back(readBe32(buf.data() + 8 + i * 4));
                }
            } else if (b.type == kFourCC('c', 'o', '6', '4') && buf.size() >= 8) {
                const uint32_t count = readBe32(buf.data() + 4);
                chunkOffsets.reserve(count);
                for (uint32_t i = 0; i < count && 8 + (i + 1) * 8 <= buf.size(); ++i) {
                    chunkOffsets.push_back(readBe64(buf.data() + 8 + i * 8));
                }
            }
            p = b.nextOffset;
        }
    };

    auto parseMinf = [&](int64_t start, int64_t end) {
        int64_t p = start;
        Mp4BoxHeader b;
        while (readBoxHeader(reader, p, end, &b)) {
            if (b.type == kFourCC('s', 't', 'b', 'l')) {
                parseStbl(b.bodyOffset, b.nextOffset);
            }
            p = b.nextOffset;
        }
    };

    auto parseMdia = [&](int64_t start, int64_t end) {
        int64_t p = start;
        Mp4BoxHeader b;
        while (readBoxHeader(reader, p, end, &b)) {
            if (b.type == kFourCC('m', 'd', 'h', 'd')) {
                uint8_t buf[32] = {};
                const size_t n = static_cast<size_t>(std::min<int64_t>(b.bodySize, sizeof(buf)));
                if (reader.readExact(b.bodyOffset, buf, n)) {
                    if (buf[0] == 1 && n >= 32) {
                        timescale = readBe32(buf + 20);
                        mediaDuration = readBe64(buf + 24);
                    } else if (buf[0] == 0 && n >= 20) {
                        timescale = readBe32(buf + 12);
                        mediaDuration = readBe32(buf + 16);
                    }
                    if (timescale == 0) {
                        timescale = 1000000;
                    }
                }
            } else if (b.type == kFourCC('h', 'd', 'l', 'r') && b.bodySize >= 12) {
                uint8_t buf[12];
                if (reader.readExact(b.bodyOffset, buf, 12)) {
                    handlerType = readBe32(buf + 8);
                }
            } else if (b.type == kFourCC('m', 'i', 'n', 'f')) {
                parseMinf(b.bodyOffset, b.nextOffset);
            }
            p = b.nextOffset;
        }
    };

    int64_t p = trakStart;
    Mp4BoxHeader b;
    while (readBoxHeader(reader, p, trakEnd, &b)) {
        if (b.type == kFourCC('m', 'd', 'i', 'a')) {
            parseMdia(b.bodyOffset, b.nextOffset);
        }
        p = b.nextOffset;
    }

    if (sampleCount == 0 && !sampleSizes.empty()) {
        sampleCount = static_cast<uint32_t>(sampleSizes.size());
    }

    // Build sample table.
    std::vector<SampleEntry> samples;
    samples.reserve(sampleCount);

    // Expand sample sizes and chunk offsets.
    uint32_t sampleIdx = 0;
    for (size_t chunkIdx = 0; chunkIdx < chunkOffsets.size() && sampleIdx < sampleCount;
         ++chunkIdx) {
        const uint32_t chunkNum1 = static_cast<uint32_t>(chunkIdx + 1);
        uint32_t spc = 1;
        for (size_t k = 0; k < stsc.size(); ++k) {
            if (stsc[k].firstChunk <= chunkNum1) {
                spc = stsc[k].samplesPerChunk;
            } else {
                break;
            }
        }
        uint64_t curOffset = chunkOffsets[chunkIdx];
        for (uint32_t s = 0; s < spc && sampleIdx < sampleCount; ++s, ++sampleIdx) {
            const uint32_t sz =
                (constantSampleSize != 0)
                    ? constantSampleSize
                    : (sampleIdx < sampleSizes.size() ? sampleSizes[sampleIdx] : 0u);
            SampleEntry entry;
            entry.offset = curOffset;
            entry.size = sz;
            entry.flags = hasStss ? 0u : AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC;
            samples.push_back(entry);
            curOffset += sz;
        }
    }

    // Assign DTS/PTS from stts + ctts.
    uint64_t dts = 0;
    size_t idx = 0;
    for (const auto& e : stts) {
        for (uint32_t i = 0; i < e.sampleCount && idx < samples.size(); ++i, ++idx) {
            samples[idx].ptsUs =
                static_cast<int64_t>((dts * 1000000ULL) / static_cast<uint64_t>(timescale));
            dts += e.sampleDelta;
        }
    }
    idx = 0;
    for (const auto& e : ctts) {
        for (uint32_t i = 0; i < e.sampleCount && idx < samples.size(); ++i, ++idx) {
            const int64_t compUs = (static_cast<int64_t>(e.sampleOffset) * 1000000LL) /
                                   static_cast<int64_t>(timescale);
            samples[idx].ptsUs += compUs;
        }
    }
    if (hasStss) {
        for (uint32_t syncNum1 : stss) {
            if (syncNum1 >= 1 && syncNum1 <= samples.size()) {
                samples[syncNum1 - 1].flags |= AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC;
            }
        }
    }

    outTrack->format = AMediaFormat_new();
    const std::string mime =
        !explicitMime.empty() ? explicitMime : mimeFromFourCC(sampleFourCC, handlerType);
    AMediaFormat_setString(outTrack->format, "mime", mime.c_str());
    if (width > 0) {
        AMediaFormat_setInt32(outTrack->format, "width", width);
    }
    if (height > 0) {
        AMediaFormat_setInt32(outTrack->format, "height", height);
    }
    if (channels > 0) {
        AMediaFormat_setInt32(outTrack->format, "channel-count", channels);
    }
    if (sampleRate > 0) {
        AMediaFormat_setInt32(outTrack->format, "sample-rate", sampleRate);
    }
    if (mediaDuration > 0) {
        const int64_t durUs =
            static_cast<int64_t>((mediaDuration * 1000000ULL) / static_cast<uint64_t>(timescale));
        AMediaFormat_setInt64(outTrack->format, "durationUs", durUs);
    }
    if (!csd0.empty()) {
        AMediaFormat_setBuffer(outTrack->format, "csd-0", csd0.data(), csd0.size());
    }
    outTrack->samples = std::move(samples);
    return true;
}

bool parseMp4(const ByteReader& reader, int64_t totalSize, AMediaFormat* fileFormat,
              std::vector<ExtractorTrack>& tracks) {
    bool sawFtyp = false;
    bool sawMoov = false;
    int64_t p = 0;
    Mp4BoxHeader b;
    while (readBoxHeader(reader, p, totalSize, &b)) {
        if (b.type == kFourCC('f', 't', 'y', 'p')) {
            sawFtyp = true;
        } else if (b.type == kFourCC('m', 'o', 'o', 'v')) {
            sawMoov = true;
            int64_t mp = b.bodyOffset;
            Mp4BoxHeader mb;
            while (readBoxHeader(reader, mp, b.nextOffset, &mb)) {
                if (mb.type == kFourCC('t', 'r', 'a', 'k')) {
                    ExtractorTrack tr;
                    if (parseMp4Track(reader, mb.bodyOffset, mb.nextOffset, &tr)) {
                        tracks.push_back(std::move(tr));
                    }
                }
                mp = mb.nextOffset;
            }
        }
        p = b.nextOffset;
    }
    if (!sawFtyp && !sawMoov) {
        return false;
    }
    AMediaFormat_setString(fileFormat, "mime", "video/mp4");
    return !tracks.empty();
}

struct MuxedSample {
    std::vector<uint8_t> data;
    int64_t ptsUs = 0;
    uint32_t flags = 0;
    uint64_t fileOffset = 0;
};

struct MuxerTrack {
    std::string mime;
    int32_t width = 0;
    int32_t height = 0;
    int32_t sampleRate = 48000;
    int32_t channelCount = 2;
    std::vector<uint8_t> csd0;
    std::vector<MuxedSample> samples;
};

const char* fourCCForMime(const std::string& mime, bool* isVideo) {
    if (mime.rfind("video/", 0) == 0) {
        *isVideo = true;
        if (mime == "video/avc") {
            return "avc1";
        }
        if (mime == "video/hevc") {
            return "hvc1";
        }
        if (mime == "video/x-vnd.on2.vp9") {
            return "vp09";
        }
        if (mime == "video/av01") {
            return "av01";
        }
        return "mp4v";
    }
    *isVideo = false;
    if (mime == "audio/mp4a-latm") {
        return "mp4a";
    }
    if (mime == "audio/opus") {
        return "Opus";
    }
    if (mime == "audio/flac") {
        return "fLaC";
    }
    return "raw ";
}

bool writeAllFd(int fd, const uint8_t* data, size_t len) {
    size_t written = 0;
    while (written < len) {
        const ssize_t n = ::write(fd, data + written, len - written);
        if (n <= 0) {
            return false;
        }
        written += static_cast<size_t>(n);
    }
    return true;
}

} // namespace

struct AMediaExtractor {
    ByteReader reader;
    AMediaFormat* fileFormat = nullptr;
    std::vector<ExtractorTrack> tracks;
    int currentTrack = -1;

    AMediaExtractor() : fileFormat(AMediaFormat_new()) {}
    ~AMediaExtractor() {
        if (fileFormat) {
            AMediaFormat_delete(fileFormat);
        }
    }

    void resetSource() {
        reader.reset();
        tracks.clear();
        currentTrack = -1;
        if (fileFormat) {
            AMediaFormat_clear(fileFormat);
        }
    }

    void pickCurrentTrack() {
        currentTrack = -1;
        int64_t bestPts = std::numeric_limits<int64_t>::max();
        for (size_t i = 0; i < tracks.size(); ++i) {
            const auto& t = tracks[i];
            if (!t.selected || t.cursor >= t.samples.size()) {
                continue;
            }
            const int64_t pts = t.samples[t.cursor].ptsUs;
            if (currentTrack < 0 || pts < bestPts) {
                bestPts = pts;
                currentTrack = static_cast<int>(i);
            }
        }
    }

    media_status_t parseLoadedSource() {
        const int64_t totalSize = reader.size();
        if (totalSize <= 0) {
            return AMEDIA_ERROR_MALFORMED;
        }
        if (parseWav(reader, totalSize, fileFormat, tracks)) {
            return AMEDIA_OK;
        }
        tracks.clear();
        AMediaFormat_clear(fileFormat);
        if (parseMp4(reader, totalSize, fileFormat, tracks)) {
            return AMEDIA_OK;
        }
        tracks.clear();
        AMediaFormat_clear(fileFormat);
        return AMEDIA_ERROR_UNSUPPORTED;
    }
};

struct AMediaMuxer {
    int fd = -1;
    OutputFormat outputFormat = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
    bool started = false;
    bool stopped = false;
    bool hasLocation = false;
    float latitude = 0.0f;
    float longitude = 0.0f;
    int orientationHint = 0;
    std::vector<MuxerTrack> tracks;

    ~AMediaMuxer() {
        if (fd >= 0) {
            ::close(fd);
        }
    }
};

extern "C" {

// --- AMediaDataSource ---

AMediaDataSource* AMediaDataSource_new(void) {
    return new (std::nothrow) AMediaDataSource();
}

void AMediaDataSource_delete(AMediaDataSource* src) {
    if (!src) {
        return;
    }
    AMediaDataSource_close(src);
    delete src;
}

void AMediaDataSource_setUserdata(AMediaDataSource* src, void* userdata) {
    if (!src) {
        return;
    }
    std::lock_guard<std::mutex> lock(src->mu);
    src->userdata = userdata;
}

void AMediaDataSource_setReadAt(AMediaDataSource* src, AMediaDataSourceReadAt readAt) {
    if (!src) {
        return;
    }
    std::lock_guard<std::mutex> lock(src->mu);
    src->readAt = readAt;
}

void AMediaDataSource_setGetSize(AMediaDataSource* src, AMediaDataSourceGetSize getSize) {
    if (!src) {
        return;
    }
    std::lock_guard<std::mutex> lock(src->mu);
    src->getSize = getSize;
}

void AMediaDataSource_setClose(AMediaDataSource* src, AMediaDataSourceClose closeFn) {
    if (!src) {
        return;
    }
    std::lock_guard<std::mutex> lock(src->mu);
    src->closeFn = closeFn;
}

void AMediaDataSource_setGetAvailableSize(AMediaDataSource* src,
                                          AMediaDataSourceGetAvailableSize getAvailableSize) {
    if (!src) {
        return;
    }
    std::lock_guard<std::mutex> lock(src->mu);
    src->getAvailableSize = getAvailableSize;
}

void AMediaDataSource_close(AMediaDataSource* src) {
    if (!src) {
        return;
    }
    AMediaDataSourceClose fn = nullptr;
    void* ud = nullptr;
    {
        std::lock_guard<std::mutex> lock(src->mu);
        if (src->closed) {
            return;
        }
        src->closed = true;
        fn = src->closeFn;
        ud = src->userdata;
    }
    if (fn) {
        fn(ud);
    }
}

// --- AMediaExtractor ---

AMediaExtractor* AMediaExtractor_new(void) {
    return new (std::nothrow) AMediaExtractor();
}

media_status_t AMediaExtractor_delete(AMediaExtractor* ex) {
    if (!ex) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    delete ex;
    return AMEDIA_OK;
}

media_status_t AMediaExtractor_setDataSourceFd(AMediaExtractor* ex, int fd, int64_t offset,
                                               int64_t length) {
    if (!ex || fd < 0 || offset < 0) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    ex->resetSource();
    const int dupFd = ::dup(fd);
    if (dupFd < 0) {
        return AMEDIA_ERROR_IO;
    }
    struct stat st{};
    if (::fstat(dupFd, &st) != 0) {
        ::close(dupFd);
        return AMEDIA_ERROR_IO;
    }
    const int64_t fileSize = static_cast<int64_t>(st.st_size);
    if (offset > fileSize) {
        ::close(dupFd);
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    const int64_t avail = fileSize - offset;
    const int64_t effectiveLen = (length < 0 || length > avail) ? avail : length;
    ex->reader.fd = dupFd;
    ex->reader.ownsFd = true;
    ex->reader.baseOffset = offset;
    ex->reader.length = effectiveLen;
    return ex->parseLoadedSource();
}

media_status_t AMediaExtractor_setDataSource(AMediaExtractor* ex, const char* location) {
    if (!ex || !location || location[0] == '\0') {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    std::string path(location);
    if (path.rfind("http://", 0) == 0 || path.rfind("https://", 0) == 0 ||
        path.rfind("rtsp://", 0) == 0) {
        return AMEDIA_ERROR_UNSUPPORTED;
    }
    if (path.rfind("file://", 0) == 0) {
        path = path.substr(7);
    }
    const int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        return AMEDIA_ERROR_IO;
    }
    const media_status_t st = AMediaExtractor_setDataSourceFd(ex, fd, 0, -1);
    ::close(fd);
    return st;
}

media_status_t AMediaExtractor_setDataSourceCustom(AMediaExtractor* ex, AMediaDataSource* src) {
    if (!ex || !src || !src->readAt || !src->getSize) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    ex->resetSource();
    ex->reader.custom = src;
    return ex->parseLoadedSource();
}

size_t AMediaExtractor_getTrackCount(AMediaExtractor* ex) {
    return ex ? ex->tracks.size() : 0;
}

AMediaFormat* AMediaExtractor_getTrackFormat(AMediaExtractor* ex, size_t idx) {
    if (!ex || idx >= ex->tracks.size() || !ex->tracks[idx].format) {
        return nullptr;
    }
    AMediaFormat* copy = AMediaFormat_new();
    if (!copy) {
        return nullptr;
    }
    AMediaFormat_copy(copy, ex->tracks[idx].format);
    return copy;
}

AMediaFormat* AMediaExtractor_getFileFormat(AMediaExtractor* ex) {
    if (!ex || !ex->fileFormat) {
        return nullptr;
    }
    AMediaFormat* copy = AMediaFormat_new();
    if (!copy) {
        return nullptr;
    }
    AMediaFormat_copy(copy, ex->fileFormat);
    return copy;
}

media_status_t AMediaExtractor_selectTrack(AMediaExtractor* ex, size_t idx) {
    if (!ex || idx >= ex->tracks.size()) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    ex->tracks[idx].selected = true;
    ex->pickCurrentTrack();
    return AMEDIA_OK;
}

media_status_t AMediaExtractor_unselectTrack(AMediaExtractor* ex, size_t idx) {
    if (!ex || idx >= ex->tracks.size()) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    ex->tracks[idx].selected = false;
    ex->pickCurrentTrack();
    return AMEDIA_OK;
}

ssize_t AMediaExtractor_readSampleData(AMediaExtractor* ex, uint8_t* buffer, size_t capacity) {
    if (!ex || !buffer || ex->currentTrack < 0) {
        return -1;
    }
    const auto& tr = ex->tracks[static_cast<size_t>(ex->currentTrack)];
    if (tr.cursor >= tr.samples.size()) {
        return -1;
    }
    const auto& s = tr.samples[tr.cursor];
    if (capacity < s.size) {
        return -1;
    }
    return ex->reader.readAt(static_cast<int64_t>(s.offset), buffer, s.size);
}

uint32_t AMediaExtractor_getSampleFlags(AMediaExtractor* ex) {
    if (!ex || ex->currentTrack < 0) {
        return 0;
    }
    const auto& tr = ex->tracks[static_cast<size_t>(ex->currentTrack)];
    if (tr.cursor >= tr.samples.size()) {
        return 0;
    }
    return tr.samples[tr.cursor].flags;
}

int AMediaExtractor_getSampleTrackIndex(AMediaExtractor* ex) {
    if (!ex || ex->currentTrack < 0) {
        return -1;
    }
    return ex->currentTrack;
}

int64_t AMediaExtractor_getSampleTime(AMediaExtractor* ex) {
    if (!ex || ex->currentTrack < 0) {
        return -1;
    }
    const auto& tr = ex->tracks[static_cast<size_t>(ex->currentTrack)];
    if (tr.cursor >= tr.samples.size()) {
        return -1;
    }
    return tr.samples[tr.cursor].ptsUs;
}

int64_t AMediaExtractor_getSampleSize(AMediaExtractor* ex) {
    if (!ex || ex->currentTrack < 0) {
        return -1;
    }
    const auto& tr = ex->tracks[static_cast<size_t>(ex->currentTrack)];
    if (tr.cursor >= tr.samples.size()) {
        return -1;
    }
    return static_cast<int64_t>(tr.samples[tr.cursor].size);
}

bool AMediaExtractor_advance(AMediaExtractor* ex) {
    if (!ex || ex->currentTrack < 0) {
        return false;
    }
    auto& tr = ex->tracks[static_cast<size_t>(ex->currentTrack)];
    if (tr.cursor < tr.samples.size()) {
        tr.cursor++;
    }
    ex->pickCurrentTrack();
    return ex->currentTrack >= 0;
}

media_status_t AMediaExtractor_seekTo(AMediaExtractor* ex, int64_t seekPosUs, SeekMode mode) {
    if (!ex) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    for (auto& tr : ex->tracks) {
        if (!tr.selected) {
            continue;
        }
        if (tr.samples.empty()) {
            tr.cursor = 0;
            continue;
        }
        // Collect sync sample indices (fallback to all samples if none flagged).
        std::vector<size_t> syncs;
        for (size_t i = 0; i < tr.samples.size(); ++i) {
            if (tr.samples[i].flags & AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC) {
                syncs.push_back(i);
            }
        }
        if (syncs.empty()) {
            for (size_t i = 0; i < tr.samples.size(); ++i) {
                syncs.push_back(i);
            }
        }

        size_t prevIdx = syncs.front();
        size_t nextIdx = syncs.back();
        for (size_t si : syncs) {
            if (tr.samples[si].ptsUs <= seekPosUs) {
                prevIdx = si;
            }
            if (tr.samples[si].ptsUs >= seekPosUs) {
                nextIdx = si;
                break;
            }
        }
        if (mode == AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC) {
            tr.cursor = prevIdx;
        } else if (mode == AMEDIAEXTRACTOR_SEEK_NEXT_SYNC) {
            tr.cursor = nextIdx;
        } else {
            const int64_t dPrev = std::llabs(seekPosUs - tr.samples[prevIdx].ptsUs);
            const int64_t dNext = std::llabs(tr.samples[nextIdx].ptsUs - seekPosUs);
            tr.cursor = (dNext < dPrev) ? nextIdx : prevIdx;
        }
    }
    ex->pickCurrentTrack();
    return AMEDIA_OK;
}

int64_t AMediaExtractor_getCachedDuration(AMediaExtractor* ex) {
    if (!ex || ex->tracks.empty()) {
        return -1;
    }
    int64_t maxDur = 0;
    for (const auto& tr : ex->tracks) {
        int64_t d = 0;
        if (tr.format && AMediaFormat_getInt64(tr.format, "durationUs", &d)) {
            maxDur = std::max(maxDur, d);
        } else if (!tr.samples.empty()) {
            maxDur = std::max(maxDur, tr.samples.back().ptsUs);
        }
    }
    return maxDur;
}

// --- AMediaMuxer ---

AMediaMuxer* AMediaMuxer_new(int fd, OutputFormat format) {
    if (fd < 0) {
        return nullptr;
    }
    if (format != AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4 &&
        format != AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP) {
        return nullptr;
    }
    const int dupFd = ::dup(fd);
    if (dupFd < 0) {
        return nullptr;
    }
    AMediaMuxer* m = new (std::nothrow) AMediaMuxer();
    if (!m) {
        ::close(dupFd);
        return nullptr;
    }
    m->fd = dupFd;
    m->outputFormat = format;
    return m;
}

media_status_t AMediaMuxer_delete(AMediaMuxer* muxer) {
    if (!muxer) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    delete muxer;
    return AMEDIA_OK;
}

media_status_t AMediaMuxer_setLocation(AMediaMuxer* muxer, float latitude, float longitude) {
    if (!muxer || muxer->started) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    if (latitude < -90.0f || latitude > 90.0f || longitude < -180.0f || longitude > 180.0f) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    muxer->hasLocation = true;
    muxer->latitude = latitude;
    muxer->longitude = longitude;
    return AMEDIA_OK;
}

media_status_t AMediaMuxer_setOrientationHint(AMediaMuxer* muxer, int degrees) {
    if (!muxer || muxer->started) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    muxer->orientationHint = degrees;
    return AMEDIA_OK;
}

ssize_t AMediaMuxer_addTrack(AMediaMuxer* muxer, const AMediaFormat* format) {
    if (!muxer || !format || muxer->started) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    AMediaFormat* fmt = const_cast<AMediaFormat*>(format);
    const char* mime = nullptr;
    if (!AMediaFormat_getString(fmt, "mime", &mime) || !mime) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    MuxerTrack tr;
    tr.mime = mime;
    AMediaFormat_getInt32(fmt, "width", &tr.width);
    AMediaFormat_getInt32(fmt, "height", &tr.height);
    AMediaFormat_getInt32(fmt, "sample-rate", &tr.sampleRate);
    AMediaFormat_getInt32(fmt, "channel-count", &tr.channelCount);
    void* csdPtr = nullptr;
    size_t csdLen = 0;
    if (AMediaFormat_getBuffer(fmt, "csd-0", &csdPtr, &csdLen) && csdPtr && csdLen > 0) {
        const uint8_t* bytes = static_cast<const uint8_t*>(csdPtr);
        tr.csd0.assign(bytes, bytes + csdLen);
    }
    muxer->tracks.push_back(std::move(tr));
    return static_cast<ssize_t>(muxer->tracks.size() - 1);
}

media_status_t AMediaMuxer_start(AMediaMuxer* muxer) {
    if (!muxer || muxer->started || muxer->stopped || muxer->tracks.empty()) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    muxer->started = true;
    return AMEDIA_OK;
}

media_status_t AMediaMuxer_writeSampleData(AMediaMuxer* muxer, size_t trackIdx, const uint8_t* data,
                                           const AMediaCodecBufferInfo* info) {
    if (!muxer || !muxer->started || muxer->stopped) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    if (trackIdx >= muxer->tracks.size() || !info || info->offset < 0 || info->size < 0) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    if (info->size > 0 && !data) {
        return AMEDIA_ERROR_INVALID_PARAMETER;
    }
    MuxedSample s;
    s.ptsUs = info->presentationTimeUs;
    s.flags = info->flags;
    if (info->size > 0) {
        s.data.assign(data + info->offset, data + info->offset + info->size);
    }
    muxer->tracks[trackIdx].samples.push_back(std::move(s));
    return AMEDIA_OK;
}

media_status_t AMediaMuxer_stop(AMediaMuxer* muxer) {
    if (!muxer || !muxer->started || muxer->stopped) {
        return AMEDIA_ERROR_INVALID_OPERATION;
    }
    muxer->stopped = true;

    std::vector<uint8_t> out;

    // 1. ftyp box
    {
        std::vector<uint8_t> ftyp;
        if (muxer->outputFormat == AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP) {
            writeFourCC(ftyp, "3gp4");
            writeBe32(ftyp, 512);
            writeFourCC(ftyp, "3gp4");
            writeFourCC(ftyp, "isom");
        } else {
            writeFourCC(ftyp, "isom");
            writeBe32(ftyp, 512);
            writeFourCC(ftyp, "isom");
            writeFourCC(ftyp, "iso2");
            writeFourCC(ftyp, "mp41");
        }
        writeBox(out, "ftyp", ftyp);
    }

    // 2. mdat box (record exact file offset of each sample)
    uint64_t totalMdatPayload = 0;
    for (const auto& tr : muxer->tracks) {
        for (const auto& s : tr.samples) {
            totalMdatPayload += s.data.size();
        }
    }
    const bool largeMdat = (totalMdatPayload + 8ULL) > std::numeric_limits<uint32_t>::max();
    if (!largeMdat) {
        writeBe32(out, static_cast<uint32_t>(8ULL + totalMdatPayload));
        writeFourCC(out, "mdat");
    } else {
        writeBe32(out, 1u);
        writeFourCC(out, "mdat");
        writeBe64(out, 16ULL + totalMdatPayload);
    }
    for (auto& tr : muxer->tracks) {
        for (auto& s : tr.samples) {
            s.fileOffset = out.size();
            out.insert(out.end(), s.data.begin(), s.data.end());
        }
    }

    // 3. moov box
    int64_t movieDurationUs = 0;
    for (const auto& tr : muxer->tracks) {
        if (!tr.samples.empty()) {
            const int64_t lastPts = tr.samples.back().ptsUs;
            const int64_t delta =
                tr.samples.size() >= 2
                    ? std::max<int64_t>(1, tr.samples.back().ptsUs -
                                               tr.samples[tr.samples.size() - 2].ptsUs)
                    : 33333;
            movieDurationUs = std::max(movieDurationUs, lastPts + delta);
        }
    }

    std::vector<uint8_t> moov;
    // mvhd (version 0, timescale 1000)
    {
        std::vector<uint8_t> mvhd;
        writeBe32(mvhd, 0);    // creation_time
        writeBe32(mvhd, 0);    // modification_time
        writeBe32(mvhd, 1000); // timescale
        writeBe32(mvhd, static_cast<uint32_t>(std::max<int64_t>(0, movieDurationUs / 1000)));
        writeBe32(mvhd, 0x00010000);    // rate 1.0
        writeBe16(mvhd, 0x0100);        // volume 1.0
        mvhd.insert(mvhd.end(), 10, 0); // reserved
        // Identity matrix (36 bytes)
        const uint32_t matrix[9] = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
        for (uint32_t v : matrix) {
            writeBe32(mvhd, v);
        }
        mvhd.insert(mvhd.end(), 24, 0);                                   // pre_defined
        writeBe32(mvhd, static_cast<uint32_t>(muxer->tracks.size() + 1)); // next_track_ID
        writeFullBox(moov, "mvhd", 0, 0, mvhd);
    }

    for (size_t ti = 0; ti < muxer->tracks.size(); ++ti) {
        const auto& tr = muxer->tracks[ti];
        bool isVideo = false;
        const char* sampleTag = fourCCForMime(tr.mime, &isVideo);

        int64_t trackDurUs = 0;
        if (!tr.samples.empty()) {
            const int64_t delta =
                tr.samples.size() >= 2
                    ? std::max<int64_t>(1, tr.samples.back().ptsUs -
                                               tr.samples[tr.samples.size() - 2].ptsUs)
                    : 33333;
            trackDurUs = std::max<int64_t>(0, tr.samples.back().ptsUs + delta);
        }

        std::vector<uint8_t> trak;

        // tkhd
        {
            std::vector<uint8_t> tkhd;
            writeBe32(tkhd, 0);                                        // creation_time
            writeBe32(tkhd, 0);                                        // modification_time
            writeBe32(tkhd, static_cast<uint32_t>(ti + 1));            // track_ID
            writeBe32(tkhd, 0);                                        // reserved
            writeBe32(tkhd, static_cast<uint32_t>(trackDurUs / 1000)); // duration (movie timescale)
            tkhd.insert(tkhd.end(), 8, 0);                             // reserved
            writeBe16(tkhd, 0);                                        // layer
            writeBe16(tkhd, 0);                                        // alternate_group
            writeBe16(tkhd, isVideo ? 0 : 0x0100);                     // volume
            writeBe16(tkhd, 0);                                        // reserved
            uint32_t matrix[9] = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
            if (isVideo && muxer->orientationHint == 90) {
                matrix[0] = 0;
                matrix[1] = 0x00010000;
                matrix[3] = 0xFFFF0000;
                matrix[4] = 0;
            } else if (isVideo && muxer->orientationHint == 180) {
                matrix[0] = 0xFFFF0000;
                matrix[4] = 0xFFFF0000;
            } else if (isVideo && muxer->orientationHint == 270) {
                matrix[0] = 0;
                matrix[1] = 0xFFFF0000;
                matrix[3] = 0x00010000;
                matrix[4] = 0;
            }
            for (uint32_t v : matrix) {
                writeBe32(tkhd, v);
            }
            writeBe32(tkhd, static_cast<uint32_t>(std::max(0, tr.width)) << 16);
            writeBe32(tkhd, static_cast<uint32_t>(std::max(0, tr.height)) << 16);
            writeFullBox(trak, "tkhd", 0, 3, tkhd);
        }

        // mdia
        std::vector<uint8_t> mdia;
        // mdhd (version 1 for 64-bit microsecond duration)
        {
            std::vector<uint8_t> mdhd;
            writeBe64(mdhd, 0);       // creation
            writeBe64(mdhd, 0);       // modification
            writeBe32(mdhd, 1000000); // timescale = 1 MHz (microseconds)
            writeBe64(mdhd, static_cast<uint64_t>(trackDurUs));
            writeBe16(mdhd, 0x55C4); // language "und"
            writeBe16(mdhd, 0);
            writeFullBox(mdia, "mdhd", 1, 0, mdhd);
        }
        // hdlr
        {
            std::vector<uint8_t> hdlr;
            writeBe32(hdlr, 0); // pre_defined
            writeFourCC(hdlr, isVideo ? "vide" : "soun");
            hdlr.insert(hdlr.end(), 12, 0);
            const char* name = isVideo ? "VideoHandler" : "SoundHandler";
            hdlr.insert(hdlr.end(), name, name + std::strlen(name) + 1);
            writeFullBox(mdia, "hdlr", 0, 0, hdlr);
        }

        // minf -> stbl
        std::vector<uint8_t> stbl;
        // stsd
        {
            std::vector<uint8_t> entry;
            entry.insert(entry.end(), 6, 0); // reserved
            writeBe16(entry, 1);             // data_reference_index
            if (isVideo) {
                entry.insert(entry.end(), 16, 0); // pre_defined + reserved
                writeBe16(entry, static_cast<uint16_t>(std::max(0, tr.width)));
                writeBe16(entry, static_cast<uint16_t>(std::max(0, tr.height)));
                writeBe32(entry, 0x00480000);     // horizresolution 72 dpi
                writeBe32(entry, 0x00480000);     // vertresolution 72 dpi
                writeBe32(entry, 0);              // reserved
                writeBe16(entry, 1);              // frame_count
                entry.insert(entry.end(), 32, 0); // compressorname
                writeBe16(entry, 0x0018);         // depth
                writeBe16(entry, 0xFFFF);         // pre_defined = -1
            } else {
                entry.insert(entry.end(), 8, 0); // reserved
                writeBe16(entry, static_cast<uint16_t>(std::max(1, tr.channelCount)));
                writeBe16(entry, 16); // sampleSize
                writeBe16(entry, 0);  // pre_defined
                writeBe16(entry, 0);  // reserved
                writeBe32(entry, static_cast<uint32_t>(std::max(1, tr.sampleRate)) << 16);
            }
            if (!tr.csd0.empty()) {
                const char* csdTag = isVideo ? "avcC" : "esds";
                writeBox(entry, csdTag, tr.csd0);
            }
            // Store exact MIME string in private 'shrz' child box for lossless round-trip.
            std::vector<uint8_t> mimeBytes(tr.mime.begin(), tr.mime.end());
            writeBox(entry, "shrz", mimeBytes);

            std::vector<uint8_t> stsdBody;
            writeBe32(stsdBody, 1); // entry_count
            writeBox(stsdBody, sampleTag, entry);
            writeFullBox(stbl, "stsd", 0, 0, stsdBody);
        }

        // stts
        {
            std::vector<uint8_t> stts;
            writeBe32(stts, static_cast<uint32_t>(tr.samples.size()));
            for (size_t i = 0; i < tr.samples.size(); ++i) {
                int64_t delta = 33333;
                if (i + 1 < tr.samples.size()) {
                    delta = std::max<int64_t>(0, tr.samples[i + 1].ptsUs - tr.samples[i].ptsUs);
                } else if (i > 0) {
                    delta = std::max<int64_t>(0, tr.samples[i].ptsUs - tr.samples[i - 1].ptsUs);
                }
                writeBe32(stts, 1);
                writeBe32(stts, static_cast<uint32_t>(delta));
            }
            writeFullBox(stbl, "stts", 0, 0, stts);
        }

        // ctts (preserve non-zero initial PTS offset)
        if (!tr.samples.empty() && tr.samples.front().ptsUs != 0) {
            std::vector<uint8_t> ctts;
            writeBe32(ctts, 1);
            writeBe32(ctts, static_cast<uint32_t>(tr.samples.size()));
            writeBe32(ctts, static_cast<uint32_t>(static_cast<int32_t>(tr.samples.front().ptsUs)));
            writeFullBox(stbl, "ctts", 1, 0, ctts);
        }

        // stss (only if any samples exist)
        {
            std::vector<uint32_t> syncs;
            for (size_t i = 0; i < tr.samples.size(); ++i) {
                if (tr.samples[i].flags & AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC) {
                    syncs.push_back(static_cast<uint32_t>(i + 1));
                }
            }
            if (!syncs.empty() && syncs.size() < tr.samples.size()) {
                std::vector<uint8_t> stss;
                writeBe32(stss, static_cast<uint32_t>(syncs.size()));
                for (uint32_t s : syncs) {
                    writeBe32(stss, s);
                }
                writeFullBox(stbl, "stss", 0, 0, stss);
            }
        }

        // stsc: 1 sample per chunk
        {
            std::vector<uint8_t> stsc;
            if (!tr.samples.empty()) {
                writeBe32(stsc, 1);
                writeBe32(stsc, 1); // first_chunk
                writeBe32(stsc, 1); // samples_per_chunk
                writeBe32(stsc, 1); // sample_description_index
            } else {
                writeBe32(stsc, 0);
            }
            writeFullBox(stbl, "stsc", 0, 0, stsc);
        }

        // stsz
        {
            std::vector<uint8_t> stsz;
            writeBe32(stsz, 0); // variable sample sizes
            writeBe32(stsz, static_cast<uint32_t>(tr.samples.size()));
            for (const auto& s : tr.samples) {
                writeBe32(stsz, static_cast<uint32_t>(s.data.size()));
            }
            writeFullBox(stbl, "stsz", 0, 0, stsz);
        }

        // co64 (64-bit chunk offsets)
        {
            std::vector<uint8_t> co64;
            writeBe32(co64, static_cast<uint32_t>(tr.samples.size()));
            for (const auto& s : tr.samples) {
                writeBe64(co64, s.fileOffset);
            }
            writeFullBox(stbl, "co64", 0, 0, co64);
        }

        std::vector<uint8_t> minf;
        writeBox(minf, "stbl", stbl);
        writeBox(mdia, "minf", minf);
        writeBox(trak, "mdia", mdia);
        writeBox(moov, "trak", trak);
    }

    if (muxer->hasLocation) {
        char locStr[64];
        std::snprintf(locStr, sizeof(locStr), "%+08.4f%+09.4f/", muxer->latitude, muxer->longitude);
        std::vector<uint8_t> xyz;
        const size_t len = std::strlen(locStr);
        writeBe16(xyz, static_cast<uint16_t>(len));
        writeBe16(xyz, 0x15C7);
        xyz.insert(xyz.end(), locStr, locStr + len);
        std::vector<uint8_t> udta;
        const char xyzTag[4] = {static_cast<char>(0xA9), 'x', 'y', 'z'};
        writeBox(udta, xyzTag, xyz);
        writeBox(moov, "udta", udta);
    }

    writeBox(out, "moov", moov);

    if (::lseek(muxer->fd, 0, SEEK_SET) < 0) {
        return AMEDIA_ERROR_IO;
    }
    if (!writeAllFd(muxer->fd, out.data(), out.size())) {
        return AMEDIA_ERROR_IO;
    }
    return AMEDIA_OK;
}

} // extern "C"
