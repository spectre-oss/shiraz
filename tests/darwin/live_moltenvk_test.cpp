// SPDX-License-Identifier: GPL-3.0-only
// Live Apple Silicon MoltenVK (Vulkan-over-Metal) end-to-end verification:
// exercises real VkInstance, VkPhysicalDevice (Apple GPU via Metal), VkDevice,
// queue synchronization, VK_KHR_android_surface + ANativeWindow swapchain
// present, and Darwin guest ELF64 AAPCS64 libvulkan.so thunks against MoltenVK.
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/vulkan.hpp"
#include "shiraz/hardware_buffer.h"
#include "shiraz/native_window.h"
#include "shiraz/ndk/vulkan.h"

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void require(bool cond, const char* msg) {
    if (!cond) {
        throw std::runtime_error(msg);
    }
}

template <typename Fn> Fn load_sym(void* lib, const char* name) {
    void* raw = ::dlsym(lib, name);
    Fn fn = nullptr;
    if (raw) {
        std::memcpy(&fn, &raw, sizeof(fn));
    }
    return fn;
}

} // namespace

int main(int argc, char** argv) {
    try {
        require(argc == 3, "usage: live-moltenvk-test <abs-libvulkan.so> <guest-fixture-dir>");
        const std::filesystem::path vk_path = std::filesystem::canonical(argv[1]);
        const std::filesystem::path guest_dir = std::filesystem::canonical(argv[2]);

        void* vk = ::dlopen(vk_path.c_str(), RTLD_NOW | RTLD_LOCAL);
        require(vk != nullptr, "dlopen(libvulkan.so) failed");

        auto pEnumVersion =
            load_sym<decltype(&::vkEnumerateInstanceVersion)>(vk, "vkEnumerateInstanceVersion");
        auto pEnumInstExt = load_sym<decltype(&::vkEnumerateInstanceExtensionProperties)>(
            vk, "vkEnumerateInstanceExtensionProperties");
        auto pCreateInst = load_sym<decltype(&::vkCreateInstance)>(vk, "vkCreateInstance");
        auto pDestroyInst = load_sym<decltype(&::vkDestroyInstance)>(vk, "vkDestroyInstance");
        auto pEnumPhys =
            load_sym<decltype(&::vkEnumeratePhysicalDevices)>(vk, "vkEnumeratePhysicalDevices");
        auto pGetProps = load_sym<decltype(&::vkGetPhysicalDeviceProperties)>(
            vk, "vkGetPhysicalDeviceProperties");
        auto pGetFeats =
            load_sym<decltype(&::vkGetPhysicalDeviceFeatures)>(vk, "vkGetPhysicalDeviceFeatures");
        auto pGetQueues = load_sym<decltype(&::vkGetPhysicalDeviceQueueFamilyProperties)>(
            vk, "vkGetPhysicalDeviceQueueFamilyProperties");
        auto pCreateDev = load_sym<decltype(&::vkCreateDevice)>(vk, "vkCreateDevice");
        auto pDestroyDev = load_sym<decltype(&::vkDestroyDevice)>(vk, "vkDestroyDevice");
        auto pGetQueue = load_sym<decltype(&::vkGetDeviceQueue)>(vk, "vkGetDeviceQueue");
        auto pQueueWait = load_sym<decltype(&::vkQueueWaitIdle)>(vk, "vkQueueWaitIdle");
        auto pDevWait = load_sym<decltype(&::vkDeviceWaitIdle)>(vk, "vkDeviceWaitIdle");
        auto pCreateSurf =
            load_sym<decltype(&::vkCreateAndroidSurfaceKHR)>(vk, "vkCreateAndroidSurfaceKHR");
        auto pDestroySurf = load_sym<decltype(&::vkDestroySurfaceKHR)>(vk, "vkDestroySurfaceKHR");
        auto pGetSurfCaps = load_sym<decltype(&::vkGetPhysicalDeviceSurfaceCapabilitiesKHR)>(
            vk, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        auto pCreateSwap = load_sym<decltype(&::vkCreateSwapchainKHR)>(vk, "vkCreateSwapchainKHR");
        auto pDestroySwap =
            load_sym<decltype(&::vkDestroySwapchainKHR)>(vk, "vkDestroySwapchainKHR");
        auto pGetSwapImgs =
            load_sym<decltype(&::vkGetSwapchainImagesKHR)>(vk, "vkGetSwapchainImagesKHR");
        auto pAcquire = load_sym<decltype(&::vkAcquireNextImageKHR)>(vk, "vkAcquireNextImageKHR");
        auto pPresent = load_sym<decltype(&::vkQueuePresentKHR)>(vk, "vkQueuePresentKHR");
        require(pEnumVersion && pEnumInstExt && pCreateInst && pDestroyInst && pEnumPhys &&
                    pGetProps && pGetFeats && pGetQueues && pCreateDev && pDestroyDev &&
                    pGetQueue && pQueueWait && pDevWait && pCreateSurf && pDestroySurf &&
                    pGetSurfCaps && pCreateSwap && pDestroySwap && pGetSwapImgs && pAcquire &&
                    pPresent,
                "missing required libvulkan.so exports");

        uint32_t apiVersion = 0;
        require(pEnumVersion(&apiVersion) == VK_SUCCESS && apiVersion >= VK_API_VERSION_1_1,
                "MoltenVK instance version must be >= 1.1");

        uint32_t extCount = 0;
        require(pEnumInstExt(nullptr, &extCount, nullptr) == VK_SUCCESS && extCount > 0,
                "instance extensions unavailable");
        std::vector<VkExtensionProperties> exts(extCount);
        require(pEnumInstExt(nullptr, &extCount, exts.data()) == VK_SUCCESS,
                "enumerate instance extensions failed");
        bool hasAndroidSurface = false;
        for (const auto& e : exts) {
            if (std::strcmp(e.extensionName, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME) == 0) {
                hasAndroidSurface = true;
            }
        }
        require(hasAndroidSurface, "VK_KHR_android_surface must be advertised");

        const char* enabledExts[] = {
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
        };
        VkApplicationInfo appInfo{};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = "shiraz-live-moltenvk-test";
        appInfo.apiVersion = VK_API_VERSION_1_1;
        VkInstanceCreateInfo instInfo{};
        instInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        instInfo.pApplicationInfo = &appInfo;
        instInfo.enabledExtensionCount = 2;
        instInfo.ppEnabledExtensionNames = enabledExts;

        VkInstance instance = nullptr;
        require(pCreateInst(&instInfo, nullptr, &instance) == VK_SUCCESS && instance != nullptr,
                "vkCreateInstance against live MoltenVK failed");

        uint32_t physCount = 0;
        require(pEnumPhys(instance, &physCount, nullptr) == VK_SUCCESS && physCount > 0,
                "MoltenVK reported zero physical devices");
        std::vector<VkPhysicalDevice> devices(physCount);
        require(pEnumPhys(instance, &physCount, devices.data()) == VK_SUCCESS,
                "vkEnumeratePhysicalDevices failed");

        VkPhysicalDevice gpu = devices[0];
        VkPhysicalDeviceProperties props{};
        pGetProps(gpu, &props);
        VkPhysicalDeviceFeatures feats{};
        pGetFeats(gpu, &feats);
        require(props.deviceName[0] != '\0', "physical device name empty");
        require(props.apiVersion >= VK_API_VERSION_1_1, "physical device apiVersion < 1.1");

        uint32_t qCount = 0;
        pGetQueues(gpu, &qCount, nullptr);
        require(qCount > 0, "physical device reported zero queue families");
        std::vector<VkQueueFamilyProperties> qProps(qCount);
        pGetQueues(gpu, &qCount, qProps.data());
        constexpr uint32_t kVkQueueGraphicsBit = 0x00000001u;
        uint32_t graphicsFamily = UINT32_MAX;
        for (uint32_t i = 0; i < qCount; ++i) {
            if ((qProps[i].queueFlags & kVkQueueGraphicsBit) != 0) {
                graphicsFamily = i;
                break;
            }
        }
        require(graphicsFamily != UINT32_MAX, "no graphics queue family");

        float prio = 1.0f;
        VkDeviceQueueCreateInfo qInfo{};
        qInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        qInfo.queueFamilyIndex = graphicsFamily;
        qInfo.queueCount = 1;
        qInfo.pQueuePriorities = &prio;

        VkDeviceCreateInfo devInfo{};
        devInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        devInfo.queueCreateInfoCount = 1;
        devInfo.pQueueCreateInfos = &qInfo;

        VkDevice device = nullptr;
        require(pCreateDev(gpu, &devInfo, nullptr, &device) == VK_SUCCESS && device != nullptr,
                "vkCreateDevice on Apple Silicon Metal failed");

        VkQueue queue = nullptr;
        pGetQueue(device, graphicsFamily, 0, &queue);
        require(queue != nullptr, "vkGetDeviceQueue returned null");
        require(pQueueWait(queue) == VK_SUCCESS, "vkQueueWaitIdle failed");
        require(pDevWait(device) == VK_SUCCESS, "vkDeviceWaitIdle failed");

        // Exercise VK_KHR_android_surface + ANativeWindow swapchain present with real MoltenVK
        // VkDevice.
        ANativeWindow* win = shiraz_window_create(160, 120);
        require(win != nullptr, "create ANativeWindow failed");
        VkAndroidSurfaceCreateInfoKHR surfInfo{};
        surfInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        surfInfo.window = win;
        VkSurfaceKHR surface = VK_NULL_HANDLE;
        require(pCreateSurf(instance, &surfInfo, nullptr, &surface) == VK_SUCCESS &&
                    surface != VK_NULL_HANDLE,
                "vkCreateAndroidSurfaceKHR failed");
        VkSurfaceCapabilitiesKHR caps{};
        require(pGetSurfCaps(gpu, surface, &caps) == VK_SUCCESS &&
                    caps.currentExtent.width == 160 && caps.currentExtent.height == 120,
                "surface capabilities extent mismatch");

        VkSwapchainCreateInfoKHR swapInfo{};
        swapInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        swapInfo.surface = surface;
        swapInfo.minImageCount = 2;
        swapInfo.imageFormat = VK_FORMAT_R8G8B8A8_UNORM;
        swapInfo.imageExtent = caps.currentExtent;
        swapInfo.imageArrayLayers = 1;
        VkSwapchainKHR swapchain = VK_NULL_HANDLE;
        require(pCreateSwap(device, &swapInfo, nullptr, &swapchain) == VK_SUCCESS &&
                    swapchain != VK_NULL_HANDLE,
                "vkCreateSwapchainKHR failed");

        uint32_t imgCount = 0;
        require(pGetSwapImgs(device, swapchain, &imgCount, nullptr) == VK_SUCCESS && imgCount == 2,
                "swapchain image count mismatch");
        std::vector<VkImage> imgs(imgCount);
        require(pGetSwapImgs(device, swapchain, &imgCount, imgs.data()) == VK_SUCCESS,
                "vkGetSwapchainImagesKHR failed");

        uint32_t imgIndex = UINT32_MAX;
        require(pAcquire(device, swapchain, UINT64_MAX, VK_NULL_HANDLE, VK_NULL_HANDLE,
                         &imgIndex) == VK_SUCCESS &&
                    imgIndex < imgCount,
                "vkAcquireNextImageKHR failed");
        const uint64_t revBefore = shiraz_window_revision(win);
        VkPresentInfoKHR presentInfo{};
        presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &swapchain;
        presentInfo.pImageIndices = &imgIndex;
        require(pPresent(queue, &presentInfo) == VK_SUCCESS, "vkQueuePresentKHR failed");
        require(shiraz_window_revision(win) > revBefore, "present did not advance window revision");

        pDestroySwap(device, swapchain, nullptr);
        pDestroySurf(instance, surface, nullptr);
        ANativeWindow_release(win);
        pDestroyDev(device, nullptr);
        pDestroyInst(instance, nullptr);
        ::dlclose(vk);

        // Second phase: verify Darwin guest ELF64 AAPCS64 libvulkan.so thunks with live MoltenVK
        // active!
        using namespace shiraz::guest;
        Providers providers;
        auto core = core_personality();
        for (const auto& entry : core.modules()) {
            providers.add(entry.second);
        }
        providers.add(guest_vulkan_provider(vk_path));
        Loader loader({guest_dir}, std::move(providers));
        auto* mod = loader.open("libguestvulkan.so");

        ANativeWindow* gwin = shiraz_window_create(128, 64);
        AHardwareBuffer_Desc ahb_desc{};
        ahb_desc.width = 32;
        ahb_desc.height = 16;
        ahb_desc.layers = 1;
        ahb_desc.format = 1;
        ahb_desc.usage = 0x33ULL;
        AHardwareBuffer* ahb = nullptr;
        require(AHardwareBuffer_allocate(&ahb_desc, &ahb) == 0 && ahb != nullptr,
                "AHardwareBuffer_allocate failed");
        uintptr_t procs[3]{};
        const uint64_t gRevBefore = shiraz_window_revision(gwin);
        auto res = invoke(
            reinterpret_cast<void*>(loader.symbol(mod, "run_guestvulkan").address),
            {Argument::integer(int64_t(reinterpret_cast<uintptr_t>(gwin)), ArgumentType::Int64),
             Argument::integer(int64_t(reinterpret_cast<uintptr_t>(ahb)), ArgumentType::Int64),
             Argument::integer(int64_t(reinterpret_cast<uintptr_t>(procs)), ArgumentType::Int64)});
        require(int(res.result_integers[0]) == 77, "guest Vulkan ELF failed under live MoltenVK");
        require(shiraz_window_revision(gwin) > gRevBefore, "guest Vulkan did not present");
        AHardwareBuffer_release(ahb);
        AHardwareBuffer_release(ahb);
        ANativeWindow_release(gwin);

        const uint32_t major = (props.apiVersion >> 22) & 0x7fu;
        const uint32_t minor = (props.apiVersion >> 12) & 0x3ffu;
        const uint32_t patch = props.apiVersion & 0xfffu;
        std::printf("DARWIN_LIVE_MOLTENVK_PASS gpu=\"%s\" api=%u.%u.%u vendor=0x%x "
                    "device_present=1 guest_elf_thunks=1\n",
                    props.deviceName, major, minor, patch, props.vendorID);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "live-moltenvk-test: " << e.what() << '\n';
        return 1;
    }
}
