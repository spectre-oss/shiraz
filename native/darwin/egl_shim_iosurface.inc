// SPDX-License-Identifier: GPL-3.0-only
// Included by native/egl_shim.cpp on macOS only, inside its anonymous namespace,
// after egl_shim_gles1.inc (so the framebuffer/renderbuffer names below are its
// ES 1-aware wrappers) and before egl_shim_contexts.inc.
//
// M4b producer (build/macos/m4b/design/DESIGN-v2.md section 4): a Shiraz window
// surface renders into IOSurface slots of the window's buffer queue
// (include/shiraz/darwin/window_queue.h). Each slot generation becomes one
// EGLImage on the 'SHIR' display (EGL_METAL_TEXTURE_ANGLE over the slot's
// MTLTexture), and per (context, generation) one renderbuffer made with
// glEGLImageTargetRenderbufferStorageOES. The window FBO keeps its D24S8
// renderbuffer; its colour attachment is re-pointed at the current slot.
// Swap signals the window's frame event, flushes and queues: no glFinish, no
// glReadPixels, no CPU copy. A window that fails any precondition keeps the
// readback path (the rest of egl_shim.cpp, unchanged).
#include "shiraz/darwin/window_queue.h"
#include <EGL/eglext_angle.h>
#include <GLES2/gl2ext.h>
#include <climits>
#include <memory>

constexpr EGLint darwinDeviceExt = 0x322C;  // EGL_DEVICE_EXT
struct ZcContext {
    GLuint fbo = 0, depth = 0;
    int32_t depthWidth = 0, depthHeight = 0;
    struct Rb { uint64_t generation = 0; GLuint name = 0; bool checked = false; } rbs[SHIRAZ_WINDOW_MAX_SLOTS];
    int attached = -1; uint64_t attachedGeneration = 0;
};
struct DarwinDraw {
    std::mutex mutex;
    ANativeWindow* window = nullptr;  // own reference
    EGLDisplay display = EGL_NO_DISPLAY;
    uint64_t producer = 0;
    enum Mode { UNDECIDED, ZERO_COPY, READBACK } mode = UNDECIDED;
    bool held = false, waitPending = false, loggedEvent = false;
    ShirazWindowSlot slot{};
    struct Img { uint64_t generation = 0; EGLImageKHR image = EGL_NO_IMAGE_KHR; } images[SHIRAZ_WINDOW_MAX_SLOTS];
    std::unordered_map<EGLContext, ZcContext> contexts;
    ~DarwinDraw();
};
// Under drawsMutex. Linux text (Draw*, draws, findDraw) is unchanged.
// Lock order: drawsMutex, then DarwinDraw::mutex, then the window mutex; orphanMutex is a leaf.
std::unordered_map<EGLSurface, std::shared_ptr<DarwinDraw>> darwinDraws;
std::mutex orphanMutex;
std::unordered_map<EGLContext, std::vector<std::pair<bool, GLuint>>> darwinOrphans;  // (framebuffer?, name), under orphanMutex

