// SPDX-License-Identifier: GPL-3.0-only
// Behavioral tests for Win32 Guest ELF64 loader, RELA relocation, W^X policy,
// ARM64 TEB/TLS scanner, NativeBridge v3 C ABI, and SysV x86_64 <-> Win64 thunks.
#include "shiraz/win32/guest/elf_loader.hpp"
#include "shiraz/win32/guest/native_bridge.hpp"

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <unordered_map>
#include <vector>

namespace {

void put_u16(std::vector<std::uint8_t>& buf, std::size_t off, std::uint16_t v) {
    buf[off] = static_cast<std::uint8_t>(v & 0xFFU);
    buf[off + 1] = static_cast<std::uint8_t>((v >> 8U) & 0xFFU);
}

void put_u32(std::vector<std::uint8_t>& buf, std::size_t off, std::uint32_t v) {
    buf[off] = static_cast<std::uint8_t>(v & 0xFFU);
    buf[off + 1] = static_cast<std::uint8_t>((v >> 8U) & 0xFFU);
    buf[off + 2] = static_cast<std::uint8_t>((v >> 16U) & 0xFFU);
    buf[off + 3] = static_cast<std::uint8_t>((v >> 24U) & 0xFFU);
}

void put_u64(std::vector<std::uint8_t>& buf, std::size_t off, std::uint64_t v) {
    put_u32(buf, off, static_cast<std::uint32_t>(v & 0xFFFFFFFFULL));
    put_u32(buf, off + 4, static_cast<std::uint32_t>((v >> 32U) & 0xFFFFFFFFULL));
}

std::vector<std::uint8_t> build_synthetic_elf64(std::uint16_t machine, std::uint32_t seg0Flags,
                                                bool overlapSegments = false) {
    std::vector<std::uint8_t> elf(8192, 0);
    elf[0] = 0x7FU;
    elf[1] = 'E';
    elf[2] = 'L';
    elf[3] = 'F';
    elf[4] = 2U;               // ELFCLASS64
    elf[5] = 1U;               // ELFDATA2LSB
    elf[6] = 1U;               // EV_CURRENT
    put_u16(elf, 16, 3U);      // ET_DYN
    put_u16(elf, 18, machine); // EM_X86_64 or EM_AARCH64
    put_u32(elf, 20, 1U);
    put_u64(elf, 24, 0x1000U); // e_entry
    put_u64(elf, 32, 64U);     // e_phoff
    put_u16(elf, 52, 64U);     // e_ehsize
    put_u16(elf, 54, 56U);     // e_phentsize
    put_u16(elf, 56, 3U);      // e_phnum (2 PT_LOAD + 1 PT_GNU_RELRO)

    // Program header 0: RX code segment at vaddr 0x1000..0x2000
    std::size_t ph0 = 64;
    put_u32(elf, ph0 + 0, shiraz::win32::guest::kPtLoad);
    put_u32(elf, ph0 + 4, seg0Flags);
    put_u64(elf, ph0 + 8, 0x1000U);  // p_offset
    put_u64(elf, ph0 + 16, 0x1000U); // p_vaddr
    put_u64(elf, ph0 + 32, 0x400U);  // p_filesz
    put_u64(elf, ph0 + 40, 0x400U);  // p_memsz
    put_u64(elf, ph0 + 48, 0x1000U); // p_align

    // Program header 1: RW data segment at vaddr 0x2000 (or overlapping 0x1800)
    std::size_t ph1 = 64 + 56;
    put_u32(elf, ph1 + 0, shiraz::win32::guest::kPtLoad);
    put_u32(elf, ph1 + 4, shiraz::win32::guest::kPfRead | shiraz::win32::guest::kPfWrite);
    put_u64(elf, ph1 + 8, 0x1400U);
    put_u64(elf, ph1 + 16, overlapSegments ? 0x1800U : 0x2000U);
    put_u64(elf, ph1 + 32, 0x200U);
    put_u64(elf, ph1 + 40, 0x400U);
    put_u64(elf, ph1 + 48, 0x1000U);

    // Program header 2: PT_GNU_RELRO covering 0x2000..0x2100
    std::size_t ph2 = 64 + 112;
    put_u32(elf, ph2 + 0, shiraz::win32::guest::kPtGnuRelro);
    put_u32(elf, ph2 + 4, shiraz::win32::guest::kPfRead);
    put_u64(elf, ph2 + 8, 0x1400U);
    put_u64(elf, ph2 + 16, 0x2000U);
    put_u64(elf, ph2 + 32, 0x100U);
    put_u64(elf, ph2 + 40, 0x100U);
    put_u64(elf, ph2 + 48, 1U);

    return elf;
}

#if defined(_WIN32) && defined(__x86_64__)
extern "C" std::uint64_t win64_six_arg_target(std::uint64_t a, std::uint64_t b, std::uint64_t c,
                                              std::uint64_t d, std::uint64_t e, std::uint64_t f) {
    return a * 1 + b * 10 + c * 100 + d * 1000 + e * 10000 + f * 100000;
}
#endif

} // namespace

