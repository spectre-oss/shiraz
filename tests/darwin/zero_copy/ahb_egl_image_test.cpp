// SPDX-License-Identifier: GPL-3.0-only
// Live macOS ANGLE Metal test verifying EGL_NATIVE_BUFFER_ANDROID -> IOSurface ->
// MTLTexture -> EGL_METAL_TEXTURE_ANGLE import via eglCreateImageKHR and sampling
// in GLES 3 through glEGLImageTargetTexture2DOES(GL_TEXTURE_2D).
#include "shiraz/hardware_buffer.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

#include <GLES2/gl2ext.h>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <string>
#if defined(__APPLE__)
#include <unistd.h>

extern "C" int sync_merge(const char* name, int first, int second);
extern "C" int sync_wait(int fd, int timeout);
#endif

namespace {

GLuint compileShader(GLenum type, const char* src, PFNGLCREATESHADERPROC createShader,
                     PFNGLSHADERSOURCEPROC shaderSource, PFNGLCOMPILESHADERPROC compileShaderFn,
                     PFNGLGETSHADERIVPROC getShaderiv) {
    GLuint s = createShader(type);
    assert(s != 0);
    shaderSource(s, 1, &src, nullptr);
    compileShaderFn(s);
    GLint ok = 0;
    getShaderiv(s, GL_COMPILE_STATUS, &ok);
    assert(ok == GL_TRUE);
    return s;
}

} // namespace

int main(int argc, char** argv) {
    assert(argc >= 2);
    const std::string libDir = argv[1];
    void* gles = dlopen((libDir + "/libGLESv2.dylib").c_str(), RTLD_NOW | RTLD_GLOBAL);
    void* shim = dlopen((libDir + "/libEGL_shim.so").c_str(), RTLD_NOW | RTLD_LOCAL);
    assert(gles && shim);

    auto getDisplay = reinterpret_cast<PFNEGLGETDISPLAYPROC>(dlsym(shim, "eglGetDisplay"));
    auto initialize = reinterpret_cast<PFNEGLINITIALIZEPROC>(dlsym(shim, "eglInitialize"));
    auto bindAPI = reinterpret_cast<PFNEGLBINDAPIPROC>(dlsym(shim, "eglBindAPI"));
    auto chooseConfig = reinterpret_cast<PFNEGLCHOOSECONFIGPROC>(dlsym(shim, "eglChooseConfig"));
    auto createContext = reinterpret_cast<PFNEGLCREATECONTEXTPROC>(dlsym(shim, "eglCreateContext"));
    auto createPbuffer =
        reinterpret_cast<PFNEGLCREATEPBUFFERSURFACEPROC>(dlsym(shim, "eglCreatePbufferSurface"));
    auto makeCurrent = reinterpret_cast<PFNEGLMAKECURRENTPROC>(dlsym(shim, "eglMakeCurrent"));
    auto getProc = reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(dlsym(shim, "eglGetProcAddress"));
    auto destroyContext =
        reinterpret_cast<PFNEGLDESTROYCONTEXTPROC>(dlsym(shim, "eglDestroyContext"));
    auto destroySurface =
        reinterpret_cast<PFNEGLDESTROYSURFACEPROC>(dlsym(shim, "eglDestroySurface"));
    auto terminate = reinterpret_cast<PFNEGLTERMINATEPROC>(dlsym(shim, "eglTerminate"));
    auto getError = reinterpret_cast<PFNEGLGETERRORPROC>(dlsym(shim, "eglGetError"));
#if defined(__APPLE__)
    auto queryString = reinterpret_cast<PFNEGLQUERYSTRINGPROC>(dlsym(shim, "eglQueryString"));
    assert(getDisplay && initialize && bindAPI && chooseConfig && createContext && createPbuffer &&
           makeCurrent && getProc && destroyContext && destroySurface && terminate && getError &&
           queryString);
#else
    assert(getDisplay && initialize && bindAPI && chooseConfig && createContext && createPbuffer &&
           makeCurrent && getProc && destroyContext && destroySurface && terminate && getError);
#endif

    auto getNativeClientBuffer = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
        getProc("eglGetNativeClientBufferANDROID"));
    auto createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(getProc("eglCreateImageKHR"));
    auto destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(getProc("eglDestroyImageKHR"));
    auto imageTargetTex2D = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
        getProc("glEGLImageTargetTexture2DOES"));
