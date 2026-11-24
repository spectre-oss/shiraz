// SPDX-License-Identifier: GPL-3.0-only
// Included by native/egl_shim.cpp on Linux only, inside its anonymous namespace.
// Headers (window_queue.h, gl2ext.h, memory, poll.h, unistd.h) are included at
// file scope in native/egl_shim.cpp.

namespace linux_zc_prod {

constexpr EGLenum kEglSyncNativeFenceAndroid = 0x3144;
constexpr EGLint kEglSyncNativeFenceFdAndroid = 0x3145;

struct ZcContext {
    GLuint fbo = 0, depth = 0;
    int32_t depthWidth = 0, depthHeight = 0;
    struct Tex {
        uint64_t generation = 0;
        GLuint name = 0;
        bool checked = false;
    } textures[SHIRAZ_WINDOW_MAX_SLOTS];
    int attached = -1;
    uint64_t attachedGeneration = 0;
};

struct LinuxDraw {
    std::mutex mutex;
    ANativeWindow* window = nullptr; // +1 own reference
    EGLDisplay display = EGL_NO_DISPLAY;
    uint64_t producer = 0;
    enum Mode { UNDECIDED, ZERO_COPY, READBACK } mode = UNDECIDED;
    bool held = false, waitPending = false, hasNativeFence = false;
    ShirazWindowSlot slot{};
    struct Img {
        uint64_t generation = 0;
        EGLImageKHR image = EGL_NO_IMAGE_KHR;
    } images[SHIRAZ_WINDOW_MAX_SLOTS];
    std::unordered_map<EGLContext, ZcContext> contexts;
    ~LinuxDraw();
};

std::unordered_map<EGLSurface, std::shared_ptr<LinuxDraw>> linuxDraws;
std::mutex linuxOrphanMutex;
// (isFramebuffer, isTexture, name)
struct OrphanName {
    enum Kind { FBO, RB, TEX } kind;
    GLuint name;
};
std::unordered_map<EGLContext, std::vector<OrphanName>> linuxOrphans;

struct ZcProcs {
    PFNEGLCREATEIMAGEKHRPROC createImage = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC destroyImage = nullptr;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC imageTargetTexture = nullptr;
    PFNEGLCREATESYNCKHRPROC createSync = nullptr;
    PFNEGLDESTROYSYNCKHRPROC destroySync = nullptr;
    PFNEGLWAITSYNCKHRPROC waitSync = nullptr;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dupFenceFd = nullptr;
    PFNEGLQUERYSTRINGPROC queryString = nullptr;

    bool complete() const {
        return createImage && destroyImage && imageTargetTexture && queryString;
    }
    bool fenceReady() const {
        return createSync && destroySync && waitSync && dupFenceFd;
    }
};

const ZcProcs& zcProcs() {
    static const ZcProcs procs = [] {
        ZcProcs p;
        Proc proc = realProc();
        if (!proc)
            return p;
        p.createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(proc("eglCreateImageKHR"));
        p.destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(proc("eglDestroyImageKHR"));
        p.imageTargetTexture = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
            proc("glEGLImageTargetTexture2DOES"));
        p.createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(proc("eglCreateSyncKHR"));
        p.destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(proc("eglDestroySyncKHR"));
        p.waitSync = reinterpret_cast<PFNEGLWAITSYNCKHRPROC>(proc("eglWaitSyncKHR"));
        p.dupFenceFd =
            reinterpret_cast<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>(proc("eglDupNativeFenceFDANDROID"));
        p.queryString = reinterpret_cast<PFNEGLQUERYSTRINGPROC>(symbol("eglQueryString"));
        return p;
    }();
    return procs;
}

LinuxDraw::~LinuxDraw() {
    if (slot.fence_fd >= 0) {
        close(slot.fence_fd);
        slot.fence_fd = -1;
    }
    const ZcProcs& p = zcProcs();
    for (auto& image : images) {
        if (image.image != EGL_NO_IMAGE_KHR && p.destroyImage)
            p.destroyImage(display, image.image);
    }
    if (window)
        ANativeWindow_release(window);
}

bool zcTrace() {
    static const bool enabled = [] {
        const char* v = std::getenv("SHIRAZ_TRACE_ZERO_COPY");
        return v && std::strcmp(v, "1") == 0;
    }();
    return enabled;
}

