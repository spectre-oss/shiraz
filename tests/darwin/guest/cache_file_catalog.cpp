// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cache_file_catalog.hpp"
#include <array>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <vector>

using namespace shiraz::guest;
int main() {
    std::array<uint8_t, 16> uuid{};
    uuid[0] = 7;
    std::vector<uint8_t> bytes(20 + 2 * 40);
    std::memcpy(bytes.data(), uuid.data(), 16);
    uint32_t count = 2;
    std::memcpy(bytes.data() + 16, &count, 4);
    auto record = [&](size_t i, uint64_t start, uint64_t size, uint64_t offset, uint64_t dev,
                      uint64_t ino) {
        uint64_t words[] = {start, size, offset, dev, ino};
        std::memcpy(bytes.data() + 20 + i * 40, words, sizeof(words));
    };
    record(0, 0x1200, 0x100, 0x300, 2, 3);
    record(1, 0x1000, 0x100, 0, 4, 5);
    auto parse = [&] {
        return parse_cache_file_catalog(bytes.data(), bytes.size(), uuid.data(), 0x1000, 0x2000,
                                        0x1000);
    };
    auto good = parse();
    assert(good.status == CacheFileCatalogStatus::Ok && good.mappings.size() == 2);
    assert(good.mappings[0].start == 0x2000 && good.mappings[0].end == 0x2100 &&
           good.mappings[0].inode == 5);
    assert(good.mappings[1].start == 0x2200 && good.mappings[1].file_offset == 0x300 &&
           good.mappings[1].device == 2);
    auto short_data = parse_cache_file_catalog(bytes.data(), bytes.size() - 1, uuid.data(), 0x1000,
                                               0x2000, 0x1000);
    assert(short_data.status == CacheFileCatalogStatus::Malformed && short_data.mappings.empty());
    auto wrong = uuid;
    wrong[0] = 1;
    assert(
        parse_cache_file_catalog(bytes.data(), bytes.size(), wrong.data(), 0x1000, 0x2000, 0x1000)
            .status == CacheFileCatalogStatus::UuidMismatch);
    wrong.fill(0);
    assert(
        parse_cache_file_catalog(bytes.data(), bytes.size(), wrong.data(), 0x1000, 0x2000, 0x1000)
            .status == CacheFileCatalogStatus::UuidMismatch);
    count = UINT32_MAX;
    std::memcpy(bytes.data() + 16, &count, 4);
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    count = 2;
    std::memcpy(bytes.data() + 16, &count, 4);
    record(0, 0x1080, 0x100, 0, 2, 3);
    auto overlap = parse();
    assert(overlap.status == CacheFileCatalogStatus::Overlap && overlap.mappings.empty());
    record(0, UINT64_MAX, 2, 0, 2, 3);
    auto overflow = parse();
    assert(overflow.status == CacheFileCatalogStatus::OutOfRange && overflow.mappings.empty());
    record(0, 0x1200, 0x100, UINT64_MAX - 10, 2, 3);
    assert(parse().status == CacheFileCatalogStatus::OutOfRange);
    record(0, 0x1fff, 2, 0, 2, 3);
    assert(parse().status == CacheFileCatalogStatus::OutOfRange);
    record(0, 0x1200, 0, 0, 2, 3);
    assert(parse().status == CacheFileCatalogStatus::OutOfRange);
    assert(parse_cache_file_catalog(nullptr, 20, uuid.data(), 0, 0x2000, 0x1000).status ==
           CacheFileCatalogStatus::Malformed);
    assert(parse_cache_file_catalog(bytes.data(), bytes.size(), uuid.data(), 0, UINT64_MAX, 2)
               .status == CacheFileCatalogStatus::Malformed);
    std::puts("CACHE_FILE_CATALOG_PASS bounds=1 uuid=1 offset=1 overlap=1 failure_closed=1");
}
