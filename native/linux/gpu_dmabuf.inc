// SPDX-License-Identifier: GPL-3.0-only
// Included by native/gpu.cpp on Linux only, at file scope after its anonymous namespace.
//
// Linux GBM/DMA-BUF zero-copy consumer: NativeGpu.window draws the newest queued
// AHardwareBuffer slot of a Shiraz ANativeWindow straight into Skia Ganesh on SDL's
// EGL/GLES context (EGL_LINUX_DMA_BUF_EXT -> GL_TEXTURE_2D -> SkImages::BorrowTextureFrom,
// kBottomLeft_GrSurfaceOrigin) after a GPU-side wait on the slot's kernel sync_file fence.
// After flushAndSubmit(), drainReleases() exports a release sync_file fence per sampled
// window and marks each slot so the producer can safely reuse it on supersede.
// libandroid's queue API is resolved dynamically via dlsym so libshiraz-gpu gains no
// link dependency on libandroid.
#include "include/gpu/ganesh/SkImageGanesh.h"
#include "include/gpu/ganesh/gl/GrGLTypes.h"
#include "shiraz/linux/window_queue.h"
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <fcntl.h>
#include <map>
#include <poll.h>
#include <set>
#include <string>
#include <unistd.h>
#include <utility>

struct ANativeWindow;

