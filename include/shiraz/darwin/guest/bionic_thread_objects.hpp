// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "guest_pthread_layout.hpp"
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>
#include <vector>

namespace shiraz::guest {
class GuestThreadImageReservation;
// Experimental ownership primitive: identity prefix, optional verified stack
// attributes, join state and return value are modeled. Other fields remain
// unimplemented.
// Never publish this object to an APK or use it as a pthread_t until those
// fields, native thread exit and TLS/self-cache integration are implemented.
class BionicThreadObjects {
  public:
    struct StackAttributes {
        uintptr_t base;
        uint64_t size, guard_size;
        uint32_t flags;
        int32_t scheduling_policy, scheduling_priority;
    };
    // Values retained from the guest thread creation request. Darwin does not
    // provide a complete pthread attribute snapshot for arbitrary threads.
    struct CreationAttributes {
        uint64_t guard_size;
        uint32_t flags;
        int32_t scheduling_policy, scheduling_priority;
    };
    struct CurrentSnapshot {
        int32_t tid;
        StackAttributes attributes;
    };
    // Observe only the calling native thread. Creation metadata must be known
    // by the caller; nullptr explicitly means unavailable. Returns a host errno
    // value and leaves both output and native errno unchanged on failure.
    static int snapshot_current(const CreationAttributes* creation,
                                CurrentSnapshot* output) noexcept;
    class Record {
      public:
        ~Record();
        Record(const Record&) = delete;
        Record& operator=(const Record&) = delete;
        Record(Record&&) = delete;
        Record& operator=(Record&&) = delete;
        uintptr_t handle() const noexcept {
            return reinterpret_cast<uintptr_t>(image_);
        }
        uintptr_t next() const noexcept {
            return image_->next.load(std::memory_order_acquire);
        }
        uintptr_t previous() const noexcept {
            return image_->previous.load(std::memory_order_acquire);
        }
        int32_t tid() const noexcept {
            return image_->tid.load(std::memory_order_acquire);
        }
        uint32_t join_state() const noexcept {
            return image_->join_state.load(std::memory_order_acquire);
        }
        uintptr_t result() const noexcept {
            return image_->return_value.load(std::memory_order_acquire);
        }
        bool has_stack_attributes() const noexcept {
            return has_stack_attributes_;
        }

      private:
        friend class BionicThreadObjects;
        struct alignas(8) Image {
            std::atomic<uintptr_t> next{0}, previous{0};
            std::atomic<int32_t> tid{0};
            uint32_t reserved_pid = 0;
            std::byte attributes[bionic::kPthread_attr_size]{};
            std::atomic<uint32_t> join_state{0};
            std::byte
                before_result[bionic::kPthread_return_value - bionic::kPthread_join_state - 4]{};
            std::atomic<uintptr_t> return_value{0};
            std::byte
                before_stack_top[bionic::kPthread_stack_top - bionic::kPthread_return_value - 8]{};
            uintptr_t stack_top = 0;
            std::byte remaining[bionic::kPthread_size - bionic::kPthread_stack_top - 8]{};
        };
        // Guest-writable bytes never share an allocation with trusted sidecars.
        // Inaccessible pages bound the mapping on both sides.
        void* mapping_ = nullptr;
        size_t mapping_size_ = 0;
        Image* image_ = nullptr;
        std::unique_ptr<GuestThreadImageReservation> reservation_;
        Record *next_ = nullptr, *previous_ = nullptr;
        const int32_t identity_tid_;
        const bool has_stack_attributes_;
        uint32_t state_;
        uintptr_t exit_value_ = 0;
        bool cleanup_complete_ = false, native_exited_ = false;
        explicit Record(int32_t tid, bool detached, const StackAttributes* attributes);
    };
    using Lease = std::shared_ptr<Record>;
    enum class Outcome { Ok, Missing, Invalid, NeedsJoin, NotReady };
    enum class PublishStatus { Ok, InvalidIdentity, DuplicateIdentity, CapacityExhausted };
    struct PublishResult {
        Lease record;
        PublishStatus status;
    };
    // Retention is for experiments that expose raw guest pointers. It keeps
    // retired mappings alive until the pool is destroyed and counts them
    // against the capacity. The owner must stop all guest access before pool
    // destruction; retention does not make concurrent list traversal coherent.
    enum class Retention { LeasesOnly, UntilPoolDestruction };
    explicit BionicThreadObjects(size_t capacity, Retention retention = Retention::LeasesOnly);
    ~BionicThreadObjects();
    // A successful publish transfers no ownership to callers: the returned
    // lease keeps storage alive, including after retirement. Input is a known
    // guest TID, not a host handle or an inferred identity.
    Lease publish(int32_t tid, bool detached);
    // Distinguish an exhausted budget from a duplicate live identity. Neither
    // failure publishes an object; allocation errors still propagate.
    PublishResult publish_checked(int32_t tid, bool detached);
    // The caller supplies a complete verified snapshot; this method never
    // guesses missing guard, scheduling, or stack values. Invalid snapshots
    // are rejected before a record is published.
    Lease publish_with_attributes(int32_t tid, bool detached, const StackAttributes& attributes);
    Lease lookup(uintptr_t handle) const;
    Outcome claim_join(uintptr_t handle);
    Outcome cancel_join(uintptr_t handle);
    Outcome detach(uintptr_t handle);
    Outcome cleanup_complete(uintptr_t handle, uintptr_t result);
    // The caller must establish native thread exit; guest cleanup alone is not
    // enough. Detached records are removed only after this explicit boundary.
    Outcome native_exited(uintptr_t handle);
    Outcome finish_join(uintptr_t handle, uintptr_t* result);
    size_t size() const;

  private:
    Lease publish_record(int32_t tid, bool detached, const StackAttributes* attributes,
                         PublishStatus* status = nullptr);
    void unlink_locked(Record& record) noexcept;
    mutable std::mutex mutex_;
    std::map<uintptr_t, Lease> entries_;
    std::vector<Lease> retired_;
    Record* head_ = nullptr;
    const size_t capacity_;
    const Retention retention_;
};
} // namespace shiraz::guest
