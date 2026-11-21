// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/decode.hpp"
#include <array>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

using namespace shiraz::guest;

static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}

static bool rejects(const Elf& elf, const std::vector<uint8_t>& bytes, const char* reason) {
    try {
        (void)analyze_thread_pointers(elf, bytes);
    } catch (const ElfError& error) {
        return std::string(error.what()).find(reason) != std::string::npos;
    }
    return false;
}

int main() {
    try {
        // No TPIDR or SVC #0 appears in these images. Each reachable dangerous
        // instruction must trigger analysis rather than taking the empty fast path.
        constexpr std::array<uint32_t, 10> exceptions = {
            0xd4001001u, 0xd4000022u, 0xd4000023u, 0xd41fffe1u, 0xd41fffe2u,
            0xd41fffe3u, 0xd69f03e0u, 0xd69f0bffu, 0xd69f0fffu, 0xd6bf03e0u};
        for (uint32_t instruction : exceptions) {
            uint32_t code[] = {instruction, 0xd65f03c0u};
            std::vector<uint8_t> bytes(sizeof(code));
            std::memcpy(bytes.data(), code, sizeof(code));
            Elf elf;
            elf.first_page = 0x1000;
            elf.span = sizeof(code);
            elf.init = elf.first_page;
            elf.segments.push_back({0, elf.first_page, sizeof(code), sizeof(code), 5});
            require(rejects(elf, bytes, "unsupported guest"), "reachable exception was accepted");
            // A return makes the same word unreachable; strict mode must not guess
            // it is data, and inspection must expose it for later admission.
            std::swap(code[0], code[1]);
            std::memcpy(bytes.data(), code, sizeof(code));
            require(rejects(elf, bytes, "ambiguous unreachable"),
                    "unclassified exception was accepted");
            auto analysis = inspect_thread_pointers(elf, bytes, {});
            require(analysis.unresolved.size() == 1 && analysis.unresolved[0].address == 0x1004,
                    "inspection omitted unreachable exception");
            bool refused = false;
            try {
                (void)inspect_thread_pointers(elf, bytes, {0x1004});
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused, "inspection admitted unsupported exception");
            elf.data_sections.push_back({4, 0x1004, 4, 4, 4});
            analysis = analyze_thread_pointers(elf, bytes);
            require(analysis.data_words == 1, "declared data was not preserved");
        }
        uint32_t syscall[] = {0xd4000001u, 0xd65f03c0u};
        std::vector<uint8_t> bytes(sizeof(syscall));
        std::memcpy(bytes.data(), syscall, sizeof(syscall));
        Elf elf;
        elf.first_page = 0x1000;
        elf.span = sizeof(syscall);
        elf.init = elf.first_page;
        elf.segments.push_back({0, elf.first_page, sizeof(syscall), sizeof(syscall), 5});
        auto analysis = analyze_thread_pointers(elf, bytes);
        require(analysis.syscalls.size() == 1 && analysis.syscalls[0].address == elf.first_page,
                "Linux SVC #0 stopped being collected for rewriting");
        std::cout
            << "M5_DECODER_EXCEPTION_PASS reachable=10 ambiguous=10 inspected=10 data=10 svc0=1\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "M5_DECODER_EXCEPTION_FAIL " << error.what() << '\n';
        return 1;
    }
}
