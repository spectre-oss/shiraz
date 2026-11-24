// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct ANativeWindow;
struct AHardwareBuffer;

typedef uint32_t VkFlags;
typedef uint32_t VkBool32;
typedef uint64_t VkDeviceSize;
typedef uint32_t VkSampleMask;

#define VK_TRUE 1U
#define VK_FALSE 0U
#define VK_NULL_HANDLE 0

#define VK_MAX_PHYSICAL_DEVICE_NAME_SIZE 256U
#define VK_UUID_SIZE 16U
#define VK_MAX_EXTENSION_NAME_SIZE 256U
#define VK_MAX_DESCRIPTION_SIZE 256U
#define VK_MAX_MEMORY_TYPES 32U
#define VK_MAX_MEMORY_HEAPS 16U

#define VK_MAKE_API_VERSION(variant, major, minor, patch)                                          \
    ((((uint32_t)(variant)) << 29U) | (((uint32_t)(major)) << 22U) |                               \
     (((uint32_t)(minor)) << 12U) | ((uint32_t)(patch)))
#define VK_API_VERSION_1_0 VK_MAKE_API_VERSION(0, 1, 0, 0)
#define VK_API_VERSION_1_1 VK_MAKE_API_VERSION(0, 1, 1, 0)
#define VK_API_VERSION_1_2 VK_MAKE_API_VERSION(0, 1, 2, 0)
#define VK_API_VERSION_1_3 VK_MAKE_API_VERSION(0, 1, 3, 0)

#define VK_KHR_SURFACE_EXTENSION_NAME "VK_KHR_surface"
#define VK_KHR_SURFACE_SPEC_VERSION 25U
#define VK_KHR_SWAPCHAIN_EXTENSION_NAME "VK_KHR_swapchain"
#define VK_KHR_SWAPCHAIN_SPEC_VERSION 70U
#define VK_KHR_ANDROID_SURFACE_EXTENSION_NAME "VK_KHR_android_surface"
#define VK_KHR_ANDROID_SURFACE_SPEC_VERSION 6U
#define VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME "VK_KHR_portability_enumeration"
#define VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR 0x00000001U
#define VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME                          \
    "VK_ANDROID_external_memory_android_hardware_buffer"
#define VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION 5U
#define VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID 0x00000400U

typedef struct VkInstance_T* VkInstance;
typedef struct VkPhysicalDevice_T* VkPhysicalDevice;
typedef struct VkDevice_T* VkDevice;
typedef struct VkQueue_T* VkQueue;
typedef struct VkCommandBuffer_T* VkCommandBuffer;

typedef uint64_t VkSurfaceKHR;
typedef uint64_t VkSwapchainKHR;
typedef uint64_t VkImage;
typedef uint64_t VkDeviceMemory;
typedef uint64_t VkSemaphore;
typedef uint64_t VkFence;

typedef void (*PFN_vkVoidFunction)(void);

typedef enum VkResult {
    VK_SUCCESS = 0,
    VK_NOT_READY = 1,
    VK_TIMEOUT = 2,
    VK_EVENT_SET = 3,
    VK_EVENT_RESET = 4,
    VK_INCOMPLETE = 5,
    VK_ERROR_OUT_OF_HOST_MEMORY = -1,
    VK_ERROR_OUT_OF_DEVICE_MEMORY = -2,
    VK_ERROR_INITIALIZATION_FAILED = -3,
    VK_ERROR_DEVICE_LOST = -4,
    VK_ERROR_MEMORY_MAP_FAILED = -5,
    VK_ERROR_LAYER_NOT_PRESENT = -6,
    VK_ERROR_EXTENSION_NOT_PRESENT = -7,
    VK_ERROR_FEATURE_NOT_PRESENT = -8,
    VK_ERROR_INCOMPATIBLE_DRIVER = -9,
    VK_ERROR_TOO_MANY_OBJECTS = -10,
    VK_ERROR_FORMAT_NOT_SUPPORTED = -11,
    VK_ERROR_FRAGMENTED_POOL = -12,
    VK_ERROR_UNKNOWN = -13,
    VK_ERROR_SURFACE_LOST_KHR = -1000000000,
    VK_ERROR_NATIVE_WINDOW_IN_USE_KHR = -1000000001,
    VK_SUBOPTIMAL_KHR = 1000001003,
    VK_ERROR_OUT_OF_DATE_KHR = -1000001004,
    VK_ERROR_INVALID_EXTERNAL_HANDLE = -1000072003,
} VkResult;

