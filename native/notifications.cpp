// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <dbus/dbus.h>
#include <algorithm>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <set>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
constexpr const char* service = "org.freedesktop.Notifications";
constexpr const char* path = "/org/freedesktop/Notifications";
constexpr int timeoutMs = 1500;
struct Pending {};
struct Error {
    DBusError value;
    Error() { dbus_error_init(&value); }
    ~Error() { dbus_error_free(&value); }
};
using Message = std::unique_ptr<DBusMessage, decltype(&dbus_message_unref)>;
Message own(DBusMessage* value) { if (!value) throw std::bad_alloc(); return Message(value, dbus_message_unref); }
void require(bool success) { if (!success) throw std::bad_alloc(); }
void pending(JNIEnv* env) { if (env->ExceptionCheck()) throw Pending{}; }
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    auto cls = env->FindClass(type); if (!cls) return;
    env->ThrowNew(cls, message); env->DeleteLocalRef(cls);
}
template<class F> auto guarded(JNIEnv* env, F function) -> decltype(function()) {
    try { return function(); }
    catch (const Pending&) { }
    catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate desktop notification data"); }
    catch (const std::invalid_argument& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
    catch (const std::exception& issue) { error(env, "java/lang/IllegalStateException", issue.what()); }
    return {};
}
std::string text(JNIEnv* env, jstring value, jsize limit) {
    if (!value) throw std::invalid_argument("Null notification text");
    jsize count = env->GetStringLength(value);
    if (count > limit) throw std::invalid_argument("Notification text exceeds host limit");
    std::vector<jchar> input(static_cast<std::size_t>(count));
    if (count) env->GetStringRegion(value, 0, count, input.data());
    pending(env); std::string result;
    for (jsize index = 0; index < count; ++index) {
        uint32_t cp = input[index];
        if (cp >= 0xd800 && cp <= 0xdbff) {
            if (++index == count || input[index] < 0xdc00 || input[index] > 0xdfff) throw std::invalid_argument("Unpaired notification surrogate");
            cp = 0x10000 + ((cp - 0xd800) << 10) + input[index] - 0xdc00;
        } else if (cp >= 0xdc00 && cp <= 0xdfff) throw std::invalid_argument("Unpaired notification surrogate");
        if (!cp) throw std::invalid_argument("NUL in notification text");
        if (cp < 0x80) result.push_back(static_cast<char>(cp));
        else if (cp < 0x800) { result.push_back(static_cast<char>(0xc0 | (cp >> 6))); result.push_back(static_cast<char>(0x80 | (cp & 63))); }
        else if (cp < 0x10000) { result.push_back(static_cast<char>(0xe0 | (cp >> 12))); result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63))); result.push_back(static_cast<char>(0x80 | (cp & 63))); }
        else { result.push_back(static_cast<char>(0xf0 | (cp >> 18))); result.push_back(static_cast<char>(0x80 | ((cp >> 12) & 63))); result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63))); result.push_back(static_cast<char>(0x80 | (cp & 63))); }
    }
    return result;
}
Message call(DBusConnection* connection, DBusMessage* request) {
    Error issue;
    auto* reply = dbus_connection_send_with_reply_and_block(connection, request, timeoutMs, &issue.value);
    if (!reply) throw std::runtime_error("Desktop notification service call failed or timed out");
    return own(reply);
}
struct Client {
    DBusConnection* connection = nullptr;
    std::string application, owner;
    std::vector<std::string> caps;
    std::set<dbus_uint32_t> ids;
    bool ownerLost = false;
    std::mutex mutex;
    ~Client() { if (connection) { dbus_connection_close(connection); dbus_connection_unref(connection); } }
    void live() {
        if (!connection || !dbus_connection_get_is_connected(connection)) throw std::logic_error("Notification connection closed");
        if (ownerLost) throw std::logic_error("Desktop notification service owner changed; reconnect required");
    }
    Message request(const char* method) { live(); return own(dbus_message_new_method_call(owner.c_str(), path, service, method)); }
    void drain() {
        live(); dbus_connection_read_write(connection, 0);
        for (int count = 0; count < 256; ++count) {
            auto* next = dbus_connection_pop_message(connection); if (!next) break;
            auto message = own(next);
            if (dbus_message_is_signal(next, service, "NotificationClosed") && dbus_message_has_path(next, path)
                    && dbus_message_get_sender(next) && owner == dbus_message_get_sender(next)) {
                dbus_uint32_t id = 0, reason = 0; Error issue;
                if (!dbus_message_get_args(next, &issue.value, DBUS_TYPE_UINT32, &id, DBUS_TYPE_UINT32, &reason, DBUS_TYPE_INVALID))
                    throw std::runtime_error("Malformed NotificationClosed signal");
                ids.erase(id);
            } else if (dbus_message_is_signal(next, DBUS_INTERFACE_DBUS, "NameOwnerChanged")
                    && dbus_message_has_path(next, DBUS_PATH_DBUS) && dbus_message_has_sender(next, DBUS_SERVICE_DBUS)) {
                const char *name = nullptr, *previous = nullptr, *current = nullptr; Error issue;
                if (!dbus_message_get_args(next, &issue.value, DBUS_TYPE_STRING, &name, DBUS_TYPE_STRING, &previous,
                                           DBUS_TYPE_STRING, &current, DBUS_TYPE_INVALID)) throw std::runtime_error("Malformed service ownership signal");
                if (std::strcmp(name, service) == 0 && owner == previous && owner != current) ownerLost = true;
            }
        }
        live();
    }
};
std::mutex registry;
std::unordered_map<jlong, std::shared_ptr<Client>> clients;
jlong nextHandle = 1;
std::shared_ptr<Client> client(jlong handle) {
    std::lock_guard<std::mutex> lock(registry); auto found = clients.find(handle);
    if (found == clients.end()) throw std::logic_error("Notification connection closed");
    return found->second;
}
std::vector<std::string> capabilities(Client& c) {
    auto request = c.request("GetCapabilities"), reply = call(c.connection, request.get());
    if (!dbus_message_has_signature(reply.get(), "as")) throw std::runtime_error("Malformed notification capabilities");
    DBusMessageIter outer, array; dbus_message_iter_init(reply.get(), &outer); dbus_message_iter_recurse(&outer, &array);
    std::vector<std::string> result;
    while (dbus_message_iter_get_arg_type(&array) != DBUS_TYPE_INVALID) {
        const char* value = nullptr; dbus_message_iter_get_basic(&array, &value);
        if (result.size() >= 64 || !*value || std::strlen(value) > 128) throw std::runtime_error("Notification capability limit exceeded");
        for (const char* p = value; *p; ++p) if (!((*p >= 'a' && *p <= 'z') || (*p >= 'A' && *p <= 'Z') || (*p >= '0' && *p <= '9') || *p == '-'))
            throw std::runtime_error("Malformed notification capability name");
        result.emplace_back(value); dbus_message_iter_next(&array);
    }
    return result;
}
bool supports(const Client& c, const char* name) { return std::find(c.caps.begin(), c.caps.end(), name) != c.caps.end(); }
void hint(DBusMessageIter* hints, const char* name, int type, const char* signature, const void* value) {
    DBusMessageIter entry, variant;
    require(dbus_message_iter_open_container(hints, DBUS_TYPE_DICT_ENTRY, nullptr, &entry));
    require(dbus_message_iter_append_basic(&entry, DBUS_TYPE_STRING, &name));
    require(dbus_message_iter_open_container(&entry, DBUS_TYPE_VARIANT, signature, &variant));
    require(dbus_message_iter_append_basic(&variant, type, value));
    require(dbus_message_iter_close_container(&entry, &variant));
    require(dbus_message_iter_close_container(hints, &entry));
}
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_DesktopNotifications_open(JNIEnv* env, jclass, jstring application) {
    return guarded(env, [&]() -> jlong {
        static std::once_flag initialized;
        std::call_once(initialized, [] { require(dbus_threads_init_default()); });
        auto c = std::make_shared<Client>(); c->application = text(env, application, 256);
        Error issue; c->connection = dbus_bus_get_private(DBUS_BUS_SESSION, &issue.value);
        if (!c->connection) throw std::runtime_error("Cannot connect to session bus for desktop notifications");
        dbus_connection_set_exit_on_disconnect(c->connection, false);
        dbus_connection_set_max_message_size(c->connection, 128 * 1024);
        dbus_connection_set_max_received_size(c->connection, 256 * 1024);
        dbus_bus_add_match(c->connection, "type='signal',sender='org.freedesktop.DBus',interface='org.freedesktop.DBus',member='NameOwnerChanged',arg0='org.freedesktop.Notifications'", &issue.value);
        if (dbus_error_is_set(&issue.value)) throw std::runtime_error("Cannot watch desktop notification service owner");
        auto request = own(dbus_message_new_method_call(DBUS_SERVICE_DBUS, DBUS_PATH_DBUS, DBUS_INTERFACE_DBUS, "GetNameOwner"));
        require(dbus_message_append_args(request.get(), DBUS_TYPE_STRING, &service, DBUS_TYPE_INVALID));
        auto reply = call(c->connection, request.get()); const char* owner = nullptr;
        if (!dbus_message_get_args(reply.get(), &issue.value, DBUS_TYPE_STRING, &owner, DBUS_TYPE_INVALID)) throw std::runtime_error("Malformed desktop service owner");
        c->owner = owner;
        std::string match = "type='signal',sender='" + c->owner + "',interface='org.freedesktop.Notifications',member='NotificationClosed',path='/org/freedesktop/Notifications'";
        dbus_bus_add_match(c->connection, match.c_str(), &issue.value);
        if (dbus_error_is_set(&issue.value)) throw std::runtime_error("Cannot subscribe to desktop notification closure");
        c->caps = capabilities(*c);
        std::lock_guard<std::mutex> lock(registry);
        if (clients.size() >= 128 || nextHandle == std::numeric_limits<jlong>::max()) throw std::runtime_error("Desktop notification connection limit exceeded");
        auto id = nextHandle++; clients.emplace(id, c); return id;
    });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_DesktopNotifications_close(JNIEnv* env, jclass, jlong handle) {
    guarded(env, [&]() -> int {
        std::shared_ptr<Client> c;
        { std::lock_guard<std::mutex> lock(registry); auto found = clients.find(handle); if (found == clients.end()) return 0; c = found->second; clients.erase(found); }
        std::lock_guard<std::mutex> lock(c->mutex);
        if (c->connection) { dbus_connection_close(c->connection); dbus_connection_unref(c->connection); c->connection = nullptr; }
        return 0;
    });
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_shiraz_runtime_DesktopNotifications_capabilities(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jobjectArray {
        auto c = client(handle); std::lock_guard<std::mutex> lock(c->mutex); c->drain();
        auto cls = env->FindClass("java/lang/String"); pending(env);
        auto result = env->NewObjectArray(static_cast<jsize>(c->caps.size()), cls, nullptr); env->DeleteLocalRef(cls); pending(env);
        for (std::size_t i = 0; i < c->caps.size(); ++i) {
            auto value = env->NewStringUTF(c->caps[i].c_str()); pending(env); env->SetObjectArrayElement(result, static_cast<jsize>(i), value); env->DeleteLocalRef(value); pending(env);
        }
        return result;
    });
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_DesktopNotifications_post(JNIEnv* env, jclass, jlong handle, jlong replaces, jstring summary, jstring body, jint urgency, jint timeout, jboolean suppressSound) {
    return guarded(env, [&]() -> jlong {
        if (replaces < 0 || replaces > std::numeric_limits<dbus_uint32_t>::max() || urgency < 0 || urgency > 2 || timeout < -1) throw std::invalid_argument("Invalid desktop notification options");
        auto title = text(env, summary, 8192), content = text(env, body, 8192);
        auto c = client(handle); std::lock_guard<std::mutex> lock(c->mutex); c->drain();
        auto id = static_cast<dbus_uint32_t>(replaces);
        if (id && !c->ids.count(id)) throw std::invalid_argument("Replacement notification is not owned by this connection");
        if (!id && c->ids.size() >= 256) throw std::runtime_error("Active notification limit exceeded");
        if (!content.empty() && !supports(*c, "body")) throw std::runtime_error("Desktop server cannot display notification bodies");
        if (supports(*c, "body-markup")) {
            std::string escaped;
            for (char value : content) { if (value == '&') escaped += "&amp;"; else if (value == '<') escaped += "&lt;"; else if (value == '>') escaped += "&gt;"; else escaped += value; }
            content = std::move(escaped);
        }
        auto request = c->request("Notify"); DBusMessageIter args, array, hints;
        dbus_message_iter_init_append(request.get(), &args);
        const char* app = c->application.c_str(); const char* icon = ""; const char* titleText = title.c_str(); const char* bodyText = content.c_str();
        require(dbus_message_iter_append_basic(&args, DBUS_TYPE_STRING, &app));
        require(dbus_message_iter_append_basic(&args, DBUS_TYPE_UINT32, &id));
        for (const char* value : {icon, titleText, bodyText}) require(dbus_message_iter_append_basic(&args, DBUS_TYPE_STRING, &value));
        require(dbus_message_iter_open_container(&args, DBUS_TYPE_ARRAY, "s", &array)); require(dbus_message_iter_close_container(&args, &array));
        require(dbus_message_iter_open_container(&args, DBUS_TYPE_ARRAY, "{sv}", &hints));
        unsigned char level = static_cast<unsigned char>(urgency); dbus_bool_t quiet = suppressSound;
        hint(&hints, "urgency", DBUS_TYPE_BYTE, "y", &level); hint(&hints, "suppress-sound", DBUS_TYPE_BOOLEAN, "b", &quiet);
        require(dbus_message_iter_close_container(&args, &hints));
        dbus_int32_t expiration = timeout; require(dbus_message_iter_append_basic(&args, DBUS_TYPE_INT32, &expiration));
        auto reply = call(c->connection, request.get()); dbus_uint32_t posted = 0; Error issue;
        if (!dbus_message_get_args(reply.get(), &issue.value, DBUS_TYPE_UINT32, &posted, DBUS_TYPE_INVALID) || !posted || (id && posted != id))
            throw std::runtime_error("Malformed desktop notification identifier");
        c->ids.insert(posted); return posted;
    });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_DesktopNotifications_cancel(JNIEnv* env, jclass, jlong handle, jlong id) {
    guarded(env, [&]() -> int {
        if (id <= 0 || id > std::numeric_limits<dbus_uint32_t>::max()) throw std::invalid_argument("Invalid desktop notification identifier");
        auto c = client(handle); std::lock_guard<std::mutex> lock(c->mutex); c->drain(); auto value = static_cast<dbus_uint32_t>(id);
        if (!c->ids.count(value)) return 0;
        auto request = c->request("CloseNotification"); require(dbus_message_append_args(request.get(), DBUS_TYPE_UINT32, &value, DBUS_TYPE_INVALID));
        Message reply(nullptr, dbus_message_unref);
        try { reply = call(c->connection, request.get()); }
        catch (const std::runtime_error&) {
            // Expiry may win between draining the queue and CloseNotification.
            // Suppress the resulting error only if the trusted closure signal
            // actually invalidated this exact ID; other server errors propagate.
            c->drain();
            if (!c->ids.count(value)) return 0;
            throw;
        }
        if (!dbus_message_has_signature(reply.get(), "")) throw std::runtime_error("Malformed notification cancellation reply");
        c->ids.erase(value); return 0;
    });
}
extern "C" JNIEXPORT jlongArray JNICALL Java_shiraz_runtime_DesktopNotifications_activeIds(JNIEnv* env, jclass, jlong handle) {
    return guarded(env, [&]() -> jlongArray {
        auto c = client(handle); std::lock_guard<std::mutex> lock(c->mutex); c->drain(); std::vector<jlong> ids(c->ids.begin(), c->ids.end());
        auto result = env->NewLongArray(static_cast<jsize>(ids.size())); pending(env);
        if (!ids.empty()) env->SetLongArrayRegion(result, 0, static_cast<jsize>(ids.size()), ids.data());
        pending(env); return result;
    });
}