namespace {
namespace zc {
using EGLDisplay = void*;
using EGLSurface = void*;
using EGLContext = void*;
using EGLImageKHR = void*;
using EGLSyncKHR = void*;
using EGLint = int32_t;
using EGLenum = unsigned int;
using EGLBoolean = unsigned int;

constexpr EGLint kEglNone = 0x3038;
constexpr EGLint kEglDraw = 0x3059;
constexpr EGLint kEglRead = 0x305A;
constexpr EGLint kEglExtensions = 0x3055;
constexpr EGLint kEglWidth = 0x3057;
constexpr EGLint kEglHeight = 0x3056;
constexpr EGLenum kEglLinuxDmaBufExt = 0x3270;
constexpr EGLint kEglLinuxDrmFourccExt = 0x3271;
constexpr EGLint kEglDmaBufPlane0FdExt = 0x3272;
constexpr EGLint kEglDmaBufPlane0OffsetExt = 0x3273;
constexpr EGLint kEglDmaBufPlane0PitchExt = 0x3274;
constexpr EGLenum kEglSyncNativeFenceAndroid = 0x3144;
constexpr EGLint kEglSyncNativeFenceFdAndroid = 0x3145;

constexpr unsigned kTexture2D = 0x0DE1;
constexpr unsigned kMinFilter = 0x2801;
constexpr unsigned kMagFilter = 0x2800;
constexpr unsigned kWrapS = 0x2802;
constexpr unsigned kWrapT = 0x2803;
constexpr int kNearest = 0x2600;
constexpr int kClampToEdge = 0x812F;

bool hasExt(const char* list, const char* name) {
    if (!list)
        return false;
    const size_t len = std::strlen(name);
    for (const char* at = std::strstr(list, name); at; at = std::strstr(at + 1, name)) {
        if ((at == list || at[-1] == ' ') && (at[len] == ' ' || at[len] == 0))
            return true;
    }
    return false;
}

struct Api {
    // libandroid (linux/window_queue.h + hardware_buffer.h)
    decltype(&shiraz_window_acquire_slot) acquire = nullptr;
    decltype(&shiraz_window_nodata_reason) nodataReason = nullptr;
    decltype(&shiraz_window_mark_sampled) mark = nullptr;
    decltype(&shiraz_window_consumer_detach) detach = nullptr;
    decltype(&shiraz_window_abandoned) abandoned = nullptr;
    decltype(&shiraz_hardware_buffer_export) exportBuffer = nullptr;
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
    const char* (*queryString)(EGLDisplay, EGLint) = nullptr;
    EGLImageKHR (*createImage)(EGLDisplay, EGLContext, EGLenum, void*, const EGLint*) = nullptr;
    EGLBoolean (*destroyImage)(EGLDisplay, EGLImageKHR) = nullptr;
    EGLSyncKHR (*createSync)(EGLDisplay, EGLenum, const EGLint*) = nullptr;
    EGLBoolean (*destroySync)(EGLDisplay, EGLSyncKHR) = nullptr;
    EGLint (*waitSync)(EGLDisplay, EGLSyncKHR, EGLint) = nullptr;
    EGLint (*dupFenceFd)(EGLDisplay, EGLSyncKHR) = nullptr;
    void (*genTextures)(int, unsigned*) = nullptr;
    void (*deleteTextures)(int, const unsigned*) = nullptr;
    void (*bindTexture)(unsigned, unsigned) = nullptr;
    void (*texParameteri)(unsigned, unsigned, int) = nullptr;
    void (*imageTargetTexture)(unsigned, void*) = nullptr;
    void (*flush)() = nullptr;
    void (*finish)() = nullptr;
};

struct Entry {
    ANativeWindow* window = nullptr;
    uint32_t index = 0;
    uint64_t generation = 0;
    EGLImageKHR image = nullptr;
    unsigned texture = 0;
    int32_t width = 0, height = 0;
    sk_sp<SkImage> premul, opaque;
};

struct Sampled {
    ANativeWindow* window;
    uint32_t index;
    uint64_t generation;
};

struct Consumer {
    Api api;
    bool ready = false, pending = false, hasNativeFence = false;
    const char* reason = "not-initialized";
    EGLDisplay display = nullptr;
    EGLSurface draw = nullptr, read = nullptr;
    EGLContext context = nullptr;
    uint64_t token = 0, frameSerial = 0;
    std::map<std::pair<ANativeWindow*, uint32_t>, Entry> cache;
    std::vector<Entry> retireAfterFlush;
    std::vector<Sampled> sampledThisFrame;
    std::set<ANativeWindow*> windows; // +1 own references
    std::set<std::pair<ANativeWindow*, std::string>> logged;
    bool loggedRestore = false;
};

thread_local Consumer consumer;

template <typename Fn> void resolveAndroid(Fn& out, const char* name, bool& missing) {
    out = reinterpret_cast<Fn>(dlsym(RTLD_DEFAULT, name));
    if (!out) {
        static void* const android = [] {
            Dl_info info{};
            if (!dladdr(reinterpret_cast<void*>(&consumer), &info) || !info.dli_fname)
                return static_cast<void*>(nullptr);
            std::string path(info.dli_fname);
            auto slash = path.rfind('/');
            path = (slash == std::string::npos ? "" : path.substr(0, slash + 1)) + "libandroid.so";
            return dlopen(path.c_str(), RTLD_NOW | RTLD_NOLOAD);
        }();
        if (android)
            out = reinterpret_cast<Fn>(dlsym(android, name));
    }
    if (!out)
        missing = true;
}

template <typename Fn>
void resolveProc(void* (*getProc)(const char*), Fn& out, const char* name, bool& missing) {
    void* sym = getProc ? getProc(name) : nullptr;
    if (!sym)
        sym = dlsym(RTLD_DEFAULT, name);
    out = reinterpret_cast<Fn>(sym);
    if (!out)
        missing = true;
}

template <typename Fn>
void resolveOptionalProc(void* (*getProc)(const char*), Fn& out, const char* name) {
    void* sym = getProc ? getProc(name) : nullptr;
    if (!sym)
        sym = dlsym(RTLD_DEFAULT, name);
    out = reinterpret_cast<Fn>(sym);
}

void logFallback(ANativeWindow* window, const char* reason) {
    Consumer& c = consumer;
    if (c.api.count)
        c.api.count("consumer_fallbacks", 1);
    if (c.api.event_)
        c.api.event_("consumer_fallback", window);
    if (c.logged.insert({window, reason}).second) {
        std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-fallback window=%p reason=%s\n",
                     static_cast<void*>(window), reason);
    }
    if (std::strncmp(reason, "nodata-", 7) != 0 && c.api.fatal) {
        char fatal[96];
        std::snprintf(fatal, sizeof fatal, "consumer-%s", reason);
        c.api.fatal(fatal);
    }
}

void initialize(void* (*getProc)(const char*)) {
    Consumer& c = consumer;
    bool missing = false;
    Api& a = c.api;
    resolveProc(getProc, a.currentDisplay, "eglGetCurrentDisplay", missing);
    resolveProc(getProc, a.currentSurface, "eglGetCurrentSurface", missing);
    resolveProc(getProc, a.currentContext, "eglGetCurrentContext", missing);
    resolveProc(getProc, a.makeCurrent, "eglMakeCurrent", missing);
    resolveProc(getProc, a.queryString, "eglQueryString", missing);
    resolveProc(getProc, a.createImage, "eglCreateImageKHR", missing);
    resolveProc(getProc, a.destroyImage, "eglDestroyImageKHR", missing);
    resolveProc(getProc, a.genTextures, "glGenTextures", missing);
    resolveProc(getProc, a.deleteTextures, "glDeleteTextures", missing);
    resolveProc(getProc, a.bindTexture, "glBindTexture", missing);
    resolveProc(getProc, a.texParameteri, "glTexParameteri", missing);
    resolveProc(getProc, a.imageTargetTexture, "glEGLImageTargetTexture2DOES", missing);
    resolveProc(getProc, a.flush, "glFlush", missing);
    resolveProc(getProc, a.finish, "glFinish", missing);
    resolveOptionalProc(getProc, a.createSync, "eglCreateSyncKHR");
    resolveOptionalProc(getProc, a.destroySync, "eglDestroySyncKHR");
    resolveOptionalProc(getProc, a.waitSync, "eglWaitSyncKHR");
    resolveOptionalProc(getProc, a.dupFenceFd, "eglDupNativeFenceFDANDROID");
    if (missing) {
        c.reason = "missing-procs";
        c.pending = false;
        return;
    }
    c.display = a.currentDisplay();
    c.draw = a.currentSurface(kEglDraw);
    c.read = a.currentSurface(kEglRead);
    c.context = a.currentContext();
    if (!c.display || !c.context) {
        // SDL is using GLX rather than EGL (e.g. X11 without EGL) — keep CPU fallback.
        c.reason = "no-egl-context";
        c.pending = false;
        return;
    }
    const char* exts = a.queryString(c.display, kEglExtensions);
    if (!hasExt(exts, "EGL_EXT_image_dma_buf_import")) {
        c.reason = "missing-egl-dmabuf";
        c.pending = false;
        return;
    }
    c.hasNativeFence = a.createSync && a.destroySync && a.waitSync && a.dupFenceFd &&
                       hasExt(exts, "EGL_ANDROID_native_fence_sync") &&
                       hasExt(exts, "EGL_KHR_wait_sync");
    c.reason = "natives-missing";
    c.pending = true;
}

bool ensure() {
    Consumer& c = consumer;
    if (c.ready)
        return true;
    if (!c.pending)
        return false;
    bool missing = false;
    Api& a = c.api;
    resolveAndroid(a.acquire, "shiraz_window_acquire_slot", missing);
    resolveAndroid(a.nodataReason, "shiraz_window_nodata_reason", missing);
    resolveAndroid(a.mark, "shiraz_window_mark_sampled", missing);
    resolveAndroid(a.detach, "shiraz_window_consumer_detach", missing);
    resolveAndroid(a.abandoned, "shiraz_window_abandoned", missing);
    resolveAndroid(a.exportBuffer, "shiraz_hardware_buffer_export", missing);
    resolveAndroid(a.reads, "shiraz_cpu_pixel_reads_add", missing);
    resolveAndroid(a.count, "shiraz_zero_copy_count", missing);
    resolveAndroid(a.event_, "shiraz_zero_copy_event", missing);
    resolveAndroid(a.mode, "shiraz_zero_copy_mode", missing);
    resolveAndroid(a.fatal, "shiraz_zero_copy_fatal", missing);
    resolveAndroid(a.acquireWindow, "ANativeWindow_acquire", missing);
    resolveAndroid(a.releaseWindow, "ANativeWindow_release", missing);
    if (missing)
        return false;
    c.pending = false;
    if (a.mode() == 0) {
        c.reason = "env";
        return false;
    }
    static std::atomic<uint64_t> tokens{0};
    c.token = 0x534849524C000000ull + ++tokens; // "SHIRL" + n
    c.ready = true;
    c.reason = "ok";
    const char* trace = std::getenv("SHIRAZ_TRACE_ZERO_COPY");
    if (trace && std::strcmp(trace, "1") == 0) {
        std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer=ready display=%p context=%p fence=%d\n",
                     c.display, c.context, c.hasNativeFence ? 1 : 0);
    }
    return true;
}

