// SPDX-License-Identifier: GPL-3.0-only
// Linux host location bridge (libshiraz-location.so) for shiraz.runtime.HostLocation.
//
// Provides real GPS / network / fused location fixes on Linux desktops, laptops,
// Flatpak sandboxes, and Linux phones without adding any hard link dependency on
// libdbus-1 or GTK:
//   1. Dynamically loads `libdbus-1.so.3` (fallback `libdbus-1.so`, or
//      `SHIRAZ_LOCATION_DBUS_LIB` for unit testing) via `dlopen`.
//   2. Supports both GeoClue2 (`org.freedesktop.GeoClue2` on the system bus) and
//      XDG Desktop Portal Location (`org.freedesktop.portal.Location` on the session
//      bus, which works seamlessly inside Flatpak and Wayland desktops).
//   3. Runs D-Bus communication on a dedicated worker thread using a private bus
//      connection so Android/UI threads never block on IPC.
//   4. Shares the exact same 7-element fix array and authorization status contract
//      as macOS `native/darwin/location.mm`.

#include <dbus/dbus.h>
#include <jni.h>

#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <thread>
#include <time.h>
#include <unistd.h>

namespace {

constexpr jint kStatusUnavailable = 0;
constexpr jint kStatusAuthorized = 4;
constexpr int kProbeTimeoutMs = 150;
constexpr int kCallTimeoutMs = 2000;
constexpr int kDispatchPollMs = 200;

struct LocationFix {
    bool valid = false;
    double latitude = 0.0;
    double longitude = 0.0;
    double altitude = 0.0;
    double horizontalAccuracy = -1.0;
    double speed = -1.0;
    double course = -1.0;
    double timestampMs = 0.0;
};

enum class BackendKind {
    None,
    GeoClue2,
    Portal,
};

struct DbusApi {
    void* lib = nullptr;
    void (*error_init)(DBusError*) = nullptr;
    void (*error_free)(DBusError*) = nullptr;
    dbus_bool_t (*error_is_set)(const DBusError*) = nullptr;
    DBusConnection* (*bus_get_private)(DBusBusType, DBusError*) = nullptr;
    void (*bus_add_match)(DBusConnection*, const char*, DBusError*) = nullptr;
    void (*connection_set_exit_on_disconnect)(DBusConnection*, dbus_bool_t) = nullptr;
    dbus_bool_t (*connection_get_is_connected)(DBusConnection*) = nullptr;
    void (*connection_close)(DBusConnection*) = nullptr;
    void (*connection_unref)(DBusConnection*) = nullptr;
    DBusMessage* (*connection_send_with_reply_and_block)(DBusConnection*, DBusMessage*, int,
                                                         DBusError*) = nullptr;
    dbus_bool_t (*connection_read_write)(DBusConnection*, int) = nullptr;
    DBusMessage* (*connection_pop_message)(DBusConnection*) = nullptr;
    DBusMessage* (*message_new_method_call)(const char*, const char*, const char*,
                                            const char*) = nullptr;
    void (*message_unref)(DBusMessage*) = nullptr;
    int (*message_get_type)(DBusMessage*) = nullptr;
    dbus_bool_t (*message_is_signal)(DBusMessage*, const char*, const char*) = nullptr;
    const char* (*message_get_path)(DBusMessage*) = nullptr;
    dbus_bool_t (*message_iter_init)(DBusMessage*, DBusMessageIter*) = nullptr;
    int (*message_iter_get_arg_type)(DBusMessageIter*) = nullptr;
    void (*message_iter_get_basic)(DBusMessageIter*, void*) = nullptr;
    dbus_bool_t (*message_iter_next)(DBusMessageIter*) = nullptr;
    void (*message_iter_recurse)(DBusMessageIter*, DBusMessageIter*) = nullptr;
    void (*message_iter_init_append)(DBusMessage*, DBusMessageIter*) = nullptr;
    dbus_bool_t (*message_iter_append_basic)(DBusMessageIter*, int, const void*) = nullptr;
    dbus_bool_t (*message_iter_open_container)(DBusMessageIter*, int, const char*,
                                               DBusMessageIter*) = nullptr;
    dbus_bool_t (*message_iter_close_container)(DBusMessageIter*, DBusMessageIter*) = nullptr;

    template <typename Fn> bool loadSym(Fn& out, const char* sym) {
        out = reinterpret_cast<Fn>(dlsym(lib, sym));
        return out != nullptr;
    }

