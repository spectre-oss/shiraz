// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/vulkan.hpp"
#include "shiraz/ndk/vulkan.h"
#include <dlfcn.h>
#include <cstring>
#include <map>
#include <memory>
#include <set>
#include <string>

namespace shiraz::guest {
namespace {
static_assert(sizeof(VkResult) == 4 && sizeof(VkBool32) == 4 && sizeof(VkFlags) == 4 &&
                  sizeof(VkDeviceSize) == 8 && sizeof(VkInstance) == 8 &&
                  sizeof(VkPhysicalDevice) == 8 && sizeof(VkDevice) == 8 && sizeof(VkQueue) == 8 &&
                  sizeof(VkSurfaceKHR) == 8 && sizeof(VkSwapchainKHR) == 8 &&
                  sizeof(VkImage) == 8 && sizeof(VkDeviceMemory) == 8 && sizeof(VkSemaphore) == 8 &&
                  sizeof(VkFence) == 8 && sizeof(PFN_vkVoidFunction) == 8,
              "guest Vulkan scalar and handle widths require audited arm64 Vulkan ABI");

struct VulkanBridge {
    void* library = nullptr;
    std::map<std::string, ProviderExport> procedures;
    std::set<std::string> global_names;
    std::set<std::string> device_names;

    explicit VulkanBridge(const std::filesystem::path& path) {
        if (!path.is_absolute())
            throw ElfError("guest Vulkan requires an absolute libvulkan.so path");
        auto resolved = std::filesystem::canonical(path);
        if (resolved.filename() != "libvulkan.so")
            throw ElfError("guest Vulkan requires the explicitly named Darwin libvulkan.so");
        library = ::dlopen(resolved.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library)
            throw ElfError("cannot load configured Darwin libvulkan.so");
    }

    ~VulkanBridge() {
        if (library)
            ::dlclose(library);
    }

