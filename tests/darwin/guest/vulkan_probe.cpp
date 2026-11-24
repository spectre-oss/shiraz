// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/vulkan.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"
#include "shiraz/ndk/vulkan.h"
#include <cerrno>
#include <dlfcn.h>
#include <filesystem>
#include <iostream>

using namespace shiraz::guest;

namespace {
void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
} // namespace

int main(int argc, char** argv) {
    try {
        require(argc == 3, "usage: guestvulkan-probe fixture-directory absolute-libvulkan.so");
        std::filesystem::path vk_path = std::filesystem::canonical(argv[2]);
        Providers providers;
        auto core = core_personality();
        for (const auto& entry : core.modules())
            providers.add(entry.second);
        auto vk_provider = guest_vulkan_provider(vk_path);
        const size_t vk_exports = vk_provider.exports.size();
        providers.add(std::move(vk_provider));

        Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
        auto* module = loader.open("libguestvulkan.so");
        auto* vk_mod = loader.link("libvulkan.so");

        ANativeWindow* win = shiraz_window_create(128, 64);
        require(win != nullptr, "create ANativeWindow for guest Vulkan surface");

        AHardwareBuffer_Desc ahb_desc{};
        ahb_desc.width = 32;
        ahb_desc.height = 16;
        ahb_desc.layers = 1;
        ahb_desc.format = 1; // RGBA8888
        ahb_desc.usage = 0x33ULL;
        AHardwareBuffer* ahb = nullptr;
        require(AHardwareBuffer_allocate(&ahb_desc, &ahb) == 0 && ahb != nullptr,
                "allocate AHardwareBuffer for guest Vulkan AHB import");

        const uint64_t rev_before = shiraz_window_revision(win);
        uintptr_t procedures[3]{};
        errno = ELOOP;
        auto result = invoke(
            reinterpret_cast<void*>(loader.symbol(module, "run_guestvulkan").address),
            {Argument::integer(int64_t(reinterpret_cast<uintptr_t>(win)), ArgumentType::Int64),
             Argument::integer(int64_t(reinterpret_cast<uintptr_t>(ahb)), ArgumentType::Int64),
             Argument::integer(int64_t(reinterpret_cast<uintptr_t>(procedures)),
                               ArgumentType::Int64)});
        if (int(result.result_integers[0]) != 77)
            throw std::runtime_error("guest Vulkan flow failed at fixture line " +
                                     std::to_string(result.result_integers[0]));
        require(errno == ELOOP, "Vulkan adapters preserve host errno");
        require(shiraz_window_revision(win) > rev_before,
                "guest vkQueuePresentKHR posted a frame to ANativeWindow");

        // Verify returned procedure pointers are guest thunks, not raw native symbols.
        void* native = ::dlopen(vk_path.c_str(), RTLD_NOW | RTLD_LOCAL);
        require(native != nullptr, "open native libvulkan.so for address verification");
        const char* names[] = {"vkEnumerateInstanceVersion", "vkCreateAndroidSurfaceKHR",
                               "vkCreateSwapchainKHR"};
        for (size_t i = 0; i < 3; ++i) {
            void* raw = ::dlsym(native, names[i]);
            require(procedures[i] != 0 &&
                        procedures[i] == loader.symbol(vk_mod, names[i]).address &&
                        procedures[i] != reinterpret_cast<uintptr_t>(raw),
                    "guest Vulkan procedures equal guest thunks and differ from raw host pointers");
        }
        ::dlclose(native);

        // Release extra reference acquired by guest vkGetMemoryAndroidHardwareBufferANDROID
        // plus our own owner reference.
        AHardwareBuffer_release(ahb);
        AHardwareBuffer_release(ahb);
        ANativeWindow_release(win);

        std::cout << "M5_GUEST_VULKAN_PASS exports=" << vk_exports
                  << " surface_swapchain_present=1 ahb_import=1 no_raw_proc=1 "
                     "unknown_rejected=1 host_errno_preserved=1\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "guestvulkan-probe: " << error.what() << '\n';
        return 1;
    }
}
