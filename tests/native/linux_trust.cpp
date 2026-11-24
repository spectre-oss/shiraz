// SPDX-License-Identifier: GPL-3.0-only
// Exercises native/linux/trust.cpp PEM->DER host CA anchor extraction.
#include <jni.h>

#include <cassert>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <fstream>
#include <string>
#include <unistd.h>
#include <vector>

namespace {

struct FakeJni {
    JNINativeInterface_ table{};
    JNIEnv env{};
    std::vector<std::vector<uint8_t>> arrays;

    static FakeJni* self(JNIEnv* e) {
        return reinterpret_cast<FakeJni*>(reinterpret_cast<char*>(e) - offsetof(FakeJni, env));
    }
    static jclass findClass(JNIEnv*, const char*) {
        return reinterpret_cast<jclass>(0x1);
    }
    static void deleteLocalRef(JNIEnv*, jobject) {}
    static jboolean exceptionCheck(JNIEnv*) {
        return JNI_FALSE;
    }
    static jobjectArray newObjectArray(JNIEnv* e, jsize len, jclass, jobject) {
        auto* f = self(e);
        f->arrays.assign(static_cast<size_t>(len), {});
        return reinterpret_cast<jobjectArray>(&f->arrays);
    }
    static jbyteArray newByteArray(JNIEnv*, jsize len) {
        auto* buf = new std::vector<uint8_t>(static_cast<size_t>(len));
        return reinterpret_cast<jbyteArray>(buf);
    }
    static void setByteArrayRegion(JNIEnv*, jbyteArray arr, jsize start, jsize len,
                                   const jbyte* data) {
        auto* buf = reinterpret_cast<std::vector<uint8_t>*>(arr);
        std::memcpy(buf->data() + start, data, static_cast<size_t>(len));
    }
    static void setObjectArrayElement(JNIEnv*, jobjectArray arr, jsize idx, jobject val) {
        auto* list = reinterpret_cast<std::vector<std::vector<uint8_t>>*>(arr);
        auto* buf = reinterpret_cast<std::vector<uint8_t>*>(val);
        (*list)[static_cast<size_t>(idx)] = *buf;
        delete buf;
    }

    FakeJni() {
        table.FindClass = findClass;
        table.DeleteLocalRef = deleteLocalRef;
        table.ExceptionCheck = exceptionCheck;
        table.NewObjectArray = newObjectArray;
        table.NewByteArray = newByteArray;
        table.SetByteArrayRegion = setByteArrayRegion;
        table.SetObjectArrayElement = setObjectArrayElement;
        env.functions = &table;
    }
};

} // namespace

int main(int argc, char** argv) {
    assert(argc == 2);
    void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    assert(lib != nullptr);
    using AnchorsFn = jobjectArray (*)(JNIEnv*, jclass);
    auto anchors =
        reinterpret_cast<AnchorsFn>(dlsym(lib, "Java_shiraz_runtime_HostTrustProvider_anchors"));
    assert(anchors != nullptr);

    // Two distinct synthetic DER SEQUENCE payloads + one duplicate + one invalid block.
    // 0x30 0x03 0x02 0x01 0x01 -> MAMCAQE=
    // 0x30 0x03 0x02 0x01 0x02 -> MAMCAQI=
    std::string tmpPath = "/tmp/shiraz-trust-test-XXXXXX";
    int fd = mkstemp(&tmpPath[0]);
    assert(fd >= 0);
    close(fd);
    {
        std::ofstream out(tmpPath, std::ios::binary | std::ios::trunc);
        out << "# Test bundle\n"
            << "-----BEGIN CERTIFICATE-----\n"
            << "MAMCAQE=\n"
            << "-----END CERTIFICATE-----\n"
            << "-----BEGIN CERTIFICATE-----\n"
            << "MAMCAQE=\n"
            << "-----END CERTIFICATE-----\n"
            << "-----BEGIN CERTIFICATE-----\n"
            << "not-valid-der-base64!!!\n"
            << "-----END CERTIFICATE-----\n"
            << "-----BEGIN CERTIFICATE-----\n"
            << "MAMCAQI=\n"
            << "-----END CERTIFICATE-----\n";
    }

    setenv("SHIRAZ_SYSTEM_CA_BUNDLE", tmpPath.c_str(), 1);
    FakeJni jni;
    assert(anchors(&jni.env, nullptr) != nullptr);
    assert(jni.arrays.size() == 2);
    assert(jni.arrays[0].size() == 5 && jni.arrays[0][0] == 0x30 && jni.arrays[0][4] == 0x01);
    assert(jni.arrays[1].size() == 5 && jni.arrays[1][0] == 0x30 && jni.arrays[1][4] == 0x02);

    // Missing bundle returns empty array (so packaged apps fall back to cacerts.jks cleanly).
    setenv("SHIRAZ_SYSTEM_CA_BUNDLE", "/nonexistent/shiraz-missing-ca.pem", 1);
    assert(anchors(&jni.env, nullptr) != nullptr);
    assert(jni.arrays.empty());

    unsetenv("SHIRAZ_SYSTEM_CA_BUNDLE");
    unlink(tmpPath.c_str());
    dlclose(lib);
    std::puts("LINUX_TRUST_NATIVE_OK");
    return 0;
}
