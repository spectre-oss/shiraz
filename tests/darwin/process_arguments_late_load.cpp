// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <crt_externs.h>

int main(int argc, char** argv) {
    assert((argc == 2 || argc == 3) && std::strlen(argv[0]) > 1);
    bool invalid = !std::strcmp(argv[1], "invalid");
    bool shortened = !std::strcmp(argv[1], "shortened");
    bool oversized = !std::strcmp(argv[1], "oversized");
    bool extended = !std::strcmp(argv[1], "extended");
    assert((argc == 2 && (invalid || shortened)) ||
           (argc == 3 && ((oversized && std::strlen(argv[2]) == 4096) || extended)));
    char* original = argv[0];
    char saved = original[1];
    char* last_end = extended ? argv[2] + std::strlen(argv[2]) : nullptr;
    if (extended) {
        char** environment = *_NSGetEnviron();
        assert(environment && environment[0] && last_end + 1 == environment[0]);
        assert(!std::strcmp(environment[0], "SHIRAZ_FIXTURE_MARKER=controlled"));
        *last_end = 'X';
    }
    if (invalid)
        argv[0] = reinterpret_cast<char*>(1);
    else if (shortened)
        original[1] = '\0';
    void* image = dlopen("build/macos/libshiraz-thread-identity.so", RTLD_NOW | RTLD_LOCAL);
    if (extended)
        *last_end = '\0';
    argv[0] = original;
    original[1] = saved;
    assert(image);
    using Reader = int (*)(uint64_t, void*, size_t, size_t*) noexcept;
    auto reader = reinterpret_cast<Reader>(dlsym(image, "shiraz_process_arguments_read"));
    assert(reader);
    char byte = 0;
    size_t result = 77;
    assert(reader(0, &byte, 1, &result) == ENOTSUP && result == 77);
    assert(!dlclose(image));
    std::puts("DARWIN_PROCESS_ARGUMENTS_LATE_LOAD_PASS fail_closed=1");
}