typedef enum VkStructureType {
    VK_STRUCTURE_TYPE_APPLICATION_INFO = 0,
    VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1,
    VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO = 2,
    VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO = 3,
    VK_STRUCTURE_TYPE_SUBMIT_INFO = 4,
    VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO = 5,
    VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR = 1000001000,
    VK_STRUCTURE_TYPE_PRESENT_INFO_KHR = 1000001001,
    VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR = 1000008000,
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO = 1000072002,
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID = 1000129001,
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID = 1000129003,
    VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID = 1000129004,
    VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID = 1000129005,
} VkStructureType;

typedef enum VkPhysicalDeviceType {
    VK_PHYSICAL_DEVICE_TYPE_OTHER = 0,
    VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU = 1,
    VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU = 2,
    VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU = 3,
    VK_PHYSICAL_DEVICE_TYPE_CPU = 4,
} VkPhysicalDeviceType;

typedef enum VkFormat {
    VK_FORMAT_UNDEFINED = 0,
    VK_FORMAT_R5G6B5_UNORM_PACK16 = 4,
    VK_FORMAT_R8G8B8_UNORM = 23,
    VK_FORMAT_R8G8B8A8_UNORM = 37,
    VK_FORMAT_R8G8B8A8_SRGB = 43,
    VK_FORMAT_B8G8R8A8_UNORM = 44,
    VK_FORMAT_B8G8R8A8_SRGB = 50,
    VK_FORMAT_A2B10G10R10_UNORM_PACK32 = 64,
    VK_FORMAT_R16G16B16A16_SFLOAT = 97,
} VkFormat;

typedef enum VkColorSpaceKHR {
    VK_COLOR_SPACE_SRGB_NONLINEAR_KHR = 0,
} VkColorSpaceKHR;

typedef enum VkPresentModeKHR {
    VK_PRESENT_MODE_IMMEDIATE_KHR = 0,
    VK_PRESENT_MODE_MAILBOX_KHR = 1,
    VK_PRESENT_MODE_FIFO_KHR = 2,
    VK_PRESENT_MODE_FIFO_RELAXED_KHR = 3,
} VkPresentModeKHR;

typedef enum VkSharingMode {
    VK_SHARING_MODE_EXCLUSIVE = 0,
    VK_SHARING_MODE_CONCURRENT = 1,
} VkSharingMode;

typedef enum VkSurfaceTransformFlagBitsKHR {
    VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR = 0x00000001,
} VkSurfaceTransformFlagBitsKHR;

typedef enum VkCompositeAlphaFlagBitsKHR {
    VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR = 0x00000001,
    VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR = 0x00000002,
    VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR = 0x00000008,
} VkCompositeAlphaFlagBitsKHR;

typedef enum VkImageUsageFlagBits {
    VK_IMAGE_USAGE_TRANSFER_SRC_BIT = 0x00000001,
    VK_IMAGE_USAGE_TRANSFER_DST_BIT = 0x00000002,
    VK_IMAGE_USAGE_SAMPLED_BIT = 0x00000004,
    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT = 0x00000010,
} VkImageUsageFlagBits;

