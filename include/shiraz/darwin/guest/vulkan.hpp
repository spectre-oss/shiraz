// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include <filesystem>

namespace shiraz::guest {
// Load only an explicitly configured absolute Darwin libvulkan.so shim.
// All typed Vulkan 1.0-1.3 loader, VK_KHR_android_surface, VK_KHR_swapchain,
// and VK_ANDROID_external_memory_android_hardware_buffer entry points become
// guest AAPCS64 thunks. vkGetInstanceProcAddr / vkGetDeviceProcAddr return
// only guest thunk addresses, never raw host function pointers.
Provider guest_vulkan_provider(const std::filesystem::path& library_path);
} // namespace shiraz::guest
