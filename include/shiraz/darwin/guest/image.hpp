// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"
#include "shiraz/darwin/guest/decode.hpp"
#include "shiraz/darwin/guest/unwind_runtime.hpp"
#include <memory>
#include <functional>
#include <string_view>

namespace shiraz::guest {
class GuestImageReservation;
// Monotonic wall durations for completed stages, in nanoseconds. Rewrite includes
// decoder/site analysis and patching; map includes protections/unwind registration.
struct ImageMetrics {
  uint64_t parse_ns = 0, map_ns = 0, rewrite_ns = 0, bind_ns = 0, init_ns = 0;
  size_t expected_constructors = 0, executed_constructors = 0;
  bool initialized = false;
};
// Mapping, binding and execution are separate stages. Deferred images cannot
// run constructors until the whole load group has bound and been sealed.
class Image {
public:
  enum class Mapping { SelfContained, Deferred };
  explicit Image(const std::vector<uint8_t> &bytes,
                 Mapping mode = Mapping::SelfContained, uintptr_t syscall_dispatcher = 0);
  ~Image();
  Image(const Image &) = delete;
  Image &operator=(const Image &) = delete;
  Image(Image &&) = delete;
  Image &operator=(Image &&) = delete;
  void relocate(const std::function<uintptr_t(size_t)> &resolve);
  void seal();
  uintptr_t address(size_t symbol_index) const;
  void *symbol(std::string_view name) const;
  bool contains_code(const void *pointer) const;
  size_t initialize();
  const ImageMetrics &metrics() const { return metrics_; }
  const Elf &elf() const { return elf_; }
  uintptr_t load_bias() const { return reinterpret_cast<uintptr_t>(base_) - elf_.first_page; }
  uint64_t mapping_size() const { return mapping_size_; }
  size_t rewritten_thread_pointers() const { return analysis_.sites.size(); }
  size_t unwind_frame_count() const { return unwind_ ? unwind_->frame_count() : 0; }

private:
  void *at(uint64_t address, uint64_t size) const;
  ImageMetrics metrics_;
  Elf elf_;
  ThreadPointerAnalysis analysis_;
  uintptr_t syscall_dispatcher_ = 0;
  std::unique_ptr<UnwindRegistration> unwind_;
  std::unique_ptr<GuestImageReservation> reservation_;
  uint64_t mapping_size_ = 0;
  uint8_t *base_ = nullptr;
  std::vector<unsigned> page_flags_;
  enum class LinkState { Mapped, Relocated, Sealed };
  LinkState link_state_ = LinkState::Mapped;
  enum class State { Uninitialized, Initializing, Ready, Failed };
  State state_ = State::Uninitialized;
};
} // namespace shiraz::guest
