// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <dlfcn.h>
#include <thread>
#include <unistd.h>

int main() {
    using Current = int (*)(int*) noexcept;
    using Reverse = int (*)(int, uint64_t*) noexcept;
    Current current = nullptr;
    Reverse reverse = nullptr;
    int worker_tid = 0;
    void* image = nullptr;
    std::thread worker([&] {
        image = dlopen("build/macos/libshiraz-thread-identity.so", RTLD_NOW | RTLD_LOCAL);
        assert(image);
        current = reinterpret_cast<Current>(dlsym(image, "shiraz_android_tid_current"));
        reverse = reinterpret_cast<Reverse>(dlsym(image, "shiraz_android_tid_to_native"));
        assert(current && reverse && !current(&worker_tid));
    });
    worker.join();
    int leader_tid = 0;
    assert(!current(&leader_tid) && leader_tid != getpid() && leader_tid != worker_tid);
    uint64_t native = 123;
    assert(reverse(getpid(), &native) == ESRCH && native == 123);
    assert(!reverse(leader_tid, &native) && native != 0);
    assert(!dlclose(image));
    std::puts("DARWIN_THREAD_IDENTITY_LATE_LOAD_PASS stable_fallback=1");
}