    template <typename Fn> Fn resolve(const char* name) const {
        void* raw = ::dlsym(library, name);
        Fn fn = nullptr;
        if (raw)
            std::memcpy(&fn, &raw, sizeof(fn));
        return fn;
    }
};
} // namespace

Provider guest_vulkan_provider(const std::filesystem::path& library_path) {
    auto state = std::make_shared<VulkanBridge>(library_path);
    Provider vk{"libvulkan.so", {}, {}, false};

    auto add = [&](const char* name, auto function, bool is_global, bool is_device) {
        if (!function)
            throw ElfError(std::string("configured Darwin libvulkan.so lacks ") + name);
        auto entry = fixed_export(name, "", function);
        state->procedures.emplace(name, entry);
        if (is_global)
            state->global_names.insert(name);
        if (is_device)
            state->device_names.insert(name);
        vk.exports.push_back(std::move(entry));
    };

#define VK_EXPORT(NAME, IS_GLOBAL, IS_DEVICE)                                                      \
    add(#NAME, state->resolve<decltype(&::NAME)>(#NAME), IS_GLOBAL, IS_DEVICE)

    // Global & instance-level commands
    VK_EXPORT(vkEnumerateInstanceVersion, true, false);
    VK_EXPORT(vkEnumerateInstanceExtensionProperties, true, false);
    VK_EXPORT(vkEnumerateInstanceLayerProperties, true, false);
    VK_EXPORT(vkCreateInstance, true, false);
    VK_EXPORT(vkDestroyInstance, false, false);
    VK_EXPORT(vkEnumeratePhysicalDevices, false, false);
    VK_EXPORT(vkGetPhysicalDeviceProperties, false, false);
    VK_EXPORT(vkGetPhysicalDeviceFeatures, false, false);
    VK_EXPORT(vkGetPhysicalDeviceQueueFamilyProperties, false, false);
    VK_EXPORT(vkGetPhysicalDeviceMemoryProperties, false, false);
    VK_EXPORT(vkEnumerateDeviceExtensionProperties, false, false);
    VK_EXPORT(vkCreateDevice, false, false);

    // Device / queue / memory commands
    VK_EXPORT(vkDestroyDevice, false, true);
    VK_EXPORT(vkGetDeviceQueue, false, true);
    VK_EXPORT(vkDeviceWaitIdle, false, true);
    VK_EXPORT(vkQueueSubmit, false, true);
    VK_EXPORT(vkQueueWaitIdle, false, true);
    VK_EXPORT(vkAllocateMemory, false, true);
    VK_EXPORT(vkFreeMemory, false, true);

    // VK_KHR_android_surface & VK_KHR_surface
    VK_EXPORT(vkCreateAndroidSurfaceKHR, false, false);
    VK_EXPORT(vkDestroySurfaceKHR, false, false);
    VK_EXPORT(vkGetPhysicalDeviceSurfaceSupportKHR, false, false);
    VK_EXPORT(vkGetPhysicalDeviceSurfaceCapabilitiesKHR, false, false);
    VK_EXPORT(vkGetPhysicalDeviceSurfaceFormatsKHR, false, false);
    VK_EXPORT(vkGetPhysicalDeviceSurfacePresentModesKHR, false, false);

    // VK_KHR_swapchain
    VK_EXPORT(vkCreateSwapchainKHR, false, true);
    VK_EXPORT(vkDestroySwapchainKHR, false, true);
    VK_EXPORT(vkGetSwapchainImagesKHR, false, true);
    VK_EXPORT(vkAcquireNextImageKHR, false, true);
    VK_EXPORT(vkQueuePresentKHR, false, true);

    // VK_ANDROID_external_memory_android_hardware_buffer
    VK_EXPORT(vkGetAndroidHardwareBufferPropertiesANDROID, false, true);
    VK_EXPORT(vkGetMemoryAndroidHardwareBufferANDROID, false, true);
#undef VK_EXPORT

    // Guest-callable vkGetInstanceProcAddr: never leaks raw host function pointers.
    auto gipa = closure([state](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        VkInstance instance = cursor.read<VkInstance>();
        const char* name = cursor.read<const char*>();
        HostErrno preserve;
        uintptr_t address = 0;
        if (name && ::strnlen(name, 129) <= 128) {
            std::string key(name);
            if (!instance) {
                if (state->global_names.count(key) > 0) {
                    auto found = state->procedures.find(key);
                    if (found != state->procedures.end())
                        address = found->second.address;
                }
            } else {
                auto found = state->procedures.find(key);
                if (found != state->procedures.end())
                    address = found->second.address;
            }
        }
        return_value(capture, address);
    });
    ProviderExport gipa_export{"vkGetInstanceProcAddr",
                               "",
                               reinterpret_cast<uintptr_t>(gipa),
                               SymbolKind::Function,
                               "vulkan/typed-whitelist-guest-procedures",
                               false};
    state->global_names.insert(gipa_export.name);
    state->procedures.emplace(gipa_export.name, gipa_export);
    vk.exports.push_back(std::move(gipa_export));

    // Guest-callable vkGetDeviceProcAddr: returns only device-level guest thunks.
    auto gdpa = closure([state](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        VkDevice device = cursor.read<VkDevice>();
        const char* name = cursor.read<const char*>();
        HostErrno preserve;
        uintptr_t address = 0;
        if (device && name && ::strnlen(name, 129) <= 128) {
            std::string key(name);
            if (state->device_names.count(key) > 0) {
                auto found = state->procedures.find(key);
                if (found != state->procedures.end())
                    address = found->second.address;
            }
        }
        return_value(capture, address);
    });
    ProviderExport gdpa_export{"vkGetDeviceProcAddr",
                               "",
                               reinterpret_cast<uintptr_t>(gdpa),
                               SymbolKind::Function,
                               "vulkan/typed-whitelist-guest-procedures",
                               false};
    state->device_names.insert(gdpa_export.name);
    state->procedures.emplace(gdpa_export.name, gdpa_export);
    vk.exports.push_back(std::move(gdpa_export));

    return vk;
}
} // namespace shiraz::guest
