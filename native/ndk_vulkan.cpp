// SPDX-License-Identifier: GPL-3.0-only
// Android NDK libvulkan.so dynamic loader/ICD bridge with VK_KHR_android_surface
// and VK_ANDROID_external_memory_android_hardware_buffer support.
#include "shiraz/ndk/vulkan.h"

#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

using PFN_vkGetInstanceProcAddr = PFN_vkVoidFunction (*)(VkInstance, const char*);
using PFN_vkGetDeviceProcAddr = PFN_vkVoidFunction (*)(VkDevice, const char*);
using PFN_vkEnumerateInstanceVersion = VkResult (*)(uint32_t*);
using PFN_vkEnumerateInstanceExtensionProperties = VkResult (*)(const char*, uint32_t*,
                                                                VkExtensionProperties*);
using PFN_vkEnumerateInstanceLayerProperties = VkResult (*)(uint32_t*, VkLayerProperties*);
using PFN_vkCreateInstance = VkResult (*)(const VkInstanceCreateInfo*, const VkAllocationCallbacks*,
                                          VkInstance*);
using PFN_vkDestroyInstance = void (*)(VkInstance, const VkAllocationCallbacks*);
using PFN_vkEnumeratePhysicalDevices = VkResult (*)(VkInstance, uint32_t*, VkPhysicalDevice*);
using PFN_vkGetPhysicalDeviceProperties = void (*)(VkPhysicalDevice, VkPhysicalDeviceProperties*);
using PFN_vkGetPhysicalDeviceFeatures = void (*)(VkPhysicalDevice, VkPhysicalDeviceFeatures*);
using PFN_vkGetPhysicalDeviceQueueFamilyProperties = void (*)(VkPhysicalDevice, uint32_t*,
                                                              VkQueueFamilyProperties*);
using PFN_vkGetPhysicalDeviceMemoryProperties = void (*)(VkPhysicalDevice,
                                                         VkPhysicalDeviceMemoryProperties*);
using PFN_vkEnumerateDeviceExtensionProperties = VkResult (*)(VkPhysicalDevice, const char*,
                                                              uint32_t*, VkExtensionProperties*);
using PFN_vkCreateDevice = VkResult (*)(VkPhysicalDevice, const VkDeviceCreateInfo*,
                                        const VkAllocationCallbacks*, VkDevice*);
using PFN_vkDestroyDevice = void (*)(VkDevice, const VkAllocationCallbacks*);
using PFN_vkGetDeviceQueue = void (*)(VkDevice, uint32_t, uint32_t, VkQueue*);
using PFN_vkDeviceWaitIdle = VkResult (*)(VkDevice);
using PFN_vkQueueSubmit = VkResult (*)(VkQueue, uint32_t, const VkSubmitInfo*, VkFence);
using PFN_vkQueueWaitIdle = VkResult (*)(VkQueue);
using PFN_vkCreateSwapchainKHR = VkResult (*)(VkDevice, const VkSwapchainCreateInfoKHR*,
                                              const VkAllocationCallbacks*, VkSwapchainKHR*);
using PFN_vkDestroySwapchainKHR = void (*)(VkDevice, VkSwapchainKHR, const VkAllocationCallbacks*);
using PFN_vkGetSwapchainImagesKHR = VkResult (*)(VkDevice, VkSwapchainKHR, uint32_t*, VkImage*);
using PFN_vkAcquireNextImageKHR = VkResult (*)(VkDevice, VkSwapchainKHR, uint64_t, VkSemaphore,
                                               VkFence, uint32_t*);
using PFN_vkQueuePresentKHR = VkResult (*)(VkQueue, const VkPresentInfoKHR*);

template <typename Fn> Fn load_dlsym(void* handle, const char* name) {
    if (!handle || !name) {
        return nullptr;
    }
    void* raw = ::dlsym(handle, name);
    Fn fn = nullptr;
    if (raw) {
        std::memcpy(&fn, &raw, sizeof(fn));
    }
    return fn;
}

template <typename Fn> Fn cast_pfn(PFN_vkVoidFunction raw) {
    Fn fn = nullptr;
    if (raw) {
        std::memcpy(&fn, &raw, sizeof(fn));
    }
    return fn;
}

struct HostVulkanLoader {
    void* handle = nullptr;
    PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
    PFN_vkGetDeviceProcAddr getDeviceProcAddr = nullptr;

