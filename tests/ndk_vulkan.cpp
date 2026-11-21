// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N8: libvulkan.so (32/32 symbols).
#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"
#include "shiraz/ndk/vulkan.h"

#include <cassert>
#include <cstdio>
#include <cstring>
#include <vector>

int main() {
    // 1. Global vkGetInstanceProcAddr resolution.
    assert(vkGetInstanceProcAddr(nullptr, "vkGetInstanceProcAddr") != nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion") != nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceExtensionProperties") != nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceLayerProperties") != nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkCreateInstance") != nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkDestroyInstance") == nullptr);
    assert(vkGetInstanceProcAddr(nullptr, "vkNonExistentFunc") == nullptr);

    // 2. Instance and device proc lookup for Android extensions and core commands.
    auto fakeInstance = reinterpret_cast<VkInstance>(0x1000);
    auto fakeDevice = reinterpret_cast<VkDevice>(0x2000);
    assert(vkGetInstanceProcAddr(fakeInstance, "vkCreateAndroidSurfaceKHR") != nullptr);
    assert(vkGetInstanceProcAddr(fakeInstance, "vkDestroySurfaceKHR") != nullptr);
    assert(vkGetInstanceProcAddr(fakeInstance, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR") !=
           nullptr);
    assert(vkGetInstanceProcAddr(fakeInstance, "vkGetPhysicalDeviceSurfaceFormatsKHR") != nullptr);
    assert(vkGetInstanceProcAddr(fakeInstance, "vkGetPhysicalDeviceSurfacePresentModesKHR") !=
           nullptr);
    assert(vkGetDeviceProcAddr(fakeDevice, "vkCreateSwapchainKHR") != nullptr);
    assert(vkGetDeviceProcAddr(fakeDevice, "vkAcquireNextImageKHR") != nullptr);
    assert(vkGetDeviceProcAddr(fakeDevice, "vkQueuePresentKHR") != nullptr);
    assert(vkGetDeviceProcAddr(fakeDevice, "vkGetAndroidHardwareBufferPropertiesANDROID") !=
           nullptr);
    assert(vkGetDeviceProcAddr(fakeDevice, "vkGetMemoryAndroidHardwareBufferANDROID") != nullptr);
    assert(vkGetDeviceProcAddr(nullptr, "vkCreateSwapchainKHR") == nullptr);

    // 3. Instance version and layer/extension enumeration.
    uint32_t apiVersion = 0;
    assert(vkEnumerateInstanceVersion(&apiVersion) == VK_SUCCESS);
    assert(apiVersion >= VK_API_VERSION_1_0);

    uint32_t extCount = 0;
    assert(vkEnumerateInstanceExtensionProperties(nullptr, &extCount, nullptr) == VK_SUCCESS);
    assert(extCount >= 2);

    // Partial read must return VK_INCOMPLETE.
    VkExtensionProperties oneExt{};
    uint32_t oneCount = 1;
    assert(vkEnumerateInstanceExtensionProperties(nullptr, &oneCount, &oneExt) == VK_INCOMPLETE);
    assert(oneCount == 1);

    std::vector<VkExtensionProperties> exts(extCount);
    assert(vkEnumerateInstanceExtensionProperties(nullptr, &extCount, exts.data()) == VK_SUCCESS);
    bool hasSurface = false;
    bool hasAndroidSurface = false;
    for (const auto& e : exts) {
        if (std::strcmp(e.extensionName, VK_KHR_SURFACE_EXTENSION_NAME) == 0) {
            hasSurface = true;
        }
        if (std::strcmp(e.extensionName, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME) == 0) {
            hasAndroidSurface = true;
        }
    }
    assert(hasSurface);
    assert(hasAndroidSurface);

    uint32_t bogusCount = 0;
    assert(vkEnumerateInstanceExtensionProperties("VK_LAYER_NON_EXISTENT", &bogusCount, nullptr) ==
           VK_ERROR_LAYER_NOT_PRESENT);

    uint32_t layerCount = 99;
    assert(vkEnumerateInstanceLayerProperties(&layerCount, nullptr) == VK_SUCCESS);

    // 4. Instance creation: either connects to a real host Vulkan ICD or fails explicitly
    // with VK_ERROR_INCOMPATIBLE_DRIVER (never a silent fake GPU).
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "shiraz-ndk-vulkan-test";
    appInfo.apiVersion = VK_API_VERSION_1_3;

    const char* requestedExts[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
    };
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = requestedExts;

    VkInstance instance = nullptr;
    VkResult ciRes = vkCreateInstance(&createInfo, nullptr, &instance);
    assert(ciRes == VK_SUCCESS || ciRes == VK_ERROR_INCOMPATIBLE_DRIVER);
    if (ciRes == VK_SUCCESS) {
        assert(instance != nullptr);
    } else {
        assert(instance == nullptr);
    }

    // 5. VK_KHR_android_surface backed by a real Shiraz ANativeWindow.
    ANativeWindow* win = shiraz_window_create(640, 360);
    assert(win != nullptr);

    VkAndroidSurfaceCreateInfoKHR surfInfo{};
    surfInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfInfo.window = win;

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    assert(vkCreateAndroidSurfaceKHR(instance, &surfInfo, nullptr, &surface) == VK_SUCCESS);
    assert(surface != VK_NULL_HANDLE);

    VkSurfaceCapabilitiesKHR caps{};
    assert(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(nullptr, surface, &caps) == VK_SUCCESS);
    assert(caps.currentExtent.width == 640);
    assert(caps.currentExtent.height == 360);
    assert(caps.minImageCount >= 2);

    // Resizing the ANativeWindow reflects live in surface capabilities.
    assert(shiraz_window_resize(win, 800, 480) == 0);
    assert(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(nullptr, surface, &caps) == VK_SUCCESS);
    assert(caps.currentExtent.width == 800);
    assert(caps.currentExtent.height == 480);

    uint32_t fmtCount = 0;
    assert(vkGetPhysicalDeviceSurfaceFormatsKHR(nullptr, surface, &fmtCount, nullptr) ==
           VK_SUCCESS);
    assert(fmtCount >= 1);
    std::vector<VkSurfaceFormatKHR> fmts(fmtCount);
    assert(vkGetPhysicalDeviceSurfaceFormatsKHR(nullptr, surface, &fmtCount, fmts.data()) ==
           VK_SUCCESS);
    assert(fmts[0].format == VK_FORMAT_R8G8B8A8_UNORM);
    assert(fmts[0].colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);

    uint32_t modeCount = 0;
    assert(vkGetPhysicalDeviceSurfacePresentModesKHR(nullptr, surface, &modeCount, nullptr) ==
           VK_SUCCESS);
    assert(modeCount >= 1);
    std::vector<VkPresentModeKHR> modes(modeCount);
    assert(vkGetPhysicalDeviceSurfacePresentModesKHR(nullptr, surface, &modeCount, modes.data()) ==
           VK_SUCCESS);
    assert(modes[0] == VK_PRESENT_MODE_FIFO_KHR);

    VkBool32 supported = VK_FALSE;
    assert(vkGetPhysicalDeviceSurfaceSupportKHR(nullptr, 0, surface, &supported) == VK_SUCCESS);

    vkDestroySurfaceKHR(instance, surface, nullptr);
    assert(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(nullptr, surface, &caps) ==
           VK_ERROR_SURFACE_LOST_KHR);
    ANativeWindow_release(win);

    if (instance) {
        vkDestroyInstance(instance, nullptr);
    }

    // 6. VK_ANDROID_external_memory_android_hardware_buffer backed by a real AHardwareBuffer.
    AHardwareBuffer_Desc ahbDesc{};
    ahbDesc.width = 64;
    ahbDesc.height = 32;
    ahbDesc.layers = 1;
    ahbDesc.format = 1; // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM
    ahbDesc.usage = 0x33ULL;

    AHardwareBuffer* ahb = nullptr;
    assert(AHardwareBuffer_allocate(&ahbDesc, &ahb) == 0);
    assert(ahb != nullptr);

    VkAndroidHardwareBufferFormatPropertiesANDROID fmtProps{};
    fmtProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;

    VkAndroidHardwareBufferPropertiesANDROID ahbProps{};
    ahbProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    ahbProps.pNext = &fmtProps;

    assert(vkGetAndroidHardwareBufferPropertiesANDROID(fakeDevice, ahb, &ahbProps) == VK_SUCCESS);
    assert(ahbProps.allocationSize >= 64ULL * 32ULL * 4ULL);
    assert(ahbProps.memoryTypeBits != 0);
    assert(fmtProps.format == VK_FORMAT_R8G8B8A8_UNORM);
    assert(fmtProps.externalFormat == 0);

    // Invalid memory handle must fail explicitly.
    VkMemoryGetAndroidHardwareBufferInfoANDROID memInfo{};
    memInfo.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    memInfo.memory = 999999ULL;
    AHardwareBuffer* outAhb = nullptr;
    assert(vkGetMemoryAndroidHardwareBufferANDROID(fakeDevice, &memInfo, &outAhb) ==
           VK_ERROR_INVALID_EXTERNAL_HANDLE);
    assert(outAhb == nullptr);

    AHardwareBuffer_release(ahb);

    std::printf("ndk_vulkan: ALL TESTS PASSED\n");
    return 0;
}
