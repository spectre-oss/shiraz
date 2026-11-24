// SPDX-License-Identifier: GPL-3.0-only
// Native Windows platform services: IP Helper connectivity, DPAPI keystore
// protection, SAPI5 TTS probe, and WASAPI audio capture probe.
#include "shiraz/platform.hpp"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <iphlpapi.h>
#include <windows.h>
#include <wincrypt.h>
#include <audioclient.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <mmdeviceapi.h>
#include <objbase.h>

extern "C" {

// Connectivity type matches Android ConnectivityManager constants:
// -1 = NONE, 1 = TYPE_WIFI, 9 = TYPE_ETHERNET.
SHIRAZ_EXPORT int shiraz_win32_connectivity_summary(int* outConnected, int* outType) {
    if (outConnected)
        *outConnected = 0;
    if (outType)
        *outType = -1;

    ULONG bufLen = 16384;
    std::vector<std::uint8_t> buffer(bufLen);
    ULONG rc = GetAdaptersAddresses(
        AF_UNSPEC, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER,
        nullptr, reinterpret_cast<IP_ADAPTER_ADDRESSES*>(buffer.data()), &bufLen);
    if (rc == ERROR_BUFFER_OVERFLOW && bufLen > 0) {
        buffer.resize(bufLen);
        rc = GetAdaptersAddresses(
            AF_UNSPEC, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER,
            nullptr, reinterpret_cast<IP_ADAPTER_ADDRESSES*>(buffer.data()), &bufLen);
    }
    if (rc != NO_ERROR)
        return -EIO;

    bool hasWifi = false;
    bool hasEthernet = false;
    const auto* cur = reinterpret_cast<const IP_ADAPTER_ADDRESSES*>(buffer.data());
    while (cur) {
        if (cur->OperStatus == IfOperStatusUp && cur->IfType != IF_TYPE_SOFTWARE_LOOPBACK &&
            cur->IfType != IF_TYPE_TUNNEL && cur->FirstUnicastAddress != nullptr) {
            if (cur->IfType == IF_TYPE_IEEE80211)
                hasWifi = true;
            else
                hasEthernet = true;
        }
        cur = cur->Next;
    }

    if (hasWifi || hasEthernet) {
        if (outConnected)
            *outConnected = 1;
        if (outType)
            *outType = hasEthernet ? 9 : 1;
    }
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_dpapi_protect(const std::uint8_t* plain, std::size_t plainLen,
                                             std::uint8_t* outBuf, std::size_t outCap,
                                             std::size_t* outLen) {
    if (!plain || plainLen == 0 || !outLen)
        return -EINVAL;
    DATA_BLOB inBlob{};
    inBlob.cbData = static_cast<DWORD>(plainLen);
    inBlob.pbData = const_cast<BYTE*>(plain);
    DATA_BLOB outBlob{};
    if (!CryptProtectData(&inBlob, L"ShirazKeyStore", nullptr, nullptr, nullptr,
                          CRYPTPROTECT_UI_FORBIDDEN, &outBlob)) {
        return -EIO;
    }
    *outLen = static_cast<std::size_t>(outBlob.cbData);
    int status = 0;
    if (!outBuf || outCap < outBlob.cbData) {
        status = -ENOSPC;
    } else {
        std::memcpy(outBuf, outBlob.pbData, outBlob.cbData);
    }
    LocalFree(outBlob.pbData);
    return status;
}

SHIRAZ_EXPORT int shiraz_win32_dpapi_unprotect(const std::uint8_t* cipher, std::size_t cipherLen,
                                               std::uint8_t* outBuf, std::size_t outCap,
                                               std::size_t* outLen) {
    if (!cipher || cipherLen == 0 || !outLen)
        return -EINVAL;
    DATA_BLOB inBlob{};
    inBlob.cbData = static_cast<DWORD>(cipherLen);
    inBlob.pbData = const_cast<BYTE*>(cipher);
    DATA_BLOB outBlob{};
    if (!CryptUnprotectData(&inBlob, nullptr, nullptr, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN,
                            &outBlob)) {
        return -EACCES;
    }
    *outLen = static_cast<std::size_t>(outBlob.cbData);
    int status = 0;
    if (!outBuf || outCap < outBlob.cbData) {
        status = -ENOSPC;
    } else {
        std::memcpy(outBuf, outBlob.pbData, outBlob.cbData);
    }
    LocalFree(outBlob.pbData);
    return status;
}

SHIRAZ_EXPORT int shiraz_win32_tts_probe() {
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needUninit = SUCCEEDED(coHr);
    CLSID clsid{};
    int available = 0;
    if (SUCCEEDED(CLSIDFromProgID(L"SAPI.SpVoice", &clsid))) {
        IUnknown* voice = nullptr;
        if (SUCCEEDED(CoCreateInstance(clsid, nullptr, CLSCTX_ALL, IID_IUnknown,
                                       reinterpret_cast<void**>(&voice))) &&
            voice) {
            available = 1;
            voice->Release();
        }
    }
    if (needUninit)
        CoUninitialize();
    return available;
}

SHIRAZ_EXPORT int shiraz_win32_audio_capture_probe(std::uint32_t* outSampleRate,
                                                   std::uint16_t* outChannels) {
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needUninit = SUCCEEDED(coHr);

    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice* device = nullptr;
    IAudioClient* client = nullptr;
    WAVEFORMATEX* mixFormat = nullptr;
    int status = -1;

    if (SUCCEEDED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL,
                                   __uuidof(IMMDeviceEnumerator),
                                   reinterpret_cast<void**>(&enumerator))) &&
        enumerator && SUCCEEDED(enumerator->GetDefaultAudioEndpoint(eCapture, eConsole, &device)) &&
        device &&
        SUCCEEDED(device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, nullptr,
                                   reinterpret_cast<void**>(&client))) &&
        client && SUCCEEDED(client->GetMixFormat(&mixFormat)) && mixFormat) {
        if (outSampleRate)
            *outSampleRate = static_cast<std::uint32_t>(mixFormat->nSamplesPerSec);
        if (outChannels)
            *outChannels = static_cast<std::uint16_t>(mixFormat->nChannels);
        status = 0;
    }

    if (mixFormat)
        CoTaskMemFree(mixFormat);
    if (client)
        client->Release();
    if (device)
        device->Release();
    if (enumerator)
        enumerator->Release();
    if (needUninit)
        CoUninitialize();
    return status;
}

