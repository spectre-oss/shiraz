// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_plan.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include <algorithm>
#include <cstring>
#include <limits>
namespace shiraz::guest {
std::vector<AdmissionPatch>
plan_guest_admission(const Elf& elf, const std::vector<uint8_t>& original,
                     const ThreadPointerAnalysis& analysis, uintptr_t image_base, uintptr_t islands,
                     size_t island_capacity, uintptr_t syscall_dispatcher,
                     uintptr_t transfer_validator) {
    if (!image_base || (image_base & 3) || !islands || (islands & 15) || elf.span < 4 ||
        elf.span > (512ull << 20) || image_base > UINTPTR_MAX - elf.span ||
        island_capacity > (512ull << 20) || islands > UINTPTR_MAX - island_capacity)
        throw ElfError("invalid guest admission address range");
    if (island_capacity && islands < image_base + elf.span &&
        image_base < islands + island_capacity)
        throw ElfError("guest admission islands overlap image");
    constexpr size_t limit = 1u << 20;
    if (analysis.sites.size() > limit || analysis.syscalls.size() > limit - analysis.sites.size() ||
        analysis.indirect_transfers.size() >
            limit - analysis.sites.size() - analysis.syscalls.size())
        throw ElfError("guest admission patch limit");
    const size_t total =
        analysis.sites.size() + analysis.syscalls.size() + analysis.indirect_transfers.size();
    std::vector<AdmissionPatch> result;
    result.reserve(total);
    size_t used = 0;
    auto add = [&](uint64_t address, uint32_t instruction, unsigned kind) {
        bool code = false;
        uint64_t file_offset = 0;
        for (const auto& segment : elf.segments)
            if ((segment.flags & 1) && address >= segment.address &&
                address - segment.address <= segment.file_size &&
                4 <= segment.file_size - (address - segment.address)) {
                uint64_t delta = address - segment.address;
                if (segment.offset > UINT64_MAX - delta)
                    throw ElfError("guest admission file offset overflow");
                file_offset = segment.offset + delta;
                code = true;
            }
        if (!code || (address & 3) || address < elf.first_page ||
            address - elf.first_page > elf.span - 4)
            throw ElfError("guest admission patch outside executable bytes");
        if (file_offset > original.size() || 4 > original.size() - size_t(file_offset))
            throw ElfError("guest admission source bytes unavailable");
        uint32_t actual;
        std::memcpy(&actual, original.data() + file_offset, sizeof(actual));
        if (actual != instruction)
            throw ElfError("guest admission source changed after analysis");
        if (used > island_capacity || 80 > island_capacity - used)
            throw ElfError("guest admission island capacity");
        uintptr_t host = image_base + uintptr_t(address - elf.first_page);
        uintptr_t island = islands + used;
        AdmissionPatch patch{address, host, island, instruction, 0, {}, 0};
        if (kind == 2) {
            auto p = plan_indirect_transfer(host, instruction, island, transfer_validator);
            patch.replacement = p.replacement;
            patch.island = p.island;
            patch.island_size = p.island.size();
        } else {
            auto p = kind == 1 ? plan_system_call(host, instruction, island, syscall_dispatcher)
                               : plan_thread_pointer(host, instruction, island);
            patch.replacement = p.replacement;
            if (p.uses_island) {
                std::memcpy(patch.island.data(), p.island.data(), p.island.size());
                patch.island_size = p.island.size();
            }
        }
        result.push_back(patch);
        used += 80;
    };
    for (const auto& site : analysis.sites)
        add(site.address, site.instruction, 0);
    for (const auto& site : analysis.syscalls)
        add(site.address, site.instruction, 1);
    for (const auto& site : analysis.indirect_transfers)
        add(site.address, site.instruction, 2);
    std::sort(result.begin(), result.end(),
              [](const auto& a, const auto& b) { return a.guest_address < b.guest_address; });
    for (size_t i = 1; i < result.size(); ++i)
        if (result[i - 1].guest_address == result[i].guest_address)
            throw ElfError("duplicate guest admission patch site");
    return result;
}
} // namespace shiraz::guest
