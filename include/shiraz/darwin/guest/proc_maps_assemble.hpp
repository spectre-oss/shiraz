// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/mapping_reconcile.hpp"
#include "shiraz/darwin/guest/mapped_file_identity.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"
#include <functional>
#include <sys/types.h>

namespace shiraz::guest {
enum class ProcMapsAssemblyStatus { Complete, Incomplete, Stale, Invalid, Limit, OutOfMemory };
struct ProcMapsAssembly {
    ProcMapsAssemblyStatus status = ProcMapsAssemblyStatus::Invalid;
    std::string text;
    size_t owned_pieces = 0, unresolved_pieces = 0;
};
// File/anonymous attribution requires actual backing evidence; a missing path
// does not prove anonymity. Unowned host ranges may instead use explicit host
// labels, preserving actual ranges/access without claiming a guest file identity
// or original native mmap offset. Host sharing is a Darwin projection where
// Linux's original mapping flags cannot be recovered. Guest-owned pieces never
// use this fallback, including when their file identity cannot be established.
using HostMapResolver = std::function<bool(const ProcessRegion&, ProcMapRecord&)>;
struct LiveOwnedFileResolver {
    MappedFileIdentity operator()(const ProcessRegion& region, const MemoryMapRecord& owner) const {
        return mapped_file_identity(region.start, region.end, owner.device, owner.inode);
    }
};

// Connect provenance, live vnode identity and formatting without emitting any
// partial view. Inputs are snapshots, not a process-wide atomicity guarantee.
// No default host resolver is supplied: unresolved host/cache ranges must be
// classified before this can produce bytes for a maps descriptor.
template <typename FileResolver = LiveOwnedFileResolver>
ProcMapsAssembly
assemble_proc_maps(const RegionSnapshot& host, const std::vector<MemoryMapRecord>& owned,
                   HostMapResolver resolve_host = {}, FileResolver resolve_file = {}) {
    auto reconciled = reconcile_mappings(host, owned);
    switch (reconciled.status) {
    case MappingReconcileStatus::Stale:
        return {ProcMapsAssemblyStatus::Stale, {}, 0, 0};
    case MappingReconcileStatus::Limit:
        return {ProcMapsAssemblyStatus::Limit, {}, 0, 0};
    case MappingReconcileStatus::OutOfMemory:
        return {ProcMapsAssemblyStatus::OutOfMemory, {}, 0, 0};
    case MappingReconcileStatus::Invalid:
        return {};
    case MappingReconcileStatus::Ok:
        break;
    }
    if (reconciled.mappings.empty())
        return {};
    ProcMapsAssembly result;
    try {
        std::vector<ProcMapRecord> records;
        records.reserve(reconciled.mappings.size());
        for (const auto& piece : reconciled.mappings) {
            const auto& region = piece.region;
            if (region.protection & ~(VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE))
                return {};
            ProcMapRecord record;
            record.start = region.start;
            record.end = region.end;
            record.protection = (region.protection & VM_PROT_READ ? 1 : 0) |
                                (region.protection & VM_PROT_WRITE ? 2 : 0) |
                                (region.protection & VM_PROT_EXECUTE ? 4 : 0);
            if (piece.owner_index == no_mapping_owner) {
                auto expected_protection = record.protection;
                if (!resolve_host || !resolve_host(region, record)) {
                    ++result.unresolved_pieces;
                    continue;
                }
                if (record.start != region.start || record.end != region.end ||
                    record.protection != expected_protection)
                    return {};
            } else {
                ++result.owned_pieces;
                const auto& owner = owned[piece.owner_index];
                int kind = owner.flags & (MAP_SHARED | MAP_PRIVATE);
                if (kind != MAP_SHARED && kind != MAP_PRIVATE)
                    return {};
                record.shared = kind == MAP_SHARED;
                if (!(owner.flags & MAP_ANON)) {
                    auto file = resolve_file(region, owner);
                    if (file.status == MappedFileStatus::Mismatch) {
                        result.status = ProcMapsAssemblyStatus::Stale;
                        return result;
                    }
                    if (file.status == MappedFileStatus::OutOfMemory)
                        return {ProcMapsAssemblyStatus::OutOfMemory, {}, 0, 0};
                    if (file.status != MappedFileStatus::Verified ||
                        file.path_status == MappedPathStatus::Unresolved) {
                        ++result.unresolved_pieces;
                        continue;
                    }
                    if (file.device != owner.device || file.inode != owner.inode ||
                        file.path.empty() || file.path[0] != '/')
                        return {};
                    record.name_kind = ProcMapName::File;
                    record.name = file.path;
                    if (file.path_status == MappedPathStatus::Deleted)
                        record.name += " (deleted)";
                    record.offset = piece.file_offset;
                    record.inode = file.inode;
                    record.device_major = major(dev_t(file.device));
                    record.device_minor = minor(dev_t(file.device));
                }
            }
            records.push_back(std::move(record));
        }
        if (result.unresolved_pieces) {
            result.status = ProcMapsAssemblyStatus::Incomplete;
            return result;
        }
        auto formatted = format_proc_maps(records);
        if (formatted.status == ProcMapsFormatStatus::Limit)
            result.status = ProcMapsAssemblyStatus::Limit;
        else if (formatted.status == ProcMapsFormatStatus::OutOfMemory)
            result.status = ProcMapsAssemblyStatus::OutOfMemory;
        else if (formatted.status == ProcMapsFormatStatus::Ok) {
            result.status = ProcMapsAssemblyStatus::Complete;
            result.text = std::move(formatted.text);
        }
        return result;
    } catch (const std::bad_alloc&) {
        return {ProcMapsAssemblyStatus::OutOfMemory, {}, 0, 0};
    }
}
} // namespace shiraz::guest