SHIRAZ_EXPORT int shiraz_win32_clipboard_set_text(const char* utf8) {
    if (!utf8)
        return -EINVAL;
    const int wideLen = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, nullptr, 0);
    if (wideLen <= 0)
        return -EINVAL;
    HGLOBAL mem = GlobalAlloc(GMEM_MOVEABLE, static_cast<SIZE_T>(wideLen) * sizeof(wchar_t));
    if (!mem)
        return -ENOMEM;
    auto* dst = static_cast<wchar_t*>(GlobalLock(mem));
    if (!dst) {
        GlobalFree(mem);
        return -ENOMEM;
    }
    const int written = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, dst, wideLen);
    GlobalUnlock(mem);
    if (written <= 0) {
        GlobalFree(mem);
        return -EINVAL;
    }
    if (!OpenClipboard(nullptr)) {
        GlobalFree(mem);
        return -EBUSY;
    }
    if (!EmptyClipboard()) {
        CloseClipboard();
        GlobalFree(mem);
        return -EIO;
    }
    if (!SetClipboardData(CF_UNICODETEXT, mem)) {
        CloseClipboard();
        GlobalFree(mem);
        return -EIO;
    }
    // Ownership of mem transferred to the system on SetClipboardData success.
    CloseClipboard();
    return 0;
}

