// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstdint>
#include <cstring>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mftransform.h>

namespace {

bool subtype_for_mime(const char* mime, GUID* outSubtype) {
    if (!mime || !outSubtype)
        return false;
    if (std::strcmp(mime, "video/avc") == 0) {
        *outSubtype = MFVideoFormat_H264;
        return true;
    }
    if (std::strcmp(mime, "video/hevc") == 0) {
        *outSubtype = MFVideoFormat_HEVC;
        return true;
    }
    if (std::strcmp(mime, "video/x-vnd.on2.vp9") == 0) {
        *outSubtype = MFVideoFormat_VP90;
        return true;
    }
    if (std::strcmp(mime, "video/av01") == 0) {
        *outSubtype = MFVideoFormat_AV1;
        return true;
    }
    return false;
}

} // namespace

extern "C" {

SHIRAZ_EXPORT int shiraz_win32_video_has_decoder(const char* mime) {
    GUID subtype{};
    if (!subtype_for_mime(mime, &subtype))
        return 0;

    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needCoUninit = SUCCEEDED(coHr);
    const HRESULT mfHr = MFStartup(MF_VERSION, MFSTARTUP_LITE);
    if (FAILED(mfHr)) {
        if (needCoUninit)
            CoUninitialize();
        return 0;
    }

    MFT_REGISTER_TYPE_INFO inputInfo{};
    inputInfo.guidMajorType = MFMediaType_Video;
    inputInfo.guidSubtype = subtype;

    IMFActivate** activates = nullptr;
    UINT32 count = 0;
    const DWORD flags = MFT_ENUM_FLAG_SYNCMFT | MFT_ENUM_FLAG_ASYNCMFT | MFT_ENUM_FLAG_HARDWARE |
                        MFT_ENUM_FLAG_SORTANDFILTER;
    const HRESULT hr =
        MFTEnumEx(MFT_CATEGORY_VIDEO_DECODER, flags, &inputInfo, nullptr, &activates, &count);
    int found = 0;
    if (SUCCEEDED(hr) && activates) {
        found = count > 0 ? 1 : 0;
        for (UINT32 i = 0; i < count; ++i) {
            if (activates[i])
                activates[i]->Release();
        }
        CoTaskMemFree(activates);
    }

    MFShutdown();
    if (needCoUninit)
        CoUninitialize();
    return found;
}

} // extern "C"
#endif
