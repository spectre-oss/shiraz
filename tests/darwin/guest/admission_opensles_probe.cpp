// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/opensles.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <SLES/OpenSLES_Android.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
void shiraz_opensl_test_deliver(uintptr_t, bool, uint32_t);
void shiraz_opensl_test_set_registration_hook(void (*)());
bool shiraz_opensl_test_closing();
static std::mutex registration_mutex;
static std::condition_variable registration_changed;
static bool registration_entered = false, registration_release = false;
static void hold_registration() {
    std::unique_lock<std::mutex> lock(registration_mutex);
    registration_entered = true;
    registration_changed.notify_all();
    registration_changed.wait(lock, [] { return registration_release; });
}
struct Observation {
    uintptr_t expected_tls = 0, seen_tls = 0, self = 0;
    uint32_t events = 0, calls = 0;
};
static std::atomic<unsigned> routed{0};
extern "C" uintptr_t audio_admission_validate(uintptr_t target, uintptr_t site,
                                              uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        ++routed;
        return domain->route(site, target, sp).target;
    } catch (const std::exception& e) {
        std::fprintf(stderr, "M5_AUDIO_ADMISSION_REJECTED %s\n", e.what());
        std::abort();
    }
}
static void check(bool ok, const char* message) {
    if (!ok)
        throw std::runtime_error(message);
}
static Argument arg(uintptr_t p) {
    return Argument::integer(p, ArgumentType::Int64);
}
template <class T> static Argument ptr(T p) {
    return arg(reinterpret_cast<uintptr_t>(p));
}
static uint64_t call(void* fn, std::vector<Argument> args) {
    return invoke(fn, args).result_integers[0];
}
int main(int argc, char** argv) {
    try {
        check(argc == 2, "fixture directory required");
        Loader* resident = nullptr;
        CodeValidator validate = [&](const void* p) {
            return resident && resident->contains_code(p);
        };
        auto lifecycle = std::make_shared<Lifecycle>(validate);
        Loader loader({argv[1]}, native_personality(validate, lifecycle),
                      Loader::Mode::AdmissionFixture, uintptr_t(&audio_admission_validate));
        resident = &loader;
        lifecycle->attach(loader);
        auto* module = loader.link("libadmission-opensles.so");
        auto domain = loader.admission_domain();
        void* queue_cb =
            reinterpret_cast<void*>(loader.symbol(module, "admission_audio_queue").address);
        void* play_cb =
            reinterpret_cast<void*>(loader.symbol(module, "admission_audio_play").address);
        std::ifstream file(std::string(argv[1]) + "/libadmission-opensles.so",
                           std::ios::binary | std::ios::ate);
        auto size = file.tellg();
        check(bool(file) && size > 0 && size < (1 << 20), "staged fixture size");
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        file.seekg(0);
        check(bool(file.read(reinterpret_cast<char*>(bytes.data()), size)), "staged fixture read");
        auto staged_image = std::make_shared<Image>(bytes, Image::Mapping::AdmissionDeferred, 0,
                                                    uintptr_t(&audio_admission_validate));
        staged_image->relocate([&](size_t index) {
            const auto& s = staged_image->elf().symbols.at(index);
            if (s.section)
                return staged_image->address(index);
            if (s.name.empty())
                return uintptr_t(0);
            throw std::runtime_error("unexpected staged fixture import");
        });
        staged_image->seal();
        auto staged_session = staged_image->admission_session();
        std::shared_ptr<void> staged_lease(
            staged_image,
            reinterpret_cast<void*>(staged_image->load_bias() + staged_image->elf().first_page));
        std::function<void()> shutdown;
        auto provider = opensles_provider(validate, shutdown, lifecycle);
        void* create = nullptr;
        for (auto& e : provider.exports)
            if (e.name == "slCreateEngine")
                create = reinterpret_cast<void*>(e.address);
        SLObjectItf engine = nullptr;
        check(call(create, {ptr(&engine), arg(0), arg(0), arg(0), arg(0), arg(0)}) ==
                  SL_RESULT_SUCCESS,
              "engine");
        SLEngineItf api = nullptr;
        check(call(reinterpret_cast<void*>((*engine)->GetInterface),
                   {ptr(engine), ptr(SL_IID_ENGINE), ptr(&api)}) == SL_RESULT_SUCCESS,
              "engine interface");
        SLObjectItf mix = nullptr;
        check(call(reinterpret_cast<void*>((*api)->CreateOutputMix),
                   {ptr(api), ptr(&mix), arg(0), arg(0), arg(0)}) == SL_RESULT_SUCCESS,
              "mix");
        SLDataLocator_AndroidSimpleBufferQueue loc{SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
        SLDataFormat_PCM pcm{
            SL_DATAFORMAT_PCM,           2,
            SL_SAMPLINGRATE_48,          SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
            SL_BYTEORDER_LITTLEENDIAN};
        SLDataSource source{&loc, &pcm};
        SLDataLocator_OutputMix dest{SL_DATALOCATOR_OUTPUTMIX, mix};
        SLDataSink sink{&dest, nullptr};
        SLObjectItf player = nullptr;
        check(call(reinterpret_cast<void*>((*api)->CreateAudioPlayer),
                   {ptr(api), ptr(&player), ptr(&source), ptr(&sink), arg(0), arg(0), arg(0)}) ==
                  SL_RESULT_SUCCESS,
              "player");
        SLAndroidSimpleBufferQueueItf queue = nullptr;
        SLPlayItf play = nullptr;
        check(call(reinterpret_cast<void*>((*player)->GetInterface),
                   {ptr(player), ptr(SL_IID_ANDROIDSIMPLEBUFFERQUEUE), ptr(&queue)}) ==
                  SL_RESULT_SUCCESS,
              "queue interface");
        check(call(reinterpret_cast<void*>((*player)->GetInterface),
                   {ptr(player), ptr(SL_IID_PLAY), ptr(&play)}) == SL_RESULT_SUCCESS,
              "play interface");
        Observation first, second, playing, host;
        auto qreg = reinterpret_cast<void*>((*queue)->RegisterCallback);
        auto preg = reinterpret_cast<void*>((*play)->RegisterCallback);
        {
            AdmissionDomain::Invocation active(domain);
            check(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&first)}) == SL_RESULT_SUCCESS,
                  "queue register");
            check(call(preg, {ptr(play), ptr(play_cb), ptr(&playing)}) == SL_RESULT_SUCCESS,
                  "play register");
            {
                AdmissionDomain::StagedExtension staged(domain, {{staged_session, staged_lease}},
                                                        {}, {});
                check(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&second)}) ==
                          SL_RESULT_PRECONDITIONS_VIOLATED,
                      "staged queue rejected");
                check(call(preg, {ptr(play), arg(0), arg(0)}) == SL_RESULT_PRECONDITIONS_VIOLATED,
                      "staged play clear rejected");
            }
        }
        auto deliver = [&](auto iface, bool is_play, Observation& o, uint32_t events) {
            std::thread worker([&] {
                prepare_guest_tls();
                o.expected_tls = uintptr_t(guest_tls_base());
                shiraz_opensl_test_deliver(uintptr_t(iface), is_play, events);
            });
            worker.join();
        };
        deliver(queue, false, first, 0);
        deliver(play, true, playing, SL_PLAYEVENT_HEADATEND);
        check(first.calls == 1 && first.seen_tls == first.expected_tls &&
                  first.self == uintptr_t(queue),
              "queue callback TLS and arguments");
        check(playing.calls == 1 && playing.seen_tls == playing.expected_tls &&
                  playing.self == uintptr_t(play) && playing.events == SL_PLAYEVENT_HEADATEND,
              "play callback TLS and arguments");
        {
            Loader other({argv[1]}, native_personality(), Loader::Mode::AdmissionFixture,
                         uintptr_t(&audio_admission_validate));
            other.link("libadmission-opensles.so");
            AdmissionDomain::Invocation foreign(other.admission_domain());
            check(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&host)}) ==
                      SL_RESULT_PRECONDITIONS_VIOLATED,
                  "foreign callback domain accepted");
        }
        check(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&host)}) == SL_RESULT_SUCCESS,
              "host-origin admitted callback registration");
        deliver(queue, false, host, 0);
        check(host.calls == 1 && host.seen_tls == host.expected_tls,
              "host-origin callback domain or TLS");
        {
            AdmissionDomain::Invocation active(domain);
            check(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&second)}) == SL_RESULT_SUCCESS,
                  "queue replacement");
        }
        deliver(queue, false, second, 0);
        check(first.calls == 1 && second.calls == 1 && second.seen_tls == second.expected_tls,
              "replacement callback");
        {
            AdmissionDomain::Invocation active(domain);
            check(call(qreg, {ptr(queue), arg(0), arg(0)}) == SL_RESULT_SUCCESS, "queue clear");
        }
        deliver(queue, false, second, 0);
        check(second.calls == 1 && routed.load(), "cleared callback and routed returns");
        shiraz_opensl_test_set_registration_hook(&hold_registration);
        std::atomic<int> registered{-1};
        std::thread registering(
            [&] { registered = int(call(qreg, {ptr(queue), ptr(queue_cb), ptr(&host)})); });
        {
            std::unique_lock<std::mutex> lock(registration_mutex);
            check(registration_changed.wait_for(lock, std::chrono::seconds(10),
                                                [] { return registration_entered; }),
                  "registration hook timeout");
        }
        check(lifecycle->retained_thread_callbacks() == 1, "registration lost namespace retention");
        std::atomic<bool> stopped{false};
        std::thread stopping([&] {
            shutdown();
            stopped = true;
            registration_changed.notify_all();
        });
        auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
        while (!shiraz_opensl_test_closing() && std::chrono::steady_clock::now() < deadline)
            std::this_thread::yield();
        check(shiraz_opensl_test_closing() && !stopped,
              "shutdown did not wait after closing admission");
        {
            std::unique_lock<std::mutex> lock(registration_mutex);
            registration_release = true;
            registration_changed.notify_all();
        }
        registering.join();
        stopping.join();
        shiraz_opensl_test_set_registration_hook(nullptr);
        check(registered == SL_RESULT_SUCCESS && stopped && !lifecycle->retained_thread_callbacks(),
              "registration shutdown drain");
        std::cout << "M5_ADMISSION_OPENSL_PASS worker_callbacks=3 tls=1 staged_rejected=1 "
                     "foreign_rejected=1 host_origin=1 registration_drain=1 replacement=1 clear=1 "
                     "teardown=1 audio_device_execution=unverified\n";
    } catch (const std::exception& e) {
        std::cerr << "FAIL " << e.what() << '\n';
        return 1;
    }
}