    bool load() {
        if (lib)
            return true;
        const char* overrideLib = std::getenv("SHIRAZ_LOCATION_DBUS_LIB");
        if (overrideLib && *overrideLib) {
            lib = dlopen(overrideLib, RTLD_LAZY | RTLD_LOCAL);
        } else {
            lib = dlopen("libdbus-1.so.3", RTLD_LAZY | RTLD_LOCAL);
            if (!lib)
                lib = dlopen("libdbus-1.so", RTLD_LAZY | RTLD_LOCAL);
        }
        if (!lib)
            return false;
        const bool ok =
            loadSym(error_init, "dbus_error_init") && loadSym(error_free, "dbus_error_free") &&
            loadSym(error_is_set, "dbus_error_is_set") &&
            loadSym(bus_get_private, "dbus_bus_get_private") &&
            loadSym(bus_add_match, "dbus_bus_add_match") &&
            loadSym(connection_set_exit_on_disconnect, "dbus_connection_set_exit_on_disconnect") &&
            loadSym(connection_get_is_connected, "dbus_connection_get_is_connected") &&
            loadSym(connection_close, "dbus_connection_close") &&
            loadSym(connection_unref, "dbus_connection_unref") &&
            loadSym(connection_send_with_reply_and_block,
                    "dbus_connection_send_with_reply_and_block") &&
            loadSym(connection_read_write, "dbus_connection_read_write") &&
            loadSym(connection_pop_message, "dbus_connection_pop_message") &&
            loadSym(message_new_method_call, "dbus_message_new_method_call") &&
            loadSym(message_unref, "dbus_message_unref") &&
            loadSym(message_get_type, "dbus_message_get_type") &&
            loadSym(message_is_signal, "dbus_message_is_signal") &&
            loadSym(message_get_path, "dbus_message_get_path") &&
            loadSym(message_iter_init, "dbus_message_iter_init") &&
            loadSym(message_iter_get_arg_type, "dbus_message_iter_get_arg_type") &&
            loadSym(message_iter_get_basic, "dbus_message_iter_get_basic") &&
            loadSym(message_iter_next, "dbus_message_iter_next") &&
            loadSym(message_iter_recurse, "dbus_message_iter_recurse") &&
            loadSym(message_iter_init_append, "dbus_message_iter_init_append") &&
            loadSym(message_iter_append_basic, "dbus_message_iter_append_basic") &&
            loadSym(message_iter_open_container, "dbus_message_iter_open_container") &&
            loadSym(message_iter_close_container, "dbus_message_iter_close_container");
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

double wallClockMs() {
    timespec ts{};
    if (clock_gettime(CLOCK_REALTIME, &ts) == 0)
        return static_cast<double>(ts.tv_sec) * 1000.0 + static_cast<double>(ts.tv_nsec) / 1e6;
    return 0.0;
}

bool envDisabled() {
    const char* v = std::getenv("SHIRAZ_DISABLE_HOST_LOCATION");
    return v && std::strcmp(v, "1") == 0;
}

bool isFlatpakSandbox() {
    return access("/.flatpak-info", F_OK) == 0;
}

bool hasDesktopSessionEnv() {
    const char* d = std::getenv("DISPLAY");
    const char* w = std::getenv("WAYLAND_DISPLAY");
    return (d && *d) || (w && *w);
}

DBusConnection* openPrivateBus(DbusApi& api, DBusBusType busType) {
    DBusError err{};
    api.error_init(&err);
    DBusConnection* conn = api.bus_get_private(busType, &err);
    if (api.error_is_set(&err) || !conn) {
        api.error_free(&err);
        if (conn) {
            api.connection_close(conn);
            api.connection_unref(conn);
        }
        return nullptr;
    }
    api.error_free(&err);
    api.connection_set_exit_on_disconnect(conn, 0);
    return conn;
}

void closePrivateBus(DbusApi& api, DBusConnection* conn) {
    if (!conn)
        return;
    api.connection_close(conn);
    api.connection_unref(conn);
}

bool busNameHasOwner(DbusApi& api, DBusConnection* conn, const char* name) {
    DBusMessage* req = api.message_new_method_call("org.freedesktop.DBus", "/org/freedesktop/DBus",
                                                   "org.freedesktop.DBus", "NameHasOwner");
    if (!req)
        return false;
    DBusMessageIter args{};
    api.message_iter_init_append(req, &args);
    if (!api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &name)) {
        api.message_unref(req);
        return false;
    }
    DBusError err{};
    api.error_init(&err);
    DBusMessage* reply = api.connection_send_with_reply_and_block(conn, req, kProbeTimeoutMs, &err);
    api.message_unref(req);
    if (api.error_is_set(&err) || !reply) {
        api.error_free(&err);
        if (reply)
            api.message_unref(reply);
        return false;
    }
    api.error_free(&err);
    dbus_bool_t owned = 0;
    DBusMessageIter iter{};
    if (api.message_iter_init(reply, &iter) &&
        api.message_iter_get_arg_type(&iter) == DBUS_TYPE_BOOLEAN) {
        api.message_iter_get_basic(&iter, &owned);
    }
    api.message_unref(reply);
    return owned != 0;
}

bool busNameActivatable(DbusApi& api, DBusConnection* conn, const char* name) {
    DBusMessage* req = api.message_new_method_call("org.freedesktop.DBus", "/org/freedesktop/DBus",
                                                   "org.freedesktop.DBus", "ListActivatableNames");
    if (!req)
        return false;
    DBusError err{};
    api.error_init(&err);
    DBusMessage* reply = api.connection_send_with_reply_and_block(conn, req, kProbeTimeoutMs, &err);
    api.message_unref(req);
    if (api.error_is_set(&err) || !reply) {
        api.error_free(&err);
        if (reply)
            api.message_unref(reply);
        return false;
    }
    api.error_free(&err);
    bool found = false;
    DBusMessageIter iter{};
    if (api.message_iter_init(reply, &iter) &&
        api.message_iter_get_arg_type(&iter) == DBUS_TYPE_ARRAY) {
        DBusMessageIter sub{};
        api.message_iter_recurse(&iter, &sub);
        while (api.message_iter_get_arg_type(&sub) == DBUS_TYPE_STRING) {
            const char* item = nullptr;
            api.message_iter_get_basic(&sub, &item);
            if (item && std::strcmp(item, name) == 0) {
                found = true;
                break;
            }
            api.message_iter_next(&sub);
        }
    }
    api.message_unref(reply);
    return found;
}

bool probeGeoClue2(DbusApi& api) {
    DBusConnection* conn = openPrivateBus(api, DBUS_BUS_SYSTEM);
    if (!conn)
        return false;
    bool ok = busNameHasOwner(api, conn, "org.freedesktop.GeoClue2");
    if (!ok && hasDesktopSessionEnv())
        ok = busNameActivatable(api, conn, "org.freedesktop.GeoClue2");
    closePrivateBus(api, conn);
    return ok;
}

bool probePortal(DbusApi& api) {
    DBusConnection* conn = openPrivateBus(api, DBUS_BUS_SESSION);
    if (!conn)
        return false;
    const bool ok = busNameHasOwner(api, conn, "org.freedesktop.portal.Desktop");
    closePrivateBus(api, conn);
    return ok;
}

BackendKind detectBackend(DbusApi& api) {
    if (envDisabled() || !api.load())
        return BackendKind::None;
    const char* pref = std::getenv("SHIRAZ_LOCATION_BACKEND");
    if (pref && std::strcmp(pref, "portal") == 0)
        return probePortal(api) ? BackendKind::Portal : BackendKind::None;
    if (pref && std::strcmp(pref, "geoclue2") == 0)
        return probeGeoClue2(api) ? BackendKind::GeoClue2 : BackendKind::None;
    if (isFlatpakSandbox()) {
        if (probePortal(api))
            return BackendKind::Portal;
        if (probeGeoClue2(api))
            return BackendKind::GeoClue2;
        return BackendKind::None;
    }
    if (probeGeoClue2(api))
        return BackendKind::GeoClue2;
    if (probePortal(api))
        return BackendKind::Portal;
    return BackendKind::None;
}

bool appendVariantString(DbusApi& api, DBusMessageIter* iter, const char* value) {
    DBusMessageIter var{};
    if (!api.message_iter_open_container(iter, DBUS_TYPE_VARIANT, "s", &var))
        return false;
    const bool ok = api.message_iter_append_basic(&var, DBUS_TYPE_STRING, &value);
    return api.message_iter_close_container(iter, &var) && ok;
}

bool appendVariantUint32(DbusApi& api, DBusMessageIter* iter, uint32_t value) {
    DBusMessageIter var{};
    if (!api.message_iter_open_container(iter, DBUS_TYPE_VARIANT, "u", &var))
        return false;
    const bool ok = api.message_iter_append_basic(&var, DBUS_TYPE_UINT32, &value);
    return api.message_iter_close_container(iter, &var) && ok;
}

bool appendDictString(DbusApi& api, DBusMessageIter* dict, const char* key, const char* value) {
    DBusMessageIter entry{};
    if (!api.message_iter_open_container(dict, DBUS_TYPE_DICT_ENTRY, nullptr, &entry))
        return false;
    const bool ok = api.message_iter_append_basic(&entry, DBUS_TYPE_STRING, &key) &&
                    appendVariantString(api, &entry, value);
    return api.message_iter_close_container(dict, &entry) && ok;
}

bool appendDictUint32(DbusApi& api, DBusMessageIter* dict, const char* key, uint32_t value) {
    DBusMessageIter entry{};
    if (!api.message_iter_open_container(dict, DBUS_TYPE_DICT_ENTRY, nullptr, &entry))
        return false;
    const bool ok = api.message_iter_append_basic(&entry, DBUS_TYPE_STRING, &key) &&
                    appendVariantUint32(api, &entry, value);
    return api.message_iter_close_container(dict, &entry) && ok;
}

// Parses a GeoClue2 Location property dict or XDG Portal LocationUpdated `a{sv}` dict.
bool parseLocationDict(DbusApi& api, DBusMessageIter* arrayIter, LocationFix& out) {
    if (api.message_iter_get_arg_type(arrayIter) != DBUS_TYPE_ARRAY)
        return false;
    LocationFix parsed{};
    bool hasLat = false;
    bool hasLon = false;
    DBusMessageIter entry{};
    api.message_iter_recurse(arrayIter, &entry);
    while (api.message_iter_get_arg_type(&entry) == DBUS_TYPE_DICT_ENTRY) {
        DBusMessageIter kv{};
        api.message_iter_recurse(&entry, &kv);
        if (api.message_iter_get_arg_type(&kv) == DBUS_TYPE_STRING) {
            const char* key = nullptr;
            api.message_iter_get_basic(&kv, &key);
            if (key && api.message_iter_next(&kv) &&
                api.message_iter_get_arg_type(&kv) == DBUS_TYPE_VARIANT) {
                DBusMessageIter val{};
                api.message_iter_recurse(&kv, &val);
                const int vtype = api.message_iter_get_arg_type(&val);
                if (vtype == DBUS_TYPE_DOUBLE) {
                    double d = 0.0;
                    api.message_iter_get_basic(&val, &d);
                    if (std::isfinite(d)) {
                        if (std::strcmp(key, "Latitude") == 0 && d >= -90.0 && d <= 90.0) {
                            parsed.latitude = d;
                            hasLat = true;
                        } else if (std::strcmp(key, "Longitude") == 0 && d >= -180.0 &&
                                   d <= 180.0) {
                            parsed.longitude = d;
                            hasLon = true;
                        } else if (std::strcmp(key, "Altitude") == 0 && std::fabs(d) <= 1e6) {
                            parsed.altitude = d;
                        } else if (std::strcmp(key, "Accuracy") == 0 && d >= 0.0) {
                            parsed.horizontalAccuracy = d;
                        } else if (std::strcmp(key, "Speed") == 0 && d >= 0.0) {
                            parsed.speed = d;
                        } else if (std::strcmp(key, "Heading") == 0 && d >= 0.0) {
                            parsed.course = d;
                        }
                    }
                } else if (vtype == DBUS_TYPE_STRUCT && std::strcmp(key, "Timestamp") == 0) {
                    DBusMessageIter ts{};
                    api.message_iter_recurse(&val, &ts);
                    uint64_t sec = 0;
                    uint64_t usec = 0;
                    if (api.message_iter_get_arg_type(&ts) == DBUS_TYPE_UINT64) {
                        api.message_iter_get_basic(&ts, &sec);
                        if (api.message_iter_next(&ts) &&
                            api.message_iter_get_arg_type(&ts) == DBUS_TYPE_UINT64) {
                            api.message_iter_get_basic(&ts, &usec);
                        }
                        if (sec > 0)
                            parsed.timestampMs =
                                static_cast<double>(sec) * 1000.0 + static_cast<double>(usec) / 1e3;
                    }
                }
            }
        }
        api.message_iter_next(&entry);
    }
    if (!hasLat || !hasLon)
        return false;
    if (parsed.timestampMs <= 0.0)
        parsed.timestampMs = wallClockMs();
    parsed.valid = true;
    out = parsed;
    return true;
}

struct LocationSession {
    std::mutex mutex;
    std::condition_variable readyCv;
    std::thread worker;
    bool ready = false;
    bool stopRequested = false;
    bool fineAccuracy = true;
    double minDistanceMeters = 0.0;
    BackendKind backend = BackendKind::None;
    LocationFix fix;
    std::string lastError;
};

void setSessionError(LocationSession* s, const std::string& msg) {
    std::lock_guard<std::mutex> lock(s->mutex);
    s->lastError = msg;
}

void publishSessionFix(LocationSession* s, const LocationFix& fix) {
    std::lock_guard<std::mutex> lock(s->mutex);
    s->fix = fix;
    s->lastError.clear();
}

void signalReady(LocationSession* s) {
    {
        std::lock_guard<std::mutex> lock(s->mutex);
        s->ready = true;
    }
    s->readyCv.notify_all();
}

bool sessionStopRequested(LocationSession* s) {
    std::lock_guard<std::mutex> lock(s->mutex);
    return s->stopRequested;
}

bool geoclueSetPropertyString(DbusApi& api, DBusConnection* conn, const std::string& clientPath,
                              const char* prop, const char* value) {
    DBusMessage* req = api.message_new_method_call("org.freedesktop.GeoClue2", clientPath.c_str(),
                                                   "org.freedesktop.DBus.Properties", "Set");
    if (!req)
        return false;
    const char* iface = "org.freedesktop.GeoClue2.Client";
    DBusMessageIter args{};
    api.message_iter_init_append(req, &args);
    const bool built = api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &iface) &&
                       api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &prop) &&
                       appendVariantString(api, &args, value);
    if (!built) {
        api.message_unref(req);
        return false;
    }
    DBusError err{};
    api.error_init(&err);
    DBusMessage* reply = api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
    api.message_unref(req);
    const bool ok = !api.error_is_set(&err) && reply != nullptr;
    api.error_free(&err);
    if (reply)
        api.message_unref(reply);
    return ok;
}

