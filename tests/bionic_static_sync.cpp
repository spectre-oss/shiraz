// SPDX-License-Identifier: GPL-3.0-only
#include <atomic>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <thread>
#include <unistd.h>
#include <vector>

// Force a legitimate preemption point inside the pinned adapter's lazy host
// allocation. Without this, a single-CPU VM often serializes the first calls.
static thread_local bool delayAllocation;
static std::atomic<unsigned> delayedAllocations{0};
static void* map(void* address, size_t size, int protection, int flags, int fd, off_t offset) {
    if (delayAllocation && size == sizeof(pthread_mutex_t)) {
        delayAllocation = false;
        delayedAllocations.fetch_add(1);
        usleep(25000);
    }
    return reinterpret_cast<void*>(syscall(SYS_mmap, address, size, protection, flags, fd, offset));
}
extern "C" void* mmap(void* address, size_t size, int protection, int flags, int fd, off_t offset) {
    return map(address, size, protection, flags, fd, offset);
}
extern "C" void* mmap64(void* address, size_t size, int protection, int flags, int fd,
                        off64_t offset) {
    return map(address, size, protection, flags, fd, offset);
}

struct BionicMutex {
    alignas(void*) int32_t storage[10];
};
struct UnalignedBionicMutex {
    alignas(void*) int32_t pad;
    int32_t storage[10];
};
struct UnalignedBionicCond {
    alignas(void*) int32_t pad;
    int32_t storage[12];
};
struct UnalignedBionicRwlock {
    alignas(void*) int32_t pad;
    int32_t storage[14];
};
struct UnalignedBionicSem {
    alignas(void*) int32_t pad;
    uint32_t storage[4];
};

template <typename Fn> static Fn loadSymbol(void* library, const char* name) {
    auto fn = reinterpret_cast<Fn>(dlsym(library, name));
    assert(fn != nullptr);
    return fn;
}

static int runConcurrentFirstUse(int (*lock)(void*), int (*unlock)(void*), int (*destroy)(void*),
                                 void* mutex, int rounds, int32_t initWord) {
    for (int round = 0; round < rounds; ++round) {
        auto* words = reinterpret_cast<int32_t*>(mutex);
        for (int k = 0; k < 10; ++k)
            words[k] = 0;
        words[0] = initWord;
        std::atomic<int> inside{0}, violations{0};
        pthread_barrier_t start;
        assert(pthread_barrier_init(&start, nullptr, 8) == 0);
        std::vector<std::thread> threads;
        for (int i = 0; i < 8; ++i)
            threads.emplace_back([&] {
                pthread_barrier_wait(&start);
                delayAllocation = true;
                for (int j = 0; j < 100; ++j) {
                    assert(lock(mutex) == 0);
                    if (inside.fetch_add(1) != 0)
                        violations.fetch_add(1);
                    std::this_thread::yield();
                    inside.fetch_sub(1);
                    assert(unlock(mutex) == 0);
                }
            });
        for (auto& thread : threads)
            thread.join();
        assert(pthread_barrier_destroy(&start) == 0);
        if (violations.load()) {
            std::fprintf(stderr, "first-use mutex overlap: round=%d violations=%d\n", round,
                         violations.load());
            return 1;
        }
        assert(destroy(mutex) == 0);
    }
    return 0;
}