typedef VkFlags VkInstanceCreateFlags;
typedef VkFlags VkDeviceCreateFlags;
typedef VkFlags VkDeviceQueueCreateFlags;
typedef VkFlags VkQueueFlags;
typedef VkFlags VkMemoryPropertyFlags;
typedef VkFlags VkMemoryHeapFlags;
typedef VkFlags VkPipelineStageFlags;
typedef VkFlags VkAndroidSurfaceCreateFlagsKHR;
typedef VkFlags VkSurfaceTransformFlagsKHR;
typedef VkFlags VkCompositeAlphaFlagsKHR;
typedef VkFlags VkImageUsageFlags;
typedef VkFlags VkSwapchainCreateFlagsKHR;
typedef VkFlags VkFormatFeatureFlags;

typedef struct VkAllocationCallbacks VkAllocationCallbacks;

typedef struct VkBaseInStructure {
    VkStructureType sType;
    const struct VkBaseInStructure* pNext;
} VkBaseInStructure;

typedef struct VkBaseOutStructure {
    VkStructureType sType;
    struct VkBaseOutStructure* pNext;
} VkBaseOutStructure;

typedef struct VkExtent2D {
    uint32_t width;
    uint32_t height;
} VkExtent2D;

typedef struct VkExtent3D {
    uint32_t width;
    uint32_t height;
    uint32_t depth;
} VkExtent3D;

typedef struct VkExtensionProperties {
    char extensionName[VK_MAX_EXTENSION_NAME_SIZE];
    uint32_t specVersion;
} VkExtensionProperties;

typedef struct VkLayerProperties {
    char layerName[VK_MAX_EXTENSION_NAME_SIZE];
    uint32_t specVersion;
    uint32_t implementationVersion;
    char description[VK_MAX_DESCRIPTION_SIZE];
} VkLayerProperties;

typedef struct VkApplicationInfo {
    VkStructureType sType;
    const void* pNext;
    const char* pApplicationName;
    uint32_t applicationVersion;
    const char* pEngineName;
    uint32_t engineVersion;
    uint32_t apiVersion;
} VkApplicationInfo;

typedef struct VkInstanceCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkInstanceCreateFlags flags;
    const VkApplicationInfo* pApplicationInfo;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
} VkInstanceCreateInfo;

