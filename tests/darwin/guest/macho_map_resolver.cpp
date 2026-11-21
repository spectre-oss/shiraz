// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/macho_map_resolver.hpp"
#include <mach-o/dyld.h>
#include <cassert>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <unistd.h>
using namespace shiraz::guest;
int main() {
    char path[] = "/private/tmp/shiraz-macho-map-XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0 && !ftruncate(fd, 5000));
    struct stat st{};
    assert(!fstat(fd, &st));
    MachoFileIdentity file{path, uint32_t(st.st_dev), uint64_t(st.st_ino), 5000};
    MachoFileLayout layout;
    layout.segments.push_back({0x1000, 0x2000, 4096, 904, 4096});
    bool current = true;
    MachoMapResolver resolve(layout, file, 0x2000, [&] { return current; });
    ProcessRegion r{0x3000, 0x4000, 4096, 1, 7, SM_COW, true, VM_INHERIT_COPY};
    ProcMapRecord out;
    assert(resolve(r, out) && out.offset == 4096 && out.inode == file.inode && out.name == path &&
           !out.shared);
    r.object_offset = 0;
    assert(resolve(r, out));
    out.name = "untouched";
    auto reject = [&] { assert(!resolve(r, out) && out.name == "untouched"); };
    current = false;
    reject();
    current = true;
    r.end = 0x5000;
    reject();
    r.end = 0x4000;
    r.object_offset = 8;
    reject();
    r.object_offset = 0;
    r.external_pager = false;
    reject();
    r.external_pager = true;
    r.inheritance = VM_INHERIT_SHARE;
    reject();
    r.inheritance = VM_INHERIT_COPY;
    assert(!ftruncate(fd, 4999));
    reject();
    assert(!ftruncate(fd, 5000));
    MachoMapResolver no_guard(layout, file, 0x2000, {});
    assert(!no_guard(r, out));
    MachoMapResolver negative(layout, file, -0x1000, [&] { return true; });
    auto low = r;
    low.start = 0;
    low.end = 4096;
    assert(negative(low, out) && out.offset == 4096);
    assert(!unlink(path));
    out.name = "untouched";
    reject();
    close(fd);

    // The main executable is process-lifetime pinned. Compare a safe copy of its
    // current load commands to the registration before each resolution.
    auto* header = reinterpret_cast<const mach_header_64*>(_dyld_get_image_header(0));
    assert(header && header->magic == MH_MAGIC_64 && header->sizeofcmds <= (1u << 20));
    std::vector<uint8_t> registered(sizeof(*header) + header->sizeofcmds);
    auto read_header = [&](std::vector<uint8_t>& bytes) {
        mach_vm_size_t copied = 0;
        return mach_vm_read_overwrite(mach_task_self(), reinterpret_cast<uintptr_t>(header),
                                      bytes.size(), reinterpret_cast<uintptr_t>(bytes.data()),
                                      &copied) == KERN_SUCCESS &&
               copied == bytes.size();
    };
    assert(read_header(registered));
    std::array<uint8_t, 16> uuid{};
    size_t cursor = sizeof(*header);
    for (unsigned i = 0; i < header->ncmds; ++i) {
        load_command c;
        assert(cursor + sizeof(c) <= registered.size());
        std::memcpy(&c, registered.data() + cursor, sizeof(c));
        assert(c.cmdsize >= sizeof(c) && c.cmdsize <= registered.size() - cursor);
        if (c.cmd == LC_UUID) {
            assert(c.cmdsize == sizeof(uuid_command));
            std::memcpy(uuid.data(), registered.data() + cursor + 8, 16);
        }
        cursor += c.cmdsize;
    }
    char absolute[4096];
    assert(realpath(_dyld_get_image_name(0), absolute));
    std::ifstream stream(absolute, std::ios::binary);
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(stream)), {});
    assert(parse_macho_file_layout(bytes.data(), bytes.size(), uint32_t(header->cputype),
                                   uint32_t(header->cpusubtype), uuid.data(), getpagesize(),
                                   layout));
    assert(!stat(absolute, &st));
    MachoFileIdentity actual{absolute, uint32_t(st.st_dev), uint64_t(st.st_ino),
                             uint64_t(st.st_size)};
    std::vector<uint8_t> check(registered.size());
    auto slide = _dyld_get_image_vmaddr_slide(0);
    MachoMapResolver self(layout, actual, slide, [&] {
        return _dyld_get_image_header(0) == reinterpret_cast<const mach_header*>(header) &&
               _dyld_get_image_vmaddr_slide(0) == slide && read_header(check) &&
               check == registered;
    });
    auto snapshot = snapshot_process_regions(CurrentProcessRegionQuery{});
    assert(snapshot.status == RegionSnapshotStatus::Complete);
    unsigned resolved = 0, rounded_tail = 0;
    for (const auto& region : snapshot.regions) {
        bool owned = false;
        for (const auto& s : layout.segments) {
            uint64_t base = s.vmaddr + slide;
            if (s.mapped_file_size && region.start >= base &&
                region.end <= base + s.mapped_file_size) {
                owned = true;
                rounded_tail += region.end > base + s.file_size;
            }
        }
        if (!owned || !region.external_pager)
            continue;
        assert(self(region, out) && out.name == absolute && out.inode == actual.inode);
        ++resolved;
    }
    assert(resolved >= 3 && rounded_tail);
    std::printf("MACHO_MAP_RESOLVER_PASS actual_self_regions=%u rounded_tail=%u rejection=1\n",
                resolved, rounded_tail);
}
