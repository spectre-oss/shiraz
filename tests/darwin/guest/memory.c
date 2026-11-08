// SPDX-License-Identifier: GPL-3.0-only
// Phase-0 synthetic memory-layout probe; this does not load ELF or run an APK.
#include <errno.h>
#include <libkern/OSCacheControl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#if !defined(__APPLE__) || !defined(__aarch64__)
#error This probe requires Apple Silicon
#endif

static void fail(const char *operation) {
    fprintf(stderr, "FAIL %s: %s\n", operation, strerror(errno));
    exit(1);
}

static void require(int condition, const char *message) {
    if (!condition) {
        fprintf(stderr, "FAIL %s\n", message);
        exit(1);
    }
}

struct image { unsigned char *base; size_t size; };

static struct image create(size_t page, unsigned result) {
    size_t size = 3 * page;
    unsigned char *base = mmap(NULL, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                              MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0);
    if (base == MAP_FAILED) fail("MAP_JIT image");
    // Replace only pages inside our own reservation. Keep a guard between text/data.
    if (mmap(base + 2 * page, page, PROT_READ | PROT_WRITE,
             MAP_PRIVATE | MAP_ANON | MAP_FIXED, -1, 0) == MAP_FAILED)
        fail("replace data page");
    if (mmap(base + page, page, PROT_NONE,
             MAP_PRIVATE | MAP_ANON | MAP_FIXED, -1, 0) == MAP_FAILED)
        fail("replace guard page");
    pthread_jit_write_protect_np(0);
    uint32_t instructions[] = {0x52800000u | (result << 5), 0xd65f03c0u};
    memcpy(base, instructions, sizeof(instructions)); // mov w0,#result; ret
    pthread_jit_write_protect_np(1);
    sys_icache_invalidate(base, sizeof(instructions));
    return (struct image){base, size};
}

static unsigned execute(struct image image) {
    unsigned (*function)(void);
    _Static_assert(sizeof(function) == sizeof(image.base), "pointer size");
    memcpy(&function, &image.base, sizeof(function));
    return function();
}

int main(void) {
    long value = sysconf(_SC_PAGESIZE);
    require(value > 0 && (value & (value - 1)) == 0, "host page size");
    size_t page = (size_t)value;
    require(pthread_jit_write_protect_supported_np(), "JIT write protection supported");
    for (unsigned iteration = 0; iteration < 32; ++iteration) {
        struct image first = create(page, 73), second = create(page, 91);
        // Ordinary data must remain writable with JIT execution enabled.
        first.base[2 * page] = 17;
        second.base[2 * page] = 29;
        require(execute(first) == 73 && execute(second) == 91, "two live images");
        require(first.base[2 * page] == 17 && second.base[2 * page] == 29,
                "independent writable data");
        pthread_jit_write_protect_np(0);
        uint32_t replacement = 0x52800000u | (117u << 5);
        memcpy(first.base, &replacement, sizeof(replacement));
        pthread_jit_write_protect_np(1);
        sys_icache_invalidate(first.base, sizeof(replacement));
        require(execute(first) == 117 && execute(second) == 91, "rewrite isolation");
        // RELRO is represented here by sealing the ordinary data page.
        if (mprotect(first.base + 2 * page, page, PROT_READ)) fail("RELRO");
        if (munmap(first.base, first.size) || munmap(second.base, second.size))
            fail("image cleanup");
    }
    printf("M5_MEMORY_PASS page=%zu iterations=32 images=2 rewrite=1\n", page);
    return 0;
}
