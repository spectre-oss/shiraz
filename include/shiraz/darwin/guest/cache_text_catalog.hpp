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
struct CacheTextSpan {
    uint64_t start, end;
};
enum class CacheCatalogStatus { Ok, Malformed, UuidMismatch, OutOfRange, OutOfMemory };
struct CacheTextCatalog {
    CacheCatalogStatus status = CacheCatalogStatus::Malformed;
    std::vector<CacheTextSpan> spans;
};

// The helper and consumer run on the same host and use its native byte order.
// No paths or backing identity are implied by these logical text intervals.
inline CacheTextCatalog parse_cache_text_catalog(const uint8_t* data, size_t length,
                                                 const uint8_t uuid[16], uint64_t slide,
                                                 uint64_t cache_start, uint64_t cache_size) {
    constexpr size_t header = 20, record = 16, max_records = 65536;
    CacheTextCatalog result;
    if (!data || !uuid || length < header || !cache_start || !cache_size ||
        cache_size > UINT64_MAX - cache_start)
        return result;
    uint8_t nonzero = 0;
    for (size_t i = 0; i < 16; ++i)
        nonzero |= uuid[i];
    if (!nonzero || std::memcmp(data, uuid, 16)) {
        result.status = CacheCatalogStatus::UuidMismatch;
        return result;
    }
    uint32_t count = 0;
    std::memcpy(&count, data + 16, sizeof(count));
    if (!count || count > max_records || length != header + size_t(count) * record)
        return result;
    try {
        std::vector<CacheTextSpan> spans;
        spans.reserve(count);
        for (uint32_t i = 0; i < count; ++i) {
            uint64_t start = 0, size = 0;
            std::memcpy(&start, data + header + size_t(i) * record, 8);
            std::memcpy(&size, data + header + size_t(i) * record + 8, 8);
            if (!size || start > UINT64_MAX - slide) {
                result.status = CacheCatalogStatus::OutOfRange;
                return result;
            }
            start += slide;
            if (size > UINT64_MAX - start || start < cache_start ||
                start + size > cache_start + cache_size) {
                result.status = CacheCatalogStatus::OutOfRange;
                return result;
            }
            spans.push_back({start, start + size});
        }
        std::sort(spans.begin(), spans.end(),
                  [](const CacheTextSpan& a, const CacheTextSpan& b) { return a.start < b.start; });
        std::vector<CacheTextSpan> merged;
        merged.reserve(spans.size());
        for (const auto& span : spans) {
            if (!merged.empty() && span.start <= merged.back().end)
                merged.back().end = std::max(merged.back().end, span.end);
            else
                merged.push_back(span);
        }
        result.status = CacheCatalogStatus::Ok;
        result.spans = std::move(merged);
    } catch (const std::bad_alloc&) {
        result.status = CacheCatalogStatus::OutOfMemory;
    }
    return result;
}
} // namespace shiraz::guest