#if defined(__APPLE__)
    auto createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(getProc("eglCreateSyncKHR"));
    auto destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(getProc("eglDestroySyncKHR"));
    auto clientWaitSync =
        reinterpret_cast<PFNEGLCLIENTWAITSYNCKHRPROC>(getProc("eglClientWaitSyncKHR"));
    auto dupNativeFenceFd =
        reinterpret_cast<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>(getProc("eglDupNativeFenceFDANDROID"));
    assert(getNativeClientBuffer && createImage && destroyImage && imageTargetTex2D && createSync &&
           destroySync && clientWaitSync && dupNativeFenceFd);
#else
    assert(getNativeClientBuffer && createImage && destroyImage && imageTargetTex2D);
#endif

    auto genTextures = reinterpret_cast<PFNGLGENTEXTURESPROC>(dlsym(gles, "glGenTextures"));
    auto bindTexture = reinterpret_cast<PFNGLBINDTEXTUREPROC>(dlsym(gles, "glBindTexture"));
    auto texParameteri = reinterpret_cast<PFNGLTEXPARAMETERIPROC>(dlsym(gles, "glTexParameteri"));
    auto deleteTextures =
        reinterpret_cast<PFNGLDELETETEXTURESPROC>(dlsym(gles, "glDeleteTextures"));
    auto genFramebuffers =
        reinterpret_cast<PFNGLGENFRAMEBUFFERSPROC>(dlsym(gles, "glGenFramebuffers"));
    auto bindFramebuffer =
        reinterpret_cast<PFNGLBINDFRAMEBUFFERPROC>(dlsym(gles, "glBindFramebuffer"));
    auto framebufferTexture2D =
        reinterpret_cast<PFNGLFRAMEBUFFERTEXTURE2DPROC>(dlsym(gles, "glFramebufferTexture2D"));
    auto deleteFramebuffers =
        reinterpret_cast<PFNGLDELETEFRAMEBUFFERSPROC>(dlsym(gles, "glDeleteFramebuffers"));
    auto texImage2D = reinterpret_cast<PFNGLTEXIMAGE2DPROC>(dlsym(gles, "glTexImage2D"));
    auto viewport = reinterpret_cast<PFNGLVIEWPORTPROC>(dlsym(gles, "glViewport"));
    auto createShader = reinterpret_cast<PFNGLCREATESHADERPROC>(dlsym(gles, "glCreateShader"));
    auto shaderSource = reinterpret_cast<PFNGLSHADERSOURCEPROC>(dlsym(gles, "glShaderSource"));
    auto compileShaderFn = reinterpret_cast<PFNGLCOMPILESHADERPROC>(dlsym(gles, "glCompileShader"));
    auto getShaderiv = reinterpret_cast<PFNGLGETSHADERIVPROC>(dlsym(gles, "glGetShaderiv"));
    auto createProgram = reinterpret_cast<PFNGLCREATEPROGRAMPROC>(dlsym(gles, "glCreateProgram"));
    auto attachShader = reinterpret_cast<PFNGLATTACHSHADERPROC>(dlsym(gles, "glAttachShader"));
    auto linkProgram = reinterpret_cast<PFNGLLINKPROGRAMPROC>(dlsym(gles, "glLinkProgram"));
    auto getProgramiv = reinterpret_cast<PFNGLGETPROGRAMIVPROC>(dlsym(gles, "glGetProgramiv"));
    auto useProgram = reinterpret_cast<PFNGLUSEPROGRAMPROC>(dlsym(gles, "glUseProgram"));
    auto drawArrays = reinterpret_cast<PFNGLDRAWARRAYSPROC>(dlsym(gles, "glDrawArrays"));
    auto readPixels = reinterpret_cast<PFNGLREADPIXELSPROC>(dlsym(gles, "glReadPixels"));
    auto getGlError = reinterpret_cast<PFNGLGETERRORPROC>(dlsym(gles, "glGetError"));
    auto activeTexture = reinterpret_cast<PFNGLACTIVETEXTUREPROC>(dlsym(gles, "glActiveTexture"));
    auto getUniformLocation =
        reinterpret_cast<PFNGLGETUNIFORMLOCATIONPROC>(dlsym(gles, "glGetUniformLocation"));
    auto uniform1i = reinterpret_cast<PFNGLUNIFORM1IPROC>(dlsym(gles, "glUniform1i"));
