// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/internal_map_resolver.hpp"
#include <cassert>
#include <cstdio>
#include <sys/mman.h>
#include <unistd.h>
using namespace shiraz::guest;
int main() {
    ProcessRegion region{0x1000, 0x2000, 0, 3, 7, SM_PRIVATE, false, VM_INHERIT_COPY};
    ProcMapRecord out;
    assert(resolve_internal_mapping(region, out) && !out.shared && out.protection == 3 &&
           out.name.empty() && !out.inode && !out.offset);
    region.external_pager = true;
    out.name = "untouched";
    assert(!resolve_internal_mapping(region, out) && out.name == "untouched");
    region.external_pager = false;
    region.inheritance = VM_INHERIT_NONE;
    assert(!resolve_internal_mapping(region, out));
    region.inheritance = VM_INHERIT_COPY;
    region.share_mode = SM_TRUESHARED;
    assert(!resolve_internal_mapping(region, out));
    region.protection = 0;
    assert(resolve_internal_mapping(region, out) && !out.shared && !out.protection);
    region.protection = 3;
    region.inheritance = VM_INHERIT_SHARE;
    assert(resolve_internal_mapping(region, out) && out.shared);
    region.share_mode = SM_COW;
    assert(!resolve_internal_mapping(region, out));
    region.share_mode = SM_PRIVATE;
    region.protection = 0x4000;
    assert(!resolve_internal_mapping(region, out));
    size_t page = getpagesize();
    for (int mode : {MAP_PRIVATE, MAP_SHARED}) {
        void* memory = mmap(nullptr, page * 2, PROT_READ | PROT_WRITE, mode | MAP_ANON, -1, 0);
        assert(memory != MAP_FAILED);
        *static_cast<char*>(memory) = 'x';
        assert(!mprotect(static_cast<char*>(memory) + page, page, PROT_NONE));
        auto snapshot = snapshot_process_regions(CurrentProcessRegionQuery{});
        assert(snapshot.status == RegionSnapshotStatus::Complete);
        uint64_t base = reinterpret_cast<uintptr_t>(memory), covered = 0;
        for (const auto& r : snapshot.regions)
            if (r.start >= base && r.end <= base + page * 2) {
                bool resolved = resolve_internal_mapping(r, out);
                if (!resolved || out.shared != (mode == MAP_SHARED))
                    std::fprintf(stderr,
                                 "INTERNAL_CASE requested=%d mode=%u inheritance=%d external=%d "
                                 "protection=%d resolved=%d\n",
                                 mode, unsigned(r.share_mode), int(r.inheritance), r.external_pager,
                                 r.protection, resolved);
                assert(resolved && out.shared == (mode == MAP_SHARED));
                covered += r.end - r.start;
            }
        assert(covered == page * 2);
        assert(!munmap(memory, page * 2));
    }
    std::puts("INTERNAL_MAP_RESOLVER_PASS private=1 shared=1 guard=1 external_refused=1 "
              "unknown_refused=1");
}
