// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/guestegl.hpp"
#define GL_GLEXT_PROTOTYPES 1
#define EGL_EGLEXT_PROTOTYPES 1
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <dlfcn.h>
#include <cstring>
#include <map>

namespace shiraz::guest {
namespace {
static_assert(sizeof(EGLint) == 4 && sizeof(EGLBoolean) == 4 && sizeof(EGLenum) == 4 &&
              sizeof(EGLDisplay) == 8 && sizeof(EGLContext) == 8 && sizeof(EGLSurface) == 8 &&
              sizeof(GLboolean) == 1 && sizeof(GLint) == 4 && sizeof(GLenum) == 4 &&
              sizeof(GLintptr) == 8 && sizeof(GLsizeiptr) == 8 && sizeof(GLuint64) == 8,
              "guest EGL/GLES scalar widths require audited arm64 Khronos ABI");
struct Graphics {
  void *library = nullptr;
  PFNEGLGETPROCADDRESSPROC get = nullptr;
  std::map<std::string, ProviderExport> procedures;
  explicit Graphics(const std::filesystem::path &path) {
    if (!path.is_absolute()) throw ElfError("guest EGL requires an absolute shim path");
    auto resolved = std::filesystem::canonical(path);
    if (resolved.filename() != "libEGL_shim.so")
      throw ElfError("guest EGL requires the explicitly named Darwin EGL shim");
    library = ::dlopen(resolved.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!library) throw ElfError("cannot load configured Darwin EGL shim");
    get = reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(::dlsym(library, "eglGetProcAddress"));
    if (!get) { ::dlclose(library); library = nullptr; throw ElfError("configured EGL shim lacks eglGetProcAddress"); }
  }
  ~Graphics() { if (library) ::dlclose(library); }
  void *egl(const char *name) const { return ::dlsym(library, name); }
  void *procedure(const char *name) const { return reinterpret_cast<void *>(get(name)); }
};
} // namespace
std::vector<Provider> guest_graphics_providers(const std::filesystem::path &shim) {
  auto state = std::make_shared<Graphics>(shim);
  Provider egl{"libEGL.so", {}, {}, false};
  Provider gl{"libGLESv3.so", {}, {}, false};
  auto add = [&](Provider &provider, const char *name, auto function) {
    if (!function) return; // An unavailable native feature remains unavailable.
    auto entry = fixed_export(name, "", function);
    state->procedures.emplace(name, entry);
    provider.exports.push_back(std::move(entry));
  };
  // EGL 1.4 uses 32-bit EGLint attributes on both sides. EGL1.5 platform/image
  // entry points with intptr-sized attribute lists are deliberately absent.
#define EGL_CORE(NAME) add(egl, #NAME, reinterpret_cast<decltype(&::NAME)>(state->egl(#NAME)))
  EGL_CORE(eglGetError); EGL_CORE(eglGetDisplay); EGL_CORE(eglInitialize);
  EGL_CORE(eglTerminate); EGL_CORE(eglQueryString); EGL_CORE(eglGetConfigs);
  EGL_CORE(eglChooseConfig); EGL_CORE(eglGetConfigAttrib); EGL_CORE(eglCreateWindowSurface);
  EGL_CORE(eglCreatePbufferSurface); EGL_CORE(eglCreatePixmapSurface); EGL_CORE(eglDestroySurface);
  EGL_CORE(eglQuerySurface); EGL_CORE(eglBindAPI); EGL_CORE(eglQueryAPI);
  EGL_CORE(eglWaitClient); EGL_CORE(eglReleaseThread); EGL_CORE(eglCreatePbufferFromClientBuffer);
  EGL_CORE(eglSurfaceAttrib); EGL_CORE(eglBindTexImage); EGL_CORE(eglReleaseTexImage);
  EGL_CORE(eglSwapInterval); EGL_CORE(eglCreateContext); EGL_CORE(eglDestroyContext);
  EGL_CORE(eglMakeCurrent); EGL_CORE(eglGetCurrentContext); EGL_CORE(eglGetCurrentSurface);
  EGL_CORE(eglGetCurrentDisplay); EGL_CORE(eglQueryContext); EGL_CORE(eglWaitGL);
  EGL_CORE(eglWaitNative); EGL_CORE(eglSwapBuffers); EGL_CORE(eglCopyBuffers);
#undef EGL_CORE
#define EGL_EXTENSION(NAME, TYPE) add(egl, #NAME, reinterpret_cast<TYPE>(state->procedure(#NAME)))
  EGL_EXTENSION(eglCreateImageKHR, PFNEGLCREATEIMAGEKHRPROC);
  EGL_EXTENSION(eglDestroyImageKHR, PFNEGLDESTROYIMAGEKHRPROC);
  EGL_EXTENSION(eglGetNativeClientBufferANDROID, PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC);
  EGL_EXTENSION(eglSetDamageRegionKHR, PFNEGLSETDAMAGEREGIONKHRPROC);
  EGL_EXTENSION(eglSwapBuffersWithDamageKHR, PFNEGLSWAPBUFFERSWITHDAMAGEKHRPROC);
  EGL_EXTENSION(eglSwapBuffersWithDamageEXT, PFNEGLSWAPBUFFERSWITHDAMAGEEXTPROC);
#undef EGL_EXTENSION
#define GUEST_GL(NAME) add(gl, #NAME, reinterpret_cast<decltype(&::NAME)>(state->procedure(#NAME)));
#include "shiraz/darwin/guest/guestegl_symbols.inc"
#undef GUEST_GL
  // Preserve shim routing for glBindFramebuffer and all its window work.
  // The host procedure resolver is consulted only for this typed whitelist.
  auto get = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const char *name = cursor.read<const char *>();
    HostErrno preserve;
    uintptr_t address = 0;
    if (name && ::strnlen(name, 129) <= 128) {
      auto found = state->procedures.find(name);
      if (found != state->procedures.end()) address = found->second.address;
    }
    return_value(capture, address);
  });
  ProviderExport getter{"eglGetProcAddress", "", reinterpret_cast<uintptr_t>(get),
                         SymbolKind::Function, "graphics/typed-whitelist-guest-procedures", false};
  state->procedures.emplace(getter.name, getter);
  egl.exports.push_back(std::move(getter));
  // The getter's VM-lifetime closure retains state and its explicit native
  // library handle for every thunk, even if a Loader is destroyed earlier.
  Provider gl2 = gl; gl2.soname = "libGLESv2.so";
  return {std::move(egl), std::move(gl), std::move(gl2)};
}
} // namespace shiraz::guest
