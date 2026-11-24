// SPDX-License-Identifier: GPL-3.0-only
// Win32 Android ART NativeBridge v3 C ABI adapter backed by the Shiraz Win32
// Guest ELF64 loader and SysV x86_64 <-> Win64 ABI thunk emitter.
#include "shiraz/win32/guest/native_bridge.hpp"

#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace shiraz::win32::guest {
namespace {

constexpr std::uint32_t kNativeBridgeVersion = 3U;
constexpr std::size_t kMaxLoadedModules = 256U;
constexpr std::size_t kMaxNamespaces = 64U;

struct LoadedModule {
    std::string path;
    Elf64ImagePlan plan;
    std::unordered_map<std::string, std::uint64_t> exports;
};

struct BridgeNamespace {
    std::string name;
    std::string searchPath;
    std::vector<std::string> linkedSonames;
};

struct BridgeState {
    std::mutex mu;
    bool initialized = false;
    GuestArch targetArch = GuestArch::X86_64;
    std::string privateDir;
    std::string lastError;
    std::unordered_map<void*, std::unique_ptr<LoadedModule>> modules;
    std::unordered_map<void*, std::unique_ptr<BridgeNamespace>> namespaces;
    std::unique_ptr<BridgeNamespace> vendorNamespace;
    std::uintptr_t nextModuleId = 0x10000U;
    std::uintptr_t nextNsId = 0x20000U;
};

BridgeState& state() {
    static BridgeState s;
    return s;
}

const NativeBridgeRuntimeValues kX86_64Env = {"x86_64", "x86_64", "", "x86_64", 1};
const NativeBridgeRuntimeValues kArm64Env = {"aarch64", "arm64-v8a", "", "arm64-v8a", 1};

GuestArch parse_isa(const char* isa) {
    if (!isa) {
        return GuestArch::Unknown;
    }
    if (std::strcmp(isa, "x86_64") == 0) {
        return GuestArch::X86_64;
    }
    if (std::strcmp(isa, "arm64") == 0 || std::strcmp(isa, "arm64-v8a") == 0 ||
        std::strcmp(isa, "aarch64") == 0) {
        return GuestArch::AArch64;
    }
    return GuestArch::Unknown;
}

bool read_bounded_file(const char* path, std::vector<std::uint8_t>* outBytes,
                       std::string* outError) {
    if (!path || !*path) {
        *outError = "Empty library path";
        return false;
    }
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in) {
        *outError = std::string("Cannot open guest library: ") + path;
        return false;
    }
    const std::streamoff sz = in.tellg();
    if (sz < 64 || static_cast<std::size_t>(sz) > kMaxElfSizeBytes) {
        *outError = "Guest library size out of bounds";
        return false;
    }
    in.seekg(0, std::ios::beg);
    outBytes->resize(static_cast<std::size_t>(sz));
    if (!in.read(reinterpret_cast<char*>(outBytes->data()), sz)) {
        *outError = "Failed to read guest library bytes";
        return false;
    }
    return true;
}

bool nb_initialize(const NativeBridgeRuntimeCallbacks* /*runtimeCbs*/, const char* privateDir,
                   const char* instructionSet) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    const GuestArch arch = parse_isa(instructionSet ? instructionSet : "x86_64");
    if (arch == GuestArch::Unknown) {
        st.lastError = "Unsupported instruction set for Win32 NativeBridge";
        return false;
    }
    st.targetArch = arch;
    st.privateDir = privateDir ? privateDir : "";
    st.initialized = true;
    st.lastError.clear();
    if (!st.vendorNamespace) {
        st.vendorNamespace = std::make_unique<BridgeNamespace>();
        st.vendorNamespace->name = "vendor";
    }
    return true;
}

void* nb_load_library_ext(const char* libpath, int /*flag*/, void* /*ns*/) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!st.initialized) {
        st.lastError = "NativeBridge not initialized";
        return nullptr;
    }
    if (st.modules.size() >= kMaxLoadedModules) {
        st.lastError = "Loaded guest module limit exceeded";
        return nullptr;
    }
    std::vector<std::uint8_t> bytes;
    std::string ioErr;
    if (!read_bounded_file(libpath, &bytes, &ioErr)) {
        st.lastError = ioErr;
        return nullptr;
    }
    const ParseResult parsed = parse_elf64_image(bytes.data(), bytes.size(), st.targetArch);
    if (!parsed.ok) {
        st.lastError = parsed.error;
        return nullptr;
    }
    auto mod = std::make_unique<LoadedModule>();
    mod->path = libpath;
    mod->plan = parsed.plan;
    void* handle = reinterpret_cast<void*>(st.nextModuleId++);
    st.modules.emplace(handle, std::move(mod));
    st.lastError.clear();
    return handle;
}

void* nb_load_library(const char* libpath, int flag) {
    return nb_load_library_ext(libpath, flag, nullptr);
}

void* nb_get_trampoline(void* handle, const char* name, const char* /*shorty*/,
                        std::uint32_t /*len*/) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!handle || !name || !*name) {
        st.lastError = "Invalid handle or symbol name";
        return nullptr;
    }
    auto it = st.modules.find(handle);
    if (it == st.modules.end()) {
        st.lastError = "Unknown guest library handle";
        return nullptr;
    }
    auto exp = it->second->exports.find(name);
    if (exp == it->second->exports.end()) {
        st.lastError = std::string("Symbol not found in guest module: ") + name;
        return nullptr;
    }
    st.lastError.clear();
    return reinterpret_cast<void*>(static_cast<std::uintptr_t>(exp->second));
}

