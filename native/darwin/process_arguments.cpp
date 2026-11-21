// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/process_arguments.hpp"
#include <cerrno>
#include <cstring>
#include <crt_externs.h>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <unistd.h>

namespace {
constexpr size_t limit = 1024 * 1024;
uintptr_t beginning = 0;
size_t length = 0;
pid_t owner = 0;
struct PreserveErrno {
    int saved = errno;
    ~PreserveErrno() {
        errno = saved;
    }
};

bool copy_from_self(uintptr_t source, void* destination, size_t amount) noexcept {
    if (!source || !destination || !amount || amount > UINTPTR_MAX - source)
        return false;
    mach_vm_size_t copied = 0;
    return mach_vm_read_overwrite(mach_task_self(), source, amount,
                                  reinterpret_cast<mach_vm_address_t>(destination),
                                  &copied) == KERN_SUCCESS &&
           copied == amount;
}
bool bounded_string_length(uintptr_t address, size_t maximum, size_t page, bool bytewise,
                           size_t& length_out) noexcept {
    char bytes[256];
    size_t offset = 0;
    while (offset < maximum) {
        if (offset > UINTPTR_MAX - address)
            return false;
        uintptr_t current = address + offset;
        size_t count = maximum - offset;
        if (count > sizeof(bytes))
            count = sizeof(bytes);
        if (bytewise)
            count = 1;
        size_t page_remaining = page - size_t(current % page);
        if (count > page_remaining)
            count = page_remaining;
        if (!copy_from_self(current, bytes, count))
            return false;
        const void* end = std::memchr(bytes, 0, count);
        if (end) {
            length_out = offset + size_t(static_cast<const char*>(end) - bytes);
            return true;
        }
        offset += count;
    }
    return false;
}

// Capture before this image's later Process.setArgV0 calls. A pre-load argv
// mutation cannot be detected reliably. Reject unreadable, noncontiguous or
// oversized ranges instead of trusting a potentially modified argv pointer.
__attribute__((constructor)) void capture() noexcept {
    PreserveErrno preserve;
    int argc = 0;
    char** argv = nullptr;
    char** environment = nullptr;
    uintptr_t first_environment = 0;
    long page = sysconf(_SC_PAGESIZE);
    if (page <= 0 ||
        !copy_from_self(reinterpret_cast<uintptr_t>(_NSGetArgc()), &argc, sizeof(argc)) ||
        !copy_from_self(reinterpret_cast<uintptr_t>(_NSGetArgv()), &argv, sizeof(argv)))
        return;
    if (argc <= 0 || argc > 4096 || !argv)
        return;
    // This is an additional guard, not proof of the original kernel boundary:
    // another component can replace environ before this library loads.
    if (copy_from_self(reinterpret_cast<uintptr_t>(_NSGetEnviron()), &environment,
                       sizeof(environment)) &&
        environment)
        (void)copy_from_self(reinterpret_cast<uintptr_t>(environment), &first_environment,
                             sizeof(first_environment));
    uintptr_t start = 0;
    if (!copy_from_self(reinterpret_cast<uintptr_t>(argv), &start, sizeof(start)) || !start)
        return;
    uintptr_t cursor = start;
    for (int i = 0; i < argc; ++i) {
        uintptr_t entry = 0;
        size_t slot = size_t(i) * sizeof(uintptr_t);
        if (slot > UINTPTR_MAX - reinterpret_cast<uintptr_t>(argv) ||
            !copy_from_self(reinterpret_cast<uintptr_t>(argv) + slot, &entry, sizeof(entry)) ||
            entry != cursor)
            return;
        size_t used = size_t(cursor - start);
        if (used >= limit)
            return;
        size_t maximum = limit - used;
        const bool last = i + 1 == argc;
        if (!last) {
            uintptr_t next = 0;
            if (slot > UINTPTR_MAX - sizeof(uintptr_t) ||
                slot + sizeof(uintptr_t) > UINTPTR_MAX - reinterpret_cast<uintptr_t>(argv) ||
                !copy_from_self(reinterpret_cast<uintptr_t>(argv) + slot + sizeof(uintptr_t), &next,
                                sizeof(next)) ||
                next <= entry || next - entry > maximum)
                return;
            maximum = size_t(next - entry);
        } else {
            if (maximum > 4096)
                maximum = 4096;
            if (first_environment > entry && first_environment - entry < maximum)
                maximum = size_t(first_environment - entry);
        }
        size_t count = 0;
        if (!bounded_string_length(entry, maximum, size_t(page), last, count) ||
            (!last && count + 1 != maximum) || count + 1 > UINTPTR_MAX - cursor)
            return;
        cursor += count + 1;
    }
    beginning = start;
    length = size_t(cursor - start);
    owner = getpid();
}
} // namespace

extern "C" int shiraz_process_arguments_extent(uintptr_t* start, size_t* size) noexcept {
    PreserveErrno preserve;
    if (!start || !size)
        return EINVAL;
    if (!owner || !beginning || !length)
        return ENOTSUP;
    if (owner != getpid())
        return ECHILD;
    *start = beginning;
    *size = length;
    return 0;
}

extern "C" int shiraz_process_arguments_read(uint64_t offset, void* destination, size_t capacity,
                                             size_t* read_bytes) noexcept {
    PreserveErrno preserve;
    if (!read_bytes || (!destination && capacity))
        return EINVAL;
    if (capacity > limit)
        return E2BIG;
    if (!owner || !beginning || !length)
        return ENOTSUP;
    if (owner != getpid())
        return ECHILD;
    if (offset >= length || !capacity) {
        *read_bytes = 0;
        return 0;
    }
    size_t amount = length - size_t(offset);
    if (amount > capacity)
        amount = capacity;
    mach_vm_size_t copied = 0;
    kern_return_t result =
        mach_vm_read_overwrite(mach_task_self(), beginning + offset, amount,
                               reinterpret_cast<mach_vm_address_t>(destination), &copied);
    if (result != KERN_SUCCESS || copied != amount)
        return EFAULT;
    *read_bytes = amount;
    return 0;
}
