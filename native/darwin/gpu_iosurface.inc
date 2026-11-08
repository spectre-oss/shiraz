// SPDX-License-Identifier: GPL-3.0-only
// Included by native/gpu.cpp on macOS only, at file scope after its anonymous
// namespace. The M4b consumer (build/macos/m4b/design/DESIGN-v2.md section 5):
// NativeGpu.window draws the newest queued IOSurface slot of a Shiraz window
// straight into the Skia canvas on SDL's ANGLE context (EGLImage texture,
// kBottomLeft), after a GPU wait for the window's frame event. After the
// frame's flushAndSubmit, drainReleases signals the window's release event and
// marks each sampled slot, so the producer can reuse a slot once a later frame
// supersedes it (release on supersede, C1). No CPU copy on this path.
// libandroid's queue API is resolved at run time (dlsym), so libshiraz-gpu
// gains no link dependency; EGL and GL come through SDL's getProc.
#include "include/gpu/ganesh/SkImageGanesh.h"
#include "include/gpu/ganesh/gl/GrGLTypes.h"
#include "shiraz/darwin/window_queue.h"
#include <dlfcn.h>
#include <cerrno>
#include <climits>
#include <cstring>
#include <cstdio>
#include <map>
#include <set>
#include <string>
#include <utility>
struct ANativeWindow;

