// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/process_regions.hpp"
#include <algorithm>
#include <sys/mman.h>

namespace shiraz::guest {
constexpr size_t no_mapping_owner = SIZE_MAX;
struct ReconciledMapping {
    ProcessRegion region;
    size_t owner_index = no_mapping_owner;
    uint64_t file_offset = 0;
};
enum class MappingReconcileStatus { Ok, Invalid, Stale, Limit, OutOfMemory };
struct MappingReconcileResult {
    MappingReconcileStatus status = MappingReconcileStatus::Invalid;
    std::vector<ReconciledMapping> mappings;
};

// Partition every VM leaf without dropping unowned host regions. An owner index
// refers to the supplied immutable owned snapshot. Unowned pieces still require
// backing classification; they must not be assumed anonymous. Captured file
// paths remain historical. This operation does not freeze the address space.
inline MappingReconcileResult reconcile_mappings(const RegionSnapshot& host,
                                                 const std::vector<MemoryMapRecord>& owned,
                                                 size_t limit = 65536) {
    if (!limit || limit > 65536 || host.regions.size() > limit || owned.size() > limit)
        return {MappingReconcileStatus::Limit, {}};
    if (host.status != RegionSnapshotStatus::Complete)
        return {MappingReconcileStatus::Invalid, {}};
    uint64_t end = 0;
    for (const auto& r : host.regions) {
        if (r.start >= r.end || r.start < end || r.end - r.start > UINT64_MAX - r.object_offset)
            return {MappingReconcileStatus::Invalid, {}};
        end = r.end;
    }
    end = 0;
    for (const auto& r : owned) {
        if (r.start >= r.end || r.start < end || !r.owner ||
            r.end - r.start > UINT64_MAX - r.file_offset || ((r.flags & MAP_ANON) && r.file_offset))
            return {MappingReconcileStatus::Invalid, {}};
        end = r.end;
    }
    try {
        std::vector<uint64_t> covered(owned.size(), 0);
        std::vector<ReconciledMapping> pending;
        pending.reserve(host.regions.size());
        size_t index = 0;
        for (const auto& r : host.regions) {
            uint64_t cursor = r.start;
            while (cursor < r.end) {
                while (index < owned.size() && owned[index].end <= cursor)
                    ++index;
                uint64_t stop = r.end, file_offset = 0;
                size_t owner = no_mapping_owner;
                if (index < owned.size() && owned[index].start < stop) {
                    const auto& o = owned[index];
                    if (o.start > cursor)
                        stop = o.start;
                    else {
                        stop = std::min(stop, uint64_t(o.end));
                        if (o.protection != r.protection)
                            return {MappingReconcileStatus::Stale, {}};
                        owner = index;
                        covered[index] += stop - cursor;
                        if (!(o.flags & MAP_ANON))
                            file_offset = o.file_offset + cursor - o.start;
                    }
                }
                if (pending.size() == limit)
                    return {MappingReconcileStatus::Limit, {}};
                auto piece = r;
                piece.start = cursor;
                piece.end = stop;
                piece.object_offset += cursor - r.start;
                pending.push_back({piece, owner, file_offset});
                cursor = stop;
            }
        }
        for (size_t i = 0; i < owned.size(); ++i)
            if (covered[i] != owned[i].end - owned[i].start)
                return {MappingReconcileStatus::Stale, {}};
        return {MappingReconcileStatus::Ok, std::move(pending)};
    } catch (const std::bad_alloc&) {
        return {MappingReconcileStatus::OutOfMemory, {}};
    }
}
} // namespace shiraz::guest
