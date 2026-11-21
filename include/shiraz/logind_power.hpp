// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <dlfcn.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <cstdint>
#include <stdexcept>

namespace shiraz {
// Runtime loading keeps logind optional and avoids a desktop-environment dependency.
struct PowerBus;
struct PowerMessage;
struct PowerError;
class LogindPower {
    void* library_;
    template <class T> T symbol(const char* name) {
        void* value = dlsym(library_, name);
        if (!value)
            throw std::runtime_error("Incomplete libsystemd power API");
        return reinterpret_cast<T>(value);
    }
    int (*open_)(PowerBus**);
    PowerBus* (*close_)(PowerBus*);
    int (*timeout_)(PowerBus*, uint64_t);
    int (*call_)(PowerBus*, const char*, const char*, const char*, const char*, PowerError*,
                 PowerMessage**, const char*, ...);
    int (*read_)(PowerMessage*, const char*, ...);
    PowerMessage* (*unref_)(PowerMessage*);

  public:
    static bool active(int descriptor) {
        if (descriptor < 0 || fcntl(descriptor, F_GETFD) < 0)
            return false;
        // logind owns the FIFO's read end. An open writer alone does not prove
        // inhibition: peer loss reports POLLERR/POLLHUP without writing data.
        pollfd state{descriptor, POLLOUT, 0};
        if (poll(&state, 1, 0) < 0)
            throw std::runtime_error("Cannot inspect logind inhibition descriptor");
        return (state.revents & (POLLERR | POLLHUP | POLLNVAL)) == 0;
    }
    explicit LogindPower(const char* library = "libsystemd.so.0")
        : library_(dlopen(library, RTLD_NOW | RTLD_LOCAL)) {
        if (!library_)
            throw std::runtime_error("libsystemd idle-sleep inhibition unavailable");
        try {
            open_ = symbol<decltype(open_)>("sd_bus_open_system");
            close_ = symbol<decltype(close_)>("sd_bus_flush_close_unref");
            timeout_ = symbol<decltype(timeout_)>("sd_bus_set_method_call_timeout");
            call_ = symbol<decltype(call_)>("sd_bus_call_method");
            read_ = symbol<decltype(read_)>("sd_bus_message_read");
            unref_ = symbol<decltype(unref_)>("sd_bus_message_unref");
        } catch (...) {
            dlclose(library_);
            throw;
        }
    }
    ~LogindPower() {
        dlclose(library_);
    }
    LogindPower(const LogindPower&) = delete;
    LogindPower& operator=(const LogindPower&) = delete;
    int acquire() {
        PowerBus* bus = nullptr;
        PowerMessage* reply = nullptr;
        int owned = -1;
        try {
            if (open_(&bus) < 0)
                throw std::runtime_error("System bus unavailable for idle-sleep inhibition");
            if (timeout_(bus, 2000000) < 0)
                throw std::runtime_error("Cannot bound power-inhibition request");
            // Only automatic idle action is inhibited; manual sleep is not blocked.
            if (call_(bus, "org.freedesktop.login1", "/org/freedesktop/login1",
                      "org.freedesktop.login1.Manager", "Inhibit", nullptr, &reply, "ssss", "idle",
                      "Shiraz", "Android background work", "block") < 0)
                throw std::runtime_error("logind denied idle-sleep inhibition");
            int borrowed = -1;
            if (read_(reply, "h", &borrowed) <= 0 || borrowed < 0)
                throw std::runtime_error("Invalid logind inhibition descriptor");
            owned = fcntl(borrowed, F_DUPFD_CLOEXEC, 0);
            if (owned < 0)
                throw std::runtime_error("Cannot own logind inhibition descriptor");
        } catch (...) {
            if (reply)
                unref_(reply);
            if (bus)
                close_(bus);
            throw;
        }
        unref_(reply);
        close_(bus);
        return owned;
    }
};
} // namespace shiraz
