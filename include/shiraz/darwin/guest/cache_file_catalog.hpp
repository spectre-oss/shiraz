// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <new>
#include <utility>
#include <vector>

namespace shiraz::guest {
struct CacheFileMapping {
    uint64_t start, end, file_offset, device, inode;
};
enum class CacheFileCatalogStatus { Ok, Malformed, UuidMismatch, OutOfRange, Overlap, OutOfMemory };
struct CacheFileCatalog {
    CacheFileCatalogStatus status = CacheFileCatalogStatus::Malformed;
    std::vector<CacheFileMapping> mappings;
};

// Candidate cache metadata, not proof that a live VM region has this backing.
// The producing helper and consumer use the same host's native byte order.
inline CacheFileCatalog parse_cache_file_catalog(const uint8_t* data, size_t length,
                                                 const uint8_t uuid[16], uint64_t slide,
                                                 uint64_t cache_start, uint64_t cache_size) {
    constexpr size_t header = 20, record = 40, max_records = 4096;
    CacheFileCatalog result;
    if (!data || !uuid || length < header || !cache_start || !cache_size ||
        cache_size > UINT64_MAX - cache_start)
        return result;
    uint8_t nonzero = 0;
    for (size_t i = 0; i < 16; ++i)
        nonzero |= uuid[i];
    if (!nonzero || std::memcmp(data, uuid, 16)) {
        result.status = CacheFileCatalogStatus::UuidMismatch;
        return result;
    }
    uint32_t count = 0;
    std::memcpy(&count, data + 16, sizeof(count));
    if (!count || count > max_records || length != header + size_t(count) * record)
        return result;
    try {
        std::vector<CacheFileMapping> mappings;
        mappings.reserve(count);
        for (uint32_t i = 0; i < count; ++i) {
            uint64_t values[5];
            std::memcpy(values, data + header + size_t(i) * record, record);
            uint64_t start = values[0], size = values[1], offset = values[2];
            if (!size || start > UINT64_MAX - slide) {
                result.status = CacheFileCatalogStatus::OutOfRange;
                return result;
            }
            start += slide;
            if (size > UINT64_MAX - start || size > UINT64_MAX - offset || start < cache_start ||
                start + size > cache_start + cache_size) {
                result.status = CacheFileCatalogStatus::OutOfRange;
                return result;
            }
            mappings.push_back({start, start + size, offset, values[3], values[4]});
        }
        std::sort(
            mappings.begin(), mappings.end(),
            [](const CacheFileMapping& a, const CacheFileMapping& b) { return a.start < b.start; });
        for (size_t i = 1; i < mappings.size(); ++i)
            if (mappings[i].start < mappings[i - 1].end) {
                result.status = CacheFileCatalogStatus::Overlap;
                return result;
            }
        result.status = CacheFileCatalogStatus::Ok;
        result.mappings = std::move(mappings);
    } catch (const std::bad_alloc&) {
        result.status = CacheFileCatalogStatus::OutOfMemory;
    }
    return result;
}
} // namespace shiraz::guest