    HostVulkanLoader() {
        std::vector<std::string> candidates;
        if (const char* envIcd = std::getenv("SHIRAZ_VULKAN_ICD"); envIcd && *envIcd) {
            candidates.emplace_back(envIcd);
        }
#if defined(__APPLE__)
        candidates.emplace_back("libvulkan.1.dylib");
        candidates.emplace_back("libvulkan.dylib");
        candidates.emplace_back("libMoltenVK.dylib");
        candidates.emplace_back("/opt/homebrew/lib/libvulkan.1.dylib");
        candidates.emplace_back("/opt/homebrew/lib/libMoltenVK.dylib");
        candidates.emplace_back("/usr/local/lib/libvulkan.1.dylib");
        candidates.emplace_back("/usr/local/lib/libMoltenVK.dylib");
#else
        candidates.emplace_back("libvulkan.so.1");
        candidates.emplace_back("/usr/lib/x86_64-linux-gnu/libvulkan.so.1");
        candidates.emplace_back("/usr/lib/aarch64-linux-gnu/libvulkan.so.1");
        candidates.emplace_back("/usr/lib64/libvulkan.so.1");
#endif
        for (const auto& path : candidates) {
            void* h = ::dlopen(path.c_str(), RTLD_LAZY | RTLD_LOCAL);
            if (!h) {
                continue;
            }
            auto gipa = load_dlsym<PFN_vkGetInstanceProcAddr>(h, "vkGetInstanceProcAddr");
            if (!gipa) {
                ::dlclose(h);
                continue;
            }
            handle = h;
            getInstanceProcAddr = gipa;
            getDeviceProcAddr = load_dlsym<PFN_vkGetDeviceProcAddr>(h, "vkGetDeviceProcAddr");
            break;
        }
    }

    bool available() const {
        return handle != nullptr && getInstanceProcAddr != nullptr;
    }

    template <typename Fn> Fn instanceProc(VkInstance instance, const char* name) const {
        if (!available()) {
            return nullptr;
        }
        return cast_pfn<Fn>(getInstanceProcAddr(instance, name));
    }

    template <typename Fn> Fn deviceProc(VkDevice device, const char* name) const {
        if (!available()) {
            return nullptr;
        }
        if (getDeviceProcAddr && device) {
            if (auto fn = cast_pfn<Fn>(getDeviceProcAddr(device, name))) {
                return fn;
            }
        }
        return load_dlsym<Fn>(handle, name);
    }
};

const HostVulkanLoader& host_loader() {
    static HostVulkanLoader loader;
    return loader;
}

struct AndroidSurfaceRecord {
    VkInstance instance = nullptr;
    ANativeWindow* window = nullptr;
};

struct VulkanState {
    std::mutex mu;
    std::atomic<uint64_t> nextHandle{1};
    std::unordered_map<VkSurfaceKHR, AndroidSurfaceRecord> surfaces;
    std::unordered_map<VkDeviceMemory, AHardwareBuffer*> ahbMemories;
};

VulkanState& vk_state() {
    static VulkanState s;
    return s;
}

bool has_extension(const std::vector<VkExtensionProperties>& list, const char* name) {
    for (const auto& ext : list) {
        if (std::strcmp(ext.extensionName, name) == 0) {
            return true;
        }
    }
    return false;
}

void append_extension(std::vector<VkExtensionProperties>& list, const char* name,
                      uint32_t specVersion) {
    if (has_extension(list, name)) {
        return;
    }
    VkExtensionProperties prop{};
    std::strncpy(prop.extensionName, name, VK_MAX_EXTENSION_NAME_SIZE - 1);
    prop.extensionName[VK_MAX_EXTENSION_NAME_SIZE - 1] = '\0';
    prop.specVersion = specVersion;
    list.push_back(prop);
}

// Map Android AHardwareBuffer format constants (hardware_buffer.h / AHardwareBuffer_Format)
// to Vulkan VkFormat and bytes per pixel.
void describe_ahb_format(uint32_t ahbFormat, VkFormat* outVkFormat, uint32_t* outBpp) {
    switch (ahbFormat) {
    case 1: // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM
    case 2: // AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM
        *outVkFormat = VK_FORMAT_R8G8B8A8_UNORM;
        *outBpp = 4;
        break;
    case 3: // AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM
        *outVkFormat = VK_FORMAT_R8G8B8_UNORM;
        *outBpp = 3;
        break;
    case 4: // AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM
        *outVkFormat = VK_FORMAT_R5G6B5_UNORM_PACK16;
        *outBpp = 2;
        break;
    case 22: // AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT
        *outVkFormat = VK_FORMAT_R16G16B16A16_SFLOAT;
        *outBpp = 8;
        break;
    case 43: // AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM
        *outVkFormat = VK_FORMAT_A2B10G10R10_UNORM_PACK32;
        *outBpp = 4;
        break;
    case 33: // AHARDWAREBUFFER_FORMAT_BLOB
        *outVkFormat = VK_FORMAT_UNDEFINED;
        *outBpp = 1;
        break;
    default:
        *outVkFormat = VK_FORMAT_UNDEFINED;
        *outBpp = 4;
        break;
    }
}

