// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/bitmap.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/guestegl.hpp"
#include "shiraz/darwin/guest/jni.hpp"
#include "shiraz/darwin/guest/path.hpp"
#include "shiraz/darwin/guest/opensles.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include "shiraz/darwin/guest/binding_evidence.hpp"
#include <cstdio>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <filesystem>
#include <fstream>
#include <map>
#include <memory>
#include <mutex>
#include <signal.h>

namespace {
thread_local std::string last_error;
auto mutex = std::make_shared<std::recursive_mutex>();
std::shared_ptr<const shiraz::guest::CodeValidator> code_validation;
std::shared_ptr<shiraz::guest::Lifecycle> lifecycle;
std::shared_ptr<shiraz::guest::SignalRuntime> signals;
std::shared_ptr<shiraz::guest::CleanupRuntime> cleanup;
std::unique_ptr<shiraz::guest::Loader> loader;
std::vector<std::string> binding_load_order;
std::vector<int> binding_load_flags;
bool initialize(const android::NativeBridgeRuntimeCallbacks *callbacks,
                const char *, const char *) {
  try {
    shiraz::guest::initialize_jni(callbacks);
    return true;
  } catch (const std::exception &error) {
    last_error = error.what();
    return false;
  }
}
bool supported(const char *path) {
  if (!path)
    return false;
  uint8_t header[64];
  FILE *file = std::fopen(path, "rb");
  if (!file)
    return false;
  bool read = std::fread(header, 1, sizeof(header), file) == sizeof(header);
  std::fclose(file);
  return read && std::memcmp(header, "\177ELF\2\1\1", 7) == 0 &&
         header[16] == 3 && header[17] == 0 && header[18] == 183 &&
         header[19] == 0;
}
void *load(const char *path, int flags) {
  try {
    if (!supported(path))
      throw shiraz::guest::ElfError("requires AArch64 guest ELF path");
    int mode = flags & (RTLD_LAZY | RTLD_NOW);
    if ((mode != RTLD_LAZY && mode != RTLD_NOW) ||
        (flags & ~(RTLD_LAZY | RTLD_NOW | RTLD_GLOBAL | RTLD_LOCAL)))
      throw shiraz::guest::ElfError(
          "unsupported Darwin NativeBridge load flags");
    std::lock_guard<std::recursive_mutex> lock(*mutex);
    auto canonical = std::filesystem::canonical(path);
    if (!loader) {
      std::function<void()> close_audio;
      lifecycle = std::make_shared<shiraz::guest::Lifecycle>(shiraz::guest::is_guest_code);
      signals = std::make_shared<shiraz::guest::SignalRuntime>(shiraz::guest::is_guest_code);
      cleanup = std::make_shared<shiraz::guest::CleanupRuntime>(shiraz::guest::is_guest_code);
      loader = std::make_unique<shiraz::guest::Loader>(
          std::vector<std::filesystem::path>{canonical.parent_path()},
          shiraz::guest::dynamic_personality(
              [&] {
                auto providers = shiraz::guest::native_personality(shiraz::guest::is_guest_code, lifecycle, signals, cleanup);
                if(const char *shim=std::getenv("SHIRAZ_GUEST_EGL_SHIM")) {
                  for(auto &provider:shiraz::guest::guest_graphics_providers(shim))providers.add(std::move(provider));
                  providers.add(shiraz::guest::android_provider(std::filesystem::path(shim).parent_path(),[](void *env)->void * {
                    return shiraz::guest::host_env(static_cast<JNIEnv *>(env));
                  }));
                }
                providers.add(shiraz::guest::bitmap_provider([](void *env) -> void * {
                  return shiraz::guest::host_env(static_cast<JNIEnv *>(env));
                }));
                providers.add(shiraz::guest::opensles_provider(shiraz::guest::is_guest_code,close_audio));
                return providers;
              }(),
              {[] { return loader.get(); }, mutex, [] {
                 std::atomic_store(&code_validation,
                     std::make_shared<const shiraz::guest::CodeValidator>(loader->code_validator()));
                 shiraz::guest::set_path_reader_validator(
                     loader->code_validator("libandroidx.graphics.path.so"));
               }}));
      loader->add_preunmap_hook(std::move(close_audio));
      lifecycle->attach(*loader);
      signals->attach(*loader);
      cleanup->attach(*loader);
    }
    size_t before = loader->constructor_count(),
           records = loader->bindings().size();
    loader->link(canonical, flags & RTLD_GLOBAL);
    std::atomic_store(&code_validation,
        std::make_shared<const shiraz::guest::CodeValidator>(loader->code_validator()));
    shiraz::guest::set_path_reader_validator(
        loader->code_validator("libandroidx.graphics.path.so"));
    if (const char *trace = std::getenv("SHIRAZ_GUEST_TRACE_IMAGES");
        trace && std::strcmp(trace, "0")) {
      for (const auto &image : loader->module_info())
        std::fprintf(stderr, "SHIRAZ_GUEST_IMAGE name=%s bias=0x%llx\n",
                     image.name.c_str(), static_cast<unsigned long long>(image.load_bias));
    }
    if (const char *report = std::getenv("SHIRAZ_GUEST_BINDING_REPORT"); report && *report) {
      if (binding_load_order.size() >= 1024) throw shiraz::guest::ElfError("binding report load-order limit");
      binding_load_order.push_back(canonical.filename().string());
      binding_load_flags.push_back(flags);
      shiraz::guest::write_binding_evidence(*loader, report, "nativebridge", binding_load_order, binding_load_flags);
    }
    auto *handle = loader->open(canonical, flags & RTLD_GLOBAL);
    for (const auto &image : loader->module_info()) {
      const auto &m = image.metrics;
      std::fprintf(stderr,
          "SHIRAZ_GUEST_PHASE module=%s parse_ns=%llu map_ns=%llu rewrite_ns=%llu "
          "bind_ns=%llu init_ns=%llu expected_constructors=%zu executed_constructors=%zu initialized=%d\n",
          std::filesystem::path(image.name).filename().c_str(),
          static_cast<unsigned long long>(m.parse_ns), static_cast<unsigned long long>(m.map_ns),
          static_cast<unsigned long long>(m.rewrite_ns), static_cast<unsigned long long>(m.bind_ns),
          static_cast<unsigned long long>(m.init_ns), m.expected_constructors,
          m.executed_constructors, m.initialized ? 1 : 0);
    }
    if (const char *trace = std::getenv("SHIRAZ_GUEST_TRACE_BINDINGS");
        trace && std::strcmp(trace, "0")) {
      for (size_t i = records; i < loader->bindings().size(); ++i) {
        const auto &record = loader->bindings()[i];
        std::fprintf(stderr,
                     "SHIRAZ_GUEST_BIND module=%s symbol=%s version=%s "
                     "provider=%s abi=%s\n",
                     record.module.c_str(), record.symbol.c_str(),
                     record.version.c_str(), record.binding.provider.c_str(),
                     record.binding.abi.c_str());
      }
    }
    std::fprintf(stderr,
                 "SHIRAZ_GUEST_ELF_LOADED constructors=%zu images=%zu\n",
                 loader->constructor_count() - before, loader->image_count());
    std::fprintf(stderr, "SHIRAZ_GUEST_TPIDR_REWRITTEN sites=%zu\n",
                 loader->rewritten_thread_pointers());
    return handle;
  } catch (const std::exception &error) {
    last_error = error.what();
    std::fprintf(stderr, "SHIRAZ_GUEST_LOAD_FAILED reason=%s\n", last_error.c_str());
    return nullptr;
  }
}
void *trampoline(void *handle, const char *name, const char *shorty,
                 uint32_t length) {
  try {
    if (!name)
      throw shiraz::guest::ElfError("null guest symbol name");
    std::lock_guard<std::recursive_mutex> lock(*mutex);
    if (!loader || !loader->valid_handle(handle))
      throw shiraz::guest::ElfError("invalid guest library handle");
    auto binding = loader->symbol(
        static_cast<shiraz::guest::Loader::Module *>(handle), name);
    void *function = reinterpret_cast<void *>(binding.address);
    if (!loader->contains_code(function))
      throw shiraz::guest::ElfError("guest JNI symbol is not executable");
    if (shorty) {
      // This standalone ART passes length=0 even with a valid shorty.
      size_t actual = strnlen(shorty, 258);
      if (!actual || actual > 257 || (length && length != actual))
        throw shiraz::guest::ElfError("invalid guest native shorty length");
      return shiraz::guest::native_trampoline(function,
                                              std::string(shorty, actual));
    }
    if (!std::strcmp(name, "JNI_OnLoad"))
      return shiraz::guest::onload_trampoline(function, false, binding.provider);
    if (!std::strcmp(name, "JNI_OnUnload"))
      return shiraz::guest::onload_trampoline(function, true);
    throw shiraz::guest::ElfError(
        "guest symbol requires an explicit ABI signature");
  } catch (const std::exception &error) {
    last_error = error.what();
    return nullptr;
  }
}
const char *error() {
  return last_error.empty() ? nullptr : last_error.c_str();
}
bool compatible(uint32_t version) { return version >= 1 && version <= 3; }
const android::NativeBridgeRuntimeValues *environment(const char *) {
  return nullptr;
}
// Opt-in ART integration probe only. Normal bridge execution advertises no
// managed signal handlers. Keep the probe process-resident, like the bridge;
// never remove a special handler while ART threads might dispatch through it.
std::atomic<unsigned> signal_probe_deliveries{0}, signal_probe_queries{0};
std::atomic<unsigned> signal_probe_handled{0}, signal_probe_forwarded{0};
std::atomic<bool> signal_probe_claim{true};
static_assert(std::atomic<unsigned>::is_always_lock_free &&
              std::atomic<bool>::is_always_lock_free);
void signal_probe_fallback(int number) {
  if (number == SIGWINCH)
    signal_probe_forwarded.fetch_add(1, std::memory_order_relaxed);
}
bool signal_probe(int number, siginfo_t *, void *) {
  if (number != SIGWINCH) return false;
  signal_probe_deliveries.fetch_add(1, std::memory_order_relaxed);
  const bool claim = signal_probe_claim.load(std::memory_order_relaxed);
  if (claim) signal_probe_handled.fetch_add(1, std::memory_order_relaxed);
  return claim;
}
android::NativeBridgeSignalHandlerFn signal_handler(int number) {
  static const bool probe = [] {
    const char *value = std::getenv("SHIRAZ_GUEST_SIGNAL_CHAIN_CHECK");
    if (!value || std::strcmp(value, "1")) return false;
    struct sigaction previous{}, fallback{};
    if (::sigaction(SIGWINCH, nullptr, &previous) ||
        (previous.sa_flags & SA_SIGINFO) ||
        (previous.sa_handler != SIG_DFL && previous.sa_handler != SIG_IGN)) {
      std::fprintf(stderr, "M5_ART_NATIVEBRIDGE_SIGNAL_FAIL foreign signal owner\n");
      return false;
    }
    // libsigchain retains callback pointers. Keep one reference to this exact
    // already loaded image for process lifetime, even if its owner dlcloses it.
    // NOLOAD forbids loading another image or falling back to a host search.
    Dl_info image{};
    if (!dladdr(reinterpret_cast<const void *>(signal_probe), &image) || !image.dli_fname ||
        !dlopen(image.dli_fname, RTLD_NOLOAD | RTLD_LAZY)) {
      std::fprintf(stderr, "M5_ART_NATIVEBRIDGE_SIGNAL_FAIL callback image retention\n");
      return false;
    }
    fallback.sa_handler = signal_probe_fallback;
    fallback.sa_flags = SA_RESTART;
    sigemptyset(&fallback.sa_mask);
    // Install before ART claims this signal, so it captures this native user
    // action behind the special handler. Refuse unrelated pre-existing owners.
    if (::sigaction(SIGWINCH, &fallback, nullptr)) {
      std::fprintf(stderr, "M5_ART_NATIVEBRIDGE_SIGNAL_FAIL fallback installation\n");
      return false;
    }
    return true;
  }();
  if (!probe) return nullptr;
  signal_probe_queries.fetch_add(1, std::memory_order_relaxed);
  // SIGWINCH precedes NSIG-1 on Darwin. At this query ART must already have
  // installed our prior callback. raise() delivers synchronously on this
  // thread; the callback itself uses only a lock-free counter.
  static_assert(SIGWINCH < NSIG - 1);
  if (number == NSIG - 1) {
    const int first = ::raise(SIGWINCH);
    signal_probe_claim.store(false, std::memory_order_relaxed);
    const int second = ::raise(SIGWINCH);
    const auto queries = signal_probe_queries.load(std::memory_order_relaxed);
    const auto deliveries = signal_probe_deliveries.load(std::memory_order_relaxed);
    const auto handled = signal_probe_handled.load(std::memory_order_relaxed);
    const auto forwarded = signal_probe_forwarded.load(std::memory_order_relaxed);
    if (!first && !second && queries == NSIG - 1 && deliveries == 2 &&
        handled == 1 && forwarded == 1)
      std::fprintf(stderr, "M5_ART_NATIVEBRIDGE_SIGNAL_PASS queries=%u signal=%d deliveries=2 handled=1 forwarded=1\n",
                   queries, SIGWINCH);
    else
      std::fprintf(stderr, "M5_ART_NATIVEBRIDGE_SIGNAL_FAIL queries=%u signal=%d deliveries=%u handled=%u forwarded=%u statuses=%d,%d\n",
                   queries, SIGWINCH, deliveries, handled, forwarded, first, second);
  }
  return number == SIGWINCH ? signal_probe : nullptr;
}
int unload(void *handle) {
  std::lock_guard<std::recursive_mutex> lock(*mutex);
  if (loader && loader->valid_handle(handle))
    return 0;
  last_error = "invalid guest library handle";
  return -1;
} // Code and registered JNI entry points remain pinned until VM exit.
} // namespace
namespace shiraz::guest {
bool is_guest_code(const void *pointer) {
  auto validate = std::atomic_load(&code_validation);
  return validate && (*validate)(pointer);
}
} // namespace shiraz::guest
extern "C" {
// Opt-in probe observation for the standalone reference-drop fixture. It is
// never a guest implementation or an arbitrary asynchronous teardown promise.
__attribute__((visibility("default"))) uint64_t shiraz_guest_signal_probe_snapshot() {
  const uint64_t queries = signal_probe_queries.load(std::memory_order_relaxed);
  const uint64_t deliveries = signal_probe_deliveries.load(std::memory_order_relaxed);
  const uint64_t handled = signal_probe_handled.load(std::memory_order_relaxed);
  const uint64_t forwarded = signal_probe_forwarded.load(std::memory_order_relaxed);
  if ((queries | deliveries | handled | forwarded) > 0xffff) return UINT64_MAX;
  return (queries << 48) | (deliveries << 32) | (handled << 16) | forwarded;
}
__attribute__((visibility(
    "default"))) android::NativeBridgeCallbacks NativeBridgeItf = [] {
  android::NativeBridgeCallbacks table{};
  table.version = 3;
  table.initialize = initialize;
  table.loadLibrary = load;
  table.getTrampoline = trampoline;
  table.isSupported = supported;
  table.getAppEnv = environment;
  table.isCompatibleWith = compatible;
  table.getSignalHandler = signal_handler;
  table.unloadLibrary = unload;
  table.getError = error;
  table.isPathSupported = [](const char *) {
    last_error = "guest namespaces are not implemented";
    return false;
  };
  table.createNamespace = [](const char *, const char *, const char *, uint64_t,
                             const char *, android::native_bridge_namespace_t *)
      -> android::native_bridge_namespace_t * {
    last_error = "guest namespaces are not implemented";
    return nullptr;
  };
  table.linkNamespaces = [](android::native_bridge_namespace_t *,
                            android::native_bridge_namespace_t *,
                            const char *) {
    last_error = "guest namespaces are not implemented";
    return false;
  };
  table.loadLibraryExt = [](const char *, int,
                            android::native_bridge_namespace_t *) -> void * {
    last_error = "guest namespaces are not implemented";
    return nullptr;
  };
  return table;
}();
}