SHIRAZ_EXPORT int shiraz_win32_clipboard_get_text(char* outBuf, std::size_t outCap,
                                                  std::size_t* outLen) {
    if (!outLen)
        return -EINVAL;
    *outLen = 0;
    if (!OpenClipboard(nullptr))
        return -EBUSY;
    if (!IsClipboardFormatAvailable(CF_UNICODETEXT)) {
        CloseClipboard();
        return -ENOENT;
    }
    HANDLE data = GetClipboardData(CF_UNICODETEXT);
    if (!data) {
        CloseClipboard();
        return -ENOENT;
    }
    const auto* wide = static_cast<const wchar_t*>(GlobalLock(data));
    if (!wide) {
        CloseClipboard();
        return -EIO;
    }
    const int utf8WithNull =
        WideCharToMultiByte(CP_UTF8, 0, wide, -1, nullptr, 0, nullptr, nullptr);
    int status = 0;
    if (utf8WithNull <= 0) {
        status = -EIO;
    } else {
        const std::size_t bytes = static_cast<std::size_t>(utf8WithNull - 1);
        *outLen = bytes;
        if (!outBuf || outCap < bytes + 1) {
            status = -ENOSPC;
        } else {
            const int copied = WideCharToMultiByte(CP_UTF8, 0, wide, -1, outBuf,
                                                   static_cast<int>(outCap), nullptr, nullptr);
            if (copied <= 0)
                status = -EIO;
        }
    }
    GlobalUnlock(data);
    CloseClipboard();
    return status;
}

SHIRAZ_EXPORT int shiraz_win32_camera_probe(std::uint32_t* outDeviceCount) {
    if (outDeviceCount)
        *outDeviceCount = 0;
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needCoUninit = SUCCEEDED(coHr);
    const HRESULT mfHr = MFStartup(MF_VERSION, MFSTARTUP_LITE);
    if (FAILED(mfHr)) {
        if (needCoUninit)
            CoUninitialize();
        return -EIO;
    }

    IMFAttributes* attrs = nullptr;
    IMFActivate** devices = nullptr;
    UINT32 count = 0;
    int status = -EIO;

    if (SUCCEEDED(MFCreateAttributes(&attrs, 1)) && attrs &&
        SUCCEEDED(attrs->SetGUID(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE,
                                 MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID))) {
        const HRESULT enumHr = MFEnumDeviceSources(attrs, &devices, &count);
        if (SUCCEEDED(enumHr)) {
            if (outDeviceCount)
                *outDeviceCount = static_cast<std::uint32_t>(count);
            for (UINT32 i = 0; i < count; ++i) {
                if (devices && devices[i])
                    devices[i]->Release();
            }
            if (devices)
                CoTaskMemFree(devices);
            status = 0;
        }
    }

    if (attrs)
        attrs->Release();
    MFShutdown();
    if (needCoUninit)
        CoUninitialize();
    return status;
}

