// SPDX-License-Identifier: GPL-3.0-only
// Included by native/presentation.cpp on Linux only (`#if defined(__linux__)`).
//
// Controls Freedesktop session D-Bus on-screen keyboard visibility cleanly across Linux
// environments without adding any link-time dependency on libdbus-1 and without affecting
// macOS, Windows, or desktop Linux systems that have no OSK service running:
//   1. Dynamically loads `libdbus-1.so.3` (fallback `libdbus-1.so`) via `dlopen`.
//   2. Runs all D-Bus probing and `SetVisible(b)` calls on a dedicated coalescing worker thread
//      using a private session bus connection so the SDL presentation loop never blocks.
//   3. Targets SpectreOS's default keyboard (`org.spectreos.OSK0` at `/org/spectreos/OSK0`)
//      first, falling back to `sm.puri.OSK0` (`/sm/puri/OSK0`) on other Linux mobile shells,
//      and silently no-oping when no OSK owner exists or when `SHIRAZ_OSK_DBUS=0`.

#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace shiraz {
namespace linux_osk {

struct Target {
    std::string service;
    std::string path;
    std::string interface;
};

inline std::string objectPathForService(const std::string& service) {
    if (service.empty())
        return {};
    std::string path;
    path.reserve(service.size() + 1);
    path.push_back('/');
    for (char ch : service)
        path.push_back(ch == '.' ? '/' : ch);
    return path;
}

inline bool isValidBusName(const std::string& name) {
    if (name.empty() || name.size() > 255 || name.front() == '.' || name.back() == '.')
        return false;
    bool hasDot = false;
    bool prevDot = false;
    for (char ch : name) {
        const bool ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                        (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.';
        if (!ok)
            return false;
        if (ch == '.') {
            if (prevDot)
                return false;
            hasDot = true;
            prevDot = true;
        } else {
            prevDot = false;
        }
    }
    return hasDot;
}

inline bool envEnabled() {
    const char* flag = std::getenv("SHIRAZ_OSK_DBUS");
    if (!flag || !*flag)
        return true;
    return std::strcmp(flag, "0") != 0 && std::strcmp(flag, "false") != 0 &&
           std::strcmp(flag, "off") != 0;
}

inline std::vector<Target> defaultTargets() {
    std::vector<Target> targets;
    const char* custom = std::getenv("SHIRAZ_OSK_DBUS_SERVICE");
    if (custom && *custom) {
        std::string svc(custom);
        if (isValidBusName(svc)) {
            targets.push_back({svc, objectPathForService(svc), svc});
            return targets;
        }
    }
    targets.push_back({"org.spectreos.OSK0", "/org/spectreos/OSK0", "org.spectreos.OSK0"});
    targets.push_back({"sm.puri.OSK0", "/sm/puri/OSK0", "sm.puri.OSK0"});
    return targets;
}

// Stable libdbus-1 C ABI definitions (avoids compile-time dbus-1 header/link dependency).
struct DBusConnection;
struct DBusMessage;
using dbus_bool_t = uint32_t;

struct DBusError {
    const char* name;
    const char* message;
    unsigned int dummy1 : 1;
    unsigned int dummy2 : 1;
    unsigned int dummy3 : 1;
    unsigned int dummy4 : 1;
    unsigned int dummy5 : 1;
    void* padding1;
};

constexpr int kDbusBusSession = 0;
constexpr int kDbusTypeInvalid = static_cast<int>('\0');
constexpr int kDbusTypeBoolean = static_cast<int>('b');
constexpr int kDbusTypeString = static_cast<int>('s');
constexpr int kProbeTimeoutMs = 150;

struct DbusApi {
    void* lib = nullptr;
    void (*error_init)(DBusError*) = nullptr;
    void (*error_free)(DBusError*) = nullptr;
    dbus_bool_t (*error_is_set)(const DBusError*) = nullptr;
    DBusConnection* (*bus_get_private)(int, DBusError*) = nullptr;
    void (*connection_set_exit_on_disconnect)(DBusConnection*, dbus_bool_t) = nullptr;
    dbus_bool_t (*connection_get_is_connected)(DBusConnection*) = nullptr;
    void (*connection_close)(DBusConnection*) = nullptr;
    void (*connection_unref)(DBusConnection*) = nullptr;
    DBusMessage* (*message_new_method_call)(const char*, const char*, const char*,
                                            const char*) = nullptr;
    void (*message_set_no_reply)(DBusMessage*, dbus_bool_t) = nullptr;
    dbus_bool_t (*message_append_args)(DBusMessage*, int, ...) = nullptr;
    dbus_bool_t (*message_get_args)(DBusMessage*, DBusError*, int, ...) = nullptr;
    void (*message_unref)(DBusMessage*) = nullptr;
    DBusMessage* (*connection_send_with_reply_and_block)(DBusConnection*, DBusMessage*, int,
                                                         DBusError*) = nullptr;
    dbus_bool_t (*connection_send)(DBusConnection*, DBusMessage*, uint32_t*) = nullptr;
    void (*connection_flush)(DBusConnection*) = nullptr;

    template <typename Fn> bool loadSym(Fn& out, const char* sym) {
        out = reinterpret_cast<Fn>(dlsym(lib, sym));
        return out != nullptr;
    }

    bool load() {
        if (lib)
            return true;
        lib = dlopen("libdbus-1.so.3", RTLD_LAZY | RTLD_LOCAL);
        if (!lib)
            lib = dlopen("libdbus-1.so", RTLD_LAZY | RTLD_LOCAL);
        if (!lib)
            return false;
        const bool ok =
            loadSym(error_init, "dbus_error_init") && loadSym(error_free, "dbus_error_free") &&
            loadSym(error_is_set, "dbus_error_is_set") &&
            loadSym(bus_get_private, "dbus_bus_get_private") &&
            loadSym(connection_set_exit_on_disconnect, "dbus_connection_set_exit_on_disconnect") &&
            loadSym(connection_get_is_connected, "dbus_connection_get_is_connected") &&
            loadSym(connection_close, "dbus_connection_close") &&
            loadSym(connection_unref, "dbus_connection_unref") &&
            loadSym(message_new_method_call, "dbus_message_new_method_call") &&
            loadSym(message_set_no_reply, "dbus_message_set_no_reply") &&
            loadSym(message_append_args, "dbus_message_append_args") &&
            loadSym(message_get_args, "dbus_message_get_args") &&
            loadSym(message_unref, "dbus_message_unref") &&
            loadSym(connection_send_with_reply_and_block,
                    "dbus_connection_send_with_reply_and_block") &&
            loadSym(connection_send, "dbus_connection_send") &&
            loadSym(connection_flush, "dbus_connection_flush");
        if (!ok) {
            dlclose(lib);
            lib = nullptr;
            return false;
        }
        return true;
    }

    ~DbusApi() {
        if (lib)
            dlclose(lib);
    }
};

class Session {
  public:
    Session() = default;
    Session(const Session&) = delete;
    Session& operator=(const Session&) = delete;

    ~Session() {
        shutdown();
    }

    void setVisible(bool visible) {
        if (!envEnabled())
            return;
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopping_)
            return;
        pendingVisible_ = visible;
        hasPending_ = true;
        if (!worker_.joinable()) {
            worker_ = std::thread([this]() { runWorker(); });
        }
        cv_.notify_one();
    }

    void shutdown() {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            stopping_ = true;
        }
        cv_.notify_one();
        if (worker_.joinable())
            worker_.join();
    }