typedef struct VkPhysicalDeviceLimits {
    uint32_t maxImageDimension1D;
    uint32_t maxImageDimension2D;
    uint32_t maxImageDimension3D;
    uint32_t maxImageDimensionCube;
    uint32_t maxImageArrayLayers;
    uint32_t maxTexelBufferElements;
    uint32_t maxUniformBufferRange;
    uint32_t maxStorageBufferRange;
    uint32_t maxPushConstantsSize;
    uint32_t maxMemoryAllocationCount;
    uint32_t maxSamplerAllocationCount;
    VkDeviceSize bufferImageGranularity;
    VkDeviceSize sparseAddressSpaceSize;
    uint32_t maxBoundDescriptorSets;
    uint32_t maxPerStageDescriptorSamplers;
    uint32_t maxPerStageDescriptorUniformBuffers;
    uint32_t maxPerStageDescriptorStorageBuffers;
    uint32_t maxPerStageDescriptorSampledImages;
    uint32_t maxPerStageDescriptorStorageImages;
    uint32_t maxPerStageDescriptorInputAttachments;
    uint32_t maxPerStageResources;
    uint32_t maxDescriptorSetSamplers;
    uint32_t maxDescriptorSetUniformBuffers;
    uint32_t maxDescriptorSetUniformBuffersDynamic;
    uint32_t maxDescriptorSetStorageBuffers;
    uint32_t maxDescriptorSetStorageBuffersDynamic;
    uint32_t maxDescriptorSetSampledImages;
    uint32_t maxDescriptorSetStorageImages;
    uint32_t maxDescriptorSetInputAttachments;
    uint32_t maxVertexInputAttributes;
    uint32_t maxVertexInputBindings;
    uint32_t maxVertexInputAttributeOffset;
    uint32_t maxVertexInputBindingStride;
    uint32_t maxVertexOutputComponents;
    uint32_t maxTessellationGenerationLevel;
    uint32_t maxTessellationPatchSize;
    uint32_t maxTessellationControlPerVertexInputComponents;
    uint32_t maxTessellationControlPerVertexOutputComponents;
    uint32_t maxTessellationControlPerPatchOutputComponents;
    uint32_t maxTessellationControlTotalOutputComponents;
    uint32_t maxTessellationEvaluationInputComponents;
    uint32_t maxTessellationEvaluationOutputComponents;
    uint32_t maxGeometryShaderInvocations;
    uint32_t maxGeometryInputComponents;
    uint32_t maxGeometryOutputComponents;
    uint32_t maxGeometryOutputVertices;
    uint32_t maxGeometryTotalOutputComponents;
    uint32_t maxFragmentInputComponents;
    uint32_t maxFragmentOutputAttachments;
    uint32_t maxFragmentDualSrcAttachments;
    uint32_t maxFragmentCombinedOutputResources;
    uint32_t maxComputeSharedMemorySize;
    uint32_t maxComputeWorkGroupCount[3];
    uint32_t maxComputeWorkGroupInvocations;
    uint32_t maxComputeWorkGroupSize[3];
    uint32_t subPixelPrecisionBits;
    uint32_t subTexelPrecisionBits;
    uint32_t mipmapPrecisionBits;
    uint32_t maxDrawIndexedIndexValue;
    uint32_t maxDrawIndirectCount;
    float maxSamplerLodBias;
    float maxSamplerAnisotropy;
    uint32_t maxViewports;
    uint32_t maxViewportDimensions[2];
    float viewportBoundsRange[2];
    uint32_t viewportSubPixelBits;
    size_t minMemoryMapAlignment;
    VkDeviceSize minTexelBufferOffsetAlignment;
    VkDeviceSize minUniformBufferOffsetAlignment;
    VkDeviceSize minStorageBufferOffsetAlignment;
    int32_t minTexelOffset;
    uint32_t maxTexelOffset;
    int32_t minTexelGatherOffset;
    uint32_t maxTexelGatherOffset;
    float minInterpolationOffset;
    float maxInterpolationOffset;
    uint32_t subPixelInterpolationOffsetBits;
    uint32_t maxFramebufferWidth;
    uint32_t maxFramebufferHeight;
    uint32_t maxFramebufferLayers;
    VkFlags framebufferColorSampleCounts;
    VkFlags framebufferDepthSampleCounts;
    VkFlags framebufferStencilSampleCounts;
    VkFlags framebufferNoAttachmentsSampleCounts;
    uint32_t maxColorAttachments;
    VkFlags sampledImageColorSampleCounts;
    VkFlags sampledImageIntegerSampleCounts;
    VkFlags sampledImageDepthSampleCounts;
    VkFlags sampledImageStencilSampleCounts;
    VkFlags storageImageSampleCounts;
    uint32_t maxSampleMaskWords;
    VkBool32 timestampComputeAndGraphics;
    float timestampPeriod;
    uint32_t maxClipDistances;
    uint32_t maxCullDistances;
    uint32_t maxCombinedClipAndCullDistances;
    uint32_t discreteQueuePriorities;
    float pointSizeRange[2];
    float lineWidthRange[2];
    float pointSizeGranularity;
    float lineWidthGranularity;
    VkBool32 strictLines;
    VkBool32 standardSampleLocations;
    VkDeviceSize optimalBufferCopyOffsetAlignment;
    VkDeviceSize optimalBufferCopyRowPitchAlignment;
    VkDeviceSize nonCoherentAtomSize;
} VkPhysicalDeviceLimits;

typedef struct VkPhysicalDeviceSparseProperties {
    VkBool32 residencyStandard2DBlockShape;
    VkBool32 residencyStandard2DMultisampleBlockShape;
    VkBool32 residencyStandard3DBlockShape;
    VkBool32 residencyAlignedMipSize;
    VkBool32 residencyNonResidentStrict;
} VkPhysicalDeviceSparseProperties;

