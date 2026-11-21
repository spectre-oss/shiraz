// SPDX-License-Identifier: GPL-3.0-only
// This fixture emits inline atomics, without a compiler-rt helper dependency.
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <sched.h>
#include <sys/syscall.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include "guest_tls_layout.hpp"
_Static_assert(SHIRAZ_BIONIC_TLS_THREAD_ID_SLOT == 1, "pinned Android arm64 thread identity slot");
#define CHECK(value)                                                                               \
    do {                                                                                           \
        if (!(value))                                                                              \
            return __LINE__;                                                                       \
    } while (0)
static pthread_key_t key;
static int destroyed;
// bionic marks pthread_self const; prevent CSE/hoisting across the errno
// sentinels so these checks actually cross the provider on every invocation.
static pthread_t (*volatile call_self)(void) = pthread_self;
static int (*volatile call_equal)(pthread_t, pthread_t) = pthread_equal;
extern int __cxa_thread_atexit_impl(void (*)(void*), void*, void*);
struct Work {
    int index, error, destructors;
    pthread_t self;
    int expected_detach, ready, cxx_destructors;
    int gate_cleanup, cleanup_entered, cleanup_go;
};
static void cxx_destructor(void* opaque) {
    struct Work* work = opaque;
    if (work->gate_cleanup) {
        __atomic_store_n(&work->cleanup_entered, 1, __ATOMIC_RELEASE);
        unsigned remaining = 100000000;
        while (!__atomic_load_n(&work->cleanup_go, __ATOMIC_ACQUIRE) && --remaining)
            __asm__ volatile("yield");
        if (!remaining) {
            work->error = 12;
            return;
        }
    }
    pthread_attr_t snapshot;
    int detached = -1;
    if (pthread_getattr_np(call_self(), &snapshot) ||
        pthread_attr_getdetachstate(&snapshot, &detached) || detached != work->expected_detach ||
        pthread_attr_destroy(&snapshot) || pthread_getspecific(key) != work)
        work->error = 9;
    ++work->cxx_destructors;
}
static void destructor(void* opaque) {
    struct Work* work = opaque;
    pthread_attr_t snapshot;
    int detached = -1;
    if (pthread_getattr_np(call_self(), &snapshot) ||
        pthread_attr_getdetachstate(&snapshot, &detached) || detached != work->expected_detach ||
        pthread_attr_destroy(&snapshot))
        work->error = 8;
    if (work->cxx_destructors != 1)
        work->error = 10;
    if (pthread_getspecific(key))
        work->error = 1;
    if (++work->destructors == 1) {
        if (pthread_setspecific(key, work))
            work->error = 2;
    } else
        __atomic_fetch_add(&destroyed, 1, __ATOMIC_RELEASE);
}
static void* worker(void* opaque) {
    struct Work* work = opaque;
    work->self = call_self();
    unsigned remaining = 100000000;
    while (!__atomic_load_n(&work->ready, __ATOMIC_ACQUIRE) && --remaining)
        __asm__ volatile("yield");
    if (!remaining) {
        work->error = 6;
        return work;
    }
    errno = 700 + work->index;
    pthread_attr_t snapshot;
    int detached = -1;
    size_t guard = 123;
    if (pthread_getattr_np(work->self, &snapshot) ||
        pthread_attr_getdetachstate(&snapshot, &detached) || detached != work->expected_detach ||
        pthread_attr_getguardsize(&snapshot, &guard) != ENOTSUP || guard != 123 ||
        pthread_attr_setguardsize(&snapshot, 32768) ||
        pthread_attr_getguardsize(&snapshot, &guard) || guard != 32768 ||
        pthread_attr_destroy(&snapshot))
        work->error = 7;
    if (work->index < 4) {
        pthread_attr_t stack_snapshot;
        size_t actual_stack = 0;
        if (pthread_getattr_np(work->self, &stack_snapshot) ||
            pthread_attr_getstacksize(&stack_snapshot, &actual_stack) ||
            pthread_attr_destroy(&stack_snapshot) || actual_stack < 2 * 1024 * 1024 + 1)
            work->error = 13;
    }
    if (!pthread_equal(call_self(), work->self) || errno != 700 + work->index)
        work->error = 5;
    if (pthread_join(work->self, 0) != EDEADLK)
        work->error = 3;
    if (pthread_getspecific(key) || pthread_setspecific(key, work) ||
        pthread_getspecific(key) != work || errno != 700 + work->index)
        work->error = 4;
    if (__cxa_thread_atexit_impl(cxx_destructor, work, 0))
        work->error = 11;
    return work;
}
uintptr_t self_identity(void) {
    errno = 731;
    pthread_t first = call_self();
    if (!pthread_equal(first, call_self()) || errno != 731)
        return 0;
    return (uintptr_t)first;
}
int compare_opaque_threads(void) {
    // Comparison must not interpret the opaque bits as a Darwin pthread_t.
    const pthread_t a = (pthread_t)(uintptr_t)0x100000001ULL;
    const pthread_t b = (pthread_t)(uintptr_t)0x200000001ULL;
    errno = EDOM;
    CHECK(call_equal(a, a) == 1 && call_equal(a, b) == 0 && call_equal(b, a) == 0 &&
          call_equal(b, b) == 1 && errno == EDOM);
    return 0;
}
static int snapshot_workers;
static void* snapshot_worker(void* opaque) {
    (void)opaque;
    __atomic_add_fetch(&snapshot_workers, 1, __ATOMIC_RELEASE);
    return 0;
}
// Called on the host's preexisting thread, which has no guest-created Record.
int run_native_attributes(void) {
    pthread_attr_t snapshot;
    int detached = 123;
    size_t guard = 123;
    errno = EDOM;
    CHECK(pthread_getattr_np(call_self(), &snapshot) == 0);
    CHECK(pthread_attr_getdetachstate(&snapshot, &detached) == ENOTSUP && detached == 123);
    CHECK(pthread_attr_setdetachstate(&snapshot, PTHREAD_CREATE_DETACHED) == 0 &&
          pthread_attr_getdetachstate(&snapshot, &detached) == 0 &&
          detached == PTHREAD_CREATE_DETACHED);
    CHECK(pthread_attr_getguardsize(&snapshot, &guard) == ENOTSUP && guard == 123);
    CHECK(pthread_attr_setguardsize(&snapshot, 32768) == 0 &&
          pthread_attr_getguardsize(&snapshot, &guard) == 0 && guard == 32768);
    // Resolving unknown scalar fields does not permit cloning the snapshot's
    // existing native stack. Keep an unexpected worker joinable for safe cleanup.
    CHECK(pthread_attr_setdetachstate(&snapshot, PTHREAD_CREATE_JOINABLE) == 0);
    __atomic_store_n(&snapshot_workers, 0, __ATOMIC_RELEASE);
    const pthread_t sentinel = (pthread_t)(uintptr_t)0x12345;
    pthread_t unexpected = sentinel;
    int created = pthread_create(&unexpected, &snapshot, snapshot_worker, 0);
    if (!created)
        CHECK(pthread_join(unexpected, 0) == 0);
    CHECK(created == ENOTSUP && unexpected == sentinel &&
          __atomic_load_n(&snapshot_workers, __ATOMIC_ACQUIRE) == 0);
    CHECK(pthread_attr_destroy(&snapshot) == 0 && errno == EDOM);
    // Opaque foreign values must be rejected before a host pthread API sees
    // them; the destination and both errno domains remain unchanged.
    pthread_attr_t untouched, original;
    memset(&untouched, 0xa5, sizeof(untouched));
    memcpy(&original, &untouched, sizeof(original));
    struct sched_param parameters = {0};
    CHECK(pthread_getattr_np(sentinel, &untouched) == ESRCH &&
          memcmp(&untouched, &original, sizeof(untouched)) == 0);
    CHECK(pthread_join(sentinel, 0) == ESRCH && pthread_detach(sentinel) == ESRCH);
    CHECK(pthread_setschedparam(sentinel, SCHED_OTHER, &parameters) == ESRCH);
    CHECK(pthread_setname_np(sentinel, "probe") == ENOTSUP && errno == EDOM);
    return 0;
}
int run_threads(void) {
    struct {
        uint64_t before;
        pthread_attr_t value;
        uint64_t after;
    } attribute;
    memset(&attribute, 0xa5, sizeof(attribute));
    errno = EDOM;
    CHECK(pthread_attr_init(&attribute.value) == 0);
    CHECK(attribute.before == 0xa5a5a5a5a5a5a5a5ULL && attribute.after == 0xa5a5a5a5a5a5a5a5ULL);
    int state = -1;
    CHECK(pthread_attr_getdetachstate(&attribute.value, &state) == 0 &&
          state == PTHREAD_CREATE_JOINABLE);
    CHECK(pthread_attr_setdetachstate(&attribute.value, 123) == EINVAL && errno == EDOM);
    CHECK(pthread_attr_setstacksize(&attribute.value, 4096) == EINVAL);
    // Bionic stores a non-page-aligned request; the host allocation may need
    // rounding, but the guest attribute must retain the requested value.
    const size_t requested_stack = 2 * 1024 * 1024 + 1;
    CHECK(pthread_attr_setstacksize(&attribute.value, requested_stack) == 0);
    size_t size = 0;
    CHECK(pthread_attr_getstacksize(&attribute.value, &size) == 0 && size == requested_stack);
    CHECK(pthread_attr_setstacksize(&attribute.value, 64 * 1024 * 1024 + 1) == EAGAIN &&
          pthread_attr_getstacksize(&attribute.value, &size) == 0 && size == requested_stack &&
          errno == EDOM);
    CHECK(pthread_attr_setguardsize(&attribute.value, 32768) == 0 &&
          pthread_attr_getguardsize(&attribute.value, &size) == 0 && size == 32768);
    struct {
        uint32_t before;
        pthread_key_t value;
        uint32_t after;
    } guarded;
    memset(&guarded, 0xa5, sizeof(guarded));
    CHECK(pthread_key_create(&guarded.value, destructor) == 0 && guarded.before == 0xa5a5a5a5 &&
          guarded.after == 0xa5a5a5a5);
    key = guarded.value;
    struct Work work[4] = {{0}};
    pthread_t threads[4];
    for (int i = 0; i < 4; ++i) {
        work[i].index = i;
        work[i].expected_detach = PTHREAD_CREATE_JOINABLE;
        work[i].ready = 1;
        CHECK(pthread_create(&threads[i], &attribute.value, worker, &work[i]) == 0);
    }
    CHECK(pthread_getspecific(key) == 0 && errno == EDOM);
    for (int i = 0; i < 4; ++i) {
        void* result = 0;
        CHECK(pthread_join(threads[i], &result) == 0 && result == &work[i]);
        CHECK(pthread_equal(work[i].self, threads[i]) && work[i].error == 0 &&
              work[i].destructors == 2);
    }
    CHECK(__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) == 4);
    CHECK(pthread_attr_setdetachstate(&attribute.value, PTHREAD_CREATE_DETACHED) == 0 &&
          pthread_attr_getdetachstate(&attribute.value, &state) == 0 &&
          state == PTHREAD_CREATE_DETACHED);
    struct Work detached = {5, 0, 0, 0, PTHREAD_CREATE_DETACHED, 1, 0, 0, 0, 0};
    pthread_t detached_id;
    CHECK(pthread_create(&detached_id, &attribute.value, worker, &detached) == 0);
    unsigned remaining = 100000000;
    while (__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) < 5 && --remaining)
        __asm__ volatile("yield");
    CHECK(remaining && detached.error == 0 && detached.destructors == 2);
    // Convert a joinable thread to detached and wait for its key destructor.
    struct Work converted = {6, 0, 0, 0, PTHREAD_CREATE_DETACHED, 0, 0, 0, 0, 0};
    CHECK(pthread_create(&detached_id, 0, worker, &converted) == 0);
    // The worker is held at its ready gate, so an unrelated live handle must
    // resolve to its owned host thread without modifying scheduling policy.
    pthread_attr_t remote_snapshot;
    int remote_detach = -1;
    struct sched_param remote_parameters = {0};
    CHECK(pthread_getattr_np(detached_id, &remote_snapshot) == 0 &&
          pthread_attr_getdetachstate(&remote_snapshot, &remote_detach) == 0 &&
          remote_detach == PTHREAD_CREATE_JOINABLE && pthread_attr_destroy(&remote_snapshot) == 0);
    CHECK(pthread_setschedparam(detached_id, -123, &remote_parameters) == EINVAL);
    CHECK(pthread_detach(detached_id) == 0);
    __atomic_store_n(&converted.ready, 1, __ATOMIC_RELEASE);
    remaining = 100000000;
    while (__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) < 6 && --remaining)
        __asm__ volatile("yield");
    CHECK(remaining && converted.error == 0 && converted.destructors == 2);
    // Detach while C++ TLS cleanup is active, then inspect the updated state
    // from both C++ and pthread-key destructors before the thread is retired.
    struct Work during_cleanup = {7, 0, 0, 0, PTHREAD_CREATE_JOINABLE, 1, 0, 1, 0, 0};
    pthread_t cleanup_id;
    CHECK(pthread_create(&cleanup_id, 0, worker, &during_cleanup) == 0);
    remaining = 100000000;
    while (!__atomic_load_n(&during_cleanup.cleanup_entered, __ATOMIC_ACQUIRE) && --remaining)
        __asm__ volatile("yield");
    if (!remaining)
        return __LINE__;
    int detach_error = pthread_detach(cleanup_id);
    during_cleanup.expected_detach = PTHREAD_CREATE_DETACHED;
    __atomic_store_n(&during_cleanup.cleanup_go, 1, __ATOMIC_RELEASE);
    remaining = 100000000;
    while (__atomic_load_n(&destroyed, __ATOMIC_ACQUIRE) < 7 && --remaining)
        __asm__ volatile("yield");
    CHECK(!detach_error && remaining && during_cleanup.error == 0 &&
          during_cleanup.cxx_destructors == 1 && during_cleanup.destructors == 2);
    CHECK(pthread_attr_destroy(&attribute.value) == 0);
    CHECK(attribute.before == 0xa5a5a5a5a5a5a5a5ULL && attribute.after == 0xa5a5a5a5a5a5a5a5ULL);
    CHECK(pthread_key_delete(key) == 0 && pthread_key_delete(key) == EINVAL);
    CHECK(pthread_setspecific(key, work) == EINVAL && pthread_getspecific(key) == 0 &&
          errno == EDOM);
    CHECK(pthread_create(&threads[0], 0, (void* (*)(void*))1, 0) == EINVAL);
    CHECK(pthread_join((pthread_t)1, 0) == ESRCH);
    return 0;
}

