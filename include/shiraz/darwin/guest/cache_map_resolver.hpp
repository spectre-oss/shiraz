// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/named_cache_catalog.hpp"
#include "shiraz/darwin/guest/process_regions.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"
#include <functional>
#include <sys/stat.h>

namespace shiraz::guest {
// The caller registers the layout parsed against the currently loaded dyld
// cache UUID, slide and range, before guest execution. Original offsets come
// from that loader provenance; a COW shadow's zero offset cannot recover them.
// Guest mapping APIs must continue to forbid replacing unowned host mappings.
// identity_current must recheck that same loaded-cache identity. A file check
// verifies its current pathname still denotes the recorded regular file.
class CacheMapResolver {
  public:
    using IdentityGuard = std::function<bool()>;
    using FileCheck = std::function<bool(const NamedCacheMapping&)>;
    CacheMapResolver(NamedCacheCatalog catalog, uint64_t start, uint64_t size,
                     IdentityGuard identity_current, FileCheck file_check = {})
        : catalog_(std::move(catalog)), start_(start), size_(size),
          identity_current_(std::move(identity_current)), file_check_(std::move(file_check)) {}

    bool operator()(const ProcessRegion& region, ProcMapRecord& output) const {
        if (catalog_.status != CacheFileCatalogStatus::Ok || !start_ || !size_ ||
            size_ > UINT64_MAX - start_ || region.start >= region.end || region.start < start_ ||
            region.end > start_ + size_ || !identity_current_ || !identity_current_())
            return false;
        if (region.inheritance != VM_INHERIT_COPY || region.share_mode != SM_COW ||
            (region.protection & ~(VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE)))
            return false;
        const NamedCacheMapping* match = nullptr;
        bool overlap = false;
        for (const auto& entry : catalog_.mappings) {
            const auto& m = entry.mapping;
            if (m.end <= region.start)
                continue;
            if (m.start >= region.end)
                break;
            overlap = true;
            if (region.start >= m.start && region.end <= m.end) {
                match = &entry;
                break;
            }
        }
        ProcMapRecord record;
        record.start = region.start;
        record.end = region.end;
        record.protection = (region.protection & VM_PROT_READ ? 1 : 0) |
                            (region.protection & VM_PROT_WRITE ? 2 : 0) |
                            (region.protection & VM_PROT_EXECUTE ? 4 : 0);
        if (!region.external_pager) {
            // Cache holes with internal backing are not cache-file mappings. Refuse
            // overlaps and executable internal regions rather than inventing origins.
            if (overlap || (region.protection & VM_PROT_EXECUTE))
                return false;
        } else {
            if (!match)
                return false;
            const auto& m = match->mapping;
            uint64_t delta = region.start - m.start;
            if (delta > UINT64_MAX - m.file_offset || m.device > UINT32_MAX || !m.inode)
                return false;
            record.offset = m.file_offset + delta;
            if (region.object_offset && region.object_offset != record.offset)
                return false;
            if (!(file_check_ ? file_check_(*match) : current_file(*match)))
                return false;
            record.inode = m.inode;
            record.device_major = major(dev_t(m.device));
            record.device_minor = minor(dev_t(m.device));
            record.name_kind = ProcMapName::File;
            record.name = match->path;
        }
        output = std::move(record);
        return true;
    }

  private:
    static bool current_file(const NamedCacheMapping& entry) {
        if (entry.path.empty() || entry.path[0] != '/' ||
            entry.path.find('\0') != std::string::npos)
            return false;
        struct stat st{};
        return !::stat(entry.path.c_str(), &st) && S_ISREG(st.st_mode) &&
               uint32_t(st.st_dev) == entry.mapping.device &&
               uint64_t(st.st_ino) == entry.mapping.inode && st.st_size >= 0 &&
               entry.mapping.file_offset <= uint64_t(st.st_size) &&
               entry.mapping.end - entry.mapping.start <=
                   uint64_t(st.st_size) - entry.mapping.file_offset;
    }
    NamedCacheCatalog catalog_;
    uint64_t start_, size_;
    IdentityGuard identity_current_;
    FileCheck file_check_;
};
} // namespace shiraz::guest
