// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/vulkan.h"

#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {
bool extension(EGLDisplay display, const char* name) {
    const char* text = eglQueryString(display, EGL_EXTENSIONS);
    return text && std::string(" ").append(text).append(" ").find(std::string(" ") + name + " ") !=
                       std::string::npos;
}
jint contextVersion(EGLDisplay display) {
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr))
        return 0;
    const EGLenum previousApi = eglQueryAPI();
    struct RestoreApi {
        EGLenum value;
        ~RestoreApi() {
            eglBindAPI(value);
        }
    } restore{previousApi};
    if (!eglBindAPI(EGL_OPENGL_ES_API))
        return 0;
    const EGLint configAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
    EGLConfig config = nullptr;
    EGLint count = 0;
    bool chosen = eglChooseConfig(display, configAttribs, &config, 1, &count) && count > 0;
    const EGLint contextAttribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 0,
                                     EGL_NONE};
    EGLContext context =
        chosen ? eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs) : EGL_NO_CONTEXT;
    if (context == EGL_NO_CONTEXT && extension(display, "EGL_KHR_no_config_context"))
        context = eglCreateContext(display, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, contextAttribs);
    bool created = context != EGL_NO_CONTEXT;
    if (created)
        eglDestroyContext(display, context);
    // EGL displays are shared process-wide, and eglInitialize is not reference
    // counted. Terminating this display can destroy an application's live
    // rendering context. Retain the display until process teardown; only the
    // temporary probe context belongs to us.
    return created ? 0x30000 : 0;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL Java_android_app_ActivityManager_nativeGlesVersion(JNIEnv*,
                                                                                     jclass) {
    // Devices/capabilities are stable for this process. Probe once, including
    // when the first query happens after an application has started rendering.
    static const jint result = []() -> jint {
        auto query =
            reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(eglGetProcAddress("eglQueryDevicesEXT"));
        auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(
            eglGetProcAddress("eglGetPlatformDisplay"));
        if (query && platform) {
            EGLDeviceEXT devices[8];
            EGLint count = 0;
            if (query(8, devices, &count)) {
                for (EGLint i = 0; i < count; ++i) {
                    jint version =
                        contextVersion(platform(EGL_PLATFORM_DEVICE_EXT, devices[i], nullptr));
                    if (version != 0)
                        return version;
                }
            }
        }
        if (platform) {
            jint version =
                contextVersion(platform(EGL_PLATFORM_SURFACELESS_MESA, nullptr, nullptr));
            if (version != 0)
                return version;
        }
        return contextVersion(eglGetDisplay(EGL_DEFAULT_DISPLAY));
    }();
    return result;
}