#if defined(__APPLE__)
    auto flushFn = reinterpret_cast<PFNGLFLUSHPROC>(dlsym(gles, "glFlush"));
    assert(genTextures && bindTexture && texParameteri && deleteTextures && genFramebuffers &&
           bindFramebuffer && framebufferTexture2D && deleteFramebuffers && texImage2D &&
           viewport && createShader && shaderSource && compileShaderFn && getShaderiv &&
           createProgram && attachShader && linkProgram && getProgramiv && useProgram &&
           drawArrays && readPixels && getGlError && activeTexture && getUniformLocation &&
           uniform1i && flushFn);
#else
    assert(genTextures && bindTexture && texParameteri && deleteTextures && genFramebuffers &&
           bindFramebuffer && framebufferTexture2D && deleteFramebuffers && texImage2D &&
           viewport && createShader && shaderSource && compileShaderFn && getShaderiv &&
           createProgram && attachShader && linkProgram && getProgramiv && useProgram &&
           drawArrays && readPixels && getGlError && activeTexture && getUniformLocation &&
           uniform1i);
#endif

    EGLDisplay dpy = getDisplay(EGL_DEFAULT_DISPLAY);
    assert(dpy != EGL_NO_DISPLAY);
    EGLint major = 0, minor = 0;
    assert(initialize(dpy, &major, &minor) == EGL_TRUE);
    assert(bindAPI(EGL_OPENGL_ES_API) == EGL_TRUE);
#if defined(__APPLE__)
    const char* exts = queryString(dpy, EGL_EXTENSIONS);
    assert(exts && std::strstr(exts, "EGL_ANDROID_image_native_buffer") &&
           std::strstr(exts, "EGL_ANDROID_get_native_client_buffer") &&
           std::strstr(exts, "EGL_ANDROID_native_fence_sync"));
#endif

    const EGLint cfgAttribs[] = {
        EGL_SURFACE_TYPE,
        EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE,
        EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE,
        8,
        EGL_GREEN_SIZE,
        8,
        EGL_BLUE_SIZE,
        8,
        EGL_ALPHA_SIZE,
        8,
        EGL_NONE,
    };
    EGLConfig cfg = nullptr;
    EGLint numCfg = 0;
    assert(chooseConfig(dpy, cfgAttribs, &cfg, 1, &numCfg) == EGL_TRUE && numCfg == 1);

    const EGLint pbAttribs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    EGLSurface pb = createPbuffer(dpy, cfg, pbAttribs);
    assert(pb != EGL_NO_SURFACE);

    const EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext ctx = createContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttribs);
    assert(ctx != EGL_NO_CONTEXT);
    assert(makeCurrent(dpy, pb, pb, ctx) == EGL_TRUE);

    // 1. Allocate an IOSurface-backed AHardwareBuffer and fill it with RGBA=(224, 64, 160, 255).
    AHardwareBuffer_Desc desc{32, 32, 1, 1, 0x333, 0, 0, 0};
    AHardwareBuffer* ahb = nullptr;
    assert(AHardwareBuffer_allocate(&desc, &ahb) == 0 && ahb != nullptr);
    AHardwareBuffer_describe(ahb, &desc);

    void* ptr = nullptr;
    assert(AHardwareBuffer_lock(ahb, 0x30, -1, nullptr, &ptr) == 0 && ptr != nullptr);
    auto* bytes = static_cast<uint8_t*>(ptr);
    for (uint32_t y = 0; y < desc.height; ++y) {
        for (uint32_t x = 0; x < desc.width; ++x) {
            uint8_t* px = bytes + (y * desc.stride + x) * 4;
            px[0] = 224;
            px[1] = 64;
            px[2] = 160;
            px[3] = 255;
        }
    }
    assert(AHardwareBuffer_unlock(ahb, nullptr) == 0);

    // 2. Wrap AHardwareBuffer -> EGLClientBuffer -> EGLImageKHR (EGL_NATIVE_BUFFER_ANDROID).
    EGLClientBuffer clientBuf = getNativeClientBuffer(ahb);
    assert(clientBuf != nullptr);
    const EGLint imgAttribs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    EGLImageKHR img =
        createImage(dpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuf, imgAttribs);
    assert(img != EGL_NO_IMAGE_KHR);

    // 3. Bind EGLImageKHR to a GL_TEXTURE_2D and sample it into an FBO.
    GLuint srcTex = 0;
    genTextures(1, &srcTex);
    bindTexture(GL_TEXTURE_2D, srcTex);
    texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    imageTargetTex2D(GL_TEXTURE_2D, img);
    assert(getGlError() == GL_NO_ERROR);

    GLuint dstTex = 0, fbo = 0;
    genTextures(1, &dstTex);
    bindTexture(GL_TEXTURE_2D, dstTex);
    texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    genFramebuffers(1, &fbo);
    bindFramebuffer(GL_FRAMEBUFFER, fbo);
    framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, dstTex, 0);

    const char* vsSrc = "#version 300 es\n"
                        "void main() {\n"
                        "  vec2 p[3] = vec2[3](vec2(-1.0,-1.0), vec2(3.0,-1.0), vec2(-1.0,3.0));\n"
                        "  gl_Position = vec4(p[gl_VertexID], 0.0, 1.0);\n"
                        "}\n";
    const char* fsSrc = "#version 300 es\n"
                        "precision mediump float;\n"
                        "uniform sampler2D uTex;\n"
                        "out vec4 oColor;\n"
                        "void main() { oColor = texture(uTex, vec2(0.5, 0.5)); }\n";
    GLuint vs = compileShader(GL_VERTEX_SHADER, vsSrc, createShader, shaderSource, compileShaderFn,
                              getShaderiv);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fsSrc, createShader, shaderSource,
                              compileShaderFn, getShaderiv);
    GLuint prog = createProgram();
    attachShader(prog, vs);
    attachShader(prog, fs);
    linkProgram(prog);
    GLint linked = 0;
    getProgramiv(prog, GL_LINK_STATUS, &linked);
    assert(linked == GL_TRUE);

    useProgram(prog);
    activeTexture(GL_TEXTURE0);
    bindTexture(GL_TEXTURE_2D, srcTex);
    uniform1i(getUniformLocation(prog, "uTex"), 0);
    viewport(0, 0, 16, 16);
    drawArrays(GL_TRIANGLES, 0, 3);
    assert(getGlError() == GL_NO_ERROR);

    uint8_t pixel[4] = {0, 0, 0, 0};
    readPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
    assert(pixel[0] == 224 && pixel[1] == 64 && pixel[2] == 160 && pixel[3] == 255);
    std::printf("PASS ahb egl_image zero-copy rgba=(%u,%u,%u,%u)\n", pixel[0], pixel[1], pixel[2],
                pixel[3]);