PFN_vkVoidFunction lookup_shiraz_export(const char* pName) {
    if (!pName) {
        return nullptr;
    }
#define SHIRAZ_VK_MATCH(sym)                                                                       \
    if (std::strcmp(pName, #sym) == 0) {                                                           \
        return reinterpret_cast<PFN_vkVoidFunction>(&sym);                                         \
    }
    SHIRAZ_VK_MATCH(vkGetInstanceProcAddr)
    SHIRAZ_VK_MATCH(vkGetDeviceProcAddr)
    SHIRAZ_VK_MATCH(vkEnumerateInstanceVersion)
    SHIRAZ_VK_MATCH(vkEnumerateInstanceExtensionProperties)
    SHIRAZ_VK_MATCH(vkEnumerateInstanceLayerProperties)
    SHIRAZ_VK_MATCH(vkCreateInstance)
    SHIRAZ_VK_MATCH(vkDestroyInstance)
    SHIRAZ_VK_MATCH(vkEnumeratePhysicalDevices)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceProperties)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceFeatures)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceQueueFamilyProperties)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceMemoryProperties)
    SHIRAZ_VK_MATCH(vkEnumerateDeviceExtensionProperties)
    SHIRAZ_VK_MATCH(vkCreateDevice)
    SHIRAZ_VK_MATCH(vkDestroyDevice)
    SHIRAZ_VK_MATCH(vkGetDeviceQueue)
    SHIRAZ_VK_MATCH(vkDeviceWaitIdle)
    SHIRAZ_VK_MATCH(vkQueueSubmit)
    SHIRAZ_VK_MATCH(vkQueueWaitIdle)
    SHIRAZ_VK_MATCH(vkCreateAndroidSurfaceKHR)
    SHIRAZ_VK_MATCH(vkDestroySurfaceKHR)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceSurfaceSupportKHR)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceSurfaceCapabilitiesKHR)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceSurfaceFormatsKHR)
    SHIRAZ_VK_MATCH(vkGetPhysicalDeviceSurfacePresentModesKHR)
    SHIRAZ_VK_MATCH(vkCreateSwapchainKHR)
    SHIRAZ_VK_MATCH(vkDestroySwapchainKHR)
    SHIRAZ_VK_MATCH(vkGetSwapchainImagesKHR)
    SHIRAZ_VK_MATCH(vkAcquireNextImageKHR)
    SHIRAZ_VK_MATCH(vkQueuePresentKHR)
    SHIRAZ_VK_MATCH(vkGetAndroidHardwareBufferPropertiesANDROID)
    SHIRAZ_VK_MATCH(vkGetMemoryAndroidHardwareBufferANDROID)
#undef SHIRAZ_VK_MATCH
    return nullptr;
}

} // namespace

extern "C" {

PFN_vkVoidFunction vkGetInstanceProcAddr(VkInstance instance, const char* pName) {
    if (!pName) {
        return nullptr;
    }
    // Global commands allowed with instance == nullptr per Vulkan spec.
    if (!instance) {
        if (std::strcmp(pName, "vkGetInstanceProcAddr") == 0 ||
            std::strcmp(pName, "vkEnumerateInstanceVersion") == 0 ||
            std::strcmp(pName, "vkEnumerateInstanceExtensionProperties") == 0 ||
            std::strcmp(pName, "vkEnumerateInstanceLayerProperties") == 0 ||
            std::strcmp(pName, "vkCreateInstance") == 0) {
            return lookup_shiraz_export(pName);
        }
        return nullptr;
    }
    if (auto local = lookup_shiraz_export(pName)) {
        return local;
    }
    const auto& loader = host_loader();
    if (loader.available()) {
        return loader.getInstanceProcAddr(instance, pName);
    }
    return nullptr;
}

PFN_vkVoidFunction vkGetDeviceProcAddr(VkDevice device, const char* pName) {
    if (!device || !pName) {
        return nullptr;
    }
    if (std::strcmp(pName, "vkGetDeviceProcAddr") == 0 ||
        std::strcmp(pName, "vkDestroyDevice") == 0 || std::strcmp(pName, "vkGetDeviceQueue") == 0 ||
        std::strcmp(pName, "vkDeviceWaitIdle") == 0 || std::strcmp(pName, "vkQueueSubmit") == 0 ||
        std::strcmp(pName, "vkQueueWaitIdle") == 0 ||
        std::strcmp(pName, "vkCreateSwapchainKHR") == 0 ||
        std::strcmp(pName, "vkDestroySwapchainKHR") == 0 ||
        std::strcmp(pName, "vkGetSwapchainImagesKHR") == 0 ||
        std::strcmp(pName, "vkAcquireNextImageKHR") == 0 ||
        std::strcmp(pName, "vkQueuePresentKHR") == 0 ||
        std::strcmp(pName, "vkGetAndroidHardwareBufferPropertiesANDROID") == 0 ||
        std::strcmp(pName, "vkGetMemoryAndroidHardwareBufferANDROID") == 0) {
        return lookup_shiraz_export(pName);
    }
    const auto& loader = host_loader();
    if (loader.available() && loader.getDeviceProcAddr) {
        return loader.getDeviceProcAddr(device, pName);
    }
    return nullptr;
}

VkResult vkEnumerateInstanceVersion(uint32_t* pApiVersion) {
    if (!pApiVersion) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.instanceProc<PFN_vkEnumerateInstanceVersion>(
            nullptr, "vkEnumerateInstanceVersion")) {
        return fn(pApiVersion);
    }
    *pApiVersion = VK_API_VERSION_1_3;
    return VK_SUCCESS;
}

