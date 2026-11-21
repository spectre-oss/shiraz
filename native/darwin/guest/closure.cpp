// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/boundary_token.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cerrno>
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
struct BoundaryState {
    std::atomic<uint64_t> current{0}, next{1};
};
static_assert(std::atomic<uint64_t>::is_always_lock_free && sizeof(BoundaryState) == 16);
thread_local BoundaryState boundary;
} // namespace
CaptureBoundary::CaptureBoundary() noexcept {
    const auto token = detail::reserve_boundary_token(boundary.next);
    if (!token) {
        constexpr char message[] = "SHIRAZ_GUEST_FATAL ABI boundary token exhausted\n";
        (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
        std::abort();
    }
    previous_ = boundary.current.exchange(token, std::memory_order_relaxed);
}
CaptureBoundary::~CaptureBoundary() {
    boundary.current.store(previous_, std::memory_order_relaxed);
}
uint64_t capture_boundary_token() noexcept {
    return boundary.current.load(std::memory_order_relaxed);
}
CaptureBoundaryStorage capture_boundary_storage() noexcept {
    return {reinterpret_cast<uintptr_t>(&boundary), sizeof(boundary)};
}
extern "C" void shiraz_guest_closure_entry();
extern "C" void shiraz_guest_scalar_closure_entry();
#if defined(SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE) && defined(SHIRAZ_TEST_IDENTITY_CAPACITY)
// Test-only preflight injection; never compiled into runtime bridges.
extern "C" bool shiraz_test_identity_capacity();
#endif
namespace {
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
struct ClosureContext {
    std::function<void(Capture&)> handler;
    bool (*on_capacity)(Capture&) = nullptr;
};
#else
using ClosureContext = std::function<void(Capture&)>;
#endif
struct ClosurePool {
    std::mutex mutex;
    std::vector<std::unique_ptr<ClosureContext>> handlers;
    std::vector<uint8_t*> pages;
    size_t used = 65536;
};
// Mappings and contexts are deliberately retained for the VM lifetime.
ClosurePool& closure_pool() {
    static ClosurePool* pool = new ClosurePool;
    return *pool;
}
} // namespace
static_assert(offsetof(Capture, stack) == 240);
static_assert(offsetof(Capture, native_stack_remaining) == 248 && sizeof(Capture) == 256);
extern "C" void shiraz_guest_closure_dispatch(ClosureContext* context, Capture* capture) noexcept {
    try {
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        const int preflight_errno = errno;
        try {
            if (!guest_tls_prepared()) {
#ifdef SHIRAZ_TEST_IDENTITY_CAPACITY
                if (shiraz_test_identity_capacity())
                    throw ExperimentalIdentityCapacity();
#endif
                prepare_guest_tls();
            }
        } catch (const ExperimentalIdentityCapacity&) {
            if (context->on_capacity && context->on_capacity(*capture)) {
                errno = preflight_errno;
                return;
            }
            throw;
        }
#else
        if (!guest_tls_prepared())
            prepare_guest_tls();
#endif
        const auto incoming_sp = reinterpret_cast<uintptr_t>(capture->stack);
        const auto remaining = prepared_guest_stack_remaining(incoming_sp);
        if ((incoming_sp & 15) || !remaining) {
            constexpr char message[] =
                "SHIRAZ_GUEST_FATAL provider SP outside prepared native stack extent\n";
            (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
            std::abort();
        }
        capture->native_stack_remaining = remaining;
        CaptureBoundary transition;
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        context->handler(*capture);
#else
        (*context)(*capture);
#endif
    } catch (const std::exception& error) {
        std::fprintf(stderr, "SHIRAZ_GUEST_FATAL %s\n", error.what());
        std::abort();
    } catch (...) {
        std::fprintf(stderr, "SHIRAZ_GUEST_FATAL unknown ABI callback failure\n");
        std::abort();
    }
}
static void* make_closure(std::function<void(Capture&)> handler, bool scalar
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
                          ,
                          bool (*on_capacity)(Capture&) = nullptr
#endif
) {
    if (!pthread_jit_write_protect_supported_np())
        throw std::runtime_error("JIT write protection unavailable");
    auto* pool = &closure_pool();
    std::lock_guard<std::mutex> lock(pool->mutex);
    if (pool->handlers.size() >= 65536)
        throw std::runtime_error("guest closure limit exceeded");
    if (pool->used == 65536) {
        void* memory = mmap(nullptr, 65536, PROT_READ | PROT_WRITE | PROT_EXEC,
                            MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0);
        if (memory == MAP_FAILED)
            throw std::runtime_error("cannot allocate guest closure code");
        try {
            pool->pages.push_back(static_cast<uint8_t*>(memory));
        } catch (...) {
            munmap(memory, 65536);
            throw;
        }
        pool->used = 0;
    }
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
    auto context =
        std::make_unique<ClosureContext>(ClosureContext{std::move(handler), on_capacity});
#else
    auto context = std::make_unique<ClosureContext>(std::move(handler));
#endif
    uintptr_t pointer = reinterpret_cast<uintptr_t>(context.get());
    uintptr_t entry = scalar ? reinterpret_cast<uintptr_t>(&shiraz_guest_scalar_closure_entry)
                             : reinterpret_cast<uintptr_t>(&shiraz_guest_closure_entry);
    pool->handlers.push_back(std::move(context));
    uint8_t* code = pool->pages.back() + pool->used;
    // ldr x17,[pc,#16]; ldr x16,[pc,#20]; br x16; nop; context; entry.
    const uint32_t instructions[] = {0x58000091u, 0x580000b0u, 0xd61f0200u, 0xd503201fu};
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
void* closure(std::function<void(Capture&)> handler) {
    return make_closure(std::move(handler), false);
}
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
void* experimental_capacity_closure(std::function<void(Capture&)> handler,
                                    bool (*on_capacity)(Capture&)) {
    if (!on_capacity)
        throw std::invalid_argument("missing capacity failure handler");
    return make_closure(std::move(handler), false, on_capacity);
}
#endif
void* scalar_closure(std::function<void(Capture&)> handler) {
    return make_closure(std::move(handler), true);
}
bool known_guest_closure(const void* entry) {
    if (!entry)
        return false;
    auto& pool = closure_pool();
    std::lock_guard<std::mutex> lock(pool.mutex);
    uintptr_t value = reinterpret_cast<uintptr_t>(entry);
    for (size_t i = 0; i < pool.pages.size(); ++i) {
        uintptr_t begin = reinterpret_cast<uintptr_t>(pool.pages[i]);
        size_t used = i + 1 == pool.pages.size() ? pool.used : 65536;
        if (value >= begin && value - begin < used && ((value - begin) % 32) == 0)
            return true;
    }
    return false;
}
CallCursor::CallCursor(const Capture& capture, Convention convention)
    : capture_(capture), convention_(convention),
      stack_base_(reinterpret_cast<uintptr_t>(capture.stack)),
      native_remaining_(capture.native_stack_remaining),
      native_thread_(native_remaining_ ? reinterpret_cast<uintptr_t>(pthread_self()) : 0) {}
uint64_t CallCursor::next(unsigned size, bool floating) {
    if (size != 1 && size != 2 && size != 4 && size != 8)
        throw std::invalid_argument("unsupported scalar ABI width");
    const void* source;
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
        source = reinterpret_cast<const void*>(address);
        stack_offset_ = offset + slot;
    }
    uint64_t value = 0;
    std::memcpy(&value, source, size);
    return value;
}
} // namespace shiraz::guest
