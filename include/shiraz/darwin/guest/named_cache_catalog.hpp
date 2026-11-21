// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/cache_file_catalog.hpp"
#include <string>

namespace shiraz::guest {
struct NamedCacheMapping {
    CacheFileMapping mapping;
    std::string path;
};
struct NamedCacheCatalog {
    CacheFileCatalogStatus status = CacheFileCatalogStatus::Malformed;
    std::vector<NamedCacheMapping> mappings;
};
// Version 2 adds a length-prefixed pathname to each legacy mapping record.
// Names are file metadata, not proof that a live VM leaf retains that backing.
// The consumer must validate the current cache identity and live VM coverage.
inline NamedCacheCatalog parse_named_cache_catalog(const uint8_t* data, size_t length,
                                                   const uint8_t uuid[16], uint64_t slide,
                                                   uint64_t cache_start, uint64_t cache_size) {
    NamedCacheCatalog result;
    constexpr size_t header = 28, record = 40, maximum = 8u * 1024u * 1024u;
    constexpr char magic[8] = {'S', 'H', 'Z', 'M', 'A', 'P', '2', '\0'};
    if (!data || !uuid || length < header || length > maximum || std::memcmp(data, magic, 8))
        return result;
    uint32_t count = 0;
    std::memcpy(&count, data + 24, 4);
    if (!count || count > 4096)
        return result;
    try {
        // Reuse legacy UUID, overflow, bounds and overlap validation exactly.
        std::vector<uint8_t> legacy(20 + size_t(count) * record);
        std::memcpy(legacy.data(), data + 8, 20);
        std::vector<std::pair<uint64_t, std::string>> names;
        names.reserve(count);
        size_t cursor = header;
        for (uint32_t i = 0; i < count; ++i) {
            if (length - cursor < record + 4)
                return result;
            uint64_t values[5];
            std::memcpy(values, data + cursor, record);
            std::memcpy(legacy.data() + 20 + size_t(i) * record, data + cursor, record);
            cursor += record;
            uint32_t size = 0;
            std::memcpy(&size, data + cursor, 4);
            cursor += 4;
            if (!size || size > 4096 || size > length - cursor || values[3] > UINT32_MAX ||
                !values[4])
                return result;
            const char* path = reinterpret_cast<const char*>(data + cursor);
            if (path[0] != '/' || std::memchr(path, 0, size))
                return result;
            names.emplace_back(values[0], std::string(path, size));
            cursor += size;
        }
        if (cursor != length)
            return result;
        auto checked = parse_cache_file_catalog(legacy.data(), legacy.size(), uuid, slide,
                                                cache_start, cache_size);
        if (checked.status != CacheFileCatalogStatus::Ok) {
            result.status = checked.status;
            return result;
        }
        std::sort(names.begin(), names.end(),
                  [](const auto& a, const auto& b) { return a.first < b.first; });
        std::vector<NamedCacheMapping> mappings;
        mappings.reserve(count);
        for (size_t i = 0; i < count; ++i)
            mappings.push_back({checked.mappings[i], std::move(names[i].second)});
        result.status = CacheFileCatalogStatus::Ok;
        result.mappings = std::move(mappings);
    } catch (const std::bad_alloc&) {
        result.status = CacheFileCatalogStatus::OutOfMemory;
    }
    return result;
}
} // namespace shiraz::guest