VkResult vkEnumerateInstanceExtensionProperties(const char* pLayerName, uint32_t* pPropertyCount,
                                                VkExtensionProperties* pProperties) {
    if (!pPropertyCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (pLayerName && *pLayerName) {
        if (auto fn = loader.instanceProc<PFN_vkEnumerateInstanceExtensionProperties>(
                nullptr, "vkEnumerateInstanceExtensionProperties")) {
            return fn(pLayerName, pPropertyCount, pProperties);
        }
        *pPropertyCount = 0;
        return VK_ERROR_LAYER_NOT_PRESENT;
    }

    std::vector<VkExtensionProperties> extensions;
    if (auto fn = loader.instanceProc<PFN_vkEnumerateInstanceExtensionProperties>(
            nullptr, "vkEnumerateInstanceExtensionProperties")) {
        uint32_t count = 0;
        if (fn(nullptr, &count, nullptr) == VK_SUCCESS && count > 0) {
            extensions.resize(count);
            if (fn(nullptr, &count, extensions.data()) == VK_SUCCESS) {
                extensions.resize(count);
            } else {
                extensions.clear();
            }
        }
    }
    append_extension(extensions, VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_SURFACE_SPEC_VERSION);
    append_extension(extensions, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
                     VK_KHR_ANDROID_SURFACE_SPEC_VERSION);

    if (!pProperties) {
        *pPropertyCount = static_cast<uint32_t>(extensions.size());
        return VK_SUCCESS;
    }
    const uint32_t capacity = *pPropertyCount;
    const uint32_t toCopy = std::min<uint32_t>(capacity, static_cast<uint32_t>(extensions.size()));
    for (uint32_t i = 0; i < toCopy; ++i) {
        pProperties[i] = extensions[i];
    }
    *pPropertyCount = toCopy;
    return toCopy < extensions.size() ? VK_INCOMPLETE : VK_SUCCESS;
}

VkResult vkEnumerateInstanceLayerProperties(uint32_t* pPropertyCount,
                                            VkLayerProperties* pProperties) {
    if (!pPropertyCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.instanceProc<PFN_vkEnumerateInstanceLayerProperties>(
            nullptr, "vkEnumerateInstanceLayerProperties")) {
        return fn(pPropertyCount, pProperties);
    }
    *pPropertyCount = 0;
    return VK_SUCCESS;
}

VkResult vkCreateInstance(const VkInstanceCreateInfo* pCreateInfo,
                          const VkAllocationCallbacks* pAllocator, VkInstance* pInstance) {
    if (!pCreateInfo || !pInstance ||
        pCreateInfo->sType != VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    *pInstance = nullptr;
    const auto& loader = host_loader();
    auto fn = loader.instanceProc<PFN_vkCreateInstance>(nullptr, "vkCreateInstance");
    if (!fn) {
        return VK_ERROR_INCOMPATIBLE_DRIVER;
    }

    // Strip Android-only VK_KHR_android_surface when forwarding to a desktop ICD.
    std::vector<const char*> filteredExts;
    filteredExts.reserve(pCreateInfo->enabledExtensionCount);
    for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount; ++i) {
        const char* ext = pCreateInfo->ppEnabledExtensionNames[i];
        if (ext && std::strcmp(ext, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME) == 0) {
            continue;
        }
        filteredExts.push_back(ext);
    }
    VkInstanceCreateInfo forwarded = *pCreateInfo;
    forwarded.enabledExtensionCount = static_cast<uint32_t>(filteredExts.size());
    forwarded.ppEnabledExtensionNames = filteredExts.empty() ? nullptr : filteredExts.data();
    return fn(&forwarded, pAllocator, pInstance);
}

void vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks* pAllocator) {
    if (!instance) {
        return;
    }
    {
        auto& st = vk_state();
        std::lock_guard<std::mutex> lock(st.mu);
        for (auto it = st.surfaces.begin(); it != st.surfaces.end();) {
            if (it->second.instance == instance) {
                if (it->second.window) {
                    ANativeWindow_release(it->second.window);
                }
                it = st.surfaces.erase(it);
            } else {
                ++it;
            }
        }
    }
    const auto& loader = host_loader();
    if (auto fn = loader.instanceProc<PFN_vkDestroyInstance>(instance, "vkDestroyInstance")) {
        fn(instance, pAllocator);
    }
}