typedef struct VkPhysicalDeviceProperties {
    uint32_t apiVersion;
    uint32_t driverVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    VkPhysicalDeviceType deviceType;
    char deviceName[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE];
    uint8_t pipelineCacheUUID[VK_UUID_SIZE];
    VkPhysicalDeviceLimits limits;
    VkPhysicalDeviceSparseProperties sparseProperties;
} VkPhysicalDeviceProperties;

typedef struct VkPhysicalDeviceFeatures {
    VkBool32 robustBufferAccess;
    VkBool32 fullDrawIndexUint32;
    VkBool32 imageCubeArray;
    VkBool32 independentBlend;
    VkBool32 geometryShader;
    VkBool32 tessellationShader;
    VkBool32 sampleRateShading;
    VkBool32 dualSrcBlend;
    VkBool32 logicOp;
    VkBool32 multiDrawIndirect;
    VkBool32 drawIndirectFirstInstance;
    VkBool32 depthClamp;
    VkBool32 depthBiasClamp;
    VkBool32 fillModeNonSolid;
    VkBool32 depthBounds;
    VkBool32 wideLines;
    VkBool32 largePoints;
    VkBool32 alphaToOne;
    VkBool32 multiViewport;
    VkBool32 samplerAnisotropy;
    VkBool32 textureCompressionETC2;
    VkBool32 textureCompressionASTC_LDR;
    VkBool32 textureCompressionBC;
    VkBool32 occlusionQueryPrecise;
    VkBool32 pipelineStatisticsQuery;
    VkBool32 vertexPipelineStoresAndAtomics;
    VkBool32 fragmentStoresAndAtomics;
    VkBool32 shaderTessellationAndGeometryPointSize;
    VkBool32 shaderImageGatherExtended;
    VkBool32 shaderStorageImageExtendedFormats;
    VkBool32 shaderStorageImageMultisample;
    VkBool32 shaderStorageImageReadWithoutFormat;
    VkBool32 shaderStorageImageWriteWithoutFormat;
    VkBool32 shaderUniformBufferArrayDynamicIndexing;
    VkBool32 shaderSampledImageArrayDynamicIndexing;
    VkBool32 shaderStorageBufferArrayDynamicIndexing;
    VkBool32 shaderStorageImageArrayDynamicIndexing;
    VkBool32 shaderClipDistance;
    VkBool32 shaderCullDistance;
    VkBool32 shaderFloat64;
    VkBool32 shaderInt64;
    VkBool32 shaderInt16;
    VkBool32 shaderResourceResidency;
    VkBool32 shaderResourceMinLod;
    VkBool32 sparseBinding;
    VkBool32 sparseResidencyBuffer;
    VkBool32 sparseResidencyImage2D;
    VkBool32 sparseResidencyImage3D;
    VkBool32 sparseResidency2Samples;
    VkBool32 sparseResidency4Samples;
    VkBool32 sparseResidency8Samples;
    VkBool32 sparseResidency16Samples;
    VkBool32 sparseResidencyAliased;
    VkBool32 variableMultisampleRate;
    VkBool32 inheritedQueries;
} VkPhysicalDeviceFeatures;

typedef struct VkQueueFamilyProperties {
    VkQueueFlags queueFlags;
    uint32_t queueCount;
    uint32_t timestampValidBits;
    VkExtent3D minImageTransferGranularity;
} VkQueueFamilyProperties;

typedef struct VkMemoryType {
    VkMemoryPropertyFlags propertyFlags;
    uint32_t heapIndex;
} VkMemoryType;

typedef struct VkMemoryHeap {
    VkDeviceSize size;
    VkMemoryHeapFlags flags;
} VkMemoryHeap;