bool geoclueSetPropertyUint32(DbusApi& api, DBusConnection* conn, const std::string& clientPath,
                              const char* prop, uint32_t value) {
    DBusMessage* req = api.message_new_method_call("org.freedesktop.GeoClue2", clientPath.c_str(),
                                                   "org.freedesktop.DBus.Properties", "Set");
    if (!req)
        return false;
    const char* iface = "org.freedesktop.GeoClue2.Client";
    DBusMessageIter args{};
    api.message_iter_init_append(req, &args);
    const bool built = api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &iface) &&
                       api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &prop) &&
                       appendVariantUint32(api, &args, value);
    if (!built) {
        api.message_unref(req);
        return false;
    }
    DBusError err{};
    api.error_init(&err);
    DBusMessage* reply = api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
    api.message_unref(req);
    const bool ok = !api.error_is_set(&err) && reply != nullptr;
    api.error_free(&err);
    if (reply)
        api.message_unref(reply);
    return ok;
}

bool geoclueFetchLocationObject(DbusApi& api, DBusConnection* conn, const std::string& locPath,
                                LocationFix& out) {
    if (locPath.empty() || locPath == "/")
        return false;
    DBusMessage* req = api.message_new_method_call("org.freedesktop.GeoClue2", locPath.c_str(),
                                                   "org.freedesktop.DBus.Properties", "GetAll");
    if (!req)
        return false;
    const char* iface = "org.freedesktop.GeoClue2.Location";
    DBusMessageIter args{};
    api.message_iter_init_append(req, &args);
    if (!api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &iface)) {
        api.message_unref(req);
        return false;
    }
    DBusError err{};
    api.error_init(&err);
    DBusMessage* reply = api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
    api.message_unref(req);
    if (api.error_is_set(&err) || !reply) {
        api.error_free(&err);
        if (reply)
            api.message_unref(reply);
        return false;
    }
    api.error_free(&err);
    DBusMessageIter iter{};
    bool ok = false;
    if (api.message_iter_init(reply, &iter))
        ok = parseLocationDict(api, &iter, out);
    api.message_unref(reply);
    return ok;
}

