// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cstdio>
#include <cstdlib>
#include <libkern/OSCacheControl.h>
#include <memory>
#include <mutex>
#include <pthread.h>
#include <stdexcept>
#include <sys/mman.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
struct BoundaryState { uint64_t current = 0, next = 1; };
thread_local BoundaryState boundary;
}
CaptureBoundary::CaptureBoundary() noexcept : previous_(boundary.current) {
  if (!boundary.next) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL ABI boundary token exhausted\n");
    std::abort();
  }
  boundary.current = boundary.next++;
}
CaptureBoundary::~CaptureBoundary() { boundary.current = previous_; }
uint64_t capture_boundary_token() noexcept { return boundary.current; }
extern "C" void shiraz_guest_closure_entry();
static_assert(offsetof(Capture, stack) == 240);
static_assert(offsetof(Capture, native_stack_remaining) == 248 && sizeof(Capture) == 256);
extern "C" void
shiraz_guest_closure_dispatch(std::function<void(Capture &)> *handler,
                              Capture *capture) noexcept {
  try {
    if (!guest_tls_prepared()) prepare_guest_tls();
    const auto incoming_sp = reinterpret_cast<uintptr_t>(capture->stack);
    const auto remaining = prepared_guest_stack_remaining(incoming_sp);
    if ((incoming_sp & 15) || !remaining) {
      constexpr char message[] = "SHIRAZ_GUEST_FATAL provider SP outside prepared native stack extent\n";
      (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
      std::abort();
    }
    capture->native_stack_remaining = remaining;
    CaptureBoundary transition;
    (*handler)(*capture);
  } catch (const std::exception &error) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL %s\n", error.what());
    std::abort();
  } catch (...) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL unknown ABI callback failure\n");
    std::abort();
  }
}
void *closure(std::function<void(Capture &)> handler) {
  if (!pthread_jit_write_protect_supported_np())
    throw std::runtime_error("JIT write protection unavailable");
  struct Pool {
    std::mutex mutex;
    std::vector<std::unique_ptr<std::function<void(Capture &)>>> handlers;
    std::vector<uint8_t *> pages;
    size_t used = 65536;
  };
  // Deliberately retain mappings and contexts for the VM lifetime.
  static Pool *pool = new Pool;
  std::lock_guard<std::mutex> lock(pool->mutex);
  if (pool->handlers.size() >= 65536)
    throw std::runtime_error("guest closure limit exceeded");
  if (pool->used == 65536) {
    void *memory = mmap(nullptr, 65536, PROT_READ | PROT_WRITE | PROT_EXEC,
                        MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0);
    if (memory == MAP_FAILED)
      throw std::runtime_error("cannot allocate guest closure code");
    try {
      pool->pages.push_back(static_cast<uint8_t *>(memory));
    } catch (...) {
      munmap(memory, 65536);
      throw;
    }
    pool->used = 0;
  }
  auto context =
      std::make_unique<std::function<void(Capture &)>>(std::move(handler));
  uintptr_t pointer = reinterpret_cast<uintptr_t>(context.get());
  uintptr_t entry = reinterpret_cast<uintptr_t>(&shiraz_guest_closure_entry);
  pool->handlers.push_back(std::move(context));
  uint8_t *code = pool->pages.back() + pool->used;
  // ldr x17,[pc,#16]; ldr x16,[pc,#20]; br x16; nop; context; entry.
  const uint32_t instructions[] = {0x58000091u, 0x580000b0u, 0xd61f0200u,
                                   0xd503201fu};
  {
    JitWriteScope writable;
    std::memcpy(code, instructions, sizeof(instructions));
    std::memcpy(code + 16, &pointer, sizeof(pointer));
    std::memcpy(code + 24, &entry, sizeof(entry));
  }
  sys_icache_invalidate(code, 32);
  pool->used += 32;
  return code;
}
CallCursor::CallCursor(const Capture &capture, Convention convention)
    : capture_(capture), convention_(convention),
      stack_base_(reinterpret_cast<uintptr_t>(capture.stack)),
      native_remaining_(capture.native_stack_remaining),
      native_thread_(native_remaining_ ? reinterpret_cast<uintptr_t>(pthread_self()) : 0) {}
uint64_t CallCursor::next(unsigned size, bool floating) {
  if (size != 1 && size != 2 && size != 4 && size != 8)
    throw std::invalid_argument("unsupported scalar ABI width");
  const void *source;
  if (floating && vector_ < 8)
    source = capture_.frame.vectors[vector_++];
  else if (!floating && integer_ < 8)
    source = &capture_.frame.integers[integer_++];
  else {
    unsigned slot = convention_ == Convention::Apple ? size : 8;
    if (stack_offset_ > 4096)
      throw std::invalid_argument("guest callback stack limit");
    const size_t offset = (stack_offset_ + slot - 1) & ~(size_t(slot) - 1);
    if (offset > 4096 || slot > 4096 - offset)
      throw std::invalid_argument("guest callback stack limit");
    if (stack_base_ > UINTPTR_MAX - offset)
      throw std::invalid_argument("guest callback stack address overflow");
    const auto address = stack_base_ + offset;
    if (address > UINTPTR_MAX - (size - 1))
      throw std::invalid_argument("guest callback stack address overflow");
    if (native_remaining_ && (native_thread_ != reinterpret_cast<uintptr_t>(pthread_self()) ||
        offset > native_remaining_ || size > native_remaining_ - offset))
      throw std::invalid_argument("guest callback stack read outside prepared native extent");
    source = reinterpret_cast<const void *>(address);
    stack_offset_ = offset + slot;
  }
  uint64_t value = 0;
  std::memcpy(&value, source, size);
  return value;
}
} // namespace shiraz::guest