namespace {
namespace zc {
using EGLDisplay = void*; using EGLSurface = void*; using EGLContext = void*; using EGLImage = void*; using EGLSync = void*;
using EGLAttrib = intptr_t; using EGLint = int32_t; using EGLBoolean = unsigned;
constexpr EGLint kNone = 0x3038, kDraw = 0x3059, kRead = 0x305A, kMetalTexture = 0x34A7, kSharedEvent = 0x34D8,
    kEventObject = 0x34D9, kValueLo = 0x34DA, kValueHi = 0x34DB, kSignaled = 0x34DC, kCondition = 0x30F8,
    kDeviceExt = 0x322C, kMetalDevice = 0x34A6;
constexpr unsigned kTexture2D = 0x0DE1, kMinFilter = 0x2801, kMagFilter = 0x2800, kNearest = 0x2600;

struct Api {
    // libandroid (window_queue.h)
    decltype(&shiraz_window_acquire_slot) acquire = nullptr;
    decltype(&shiraz_window_nodata_reason) nodataReason = nullptr;
    decltype(&shiraz_window_mark_sampled) mark = nullptr;
    decltype(&shiraz_window_consumer_detach) detach = nullptr;
    decltype(&shiraz_window_abandoned) abandoned = nullptr;
    decltype(&shiraz_window_next_value) nextValue = nullptr;
    decltype(&shiraz_window_event) event = nullptr;
    decltype(&shiraz_window_adopt_event) adopt = nullptr;
    decltype(&shiraz_event_signaled) signaled = nullptr;
    decltype(&shiraz_window_set_device) setDevice = nullptr;
    decltype(&shiraz_cpu_pixel_reads_add) reads = nullptr;
    decltype(&shiraz_zero_copy_count) count = nullptr;
    decltype(&shiraz_zero_copy_event) event_ = nullptr;
    decltype(&shiraz_zero_copy_mode) mode = nullptr;
    decltype(&shiraz_zero_copy_fatal) fatal = nullptr;
    void (*acquireWindow)(ANativeWindow*) = nullptr;
    void (*releaseWindow)(ANativeWindow*) = nullptr;
    // EGL / GL on SDL's context
    EGLDisplay (*currentDisplay)() = nullptr;
    EGLSurface (*currentSurface)(EGLint) = nullptr;
    EGLContext (*currentContext)() = nullptr;
    EGLBoolean (*makeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext) = nullptr;
    EGLImage (*createImage)(EGLDisplay, EGLContext, unsigned, void*, const EGLint*) = nullptr;
    EGLBoolean (*destroyImage)(EGLDisplay, EGLImage) = nullptr;
    EGLSync (*createSync)(EGLDisplay, unsigned, const EGLAttrib*) = nullptr;
    EGLBoolean (*destroySync)(EGLDisplay, EGLSync) = nullptr;
    EGLBoolean (*waitSync)(EGLDisplay, EGLSync, EGLint) = nullptr;
    void* (*copyEvent)(EGLDisplay, EGLSync) = nullptr;
    EGLBoolean (*queryDisplay)(EGLDisplay, EGLint, EGLAttrib*) = nullptr;
    EGLBoolean (*queryDevice)(void*, EGLint, EGLAttrib*) = nullptr;
    void (*genTextures)(int, unsigned*) = nullptr;
    void (*deleteTextures)(int, const unsigned*) = nullptr;
    void (*bindTexture)(unsigned, unsigned) = nullptr;
    void (*texParameteri)(unsigned, unsigned, int) = nullptr;
    void (*imageTargetTexture)(unsigned, void*) = nullptr;
    void (*flush)() = nullptr;
    void (*finish)() = nullptr;
};
struct Entry {
    ANativeWindow* window = nullptr; uint32_t index = 0; uint64_t generation = 0;
    EGLImage image = nullptr; unsigned texture = 0;
    int32_t width = 0, height = 0;
    sk_sp<SkImage> premul, opaque;
    uint64_t waitedSerial = 0, waitedValue = 0;
};
struct Sampled { ANativeWindow* window; uint32_t index; uint64_t generation; };
struct Consumer {
    Api api;
    bool ready = false, pending = false;
    const char* reason = "not-initialized";
    EGLDisplay display = nullptr; EGLSurface draw = nullptr, read = nullptr; EGLContext context = nullptr;
    uint64_t token = 0, frameSerial = 0;
    int instanceChecked = 0;  // 0 unknown, 1 same, -1 different
    std::map<std::pair<ANativeWindow*, uint32_t>, Entry> cache;
    std::vector<Entry> retireAfterFlush;
    std::vector<Sampled> sampledThisFrame;
    std::set<ANativeWindow*> windows;  // own references
    std::set<std::pair<ANativeWindow*, std::string>> logged;
    bool loggedRestore = false;
};
thread_local Consumer consumer;

template <typename Fn> void resolveAndroid(Fn& out, const char* name, bool& missing) {
    out = reinterpret_cast<Fn>(dlsym(RTLD_DEFAULT, name));
    if (!out) {
        // libandroid next to this library, if already loaded but not global.
        static void* const android = [] {
            Dl_info info{};
            if (!dladdr(reinterpret_cast<void*>(&consumer), &info) || !info.dli_fname) return static_cast<void*>(nullptr);
            std::string path(info.dli_fname);
            path = path.substr(0, path.rfind('/') + 1) + "libandroid.so";
            return dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD);
        }();
        if (android) out = reinterpret_cast<Fn>(dlsym(android, name));
    }
    if (!out) missing = true;
}
template <typename Fn> void resolveProc(void* (*getProc)(const char*), Fn& out, const char* name, bool& missing) {
    out = reinterpret_cast<Fn>(getProc(name));
    if (!out) missing = true;
}
void logFallback(ANativeWindow* window, const char* reason) {
    Consumer& c = consumer;
    if (c.api.count) c.api.count("consumer_fallbacks", 1);
    if (c.api.event_) c.api.event_("consumer_fallback", window);
    if (c.logged.insert({window, reason}).second)
        std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-fallback window=%p reason=%s\n", static_cast<void*>(window), reason);
    if (std::strncmp(reason, "nodata-", 7) != 0 && c.api.fatal) {
        char fatal[96]; std::snprintf(fatal, sizeof fatal, "consumer-%s", reason);
        c.api.fatal(fatal);
    }
}
// gpu_initialize time, SDL's context current: EGL/GL procs and SDL's binding (C6).
void initialize(void* (*getProc)(const char*)) {
    Consumer& c = consumer;
    bool missing = false;
    Api& a = c.api;
    resolveProc(getProc, a.currentDisplay, "eglGetCurrentDisplay", missing);
    resolveProc(getProc, a.currentSurface, "eglGetCurrentSurface", missing);
    resolveProc(getProc, a.currentContext, "eglGetCurrentContext", missing);
    resolveProc(getProc, a.makeCurrent, "eglMakeCurrent", missing);
    resolveProc(getProc, a.createImage, "eglCreateImageKHR", missing);
    resolveProc(getProc, a.destroyImage, "eglDestroyImageKHR", missing);
    resolveProc(getProc, a.createSync, "eglCreateSync", missing);
    resolveProc(getProc, a.destroySync, "eglDestroySync", missing);
    resolveProc(getProc, a.waitSync, "eglWaitSync", missing);
    resolveProc(getProc, a.copyEvent, "eglCopyMetalSharedEventANGLE", missing);
    resolveProc(getProc, a.queryDisplay, "eglQueryDisplayAttribEXT", missing);
    resolveProc(getProc, a.queryDevice, "eglQueryDeviceAttribEXT", missing);
    resolveProc(getProc, a.genTextures, "glGenTextures", missing);
    resolveProc(getProc, a.deleteTextures, "glDeleteTextures", missing);
    resolveProc(getProc, a.bindTexture, "glBindTexture", missing);
    resolveProc(getProc, a.texParameteri, "glTexParameteri", missing);
    resolveProc(getProc, a.imageTargetTexture, "glEGLImageTargetTexture2DOES", missing);
    resolveProc(getProc, a.flush, "glFlush", missing);
    resolveProc(getProc, a.finish, "glFinish", missing);
    if (missing) { c.reason = "missing-procs"; c.pending = false; return; }
    c.display = a.currentDisplay(); c.draw = a.currentSurface(kDraw); c.read = a.currentSurface(kRead); c.context = a.currentContext();
    if (!c.display || !c.context) { c.reason = "no-sdl-context"; c.pending = false; return; }
    c.reason = "natives-missing"; c.pending = true;
}
// First use: libandroid's queue API (loaded by the runtime after presentation starts),
// then the device check. Retried while libandroid is not loaded yet.
bool ensure() {
    Consumer& c = consumer;
    if (c.ready) return true;
    if (!c.pending) return false;
    bool missing = false;
    Api& a = c.api;
    resolveAndroid(a.acquire, "shiraz_window_acquire_slot", missing);
    resolveAndroid(a.nodataReason, "shiraz_window_nodata_reason", missing);
    resolveAndroid(a.mark, "shiraz_window_mark_sampled", missing);
    resolveAndroid(a.detach, "shiraz_window_consumer_detach", missing);
    resolveAndroid(a.abandoned, "shiraz_window_abandoned", missing);
    resolveAndroid(a.nextValue, "shiraz_window_next_value", missing);
    resolveAndroid(a.event, "shiraz_window_event", missing);
    resolveAndroid(a.adopt, "shiraz_window_adopt_event", missing);
    resolveAndroid(a.signaled, "shiraz_event_signaled", missing);
    resolveAndroid(a.setDevice, "shiraz_window_set_device", missing);
    resolveAndroid(a.reads, "shiraz_cpu_pixel_reads_add", missing);
    resolveAndroid(a.count, "shiraz_zero_copy_count", missing);
    resolveAndroid(a.event_, "shiraz_zero_copy_event", missing);
    resolveAndroid(a.mode, "shiraz_zero_copy_mode", missing);
    resolveAndroid(a.fatal, "shiraz_zero_copy_fatal", missing);
    resolveAndroid(a.acquireWindow, "ANativeWindow_acquire", missing);
    resolveAndroid(a.releaseWindow, "ANativeWindow_release", missing);
    if (missing) return false;
    c.pending = false;
    if (a.mode() == 0) { c.reason = "env"; return false; }
    EGLAttrib device = 0, metal = 0;
    if (!a.queryDisplay(c.display, kDeviceExt, &device) || !device || !a.queryDevice(reinterpret_cast<void*>(device), kMetalDevice, &metal) || !metal) {
        c.reason = "no-device"; a.fatal("consumer-no-device"); return false;
    }
    if (a.setDevice(reinterpret_cast<void*>(metal)) != 0) { c.reason = "device-mismatch"; a.fatal("device-mismatch"); return false; }
    static std::atomic<uint64_t> tokens{0};
    c.token = 0x5348495243000000ull + ++tokens;  // "SHIRC" + n
    c.ready = true; c.reason = "ok";
    std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer=ready display=%p context=%p\n", c.display, c.context);
    return true;
}
// Same ANGLE instance as the shim (section 3.5). Checked once the shim is loaded.
bool sameInstance(void* (*getProcAny)(const char*)) {
    Consumer& c = consumer;
    if (c.instanceChecked) return c.instanceChecked > 0;
    using PathFn = const char* (*)();
    auto shimPath = reinterpret_cast<PathFn>(dlsym(RTLD_DEFAULT, "shiraz_egl_library_path"));
    if (!shimPath) {
        Dl_info self{};
        if (dladdr(reinterpret_cast<void*>(&consumer), &self) && self.dli_fname) {
            std::string path(self.dli_fname);
            path = path.substr(0, path.rfind('/') + 1) + "libEGL_shim.so";
            if (void* shim = dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD)) shimPath = reinterpret_cast<PathFn>(dlsym(shim, "shiraz_egl_library_path"));
        }
    }
    if (!shimPath || !shimPath()) return false;  // not decided yet: the shim is not loaded
    Dl_info info{};
    char resolved[PATH_MAX];
    std::string mine;
    if (dladdr(reinterpret_cast<void*>(getProcAny("eglCreateSync")), &info) && info.dli_fname)
        mine = realpath(info.dli_fname, resolved) ? resolved : info.dli_fname;
    c.instanceChecked = mine == shimPath() ? 1 : -1;
    std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-instance %s sdl=%s shim=%s\n", c.instanceChecked > 0 ? "same" : "different", mine.c_str(), shimPath());
    return c.instanceChecked > 0;
}
void* (*storedGetProc)(const char*) = nullptr;

