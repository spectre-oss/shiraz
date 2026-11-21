// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/bitmap.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <dlfcn.h>
#include <filesystem>
#include <iostream>
#include <memory>
using namespace shiraz::guest;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
extern "C" uintptr_t asset_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        return domain->route(site, target, sp).target;
    } catch (...) {
        std::abort();
    }
}
template <typename F> F symbol(void* library, const char* name) {
    auto result = reinterpret_cast<F>(dlsym(library, name));
    if (!result)
        throw std::runtime_error(std::string("missing androidfw export: ") + name);
    return result;
}
int main(int argc, char** argv) {
    try {
        require(argc == 4, "usage: asset-probe fixture-directory apk androidfw-library");
        auto path = std::filesystem::canonical(argv[3]);
        void* library = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library)
            throw std::runtime_error(std::string("androidfw load failed: ") + dlerror());
        auto load = symbol<const void* (*)(const char*, bool)>(library, "ApkAssets_load");
        auto apk_delete = symbol<void (*)(const void*)>(library, "ApkAssets_delete");
        auto make_manager = symbol<void* (*)()>(library, "AssetManager_new");
        auto delete_manager = symbol<void (*)(void*)>(library, "AssetManager_delete");
        auto attach = symbol<bool (*)(void*, const void**, size_t, bool, bool)>(
            library, "AssetManager_setApkAssets");
        auto apk_path = std::filesystem::canonical(argv[2]);
        std::unique_ptr<const void, decltype(apk_delete)> apk(load(apk_path.c_str(), false),
                                                              apk_delete);
        require(bool(apk), "load fixture APK assets");
        std::unique_ptr<void, decltype(delete_manager)> manager(make_manager(), delete_manager);
        require(bool(manager), "create asset manager");
        const void* apk_value = apk.get();
        require(attach(manager.get(), &apk_value, 1, true, false), "attach fixture assets");
        Loader* owner = nullptr;
        auto validate = [&](const void* p) { return owner && owner->contains_code(p); };
        auto providers = native_personality(validate);
        providers.add(android_provider(
            std::filesystem::canonical("build/macos"), [](void* env) { return env; }, validate));
        Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers),
                      Loader::Mode::AdmissionFixture, uintptr_t(&asset_validate));
        owner = &loader;
        auto* module = loader.open("libasset-fixture.so");
        auto* entry = reinterpret_cast<void*>(loader.symbol(module, "guest_asset_check").address);
        auto argument =
            Argument::integer(reinterpret_cast<uintptr_t>(manager.get()), ArgumentType::Int64);
        auto result =
            invoke_admitted(loader.admission_domain(), entry, {argument}).result_integers[0];
        if (result)
            throw std::runtime_error("guest asset check failed at line " + std::to_string(result));
        owner = nullptr;
        std::cout << "M5_GUEST_ASSET_PASS stored=1 compressed=1 buffer=1 length64=1 read=1 null=1 "
                     "admission=1\n";
    } catch (const std::exception& error) {
        std::cerr << "asset-probe: " << error.what() << '\n';
        return 1;
    }
}