// Captures one RGBA8888 frame from the requested Media Foundation video capture
// device via IMFSourceReader. Returns -ENODEV when no webcam is attached (e.g.
// headless CI), -EINVAL on null output pointers, or -ENOSPC if the output
// buffer is smaller than width * height * 4 (writing required dimensions/size).
SHIRAZ_EXPORT int shiraz_win32_camera_capture_frame(std::uint32_t deviceIndex,
                                                    std::uint8_t* outRgba, std::size_t outCap,
                                                    std::uint32_t* outWidth,
                                                    std::uint32_t* outHeight,
                                                    std::size_t* outBytes) {
    if (!outWidth || !outHeight || !outBytes)
        return -EINVAL;
    *outWidth = 0;
    *outHeight = 0;
    *outBytes = 0;

    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needCoUninit = SUCCEEDED(coHr);
    if (FAILED(MFStartup(MF_VERSION, MFSTARTUP_LITE))) {
        if (needCoUninit)
            CoUninitialize();
        return -EIO;
    }

    IMFAttributes* attrs = nullptr;
    IMFActivate** devices = nullptr;
    IMFMediaSource* mediaSource = nullptr;
    IMFSourceReader* reader = nullptr;
    IMFMediaType* rgbType = nullptr;
    IMFMediaType* currentType = nullptr;
    IMFSample* sample = nullptr;
    IMFMediaBuffer* buffer = nullptr;
    UINT32 count = 0;
    int status = -EIO;

    do {
        if (FAILED(MFCreateAttributes(&attrs, 1)) || !attrs ||
            FAILED(attrs->SetGUID(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE,
                                  MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID))) {
            break;
        }
        if (FAILED(MFEnumDeviceSources(attrs, &devices, &count))) {
            break;
        }
        if (count == 0 || deviceIndex >= count || !devices || !devices[deviceIndex]) {
            status = -ENODEV;
            break;
        }
        if (FAILED(devices[deviceIndex]->ActivateObject(__uuidof(IMFMediaSource),
                                                        reinterpret_cast<void**>(&mediaSource))) ||
            !mediaSource) {
            status = -EIO;
            break;
        }
        if (FAILED(MFCreateSourceReaderFromMediaSource(mediaSource, nullptr, &reader)) || !reader) {
            status = -EIO;
            break;
        }
        if (SUCCEEDED(MFCreateMediaType(&rgbType)) && rgbType) {
            rgbType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            rgbType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
            reader->SetCurrentMediaType(static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM),
                                        nullptr, rgbType);
        }
        if (FAILED(reader->GetCurrentMediaType(
                static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM), &currentType)) ||
            !currentType) {
            status = -EIO;
            break;
        }
        UINT32 width = 0;
        UINT32 height = 0;
        if (FAILED(MFGetAttributeSize(currentType, MF_MT_FRAME_SIZE, &width, &height)) ||
            width == 0 || height == 0 || width > 8192 || height > 8192) {
            status = -EIO;
            break;
        }
        const std::size_t needed = static_cast<std::size_t>(width) * height * 4U;
        *outWidth = static_cast<std::uint32_t>(width);
        *outHeight = static_cast<std::uint32_t>(height);
        *outBytes = needed;
        if (!outRgba || outCap < needed) {
            status = -ENOSPC;
            break;
        }
        DWORD streamFlags = 0;
        LONGLONG timestamp = 0;
        bool captured = false;
        for (int attempt = 0; attempt < 8 && !captured; ++attempt) {
            if (sample) {
                sample->Release();
                sample = nullptr;
            }
            const HRESULT rhr =
                reader->ReadSample(static_cast<DWORD>(MF_SOURCE_READER_FIRST_VIDEO_STREAM), 0,
                                   nullptr, &streamFlags, &timestamp, &sample);
            if (FAILED(rhr)) {
                break;
            }
            if (sample) {
                captured = true;
            }
        }
        if (!captured || !sample || FAILED(sample->ConvertToContiguousBuffer(&buffer)) || !buffer) {
            status = -EAGAIN;
            break;
        }
        BYTE* raw = nullptr;
        DWORD maxLen = 0;
        DWORD curLen = 0;
        if (FAILED(buffer->Lock(&raw, &maxLen, &curLen)) || !raw) {
            status = -EIO;
            break;
        }
        const std::size_t copyBytes = curLen < needed ? static_cast<std::size_t>(curLen) : needed;
        // Convert Media Foundation RGB32 (BGRA) to Android RGBA8888.
        for (std::size_t i = 0; i + 3U < copyBytes; i += 4U) {
            outRgba[i + 0U] = raw[i + 2U];
            outRgba[i + 1U] = raw[i + 1U];
            outRgba[i + 2U] = raw[i + 0U];
            outRgba[i + 3U] = 0xFFU;
        }
        buffer->Unlock();
        status = 0;
    } while (false);

    if (buffer)
        buffer->Release();
    if (sample)
        sample->Release();
    if (currentType)
        currentType->Release();
    if (rgbType)
        rgbType->Release();
    if (reader)
        reader->Release();
    if (mediaSource) {
        mediaSource->Shutdown();
        mediaSource->Release();
    }
    if (devices) {
        for (UINT32 i = 0; i < count; ++i) {
            if (devices[i])
                devices[i]->Release();
        }
        CoTaskMemFree(devices);
    }
    if (attrs)
        attrs->Release();
    MFShutdown();
    if (needCoUninit)
        CoUninitialize();
    return status;
}

} // extern "C"
#endif
