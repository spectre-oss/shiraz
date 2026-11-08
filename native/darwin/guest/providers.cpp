// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "guest_errno.hpp"
#include <atomic>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <iterator>
#include <limits>
#include <malloc/malloc.h>
#include <map>
#include <mutex>
#include <new>
#include <tuple>

namespace shiraz::guest {
int *guest_errno_location() {
  static thread_local int value = 0;
  return &value;
}
int errno_to_guest(int host) {
  if (!host)
    return 0;
  for (const auto &pair : bionic::errors)
    if (pair.host == host)
      return pair.guest;
  return bionic::kEIO;
}
namespace {
struct NativeErrno {
  int value;
  ~NativeErrno() { errno = value; }
};
struct Allocation {
  uint64_t bytes;
  GuestHeapReservation reservation;
  Allocation(uintptr_t base, uint64_t size) : bytes(size), reservation(base, size) {}
};
struct Allocations {
  std::mutex mutex;
  std::map<uintptr_t, Allocation> entries;
  static constexpr size_t limit = 1u << 20;

  // A native allocator cannot return a span overlapping another live native
  // allocation. Opaque host APIs can free/reallocate tracked storage without
  // reporting it here; overlapping old records are therefore stale. Drop only
  // their metadata, never free their old pointers. Split-block reuse can have
  // a different base, so exact-key replacement alone is insufficient.
  void retire_reused(uintptr_t base, uint64_t bytes,
                     std::map<uintptr_t, Allocation>::iterator except) {
    const uintptr_t end = base + bytes;
    auto item = entries.lower_bound(base);
    if (item != entries.begin()) {
      auto previous = std::prev(item);
      if (previous->first + previous->second.bytes > base) item = previous;
    }
    while (item != entries.end() && item->first < end) {
      if (item == except) { ++item; continue; }
      item = entries.erase(item);
    }
  }
  auto remember(uintptr_t base, uint64_t bytes) {
    retire_reused(base, bytes, entries.end());
    if (entries.size() >= limit) throw std::bad_alloc();
    return entries.emplace(std::piecewise_construct, std::forward_as_tuple(base),
                           std::forward_as_tuple(base, bytes)).first;
  }
};
std::atomic<Allocations *> published_allocations{nullptr};
Allocations &allocations() {
  // Like the memory-domain registry, the shell outlives native teardown.
  // free() must not initialize/allocate it when no managed object exists.
  static auto *value = [] {
    auto *created = new Allocations;
    published_allocations.store(created, std::memory_order_release);
    return created;
  }();
  return *value;
}
uint64_t allocation_span(const void *pointer) {
  const auto base = reinterpret_cast<uintptr_t>(pointer);
  // A non-null zero-size allocation still has a distinct pointer residence.
  // This is separation metadata, not a readable-user-byte/validity promise.
  uint64_t bytes = ::malloc_size(pointer);
  if (!bytes) bytes = 1;
  if (!base || bytes > UINTPTR_MAX - base)
    throw ElfError("invalid native allocation extent");
  return bytes;
}
void *admit_allocation(void *pointer, int native_errno) {
  NativeErrno restore{native_errno};
  if (!pointer) return nullptr;
  try {
    auto &state = allocations();
    std::lock_guard<std::mutex> lock(state.mutex);
    state.remember(reinterpret_cast<uintptr_t>(pointer), allocation_span(pointer));
  } catch (const std::bad_alloc &) {
    std::free(pointer);
    restore.value = ENOMEM;
    return nullptr;
  } catch (...) {
    std::free(pointer);
    throw;
  }
  return pointer;
}
bool oversized(size_t bytes) {
  // Such an object cannot have representable pointer differences. Reject it
  // before the host allocator (including sanitizer oversized-allocation traps).
  return bytes > size_t(std::numeric_limits<ptrdiff_t>::max());
}
void *managed_malloc(size_t bytes) {
  if (oversized(bytes)) { errno = ENOMEM; return nullptr; }
  void *pointer = std::malloc(bytes);
  return admit_allocation(pointer, errno);
}
void *managed_calloc(size_t count, size_t bytes) {
  if ((bytes && count > SIZE_MAX / bytes) || oversized(count * bytes)) {
    errno = ENOMEM;
    return nullptr;
  }
  void *pointer = std::calloc(count, bytes);
  return admit_allocation(pointer, errno);
}
void *managed_realloc(void *pointer, size_t bytes) {
  if (oversized(bytes)) { errno = ENOMEM; return nullptr; }
  if (!pointer) {
    void *result = std::realloc(nullptr, bytes);
    return admit_allocation(result, errno);
  }
  NativeErrno restore{errno};
  Allocations *state;
  std::unique_lock<std::mutex> lock;
  std::map<uintptr_t, Allocation>::iterator old;
  const auto base = reinterpret_cast<uintptr_t>(pointer);
  try {
    state = &allocations();
    lock = std::unique_lock<std::mutex>(state->mutex);
    const auto span = allocation_span(pointer);
    old = state->entries.find(base);
    if (old == state->entries.end()) {
      // Adopt before the native operation: bookkeeping failure can still
      // return ENOMEM while preserving the caller's original allocation.
      old = state->remember(base, span);
    } else {
      state->retire_reused(base, span, old);
      old->second.reservation.relocate(base, span);
      old->second.bytes = span;
    }
  } catch (const std::bad_alloc &) {
    restore.value = ENOMEM;
    return nullptr;
  }
  // Metadata preparation must not manufacture errno observed by the actual
  // realloc call (whose wrapper deliberately observes a nonzero host errno).
  errno = restore.value;
  void *result = std::realloc(pointer, bytes);
  restore.value = errno;
  if (!result) {
    // Nonzero failure preserves the old native object. For size zero, host
    // allocators differ: retain a conservative stale range if it was freed.
    return nullptr;
  }
  const auto replacement = reinterpret_cast<uintptr_t>(result);
  const auto span = allocation_span(result);
  state->retire_reused(replacement, span, old);
  // Neither operation allocates a map node. Never translate a post-success
  // bookkeeping failure into NULL: native realloc may have freed the old ptr.
  old->second.reservation.relocate(replacement, span);
  old->second.bytes = span;
  auto node = state->entries.extract(old);
  node.key() = replacement;
  if (!state->entries.insert(std::move(node)).inserted)
    throw ElfError("duplicate native realloc record");
  return result;
}
void managed_free(void *pointer) {
  if (!pointer) return;
  auto *state = published_allocations.load(std::memory_order_acquire);
  if (!state) { std::free(pointer); return; }
  NativeErrno restore{errno};
  std::lock_guard<std::mutex> lock(state->mutex);
  const auto base = reinterpret_cast<uintptr_t>(pointer);
  const auto span = allocation_span(pointer);
  std::free(pointer);
  // Keep the reservation across native free, then retire any exact or stale
  // overlapping record. Unknown valid native allocations remain freeable.
  state->retire_reused(base, span, state->entries.end());
}
ProviderExport managed_malloc_export() {
  void *address = closure([](Capture &capture) {
    auto *guest_error = guest_errno_location();
    const auto storage = capture_boundary_storage();
    // This admission is about normal returned calls. The entry boundary may
    // already have written its cell; an overlap refuses before continuation,
    // rather than certifying pre-write safety. Native C/C++ object lifetimes
    // and conforming allocator/private metadata behavior remain assumptions.
    if (!prepared_guest_stack_disjoint(reinterpret_cast<uintptr_t>(&errno), sizeof(errno)) ||
        !prepared_guest_stack_disjoint(reinterpret_cast<uintptr_t>(guest_error), sizeof(*guest_error)) ||
        !prepared_guest_stack_disjoint(storage.address, storage.bytes))
      throw ElfError("managed malloc adapter storage overlaps native stack extent");
    CallCursor cursor(capture, Convention::Aapcs64);
    const auto bytes = cursor.read<size_t>();
    HostErrno preserve;
    void *result = managed_malloc(bytes);
    if (!result) *guest_error = errno_to_guest(errno ? errno : EIO);
    return_value(capture, result);
  });
  return {"malloc", "LIBC", reinterpret_cast<uintptr_t>(address), SymbolKind::Function,
          "heap/managed-malloc-v1", false};
}
void *memory_find(const void *pointer, int value, size_t length) {
  return const_cast<void *>(std::memchr(pointer, value, length));
}
char *string_find(const char *pointer, int value) {
  return const_cast<char *>(std::strchr(pointer, value));
}
char *string_rfind(const char *pointer, int value) {
  return const_cast<char *>(std::strrchr(pointer, value));
}
char *string_substring(const char *haystack, const char *needle) {
  return const_cast<char *>(std::strstr(haystack, needle));
}
} // namespace
void release_guest_allocation(void *pointer) { managed_free(pointer); }
Providers core_personality() {
  Provider libc{"libc.so", {"LIBC", "LIBC_O"}, {}, false};
  auto add = [&](auto function, const char *name,
                 ErrnoPolicy policy = ErrnoPolicy::None) {
    libc.exports.push_back(fixed_export(name, "LIBC", function, policy));
  };
  add(&guest_errno_location, "__errno");
  add(&std::memcpy, "memcpy");
  add(&std::memmove, "memmove");
  add(&std::memset, "memset");
  add(&std::memcmp, "memcmp");
  add(&memory_find, "memchr");
  add(&std::strlen, "strlen");
  add(&::strnlen, "strnlen");
  add(&std::strcmp, "strcmp");
  add(&std::strncmp, "strncmp");
  add(&std::strcpy, "strcpy");
  add(&std::strncpy, "strncpy");
  add(&std::strcat, "strcat");
  add(&std::strncat, "strncat");
  add(&string_find, "strchr");
  add(&string_rfind, "strrchr");
  add(&string_substring, "strstr");
  libc.exports.push_back(managed_malloc_export());
  add(&managed_calloc, "calloc", ErrnoPolicy::Null);
  add(&managed_realloc, "realloc", ErrnoPolicy::Observe);
  add(&std::abort, "abort");
  add(&release_guest_allocation, "free");
  add(&::malloc_size, "malloc_usable_size");
  add(&std::strtol, "strtol", ErrnoPolicy::Observe);
  add(&std::strtoul, "strtoul", ErrnoPolicy::Observe);
  add(&std::strtoll, "strtoll", ErrnoPolicy::Observe);
  add(&std::strtoull, "strtoull", ErrnoPolicy::Observe);
  Providers registry;
  registry.add(std::move(libc));
  return registry;
}
} // namespace shiraz::guest