struct ContextGuard {
    bool switched = false;
    EGLDisplay display = nullptr;
    EGLSurface draw = nullptr, read = nullptr;
    EGLContext context = nullptr;
    bool ok = true;

    ContextGuard() {
        Consumer& c = consumer;
        Api& a = c.api;
        if (!c.ready)
            return;
        EGLContext current = a.currentContext();
        if (current == c.context)
            return;
        display = a.currentDisplay();
        draw = a.currentSurface(kEglDraw);
        read = a.currentSurface(kEglRead);
        context = current;
        ok = a.makeCurrent(c.display, c.draw, c.read, c.context) != 0;
        switched = ok;
        if (!c.loggedRestore) {
            c.loggedRestore = true;
            const char* trace = std::getenv("SHIRAZ_TRACE_ZERO_COPY");
            if (trace && std::strcmp(trace, "1") == 0)
                std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY consumer-context-restored\n");
        }
    }

    ~ContextGuard() {
        if (!switched)
            return;
        Api& a = consumer.api;
        if (display)
            a.makeCurrent(display, draw, read, context);
        else
            a.makeCurrent(consumer.display, nullptr, nullptr, nullptr);
    }
};

void destroyEntry(Entry& e) {
    Api& a = consumer.api;
    e.premul.reset();
    e.opaque.reset();
    if (e.texture)
        a.deleteTextures(1, &e.texture);
    if (e.image)
        a.destroyImage(consumer.display, e.image);
    e.texture = 0;
    e.image = nullptr;
}

