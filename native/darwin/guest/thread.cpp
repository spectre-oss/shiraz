// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/thread.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/thread_identity.hpp"
#include "guest_io.hpp"
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
#include "shiraz/darwin/guest/bionic_thread_objects.hpp"
#include "guest_tls_layout.hpp"
#include <atomic>
#endif
#include <climits>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <mutex>
#include <optional>
#include <pthread.h>
#include <unistd.h>
#include <sched.h>

namespace shiraz::guest {
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
namespace {
std::atomic<uint64_t> research_thread_captures{0};
}
extern "C" uint64_t shiraz_experimental_thread_captures() noexcept {
    return research_thread_captures.load(std::memory_order_acquire);
}
#endif
namespace {
namespace b = bionic;
struct Attributes {
    size_t stack = 1024 * 1024;
    size_t guard = size_t(getpagesize());
    bool detached = false;
    bool detach_known = true;
    void* base = nullptr;
    bool guard_known = true;
};
struct Record {
    pthread_t host = nullptr;
    uintptr_t guest_handle = 0;
    bool detached = false, finished = false, joining = false, published = false;
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
    std::shared_ptr<BionicThreadObjects> research_pool;
    BionicThreadObjects::Lease research_record;
    bool research_ready = false, research_keyed = false;
#endif
};
Argument pointer(const void* value) {
    return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
// Bionic compares the opaque pthread_t values, without dereferencing them or
// interpreting them as host pthread handles. Keep this independent of the
// current host-backed representation so future guest handles retain the same
// comparison semantics.
int guest_pthread_equal(uintptr_t left, uintptr_t right) noexcept {
    return left == right ? 1 : 0;
}
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
uintptr_t experimental_thread_self() noexcept {
    HostErrno preserve;
    try {
        auto* slots = static_cast<uintptr_t*>(guest_tls_base());
        auto value = __atomic_load_n(&slots[b::kTlsThreadIdSlot], __ATOMIC_ACQUIRE);
        return value ? value : shiraz_guest_cached_pthread_self();
    } catch (...) {
        std::abort();
    }
}
#endif
uintptr_t current_guest_self() noexcept {
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
    return experimental_thread_self();
#else
    return shiraz_guest_cached_pthread_self();
#endif
}
ProviderExport self_export() {
    // No arguments and a pointer result have identical Darwin/AAPCS64 calling
    // conventions. The prepared-stack leaf returns the exact native handle
    // without executing Darwin's pthread_self body on a guest call. No closure,
    // TLV lookup or errno wrapper is needed. Its ABI tag alone grants no analyzer
    // memory-effect exemption; bounded body/data-range proof remains separate.
    static_assert(std::is_pointer_v<pthread_t> && sizeof(pthread_t) == 8);
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
    // The research implementation is C++ code, not one of the exact audited
    // assembly provider entries. Route it through the standard fixed-signature
    // closure so admission authorizes only this bound ABI entry.
    return fixed_export("pthread_self", "LIBC",
                        static_cast<uintptr_t (*)()>(&experimental_thread_self));
#else
    return {"pthread_self",
            "LIBC",
            reinterpret_cast<uintptr_t>(&shiraz_guest_cached_pthread_self),
            SymbolKind::Function,
            "prepared/pthread-self-noargs",
            false};
#endif
}
void* call_guest(void* function, void* argument) {
    try {
        prepare_guest_tls();
        auto domain = AdmissionDomain::current_invocation();
        auto result = domain ? invoke_admitted(std::move(domain), function, {pointer(argument)})
                             : invoke(function, {pointer(argument)});
        return reinterpret_cast<void*>(result.result_integers[0]);
    } catch (const std::exception& error) {
        std::fprintf(stderr, "SHIRAZ_GUEST_THREAD_FAILURE %s\n", error.what());
        std::abort();
    }
}
class Threads : public std::enable_shared_from_this<Threads> {
    struct Start {
        std::shared_ptr<Threads> state;
        std::shared_ptr<Record> record;
        std::shared_ptr<void> retention;
        std::unique_ptr<AdmissionDomain::Reservation> admission;
        void *function, *argument;
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        Attributes creation;
#endif
    };
    std::mutex mutex_;
    std::condition_variable registry_changed_;
    CodeValidator validate_;
    const std::shared_ptr<Lifecycle> lifecycle_;
    std::map<void*, Attributes> attributes_;
    std::map<uintptr_t, std::shared_ptr<Record>> records_;
    // Extracted nodes waiting for a recycled host handle still consume the
    // guest thread quota, even though they are not currently in records_.
    size_t pending_publications_ = 0;
    std::map<uint32_t, GuestThreadKeyHandle> keys_;
    uint32_t next_key_ = 1;
    static void* entry(void* opaque) {
        std::unique_ptr<Start> start(static_cast<Start*>(opaque));
        {
            // A host pthread value can be recycled as soon as another join returns,
            // before that joiner can remove its old guest record. Do not execute
            // guest code until this record has its own published registry entry.
            std::unique_lock<std::mutex> lock(start->state->mutex_);
            start->state->registry_changed_.wait(lock, [&] { return start->record->published; });
        }
        std::optional<AdmissionDomain::Invocation> admission;
        if (start->admission)
            admission.emplace(std::move(start->admission));
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        // Research only: construct the object using the actual guest-created
        // Research only: the child remains gated until its TLS object and parent
        // handle are both published. Foreign/native-exit ownership is unfinished.
        auto research_pool = std::make_shared<BionicThreadObjects>(1);
        BionicThreadObjects::Lease research_record;
        try {
            experimental_begin_guest_thread();
            prepare_guest_tls();
            const size_t page = size_t(getpagesize());
            if (!page || start->creation.guard > SIZE_MAX - (page - 1))
                std::abort();
            const size_t guard = ((start->creation.guard + page - 1) / page) * page;
            BionicThreadObjects::CreationAttributes creation{
                guard, start->creation.detached ? uint32_t(b::kPthread_attr_flag_detached) : 0u, 0,
                0};
            BionicThreadObjects::CurrentSnapshot snapshot{};
            if (BionicThreadObjects::snapshot_current(&creation, &snapshot))
                std::abort();
            research_record = research_pool->publish_with_attributes(
                snapshot.tid, start->creation.detached, snapshot.attributes);
            if (!research_record || !research_record->has_stack_attributes() ||
                research_record->tid() != snapshot.tid)
                std::abort();
            experimental_install_thread_slot(research_record->handle());
            research_thread_captures.fetch_add(1, std::memory_order_release);
            {
                std::unique_lock<std::mutex> lock(start->state->mutex_);
                start->record->research_pool = research_pool;
                start->record->research_record = research_record;
                start->record->research_ready = true;
                start->state->registry_changed_.notify_all();
                start->state->registry_changed_.wait(lock,
                                                     [&] { return start->record->research_keyed; });
            }
        } catch (...) {
            std::abort();
        }
#endif
        void* result = call_guest(start->function, start->argument);
        cleanup_require_empty();
        // Android runs guest TLS/key destructors before removing a detached
        // thread from its pthread registry. Keep Start and its code retention
        // alive across the entire drain as well.
        try {
            finish_guest_thread_tls();
        } catch (const std::exception& error) {
            std::fprintf(stderr, "SHIRAZ_GUEST_THREAD_FAILURE %s\n", error.what());
            std::abort();
        }
        cleanup_require_empty();
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        if (research_pool->cleanup_complete(research_record->handle(),
                                            reinterpret_cast<uintptr_t>(result)) !=
            BionicThreadObjects::Outcome::Ok)
            std::abort();
#endif
        {
            std::lock_guard<std::mutex> lock(start->state->mutex_);
            start->record->finished = true;
            if (start->record->detached)
                start->state->records_.erase(start->record->guest_handle);
        }
        return result;
    }
    Attributes* attr(void* address) {
        auto found = attributes_.find(address);
        return found == attributes_.end() ? nullptr : &found->second;
    }
    // Caller holds mutex_. Guest handles are opaque keys; only the validated
    // record or the current prepared thread can supply a native pthread value.
    pthread_t resolve_locked(uintptr_t guest_handle) const noexcept {
        auto found = records_.find(guest_handle);
        if (found != records_.end())
            return found->second->host;
        if (guest_handle == current_guest_self())
            return pthread_self();
        return nullptr;
    }

  public:
    explicit Threads(CodeValidator validate, std::shared_ptr<Lifecycle> lifecycle)
        : validate_(std::move(validate)), lifecycle_(std::move(lifecycle)) {}
    int attr_init(void* address) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!address || attributes_.count(address))
            return EINVAL;
        if (attributes_.size() >= 4096)
            return EAGAIN;
        attributes_.emplace(address, Attributes{});
        std::memset(address, 0, b::kPthreadAttrSize);
        return 0;
    }
    int attr_destroy(void* address) {
        std::lock_guard<std::mutex> lock(mutex_);
        return attributes_.erase(address) ? 0 : EINVAL;
    }
    int attr_setdetach(void* address, int state) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value ||
            (state != b::kPTHREAD_CREATE_JOINABLE && state != b::kPTHREAD_CREATE_DETACHED))
            return EINVAL;
        value->detached = state == b::kPTHREAD_CREATE_DETACHED;
        value->detach_known = true;
        return 0;
    }
    int attr_getdetach(void* address, int* result) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value || !result)
            return EINVAL;
        if (!value->detach_known)
            return ENOTSUP;
        *result = value->detached ? b::kPTHREAD_CREATE_DETACHED : b::kPTHREAD_CREATE_JOINABLE;
        return 0;
    }
    int attr_setstack(void* address, size_t size) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value || size < size_t(b::kPTHREAD_STACK_MIN))
            return EINVAL;
        if (size > (size_t(64) << 20))
            return EAGAIN;
        value->stack = size;
        return 0;
    }
    int attr_getstack(void* address, size_t* size) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value || !size)
            return EINVAL;
        *size = value->stack;
        return 0;
    }
    int get_attributes(uintptr_t guest_handle, void* address) {
        if (!address)
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        const auto record = records_.find(guest_handle);
        pthread_t host = resolve_locked(guest_handle);
        if (!host)
            return ESRCH;
        if (attributes_.count(address))
            return EBUSY;
        if (attributes_.size() >= 4096)
            return EAGAIN;
        void* top = pthread_get_stackaddr_np(host);
        size_t size = pthread_get_stacksize_np(host);
        if (!top || !size)
            return ESRCH;
        Attributes value;
        value.stack = size;
        value.base = static_cast<char*>(top) - size;
        value.guard_known = false;
        // A foreign native self handle has no guest-owned detach-state record.
        // Preserve that uncertainty rather than fabricating JOINABLE.
        value.detach_known = record != records_.end();
        if (value.detach_known)
            value.detached = record->second->detached;
        attributes_.emplace(address, value);
        std::memset(address, 0, b::kPthreadAttrSize);
        return 0;
    }
    int attr_getstack_address(void* address, void** base, size_t* size) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value || !base || !size)
            return EINVAL;
        *base = value->base;
        *size = value->stack;
        return 0;
    }
    int set_name(uintptr_t guest_handle, const char* name) {
        if (!name)
            return EINVAL;
        if (strnlen(name, 16) == 16)
            return ERANGE;
        if (guest_handle != current_guest_self())
            return ENOTSUP;
        return pthread_setname_np(name);
    }
    int set_schedule(uintptr_t guest_handle, int policy, const void* parameters) {
        if (!parameters)
            return EINVAL;
        std::lock_guard<std::mutex> lock(mutex_);
        pthread_t host = resolve_locked(guest_handle);
        if (!host)
            return ESRCH;
        int native = policy == 0   ? SCHED_OTHER
                     : policy == 1 ? SCHED_FIFO
                     : policy == 2 ? SCHED_RR
                                   : -1;
        if (native < 0)
            return EINVAL;
        int32_t priority;
        std::memcpy(&priority, parameters, 4);
        sched_param value{};
        value.sched_priority = priority;
        return pthread_setschedparam(host, native, &value);
    }
    int thread_signal(int process, int tid, int signal) {
        if (process != ::getpid() || tid <= 0)
            return ESRCH;
        uint64_t target = 0;
        if (shiraz_android_tid_to_native(tid, &target))
            return ESRCH;
        uint64_t own = 0;
        if (!pthread_threadid_np(nullptr, &own) && own == target)
            return guest_thread_signal(pthread_self(), signal);
        std::lock_guard<std::mutex> lock(mutex_);
        for (const auto& entry : records_) {
            uint64_t candidate = 0;
            if (!entry.second->finished && !pthread_threadid_np(entry.second->host, &candidate) &&
                candidate == target)
                return guest_thread_signal(entry.second->host, signal);
        }
        return ESRCH;
    }
    int attr_setguard(void* address, size_t size) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value)
            return EINVAL;
        if (size > (size_t(64) << 20))
            return EAGAIN;
        value->guard = size;
        value->guard_known = true;
        return 0;
    }
    int attr_getguard(void* address, size_t* size) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto* value = attr(address);
        if (!value || !size)
            return EINVAL;
        if (!value->guard_known)
            return ENOTSUP;
        *size = value->guard;
        return 0;
    }
    int create(uint64_t* output, void* address, void* function, void* argument) {
        if (!output || !function)
            return EINVAL;
        // Admission precedes captured-code validation and child publication. The
        // child owns this token through invoke, cleanup and its wrapper return.
        // Native TSD callbacks have their own queued/selected retention tokens.
        auto retention = lifecycle_ ? lifecycle_->retain_thread_callback() : nullptr;
        if (lifecycle_ && !retention)
            return EINVAL;
        if (!validate_(function))
            return EINVAL;
        // Bind the worker to the callback's owning namespace. A provider can be
        // called by a host thread or while an unrelated loader is active; neither
        // is evidence that the current invocation owns this callback.
        std::shared_ptr<AdmissionDomain> owner;
        try {
            owner = lifecycle_ ? lifecycle_->callback_admission()
                               : AdmissionDomain::current_invocation();
        } catch (const ElfError&) {
            return EINVAL;
        }
        std::unique_ptr<AdmissionDomain::Reservation> admission;
        if (owner) {
            if (AdmissionDomain::current_invocation()) {
                admission = AdmissionDomain::reserve_current();
            } else {
                try {
                    AdmissionDomain::Invocation host_entry(owner);
                    admission = AdmissionDomain::reserve_current();
                } catch (const ElfError&) {
                    return EAGAIN;
                }
            }
            if (!admission)
                return EAGAIN;
        }
        std::unique_lock<std::mutex> lock(mutex_);
        Attributes config;
        if (address) {
            auto* found = attr(address);
            if (!found)
                return EINVAL;
            config = *found;
        }
        // A getattr snapshot describes an existing native stack. Custom-stack
        // creation is unsupported: never silently discard that address, nor
        // fabricate values for snapshot fields whose native state is unknown.
        if (config.base || !config.detach_known || !config.guard_known)
            return ENOTSUP;
        // Bionic preserves the requested size even when it is not page aligned.
        // Darwin requires a host-page multiple, so round only the allocation.
        const int page_size = ::getpagesize();
        if (page_size <= 0)
            return EAGAIN;
        const size_t page = size_t(page_size);
        if (config.stack > SIZE_MAX - (page - 1))
            return EAGAIN;
        const size_t native_stack = ((config.stack + page - 1) / page) * page;
        if (native_stack > (size_t(64) << 20))
            return EAGAIN;
        if (records_.size() + pending_publications_ >= 128)
            return EAGAIN;
        pthread_attr_t host;
        int error = ::pthread_attr_init(&host);
        if (error)
            return error;
        struct Cleanup {
            pthread_attr_t* value;
            ~Cleanup() {
                ::pthread_attr_destroy(value);
            }
        } cleanup{&host};
        if ((error = ::pthread_attr_setstacksize(&host, native_stack)) ||
            (error = ::pthread_attr_setguardsize(&host, config.guard)) ||
            (error = ::pthread_attr_setdetachstate(
                 &host, config.detached ? PTHREAD_CREATE_DETACHED : PTHREAD_CREATE_JOINABLE)))
            return error;
        auto record = std::make_shared<Record>();
        record->detached = config.detached;
        auto start = std::make_unique<Start>(Start{shared_from_this(), record, std::move(retention),
                                                   std::move(admission), function, argument
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
                                                   ,
                                                   config
#endif
        });
        // Reserve the registry node before creating a live thread.
        records_.emplace(0, record);
        error = ::pthread_create(&record->host, &host, entry, start.get());
        if (error) {
            records_.erase(0);
            return error;
        }
        start.release();
        auto node = records_.extract(0);
        ++pending_publications_;
        // The current representation still uses the native value. Store it as a
        // separate key so a future bionic object need not be a host pthread_t.
        uintptr_t id = reinterpret_cast<uintptr_t>(record->host);
        record->guest_handle = id;
        node.key() = id;
        for (;;) {
            auto previous = records_.find(id);
            if (previous == records_.end())
                break;
            // A joinable host pthread cannot be recycled before successful join.
            // The joiner must finish retiring that exact old record; waiting drops
            // the registry mutex, while the new child remains at its entry gate.
            if (!previous->second->joining) {
                std::fprintf(stderr,
                             "SHIRAZ_GUEST_FATAL unexpected live pthread handle collision\n");
                std::abort();
            }
            registry_changed_.wait(lock);
        }
        if (!records_.insert(std::move(node)).inserted || !pending_publications_)
            std::abort();
        --pending_publications_;
        record->published = true;
        registry_changed_.notify_all();
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        registry_changed_.wait(lock, [&] { return record->research_ready; });
        const uintptr_t object_handle = record->research_record->handle();
        auto research_node = records_.extract(id);
        if (research_node.empty() || research_node.mapped() != record)
            std::abort();
        research_node.key() = object_handle;
        if (!records_.insert(std::move(research_node)).inserted)
            std::abort();
        id = object_handle;
        record->guest_handle = id;
        record->research_keyed = true;
        registry_changed_.notify_all();
#endif
        std::memcpy(output, &id, sizeof(id));
        return 0;
    }
    int join(uint64_t id, void** result) {
        if (id == current_guest_self())
            return EDEADLK;
        std::shared_ptr<Record> record;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto found = records_.find(id);
            if (found == records_.end())
                return ESRCH;
            record = found->second;
            if (::pthread_equal(record->host, ::pthread_self()))
                return EDEADLK;
            if (record->detached || record->joining)
                return EINVAL;
            record->joining = true;
        }
        int error = ::pthread_join(record->host, result);
        std::lock_guard<std::mutex> lock(mutex_);
        if (!error) {
            auto found = records_.find(id);
            if (found == records_.end() || found->second != record)
                std::abort();
            records_.erase(found);
        } else
            record->joining = false;
        registry_changed_.notify_all();
        return error;
    }
    int detach(uint64_t id) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto found = records_.find(id);
        if (found == records_.end())
            return ESRCH;
        auto record = found->second;
        if (record->detached || record->joining)
            return EINVAL;
        int error = ::pthread_detach(record->host);
        if (!error) {
            record->detached = true;
            if (record->finished)
                records_.erase(found);
        }
        return error;
    }
    int key_create(uint32_t* output, void* destructor) {
        if (!output)
            return EINVAL;
        // Even key creation with no initial value can race captured code
        // validation. Empty keys do not retain code after publication.
        auto retention = lifecycle_ ? lifecycle_->retain_thread_callback() : nullptr;
        if (lifecycle_ && !retention)
            return EINVAL;
        if (destructor && !validate_(destructor))
            return EINVAL;
        GuestThreadKeyHandle handle;
        try {
            handle = make_guest_thread_key(destructor, lifecycle_);
        } catch (const ElfError&) {
            return EINVAL;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        if (keys_.size() >= 4096 || next_key_ > INT32_MAX)
            return EAGAIN;
        const uint32_t guest = next_key_;
        keys_.emplace(guest, std::move(handle));
        ++next_key_;
        std::memcpy(output, &guest, sizeof(guest));
        return 0;
    }
    int key_delete(uint32_t key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = keys_.find(key);
        if (it == keys_.end())
            return EINVAL;
        delete_guest_thread_key(it->second);
        keys_.erase(it);
        return 0;
    }
    int set(uint32_t key, const void* value) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = keys_.find(key);
        return it == keys_.end() ? EINVAL : set_guest_thread_value(it->second, value);
    }
    void* get(uint32_t key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = keys_.find(key);
        return it == keys_.end() ? nullptr : guest_thread_value(it->second);
    }
};
template <typename R, typename... Args>
ProviderExport method(const char* name, std::shared_ptr<Threads> state,
                      R (Threads::*function)(Args...)) {
    void* address = closure([state, function](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        std::tuple<Args...> args{cursor.read<Args>()...};
        HostErrno preserve;
        R result{};
        try {
            result =
                std::apply([&](Args... values) { return ((*state).*function)(values...); }, args);
        } catch (const std::bad_alloc&) {
            if constexpr (std::is_integral_v<R>)
                result = EAGAIN;
        }
        if constexpr (std::is_integral_v<R>)
            if (result)
                result = errno_to_guest(result);
        return_value(capture, result);
    });
    return {name,
            "LIBC",
            reinterpret_cast<uintptr_t>(address),
            SymbolKind::Function,
            "pthread/translated",
            false};
}
} // namespace
std::vector<ProviderExport> thread_exports(CodeValidator validate,
                                           std::shared_ptr<Lifecycle> lifecycle) {
    if (!validate)
        throw ElfError("pthread adapters require guest code validation");
    auto state = std::make_shared<Threads>(std::move(validate), std::move(lifecycle));
    std::vector<ProviderExport> result{
        method("pthread_attr_init", state, &Threads::attr_init),
        method("pthread_attr_destroy", state, &Threads::attr_destroy),
        method("pthread_attr_setdetachstate", state, &Threads::attr_setdetach),
        method("pthread_attr_getdetachstate", state, &Threads::attr_getdetach),
        method("pthread_attr_setstacksize", state, &Threads::attr_setstack),
        method("pthread_attr_getstacksize", state, &Threads::attr_getstack),
        method("pthread_attr_getstack", state, &Threads::attr_getstack_address),
        method("pthread_getattr_np", state, &Threads::get_attributes),
        method("pthread_setname_np", state, &Threads::set_name),
        method("pthread_setschedparam", state, &Threads::set_schedule),
        method("pthread_attr_setguardsize", state, &Threads::attr_setguard),
        method("pthread_attr_getguardsize", state, &Threads::attr_getguard),
        method("pthread_create", state, &Threads::create),
        method("pthread_join", state, &Threads::join),
        method("pthread_detach", state, &Threads::detach),
        method("pthread_key_create", state, &Threads::key_create),
        method("pthread_key_delete", state, &Threads::key_delete),
        method("pthread_setspecific", state, &Threads::set),
        method("pthread_getspecific", state, &Threads::get),
        self_export(),
        fixed_export("pthread_equal", "LIBC", &guest_pthread_equal),
    };
    auto signal = closure([state](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        int process = cursor.read<int>(), tid = cursor.read<int>(), number = cursor.read<int>();
        HostErrno preserve;
        int error = state->thread_signal(process, tid, number);
        if (error)
            *guest_errno_location() = errno_to_guest(error);
        return_value(capture, error ? -1 : 0);
    });
    result.push_back({"tgkill", "LIBC", uintptr_t(signal), SymbolKind::Function,
                      "pthread/owned-thread-signal", false});
    return result;
}
} // namespace shiraz::guest
