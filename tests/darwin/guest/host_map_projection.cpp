// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/host_map_projection.hpp"
#include "shiraz/darwin/guest/proc_maps_assemble.hpp"
#include <cassert>
#include <cstdio>
#include <sys/mman.h>
#include <unistd.h>
using namespace shiraz::guest;
int main() {
    ProcessRegion r{0x4000, 0x8000, 0x9000, VM_PROT_READ, 7, SM_COW, true, VM_INHERIT_COPY};
    ProcMapRecord out;
    assert(project_host_mapping(r, out) && !out.shared && out.protection == 1);
    assert(out.name == "[shiraz:host-external]" && !out.inode && !out.offset && !out.device_major &&
           !out.device_minor);
    for (auto mode : {SM_PRIVATE, SM_COW, SM_EMPTY, SM_PRIVATE_ALIASED, SM_SHARED, SM_TRUESHARED,
                      SM_SHARED_ALIASED}) {
        r.share_mode = mode;
        for (auto inherit : {VM_INHERIT_COPY, VM_INHERIT_SHARE, VM_INHERIT_NONE}) {
            r.inheritance = inherit;
            assert(project_host_mapping(r, out));
            assert(out.shared ==
                   (inherit == VM_INHERIT_SHARE ||
                    (inherit == VM_INHERIT_NONE &&
                     (mode == SM_SHARED || mode == SM_TRUESHARED || mode == SM_SHARED_ALIASED))));
            assert(format_proc_maps({out}).status == ProcMapsFormatStatus::Ok);
        }
    }
    out.name = "untouched";
    r.share_mode = 255;
    assert(!project_host_mapping(r, out) && out.name == "untouched");
    r.share_mode = SM_SHARED;
    r.inheritance = -1;
    assert(!project_host_mapping(r, out));
    r.inheritance = VM_INHERIT_NONE;
    r.maximum_protection = 0;
    assert(!project_host_mapping(r, out));
    r.maximum_protection = 7;
    r.protection = 0x1000;
    assert(!project_host_mapping(r, out));
    r.protection = VM_PROT_READ;
    RegionSnapshot host{RegionSnapshotStatus::Complete, {r}};
    MemoryMapRecord owned{
        uintptr_t(r.start), uintptr_t(r.end), VM_PROT_READ, MAP_PRIVATE, 1, 0, 1, 2, "/guest"};
    unsigned fallback = 0;
    auto result = assemble_proc_maps(
        host, {owned},
        [&](const auto& region, auto& record) {
            ++fallback;
            return project_host_mapping(region, record);
        },
        [](const auto&, const auto&) { return MappedFileIdentity{}; });
    assert(!fallback && result.status == ProcMapsAssemblyStatus::Incomplete && result.text.empty());
    size_t page = getpagesize();
    void* mapped = mmap(nullptr, page, PROT_READ | PROT_WRITE, MAP_ANON | MAP_SHARED, -1, 0);
    assert(mapped != MAP_FAILED);
    *static_cast<char*>(mapped) = 42;
    uintptr_t address = reinterpret_cast<uintptr_t>(mapped);
    assert(mach_vm_inherit(mach_task_self(), address, page, VM_INHERIT_NONE) == KERN_SUCCESS);
    auto actual = snapshot_process_regions(CurrentProcessRegionQuery{});
    assert(actual.status == RegionSnapshotStatus::Complete);
    bool found = false;
    for (const auto& region : actual.regions)
        if (region.start <= address && address < region.end) {
            assert(project_host_mapping(region, out) && out.name == "[shiraz:host-memory]" &&
                   out.protection == 3);
            found = true;
        }
    assert(found && *static_cast<char*>(mapped) == 42);
    assert(!munmap(mapped, page));
    std::puts("HOST_MAP_PROJECTION_PASS explicit_labels=1 access_preserved=1 "
              "guest_file_not_masked=1 none_inheritance=1");
}
