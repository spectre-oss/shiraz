// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <crt_externs.h>
#include <dlfcn.h>
#include <string>

static bool threw = false;
static const char* JNICALL get_chars(JNIEnv*, jstring, jboolean*) {
    return "shiraz.fixture";
}
static void JNICALL release_chars(JNIEnv*, jstring, const char*) {}
static jclass JNICALL find_class(JNIEnv*, const char*) {
    return reinterpret_cast<jclass>(1);
}
static jint JNICALL throw_new(JNIEnv*, jclass, const char*) {
    threw = true;
    return 0;
}
static std::string captured;
static jbyteArray JNICALL new_bytes(JNIEnv*, jsize size) {
    captured.resize(size_t(size));
    return reinterpret_cast<jbyteArray>(1);
}
static void JNICALL set_bytes(JNIEnv*, jbyteArray, jsize start, jsize size, const jbyte* bytes) {
    assert(start >= 0 && size >= 0 && size_t(start) + size_t(size) <= captured.size());
    std::memcpy(&captured[size_t(start)], bytes, size_t(size));
}

int main(int argc, char** argv) {
    assert(argc == 3);
    const bool extended = !std::strcmp(argv[1], "extended");
    assert(extended || !std::strcmp(argv[1], "normal"));
    char* end = argv[2] + std::strlen(argv[2]);
    char** environment = *_NSGetEnviron();
    assert(environment && environment[0] && end + 1 == environment[0]);
    assert(!std::strcmp(environment[0], "SHIRAZ_FIXTURE_MARKER=controlled"));
    std::string expected;
    for (int i = 0; i < argc; ++i) {
        expected.append(argv[i]);
        expected.push_back('\0');
    }
    if (extended)
        *end = 'X';
    void* library = dlopen("build/macos/process-name-late-host.dylib", RTLD_NOW | RTLD_LOCAL);
    if (extended)
        *end = '\0';
    assert(library);
    using SetName = void (*)(JNIEnv*, jclass, jstring);
    auto set_name = reinterpret_cast<SetName>(dlsym(library, "Java_android_os_Process_setArgV0"));
    using CommandLine = jbyteArray (*)(JNIEnv*, jclass);
    auto command_line = reinterpret_cast<CommandLine>(
        dlsym(library, "Java_shiraz_runtime_ServiceProcess_nativeCommandLine"));
    assert(set_name && command_line);
    JNINativeInterface_ table{};
    table.GetStringUTFChars = get_chars;
    table.ReleaseStringUTFChars = release_chars;
    table.FindClass = find_class;
    table.ThrowNew = throw_new;
    table.NewByteArray = new_bytes;
    table.SetByteArrayRegion = set_bytes;
    JNIEnv_ env{&table};
    jbyteArray line = command_line(&env, nullptr);
    assert(extended ? (line == nullptr && threw)
                    : (line != nullptr && !threw && captured == expected));
    threw = false;
    set_name(&env, nullptr, reinterpret_cast<jstring>(1));
    assert(!std::strcmp(environment[0], "SHIRAZ_FIXTURE_MARKER=controlled"));
    assert(threw == extended);
    if (!extended)
        assert(!std::strcmp(argv[0], "shiraz.fixture"));
    std::puts("DARWIN_PROCESS_NAME_BOUNDARY_PASS");
}