  private:
    bool ensureConnected(DbusApi& api, DBusConnection*& conn) {
        if (conn && api.connection_get_is_connected(conn))
            return true;
        if (conn) {
            api.connection_close(conn);
            api.connection_unref(conn);
            conn = nullptr;
        }
        if (!api.load())
            return false;
        DBusError err{};
        api.error_init(&err);
        conn = api.bus_get_private(kDbusBusSession, &err);
        if (api.error_is_set(&err) || !conn) {
            api.error_free(&err);
            if (conn) {
                api.connection_close(conn);
                api.connection_unref(conn);
                conn = nullptr;
            }
            return false;
        }
        api.error_free(&err);
        api.connection_set_exit_on_disconnect(conn, 0);
        return true;
    }

    bool nameHasOwner(DbusApi& api, DBusConnection* conn, const std::string& name) {
        DBusMessage* req = api.message_new_method_call(
            "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
            "NameHasOwner");
        if (!req)
            return false;
        const char* cname = name.c_str();
        if (!api.message_append_args(req, kDbusTypeString, &cname, kDbusTypeInvalid)) {
            api.message_unref(req);
            return false;
        }
        DBusError err{};
        api.error_init(&err);
        DBusMessage* reply =
            api.connection_send_with_reply_and_block(conn, req, kProbeTimeoutMs, &err);
        api.message_unref(req);
        if (!reply || api.error_is_set(&err)) {
            api.error_free(&err);
            if (reply)
                api.message_unref(reply);
            return false;
        }
        dbus_bool_t owned = 0;
        const bool ok =
            api.message_get_args(reply, &err, kDbusTypeBoolean, &owned, kDbusTypeInvalid) != 0 &&
            !api.error_is_set(&err);
        api.error_free(&err);
        api.message_unref(reply);
        return ok && owned != 0;
    }

    void dispatch(DbusApi& api, DBusConnection*& conn, bool visible) {
        if (!ensureConnected(api, conn))
            return;
        const auto targets = defaultTargets();
        const Target* active = nullptr;
        for (const auto& candidate : targets) {
            if (nameHasOwner(api, conn, candidate.service)) {
                active = &candidate;
                break;
            }
        }
        if (!active)
            return;
        DBusMessage* msg = api.message_new_method_call(
            active->service.c_str(), active->path.c_str(), active->interface.c_str(),
            "SetVisible");
        if (!msg)
            return;
        api.message_set_no_reply(msg, 1);
        dbus_bool_t arg = visible ? 1u : 0u;
        if (api.message_append_args(msg, kDbusTypeBoolean, &arg, kDbusTypeInvalid)) {
            api.connection_send(conn, msg, nullptr);
            api.connection_flush(conn);
        }
        api.message_unref(msg);
    }

    void runWorker() {
        DbusApi api;
        DBusConnection* conn = nullptr;
        for (;;) {
            bool visible = false;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                cv_.wait(lock, [this]() { return hasPending_ || stopping_; });
                if (!hasPending_ && stopping_)
                    break;
                visible = pendingVisible_;
                hasPending_ = false;
            }
            dispatch(api, conn, visible);
        }
        if (conn && api.lib) {
            api.connection_close(conn);
            api.connection_unref(conn);
        }
    }

    std::mutex mutex_;
    std::condition_variable cv_;
    std::thread worker_;
    bool pendingVisible_ = false;
    bool hasPending_ = false;
    bool stopping_ = false;
};

} // namespace linux_osk
} // namespace shiraz
