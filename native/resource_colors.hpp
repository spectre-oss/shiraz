// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <androidfw/androidfw_c_api.h>
#include "shiraz/archive_path.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <memory>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>

namespace shiraz::resources {
struct ColorEntry { uint32_t color; std::vector<int32_t> states; uint32_t colorAttr = 0, alphaAttr = 0, baseColor = 0; float alpha = 1; };
struct ColorValue { Res_value value{}; ApkAssetsCookie cookie = -1; };
struct ColorReader {
    AssetManager* manager;
    const ApkAssets* app;
    const ApkAssets* framework;
    const Theme* theme;
    std::set<std::pair<ApkAssetsCookie, std::string>> active;

    ColorValue resource(uint32_t id) {
        ColorValue result; ResTable_config selected{}; uint32_t flags = 0;
        result.cookie = AssetManager_getResource(manager, id, false, 0, &result.value, &selected, &flags);
        if (result.cookie < 0) throw std::runtime_error("Missing color resource");
        return resolved(result);
    }
    ColorValue resolved(ColorValue result, bool defer = false) {
        for (int i = 0; i < 32; ++i) {
            if (result.value.dataType == TYPE_ATTRIBUTE) {
                if (!theme) { if (defer) return result; throw std::runtime_error("Color requires a theme"); }
                uint32_t flags = 0, attribute = result.value.data;
                Res_value original = result.value;
                result.cookie = Theme_getAttribute(theme, attribute, &result.value, &flags);
                if (result.cookie < 0 && defer) { result.value = original; return result; }
                if (result.cookie < 0) throw std::runtime_error("Missing color theme attribute " + std::to_string(attribute));
            } else if (result.value.dataType == TYPE_REFERENCE) {
                ResTable_config selected{}; uint32_t flags = 0, last = 0;
                result.cookie = AssetManager_resolveReference(manager, result.cookie, &result.value, &selected, &flags, &last);
                if (result.cookie < 0) throw std::runtime_error("Unresolved color reference");
            } else return result;
        }
        throw std::runtime_error("Cyclic color reference");
    }
    static std::u16string string(const ResStringPool* pool, size_t index, size_t limit) {
        size_t count = 0; const char16_t* text = pool ? ResStringPool_stringAt(pool, index, &count) : nullptr;
        if (!text || count > limit) throw std::runtime_error("Invalid color XML string");
        return std::u16string(text, count);
    }
    static std::string utf8(const std::u16string& text) {
        std::string result;
        for (size_t i = 0; i < text.size(); ++i) {
            uint32_t c = text[i];
            if (c >= 0xd800 && c <= 0xdbff) {
                if (++i >= text.size() || text[i] < 0xdc00 || text[i] > 0xdfff) throw std::runtime_error("Invalid color asset name");
                c = 0x10000 + ((c - 0xd800) << 10) + (text[i] - 0xdc00);
            } else if (c >= 0xdc00 && c <= 0xdfff) throw std::runtime_error("Invalid color asset name");
            if (c == 0) throw std::runtime_error("NUL in color asset name");
            if (c < 0x80) result += char(c);
            else if (c < 0x800) { result += char(0xc0 | (c >> 6)); result += char(0x80 | (c & 63)); }
            else if (c < 0x10000) { result += char(0xe0 | (c >> 12)); result += char(0x80 | ((c >> 6) & 63)); result += char(0x80 | (c & 63)); }
            else { result += char(0xf0 | (c >> 18)); result += char(0x80 | ((c >> 12) & 63)); result += char(0x80 | ((c >> 6) & 63)); result += char(0x80 | (c & 63)); }
        }
        return result;
    }
    static uint32_t defaultColor(const std::vector<ColorEntry>& entries) {
        uint32_t color = entries.front().color;
        for (const auto& entry : entries) if (entry.states.empty()) color = entry.color;
        return color;
    }
    std::vector<ColorEntry> colors(ColorValue result, int depth = 0, bool integers = false) {
        if (depth > 32) throw std::runtime_error("Color selector nesting limit exceeded");
        result = resolved(result);
        if ((result.value.dataType >= TYPE_FIRST_COLOR_INT && result.value.dataType <= TYPE_LAST_COLOR_INT)
                || (integers && result.value.dataType >= TYPE_FIRST_INT && result.value.dataType <= TYPE_LAST_INT))
            return {{result.value.data, {}}};
        if (result.value.dataType != TYPE_STRING) throw std::runtime_error("Resource is not a color");
        auto filename = utf8(string(AssetManager_getStringPoolForCookie(manager, result.cookie), result.value.data, 65536));
        if (!shiraz::validArchiveFilePath(filename))
            throw std::runtime_error("Invalid color resource archive path");
        auto key = std::make_pair(result.cookie, filename);
        if (!active.insert(key).second) throw std::runtime_error("Cyclic color selector");
        const ApkAssets* owner = result.cookie == 0 && framework ? framework : result.cookie == (framework ? 1 : 0) ? app : nullptr;
        if (!owner) throw std::runtime_error("Unknown color asset cookie");
        // A temporary one-package manager opens the exact cookie's file, even when an
        // app and framework APK contain the same res/... path. No extraction occurs.
        std::unique_ptr<AssetManager, decltype(&AssetManager_delete)> files(AssetManager_new(), AssetManager_delete);
        if (!files) throw std::bad_alloc();
        if (!AssetManager_setApkAssets(files.get(), &owner, 1, true, false)) throw std::runtime_error("Cannot open color asset owner");
        std::unique_ptr<Asset, decltype(&Asset_delete)> asset(AssetManager_openNonAsset(files.get(), filename.c_str(), ACCESS_BUFFER), Asset_delete);
        if (!asset) throw std::runtime_error("Missing color XML asset");
        size_t length = Asset_getLength(asset.get());
        if (length < 8 || length > 4 * 1024 * 1024) throw std::runtime_error("Invalid color XML length");
        const void* bytes = Asset_getBuffer(asset.get(), true);
        if (!bytes) throw std::runtime_error("Cannot read color XML");
        std::unique_ptr<ResXMLTree, decltype(&ResXMLTree_delete)> tree(ResXMLTree_new(), ResXMLTree_delete);
        if (!tree) throw std::bad_alloc();
        ResXMLTree_setTo(tree.get(), bytes, length, false);
        std::unique_ptr<ResXMLParser, decltype(&ResXMLParser_delete)> parser(ResXMLParser_new(tree.get()), ResXMLParser_delete);
        if (!parser) throw std::bad_alloc();
        ResXMLParser_restart(parser.get());
        auto pool = ResXMLParser_getStrings(parser.get());
        std::vector<ColorEntry> entries; int level = 0; bool root = false, ended = false;
        for (int events = 0; events < 65536; ++events) {
            auto event = ResXMLParser_next(parser.get());
            if (event == BAD_DOCUMENT) throw std::runtime_error("Malformed compiled color XML");
            if (event == END_DOCUMENT) { ended = true; break; }
            if (event == END_TAG) { --level; if (level < 0) throw std::runtime_error("Unbalanced color XML"); continue; }
            if (event != START_TAG) continue;
            ++level; auto name = string(pool, ResXMLParser_getElementNameID(parser.get()), 128);
            if (level == 1 && !root && name == u"selector") { root = true; continue; }
            if (!root || level != 2 || name != u"item") throw std::logic_error("Unsupported color XML element " + utf8(name) + " in " + filename);
            if (entries.size() >= 4096) throw std::runtime_error("Too many color selector items");
            size_t attributes = ResXMLParser_getAttributeCount(parser.get());
            if (attributes > 64) throw std::runtime_error("Too many color item attributes");
            ColorEntry entry{}; bool hasColor = false; float alpha = 1;
            constexpr char16_t android_ns[] = u"http://schemas.android.com/apk/res/android";
            constexpr size_t ns_length = sizeof(android_ns) / sizeof(char16_t) - 1;
            auto color_index = ResXMLParser_indexOfAttribute(parser.get(), android_ns, ns_length, u"color", 5);
            auto alpha_index = ResXMLParser_indexOfAttribute(parser.get(), android_ns, ns_length, u"alpha", 5);
            for (size_t i = 0; i < attributes; ++i) {
                auto attr = string(pool, ResXMLParser_getAttributeNameID(parser.get(), i), 128);
                ColorValue value; value.cookie = result.cookie;
                ResXMLParser_getAttributeValue(parser.get(), i, &value.value);
                value = resolved(value, static_cast<ssize_t>(i) == color_index || static_cast<ssize_t>(i) == alpha_index);
                if (static_cast<ssize_t>(i) == color_index) {
                    if (value.value.dataType == TYPE_ATTRIBUTE) { entry.colorAttr = value.value.data; entry.color = 0xffff00ff; }
                    else entry.color = defaultColor(colors(value, depth + 1, true));
                    hasColor = true;
                }
                else if (static_cast<ssize_t>(i) == alpha_index) {
                    if (value.value.dataType == TYPE_ATTRIBUTE) entry.alphaAttr = value.value.data;
                    else if (value.value.dataType == TYPE_FLOAT) std::memcpy(&alpha, &value.value.data, sizeof(alpha));
                    else if (value.value.dataType == TYPE_INT_DEC) alpha = static_cast<float>(static_cast<int32_t>(value.value.data));
                    else throw std::runtime_error("Invalid selector alpha type");
                    if (!std::isfinite(alpha)) throw std::runtime_error("Nonfinite selector alpha");
                } else if (attr == u"lStar") throw std::logic_error("Color selector lStar is not implemented");
                else if (attr.compare(0, 6, u"state_") == 0) {
                    auto id = ResXMLParser_getAttributeNameResID(parser.get(), i);
                    if (!id || id > uint32_t(std::numeric_limits<int32_t>::max()) || value.value.dataType != TYPE_INT_BOOLEAN)
                        throw std::runtime_error("Invalid color state attribute");
                    entry.states.push_back(value.value.data ? static_cast<int32_t>(id) : -static_cast<int32_t>(id));
                } else throw std::logic_error("Unsupported color selector item attribute");
            }
            if (!hasColor) throw std::runtime_error("Color selector item has no color");
            entry.baseColor = entry.color; entry.alpha = alpha;
            float scaled_alpha = float(entry.color >> 24) * alpha;
            auto opacity = static_cast<uint32_t>(std::floor(std::clamp(scaled_alpha, 0.0f, 255.0f) + 0.5f));
            entry.color = (entry.color & 0xffffff) | (opacity << 24); entries.push_back(std::move(entry));
        }
        if (!ended || level != 0 || !root || entries.empty()) throw std::runtime_error("Incomplete or empty color selector");
        active.erase(key); return entries;
    }
};
}
