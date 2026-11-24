// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/vulkan.h"
#include <stdint.h>

_Static_assert(sizeof(VkResult) == 4 && sizeof(VkBool32) == 4 && sizeof(VkSurfaceKHR) == 8 &&
                   sizeof(VkSwapchainKHR) == 8 && sizeof(VkImage) == 8 &&
                   sizeof(VkDeviceMemory) == 8,
               "Android arm64 Vulkan ABI");

typedef VkResult (*PFN_EnumVer)(uint32_t*);
typedef VkResult (*PFN_EnumExt)(const char*, uint32_t*, VkExtensionProperties*);
typedef VkResult (*PFN_CreateAndroidSurface)(VkInstance, const VkAndroidSurfaceCreateInfoKHR*,
                                             const VkAllocationCallbacks*, VkSurfaceKHR*);
typedef void (*PFN_DestroySurface)(VkInstance, VkSurfaceKHR, const VkAllocationCallbacks*);
typedef VkResult (*PFN_GetCaps)(VkPhysicalDevice, VkSurfaceKHR, VkSurfaceCapabilitiesKHR*);
typedef VkResult (*PFN_CreateSwapchain)(VkDevice, const VkSwapchainCreateInfoKHR*,
                                        const VkAllocationCallbacks*, VkSwapchainKHR*);
typedef void (*PFN_DestroySwapchain)(VkDevice, VkSwapchainKHR, const VkAllocationCallbacks*);
typedef VkResult (*PFN_GetImages)(VkDevice, VkSwapchainKHR, uint32_t*, VkImage*);
typedef VkResult (*PFN_AcquireNext)(VkDevice, VkSwapchainKHR, uint64_t, VkSemaphore, VkFence,
                                    uint32_t*);
typedef VkResult (*PFN_QueuePresent)(VkQueue, const VkPresentInfoKHR*);
typedef VkResult (*PFN_GetAhbProps)(VkDevice, const struct AHardwareBuffer*,
                                    VkAndroidHardwareBufferPropertiesANDROID*);
typedef VkResult (*PFN_AllocMem)(VkDevice, const VkMemoryAllocateInfo*,
                                 const VkAllocationCallbacks*, VkDeviceMemory*);
typedef void (*PFN_FreeMem)(VkDevice, VkDeviceMemory, const VkAllocationCallbacks*);
typedef VkResult (*PFN_GetMemAhb)(VkDevice, const VkMemoryGetAndroidHardwareBufferInfoANDROID*,
                                  struct AHardwareBuffer**);

