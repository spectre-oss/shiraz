// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/math.hpp"
#include "shiraz/darwin/guest/log.hpp"
#include "shiraz/darwin/guest/properties.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/process.hpp"
#include "shiraz/darwin/guest/libc_scalar.hpp"
#include "shiraz/darwin/guest/quad.hpp"
#include "shiraz/darwin/guest/transfer.hpp"
#include "shiraz/darwin/guest/fs.hpp"
#include "shiraz/darwin/guest/wideformat.hpp"
#include "shiraz/darwin/guest/fortify.hpp"
#include "shiraz/darwin/guest/core_services.hpp"
#include "shiraz/darwin/guest/accounts.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/networking.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/cxx.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include "shiraz/darwin/guest/multibyte.hpp"
#include "shiraz/darwin/guest/time.hpp"
#include "shiraz/darwin/guest/system.hpp"
#include "shiraz/darwin/guest/scan.hpp"
#include "shiraz/darwin/guest/jump.hpp"
#include "shiraz/darwin/guest/zlib.hpp"
#include "shiraz/darwin/guest/stdio.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/sync.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "guest_io.hpp"
#include <algorithm>
#include <cstring>
#include <map>
#include <mutex>
#include <sys/mman.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr uintptr_t minimum_address = uintptr_t(1) << 32;
constexpr size_t maximum_mapping = size_t(256) << 20;
constexpr size_t maximum_total = size_t(1) << 30;
constexpr size_t maximum_ranges = 4096;
int fail(int error) {
  errno = error;
  return -1;
}
int protection(int guest) {
  if (guest & ~(b::kPROT_READ | b::kPROT_WRITE | b::kPROT_EXEC))
    return fail(EINVAL);
  // Executable guest allocations need MAP_JIT plus instruction/TLS handling.
  // Ordinary file/data mappings must never gain executable permission.
  if (guest & b::kPROT_EXEC)
    return fail(EOPNOTSUPP);
  return (guest & b::kPROT_READ ? PROT_READ : 0) |
         (guest & b::kPROT_WRITE ? PROT_WRITE : 0);
}
class MemoryState {
  struct Segment { uintptr_t end; int protection, flags; uint64_t owner; };
  using Ranges = std::map<uintptr_t, Segment>;
  Ranges ranges_;
  uint64_t next_owner_ = 1;
  std::mutex mutex_;
  const size_t page_ = size_t(::getpagesize());
  bool rounded(size_t size, size_t &result) const {
    if (!size || size > maximum_mapping) {
      errno = size ? ENOMEM : EINVAL;
      return false;
    }
    result = (size + page_ - 1) & ~(page_ - 1);
    return true;
  }
  bool range(void *address, size_t size, uintptr_t &begin, uintptr_t &end) const {
    size_t rounded_size;
    begin = reinterpret_cast<uintptr_t>(address);
    if (!rounded(size, rounded_size))
      return false;
    if (begin < minimum_address || begin % page_ ||
        begin > UINTPTR_MAX - rounded_size) {
      errno = EINVAL;
      return false;
    }
    end = begin + rounded_size;
    return true;
  }
  bool owned(uintptr_t begin, uintptr_t end) const {
    auto it = ranges_.upper_bound(begin);
    if (it == ranges_.begin())
      return false;
    --it;
    for (;;) {
      if (it->first > begin || it->second.end <= begin)
        return false;
      begin = it->second.end;
      if (begin >= end)
        return true;
      if (++it == ranges_.end())
        return false;
    }
  }
  static void remove(Ranges &ranges, uintptr_t begin, uintptr_t end) {
    auto it = ranges.upper_bound(begin);
    if (it != ranges.begin())
      --it;
    while (it != ranges.end() && it->first < end) {
      auto start = it->first, stop = it->second.end;
      auto segment = it->second;
      if (stop <= begin) {
        ++it;
        continue;
      }
      it = ranges.erase(it);
      if (start < begin) { auto before = segment; before.end = begin; ranges.emplace(start, before); }
      if (stop > end) {
        ranges.emplace(end, segment);
        break;
      }
    }
  }
  static size_t bytes(const Ranges &ranges) {
    size_t result = 0;
    for (auto &entry : ranges)
      result += entry.second.end - entry.first;
    return result;
  }
  static void split(Ranges &ranges, uintptr_t address) {
    auto it = ranges.upper_bound(address);
    if (it == ranges.begin()) return;
    --it;
    if (it->first == address || it->second.end <= address) return;
    auto after = it->second;
    ranges.emplace(address, after);
    it->second.end = address;
  }
  // Reserve every destination node before any native mapping/unmapping. Its
  // temporary relative keys are below the minimum permitted guest address.
  static void relocate(Ranges &ranges, uintptr_t address) {
    auto it = ranges.begin();
    while (it != ranges.end() && it->first < minimum_address) {
      auto node = ranges.extract(it++);
      node.key() += address; node.mapped().end += address;
      ranges.insert(std::move(node));
    }
  }

public:
  ~MemoryState() {
    for (auto &entry : ranges_)
      ::munmap(reinterpret_cast<void *>(entry.first), entry.second.end - entry.first);
  }
  void *map(void *hint, size_t size, int prot, int flags, int fd, int64_t offset) {
    std::lock_guard<std::mutex> lock(mutex_);
    size_t length;
    int native_prot = protection(prot);
    if (native_prot == -1 || !rounded(size, length))
      return MAP_FAILED;
    int type = flags & (b::kMAP_SHARED | b::kMAP_PRIVATE);
    if (type != b::kMAP_SHARED && type != b::kMAP_PRIVATE) {
      fail(EINVAL);
      return MAP_FAILED;
    }
    int known = b::kMAP_SHARED | b::kMAP_PRIVATE | b::kMAP_FIXED |
                b::kMAP_ANONYMOUS | b::kMAP_NORESERVE | b::kMAP_FIXED_NOREPLACE;
    if (flags & ~known) {
      fail(EOPNOTSUPP);
      return MAP_FAILED;
    }
    if (offset < 0 || uint64_t(offset) % page_) {
      fail(EINVAL);
      return MAP_FAILED;
    }
    // Darwin has no atomic MAP_FIXED_NOREPLACE. Never emulate it using a
    // separate address-space probe followed by a destructive MAP_FIXED.
    if (flags & b::kMAP_FIXED_NOREPLACE) {
      fail(EOPNOTSUPP);
      return MAP_FAILED;
    }
    bool fixed = flags & b::kMAP_FIXED;
    uintptr_t begin = reinterpret_cast<uintptr_t>(hint), end = 0;
    if (fixed && !range(hint, size, begin, end))
      return MAP_FAILED;
    if (fixed && !owned(begin, end)) {
      fail(EPERM);
      return MAP_FAILED;
    }
    if (next_owner_ == UINT64_MAX) { fail(ENOMEM); return MAP_FAILED; }
    int native_flags = type == b::kMAP_SHARED ? MAP_SHARED : MAP_PRIVATE;
    if (flags & b::kMAP_ANONYMOUS) { native_flags |= MAP_ANON; fd = -1; }
    if (flags & b::kMAP_NORESERVE) native_flags |= MAP_NORESERVE;
    Ranges updated = ranges_;
    if (fixed)
      remove(updated, begin, end);
    if (updated.size() >= maximum_ranges || bytes(updated) > maximum_total - length) {
      fail(ENOMEM);
      return MAP_FAILED;
    }
    // Allocate bookkeeping before changing the actual address space.
    updated.emplace(0, Segment{length, native_prot, native_flags, next_owner_});
    if (fixed)
      native_flags |= MAP_FIXED;
    else if (begin < minimum_address)
      hint = nullptr;
    void *result = ::mmap(hint, length, native_prot, native_flags, fd, offset);
    if (result == MAP_FAILED)
      return result;
    uintptr_t address = reinterpret_cast<uintptr_t>(result);
    if (address < minimum_address || address > UINTPTR_MAX - length) {
      ::munmap(result, length);
      fail(ENOMEM);
      return MAP_FAILED;
    }
    auto node = updated.extract(0);
    node.key() = address;
    node.mapped().end = address + length;
    updated.insert(std::move(node));
    ranges_.swap(updated);
    ++next_owner_;
    return result;
  }
  int unmap(void *address, size_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    uintptr_t begin, end;
    if (!range(address, size, begin, end))
      return -1;
    if (!owned(begin, end))
      return fail(EPERM);
    Ranges updated = ranges_;
    remove(updated, begin, end);
    if (updated.size() > maximum_ranges)
      return fail(ENOMEM);
    if (::munmap(address, end - begin) == -1)
      return -1;
    ranges_.swap(updated);
    return 0;
  }
  int protect(void *address, size_t size, int prot) {
    std::lock_guard<std::mutex> lock(mutex_);
    uintptr_t begin, end;
    int native = protection(prot);
    if (native == -1 || !range(address, size, begin, end))
      return -1;
    if (!owned(begin, end))
      return fail(EPERM);
    Ranges updated = ranges_;
    split(updated, begin); split(updated, end);
    if (updated.size() > maximum_ranges) return fail(ENOMEM);
    for (auto it = updated.lower_bound(begin); it != updated.end() && it->first < end; ++it) it->second.protection = native;
    if (::mprotect(address, end - begin, native)) return -1;
    ranges_.swap(updated); return 0;
  }
  void *remap(void *address, size_t old_size, size_t new_size, int flags) {
    std::lock_guard<std::mutex> lock(mutex_);
    // Android14's public MREMAP_MAYMOVE=1/FIXED=2. Fixed/DONTUNMAP and the
    // old_size==0 shared-alias extension are deliberately not emulated.
    constexpr int may_move = 1, fixed = 2;
    if (flags & ~may_move) { fail((flags & fixed) ? EOPNOTSUPP : EINVAL); return MAP_FAILED; }
    uintptr_t begin, old_end;
    size_t old_length, new_length;
    if (!rounded(old_size, old_length) || !rounded(new_size, new_length) || !range(address, old_size, begin, old_end)) return MAP_FAILED;
    if (begin > UINTPTR_MAX - new_length) { fail(EINVAL); return MAP_FAILED; }
    auto first = ranges_.find(begin);
    if (first == ranges_.end() || !owned(begin, old_end)) { fail(EFAULT); return MAP_FAILED; }
    uint64_t owner = first->second.owner;
    int backing = first->second.flags;
    if ((backing & (MAP_PRIVATE | MAP_ANON)) != (MAP_PRIVATE | MAP_ANON)) { fail(EOPNOTSUPP); return MAP_FAILED; }
    for (const auto &entry : ranges_) if (entry.second.owner == owner && (entry.first < begin || entry.second.end > old_end)) {
      fail(EOPNOTSUPP); return MAP_FAILED; // Only an entire original owned mapping is supported.
    }
    auto last = first;
    for (auto it = first; it != ranges_.end() && it->first < old_end; ++it) {
      if (it->second.owner != owner || it->second.flags != backing || it->second.end > old_end) { fail(EFAULT); return MAP_FAILED; }
      last = it;
    }
    if (last->second.end != old_end) { fail(EFAULT); return MAP_FAILED; }
    if (new_length == old_length) return address;
    if (new_length < old_length) {
      Ranges updated = ranges_;
      remove(updated, begin + new_length, old_end);
      if (updated.size() > maximum_ranges) { fail(ENOMEM); return MAP_FAILED; }
      if (::munmap(reinterpret_cast<void *>(begin + new_length), old_length - new_length)) return MAP_FAILED;
      ranges_.swap(updated); return address;
    }
    size_t total = bytes(ranges_);
    if (total > maximum_total - (new_length - old_length)) { fail(ENOMEM); return MAP_FAILED; }
    // mmap with a non-fixed hint never replaces an adjacent foreign mapping.
    // Commit in place only if the kernel really allocated the exact free tail.
    Ranges grown = ranges_;
    auto grown_last = grown.find(last->first); grown_last->second.end = begin + new_length;
    void *tail = ::mmap(reinterpret_cast<void *>(old_end), new_length - old_length,
                        last->second.protection, backing, -1, 0);
    if (tail != MAP_FAILED) {
      if (reinterpret_cast<uintptr_t>(tail) == old_end) { ranges_.swap(grown); return address; }
      ::munmap(tail, new_length - old_length);
    }
    if (!(flags & may_move)) { fail(ENOMEM); return MAP_FAILED; }
    // Read permission is required for the copying fallback. In particular,
    // never temporarily relax PROT_NONE/write-only pages just to read them.
    for (auto it = first; it != ranges_.end() && it->first < old_end; ++it)
      if (!(it->second.protection & PROT_READ)) { fail(EOPNOTSUPP); return MAP_FAILED; }
    // Bound the transient old+new allocations, not merely the committed total.
    if (total > maximum_total - new_length) { fail(ENOMEM); return MAP_FAILED; }
    Ranges moved = ranges_;
    remove(moved, begin, old_end);
    for (auto it = first; it != ranges_.end() && it->first < old_end; ++it) {
      auto segment = it->second; segment.end -= begin;
      moved.emplace(it->first - begin, segment);
    }
    auto extension = last->second; extension.end = new_length;
    moved.emplace(old_length, extension);
    if (moved.size() > maximum_ranges) { fail(ENOMEM); return MAP_FAILED; }
    void *destination = ::mmap(nullptr, new_length, PROT_READ | PROT_WRITE, backing, -1, 0);
    if (destination == MAP_FAILED) return MAP_FAILED;
    uintptr_t target = reinterpret_cast<uintptr_t>(destination);
    if (target < minimum_address || target > UINTPTR_MAX - new_length) {
      ::munmap(destination, new_length); fail(ENOMEM); return MAP_FAILED;
    }
    std::memcpy(destination, address, old_length);
    for (auto it = moved.begin(); it != moved.end() && it->first < minimum_address; ++it) {
      if (::mprotect(reinterpret_cast<void *>(target + it->first), it->second.end - it->first, it->second.protection)) {
        int error = errno; ::munmap(destination, new_length); fail(error); return MAP_FAILED;
      }
    }
    if (::munmap(address, old_length)) { int error = errno; ::munmap(destination, new_length); fail(error); return MAP_FAILED; }
    relocate(moved, target); ranges_.swap(moved); return destination;
  }
  int advise(void *address,size_t size,int advice) {
    std::lock_guard<std::mutex> lock(mutex_);uintptr_t begin,end;
    if(!range(address,size,begin,end))return -1;
    if(!owned(begin,end))return fail(EPERM);
    int native;
    switch(advice){case 0:native=MADV_NORMAL;break;case 1:native=MADV_RANDOM;break;
      case 2:native=MADV_SEQUENTIAL;break;case 3:native=MADV_WILLNEED;break;case 8:native=MADV_FREE;break;
      default:return fail(EOPNOTSUPP);}
    return ::madvise(address,end-begin,native);
  }
  int sync(void *address, size_t size, int flags) {
    std::lock_guard<std::mutex> lock(mutex_);
    uintptr_t begin, end;
    if (!range(address, size, begin, end))
      return -1;
    if (!owned(begin, end))
      return fail(EPERM);
    if ((flags & ~(b::kMS_SYNC | b::kMS_ASYNC | b::kMS_INVALIDATE)) ||
        ((flags & b::kMS_SYNC) && (flags & b::kMS_ASYNC)))
      return fail(EINVAL);
    int native = flags & b::kMS_ASYNC ? MS_ASYNC : MS_SYNC;
    if (flags & b::kMS_INVALIDATE)
      native |= MS_INVALIDATE;
    return ::msync(address, end - begin, native);
  }
};
template <typename R, typename... Args>
ProviderExport method_export(const char *name, std::shared_ptr<MemoryState> state,
                             R (MemoryState::*method)(Args...)) {
  void *address = closure([state, method](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    R result;
    try {
      result = std::apply(
          [&](Args... values) { return ((*state).*method)(values...); }, args);
    } catch (const std::bad_alloc &) {
      errno = ENOMEM;
      if constexpr (std::is_pointer_v<R>)
        result = MAP_FAILED;
      else
        result = -1;
    }
    bool failed;
    if constexpr (std::is_pointer_v<R>)
      failed = result == MAP_FAILED;
    else
      failed = result == -1;
    if (failed)
      *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
    return_value(capture, result);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(address), SymbolKind::Function,
          "memory/owned-translated", false};
}
} // namespace
std::vector<ProviderExport> memory_exports() {
  auto state = std::make_shared<MemoryState>();
  return {method_export("mmap", state, &MemoryState::map),
          method_export("mmap64", state, &MemoryState::map),
          method_export("munmap", state, &MemoryState::unmap),
          method_export("mprotect", state, &MemoryState::protect),
          method_export("msync", state, &MemoryState::sync),
          method_export("mremap", state, &MemoryState::remap),
          method_export("madvise",state,&MemoryState::advise),
          fixed_export("getpagesize", "LIBC", &::getpagesize)};
}
Providers native_personality(CodeValidator validate, std::shared_ptr<Lifecycle> lifecycle, std::shared_ptr<SignalRuntime> signals, std::shared_ptr<CleanupRuntime> cleanup) {
  auto base = stdio_personality();
  Providers result;
  for (auto &pair : base.modules()) {
    Provider provider = pair.second;
    if (pair.first == "libc.so") {
      // The process adapter owns the copied Android abort message and emits
      // it before real abort; do not retain the core bare-host abort export.
      provider.exports.erase(std::remove_if(provider.exports.begin(), provider.exports.end(),
          [](const auto &entry) { return entry.name == "abort"; }), provider.exports.end());
      auto exports = memory_exports();
      auto checked = fortify_exports();
      exports.insert(exports.end(), std::make_move_iterator(checked.begin()), std::make_move_iterator(checked.end()));
      for (auto group : {locale_exports(), multibyte_exports(), time_exports(), system_exports(), scan_exports(), jump_exports(), quad_exports(), transfer_exports(), wideformat_exports(), filesystem_exports()})
        exports.insert(exports.end(), std::make_move_iterator(group.begin()), std::make_move_iterator(group.end()));
      auto services = core_service_exports(validate);
      auto accounts=accounts_exports();exports.insert(exports.end(),accounts.begin(),accounts.end());
      auto network=networking_exports();exports.insert(exports.end(),network.begin(),network.end());
      auto events=event_exports();exports.insert(exports.end(),events.begin(),events.end());
      if(signals){auto actions=signals->exports();exports.insert(exports.end(),actions.begin(),actions.end());}
      if(cleanup){auto actions=cleanup->exports();exports.insert(exports.end(),actions.begin(),actions.end());}
      exports.insert(exports.end(),services.begin(),services.end());
      auto scalar = libc_scalar_exports(validate);
      exports.insert(exports.end(), std::make_move_iterator(scalar.begin()), std::make_move_iterator(scalar.end()));
      if (lifecycle) {
        auto finalizers = lifecycle->exports();
        exports.insert(exports.end(), std::make_move_iterator(finalizers.begin()), std::make_move_iterator(finalizers.end()));
      }
      auto properties = property_exports(api27_properties());
      exports.insert(exports.end(), std::make_move_iterator(properties.begin()),
                     std::make_move_iterator(properties.end()));
      provider.exports.insert(provider.exports.end(),
                              std::make_move_iterator(exports.begin()),
                              std::make_move_iterator(exports.end()));
      if (validate) {
        auto threads = thread_exports(validate, lifecycle);
        provider.exports.insert(provider.exports.end(),
                                std::make_move_iterator(threads.begin()),
                                std::make_move_iterator(threads.end()));
        auto synchronization = sync_exports(validate);
        provider.exports.insert(provider.exports.end(),
                                std::make_move_iterator(synchronization.begin()),
                                std::make_move_iterator(synchronization.end()));
        auto semaphores = semaphore_exports();
        provider.exports.insert(provider.exports.end(),
                                std::make_move_iterator(semaphores.begin()),
                                std::make_move_iterator(semaphores.end()));
        auto tls = tls_exports(validate, lifecycle);
        provider.exports.insert(provider.exports.end(),
                                std::make_move_iterator(tls.begin()),
                                std::make_move_iterator(tls.end()));
      }
    }
    if (pair.first == "libc.so") {
      auto processes = process_exports(lifecycle, provider.exports);
      provider.exports.insert(provider.exports.end(), processes.begin(), processes.end());
    }
    result.add(std::move(provider));
  }
  result.add(cxx_provider());
  auto math = math_provider();
  math.exports.push_back(quad_log2_export());
  result.add(std::move(math));
  result.add(log_provider());
  if (validate) result.add(zlib_provider(validate));
  return result;
}
} // namespace shiraz::guest