uint64_t zcNowNs() {
    return uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::steady_clock::now().time_since_epoch())
                        .count());
}

bool hasExtension(const char* list, const char* name) {
    if (!list)
        return false;
    const size_t length = std::strlen(name);
    for (const char* at = std::strstr(list, name); at; at = std::strstr(at + 1, name)) {
        if ((at == list || at[-1] == ' ') && (at[length] == ' ' || at[length] == 0))
            return true;
    }
    return false;
}

bool gbmLinearSupported() {
    static const bool supported = [] {
        AHardwareBuffer_Desc d{};
        d.width = 4;
        d.height = 4;
        d.layers = 1;
        d.format = 1;
        d.usage = 0x303;
        return AHardwareBuffer_isSupported(&d) != 0;
    }();
    return supported;
}

std::shared_ptr<LinuxDraw> lookup(EGLSurface surface) {
    std::lock_guard<std::mutex> guard(drawsMutex);
    auto found = linuxDraws.find(surface);
    return found == linuxDraws.end() ? nullptr : found->second;
}

// Called with drawsMutex held.
void registerSurface(EGLSurface surface, ANativeWindow* window, EGLDisplay display) {
    auto draw = std::make_shared<LinuxDraw>();
    ANativeWindow_acquire(window);
    draw->window = window;
    draw->display = display;
    draw->producer = reinterpret_cast<uint64_t>(surface);
    linuxDraws[surface] = std::move(draw);
}

void processOrphans(EGLContext context) {
    std::vector<OrphanName> names;
    {
        std::lock_guard<std::mutex> guard(linuxOrphanMutex);
        auto found = linuxOrphans.find(context);
        if (found == linuxOrphans.end())
            return;
        names.swap(found->second);
        linuxOrphans.erase(found);
    }
    for (auto& n : names) {
        if (n.kind == OrphanName::FBO)
            glDeleteFramebuffers(1, &n.name);
        else if (n.kind == OrphanName::RB)
            glDeleteRenderbuffers(1, &n.name);
        else
            glDeleteTextures(1, &n.name);
    }
}

void dropContextNames(LinuxDraw& draw, EGLContext context, bool isCurrent) {
    auto found = draw.contexts.find(context);
    if (found == draw.contexts.end())
        return;
    std::vector<OrphanName> names;
    ZcContext& c = found->second;
    if (c.fbo)
        names.push_back({OrphanName::FBO, c.fbo});
    if (c.depth)
        names.push_back({OrphanName::RB, c.depth});
    for (auto& t : c.textures) {
        if (t.name)
            names.push_back({OrphanName::TEX, t.name});
    }
    draw.contexts.erase(found);
    if (isCurrent) {
        for (auto& n : names) {
            if (n.kind == OrphanName::FBO)
                glDeleteFramebuffers(1, &n.name);
            else if (n.kind == OrphanName::RB)
                glDeleteRenderbuffers(1, &n.name);
            else
                glDeleteTextures(1, &n.name);
        }
    } else {
        std::lock_guard<std::mutex> guard(linuxOrphanMutex);
        auto& list = linuxOrphans[context];
        list.insert(list.end(), names.begin(), names.end());
    }
}

const char* decide(LinuxDraw& draw) {
    if (shiraz_zero_copy_mode() == 0)
        return "env";
    const char* why = nullptr;
    if (shiraz_window_cpu_frames_required(draw.window, &why))
        return why;
    if (!gbmLinearSupported())
        return "no-gbm-dmabuf";
    const ZcProcs& p = zcProcs();
    if (!p.complete())
        return "missing-procs";
    const char* extensions = p.queryString(draw.display, EGL_EXTENSIONS);
    if (!hasExtension(extensions, "EGL_EXT_image_dma_buf_import"))
        return "missing-egl-dmabuf";
    const char* glExts = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    if (!hasExtension(glExts, "GL_OES_EGL_image"))
        return "missing-gl-oes-egl-image";
    draw.hasNativeFence = p.fenceReady() &&
                          hasExtension(extensions, "EGL_ANDROID_native_fence_sync") &&
                          hasExtension(extensions, "EGL_KHR_wait_sync");
    int32_t connected = shiraz_window_connect_producer(draw.window, draw.producer);
    if (connected == -EBUSY)
        return "producer-busy";
    if (connected)
        return "abandoned";
    return nullptr;
}

