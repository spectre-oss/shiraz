// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/process_regions.hpp"
#include <cassert>
#include <cstdio>
#include <sys/mman.h>
#include <unistd.h>

using namespace shiraz::guest;
struct FixtureQuery {
    int step = 0;
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t& depth,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        info = {};
        if (step++ == 0) {
            address = 0;
            size = 8192;
            info.is_submap = true;
            return KERN_SUCCESS;
        }
        if (step == 2) {
            assert(depth == 1);
            address = 4096;
            size = 4096;
            info.offset = 64;
            info.protection = VM_PROT_READ;
            return KERN_SUCCESS;
        }
        if (step == 3) {
            depth = 0;
            address = 12288;
            size = 4096;
            info.offset = 128;
            info.protection = VM_PROT_WRITE;
            return KERN_SUCCESS;
        }
        return KERN_INVALID_ADDRESS;
    }
};
struct InvalidQuery {
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        address = UINT64_MAX - 1;
        size = 4;
        info = {};
        return KERN_SUCCESS;
    }
};
struct FailureAfterOne {
    int step = 0;
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        if (step++)
            return KERN_FAILURE;
        address = 4096;
        size = 4096;
        info = {};
        return KERN_SUCCESS;
    }
};
struct OverlapQuery {
    int step = 0;
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        info = {};
        if (step == 0) {
            ++step;
            address = 100;
            size = 200;
            return KERN_SUCCESS;
        }
        if (step == 1) {
            ++step;
            address = 250;
            size = 100;
            info.offset = 1000;
            return KERN_SUCCESS;
        }
        return KERN_INVALID_ADDRESS;
    }
};
struct WrongCount {
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t& count) {
        address = 0;
        size = 4096;
        info = {};
        count = 0;
        return KERN_SUCCESS;
    }
};
struct ExcessDepth {
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        address = 0;
        size = 4096;
        info = {};
        info.is_submap = true;
        return KERN_SUCCESS;
    }
};
// A later cursor can land inside a submap whose reported start is earlier.
// The recursive query must continue at the cursor, not at the submap start.
struct InteriorSubmap {
    int step = 0;
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t& depth,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        info = {};
        if (step++ == 0) {
            address = 0;
            size = 4096;
            return KERN_SUCCESS;
        }
        if (step == 2) {
            if (address != 4096)
                return KERN_FAILURE;
            address = 0;
            size = 12288;
            info.is_submap = true;
            return KERN_SUCCESS;
        }
        if (step == 3) {
            if (depth != 1 || address != 4096)
                return KERN_FAILURE;
            address = 4096;
            size = 4096;
            return KERN_SUCCESS;
        }
        return KERN_INVALID_ADDRESS;
    }
};
struct StaleSubmap {
    int step = 0;
    kern_return_t operator()(mach_vm_address_t& address, mach_vm_size_t& size, natural_t&,
                             vm_region_submap_info_data_64_t& info, mach_msg_type_number_t&) {
        info = {};
        if (!step++) {
            address = 0;
            size = 4096;
            return KERN_SUCCESS;
        }
        address = 0;
        size = 2048;
        info.is_submap = true;
        return KERN_SUCCESS;
    }
};
static const ProcessRegion* cover(const RegionSnapshot& snapshot, uintptr_t address) {
    for (const auto& region : snapshot.regions)
        if (address >= region.start && address < region.end)
            return &region;
    return nullptr;
}
int main() {
    auto expected = snapshot_process_regions(FixtureQuery{}, 8);
    assert(expected.status == RegionSnapshotStatus::Complete && expected.regions.size() == 2);
    assert(expected.regions[0].start == 4096 && expected.regions[0].end == 8192 &&
           expected.regions[0].object_offset == 64 &&
           expected.regions[0].protection == VM_PROT_READ);
    assert(expected.regions[1].start == 12288 && expected.regions[1].end == 16384);
    auto limited = snapshot_process_regions(FixtureQuery{}, 1);
    assert(limited.status == RegionSnapshotStatus::Limit && limited.regions.empty());
    auto malformed = snapshot_process_regions(InvalidQuery{});
    assert(malformed.status == RegionSnapshotStatus::Malformed && malformed.regions.empty());
    auto failed = snapshot_process_regions(FailureAfterOne{});
    assert(failed.status == RegionSnapshotStatus::KernelError && failed.regions.empty());
    auto overlap = snapshot_process_regions(OverlapQuery{});
    assert(overlap.status == RegionSnapshotStatus::Complete && overlap.regions.size() == 2 &&
           overlap.regions[1].start == 300 && overlap.regions[1].end == 350 &&
           overlap.regions[1].object_offset == 1050);
    auto short_reply = snapshot_process_regions(WrongCount{});
    assert(short_reply.status == RegionSnapshotStatus::Malformed && short_reply.regions.empty());
    auto too_deep = snapshot_process_regions(ExcessDepth{});
    assert(too_deep.status == RegionSnapshotStatus::Malformed && too_deep.regions.empty());
    auto interior = snapshot_process_regions(InteriorSubmap{});
    assert(interior.status == RegionSnapshotStatus::Complete && interior.regions.size() == 2 &&
           interior.regions[1].start == 4096 && interior.regions[1].end == 8192);
    auto stale = snapshot_process_regions(StaleSubmap{});
    assert(stale.status == RegionSnapshotStatus::Malformed && stale.regions.empty());

    long page = sysconf(_SC_PAGESIZE);
    assert(page > 0);
    void* memory =
        mmap(nullptr, size_t(page) * 3, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
    assert(memory != MAP_FAILED);
    assert(!mprotect(static_cast<char*>(memory) + page, size_t(page), PROT_NONE));
    assert(!mprotect(static_cast<char*>(memory) + 2 * page, size_t(page), PROT_READ));
    void* shared =
        mmap(nullptr, size_t(page), PROT_READ | PROT_WRITE, MAP_SHARED | MAP_ANON, -1, 0);
    assert(shared != MAP_FAILED);
    auto live = snapshot_process_regions(CurrentProcessRegionQuery{});
    assert(live.status == RegionSnapshotStatus::Complete && !live.regions.empty());
    auto* first = cover(live, reinterpret_cast<uintptr_t>(memory));
    auto* middle = cover(live, reinterpret_cast<uintptr_t>(memory) + page);
    auto* last = cover(live, reinterpret_cast<uintptr_t>(memory) + 2 * page);
    assert(first && middle && last &&
           (first->protection & (VM_PROT_READ | VM_PROT_WRITE)) == (VM_PROT_READ | VM_PROT_WRITE));
    assert(!(middle->protection & (VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE)));
    assert((last->protection & (VM_PROT_READ | VM_PROT_WRITE)) == VM_PROT_READ);
    auto* shared_region = cover(live, reinterpret_cast<uintptr_t>(shared));
    assert(first->inheritance == VM_INHERIT_COPY && middle->inheritance == VM_INHERIT_COPY &&
           last->inheritance == VM_INHERIT_COPY);
    assert(shared_region && shared_region->inheritance == VM_INHERIT_SHARE);
    assert(!munmap(shared, size_t(page)));
    for (size_t i = 1; i < live.regions.size(); ++i)
        assert(live.regions[i - 1].end <= live.regions[i].start);
    assert(!munmap(memory, size_t(page) * 3));
    std::puts("DARWIN_PROCESS_REGIONS_PASS raw_only=1 bounds=1 failure_closed=1 protections=1");
}
