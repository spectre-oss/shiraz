// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <mach-o/fat.h>
#include <mach-o/loader.h>
#include <algorithm>
#include <array>
#include <cstring>
#include <vector>

namespace shiraz::guest {
struct MachoFileSegment {
    uint64_t vmaddr, vmsize, file_offset, file_size, mapped_file_size;
};
struct MachoFileLayout {
    uint64_t slice_offset = 0;
    std::array<uint8_t, 16> uuid{};
    std::vector<MachoFileSegment> segments;
};

// Metadata only: the caller must pin file identity and verify this UUID against
// the current loaded image before using these offsets as mapping provenance.
// Supports little-endian 64-bit images and standard big-endian FAT/FAT64 wrappers.
// Output remains untouched on rejection. Page rounding describes the file tail,
// never the zero-fill remainder of a segment.
inline bool parse_macho_file_layout(const uint8_t* bytes, size_t size, uint32_t cpu,
                                    uint32_t subtype, const uint8_t* uuid, uint64_t page,
                                    MachoFileLayout& output) {
    if (!bytes || !uuid || size < sizeof(mach_header_64) || size > (64u << 20) || !page ||
        (page & (page - 1)) || page > (1u << 20))
        return false;
    auto be32 = [](const uint8_t* p) {
        return uint32_t(p[0]) << 24 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 8 | p[3];
    };
    auto be64 = [&](const uint8_t* p) { return uint64_t(be32(p)) << 32 | be32(p + 4); };
    uint64_t slice = 0, slice_size = size;
    uint32_t magic = be32(bytes);
    if (magic == FAT_MAGIC || magic == FAT_MAGIC_64) {
        uint32_t count = be32(bytes + 4);
        size_t width = magic == FAT_MAGIC ? 20 : 32;
        if (!count || count > 64 || count > (size - 8) / width)
            return false;
        const uint64_t table_end = 8 + count * width;
        std::vector<std::pair<uint64_t, uint64_t>> extents;
        unsigned matches = 0;
        for (uint32_t i = 0; i < count; ++i) {
            auto p = bytes + 8 + i * width;
            uint64_t off = width == 20 ? be32(p + 8) : be64(p + 8);
            uint64_t len = width == 20 ? be32(p + 12) : be64(p + 16);
            uint32_t align = be32(p + (width == 20 ? 16 : 24));
            if (align > 31 || (off & ((uint64_t(1) << align) - 1)) || off < table_end ||
                off > size || len < sizeof(mach_header_64) || len > size - off ||
                (width == 32 && be32(p + 28)))
                return false;
            extents.emplace_back(off, off + len);
            if (be32(p) == cpu && be32(p + 4) == subtype) {
                ++matches;
                slice = off;
                slice_size = len;
            }
        }
        std::sort(extents.begin(), extents.end());
        for (size_t i = 1; i < extents.size(); ++i)
            if (extents[i].first < extents[i - 1].second)
                return false;
        if (matches != 1)
            return false;
    }
    mach_header_64 header;
    std::memcpy(&header, bytes + slice, sizeof(header));
    if (header.magic != MH_MAGIC_64 || uint32_t(header.cputype) != cpu ||
        uint32_t(header.cpusubtype) != subtype ||
        (header.filetype != MH_EXECUTE && header.filetype != MH_DYLIB &&
         header.filetype != MH_DYLINKER && header.filetype != MH_BUNDLE) ||
        !header.ncmds || header.ncmds > 4096 || header.sizeofcmds > (1u << 20) ||
        header.sizeofcmds > slice_size - sizeof(header))
        return false;
    MachoFileLayout result;
    result.slice_offset = slice;
    size_t cursor = sizeof(header), end = cursor + header.sizeofcmds;
    unsigned uuids = 0;
    for (uint32_t i = 0; i < header.ncmds; ++i) {
        if (end - cursor < sizeof(load_command))
            return false;
        load_command command;
        std::memcpy(&command, bytes + slice + cursor, sizeof(command));
        if (command.cmdsize < sizeof(command) || command.cmdsize % 8 ||
            command.cmdsize > end - cursor)
            return false;
        if (command.cmd == LC_UUID) {
            if (command.cmdsize != sizeof(uuid_command) || ++uuids != 1)
                return false;
            std::memcpy(result.uuid.data(), bytes + slice + cursor + sizeof(command), 16);
        } else if (command.cmd == LC_SEGMENT_64) {
            if (command.cmdsize < sizeof(segment_command_64))
                return false;
            segment_command_64 seg;
            std::memcpy(&seg, bytes + slice + cursor, sizeof(seg));
            if (seg.nsects > (command.cmdsize - sizeof(seg)) / sizeof(section_64) ||
                sizeof(seg) + uint64_t(seg.nsects) * sizeof(section_64) != command.cmdsize ||
                seg.vmsize > UINT64_MAX - seg.vmaddr || seg.fileoff > slice_size ||
                seg.filesize > slice_size - seg.fileoff || seg.filesize > seg.vmsize ||
                seg.filesize > UINT64_MAX - (page - 1))
                return false;
            uint64_t rounded = (seg.filesize + page - 1) & ~(page - 1);
            if (rounded > seg.vmsize)
                return false;
            if (seg.vmsize)
                result.segments.push_back(
                    {seg.vmaddr, seg.vmsize, slice + seg.fileoff, seg.filesize, rounded});
        }
        cursor += command.cmdsize;
    }
    if (cursor != end || uuids != 1 || std::memcmp(uuid, result.uuid.data(), 16) ||
        result.segments.empty())
        return false;
    std::sort(result.segments.begin(), result.segments.end(),
              [](const auto& a, const auto& b) { return a.vmaddr < b.vmaddr; });
    for (size_t i = 1; i < result.segments.size(); ++i)
        if (result.segments[i].vmaddr <
            result.segments[i - 1].vmaddr + result.segments[i - 1].vmsize)
            return false;
    output = std::move(result);
    return true;
}
} // namespace shiraz::guest
