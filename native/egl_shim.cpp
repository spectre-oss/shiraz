// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include "shiraz/hardware_buffer.h"
#include <unistd.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <chrono>
#include <cerrno>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
thread_local EGLint shimError = EGL_SUCCESS;
std::atomic<EGLDisplay> adapterDisplay{EGL_NO_DISPLAY};
EGLClientBuffer nativeClientBuffer(const AHardwareBuffer* buffer) {
    if (!buffer) { shimError = EGL_BAD_PARAMETER; return nullptr; }
    return reinterpret_cast<EGLClientBuffer>(const_cast<AHardwareBuffer*>(buffer));
}

void* mesa() {
    static void* const handle=[]() -> void* {
        void* library=nullptr;
        if(const char* path=std::getenv("SHIRAZ_EGL_LIBRARY"))library=dlopen(path,RTLD_NOW|RTLD_LOCAL);
        if(!library)library=dlopen("libEGL_mesa_host.so.1",RTLD_NOW|RTLD_LOCAL);
        if(!library)library=dlopen("/usr/lib/libEGL.so.1",RTLD_NOW|RTLD_LOCAL);
        return library;
    }();
    return handle;
}
void* symbol(const char* name) { void* library = mesa(); return library ? dlsym(library, name) : nullptr; }
EGLImageKHR createImage(EGLDisplay display, EGLContext context, EGLenum target, EGLClientBuffer client, const EGLint* attributes) {
    using Fn = EGLImageKHR (*)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint*);
    auto getProc = reinterpret_cast<void* (*)(const char*)>(symbol("eglGetProcAddress"));
    auto real = getProc ? reinterpret_cast<Fn>(getProc("eglCreateImageKHR")) : nullptr;
    if (!real) { shimError = EGL_BAD_ACCESS; return EGL_NO_IMAGE_KHR; }
    if (target != EGL_NATIVE_BUFFER_ANDROID) return real(display, context, target, client, attributes);
    if (!client || context != EGL_NO_CONTEXT) { shimError = EGL_BAD_PARAMETER; return EGL_NO_IMAGE_KHR; }
    // Android native-buffer images accept only the preservation hint.
    if (attributes) for (unsigned i = 0; attributes[i] != EGL_NONE; i += 2) {
        if (i >= 32 || attributes[i] != EGL_IMAGE_PRESERVED_KHR ||
            (attributes[i + 1] != EGL_TRUE && attributes[i + 1] != EGL_FALSE)) {
            shimError = EGL_BAD_PARAMETER; return EGL_NO_IMAGE_KHR;
        }
    }
    auto* buffer = reinterpret_cast<AHardwareBuffer*>(client);
    AHardwareBuffer_Desc desc{}; AHardwareBuffer_describe(buffer, &desc);
    uint32_t format = 0, pitch = 0;
    int fd = shiraz_hardware_buffer_export(buffer, &format, &pitch);
    if (fd < 0) { shimError = EGL_BAD_ALLOC; return EGL_NO_IMAGE_KHR; }
    const EGLint dma[] = {EGL_WIDTH, EGLint(desc.width), EGL_HEIGHT, EGLint(desc.height),
        EGL_LINUX_DRM_FOURCC_EXT, EGLint(format), EGL_DMA_BUF_PLANE0_FD_EXT, fd,
        EGL_DMA_BUF_PLANE0_OFFSET_EXT, 0, EGL_DMA_BUF_PLANE0_PITCH_EXT, EGLint(pitch), EGL_NONE};
    EGLImageKHR image = real(display, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, dma);
    close(fd); // EGL retains its own reference on the DMA buffer.
    return image;
}
using Proc = void* (*)(const char*);
Proc realProc() { return reinterpret_cast<Proc>(symbol("eglGetProcAddress")); }
struct Draw {
    ANativeWindow* window; int width, height;
    GLuint color = 0, depth = 0;
    std::unordered_map<EGLContext, GLuint> framebuffers;
    std::vector<uint32_t> read, argb;
    bool rgbaOnly = false;
};
std::mutex drawsMutex;
std::unordered_map<EGLSurface, Draw*> draws;
thread_local EGLSurface currentWindow = EGL_NO_SURFACE;
thread_local EGLContext currentContext = EGL_NO_CONTEXT;
thread_local GLuint currentFramebuffer = 0;
Draw* findDraw(EGLSurface surface) {
    std::lock_guard<std::mutex> guard(drawsMutex);
    auto found = draws.find(surface);
    return found == draws.end() ? nullptr : found->second;
}
using BindFramebuffer = void (*)(GLenum, GLuint);
BindFramebuffer realBindFramebuffer() {
    static BindFramebuffer bind = reinterpret_cast<BindFramebuffer>(dlsym(RTLD_NEXT, "glBindFramebuffer"));
    return bind;
}
bool prepare(Draw& draw, EGLContext context) {
    BindFramebuffer bind = realBindFramebuffer();
    if (!bind) return false;
    GLuint framebuffer = 0;
    {
        std::lock_guard<std::mutex> guard(drawsMutex);
        int width=ANativeWindow_getWidth(draw.window),height=ANativeWindow_getHeight(draw.window);
        if(width<1||height<1)return false;
        if (!draw.color || width!=draw.width || height!=draw.height) {
            GLint previousRenderbuffer=0;glGetIntegerv(GL_RENDERBUFFER_BINDING,&previousRenderbuffer);
            draw.width=width;draw.height=height;
            if(!draw.color)glGenRenderbuffers(1, &draw.color);
            glBindRenderbuffer(GL_RENDERBUFFER, draw.color);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, draw.width, draw.height);
            if(!draw.depth)glGenRenderbuffers(1, &draw.depth);
            glBindRenderbuffer(GL_RENDERBUFFER, draw.depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, draw.width, draw.height);
            glBindRenderbuffer(GL_RENDERBUFFER,static_cast<GLuint>(previousRenderbuffer));
        }
        auto found = draw.framebuffers.find(context);
        if (found != draw.framebuffers.end()) framebuffer = found->second;
    }
    if (!framebuffer) {
        glGenFramebuffers(1, &framebuffer);
        bind(GL_FRAMEBUFFER, framebuffer);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, draw.color);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, draw.depth);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, draw.depth);
        GLenum status=glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            if(std::getenv("SHIRAZ_TRACE_EGL"))std::fprintf(stderr,"Shiraz EGL FBO failed: version=%s status=%x error=%x framebuffer=%u color=%u depth=%u\n",glGetString(GL_VERSION),status,glGetError(),framebuffer,draw.color,draw.depth);
            glDeleteFramebuffers(1, &framebuffer); return false;
        }
        std::lock_guard<std::mutex> guard(drawsMutex);
        draw.framebuffers[context] = framebuffer;
    }
    bind(GL_FRAMEBUFFER, framebuffer);
    currentFramebuffer = framebuffer;
    return true;
}
EGLSurface createWindow(EGLDisplay display, EGLConfig config, EGLNativeWindowType window, const EGLint* attribs) {
    auto* native = reinterpret_cast<ANativeWindow*>(window);
    if (shiraz_window_known(native)) {
        int width = ANativeWindow_getWidth(native), height = ANativeWindow_getHeight(native);
        if (width < 1 || height < 1) return EGL_NO_SURFACE;
        auto* token = new char;
        std::lock_guard<std::mutex> guard(drawsMutex);
        Draw* entry = new Draw();
        entry->window = native; entry->width = width; entry->height = height;
        draws[reinterpret_cast<EGLSurface>(token)] = entry;
        ANativeWindow_acquire(native);
        return reinterpret_cast<EGLSurface>(token);
    }
    Proc proc = realProc();
    using Fn = EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
    auto real = proc ? reinterpret_cast<Fn>(proc("eglCreateWindowSurface")) : nullptr;
    return real ? real(display, config, window, attribs) : EGL_NO_SURFACE;
}
EGLBoolean makeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context) {
    using Fn = EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    auto real = reinterpret_cast<Fn>(symbol("eglMakeCurrent"));
    if (!real) return EGL_FALSE;
    Draw* found = findDraw(draw);
    if (!found) {
        EGLBoolean result = real(display, draw, read, context);
        if (result) { currentWindow = EGL_NO_SURFACE; currentContext = context; currentFramebuffer = 0; }
        return result;
    }
    if (read != draw) return EGL_FALSE;
    if (!real(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) return EGL_FALSE;
    currentContext = context;
    if (!prepare(*found, context)) return EGL_FALSE;
    currentWindow = draw;
    return EGL_TRUE;
}
EGLSurface currentSurface(EGLint api) {
    if (currentWindow != EGL_NO_SURFACE) return currentWindow;
    using Fn = EGLSurface (*)(EGLint);
    auto real = reinterpret_cast<Fn>(symbol("eglGetCurrentSurface"));
    return real ? real(api) : EGL_NO_SURFACE;
}
void traceSwap(std::chrono::steady_clock::time_point started) {
    static const bool enabled = [] { const char* value = std::getenv("SHIRAZ_TRACE_EGL"); return value && std::strcmp(value, "1") == 0; }();
    if (!enabled) return;
    static std::mutex traceMutex;
    static std::chrono::steady_clock::time_point window = std::chrono::steady_clock::now();
    static unsigned swaps = 0;
    static double readback = 0;
    auto now = std::chrono::steady_clock::now();
    std::lock_guard<std::mutex> guard(traceMutex);
    ++swaps;
    readback += std::chrono::duration<double, std::milli>(now - started).count();
    double elapsed = std::chrono::duration<double>(now - window).count();
    if (elapsed < 1.0) return;
    std::fprintf(stderr, "SHIRAZ_EGL_FPS swaps_per_s=%.1f readback_ms=%.2f\n", swaps / elapsed, readback / swaps);
    window = now; swaps = 0; readback = 0;
}
EGLBoolean swapBuffers(EGLDisplay display, EGLSurface surface) {
    Draw* found = findDraw(surface);
    if (!found) {
        using Fn = EGLBoolean (*)(EGLDisplay, EGLSurface);
        auto real = reinterpret_cast<Fn>(symbol("eglSwapBuffers"));
        return real ? real(display, surface) : EGL_FALSE;
    }
    Draw& draw = *found;
    auto started = std::chrono::steady_clock::now();
    if (!prepare(draw, currentContext)) return EGL_FALSE;
    const size_t width = static_cast<size_t>(draw.width), pixels = width * draw.height;
    draw.read.resize(pixels);
    draw.argb.resize(pixels);
    GLint packBuffer = 0, packAlignment = 4;
    glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &packBuffer);
    glGetIntegerv(GL_PACK_ALIGNMENT, &packAlignment);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
    while (glGetError() != GL_NO_ERROR) {}
    GLenum readError = GL_INVALID_OPERATION;
    bool bgra = false;
    if (!draw.rgbaOnly) {
        glReadPixels(0, 0, draw.width, draw.height, 0x80E1, GL_UNSIGNED_BYTE, draw.read.data());
        readError = glGetError();
        bgra = readError == GL_NO_ERROR;
        if (!bgra) draw.rgbaOnly = true;
    }
    if (!bgra) {
        glReadPixels(0, 0, draw.width, draw.height, GL_RGBA, GL_UNSIGNED_BYTE, draw.read.data());
        readError = glGetError();
    }
    glBindBuffer(GL_PIXEL_PACK_BUFFER, static_cast<GLuint>(packBuffer));
    glPixelStorei(GL_PACK_ALIGNMENT, packAlignment);
    if (readError != GL_NO_ERROR) return EGL_FALSE;
    for (size_t y = 0; y < static_cast<size_t>(draw.height); ++y) {
        const uint32_t* source = draw.read.data() + (draw.height - 1 - y) * width;
        uint32_t* target = draw.argb.data() + y * width;
        if (bgra) { std::memcpy(target, source, width * sizeof(uint32_t)); continue; }
        for (size_t x = 0; x < width; ++x) {
            uint32_t value = source[x];
            target[x] = (value & 0xff00ff00u) | ((value & 0xffu) << 16) | ((value >> 16) & 0xffu);
        }
    }
    // A concurrent host resize supersedes this frame; do not reset Gecko's compositor.
    int result=shiraz_window_post_argb(draw.window, draw.argb.data(), draw.argb.size());
    EGLBoolean posted = result==0 || result==-EAGAIN ? EGL_TRUE : EGL_FALSE;
    traceSwap(started);
    return posted;
}
// Damage is a hint: our retained FBO stores the complete image and swapBuffers
// publishes it in full. Never pass the adapter's surface tokens into Mesa.
EGLBoolean setDamageRegion(EGLDisplay display, EGLSurface surface, EGLint* rects, EGLint count) {
    if (findDraw(surface)) {
        if (count < 0 || (count && !rects)) { shimError = EGL_BAD_PARAMETER; return EGL_FALSE; }
        if (surface != currentWindow) { shimError = EGL_BAD_MATCH; return EGL_FALSE; }
        return EGL_TRUE;
    }
    auto proc = realProc();
    auto real = proc ? reinterpret_cast<PFNEGLSETDAMAGEREGIONKHRPROC>(proc("eglSetDamageRegionKHR")) : nullptr;
    if (!real) { shimError = EGL_BAD_ACCESS; return EGL_FALSE; }
    return real(display, surface, rects, count);
}
EGLBoolean swapWithDamage(EGLDisplay display, EGLSurface surface, const EGLint* rects, EGLint count) {
    if (count < 0 || (count && !rects)) { shimError = EGL_BAD_PARAMETER; return EGL_FALSE; }
    // Full swaps are also a valid implementation for native Mesa surfaces.
    return swapBuffers(display, surface);
}
EGLBoolean querySurface(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint* value) {
    Draw* found = findDraw(surface);
    if (!found) {
        using Fn = EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint*);
        auto real = reinterpret_cast<Fn>(symbol("eglQuerySurface"));
        return real ? real(display, surface, attribute, value) : EGL_FALSE;
    }
    if (!value) return EGL_FALSE;
    if (attribute == EGL_SWAP_BEHAVIOR) { *value = EGL_BUFFER_DESTROYED; return EGL_TRUE; }
    if (attribute == EGL_MULTISAMPLE_RESOLVE) { *value = EGL_MULTISAMPLE_RESOLVE_DEFAULT; return EGL_TRUE; }
    if (attribute == EGL_BUFFER_AGE_EXT) { *value = 0; return EGL_TRUE; }
    if (attribute == EGL_WIDTH) { *value = ANativeWindow_getWidth(found->window); return EGL_TRUE; }
    if (attribute == EGL_HEIGHT) { *value = ANativeWindow_getHeight(found->window); return EGL_TRUE; }
    return EGL_FALSE;
}
EGLBoolean surfaceAttrib(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint value) {
    if (!findDraw(surface)) {
        using Fn = EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint);
        auto real = reinterpret_cast<Fn>(symbol("eglSurfaceAttrib"));
        return real ? real(display, surface, attribute, value) : EGL_FALSE;
    }
    if ((attribute == EGL_SWAP_BEHAVIOR && value == EGL_BUFFER_DESTROYED) ||
        (attribute == EGL_MULTISAMPLE_RESOLVE && value == EGL_MULTISAMPLE_RESOLVE_DEFAULT)) return EGL_TRUE;
    shimError = (attribute == EGL_SWAP_BEHAVIOR || attribute == EGL_MULTISAMPLE_RESOLVE) ? EGL_BAD_MATCH : EGL_BAD_ATTRIBUTE;
    return EGL_FALSE;
}
EGLBoolean destroySurface(EGLDisplay display, EGLSurface surface) {
    Draw* found = nullptr;
    {
        std::lock_guard<std::mutex> guard(drawsMutex);
        auto entry = draws.find(surface);
        if (entry != draws.end()) { found = entry->second; draws.erase(entry); }
    }
    if (!found) {
        using Fn = EGLBoolean (*)(EGLDisplay, EGLSurface);
        auto real = reinterpret_cast<Fn>(symbol("eglDestroySurface"));
        return real ? real(display, surface) : EGL_FALSE;
    }
    if (currentContext != EGL_NO_CONTEXT) {
        auto own = found->framebuffers.find(currentContext);
        if (own != found->framebuffers.end()) glDeleteFramebuffers(1, &own->second);
        if (found->color) glDeleteRenderbuffers(1, &found->color);
        if (found->depth) glDeleteRenderbuffers(1, &found->depth);
    }
    if (currentWindow == surface) { currentWindow = EGL_NO_SURFACE; currentFramebuffer = 0; }
    delete reinterpret_cast<char*>(surface);
    ANativeWindow_release(found->window);
    delete found;
    return EGL_TRUE;
}
}
/** Android's framebuffer 0 is the window; here the window is the shim framebuffer of the current context. */
extern "C" void glBindFramebuffer(GLenum target, GLuint framebuffer) {
    BindFramebuffer bind = realBindFramebuffer();
    if (!bind) return;
    if (framebuffer == 0 && currentWindow != EGL_NO_SURFACE) {
        Draw* draw=findDraw(currentWindow);
        GLint previousDraw=0,previousRead=0;
        if(target!=GL_FRAMEBUFFER){glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&previousDraw);glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING,&previousRead);}
        if(draw&&!prepare(*draw,currentContext))return;
        if(target!=GL_FRAMEBUFFER){bind(GL_DRAW_FRAMEBUFFER,static_cast<GLuint>(previousDraw));bind(GL_READ_FRAMEBUFFER,static_cast<GLuint>(previousRead));}
        framebuffer=currentFramebuffer;
    }
    bind(target, framebuffer);
}
extern "C" __eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* name) {
    if (!name) return nullptr;
    if (std::strcmp(name, "eglGetDisplay") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(eglGetDisplay);
    if (std::strcmp(name, "eglChooseConfig") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(eglChooseConfig);
    if (std::strcmp(name, "eglGetConfigAttrib") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(eglGetConfigAttrib);
    if (std::strcmp(name, "eglGetError") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(eglGetError);
    if (std::strcmp(name, "eglQueryString") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(eglQueryString);
    if (std::strcmp(name, "eglCreateImageKHR") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(createImage);
    if (std::strcmp(name, "eglGetNativeClientBufferANDROID") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(nativeClientBuffer);
    if (std::strcmp(name, "eglCreateWindowSurface") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(createWindow);
    if (std::strcmp(name, "eglMakeCurrent") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(makeCurrent);
    if (std::strcmp(name, "eglSwapBuffers") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(swapBuffers);
    if (std::strcmp(name, "eglSurfaceAttrib") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(surfaceAttrib);
    if (std::strcmp(name, "eglQuerySurface") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(querySurface);
    if (std::strcmp(name, "eglDestroySurface") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(destroySurface);
    if (std::strcmp(name, "eglGetCurrentSurface") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(currentSurface);
    if (std::strcmp(name, "glBindFramebuffer") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(glBindFramebuffer);
    if (std::strcmp(name, "eglSetDamageRegionKHR") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(setDamageRegion);
    if (std::strcmp(name, "eglSwapBuffersWithDamageKHR") == 0 || std::strcmp(name, "eglSwapBuffersWithDamageEXT") == 0) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(swapWithDamage);
    Proc next = realProc();
    return next ? reinterpret_cast<__eglMustCastToProperFunctionPointerType>(next(name)) : nullptr;
}
#define FORWARD(name, type, args, call) extern "C" type name args { using Fn = type (*) args; auto fn = reinterpret_cast<Fn>(symbol(#name)); return fn ? fn call : type(); }
FORWARD(eglBindAPI, EGLBoolean, (EGLenum api), (api))
extern "C" EGLBoolean eglChooseConfig(EGLDisplay d,const EGLint* a,EGLConfig* c,EGLint n,EGLint* count){
    auto fn=reinterpret_cast<EGLBoolean(*)(EGLDisplay,const EGLint*,EGLConfig*,EGLint,EGLint*)>(symbol("eglChooseConfig"));
    if(!fn){shimError=EGL_NOT_INITIALIZED;return EGL_FALSE;}
    if(d==EGL_NO_DISPLAY||d!=adapterDisplay.load())return fn(d,a,c,n,count);
    // Android windows are backed by Shiraz FBOs on this surfaceless display.
    // Mesa's pbuffer-capable configs provide their context/color format.
    EGLint mapped[260];unsigned used=0;bool surface=false;
    if(a)for(unsigned i=0;a[i]!=EGL_NONE;i+=2){
        if(i>=256){shimError=EGL_BAD_ATTRIBUTE;return EGL_FALSE;}
        EGLint value=a[i+1];if(a[i]==EGL_SURFACE_TYPE){surface=true;if(value!=EGL_DONT_CARE&&(value&EGL_WINDOW_BIT))value=(value&~EGL_WINDOW_BIT)|EGL_PBUFFER_BIT;}
        mapped[used++]=a[i];mapped[used++]=value;
    }
    if(!surface){mapped[used++]=EGL_SURFACE_TYPE;mapped[used++]=EGL_PBUFFER_BIT;}
    mapped[used]=EGL_NONE;return fn(d,mapped,c,n,count);
}
FORWARD(eglCreateContext, EGLContext, (EGLDisplay d, EGLConfig c, EGLContext s, const EGLint* a), (d, c, s, a))
FORWARD(eglDestroyContext, EGLBoolean, (EGLDisplay d, EGLContext c), (d, c))
extern "C" EGLDisplay eglGetDisplay(EGLNativeDisplayType display) {
    // Android's default EGL display does not refer to an X11 connection. Our
    // window adapter owns presentation, so its contexts use Mesa's headless
    // display even when no desktop DISPLAY variable is available.
    if(display==EGL_DEFAULT_DISPLAY){
        using Platform=EGLDisplay(*)(EGLenum,void*,const EGLint*);
        auto proc=realProc();auto platform=proc?reinterpret_cast<Platform>(proc("eglGetPlatformDisplayEXT")):nullptr;
        if(platform){EGLDisplay result=platform(EGL_PLATFORM_SURFACELESS_MESA,nullptr,nullptr);if(result!=EGL_NO_DISPLAY){adapterDisplay.store(result);return result;}}
    }
    auto fn=reinterpret_cast<EGLDisplay(*)(EGLNativeDisplayType)>(symbol("eglGetDisplay"));return fn?fn(display):EGL_NO_DISPLAY;
}
FORWARD(eglInitialize, EGLBoolean, (EGLDisplay d, EGLint* major, EGLint* minor), (d, major, minor))
FORWARD(eglTerminate, EGLBoolean, (EGLDisplay d), (d))
FORWARD(eglGetCurrentContext, EGLContext, (void), ())
FORWARD(eglGetCurrentDisplay, EGLDisplay, (void), ())
FORWARD(eglCreatePbufferSurface, EGLSurface, (EGLDisplay d,EGLConfig c,const EGLint* a), (d,c,a))
FORWARD(eglCreatePbufferFromClientBuffer, EGLSurface, (EGLDisplay d,EGLenum type,EGLClientBuffer b,EGLConfig c,const EGLint* a), (d,type,b,c,a))
FORWARD(eglCreatePixmapSurface, EGLSurface, (EGLDisplay d,EGLConfig c,EGLNativePixmapType p,const EGLint* a), (d,c,p,a))
FORWARD(eglGetConfigs, EGLBoolean, (EGLDisplay d,EGLConfig* c,EGLint size,EGLint* count), (d,c,size,count))
extern "C" EGLBoolean eglGetConfigAttrib(EGLDisplay d,EGLConfig c,EGLint a,EGLint* value){
    auto fn=reinterpret_cast<EGLBoolean(*)(EGLDisplay,EGLConfig,EGLint,EGLint*)>(symbol("eglGetConfigAttrib"));
    if(!fn){shimError=EGL_NOT_INITIALIZED;return EGL_FALSE;}
    EGLBoolean ok=fn(d,c,a,value);
    if(ok&&value&&d!=EGL_NO_DISPLAY&&d==adapterDisplay.load()&&a==EGL_SURFACE_TYPE&&(*value&EGL_PBUFFER_BIT))*value|=EGL_WINDOW_BIT;
    return ok;
}
FORWARD(eglWaitNative, EGLBoolean, (EGLint engine), (engine))
FORWARD(eglWaitGL, EGLBoolean, (void), ())
FORWARD(eglWaitClient, EGLBoolean, (void), ())
FORWARD(eglCopyBuffers, EGLBoolean, (EGLDisplay d,EGLSurface s,EGLNativePixmapType p), (d,s,p))
FORWARD(eglQueryContext, EGLBoolean, (EGLDisplay d,EGLContext c,EGLint a,EGLint* value), (d,c,a,value))
FORWARD(eglBindTexImage, EGLBoolean, (EGLDisplay d,EGLSurface s,EGLint b), (d,s,b))
FORWARD(eglReleaseTexImage, EGLBoolean, (EGLDisplay d,EGLSurface s,EGLint b), (d,s,b))
FORWARD(eglSwapInterval, EGLBoolean, (EGLDisplay d,EGLint interval), (d,interval))
FORWARD(eglReleaseThread, EGLBoolean, (void), ())
FORWARD(eglQueryAPI, EGLenum, (void), ())
extern "C" const char* eglQueryString(EGLDisplay d, EGLint name) {
    using Fn = const char* (*)(EGLDisplay, EGLint);
    auto fn = reinterpret_cast<Fn>(symbol("eglQueryString"));
    const char* result = fn ? fn(d, name) : nullptr;
    if (result && name == EGL_EXTENSIONS && d != EGL_NO_DISPLAY &&
        std::strstr(result, "EGL_EXT_image_dma_buf_import")) {
        // Report Android buffer extensions only on displays capable of the
        // DMA-buffer import used by our implementation.
        static std::mutex extensionMutex;
        static std::unordered_map<EGLDisplay, std::string> extensions;
        std::lock_guard<std::mutex> guard(extensionMutex);
        auto found = extensions.find(d);
        if (found == extensions.end()) found = extensions.emplace(d, std::string(result) +
            " EGL_ANDROID_image_native_buffer EGL_ANDROID_get_native_client_buffer").first;
        return found->second.c_str();
    }
    return result;
}
#undef FORWARD
extern "C" EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface draw, EGLSurface read, EGLContext c) { return makeCurrent(d, draw, read, c); }
extern "C" EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s) { return swapBuffers(d, s); }
extern "C" EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint* a) { return createWindow(d, c, w, a); }
extern "C" EGLBoolean eglQuerySurface(EGLDisplay d, EGLSurface s, EGLint a, EGLint* v) { return querySurface(d, s, a, v); }
extern "C" EGLBoolean eglDestroySurface(EGLDisplay d, EGLSurface s) { return destroySurface(d, s); }
extern "C" EGLSurface eglGetCurrentSurface(EGLint api) { return currentSurface(api); }

extern "C" EGLint eglGetError() {
    if (shimError != EGL_SUCCESS) { EGLint result = shimError; shimError = EGL_SUCCESS; return result; }
    auto real = reinterpret_cast<EGLint (*)()>(symbol("eglGetError"));
    return real ? real() : EGL_NOT_INITIALIZED;
}
extern "C" EGLImageKHR eglCreateImageKHR(EGLDisplay d, EGLContext c, EGLenum t, EGLClientBuffer b, const EGLint* a) { return createImage(d,c,t,b,a); }
extern "C" EGLBoolean eglDestroyImageKHR(EGLDisplay display, EGLImageKHR image) {
    using Fn = EGLBoolean (*)(EGLDisplay, EGLImageKHR);
    auto getProc = reinterpret_cast<void* (*)(const char*)>(symbol("eglGetProcAddress"));
    auto real = getProc ? reinterpret_cast<Fn>(getProc("eglDestroyImageKHR")) : nullptr;
    if (!real) { shimError = EGL_BAD_ACCESS; return EGL_FALSE; }
    return real(display, image);
}
extern "C" EGLClientBuffer eglGetNativeClientBufferANDROID(const AHardwareBuffer* b) { return nativeClientBuffer(b); }

extern "C" EGLBoolean eglSurfaceAttrib(EGLDisplay display, EGLSurface surface, EGLint attribute, EGLint value) { return surfaceAttrib(display, surface, attribute, value); }
