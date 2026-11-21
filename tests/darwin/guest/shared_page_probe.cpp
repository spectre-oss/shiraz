// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/image.hpp"
#include <csignal>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
static uint64_t read(const std::vector<uint8_t>& b, size_t p, unsigned n) {
    uint64_t v = 0;
    for (unsigned i = 0; i < n; ++i)
        v |= uint64_t(b.at(p + i)) << (8 * i);
    return v;
}
int main(int argc, char** argv) {
    try {
        require(argc == 2, "fixture required");
        std::ifstream f(argv[1], std::ios::binary | std::ios::ate);
        auto size = f.tellg();
        require(bool(f) && size > 0 && size < (1 << 20), "fixture size");
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        f.seekg(0);
        require(bool(f.read(reinterpret_cast<char*>(bytes.data()), size)), "fixture read");
        auto elf = parse_elf(bytes, 16384);
        require(elf.relro_size && elf.segments.size() >= 4, "shared page fixture layout");
        bool code_relro_page = false, separate_mutable = false;
        for (const auto& segment : elf.segments) {
            if ((segment.flags & 1) && (segment.address / 16384 == elf.relro / 16384))
                code_relro_page = true;
            if ((segment.flags & 2) && segment.address >= elf.relro + elf.relro_size &&
                segment.address / 16384 != elf.relro / 16384)
                separate_mutable = true;
        }
        require(code_relro_page && separate_mutable,
                "expected executable RELRO and separate mutable page");
        auto phoff = read(bytes, 32, 8), count = read(bytes, 56, 2);
        size_t relro_header = 0;
        for (size_t i = 0; i < count; ++i) {
            auto p = phoff + i * 56;
            if (read(bytes, p, 4) == 0x6474e552) {
                relro_header = p;
                break;
            }
        }
        require(relro_header && elf.relro_size > 8, "RELRO header");
        for (uint64_t length : {uint64_t(0), elf.relro_size - 8}) {
            auto malformed = bytes;
            for (unsigned j = 0; j < 8; ++j)
                malformed.at(relro_header + 40 + j) = uint8_t(length >> (8 * j));
            bool rejected = false;
            try {
                (void)parse_elf(malformed, 16384);
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "mutable code-page sharing must fail");
        }
        Image image(bytes);
        auto get = reinterpret_cast<long (*)()>(image.symbol("shared_page_read"));
        auto increment = reinterpret_cast<long (*)()>(image.symbol("shared_page_increment"));
        require(get && increment && get() == 3 && increment() == 4 && get() == 4,
                "guest execution and mutable data");
        pid_t child = fork();
        require(child >= 0, "fork");
        if (!child) {
            *reinterpret_cast<volatile unsigned char*>(image.load_bias() + elf.relro) = 0;
            _exit(0);
        }
        int status = 0;
        require(waitpid(child, &status, 0) == child, "waitpid");
        require(WIFSIGNALED(status) && (WTERMSIG(status) == SIGBUS || WTERMSIG(status) == SIGSEGV),
                "RELRO write must fault");
        std::cout << "M5_SHARED_PAGE_PASS code=1 relocation=1 mutable_data=1 relro_write_fault=1 "
                     "mutable_code_rejected=1\n";
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
