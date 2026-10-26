// SPDX-License-Identifier: GPL-3.0-only
#include <assert.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>
extern void *bionic___memchr_chk(const void *, int, size_t, size_t);
int main(void) {
    const char bytes[] = {'a', 'b', 'c'};
    assert(bionic___memchr_chk(bytes, 'b', 3, 3) == bytes + 1);
    assert(bionic___memchr_chk(bytes, 'z', 3, 3) == NULL);
    assert(bionic___memchr_chk(bytes, 'a', 0, 0) == NULL);
    struct rlimit limit = {0, 0};
    assert(setrlimit(RLIMIT_CORE, &limit) == 0);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        bionic___memchr_chk(bytes, 'a', 4, 3);
        _exit(99);
    }
    int status;
    assert(waitpid(child, &status, 0) == child);
    assert(WIFSIGNALED(status) && WTERMSIG(status) == SIGABRT);
    puts("BIONIC_CHECKED_MEMCHR_OK");
    return 0;
}
