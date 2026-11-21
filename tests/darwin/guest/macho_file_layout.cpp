// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/macho_file_layout.hpp"
#include <cassert>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <mach-o/dyld.h>
using namespace shiraz::guest;
namespace {
template <class T> void put(std::vector<uint8_t>& b, size_t off, const T& value) {
    assert(off <= b.size() && sizeof(value) <= b.size() - off);
    std::memcpy(b.data() + off, &value, sizeof(value));
}
void big(std::vector<uint8_t>& b, size_t off, uint32_t n) {
    for (unsigned i = 0; i < 4; ++i)
        b[off + i] = uint8_t(n >> (24 - 8 * i));
}
constexpr uint32_t cpu = CPU_TYPE_ARM64, subtype = 0x80000002;
std::array<uint8_t, 16> id{1, 2, 3};
std::vector<uint8_t> thin() {
    std::vector<uint8_t> b(4096);
    mach_header_64 h{};
    h.magic = MH_MAGIC_64;
    h.cputype = cpu;
    h.cpusubtype = subtype;
    h.filetype = MH_DYLINKER;
    h.ncmds = 3;
    h.sizeofcmds = sizeof(uuid_command) + 2 * sizeof(segment_command_64);
    put(b, 0, h);
    uuid_command u{};
    u.cmd = LC_UUID;
    u.cmdsize = sizeof(u);
    std::memcpy(u.uuid, id.data(), 16);
    put(b, sizeof(h), u);
    segment_command_64 s{};
    s.cmd = LC_SEGMENT_64;
    s.cmdsize = sizeof(s);
    s.vmaddr = 0x1000;
    s.vmsize = 4096;
    s.filesize = 300;
    put(b, sizeof(h) + sizeof(u), s);
    s.vmaddr = 0x2000;
    s.filesize = 0;
    put(b, sizeof(h) + sizeof(u) + sizeof(s), s);
    return b;
}
bool parse(const std::vector<uint8_t>& b, MachoFileLayout& out) {
    return parse_macho_file_layout(b.data(), b.size(), cpu, subtype, id.data(), 4096, out);
}
} // namespace
int main() {
    auto b = thin();
    MachoFileLayout out;
    assert(parse(b, out) && !out.slice_offset && out.segments.size() == 2);
    assert(out.segments[0].mapped_file_size == 4096 && !out.segments[1].mapped_file_size);
    auto bad = b;
    bad[sizeof(mach_header_64) + 8] ^= 1;
    out.slice_offset = 123;
    assert(!parse(bad, out) && out.slice_offset == 123);
    auto reject = [&](const std::vector<uint8_t>& input) {
        assert(!parse(input, out) && out.slice_offset == 123);
    };
    bad = b;
    big(bad, 0, 0);
    reject(bad);
    bad = b;
    uint32_t huge = UINT32_MAX;
    put(bad, offsetof(mach_header_64, sizeofcmds), huge);
    reject(bad);
    bad = b;
    uint64_t huge64 = UINT64_MAX;
    const size_t segment = sizeof(mach_header_64) + sizeof(uuid_command);
    put(bad, segment + offsetof(segment_command_64, fileoff), huge64);
    reject(bad);
    bad = b;
    uint64_t overlap = 0x1000;
    put(bad, segment + sizeof(segment_command_64) + offsetof(segment_command_64, vmaddr), overlap);
    reject(bad);
    bad = b;
    put(bad, segment + offsetof(segment_command_64, nsects), huge);
    reject(bad);
    bad = b;
    put(bad, segment + offsetof(segment_command_64, vmaddr), huge64);
    reject(bad);
    bad = b;
    uint64_t short_vm = 300;
    put(bad, segment + offsetof(segment_command_64, vmsize), short_vm);
    reject(bad);
    bad = b;
    uint32_t duplicate = LC_UUID;
    put(bad, segment, duplicate);
    reject(bad);
    for (size_t n = 0; n < segment + 2 * sizeof(segment_command_64); ++n)
        reject(std::vector<uint8_t>(b.begin(), b.begin() + n));
    assert(!parse_macho_file_layout(b.data(), b.size(), cpu, subtype, id.data(), 3, out));
    for (bool wide : {false, true}) {
        std::vector<uint8_t> fat(8192);
        big(fat, 0, wide ? FAT_MAGIC_64 : FAT_MAGIC);
        big(fat, 4, 1);
        big(fat, 8, cpu);
        big(fat, 12, subtype);
        if (wide) {
            big(fat, 20, 4096);
            big(fat, 28, 4096);
            big(fat, 32, 12);
        } else {
            big(fat, 16, 4096);
            big(fat, 20, 4096);
            big(fat, 24, 12);
        }
        std::copy(b.begin(), b.end(), fat.begin() + 4096);
        assert(parse(fat, out) && out.slice_offset == 4096 && out.segments[0].file_offset == 4096);
        out.slice_offset = 123;
        auto wrong = fat;
        big(wrong, 12, subtype ^ 0x80000000);
        reject(wrong);
        wrong = fat;
        big(wrong, wide ? 20 : 16, 4097);
        reject(wrong);
        wrong = fat;
        big(wrong, 4, 2);
        std::copy_n(fat.begin() + 8, wide ? 32 : 20, wrong.begin() + (wide ? 40 : 28));
        reject(wrong);
        if (wide) {
            wrong = fat;
            big(wrong, 36, 1);
            reject(wrong);
        }
    }
    // Actual host dyld: independently read the standard FAT32 table and UUID;
    // verify absolute file offsets include each slice, including arm64e variants.
    std::ifstream file("/usr/lib/dyld", std::ios::binary);
    std::vector<uint8_t> actual((std::istreambuf_iterator<char>(file)), {});
    assert(actual.size() > 8);
    auto be = [&](size_t p) {
        assert(p + 4 <= actual.size());
        return uint32_t(actual[p]) << 24 | uint32_t(actual[p + 1]) << 16 |
               uint32_t(actual[p + 2]) << 8 | actual[p + 3];
    };
    assert(be(0) == FAT_MAGIC && be(4) <= 64);
    unsigned checked = 0;
    for (unsigned i = 0; i < be(4); ++i) {
        uint64_t off = be(8 + i * 20 + 8);
        assert(off + sizeof(mach_header_64) <= actual.size());
        mach_header_64 h;
        std::memcpy(&h, actual.data() + off, sizeof(h));
        size_t pos = off + sizeof(h);
        std::array<uint8_t, 16> found{};
        for (unsigned j = 0; j < h.ncmds; ++j) {
            load_command c;
            assert(pos + sizeof(c) <= actual.size());
            std::memcpy(&c, actual.data() + pos, sizeof(c));
            assert(c.cmdsize >= sizeof(c) && c.cmdsize <= actual.size() - pos);
            if (c.cmd == LC_UUID) {
                assert(c.cmdsize == sizeof(uuid_command));
                std::memcpy(found.data(), actual.data() + pos + 8, 16);
            }
            pos += c.cmdsize;
        }
        assert(parse_macho_file_layout(actual.data(), actual.size(), uint32_t(h.cputype),
                                       uint32_t(h.cpusubtype), found.data(),
                                       h.cputype == CPU_TYPE_X86_64 ? 4096 : 16384, out));
        assert(out.slice_offset == off);
        for (const auto& s : out.segments)
            assert(s.file_offset >= off);
        ++checked;
    }
    const auto* self = reinterpret_cast<const mach_header_64*>(_dyld_get_image_header(0));
    assert(self && self->magic == MH_MAGIC_64);
    const uint8_t* commands = reinterpret_cast<const uint8_t*>(self + 1);
    std::array<uint8_t, 16> self_uuid{};
    for (unsigned i = 0; i < self->ncmds; ++i) {
        load_command c;
        std::memcpy(&c, commands, sizeof(c));
        if (c.cmd == LC_UUID)
            std::memcpy(self_uuid.data(), commands + 8, 16);
        commands += c.cmdsize;
    }
    std::ifstream own_file(_dyld_get_image_name(0), std::ios::binary);
    std::vector<uint8_t> own_bytes((std::istreambuf_iterator<char>(own_file)), {});
    assert(parse_macho_file_layout(own_bytes.data(), own_bytes.size(), uint32_t(self->cputype),
                                   uint32_t(self->cpusubtype), self_uuid.data(),
                                   self->cputype == CPU_TYPE_X86_64 ? 4096 : 16384, out));
    assert(!out.slice_offset);
    std::printf(
        "MACHO_FILE_LAYOUT_PASS thin=1 fat32=1 fat64=1 bounded_rejection=1 host_dyld_slices=%u\n",
        checked);
}
