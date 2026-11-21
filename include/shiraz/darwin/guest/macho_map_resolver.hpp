// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/macho_file_layout.hpp"
#include "shiraz/darwin/guest/process_regions.hpp"
#include "shiraz/darwin/guest/proc_maps_format.hpp"
#include <functional>
#include <sys/stat.h>

namespace shiraz::guest {
struct MachoFileIdentity {
    std::string path;
    uint32_t device = 0;
    uint64_t inode = 0, size = 0;
};
// Register a file layout against a pinned, loaded image before guest execution.
// The mandatory guard must verify that same loaded image/UUID/slide remains
// current. File metadata alone is not evidence that it owns an address range.
// Guest mapping APIs must not replace unowned native image mappings.
class MachoMapResolver {
  public:
    using Guard = std::function<bool()>;
    MachoMapResolver(MachoFileLayout layout, MachoFileIdentity file, int64_t slide, Guard current)
        : layout_(std::move(layout)), file_(std::move(file)), slide_(slide),
          current_(std::move(current)) {}
    bool operator()(const ProcessRegion& region, ProcMapRecord& output) const {
        if (region.start >= region.end || !region.external_pager ||
            region.inheritance != VM_INHERIT_COPY ||
            (region.share_mode != SM_COW && region.share_mode != SM_PRIVATE &&
             region.share_mode != SM_PRIVATE_ALIASED) ||
            (region.protection & ~(VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE)) || !current_ ||
            !current_())
            return false;
        const MachoFileSegment* match = nullptr;
        uint64_t base = 0;
        for (const auto& segment : layout_.segments) {
            uint64_t start;
            if (slide_ >= 0) {
                if (segment.vmaddr > UINT64_MAX - uint64_t(slide_))
                    return false;
                start = segment.vmaddr + uint64_t(slide_);
            } else {
                uint64_t amount = uint64_t(-(slide_ + 1)) + 1;
                if (segment.vmaddr < amount)
                    return false;
                start = segment.vmaddr - amount;
            }
            if (segment.mapped_file_size > segment.vmsize ||
                segment.mapped_file_size > UINT64_MAX - start)
                return false;
            if (region.start >= start && region.end <= start + segment.mapped_file_size) {
                if (match)
                    return false;
                match = &segment;
                base = start;
            }
        }
        if (!match || !match->file_size || match->file_offset > file_.size ||
            match->file_size > file_.size - match->file_offset ||
            region.start - base > UINT64_MAX - match->file_offset || file_.path.empty() ||
            file_.path[0] != '/' || file_.path.find('\0') != std::string::npos || !file_.inode)
            return false;
        uint64_t offset = match->file_offset + region.start - base;
        if (region.object_offset && region.object_offset != offset)
            return false;
        struct stat st{};
        if (::stat(file_.path.c_str(), &st) || !S_ISREG(st.st_mode) || st.st_size < 0 ||
            uint32_t(st.st_dev) != file_.device || uint64_t(st.st_ino) != file_.inode ||
            uint64_t(st.st_size) != file_.size || !current_())
            return false;
        ProcMapRecord record;
        record.start = region.start;
        record.end = region.end;
        record.offset = offset;
        record.protection = (region.protection & VM_PROT_READ ? 1 : 0) |
                            (region.protection & VM_PROT_WRITE ? 2 : 0) |
                            (region.protection & VM_PROT_EXECUTE ? 4 : 0);
        record.inode = file_.inode;
        record.device_major = major(dev_t(file_.device));
        record.device_minor = minor(dev_t(file_.device));
        record.name_kind = ProcMapName::File;
        record.name = file_.path;
        output = std::move(record);
        return true;
    }

  private:
    MachoFileLayout layout_;
    MachoFileIdentity file_;
    int64_t slide_;
    Guard current_;
};
} // namespace shiraz::guest
