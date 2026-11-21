// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/bionic_thread_objects.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/thread_identity.hpp"
#include <atomic>
#include <cerrno>
#include <cstring>
#include <iostream>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <pthread.h>
#include <stdexcept>
#include <thread>
#include <sys/mman.h>
#include <type_traits>
#include <unistd.h>
#include <vector>
using namespace shiraz::guest;
using Pool = BionicThreadObjects;
using O = Pool::Outcome;
namespace b = shiraz::guest::bionic;
static_assert(!std::is_copy_constructible_v<Pool::Record> &&
              !std::is_move_constructible_v<Pool::Record>);
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
template <class T> static T field(uintptr_t address, size_t offset) {
    T value{};
    std::memcpy(&value, reinterpret_cast<const void*>(address + offset), sizeof(value));
    return value;
}
static bool protection(uintptr_t address, vm_prot_t expected) {
    mach_vm_address_t region = address;
    mach_vm_size_t size = 0;
    vm_region_basic_info_data_64_t info{};
    mach_msg_type_number_t count = VM_REGION_BASIC_INFO_COUNT_64;
    mach_port_t object = MACH_PORT_NULL;
    kern_return_t result =
        mach_vm_region(mach_task_self(), &region, &size, VM_REGION_BASIC_INFO_64,
                       reinterpret_cast<vm_region_info_t>(&info), &count, &object);
    if (object != MACH_PORT_NULL)
        mach_port_deallocate(mach_task_self(), object);
    return result == KERN_SUCCESS && region <= address && address - region < size &&
           info.protection == expected;
}
static bool readable(uintptr_t address) {
    uint64_t value = 0;
    mach_vm_size_t copied = 0;
    return mach_vm_read_overwrite(mach_task_self(), address, sizeof(value),
                                  reinterpret_cast<mach_vm_address_t>(&value),
                                  &copied) == KERN_SUCCESS &&
           copied == sizeof(value);
}
struct CaptureCase {
    Pool* pool;
    Pool::CreationAttributes creation;
    Pool::CurrentSnapshot snapshot{};
    Pool::Lease record;
    int error = 0, native_errno = 0, mapped_tid = 0;
    bool local_in_stack = false;
};
static void* capture_worker(void* opaque) {
    auto& state = *static_cast<CaptureCase*>(opaque);
    try {
        errno = EBUSY;
        state.error = Pool::snapshot_current(&state.creation, &state.snapshot);
        state.native_errno = errno;
        if (state.error)
            return nullptr;
        if (shiraz_android_tid_current(&state.mapped_tid)) {
            state.error = EIO;
            return nullptr;
        }
        uintptr_t address = 0;
        __asm__ volatile("mov %0, sp" : "=r"(address));
        const auto& attrs = state.snapshot.attributes;
        state.local_in_stack = address >= attrs.base && address - attrs.base < attrs.size;
        state.record = state.pool->publish_with_attributes(state.snapshot.tid, false, attrs);
        if (!state.record)
            state.error = EINVAL;
    } catch (...) {
        state.error = EIO;
    }
    return nullptr;
}
int main() {
    try {
        {
            Pool checked(1, Pool::Retention::UntilPoolDestruction);
            using S = Pool::PublishStatus;
            auto invalid = checked.publish_checked(0, false);
            require(!invalid.record && invalid.status == S::InvalidIdentity,
                    "checked publication rejects invalid identity");
            auto first = checked.publish_checked(500, true);
            require(first.record && first.status == S::Ok, "checked publication succeeds");
            auto duplicate = checked.publish_checked(500, false);
            auto full = checked.publish_checked(501, false);
            require(!duplicate.record && duplicate.status == S::DuplicateIdentity && !full.record &&
                        full.status == S::CapacityExhausted,
                    "checked publication distinguishes duplicate from full pool");
            const auto address = first.record->handle();
            require(checked.cleanup_complete(address, 0) == O::Ok &&
                        checked.native_exited(address) == O::Ok,
                    "checked fixture retires object");
            auto retained_full = checked.publish_checked(500, false);
            require(!retained_full.record && retained_full.status == S::CapacityExhausted,
                    "retired identity no longer counts as duplicate but consumes capacity");
        }
        // A guest can retain a list pointer across invocations, so active-call
        // quiescence does not by itself make an exposed object safe to unmap.
        uintptr_t retired_joined = 0, retired_detached = 0;
        {
            Pool retained(2, Pool::Retention::UntilPoolDestruction);
            auto joined = retained.publish(101, false);
            auto detached = retained.publish(102, true);
            require(joined && detached, "retention fixture publication");
            retired_joined = joined->handle();
            retired_detached = detached->handle();
            require(detached->next() == retired_joined, "guest-visible link to joined object");
            require(retained.claim_join(retired_joined) == O::Ok &&
                        retained.cleanup_complete(retired_joined, 42) == O::Ok &&
                        retained.native_exited(retired_joined) == O::Ok &&
                        retained.finish_join(retired_joined, nullptr) == O::Ok,
                    "retire joined object");
            joined.reset();
            require(readable(retired_joined) && !retained.lookup(retired_joined),
                    "unlinked joined object remains mapped through pool lifetime");
            require(retained.cleanup_complete(retired_detached, 0) == O::Ok &&
                        retained.native_exited(retired_detached) == O::Ok,
                    "retire detached object");
            detached.reset();
            require(readable(retired_detached) && retained.size() == 0 &&
                        !retained.publish(103, false),
                    "retained detached object consumes bounded lifetime capacity");
        }
        require(!readable(retired_joined) && !readable(retired_detached),
                "pool destruction releases retained mappings after guest access ends");
        {
            Pool retained(16, Pool::Retention::UntilPoolDestruction);
            std::atomic<unsigned> failures{0};
            std::vector<std::thread> workers;
            for (int i = 0; i < 16; ++i)
                workers.emplace_back([&, i] {
                    auto object = retained.publish(1000 + i, true);
                    if (!object || retained.cleanup_complete(object->handle(), 0) != O::Ok ||
                        retained.native_exited(object->handle()) != O::Ok)
                        ++failures;
                });
            for (auto& worker : workers)
                worker.join();
            require(!failures && retained.size() == 0 && !retained.publish(2000, true),
                    "concurrent retirement respects lifetime capacity");
        }
        // Competing thread mappings may reuse an address immediately after
        // munmap. Unmap and registry retirement must be serialized together.
        {
            std::atomic<unsigned> failures{0};
            std::vector<std::thread> workers;
            for (unsigned i = 0; i < 8; ++i)
                workers.emplace_back([&] {
                    const size_t page = size_t(getpagesize());
                    for (unsigned j = 0; j < 500; ++j) {
                        void* mapping =
                            mmap(nullptr, page, PROT_NONE, MAP_PRIVATE | MAP_ANON, -1, 0);
                        if (mapping == MAP_FAILED) {
                            ++failures;
                            continue;
                        }
                        try {
                            GuestThreadImageReservation owned(reinterpret_cast<uintptr_t>(mapping),
                                                              page);
                            errno = EBUSY;
                            owned.unmap_owned();
                            if (errno != EBUSY)
                                ++failures;
                        } catch (...) {
                            ++failures;
                            munmap(mapping, page);
                        }
                    }
                });
            for (auto& worker : workers)
                worker.join();
            require(failures == 0, "atomic thread mapping unmap and reservation retirement");
        }
        bool refused = false;
        try {
            Pool invalid(0);
        } catch (const std::invalid_argument&) {
            refused = true;
        }
        require(refused, "zero capacity refused");
        Pool::CurrentSnapshot unchanged{123, {0x1000, 16, 17, 0, 0, 0}};
        errno = EBUSY;
        require(Pool::snapshot_current(nullptr, &unchanged) == ENOTSUP && errno == EBUSY &&
                    unchanged.tid == 123 && unchanged.attributes.base == 0x1000,
                "unknown creation metadata leaves output and errno unchanged");
        Pool::CreationAttributes invalid_creation{0, 0xffffffffu, 0, 0};
        require(Pool::snapshot_current(&invalid_creation, &unchanged) == EINVAL && errno == EBUSY &&
                    unchanged.tid == 123 && unchanged.attributes.base == 0x1000 &&
                    Pool::snapshot_current(&invalid_creation, nullptr) == EINVAL && errno == EBUSY,
                "invalid metadata and output are refused before publication");
        pthread_attr_t native_attr;
        require(!pthread_attr_init(&native_attr), "native fixture attribute initialization");
        const size_t requested_guard = size_t(getpagesize());
        require(!pthread_attr_setstacksize(&native_attr, 1024 * 1024) &&
                    !pthread_attr_setguardsize(&native_attr, requested_guard),
                "native fixture stack configuration");
        size_t configured_guard = 0;
        int policy = -1;
        sched_param scheduling{};
        require(!pthread_attr_getguardsize(&native_attr, &configured_guard) &&
                    !pthread_attr_getschedpolicy(&native_attr, &policy) &&
                    !pthread_attr_getschedparam(&native_attr, &scheduling) &&
                    policy == SCHED_OTHER && scheduling.sched_priority >= 0,
                "known native fixture creation attributes");
        Pool captured_pool(2);
        CaptureCase capture{};
        capture.pool = &captured_pool;
        capture.creation = {configured_guard, 0, 0, scheduling.sched_priority};
        pthread_t native_thread;
        require(!pthread_create(&native_thread, &native_attr, capture_worker, &capture),
                "native fixture thread creation");
        require(!pthread_attr_destroy(&native_attr) && !pthread_join(native_thread, nullptr),
                "native fixture joins");
        require(!capture.error && capture.native_errno == EBUSY && capture.local_in_stack &&
                    capture.snapshot.tid == capture.mapped_tid && capture.snapshot.tid > 0 &&
                    capture.record && capture.record->tid() == capture.snapshot.tid &&
                    capture.record->has_stack_attributes() &&
                    field<uintptr_t>(capture.record->handle(), b::kPthread_stack_top) ==
                        capture.snapshot.attributes.base + capture.snapshot.attributes.size &&
                    field<uint64_t>(capture.record->handle() + b::kPthread_attr,
                                    b::kPthread_attr_guard_size) == configured_guard,
                "live native stack and mapped TID populate the experimental object");
        require(captured_pool.claim_join(capture.record->handle()) == O::Ok &&
                    captured_pool.cleanup_complete(capture.record->handle(), 0) == O::Ok &&
                    captured_pool.native_exited(capture.record->handle()) == O::Ok &&
                    captured_pool.finish_join(capture.record->handle(), nullptr) == O::Ok,
                "captured record retires after native join confirms exit");
        Pool::Lease retained_one, retained_two;
        {
            Pool temporary(2);
            retained_one = temporary.publish(21, false);
            retained_two = temporary.publish(22, false);
            require(retained_one && retained_two && retained_two->next() == retained_one->handle(),
                    "leases linked while registry lives");
        }
        require(!retained_one->next() && !retained_one->previous() && !retained_two->next() &&
                    !retained_two->previous(),
                "pool destruction clears retained links");
        const uintptr_t retained_page = uintptr_t(getpagesize());
        require(retained_page && protection(retained_one->handle(), VM_PROT_READ | VM_PROT_WRITE) &&
                    protection(retained_one->handle() - retained_page, VM_PROT_NONE) &&
                    protection(retained_one->handle() + retained_page, VM_PROT_NONE),
                "retained lease keeps guarded image mapping after pool destruction");
        bool image_refused = false, heap_refused = false;
        try {
            GuestImageReservation overlap(retained_one->handle() - retained_page, retained_page);
        } catch (const ElfError&) {
            image_refused = true;
        }
        try {
            GuestHeapReservation overlap(retained_one->handle(), 1);
        } catch (const ElfError&) {
            heap_refused = true;
        }
        require(image_refused && heap_refused &&
                    !registered_guest_heap_contains(retained_one->handle(), 1),
                "retained thread mapping excludes image and heap without becoming guest malloc");
        auto retired_mapping = retained_one->handle() - retained_page;
        retained_one.reset();
        {
            GuestThreadImageReservation released(retired_mapping, retained_page * 3);
        }
        Pool pool(3);
        Pool attributes_pool(2);
        Pool::StackAttributes attrs{
            0x100000, 0x20000,
            0x4000,   uint32_t(b::kPthread_attr_flag_detached | b::kPthread_attr_flag_explicit),
            2,        7};
        auto invalid = attrs;
        invalid.size = UINT64_MAX;
        require(!attributes_pool.publish_with_attributes(90, true, invalid),
                "overflowing stack snapshot refused");
        invalid = attrs;
        invalid.base++;
        require(!attributes_pool.publish_with_attributes(90, true, invalid),
                "misaligned stack snapshot refused");
        invalid = attrs;
        invalid.flags |= b::kPthread_attr_flag_inherit;
        require(!attributes_pool.publish_with_attributes(90, true, invalid),
                "conflicting scheduling flags refused");
        require(!attributes_pool.publish_with_attributes(90, false, attrs) &&
                    attributes_pool.size() == 0,
                "detachment mismatch leaves pool unchanged");
        auto initialized = attributes_pool.publish_with_attributes(90, true, attrs);
        require(initialized && initialized->has_stack_attributes(),
                "complete stack snapshot published");
        auto a = initialized->handle() + b::kPthread_attr;
        require(field<uint32_t>(a, b::kPthread_attr_flags) == attrs.flags &&
                    field<uintptr_t>(a, b::kPthread_attr_stack_base) == attrs.base &&
                    field<uint64_t>(a, b::kPthread_attr_stack_size) == attrs.size &&
                    field<uint64_t>(a, b::kPthread_attr_guard_size) == attrs.guard_size &&
                    field<int32_t>(a, b::kPthread_attr_sched_policy) == attrs.scheduling_policy &&
                    field<int32_t>(a, b::kPthread_attr_sched_priority) ==
                        attrs.scheduling_priority &&
                    field<uint64_t>(a, b::kPthread_attr___reserved) == 0 &&
                    field<uintptr_t>(initialized->handle(), b::kPthread_stack_top) ==
                        attrs.base + attrs.size,
                "guest image stack and scheduling fields use pinned bionic offsets");
        require(attributes_pool.cleanup_complete(initialized->handle(), 0) == O::Ok &&
                    attributes_pool.native_exited(initialized->handle()) == O::Ok &&
                    attributes_pool.size() == 0,
                "initialized detached record retires normally");
        require(!pool.publish(0, false) && !pool.publish(-1, false), "invalid tid refused");
        auto first = pool.publish(11, false), second = pool.publish(12, false),
             third = pool.publish(13, false);
        require(first && second && third && !first->has_stack_attributes() && pool.size() == 3 &&
                    !pool.publish(14, false),
                "bounded publication");
        const uintptr_t page = uintptr_t(getpagesize());
        require(page && !(first->handle() % page) && first->handle() >= page &&
                    protection(first->handle() - page, VM_PROT_NONE) &&
                    protection(first->handle(), VM_PROT_READ | VM_PROT_WRITE) &&
                    protection(first->handle() + page, VM_PROT_NONE),
                "guest image has independent read/write page and inaccessible guards");
        require(second->handle() != first->handle() &&
                    (second->handle() > first->handle()
                         ? second->handle() - first->handle()
                         : first->handle() - second->handle()) >= 3 * page,
                "distinct image mappings cannot share backing or guard pages");
        require(!pool.publish(11, false), "duplicate tid refused");
        require(third->next() == second->handle() && second->next() == first->handle() &&
                    first->previous() == second->handle() &&
                    second->previous() == third->handle() && !first->next() && !third->previous(),
                "head insertion and bidirectional links");
        int32_t raw_tid = 0;
        std::memcpy(&raw_tid, reinterpret_cast<const void*>(first->handle() + b::kPthread_tid),
                    sizeof(raw_tid));
        require(raw_tid == 11 && first->join_state() == b::kPthread_join_not_joined &&
                    pool.lookup(first->handle()) == first && !pool.lookup(0x12345),
                "layout and opaque lookup");
        require(pool.native_exited(first->handle()) == O::NotReady && first->tid() == 11,
                "native exit cannot precede guest cleanup");
        uintptr_t result = 99;
        require(pool.claim_join(first->handle()) == O::Ok &&
                    pool.claim_join(first->handle()) == O::Invalid &&
                    pool.detach(first->handle()) == O::Invalid &&
                    pool.finish_join(first->handle(), &result) == O::NotReady && result == 99,
                "join ownership and pending result");
        require(pool.cleanup_complete(first->handle(), 42) == O::Ok && first->tid() == 11 &&
                    first->join_state() == b::kPthread_join_joined &&
                    pool.cancel_join(first->handle()) == O::Ok &&
                    first->join_state() == b::kPthread_join_exited_not_joined &&
                    pool.detach(first->handle()) == O::NeedsJoin,
                "cleanup, rollback and exited detach");
        auto* guest_result =
            reinterpret_cast<std::atomic<uintptr_t>*>(first->handle() + b::kPthread_return_value);
        guest_result->store(999);
        require(pool.claim_join(first->handle()) == O::Ok &&
                    pool.native_exited(first->handle()) == O::Ok && first->tid() == 0 &&
                    pool.finish_join(first->handle(), &result) == O::Ok && result == 42 &&
                    !pool.lookup(first->handle()) && first->result() == 999 && pool.size() == 2,
                "join retirement and retained lease");
        // Corrupt guest-visible link/state words. Host bookkeeping must use its
        // private sidecar rather than following or trusting guest-written data.
        auto* link = reinterpret_cast<std::atomic<uintptr_t>*>(second->handle() + b::kPthread_next);
        auto* state =
            reinterpret_cast<std::atomic<uint32_t>*>(second->handle() + b::kPthread_join_state);
        auto* tid = reinterpret_cast<std::atomic<int32_t>*>(second->handle() + b::kPthread_tid);
        link->store(0x12345);
        state->store(0xffffffffu);
        tid->store(0);
        require(!pool.publish(12, false), "duplicate identity uses trusted sidecar");
        require(pool.detach(second->handle()) == O::Ok &&
                    pool.cleanup_complete(second->handle(), 0) == O::Ok &&
                    pool.native_exited(second->handle()) == O::Ok &&
                    !pool.lookup(second->handle()) && !third->next() && !third->previous(),
                "detached retirement ignores corrupted guest links");
        require(pool.detach(third->handle()) == O::Ok &&
                    pool.cleanup_complete(third->handle(), 0) == O::Ok &&
                    pool.native_exited(third->handle()) == O::Ok && pool.size() == 0,
                "last record retired");
        // The kernel can reuse a TID after native exit even while the old
        // joinable object remains alive awaiting its owner.
        Pool reuse(2);
        auto old = reuse.publish(77, false);
        require(old && reuse.claim_join(old->handle()) == O::Ok &&
                    reuse.cleanup_complete(old->handle(), 9) == O::Ok &&
                    reuse.native_exited(old->handle()) == O::Ok,
                "old identity exited");
        auto replacement = reuse.publish(77, false);
        require(replacement && replacement->handle() != old->handle() && reuse.size() == 2,
                "exited unjoined TID does not block a new live identity");
        require(reuse.finish_join(old->handle(), nullptr) == O::Ok &&
                    reuse.detach(replacement->handle()) == O::Ok &&
                    reuse.cleanup_complete(replacement->handle(), 0) == O::Ok &&
                    reuse.native_exited(replacement->handle()) == O::Ok && reuse.size() == 0,
                "reused TID objects retire independently");
        for (int i = 0; i < 64; ++i) {
            auto object = pool.publish(100 + i, false);
            require(bool(object), "race object published");
            std::atomic<bool> go{false};
            O joined = O::Missing, detached = O::Missing;
            std::thread a([&] {
                while (!go.load(std::memory_order_acquire))
                    std::this_thread::yield();
                joined = pool.claim_join(object->handle());
            });
            std::thread c([&] {
                while (!go.load(std::memory_order_acquire))
                    std::this_thread::yield();
                detached = pool.detach(object->handle());
            });
            go.store(true, std::memory_order_release);
            a.join();
            c.join();
            require((joined == O::Ok && detached == O::Invalid) ||
                        (joined == O::Invalid && detached == O::Ok),
                    "join and detach have one winner");
            require(pool.cleanup_complete(object->handle(), 7) == O::Ok &&
                        pool.native_exited(object->handle()) == O::Ok,
                    "raced object exits");
            if (joined == O::Ok)
                require(pool.finish_join(object->handle(), nullptr) == O::Ok, "race join retired");
            require(pool.size() == 0, "race leaves no live object");
        }
        Pool concurrent(128), alternate(128);
        std::atomic<int32_t> next_tid{1000};
        std::atomic<bool> ok{true};
        std::vector<std::thread> workers;
        for (int t = 0; t < 8; ++t)
            workers.emplace_back([&, t] {
                Pool& selected = (t & 1) ? alternate : concurrent;
                for (int i = 0; i < 200; ++i) {
                    auto object = selected.publish(next_tid.fetch_add(1), false);
                    if (!object || selected.claim_join(object->handle()) != O::Ok ||
                        selected.cleanup_complete(object->handle(), 1) != O::Ok ||
                        selected.native_exited(object->handle()) != O::Ok ||
                        selected.finish_join(object->handle(), nullptr) != O::Ok)
                        ok = false;
                }
            });
        for (auto& worker : workers)
            worker.join();
        require(ok && concurrent.size() == 0 && alternate.size() == 0,
                "concurrent publication and retirement across pools");
        std::cout << "M5_BIONIC_OBJECT_RESEARCH_PASS layout=1 links=1 lifetime=1 corruption=1 "
                     "races=64 concurrent=1600 guest_execution=0\n";
    } catch (const std::exception& error) {
        std::cerr << "bionic-objects: " << error.what() << '\n';
        return 1;
    }
}