int run_guestvulkan(struct ANativeWindow* window, struct AHardwareBuffer* ahb,
                    uintptr_t* procedures) {
    // Global vkGetInstanceProcAddr rules: global procs succeed, instance-only & unknown fail.
    PFN_EnumVer enum_ver = (PFN_EnumVer)vkGetInstanceProcAddr(0, "vkEnumerateInstanceVersion");
    PFN_EnumExt enum_ext =
        (PFN_EnumExt)vkGetInstanceProcAddr(0, "vkEnumerateInstanceExtensionProperties");
    if (!enum_ver || !enum_ext)
        return __LINE__;
    if (vkGetInstanceProcAddr(0, "vkDestroyInstance") != 0)
        return __LINE__;
    if (vkGetInstanceProcAddr(0, "system") != 0)
        return __LINE__;

    VkInstance fake_instance = (VkInstance)(uintptr_t)0x1000;
    VkDevice fake_device = (VkDevice)(uintptr_t)0x2000;
    VkQueue fake_queue = (VkQueue)(uintptr_t)0x3000;

    if (vkGetInstanceProcAddr(fake_instance, "system") != 0)
        return __LINE__;
    if (vkGetDeviceProcAddr(0, "vkCreateSwapchainKHR") != 0)
        return __LINE__;
    if (vkGetDeviceProcAddr(fake_device, "vkCreateInstance") != 0)
        return __LINE__;

    PFN_CreateAndroidSurface create_surface =
        (PFN_CreateAndroidSurface)vkGetInstanceProcAddr(fake_instance, "vkCreateAndroidSurfaceKHR");
    PFN_DestroySurface destroy_surface =
        (PFN_DestroySurface)vkGetInstanceProcAddr(fake_instance, "vkDestroySurfaceKHR");
    PFN_GetCaps get_caps = (PFN_GetCaps)vkGetInstanceProcAddr(
        fake_instance, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
    PFN_CreateSwapchain create_sc =
        (PFN_CreateSwapchain)vkGetDeviceProcAddr(fake_device, "vkCreateSwapchainKHR");
    PFN_DestroySwapchain destroy_sc =
        (PFN_DestroySwapchain)vkGetDeviceProcAddr(fake_device, "vkDestroySwapchainKHR");
    PFN_GetImages get_images =
        (PFN_GetImages)vkGetDeviceProcAddr(fake_device, "vkGetSwapchainImagesKHR");
    PFN_AcquireNext acquire_next =
        (PFN_AcquireNext)vkGetDeviceProcAddr(fake_device, "vkAcquireNextImageKHR");
    PFN_QueuePresent queue_present =
        (PFN_QueuePresent)vkGetDeviceProcAddr(fake_device, "vkQueuePresentKHR");
    PFN_GetAhbProps get_ahb_props = (PFN_GetAhbProps)vkGetDeviceProcAddr(
        fake_device, "vkGetAndroidHardwareBufferPropertiesANDROID");
    PFN_AllocMem alloc_mem = (PFN_AllocMem)vkGetDeviceProcAddr(fake_device, "vkAllocateMemory");
    PFN_FreeMem free_mem = (PFN_FreeMem)vkGetDeviceProcAddr(fake_device, "vkFreeMemory");
    PFN_GetMemAhb get_mem_ahb =
        (PFN_GetMemAhb)vkGetDeviceProcAddr(fake_device, "vkGetMemoryAndroidHardwareBufferANDROID");

    if (!create_surface || !destroy_surface || !get_caps || !create_sc || !destroy_sc ||
        !get_images || !acquire_next || !queue_present || !get_ahb_props || !alloc_mem ||
        !free_mem || !get_mem_ahb)
        return __LINE__;

    procedures[0] = (uintptr_t)enum_ver;
    procedures[1] = (uintptr_t)create_surface;
    procedures[2] = (uintptr_t)create_sc;

    uint32_t version = 0;
    if (enum_ver(&version) != VK_SUCCESS || version < VK_API_VERSION_1_0)
        return __LINE__;

    uint32_t ext_count = 0;
    if (enum_ext(0, &ext_count, 0) != VK_SUCCESS || ext_count < 2)
        return __LINE__;

    // Exercise VK_KHR_android_surface + swapchain backed by real ANativeWindow.
    VkAndroidSurfaceCreateInfoKHR surf_info = {0};
    surf_info.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surf_info.window = window;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    if (create_surface(fake_instance, &surf_info, 0, &surface) != VK_SUCCESS ||
        surface == VK_NULL_HANDLE)
        return __LINE__;

    VkSurfaceCapabilitiesKHR caps = {0};
    if (get_caps(0, surface, &caps) != VK_SUCCESS)
        return __LINE__;
    if (caps.currentExtent.width != 128 || caps.currentExtent.height != 64)
        return __LINE__;

    VkSwapchainCreateInfoKHR sc_info = {0};
    sc_info.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    sc_info.surface = surface;
    sc_info.minImageCount = 2;
    sc_info.imageFormat = VK_FORMAT_R8G8B8A8_UNORM;
    sc_info.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    sc_info.imageExtent.width = 128;
    sc_info.imageExtent.height = 64;
    sc_info.imageArrayLayers = 1;
    sc_info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    sc_info.presentMode = VK_PRESENT_MODE_FIFO_KHR;

    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    if (create_sc(fake_device, &sc_info, 0, &swapchain) != VK_SUCCESS ||
        swapchain == VK_NULL_HANDLE) {
        destroy_surface(fake_instance, surface, 0);
        return __LINE__;
    }

    uint32_t img_count = 0;
    if (get_images(fake_device, swapchain, &img_count, 0) != VK_SUCCESS || img_count < 2) {
        destroy_sc(fake_device, swapchain, 0);
        destroy_surface(fake_instance, surface, 0);
        return __LINE__;
    }

    uint32_t acquired = 99;
    if (acquire_next(fake_device, swapchain, ~0ULL, VK_NULL_HANDLE, VK_NULL_HANDLE, &acquired) !=
            VK_SUCCESS ||
        acquired >= img_count) {
        destroy_sc(fake_device, swapchain, 0);
        destroy_surface(fake_instance, surface, 0);
        return __LINE__;
    }

    VkResult present_res = VK_ERROR_UNKNOWN;
    VkPresentInfoKHR present_info = {0};
    present_info.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present_info.swapchainCount = 1;
    present_info.pSwapchains = &swapchain;
    present_info.pImageIndices = &acquired;
    present_info.pResults = &present_res;
    if (queue_present(fake_queue, &present_info) != VK_SUCCESS || present_res != VK_SUCCESS) {
        destroy_sc(fake_device, swapchain, 0);
        destroy_surface(fake_instance, surface, 0);
        return __LINE__;
    }

    destroy_sc(fake_device, swapchain, 0);
    destroy_surface(fake_instance, surface, 0);

    // Exercise VK_ANDROID_external_memory_android_hardware_buffer from guest code.
    VkAndroidHardwareBufferPropertiesANDROID ahb_props = {0};
    ahb_props.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    if (get_ahb_props(fake_device, ahb, &ahb_props) != VK_SUCCESS ||
        ahb_props.allocationSize < 32ULL * 16ULL * 4ULL)
        return __LINE__;

    VkImportAndroidHardwareBufferInfoANDROID import_info = {0};
    import_info.sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    import_info.buffer = ahb;
    VkMemoryAllocateInfo alloc_info = {0};
    alloc_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    alloc_info.pNext = &import_info;
    alloc_info.allocationSize = ahb_props.allocationSize;
    VkDeviceMemory mem = VK_NULL_HANDLE;
    if (alloc_mem(fake_device, &alloc_info, 0, &mem) != VK_SUCCESS || mem == VK_NULL_HANDLE)
        return __LINE__;

    VkMemoryGetAndroidHardwareBufferInfoANDROID get_info = {0};
    get_info.sType = VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    get_info.memory = mem;
    struct AHardwareBuffer* fetched = 0;
    if (get_mem_ahb(fake_device, &get_info, &fetched) != VK_SUCCESS || fetched != ahb) {
        free_mem(fake_device, mem, 0);
        return __LINE__;
    }
    free_mem(fake_device, mem, 0);

    return 77;
}
