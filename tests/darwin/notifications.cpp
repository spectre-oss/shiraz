// SPDX-License-Identifier: GPL-3.0-only
// macOS UserNotifications verification against build/macos/libshiraz-notifications.so.
// Covers safe unbundled guard (no NSInternalInconsistencyException abort), UTF-16 input
// validation, invalid-handle rejection, and bundled .app bundle detection.
#include <dlfcn.h>
#include <jni.h>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

struct FakeObj {
    enum Kind { kClass, kString, kLongArray, kObjectArray } kind;
    std::string className;
    std::vector<jchar> utf16;
    std::string utf8;
    std::vector<jlong> longs;
    std::vector<FakeObj*> objs;
};

struct Harness {
    JNINativeInterface_ table{};
    JNIEnv env{};
    std::vector<FakeObj*> allocs;
    std::string lastExceptionClass;
    std::string lastExceptionMsg;

    Harness() {
        env.functions = &table;
        table.FindClass = [](JNIEnv* e, const char* name) -> jclass {
            auto* o = from(e)->make(FakeObj::kClass);
            o->className = name ? name : "";
            return reinterpret_cast<jclass>(o);
        };
        table.ThrowNew = [](JNIEnv* e, jclass cls, const char* msg) -> jint {
            auto* h = from(e);
            h->lastExceptionClass = cls ? reinterpret_cast<FakeObj*>(cls)->className : "";
            h->lastExceptionMsg = msg ? msg : "";
            return 0;
        };
        table.DeleteLocalRef = [](JNIEnv*, jobject) {};
        table.ExceptionCheck = [](JNIEnv* e) -> jboolean {
            return from(e)->lastExceptionClass.empty() ? JNI_FALSE : JNI_TRUE;
        };
        table.GetStringLength = [](JNIEnv*, jstring s) -> jsize {
            return static_cast<jsize>(reinterpret_cast<FakeObj*>(s)->utf16.size());
        };
        table.GetStringRegion = [](JNIEnv*, jstring s, jsize start, jsize len, jchar* buf) {
            auto* o = reinterpret_cast<FakeObj*>(s);
            if (len > 0)
                std::memcpy(buf, o->utf16.data() + start, static_cast<size_t>(len) * sizeof(jchar));
        };
        table.NewStringUTF = [](JNIEnv* e, const char* utf) -> jstring {
            auto* o = from(e)->make(FakeObj::kString);
            o->utf8 = utf ? utf : "";
            return reinterpret_cast<jstring>(o);
        };
        table.NewObjectArray = [](JNIEnv* e, jsize len, jclass, jobject init) -> jobjectArray {
            auto* o = from(e)->make(FakeObj::kObjectArray);
            o->objs.assign(static_cast<size_t>(len), reinterpret_cast<FakeObj*>(init));
            return reinterpret_cast<jobjectArray>(o);
        };
        table.SetObjectArrayElement = [](JNIEnv*, jobjectArray arr, jsize i, jobject val) {
            reinterpret_cast<FakeObj*>(arr)->objs[static_cast<size_t>(i)] =
                reinterpret_cast<FakeObj*>(val);
        };
        table.NewLongArray = [](JNIEnv* e, jsize len) -> jlongArray {
            auto* o = from(e)->make(FakeObj::kLongArray);
            o->longs.resize(static_cast<size_t>(len));
            return reinterpret_cast<jlongArray>(o);
        };
        table.SetLongArrayRegion = [](JNIEnv*, jlongArray arr, jsize start, jsize len,
                                      const jlong* buf) {
            auto* o = reinterpret_cast<FakeObj*>(arr);
            if (len > 0)
                std::memcpy(o->longs.data() + start, buf, static_cast<size_t>(len) * sizeof(jlong));
        };
    }

    ~Harness() {
        for (auto* o : allocs)
            delete o;
    }

    static Harness* from(JNIEnv* e) {
        return reinterpret_cast<Harness*>(reinterpret_cast<char*>(e) - offsetof(Harness, env));
    }

    FakeObj* make(FakeObj::Kind k) {
        auto* o = new FakeObj{k, {}, {}, {}, {}, {}};
        allocs.push_back(o);
        return o;
    }

    jstring makeAscii(const std::string& s) {
        auto* o = make(FakeObj::kString);
        for (unsigned char c : s)
            o->utf16.push_back(c);
        return reinterpret_cast<jstring>(o);
    }

    jstring makeUtf16(const std::vector<jchar>& units) {
        auto* o = make(FakeObj::kString);
        o->utf16 = units;
        return reinterpret_cast<jstring>(o);
    }

    void clear() {
        lastExceptionClass.clear();
        lastExceptionMsg.clear();
    }
};

void require(bool cond, const char* msg) {
    if (!cond)
        throw std::runtime_error(msg);
}

} // namespace

