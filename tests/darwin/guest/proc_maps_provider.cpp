// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/proc_maps_provider.hpp"
#include <cassert>
#include <cstdio>
#include <fcntl.h>
#include <sys/mman.h>
using namespace shiraz::guest;
int main() {
    std::vector<uint8_t> bytes(8u * 1024u * 1024u + 1);
    size_t n = fread(bytes.data(), 1, bytes.size(), stdin);
    assert(n && n < bytes.size());
    bytes.resize(n);
    char capability_path[] = "/private/tmp/shiraz-cache-capability-XXXXXX";
    int writer = mkstemp(capability_path);
    assert(writer >= 0);
    assert(::write(writer, bytes.data(), bytes.size()) == ssize_t(bytes.size()));
    std::vector<uint8_t> copied{42};
    assert(!read_proc_maps_catalog(writer, copied) && copied == std::vector<uint8_t>{42});
    int reader = ::open(capability_path, O_RDONLY | O_CLOEXEC);
    assert(reader >= 3);
    assert(!read_proc_maps_catalog(reader, copied)); // Still linked.
    assert(!unlink(capability_path));
    close(writer);
    assert(lseek(reader, 3, SEEK_SET) == 3);
    assert(read_proc_maps_catalog(reader, copied) && copied == bytes &&
           lseek(reader, 0, SEEK_CUR) == 3);
    close(reader);
    assert(!read_proc_maps_catalog(reader, copied));
    assert(!proc_maps_factory({}, [] { return std::vector<MemoryMapRecord>{}; }));
    assert(!proc_maps_factory(bytes, {}));
    auto wrong = bytes;
    wrong[8] ^= 1;
    assert(!proc_maps_factory(wrong, [] { return std::vector<MemoryMapRecord>{}; }));
    char path[] = "/private/tmp/shiraz-maps-provider-XXXXXX";
    int fd = mkstemp(path);
    size_t page = getpagesize();
    assert(fd >= 0 && !ftruncate(fd, page * 2));
    struct stat st{};
    assert(!fstat(fd, &st));
    void* mapped = mmap(nullptr, page, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, page);
    assert(mapped != MAP_FAILED);
    *static_cast<char*>(mapped) = 'x';
    uintptr_t start = reinterpret_cast<uintptr_t>(mapped);
    std::vector<MemoryMapRecord> owners{{start, start + page, PROT_READ | PROT_WRITE, MAP_PRIVATE,
                                         1, page, uint32_t(st.st_dev), uint64_t(st.st_ino), path}};
    unsigned calls = 0;
    auto factory = proc_maps_factory(bytes, [&] {
        ++calls;
        return owners;
    });
    assert(factory);
    MapsStream stream(factory);
    std::string text;
    char chunk[113];
    size_t amount = 0;
    do {
        assert(!stream.read(chunk, sizeof(chunk), &amount));
        text.append(chunk, amount);
    } while (amount);
    assert(calls == 1 && !text.empty() && text.find(path) != std::string::npos);
    char expected[160];
    std::snprintf(expected, sizeof(expected), "%08llx-%08llx rw-p %08llx %02x:%02x %llu",
                  static_cast<unsigned long long>(start),
                  static_cast<unsigned long long>(start + page),
                  static_cast<unsigned long long>(page), major(st.st_dev), minor(st.st_dev),
                  static_cast<unsigned long long>(st.st_ino));
    assert(text.find(expected) != std::string::npos);
    owners[0].protection = PROT_READ; // Deliberately stale ownership snapshot.
    std::string untouched = "untouched";
    assert(factory(untouched) == EAGAIN && untouched == "untouched");
    owners[0].protection = PROT_READ | PROT_WRITE;
    assert(!unlink(path));
    assert(!factory(untouched) &&
           untouched.find(std::string(path) + " (deleted)") != std::string::npos);
    assert(!munmap(mapped, page));
    close(fd);
    owners.clear();
    assert(!factory(untouched) && untouched.find(path) == std::string::npos);
    std::printf("PROC_MAPS_PROVIDER_PASS bytes=%zu owned_offset=1 stale_rejected=1 "
                "deleted_identity=1 removed_mapping=1\n",
                text.size());
}
