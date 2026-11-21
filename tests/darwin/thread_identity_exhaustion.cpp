// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/thread_identity.hpp"
#include <cassert>
#include <cerrno>
#include <cstdio>
#include <set>

int main() {
    std::set<int> ids;
    for (unsigned long long native = 1; native <= 254; ++native) {
        int tid = 0;
        assert(!shiraz_android_tid_for_native(native, &tid));
        assert(tid > 0 && tid < 256 && tid != 42 && ids.insert(tid).second);
    }
    int value = 71;
    assert(shiraz_android_tid_for_native(255, &value) == EAGAIN && value == 71);
    unsigned long long native = 0;
    for (int tid : ids) {
        uint64_t result = 0;
        assert(!shiraz_android_tid_to_native(tid, &result));
        assert(result >= 1 && result <= 254);
        native += result;
    }
    assert(native == 254ULL * 255 / 2);
    std::puts("DARWIN_THREAD_IDENTITY_EXHAUSTION_PASS slots=254 collision_safe=1");
}
