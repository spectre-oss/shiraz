// SPDX-License-Identifier: GPL-3.0-only
// Linux Landlock LSM filesystem isolation using direct syscalls so Shiraz
// builds cleanly across kernels without requiring recent UAPI headers.
#include "shiraz/sandbox.hpp"

#include <cerrno>
#include <cstdint>

#if defined(__linux__)
#include <fcntl.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

namespace {

#ifndef __NR_landlock_create_ruleset
#define __NR_landlock_create_ruleset 444
#endif
#ifndef __NR_landlock_add_rule
#define __NR_landlock_add_rule 445
#endif
#ifndef __NR_landlock_restrict_self
#define __NR_landlock_restrict_self 446
#endif

constexpr uint32_t kLandlockCreateRulesetVersion = 1U << 0;
constexpr uint32_t kLandlockRulePathBeneath = 1U;

// Landlock ABI v1 access rights.
constexpr uint64_t kAccessFsExecute = 1ULL << 0;
constexpr uint64_t kAccessFsWriteFile = 1ULL << 1;
constexpr uint64_t kAccessFsReadFile = 1ULL << 2;
constexpr uint64_t kAccessFsReadDir = 1ULL << 3;
constexpr uint64_t kAccessFsRemoveDir = 1ULL << 4;
constexpr uint64_t kAccessFsRemoveFile = 1ULL << 5;
constexpr uint64_t kAccessFsMakeChar = 1ULL << 6;
constexpr uint64_t kAccessFsMakeDir = 1ULL << 7;
constexpr uint64_t kAccessFsMakeReg = 1ULL << 8;
constexpr uint64_t kAccessFsMakeSock = 1ULL << 9;
constexpr uint64_t kAccessFsMakeFifo = 1ULL << 10;
constexpr uint64_t kAccessFsMakeBlock = 1ULL << 11;
constexpr uint64_t kAccessFsMakeSym = 1ULL << 12;

// Landlock ABI v2/v3 additions.
constexpr uint64_t kAccessFsRefer = 1ULL << 13;
constexpr uint64_t kAccessFsTruncate = 1ULL << 14;

struct LandlockRulesetAttr {
    uint64_t handled_access_fs;
};

struct __attribute__((packed)) LandlockPathBeneathAttr {
    uint64_t allowed_access;
    int32_t parent_fd;
};

int sys_landlock_create_ruleset(const LandlockRulesetAttr* attr, size_t size, uint32_t flags) {
    return static_cast<int>(syscall(__NR_landlock_create_ruleset, attr, size, flags));
}

int sys_landlock_add_rule(int rulesetFd, uint32_t ruleType, const void* ruleAttr, uint32_t flags) {
    return static_cast<int>(syscall(__NR_landlock_add_rule, rulesetFd, ruleType, ruleAttr, flags));
}

int sys_landlock_restrict_self(int rulesetFd, uint32_t flags) {
    return static_cast<int>(syscall(__NR_landlock_restrict_self, rulesetFd, flags));
}

int add_path_rule(int rulesetFd, const char* path, uint64_t allowedAccess) {
    if (!path || path[0] != '/')
        return -EINVAL;
    const int fd = open(path, O_PATH | O_CLOEXEC);
    if (fd < 0)
        return -errno;
    LandlockPathBeneathAttr attr{};
    attr.allowed_access = allowedAccess;
    attr.parent_fd = fd;
    const int rc = sys_landlock_add_rule(rulesetFd, kLandlockRulePathBeneath, &attr, 0);
    const int saved = errno;
    close(fd);
    return rc == 0 ? 0 : -saved;
}

} // namespace

extern "C" int shiraz_sandbox_landlock_abi_version(void) {
    const int abi = sys_landlock_create_ruleset(nullptr, 0, kLandlockCreateRulesetVersion);
    return abi > 0 ? abi : 0;
}

extern "C" int shiraz_sandbox_apply_landlock(const char* const* readOnlyPaths, size_t readOnlyCount,
                                             const char* const* readWritePaths,
                                             size_t readWriteCount) {
    if ((readOnlyCount > 0 && !readOnlyPaths) || (readWriteCount > 0 && !readWritePaths))
        return -EINVAL;
    if (readOnlyCount == 0 && readWriteCount == 0)
        return -EINVAL;

    const int abi = shiraz_sandbox_landlock_abi_version();
    if (abi <= 0)
        return -ENOSYS;

    uint64_t readRights = kAccessFsExecute | kAccessFsReadFile | kAccessFsReadDir;
    uint64_t writeRights = kAccessFsWriteFile | kAccessFsRemoveDir | kAccessFsRemoveFile |
                           kAccessFsMakeDir | kAccessFsMakeReg | kAccessFsMakeSym |
                           kAccessFsMakeSock | kAccessFsMakeFifo;
    uint64_t handled = readRights | writeRights | kAccessFsMakeChar | kAccessFsMakeBlock;
    if (abi >= 2) {
        handled |= kAccessFsRefer;
        writeRights |= kAccessFsRefer;
    }
    if (abi >= 3) {
        handled |= kAccessFsTruncate;
        writeRights |= kAccessFsTruncate;
    }

    LandlockRulesetAttr attr{};
    attr.handled_access_fs = handled;
    const int rulesetFd = sys_landlock_create_ruleset(&attr, sizeof(attr), 0);
    if (rulesetFd < 0)
        return -errno;

    for (size_t i = 0; i < readOnlyCount; ++i) {
        const int rc = add_path_rule(rulesetFd, readOnlyPaths[i], readRights);
        if (rc != 0) {
            close(rulesetFd);
            return rc;
        }
    }
    for (size_t i = 0; i < readWriteCount; ++i) {
        const int rc = add_path_rule(rulesetFd, readWritePaths[i], readRights | writeRights);
        if (rc != 0) {
            close(rulesetFd);
            return rc;
        }
    }

    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
        const int saved = errno;
        close(rulesetFd);
        return -saved;
    }
    if (sys_landlock_restrict_self(rulesetFd, 0) != 0) {
        const int saved = errno;
        close(rulesetFd);
        return -saved;
    }
    close(rulesetFd);
    return 0;
}

#else

extern "C" int shiraz_sandbox_landlock_abi_version(void) {
    return 0;
}

extern "C" int shiraz_sandbox_apply_landlock(const char* const* readOnlyPaths, size_t readOnlyCount,
                                             const char* const* readWritePaths,
                                             size_t readWriteCount) {
    if ((readOnlyCount > 0 && !readOnlyPaths) || (readWriteCount > 0 && !readWritePaths))
        return -EINVAL;
    if (readOnlyCount == 0 && readWriteCount == 0)
        return -EINVAL;
    return -ENOSYS;
}

#endif