int main(int argc, char** argv) {
    assert(argc == 2);
    alarm(30); // A lost lock/wakeup is a test failure, not an unbounded hang.
    void* library = dlopen(argv[1], RTLD_NOW);
    if (!library) {
        std::fprintf(stderr, "%s\n", dlerror());
        return 2;
    }
    auto lock = loadSymbol<int (*)(void*)>(library, "bionic_pthread_mutex_lock");
    auto unlock = loadSymbol<int (*)(void*)>(library, "bionic_pthread_mutex_unlock");
    auto destroy = loadSymbol<int (*)(void*)>(library, "bionic_pthread_mutex_destroy");
    auto condWait = loadSymbol<int (*)(void*, void*)>(library, "bionic_pthread_cond_wait");
    auto condSignal = loadSymbol<int (*)(void*)>(library, "bionic_pthread_cond_signal");
    auto condDestroy = loadSymbol<int (*)(void*)>(library, "bionic_pthread_cond_destroy");
    auto rwRdlock = loadSymbol<int (*)(void*)>(library, "bionic_pthread_rwlock_rdlock");
    auto rwWrlock = loadSymbol<int (*)(void*)>(library, "bionic_pthread_rwlock_wrlock");
    auto rwUnlock = loadSymbol<int (*)(void*)>(library, "bionic_pthread_rwlock_unlock");
    auto rwDestroy = loadSymbol<int (*)(void*)>(library, "bionic_pthread_rwlock_destroy");
    auto semInit = loadSymbol<int (*)(void*, int, unsigned int)>(library, "bionic_sem_init");
    auto semPost = loadSymbol<int (*)(void*)>(library, "bionic_sem_post");
    auto semWait = loadSymbol<int (*)(void*)>(library, "bionic_sem_wait");
    auto semDestroy = loadSymbol<int (*)(void*)>(library, "bionic_sem_destroy");

    BionicMutex alignedMutex{};
    if (runConcurrentFirstUse(lock, unlock, destroy, &alignedMutex, 100, 0) != 0)
        return 1;

    // Android Bionic LP64 sync objects have 4-byte alignment (int32_t arrays), so
    // NDK libraries routinely place them at 4 mod 8 addresses (e.g. libc++_shared.so).
    // On ARM64, unaligned 64-bit atomic load/store faults with SIGBUS (BUS_ADRALN).
    UnalignedBionicMutex unalignedMutex{};
    assert(reinterpret_cast<uintptr_t>(unalignedMutex.storage) % sizeof(void*) == 4);
    if (runConcurrentFirstUse(lock, unlock, destroy, unalignedMutex.storage, 40, 0) != 0)
        return 1;

    // Verify static recursive mutex initializer at 4 mod 8 (matches Bionic's
    // PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP = ((1 & 3) << 14)).
    UnalignedBionicMutex unalignedRecursive{};
    assert(reinterpret_cast<uintptr_t>(unalignedRecursive.storage) % sizeof(void*) == 4);
    unalignedRecursive.storage[0] = (PTHREAD_MUTEX_RECURSIVE & 3) << 14;
    assert(lock(unalignedRecursive.storage) == 0);
    assert(lock(unalignedRecursive.storage) == 0);
    assert(unlock(unalignedRecursive.storage) == 0);
    assert(unlock(unalignedRecursive.storage) == 0);
    assert(destroy(unalignedRecursive.storage) == 0);

    // Verify 4-byte-aligned condvar + mutex wait/signal/destroy.
    UnalignedBionicCond unalignedCond{};
    assert(reinterpret_cast<uintptr_t>(unalignedCond.storage) % sizeof(void*) == 4);
    bool ready = false;
    std::thread notifier([&] {
        assert(lock(unalignedMutex.storage) == 0);
        ready = true;
        assert(condSignal(unalignedCond.storage) == 0);
        assert(unlock(unalignedMutex.storage) == 0);
    });
    assert(lock(unalignedMutex.storage) == 0);
    while (!ready)
        assert(condWait(unalignedCond.storage, unalignedMutex.storage) == 0);
    assert(unlock(unalignedMutex.storage) == 0);
    notifier.join();
    assert(condDestroy(unalignedCond.storage) == 0);
    assert(destroy(unalignedMutex.storage) == 0);

    // Verify 4-byte-aligned rwlock and semaphore.
    UnalignedBionicRwlock unalignedRw{};
    assert(reinterpret_cast<uintptr_t>(unalignedRw.storage) % sizeof(void*) == 4);
    assert(rwRdlock(unalignedRw.storage) == 0);
    assert(rwUnlock(unalignedRw.storage) == 0);
    assert(rwWrlock(unalignedRw.storage) == 0);
    assert(rwUnlock(unalignedRw.storage) == 0);
    assert(rwDestroy(unalignedRw.storage) == 0);

    UnalignedBionicSem unalignedSem{};
    assert(reinterpret_cast<uintptr_t>(unalignedSem.storage) % sizeof(void*) == 4);
    assert(semInit(unalignedSem.storage, 0, 0) == 0);
    assert(semPost(unalignedSem.storage) == 0);
    assert(semWait(unalignedSem.storage) == 0);
    assert(semDestroy(unalignedSem.storage) == 0);

    assert(delayedAllocations.load() > 0); // Confirm the scheduling probe ran.
    dlclose(library);
    alarm(0);
    puts("BIONIC_STATIC_MUTEX_CONCURRENT_FIRST_USE_OK");
}
