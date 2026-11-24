// SPDX-License-Identifier: GPL-3.0-only
// Mock libdbus-1 shared library fixture for testing native/linux/location.cpp
// without requiring a live system GeoClue2 daemon or desktop portal.
#include <dbus/dbus.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <variant>
#include <vector>

struct Val {
    int type = DBUS_TYPE_INVALID;
    std::string str;
    double dbl = 0.0;
    uint32_t u32 = 0;
    dbus_bool_t boolean = 0;
    uint64_t t1 = 0, t2 = 0;
    std::vector<std::string> strArray;
    std::map<std::string, Val> dict;
};

struct DBusMessage {
    int type = DBUS_MESSAGE_TYPE_METHOD_CALL;
    std::string dest, path, iface, member;
    std::vector<Val> args;
};

struct DBusConnection {
    DBusBusType busType = DBUS_BUS_SYSTEM;
    bool connected = true;
    bool signalDelivered = false;
    bool started = false;
    bool stopped = false;
    std::vector<DBusMessage*> inbox;
};

struct IterState {
    const std::vector<Val>* list = nullptr;
    size_t listIdx = 0;
    const std::map<std::string, Val>* dict = nullptr;
    std::map<std::string, Val>::const_iterator dictIt;
    const std::pair<const std::string, Val>* kv = nullptr;
    int kvStep = 0;
    const Val* single = nullptr;
    bool singleConsumed = false;
    int structStep = 0;
    const std::vector<std::string>* strArr = nullptr;
    size_t strArrIdx = 0;
    // Append mode:
    DBusMessage* outMsg = nullptr;
    bool inDict = false;
};

static_assert(sizeof(IterState*) <= sizeof(DBusMessageIter), "DBusMessageIter too small");

namespace {
std::mutex gStateMutex;
std::vector<IterState*> gIters;
std::atomic<int> gStarts{0};
std::atomic<int> gStops{0};

IterState* makeIter(DBusMessageIter* raw) {
    auto* st = new IterState();
    std::memset(raw, 0, sizeof(*raw));
    std::memcpy(raw, &st, sizeof(st));
    std::lock_guard<std::mutex> lock(gStateMutex);
    gIters.push_back(st);
    return st;
}

IterState* getIter(DBusMessageIter* raw) {
    IterState* st = nullptr;
    std::memcpy(&st, raw, sizeof(st));
    return st;
}

Val makeDouble(double d) {
    Val v;
    v.type = DBUS_TYPE_DOUBLE;
    v.dbl = d;
    return v;
}

Val makeTimestamp(uint64_t sec, uint64_t usec) {
    Val v;
    v.type = DBUS_TYPE_STRUCT;
    v.t1 = sec;
    v.t2 = usec;
    return v;
}

Val sampleLocationDict(bool portal) {
    Val dict;
    dict.type = DBUS_TYPE_ARRAY;
    dict.dict["Latitude"] = makeDouble(portal ? 48.8566 : 52.5200);
    dict.dict["Longitude"] = makeDouble(portal ? 2.3522 : 13.4050);
    dict.dict["Altitude"] = makeDouble(portal ? 35.0 : 42.5);
    dict.dict["Accuracy"] = makeDouble(6.5);
    dict.dict["Speed"] = makeDouble(2.2);
    dict.dict["Heading"] = makeDouble(180.0);
    dict.dict["Timestamp"] = makeTimestamp(1730000000ULL, 500000ULL);
    return dict;
}

} // namespace