void runGeoClue2Worker(DbusApi& api, LocationSession* session) {
    DBusConnection* conn = openPrivateBus(api, DBUS_BUS_SYSTEM);
    if (!conn) {
        setSessionError(session, "Cannot connect to system D-Bus for GeoClue2");
        signalReady(session);
        return;
    }

    // 1. Manager.GetClient() -> object path
    std::string clientPath;
    {
        DBusMessage* req = api.message_new_method_call(
            "org.freedesktop.GeoClue2", "/org/freedesktop/GeoClue2/Manager",
            "org.freedesktop.GeoClue2.Manager", "GetClient");
        if (req) {
            DBusError err{};
            api.error_init(&err);
            DBusMessage* reply =
                api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
            api.message_unref(req);
            if (!api.error_is_set(&err) && reply) {
                DBusMessageIter iter{};
                if (api.message_iter_init(reply, &iter) &&
                    api.message_iter_get_arg_type(&iter) == DBUS_TYPE_OBJECT_PATH) {
                    const char* p = nullptr;
                    api.message_iter_get_basic(&iter, &p);
                    if (p)
                        clientPath = p;
                }
            } else if (api.error_is_set(&err) && err.message) {
                setSessionError(session, err.message);
            }
            api.error_free(&err);
            if (reply)
                api.message_unref(reply);
        }
    }
    if (clientPath.empty()) {
        if (session->lastError.empty())
            setSessionError(session, "GeoClue2 GetClient failed");
        closePrivateBus(api, conn);
        signalReady(session);
        return;
    }

    // 2. Configure DesktopId, RequestedAccuracyLevel (8=EXACT, 4=CITY), DistanceThreshold
    const char* desktopId = std::getenv("SHIRAZ_LOCATION_DESKTOP_ID");
    if (!desktopId || !*desktopId)
        desktopId = "org.shiraz.app";
    geoclueSetPropertyString(api, conn, clientPath, "DesktopId", desktopId);
    geoclueSetPropertyUint32(api, conn, clientPath, "RequestedAccuracyLevel",
                             session->fineAccuracy ? 8u : 4u);
    if (session->minDistanceMeters > 0.0 && std::isfinite(session->minDistanceMeters)) {
        const double clamped =
            session->minDistanceMeters > 100000.0 ? 100000.0 : session->minDistanceMeters;
        geoclueSetPropertyUint32(api, conn, clientPath, "DistanceThreshold",
                                 static_cast<uint32_t>(clamped));
    }

    // 3. Subscribe to LocationUpdated signal before calling Start().
    {
        std::string rule = "type='signal',interface='org.freedesktop.GeoClue2.Client',"
                           "member='LocationUpdated',path='" +
                           clientPath + "'";
        DBusError err{};
        api.error_init(&err);
        api.bus_add_match(conn, rule.c_str(), &err);
        api.error_free(&err);
    }

    // 4. Client.Start()
    bool started = false;
    {
        DBusMessage* req =
            api.message_new_method_call("org.freedesktop.GeoClue2", clientPath.c_str(),
                                        "org.freedesktop.GeoClue2.Client", "Start");
        if (req) {
            DBusError err{};
            api.error_init(&err);
            DBusMessage* reply =
                api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
            api.message_unref(req);
            if (!api.error_is_set(&err) && reply) {
                started = true;
            } else {
                setSessionError(session, (api.error_is_set(&err) && err.message)
                                             ? err.message
                                             : "GeoClue2 Client.Start denied");
            }
            api.error_free(&err);
            if (reply)
                api.message_unref(reply);
        }
    }

    // 5. Read any already-available initial Location property.
    if (started) {
        DBusMessage* req =
            api.message_new_method_call("org.freedesktop.GeoClue2", clientPath.c_str(),
                                        "org.freedesktop.DBus.Properties", "Get");
        if (req) {
            const char* iface = "org.freedesktop.GeoClue2.Client";
            const char* prop = "Location";
            DBusMessageIter args{};
            api.message_iter_init_append(req, &args);
            if (api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &iface) &&
                api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &prop)) {
                DBusError err{};
                api.error_init(&err);
                DBusMessage* reply =
                    api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
                api.error_free(&err);
                if (reply) {
                    DBusMessageIter iter{};
                    if (api.message_iter_init(reply, &iter) &&
                        api.message_iter_get_arg_type(&iter) == DBUS_TYPE_VARIANT) {
                        DBusMessageIter sub{};
                        api.message_iter_recurse(&iter, &sub);
                        if (api.message_iter_get_arg_type(&sub) == DBUS_TYPE_OBJECT_PATH) {
                            const char* locPath = nullptr;
                            api.message_iter_get_basic(&sub, &locPath);
                            LocationFix fix{};
                            if (locPath && geoclueFetchLocationObject(api, conn, locPath, fix))
                                publishSessionFix(session, fix);
                        }
                    }
                    api.message_unref(reply);
                }
            }
            api.message_unref(req);
        }
    }

    signalReady(session);

    // 6. Pump LocationUpdated signals until stopped.
    while (started && !sessionStopRequested(session)) {
        if (!api.connection_read_write(conn, kDispatchPollMs))
            break;
        while (DBusMessage* msg = api.connection_pop_message(conn)) {
            if (api.message_is_signal(msg, "org.freedesktop.GeoClue2.Client", "LocationUpdated")) {
                const char* msgPath = api.message_get_path(msg);
                if (!msgPath || clientPath == msgPath) {
                    DBusMessageIter iter{};
                    if (api.message_iter_init(msg, &iter) && api.message_iter_next(&iter) &&
                        api.message_iter_get_arg_type(&iter) == DBUS_TYPE_OBJECT_PATH) {
                        const char* newPath = nullptr;
                        api.message_iter_get_basic(&iter, &newPath);
                        LocationFix fix{};
                        if (newPath && geoclueFetchLocationObject(api, conn, newPath, fix))
                            publishSessionFix(session, fix);
                    }
                }
            }
            api.message_unref(msg);
        }
    }

    if (started) {
        DBusMessage* req =
            api.message_new_method_call("org.freedesktop.GeoClue2", clientPath.c_str(),
                                        "org.freedesktop.GeoClue2.Client", "Stop");
        if (req) {
            DBusError err{};
            api.error_init(&err);
            DBusMessage* reply =
                api.connection_send_with_reply_and_block(conn, req, kProbeTimeoutMs, &err);
            api.message_unref(req);
            api.error_free(&err);
            if (reply)
                api.message_unref(reply);
        }
    }
    closePrivateBus(api, conn);
}

