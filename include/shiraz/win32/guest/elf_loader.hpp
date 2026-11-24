// SPDX-License-Identifier: GPL-3.0-only
// Win32 Guest ELF64 loader, PT_LOAD/RELA planner, and SysV x86_64 <-> Win64 ABI
// machine-code thunk emitter (plus ARM64 TEB/TLS inspection for Windows on ARM64).
#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace shiraz::win32::guest {

enum class GuestArch : std::uint16_t {
    Unknown = 0,
    X86_64 = 62,  // EM_X86_64
    AArch64 = 183 // EM_AARCH64
};

constexpr std::size_t kWin32PageSize = 4096U;
constexpr std::size_t kWin32AllocGranularity = 65536U;
constexpr std::size_t kMaxElfSizeBytes = 256U * 1024U * 1024U;
constexpr std::uint16_t kMaxProgramHeaders = 64U;

constexpr std::uint32_t kPfExec = 0x1U;
constexpr std::uint32_t kPfWrite = 0x2U;
constexpr std::uint32_t kPfRead = 0x4U;

constexpr std::uint32_t kPtLoad = 1U;
constexpr std::uint32_t kPtDynamic = 2U;
constexpr std::uint32_t kPtGnuRelro = 0x6474e552U;

struct LoadSegment {
    std::uint64_t offset = 0;
    std::uint64_t vaddr = 0;
    std::uint64_t filesz = 0;
    std::uint64_t memsz = 0;
    std::uint64_t align = kWin32PageSize;
    std::uint32_t flags = 0;
};

struct Elf64RelaEntry {
    std::uint64_t offset = 0;
    std::uint32_t type = 0;
    std::uint32_t symIndex = 0;
    std::int64_t addend = 0;
};

struct Elf64ImagePlan {
    GuestArch arch = GuestArch::Unknown;
    std::uint64_t entry = 0;
    std::uint64_t minVaddr = 0;
    std::uint64_t maxVaddr = 0;
    std::uint64_t reservationBytes = 0; // Rounded up to 64 KiB Win32 VirtualAlloc granularity
    bool hasRelro = false;
    std::uint64_t relroStart = 0;
    std::uint64_t relroSize = 0;
    std::vector<LoadSegment> loadSegments;
};

struct ParseResult {
    bool ok = false;
    std::string error;
    Elf64ImagePlan plan;
};

// Bounded ELF64 shared-object (ET_DYN) parser and Win32 mapping planner.
// Enforces host ISA match (`expectedArch`), rejects >256 MiB / >64 PT_LOAD /
// non-4KiB-aligned / overlapping / W+X segments.
ParseResult parse_elf64_image(const std::uint8_t* data, std::size_t size, GuestArch expectedArch);

// Applies ELF64 RELA relocations against a mapped image buffer starting at
// `imageBase` with virtual bias `loadBias = imageBase - plan.minVaddr`.
// Resolves symbol indices via `symbolAddresses` (optional for RELATIVE relocs).
bool apply_rela_relocations(const Elf64ImagePlan& plan, std::uint8_t* mappedBase,
                            std::uint64_t loadBias, const std::vector<Elf64RelaEntry>& relas,
                            const std::unordered_map<std::uint32_t, std::uint64_t>& symbolAddresses,
                            std::string* outError);

// Emits audited x86_64 machine code translating a Linux SysV x86_64 guest call
// (RDI, RSI, RDX, RCX, R8, R9) into a Win64 host call (RCX, RDX, R8, R9, [rsp+32], [rsp+40])
// with 32-byte Win64 shadow space and 16-byte stack alignment.
std::vector<std::uint8_t> emit_sysv_to_win64_thunk(std::uint64_t win64Target);

// Emits audited x86_64 machine code translating a Win64 host call
// (RCX, RDX, R8, R9, [rsp+40], [rsp+48]) into a Linux SysV x86_64 guest call
// (RDI, RSI, RDX, RCX, R8, R9) while preserving Win64 callee-saved RDI and RSI.
std::vector<std::uint8_t> emit_win64_to_sysv_thunk(std::uint64_t sysvTarget);

// Scans an AArch64 executable segment for raw `mrs/msr xN, tpidr_el0` and
// `svc #0` instructions that must be rewritten before executing on Windows ARM64.
struct Arm64ScanSummary {
    std::size_t tpidrReads = 0;
    std::size_t tpidrWrites = 0;
    std::size_t rawSyscalls = 0;
    std::size_t x18Writes = 0; // x18 is reserved for Win64 ARM64 TEB.
};
Arm64ScanSummary scan_aarch64_code(const std::uint8_t* code, std::size_t sizeBytes);

// Canonical Android bionic SONAMEs provided by the Win32 Shiraz personality layer.
const std::vector<std::string>& win32_bionic_personality_libraries();

#if defined(_WIN32)
// Allocates RW memory via VirtualAlloc, writes machine-code bytes, flips to
// PAGE_EXECUTE_READ (strict W^X), and calls FlushInstructionCache.
struct ExecutablePage {
    void* base = nullptr;
    std::size_t size = 0;

    ExecutablePage() = default;
    ~ExecutablePage();
    ExecutablePage(const ExecutablePage&) = delete;
    ExecutablePage& operator=(const ExecutablePage&) = delete;
    ExecutablePage(ExecutablePage&& other) noexcept;
    ExecutablePage& operator=(ExecutablePage&& other) noexcept;

    bool publish(const std::vector<std::uint8_t>& code, std::string* outError);
};
#endif

} // namespace shiraz::win32::guest
