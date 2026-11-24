// SPDX-License-Identifier: GPL-3.0-only
// Win32 Android ART NativeBridge v3 C ABI adapter backed by the Shiraz Win32
// Guest ELF64 loader and SysV x86_64 <-> Win64 ABI thunk emitter.
#pragma once

#include "shiraz/platform.hpp"
#include "shiraz/win32/guest/elf_loader.hpp"

#include <cstdint>

namespace shiraz::win32::guest {

struct NativeBridgeRuntimeCallbacks {
    const char* (*getMethodShorty)(void* env, void* mid);
    std::uint32_t (*getNativeMethodCount)(void* env, void* clazz);
    std::uint32_t (*getNativeMethods)(void* env, void* clazz, void* methods,
                                      std::uint32_t methodCount);
};

struct NativeBridgeRuntimeValues {
    const char* os_arch;
    const char* cpu_abi;
    const char* cpu_abi2;
    const char* supported_abis;
    std::int32_t abi_count;
};

struct NativeBridgeCallbacksV3 {
    std::uint32_t version;
    bool (*initialize)(const NativeBridgeRuntimeCallbacks* runtimeCbs, const char* privateDir,
                       const char* instructionSet);
    void* (*loadLibrary)(const char* libpath, int flag);
    void* (*getTrampoline)(void* handle, const char* name, const char* shorty, std::uint32_t len);
    bool (*isSupported)(const char* libpath);
    const NativeBridgeRuntimeValues* (*getAppEnv)(const char* instructionSet);
    bool (*isCompatibleWith)(std::uint32_t bridgeVersion);
    void* (*getSignalHandler)(int signal);
    int (*unloadLibrary)(void* handle);
    const char* (*getError)();
    bool (*isPathSupported)(const char* libraryPath);
    bool (*initAnonymousNamespace)(const char* publicNsSonames, const char* anonNsLibraryPath);
    void* (*createNamespace)(const char* name, const char* ldLibraryPath,
                             const char* defaultLibraryPath, std::uint64_t type,
                             const char* permittedWhenIsolatedPath, void* parentNs);
    bool (*linkNamespaces)(void* fromNs, void* toNs, const char* sharedLibrariesList);
    void* (*loadLibraryExt)(const char* libpath, int flag, void* ns);
    void* (*getVendorNamespace)();
};

// Returns the host-matched Win32 NativeBridge v3 callback table.
const NativeBridgeCallbacksV3* win32_native_bridge_callbacks();

// Resets internal bridge state (intended for unit test isolation).
void win32_native_bridge_reset_for_tests();

// Registers a synthetic export address on an already-loaded module handle (tests/trampolines).
bool win32_native_bridge_register_export(void* handle, const char* name, std::uint64_t address);

} // namespace shiraz::win32::guest

extern "C" {
SHIRAZ_EXPORT extern shiraz::win32::guest::NativeBridgeCallbacksV3 NativeBridgeItf;
}
