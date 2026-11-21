// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <functional>
#include <new>
#include <utility>
#include <string>
#include <mutex>
#include <cstring>
#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <unistd.h>
namespace shiraz::guest {
// Bounded per-open snapshot reader. Factory must supply a complete formatted view.
// Refreshes on seek to a different position, including rewind after a read.
// No global atomic-snapshot claim, signal-handler use, or fork inheritance.
class MapsStream {
  public:
    using Factory = std::function<int(std::string&)>;
    explicit MapsStream(Factory f) : factory_(std::move(f)), owner_(getpid()) {}
    int read(void* buffer, size_t count, size_t* amount) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (!amount || (!buffer && count))
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        if (!count) {
            *amount = 0;
            return 0;
        }
        int error = ensure();
        if (error)
            return error;
        size_t n = copy(position_, buffer, count);
        position_ += n;
        *amount = n;
        return 0;
    }
    int pread(int64_t offset, void* buffer, size_t count, size_t* amount) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (offset < 0 || !amount || (!buffer && count))
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        if (!count) {
            *amount = 0;
            return 0;
        }
        int error = ensure();
        if (error)
            return error;
        *amount = copy(offset, buffer, count);
        return 0;
    }
    int seek(int64_t offset, int origin, int64_t* result) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (!result || (origin != SEEK_SET && origin != SEEK_CUR))
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        int64_t next;
        if (__builtin_add_overflow(origin == SEEK_CUR ? position_ : int64_t(0), offset, &next) ||
            next < 0)
            return EINVAL;
        if (next != position_) {
            ready_ = false;
            text_.clear();
        }
        position_ = next;
        *result = next;
        return 0;
    }

  private:
    int ensure() noexcept {
        if (ready_)
            return 0;
        try {
            std::string candidate;
            int error = factory_ ? factory_(candidate) : ENOSYS;
            if (error)
                return error;
            if (candidate.size() > 8 * 1024 * 1024)
                return EFBIG;
            if (candidate.find('\0') != std::string::npos ||
                (!candidate.empty() && candidate.back() != '\n'))
                return EINVAL;
            text_ = std::move(candidate);
            ready_ = true;
            return 0;
        } catch (const std::bad_alloc&) {
            return ENOMEM;
        } catch (...) {
            return EIO;
        }
    }
    size_t copy(int64_t offset, void* buffer, size_t count) const noexcept {
        if (uint64_t(offset) >= text_.size())
            return 0;
        size_t n = std::min({count, size_t(1024 * 1024), text_.size() - size_t(offset)});
        if (n)
            std::memcpy(buffer, text_.data() + offset, n);
        return n;
    }
    Factory factory_;
    const pid_t owner_;
    std::mutex mutex_;
    int64_t position_ = 0;
    bool ready_ = false;
    std::string text_;
};
} // namespace shiraz::guest
