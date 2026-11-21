// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/elf.hpp"
#include "shiraz/darwin/guest/decode.hpp"
#include "shiraz/darwin/guest/unwind_runtime.hpp"
#include <memory>
#include <functional>
#include <string_view>

namespace shiraz::guest {
class GuestImageReservation;
class AdmissionDomain;
class AdmissionSession;
// Monotonic wall durations for completed stages, in nanoseconds. Rewrite includes
// decoder/site analysis and patching; map includes protections/unwind registration.
struct ImageMetrics {
    uint64_t parse_ns = 0, map_ns = 0, rewrite_ns = 0, bind_ns = 0, init_ns = 0;
    size_t expected_constructors = 0, executed_constructors = 0;
    bool initialized = false;
    size_t verified_relocations = 0;
    bool relocations_verified = false;
};
// Mapping, binding and execution are separate stages. Deferred images cannot
// run constructors until the whole load group has bound and been sealed.
class Image {
  public:
    enum class Mapping { SelfContained, Deferred, AdmissionDeferred };
    explicit Image(const std::vector<uint8_t>& bytes, Mapping mode = Mapping::SelfContained,
                   uintptr_t syscall_dispatcher = 0, uintptr_t transfer_validator = 0);
    ~Image();
    Image(const Image&) = delete;
    Image& operator=(const Image&) = delete;
    Image(Image&&) = delete;
    Image& operator=(Image&&) = delete;
    void relocate(const std::function<uintptr_t(size_t)>& resolve);
    void seal();
    uintptr_t address(size_t symbol_index) const;
    void* symbol(std::string_view name) const;
    bool contains_code(const void* pointer) const;
    size_t initialize(std::shared_ptr<AdmissionDomain> domain = {});
    // Loader calls this while every dependency and lifecycle provider is resident.
    // Reverse FINI_ARRAY order, then FINI; never run for a link-only image.
    size_t finalize(std::shared_ptr<AdmissionDomain> domain = {});
    // Experimental only; the caller must retain this Image via the domain's
    // mapping lease while the returned session can be used.
    std::shared_ptr<AdmissionSession> admission_session() const;
    const ImageMetrics& metrics() const {
        return metrics_;
    }
    const Elf& elf() const {
        return elf_;
    }
    // Valid while this Image is resident; unusual unmapped tables use its owned copy.
    const void* program_header_address() const {
        return elf_.program_headers_mapped
                   ? at(elf_.mapped_program_headers, elf_.program_headers.size())
                   : elf_.program_headers.data();
    }
    // Historical values read back immediately after relocation, before seal/init.
    // Guest constructors may subsequently mutate writable pointer objects.
    const std::vector<uint64_t>& relocation_values() const {
        return relocation_values_;
    }
    uintptr_t load_bias() const {
        return reinterpret_cast<uintptr_t>(base_) - elf_.first_page;
    }
    uint64_t mapping_size() const {
        return mapping_size_;
    }
    size_t rewritten_thread_pointers() const {
        if (admission_mode_)
            throw ElfError("dynamic MRS accounting is not implemented for admitted images");
        return analysis_.sites.size();
    }
    // Read-only mapped-code verification, not execution or spill-footprint proof.
    std::vector<uint64_t> verified_mrs_window_sites() const;
    size_t unwind_frame_count() const {
        return unwind_ ? unwind_->frame_count() : 0;
    }

  private:
    void* at(uint64_t address, uint64_t size) const;
    ImageMetrics metrics_;
    Elf elf_;
    std::vector<uint64_t> relocation_values_;
    ThreadPointerAnalysis analysis_;
    uintptr_t syscall_dispatcher_ = 0;
    uintptr_t transfer_validator_ = 0;
    bool admission_mode_ = false;
    std::vector<uint8_t> admission_source_;
    std::shared_ptr<AdmissionSession> admission_;
    std::unique_ptr<UnwindRegistration> unwind_;
    std::unique_ptr<GuestImageReservation> reservation_;
    uint64_t mapping_size_ = 0;
    uint8_t* base_ = nullptr;
    std::vector<unsigned> page_flags_;
    enum class LinkState { Mapped, Relocated, Sealed };
    LinkState link_state_ = LinkState::Mapped;
    enum class State { Uninitialized, Initializing, Ready, Finalizing, Finalized, Failed };
    State state_ = State::Uninitialized;
};
} // namespace shiraz::guest
