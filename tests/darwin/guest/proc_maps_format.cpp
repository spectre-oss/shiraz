// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/proc_maps_format.hpp"

#include <cassert>
#include <cstdio>
#include <fstream>
#include <sstream>

using namespace shiraz::guest;

static ProcMapRecord anonymous(uint64_t start, uint64_t end) {
    ProcMapRecord value;
    value.start = start;
    value.end = end;
    value.protection = 1;
    return value;
}

// Compare independently collected Linux procfs bytes, not generated fixtures.
// The reference comes from a disposable Linux process with no user paths.
static void linux_reference(const char* path) {
    std::ifstream input(path, std::ios::binary);
    assert(input);
    std::vector<ProcMapRecord> records;
    std::string original, line;
    while (std::getline(input, line)) {
        original += line + '\n';
        std::istringstream fields(line);
        std::string range, permissions, offset, device, inode;
        assert(fields >> range >> permissions >> offset >> device >> inode);
        const auto dash = range.find('-'), colon = device.find(':');
        assert(dash != std::string::npos && colon != std::string::npos && permissions.size() == 4);
        ProcMapRecord record;
        record.start = std::stoull(range.substr(0, dash), nullptr, 16);
        record.end = std::stoull(range.substr(dash + 1), nullptr, 16);
        record.offset = std::stoull(offset, nullptr, 16);
        record.device_major = uint32_t(std::stoul(device.substr(0, colon), nullptr, 16));
        record.device_minor = uint32_t(std::stoul(device.substr(colon + 1), nullptr, 16));
        record.inode = std::stoull(inode);
        assert((permissions[0] == 'r' || permissions[0] == '-') &&
               (permissions[1] == 'w' || permissions[1] == '-') &&
               (permissions[2] == 'x' || permissions[2] == '-') &&
               (permissions[3] == 's' || permissions[3] == 'p'));
        record.protection =
            uint8_t((permissions[0] == 'r' ? 1 : 0) | (permissions[1] == 'w' ? 2 : 0) |
                    (permissions[2] == 'x' ? 4 : 0));
        record.shared = permissions[3] == 's';
        std::string name;
        std::getline(fields, name);
        const auto first = name.find_first_not_of(' ');
        if (first != std::string::npos) {
            record.name = name.substr(first);
            record.name_kind = record.name[0] == '[' ? ProcMapName::KernelLabel : ProcMapName::File;
            // This reference fixture deliberately has a newline and no literal
            // backslash-012 sequence; procfs makes those names ambiguous on read.
            if (record.name_kind == ProcMapName::File) {
                size_t pos = 0;
                while ((pos = record.name.find("\\012", pos)) != std::string::npos) {
                    record.name.replace(pos, 4, 1, '\n');
                    ++pos;
                }
            }
        }
        records.push_back(std::move(record));
    }
    assert(input.eof() && !records.empty());
    auto formatted = format_proc_maps(records);
    assert(formatted.status == ProcMapsFormatStatus::Ok && formatted.text == original);
    std::printf("PROC_MAPS_LINUX_REFERENCE_PASS records=%zu\n", records.size());
}

int main(int argc, char** argv) {
    auto a = anonymous(0x1000, 0x2000);
    auto result = format_proc_maps({a});
    assert(result.status == ProcMapsFormatStatus::Ok);
    assert(result.text == "00001000-00002000 r--p 00000000 00:00 0 \n");

    ProcMapRecord file = anonymous(0x100000000ull, 0x100004000ull);
    file.name_kind = ProcMapName::File;
    file.name = "/fixture/a\nb";
    file.offset = 0x4000;
    file.inode = 123;
    file.device_major = 8;
    file.device_minor = 1;
    file.protection = 5;
    result = format_proc_maps({file});
    assert(result.status == ProcMapsFormatStatus::Ok);
    assert(result.text.find("/fixture/a\\012b\n") == 73);
    file.name = "/fixture/a\\012b (deleted)";
    result = format_proc_maps({file});
    assert(result.status == ProcMapsFormatStatus::Ok);
    assert(result.text.find("/fixture/a\\012b (deleted)\n") == 73);

    ProcMapRecord tag = anonymous(0x2000, 0x3000);
    tag.name_kind = ProcMapName::KernelLabel;
    tag.name = "[stack]";
    assert(format_proc_maps({a, tag}).status == ProcMapsFormatStatus::Ok);
    auto invalid = a;
    invalid.offset = 1;
    result = format_proc_maps({a, invalid});
    assert(result.status == ProcMapsFormatStatus::Invalid && result.text.empty());
    invalid = a;
    invalid.start = invalid.end;
    assert(format_proc_maps({invalid}).status == ProcMapsFormatStatus::Invalid);
    invalid = a;
    invalid.protection = 8;
    assert(format_proc_maps({invalid}).status == ProcMapsFormatStatus::Invalid);
    invalid = tag;
    invalid.start = 0x1800;
    assert(format_proc_maps({a, invalid}).status == ProcMapsFormatStatus::Invalid);
    invalid = file;
    invalid.name = std::string("a\0b", 3);
    assert(format_proc_maps({invalid}).status == ProcMapsFormatStatus::Invalid);
    invalid.name = std::string(16385, 'x');
    result = format_proc_maps({a, invalid});
    assert(result.status == ProcMapsFormatStatus::Invalid && result.text.empty());
    invalid = tag;
    invalid.name = "[bad\nlabel]";
    assert(format_proc_maps({invalid}).status == ProcMapsFormatStatus::Invalid);
    invalid = a;
    invalid.name_kind = static_cast<ProcMapName>(255);
    assert(format_proc_maps({invalid}).status == ProcMapsFormatStatus::Invalid);
    result = format_proc_maps({a, tag}, 44);
    assert(result.status == ProcMapsFormatStatus::Limit && result.text.empty());
    assert(format_proc_maps({a, tag}, 1024, 1).status == ProcMapsFormatStatus::Limit);
    assert(format_proc_maps({}, 0).status == ProcMapsFormatStatus::Limit);
    if (argc == 2)
        linux_reference(argv[1]);
    else
        assert(argc == 1);
    std::puts("PROC_MAPS_FORMAT_PASS delimiter=1 escaping=1 bounds=1 failure_closed=1");
}
