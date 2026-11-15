// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <fcntl.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string>

namespace shiraz {

// Opt-in export of the presented frame to an embedding launcher
// (SHIRAZ_EMBED_FRAME). The launcher creates the file, owner-only, in a
// private per-session directory; the runtime maps it shared and writes each
// presented frame under an exclusive flock. Readers take a shared flock while
// copying, so a frame is never seen half-written. The file is sized once to
// its full capacity (sparse) and never shrinks, so a mapping stays valid.
//
// Layout, little-endian: a 4 KiB header page, then BGRA pixels
// (SDL_PIXELFORMAT_ARGB8888 words), rows of `stride` bytes.
//   0 magic 'SHZF'   4 version   8 revision (u64, +1 per published frame)
//  16 width  20 height  24 stride  28 flags (bit 0 detached)  32 pid
struct FrameExportLayout {
    static constexpr uint32_t magic = 0x465a4853; // "SHZF"
    static constexpr uint32_t version = 1;
    static constexpr size_t header = 4096;
    static constexpr uint64_t maxPixels = 16ull * 1024 * 1024;
    static constexpr int maxSide = 8192;
    static constexpr size_t capacity = header + maxPixels * 4;
    static constexpr uint32_t detachedFlag = 1;
};

class FrameExport {
    int fd_ = -1;
    unsigned char* map_ = nullptr;
    uint64_t revision_ = 0;
    uint32_t flags_ = 0;

    template <typename T> void put(size_t offset, T value) {
        std::memcpy(map_ + offset, &value, sizeof value);
    }
    bool lock(bool wait) {
        while (flock(fd_, LOCK_EX | (wait ? 0 : LOCK_NB))) {
            if (errno == EINTR)
                continue;
            if (errno == EWOULDBLOCK)
                return false;
            throw std::runtime_error("Cannot lock embedded frame");
        }
        return true;
    }
    void unlock() {
        while (flock(fd_, LOCK_UN) && errno == EINTR) {
        }
    }

  public:
    explicit FrameExport(const char* path) {
        if (!path || !*path)
            throw std::invalid_argument("Embedded frame requires a path");
        fd_ = open(path, O_RDWR | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
        struct stat info{};
        if (fd_ < 0 || fstat(fd_, &info) || !S_ISREG(info.st_mode) || info.st_uid != geteuid() ||
            (info.st_mode & 077) || info.st_nlink != 1 ||
            uint64_t(info.st_size) > FrameExportLayout::capacity) {
            if (fd_ >= 0)
                close(fd_);
            fd_ = -1;
            throw std::invalid_argument(
                "Embedded frame requires an owner-only regular file within capacity");
        }
        if (uint64_t(info.st_size) < FrameExportLayout::capacity &&
            ftruncate(fd_, off_t(FrameExportLayout::capacity))) {
            close(fd_);
            fd_ = -1;
            throw std::runtime_error("Cannot size embedded frame");
        }
        void* map =
            mmap(nullptr, FrameExportLayout::capacity, PROT_READ | PROT_WRITE, MAP_SHARED, fd_, 0);
        if (map == MAP_FAILED) {
            close(fd_);
            fd_ = -1;
            throw std::runtime_error("Cannot map embedded frame");
        }
        map_ = static_cast<unsigned char*>(map);
        lock(true);
        std::memset(map_, 0, 64);
        put<uint32_t>(4, FrameExportLayout::version);
        put<uint32_t>(32, uint32_t(getpid()));
        put<uint32_t>(0, FrameExportLayout::magic);
        unlock();
    }
    ~FrameExport() {
        if (map_)
            munmap(map_, FrameExportLayout::capacity);
        if (fd_ >= 0)
            close(fd_);
    }
    FrameExport(const FrameExport&) = delete;
    FrameExport& operator=(const FrameExport&) = delete;

    // Copy one frame of ARGB8888 words. Returns false, publishing nothing,
    // when a reader holds the lock; the caller retries on a later loop.
    bool publish(const uint32_t* pixels, int width, int height) {
        if (!pixels || width <= 0 || height <= 0 || width > FrameExportLayout::maxSide ||
            height > FrameExportLayout::maxSide ||
            uint64_t(width) * uint64_t(height) > FrameExportLayout::maxPixels)
            throw std::invalid_argument("Embedded frame bounds");
        if (!lock(false))
            return false;
        const size_t stride = size_t(width) * 4;
        std::memcpy(map_ + FrameExportLayout::header, pixels, stride * size_t(height));
        put<uint32_t>(16, uint32_t(width));
        put<uint32_t>(20, uint32_t(height));
        put<uint32_t>(24, uint32_t(stride));
        put<uint32_t>(28, flags_);
        put<uint64_t>(8, ++revision_);
        unlock();
        return true;
    }
    void setDetached(bool detached) {
        flags_ = detached ? FrameExportLayout::detachedFlag : 0;
        lock(true);
        put<uint32_t>(28, flags_);
        put<uint64_t>(8, ++revision_);
        unlock();
    }
    uint64_t revision() const {
        return revision_;
    }
};

} // namespace shiraz
