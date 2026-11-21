// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <cstdio>
#include <new>
#include <string>
#include <utility>
#include <vector>

namespace shiraz::guest {

// This formatter does not discover mappings or establish their provenance.
// Callers must supply a complete snapshot and verified identities; in
// particular, an anonymous host mapping must not be labeled as file-backed.
enum class ProcMapName { Anonymous, File, KernelLabel };
struct ProcMapRecord {
    uint64_t start = 0, end = 0, offset = 0, inode = 0;
    uint32_t device_major = 0, device_minor = 0;
    // Linux VM_READ, VM_WRITE and VM_EXEC, represented here as bits 0, 1 and 2.
    uint8_t protection = 0;
    bool shared = false;
    ProcMapName name_kind = ProcMapName::Anonymous;
    std::string name;
};
enum class ProcMapsFormatStatus { Ok, Invalid, Limit, OutOfMemory };
struct ProcMapsFormatResult {
    ProcMapsFormatStatus status = ProcMapsFormatStatus::Invalid;
    std::string text;
};

// The output is all-or-nothing and bounded. Anonymous lines deliberately end
// in a space before the newline, as Linux's maps header does. File pathnames
// escape newline as \\012; kernel labels are not file pathnames.
inline ProcMapsFormatResult format_proc_maps(const std::vector<ProcMapRecord>& records,
                                             size_t byte_limit = 8u * 1024u * 1024u,
                                             size_t record_limit = 65536) {
    constexpr size_t max_bytes = 8u * 1024u * 1024u, max_records = 65536;
    constexpr size_t max_name = 16384, path_column = 73;
    if (!byte_limit || byte_limit > max_bytes || !record_limit || record_limit > max_records ||
        records.size() > record_limit)
        return {ProcMapsFormatStatus::Limit, {}};
    try {
        std::string output;
        uint64_t previous_end = 0;
        bool first = true;
        for (const auto& record : records) {
            if (record.start >= record.end || (!first && record.start < previous_end) ||
                (record.protection & ~uint8_t(7)) || record.name.size() > max_name ||
                record.name.find('\0') != std::string::npos)
                return {ProcMapsFormatStatus::Invalid, {}};
            first = false;
            previous_end = record.end;
            switch (record.name_kind) {
            case ProcMapName::Anonymous:
                if (!record.name.empty() || record.offset || record.inode || record.device_major ||
                    record.device_minor)
                    return {ProcMapsFormatStatus::Invalid, {}};
                break;
            case ProcMapName::File:
                if (record.name.empty())
                    return {ProcMapsFormatStatus::Invalid, {}};
                break;
            case ProcMapName::KernelLabel:
                if (record.name.empty() || record.name.find('\n') != std::string::npos ||
                    record.offset || record.inode || record.device_major || record.device_minor)
                    return {ProcMapsFormatStatus::Invalid, {}};
                break;
            default:
                return {ProcMapsFormatStatus::Invalid, {}};
            }
            char prefix[160];
            int length = std::snprintf(
                prefix, sizeof(prefix), "%08llx-%08llx %c%c%c%c %08llx %02x:%02x %llu ",
                static_cast<unsigned long long>(record.start),
                static_cast<unsigned long long>(record.end), record.protection & 1 ? 'r' : '-',
                record.protection & 2 ? 'w' : '-', record.protection & 4 ? 'x' : '-',
                record.shared ? 's' : 'p', static_cast<unsigned long long>(record.offset),
                record.device_major, record.device_minor,
                static_cast<unsigned long long>(record.inode));
            if (length < 0 || size_t(length) >= sizeof(prefix))
                return {ProcMapsFormatStatus::Invalid, {}};
            std::string line(prefix, size_t(length));
            if (record.name_kind != ProcMapName::Anonymous) {
                if (line.size() < path_column - 1)
                    line.append(path_column - 1 - line.size(), ' ');
                line.push_back(' ');
                for (char ch : record.name) {
                    if (record.name_kind == ProcMapName::File && ch == '\n')
                        line.append("\\012");
                    else
                        line.push_back(ch);
                }
            }
            line.push_back('\n');
            if (line.size() > byte_limit - output.size())
                return {ProcMapsFormatStatus::Limit, {}};
            output.append(line);
        }
        return {ProcMapsFormatStatus::Ok, std::move(output)};
    } catch (const std::bad_alloc&) {
        return {ProcMapsFormatStatus::OutOfMemory, {}};
    }
}
} // namespace shiraz::guest
