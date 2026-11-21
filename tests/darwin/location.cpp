// SPDX-License-Identifier: GPL-3.0-only
#include <dlfcn.h>
#include <jni.h>

#include <cassert>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

namespace {

struct FakeString {
    std::string value;
};

struct FakeDoubleArray {
    std::vector<jdouble> values;
};

std::vector<FakeString*> g_strings;
std::vector<FakeDoubleArray*> g_arrays;

jstring fake_new_string_utf(JNIEnv*, const char* utf) {
    auto* s = new FakeString{utf ? utf : ""};
    g_strings.push_back(s);
    return reinterpret_cast<jstring>(s);
}

jdoubleArray fake_new_double_array(JNIEnv*, jsize len) {
    auto* a = new FakeDoubleArray();
    a->values.resize(static_cast<size_t>(len), 0.0);
    g_arrays.push_back(a);
    return reinterpret_cast<jdoubleArray>(a);
}

void fake_set_double_array_region(JNIEnv*, jdoubleArray arr, jsize start, jsize len,
                                  const jdouble* buf) {
    auto* a = reinterpret_cast<FakeDoubleArray*>(arr);
    for (jsize i = 0; i < len; ++i)
        a->values[static_cast<size_t>(start + i)] = buf[i];
}

JNIEnv* make_fake_env(JNINativeInterface_* table, JNIEnv* env) {
    std::memset(table, 0, sizeof(*table));
    table->NewStringUTF = fake_new_string_utf;
    table->NewDoubleArray = fake_new_double_array;
    table->SetDoubleArrayRegion = fake_set_double_array_region;
    env->functions = table;
    return env;
}

using AuthFn = jint (*)(JNIEnv*, jclass);
using EnabledFn = jboolean (*)(JNIEnv*, jclass);
using StartFn = jlong (*)(JNIEnv*, jclass, jboolean, jdouble);
using StopFn = void (*)(JNIEnv*, jclass, jlong);
using PollFn = jdoubleArray (*)(JNIEnv*, jclass, jlong);
using LastErrorFn = jstring (*)(JNIEnv*, jclass, jlong);

template <typename Fn> Fn require_symbol(void* lib, const char* name) {
    void* sym = dlsym(lib, name);
    assert(sym != nullptr);
    return reinterpret_cast<Fn>(sym);
}

} // namespace

int main(int argc, char** argv) {
    assert(argc >= 2);
    const char* libPath = argv[1];
    const bool bundled = (argc >= 3 && std::strcmp(argv[2], "--bundled") == 0);

    void* lib = dlopen(libPath, RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        std::cerr << "dlopen failed: " << dlerror() << "\n";
        return 1;
    }

    auto authFn =
        require_symbol<AuthFn>(lib, "Java_shiraz_runtime_DarwinLocation_nativeAuthorizationStatus");
    auto enabledFn = require_symbol<EnabledFn>(
        lib, "Java_shiraz_runtime_DarwinLocation_nativeLocationServicesEnabled");
    auto startFn = require_symbol<StartFn>(lib, "Java_shiraz_runtime_DarwinLocation_nativeStart");
    auto stopFn = require_symbol<StopFn>(lib, "Java_shiraz_runtime_DarwinLocation_nativeStop");
    auto pollFn = require_symbol<PollFn>(lib, "Java_shiraz_runtime_DarwinLocation_nativePollFix");
    auto errFn =
        require_symbol<LastErrorFn>(lib, "Java_shiraz_runtime_DarwinLocation_nativeLastError");

    JNINativeInterface_ table{};
    JNIEnv envStorage{};
    JNIEnv* env = make_fake_env(&table, &envStorage);

    if (!bundled) {
        const jint status = authFn(env, nullptr);
        assert(status == 0);
        assert(enabledFn(env, nullptr) == JNI_FALSE);
        const jlong handle = startFn(env, nullptr, JNI_TRUE, 1.0);
        assert(handle == 0);
        assert(pollFn(env, nullptr, 0) == nullptr);
        stopFn(env, nullptr, 0);
        auto* err = reinterpret_cast<FakeString*>(errFn(env, nullptr, 0));
        assert(err != nullptr);
        assert(err->value.find("NSLocationUsageDescription") != std::string::npos);
        std::cout << "MACOS_LOCATION_UNBUNDLED_SAFE msg=\"" << err->value << "\"\n";
    } else {
        const jint status = authFn(env, nullptr);
        assert(status >= 1 && status <= 4);
        const jboolean enabled = enabledFn(env, nullptr);

        // Start fine-accuracy and coarse-accuracy sessions cleanly and tear down.
        const jlong fineHandle = startFn(env, nullptr, JNI_TRUE, 1.0);
        assert(fineHandle > 0);
        (void)pollFn(env, nullptr, fineHandle);
        auto* fineErr = reinterpret_cast<FakeString*>(errFn(env, nullptr, fineHandle));
        assert(fineErr != nullptr);
        stopFn(env, nullptr, fineHandle);

        const jlong coarseHandle = startFn(env, nullptr, JNI_FALSE, 50.0);
        assert(coarseHandle > 0);
        (void)pollFn(env, nullptr, coarseHandle);
        stopFn(env, nullptr, coarseHandle);

        std::cout << "MACOS_LOCATION_BUNDLED_OK status=" << status
                  << " services_enabled=" << int(enabled) << "\n";
    }

    for (auto* s : g_strings)
        delete s;
    for (auto* a : g_arrays)
        delete a;
    dlclose(lib);
    std::cout << "MACOS_LOCATION_PASS\n";
    return 0;
}