void runPortalWorker(DbusApi& api, LocationSession* session) {
    static std::atomic<uint64_t> tokenSeq{0};
    DBusConnection* conn = openPrivateBus(api, DBUS_BUS_SESSION);
    if (!conn) {
        setSessionError(session, "Cannot connect to session D-Bus for XDG Location Portal");
        signalReady(session);
        return;
    }

    // 1. CreateSession(a{sv}) -> session object path
    const uint64_t seq = ++tokenSeq;
    const std::string sessionToken = "shiraz_loc_" +
                                     std::to_string(static_cast<unsigned long>(getpid())) + "_" +
                                     std::to_string(seq);
    std::string sessionPath;
    {
        DBusMessage* req = api.message_new_method_call(
            "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.Location", "CreateSession");
        if (req) {
            DBusMessageIter args{}, dict{};
            api.message_iter_init_append(req, &args);
            bool built = api.message_iter_open_container(&args, DBUS_TYPE_ARRAY, "{sv}", &dict);
            if (built) {
                built =
                    appendDictString(api, &dict, "session_handle_token", sessionToken.c_str()) &&
                    appendDictUint32(api, &dict, "accuracy", session->fineAccuracy ? 5u : 2u);
                if (built && session->minDistanceMeters > 0.0 &&
                    std::isfinite(session->minDistanceMeters)) {
                    const double clamped = session->minDistanceMeters > 100000.0
                                               ? 100000.0
                                               : session->minDistanceMeters;
                    built = appendDictUint32(api, &dict, "distance-threshold",
                                             static_cast<uint32_t>(clamped));
                }
                built = api.message_iter_close_container(&args, &dict) && built;
            }
            if (built) {
                DBusError err{};
                api.error_init(&err);
                DBusMessage* reply =
                    api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
                if (!api.error_is_set(&err) && reply) {
                    DBusMessageIter iter{};
                    if (api.message_iter_init(reply, &iter) &&
                        api.message_iter_get_arg_type(&iter) == DBUS_TYPE_OBJECT_PATH) {
                        const char* p = nullptr;
                        api.message_iter_get_basic(&iter, &p);
                        if (p)
                            sessionPath = p;
                    }
                } else if (api.error_is_set(&err) && err.message) {
                    setSessionError(session, err.message);
                }
                api.error_free(&err);
                if (reply)
                    api.message_unref(reply);
            }
            api.message_unref(req);
        }
    }
    if (sessionPath.empty()) {
        if (session->lastError.empty())
            setSessionError(session, "XDG Location Portal CreateSession failed");
        closePrivateBus(api, conn);
        signalReady(session);
        return;
    }

    // 2. Subscribe to LocationUpdated signal on the portal.
    {
        DBusError err{};
        api.error_init(&err);
        api.bus_add_match(conn,
                          "type='signal',interface='org.freedesktop.portal.Location',"
                          "member='LocationUpdated',path='/org/freedesktop/portal/desktop'",
                          &err);
        api.error_free(&err);
    }

    // 3. Start(o session_handle, s parent_window, a{sv} options)
    bool started = false;
    {
        const std::string reqToken = sessionToken + "_start";
        DBusMessage* req = api.message_new_method_call("org.freedesktop.portal.Desktop",
                                                       "/org/freedesktop/portal/desktop",
                                                       "org.freedesktop.portal.Location", "Start");
        if (req) {
            const char* sPath = sessionPath.c_str();
            const char* parent = "";
            DBusMessageIter args{}, dict{};
            api.message_iter_init_append(req, &args);
            bool built = api.message_iter_append_basic(&args, DBUS_TYPE_OBJECT_PATH, &sPath) &&
                         api.message_iter_append_basic(&args, DBUS_TYPE_STRING, &parent) &&
                         api.message_iter_open_container(&args, DBUS_TYPE_ARRAY, "{sv}", &dict);
            if (built) {
                built = appendDictString(api, &dict, "handle_token", reqToken.c_str());
                built = api.message_iter_close_container(&args, &dict) && built;
            }
            if (built) {
                DBusError err{};
                api.error_init(&err);
                DBusMessage* reply =
                    api.connection_send_with_reply_and_block(conn, req, kCallTimeoutMs, &err);
                if (!api.error_is_set(&err) && reply) {
                    started = true;
                } else {
                    setSessionError(session, (api.error_is_set(&err) && err.message)
                                                 ? err.message
                                                 : "XDG Location Portal Start failed");
                }
                api.error_free(&err);
                if (reply)
                    api.message_unref(reply);
            }
            api.message_unref(req);
        }
    }

    signalReady(session);

    // 4. Pump LocationUpdated(o session_handle, a{sv} location) signals until stopped.
    while (started && !sessionStopRequested(session)) {
        if (!api.connection_read_write(conn, kDispatchPollMs))
            break;
        while (DBusMessage* msg = api.connection_pop_message(conn)) {
            if (api.message_is_signal(msg, "org.freedesktop.portal.Location", "LocationUpdated")) {
                DBusMessageIter iter{};
                if (api.message_iter_init(msg, &iter) &&
                    api.message_iter_get_arg_type(&iter) == DBUS_TYPE_OBJECT_PATH) {
                    const char* sigPath = nullptr;
                    api.message_iter_get_basic(&iter, &sigPath);
                    if (sigPath && sessionPath == sigPath && api.message_iter_next(&iter)) {
                        LocationFix fix{};
                        if (parseLocationDict(api, &iter, fix))
                            publishSessionFix(session, fix);
                    }
                }
            }
            api.message_unref(msg);
        }
    }

    // 5. Close portal session.
    {
        DBusMessage* req =
            api.message_new_method_call("org.freedesktop.portal.Desktop", sessionPath.c_str(),
                                        "org.freedesktop.portal.Session", "Close");
        if (req) {
            DBusError err{};
            api.error_init(&err);
            DBusMessage* reply =
                api.connection_send_with_reply_and_block(conn, req, kProbeTimeoutMs, &err);
            api.message_unref(req);
            api.error_free(&err);
            if (reply)
                api.message_unref(reply);
        }
    }
    closePrivateBus(api, conn);
}

