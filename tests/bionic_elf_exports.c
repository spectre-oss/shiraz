/* SPDX-License-Identifier: GPL-3.0-only */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/eventfd.h>
#include <stdint.h>
#include <unistd.h>

#define RESOLVE(name, type) \
    type name = (type)dlvsym(library, #name, "SHIRAZ_BIONIC_1.0"); \
    assert(name)

typedef ssize_t (*read_fn)(int, void *, size_t);
typedef int (*open_fn)(const char *, int, ...);
typedef void (*exit_fn)(int);
typedef void *(*malloc_fn)(size_t);
typedef void (*free_fn)(void *);
typedef void *(*mmap_fn)(void *, size_t, int, int, int, off_t);
typedef int (*munmap_fn)(void *, size_t);
typedef int (*close_fn)(int);
typedef pid_t (*fork_fn)(void);
typedef long (*syscall_fn)(long, ...);
typedef int (*prctl_fn)(int, ...);
typedef int (*killpg_fn)(pid_t, int);
typedef int (*eventfd_fn)(unsigned int, int);
typedef int (*inotify_init_fn)(void);
typedef off_t (*lseek_fn)(int, off_t, int);
typedef int (*mprotect_fn)(void *, size_t, int);
typedef long (*sysconf_fn)(int);
typedef char *(*strstr_fn)(const char *, const char *);

int main(int argc, char **argv) {
    assert(argc == 2);
    long host_page_size = sysconf(_SC_PAGESIZE);
    sysconf_fn host_sysconf = (sysconf_fn)dlsym(RTLD_DEFAULT, "sysconf");
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    assert(library);
    assert(dlsym(RTLD_DEFAULT, "sysconf") == (void *)host_sysconf);
    assert(sysconf(_SC_PAGESIZE) == host_page_size);
    RESOLVE(read, read_fn);
    RESOLVE(open, open_fn);
    RESOLVE(exit, exit_fn);
    RESOLVE(malloc, malloc_fn);
    RESOLVE(free, free_fn);
    RESOLVE(mmap, mmap_fn);
    RESOLVE(munmap, munmap_fn);
    RESOLVE(close, close_fn);
    RESOLVE(fork, fork_fn);
    RESOLVE(syscall, syscall_fn);
    RESOLVE(prctl, prctl_fn);
    RESOLVE(killpg, killpg_fn);
    RESOLVE(eventfd, eventfd_fn);
    RESOLVE(inotify_init, inotify_init_fn);
    assert(syscall(SYS_getpid) == getpid());
    assert(prctl(PR_GET_DUMPABLE, 0UL, 0UL, 0UL, 0UL) >= 0);
    assert(killpg(getpgrp(), 0) == 0);
    int event = eventfd(3, EFD_CLOEXEC);
    uint64_t value = 0;
    assert(event >= 0 && read(event, &value, sizeof(value)) == sizeof(value));
    assert(value == 3 && close(event) == 0);
    int watch = inotify_init();
    assert(watch >= 0 && close(watch) == 0);
    RESOLVE(lseek, lseek_fn);
    RESOLVE(mprotect, mprotect_fn);
    RESOLVE(sysconf, sysconf_fn);
    RESOLVE(strstr, strstr_fn);
    /* Android's _SC_PAGESIZE has a different numeric value than glibc's. */
    assert(sysconf(0x27) == host_page_size);
    char *buffer = malloc(16);
    assert(buffer);
    char path[] = "/tmp/shiraz-elf-exports-XXXXXX";
    int original = mkstemp(path);
    assert(original >= 0);
    assert(write(original, "hello", 5) == 5);
    assert(close(original) == 0);
    int fd = open(path, O_RDONLY);
    assert(fd >= 0);
    assert(read(fd, buffer, 5) == 5);
    buffer[5] = 0;
    assert(strcmp(buffer, "hello") == 0);
    assert(strstr(buffer, "ell") == buffer + 1);
    assert(lseek(fd, 1, SEEK_SET) == 1);
    assert(read(fd, buffer, 1) == 1 && buffer[0] == 'e');
    assert(close(fd) == 0);
    assert(unlink(path) == 0);
    fd = open(path, O_CREAT | O_EXCL | O_RDWR, 0600);
    assert(fd >= 0 && close(fd) == 0);
    assert(unlink(path) == 0);
    errno = 0;
    assert(read(-1, buffer, 1) == -1 && errno == EBADF);
    free(buffer);
    void *memory = mmap(NULL, (size_t)host_page_size, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(memory != MAP_FAILED);
    memcpy(memory, "mapped", 7);
    assert(mprotect(memory, (size_t)host_page_size, PROT_READ) == 0);
    assert(strcmp(memory, "mapped") == 0);
    assert(munmap(memory, (size_t)host_page_size) == 0);
    /* Exercise all six syscall arguments, including the x86-64 stack slot. */
    long mapped = syscall(SYS_mmap, 0UL, (unsigned long)host_page_size,
                          (long)(PROT_READ | PROT_WRITE),
                          (long)(MAP_PRIVATE | MAP_ANONYMOUS), -1L, 0L);
    assert(mapped != -1);
    memcpy((void *)mapped, "syscall", 8);
    assert(munmap((void *)mapped, (size_t)host_page_size) == 0);
    pid_t child = fork();
    assert(child >= 0);
    if (!child) exit(37);
    int status = 0;
    assert(waitpid(child, &status, 0) == child);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 37);
    assert(dlclose(library) == 0);
    return 0;
}