extern "C" {

int shiraz_mock_dbus_starts() {
    return gStarts.load();
}
int shiraz_mock_dbus_stops() {
    return gStops.load();
}

void dbus_error_init(DBusError* err) {
    if (err)
        std::memset(err, 0, sizeof(*err));
}
void dbus_error_free(DBusError* err) {
    if (err)
        std::memset(err, 0, sizeof(*err));
}
dbus_bool_t dbus_error_is_set(const DBusError* err) {
    return (err && err->name != nullptr) ? 1 : 0;
}

DBusConnection* dbus_bus_get_private(DBusBusType type, DBusError*) {
    auto* c = new DBusConnection();
    c->busType = type;
    return c;
}
void dbus_bus_add_match(DBusConnection*, const char*, DBusError*) {}
void dbus_connection_set_exit_on_disconnect(DBusConnection*, dbus_bool_t) {}
dbus_bool_t dbus_connection_get_is_connected(DBusConnection* c) {
    return (c && c->connected) ? 1 : 0;
}
void dbus_connection_close(DBusConnection* c) {
    if (c)
        c->connected = false;
}
void dbus_connection_unref(DBusConnection* c) {
    if (!c)
        return;
    for (auto* m : c->inbox)
        delete m;
    delete c;
}

DBusMessage* dbus_message_new_method_call(const char* dest, const char* path, const char* iface,
                                          const char* method) {
    auto* m = new DBusMessage();
    m->dest = dest ? dest : "";
    m->path = path ? path : "";
    m->iface = iface ? iface : "";
    m->member = method ? method : "";
    return m;
}
void dbus_message_unref(DBusMessage* m) {
    delete m;
}
int dbus_message_get_type(DBusMessage* m) {
    return m ? m->type : DBUS_MESSAGE_TYPE_INVALID;
}
dbus_bool_t dbus_message_is_signal(DBusMessage* m, const char* iface, const char* member) {
    if (!m || m->type != DBUS_MESSAGE_TYPE_SIGNAL)
        return 0;
    return (m->iface == (iface ? iface : "") && m->member == (member ? member : "")) ? 1 : 0;
}
const char* dbus_message_get_path(DBusMessage* m) {
    return m ? m->path.c_str() : nullptr;
}

DBusMessage* dbus_connection_send_with_reply_and_block(DBusConnection* conn, DBusMessage* req, int,
                                                       DBusError*) {
    auto* reply = new DBusMessage();
    reply->type = DBUS_MESSAGE_TYPE_METHOD_RETURN;
    const char* mode = std::getenv("SHIRAZ_MOCK_LOCATION_MODE");
    const std::string modeStr = mode ? mode : "geoclue2";

    if (req->member == "NameHasOwner") {
        Val b;
        b.type = DBUS_TYPE_BOOLEAN;
        const std::string target = (!req->args.empty()) ? req->args[0].str : "";
        if (modeStr == "geoclue2" && target == "org.freedesktop.GeoClue2")
            b.boolean = 1;
        else if (modeStr == "portal" && target == "org.freedesktop.portal.Desktop")
            b.boolean = 1;
        else
            b.boolean = 0;
        reply->args.push_back(b);
        return reply;
    }
    if (req->member == "ListActivatableNames") {
        Val arr;
        arr.type = DBUS_TYPE_ARRAY;
        reply->args.push_back(arr);
        return reply;
    }
    if (req->member == "GetClient") {
        Val p;
        p.type = DBUS_TYPE_OBJECT_PATH;
        p.str = "/org/freedesktop/GeoClue2/Client/1";
        reply->args.push_back(p);
        return reply;
    }
    if (req->member == "CreateSession") {
        Val p;
        p.type = DBUS_TYPE_OBJECT_PATH;
        p.str = "/org/freedesktop/portal/desktop/session/mock/1";
        reply->args.push_back(p);
        return reply;
    }
    if (req->member == "Start") {
        conn->started = true;
        ++gStarts;
        if (req->iface == "org.freedesktop.portal.Location") {
            auto* sig = new DBusMessage();
            sig->type = DBUS_MESSAGE_TYPE_SIGNAL;
            sig->path = "/org/freedesktop/portal/desktop";
            sig->iface = "org.freedesktop.portal.Location";
            sig->member = "LocationUpdated";
            Val sp;
            sp.type = DBUS_TYPE_OBJECT_PATH;
            sp.str = "/org/freedesktop/portal/desktop/session/mock/1";
            sig->args.push_back(sp);
            sig->args.push_back(sampleLocationDict(true));
            conn->inbox.push_back(sig);
        } else if (req->iface == "org.freedesktop.GeoClue2.Client") {
            auto* sig = new DBusMessage();
            sig->type = DBUS_MESSAGE_TYPE_SIGNAL;
            sig->path = "/org/freedesktop/GeoClue2/Client/1";
            sig->iface = "org.freedesktop.GeoClue2.Client";
            sig->member = "LocationUpdated";
            Val oldP;
            oldP.type = DBUS_TYPE_OBJECT_PATH;
            oldP.str = "/";
            Val newP;
            newP.type = DBUS_TYPE_OBJECT_PATH;
            newP.str = "/org/freedesktop/GeoClue2/Location/42";
            sig->args.push_back(oldP);
            sig->args.push_back(newP);
            conn->inbox.push_back(sig);
        }
        return reply;
    }
    if (req->member == "Stop" || req->member == "Close") {
        conn->stopped = true;
        ++gStops;
        return reply;
    }
    if (req->member == "Get" && req->iface == "org.freedesktop.DBus.Properties") {
        Val p;
        p.type = DBUS_TYPE_OBJECT_PATH;
        p.str = "/";
        Val wrap;
        wrap.type = DBUS_TYPE_VARIANT;
        wrap.str = p.str;
        wrap.u32 = static_cast<uint32_t>(DBUS_TYPE_OBJECT_PATH);
        reply->args.push_back(wrap);
        return reply;
    }
    if (req->member == "GetAll" && req->iface == "org.freedesktop.DBus.Properties") {
        reply->args.push_back(sampleLocationDict(false));
        return reply;
    }
    return reply;
}

dbus_bool_t dbus_connection_read_write(DBusConnection* c, int timeoutMs) {
    if (!c || !c->connected)
        return 0;
    if (c->inbox.empty())
        std::this_thread::sleep_for(std::chrono::milliseconds(timeoutMs > 20 ? 20 : timeoutMs));
    return 1;
}

DBusMessage* dbus_connection_pop_message(DBusConnection* c) {
    if (!c || c->inbox.empty())
        return nullptr;
    DBusMessage* m = c->inbox.front();
    c->inbox.erase(c->inbox.begin());
    return m;
}

dbus_bool_t dbus_message_iter_init(DBusMessage* m, DBusMessageIter* iter) {
    if (!m || m->args.empty())
        return 0;
    auto* st = makeIter(iter);
    st->list = &m->args;
    st->listIdx = 0;
    return 1;
}

int dbus_message_iter_get_arg_type(DBusMessageIter* iter) {
    auto* st = getIter(iter);
    if (!st)
        return DBUS_TYPE_INVALID;
    if (st->list)
        return st->listIdx < st->list->size() ? (*st->list)[st->listIdx].type : DBUS_TYPE_INVALID;
    if (st->dict)
        return st->dictIt != st->dict->end() ? DBUS_TYPE_DICT_ENTRY : DBUS_TYPE_INVALID;
    if (st->kv)
        return st->kvStep == 0 ? DBUS_TYPE_STRING
                               : (st->kvStep == 1 ? DBUS_TYPE_VARIANT : DBUS_TYPE_INVALID);
    if (st->single) {
        if (st->single->type == DBUS_TYPE_STRUCT)
            return st->structStep < 2 ? DBUS_TYPE_UINT64 : DBUS_TYPE_INVALID;
        if (st->single->type == DBUS_TYPE_VARIANT)
            return !st->singleConsumed ? static_cast<int>(st->single->u32) : DBUS_TYPE_INVALID;
        return !st->singleConsumed ? st->single->type : DBUS_TYPE_INVALID;
    }
    if (st->strArr)
        return st->strArrIdx < st->strArr->size() ? DBUS_TYPE_STRING : DBUS_TYPE_INVALID;
    return DBUS_TYPE_INVALID;
}

void dbus_message_iter_get_basic(DBusMessageIter* iter, void* value) {
    auto* st = getIter(iter);
    if (!st || !value)
        return;
    if (st->list && st->listIdx < st->list->size()) {
        const Val& v = (*st->list)[st->listIdx];
        if (v.type == DBUS_TYPE_BOOLEAN)
            *static_cast<dbus_bool_t*>(value) = v.boolean;
        else if (v.type == DBUS_TYPE_STRING || v.type == DBUS_TYPE_OBJECT_PATH)
            *static_cast<const char**>(value) = v.str.c_str();
        else if (v.type == DBUS_TYPE_DOUBLE)
            *static_cast<double*>(value) = v.dbl;
        return;
    }
    if (st->kv && st->kvStep == 0) {
        *static_cast<const char**>(value) = st->kv->first.c_str();
        return;
    }
    if (st->single) {
        if (st->single->type == DBUS_TYPE_STRUCT) {
            *static_cast<uint64_t*>(value) = st->structStep == 0 ? st->single->t1 : st->single->t2;
            return;
        }
        if (st->single->type == DBUS_TYPE_VARIANT && st->single->u32 == DBUS_TYPE_OBJECT_PATH) {
            *static_cast<const char**>(value) = st->single->str.c_str();
            return;
        }
        if (st->single->type == DBUS_TYPE_DOUBLE)
            *static_cast<double*>(value) = st->single->dbl;
        else if (st->single->type == DBUS_TYPE_STRING || st->single->type == DBUS_TYPE_OBJECT_PATH)
            *static_cast<const char**>(value) = st->single->str.c_str();
        return;
    }
    if (st->strArr && st->strArrIdx < st->strArr->size()) {
        *static_cast<const char**>(value) = (*st->strArr)[st->strArrIdx].c_str();
    }
}

dbus_bool_t dbus_message_iter_next(DBusMessageIter* iter) {
    auto* st = getIter(iter);
    if (!st)
        return 0;
    if (st->list) {
        if (st->listIdx + 1 < st->list->size()) {
            ++st->listIdx;
            return 1;
        }
        st->listIdx = st->list->size();
        return 0;
    }
    if (st->dict) {
        if (st->dictIt != st->dict->end()) {
            ++st->dictIt;
            return st->dictIt != st->dict->end() ? 1 : 0;
        }
        return 0;
    }
    if (st->kv) {
        ++st->kvStep;
        return st->kvStep < 2 ? 1 : 0;
    }
    if (st->single) {
        if (st->single->type == DBUS_TYPE_STRUCT) {
            ++st->structStep;
            return st->structStep < 2 ? 1 : 0;
        }
        st->singleConsumed = true;
        return 0;
    }
    if (st->strArr) {
        if (st->strArrIdx + 1 < st->strArr->size()) {
            ++st->strArrIdx;
            return 1;
        }
        st->strArrIdx = st->strArr->size();
        return 0;
    }
    return 0;
}

void dbus_message_iter_recurse(DBusMessageIter* iter, DBusMessageIter* sub) {
    auto* st = getIter(iter);
    auto* child = makeIter(sub);
    if (!st)
        return;
    if (st->list && st->listIdx < st->list->size()) {
        const Val& v = (*st->list)[st->listIdx];
        if (v.type == DBUS_TYPE_ARRAY) {
            if (!v.dict.empty()) {
                child->dict = &v.dict;
                child->dictIt = v.dict.begin();
            } else {
                child->strArr = &v.strArray;
                child->strArrIdx = 0;
            }
        } else if (v.type == DBUS_TYPE_VARIANT) {
            child->single = &v;
        }
        return;
    }
    if (st->dict && st->dictIt != st->dict->end()) {
        child->kv = &(*st->dictIt);
        child->kvStep = 0;
        return;
    }
    if (st->kv && st->kvStep == 1) {
        child->single = &st->kv->second;
        return;
    }
    if (st->single && st->single->type == DBUS_TYPE_STRUCT) {
        child->single = st->single;
        child->structStep = 0;
    }
}

void dbus_message_iter_init_append(DBusMessage* m, DBusMessageIter* iter) {
    auto* st = makeIter(iter);
    st->outMsg = m;
}
dbus_bool_t dbus_message_iter_append_basic(DBusMessageIter* iter, int type, const void* value) {
    auto* st = getIter(iter);
    if (!st || !st->outMsg || st->inDict)
        return 1;
    Val v;
    v.type = type;
    if (type == DBUS_TYPE_STRING || type == DBUS_TYPE_OBJECT_PATH) {
        const char* s = *static_cast<const char* const*>(value);
        v.str = s ? s : "";
    } else if (type == DBUS_TYPE_UINT32) {
        v.u32 = *static_cast<const uint32_t*>(value);
    }
    st->outMsg->args.push_back(v);
    return 1;
}
dbus_bool_t dbus_message_iter_open_container(DBusMessageIter* iter, int, const char*,
                                             DBusMessageIter* sub) {
    auto* st = getIter(iter);
    auto* child = makeIter(sub);
    child->outMsg = st ? st->outMsg : nullptr;
    child->inDict = true;
    return 1;
}
dbus_bool_t dbus_message_iter_close_container(DBusMessageIter*, DBusMessageIter*) {
    return 1;
}

} // extern "C"