#if defined(__APPLE__)

    // 4. Verify EGL_ANDROID_native_fence_sync backed by MTLSharedEvent + sync_merge + sync_wait.
    drawArrays(GL_TRIANGLES, 0, 3);
    EGLSyncKHR gpuSync = createSync(dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
    assert(gpuSync != EGL_NO_SYNC_KHR);
    flushFn();
    int fenceFd = dupNativeFenceFd(dpy, gpuSync);
    assert(fenceFd >= 0);
    int mergedFd = sync_merge("shiraz-macos-fence", fenceFd, fenceFd);
    assert(mergedFd >= 0 && sync_wait(mergedFd, 2000) == 0);
    assert(clientWaitSync(dpy, gpuSync, 0, 2000000000ULL) == EGL_CONDITION_SATISFIED_KHR);
    // Import a Shiraz fence fd back into EGL_SYNC_NATIVE_FENCE_ANDROID.
    const EGLint importAttribs[] = {EGL_SYNC_NATIVE_FENCE_FD_ANDROID, mergedFd, EGL_NONE};
    EGLSyncKHR importedSync = createSync(dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, importAttribs);
    assert(importedSync != EGL_NO_SYNC_KHR);
    assert(clientWaitSync(dpy, importedSync, 0, 1000000000ULL) == EGL_CONDITION_SATISFIED_KHR);
    int reDup = dupNativeFenceFd(dpy, importedSync);
    assert(reDup >= 0 && sync_wait(reDup, 1000) == 0);
    close(reDup);
    close(fenceFd);
    assert(destroySync(dpy, importedSync) == EGL_TRUE);
    assert(destroySync(dpy, gpuSync) == EGL_TRUE);
    std::printf("PASS egl_native_fence_sync mtl_shared_event merge_wait=ok\n");
#endif

    // 4. Clean teardown: destroy EGLImageKHR, GL objects, AHardwareBuffer, EGL context.
    assert(destroyImage(dpy, img) == EGL_TRUE);
    deleteFramebuffers(1, &fbo);
    deleteTextures(1, &dstTex);
    deleteTextures(1, &srcTex);
    AHardwareBuffer_release(ahb);
    makeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    destroyContext(dpy, ctx);
    destroySurface(dpy, pb);
    terminate(dpy);

    std::puts("MACOS_AHB_EGL_IMAGE_PASS");
    return 0;
}