typedef struct VkPhysicalDeviceMemoryProperties {
    uint32_t memoryTypeCount;
    VkMemoryType memoryTypes[VK_MAX_MEMORY_TYPES];
    uint32_t memoryHeapCount;
    VkMemoryHeap memoryHeaps[VK_MAX_MEMORY_HEAPS];
} VkPhysicalDeviceMemoryProperties;

typedef struct VkDeviceQueueCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkDeviceQueueCreateFlags flags;
    uint32_t queueFamilyIndex;
    uint32_t queueCount;
    const float* pQueuePriorities;
} VkDeviceQueueCreateInfo;

typedef struct VkDeviceCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkDeviceCreateFlags flags;
    uint32_t queueCreateInfoCount;
    const VkDeviceQueueCreateInfo* pQueueCreateInfos;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
    const VkPhysicalDeviceFeatures* pEnabledFeatures;
} VkDeviceCreateInfo;

typedef struct VkSubmitInfo {
    VkStructureType sType;
    const void* pNext;
    uint32_t waitSemaphoreCount;
    const VkSemaphore* pWaitSemaphores;
    const VkPipelineStageFlags* pWaitDstStageMask;
    uint32_t commandBufferCount;
    const VkCommandBuffer* pCommandBuffers;
    uint32_t signalSemaphoreCount;
    const VkSemaphore* pSignalSemaphores;
} VkSubmitInfo;

typedef struct VkAndroidSurfaceCreateInfoKHR {
    VkStructureType sType;
    const void* pNext;
    VkAndroidSurfaceCreateFlagsKHR flags;
    struct ANativeWindow* window;
} VkAndroidSurfaceCreateInfoKHR;

typedef struct VkSurfaceCapabilitiesKHR {
    uint32_t minImageCount;
    uint32_t maxImageCount;
    VkExtent2D currentExtent;
    VkExtent2D minImageExtent;
    VkExtent2D maxImageExtent;
    uint32_t maxImageArrayLayers;
    VkSurfaceTransformFlagsKHR supportedTransforms;
    VkSurfaceTransformFlagBitsKHR currentTransform;
    VkCompositeAlphaFlagsKHR supportedCompositeAlpha;
    VkImageUsageFlags supportedUsageFlags;
} VkSurfaceCapabilitiesKHR;

typedef struct VkSurfaceFormatKHR {
    VkFormat format;
    VkColorSpaceKHR colorSpace;
} VkSurfaceFormatKHR;

typedef struct VkSwapchainCreateInfoKHR {
    VkStructureType sType;
    const void* pNext;
    VkSwapchainCreateFlagsKHR flags;
    VkSurfaceKHR surface;
    uint32_t minImageCount;
    VkFormat imageFormat;
    VkColorSpaceKHR imageColorSpace;
    VkExtent2D imageExtent;
    uint32_t imageArrayLayers;
    VkImageUsageFlags imageUsage;
    VkSharingMode imageSharingMode;
    uint32_t queueFamilyIndexCount;
    const uint32_t* pQueueFamilyIndices;
    VkSurfaceTransformFlagBitsKHR preTransform;
    VkCompositeAlphaFlagBitsKHR compositeAlpha;
    VkPresentModeKHR presentMode;
    VkBool32 clipped;
    VkSwapchainKHR oldSwapchain;
} VkSwapchainCreateInfoKHR;

typedef struct VkPresentInfoKHR {
    VkStructureType sType;
    const void* pNext;
    uint32_t waitSemaphoreCount;
    const VkSemaphore* pWaitSemaphores;
    uint32_t swapchainCount;
    const VkSwapchainKHR* pSwapchains;
    const uint32_t* pImageIndices;
    VkResult* pResults;
} VkPresentInfoKHR;

typedef struct VkComponentMapping {
    uint32_t r;
    uint32_t g;
    uint32_t b;
    uint32_t a;
} VkComponentMapping;

typedef struct VkAndroidHardwareBufferPropertiesANDROID {
    VkStructureType sType;
    void* pNext;
    VkDeviceSize allocationSize;
    uint32_t memoryTypeBits;
} VkAndroidHardwareBufferPropertiesANDROID;

