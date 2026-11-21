// SPDX-License-Identifier: GPL-3.0-only
// Test-only: inspect this disposable process's readable mappings for one
// requested DEX class. Output is exclusive, bounded, and sandbox-controlled.
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <unistd.h>
#include <vector>

extern "C" void shiraz_probe_capture_dex() noexcept {
    const char* needle = std::getenv("SHIRAZ_PROBE_DEX_CLASS");
    const char* output = std::getenv("SHIRAZ_PROBE_DEX_OUTPUT");
    if (!needle || !output)
        return;
    const size_t needle_size = strnlen(needle, 256);
    if (needle_size < 3 || needle_size == 256 || needle[0] != 'L' || needle[needle_size - 1] != ';')
        return;
    mach_vm_address_t address = 0;
    for (size_t count = 0; count < 16384; ++count) {
        mach_vm_size_t length = 0;
        vm_region_basic_info_data_64_t info{};
        mach_msg_type_number_t info_count = VM_REGION_BASIC_INFO_COUNT_64;
        mach_port_t object = MACH_PORT_NULL;
        kern_return_t status =
            mach_vm_region(mach_task_self(), &address, &length, VM_REGION_BASIC_INFO_64,
                           reinterpret_cast<vm_region_info_t>(&info), &info_count, &object);
        if (object != MACH_PORT_NULL)
            mach_port_deallocate(mach_task_self(), object);
        if (status != KERN_SUCCESS || !length || address > UINT64_MAX - length)
            break;
        mach_vm_address_t next = address + length;
        if ((info.protection & VM_PROT_READ) && length >= 112) {
            uint8_t header[112]{};
            mach_vm_size_t copied = 0;
            if (mach_vm_read_overwrite(mach_task_self(), address, sizeof(header),
                                       reinterpret_cast<mach_vm_address_t>(header),
                                       &copied) == KERN_SUCCESS &&
                copied == sizeof(header) && !std::memcmp(header, "dex\n", 4)) {
                uint32_t size = 0;
                std::memcpy(&size, header + 32, sizeof(size));
                if (size >= sizeof(header) && size <= (64u << 20) && size <= length) {
                    try {
                        std::vector<uint8_t> bytes(size);
                        copied = 0;
                        if (mach_vm_read_overwrite(
                                mach_task_self(), address, size,
                                reinterpret_cast<mach_vm_address_t>(bytes.data()),
                                &copied) == KERN_SUCCESS &&
                            copied == size &&
                            memmem(bytes.data(), bytes.size(), needle, needle_size)) {
                            int fd = open(output, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600);
                            if (fd < 0) {
                                std::fprintf(stderr, "SHIRAZ_PROBE_DEX_CAPTURE_FAILED open\n");
                                return;
                            }
                            size_t written = 0;
                            while (written < bytes.size()) {
                                ssize_t n =
                                    write(fd, bytes.data() + written, bytes.size() - written);
                                if (n <= 0)
                                    break;
                                written += size_t(n);
                            }
                            close(fd);
                            if (written != bytes.size()) {
                                unlink(output);
                                std::fprintf(stderr, "SHIRAZ_PROBE_DEX_CAPTURE_FAILED write\n");
                            } else
                                std::fprintf(stderr, "SHIRAZ_PROBE_DEX_CAPTURED bytes=%u\n", size);
                            return;
                        }
                    } catch (...) {
                        std::fprintf(stderr, "SHIRAZ_PROBE_DEX_CAPTURE_FAILED allocation\n");
                        return;
                    }
                }
            }
        }
        address = next;
    }
    std::fprintf(stderr, "SHIRAZ_PROBE_DEX_NOT_FOUND\n");
}
