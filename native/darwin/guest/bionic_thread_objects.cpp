// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/bionic_thread_objects.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/thread_identity.hpp"
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <new>
#include <pthread.h>
#include <stdexcept>
#include <system_error>
#include <sys/mman.h>
#include <type_traits>
#include <unistd.h>
#include <utility>

namespace shiraz::guest {
namespace b = bionic;
namespace {
std::mutex& thread_mapping_mutex() {
    // Serialize this allocator's map/register and unmap/unregister handoff.
    // A retained lease may be destroyed outside its pool's registry lock.
    static auto* mutex = new std::mutex;
    return *mutex;
}
bool valid_stack_attributes(const BionicThreadObjects::StackAttributes& attributes,
                            bool detached) noexcept {
    const uint32_t allowed = b::kPthread_attr_flag_detached | b::kPthread_attr_flag_inherit |
                             b::kPthread_attr_flag_explicit;
    return attributes.base && attributes.size && attributes.size <= UINTPTR_MAX - attributes.base &&
           !(attributes.base & 15) && !((attributes.base + attributes.size) & 15) &&
           !(attributes.flags & ~allowed) &&
           bool(attributes.flags & b::kPthread_attr_flag_detached) == detached &&
           !(attributes.flags & b::kPthread_attr_flag_inherit &&
             attributes.flags & b::kPthread_attr_flag_explicit) &&
           attributes.scheduling_policy >= 0 && attributes.scheduling_priority >= 0;
}
} // namespace
int BionicThreadObjects::snapshot_current(const CreationAttributes* creation,
                                          CurrentSnapshot* output) noexcept {
    struct PreserveErrno {
        int value;
        ~PreserveErrno() {
            errno = value;
        }
    } preserve{errno};
    if (!output)
        return EINVAL;
    if (!creation)
        return ENOTSUP;
    const auto host = pthread_self();
    const uintptr_t high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(host));
    const size_t size = pthread_get_stacksize_np(host);
    if (!high || !size || high <= size)
        return EOVERFLOW;
    StackAttributes attributes{high - size,
                               size,
                               creation->guard_size,
                               creation->flags,
                               creation->scheduling_policy,
                               creation->scheduling_priority};
    const bool detached = creation->flags & b::kPthread_attr_flag_detached;
    if (!valid_stack_attributes(attributes, detached))
        return EINVAL;
    // Ensure that the reported range actually covers this thread's current
    // stack frame, rather than treating an arbitrary native extent as a stack.
    uintptr_t current = 0;
    __asm__ volatile("mov %0, sp" : "=r"(current));
    if (current < attributes.base || current >= high)
        return EOVERFLOW;
    int tid = 0;
    int error = shiraz_android_tid_current(&tid);
    if (error)
        return error;
    if (tid <= 0)
        return EOVERFLOW;
    *output = CurrentSnapshot{tid, attributes};
    return 0;
}
BionicThreadObjects::Record::Record(int32_t tid, bool detached, const StackAttributes* attributes)
    : identity_tid_(tid), has_stack_attributes_(attributes != nullptr),
      state_(detached ? b::kPthread_join_detached : b::kPthread_join_not_joined) {
    static_assert(std::atomic<uintptr_t>::is_always_lock_free &&
                  std::atomic<int32_t>::is_always_lock_free &&
                  std::atomic<uint32_t>::is_always_lock_free);
    static_assert(sizeof(std::atomic<uintptr_t>) == 8 && sizeof(std::atomic<int32_t>) == 4 &&
                  sizeof(std::atomic<uint32_t>) == 4);
    static_assert(
        sizeof(StackAttributes::base) == b::kPthread_attr_width_stack_base &&
        sizeof(StackAttributes::size) == b::kPthread_attr_width_stack_size &&
        sizeof(StackAttributes::guard_size) == b::kPthread_attr_width_guard_size &&
        sizeof(StackAttributes::flags) == b::kPthread_attr_width_flags &&
        sizeof(StackAttributes::scheduling_policy) == b::kPthread_attr_width_sched_policy &&
        sizeof(StackAttributes::scheduling_priority) == b::kPthread_attr_width_sched_priority);
    static_assert(std::is_standard_layout_v<Image> && sizeof(Image) == b::kPthread_size &&
                  alignof(Image) >= b::kPthread_align);
    static_assert(offsetof(Image, next) == b::kPthread_next &&
                  offsetof(Image, previous) == b::kPthread_prev &&
                  offsetof(Image, tid) == b::kPthread_tid &&
                  offsetof(Image, attributes) == b::kPthread_attr &&
                  offsetof(Image, join_state) == b::kPthread_join_state &&
                  offsetof(Image, return_value) == b::kPthread_return_value &&
                  offsetof(Image, stack_top) == b::kPthread_stack_top);
    const int native_page_size = ::getpagesize();
    if (native_page_size <= 0 || size_t(native_page_size) < sizeof(Image) ||
        size_t(native_page_size) > SIZE_MAX / 3)
        throw std::runtime_error("unsupported thread image page size");
    const size_t page = size_t(native_page_size);
    mapping_size_ = page * 3;
    std::lock_guard<std::mutex> mapping_lock(thread_mapping_mutex());
    mapping_ = ::mmap(nullptr, mapping_size_, PROT_NONE, MAP_PRIVATE | MAP_ANON, -1, 0);
    if (mapping_ == MAP_FAILED) {
        const int error = errno;
        mapping_ = nullptr;
        throw std::system_error(error, std::generic_category(), "thread image mapping");
    }
    void* storage = static_cast<char*>(mapping_) + page;
    if (::mprotect(storage, page, PROT_READ | PROT_WRITE)) {
        const int error = errno;
        if (::munmap(mapping_, mapping_size_))
            std::abort();
        mapping_ = nullptr;
        throw std::system_error(error, std::generic_category(), "thread image protection");
    }
    try {
        reservation_ = std::make_unique<GuestThreadImageReservation>(
            reinterpret_cast<uintptr_t>(mapping_), mapping_size_);
    } catch (...) {
        if (::munmap(mapping_, mapping_size_))
            std::abort();
        mapping_ = nullptr;
        throw;
    }
    image_ = new (storage) Image{};
    image_->tid.store(tid, std::memory_order_relaxed);
    image_->join_state.store(state_, std::memory_order_relaxed);
    if (attributes) {
        auto write = [&](size_t offset, const auto& value) {
            std::memcpy(image_->attributes + offset, &value, sizeof(value));
        };
        write(b::kPthread_attr_flags, attributes->flags);
        write(b::kPthread_attr_stack_base, attributes->base);
        write(b::kPthread_attr_stack_size, attributes->size);
        write(b::kPthread_attr_guard_size, attributes->guard_size);
        write(b::kPthread_attr_sched_policy, attributes->scheduling_policy);
        write(b::kPthread_attr_sched_priority, attributes->scheduling_priority);
        image_->stack_top = attributes->base + attributes->size;
    }
}
BionicThreadObjects::Record::~Record() {
    std::lock_guard<std::mutex> mapping_lock(thread_mapping_mutex());
    image_->~Image();
    reservation_->unmap_owned();
    reservation_.reset();
}
BionicThreadObjects::BionicThreadObjects(size_t capacity, Retention retention)
    : capacity_(capacity), retention_(retention) {
    if (!capacity || capacity > 4096)
        throw std::invalid_argument("invalid bionic object capacity");
    if (retention_ == Retention::UntilPoolDestruction)
        retired_.reserve(capacity_);
}
BionicThreadObjects::~BionicThreadObjects() {
    // Destruction is an owner boundary: callers must have stopped pool
    // operations. Surviving leases retain only their own image, never stale
    // links to other records whose storage the pool is about to release.
    std::lock_guard lock(mutex_);
    while (head_)
        unlink_locked(*head_);
    entries_.clear();
}
BionicThreadObjects::Lease BionicThreadObjects::publish(int32_t tid, bool detached) {
    return publish_record(tid, detached, nullptr);
}
BionicThreadObjects::PublishResult BionicThreadObjects::publish_checked(int32_t tid,
                                                                        bool detached) {
    PublishStatus status = PublishStatus::InvalidIdentity;
    auto record = publish_record(tid, detached, nullptr, &status);
    return {std::move(record), status};
}
BionicThreadObjects::Lease
BionicThreadObjects::publish_with_attributes(int32_t tid, bool detached,
                                             const StackAttributes& attributes) {
    if (!valid_stack_attributes(attributes, detached))
        return {};
    return publish_record(tid, detached, &attributes);
}
BionicThreadObjects::Lease BionicThreadObjects::publish_record(int32_t tid, bool detached,
                                                               const StackAttributes* attributes,
                                                               PublishStatus* status) {
    if (tid <= 0) {
        if (status)
            *status = PublishStatus::InvalidIdentity;
        return {};
    }
    std::lock_guard lock(mutex_);
    // Preserve the existing fast full-pool refusal for callers that do not
    // request a reason. Checked callers distinguish a duplicate even if full.
    if (!status && entries_.size() + retired_.size() >= capacity_)
        return {};
    for (const auto& entry : entries_)
        if (!entry.second->native_exited_ && entry.second->identity_tid_ == tid) {
            if (status)
                *status = PublishStatus::DuplicateIdentity;
            return {};
        }
    if (entries_.size() + retired_.size() >= capacity_) {
        if (status)
            *status = PublishStatus::CapacityExhausted;
        return {};
    }
    auto record = Lease(new Record(tid, detached, attributes));
    if (!entries_.emplace(record->handle(), record).second)
        throw std::logic_error("duplicate object address");
    record->next_ = head_;
    record->image_->next.store(head_ ? head_->handle() : 0, std::memory_order_relaxed);
    if (head_) {
        head_->previous_ = record.get();
        head_->image_->previous.store(record->handle(), std::memory_order_release);
    }
    head_ = record.get();
    if (status)
        *status = PublishStatus::Ok;
    return record;
}
BionicThreadObjects::Lease BionicThreadObjects::lookup(uintptr_t handle) const {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    return found == entries_.end() ? Lease{} : found->second;
}
void BionicThreadObjects::unlink_locked(Record& record) noexcept {
    if (record.previous_) {
        record.previous_->next_ = record.next_;
        record.previous_->image_->next.store(record.next_ ? record.next_->handle() : 0,
                                             std::memory_order_release);
    } else
        head_ = record.next_;
    if (record.next_) {
        record.next_->previous_ = record.previous_;
        record.next_->image_->previous.store(record.previous_ ? record.previous_->handle() : 0,
                                             std::memory_order_release);
    }
    // Do not follow guest-writable image links when maintaining the list.
    record.next_ = record.previous_ = nullptr;
    record.image_->next.store(0, std::memory_order_release);
    record.image_->previous.store(0, std::memory_order_release);
}
BionicThreadObjects::Outcome BionicThreadObjects::claim_join(uintptr_t handle) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    auto state = record.state_;
    if (state != b::kPthread_join_not_joined && state != b::kPthread_join_exited_not_joined)
        return Outcome::Invalid;
    record.state_ = b::kPthread_join_joined;
    record.image_->join_state.store(record.state_, std::memory_order_release);
    return Outcome::Ok;
}
BionicThreadObjects::Outcome BionicThreadObjects::cancel_join(uintptr_t handle) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    if (record.state_ != b::kPthread_join_joined)
        return Outcome::Invalid;
    record.state_ =
        record.cleanup_complete_ ? b::kPthread_join_exited_not_joined : b::kPthread_join_not_joined;
    record.image_->join_state.store(record.state_, std::memory_order_release);
    return Outcome::Ok;
}
BionicThreadObjects::Outcome BionicThreadObjects::detach(uintptr_t handle) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    auto state = record.state_;
    if (state == b::kPthread_join_exited_not_joined)
        return Outcome::NeedsJoin;
    if (state != b::kPthread_join_not_joined)
        return Outcome::Invalid;
    record.state_ = b::kPthread_join_detached;
    record.image_->join_state.store(record.state_, std::memory_order_release);
    return Outcome::Ok;
}
BionicThreadObjects::Outcome BionicThreadObjects::cleanup_complete(uintptr_t handle,
                                                                   uintptr_t result) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    if (record.cleanup_complete_)
        return Outcome::Invalid;
    record.cleanup_complete_ = true;
    record.exit_value_ = result;
    record.image_->return_value.store(result, std::memory_order_relaxed);
    if (record.state_ == b::kPthread_join_not_joined) {
        record.state_ = b::kPthread_join_exited_not_joined;
        record.image_->join_state.store(record.state_, std::memory_order_release);
    }
    return Outcome::Ok;
}
BionicThreadObjects::Outcome BionicThreadObjects::native_exited(uintptr_t handle) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    if (!record.cleanup_complete_)
        return Outcome::NotReady;
    if (record.native_exited_)
        return Outcome::Invalid;
    record.native_exited_ = true;
    record.image_->tid.store(0, std::memory_order_release);
    if (record.state_ == b::kPthread_join_detached) {
        if (retention_ == Retention::UntilPoolDestruction)
            retired_.push_back(found->second);
        unlink_locked(record);
        entries_.erase(found);
    }
    return Outcome::Ok;
}
BionicThreadObjects::Outcome BionicThreadObjects::finish_join(uintptr_t handle, uintptr_t* result) {
    std::lock_guard lock(mutex_);
    auto found = entries_.find(handle);
    if (found == entries_.end())
        return Outcome::Missing;
    auto& record = *found->second;
    if (record.state_ != b::kPthread_join_joined)
        return Outcome::Invalid;
    if (!record.native_exited_)
        return Outcome::NotReady;
    if (result)
        *result = record.exit_value_;
    if (retention_ == Retention::UntilPoolDestruction)
        retired_.push_back(found->second);
    unlink_locked(record);
    entries_.erase(found);
    return Outcome::Ok;
}
size_t BionicThreadObjects::size() const {
    std::lock_guard lock(mutex_);
    return entries_.size();
}
} // namespace shiraz::guest