typedef struct VkAndroidHardwareBufferFormatPropertiesANDROID {
    VkStructureType sType;
    void* pNext;
    VkFormat format;
    uint64_t externalFormat;
    VkFormatFeatureFlags formatFeatures;
    VkComponentMapping samplerYcbcrConversionComponents;
    uint32_t suggestedYcbcrModel;
    uint32_t suggestedYcbcrRange;
    uint32_t suggestedXChromaOffset;
    uint32_t suggestedYChromaOffset;
} VkAndroidHardwareBufferFormatPropertiesANDROID;

typedef struct VkMemoryAllocateInfo {
    VkStructureType sType;
    const void* pNext;
    VkDeviceSize allocationSize;
    uint32_t memoryTypeIndex;
} VkMemoryAllocateInfo;

typedef struct VkExportMemoryAllocateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags handleTypes;
} VkExportMemoryAllocateInfo;

typedef struct VkImportAndroidHardwareBufferInfoANDROID {
    VkStructureType sType;
    const void* pNext;
    struct AHardwareBuffer* buffer;
} VkImportAndroidHardwareBufferInfoANDROID;

typedef struct VkMemoryGetAndroidHardwareBufferInfoANDROID {
    VkStructureType sType;
    const void* pNext;
    VkDeviceMemory memory;
} VkMemoryGetAndroidHardwareBufferInfoANDROID;

// --- Exported Vulkan 1.0-1.3 loader & Android extension entry points ---

PFN_vkVoidFunction vkGetInstanceProcAddr(VkInstance instance, const char* pName);
PFN_vkVoidFunction vkGetDeviceProcAddr(VkDevice device, const char* pName);

VkResult vkEnumerateInstanceVersion(uint32_t* pApiVersion);
VkResult vkEnumerateInstanceExtensionProperties(const char* pLayerName, uint32_t* pPropertyCount,
                                                VkExtensionProperties* pProperties);
VkResult vkEnumerateInstanceLayerProperties(uint32_t* pPropertyCount,
                                            VkLayerProperties* pProperties);

VkResult vkCreateInstance(const VkInstanceCreateInfo* pCreateInfo,
                          const VkAllocationCallbacks* pAllocator, VkInstance* pInstance);
void vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks* pAllocator);

VkResult vkEnumeratePhysicalDevices(VkInstance instance, uint32_t* pPhysicalDeviceCount,
                                    VkPhysicalDevice* pPhysicalDevices);
void vkGetPhysicalDeviceProperties(VkPhysicalDevice physicalDevice,
                                   VkPhysicalDeviceProperties* pProperties);
void vkGetPhysicalDeviceFeatures(VkPhysicalDevice physicalDevice,
                                 VkPhysicalDeviceFeatures* pFeatures);
void vkGetPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice physicalDevice,
                                              uint32_t* pQueueFamilyPropertyCount,
                                              VkQueueFamilyProperties* pQueueFamilyProperties);
void vkGetPhysicalDeviceMemoryProperties(VkPhysicalDevice physicalDevice,
                                         VkPhysicalDeviceMemoryProperties* pMemoryProperties);
VkResult vkEnumerateDeviceExtensionProperties(VkPhysicalDevice physicalDevice,
                                              const char* pLayerName, uint32_t* pPropertyCount,
                                              VkExtensionProperties* pProperties);

VkResult vkCreateDevice(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo,
                        const VkAllocationCallbacks* pAllocator, VkDevice* pDevice);
void vkDestroyDevice(VkDevice device, const VkAllocationCallbacks* pAllocator);
void vkGetDeviceQueue(VkDevice device, uint32_t queueFamilyIndex, uint32_t queueIndex,
                      VkQueue* pQueue);
VkResult vkDeviceWaitIdle(VkDevice device);
VkResult vkQueueSubmit(VkQueue queue, uint32_t submitCount, const VkSubmitInfo* pSubmits,
                       VkFence fence);
