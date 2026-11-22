// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>

#if defined(_WIN32)
#define SHIRAZ_OS_WINDOWS 1
#define SHIRAZ_OS_DARWIN 0
#define SHIRAZ_OS_LINUX 0
#define SHIRAZ_EXPORT __declspec(dllexport)
#define SHIRAZ_LIB_EXT ".dll"
#define SHIRAZ_PATH_SEP '\\'
#elif defined(__APPLE__)
#define SHIRAZ_OS_WINDOWS 0
#define SHIRAZ_OS_DARWIN 1
#define SHIRAZ_OS_LINUX 0
#define SHIRAZ_EXPORT __attribute__((visibility("default")))
#define SHIRAZ_LIB_EXT ".so"
#define SHIRAZ_PATH_SEP '/'
#else
#define SHIRAZ_OS_WINDOWS 0
#define SHIRAZ_OS_DARWIN 0
#define SHIRAZ_OS_LINUX 1
#define SHIRAZ_EXPORT __attribute__((visibility("default")))
#define SHIRAZ_LIB_EXT ".so"
#define SHIRAZ_PATH_SEP '/'
#endif

#if defined(__aarch64__) || defined(_M_ARM64)
#define SHIRAZ_ARCH_ARM64 1
#define SHIRAZ_ARCH_X86_64 0
#elif defined(__x86_64__) || defined(_M_X64) || defined(_M_AMD64)
#define SHIRAZ_ARCH_ARM64 0
#define SHIRAZ_ARCH_X86_64 1
#else
#define SHIRAZ_ARCH_ARM64 0
#define SHIRAZ_ARCH_X86_64 0
#endif

namespace shiraz {

inline constexpr const char* host_os_name() {
#if SHIRAZ_OS_WINDOWS
    return "windows";
#elif SHIRAZ_OS_DARWIN
    return "darwin";
#else
    return "linux";
#endif
}

inline constexpr const char* host_arch_name() {
#if SHIRAZ_ARCH_ARM64
    return "arm64-v8a";
#elif SHIRAZ_ARCH_X86_64
    return "x86_64";
#else
    return "unknown";
#endif
}

inline constexpr std::size_t host_default_page_size() {
#if SHIRAZ_OS_DARWIN && SHIRAZ_ARCH_ARM64
    return 16384;
#else
    return 4096;
#endif
}

} // namespace shiraz
