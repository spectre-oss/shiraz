// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/logind_power.hpp"
#include <cassert>
#include <cstdio>
int main(int argc, char** argv) {
    assert(argc == 2);
    void* fixture = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(fixture);
    auto mode = reinterpret_cast<void (*)(int)>(dlsym(fixture, "power_fixture_mode"));
    auto balanced = reinterpret_cast<int (*)()>(dlsym(fixture, "power_fixture_balanced"));
    auto eof = reinterpret_cast<int (*)()>(dlsym(fixture, "power_fixture_eof"));
    auto disconnect = reinterpret_cast<void (*)()>(dlsym(fixture, "power_fixture_disconnect"));
    assert(mode && balanced && eof && disconnect);
    {
        shiraz::LogindPower backend(argv[1]);
        int fd = backend.acquire();
        assert(fd >= 0 && balanced());
        assert((fcntl(fd, F_GETFD) & FD_CLOEXEC) != 0 && !eof());
        assert(shiraz::LogindPower::active(fd));
        close(fd);
        assert(eof());
        assert(!shiraz::LogindPower::active(fd));
        for (int failure = 1; failure <= 2; ++failure) {
            mode(failure);
            bool rejected = false;
            try {
                int unexpected = backend.acquire();
                close(unexpected);
            } catch (const std::runtime_error&) {
                rejected = true;
            }
            assert(rejected && balanced());
        }
        mode(0);
        fd = backend.acquire();
        assert(fd >= 0 && balanced());
        close(fd);
        assert(eof());
        fd = backend.acquire();
        assert(shiraz::LogindPower::active(fd));
        disconnect();
        assert(fcntl(fd, F_GETFD) >= 0);
        assert(!shiraz::LogindPower::active(fd));
        close(fd);
        fd = backend.acquire();
        assert(shiraz::LogindPower::active(fd));
        close(fd);
        assert(eof());
        assert(!shiraz::LogindPower::active(-1));
    }
    bool missing = false;
    try {
        shiraz::LogindPower absent("/nonexistent/shiraz-power-test-library");
    } catch (const std::runtime_error&) {
        missing = true;
    }
    assert(missing);
    dlclose(fixture);
    std::puts("LOGIND_POWER_PROTOCOL_AND_FD_OWNERSHIP_OK");
}