void logMode(const LinuxDraw& draw, const char* reason) {
    if (zcTrace() || shiraz_zero_copy_mode() == 2) {
        std::fprintf(stderr,
                     "SHIRAZ_EGL_ZERO_COPY window=%p mode=%s reason=%s fence=%d format=ABGR8888\n",
                     static_cast<void*>(draw.window), reason ? "readback" : "dmabuf",
                     reason ? reason : "ok", draw.hasNativeFence ? 1 : 0);
    }
    if (reason && std::strcmp(reason, "export") && std::strcmp(reason, "frame-sink") &&
        std::strcmp(reason, "offscreen")) {
        char fatal[96];
        std::snprintf(fatal, sizeof fatal, "readback-%s", reason);
        shiraz_zero_copy_fatal(fatal);
    }
}

void releaseWait(LinuxDraw& draw) {
    if (!draw.waitPending)
        return;
    draw.waitPending = false;
    int32_t fd = draw.slot.fence_fd;
    draw.slot.fence_fd = -1;
    if (fd < 0) {
        shiraz_zero_copy_count("release_waits_skipped", 1);
        return;
    }
    const ZcProcs& p = zcProcs();
    if (draw.hasNativeFence) {
        const EGLint attribs[] = {kEglSyncNativeFenceFdAndroid, fd, EGL_NONE};
        EGLSyncKHR sync = p.createSync(draw.display, kEglSyncNativeFenceAndroid, attribs);
        if (sync != EGL_NO_SYNC_KHR) {
            // EGL_ANDROID_native_fence_sync takes ownership of fd on success.
            p.waitSync(draw.display, sync, 0);
            p.destroySync(draw.display, sync);
            shiraz_zero_copy_count("release_waits_gpu", 1);
            return;
        }
    }
    pollfd pf{fd, POLLIN, 0};
    while (poll(&pf, 1, 1000) < 0 && errno == EINTR) {
    }
    close(fd);
    shiraz_zero_copy_count("release_waits_cpu", 1);
}

int32_t dequeue(LinuxDraw& draw) {
    if (draw.slot.fence_fd >= 0) {
        close(draw.slot.fence_fd);
        draw.slot.fence_fd = -1;
    }
    int32_t result = shiraz_window_dequeue_slot(draw.window, draw.producer, &draw.slot);
    if (result == 0) {
        draw.held = true;
        draw.waitPending = true;
    }
    return result;
}