// Key iteration is guest-owned: no test depends on an order between keys.
// A reset on the final permitted pass must not cause a fifth callback.
static pthread_key_t repeat_key, deleted_key, self_deleted_key;
static int deleted_callbacks;
struct KeyWork {
    int error, repeat_calls, child_calls, self_deleted_calls;
    pthread_key_t child_key;
};
static void child_key_destructor(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_getspecific(work->child_key))
        work->error = 101;
    ++work->child_calls;
}
static void repeat_key_destructor(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_getspecific(repeat_key))
        work->error = 102;
    if (++work->repeat_calls == 1) {
        if (pthread_key_create(&work->child_key, child_key_destructor) ||
            pthread_setspecific(work->child_key, work))
            work->error = 103;
    }
    if (pthread_setspecific(repeat_key, work))
        work->error = 104;
}
static void deleted_key_destructor(void* opaque) {
    (void)opaque;
    __atomic_add_fetch(&deleted_callbacks, 1, __ATOMIC_RELAXED);
}
static void self_deleted_key_destructor(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_getspecific(self_deleted_key) || pthread_key_delete(self_deleted_key) ||
        pthread_setspecific(self_deleted_key, work) != EINVAL ||
        pthread_getspecific(self_deleted_key))
        work->error = 105;
    ++work->self_deleted_calls;
}
static void* key_worker(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_getspecific(repeat_key) || pthread_setspecific(repeat_key, work) ||
        pthread_getspecific(repeat_key) != work)
        work->error = 106;
    return work;
}
static void* deleted_key_worker(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_setspecific(deleted_key, work) || pthread_key_delete(deleted_key) ||
        pthread_getspecific(deleted_key) || pthread_setspecific(deleted_key, work) != EINVAL)
        work->error = 107;
    return work;
}
static void* self_deleted_key_worker(void* opaque) {
    struct KeyWork* work = opaque;
    if (pthread_setspecific(self_deleted_key, work))
        work->error = 108;
    return work;
}
int run_key_lifecycle(void) {
    CHECK(PTHREAD_DESTRUCTOR_ITERATIONS == 4);
    CHECK(pthread_key_create(&repeat_key, repeat_key_destructor) == 0);
    struct KeyWork work[4] = {{0}};
    pthread_t threads[4];
    for (int i = 0; i < 4; ++i)
        CHECK(pthread_create(&threads[i], 0, key_worker, &work[i]) == 0);
    CHECK(pthread_getspecific(repeat_key) == 0);
    for (int i = 0; i < 4; ++i) {
        void* returned = 0;
        CHECK(pthread_join(threads[i], &returned) == 0 && returned == &work[i]);
        CHECK(work[i].error == 0 && work[i].repeat_calls == PTHREAD_DESTRUCTOR_ITERATIONS &&
              work[i].child_calls == 1);
        CHECK(pthread_key_delete(work[i].child_key) == 0);
    }
    CHECK(pthread_key_delete(repeat_key) == 0);
    struct KeyWork deleted = {0};
    CHECK(pthread_key_create(&deleted_key, deleted_key_destructor) == 0);
    CHECK(pthread_create(&threads[0], 0, deleted_key_worker, &deleted) == 0 &&
          pthread_join(threads[0], 0) == 0);
    CHECK(deleted.error == 0 && __atomic_load_n(&deleted_callbacks, __ATOMIC_RELAXED) == 0);
    struct KeyWork self_deleted = {0};
    CHECK(pthread_key_create(&self_deleted_key, self_deleted_key_destructor) == 0);
    CHECK(pthread_create(&threads[0], 0, self_deleted_key_worker, &self_deleted) == 0 &&
          pthread_join(threads[0], 0) == 0);
    CHECK(self_deleted.error == 0 && self_deleted.self_deleted_calls == 1);
    CHECK(pthread_key_delete(self_deleted_key) == EINVAL);
    return 0;
}

