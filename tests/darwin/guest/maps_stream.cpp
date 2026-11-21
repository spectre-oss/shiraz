// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/maps_stream.hpp"
#include <cassert>
#include <atomic>
#include <thread>
#include <sys/wait.h>
#include <cstdio>
using namespace shiraz::guest;
int main() {
    std::string data(10000, 'a');
    data += '\n';
    unsigned calls = 0;
    MapsStream stream([&](std::string& out) {
        ++calls;
        out = data;
        return 0;
    });
    std::atomic<unsigned> bytes{0};
    auto work = [&] {
        char c;
        for (;;) {
            size_t n = 77;
            assert(!stream.read(&c, 1, &n));
            if (!n)
                break;
            assert(c == 'a' || c == '\n');
            ++bytes;
        }
    };
    std::thread a(work), b(work);
    a.join();
    b.join();
    assert(bytes == data.size() && calls == 1);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        char c;
        size_t n = 99;
        _exit(stream.read(&c, 1, &n) == ECHILD && n == 99 ? 0 : 1);
    }
    int status;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    int64_t p = -1;
    assert(!stream.seek(0, SEEK_SET, &p));
    data = "changed\n";
    char out[32]{};
    size_t n = 0;
    assert(!stream.read(out, sizeof out, &n) && std::string(out, n) == data && calls == 2);
    assert(stream.seek(0, SEEK_END, &p) == EINVAL);
    assert(!stream.seek(0, SEEK_CUR, &p) && p == 8);
    assert(stream.pread(-1, out, 1, &n) == EINVAL);
    puts("MAPS_STREAM_PASS concurrent_consumption=10001 snapshots=2 fork_refused=1 "
         "seek_end_refused=1 rewind_refresh=1");
}