bool attach(LinuxDraw& draw, EGLContext context) {
    const ZcProcs& p = zcProcs();
    const ShirazWindowSlot& s = draw.slot;
    ZcContext& c = draw.contexts[context];
    releaseWait(draw);
    if (c.fbo && c.attached == int(s.index) && c.attachedGeneration == s.generation)
        return true;

    auto& image = draw.images[s.index];
    if (image.generation != s.generation) {
        if (image.image != EGL_NO_IMAGE_KHR) {
            p.destroyImage(draw.display, image.image);
            image.image = EGL_NO_IMAGE_KHR;
        }
        uint32_t fourcc = 0, pitch = 0;
        int dmaFd = shiraz_hardware_buffer_export(s.buffer, &fourcc, &pitch);
        if (dmaFd < 0)
            return false;
        const EGLint dmaAttribs[] = {EGL_WIDTH,
                                     EGLint(s.width),
                                     EGL_HEIGHT,
                                     EGLint(s.height),
                                     EGL_LINUX_DRM_FOURCC_EXT,
                                     EGLint(fourcc),
                                     EGL_DMA_BUF_PLANE0_FD_EXT,
                                     dmaFd,
                                     EGL_DMA_BUF_PLANE0_OFFSET_EXT,
                                     0,
                                     EGL_DMA_BUF_PLANE0_PITCH_EXT,
                                     EGLint(pitch),
                                     EGL_NONE};
        image.image =
            p.createImage(draw.display, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, dmaAttribs);
        close(dmaFd);
        image.generation = image.image == EGL_NO_IMAGE_KHR ? 0 : s.generation;
        if (image.image == EGL_NO_IMAGE_KHR) {
            if (zcTrace())
                std::fprintf(stderr, "SHIRAZ_EGL_ZERO_COPY image-failed window=%p egl=%x\n",
                             static_cast<void*>(draw.window), eglGetError());
            return false;
        }
    }

    GLint prevTex = 0, prevRb = 0, prevFbo = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTex);
    glGetIntegerv(GL_RENDERBUFFER_BINDING, &prevRb);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);

    auto& tex = c.textures[s.index];
    if (tex.generation != s.generation) {
        if (!tex.name)
            glGenTextures(1, &tex.name);
        glBindTexture(GL_TEXTURE_2D, tex.name);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        p.imageTargetTexture(GL_TEXTURE_2D, image.image);
        tex.generation = s.generation;
        tex.checked = false;
    }
    glBindTexture(GL_TEXTURE_2D, static_cast<GLuint>(prevTex));

    bool depthChanged = false;
    if (!c.depth || c.depthWidth != s.width || c.depthHeight != s.height) {
        if (!c.depth)
            glGenRenderbuffers(1, &c.depth);
        glBindRenderbuffer(GL_RENDERBUFFER, c.depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, s.width, s.height);
        c.depthWidth = s.width;
        c.depthHeight = s.height;
        depthChanged = true;
    }
    glBindRenderbuffer(GL_RENDERBUFFER, static_cast<GLuint>(prevRb));

    BindFramebuffer bind = realBindFramebuffer();
    if (!bind)
        return false;
    const bool fresh = !c.fbo;
    if (fresh)
        glGenFramebuffers(1, &c.fbo);
    bind(GL_FRAMEBUFFER, c.fbo);
    if (fresh || depthChanged) {
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, c.depth);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, c.depth);
    }
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex.name, 0);
    if (!tex.checked || depthChanged) {
        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            if (zcTrace())
                std::fprintf(stderr,
                             "SHIRAZ_EGL_ZERO_COPY fbo-incomplete window=%p status=%x version=%s\n",
                             static_cast<void*>(draw.window), status, glGetString(GL_VERSION));
            if (prevFbo && GLuint(prevFbo) != c.fbo)
                bind(GL_FRAMEBUFFER, GLuint(prevFbo));
            return false;
        }
        tex.checked = true;
    }
    c.attached = int(s.index);
    c.attachedGeneration = s.generation;
    if (zcTrace()) {
        std::fprintf(stderr,
                     "SHIRAZ_ZC_ATTACH window=%p context=%p slot=%u gen=%llu size=%dx%d fbo=%u "
                     "tex=%u image=%p\n",
                     static_cast<void*>(draw.window), context, s.index,
                     static_cast<unsigned long long>(s.generation), s.width, s.height, c.fbo,
                     tex.name, image.image);
    }
    if (prevFbo && GLuint(prevFbo) != c.fbo)
        bind(GL_FRAMEBUFFER, GLuint(prevFbo));
    return true;
}

void switchToReadback(LinuxDraw& draw, const char* reason) {
    if (draw.held) {
        if (draw.slot.fence_fd >= 0) {
            close(draw.slot.fence_fd);
            draw.slot.fence_fd = -1;
        }
        shiraz_window_cancel_slot(draw.window, draw.producer, draw.slot.index);
        draw.held = false;
    }
    shiraz_window_disconnect_producer(draw.window, draw.producer);
    std::vector<EGLContext> owned;
    for (auto& entry : draw.contexts)
        owned.push_back(entry.first);
    for (EGLContext ctx : owned)
        dropContextNames(draw, ctx, ctx == currentContext && currentContext != EGL_NO_CONTEXT);
    draw.mode = LinuxDraw::READBACK;
    currentFramebuffer = 0;
    logMode(draw, reason);
}

