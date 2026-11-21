// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/process_arguments.hpp"
#include <cstddef>
#include <cstdint>
#include <cerrno>
#include <limits>
#include <mutex>
#include <unistd.h>

namespace shiraz::guest {
// One open-file description, not an OS file descriptor. Share this object for
// dup aliases and create a new object for each independent open. Operations are
// not async-signal-safe; the eventual descriptor adapter must reject them in
// guest signal handlers and must coordinate descriptor close/reuse separately.
class ProcessArgumentsStream {
  public:
    ProcessArgumentsStream() noexcept : owner_(getpid()) {}
    ProcessArgumentsStream(const ProcessArgumentsStream&) = delete;
    ProcessArgumentsStream& operator=(const ProcessArgumentsStream&) = delete;

    int read(void* destination, size_t count, size_t* read_bytes) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (!read_bytes || (!destination && count))
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        size_t amount = 0;
        int error = shiraz_process_arguments_read(uint64_t(position_), destination, bounded(count),
                                                  &amount);
        if (error)
            return error;
        if (amount > uint64_t(std::numeric_limits<int64_t>::max() - position_))
            return EOVERFLOW;
        position_ += int64_t(amount);
        *read_bytes = amount;
        return 0;
    }
    int pread(int64_t offset, void* destination, size_t count, size_t* read_bytes) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (offset < 0 || !read_bytes || (!destination && count))
            return EINVAL;
        return shiraz_process_arguments_read(uint64_t(offset), destination, bounded(count),
                                             read_bytes);
    }
    int seek(int64_t offset, int origin, int64_t* result) noexcept {
        if (owner_ != getpid())
            return ECHILD;
        if (!result)
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        // proc cmdline has inode size zero; generic Linux SEEK_END is based on it.
        if (origin == 3 || origin == 4)
            return ENXIO;
        if (origin < 0 || origin > 2)
            return EINVAL;
        int64_t base = origin == 1 ? position_ : 0;
        int64_t next = 0;
        if (__builtin_add_overflow(base, offset, &next) || next < 0)
            return EINVAL;
        position_ = next;
        *result = next;
        return 0;
    }

  private:
    static size_t bounded(size_t count) noexcept {
        return count > 1024 * 1024 ? 1024 * 1024 : count;
    }
    const pid_t owner_;
    std::mutex mutex_;
    int64_t position_ = 0;
};
} // namespace shiraz::guest