int main(int argc, char** argv) {
    try {
        require(argc >= 2,
                "usage: test-notifications /path/to/libshiraz-notifications.so [--bundled]");
        bool expectBundled = argc >= 3 && std::strcmp(argv[2], "--bundled") == 0;

        void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
        if (!lib) {
            std::cerr << "dlopen failed: " << dlerror() << "\n";
            return 1;
        }
        auto loadSym = [&](const char* name) {
            void* p = dlsym(lib, name);
            if (!p)
                throw std::runtime_error(std::string("missing symbol: ") + name);
            return p;
        };

        auto fnOpen = reinterpret_cast<jlong (*)(JNIEnv*, jclass, jstring)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_open"));
        auto fnClose = reinterpret_cast<void (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_close"));
        auto fnCaps = reinterpret_cast<jobjectArray (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_capabilities"));
        auto fnPost = reinterpret_cast<jlong (*)(JNIEnv*, jclass, jlong, jlong, jstring, jstring,
                                                 jint, jint, jboolean)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_post"));
        auto fnCancel = reinterpret_cast<void (*)(JNIEnv*, jclass, jlong, jlong)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_cancel"));
        auto fnActive = reinterpret_cast<jlongArray (*)(JNIEnv*, jclass, jlong)>(
            loadSym("Java_shiraz_runtime_DesktopNotifications_activeIds"));

        Harness h;

        // 1. Input validation rejects null, NUL char, >256 chars, unpaired UTF-16 surrogates.
        require(fnOpen(&h.env, nullptr, nullptr) == 0 &&
                    h.lastExceptionClass == "java/lang/IllegalArgumentException",
                "null app name must throw IllegalArgumentException");
        h.clear();

        require(fnOpen(&h.env, nullptr, h.makeUtf16({'a', 0, 'b'})) == 0 &&
                    h.lastExceptionClass == "java/lang/IllegalArgumentException",
                "NUL in app name must throw IllegalArgumentException");
        h.clear();

        require(fnOpen(&h.env, nullptr, h.makeUtf16({0xd800})) == 0 &&
                    h.lastExceptionClass == "java/lang/IllegalArgumentException",
                "unpaired surrogate must throw IllegalArgumentException");
        h.clear();

        require(fnOpen(&h.env, nullptr, h.makeAscii(std::string(257, 'x'))) == 0 &&
                    h.lastExceptionClass == "java/lang/IllegalArgumentException",
                "oversized app name must throw IllegalArgumentException");
        h.clear();

        // 2. Closed/unknown handle rejection.
        require(fnCaps(&h.env, nullptr, 99999) == nullptr &&
                    h.lastExceptionClass == "java/lang/IllegalStateException",
                "unknown handle capabilities must throw IllegalStateException");
        h.clear();
        require(fnActive(&h.env, nullptr, 99999) == nullptr &&
                    h.lastExceptionClass == "java/lang/IllegalStateException",
                "unknown handle activeIds must throw IllegalStateException");
        h.clear();
        fnCancel(&h.env, nullptr, 99999, 1);
        require(h.lastExceptionClass == "java/lang/IllegalStateException",
                "unknown handle cancel must throw IllegalStateException");
        h.clear();
        fnClose(&h.env, nullptr, 99999);
        require(h.lastExceptionClass.empty(), "close on unknown handle should be idempotent");

        // 3. Invalid post options throw IllegalArgumentException before touching bundle.
        require(fnPost(&h.env, nullptr, 1, 0, h.makeAscii("hi"), h.makeAscii("body"), 3, -1,
                       JNI_FALSE) == 0 &&
                    h.lastExceptionClass == "java/lang/IllegalArgumentException",
                "invalid urgency must throw IllegalArgumentException");
        h.clear();

        // 4. Open behavior: unbundled must fail safely with IllegalStateException (never abort);
        // bundled must reach UNUserNotificationCenter without aborting.
        jlong handle = fnOpen(&h.env, nullptr, h.makeAscii("Shiraz Test"));
        if (!expectBundled) {
            require(handle == 0, "unbundled open must return 0");
            require(h.lastExceptionClass == "java/lang/IllegalStateException",
                    "unbundled open must throw IllegalStateException");
            require(h.lastExceptionMsg.find(".app") != std::string::npos,
                    "unbundled error must mention .app bundle requirement");
            std::cout << "MACOS_NOTIFICATIONS_UNBUNDLED_SAFE msg=\"" << h.lastExceptionMsg
                      << "\"\n";
        } else {
            if (handle != 0) {
                auto* caps = reinterpret_cast<FakeObj*>(fnCaps(&h.env, nullptr, handle));
                require(caps != nullptr && caps->objs.size() == 2 && h.lastExceptionClass.empty(),
                        "bundled capabilities check failed");
                require(caps->objs[0]->utf8 == "body" && caps->objs[1]->utf8 == "sound",
                        "unexpected macOS notification capabilities");
                auto* active = reinterpret_cast<FakeObj*>(fnActive(&h.env, nullptr, handle));
                require(active != nullptr && h.lastExceptionClass.empty(),
                        "bundled activeIds failed");
                fnClose(&h.env, nullptr, handle);
                require(h.lastExceptionClass.empty(), "bundled close failed");
                std::cout << "MACOS_NOTIFICATIONS_BUNDLED_OPENED caps=body,sound\n";
            } else {
                // Bundled process reached UNUserNotificationCenter cleanly; headless runner may
                // deny or fail authorization without aborting.
                require(h.lastExceptionClass == "java/lang/IllegalStateException",
                        "bundled failure must be IllegalStateException");
                require(h.lastExceptionMsg.find("CFBundleIdentifier") == std::string::npos,
                        "bundled process must pass bundle check");
                std::cout << "MACOS_NOTIFICATIONS_BUNDLED_AUTH_RESULT msg=\"" << h.lastExceptionMsg
                          << "\"\n";
            }
        }

        dlclose(lib);
        std::cout << "MACOS_NOTIFICATIONS_PASS\n";
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "test-notifications FAILED: " << e.what() << "\n";
        return 1;
    }
}