int makeCurrent(EGLDisplay display, EGLSurface surface, EGLSurface read, EGLContext context,
                EGLBoolean (*real)(EGLDisplay, EGLSurface, EGLSurface, EGLContext)) {
    auto draw = lookup(surface);
    if (!draw || context == EGL_NO_CONTEXT)
        return -1;
    if (read != surface)
        return EGL_FALSE;
    if (!real(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context))
        return EGL_FALSE;
    currentContext = context;
    processOrphans(context);
    std::lock_guard<std::mutex> guard(draw->mutex);
    if (draw->mode == LinuxDraw::UNDECIDED) {
        const char* reason = decide(*draw);
        draw->mode = reason ? LinuxDraw::READBACK : LinuxDraw::ZERO_COPY;
        logMode(*draw, reason);
    }
    if (draw->mode != LinuxDraw::ZERO_COPY)
        return -1;
    if (!draw->held) {
        int32_t result = dequeue(*draw);
        if (result == -EPIPE) {
            shimError = EGL_BAD_SURFACE;
            return EGL_FALSE;
        }
        if (result) {
            // If GBM allocation failed at runtime (e.g. driver refused linear BO), degrade to
            // readback cleanly unless SHIRAZ_EGL_ZERO_COPY=require.
            switchToReadback(*draw, "dequeue-failed");
            return -1;
        }
    }
    if (!attach(*draw, context)) {
        switchToReadback(*draw, "attach-failed");
        return -1;
    }
    ZcContext& c = draw->contexts[context];
    if (BindFramebuffer bind = realBindFramebuffer())
        bind(GL_FRAMEBUFFER, c.fbo);
    currentFramebuffer = c.fbo;
    currentWindow = surface;
    return EGL_TRUE;
}

bool bindWindow(GLenum target, GLuint framebuffer, BindFramebuffer bind) {
    if (currentWindow == EGL_NO_SURFACE || (framebuffer != 0 && framebuffer != currentFramebuffer))
        return false;
    auto draw = lookup(currentWindow);
    if (!draw)
        return false;
    GLuint fbo = 0;
    {
        std::lock_guard<std::mutex> guard(draw->mutex);
        if (draw->mode != LinuxDraw::ZERO_COPY)
            return false;
        // If ANativeWindow geometry changed before the app drew, immediately re-dequeue
        // a matching slot so the frame renders directly at the new size with zero drops.
        const int32_t winW = ANativeWindow_getWidth(draw->window);
        const int32_t winH = ANativeWindow_getHeight(draw->window);
        if (draw->held && winW > 0 && winH > 0 &&
            (draw->slot.width != winW || draw->slot.height != winH)) {
            if (draw->slot.fence_fd >= 0) {
                close(draw->slot.fence_fd);
                draw->slot.fence_fd = -1;
            }
            shiraz_window_cancel_slot(draw->window, draw->producer, draw->slot.index);
            draw->held = false;
            if (dequeue(*draw) == 0)
                attach(*draw, currentContext);
        }
        auto found = draw->contexts.find(currentContext);
        if (found == draw->contexts.end() || !found->second.fbo)
            return false;
        fbo = found->second.fbo;
    }
    bind(target, fbo);
    currentFramebuffer = fbo;
    return true;
}

int swapLocked(LinuxDraw* draw, bool* switched) {
    if (draw->mode != LinuxDraw::ZERO_COPY)
        return -1;
    const auto started = std::chrono::steady_clock::now();
    if (!draw->held) {
        int32_t result = dequeue(*draw);
        if (result == -EPIPE) {
            shimError = EGL_BAD_SURFACE;
            return EGL_FALSE;
        }
        if (result || !attach(*draw, currentContext)) {
            shimError = EGL_BAD_ALLOC;
            return EGL_FALSE;
        }
        return EGL_TRUE;
    }

    int32_t frameFenceFd = -1;
    const ZcProcs& p = zcProcs();
    if (draw->hasNativeFence) {
        EGLSyncKHR sync = p.createSync(draw->display, kEglSyncNativeFenceAndroid, nullptr);
        if (sync != EGL_NO_SYNC_KHR) {
            glFlush();
            frameFenceFd = p.dupFenceFd(draw->display, sync);
            p.destroySync(draw->display, sync);
        }
    }
    if (frameFenceFd < 0) {
        // Driver lacks native fence sync or fence export failed: complete GPU work before queue.
        glFinish();
    }

    ShirazWindowSlot next{};
    int32_t result = shiraz_window_queue_and_dequeue(draw->window, draw->producer, draw->slot.index,
                                                     draw->slot.generation, draw->slot.width,
                                                     draw->slot.height, frameFenceFd, &next);
    shiraz_zero_copy_count("swaps", 1);
    if (result == -EPIPE) {
        draw->held = false;
        shimError = EGL_BAD_SURFACE;
        return EGL_FALSE;
    }
    if (result == -EWOULDBLOCK) {
        // Slot stays DEQUEUED and attached; frame dropped under pin timeout.
    } else if (result) {
        draw->held = false;
        shimError = EGL_BAD_ALLOC;
        return EGL_FALSE;
    } else {
        if (zcTrace()) {
            std::fprintf(stderr, "SHIRAZ_ZC_QUEUE window=%p rev=%llu fence=%d t_ns=%llu\n",
                         static_cast<void*>(draw->window),
                         static_cast<unsigned long long>(shiraz_window_revision(draw->window)),
                         frameFenceFd >= 0 ? 1 : 0, static_cast<unsigned long long>(zcNowNs()));
        }
        draw->slot = next;
        draw->waitPending = true;
        const char* why = nullptr;
        if (shiraz_window_cpu_frames_required(draw->window, &why)) {
            switchToReadback(*draw, why);
            *switched = true;
            return EGL_TRUE;
        }
        if (!attach(*draw, currentContext)) {
            switchToReadback(*draw, "attach-failed");
            *switched = true;
            return EGL_TRUE;
        }
    }
    const double ms =
        std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started)
            .count();
    shiraz_zero_copy_sample("swap_ms", ms);
    shiraz_zero_copy_sample("gl_blocked_ms", ms);
    return EGL_TRUE;
}

