// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/process_arguments.hpp"
#include <cassert>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

int main(int argc, char** argv) {
    assert(argc == 4 && !std::strcmp(argv[1], "alpha") && !std::strcmp(argv[2], "") &&
           !std::strcmp(argv[3], "omega"));
    std::vector<char> expected;
    for (int i = 0; i < argc; ++i)
        expected.insert(expected.end(), argv[i], argv[i] + std::strlen(argv[i]) + 1);
    std::vector<char> output(1024 * 1024);
    size_t size = 17;
    errno = EDOM;
    assert(!shiraz_process_arguments_read(0, output.data(), output.size(), &size));
    assert(errno == EDOM);
    assert(size == expected.size() && !std::memcmp(output.data(), expected.data(), size));
    size_t first = std::strlen(argv[0]) + 1;
    size = 0;
    assert(!shiraz_process_arguments_read(first + 1, output.data(), 3, &size));
    assert(size == 3 && !std::memcmp(output.data(), "lph", 3));
    char original = argv[1][0];
    argv[1][0] = 'A';
    assert(!shiraz_process_arguments_read(first, output.data(), 1, &size) && size == 1 &&
           output[0] == 'A');
    argv[1][0] = original;
    assert(!shiraz_process_arguments_read(UINT64_MAX, output.data(), 1, &size) && size == 0);
    assert(!shiraz_process_arguments_read(0, nullptr, 0, &size) && size == 0);
    size = 81;
    assert(shiraz_process_arguments_read(0, nullptr, 1, &size) == EINVAL && size == 81);
    assert(shiraz_process_arguments_read(0, output.data(), output.size() + 1, &size) == E2BIG &&
           size == 81);
    assert(shiraz_process_arguments_read(0, reinterpret_cast<void*>(1), 1, &size) == EFAULT &&
           size == 81);
    assert(errno == EDOM);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        size_t value = 91;
        _exit(shiraz_process_arguments_read(0, output.data(), 1, &value) == ECHILD && value == 91
                  ? 0
                  : 1);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    std::puts("DARWIN_PROCESS_ARGUMENTS_PASS live=1 nul_layout=1 bounded=1 fork_closed=1");
}
