// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/call.hpp"
#include <cstring>
#include <functional>
#include <type_traits>

namespace shiraz::guest {
struct Capture {
  CallFrame frame;
  const uint8_t *stack;
  // Set by the real entry dispatcher in formerly unused tail padding.
  // Zero for caller-owned synthetic argument buffers.
  uintptr_t native_stack_remaining = 0;
};
// Process-lifetime entry points: callers may retain JNI pointers until VM exit.
void *closure(std::function<void(Capture &)> handler);
// Each native/guest transition gets a unique per-thread token. A nonlocal
// jump may stay within the current guest invocation, but must never bypass
// a live native provider/ABI-dispatch frame and its C++ destructors.
class CaptureBoundary {
public:
  CaptureBoundary() noexcept;
  ~CaptureBoundary();
  CaptureBoundary(const CaptureBoundary &) = delete;
  CaptureBoundary &operator=(const CaptureBoundary &) = delete;
private:
  uint64_t previous_;
};
uint64_t capture_boundary_token() noexcept;
// Normal call setup only: obtaining this address can initialize Mach-O TLV.
// The dispatcher has already entered its boundary before an adapter uses it.
struct CaptureBoundaryStorage { uintptr_t address; uint64_t bytes; };
CaptureBoundaryStorage capture_boundary_storage() noexcept;
enum class Convention { Apple, Aapcs64 };
class CallCursor {
public:
  // Snapshot the starting address and validated native span. Synthetic
  // captures over caller-owned buffers do not acquire native-stack metadata.
  CallCursor(const Capture &capture, Convention convention);
  uint64_t next(unsigned size, bool floating);
  template <typename T> T read() {
    static_assert(std::is_pointer_v<T> || std::is_arithmetic_v<T> ||
                  std::is_enum_v<T>);
    static_assert(sizeof(T) <= 8);
    uint64_t bits = next(sizeof(T), std::is_floating_point_v<T>);
    T value;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
  }

private:
  const Capture &capture_;
  Convention convention_;
  size_t stack_offset_ = 0;
  unsigned integer_ = 0, vector_ = 0;
  uintptr_t stack_base_ = 0, native_remaining_ = 0, native_thread_ = 0;
};
template <typename T> void return_value(Capture &capture, T value) {
  if constexpr (std::is_floating_point_v<T>) {
    std::memcpy(capture.frame.result_vectors[0], &value, sizeof(value));
  } else if constexpr (std::is_pointer_v<T>) {
    capture.frame.result_integers[0] = reinterpret_cast<uintptr_t>(value);
  } else if constexpr (std::is_signed_v<T>) {
    capture.frame.result_integers[0] = uint64_t(int64_t(value));
  } else {
    capture.frame.result_integers[0] = uint64_t(value);
  }
}
} // namespace shiraz::guest
