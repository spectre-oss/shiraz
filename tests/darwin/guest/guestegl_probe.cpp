// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/guestegl.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <EGL/egl.h>
#include <dlfcn.h>
#include <filesystem>
#include <iostream>

using namespace shiraz::guest;
namespace {
void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 3, "usage: guestegl-probe fixture-directory absolute-libEGL_shim.so");
    std::filesystem::path shim = std::filesystem::canonical(argv[2]);
    Providers providers;
    auto core = core_personality();
    for (const auto &entry : core.modules()) providers.add(entry.second);
    auto graphics = guest_graphics_providers(shim);
    size_t gl_functions = graphics[1].exports.size();
    for (auto &provider : graphics) providers.add(std::move(provider));
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("libguestegl.so");
    auto *gles = loader.link("libGLESv3.so");
#define GUEST_GL_REQUIRED(NAME) require(loader.symbol(gles, #NAME).found, "required observed GLES import " #NAME);
#include "shiraz/darwin/guest/guestegl_required.inc"
#undef GUEST_GL_REQUIRED
    uintptr_t procedures[3]{};
    errno = ELOOP;
    auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_guestegl").address),
        {Argument::integer(int64_t(reinterpret_cast<uintptr_t>(procedures)), ArgumentType::Int64)});
    if (int(result.result_integers[0]) != 73)
      throw std::runtime_error("real guest EGL/GLES flow failed at fixture line " + std::to_string(result.result_integers[0]));
    require(errno == ELOOP, "graphics adapters preserve host errno");
    const char *names[] = {"glColorMask", "glClearColor", "glReadPixels"};
    // Open the explicit shim again solely for an independent no-raw-pointer
    // check; never use a process-global lookup or accept native fallback.
    void *native = ::dlopen(shim.c_str(), RTLD_NOW | RTLD_LOCAL);
    require(native, "open explicit native shim for address verification");
    auto get = reinterpret_cast<PFNEGLGETPROCADDRESSPROC>(::dlsym(native, "eglGetProcAddress"));
    require(get, "explicit native resolver exists");
    for (size_t i = 0; i < 3; ++i)
      require(procedures[i] && procedures[i] == loader.symbol(gles, names[i]).address &&
                  procedures[i] != reinterpret_cast<uintptr_t>(get(names[i])),
              "dynamic procedures equal guest exports and differ from native pointers");
    ::dlclose(native);
    std::cout << "M5_GUEST_EGL_PASS real_angle_pbuffer=1 rgba_readback=1 poisoned_glboolean=1 typed_gles="
              << gl_functions << " no_raw_proc=1 unknown_rejected=1 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "guestegl-probe: " << error.what() << '\n';
    return 1;
  }
}