VkResult vkEnumeratePhysicalDevices(VkInstance instance, uint32_t* pPhysicalDeviceCount,
                                    VkPhysicalDevice* pPhysicalDevices) {
    if (!instance || !pPhysicalDeviceCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.instanceProc<PFN_vkEnumeratePhysicalDevices>(
            instance, "vkEnumeratePhysicalDevices")) {
        return fn(instance, pPhysicalDeviceCount, pPhysicalDevices);
    }
    *pPhysicalDeviceCount = 0;
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

void vkGetPhysicalDeviceProperties(VkPhysicalDevice physicalDevice,
                                   VkPhysicalDeviceProperties* pProperties) {
    if (!physicalDevice || !pProperties) {
        return;
    }
    std::memset(pProperties, 0, sizeof(*pProperties));
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkGetPhysicalDeviceProperties>(loader.handle,
                                                                "vkGetPhysicalDeviceProperties")) {
        fn(physicalDevice, pProperties);
    }
}

void vkGetPhysicalDeviceFeatures(VkPhysicalDevice physicalDevice,
                                 VkPhysicalDeviceFeatures* pFeatures) {
    if (!physicalDevice || !pFeatures) {
        return;
    }
    std::memset(pFeatures, 0, sizeof(*pFeatures));
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkGetPhysicalDeviceFeatures>(loader.handle,
                                                              "vkGetPhysicalDeviceFeatures")) {
        fn(physicalDevice, pFeatures);
    }
}

void vkGetPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice physicalDevice,
                                              uint32_t* pQueueFamilyPropertyCount,
                                              VkQueueFamilyProperties* pQueueFamilyProperties) {
    if (!pQueueFamilyPropertyCount) {
        return;
    }
    if (!physicalDevice) {
        *pQueueFamilyPropertyCount = 0;
        return;
    }
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
            loader.handle, "vkGetPhysicalDeviceQueueFamilyProperties")) {
        fn(physicalDevice, pQueueFamilyPropertyCount, pQueueFamilyProperties);
        return;
    }
    *pQueueFamilyPropertyCount = 0;
}

void vkGetPhysicalDeviceMemoryProperties(VkPhysicalDevice physicalDevice,
                                         VkPhysicalDeviceMemoryProperties* pMemoryProperties) {
    if (!physicalDevice || !pMemoryProperties) {
        return;
    }
    std::memset(pMemoryProperties, 0, sizeof(*pMemoryProperties));
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkGetPhysicalDeviceMemoryProperties>(
            loader.handle, "vkGetPhysicalDeviceMemoryProperties")) {
        fn(physicalDevice, pMemoryProperties);
    }
}

VkResult vkEnumerateDeviceExtensionProperties(VkPhysicalDevice physicalDevice,
                                              const char* pLayerName, uint32_t* pPropertyCount,
                                              VkExtensionProperties* pProperties) {
    if (!physicalDevice || !pPropertyCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (pLayerName && *pLayerName) {
        if (auto fn = load_dlsym<PFN_vkEnumerateDeviceExtensionProperties>(
                loader.handle, "vkEnumerateDeviceExtensionProperties")) {
            return fn(physicalDevice, pLayerName, pPropertyCount, pProperties);
        }
        *pPropertyCount = 0;
        return VK_ERROR_LAYER_NOT_PRESENT;
    }

    std::vector<VkExtensionProperties> extensions;
    if (auto fn = load_dlsym<PFN_vkEnumerateDeviceExtensionProperties>(
            loader.handle, "vkEnumerateDeviceExtensionProperties")) {
        uint32_t count = 0;
        if (fn(physicalDevice, nullptr, &count, nullptr) == VK_SUCCESS && count > 0) {
            extensions.resize(count);
            if (fn(physicalDevice, nullptr, &count, extensions.data()) == VK_SUCCESS) {
                extensions.resize(count);
            } else {
                extensions.clear();
            }
        }
    }
    append_extension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME, VK_KHR_SWAPCHAIN_SPEC_VERSION);
    append_extension(extensions, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
                     VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION);

    if (!pProperties) {
        *pPropertyCount = static_cast<uint32_t>(extensions.size());
        return VK_SUCCESS;
    }
    const uint32_t capacity = *pPropertyCount;
    const uint32_t toCopy = std::min<uint32_t>(capacity, static_cast<uint32_t>(extensions.size()));
    for (uint32_t i = 0; i < toCopy; ++i) {
        pProperties[i] = extensions[i];
    }
    *pPropertyCount = toCopy;
    return toCopy < extensions.size() ? VK_INCOMPLETE : VK_SUCCESS;
}

