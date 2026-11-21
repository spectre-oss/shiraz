// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/proc_maps_assemble.hpp"
#include "shiraz/darwin/guest/maps_stream.hpp"
#include <cassert>
#include <cstdio>
#include <fcntl.h>
using namespace shiraz::guest;
struct FixtureFile {
    MappedFileStatus status = MappedFileStatus::Verified;
    MappedPathStatus path_status = MappedPathStatus::Present;
    MappedFileIdentity operator()(const ProcessRegion&, const MemoryMapRecord&) const {
        return {status,
                1,
                7,
                uint16_t(path_status == MappedPathStatus::Deleted ? 0 : 1),
                "/fixture\nfile",
                path_status};
    }
};
int main() {
    RegionSnapshot host{RegionSnapshotStatus::Complete,
                        {{0x1000, 0x2000, 0, 3, 3, SM_PRIVATE, false},
                         {0x2000, 0x3000, 0, 1, 3, SM_COW, true},
                         {0x4000, 0x5000, 0, 1, 1, SM_PRIVATE, false}}};
    std::vector<MemoryMapRecord> owned{
        {0x1000, 0x2000, 3, MAP_ANON | MAP_PRIVATE, 1, 0, 0, 0, {}},
        {0x2000, 0x3000, 1, MAP_PRIVATE, 2, 0x6000, 1, 7, "/historical"}};
    auto incomplete = assemble_proc_maps(host, owned, {}, FixtureFile{});
    assert(incomplete.status == ProcMapsAssemblyStatus::Incomplete && incomplete.text.empty() &&
           incomplete.owned_pieces == 2 && incomplete.unresolved_pieces == 1);
    // This callback describes only the known synthetic fixture's third range.
    HostMapResolver fixture_host = [](const ProcessRegion&, ProcMapRecord&) { return true; };
    auto complete = assemble_proc_maps(host, owned, fixture_host, FixtureFile{});
    assert(complete.status == ProcMapsAssemblyStatus::Complete && complete.unresolved_pieces == 0);
    assert(complete.text.find("00006000") != std::string::npos &&
           complete.text.find("/fixture\\012file\n") != std::string::npos);
    auto deleted =
        assemble_proc_maps(host, owned, fixture_host,
                           FixtureFile{MappedFileStatus::Verified, MappedPathStatus::Deleted});
    assert(deleted.status == ProcMapsAssemblyStatus::Complete &&
           deleted.text.find("/fixture\\012file (deleted)\n") != std::string::npos);
    auto missing =
        assemble_proc_maps(host, owned, fixture_host,
                           FixtureFile{MappedFileStatus::Verified, MappedPathStatus::Unresolved});
    assert(missing.status == ProcMapsAssemblyStatus::Incomplete && missing.text.empty());
    auto stale =
        assemble_proc_maps(host, owned, fixture_host, FixtureFile{MappedFileStatus::Mismatch});
    assert(stale.status == ProcMapsAssemblyStatus::Stale && stale.text.empty());
    auto invalid = assemble_proc_maps(
        host, owned,
        [](const ProcessRegion&, ProcMapRecord& r) {
            ++r.end;
            return true;
        },
        FixtureFile{});
    assert(invalid.status == ProcMapsAssemblyStatus::Invalid && invalid.text.empty());
    assert(assemble_proc_maps(RegionSnapshot{RegionSnapshotStatus::Complete, {}}, {}).status ==
           ProcMapsAssemblyStatus::Invalid);
    MapsStream stream([&](std::string& out) {
        auto r = assemble_proc_maps(host, owned, fixture_host, FixtureFile{});
        if (r.status != ProcMapsAssemblyStatus::Complete)
            return EIO;
        out = std::move(r.text);
        return 0;
    });
    char buffer[1024];
    size_t n = 0;
    assert(!stream.read(buffer, sizeof(buffer), &n) && std::string(buffer, n) == complete.text);
    MapsStream refused([&](std::string& out) {
        auto r = assemble_proc_maps(host, owned, {}, FixtureFile{});
        if (r.status != ProcMapsAssemblyStatus::Complete)
            return ENOTSUP;
        out = std::move(r.text);
        return 0;
    });
    n = 99;
    buffer[0] = 'x';
    assert(refused.read(buffer, sizeof(buffer), &n) == ENOTSUP && n == 99 && buffer[0] == 'x');

    char path[] = "/private/tmp/shiraz-map-assembly-XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0);
    size_t page = getpagesize();
    assert(!ftruncate(fd, 3 * page));
    struct stat st{};
    assert(!fstat(fd, &st));
    void* memory = mmap(nullptr, page, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, page);
    assert(memory != MAP_FAILED);
    *static_cast<char*>(memory) = 'x';
    auto address = reinterpret_cast<uintptr_t>(memory);
    std::vector<MemoryMapRecord> actual_owned{
        {address, address + page, 3, MAP_PRIVATE, 1, page, uint32_t(st.st_dev), st.st_ino, path}};
    auto live = snapshot_process_regions(CurrentProcessRegionQuery{});
    unsigned verified = 0;
    auto actual = assemble_proc_maps(live, actual_owned, {},
                                     [&](const ProcessRegion& r, const MemoryMapRecord& o) {
                                         auto identity = LiveOwnedFileResolver{}(r, o);
                                         if (identity.status == MappedFileStatus::Verified &&
                                             identity.path_status == MappedPathStatus::Present)
                                             ++verified;
                                         return identity;
                                     });
    assert(actual.status == ProcMapsAssemblyStatus::Incomplete && actual.text.empty() && verified &&
           actual.unresolved_pieces);
    unsigned external = 0, internal_private = 0, other = 0;
    for (const auto& r : live.regions) {
        if (r.external_pager)
            ++external;
        else if (r.share_mode == SM_PRIVATE || r.share_mode == SM_COW || r.share_mode == SM_EMPTY)
            ++internal_private;
        else
            ++other;
    }
    printf("LIVE_MAPS_COVERAGE regions=%zu owned_verified=%u unresolved=%zu external=%u "
           "internal_private_candidates=%u other=%u\n",
           live.regions.size(), verified, actual.unresolved_pieces, external, internal_private,
           other);
    assert(!munmap(memory, page));
    close(fd);
    unlink(path);
    std::puts("PROC_MAPS_ASSEMBLY_PASS complete_fixture=1 incomplete_no_bytes=1 deleted=1 stale=1 "
              "stream=1 live_owned=1");
}