// C6: make SDL's context current for the duration, then restore the caller's binding.
struct ContextGuard {
    bool switched = false;
    EGLDisplay display = nullptr; EGLSurface draw = nullptr, read = nullptr; EGLContext context = nullptr;
    bool ok = true;
    ContextGuard() {
        Consumer& c = consumer; Api& a = c.api;
        if (!c.ready) return;
        EGLContext current = a.currentContext();
        if (current == c.context) return;
        display = a.currentDisplay(); draw = a.currentSurface(kDraw); read = a.currentSurface(kRead); context = current;
        ok = a.makeCurrent(c.display, c.draw, c.read, c.context);
        switched = ok;
        if (!c.loggedRestore) { c.loggedRestore = true; std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-context-restored\n"); }
    }
    ~ContextGuard() {
        if (!switched) return;
        Api& a = consumer.api;
        if (display) a.makeCurrent(display, draw, read, context);
        else a.makeCurrent(consumer.display, nullptr, nullptr, nullptr);
    }
};
void destroyEntry(Entry& e) {
    Api& a = consumer.api;
    e.premul.reset(); e.opaque.reset();
    if (e.texture) a.deleteTextures(1, &e.texture);
    if (e.image) a.destroyImage(consumer.display, e.image);
    e.texture = 0; e.image = nullptr;
}
// End of frame (C1 invariant): signal the release event once per window that was sampled,
// one flush, then mark. Called after flushAndSubmit.
void drainReleases() {
    Consumer& c = consumer; Api& a = c.api;
    if (!c.ready) return;
    if (c.sampledThisFrame.empty() && c.retireAfterFlush.empty()) return;
    ContextGuard guard;
    std::map<ANativeWindow*, uint64_t> values;
    for (const Sampled& s : c.sampledThisFrame) {
        if (values.count(s.window)) continue;
        const uint64_t value = a.nextValue(s.window, SHIRAZ_EVENT_RELEASE);
        uint64_t serial = 0;
        void* event = a.event(s.window, SHIRAZ_EVENT_RELEASE, &serial);
        std::vector<EGLAttrib> attributes;
        if (event) { attributes.push_back(kEventObject); attributes.push_back(reinterpret_cast<EGLAttrib>(event)); }
        attributes.insert(attributes.end(), {kValueLo, EGLAttrib(value & 0xffffffffu), kValueHi, EGLAttrib(value >> 32), kNone});
        EGLSync sync = a.createSync(c.display, kSharedEvent, attributes.data());
        if (!sync) { logFallback(s.window, "release-signal-failed"); continue; }
        if (!event) a.adopt(s.window, SHIRAZ_EVENT_RELEASE, a.copyEvent(c.display, sync), &serial);
        a.destroySync(c.display, sync);
        values[s.window] = value;
    }
    a.flush();
    for (const Sampled& s : c.sampledThisFrame) {
        auto found = values.find(s.window);
        if (found != values.end()) a.mark(s.window, c.token, s.index, s.generation, found->second);
    }
    c.sampledThisFrame.clear();
    for (Entry& e : c.retireAfterFlush) destroyEntry(e);
    c.retireAfterFlush.clear();
}
void releaseWindowIfUnused(ANativeWindow* window) {
    Consumer& c = consumer;
    for (auto& entry : c.cache) if (entry.first.first == window) return;
    if (c.windows.erase(window)) c.api.releaseWindow(window);
}
void retireAbandoned(GrDirectContext* context) {
    Consumer& c = consumer; Api& a = c.api;
    if (!c.ready || c.windows.empty()) return;
    std::vector<ANativeWindow*> gone;
    for (ANativeWindow* w : c.windows) if (a.abandoned(w)) gone.push_back(w);
    if (gone.empty()) return;
    for (ANativeWindow* w : gone)
        for (auto it = c.cache.begin(); it != c.cache.end();)
            if (it->first.first == w) { it->second.premul.reset(); it->second.opaque.reset(); c.retireAfterFlush.push_back(std::move(it->second)); it = c.cache.erase(it); }
            else ++it;
    if (context) context->flushAndSubmit();
    drainReleases();
    for (ANativeWindow* w : gone) releaseWindowIfUnused(w);
}
} // namespace zc
} // namespace

