// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"

namespace shiraz::guest {
// Original ELF metadata is validated before mapping. Register only after all
// relocations/protections succeed, and reset BEFORE the image is unmapped.
// The owner must exclude concurrent execution/unwinding during destruction.
// This registers original guest frames, not generated TPIDR islands, and does
// not supply Android C++ exception personalities or libc++abi services.
// Accepted CFI uses bounded register/offset rules and state stacks; DWARF
// expressions and other unsupported instructions are rejected before mapping.
class UnwindRegistration {
public:
  UnwindRegistration(const Elf &elf, const std::vector<uint8_t> &bytes);
  ~UnwindRegistration();
  UnwindRegistration(const UnwindRegistration &) = delete;
  UnwindRegistration &operator=(const UnwindRegistration &) = delete;
  void activate(void *mapping, uint64_t mapping_size);
  void reset() noexcept;
  size_t frame_count() const { return frames_.size(); }
  bool active() const { return active_; }

private:
  uint64_t first_page_, span_;
  std::vector<uint64_t> frames_;
  uint8_t *mapping_ = nullptr;
  bool active_ = false;
};
} // namespace shiraz::guest
