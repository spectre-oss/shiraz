// SPDX-License-Identifier: GPL-3.0-only
// Exercises native/linux/location.cpp JNI contract across both GeoClue2 and
// XDG Desktop Portal Location modes using the in-process mock libdbus fixture.
#include <jni.h>

#include <cassert>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <string>
#include <thread>
#include <vector>

namespace {

struct FakeJni {
    JNINativeInterface_ table{};
    JNIEnv env{};
    std::vector<double> lastDoubles;
    std::string lastString;

    static FakeJni* self(JNIEnv* e) {
        return reinterpret_cast<FakeJni*>(reinterpret_cast<char*>(e) - offsetof(FakeJni, env));
    }
    static jdoubleArray newDoubleArray(JNIEnv* e, jsize len) {
        auto* f = self(e);
        f->lastDoubles.assign(static_cast<size_t>(len), 0.0);
        return reinterpret_cast<jdoubleArray>(&f->lastDoubles);
    }
    static void setDoubleArrayRegion(JNIEnv*, jdoubleArray arr, jsize start, jsize len,
                                     const jdouble* buf) {
        auto* vec = reinterpret_cast<std::vector<double>*>(arr);
        for (jsize i = 0; i < len; ++i)
            (*vec)[static_cast<size_t>(start + i)] = buf[i];
    }
    static jstring newStringUtf(JNIEnv* e, const char* s) {
        auto* f = self(e);
        f->lastString = s ? s : "";
        return reinterpret_cast<jstring>(&f->lastString);
    }

    FakeJni() {
        table.NewDoubleArray = newDoubleArray;
        table.SetDoubleArrayRegion = setDoubleArrayRegion;
        table.NewStringUTF = newStringUtf;
        env.functions = &table;
    }
};

template <typename Fn> Fn requireSym(void* lib, const char* name) {
    void* p = dlsym(lib, name);
    assert(p != nullptr);
    return reinterpret_cast<Fn>(p);
}

} // namespace

int main(int argc, char** argv) {
    assert(argc == 3);
    const char* locationLibPath = argv[1];
    const char* mockDbusPath = argv[2];

    setenv("SHIRAZ_LOCATION_DBUS_LIB", mockDbusPath, 1);

    void* lib = dlopen(locationLibPath, RTLD_NOW | RTLD_LOCAL);
    assert(lib != nullptr);

    using AuthFn = jint (*)(JNIEnv*, jclass);
    using EnabledFn = jboolean (*)(JNIEnv*, jclass);
    using StartFn = jlong (*)(JNIEnv*, jclass, jboolean, jdouble);
    using StopFn = void (*)(JNIEnv*, jclass, jlong);
    using PollFn = jdoubleArray (*)(JNIEnv*, jclass, jlong);
    using ErrFn = jstring (*)(JNIEnv*, jclass, jlong);

    auto authFn =
        requireSym<AuthFn>(lib, "Java_shiraz_runtime_HostLocation_nativeAuthorizationStatus");
    auto enabledFn = requireSym<EnabledFn>(
        lib, "Java_shiraz_runtime_HostLocation_nativeLocationServicesEnabled");
    auto startFn = requireSym<StartFn>(lib, "Java_shiraz_runtime_HostLocation_nativeStart");
    auto stopFn = requireSym<StopFn>(lib, "Java_shiraz_runtime_HostLocation_nativeStop");
    auto pollFn = requireSym<PollFn>(lib, "Java_shiraz_runtime_HostLocation_nativePollFix");
    auto errFn = requireSym<ErrFn>(lib, "Java_shiraz_runtime_HostLocation_nativeLastError");

    FakeJni jni;

    // 1. Unavailable mode (neither GeoClue2 nor Portal owned).
    setenv("SHIRAZ_MOCK_LOCATION_MODE", "none", 1);
    unsetenv("SHIRAZ_LOCATION_BACKEND");
    assert(authFn(&jni.env, nullptr) == 0);
    assert(enabledFn(&jni.env, nullptr) == JNI_FALSE);
    assert(startFn(&jni.env, nullptr, JNI_TRUE, 0.0) == 0);

    // 2. GeoClue2 mode: receives Berlin coordinates from mock GeoClue2 LocationUpdated signal.
    setenv("SHIRAZ_MOCK_LOCATION_MODE", "geoclue2", 1);
    setenv("SHIRAZ_LOCATION_BACKEND", "geoclue2", 1);
    assert(authFn(&jni.env, nullptr) == 4);
    assert(enabledFn(&jni.env, nullptr) == JNI_TRUE);
    jlong gcHandle = startFn(&jni.env, nullptr, JNI_TRUE, 5.0);
    assert(gcHandle != 0);
    jdoubleArray gcFix = nullptr;
    for (int i = 0; i < 50 && !gcFix; ++i) {
        gcFix = pollFn(&jni.env, nullptr, gcHandle);
        if (!gcFix)
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    assert(gcFix != nullptr);
    assert(jni.lastDoubles.size() == 7);
    assert(std::fabs(jni.lastDoubles[0] - 52.5200) < 1e-6);
    assert(std::fabs(jni.lastDoubles[1] - 13.4050) < 1e-6);
    assert(std::fabs(jni.lastDoubles[2] - 42.5) < 1e-6);
    assert(std::fabs(jni.lastDoubles[3] - 6.5) < 1e-6);
    errFn(&jni.env, nullptr, gcHandle);
    assert(jni.lastString.empty());
    stopFn(&jni.env, nullptr, gcHandle);

    // 3. XDG Desktop Portal mode: receives Paris coordinates from mock Portal signal.
    setenv("SHIRAZ_MOCK_LOCATION_MODE", "portal", 1);
    setenv("SHIRAZ_LOCATION_BACKEND", "portal", 1);
    assert(authFn(&jni.env, nullptr) == 4);
    assert(enabledFn(&jni.env, nullptr) == JNI_TRUE);
    jlong portalHandle = startFn(&jni.env, nullptr, JNI_FALSE, 10.0);
    assert(portalHandle != 0);
    jdoubleArray portalFix = nullptr;
    for (int i = 0; i < 50 && !portalFix; ++i) {
        portalFix = pollFn(&jni.env, nullptr, portalHandle);
        if (!portalFix)
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    assert(portalFix != nullptr);
    assert(jni.lastDoubles.size() == 7);
    assert(std::fabs(jni.lastDoubles[0] - 48.8566) < 1e-6);
    assert(std::fabs(jni.lastDoubles[1] - 2.3522) < 1e-6);
    assert(std::fabs(jni.lastDoubles[2] - 35.0) < 1e-6);
    stopFn(&jni.env, nullptr, portalHandle);

    // 4. Explicit disable override.
    setenv("SHIRAZ_DISABLE_HOST_LOCATION", "1", 1);
    assert(authFn(&jni.env, nullptr) == 0);
    assert(enabledFn(&jni.env, nullptr) == JNI_FALSE);
    unsetenv("SHIRAZ_DISABLE_HOST_LOCATION");

    dlclose(lib);
    std::puts("LINUX_LOCATION_NATIVE_OK");
    return 0;
}
