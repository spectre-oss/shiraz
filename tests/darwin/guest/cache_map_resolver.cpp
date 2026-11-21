// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cache_map_resolver.hpp"
#include <cassert>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>
using namespace shiraz::guest;
int main() {
    NamedCacheCatalog catalog{CacheFileCatalogStatus::Ok,
                              {{{0x2000, 0x4000, 0x8000, 1, 7}, "/fixture"}}};
    bool identity = true, file = true;
    CacheMapResolver resolve(
        catalog, 0x1000, 0x5000, [&] { return identity; }, [&](const auto&) { return file; });
    ProcessRegion region{0x3000, 0x4000, 0x9000, VM_PROT_READ, 3, SM_COW, true, VM_INHERIT_COPY};
    ProcMapRecord out;
    out.name = "untouched";
    assert(resolve(region, out) && out.offset == 0x9000 && out.inode == 7 &&
           out.name == "/fixture" && !out.shared);
    region.object_offset = 0;
    assert(resolve(region, out) && out.offset == 0x9000);
    auto reject = [&] {
        out.name = "untouched";
        assert(!resolve(region, out) && out.name == "untouched");
    };
    region.object_offset = 0x100;
    reject();
    region.object_offset = 0;
    identity = false;
    reject();
    identity = true;
    file = false;
    reject();
    file = true;
    region.inheritance = VM_INHERIT_SHARE;
    reject();
    region.inheritance = VM_INHERIT_COPY;
    region.share_mode = SM_SHARED;
    reject();
    region.share_mode = SM_COW;
    region.end = 0x5000;
    reject();
    region.end = 0x4000;
    region.external_pager = false;
    reject();
    region.start = 0x4000;
    region.end = 0x5000;
    region.protection = 0;
    assert(resolve(region, out) && out.name_kind == ProcMapName::Anonymous && out.name.empty() &&
           !out.inode && !out.offset);
    region.protection = VM_PROT_READ | VM_PROT_WRITE;
    assert(resolve(region, out) && out.protection == 3);
    region.protection = VM_PROT_EXECUTE;
    reject();
    region.protection = 0;
    region.external_pager = true;
    reject();
    region.start = 0x7000;
    region.end = 0x8000;
    reject();
    CacheMapResolver no_identity(catalog, 0x1000, 0x5000, {});
    region = {0x2000, 0x3000, 0x8000, 1, 3, SM_COW, true, VM_INHERIT_COPY};
    assert(!no_identity(region, out));
    char path[] = "/private/tmp/shiraz-cache-binding-XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0 && !ftruncate(fd, 0x4000));
    struct stat st{};
    assert(!fstat(fd, &st));
    NamedCacheCatalog actual{CacheFileCatalogStatus::Ok,
                             {{{0x2000, 0x4000, 0x1000, uint32_t(st.st_dev), st.st_ino}, path}}};
    CacheMapResolver actual_file(actual, 0x1000, 0x5000, [] { return true; });
    region = {0x2000, 0x3000, 0x1000, 1, 3, SM_COW, true, VM_INHERIT_COPY};
    assert(actual_file(region, out) && out.inode == st.st_ino && out.offset == 0x1000);
    assert(!ftruncate(fd, 0x1000));
    out.name = "untouched";
    assert(!actual_file(region, out) && out.name == "untouched");
    assert(!ftruncate(fd, 0x4000) && !unlink(path));
    int replacement = open(path, O_CREAT | O_EXCL | O_RDWR, 0600);
    assert(replacement >= 0 && !ftruncate(replacement, 0x4000));
    assert(!actual_file(region, out) && out.name == "untouched");
    close(replacement);
    close(fd);
    assert(!unlink(path));
    std::puts("CACHE_MAP_RESOLVER_PASS original_offsets=1 identity_guard=1 stale_file=1 holes=1 "
              "unexpected_regions_rejected=1 output_atomic=1");
}