namespace {
struct VulkanCaps {
    jint apiVersion = 0;
    jint hardwareLevel = -1;
};

template <typename Fn> Fn vkLoadSym(void* handle, const char* name) {
    if (!handle || !name)
        return nullptr;
    void* raw = ::dlsym(handle, name);
    Fn fn = nullptr;
    if (raw)
        std::memcpy(&fn, &raw, sizeof(fn));
    return fn;
}

template <typename Fn> Fn vkCastPfn(PFN_vkVoidFunction raw) {
    Fn fn = nullptr;
    if (raw)
        std::memcpy(&fn, &raw, sizeof(fn));
    return fn;
}

VulkanCaps probeHostVulkanCapabilities() {
    VulkanCaps caps{};
    if (const char* disabled = std::getenv("SHIRAZ_VULKAN_DISABLE");
        disabled && std::strcmp(disabled, "1") == 0) {
        return caps;
    }
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
    using GipaFn = PFN_vkVoidFunction (*)(VkInstance, const char*);
#if defined(__APPLE__)
    using EnumInstExtFn = VkResult (*)(const char*, uint32_t*, VkExtensionProperties*);
#endif
    using CreateInstFn =
        VkResult (*)(const VkInstanceCreateInfo*, const VkAllocationCallbacks*, VkInstance*);
    using DestroyInstFn = void (*)(VkInstance, const VkAllocationCallbacks*);
    using EnumPhysFn = VkResult (*)(VkInstance, uint32_t*, VkPhysicalDevice*);
    using GetPropsFn = void (*)(VkPhysicalDevice, VkPhysicalDeviceProperties*);
    using GetFeatsFn = void (*)(VkPhysicalDevice, VkPhysicalDeviceFeatures*);

    for (const auto& path : candidates) {
        void* handle = ::dlopen(path.c_str(), RTLD_LAZY | RTLD_LOCAL);
        if (!handle)
            continue;
        auto gipa = vkLoadSym<GipaFn>(handle, "vkGetInstanceProcAddr");
        auto createInst = gipa ? vkCastPfn<CreateInstFn>(gipa(nullptr, "vkCreateInstance"))
                               : vkLoadSym<CreateInstFn>(handle, "vkCreateInstance");
        if (!createInst) {
            ::dlclose(handle);
            continue;
        }
        VkApplicationInfo appInfo{};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = "shiraz-host-vulkan-probe";
        appInfo.apiVersion = VK_API_VERSION_1_0;

        VkInstanceCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        createInfo.pApplicationInfo = &appInfo;
#if defined(__APPLE__)
        const char* portabilityExt = VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
        auto enumExt =
            gipa ? vkCastPfn<EnumInstExtFn>(gipa(nullptr, "vkEnumerateInstanceExtensionProperties"))
                 : vkLoadSym<EnumInstExtFn>(handle, "vkEnumerateInstanceExtensionProperties");
        if (enumExt) {
            uint32_t extCount = 0;
            if (enumExt(nullptr, &extCount, nullptr) == VK_SUCCESS && extCount > 0) {
                std::vector<VkExtensionProperties> props(extCount);
                if (enumExt(nullptr, &extCount, props.data()) == VK_SUCCESS) {
                    props.resize(extCount);
                    for (const auto& ext : props) {
                        if (std::strcmp(ext.extensionName, portabilityExt) == 0) {
                            createInfo.flags |= VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
                            createInfo.enabledExtensionCount = 1;
                            createInfo.ppEnabledExtensionNames = &portabilityExt;
                            break;
                        }
                    }
                }
            }
        }
#endif
        VkInstance instance = nullptr;
        if (createInst(&createInfo, nullptr, &instance) == VK_SUCCESS && instance) {
            auto enumPhys =
                gipa ? vkCastPfn<EnumPhysFn>(gipa(instance, "vkEnumeratePhysicalDevices"))
                     : vkLoadSym<EnumPhysFn>(handle, "vkEnumeratePhysicalDevices");
            auto getProps =
                gipa ? vkCastPfn<GetPropsFn>(gipa(instance, "vkGetPhysicalDeviceProperties"))
                     : vkLoadSym<GetPropsFn>(handle, "vkGetPhysicalDeviceProperties");
            auto getFeats =
                gipa ? vkCastPfn<GetFeatsFn>(gipa(instance, "vkGetPhysicalDeviceFeatures"))
                     : vkLoadSym<GetFeatsFn>(handle, "vkGetPhysicalDeviceFeatures");
            auto destroyInst = gipa ? vkCastPfn<DestroyInstFn>(gipa(instance, "vkDestroyInstance"))
                                    : vkLoadSym<DestroyInstFn>(handle, "vkDestroyInstance");
            if (enumPhys && getProps) {
                uint32_t count = 0;
                if (enumPhys(instance, &count, nullptr) == VK_SUCCESS && count > 0) {
                    std::vector<VkPhysicalDevice> devices(count);
                    if (enumPhys(instance, &count, devices.data()) == VK_SUCCESS) {
                        devices.resize(count);
                        uint32_t bestApi = 0;
                        int32_t bestLevel = -1;
                        for (VkPhysicalDevice dev : devices) {
                            VkPhysicalDeviceProperties props{};
                            getProps(dev, &props);
                            if (props.apiVersion > bestApi)
                                bestApi = props.apiVersion;
                            int32_t level = 0;
                            if (getFeats) {
                                VkPhysicalDeviceFeatures feats{};
                                getFeats(dev, &feats);
                                const bool hasCompressed = feats.textureCompressionETC2 != 0 ||
                                                           feats.textureCompressionASTC_LDR != 0;
                                if (hasCompressed && feats.fullDrawIndexUint32 &&
                                    feats.imageCubeArray && feats.independentBlend &&
                                    feats.sampleRateShading && feats.samplerAnisotropy &&
                                    feats.fragmentStoresAndAtomics) {
                                    level = 1;
                                }
                            }
                            if (level > bestLevel)
                                bestLevel = level;
                        }
                        if (bestApi > 0 && bestLevel >= 0) {
                            caps.apiVersion = static_cast<jint>(bestApi);
                            caps.hardwareLevel = static_cast<jint>(bestLevel);
                        }
                    }
                }
            }
            if (destroyInst)
                destroyInst(instance, nullptr);
        }
        ::dlclose(handle);
        if (caps.apiVersion > 0)
            break;
    }
    return caps;
}
} // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_android_content_pm_PackageManager_nativeVulkanCapabilities(JNIEnv* env, jclass) {
    static const VulkanCaps caps = probeHostVulkanCapabilities();
    jintArray out = env->NewIntArray(2);
    if (!out)
        return nullptr;
    jint values[2] = {caps.apiVersion, caps.hardwareLevel};
    env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}