bool nb_is_supported(const char* libpath) {
    if (!libpath || !*libpath) {
        return false;
    }
    const std::size_t len = std::strlen(libpath);
    if (len < 3U || std::strcmp(libpath + len - 3U, ".so") != 0) {
        return false;
    }
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    std::vector<std::uint8_t> bytes;
    std::string ioErr;
    if (!read_bounded_file(libpath, &bytes, &ioErr)) {
        return false;
    }
    const GuestArch expected = st.initialized ? st.targetArch : GuestArch::X86_64;
    return parse_elf64_image(bytes.data(), bytes.size(), expected).ok;
}

const NativeBridgeRuntimeValues* nb_get_app_env(const char* instructionSet) {
    const GuestArch arch = parse_isa(instructionSet);
    if (arch == GuestArch::X86_64) {
        return &kX86_64Env;
    }
    if (arch == GuestArch::AArch64) {
        return &kArm64Env;
    }
    return nullptr;
}

bool nb_is_compatible_with(std::uint32_t bridgeVersion) {
    return bridgeVersion >= 1U && bridgeVersion <= kNativeBridgeVersion;
}

void* nb_get_signal_handler(int /*signal*/) {
    // Win32 uses VectoredExceptionHandler rather than POSIX signal handlers.
    return nullptr;
}

int nb_unload_library(void* handle) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!handle || st.modules.erase(handle) == 0U) {
        st.lastError = "Cannot unload unknown guest library handle";
        return -1;
    }
    st.lastError.clear();
    return 0;
}

const char* nb_get_error() {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    return st.lastError.empty() ? nullptr : st.lastError.c_str();
}

bool nb_is_path_supported(const char* libraryPath) {
    if (!libraryPath || !*libraryPath) {
        return false;
    }
    return std::strstr(libraryPath, "..") == nullptr;
}

bool nb_init_anonymous_namespace(const char* /*publicNsSonames*/, const char* anonNsLibraryPath) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!st.initialized || !anonNsLibraryPath) {
        return false;
    }
    return true;
}

void* nb_create_namespace(const char* name, const char* ldLibraryPath,
                          const char* /*defaultLibraryPath*/, std::uint64_t /*type*/,
                          const char* /*permittedWhenIsolatedPath*/, void* /*parentNs*/) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!name || !*name || st.namespaces.size() >= kMaxNamespaces) {
        st.lastError = "Invalid namespace request or namespace limit reached";
        return nullptr;
    }
    auto ns = std::make_unique<BridgeNamespace>();
    ns->name = name;
    ns->searchPath = ldLibraryPath ? ldLibraryPath : "";
    void* handle = reinterpret_cast<void*>(st.nextNsId++);
    st.namespaces.emplace(handle, std::move(ns));
    return handle;
}

bool nb_link_namespaces(void* fromNs, void* toNs, const char* sharedLibrariesList) {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    auto fromIt = st.namespaces.find(fromNs);
    if (fromIt == st.namespaces.end() || !toNs || !sharedLibrariesList) {
        return false;
    }
    fromIt->second->linkedSonames.emplace_back(sharedLibrariesList);
    return true;
}

void* nb_get_vendor_namespace() {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    if (!st.vendorNamespace) {
        st.vendorNamespace = std::make_unique<BridgeNamespace>();
        st.vendorNamespace->name = "vendor";
    }
    return st.vendorNamespace.get();
}

} // namespace

const NativeBridgeCallbacksV3* win32_native_bridge_callbacks() {
    return &NativeBridgeItf;
}

void win32_native_bridge_reset_for_tests() {
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    st.initialized = false;
    st.targetArch = GuestArch::X86_64;
    st.privateDir.clear();
    st.lastError.clear();
    st.modules.clear();
    st.namespaces.clear();
    st.vendorNamespace.reset();
    st.nextModuleId = 0x10000U;
    st.nextNsId = 0x20000U;
}

bool win32_native_bridge_register_export(void* handle, const char* name, std::uint64_t address) {
    if (!handle || !name || !*name || address == 0U) {
        return false;
    }
    auto& st = state();
    std::lock_guard<std::mutex> lock(st.mu);
    auto it = st.modules.find(handle);
    if (it == st.modules.end()) {
        return false;
    }
    it->second->exports[name] = address;
    return true;
}

} // namespace shiraz::win32::guest

extern "C" {
SHIRAZ_EXPORT shiraz::win32::guest::NativeBridgeCallbacksV3 NativeBridgeItf = {
    shiraz::win32::guest::kNativeBridgeVersion,  shiraz::win32::guest::nb_initialize,
    shiraz::win32::guest::nb_load_library,       shiraz::win32::guest::nb_get_trampoline,
    shiraz::win32::guest::nb_is_supported,       shiraz::win32::guest::nb_get_app_env,
    shiraz::win32::guest::nb_is_compatible_with, shiraz::win32::guest::nb_get_signal_handler,
    shiraz::win32::guest::nb_unload_library,     shiraz::win32::guest::nb_get_error,
    shiraz::win32::guest::nb_is_path_supported,  shiraz::win32::guest::nb_init_anonymous_namespace,
    shiraz::win32::guest::nb_create_namespace,   shiraz::win32::guest::nb_link_namespaces,
    shiraz::win32::guest::nb_load_library_ext,   shiraz::win32::guest::nb_get_vendor_namespace,
};
}
