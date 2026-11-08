// SPDX-License-Identifier: GPL-3.0-only
// Bridge the existing Shiraz NDK media subset; never fabricate codec success.
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>
#include <vector>
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
namespace berberis {
namespace {
[[noreturn]] void UnsupportedMedia(HostCode name, ProcessState*) {
    std::fprintf(stderr, "Shiraz Digitalis: unsupported NDK media API: %s\n",
                 static_cast<const char*>(name));
    std::abort();
}
// Generated signatures come from the pinned Apache-2.0 AOSP proxy table.
#include "trampolines_arm64_to_x86_64-inl.h"
}
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
    static std::vector<KnownTrampoline> functions(std::begin(kKnownTrampolines), std::end(kKnownTrampolines));
    void* library = dlopen("libandroid.so", RTLD_NOW | RTLD_GLOBAL);
    for (auto& entry : functions) {
        entry.thunk = library ? dlsym(library, entry.name) : nullptr;
        // Callback signatures need a dedicated marshaller. Fail at invocation
        // until it exists, even if the host exports a function of that name.
        if (!entry.thunk || entry.marshal_and_call == UnsupportedMedia) {
            entry.marshal_and_call = UnsupportedMedia;
            entry.thunk = const_cast<char*>(entry.name);
        }
    }
    builder->Build("libandroid.so", functions.size(), functions.data(),
                   sizeof(kKnownVariables)/sizeof(KnownVariable), kKnownVariables);
}
}