int swap(EGLSurface surface) {
    auto draw = lookup(surface);
    if (!draw)
        return -1;
    bool switched = false;
    int result = -1;
    {
        std::lock_guard<std::mutex> guard(draw->mutex);
        result = swapLocked(draw.get(), &switched);
    }
    if (switched) {
        std::lock_guard<std::mutex> guard(drawsMutex);
        auto found = draws.find(surface);
        if (found != draws.end()) {
            Draw* readback = found->second;
            readback->framebuffers.clear();
            readback->color = readback->depth = 0;
        }
    }
    return result;
}

bool destroySurface(EGLSurface surface) {
    std::shared_ptr<LinuxDraw> draw;
    Draw* found = nullptr;
    {
        std::lock_guard<std::mutex> guard(drawsMutex);
        auto entry = linuxDraws.find(surface);
        if (entry == linuxDraws.end())
            return false;
        draw = entry->second;
        linuxDraws.erase(entry);
        auto old = draws.find(surface);
        if (old != draws.end()) {
            found = old->second;
            draws.erase(old);
        }
    }
    {
        std::lock_guard<std::mutex> guard(draw->mutex);
        if (draw->held) {
            if (draw->slot.fence_fd >= 0) {
                close(draw->slot.fence_fd);
                draw->slot.fence_fd = -1;
            }
            shiraz_window_cancel_slot(draw->window, draw->producer, draw->slot.index);
            draw->held = false;
        }
        shiraz_window_disconnect_producer(draw->window, draw->producer);
        std::vector<EGLContext> owned;
        for (auto& entry : draw->contexts)
            owned.push_back(entry.first);
        for (EGLContext ctx : owned)
            dropContextNames(*draw, ctx, ctx == currentContext && currentContext != EGL_NO_CONTEXT);
    }
    if (found) {
        if (currentContext != EGL_NO_CONTEXT) {
            auto own = found->framebuffers.find(currentContext);
            if (own != found->framebuffers.end())
                glDeleteFramebuffers(1, &own->second);
            if (found->color)
                glDeleteRenderbuffers(1, &found->color);
            if (found->depth)
                glDeleteRenderbuffers(1, &found->depth);
        }
        ANativeWindow_release(found->window);
        delete found;
    }
    if (currentWindow == surface) {
        currentWindow = EGL_NO_SURFACE;
        currentFramebuffer = 0;
    }
    delete reinterpret_cast<char*>(surface);
    return true;
}

void forgetContext(EGLContext context) {
    {
        std::lock_guard<std::mutex> guard(linuxOrphanMutex);
        linuxOrphans.erase(context);
    }
    std::lock_guard<std::mutex> guard(drawsMutex);
    for (auto& entry : linuxDraws) {
        std::lock_guard<std::mutex> dguard(entry.second->mutex);
        entry.second->contexts.erase(context);
    }
}

} // namespace linux_zc_prod