namespace shiraz_zero_copy {
void initialize(void* (*getProc)(const char*)) { zc::storedGetProc = getProc; zc::initialize(getProc); }
// Start of a frame: retire abandoned windows; drain a frame that ended without a present.
void prepare(GrDirectContext* context) {
    zc::Consumer& c = zc::consumer;
    if (!c.ready) return;
    if (!c.sampledThisFrame.empty()) { if (context) context->flushAndSubmit(); zc::drainReleases(); }
    zc::retireAbandoned(context);
    ++c.frameSerial;
}
void present() { zc::drainReleases(); }
void shutdown(GrDirectContext* context) {
    zc::Consumer& c = zc::consumer;
    if (!c.ready) return;
    if (context) context->flushAndSubmit();
    zc::drainReleases();
    {
        zc::ContextGuard guard;
        c.api.finish();
        for (ANativeWindow* w : c.windows) c.api.detach(w, c.token);
        for (auto& entry : c.cache) zc::destroyEntry(entry.second);
        c.cache.clear();
    }
    for (ANativeWindow* w : c.windows) c.api.releaseWindow(w);
    c.windows.clear();
    c.ready = false; c.reason = "shutdown";
}
void countRead(const char* site, uint64_t bytes) {
    static auto reads = reinterpret_cast<void (*)(const char*, uint64_t)>(dlsym(RTLD_DEFAULT, "shiraz_cpu_pixel_reads_add"));
    if (reads) reads(site, bytes);
}
} // namespace shiraz_zero_copy

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeGpu_windowSupported(JNIEnv*, jclass) {
    zc::Consumer& c = zc::consumer;
    if (!engine.context || !zc::ensure()) {
        static std::string logged;
        if (logged != c.reason) { logged = c.reason; std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-unavailable reason=%s\n", c.reason); }
        // Under require only an explicit SHIRAZ_EGL_ZERO_COPY=0 / software canvas may skip the consumer.
        auto fatal = reinterpret_cast<void (*)(const char*)>(dlsym(RTLD_DEFAULT, "shiraz_zero_copy_fatal"));
        if (fatal && engine.context && std::strcmp(c.reason, "env") != 0 && std::strcmp(c.reason, "shutdown") != 0) {
            char reason[96]; std::snprintf(reason, sizeof reason, "consumer-%s", c.reason);
            fatal(reason);
        }
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeGpu_window(JNIEnv* e, jclass, jlong h, jlong value, jfloatArray to, jboolean opaque) {
    zc::Consumer& c = zc::consumer; zc::Api& a = c.api;
    auto* window = reinterpret_cast<ANativeWindow*>(value);
    if (!engine.context || !window || !zc::ensure()) return JNI_FALSE;
    try {
        auto& s = surface(h);
        auto b = fixedFloats<4>(e, to);
        zc::ContextGuard guard;
        if (!guard.ok) { zc::logFallback(window, "context-switch-failed"); return JNI_FALSE; }
        ShirazWindowSlot slot{};
        int32_t result = a.acquire(window, c.token, c.frameSerial, &slot);
        if (result) {
            const char* reason = result == -ENODATA ? a.nodataReason(window) : result == -EBUSY ? "busy" : result == -EPIPE ? "abandoned"
                               : result == -EINPROGRESS ? "inprogress" : "acquire-failed";
            zc::logFallback(window, reason);
            return JNI_FALSE;
        }
        // From here the slot counts as sampled this frame, whatever happens (section 5.2).
        c.sampledThisFrame.push_back({window, slot.index, slot.generation});
        if (c.windows.insert(window).second) a.acquireWindow(window);
        if (!zc::sameInstance(zc::storedGetProc) && c.instanceChecked < 0) { zc::logFallback(window, "two-angle-instances"); return JNI_FALSE; }
        auto key = std::make_pair(window, slot.index);
        auto found = c.cache.find(key);
        if (found != c.cache.end() && found->second.generation != slot.generation) {
            c.retireAfterFlush.push_back(std::move(found->second));
            c.cache.erase(found); found = c.cache.end();
        }
        if (found == c.cache.end()) {
            zc::Entry entry;
            entry.window = window; entry.index = slot.index; entry.generation = slot.generation;
            entry.width = slot.width; entry.height = slot.height;
            const zc::EGLint none[] = {zc::kNone};
            entry.image = a.createImage(c.display, nullptr, zc::kMetalTexture, slot.texture, none);
            if (!entry.image) { zc::logFallback(window, "image-failed"); return JNI_FALSE; }
            a.genTextures(1, &entry.texture);
            a.bindTexture(zc::kTexture2D, entry.texture);
            a.imageTargetTexture(zc::kTexture2D, entry.image);
            a.texParameteri(zc::kTexture2D, zc::kMinFilter, zc::kNearest);
            a.texParameteri(zc::kTexture2D, zc::kMagFilter, zc::kNearest);
            engine.context->resetContext(kTextureBinding_GrGLBackendState);
            found = c.cache.emplace(key, std::move(entry)).first;
        }
        zc::Entry& entry = found->second;
        sk_sp<SkImage>& image = opaque ? entry.opaque : entry.premul;
        if (!image) {
            GrGLTextureInfo info{zc::kTexture2D, entry.texture, 0x8058 /* GL_RGBA8 */};
            auto backend = GrBackendTextures::MakeGL(entry.width, entry.height, skgpu::Mipmapped::kNo, info);
            image = SkImages::BorrowTextureFrom(engine.context.get(), backend, kBottomLeft_GrSurfaceOrigin,
                                                opaque ? kRGB_888x_SkColorType : kRGBA_8888_SkColorType,
                                                opaque ? kOpaque_SkAlphaType : kPremul_SkAlphaType, nullptr);
            if (!image) { zc::logFallback(window, "borrow-failed"); return JNI_FALSE; }
        }
        if ((slot.event_serial != entry.waitedSerial || slot.value != entry.waitedValue) && slot.value && !a.signaled(slot.event, slot.value)) {
            const zc::EGLAttrib attributes[] = {zc::kEventObject, reinterpret_cast<zc::EGLAttrib>(slot.event),
                zc::kValueLo, zc::EGLAttrib(slot.value & 0xffffffffu), zc::kValueHi, zc::EGLAttrib(slot.value >> 32),
                zc::kCondition, zc::kSignaled, zc::kNone};
            zc::EGLSync sync = a.createSync(c.display, zc::kSharedEvent, attributes);
            if (!sync) { zc::logFallback(window, "frame-wait-failed"); return JNI_FALSE; }
            a.waitSync(c.display, sync, 0);
            a.destroySync(c.display, sync);
            a.count("consumer_waits_gpu", 1);
        }
        entry.waitedSerial = slot.event_serial; entry.waitedValue = slot.value;
        s.target->getCanvas()->drawImageRect(image, SkRect::MakeWH(float(entry.width), float(entry.height)),
                                             SkRect::MakeLTRB(b[0], b[1], b[2], b[3]),
                                             SkSamplingOptions(SkFilterMode::kNearest), &s.paint, SkCanvas::kStrict_SrcRectConstraint);
        s.written = true;
        a.count("gpu_draws", 1);
        return JNI_TRUE;
    } catch (const std::exception& ex) {
        fail(e, ex.what());
        return JNI_FALSE;
    }
}
