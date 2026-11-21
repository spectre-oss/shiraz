// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/host_reserved_map.hpp"
#include "shiraz/darwin/guest/proc_maps_assemble.hpp"
#include <cassert>
#include <cstdio>
using namespace shiraz::guest;
int main() {
    ProcessRegion r{0x4000, 0x8000, 0x218000, 0, 0, SM_COW, true, VM_INHERIT_COPY};
    ProcMapRecord out;
    assert(resolve_host_reserved_mapping(r, out));
    assert(out.start == r.start && out.end == r.end && !out.protection && !out.shared);
    assert(out.name_kind == ProcMapName::KernelLabel && !out.inode && !out.offset);
    auto text = format_proc_maps({out});
    assert(text.status == ProcMapsFormatStatus::Ok && text.text.find("---p") != std::string::npos &&
           text.text.find("[shiraz:host-reserved]") != std::string::npos);
    out.name = "untouched";
    for (auto field : {&ProcessRegion::protection, &ProcessRegion::maximum_protection}) {
        for (int permission : {VM_PROT_READ, VM_PROT_WRITE, VM_PROT_EXECUTE}) {
            r.*field = permission;
            assert(!resolve_host_reserved_mapping(r, out) && out.name == "untouched");
        }
        r.*field = 0;
    }
    r.inheritance = VM_INHERIT_SHARE;
    assert(!resolve_host_reserved_mapping(r, out));
    r.inheritance = VM_INHERIT_COPY;
    r.end = r.start;
    assert(!resolve_host_reserved_mapping(r, out));
    r.end = 0x8000;
    RegionSnapshot host{RegionSnapshotStatus::Complete, {r}};
    MemoryMapRecord owner{uintptr_t(r.start), uintptr_t(r.end), 0, MAP_PRIVATE, 1, 0x218000, 1, 2,
                          "/guest/file"};
    unsigned host_calls = 0;
    auto assembled = assemble_proc_maps(
        host, {owner},
        [&](const auto& region, auto& record) {
            ++host_calls;
            return resolve_host_reserved_mapping(region, record);
        },
        [](const auto&, const auto&) { return MappedFileIdentity{}; });
    assert(!host_calls && assembled.status == ProcMapsAssemblyStatus::Incomplete &&
           assembled.text.empty());
    assembled = assemble_proc_maps(host, {}, resolve_host_reserved_mapping);
    assert(assembled.status == ProcMapsAssemblyStatus::Complete &&
           assembled.text.find("[shiraz:host-reserved]") != std::string::npos);
    std::puts("HOST_RESERVED_MAP_PASS explicit_label=1 inaccessible_only=1 range_preserved=1");
}
