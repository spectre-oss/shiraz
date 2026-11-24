// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/win32/guest/elf_loader.hpp"

#include <algorithm>
#include <cstring>
#include <limits>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

namespace shiraz::win32::guest {

namespace {

constexpr std::uint32_t kRelaX86_64_64 = 1U;
constexpr std::uint32_t kRelaX86_64_GlobDat = 6U;
constexpr std::uint32_t kRelaX86_64_JumpSlot = 7U;
constexpr std::uint32_t kRelaX86_64_Relative = 8U;

constexpr std::uint32_t kRelaAArch64_Abs64 = 257U;
constexpr std::uint32_t kRelaAArch64_GlobDat = 1025U;
constexpr std::uint32_t kRelaAArch64_JumpSlot = 1026U;
constexpr std::uint32_t kRelaAArch64_Relative = 1027U;

std::uint16_t read_u16(const std::uint8_t* p) {
    return static_cast<std::uint16_t>(p[0]) | (static_cast<std::uint16_t>(p[1]) << 8U);
}

std::uint32_t read_u32(const std::uint8_t* p) {
    return static_cast<std::uint32_t>(p[0]) | (static_cast<std::uint32_t>(p[1]) << 8U) |
           (static_cast<std::uint32_t>(p[2]) << 16U) | (static_cast<std::uint32_t>(p[3]) << 24U);
}

std::uint64_t read_u64(const std::uint8_t* p) {
    return static_cast<std::uint64_t>(read_u32(p)) |
           (static_cast<std::uint64_t>(read_u32(p + 4)) << 32U);
}

std::uint64_t align_down(std::uint64_t value, std::uint64_t align) {
    return value & ~(align - 1U);
}

std::uint64_t align_up(std::uint64_t value, std::uint64_t align) {
    if (value > std::numeric_limits<std::uint64_t>::max() - (align - 1U))
        return 0;
    return (value + align - 1U) & ~(align - 1U);
}

void append_bytes(std::vector<std::uint8_t>& out, std::initializer_list<std::uint8_t> bytes) {
    out.insert(out.end(), bytes.begin(), bytes.end());
}

void append_u64_le(std::vector<std::uint8_t>& out, std::uint64_t value) {
    for (int i = 0; i < 8; ++i)
        out.push_back(static_cast<std::uint8_t>((value >> (i * 8)) & 0xFFU));
}

} // namespace

ParseResult parse_elf64_image(const std::uint8_t* data, std::size_t size, GuestArch expectedArch) {
    ParseResult res{};
    if (!data || size < 64U) {
        res.error = "ELF64 image is smaller than 64-byte header";
        return res;
    }
    if (size > kMaxElfSizeBytes) {
        res.error = "ELF64 image exceeds 256 MiB bound";
        return res;
    }
    if (data[0] != 0x7FU || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
        res.error = "Invalid ELF magic";
        return res;
    }
    if (data[4] != 2U || data[5] != 1U || data[6] != 1U) {
        res.error = "Requires little-endian ELF64 version 1";
        return res;
    }
    const std::uint16_t eType = read_u16(data + 16);
    if (eType != 3U) { // ET_DYN
        res.error = "Guest Android shared library must be ET_DYN";
        return res;
    }
    const std::uint16_t eMachine = read_u16(data + 18);
    if (eMachine != static_cast<std::uint16_t>(GuestArch::X86_64) &&
        eMachine != static_cast<std::uint16_t>(GuestArch::AArch64)) {
        res.error = "Unsupported ELF64 machine architecture";
        return res;
    }
    const auto arch = static_cast<GuestArch>(eMachine);
    if (expectedArch != GuestArch::Unknown && arch != expectedArch) {
        res.error = "Cross-ISA ELF64 binary rejected (host ISA mismatch)";
        return res;
    }

    const std::uint64_t eEntry = read_u64(data + 24);
    const std::uint64_t ePhoff = read_u64(data + 32);
    const std::uint16_t ePhentsize = read_u16(data + 54);
    const std::uint16_t ePhnum = read_u16(data + 56);
    if (ePhentsize != 56U || ePhnum == 0 || ePhnum > kMaxProgramHeaders) {
        res.error = "Invalid ELF64 program header table";
        return res;
    }
    const std::uint64_t phtBytes = static_cast<std::uint64_t>(ePhentsize) * ePhnum;
    if (ePhoff > size || phtBytes > size - ePhoff) {
        res.error = "ELF64 program header table extends past file end";
        return res;
    }

    Elf64ImagePlan plan{};
    plan.arch = arch;
    plan.entry = eEntry;

    std::uint64_t minVaddr = std::numeric_limits<std::uint64_t>::max();
    std::uint64_t maxVaddr = 0;

    for (std::uint16_t i = 0; i < ePhnum; ++i) {
        const std::uint8_t* ph = data + ePhoff + static_cast<std::size_t>(i) * ePhentsize;
        const std::uint32_t pType = read_u32(ph + 0);
        const std::uint32_t pFlags = read_u32(ph + 4);
        const std::uint64_t pOffset = read_u64(ph + 8);
        const std::uint64_t pVaddr = read_u64(ph + 16);
        const std::uint64_t pFilesz = read_u64(ph + 32);
        const std::uint64_t pMemsz = read_u64(ph + 40);
        const std::uint64_t pAlign = read_u64(ph + 48);

        if (pType == kPtGnuRelro) {
            if (pMemsz > 0) {
                plan.hasRelro = true;
                plan.relroStart = pVaddr;
                plan.relroSize = pMemsz;
            }
            continue;
        }
        if (pType != kPtLoad)
            continue;

        if ((pFlags & (kPfWrite | kPfExec)) == (kPfWrite | kPfExec)) {
            res.error = "W+X PT_LOAD segment rejected by W^X loader policy";
            return res;
        }
        if (pFilesz > pMemsz) {
            res.error = "PT_LOAD filesz exceeds memsz";
            return res;
        }
        if (pOffset > size || pFilesz > size - pOffset) {
            res.error = "PT_LOAD file range extends past EOF";
            return res;
        }
        if (pAlign != 0 && (pAlign & (pAlign - 1U)) != 0) {
            res.error = "PT_LOAD alignment must be a power of two";
            return res;
        }
        if (pVaddr > std::numeric_limits<std::uint64_t>::max() - pMemsz) {
            res.error = "PT_LOAD virtual range overflow";
            return res;
        }

        LoadSegment seg{};
        seg.offset = pOffset;
        seg.vaddr = pVaddr;
        seg.filesz = pFilesz;
        seg.memsz = pMemsz;
        seg.align = pAlign >= kWin32PageSize ? pAlign : kWin32PageSize;
        seg.flags = pFlags;
        plan.loadSegments.push_back(seg);

        const std::uint64_t segStart = align_down(pVaddr, kWin32PageSize);
        const std::uint64_t segEnd = align_up(pVaddr + pMemsz, kWin32PageSize);
        if (segEnd == 0 || segEnd < segStart) {
            res.error = "PT_LOAD aligned range overflow";
            return res;
        }
        minVaddr = std::min(minVaddr, segStart);
        maxVaddr = std::max(maxVaddr, segEnd);
    }

    if (plan.loadSegments.empty()) {
        res.error = "ELF64 image contains no PT_LOAD segments";
        return res;
    }

    // Verify sorted PT_LOAD page ranges do not overlap.
    std::vector<std::pair<std::uint64_t, std::uint64_t>> ranges;
    ranges.reserve(plan.loadSegments.size());
    for (const auto& seg : plan.loadSegments) {
        ranges.emplace_back(align_down(seg.vaddr, kWin32PageSize),
                            align_up(seg.vaddr + seg.memsz, kWin32PageSize));
    }
    std::sort(ranges.begin(), ranges.end());
    for (std::size_t i = 1; i < ranges.size(); ++i) {
        if (ranges[i].first < ranges[i - 1].second) {
            res.error = "Overlapping PT_LOAD page ranges";
            return res;
        }
    }

    const std::uint64_t span = maxVaddr - minVaddr;
    const std::uint64_t reservation = align_up(span, kWin32AllocGranularity);
    if (reservation == 0 || reservation > kMaxElfSizeBytes * 2U) {
        res.error = "ELF64 reservation exceeds Win32 loader budget";
        return res;
    }

    plan.minVaddr = minVaddr;
    plan.maxVaddr = maxVaddr;
    plan.reservationBytes = reservation;
    res.ok = true;
    res.plan = std::move(plan);
    return res;
}

bool apply_rela_relocations(const Elf64ImagePlan& plan, std::uint8_t* mappedBase,
                            std::uint64_t loadBias, const std::vector<Elf64RelaEntry>& relas,
                            const std::unordered_map<std::uint32_t, std::uint64_t>& symbolAddresses,
                            std::string* outError) {
    if (!mappedBase) {
        if (outError)
            *outError = "Null mapped base";
        return false;
    }
    const std::uint64_t imageSpan = plan.maxVaddr - plan.minVaddr;
    for (const auto& rela : relas) {
        if (rela.offset < plan.minVaddr || rela.offset - plan.minVaddr + 8U > imageSpan) {
            if (outError)
                *outError = "Relocation offset outside mapped ELF64 image";
            return false;
        }
        std::uint64_t value = 0;
        const bool isX64 = plan.arch == GuestArch::X86_64;
        const bool isArm64 = plan.arch == GuestArch::AArch64;
        if ((isX64 && rela.type == kRelaX86_64_Relative) ||
            (isArm64 && rela.type == kRelaAArch64_Relative)) {
            value = static_cast<std::uint64_t>(static_cast<std::int64_t>(loadBias) + rela.addend);
        } else if ((isX64 && (rela.type == kRelaX86_64_GlobDat ||
                              rela.type == kRelaX86_64_JumpSlot || rela.type == kRelaX86_64_64)) ||
                   (isArm64 &&
                    (rela.type == kRelaAArch64_GlobDat || rela.type == kRelaAArch64_JumpSlot ||
                     rela.type == kRelaAArch64_Abs64))) {
            auto it = symbolAddresses.find(rela.symIndex);
            if (it == symbolAddresses.end()) {
                if (outError)
                    *outError =
                        "Unresolved relocation symbol index " + std::to_string(rela.symIndex);
                return false;
            }
            const bool includeAddend = (isX64 && rela.type == kRelaX86_64_64) ||
                                       (isArm64 && rela.type == kRelaAArch64_Abs64);
            value = includeAddend ? static_cast<std::uint64_t>(
                                        static_cast<std::int64_t>(it->second) + rela.addend)
                                  : it->second;
        } else {
            if (outError)
                *outError = "Unsupported ELF64 relocation type " + std::to_string(rela.type);
            return false;
        }
        std::uint8_t* dst = mappedBase + (rela.offset - plan.minVaddr);
        std::memcpy(dst, &value, sizeof(value));
    }
    return true;
}

std::vector<std::uint8_t> emit_sysv_to_win64_thunk(std::uint64_t win64Target) {
    // Translates SysV x86_64 integer args (RDI, RSI, RDX, RCX, R8, R9)
    // to Win64 integer args (RCX, RDX, R8, R9, [rsp+32], [rsp+40]).
    //
    // On entry from SysV caller: RSP % 16 == 8.
    // sub rsp, 0x38 (56 bytes): RSP % 16 == 0, reserves 32-byte Win64 shadow
    // space at [rsp+0..31] + 2 stack args at [rsp+32] (arg5) and [rsp+40] (arg6).
    std::vector<std::uint8_t> code;
    code.reserve(64);
    append_bytes(code, {0x48, 0x83, 0xEC, 0x38});       // sub rsp, 0x38
    append_bytes(code, {0x4C, 0x89, 0x44, 0x24, 0x20}); // mov [rsp+0x20], r8  (arg5)
    append_bytes(code, {0x4C, 0x89, 0x4C, 0x24, 0x28}); // mov [rsp+0x28], r9  (arg6)
    append_bytes(code, {0x49, 0x89, 0xC9});             // mov r9, rcx         (arg4)
    append_bytes(code, {0x49, 0x89, 0xD0});             // mov r8, rdx         (arg3)
    append_bytes(code, {0x48, 0x89, 0xF2});             // mov rdx, rsi        (arg2)
    append_bytes(code, {0x48, 0x89, 0xF9});             // mov rcx, rdi        (arg1)
    append_bytes(code, {0x48, 0xB8});                   // movabs rax, imm64
    append_u64_le(code, win64Target);
    append_bytes(code, {0xFF, 0xD0});             // call rax
    append_bytes(code, {0x48, 0x83, 0xC4, 0x38}); // add rsp, 0x38
    append_bytes(code, {0xC3});                   // ret
    return code;
}

std::vector<std::uint8_t> emit_win64_to_sysv_thunk(std::uint64_t sysvTarget) {
    // Translates Win64 integer args (RCX, RDX, R8, R9, [rsp+40], [rsp+48])
    // to SysV x86_64 integer args (RDI, RSI, RDX, RCX, R8, R9) while preserving
    // Win64 callee-saved RDI and RSI.
    //
    // On entry from Win64 caller: RSP % 16 == 8.
    // push rdi (RSP % 16 == 0)
    // push rsi (RSP % 16 == 8)
    // sub rsp, 8 (RSP % 16 == 0; original caller stack frame shifted by +24 bytes:
    //             arg5 is at [rsp + 24 + 40] = [rsp + 0x40],
    //             arg6 is at [rsp + 24 + 48] = [rsp + 0x48]).
    std::vector<std::uint8_t> code;
    code.reserve(64);
    append_bytes(code, {0x57});                         // push rdi
    append_bytes(code, {0x56});                         // push rsi
    append_bytes(code, {0x48, 0x83, 0xEC, 0x08});       // sub rsp, 8
    append_bytes(code, {0x48, 0x89, 0xCF});             // mov rdi, rcx        (arg1)
    append_bytes(code, {0x48, 0x89, 0xD6});             // mov rsi, rdx        (arg2)
    append_bytes(code, {0x4C, 0x89, 0xC2});             // mov rdx, r8         (arg3)
    append_bytes(code, {0x4C, 0x89, 0xC9});             // mov rcx, r9         (arg4)
    append_bytes(code, {0x4C, 0x8B, 0x44, 0x24, 0x40}); // mov r8, [rsp+0x40]  (arg5)
    append_bytes(code, {0x4C, 0x8B, 0x4C, 0x24, 0x48}); // mov r9, [rsp+0x48]  (arg6)
    append_bytes(code, {0x48, 0xB8});                   // movabs rax, imm64
    append_u64_le(code, sysvTarget);
    append_bytes(code, {0xFF, 0xD0});             // call rax
    append_bytes(code, {0x48, 0x83, 0xC4, 0x08}); // add rsp, 8
    append_bytes(code, {0x5E});                   // pop rsi
    append_bytes(code, {0x5F});                   // pop rdi
    append_bytes(code, {0xC3});                   // ret
    return code;
}

Arm64ScanSummary scan_aarch64_code(const std::uint8_t* code, std::size_t sizeBytes) {
    Arm64ScanSummary summary{};
    if (!code || sizeBytes < 4U)
        return summary;
    const std::size_t count = sizeBytes / 4U;
    for (std::size_t i = 0; i < count; ++i) {
        const std::uint32_t insn = read_u32(code + i * 4U);
        // mrs xN, tpidr_el0 == 0xD53BD040 | Rt (bits 4..0)
        if ((insn & 0xFFFFFFE0U) == 0xD53BD040U) {
            summary.tpidrReads++;
            if ((insn & 0x1FU) == 18U)
                summary.x18Writes++;
        } else if ((insn & 0xFFFFFFE0U) == 0xD51BD040U) {
            // msr tpidr_el0, xN == 0xD51BD040 | Rt
            summary.tpidrWrites++;
        } else if (insn == 0xD4000001U) {
            // svc #0
            summary.rawSyscalls++;
        } else if (((insn & 0x7F800000U) == 0x52800000U) && ((insn & 0x1FU) == 18U)) {
            // movz/movk/movn targeting x18/w18
            summary.x18Writes++;
        }
    }
    return summary;
}

const std::vector<std::string>& win32_bionic_personality_libraries() {
    static const std::vector<std::string> libs = {
        "libc.so",       "libm.so",           "libdl.so",     "liblog.so",    "libz.so",
        "libandroid.so", "libEGL.so",         "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so",
        "libaaudio.so",  "libjnigraphics.so", "libvulkan.so",
    };
    return libs;
}

#if defined(_WIN32)
ExecutablePage::~ExecutablePage() {
    if (base) {
        VirtualFree(base, 0, MEM_RELEASE);
        base = nullptr;
        size = 0;
    }
}

ExecutablePage::ExecutablePage(ExecutablePage&& other) noexcept
    : base(other.base), size(other.size) {
    other.base = nullptr;
    other.size = 0;
}

ExecutablePage& ExecutablePage::operator=(ExecutablePage&& other) noexcept {
    if (this != &other) {
        if (base)
            VirtualFree(base, 0, MEM_RELEASE);
        base = other.base;
        size = other.size;
        other.base = nullptr;
        other.size = 0;
    }
    return *this;
}

bool ExecutablePage::publish(const std::vector<std::uint8_t>& code, std::string* outError) {
    if (code.empty() || code.size() > kWin32AllocGranularity) {
        if (outError)
            *outError = "Thunk size out of bounds";
        return false;
    }
    if (base) {
        VirtualFree(base, 0, MEM_RELEASE);
        base = nullptr;
        size = 0;
    }
    void* mem = VirtualAlloc(nullptr, kWin32PageSize, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!mem) {
        if (outError)
            *outError = "VirtualAlloc(PAGE_READWRITE) failed";
        return false;
    }
    std::memcpy(mem, code.data(), code.size());
    DWORD oldProtect = 0;
    if (!VirtualProtect(mem, kWin32PageSize, PAGE_EXECUTE_READ, &oldProtect)) {
        VirtualFree(mem, 0, MEM_RELEASE);
        if (outError)
            *outError = "VirtualProtect(PAGE_EXECUTE_READ) failed";
        return false;
    }
    FlushInstructionCache(GetCurrentProcess(), mem, code.size());
    base = mem;
    size = kWin32PageSize;
    return true;
}
#endif

} // namespace shiraz::win32::guest
