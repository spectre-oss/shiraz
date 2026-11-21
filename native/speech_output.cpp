// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#ifdef __APPLE__
#include <AudioToolbox/AudioToolbox.h>
#else
#include <alsa/asoundlib.h>
#endif
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <algorithm>
namespace {
using Clock = std::chrono::steady_clock;
struct Playback {
    std::atomic<bool> canceled{false}, running{false};
    std::mutex mutex;
    std::condition_variable changed;
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Playback>> registry;
jlong nextId = 1;
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck())
        return;
    auto cls = env->FindClass(type);
    if (cls) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}
std::shared_ptr<Playback> find(jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto i = registry.find(id);
    if (i == registry.end())
        throw std::invalid_argument("Unknown speech playback handle");
    return i->second;
}
void cancel(const std::shared_ptr<Playback>& p) {
    p->canceled = true;
    p->changed.notify_all();
}
bool wait(const std::shared_ptr<Playback>& p, Clock::time_point until) {
    std::unique_lock<std::mutex> lock(p->mutex);
    p->changed.wait_until(lock, until, [&] { return p->canceled.load(); });
    return p->canceled;
}
struct Active {
    std::shared_ptr<Playback> p;
    explicit Active(std::shared_ptr<Playback> value) : p(std::move(value)) {
        if (p->running.exchange(true))
            throw std::invalid_argument("Speech handle is already playing");
    }
    ~Active() {
        p->running = false;
    }
};
#ifdef __APPLE__
struct PCM {
    std::shared_ptr<Playback> playback;
    AudioQueueRef queue = nullptr;
    AudioQueueBufferRef buffers[4]{};
    bool available[4]{true, true, true, true};
    uint64_t completed = 0;
    std::atomic<bool> failed{false};
    explicit PCM(std::shared_ptr<Playback> p) : playback(std::move(p)) {}
    ~PCM() {
        if (queue)
            AudioQueueDispose(queue, true);
    }
};
void returned(void* context, AudioQueueRef, AudioQueueBufferRef buffer) {
    auto& pcm = *static_cast<PCM*>(context);
    try {
        std::lock_guard<std::mutex> lock(pcm.playback->mutex);
        for (int i = 0; i < 4; i++)
            if (pcm.buffers[i] == buffer) {
                if (pcm.available[i]) {
                    pcm.failed = true;
                    break;
                }
                pcm.completed += buffer->mAudioDataByteSize / sizeof(short);
                pcm.available[i] = true;
                pcm.playback->changed.notify_all();
                return;
            }
        pcm.failed = true;
        pcm.playback->changed.notify_all();
    } catch (...) {
        pcm.failed = true;
        pcm.playback->changed.notify_all();
    }
}
int playDarwin(const std::shared_ptr<Playback>& p, const std::vector<short>& samples, int rate) {
    const char* device = std::getenv("SHIRAZ_AUDIO_DEVICE");
    if (device && *device && std::strcmp(device, "default"))
        return -1;
    PCM pcm(p);
    AudioStreamBasicDescription format{};
    format.mSampleRate = rate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked;
    format.mBytesPerPacket = 2;
    format.mFramesPerPacket = 1;
    format.mBytesPerFrame = 2;
    format.mChannelsPerFrame = 1;
    format.mBitsPerChannel = 16;
    if (AudioQueueNewOutput(&format, returned, &pcm, nullptr, nullptr, 0, &pcm.queue))
        return -1;
    const size_t frames = size_t(rate / 50);
    for (auto& buffer : pcm.buffers)
        if (AudioQueueAllocateBuffer(pcm.queue, UInt32(frames * 2), &buffer))
            return -1;
    bool started = false;
    size_t written = 0;
    auto deadline = Clock::now() + std::chrono::seconds(2);
    while (written < samples.size()) {
        if (p->canceled)
            return 1;
        if (pcm.failed)
            return -1;
        int index = -1;
        {
            std::lock_guard<std::mutex> lock(p->mutex);
            for (int i = 0; i < 4; i++)
                if (pcm.available[i]) {
                    index = i;
                    pcm.available[i] = false;
                    break;
                }
        }
        if (index < 0) {
            if (Clock::now() >= deadline)
                return -1;
            if (wait(p, Clock::now() + std::chrono::milliseconds(5)))
                return 1;
            continue;
        }
        auto buffer = pcm.buffers[index];
        size_t count = std::min(frames, samples.size() - written);
        std::copy_n(samples.data() + written, count, static_cast<short*>(buffer->mAudioData));
        buffer->mAudioDataByteSize = UInt32(count * 2);
        if (AudioQueueEnqueueBuffer(pcm.queue, buffer, 0, nullptr))
            return -1;
        if (!started) {
            if (AudioQueueStart(pcm.queue, nullptr))
                return -1;
            started = true;
        }
        written += count;
        deadline = Clock::now() + std::chrono::seconds(2);
    }
    for (;;) {
        if (p->canceled)
            return 1;
        if (pcm.failed)
            return -1;
        {
            std::lock_guard<std::mutex> lock(p->mutex);
            if (pcm.completed == samples.size())
                break;
        }
        if (Clock::now() >= deadline)
            return -1;
        if (wait(p, Clock::now() + std::chrono::milliseconds(5)))
            return 1;
    }
    if (AudioQueueStop(pcm.queue, false))
        return -1;
    for (;;) {
        if (p->canceled)
            return 1;
        UInt32 running = 0, size = sizeof(running);
        if (AudioQueueGetProperty(pcm.queue, kAudioQueueProperty_IsRunning, &running, &size))
            return -1;
        if (!running)
            return 0;
        if (Clock::now() >= deadline)
            return -1;
        if (wait(p, Clock::now() + std::chrono::milliseconds(5)))
            return 1;
    }
}
#else
struct PCM {
    snd_pcm_t* value = nullptr;
    ~PCM() {
        if (value) {
            snd_pcm_drop(value);
            snd_pcm_close(value);
        }
    }
};
#endif
} // namespace
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSpeech_createPlayback(JNIEnv* env,
                                                                                   jclass) {
    try {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (registry.size() >= 64 || nextId == INT64_MAX)
            throw std::invalid_argument("Speech playback handle limit");
        jlong id = nextId++;
        registry.emplace(id, std::make_shared<Playback>());
        return id;
    } catch (const std::bad_alloc&) {
        error(env, "java/lang/OutOfMemoryError", "Speech playback allocation");
    } catch (const std::exception& e) {
        error(env, "java/lang/IllegalArgumentException", e.what());
    }
    return 0;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_shiraz_runtime_NativeSpeech_isPlaybackRunning(JNIEnv* env, jclass, jlong id) {
    try {
        return find(id)->running.load() ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        error(env, "java/lang/IllegalArgumentException", e.what());
        return JNI_FALSE;
    }
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSpeech_cancelPlayback(JNIEnv* env,
                                                                                  jclass,
                                                                                  jlong id) {
    try {
        cancel(find(id));
    } catch (const std::exception& e) {
        error(env, "java/lang/IllegalArgumentException", e.what());
    }
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSpeech_releasePlayback(JNIEnv*, jclass,
                                                                                   jlong id) {
    std::shared_ptr<Playback> p;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        auto i = registry.find(id);
        if (i == registry.end())
            return;
        p = i->second;
        registry.erase(i);
    }
    cancel(p);
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSpeech_play(JNIEnv* env, jclass,
                                                                        jlong id, jshortArray input,
                                                                        jint rate) {
    try {
        auto p = find(id);
        Active active(p);
        if (!input || rate < 8000 || rate > 96000 ||
            env->GetArrayLength(input) > int64_t(rate) * 120)
            throw std::invalid_argument("Speech playback PCM bounds");
        if (p->canceled)
            return 1;
        std::vector<short> samples(static_cast<size_t>(env->GetArrayLength(input)));
        if (samples.empty())
            return 0;
        env->GetShortArrayRegion(input, 0, static_cast<jsize>(samples.size()), samples.data());
        if (env->ExceptionCheck())
            return -1;
#ifdef __APPLE__
        return playDarwin(p, samples, rate);
#else
        PCM pcm;
        const char* device = std::getenv("SHIRAZ_AUDIO_DEVICE");
        if (!device || !*device)
            device = "default";
        if (snd_pcm_open(&pcm.value, device, SND_PCM_STREAM_PLAYBACK, SND_PCM_NONBLOCK) < 0)
            return -1;
        if (snd_pcm_set_params(pcm.value, SND_PCM_FORMAT_S16_LE, SND_PCM_ACCESS_RW_INTERLEAVED, 1,
                               rate, 1, 40000) < 0)
            return -1;
        size_t done = 0;
        const auto start = Clock::now();
        auto deadline = start + std::chrono::seconds(2);
        while (done < samples.size()) {
            if (p->canceled)
                return 1;
            size_t chunk =
                std::min(samples.size() - done, static_cast<size_t>(std::max(1, rate / 100)));
            auto count = snd_pcm_writei(pcm.value, samples.data() + done, chunk);
            if (count > 0) {
                done += static_cast<size_t>(count);
                deadline = Clock::now() + std::chrono::seconds(2);
                if (wait(p, start + std::chrono::nanoseconds(int64_t(done) * 1000000000 / rate)))
                    return 1;
            } else {
                if (count == -EPIPE) {
                    if (snd_pcm_prepare(pcm.value) < 0)
                        return -1;
                } else if (count != -EAGAIN && count != -EINTR && count != 0)
                    return -1;
                if (Clock::now() >= deadline)
                    return -1;
                if (wait(p, Clock::now() + std::chrono::milliseconds(5)))
                    return 1;
            }
        }
        deadline = Clock::now() + std::chrono::seconds(2);
        for (;;) {
            if (p->canceled)
                return 1;
            int status = snd_pcm_drain(pcm.value);
            if (status == 0)
                return 0;
            if (status != -EAGAIN && status != -EINTR)
                return -1;
            if (Clock::now() >= deadline)
                return -1;
            if (wait(p, Clock::now() + std::chrono::milliseconds(5)))
                return 1;
        }
#endif
    } catch (const std::bad_alloc&) {
        error(env, "java/lang/OutOfMemoryError", "Speech playback PCM allocation");
    } catch (const std::exception& e) {
        error(env, "java/lang/IllegalArgumentException", e.what());
    }
    return -1;
}