struct ZcProcs {
    PFNEGLCREATESYNCPROC createSync = nullptr;
    PFNEGLDESTROYSYNCPROC destroySync = nullptr;
    PFNEGLWAITSYNCPROC waitSync = nullptr;
    PFNEGLCOPYMETALSHAREDEVENTANGLEPROC copyEvent = nullptr;
    PFNEGLCREATEIMAGEKHRPROC createImage = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC destroyImage = nullptr;
    PFNGLEGLIMAGETARGETRENDERBUFFERSTORAGEOESPROC imageStorage = nullptr;
    PFNEGLQUERYDISPLAYATTRIBEXTPROC queryDisplay = nullptr;
    PFNEGLQUERYDEVICEATTRIBEXTPROC queryDevice = nullptr;
    PFNEGLQUERYSTRINGPROC queryString = nullptr;
    bool complete() const { return createSync && destroySync && waitSync && copyEvent && createImage && destroyImage && imageStorage && queryDisplay && queryDevice && queryString; }
};
const ZcProcs& zcProcs() {
    static const ZcProcs procs = [] {
        ZcProcs p;
        Proc proc = realProc();
        if (!proc) return p;
        p.createSync = reinterpret_cast<PFNEGLCREATESYNCPROC>(proc("eglCreateSync"));
        p.destroySync = reinterpret_cast<PFNEGLDESTROYSYNCPROC>(proc("eglDestroySync"));
        p.waitSync = reinterpret_cast<PFNEGLWAITSYNCPROC>(proc("eglWaitSync"));
        p.copyEvent = reinterpret_cast<PFNEGLCOPYMETALSHAREDEVENTANGLEPROC>(proc("eglCopyMetalSharedEventANGLE"));
        p.createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(proc("eglCreateImageKHR"));
        p.destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(proc("eglDestroyImageKHR"));
        p.imageStorage = reinterpret_cast<PFNGLEGLIMAGETARGETRENDERBUFFERSTORAGEOESPROC>(proc("glEGLImageTargetRenderbufferStorageOES"));
        p.queryDisplay = reinterpret_cast<PFNEGLQUERYDISPLAYATTRIBEXTPROC>(proc("eglQueryDisplayAttribEXT"));
        p.queryDevice = reinterpret_cast<PFNEGLQUERYDEVICEATTRIBEXTPROC>(proc("eglQueryDeviceAttribEXT"));
        p.queryString = reinterpret_cast<PFNEGLQUERYSTRINGPROC>(symbol("eglQueryString"));
        return p;
    }();
    return procs;
}
DarwinDraw::~DarwinDraw() {
    const ZcProcs& p = zcProcs();
    for (auto& image : images)
        if (image.image != EGL_NO_IMAGE_KHR && p.destroyImage) p.destroyImage(display, image.image);
    if (window) ANativeWindow_release(window);
}
bool zcTrace() {
    static const bool enabled = [] { const char* v = std::getenv("SHIRAZ_TRACE_ZERO_COPY"); return v && std::strcmp(v, "1") == 0; }();
    return enabled;
}
uint64_t zcNowNs() {
    return uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count());
}
void (*zcBind())(GLenum, GLuint) {
    if (darwinEs1Current()) return darwinOesBindFramebuffer();
    static void (*const bind)(GLenum, GLuint) = reinterpret_cast<void (*)(GLenum, GLuint)>(dlsym(RTLD_NEXT, "glBindFramebuffer"));
    return bind;
}
std::shared_ptr<DarwinDraw> darwinLookup(EGLSurface surface) {
    std::lock_guard<std::mutex> guard(drawsMutex);
    auto found = darwinDraws.find(surface);
    return found == darwinDraws.end() ? nullptr : found->second;
}
// createWindow hook, drawsMutex held.
void darwinRegister(EGLSurface surface, ANativeWindow* window, EGLDisplay display) {
    auto draw = std::make_shared<DarwinDraw>();
    ANativeWindow_acquire(window);
    draw->window = window; draw->display = display;
    draw->producer = reinterpret_cast<uint64_t>(surface);
    darwinDraws[surface] = std::move(draw);
}
// Deletes names orphaned to CONTEXT by a destroy on another thread. CONTEXT is current.
void darwinProcessOrphans(EGLContext context) {
    std::vector<std::pair<bool, GLuint>> names;
    {
        std::lock_guard<std::mutex> guard(orphanMutex);
        auto found = darwinOrphans.find(context);
        if (found == darwinOrphans.end()) return;
        names.swap(found->second);
        darwinOrphans.erase(found);
    }
    for (auto& name : names) {
        if (name.first) glDeleteFramebuffers(1, &name.second);
        else glDeleteRenderbuffers(1, &name.second);
    }
}
bool hasExtension(const char* list, const char* name) {
    if (!list) return false;
    const size_t length = std::strlen(name);
    for (const char* at = std::strstr(list, name); at; at = std::strstr(at + 1, name))
        if ((at == list || at[-1] == ' ') && (at[length] == ' ' || at[length] == 0)) return true;
    return false;
}
// Mode selection (section 4.1). The window's context is current. Returns null for zero copy.
const char* darwinDecide(DarwinDraw& draw) {
    if (shiraz_zero_copy_mode() == 0) return "env";
    const char* why = nullptr;
    if (shiraz_window_cpu_frames_required(draw.window, &why)) return why;
    if (!darwinGlesLoaded()) return "no-gles";
    const ZcProcs& p = zcProcs();
    if (!p.complete()) return "missing-procs";
    const char* extensions = p.queryString(draw.display, EGL_EXTENSIONS);
    if (!hasExtension(extensions, "EGL_ANGLE_metal_texture_client_buffer") ||
        !hasExtension(extensions, "EGL_ANGLE_metal_shared_event_sync") ||
        !hasExtension(extensions, "EGL_ANGLE_iosurface_client_buffer")) return "missing-egl-extensions";
    if (!hasExtension(reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS)), "GL_OES_EGL_image")) return "missing-gl-oes-egl-image";
    EGLAttrib device = 0, metal = 0;
    if (!p.queryDisplay(draw.display, darwinDeviceExt, &device) || !device ||
        !p.queryDevice(reinterpret_cast<EGLDeviceEXT>(device), EGL_METAL_DEVICE_ANGLE, &metal) || !metal) return "no-device";
    int32_t set = shiraz_window_set_device(reinterpret_cast<void*>(metal));
    if (set == -EXDEV) return "device-mismatch";
    if (set) return "no-device";
    int32_t connected = shiraz_window_connect_producer(draw.window, draw.producer);
    if (connected == -EBUSY) return "producer-busy";
    if (connected) return "abandoned";
    return nullptr;
}
void darwinLogMode(const DarwinDraw& draw, const char* reason) {
    std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY window=%p mode=%s reason=%s slots=3 format=RGBA\n",
                 static_cast<void*>(draw.window), reason ? "readback" : "iosurface", reason ? reason : "ok");
    if (reason && std::strcmp(reason, "export") && std::strcmp(reason, "frame-sink") && std::strcmp(reason, "offscreen")) {
        char fatal[96]; std::snprintf(fatal, sizeof fatal, "readback-%s", reason);
        shiraz_zero_copy_fatal(fatal);
    }
}
// GPU wait for the release value of a newly dequeued slot (section 4.4). Skipped when already signalled (C10).
void darwinReleaseWait(DarwinDraw& draw) {
    if (!draw.waitPending) return;
    draw.waitPending = false;
    const ShirazWindowSlot& s = draw.slot;
    if (!s.value) return;
    if (shiraz_event_signaled(s.event, s.value)) { shiraz_zero_copy_count("release_waits_skipped", 1); return; }
    const ZcProcs& p = zcProcs();
    const EGLAttrib attributes[] = {EGL_SYNC_METAL_SHARED_EVENT_OBJECT_ANGLE, reinterpret_cast<EGLAttrib>(s.event),
        EGL_SYNC_METAL_SHARED_EVENT_SIGNAL_VALUE_LO_ANGLE, EGLAttrib(s.value & 0xffffffffu),
        EGL_SYNC_METAL_SHARED_EVENT_SIGNAL_VALUE_HI_ANGLE, EGLAttrib(s.value >> 32),
        EGL_SYNC_CONDITION, EGL_SYNC_METAL_SHARED_EVENT_SIGNALED_ANGLE, EGL_NONE};
    EGLSync sync = p.createSync(draw.display, EGL_SYNC_METAL_SHARED_EVENT_ANGLE, attributes);
    if (sync == EGL_NO_SYNC) { std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY release-wait-failed window=%p\n", static_cast<void*>(draw.window)); shiraz_zero_copy_fatal("release-wait-failed"); return; }
    p.waitSync(draw.display, sync, 0);
    p.destroySync(draw.display, sync);
    shiraz_zero_copy_count("release_waits_gpu", 1);
}
// Points CONTEXT's window FBO at the held slot (section 4.2). The context is current; draw.mutex held.
bool darwinAttach(DarwinDraw& draw, EGLContext context) {
    const ZcProcs& p = zcProcs();
    const ShirazWindowSlot& s = draw.slot;
    ZcContext& c = draw.contexts[context];
    darwinReleaseWait(draw);
    if (c.fbo && c.attached == int(s.index) && c.attachedGeneration == s.generation) return true;
    auto& image = draw.images[s.index];
    if (image.generation != s.generation) {
        if (image.image != EGL_NO_IMAGE_KHR) p.destroyImage(draw.display, image.image);
        const EGLint none[] = {EGL_NONE};
        image.image = p.createImage(draw.display, EGL_NO_CONTEXT, EGL_METAL_TEXTURE_ANGLE, static_cast<EGLClientBuffer>(s.texture), none);
        image.generation = image.image == EGL_NO_IMAGE_KHR ? 0 : s.generation;
        if (image.image == EGL_NO_IMAGE_KHR) { std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY image-failed window=%p egl=%x\n", static_cast<void*>(draw.window), eglGetError()); return false; }
    }
    GLint previousRenderbuffer = 0, previousFramebuffer = 0;
    glGetIntegerv(GL_RENDERBUFFER_BINDING, &previousRenderbuffer);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &previousFramebuffer);
    auto& rb = c.rbs[s.index];
    if (rb.generation != s.generation) {
        if (!rb.name) glGenRenderbuffers(1, &rb.name);
        glBindRenderbuffer(GL_RENDERBUFFER, rb.name);
        p.imageStorage(GL_RENDERBUFFER, image.image);
        rb.generation = s.generation; rb.checked = false;
    }
    bool depthChanged = false;
    if (!c.depth || c.depthWidth != s.width || c.depthHeight != s.height) {
        if (!c.depth) glGenRenderbuffers(1, &c.depth);
        glBindRenderbuffer(GL_RENDERBUFFER, c.depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, s.width, s.height);
        c.depthWidth = s.width; c.depthHeight = s.height; depthChanged = true;
    }
    glBindRenderbuffer(GL_RENDERBUFFER, static_cast<GLuint>(previousRenderbuffer));
    auto bind = zcBind();
    if (!bind) return false;
    const bool fresh = !c.fbo;
    if (fresh) glGenFramebuffers(1, &c.fbo);
    bind(GL_FRAMEBUFFER, c.fbo);
    if (fresh || depthChanged) {
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, c.depth);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, c.depth);
    }
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rb.name);
    if (!rb.checked || depthChanged) {
        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY fbo-incomplete window=%p status=%x version=%s\n", static_cast<void*>(draw.window), status, glGetString(GL_VERSION));
            if (previousFramebuffer && GLuint(previousFramebuffer) != c.fbo) bind(GL_FRAMEBUFFER, GLuint(previousFramebuffer));
            return false;
        }
        rb.checked = true;
    }
    c.attached = int(s.index); c.attachedGeneration = s.generation;
    if (zcTrace()) std::fprintf(stderr, "SHIRAZ_ZC_ATTACH window=%p context=%p slot=%u gen=%llu size=%dx%d fbo=%u rb=%u image=%p texture=%p\n", static_cast<void*>(draw.window), context, s.index,
                                static_cast<unsigned long long>(s.generation), s.width, s.height, c.fbo, rb.name, image.image, s.texture);
    // The app's own FBO stays bound; framebuffer 0 or the window FBO means the window.
    if (previousFramebuffer && GLuint(previousFramebuffer) != c.fbo) bind(GL_FRAMEBUFFER, GLuint(previousFramebuffer));
    return true;
}
int32_t darwinDequeue(DarwinDraw& draw) {
    int32_t result = shiraz_window_dequeue_slot(draw.window, draw.producer, &draw.slot);
    if (result == 0) { draw.held = true; draw.waitPending = true; }
    return result;
}
// Deletes this context's zero-copy names (current context) and forgets them.
void darwinDropContextNames(DarwinDraw& draw, EGLContext context, bool current) {
    auto found = draw.contexts.find(context);
    if (found == draw.contexts.end()) return;
    std::vector<std::pair<bool, GLuint>> names;
    ZcContext& c = found->second;
    if (c.fbo) names.push_back({true, c.fbo});
    if (c.depth) names.push_back({false, c.depth});
    for (auto& rb : c.rbs) if (rb.name) names.push_back({false, rb.name});
    draw.contexts.erase(found);
    if (current) {
        for (auto& name : names) {
            if (name.first) glDeleteFramebuffers(1, &name.second);
            else glDeleteRenderbuffers(1, &name.second);
        }
    } else {
        std::lock_guard<std::mutex> guard(orphanMutex);
        auto& list = darwinOrphans[context];
        list.insert(list.end(), names.begin(), names.end());
    }
}
// makeCurrent early-out: -1 = not a zero-copy window (the readback path continues).
int darwinMakeCurrent(EGLDisplay display, EGLSurface surface, EGLSurface read, EGLContext context,
                      EGLBoolean (*real)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)) {
    auto draw = darwinLookup(surface);
    if (!draw || context == EGL_NO_CONTEXT) return -1;
    if (read != surface) return EGL_FALSE;
    if (!real(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) return EGL_FALSE;
    currentContext = context;
    darwinProcessOrphans(context);
    std::lock_guard<std::mutex> guard(draw->mutex);
    if (draw->mode == DarwinDraw::UNDECIDED) {
        const char* reason = darwinDecide(*draw);
        draw->mode = reason ? DarwinDraw::READBACK : DarwinDraw::ZERO_COPY;
        darwinLogMode(*draw, reason);
    }
    if (draw->mode != DarwinDraw::ZERO_COPY) return -1;
    if (!draw->held) {
        int32_t result = darwinDequeue(*draw);
        if (result == -EPIPE) { shimError = EGL_BAD_SURFACE; return EGL_FALSE; }
        if (result) { shimError = EGL_BAD_ALLOC; return EGL_FALSE; }
    }
    if (!darwinAttach(*draw, context)) {
        shiraz_zero_copy_fatal("attach-failed");
        shimError = EGL_BAD_ALLOC; return EGL_FALSE;
    }
    ZcContext& c = draw->contexts[context];
    if (auto bind = zcBind()) bind(GL_FRAMEBUFFER, c.fbo);
    currentFramebuffer = c.fbo;
    currentWindow = surface;
    return EGL_TRUE;
}
// glBindFramebuffer(0 or window FBO) early-out: binds the current slot's FBO only (C5).
bool darwinBindWindow(GLenum target, GLuint framebuffer, void (*bind)(GLenum, GLuint)) {
    if (currentWindow == EGL_NO_SURFACE || (framebuffer != 0 && framebuffer != currentFramebuffer)) return false;
    auto draw = darwinLookup(currentWindow);
    if (!draw) return false;
    GLuint fbo = 0;
    {
        std::lock_guard<std::mutex> guard(draw->mutex);
        if (draw->mode != DarwinDraw::ZERO_COPY) return false;
        auto found = draw->contexts.find(currentContext);
        if (found == draw->contexts.end() || !found->second.fbo) return false;
        fbo = found->second.fbo;
    }
    bind(target, fbo);
    currentFramebuffer = fbo;
    return true;
}
// ZeroCopy -> Readback (window exported or given a frame sink mid-stream): the queued
// frame is materialised by the CPU reader; later frames take the readback path.
void darwinSwitchToReadback(DarwinDraw& draw, const char* reason) {
    if (draw.held) { shiraz_window_cancel_slot(draw.window, draw.producer, draw.slot.index); draw.held = false; }
    shiraz_window_disconnect_producer(draw.window, draw.producer);
    std::vector<EGLContext> owned;
    for (auto& entry : draw.contexts) owned.push_back(entry.first);
    for (EGLContext context : owned) darwinDropContextNames(draw, context, context == currentContext);
    draw.mode = DarwinDraw::READBACK;
    currentFramebuffer = 0;
    darwinLogMode(draw, reason);
}
int darwinSwapLocked(EGLSurface surface, DarwinDraw* draw, bool* switched);
// swapBuffers early-out: -1 = not a zero-copy window.
int darwinSwap(EGLSurface surface) {
    auto draw = darwinLookup(surface);  // held for the whole call (C8)
    if (!draw) return -1;
    bool switched = false;
    int result;
    { std::lock_guard<std::mutex> guard(draw->mutex); result = darwinSwapLocked(surface, draw.get(), &switched); }
    if (switched) {
        // The readback FBO must be rebuilt with its own colour renderbuffer.
        std::lock_guard<std::mutex> guard(drawsMutex);
        auto found = draws.find(surface);
        if (found != draws.end()) { Draw* readback = found->second; readback->framebuffers.clear(); readback->color = readback->depth = 0; readback->owner = EGL_NO_CONTEXT; }
    }
    return result;
}
int darwinSwapLocked(EGLSurface surface, DarwinDraw* draw, bool* switched) {
    (void)surface;
    if (draw->mode != DarwinDraw::ZERO_COPY) return -1;
    const auto started = std::chrono::steady_clock::now();
    if (!draw->held) {
        int32_t result = darwinDequeue(*draw);
        if (result == -EPIPE) { shimError = EGL_BAD_SURFACE; return EGL_FALSE; }
        if (result || !darwinAttach(*draw, currentContext)) { shimError = EGL_BAD_ALLOC; return EGL_FALSE; }
        return EGL_TRUE;  // nothing was drawn into a slot this frame
    }
    const ZcProcs& p = zcProcs();
    const uint64_t value = shiraz_window_next_value(draw->window, SHIRAZ_EVENT_FRAME);
    uint64_t serial = 0;
    void* event = shiraz_window_event(draw->window, SHIRAZ_EVENT_FRAME, &serial);
    std::vector<EGLAttrib> attributes;
    if (event) { attributes.push_back(EGL_SYNC_METAL_SHARED_EVENT_OBJECT_ANGLE); attributes.push_back(reinterpret_cast<EGLAttrib>(event)); }
    attributes.insert(attributes.end(), {EGL_SYNC_METAL_SHARED_EVENT_SIGNAL_VALUE_LO_ANGLE, EGLAttrib(value & 0xffffffffu),
                                         EGL_SYNC_METAL_SHARED_EVENT_SIGNAL_VALUE_HI_ANGLE, EGLAttrib(value >> 32), EGL_NONE});
    EGLSync sync = p.createSync(draw->display, EGL_SYNC_METAL_SHARED_EVENT_ANGLE, attributes.data());
    if (sync == EGL_NO_SYNC) {
        std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY frame-signal-failed window=%p egl=%x\n", static_cast<void*>(draw->window), eglGetError());
        shiraz_zero_copy_fatal("frame-signal-failed");
        shimError = EGL_BAD_ALLOC; return EGL_FALSE;
    }
    if (!event) {
        // First frame: ANGLE made the event; adopt it before any waiter can see VALUE (section 3.3).
        void* created = p.copyEvent(draw->display, sync);
        void* owned = shiraz_window_adopt_event(draw->window, SHIRAZ_EVENT_FRAME, created, &serial);
        if (owned != created) { std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY frame-event-race window=%p\n", static_cast<void*>(draw->window)); shiraz_zero_copy_fatal("frame-event-race"); }
    }
    glFlush();  // mandatory: the signal is committed only by a flush (angle-probe 100/100 timeouts without)
    p.destroySync(draw->display, sync);
    ShirazWindowSlot next{};
    int32_t result = shiraz_window_queue_and_dequeue(draw->window, draw->producer, draw->slot.index, draw->slot.generation,
                                                     draw->slot.width, draw->slot.height, value, &next);
    shiraz_zero_copy_count("swaps", 1);
    if (result == -EPIPE) { draw->held = false; shimError = EGL_BAD_SURFACE; return EGL_FALSE; }
    if (result == -EWOULDBLOCK) {
        // CUR was not queued and stays attached: the frame is dropped (pin_drops counted by the queue).
    } else if (result) {
        std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY queue-failed window=%p error=%d\n", static_cast<void*>(draw->window), result);
        draw->held = false; shimError = EGL_BAD_ALLOC; return EGL_FALSE;
    } else {
        if (zcTrace()) std::fprintf(stderr, "SHIRAZ_ZC_QUEUE window=%p rev=%llu v=%llu t_ns=%llu\n", static_cast<void*>(draw->window),
                                    static_cast<unsigned long long>(shiraz_window_revision(draw->window)), static_cast<unsigned long long>(value),
                                    static_cast<unsigned long long>(zcNowNs()));
        draw->slot = next; draw->waitPending = true;
        const char* why = nullptr;
        if (shiraz_window_cpu_frames_required(draw->window, &why)) {
            darwinSwitchToReadback(*draw, why);
            *switched = true;
            return EGL_TRUE;
        }
        if (!darwinAttach(*draw, currentContext)) { shiraz_zero_copy_fatal("attach-failed"); shimError = EGL_BAD_ALLOC; return EGL_FALSE; }
    }
    const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count();
    shiraz_zero_copy_sample("swap_ms", ms);
    shiraz_zero_copy_sample("gl_blocked_ms", ms);
    return EGL_TRUE;
}
// destroySurface early-out (C8): false = not a Shiraz zero-copy-capable window.
bool darwinDestroySurface(EGLSurface surface) {
    std::shared_ptr<DarwinDraw> draw;
    Draw* found = nullptr;
    {
        std::lock_guard<std::mutex> guard(drawsMutex);
        auto entry = darwinDraws.find(surface);
        if (entry == darwinDraws.end()) return false;
        draw = entry->second; darwinDraws.erase(entry);
        auto old = draws.find(surface);
        if (old != draws.end()) { found = old->second; draws.erase(old); }
    }
    {
        std::lock_guard<std::mutex> guard(draw->mutex);
        if (draw->held) { shiraz_window_cancel_slot(draw->window, draw->producer, draw->slot.index); draw->held = false; }
        shiraz_window_disconnect_producer(draw->window, draw->producer);
        std::vector<EGLContext> owned;
        for (auto& entry : draw->contexts) owned.push_back(entry.first);
        for (EGLContext context : owned) darwinDropContextNames(*draw, context, context == currentContext && currentContext != EGL_NO_CONTEXT);
    }
    if (found) {
        // The readback resources, as the Linux path deletes them.
        if (currentContext != EGL_NO_CONTEXT && found->owner == currentContext) {
            auto own = found->framebuffers.find(currentContext);
            if (own != found->framebuffers.end()) glDeleteFramebuffers(1, &own->second);
            if (found->color) glDeleteRenderbuffers(1, &found->color);
            if (found->depth) glDeleteRenderbuffers(1, &found->depth);
        }
        ANativeWindow_release(found->window);
        delete found;
    }
    if (currentWindow == surface) { currentWindow = EGL_NO_SURFACE; currentFramebuffer = 0; }
    delete reinterpret_cast<char*>(surface);
    return true;  // the last shared_ptr holder destroys the images and the window reference
}
// eglDestroyContext: the context's names die with it.
void darwinZeroCopyForgetContext(EGLContext context) {
    // drawsMutex held by the caller (darwinForgetContext).
    { std::lock_guard<std::mutex> guard(orphanMutex); darwinOrphans.erase(context); }
    for (auto& entry : darwinDraws) {
        std::lock_guard<std::mutex> guard(entry.second->mutex);
        entry.second->contexts.erase(context);
    }
}
