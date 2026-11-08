// SPDX-License-Identifier: GPL-3.0-only
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <stdint.h>
_Static_assert(sizeof(EGLint) == 4 && sizeof(EGLBoolean) == 4 && sizeof(EGLDisplay) == 8 &&
               sizeof(GLboolean) == 1 && sizeof(GLintptr) == 8 && sizeof(GLuint64) == 8,
               "Android arm64 EGL/GLES scalar ABI");
extern void guestegl_poison_mask(void *function);
#define LOAD(TYPE, NAME) TYPE NAME = (TYPE)eglGetProcAddress(#NAME); if (!NAME) return __LINE__
int run_guestegl(uintptr_t *procedures) {
  LOAD(PFNEGLGETDISPLAYPROC, eglGetDisplay);
  LOAD(PFNEGLINITIALIZEPROC, eglInitialize);
  LOAD(PFNEGLTERMINATEPROC, eglTerminate);
  LOAD(PFNEGLBINDAPIPROC, eglBindAPI);
  LOAD(PFNEGLCHOOSECONFIGPROC, eglChooseConfig);
  LOAD(PFNEGLCREATEPBUFFERSURFACEPROC, eglCreatePbufferSurface);
  LOAD(PFNEGLCREATECONTEXTPROC, eglCreateContext);
  LOAD(PFNEGLMAKECURRENTPROC, eglMakeCurrent);
  LOAD(PFNEGLDESTROYSURFACEPROC, eglDestroySurface);
  LOAD(PFNEGLDESTROYCONTEXTPROC, eglDestroyContext);
  LOAD(PFNGLGETSTRINGPROC, glGetString);
  LOAD(PFNGLCLEARCOLORPROC, glClearColor);
  LOAD(PFNGLCOLORMASKPROC, glColorMask);
  LOAD(PFNGLCLEARPROC, glClear);
  LOAD(PFNGLREADPIXELSPROC, glReadPixels);
  LOAD(PFNGLGETERRORPROC, glGetError);
  LOAD(PFNGLFINISHPROC, glFinish);
  if (eglGetProcAddress("system") || eglGetProcAddress("glDebugMessageCallback") ||
      eglGetProcAddress("eglCreatePlatformWindowSurface")) return __LINE__;
  procedures[0] = (uintptr_t)glColorMask;
  procedures[1] = (uintptr_t)glClearColor;
  procedures[2] = (uintptr_t)glReadPixels;
  EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  EGLContext context = EGL_NO_CONTEXT;
  EGLSurface surface = EGL_NO_SURFACE;
  EGLint major = 0, minor = 0, count = 0;
  EGLConfig config = 0;
  int initialized = 0, result = 73;
#define CHECK(X) do { if (!(X)) { result = __LINE__; goto done; } } while (0)
  CHECK(display != EGL_NO_DISPLAY);
  CHECK(eglInitialize(display, &major, &minor) == EGL_TRUE && major >= 1);
  initialized = 1;
  CHECK(eglBindAPI(EGL_OPENGL_ES_API) == EGL_TRUE);
  const EGLint attributes[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
      EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
  CHECK(eglChooseConfig(display, attributes, &config, 1, &count) == EGL_TRUE && count == 1);
  const EGLint pixels[] = {EGL_WIDTH, 8, EGL_HEIGHT, 8, EGL_NONE};
  surface = eglCreatePbufferSurface(display, config, pixels);
  CHECK(surface != EGL_NO_SURFACE);
  const EGLint version[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
  context = eglCreateContext(display, config, EGL_NO_CONTEXT, version);
  CHECK(context != EGL_NO_CONTEXT);
  CHECK(eglMakeCurrent(display, surface, surface, context) == EGL_TRUE);
  CHECK(glGetString(GL_VERSION) != 0);
  glClearColor(0.25f, 0.5f, 0.75f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT);
  guestegl_poison_mask((void *)glColorMask);
  glClearColor(1.0f, 0.25f, 0.0f, 0.5f);
  glClear(GL_COLOR_BUFFER_BIT);
  glFinish();
  unsigned char pixel[4] = {0};
  glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
  CHECK(glGetError() == GL_NO_ERROR);
  CHECK(pixel[0] >= 63 && pixel[0] <= 65 && pixel[1] >= 63 && pixel[1] <= 65 &&
        pixel[2] >= 190 && pixel[2] <= 192 && pixel[3] >= 127 && pixel[3] <= 129);
done:
  if (initialized) {
    (void)eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (context != EGL_NO_CONTEXT) (void)eglDestroyContext(display, context);
    if (surface != EGL_NO_SURFACE) (void)eglDestroySurface(display, surface);
    (void)eglTerminate(display);
  }
  return result;
}