VkResult vkCreateDevice(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo,
                        const VkAllocationCallbacks* pAllocator, VkDevice* pDevice) {
    if (!physicalDevice || !pCreateInfo || !pDevice ||
        pCreateInfo->sType != VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    *pDevice = nullptr;
    const auto& loader = host_loader();
    auto fn = load_dlsym<PFN_vkCreateDevice>(loader.handle, "vkCreateDevice");
    if (!fn) {
        return VK_ERROR_INCOMPATIBLE_DRIVER;
    }

    std::vector<const char*> filteredExts;
    filteredExts.reserve(pCreateInfo->enabledExtensionCount);
    for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount; ++i) {
        const char* ext = pCreateInfo->ppEnabledExtensionNames[i];
        if (ext &&
            std::strcmp(ext, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME) ==
                0) {
            continue;
        }
        filteredExts.push_back(ext);
    }
    VkDeviceCreateInfo forwarded = *pCreateInfo;
    forwarded.enabledExtensionCount = static_cast<uint32_t>(filteredExts.size());
    forwarded.ppEnabledExtensionNames = filteredExts.empty() ? nullptr : filteredExts.data();
    return fn(physicalDevice, &forwarded, pAllocator, pDevice);
}

void vkDestroyDevice(VkDevice device, const VkAllocationCallbacks* pAllocator) {
    if (!device) {
        return;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkDestroyDevice>(device, "vkDestroyDevice")) {
        fn(device, pAllocator);
    }
}

void vkGetDeviceQueue(VkDevice device, uint32_t queueFamilyIndex, uint32_t queueIndex,
                      VkQueue* pQueue) {
    if (!pQueue) {
        return;
    }
    *pQueue = nullptr;
    if (!device) {
        return;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkGetDeviceQueue>(device, "vkGetDeviceQueue")) {
        fn(device, queueFamilyIndex, queueIndex, pQueue);
    }
}