void drainReleases() {
    Consumer& c = consumer;
    Api& a = c.api;
    if (!c.ready)
        return;
    if (c.sampledThisFrame.empty() && c.retireAfterFlush.empty())
        return;
    ContextGuard guard;
    if (!c.sampledThisFrame.empty()) {
        int32_t baseFenceFd = -1;
        if (c.hasNativeFence) {
            EGLSyncKHR sync = a.createSync(c.display, kEglSyncNativeFenceAndroid, nullptr);
            if (sync) {
                a.flush();
                baseFenceFd = a.dupFenceFd(c.display, sync);
                a.destroySync(c.display, sync);
            }
        }
        if (baseFenceFd < 0)
            a.finish();
        for (const Sampled& s : c.sampledThisFrame) {
            int32_t slotFence = baseFenceFd >= 0 ? fcntl(baseFenceFd, F_DUPFD_CLOEXEC, 0) : -1;
            a.mark(s.window, c.token, s.index, s.generation, slotFence);
        }
        if (baseFenceFd >= 0)
            close(baseFenceFd);
        c.sampledThisFrame.clear();
    }
    for (Entry& e : c.retireAfterFlush)
        destroyEntry(e);
    c.retireAfterFlush.clear();
}

void releaseWindowIfUnused(ANativeWindow* window) {
    Consumer& c = consumer;
    for (auto& entry : c.cache) {
        if (entry.first.first == window)
            return;
    }
    if (c.windows.erase(window))
        c.api.releaseWindow(window);
}

void retireAbandoned(GrDirectContext* context) {
    Consumer& c = consumer;
    Api& a = c.api;
    if (!c.ready || c.windows.empty())
        return;
    std::vector<ANativeWindow*> gone;
    for (ANativeWindow* w : c.windows) {
        if (a.abandoned(w))
            gone.push_back(w);
    }
    if (gone.empty())
        return;
    for (ANativeWindow* w : gone) {
        for (auto it = c.cache.begin(); it != c.cache.end();) {
            if (it->first.first == w) {
                it->second.premul.reset();
                it->second.opaque.reset();
                c.retireAfterFlush.push_back(std::move(it->second));
                it = c.cache.erase(it);
            } else {
                ++it;
            }
        }
    }
    if (context)
        context->flushAndSubmit();
    drainReleases();
    for (ANativeWindow* w : gone)
        releaseWindowIfUnused(w);
}

} // namespace zc
} // namespace