void runLocationWorker(LocationSession* session) {
    DbusApi api;
    if (!api.load()) {
        setSessionError(session, "libdbus-1 unavailable");
        signalReady(session);
        return;
    }
    if (session->backend == BackendKind::Portal) {
        runPortalWorker(api, session);
    } else if (session->backend == BackendKind::GeoClue2) {
        runGeoClue2Worker(api, session);
    } else {
        setSessionError(session, "No Linux host location service available");
        signalReady(session);
    }
}

LocationSession* toSession(jlong handle) {
    return handle > 0 ? reinterpret_cast<LocationSession*>(static_cast<intptr_t>(handle)) : nullptr;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_shiraz_runtime_HostLocation_nativeAuthorizationStatus(JNIEnv*, jclass) {
    DbusApi api;
    return detectBackend(api) != BackendKind::None ? kStatusAuthorized : kStatusUnavailable;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_shiraz_runtime_HostLocation_nativeLocationServicesEnabled(JNIEnv*, jclass) {
    DbusApi api;
    return detectBackend(api) != BackendKind::None ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_HostLocation_nativeStart(
    JNIEnv*, jclass, jboolean fineAccuracy, jdouble minDistanceMeters) {
    DbusApi api;
    const BackendKind backend = detectBackend(api);
    if (backend == BackendKind::None)
        return 0;
    auto* session = new LocationSession();
    session->fineAccuracy = (fineAccuracy == JNI_TRUE);
    session->minDistanceMeters = minDistanceMeters;
    session->backend = backend;
    session->worker = std::thread(runLocationWorker, session);

    std::unique_lock<std::mutex> lock(session->mutex);
    session->readyCv.wait(lock, [&]() { return session->ready; });
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_HostLocation_nativeStop(JNIEnv*, jclass,
                                                                              jlong handle) {
    LocationSession* session = toSession(handle);
    if (!session)
        return;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->stopRequested = true;
    }
    if (session->worker.joinable())
        session->worker.join();
    delete session;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_shiraz_runtime_HostLocation_nativePollFix(JNIEnv* env, jclass, jlong handle) {
    LocationSession* session = toSession(handle);
    if (!session)
        return nullptr;
    LocationFix snapshot;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        snapshot = session->fix;
    }
    if (!snapshot.valid)
        return nullptr;
    jdouble values[7] = {
        snapshot.latitude, snapshot.longitude, snapshot.altitude,    snapshot.horizontalAccuracy,
        snapshot.speed,    snapshot.course,    snapshot.timestampMs,
    };
    jdoubleArray out = env->NewDoubleArray(7);
    if (!out)
        return nullptr;
    env->SetDoubleArrayRegion(out, 0, 7, values);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_shiraz_runtime_HostLocation_nativeLastError(JNIEnv* env, jclass, jlong handle) {
    LocationSession* session = toSession(handle);
    if (!session)
        return env->NewStringUTF("");
    std::string message;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        message = session->lastError;
    }
    return env->NewStringUTF(message.c_str());
}
