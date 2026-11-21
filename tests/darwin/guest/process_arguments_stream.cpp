// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/process_arguments_stream.hpp"
#include <array>
#include <atomic>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <memory>
#include <sys/wait.h>
#include <thread>
#include <vector>

int main(int argc, char** argv) {
    assert(argc == 4);
    std::vector<char> expected;
    for (int i = 0; i < argc; ++i)
        expected.insert(expected.end(), argv[i], argv[i] + std::strlen(argv[i]) + 1);
    auto stream = std::make_shared<shiraz::guest::ProcessArgumentsStream>();
    auto alias = stream;
    shiraz::guest::ProcessArgumentsStream independent;
    char bytes[8] = {};
    size_t n = 99;
    int64_t pos = -1;
    assert(!stream->read(bytes, 3, &n) && n == 3 && !std::memcmp(bytes, expected.data(), n));
    assert(!alias->read(bytes, 2, &n) && n == 2 && !std::memcmp(bytes, expected.data() + 3, n));
    assert(!independent.read(bytes, 2, &n) && n == 2 && !std::memcmp(bytes, expected.data(), n));
    assert(!stream->pread(1, bytes, 2, &n) && n == 2 &&
           !std::memcmp(bytes, expected.data() + 1, n));
    assert(!alias->seek(0, SEEK_CUR, &pos) && pos == 5);
    assert(!stream->seek(0, SEEK_END, &pos) && pos == 0);
    assert(!stream->seek(2, SEEK_SET, &pos) && pos == 2);
    assert(stream->seek(-3, SEEK_CUR, &pos) == EINVAL && pos == 2);
    assert(stream->seek(INT64_MAX, SEEK_CUR, &pos) == EINVAL && pos == 2);
    assert(stream->seek(0, 3, &pos) == ENXIO && stream->seek(0, 4, &pos) == ENXIO);
    assert(stream->seek(0, 99, &pos) == EINVAL);
    assert(!stream->seek(0, SEEK_CUR, &pos) && pos == 2);
    n = 99;
    assert(stream->pread(-1, bytes, 1, &n) == EINVAL && n == 99);
    assert(stream->read(nullptr, 1, &n) == EINVAL && n == 99);
    assert(stream->read(reinterpret_cast<void*>(1), 1, &n) == EFAULT && n == 99);
    assert(!stream->seek(0, SEEK_CUR, &pos) && pos == 2);
    assert(!stream->read(nullptr, 0, &n) && n == 0);
    assert(!stream->seek(INT64_MAX, SEEK_SET, &pos));
    assert(!stream->read(bytes, 1, &n) && n == 0);
    size_t first = std::strlen(argv[0]) + 1;
    char saved = argv[1][0];
    argv[1][0] = 'A';
    assert(!stream->pread(static_cast<int64_t>(first), bytes, 1, &n) && n == 1 && bytes[0] == 'A');
    argv[1][0] = saved;
    assert(!stream->seek(0, SEEK_SET, &pos));
    std::array<std::atomic<size_t>, 256> counts{};
    std::array<size_t, 256> wanted{};
    for (unsigned char c : expected)
        ++wanted[c];
    std::vector<std::thread> threads;
    for (int i = 0; i < 8; ++i)
        threads.emplace_back([&] {
            char c;
            size_t got;
            while (true) {
                assert(!stream->read(&c, 1, &got));
                if (!got)
                    break;
                ++counts[static_cast<unsigned char>(c)];
            }
        });
    for (auto& thread : threads)
        thread.join();
    for (size_t i = 0; i < counts.size(); ++i)
        assert(counts[i] == wanted[i]);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        size_t got = 99;
        int64_t offset = 99;
        _exit(stream->read(bytes, 1, &got) == ECHILD &&
                      stream->pread(0, bytes, 1, &got) == ECHILD &&
                      stream->seek(0, SEEK_SET, &offset) == ECHILD && got == 99 && offset == 99
                  ? 0
                  : 1);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    std::puts(
        "DARWIN_PROCESS_ARGUMENTS_STREAM_PASS shared_offset=1 live=1 concurrent=1 fork_closed=1");
}
