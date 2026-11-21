// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <unistd.h>

struct AMediaFormat;
struct sync_fence_info_layout {
    char obj_name[32];
    char driver_name[32];
    int32_t status;
    uint32_t flags;
    uint64_t timestamp_ns;
};
struct sync_file_info_layout {
    char name[32];
    int32_t status;
    uint32_t flags;
    uint32_t num_fences;
    uint32_t pad;
    uint64_t sync_fence_info;
};

extern "C" {
AMediaFormat* AMediaFormat_new();
int AMediaFormat_delete(AMediaFormat*);
void AMediaFormat_setInt32(AMediaFormat*, const char*, int32_t);
bool AMediaFormat_getInt32(AMediaFormat*, const char*, int32_t*);
int sync_wait(int fd, int timeout);
int sync_merge(const char* name, int fd1, int fd2);
sync_file_info_layout* sync_file_info(int32_t fd);
void sync_file_info_free(sync_file_info_layout* info);
uint32_t slQueryNumSupportedEngineInterfaces(uint32_t* count);
}

int main() {
    auto* fmt = AMediaFormat_new();
    assert(fmt != nullptr);
    AMediaFormat_setInt32(fmt, "sample-rate", 48000);
    int32_t sr = 0;
    assert(AMediaFormat_getInt32(fmt, "sample-rate", &sr) && sr == 48000);
    assert(AMediaFormat_delete(fmt) == 0);

    // Verify libsync.so standalone exports and sync_file_info behavior.
    assert(sync_file_info(-1) == nullptr);
    sync_file_info_free(nullptr);
#if defined(__APPLE__)
    int fds[2];
    assert(pipe(fds) == 0);
    auto* active = sync_file_info(fds[0]);
    assert(active != nullptr);
    assert(active->status == 0);
    assert(active->num_fences == 1);
    sync_file_info_free(active);
    char sig = 1;
    assert(write(fds[1], &sig, 1) == 1);
    assert(sync_wait(fds[0], 100) == 0);
    auto* done = sync_file_info(fds[0]);
    assert(done != nullptr);
    assert(done->status == 1);
    assert(done->num_fences == 1);
    auto* fence =
        reinterpret_cast<sync_fence_info_layout*>(static_cast<uintptr_t>(done->sync_fence_info));
    assert(fence != nullptr && fence->status == 1);
    sync_file_info_free(done);
    close(fds[0]);
    close(fds[1]);
#endif

    // Verify libOpenSLES.so standalone exports.
    uint32_t sl_ifaces = 0;
    assert(slQueryNumSupportedEngineInterfaces(&sl_ifaces) == 0);
    assert(dlsym(RTLD_DEFAULT, "slCreateEngine") != nullptr);
    assert(dlsym(RTLD_DEFAULT, "SL_IID_ENGINE") != nullptr);
    return 0;
}