VkResult vkQueueWaitIdle(VkQueue queue);
VkResult vkAllocateMemory(VkDevice device, const VkMemoryAllocateInfo* pAllocateInfo,
                          const VkAllocationCallbacks* pAllocator, VkDeviceMemory* pMemory);
void vkFreeMemory(VkDevice device, VkDeviceMemory memory, const VkAllocationCallbacks* pAllocator);

VkResult vkCreateAndroidSurfaceKHR(VkInstance instance,
                                   const VkAndroidSurfaceCreateInfoKHR* pCreateInfo,
                                   const VkAllocationCallbacks* pAllocator, VkSurfaceKHR* pSurface);
void vkDestroySurfaceKHR(VkInstance instance, VkSurfaceKHR surface,
                         const VkAllocationCallbacks* pAllocator);
VkResult vkGetPhysicalDeviceSurfaceSupportKHR(VkPhysicalDevice physicalDevice,
                                              uint32_t queueFamilyIndex, VkSurfaceKHR surface,
                                              VkBool32* pSupported);
VkResult vkGetPhysicalDeviceSurfaceCapabilitiesKHR(VkPhysicalDevice physicalDevice,
                                                   VkSurfaceKHR surface,
                                                   VkSurfaceCapabilitiesKHR* pSurfaceCapabilities);
VkResult vkGetPhysicalDeviceSurfaceFormatsKHR(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface,
                                              uint32_t* pSurfaceFormatCount,
                                              VkSurfaceFormatKHR* pSurfaceFormats);
VkResult vkGetPhysicalDeviceSurfacePresentModesKHR(VkPhysicalDevice physicalDevice,
                                                   VkSurfaceKHR surface,
                                                   uint32_t* pPresentModeCount,
                                                   VkPresentModeKHR* pPresentModes);

VkResult vkCreateSwapchainKHR(VkDevice device, const VkSwapchainCreateInfoKHR* pCreateInfo,
                              const VkAllocationCallbacks* pAllocator, VkSwapchainKHR* pSwapchain);
void vkDestroySwapchainKHR(VkDevice device, VkSwapchainKHR swapchain,
                           const VkAllocationCallbacks* pAllocator);
VkResult vkGetSwapchainImagesKHR(VkDevice device, VkSwapchainKHR swapchain,
                                 uint32_t* pSwapchainImageCount, VkImage* pSwapchainImages);
VkResult vkAcquireNextImageKHR(VkDevice device, VkSwapchainKHR swapchain, uint64_t timeout,
                               VkSemaphore semaphore, VkFence fence, uint32_t* pImageIndex);
VkResult vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo);

VkResult
vkGetAndroidHardwareBufferPropertiesANDROID(VkDevice device, const struct AHardwareBuffer* buffer,
                                            VkAndroidHardwareBufferPropertiesANDROID* pProperties);
VkResult
vkGetMemoryAndroidHardwareBufferANDROID(VkDevice device,
                                        const VkMemoryGetAndroidHardwareBufferInfoANDROID* pInfo,
                                        struct AHardwareBuffer** pBuffer);

// Host helper: acquires a ref to the AHardwareBuffer backing a Shiraz ANativeWindow swapchain slot.
// Caller releases with AHardwareBuffer_release().
VkResult shiraz_vulkan_swapchain_image_buffer(VkSwapchainKHR swapchain, uint32_t imageIndex,
                                              struct AHardwareBuffer** pBuffer);

// Host helper: probes whether a real host Vulkan ICD with at least one physical device is usable.
// Returns 1 when a physical device is available (writing its apiVersion and Android hardware level
// 0 or 1), or 0 when unavailable (writing apiVersion = 0 and hardwareLevel = -1).
int32_t shiraz_vulkan_host_probe(uint32_t* pApiVersion, int32_t* pHardwareLevel);

#ifdef __cplusplus
}
#endif
