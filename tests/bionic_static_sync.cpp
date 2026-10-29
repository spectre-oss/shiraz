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
extern "C" void* mmap64(void* address, size_t size, int protection, int flags, int fd, off64_t offset) {
    return map(address, size, protection, flags, fd, offset);
}

struct BionicMutex { alignas(void*) int32_t storage[10]; };
int main(int argc, char** argv) {
    assert(argc == 2);
    alarm(30); // A lost lock/wakeup is a test failure, not an unbounded hang.
    void* library = dlopen(argv[1], RTLD_NOW);
    if (!library) { std::fprintf(stderr, "%s\n", dlerror()); return 2; }
    auto lock = reinterpret_cast<int (*)(BionicMutex*)>(dlsym(library, "bionic_pthread_mutex_lock"));
    auto unlock = reinterpret_cast<int (*)(BionicMutex*)>(dlsym(library, "bionic_pthread_mutex_unlock"));
    auto destroy = reinterpret_cast<int (*)(BionicMutex*)>(dlsym(library, "bionic_pthread_mutex_destroy"));
    assert(lock && unlock && destroy);
    for (int round = 0; round < 100; ++round) {
        BionicMutex mutex{}; // Android's static normal-mutex initializer.
        std::atomic<int> inside{0}, violations{0};
        pthread_barrier_t start;
        assert(pthread_barrier_init(&start, nullptr, 8) == 0);
        std::vector<std::thread> threads;
        for (int i = 0; i < 8; ++i) threads.emplace_back([&] {
            pthread_barrier_wait(&start);
            delayAllocation = true;
            for (int j = 0; j < 100; ++j) {
                assert(lock(&mutex) == 0);
                if (inside.fetch_add(1) != 0) violations.fetch_add(1);
                std::this_thread::yield();
                inside.fetch_sub(1);
                assert(unlock(&mutex) == 0);
            }
        });
        for (auto& thread : threads) thread.join();
        assert(pthread_barrier_destroy(&start) == 0);
        if (violations.load()) {
            std::fprintf(stderr, "first-use mutex overlap: round=%d violations=%d\n", round, violations.load());
            return 1;
        }
        assert(destroy(&mutex) == 0);
    }
    assert(delayedAllocations.load() > 0); // Confirm the scheduling probe ran.
    dlclose(library);
    alarm(0);
    puts("BIONIC_STATIC_MUTEX_CONCURRENT_FIRST_USE_OK");
}
