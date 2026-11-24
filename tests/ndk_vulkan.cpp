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
    assert(supported == VK_TRUE);

    // 5b. Full ANativeWindow-backed swapchain lifecycle & pixel presentation.
    auto fakeQueue = reinterpret_cast<VkQueue>(0x3000);
    VkSwapchainCreateInfoKHR scInfo{};
    scInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    scInfo.surface = surface;
    scInfo.minImageCount = 3;
    scInfo.imageFormat = VK_FORMAT_R8G8B8A8_UNORM;
    scInfo.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    scInfo.imageExtent = {800, 480};
    scInfo.imageArrayLayers = 1;
    scInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    scInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;

    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    assert(vkCreateSwapchainKHR(fakeDevice, &scInfo, nullptr, &swapchain) == VK_SUCCESS);
    assert(swapchain != VK_NULL_HANDLE);

    uint32_t imgCount = 0;
    assert(vkGetSwapchainImagesKHR(fakeDevice, swapchain, &imgCount, nullptr) == VK_SUCCESS);
    assert(imgCount == 3);

    VkImage oneImage = VK_NULL_HANDLE;
    uint32_t partialImgCount = 1;
    assert(vkGetSwapchainImagesKHR(fakeDevice, swapchain, &partialImgCount, &oneImage) ==
           VK_INCOMPLETE);
    assert(partialImgCount == 1 && oneImage != VK_NULL_HANDLE);

    std::vector<VkImage> scImages(imgCount, VK_NULL_HANDLE);
    assert(vkGetSwapchainImagesKHR(fakeDevice, swapchain, &imgCount, scImages.data()) ==
           VK_SUCCESS);
    assert(scImages[0] != scImages[1] && scImages[1] != scImages[2]);

    uint32_t acquired0 = 99;
    assert(vkAcquireNextImageKHR(fakeDevice, swapchain, UINT64_MAX, VK_NULL_HANDLE, VK_NULL_HANDLE,
                                 &acquired0) == VK_SUCCESS);
    assert(acquired0 == 0);

    // Write RGBA=(0x11, 0x22, 0x33, 0xFF) into slot 0 and present to ANativeWindow.
    constexpr uint64_t kCpuWriteOften = 0x30ULL;
    AHardwareBuffer* slotBuf = nullptr;
    assert(shiraz_vulkan_swapchain_image_buffer(swapchain, acquired0, &slotBuf) == VK_SUCCESS);
    assert(slotBuf != nullptr);
    void* mapped = nullptr;
    assert(AHardwareBuffer_lock(slotBuf, kCpuWriteOften, -1, nullptr, &mapped) == 0);
    assert(mapped != nullptr);
    auto* rgba = static_cast<uint8_t*>(mapped);
    rgba[0] = 0x11;
    rgba[1] = 0x22;
    rgba[2] = 0x33;
    rgba[3] = 0xFF;
    assert(AHardwareBuffer_unlock(slotBuf, nullptr) == 0);
    AHardwareBuffer_release(slotBuf);

    const uint64_t revBefore = shiraz_window_revision(win);
    VkResult presentResult = VK_ERROR_UNKNOWN;
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &swapchain;
    presentInfo.pImageIndices = &acquired0;
    presentInfo.pResults = &presentResult;
    assert(vkQueuePresentKHR(fakeQueue, &presentInfo) == VK_SUCCESS);
    assert(presentResult == VK_SUCCESS);
    assert(shiraz_window_revision(win) > revBefore);

    std::vector<uint32_t> argb(800U * 480U, 0);
    assert(shiraz_window_copy_argb(win, argb.data(), argb.size()) == 0);
    assert(argb[0] == 0xFF112233U);

    // Second acquire advances round-robin to slot 1.
    uint32_t acquired1 = 99;
    assert(vkAcquireNextImageKHR(fakeDevice, swapchain, UINT64_MAX, VK_NULL_HANDLE, VK_NULL_HANDLE,
                                 &acquired1) == VK_SUCCESS);
    assert(acquired1 == 1);

    // BGRA swapchain replacement retires oldSwapchain and converts BGRA -> ARGB on present.
    VkSwapchainCreateInfoKHR bgraInfo = scInfo;
    bgraInfo.imageFormat = VK_FORMAT_B8G8R8A8_UNORM;
    bgraInfo.oldSwapchain = swapchain;
    VkSwapchainKHR bgraSwapchain = VK_NULL_HANDLE;
    assert(vkCreateSwapchainKHR(fakeDevice, &bgraInfo, nullptr, &bgraSwapchain) == VK_SUCCESS);
    assert(bgraSwapchain != VK_NULL_HANDLE);

    // Retired swapchain reports VK_ERROR_OUT_OF_DATE_KHR.
    uint32_t retiredIdx = 0;
    assert(vkAcquireNextImageKHR(fakeDevice, swapchain, UINT64_MAX, VK_NULL_HANDLE, VK_NULL_HANDLE,
                                 &retiredIdx) == VK_ERROR_OUT_OF_DATE_KHR);
    vkDestroySwapchainKHR(fakeDevice, swapchain, nullptr);

    uint32_t bgraIdx = 99;
    assert(vkAcquireNextImageKHR(fakeDevice, bgraSwapchain, UINT64_MAX, VK_NULL_HANDLE,
                                 VK_NULL_HANDLE, &bgraIdx) == VK_SUCCESS);
    AHardwareBuffer* bgraBuf = nullptr;
    assert(shiraz_vulkan_swapchain_image_buffer(bgraSwapchain, bgraIdx, &bgraBuf) == VK_SUCCESS);
    void* bgraMapped = nullptr;
    assert(AHardwareBuffer_lock(bgraBuf, kCpuWriteOften, -1, nullptr, &bgraMapped) == 0);
    auto* bgraBytes = static_cast<uint8_t*>(bgraMapped);
    bgraBytes[0] = 0xAA; // B
    bgraBytes[1] = 0xBB; // G
    bgraBytes[2] = 0xCC; // R
    bgraBytes[3] = 0xFF; // A
    assert(AHardwareBuffer_unlock(bgraBuf, nullptr) == 0);
    AHardwareBuffer_release(bgraBuf);

    presentInfo.pSwapchains = &bgraSwapchain;
    presentInfo.pImageIndices = &bgraIdx;
    assert(vkQueuePresentKHR(fakeQueue, &presentInfo) == VK_SUCCESS);
    assert(shiraz_window_copy_argb(win, argb.data(), argb.size()) == 0);
    assert(argb[0] == 0xFFCCBBAAU);

    // Resizing ANativeWindow marks the active swapchain out of date.
    assert(shiraz_window_resize(win, 320, 240) == 0);
    assert(vkAcquireNextImageKHR(fakeDevice, bgraSwapchain, UINT64_MAX, VK_NULL_HANDLE,
                                 VK_NULL_HANDLE, &bgraIdx) == VK_ERROR_OUT_OF_DATE_KHR);
    vkDestroySwapchainKHR(fakeDevice, bgraSwapchain, nullptr);

    vkDestroySurfaceKHR(instance, surface, nullptr);
    assert(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(nullptr, surface, &caps) ==
           VK_ERROR_SURFACE_LOST_KHR);
    ANativeWindow_release(win);

    if (instance) {
        vkDestroyInstance(instance, nullptr);
    }

    // 5c. Host Vulkan capability probe agrees with host ICD availability.
    uint32_t probedApi = 0;
    int32_t probedLevel = -1;
    const int32_t hasHostVk = shiraz_vulkan_host_probe(&probedApi, &probedLevel);
    assert(hasHostVk == 0 || hasHostVk == 1);
    if (hasHostVk == 1) {
        assert(probedApi >= VK_API_VERSION_1_0);
        assert(probedLevel == 0 || probedLevel == 1);
    } else {
        assert(probedApi == 0);
        assert(probedLevel == -1);
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

    // Import AHardwareBuffer into VkDeviceMemory and retrieve it back.
    VkImportAndroidHardwareBufferInfoANDROID importInfo{};
    importInfo.sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    importInfo.buffer = ahb;

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.pNext = &importInfo;
    allocInfo.allocationSize = ahbProps.allocationSize;
    allocInfo.memoryTypeIndex = 0;

    VkDeviceMemory importedMem = VK_NULL_HANDLE;
    assert(vkAllocateMemory(fakeDevice, &allocInfo, nullptr, &importedMem) == VK_SUCCESS);
    assert(importedMem != VK_NULL_HANDLE);

    VkMemoryGetAndroidHardwareBufferInfoANDROID getImported{};
    getImported.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    getImported.memory = importedMem;
    AHardwareBuffer* fetchedAhb = nullptr;
    assert(vkGetMemoryAndroidHardwareBufferANDROID(fakeDevice, &getImported, &fetchedAhb) ==
           VK_SUCCESS);
    assert(fetchedAhb == ahb);
    AHardwareBuffer_release(fetchedAhb);

    vkFreeMemory(fakeDevice, importedMem, nullptr);
    fetchedAhb = nullptr;
    assert(vkGetMemoryAndroidHardwareBufferANDROID(fakeDevice, &getImported, &fetchedAhb) ==
           VK_ERROR_INVALID_EXTERNAL_HANDLE);
    assert(fetchedAhb == nullptr);

    // Exportable AHB memory allocation creates a retrievable AHardwareBuffer.
    VkExportMemoryAllocateInfo exportInfo{};
    exportInfo.sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO;
    exportInfo.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    VkMemoryAllocateInfo exportAlloc{};
    exportAlloc.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    exportAlloc.pNext = &exportInfo;
    exportAlloc.allocationSize = 4096;
    exportAlloc.memoryTypeIndex = 0;

    VkDeviceMemory exportedMem = VK_NULL_HANDLE;
    assert(vkAllocateMemory(fakeDevice, &exportAlloc, nullptr, &exportedMem) == VK_SUCCESS);
    assert(exportedMem != VK_NULL_HANDLE);

    VkMemoryGetAndroidHardwareBufferInfoANDROID getExported{};
    getExported.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    getExported.memory = exportedMem;
    AHardwareBuffer* exportedAhb = nullptr;
    assert(vkGetMemoryAndroidHardwareBufferANDROID(fakeDevice, &getExported, &exportedAhb) ==
           VK_SUCCESS);
    assert(exportedAhb != nullptr);
    AHardwareBuffer_Desc expDesc{};
    AHardwareBuffer_describe(exportedAhb, &expDesc);
    assert(static_cast<uint64_t>(expDesc.width) * expDesc.height * 4ULL >= 4096ULL);
    assert(expDesc.format == 1U);
    AHardwareBuffer_release(exportedAhb);
    vkFreeMemory(fakeDevice, exportedMem, nullptr);

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
