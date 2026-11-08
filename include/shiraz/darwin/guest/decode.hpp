// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"
namespace shiraz::guest {
struct ThreadPointerSite { uint64_t address; uint32_t instruction; };
struct ThreadPointerAnalysis {
  std::vector<ThreadPointerSite> sites;
  size_t decoded = 0, data_words = 0;
};
// Conservative control-flow proof rooted in defined functions and constructors.
// Ambiguous/unreachable TPIDR words are rejected, never blindly rewritten.
ThreadPointerAnalysis analyze_thread_pointers(const Elf &elf,
                                            const std::vector<uint8_t> &bytes);
} // namespace shiraz::guest
