// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstddef>
#include <cstdint>
#include <cstring>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <audioclient.h>
#include <mmdeviceapi.h>

struct ShirazWin32AudioStream {
    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice* device = nullptr;
    IAudioClient* client = nullptr;
    IAudioRenderClient* render = nullptr;
    WAVEFORMATEX format{};
    UINT32 bufferFrames = 0;
    bool comInitialized = false;
    bool started = false;
};

namespace {

void cleanup_stream(ShirazWin32AudioStream* s) {
    if (!s)
        return;
    if (s->started && s->client)
        s->client->Stop();
    if (s->render)
        s->render->Release();
    if (s->client)
        s->client->Release();
    if (s->device)
        s->device->Release();
    if (s->enumerator)
        s->enumerator->Release();
    if (s->comInitialized)
        CoUninitialize();
    delete s;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_audio_probe_default(std::uint32_t* outSampleRate,
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
        enumerator && SUCCEEDED(enumerator->GetDefaultAudioEndpoint(eRender, eConsole, &device)) &&
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

SHIRAZ_EXPORT ShirazWin32AudioStream* shiraz_win32_audio_open(std::uint32_t sampleRate,
                                                              std::uint16_t channels) {
    if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 8)
        return nullptr;

    auto* s = new ShirazWin32AudioStream();
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    s->comInitialized = SUCCEEDED(coHr);

    if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL,
                                __uuidof(IMMDeviceEnumerator),
                                reinterpret_cast<void**>(&s->enumerator))) ||
        !s->enumerator) {
        cleanup_stream(s);
        return nullptr;
    }
    if (FAILED(s->enumerator->GetDefaultAudioEndpoint(eRender, eConsole, &s->device)) ||
        !s->device) {
        cleanup_stream(s);
        return nullptr;
    }
    if (FAILED(s->device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, nullptr,
                                   reinterpret_cast<void**>(&s->client))) ||
        !s->client) {
        cleanup_stream(s);
        return nullptr;
    }

    s->format.wFormatTag = WAVE_FORMAT_PCM;
    s->format.nChannels = channels;
    s->format.nSamplesPerSec = sampleRate;
    s->format.wBitsPerSample = 16;
    s->format.nBlockAlign = static_cast<WORD>(channels * 2);
    s->format.nAvgBytesPerSec = sampleRate * s->format.nBlockAlign;
    s->format.cbSize = 0;

    const REFERENCE_TIME bufferDuration = 1000000; // 100 ms in 100-ns units
    const DWORD flags =
        AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY;
    if (FAILED(s->client->Initialize(AUDCLNT_SHAREMODE_SHARED, flags, bufferDuration, 0, &s->format,
                                     nullptr))) {
        cleanup_stream(s);
        return nullptr;
    }
    if (FAILED(s->client->GetBufferSize(&s->bufferFrames)) ||
        FAILED(s->client->GetService(__uuidof(IAudioRenderClient),
                                     reinterpret_cast<void**>(&s->render))) ||
        !s->render) {
        cleanup_stream(s);
        return nullptr;
    }
    if (SUCCEEDED(s->client->Start()))
        s->started = true;
    return s;
}

SHIRAZ_EXPORT int shiraz_win32_audio_write(ShirazWin32AudioStream* s, const std::int16_t* pcm,
                                           std::size_t frames) {
    if (!s || !s->client || !s->render || !pcm || frames == 0)
        return -1;
    UINT32 padding = 0;
    if (FAILED(s->client->GetCurrentPadding(&padding)))
        return -1;
    const UINT32 available = s->bufferFrames > padding ? s->bufferFrames - padding : 0;
    const UINT32 toWrite = static_cast<UINT32>(frames < available ? frames : available);
    if (toWrite == 0)
        return 0;
    BYTE* dst = nullptr;
    if (FAILED(s->render->GetBuffer(toWrite, &dst)) || !dst)
        return -1;
    std::memcpy(dst, pcm, static_cast<std::size_t>(toWrite) * s->format.nBlockAlign);
    if (FAILED(s->render->ReleaseBuffer(toWrite, 0)))
        return -1;
    return static_cast<int>(toWrite);
}

SHIRAZ_EXPORT void shiraz_win32_audio_close(ShirazWin32AudioStream* s) {
    cleanup_stream(s);
}

} // extern "C"
#endif
