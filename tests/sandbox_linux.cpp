// SPDX-License-Identifier: GPL-3.0-only
// Native test for Linux Landlock filesystem isolation (forked child so the
// test runner itself remains unrestricted, and graceful ENOSYS check on non-Linux).
#include "shiraz/sandbox.hpp"

#include <cassert>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#if defined(__linux__)
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#endif

int main() {
    // Invalid arguments must always fail with -EINVAL across every OS.
    assert(shiraz_sandbox_apply_landlock(nullptr, 0, nullptr, 0) == -EINVAL);
    assert(shiraz_sandbox_apply_landlock(nullptr, 1, nullptr, 0) == -EINVAL);
    assert(shiraz_sandbox_apply_landlock(nullptr, 0, nullptr, 1) == -EINVAL);

    const int abi = shiraz_sandbox_landlock_abi_version();
#if defined(__linux__)
    if (abi > 0) {
        char tmpl[] = "/tmp/shiraz-landlock-XXXXXX";
        char* root = mkdtemp(tmpl);
        assert(root != nullptr);

        char allowedDir[256];
        char deniedDir[256];
        char allowedFile[300];
        char deniedFile[300];
        std::snprintf(allowedDir, sizeof(allowedDir), "%s/allowed", root);
        std::snprintf(deniedDir, sizeof(deniedDir), "%s/denied", root);
        std::snprintf(allowedFile, sizeof(allowedFile), "%s/ok.txt", allowedDir);
        std::snprintf(deniedFile, sizeof(deniedFile), "%s/secret.txt", deniedDir);

        assert(mkdir(allowedDir, 0700) == 0);
        assert(mkdir(deniedDir, 0700) == 0);
        int fd = open(deniedFile, O_CREAT | O_WRONLY | O_TRUNC, 0600);
        assert(fd >= 0);
        assert(write(fd, "secret", 6) == 6);
        close(fd);

        const pid_t pid = fork();
        assert(pid >= 0);
        if (pid == 0) {
            const char* rwPaths[] = {allowedDir};
            const int rc = shiraz_sandbox_apply_landlock(nullptr, 0, rwPaths, 1);
            if (rc != 0)
                _exit(10);
            // Writing inside allowedDir must succeed.
            int okFd = open(allowedFile, O_CREAT | O_WRONLY | O_TRUNC, 0600);
            if (okFd < 0)
                _exit(11);
            close(okFd);
            // Reading inside deniedDir must fail with EACCES.
            int badFd = open(deniedFile, O_RDONLY);
            if (badFd >= 0) {
                close(badFd);
                _exit(12);
            }
            if (errno != EACCES)
                _exit(13);
            _exit(0);
        }

        int status = 0;
        assert(waitpid(pid, &status, 0) == pid);
        assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);

        unlink(allowedFile);
        unlink(deniedFile);
        rmdir(allowedDir);
        rmdir(deniedDir);
        rmdir(root);
        std::printf("SANDBOX_LINUX_LANDLOCK_PASS abi=%d\n", abi);
    } else {
        const char* ro[] = {"/tmp"};
        assert(shiraz_sandbox_apply_landlock(ro, 1, nullptr, 0) == -ENOSYS);
        std::printf("SANDBOX_LINUX_LANDLOCK_ENOSYS_OK\n");
    }
#else
    assert(abi == 0);
    const char* ro[] = {"/tmp"};
    assert(shiraz_sandbox_apply_landlock(ro, 1, nullptr, 0) == -ENOSYS);
    std::printf("SANDBOX_NON_LINUX_FALLBACK_OK\n");
#endif
    return 0;
}