namespace shiraz_zero_copy {

void initialize(void* (*getProc)(const char*)) {
    zc::initialize(getProc);
}

void prepare(GrDirectContext* context) {
    zc::Consumer& c = zc::consumer;
    if (!c.ready)
        return;
    if (!c.sampledThisFrame.empty()) {
        if (context)
            context->flushAndSubmit();
        zc::drainReleases();
    }
    zc::retireAbandoned(context);
    ++c.frameSerial;
}

void present() {
    zc::drainReleases();
}

void shutdown(GrDirectContext* context) {
    zc::Consumer& c = zc::consumer;
    if (!c.ready)
        return;
    if (context)
        context->flushAndSubmit();
    zc::drainReleases();
    {
        zc::ContextGuard guard;
        c.api.finish();
        for (ANativeWindow* w : c.windows)
            c.api.detach(w, c.token);
        for (auto& entry : c.cache)
            zc::destroyEntry(entry.second);
        c.cache.clear();
    }
    for (ANativeWindow* w : c.windows)
        c.api.releaseWindow(w);
    c.windows.clear();
    c.ready = false;
    c.reason = "shutdown";
}

void countRead(const char* site, uint64_t bytes) {
    static auto reads = reinterpret_cast<void (*)(const char*, uint64_t)>(
        dlsym(RTLD_DEFAULT, "shiraz_cpu_pixel_reads_add"));
    if (reads)
        reads(site, bytes);
}

} // namespace shiraz_zero_copy

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeGpu_windowSupported(JNIEnv*,
                                                                                    jclass) {
    zc::Consumer& c = zc::consumer;
    if (!engine.context || !zc::ensure()) {
        auto fatal =
            reinterpret_cast<void (*)(const char*)>(dlsym(RTLD_DEFAULT, "shiraz_zero_copy_fatal"));
        if (fatal && engine.context && std::strcmp(c.reason, "env") != 0 &&
            std::strcmp(c.reason, "shutdown") != 0 &&
            std::strcmp(c.reason, "no-egl-context") != 0) {
            char reason[96];
            std::snprintf(reason, sizeof reason, "consumer-%s", c.reason);
            fatal(reason);
        }
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeGpu_window(JNIEnv* e, jclass,
                                                                           jlong h, jlong value,
                                                                           jfloatArray to,
                                                                           jboolean opaque) {
    zc::Consumer& c = zc::consumer;
    zc::Api& a = c.api;
    auto* window = reinterpret_cast<ANativeWindow*>(value);
    if (!engine.context || !window || !zc::ensure())
        return JNI_FALSE;
    try {
        auto& s = surface(h);
        auto b = fixedFloats<4>(e, to);
        zc::ContextGuard guard;
        if (!guard.ok) {
            zc::logFallback(window, "context-switch-failed");
            return JNI_FALSE;
        }
        ShirazWindowSlot slot{};
        int32_t result = a.acquire(window, c.token, c.frameSerial, &slot);
        if (result) {
            const char* reason = result == -ENODATA       ? a.nodataReason(window)
                                 : result == -EBUSY       ? "busy"
                                 : result == -EPIPE       ? "abandoned"
                                 : result == -EINPROGRESS ? "inprogress"
                                                          : "acquire-failed";
            zc::logFallback(window, reason);
            return JNI_FALSE;
        }
        // Always consume slot.fence_fd before returning.
        int32_t fenceFd = slot.fence_fd;
        slot.fence_fd = -1;
        c.sampledThisFrame.push_back({window, slot.index, slot.generation});
        if (c.windows.insert(window).second)
            a.acquireWindow(window);

        auto key = std::make_pair(window, slot.index);
        auto found = c.cache.find(key);
        if (found != c.cache.end() && found->second.generation != slot.generation) {
            c.retireAfterFlush.push_back(std::move(found->second));
            c.cache.erase(found);
            found = c.cache.end();
        }
        if (found == c.cache.end()) {
            zc::Entry entry;
            entry.window = window;
            entry.index = slot.index;
            entry.generation = slot.generation;
            entry.width = slot.width;
            entry.height = slot.height;
            uint32_t fourcc = 0, pitch = 0;
            int dmaFd = a.exportBuffer(slot.buffer, &fourcc, &pitch);
            if (dmaFd < 0) {
                if (fenceFd >= 0)
                    close(fenceFd);
                zc::logFallback(window, "export-failed");
                return JNI_FALSE;
            }
            const zc::EGLint dmaAttribs[] = {zc::kEglWidth,
                                             zc::EGLint(slot.width),
                                             zc::kEglHeight,
                                             zc::EGLint(slot.height),
                                             zc::kEglLinuxDrmFourccExt,
                                             zc::EGLint(fourcc),
                                             zc::kEglDmaBufPlane0FdExt,
                                             dmaFd,
                                             zc::kEglDmaBufPlane0OffsetExt,
                                             0,
                                             zc::kEglDmaBufPlane0PitchExt,
                                             zc::EGLint(pitch),
                                             zc::kEglNone};
            entry.image =
                a.createImage(c.display, nullptr, zc::kEglLinuxDmaBufExt, nullptr, dmaAttribs);
            close(dmaFd);
            if (!entry.image) {
                if (fenceFd >= 0)
                    close(fenceFd);
                zc::logFallback(window, "image-failed");
                return JNI_FALSE;
            }
            a.genTextures(1, &entry.texture);
            a.bindTexture(zc::kTexture2D, entry.texture);
            a.imageTargetTexture(zc::kTexture2D, entry.image);
            a.texParameteri(zc::kTexture2D, zc::kMinFilter, zc::kNearest);
            a.texParameteri(zc::kTexture2D, zc::kMagFilter, zc::kNearest);
            a.texParameteri(zc::kTexture2D, zc::kWrapS, zc::kClampToEdge);
            a.texParameteri(zc::kTexture2D, zc::kWrapT, zc::kClampToEdge);
            engine.context->resetContext(kTextureBinding_GrGLBackendState);
            found = c.cache.emplace(key, std::move(entry)).first;
        }
        zc::Entry& entry = found->second;
        sk_sp<SkImage>& image = opaque ? entry.opaque : entry.premul;
        if (!image) {
            GrGLTextureInfo info{zc::kTexture2D, entry.texture, 0x8058 /* GL_RGBA8 */};
            auto backend =
                GrBackendTextures::MakeGL(entry.width, entry.height, skgpu::Mipmapped::kNo, info);
            image = SkImages::BorrowTextureFrom(
                engine.context.get(), backend, kBottomLeft_GrSurfaceOrigin,
                opaque ? kRGB_888x_SkColorType : kRGBA_8888_SkColorType,
                opaque ? kOpaque_SkAlphaType : kPremul_SkAlphaType, nullptr);
            if (!image) {
                if (fenceFd >= 0)
                    close(fenceFd);
                zc::logFallback(window, "borrow-failed");
                return JNI_FALSE;
            }
        }
        if (fenceFd >= 0) {
            bool waitedOnGpu = false;
            if (c.hasNativeFence) {
                const zc::EGLint attribs[] = {zc::kEglSyncNativeFenceFdAndroid, fenceFd,
                                              zc::kEglNone};
                zc::EGLSyncKHR sync =
                    a.createSync(c.display, zc::kEglSyncNativeFenceAndroid, attribs);
                if (sync) {
                    // EGL owns fenceFd on success.
                    fenceFd = -1;
                    a.waitSync(c.display, sync, 0);
                    a.destroySync(c.display, sync);
                    a.count("consumer_waits_gpu", 1);
                    waitedOnGpu = true;
                }
            }
            if (!waitedOnGpu && fenceFd >= 0) {
                pollfd pf{fenceFd, POLLIN, 0};
                while (poll(&pf, 1, 1000) < 0 && errno == EINTR) {
                }
                close(fenceFd);
                a.count("consumer_waits_cpu", 1);
            }
        }
        s.target->getCanvas()->drawImageRect(
            image, SkRect::MakeWH(float(entry.width), float(entry.height)),
            SkRect::MakeLTRB(b[0], b[1], b[2], b[3]), SkSamplingOptions(SkFilterMode::kNearest),
            &s.paint, SkCanvas::kStrict_SrcRectConstraint);
        s.written = true;
        a.count("gpu_draws", 1);
        return JNI_TRUE;
    } catch (const std::exception& ex) {
        fail(e, ex.what());
        return JNI_FALSE;
    }
}
