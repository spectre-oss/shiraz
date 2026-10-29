// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <cstdlib>
#include <dlfcn.h>
#include <iostream>
#include <vector>
int main() {
    EGLDisplay defaultDisplay=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major=0,minor=0;
    if(defaultDisplay==EGL_NO_DISPLAY||!eglInitialize(defaultDisplay,&major,&minor))return 20;
    if(!eglBindAPI(EGL_OPENGL_ES_API)||eglQueryAPI()!=EGL_OPENGL_ES_API)return 21;
    EGLint configCount=0;if(!eglGetConfigs(defaultDisplay,nullptr,0,&configCount)||configCount<1)return 22;
    EGLint choose[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_NONE};EGLConfig config;
    if(!eglChooseConfig(defaultDisplay,choose,&config,1,&configCount)||configCount!=1)return 23;
    EGLint supported=0;if(!eglGetConfigAttrib(defaultDisplay,config,EGL_SURFACE_TYPE,&supported)||(supported&EGL_WINDOW_BIT)==0)return 26;
    auto chooseProc=reinterpret_cast<PFNEGLCHOOSECONFIGPROC>(eglGetProcAddress("eglChooseConfig"));
    auto configProc=reinterpret_cast<PFNEGLGETCONFIGATTRIBPROC>(eglGetProcAddress("eglGetConfigAttrib"));
    if(!chooseProc||!configProc||!chooseProc(defaultDisplay,choose,&config,1,&configCount)||configCount!=1||!configProc(defaultDisplay,config,EGL_SURFACE_TYPE,&supported)||(supported&EGL_WINDOW_BIT)==0)return 27;
    EGLint contextAttributes[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
    EGLContext headless=eglCreateContext(defaultDisplay,config,EGL_NO_CONTEXT,contextAttributes);
    EGLint pbufferAttributes[]={EGL_WIDTH,2,EGL_HEIGHT,2,EGL_NONE};EGLSurface pbuffer=eglCreatePbufferSurface(defaultDisplay,config,pbufferAttributes);
    if(headless==EGL_NO_CONTEXT||pbuffer==EGL_NO_SURFACE||!eglMakeCurrent(defaultDisplay,pbuffer,pbuffer,headless))return 24;
    if(eglGetCurrentContext()!=headless||eglGetCurrentDisplay()!=defaultDisplay||!glGetString(GL_RENDERER))return 25;
    std::cout << "EGL_HEADLESS_RENDERER " << glGetString(GL_RENDERER) << "\n";
    ANativeWindow* configuredWindow=shiraz_window_create(2,2);
    EGLSurface configuredSurface=eglCreateWindowSurface(defaultDisplay,config,reinterpret_cast<EGLNativeWindowType>(configuredWindow),nullptr);
    if(configuredSurface==EGL_NO_SURFACE||!eglMakeCurrent(defaultDisplay,configuredSurface,configuredSurface,headless))return 28;
    ANativeWindow_release(configuredWindow); // EGL must own the window until surface destruction.
    if(!shiraz_window_known(configuredWindow))return 31;
    glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);if(!eglSwapBuffers(defaultDisplay,configuredSurface))return 29;
    uint32_t configuredPixels[4]={};if(shiraz_window_copy_argb(configuredWindow,configuredPixels,4)!=0||configuredPixels[0]!=0xffff0000)return 30;
    auto damage = reinterpret_cast<PFNEGLSETDAMAGEREGIONKHRPROC>(eglGetProcAddress("eglSetDamageRegionKHR"));
    auto swapDamage = reinterpret_cast<PFNEGLSWAPBUFFERSWITHDAMAGEKHRPROC>(eglGetProcAddress("eglSwapBuffersWithDamageKHR"));
    auto swapDamageExt = reinterpret_cast<PFNEGLSWAPBUFFERSWITHDAMAGEEXTPROC>(eglGetProcAddress("eglSwapBuffersWithDamageEXT"));
    EGLint rect[] = {0, 0, 2, 2}, age = -1;
    if (!damage || !swapDamage || !swapDamageExt || !damage(defaultDisplay, configuredSurface, rect, 1)) return 33;
    if (!eglQuerySurface(defaultDisplay, configuredSurface, EGL_BUFFER_AGE_EXT, &age) || age != 0) return 34;
    if (damage(defaultDisplay, configuredSurface, nullptr, 1) || eglGetError() != EGL_BAD_PARAMETER) return 35;
    glClearColor(0,1,0,1);glClear(GL_COLOR_BUFFER_BIT);
    if (!swapDamage(defaultDisplay, configuredSurface, rect, 1) ||
        shiraz_window_copy_argb(configuredWindow, configuredPixels, 4) || configuredPixels[0] != 0xff00ff00) return 36;
    glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);
    if (!swapDamageExt(defaultDisplay, configuredSurface, nullptr, 0) ||
        shiraz_window_copy_argb(configuredWindow, configuredPixels, 4) || configuredPixels[0] != 0xff0000ff) return 37;
    auto bindProc=reinterpret_cast<PFNGLBINDFRAMEBUFFERPROC>(eglGetProcAddress("glBindFramebuffer"));
    auto bindBionic=reinterpret_cast<PFNGLBINDFRAMEBUFFERPROC>(dlsym(RTLD_DEFAULT,"bionic_glBindFramebuffer"));
    if(!bindProc||!bindBionic)return 44;
    GLint cachedWindowFramebuffer=0;glGetIntegerv(GL_FRAMEBUFFER_BINDING,&cachedWindowFramebuffer);
    for(auto bindWindow: {bindProc,bindBionic}) for(bool cached: {false,true}) for(int width: {8,3,12}) {
        int height=width+2;
        if(ANativeWindow_setBuffersGeometry(configuredWindow,width,height,1))return 38;
        EGLint reported=0;
        if(!eglQuerySurface(defaultDisplay,configuredSurface,EGL_WIDTH,&reported)||reported!=width)return 39;
        if(!eglQuerySurface(defaultDisplay,configuredSurface,EGL_HEIGHT,&reported)||reported!=height)return 40;
        bindWindow(GL_FRAMEBUFFER,cached?static_cast<GLuint>(cachedWindowFramebuffer):0);glViewport(0,0,width,height);
        glClearColor(1,0,1,1);glClear(GL_COLOR_BUFFER_BIT);
        if(!eglSwapBuffers(defaultDisplay,configuredSurface))return 41;
        std::vector<uint32_t> resized(width*height);
        if(shiraz_window_copy_argb(configuredWindow,resized.data(),resized.size()))return 42;
        for(uint32_t pixel:resized)if(pixel!=0xffff00ff)return 43;
    }
    std::cout << "EGL_WINDOW_RESIZE_OK\n";
    std::cout << "EGL_WINDOW_DAMAGE_OK\n";
    eglMakeCurrent(defaultDisplay,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(defaultDisplay,configuredSurface);if(shiraz_window_known(configuredWindow))return 32;
    eglMakeCurrent(defaultDisplay,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(defaultDisplay,pbuffer);eglDestroyContext(defaultDisplay,headless);eglTerminate(defaultDisplay);

    ANativeWindow* window = shiraz_window_create(2, 2);
    auto query = reinterpret_cast<PFNEGLQUERYDEVICESEXTPROC>(eglGetProcAddress("eglQueryDevicesEXT"));
    auto platform = reinterpret_cast<PFNEGLGETPLATFORMDISPLAYPROC>(eglGetProcAddress("eglGetPlatformDisplay"));
    EGLDeviceEXT devices[8]; EGLint count = 0;
    if (!window || !query || !platform || !query(8, devices, &count) || count < 1) return 1;
    EGLDisplay display = platform(EGL_PLATFORM_DEVICE_EXT, devices[count - 1], nullptr);
    if (!eglInitialize(display, nullptr, nullptr)) return 1;
    eglBindAPI(EGL_OPENGL_ES_API);
    auto create = reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(eglGetProcAddress);
    auto makeWindow = reinterpret_cast<EGLSurface (*)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*)>(create("eglCreateWindowSurface"));
    const EGLint attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, attribs);
    EGLSurface surface = makeWindow(display, nullptr, reinterpret_cast<EGLNativeWindowType>(window), nullptr);
    if (surface == EGL_NO_SURFACE || !eglMakeCurrent(display, surface, surface, context)) return 2;
    if (eglGetCurrentSurface(EGL_DRAW) != surface) return 6;
    auto setAttribute = reinterpret_cast<PFNEGLSURFACEATTRIBPROC>(eglGetProcAddress("eglSurfaceAttrib"));
    EGLint swapBehavior = 0;
    if (!setAttribute || !setAttribute(display, surface, EGL_SWAP_BEHAVIOR, EGL_BUFFER_DESTROYED)) return 7;
    if (!eglQuerySurface(display, surface, EGL_SWAP_BEHAVIOR, &swapBehavior) || swapBehavior != EGL_BUFFER_DESTROYED) return 8;
    if (eglSurfaceAttrib(display, surface, EGL_SWAP_BEHAVIOR, EGL_BUFFER_PRESERVED) || eglGetError() != EGL_BAD_MATCH) return 9;
    if (eglSurfaceAttrib(display, surface, EGL_WIDTH, 4) || eglGetError() != EGL_BAD_ATTRIBUTE) return 10;
    GLuint offscreen = 0;
    glGenFramebuffers(1, &offscreen);
    glBindFramebuffer(GL_FRAMEBUFFER, offscreen);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &offscreen);
    glClearColor(0.2f, 0.4f, 0.6f, 1.f);
    glClearDepthf(1.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    if (!eglSwapBuffers(display, surface)) return 3;
    uint32_t pixels[4] = {};
    if (shiraz_window_copy_argb(window, pixels, 4) != 0) return 4;
    unsigned r = (pixels[0] >> 16) & 255, g = (pixels[0] >> 8) & 255, b = pixels[0] & 255, a = pixels[0] >> 24;
    if (r < 40 || r > 70 || g < 90 || g > 120 || b < 140 || b > 170 || a < 250) {
        std::cerr << "pixel " << r << " " << g << " " << b << " " << a << "\n";
        return 5;
    }
    std::cout << "EGL_WINDOW_READBACK_OK\n";
    return 0;
}