// Two independent guest registries may issue identical numeric keys. Values
// belong to their key identity as well as the current host thread.
static pthread_key_t namespace_key;
int key_namespace_create(void) {
    return pthread_key_create(&namespace_key, 0);
}
int key_namespace_set(void* value) {
    return pthread_setspecific(namespace_key, value);
}
void* key_namespace_get(void) {
    return pthread_getspecific(namespace_key);
}
int key_namespace_delete(void) {
    return pthread_key_delete(namespace_key);
}

// Opt-in compatibility diagnostic: the Android arm64 TLS thread slot is the
// pthread_t returned by bionic. Do not dereference a missing/incoherent slot.
struct IdentityObservation {
    uintptr_t slot;
    pthread_t self;
    unsigned fields;
};
static uintptr_t android_thread_slot(void) {
    uintptr_t* base;
    __asm__ volatile("mrs %0, tpidr_el0" : "=r"(base));
    return base[SHIRAZ_BIONIC_TLS_THREAD_ID_SLOT];
}
struct ExperimentalSlotWork {
    pthread_key_t key;
    uintptr_t slot;
    int32_t tid;
    int worker_error;
    int destructor_ok;
};
void experimental_slot_destructor(void* opaque) {
    struct ExperimentalSlotWork* work = opaque;
    uintptr_t slot = android_thread_slot();
    if (!slot || slot != work->slot || slot != (uintptr_t)call_self() || (slot & 7))
        return;
    volatile uintptr_t next = *(const volatile uintptr_t*)slot;
    (void)next;
    int32_t tid = *(const volatile int32_t*)(slot + 16);
    if (tid == work->tid && tid > 0 && syscall(__NR_gettid) == tid)
        work->destructor_ok = 1;
}
void* experimental_slot_worker(void* opaque) {
    struct ExperimentalSlotWork* work = opaque;
    work->slot = android_thread_slot();
    if (!work->slot || work->slot != (uintptr_t)call_self() || (work->slot & 7)) {
        work->worker_error = 1;
        return 0;
    }
    volatile uintptr_t next = *(const volatile uintptr_t*)work->slot;
    (void)next;
    work->tid = *(const volatile int32_t*)(work->slot + 16);
    if (work->tid <= 0 || syscall(__NR_gettid) != work->tid) {
        work->worker_error = 2;
        return 0;
    }
    if (pthread_setspecific(work->key, work))
        work->worker_error = 3;
    return opaque;
}
int run_experimental_tls_object(void) {
    struct ExperimentalSlotWork work = {0};
    pthread_t thread;
    if (pthread_key_create(&work.key, experimental_slot_destructor))
        return 1;
    int create_error = pthread_create(&thread, 0, experimental_slot_worker, &work);
    if (create_error) {
        pthread_key_delete(work.key);
        return 2;
    }
    void* result = 0;
    int join_error = pthread_join(thread, &result);
    int delete_error = pthread_key_delete(work.key);
    if (join_error || delete_error)
        return 3;
    if (work.worker_error || result != &work || !work.destructor_ok ||
        work.slot != (uintptr_t)thread)
        return 4;
    return 0;
}
void* experimental_retained_worker(void* opaque) {
    return opaque;
}
int run_experimental_retained_object(void) {
    pthread_t thread;
    int marker = 17;
    if (pthread_create(&thread, 0, experimental_retained_worker, &marker))
        return 1;
    uintptr_t slot = (uintptr_t)thread;
    int ready = 0;
    // Pinned arm64 bionic join_state offset and THREAD_EXITED_NOT_JOINED value.
    for (int i = 0; i < 100000; ++i) {
        if (__atomic_load_n((const uint32_t*)(slot + 80), __ATOMIC_ACQUIRE) == 1) {
            ready = 1;
            break;
        }
        sched_yield();
    }
    int readable = 0;
    if (ready) {
        volatile uintptr_t next = *(const volatile uintptr_t*)slot;
        (void)next;
        readable = 1;
    }
    void* result = 0;
    int error = pthread_join(thread, &result);
    return error || !ready || !readable || result != &marker ? 2 : 0;
}
static void observe_identity_fields(struct IdentityObservation* observation) {
    if (!observation->slot || observation->slot != (uintptr_t)observation->self ||
        (observation->slot & 7))
        return;
    // The first pointer load is the operation exercised by both target APKs.
    // The pinned Android 14 arm64 layout puts tid at byte offset 16.
    volatile uintptr_t next = *(const volatile uintptr_t*)observation->slot;
    (void)next;
    int32_t tid = *(const volatile int32_t*)(observation->slot + 16);
    observation->fields = 1;
    if (tid > 0 && syscall(__NR_gettid) == tid)
        observation->fields |= 2;
}
void* observe_child_identity(void* opaque) {
    struct IdentityObservation* observation = opaque;
    observation->slot = android_thread_slot();
    observation->self = call_self();
    observe_identity_fields(observation);
    return opaque;
}
unsigned inspect_bionic_thread_identity(void) {
    struct IdentityObservation parent = {android_thread_slot(), call_self(), 0};
    observe_identity_fields(&parent);
    unsigned result =
        (parent.slot != 0) | ((parent.slot != 0 && parent.slot == (uintptr_t)parent.self) << 1);
    struct IdentityObservation child = {0};
    pthread_t thread;
    if (pthread_create(&thread, 0, observe_child_identity, &child))
        return result;
    result |= 4;
    void* returned = 0;
    int join_error = pthread_join(thread, &returned);
    if (join_error) {
        if (join_error == ESRCH)
            return result | 1024;
        if (join_error == EINVAL)
            return result | 2048;
        return result | 4096;
    }
    if (returned != &child)
        return result | 8192;
    result |= 8;
    if (child.slot)
        result |= 16;
    if (child.slot && child.slot == (uintptr_t)child.self && child.self == thread)
        result |= 32;
    if (parent.slot && child.slot && parent.slot != child.slot)
        result |= 64;
    if (parent.fields & 2)
        result |= 128;
    if (child.fields & 2)
        result |= 256;
    if ((parent.fields & 1) && (child.fields & 1))
        result |= 512;
    return result;
}
