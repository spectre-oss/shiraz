// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <dwrite.h>

namespace {

std::string wide_to_utf8(const wchar_t* src) {
    if (!src || !src[0])
        return "";
    const int needed = WideCharToMultiByte(CP_UTF8, 0, src, -1, nullptr, 0, nullptr, nullptr);
    if (needed <= 1)
        return "";
    std::string out(static_cast<std::size_t>(needed - 1), '\0');
    WideCharToMultiByte(CP_UTF8, 0, src, -1, &out[0], needed, nullptr, nullptr);
    return out;
}

} // namespace

extern "C" {

typedef void (*ShirazWin32FontCallback)(const char* pathUtf8, std::uint32_t faceIndex, void* user);

SHIRAZ_EXPORT std::size_t shiraz_win32_enumerate_system_fonts(ShirazWin32FontCallback cb,
                                                              void* user) {
    IDWriteFactory* factory = nullptr;
    if (FAILED(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory),
                                   reinterpret_cast<IUnknown**>(&factory))) ||
        !factory) {
        return 0;
    }

    IDWriteFontCollection* collection = nullptr;
    std::size_t count = 0;
    if (SUCCEEDED(factory->GetSystemFontCollection(&collection, FALSE)) && collection) {
        const UINT32 familyCount = collection->GetFontFamilyCount();
        for (UINT32 f = 0; f < familyCount; ++f) {
            IDWriteFontFamily* family = nullptr;
            if (FAILED(collection->GetFontFamily(f, &family)) || !family)
                continue;
            const UINT32 fontCount = family->GetFontCount();
            for (UINT32 i = 0; i < fontCount; ++i) {
                IDWriteFont* font = nullptr;
                if (FAILED(family->GetFont(i, &font)) || !font)
                    continue;
                IDWriteFontFace* face = nullptr;
                if (SUCCEEDED(font->CreateFontFace(&face)) && face) {
                    UINT32 fileCount = 1;
                    IDWriteFontFile* fontFile = nullptr;
                    if (SUCCEEDED(face->GetFiles(&fileCount, &fontFile)) && fontFile) {
                        const void* key = nullptr;
                        UINT32 keySize = 0;
                        IDWriteFontFileLoader* loader = nullptr;
                        if (SUCCEEDED(fontFile->GetReferenceKey(&key, &keySize)) &&
                            SUCCEEDED(fontFile->GetLoader(&loader)) && loader) {
                            IDWriteLocalFontFileLoader* local = nullptr;
                            if (SUCCEEDED(
                                    loader->QueryInterface(__uuidof(IDWriteLocalFontFileLoader),
                                                           reinterpret_cast<void**>(&local))) &&
                                local) {
                                UINT32 pathLen = 0;
                                if (SUCCEEDED(
                                        local->GetFilePathLengthFromKey(key, keySize, &pathLen)) &&
                                    pathLen > 0) {
                                    std::vector<wchar_t> buf(pathLen + 1, L'\0');
                                    if (SUCCEEDED(local->GetFilePathFromKey(
                                            key, keySize, buf.data(), pathLen + 1))) {
                                        const std::string utf8 = wide_to_utf8(buf.data());
                                        if (!utf8.empty()) {
                                            if (cb)
                                                cb(utf8.c_str(), face->GetIndex(), user);
                                            ++count;
                                        }
                                    }
                                }
                                local->Release();
                            }
                            loader->Release();
                        }
                        fontFile->Release();
                    }
                    face->Release();
                }
                font->Release();
            }
            family->Release();
        }
        collection->Release();
    }

    factory->Release();
    return count;
}

} // extern "C"
#endif
