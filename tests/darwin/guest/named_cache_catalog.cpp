// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/named_cache_catalog.hpp"
#include <array>
#include <cassert>
#include <cstdio>
using namespace shiraz::guest;
int main() {
    std::array<uint8_t, 16> uuid{};
    uuid[0] = 7;
    std::vector<uint8_t> bytes{'S', 'H', 'Z', 'M', 'A', 'P', '2', 0};
    bytes.insert(bytes.end(), uuid.begin(), uuid.end());
    auto append = [&](const void* p, size_t n) {
        auto b = static_cast<const uint8_t*>(p);
        bytes.insert(bytes.end(), b, b + n);
    };
    uint32_t count = 2;
    append(&count, 4);
    auto record = [&](uint64_t start, uint64_t inode, const char* path) {
        uint64_t values[] = {start, 0x100, 0x300, 2, inode};
        append(values, sizeof(values));
        uint32_t n = std::strlen(path);
        append(&n, 4);
        append(path, n);
    };
    record(0x1200, 3, "/cache.second");
    record(0x1000, 5, "/cache.first");
    auto parse = [&] {
        return parse_named_cache_catalog(bytes.data(), bytes.size(), uuid.data(), 0x1000, 0x2000,
                                         0x1000);
    };
    auto good = parse();
    assert(good.status == CacheFileCatalogStatus::Ok && good.mappings.size() == 2);
    assert(good.mappings[0].mapping.start == 0x2000 && good.mappings[0].mapping.inode == 5 &&
           good.mappings[0].path == "/cache.first");
    assert(good.mappings[1].mapping.file_offset == 0x300 &&
           good.mappings[1].path == "/cache.second");
    for (size_t n = 0; n < bytes.size(); ++n) {
        auto short_data =
            parse_named_cache_catalog(bytes.data(), n, uuid.data(), 0x1000, 0x2000, 0x1000);
        assert(short_data.status != CacheFileCatalogStatus::Ok && short_data.mappings.empty());
    }
    auto saved = bytes;
    bytes[0] = 'x';
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    bytes = saved;
    bytes[8] ^= 1;
    assert(parse().status == CacheFileCatalogStatus::UuidMismatch);
    bytes = saved;
    bytes[72] = 'x';
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    bytes = saved;
    bytes[73] = 0;
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    bytes = saved;
    uint32_t huge = UINT32_MAX;
    std::memcpy(bytes.data() + 68, &huge, 4);
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    bytes = saved;
    uint64_t overflowing = UINT64_MAX;
    std::memcpy(bytes.data() + 28, &overflowing, 8);
    assert(parse().status == CacheFileCatalogStatus::OutOfRange);
    bytes = saved;
    bytes.push_back(0);
    assert(parse().status == CacheFileCatalogStatus::Malformed);
    std::puts("NAMED_CACHE_CATALOG_PASS version=1 names=1 sorted_identity=1 truncation=1 bounds=1 "
              "uuid=1");
}
