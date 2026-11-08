// SPDX-License-Identifier: GPL-3.0-only
// Original explicit Linux-to-Darwin adapter. ABI: pinned Android14
// sys/sendfile.h. Semantics: Linux sendfile(2), with documented unsupported
// shared-cursor and non-regular-input cases rather than a Darwin ABI alias.
#include "shiraz/darwin/guest/transfer.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <sys/stat.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
ssize_t fail(int error) { errno = error; return -1; }
class Transfer {
  std::atomic<bool> warned_null_{false};
public:
  ssize_t send(int output, int input, int64_t *offset, size_t count) {
    static_assert(sizeof(off_t) == 8 && sizeof(ssize_t) == 8);
    int in_flags = ::fcntl(input, F_GETFL);
    if (in_flags == -1) return -1;
    int out_flags = ::fcntl(output, F_GETFL);
    if (out_flags == -1) return -1;
    if ((in_flags & O_ACCMODE) == O_WRONLY || (out_flags & O_ACCMODE) == O_RDONLY)
      return fail(EBADF);
    if (out_flags & O_APPEND) return fail(EINVAL);
    struct stat in_stat{}, out_stat{};
    if (::fstat(input, &in_stat) || ::fstat(output, &out_stat)) return -1;
    if (!S_ISREG(in_stat.st_mode))
      return fail(offset && (S_ISFIFO(in_stat.st_mode) || S_ISSOCK(in_stat.st_mode)) ? ESPIPE : EINVAL);
    if (S_ISDIR(out_stat.st_mode)) return fail(EINVAL);
    if (S_ISREG(out_stat.st_mode) && in_stat.st_dev == out_stat.st_dev && in_stat.st_ino == out_stat.st_ino)
      return fail(EINVAL); // No unverified same-file/overlapping-copy behavior.
    if (!offset) {
      if (!count) return 0;
      if (!warned_null_.exchange(true, std::memory_order_relaxed))
        std::fprintf(stderr, "SHIRAZ_GUEST_TRANSFER_UNSUPPORTED null_offset=shared_cursor_atomicity\n");
      return fail(ENOSYS);
    }
    int64_t position;
    std::memcpy(&position, offset, sizeof(position));
    if (position < 0) return fail(EINVAL);
    if (!count) return 0;
    // One bounded pread and one write. Native partial writes, EAGAIN and
    // EINTR are returned as observed; no invisible retry can duplicate bytes.
    // pread never moves input's open-file-description cursor.
    std::array<unsigned char, 65536> bytes;
    size_t amount = std::min(count, bytes.size());
    amount = std::min(amount, size_t(std::numeric_limits<int64_t>::max() - position));
    if (!amount) return position >= in_stat.st_size ? 0 : fail(EOVERFLOW);
    ssize_t read = ::pread(input, bytes.data(), amount, off_t(position));
    if (read <= 0) return read;
    ssize_t written = ::write(output, bytes.data(), size_t(read));
    if (written <= 0) return written;
    if (written > read) return fail(EIO); // Defensive invariant, not a retry.
    position += written;
    std::memcpy(offset, &position, sizeof(position));
    return written;
  }
};
} // namespace
std::vector<ProviderExport> transfer_exports() {
  auto state = std::make_shared<Transfer>();
  std::vector<ProviderExport> result;
  for (const char *name : {"sendfile", "sendfile64"}) {
    void *entry = closure([state](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      int output = cursor.read<int>(), input = cursor.read<int>();
      auto *offset = cursor.read<int64_t *>();
      size_t count = cursor.read<size_t>();
      HostErrno preserve;
      ssize_t value = state->send(output, input, offset, count);
      if (value == -1) *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
      return_value(capture, value);
    });
    result.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
                      "linux-off64/explicit-pread-write-partial-null-unsupported", false});
  }
  return result;
}
} // namespace shiraz::guest
