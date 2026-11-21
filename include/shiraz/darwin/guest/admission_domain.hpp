// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/admission_session.hpp"
#include <condition_variable>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>
namespace shiraz::guest {
// Experimental target domain with immutable images. Every image lease must own
// its code and island mapping and outlive its session; domains sharing a map
// must share the same lease control block. Every guest ingress must hold an
// Invocation and call admit_entry before execution. This does not establish
// ABI correctness, asynchronous signal safety, unwind safety or permission to
// execute APK code. close() drains invocations but retains mappings until the
// domain and its remaining owners are destroyed.
class AdmissionDomain {
    struct Policy;

  public:
    struct Decision {
        uintptr_t target;
        bool newly_admitted;
    };
    struct ImageRegistration {
        std::shared_ptr<AdmissionSession> session;
        std::shared_ptr<void> mapping_lease;
    };
    // A same-thread private policy overlay. The caller must prevent staged
    // pointers and side effects escaping to other threads before commit and
    // must finish every staged call before commit or destruction. Rollback
    // removes admission only; it cannot undo constructor side effects.
    class StagedExtension {
      public:
        StagedExtension(std::shared_ptr<AdmissionDomain> domain,
                        const std::vector<ImageRegistration>& images,
                        const std::vector<void*>& closures,
                        const std::vector<void*>& provider_entries);
        ~StagedExtension();
        StagedExtension(const StagedExtension&) = delete;
        StagedExtension& operator=(const StagedExtension&) = delete;
        void commit();

      private:
        friend class AdmissionDomain;
        std::shared_ptr<AdmissionDomain> domain_;
        std::vector<ImageRegistration> images_;
        std::vector<uintptr_t> entries_;
        std::shared_ptr<const Policy> private_policy_;
        bool finished_ = false;
        static thread_local StagedExtension* current_;
    };
    class Invocation;
    // A single-use active-domain reservation may cross threads. While it is
    // outstanding, close waits; consuming it installs an Invocation without
    // reopening a domain that may already be closing.
    class Reservation {
      public:
        ~Reservation();
        Reservation(const Reservation&) = delete;
        Reservation& operator=(const Reservation&) = delete;

      private:
        friend class AdmissionDomain;
        friend class Invocation;
        explicit Reservation(std::shared_ptr<AdmissionDomain> domain)
            : domain_(std::move(domain)) {}
        std::shared_ptr<AdmissionDomain> domain_;
        bool counted_ = false;
    };
    class Invocation {
      public:
        explicit Invocation(std::shared_ptr<AdmissionDomain> domain);
        explicit Invocation(std::unique_ptr<Reservation> reservation);
        ~Invocation();
        Invocation(const Invocation&) = delete;
        Invocation& operator=(const Invocation&) = delete;

      private:
        friend class AdmissionDomain;
        std::shared_ptr<AdmissionDomain> domain_;
        Invocation* previous_ = nullptr;
        static thread_local Invocation* current_;
    };
    void add_image(std::shared_ptr<AdmissionSession> session, std::shared_ptr<void> mapping_lease);
    // The caller must also authorize the closure's ABI for this domain.
    void allow_closure(void* entry);
    // Exact audited non-closure provider entries only, before sealing.
    void allow_provider_entry(void* entry);
    // Trusted host code may authorize an exact, newly created closure during a
    // live invocation after checking its namespace and ABI. Idempotent.
    bool register_closure(void* entry);
    void seal();
    // Atomically publish a fully prepared image batch and its exact authorized
    // host entries into a sealed domain. The caller must establish provenance,
    // ABI authorization, namespace serialization, constructor ordering and
    // rollback before using this experimental primitive for dynamic loading.
    // Existing invocations may continue; no image or entry is removed here.
    void extend(const std::vector<ImageRegistration>& images, const std::vector<void*>& closures,
                const std::vector<void*>& provider_entries);
    // Current synchronous invocation only; empty outside an admitted entry.
    static std::shared_ptr<AdmissionDomain> current_invocation() noexcept;
    // Only an active, public invocation can reserve a child; null means refused.
    static std::unique_ptr<Reservation> reserve_current();
    void admit_entry(uintptr_t target);
    bool owns_session(const AdmissionSession* session);
    Decision route(uintptr_t site, uintptr_t target, uintptr_t guest_sp);
    void close();

  private:
    // Destroy the session before releasing the mapping lease.
    struct Image {
        std::shared_ptr<void> lease;
        std::shared_ptr<AdmissionSession> session;
    };
    struct Policy {
        std::vector<Image> images;
        std::vector<uintptr_t> closures;
    };
    static AdmissionSession* image_for(const Policy& policy, uintptr_t address) noexcept;
    static std::vector<uintptr_t> validate_extension(const std::vector<ImageRegistration>& images,
                                                     const std::vector<void*>& closures,
                                                     const std::vector<void*>& provider_entries);
    static std::shared_ptr<const Policy>
    merge_extension(const std::shared_ptr<const Policy>& base,
                    const std::vector<ImageRegistration>& images,
                    const std::vector<uintptr_t>& entries);
    std::shared_ptr<const Policy> selected_policy() const noexcept;
    void require_invocation() const;
    std::mutex mutex_;
    std::condition_variable drained_;
    std::vector<Image> images_;
    std::vector<uintptr_t> closures_;
    // Atomic shared_ptr operations publish whole immutable policies; retaining a
    // snapshot keeps every image/session lease live during a concurrent route.
    std::shared_ptr<const Policy> policy_;
    size_t active_ = 0;
    bool sealed_ = false, closing_ = false, closed_ = false, staging_ = false;
};
} // namespace shiraz::guest
