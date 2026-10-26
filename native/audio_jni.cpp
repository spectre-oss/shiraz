// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio_output.hpp"
#include <jni.h>
#include <limits>
#include <mutex>
#include <unordered_map>
#include <stdexcept>
namespace {
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<shiraz::audio::Pool>> pools;
jlong nextId = 1;
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type); if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::shared_ptr<shiraz::audio::Pool> find(jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex); auto entry = pools.find(id);
    if (entry == pools.end()) throw std::logic_error("SoundPool is released");
    return entry->second;
}
template<class F> auto checked(JNIEnv* env, F function) -> decltype(function()) {
    try { return function(); }
    catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate audio state"); }
    catch (const std::invalid_argument& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
    catch (const std::exception& issue) { error(env, "java/lang/IllegalStateException", issue.what()); }
    return {};
}
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSound_create(JNIEnv* env, jclass, jint streams) {
    return checked(env, [&]() -> jlong {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (pools.size() >= 16 || nextId == std::numeric_limits<jlong>::max()) throw std::logic_error("SoundPool capacity exhausted");
        auto pool = std::make_shared<shiraz::audio::Pool>(streams); auto id = nextId++;
        pools.emplace(id, std::move(pool)); return id;
    });
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSound_reserve(JNIEnv* env, jclass, jlong id, jint sound) {
    return checked(env, [&]() -> jboolean { return find(id)->reserve(sound); });
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSound_load(JNIEnv* env, jclass, jlong id, jint sound, jbyteArray data) {
    return checked(env, [&]() -> jboolean {
        auto pool = find(id);
        if (!data) throw std::invalid_argument("Null encoded sound");
        auto size = env->GetArrayLength(data);
        if (size < 1 || static_cast<std::size_t>(size) > shiraz::audio::maxEncodedBytes) throw std::invalid_argument("Encoded sound size exceeds limit");
        std::vector<std::uint8_t> bytes(static_cast<std::size_t>(size));
        env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) return false;
        return pool->load(sound, bytes);
    });
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSound_unload(JNIEnv* env, jclass, jlong id, jint sound) {
    return checked(env, [&]() -> jboolean { return find(id)->unload(sound); });
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSound_play(JNIEnv* env, jclass, jlong id, jint sound, jfloat left, jfloat right, jint priority, jint loops, jfloat rate) {
    return checked(env, [&]() -> jint { return find(id)->play(sound, left, right, priority, loops, rate); });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSound_control(JNIEnv* env, jclass, jlong id, jint stream, jint operation, jfloat a, jfloat b, jint value) {
    checked(env, [&]() { find(id)->control(stream, operation, a, b, value); return 0; });
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSound_release(JNIEnv* env, jclass, jlong id) {
    checked(env, [&]() {
        std::shared_ptr<shiraz::audio::Pool> pool;
        { std::lock_guard<std::mutex> lock(registryMutex); auto entry = pools.find(id); if (entry != pools.end()) { pool = std::move(entry->second); pools.erase(entry); } }
        if (pool) pool->release();
        return 0;
    });
}
extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    std::unordered_map<jlong, std::shared_ptr<shiraz::audio::Pool>> remaining;
    { std::lock_guard<std::mutex> lock(registryMutex); remaining.swap(pools); }
    for (auto& entry : remaining) entry.second->release();
}
