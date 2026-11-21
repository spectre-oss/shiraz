// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/mapping_reconcile.hpp"
#include <cassert>
#include <cstdio>

using namespace shiraz::guest;
static ProcessRegion region(uint64_t start, uint64_t end, int protection = 3) {
    return {start, end, 0, protection, 7, 0, false};
}
static MemoryMapRecord owner(uint64_t start, uint64_t end, uint64_t offset = 0) {
    return {start, end, 3, MAP_PRIVATE, 1, offset, 1, 2, "/historical"};
}
int main() {
    RegionSnapshot host{RegionSnapshotStatus::Complete, {region(100, 500)}};
    auto owned = std::vector<MemoryMapRecord>{owner(200, 300, 4096)};
    auto result = reconcile_mappings(host, owned);
    assert(result.status == MappingReconcileStatus::Ok && result.mappings.size() == 3);
    assert(result.mappings[0].owner_index == no_mapping_owner &&
           result.mappings[0].region.end == 200);
    assert(result.mappings[1].owner_index == 0 && result.mappings[1].file_offset == 4096);
    assert(result.mappings[2].owner_index == no_mapping_owner &&
           result.mappings[2].region.start == 300);
    host.regions = {region(100, 250), region(250, 500)};
    result = reconcile_mappings(host, owned);
    assert(result.status == MappingReconcileStatus::Ok && result.mappings.size() == 4);
    assert(result.mappings[2].file_offset == 4146);
    // Neither a missing page nor a protection change may produce a partial map.
    host.regions[1].start = 251;
    result = reconcile_mappings(host, owned);
    assert(result.status == MappingReconcileStatus::Stale && result.mappings.empty());
    host.regions[1].start = 250;
    host.regions[1].protection = 1;
    result = reconcile_mappings(host, owned);
    assert(result.status == MappingReconcileStatus::Stale && result.mappings.empty());
    host.regions[1].protection = 3;
    result = reconcile_mappings(host, owned, 3);
    assert(result.status == MappingReconcileStatus::Limit && result.mappings.empty());
    owned[0].file_offset = UINT64_MAX;
    assert(reconcile_mappings(host, owned).status == MappingReconcileStatus::Invalid);
    owned = {owner(200, 300), owner(250, 350)};
    assert(reconcile_mappings(host, owned).status == MappingReconcileStatus::Invalid);
    owned = {owner(200, 300)};
    owned[0].flags |= MAP_ANON;
    result = reconcile_mappings(host, owned);
    assert(result.status == MappingReconcileStatus::Ok);
    for (const auto& piece : result.mappings)
        assert(piece.file_offset == 0);
    host.status = RegionSnapshotStatus::KernelError;
    assert(reconcile_mappings(host, owned).status == MappingReconcileStatus::Invalid);
    // Exhaustive small-grid oracle: every mapped address appears exactly once,
    // and ownership follows its interval, including gaps between VM leaves.
    for (uint64_t a = 10; a < 20; ++a)
        for (uint64_t b = a + 1; b <= 20; ++b) {
            host = {RegionSnapshotStatus::Complete, {region(0, 5), region(10, 15), region(15, 25)}};
            owned = {owner(a, b, 1000)};
            result = reconcile_mappings(host, owned);
            assert(result.status == MappingReconcileStatus::Ok);
            for (uint64_t address = 0; address < 30; ++address) {
                unsigned count = 0;
                for (const auto& piece : result.mappings)
                    if (address >= piece.region.start && address < piece.region.end) {
                        ++count;
                        bool claimed = address >= a && address < b;
                        assert((piece.owner_index != no_mapping_owner) == claimed);
                        if (claimed)
                            assert(piece.file_offset + address - piece.region.start ==
                                   1000 + address - a);
                    }
                assert(count == unsigned(address < 5 || (address >= 10 && address < 25)));
            }
        }
    std::puts("MAPPING_RECONCILE_PASS coverage=1 split_offsets=1 stale_atomic=1 bounds=1");
}