VkResult vkDeviceWaitIdle(VkDevice device) {
    if (!device) {
        return VK_ERROR_DEVICE_LOST;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkDeviceWaitIdle>(device, "vkDeviceWaitIdle")) {
        return fn(device);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

VkResult vkQueueSubmit(VkQueue queue, uint32_t submitCount, const VkSubmitInfo* pSubmits,
                       VkFence fence) {
    if (!queue) {
        return VK_ERROR_DEVICE_LOST;
    }
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkQueueSubmit>(loader.handle, "vkQueueSubmit")) {
        return fn(queue, submitCount, pSubmits, fence);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

VkResult vkQueueWaitIdle(VkQueue queue) {
    if (!queue) {
        return VK_ERROR_DEVICE_LOST;
    }
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkQueueWaitIdle>(loader.handle, "vkQueueWaitIdle")) {
        return fn(queue);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

// --- VK_KHR_android_surface & VK_KHR_surface ---

VkResult vkCreateAndroidSurfaceKHR(VkInstance instance,
                                   const VkAndroidSurfaceCreateInfoKHR* pCreateInfo,
                                   const VkAllocationCallbacks* pAllocator,
                                   VkSurfaceKHR* pSurface) {
    (void)pAllocator;
    if (!pCreateInfo || !pSurface ||
        pCreateInfo->sType != VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR ||
        !pCreateInfo->window) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    ANativeWindow_acquire(pCreateInfo->window);
    auto& st = vk_state();
    const VkSurfaceKHR handle = st.nextHandle.fetch_add(1, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(st.mu);
        st.surfaces[handle] = AndroidSurfaceRecord{instance, pCreateInfo->window};
    }
    *pSurface = handle;
    return VK_SUCCESS;
}

void vkDestroySurfaceKHR(VkInstance instance, VkSurfaceKHR surface,
                         const VkAllocationCallbacks* pAllocator) {
    (void)instance;
    (void)pAllocator;
    if (surface == VK_NULL_HANDLE) {
        return;
    }
    ANativeWindow* window = nullptr;
    {
        auto& st = vk_state();
        std::lock_guard<std::mutex> lock(st.mu);
        auto it = st.surfaces.find(surface);
        if (it != st.surfaces.end()) {
            window = it->second.window;
            st.surfaces.erase(it);
        }
    }
    if (window) {
        ANativeWindow_release(window);
    }
}

VkResult vkGetPhysicalDeviceSurfaceSupportKHR(VkPhysicalDevice physicalDevice,
                                              uint32_t queueFamilyIndex, VkSurfaceKHR surface,
                                              VkBool32* pSupported) {
    (void)physicalDevice;
    (void)queueFamilyIndex;
    if (!pSupported) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    auto& st = vk_state();
    std::lock_guard<std::mutex> lock(st.mu);
    auto it = st.surfaces.find(surface);
    if (it == st.surfaces.end()) {
        *pSupported = VK_FALSE;
        return VK_ERROR_SURFACE_LOST_KHR;
    }
    *pSupported = host_loader().available() ? VK_TRUE : VK_FALSE;
    return VK_SUCCESS;
}

VkResult vkGetPhysicalDeviceSurfaceCapabilitiesKHR(VkPhysicalDevice physicalDevice,
                                                   VkSurfaceKHR surface,
                                                   VkSurfaceCapabilitiesKHR* pSurfaceCapabilities) {
    (void)physicalDevice;
    if (!pSurfaceCapabilities) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    ANativeWindow* window = nullptr;
    {
        auto& st = vk_state();
        std::lock_guard<std::mutex> lock(st.mu);
        auto it = st.surfaces.find(surface);
        if (it == st.surfaces.end() || !it->second.window) {
            return VK_ERROR_SURFACE_LOST_KHR;
        }
        window = it->second.window;
        ANativeWindow_acquire(window);
    }
    const int32_t w = ANativeWindow_getWidth(window);
    const int32_t h = ANativeWindow_getHeight(window);
    ANativeWindow_release(window);

    const uint32_t width = w > 0 ? static_cast<uint32_t>(w) : 1U;
    const uint32_t height = h > 0 ? static_cast<uint32_t>(h) : 1U;
    std::memset(pSurfaceCapabilities, 0, sizeof(*pSurfaceCapabilities));
    pSurfaceCapabilities->minImageCount = 2;
    pSurfaceCapabilities->maxImageCount = 4;
    pSurfaceCapabilities->currentExtent = {width, height};
    pSurfaceCapabilities->minImageExtent = {width, height};
    pSurfaceCapabilities->maxImageExtent = {width, height};
    pSurfaceCapabilities->maxImageArrayLayers = 1;
    pSurfaceCapabilities->supportedTransforms = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    pSurfaceCapabilities->currentTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    pSurfaceCapabilities->supportedCompositeAlpha =
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR | VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    pSurfaceCapabilities->supportedUsageFlags =
        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
        VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    return VK_SUCCESS;
}

VkResult vkGetPhysicalDeviceSurfaceFormatsKHR(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface,
                                              uint32_t* pSurfaceFormatCount,
                                              VkSurfaceFormatKHR* pSurfaceFormats) {
    (void)physicalDevice;
    if (!pSurfaceFormatCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    {
        auto& st = vk_state();
        std::lock_guard<std::mutex> lock(st.mu);
        if (st.surfaces.find(surface) == st.surfaces.end()) {
            return VK_ERROR_SURFACE_LOST_KHR;
        }
    }
    static constexpr VkSurfaceFormatKHR kFormats[] = {
        {VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR},
        {VK_FORMAT_R8G8B8A8_SRGB, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR},
        {VK_FORMAT_B8G8R8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR},
    };
    constexpr uint32_t kCount = static_cast<uint32_t>(sizeof(kFormats) / sizeof(kFormats[0]));
    if (!pSurfaceFormats) {
        *pSurfaceFormatCount = kCount;
        return VK_SUCCESS;
    }
    const uint32_t toCopy = std::min<uint32_t>(*pSurfaceFormatCount, kCount);
    for (uint32_t i = 0; i < toCopy; ++i) {
        pSurfaceFormats[i] = kFormats[i];
    }
    *pSurfaceFormatCount = toCopy;
    return toCopy < kCount ? VK_INCOMPLETE : VK_SUCCESS;
}

VkResult vkGetPhysicalDeviceSurfacePresentModesKHR(VkPhysicalDevice physicalDevice,
                                                   VkSurfaceKHR surface,
                                                   uint32_t* pPresentModeCount,
                                                   VkPresentModeKHR* pPresentModes) {
    (void)physicalDevice;
    if (!pPresentModeCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    {
        auto& st = vk_state();
        std::lock_guard<std::mutex> lock(st.mu);
        if (st.surfaces.find(surface) == st.surfaces.end()) {
            return VK_ERROR_SURFACE_LOST_KHR;
        }
    }
    static constexpr VkPresentModeKHR kModes[] = {
        VK_PRESENT_MODE_FIFO_KHR,
        VK_PRESENT_MODE_MAILBOX_KHR,
        VK_PRESENT_MODE_IMMEDIATE_KHR,
    };
    constexpr uint32_t kCount = static_cast<uint32_t>(sizeof(kModes) / sizeof(kModes[0]));
    if (!pPresentModes) {
        *pPresentModeCount = kCount;
        return VK_SUCCESS;
    }
    const uint32_t toCopy = std::min<uint32_t>(*pPresentModeCount, kCount);
    for (uint32_t i = 0; i < toCopy; ++i) {
        pPresentModes[i] = kModes[i];
    }
    *pPresentModeCount = toCopy;
    return toCopy < kCount ? VK_INCOMPLETE : VK_SUCCESS;
}

// --- VK_KHR_swapchain ---

VkResult vkCreateSwapchainKHR(VkDevice device, const VkSwapchainCreateInfoKHR* pCreateInfo,
                              const VkAllocationCallbacks* pAllocator, VkSwapchainKHR* pSwapchain) {
    if (!device || !pCreateInfo || !pSwapchain ||
        pCreateInfo->sType != VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    *pSwapchain = VK_NULL_HANDLE;
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkCreateSwapchainKHR>(device, "vkCreateSwapchainKHR")) {
        return fn(device, pCreateInfo, pAllocator, pSwapchain);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

void vkDestroySwapchainKHR(VkDevice device, VkSwapchainKHR swapchain,
                           const VkAllocationCallbacks* pAllocator) {
    if (!device || swapchain == VK_NULL_HANDLE) {
        return;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkDestroySwapchainKHR>(device, "vkDestroySwapchainKHR")) {
        fn(device, swapchain, pAllocator);
    }
}

VkResult vkGetSwapchainImagesKHR(VkDevice device, VkSwapchainKHR swapchain,
                                 uint32_t* pSwapchainImageCount, VkImage* pSwapchainImages) {
    if (!device || swapchain == VK_NULL_HANDLE || !pSwapchainImageCount) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn =
            loader.deviceProc<PFN_vkGetSwapchainImagesKHR>(device, "vkGetSwapchainImagesKHR")) {
        return fn(device, swapchain, pSwapchainImageCount, pSwapchainImages);
    }
    *pSwapchainImageCount = 0;
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

VkResult vkAcquireNextImageKHR(VkDevice device, VkSwapchainKHR swapchain, uint64_t timeout,
                               VkSemaphore semaphore, VkFence fence, uint32_t* pImageIndex) {
    if (!device || swapchain == VK_NULL_HANDLE || !pImageIndex) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn = loader.deviceProc<PFN_vkAcquireNextImageKHR>(device, "vkAcquireNextImageKHR")) {
        return fn(device, swapchain, timeout, semaphore, fence, pImageIndex);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

VkResult vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo) {
    if (!queue || !pPresentInfo || pPresentInfo->sType != VK_STRUCTURE_TYPE_PRESENT_INFO_KHR) {
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    const auto& loader = host_loader();
    if (auto fn = load_dlsym<PFN_vkQueuePresentKHR>(loader.handle, "vkQueuePresentKHR")) {
        return fn(queue, pPresentInfo);
    }
    return VK_ERROR_INCOMPATIBLE_DRIVER;
}

// --- VK_ANDROID_external_memory_android_hardware_buffer ---

VkResult
vkGetAndroidHardwareBufferPropertiesANDROID(VkDevice device, const AHardwareBuffer* buffer,
                                            VkAndroidHardwareBufferPropertiesANDROID* pProperties) {
    (void)device;
    if (!buffer || !pProperties ||
        pProperties->sType != VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID) {
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(buffer, &desc);
    if (desc.width == 0 || desc.height == 0) {
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }

    VkFormat vkFormat = VK_FORMAT_UNDEFINED;
    uint32_t bpp = 4;
    describe_ahb_format(desc.format, &vkFormat, &bpp);
    const uint64_t rowPixels = desc.stride >= desc.width ? desc.stride : desc.width;
    const uint64_t layers = desc.layers > 0 ? desc.layers : 1;
    pProperties->allocationSize = rowPixels * desc.height * layers * bpp;
    pProperties->memoryTypeBits = 0x1U;

    auto* next = static_cast<VkBaseOutStructure*>(pProperties->pNext);
    while (next) {
        if (next->sType == VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID) {
            auto* fmtProps =
                reinterpret_cast<VkAndroidHardwareBufferFormatPropertiesANDROID*>(next);
            fmtProps->format = vkFormat;
            fmtProps->externalFormat = vkFormat == VK_FORMAT_UNDEFINED ? desc.format : 0;
            fmtProps->formatFeatures = 0x00000001U | 0x00000080U; // SAMPLED | COLOR_ATTACHMENT
            fmtProps->samplerYcbcrConversionComponents = {0, 0, 0, 0};
            fmtProps->suggestedYcbcrModel = 0;
            fmtProps->suggestedYcbcrRange = 0;
            fmtProps->suggestedXChromaOffset = 0;
            fmtProps->suggestedYChromaOffset = 0;
        }
        next = next->pNext;
    }
    return VK_SUCCESS;
}

VkResult
vkGetMemoryAndroidHardwareBufferANDROID(VkDevice device,
                                        const VkMemoryGetAndroidHardwareBufferInfoANDROID* pInfo,
                                        AHardwareBuffer** pBuffer) {
    (void)device;
    if (!pInfo || !pBuffer ||
        pInfo->sType != VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID) {
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }
    *pBuffer = nullptr;
    auto& st = vk_state();
    std::lock_guard<std::mutex> lock(st.mu);
    auto it = st.ahbMemories.find(pInfo->memory);
    if (it == st.ahbMemories.end() || !it->second) {
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }
    AHardwareBuffer_acquire(it->second);
    *pBuffer = it->second;
    return VK_SUCCESS;
}

} // extern "C"
