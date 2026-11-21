// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"
namespace shiraz::guest {
struct ThreadPointerSite {
    uint64_t address;
    uint32_t instruction;
};
enum class IndirectTransferKind { Branch, Call, Return, Unsupported };
struct IndirectTransfer {
    uint64_t address;
    uint32_t instruction;
    IndirectTransferKind kind;
    uint8_t target_register;
};
// Classifies the fixed-width BR/BLR/RET encodings only. Other encodings,
// including pointer-authenticated branches, must not be treated as ordinary
// transfers by a future runtime interceptor.
IndirectTransfer classify_indirect_transfer(uint64_t address, uint32_t instruction);
struct ThreadPointerAnalysis {
    std::vector<ThreadPointerSite> sites;
    std::vector<ThreadPointerSite> syscalls;
    // Diagnostics only: neither list grants permission to execute code.
    std::vector<ThreadPointerSite> unresolved;
    std::vector<IndirectTransfer> indirect_transfers;
    // Inspection-only bitmap: one bit per aligned guest instruction, relative
    // to Elf::first_page. Its presence is not an execution authorization.
    std::vector<uint8_t> decoded_bitmap;
    size_t decoded = 0, data_words = 0;
};
// Conservative control-flow proof rooted in defined functions and constructors.
// Ambiguous/unreachable TPIDR words are rejected, never blindly rewritten.
ThreadPointerAnalysis analyze_thread_pointers(const Elf& elf, const std::vector<uint8_t>& bytes);
// Inspect additional candidate entry points and report unresolved words and
// indirect transfers. This is not an execution or rewriting authorization:
// callers must establish actual runtime targets and safely intercept every
// dynamic transfer before allowing guest execution.
ThreadPointerAnalysis inspect_thread_pointers(const Elf& elf, const std::vector<uint8_t>& bytes,
                                              const std::vector<uint64_t>& candidate_entries);
} // namespace shiraz::guest
