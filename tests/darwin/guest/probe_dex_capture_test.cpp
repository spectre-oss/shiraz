// SPDX-License-Identifier: GPL-3.0-only
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <sys/mman.h>
#include <unistd.h>
extern "C" void shiraz_probe_capture_dex() noexcept;
int main() {
    char path[] = "/private/tmp/shiraz-dex-probe-XXXXXX";
    int fd = mkstemp(path);
    if (fd < 0 || close(fd) || unlink(path))
        return 1;
    auto* mapping = static_cast<uint8_t*>(
        mmap(nullptr, 16384, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0));
    if (mapping == MAP_FAILED)
        return 2;
    std::memcpy(mapping, "dex\n035\0", 8);
    uint32_t size = 256;
    std::memcpy(mapping + 32, &size, 4);
    const char* needle = "Lshiraz/ProbeCaptureUnique;";
    std::memcpy(mapping + 112, needle, std::strlen(needle));
    setenv("SHIRAZ_PROBE_DEX_CLASS", needle, 1);
    setenv("SHIRAZ_PROBE_DEX_OUTPUT", path, 1);
    shiraz_probe_capture_dex();
    fd = open(path, O_RDONLY);
    uint8_t bytes[256];
    bool passed = fd >= 0 && read(fd, bytes, sizeof(bytes)) == ssize_t(sizeof(bytes)) &&
                  !std::memcmp(bytes, mapping, sizeof(bytes));
    if (fd >= 0)
        close(fd);
    shiraz_probe_capture_dex(); // O_EXCL must preserve the first capture.
    fd = open(path, O_RDONLY);
    passed = passed && fd >= 0 && read(fd, bytes, sizeof(bytes)) == ssize_t(sizeof(bytes)) &&
             !std::memcmp(bytes, mapping, sizeof(bytes));
    if (fd >= 0)
        close(fd);
    unlink(path);
    munmap(mapping, 16384);
    if (!passed)
        return 3;
    std::cout << "M5_PROBE_DEX_CAPTURE_PASS exact_match=1 exclusive=1 fixture_only=1\n";
}
