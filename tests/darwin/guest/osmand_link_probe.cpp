// SPDX-License-Identifier: GPL-3.0-only
// Real library link-only diagnostic. Never executes constructors or JNI_OnLoad.
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/binding_evidence.hpp"
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/bitmap.hpp"
#include "shiraz/darwin/guest/guestegl.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include <cstdio>
#include <filesystem>
#include <cstring>
#include <fstream>
#include <map>
#include <tuple>
#include <unistd.h>
using namespace shiraz::guest;
namespace {
const char *binding_order[] = {"libosmand.so", "libc++_shared.so", "libQt5Core.so",
  "libQt5Network.so", "libQt5Sql.so", "libOsmAndCoreWithJNI.so", "libandroidx.graphics.path.so"};
int binding_report(const char *directory, const char *output) {
  Loader *active = nullptr;
  auto mutex = std::make_shared<std::recursive_mutex>();
  auto validate = [&](const void *pointer) { return active && active->contains_code(pointer); };
  auto lifecycle = std::make_shared<Lifecycle>(validate);
  auto signals = std::make_shared<SignalRuntime>(validate);
  auto cleanup = std::make_shared<CleanupRuntime>(validate);
  auto native = native_personality(validate, lifecycle, signals, cleanup);
  if (const char *shim = std::getenv("SHIRAZ_GUEST_EGL_SHIM"))
    for (auto &provider : guest_graphics_providers(shim)) native.add(std::move(provider));
  native.add(bitmap_provider([](void *) -> void * {
    throw ElfError("binding-only bitmap provider has no live JNI environment");
  }));
  auto registry = dynamic_personality(std::move(native), {[&] { return active; }, mutex, [] {}});
  Loader loader({std::filesystem::canonical(directory)}, std::move(registry));
  active = &loader;
  lifecycle->attach(loader);
  signals->attach(loader);
  cleanup->attach(loader);
  // Preserve the explicit System.load order of the real JNI gate. Default
  // local scope plus each ELF's GLOBAL flags follow the production Loader.
  // No constructor, JNI callback or app code executes in this namespace.
  for (const char *name : binding_order) loader.link(name);
  if (loader.constructor_count() || loader.image_count() != 7)
    throw ElfError("binding namespace expected seven images and zero constructors");
  std::vector<std::string> order(std::begin(binding_order), std::end(binding_order));
  size_t count = write_binding_evidence(loader, std::filesystem::absolute(output), "ordered-link-only", order);
  std::printf("M5_OSMAND_BINDING_REPORT_PASS images=7 relocations=%zu constructors=0 app_execution=0\n", count);
  return 0;
}
}
int main(int argc, char **argv) {
  if (argc == 4 && std::string(argv[2]) == "--bindings-json") {
    try { return binding_report(argv[1], argv[3]); }
    catch (const std::exception &error) { std::fprintf(stderr, "M5_OSMAND_BINDING_REPORT_FAIL reason=%s\n", error.what()); return 1; }
  }
  if (argc != 2 && !(argc == 3 && std::string(argv[2]) == "--initialize-androidx")) { std::fprintf(stderr, "usage: osmand-link-probe unchanged-library-directory\n"); return 2; }
  const char *libraries[] = {"libandroidx.graphics.path.so", "libc++_shared.so", "libosmand.so",
    "libOsmAndCoreWithJNI.so", "libQt5Core.so", "libQt5Network.so", "libQt5Sql.so"};
  size_t linked = 0;
  for (const char *library : libraries) {
    try {
      Loader *active = nullptr;
      auto mutex = std::make_shared<std::recursive_mutex>();
      auto validate = [&](const void *pointer) { return active && active->contains_code(pointer); };
      auto lifecycle = std::make_shared<Lifecycle>(validate);
      auto signals=std::make_shared<SignalRuntime>(validate);
      auto cleanup=std::make_shared<CleanupRuntime>(validate);
      auto native = native_personality(validate, lifecycle, signals, cleanup);
      if (const char *shim = std::getenv("SHIRAZ_GUEST_EGL_SHIM"))
        for (auto &provider : guest_graphics_providers(shim)) native.add(std::move(provider));
      native.add(bitmap_provider([](void *) -> void * {
        throw ElfError("link-only bitmap provider cannot supply a live ART JNI environment");
      }));
      auto registry = dynamic_personality(std::move(native),
          {[&] { return active; }, mutex, [] {}});
      Loader loader({std::filesystem::canonical(argv[1])}, std::move(registry));
      active = &loader;
      lifecycle->attach(loader);
      signals->attach(loader);
      cleanup->attach(loader);
      loader.link(library);
      if (loader.constructor_count()) throw ElfError("link-only diagnostic executed a constructor");
      std::printf("M5_OSMAND_LINK library=%s images=%zu bindings=%zu rewritten=%zu constructors=0\n",
                  library, loader.image_count(), loader.bindings().size(), loader.rewritten_thread_pointers());
      ++linked;
      if (argc == 3 && std::string(library) == "libandroidx.graphics.path.so") {
        loader.open(library);
        std::printf("M5_UNCHANGED_ANDROIDX_INITIALIZED constructors=%zu pending_finalizers=%zu jni_onload=not_called\n",
                    loader.constructor_count(), lifecycle->pending());
      }
    } catch (const std::exception &error) {
      std::fprintf(stderr, "M5_OSMAND_LINK_REJECT library=%s reason=%s\n", library, error.what());
    }
  }
  std::printf("M5_OSMAND_LINK_RESULT linked=%zu required=7 constructors_executed=0 app_execution=0\n", linked);
  return linked == 7 ? 0 : 1;
}
