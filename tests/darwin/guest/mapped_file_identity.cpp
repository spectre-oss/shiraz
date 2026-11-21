// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/mapped_file_identity.hpp"
#include <cassert>
#include <cstdio>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
using namespace shiraz::guest;
struct Reply {
    proc_regionwithpathinfo value{};
    int size = sizeof(value);
    int operator()(uint64_t, proc_regionwithpathinfo& out) const {
        errno = EACCES;
        out = value;
        return size;
    }
};
int main() {
    Reply reply;
    reply.value.prp_prinfo.pri_address = 100;
    reply.value.prp_prinfo.pri_size = 100;
    auto& st = reply.value.prp_vip.vip_vi.vi_stat;
    st.vst_dev = 0xffffffffu;
    st.vst_ino = 2;
    st.vst_nlink = 1;
    std::strcpy(reply.value.prp_vip.vip_path, "/mapped");
    errno = ELOOP;
    auto result = mapped_file_identity(120, 180, 0xffffffffu, 2, reply);
    assert(result.status == MappedFileStatus::Verified && result.path == "/mapped" &&
           errno == ELOOP);
    assert(mapped_file_identity(99, 180, 0xffffffffu, 2, reply).status ==
           MappedFileStatus::Mismatch);
    assert(mapped_file_identity(120, 201, 0xffffffffu, 2, reply).status ==
           MappedFileStatus::Mismatch);
    assert(mapped_file_identity(120, 180, 0xffffffffu, 3, reply).status ==
           MappedFileStatus::Mismatch);
    assert(mapped_file_identity(120, 180, 1, 2, reply).status == MappedFileStatus::Mismatch);
    st.vst_ino = 0;
    assert(mapped_file_identity(120, 180, 0xffffffffu, 2, reply).status ==
           MappedFileStatus::Unavailable);
    st.vst_ino = 2;
    std::memset(reply.value.prp_vip.vip_path, 'x', sizeof(reply.value.prp_vip.vip_path));
    assert(mapped_file_identity(120, 180, 0xffffffffu, 2, reply).status ==
           MappedFileStatus::Invalid);
    reply.size = 1;
    assert(mapped_file_identity(120, 180, 0xffffffffu, 2, reply).status ==
           MappedFileStatus::KernelError);
    assert(errno == ELOOP);

    char name[] = "/private/tmp/shiraz-map-identity-XXXXXX";
    int fd = mkstemp(name);
    assert(fd >= 0);
    size_t page = getpagesize();
    assert(!ftruncate(fd, page * 3));
    struct stat original{};
    assert(!fstat(fd, &original));
    void* mapping = mmap(nullptr, page, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, page);
    assert(mapping != MAP_FAILED);
    uint64_t start = reinterpret_cast<uintptr_t>(mapping);
    auto read = [&] {
        return mapped_file_identity(start, start + page, uint32_t(original.st_dev),
                                    original.st_ino);
    };
    result = read();
    assert(result.status == MappedFileStatus::Verified && result.path == name);
    *static_cast<char*>(mapping) = 42;
    result = read();
    assert(result.status == MappedFileStatus::Verified && result.path == name);
    std::string renamed = std::string(name) + ".renamed", alias = renamed + ".alias";
    assert(!rename(name, renamed.c_str()));
    close(fd);
    result = read();
    assert(result.status == MappedFileStatus::Verified && result.path == renamed &&
           result.path_status == MappedPathStatus::Present);
    assert(!link(renamed.c_str(), alias.c_str()));
    assert(!unlink(renamed.c_str()));
    result = read();
    assert(result.status == MappedFileStatus::Verified && result.links == 1);
    struct stat surviving{};
    bool present = !stat(result.path.c_str(), &surviving) && surviving.st_ino == original.st_ino;
    assert(result.path_status ==
           (present ? MappedPathStatus::Present : MappedPathStatus::Unresolved));
    assert(!unlink(alias.c_str()));
    result = read();
    assert(result.status == MappedFileStatus::Verified && result.links == 0 &&
           !result.path.empty() && result.path_status == MappedPathStatus::Deleted);
    assert(mapped_file_identity(start, start + page, uint32_t(original.st_dev), original.st_ino + 1)
               .status == MappedFileStatus::Mismatch);
    assert(!munmap(mapping, page));
    std::puts("MAPPED_FILE_IDENTITY_PASS cow=1 rename=1 close=1 hardlink=1 unlink=1 mismatch=1 "
              "bounded=1 errno=1");
}
