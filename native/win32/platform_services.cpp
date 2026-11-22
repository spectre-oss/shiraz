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

} // extern "C"
#endif