int main() {
    using namespace shiraz::win32::guest;

    // 1. Valid x86_64 ET_DYN ELF64 parsing & 64 KiB VirtualAlloc reservation planning.
    auto validX64 = build_synthetic_elf64(62U, kPfRead | kPfExec, false);
    auto parsed = parse_elf64_image(validX64.data(), validX64.size(), GuestArch::X86_64);
    assert(parsed.ok);
    assert(parsed.plan.arch == GuestArch::X86_64);
    assert(parsed.plan.minVaddr == 0x1000U);
    assert(parsed.plan.maxVaddr == 0x3000U);
    assert(parsed.plan.reservationBytes == kWin32AllocGranularity);
    assert(parsed.plan.hasRelro);
    assert(parsed.plan.relroStart == 0x2000U);
    assert(parsed.plan.loadSegments.size() == 2U);

    // 2. Reject cross-ISA, W+X, and overlapping PT_LOAD images explicitly.
    auto wrongIsa = parse_elf64_image(validX64.data(), validX64.size(), GuestArch::AArch64);
    assert(!wrongIsa.ok);

    auto wxElf = build_synthetic_elf64(62U, kPfRead | kPfWrite | kPfExec, false);
    auto wxRes = parse_elf64_image(wxElf.data(), wxElf.size(), GuestArch::X86_64);
    assert(!wxRes.ok);

    auto overlapElf = build_synthetic_elf64(62U, kPfRead | kPfExec, true);
    auto overlapRes = parse_elf64_image(overlapElf.data(), overlapElf.size(), GuestArch::X86_64);
    assert(!overlapRes.ok);

    // 3. Apply RELATIVE and GLOB_DAT relocations into a mapped image buffer.
    std::vector<std::uint8_t> mapped(
        static_cast<std::size_t>(parsed.plan.maxVaddr - parsed.plan.minVaddr), 0);
    const std::uint64_t loadBias = 0x7FF600000000ULL;
    std::vector<Elf64RelaEntry> relas = {
        {0x2000U, 8U, 0U, 0x1234}, // R_X86_64_RELATIVE
        {0x2008U, 6U, 42U, 0},     // R_X86_64_GLOB_DAT
    };
    std::unordered_map<std::uint32_t, std::uint64_t> syms = {{42U, 0x1122334455667788ULL}};
    std::string relErr;
    assert(apply_rela_relocations(parsed.plan, mapped.data(), loadBias, relas, syms, &relErr));
    std::uint64_t got0 = 0, got1 = 0;
    std::memcpy(&got0, mapped.data() + (0x2000U - parsed.plan.minVaddr), sizeof(got0));
    std::memcpy(&got1, mapped.data() + (0x2008U - parsed.plan.minVaddr), sizeof(got1));
    assert(got0 == loadBias + 0x1234ULL);
    assert(got1 == 0x1122334455667788ULL);

    // Unknown relocation symbol must fail explicitly.
    std::vector<Elf64RelaEntry> badRela = {{0x2010U, 6U, 999U, 0}};
    assert(!apply_rela_relocations(parsed.plan, mapped.data(), loadBias, badRela, syms, &relErr));

    // 4. Verify SysV <-> Win64 machine-code thunk bytes & embedded target addresses.
    const std::uint64_t sentinelTarget = 0x00007FF7CAFEBABEULL;
    auto s2w = emit_sysv_to_win64_thunk(sentinelTarget);
    auto w2s = emit_win64_to_sysv_thunk(sentinelTarget);
    assert(s2w.size() >= 30U && s2w.back() == 0xC3U);
    assert(w2s.size() >= 30U && w2s.back() == 0xC3U);
    assert(s2w[0] == 0x48 && s2w[1] == 0x83 && s2w[2] == 0xEC && s2w[3] == 0x38); // sub rsp, 0x38
    assert(w2s[0] == 0x57 && w2s[1] == 0x56); // push rdi; push rsi

    // 5. Verify AArch64 TLS / raw syscall / x18 TEB hazard scanner for Windows on ARM64.
    std::vector<std::uint8_t> arm64Code(16, 0);
    put_u32(arm64Code, 0, 0xD53BD040U);  // mrs x0, tpidr_el0
    put_u32(arm64Code, 4, 0xD51BD041U);  // msr tpidr_el0, x1
    put_u32(arm64Code, 8, 0xD4000001U);  // svc #0
    put_u32(arm64Code, 12, 0xD53BD052U); // mrs x18, tpidr_el0 (clobbers Win64 TEB reg!)
    auto scan = scan_aarch64_code(arm64Code.data(), arm64Code.size());
    assert(scan.tpidrReads == 2U);
    assert(scan.tpidrWrites == 1U);
    assert(scan.rawSyscalls == 1U);
    assert(scan.x18Writes == 1U);

    // 6. Personality library table includes core Bionic + NDK graphics/audio/Vulkan libs.
    const auto& personality = win32_bionic_personality_libraries();
    assert(personality.size() >= 12U);

    // 7. Win32 ART NativeBridge v3 C ABI adapter verification.
    win32_native_bridge_reset_for_tests();
    const auto* nb = win32_native_bridge_callbacks();
    assert(nb != nullptr && nb == &NativeBridgeItf && nb->version == 3U);
    assert(nb->isCompatibleWith(1U) && nb->isCompatibleWith(3U) && !nb->isCompatibleWith(4U));
    assert(nb->getAppEnv("x86_64") != nullptr &&
           std::strcmp(nb->getAppEnv("x86_64")->cpu_abi, "x86_64") == 0);
    assert(nb->getAppEnv("arm64") != nullptr &&
           std::strcmp(nb->getAppEnv("arm64")->cpu_abi, "arm64-v8a") == 0);
    assert(nb->getAppEnv("mips") == nullptr);
    assert(nb->isPathSupported("/app/lib/x86_64") && !nb->isPathSupported("../escape"));
    assert(!nb->initialize(nullptr, "/tmp", "riscv64"));
    assert(nb->initialize(nullptr, "/tmp", "x86_64"));
    assert(nb->initAnonymousNamespace("libc.so", "/app/lib/x86_64"));
    void* ns = nb->createNamespace("app-ns", "/app/lib/x86_64", "", 0, "", nullptr);
    void* vendorNs = nb->getVendorNamespace();
    assert(ns != nullptr && vendorNs != nullptr);
    assert(nb->linkNamespaces(ns, vendorNs, "libandroid.so:libvulkan.so"));

    const auto tmpDir = std::filesystem::temp_directory_path();
    const auto validPath = tmpDir / "shiraz_win32_nb_valid.so";
    const auto wxPath = tmpDir / "shiraz_win32_nb_wx.so";
    {
        std::ofstream f1(validPath, std::ios::binary | std::ios::trunc);
        f1.write(reinterpret_cast<const char*>(validX64.data()),
                 static_cast<std::streamsize>(validX64.size()));
        std::ofstream f2(wxPath, std::ios::binary | std::ios::trunc);
        f2.write(reinterpret_cast<const char*>(wxElf.data()),
                 static_cast<std::streamsize>(wxElf.size()));
    }
    const std::string validStr = validPath.string();
    const std::string wxStr = wxPath.string();
    assert(nb->isSupported(validStr.c_str()));
    assert(!nb->isSupported(wxStr.c_str()));
    assert(nb->loadLibrary(wxStr.c_str(), 0) == nullptr);
    assert(nb->getError() != nullptr);

    void* handle = nb->loadLibraryExt(validStr.c_str(), 0, ns);
    assert(handle != nullptr);
    assert(nb->getTrampoline(handle, "missing_symbol", "()V", 3) == nullptr);
    assert(win32_native_bridge_register_export(handle, "Java_test_add", 0x12345678ULL));
    assert(nb->getTrampoline(handle, "Java_test_add", "(II)I", 5) ==
           reinterpret_cast<void*>(static_cast<std::uintptr_t>(0x12345678ULL)));
    assert(nb->unloadLibrary(handle) == 0);
    assert(nb->unloadLibrary(handle) != 0);
    std::error_code ec;
    std::filesystem::remove(validPath, ec);
    std::filesystem::remove(wxPath, ec);
    win32_native_bridge_reset_for_tests();

#if defined(_WIN32) && defined(__x86_64__)
    // 7. Live Windows x64 execution of a real W^X VirtualAlloc round-trip thunk chain:
    //    Win64 caller -> win64_to_sysv thunk -> sysv_to_win64 thunk -> win64_six_arg_target!
    ExecutablePage sysvToWinPage;
    ExecutablePage winToSysvPage;
    std::string execErr;
    assert(sysvToWinPage.publish(
        emit_sysv_to_win64_thunk(reinterpret_cast<std::uint64_t>(&win64_six_arg_target)),
        &execErr));
    assert(winToSysvPage.publish(
        emit_win64_to_sysv_thunk(reinterpret_cast<std::uint64_t>(sysvToWinPage.base)), &execErr));
    using SixArgFn = std::uint64_t (*)(std::uint64_t, std::uint64_t, std::uint64_t, std::uint64_t,
                                       std::uint64_t, std::uint64_t);
    auto roundTrip = reinterpret_cast<SixArgFn>(winToSysvPage.base);
    const std::uint64_t liveSum = roundTrip(1, 2, 3, 4, 5, 6);
    assert(liveSum == 654321ULL);
    std::printf("WIN32_GUEST_ELF_LIVE_THUNK_OK sum=%llu\n",
                static_cast<unsigned long long>(liveSum));
#endif

    std::printf("WIN32_GUEST_ELF_TESTS_OK\n");
    return 0;
}
