// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>
extern "C" int ASharedMemory_create(const char*, size_t);
extern "C" size_t ASharedMemory_getSize(int);
extern "C" int ASharedMemory_setProt(int, int);
int main() {
    assert(ASharedMemory_create(nullptr, 0) == -EINVAL);
    assert(ASharedMemory_getSize(-1) == 0);
    assert(ASharedMemory_setProt(-1, PROT_READ) == -1);
    int fd = ASharedMemory_create(nullptr, 4096);
    assert(fd >= 0 && ASharedMemory_getSize(fd) == 4096);
    assert(fcntl(fd, F_GETFD) & FD_CLOEXEC);
    assert(ftruncate(fd, 8192) == -1 && ftruncate(fd, 2048) == -1);
    assert(ASharedMemory_setProt(fd, 0x400) == -1 && errno == EINVAL);
    assert(ASharedMemory_setProt(fd, PROT_READ | PROT_WRITE) == 0);
    auto* data = static_cast<char*>(mmap(nullptr, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0));
    assert(data != MAP_FAILED);
    std::strcpy(data, "shared");
    int other = dup(fd);
    assert(other >= 0 && ASharedMemory_getSize(other) == 4096);
    assert(ASharedMemory_setProt(other, PROT_READ) == 0);
    assert(ASharedMemory_setProt(fd, PROT_READ | PROT_WRITE) == -1 && errno == EINVAL);
    assert(mmap(nullptr, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0) == MAP_FAILED);
    assert(pwrite(fd, "x", 1, 0) == -1);
    data[0] = 'S'; // Existing mappings must remain writable.
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        auto* read = static_cast<char*>(mmap(nullptr, 4096, PROT_READ, MAP_SHARED, other, 0));
        assert(read != MAP_FAILED && std::strcmp(read, "Shared") == 0);
        assert(mmap(nullptr, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, other, 0) == MAP_FAILED);
        _exit(0);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 0);
    munmap(data, 4096);
    close(other);
    close(fd);
    std::puts("shared memory: PASS");
}
