// SPDX-License-Identifier: GPL-3.0-only
// Drop the caller's reference after creating a real NativeBridge namespace.
#include <nativebridge/native_bridge.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
static void require(bool value, const char *message) {
  if (!value) { std::fprintf(stderr, "%s\n", message); std::exit(1); }
}
int main(int argc, char **argv) {
  require(argc == 3, "usage: namespace-lifetime-probe bridge guest-elf");
  void *owner = ::dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
  require(owner, "open bridge");
  auto *table = static_cast<android::NativeBridgeCallbacks *>(::dlsym(owner, "NativeBridgeItf"));
  require(table && table->loadLibrary && table->isSupported, "bridge callbacks");
  require(table->loadLibrary(argv[2], RTLD_NOW), "load real guest ELF");
  Dl_info before{}, after{};
  require(::dladdr(table, &before) && before.dli_fbase, "bridge initially resident");
  require(!::dlclose(owner), "drop caller bridge reference");
  require(::dladdr(table, &after) && after.dli_fbase == before.dli_fbase,
          "namespace lost bridge mapping after reference drop");
  require(table->isSupported(argv[2]), "resident bridge callback after reference drop");
  std::puts("M5_NAMESPACE_LIFETIME_PASS owner_reference_dropped=1 bridge_resident=1 callback=1 process_lifetime=1 explicit_unmap=0");
}
