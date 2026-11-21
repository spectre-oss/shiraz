// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cache_text_catalog.hpp"
#include <array>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <vector>

using namespace shiraz::guest;
int main() {
    std::array<uint8_t, 16> uuid{};
    uuid[0] = 1;
    std::vector<uint8_t> bytes(20 + 3 * 16);
    std::memcpy(bytes.data(), uuid.data(), uuid.size());
    uint32_t count = 3;
    std::memcpy(bytes.data() + 16, &count, 4);
    auto record = [&](size_t i, uint64_t start, uint64_t size) {
        std::memcpy(bytes.data() + 20 + i * 16, &start, 8);
        std::memcpy(bytes.data() + 28 + i * 16, &size, 8);
    };
    record(0, 0x1200, 0x10);
    record(1, 0x1080, 0x100);
    record(2, 0x1000, 0x100);
    auto parse = [&] {
        return parse_cache_text_catalog(bytes.data(), bytes.size(), uuid.data(), 0x1000, 0x2000,
                                        0x1000);
    };
    auto valid = parse();
    assert(valid.status == CacheCatalogStatus::Ok && valid.spans.size() == 2);
    assert(valid.spans[0].start == 0x2000 && valid.spans[0].end == 0x2180);
    assert(valid.spans[1].start == 0x2200 && valid.spans[1].end == 0x2210);
    auto truncated = parse_cache_text_catalog(bytes.data(), bytes.size() - 1, uuid.data(), 0x1000,
                                              0x2000, 0x1000);
    assert(truncated.status == CacheCatalogStatus::Malformed && truncated.spans.empty());
    auto wrong_uuid = uuid;
    wrong_uuid[0] = 2;
    assert(parse_cache_text_catalog(bytes.data(), bytes.size(), wrong_uuid.data(), 0x1000, 0x2000,
                                    0x1000)
               .status == CacheCatalogStatus::UuidMismatch);
    wrong_uuid.fill(0);
    assert(parse_cache_text_catalog(bytes.data(), bytes.size(), wrong_uuid.data(), 0x1000, 0x2000,
                                    0x1000)
               .status == CacheCatalogStatus::UuidMismatch);
    count = UINT32_MAX;
    std::memcpy(bytes.data() + 16, &count, 4);
    assert(parse().status == CacheCatalogStatus::Malformed);
    count = 3;
    std::memcpy(bytes.data() + 16, &count, 4);
    record(0, UINT64_MAX, 2);
    auto overflow = parse();
    assert(overflow.status == CacheCatalogStatus::OutOfRange && overflow.spans.empty());
    record(0, 0x1fff, 2);
    assert(parse().status == CacheCatalogStatus::OutOfRange);
    record(0, 0x1000, 0);
    assert(parse().status == CacheCatalogStatus::OutOfRange);
    assert(parse_cache_text_catalog(nullptr, 20, uuid.data(), 0, 0x2000, 0x1000).status ==
           CacheCatalogStatus::Malformed);
    assert(parse_cache_text_catalog(bytes.data(), bytes.size(), uuid.data(), 0, UINT64_MAX, 2)
               .status == CacheCatalogStatus::Malformed);
    std::puts("CACHE_TEXT_CATALOG_PASS bounds=1 uuid=1 overflow=1 merge=1 failure_closed=1");
}
