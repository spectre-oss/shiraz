// SPDX-License-Identifier: GPL-3.0-only
// Native Windows AHardwareBuffer backend over D3D11 ID3D11Texture2D + DXGI shared handle
// with automatic WARP/CPU fallback for headless sessions.
#include "shiraz/platform.hpp"

#include <atomic>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#if defined(_WIN32)
#include "shiraz/hardware_buffer.h"
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <d3d11.h>
#include <dxgi.h>

struct ShirazWin32HwBuf {
    std::atomic<unsigned> refs{1};
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    std::uint32_t stride = 0;
    std::uint32_t format = 0;
    std::uint64_t usage = 0;
    bool gpuBacked = false;
    ID3D11Device* device = nullptr;
    ID3D11DeviceContext* context = nullptr;
    ID3D11Texture2D* texture = nullptr;
    HANDLE sharedHandle = nullptr;
    std::mutex mutex;
    bool locked = false;
    std::vector<std::uint8_t> pixels;

    ~ShirazWin32HwBuf() {
        if (texture)
            texture->Release();
        if (context)
            context->Release();
        if (device)
            device->Release();
    }
};

namespace {

std::uint32_t bpp_for_format(std::uint32_t fmt) {
    switch (fmt) {
    case 1: // RGBA_8888
    case 2: // RGBX_8888
        return 4;
    case 4: // RGB_565
        return 2;
    default:
        return 0;
    }
}

DXGI_FORMAT dxgi_for_format(std::uint32_t fmt) {
    switch (fmt) {
    case 1:
    case 2:
        return DXGI_FORMAT_R8G8B8A8_UNORM;
    case 4:
        return DXGI_FORMAT_B5G6R5_UNORM;
    default:
        return DXGI_FORMAT_UNKNOWN;
    }
}

bool create_d3d11_device(ID3D11Device** outDev, ID3D11DeviceContext** outCtx) {
    const D3D_FEATURE_LEVEL levels[] = {
        D3D_FEATURE_LEVEL_11_1,
        D3D_FEATURE_LEVEL_11_0,
        D3D_FEATURE_LEVEL_10_1,
        D3D_FEATURE_LEVEL_10_0,
    };
    const D3D_DRIVER_TYPE drivers[] = {D3D_DRIVER_TYPE_HARDWARE, D3D_DRIVER_TYPE_WARP};
    for (D3D_DRIVER_TYPE driver : drivers) {
        HRESULT hr =
            D3D11CreateDevice(nullptr, driver, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT, levels,
                              static_cast<UINT>(sizeof(levels) / sizeof(levels[0])),
                              D3D11_SDK_VERSION, outDev, nullptr, outCtx);
        if (SUCCEEDED(hr) && *outDev && *outCtx) {
            return true;
        }
    }
    return false;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_hwbuf_allocate(std::uint32_t width, std::uint32_t height,
                                              std::uint32_t format, std::uint64_t usage,
                                              ShirazWin32HwBuf** out) {
    if (!out)
        return -EINVAL;
    *out = nullptr;
    const std::uint32_t bpp = bpp_for_format(format);
    if (width == 0 || height == 0 || width > 8192 || height > 8192 || bpp == 0)
        return -EINVAL;

    auto* buf = new ShirazWin32HwBuf();
    buf->width = width;
    buf->height = height;
    buf->stride = width;
    buf->format = format;
    buf->usage = usage;
    buf->pixels.resize(static_cast<std::size_t>(width) * height * bpp, 0);

    // Attempt GPU D3D11 shared texture creation (hardware or WARP).
    if (create_d3d11_device(&buf->device, &buf->context)) {
        D3D11_TEXTURE2D_DESC desc{};
        desc.Width = width;
        desc.Height = height;
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        desc.Format = dxgi_for_format(format);
        desc.SampleDesc.Count = 1;
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET;
        desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED;

        if (SUCCEEDED(buf->device->CreateTexture2D(&desc, nullptr, &buf->texture)) &&
            buf->texture) {
            buf->gpuBacked = true;
            IDXGIResource* dxgiRes = nullptr;
            if (SUCCEEDED(buf->texture->QueryInterface(__uuidof(IDXGIResource),
                                                       reinterpret_cast<void**>(&dxgiRes))) &&
                dxgiRes) {
                HANDLE h = nullptr;
                if (SUCCEEDED(dxgiRes->GetSharedHandle(&h))) {
                    buf->sharedHandle = h;
                }
                dxgiRes->Release();
            }
        }
    }

    *out = buf;
    return 0;
}

SHIRAZ_EXPORT void shiraz_win32_hwbuf_acquire(ShirazWin32HwBuf* buf) {
    if (buf)
        buf->refs.fetch_add(1, std::memory_order_relaxed);
}

SHIRAZ_EXPORT void shiraz_win32_hwbuf_release(ShirazWin32HwBuf* buf) {
    if (buf && buf->refs.fetch_sub(1, std::memory_order_acq_rel) == 1)
        delete buf;
}

SHIRAZ_EXPORT int shiraz_win32_hwbuf_describe(const ShirazWin32HwBuf* buf, std::uint32_t* outWidth,
                                              std::uint32_t* outHeight, std::uint32_t* outStride,
                                              std::uint32_t* outFormat, int* outGpuBacked) {
    if (!buf)
        return -EINVAL;
    if (outWidth)
        *outWidth = buf->width;
    if (outHeight)
        *outHeight = buf->height;
    if (outStride)
        *outStride = buf->stride;
    if (outFormat)
        *outFormat = buf->format;
    if (outGpuBacked)
        *outGpuBacked = buf->gpuBacked ? 1 : 0;
    return 0;
}

SHIRAZ_EXPORT void* shiraz_win32_hwbuf_shared_handle(const ShirazWin32HwBuf* buf) {
    return buf ? static_cast<void*>(buf->sharedHandle) : nullptr;
}

SHIRAZ_EXPORT int shiraz_win32_hwbuf_lock(ShirazWin32HwBuf* buf, void** outBits) {
    if (!buf || !outBits)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(buf->mutex);
    if (buf->locked)
        return -EBUSY;
    buf->locked = true;
    *outBits = buf->pixels.data();
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_hwbuf_unlock(ShirazWin32HwBuf* buf) {
    if (!buf)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(buf->mutex);
    if (!buf->locked)
        return -EINVAL;
    buf->locked = false;
    if (buf->gpuBacked && buf->context && buf->texture) {
        const std::uint32_t bpp = bpp_for_format(buf->format);
        buf->context->UpdateSubresource(buf->texture, 0, nullptr, buf->pixels.data(),
                                        buf->stride * bpp, 0);
    }
    return 0;
}

SHIRAZ_EXPORT int AHardwareBuffer_allocate(const AHardwareBuffer_Desc* desc,
                                           AHardwareBuffer** outBuffer) {
    if (!desc || !outBuffer || (desc->layers != 0 && desc->layers != 1))
        return -EINVAL;
    ShirazWin32HwBuf* raw = nullptr;
    const int rc =
        shiraz_win32_hwbuf_allocate(desc->width, desc->height, desc->format, desc->usage, &raw);
    if (rc != 0)
        return rc;
    *outBuffer = reinterpret_cast<AHardwareBuffer*>(raw);
    return 0;
}

SHIRAZ_EXPORT void AHardwareBuffer_acquire(AHardwareBuffer* buffer) {
    shiraz_win32_hwbuf_acquire(reinterpret_cast<ShirazWin32HwBuf*>(buffer));
}

SHIRAZ_EXPORT void AHardwareBuffer_release(AHardwareBuffer* buffer) {
    shiraz_win32_hwbuf_release(reinterpret_cast<ShirazWin32HwBuf*>(buffer));
}

SHIRAZ_EXPORT void AHardwareBuffer_describe(const AHardwareBuffer* buffer,
                                            AHardwareBuffer_Desc* outDesc) {
    if (!outDesc)
        return;
    std::memset(outDesc, 0, sizeof(*outDesc));
    const auto* raw = reinterpret_cast<const ShirazWin32HwBuf*>(buffer);
    if (!raw)
        return;
    outDesc->width = raw->width;
    outDesc->height = raw->height;
    outDesc->layers = 1;
    outDesc->format = raw->format;
    outDesc->usage = raw->usage;
    outDesc->stride = raw->stride;
}

SHIRAZ_EXPORT int AHardwareBuffer_lock(AHardwareBuffer* buffer, uint64_t, int32_t, const ARect*,
                                       void** outVirtualAddress) {
    return shiraz_win32_hwbuf_lock(reinterpret_cast<ShirazWin32HwBuf*>(buffer), outVirtualAddress);
}

SHIRAZ_EXPORT int AHardwareBuffer_unlock(AHardwareBuffer* buffer, int32_t* fence) {
    if (fence)
        *fence = -1;
    return shiraz_win32_hwbuf_unlock(reinterpret_cast<ShirazWin32HwBuf*>(buffer));
}

SHIRAZ_EXPORT int AHardwareBuffer_sendHandleToUnixSocket(const AHardwareBuffer*, int) {
    return -ENOTSUP;
}

SHIRAZ_EXPORT int AHardwareBuffer_recvHandleFromUnixSocket(int, AHardwareBuffer**) {
    return -ENOTSUP;
}

SHIRAZ_EXPORT int AHardwareBuffer_isSupported(const AHardwareBuffer_Desc* desc) {
    if (!desc || desc->width == 0 || desc->height == 0 || desc->width > 8192 ||
        desc->height > 8192 || (desc->layers != 0 && desc->layers != 1))
        return 0;
    return bpp_for_format(desc->format) != 0 ? 1 : 0;
}

SHIRAZ_EXPORT int shiraz_hardware_buffer_export(AHardwareBuffer*, uint32_t*, uint32_t*) {
    return -ENOTSUP;
}

} // extern "C"
#endif
