// SPDX-License-Identifier: GPL-3.0-only
#include <dlfcn.h>
#include <link.h>
#include <stdint.h>

static volatile int initialized;
__attribute__((constructor)) static void initialize(void) { initialized = 73; }
int dynamic_value(void) { return initialized + 34; }
struct Walk { int seen, stop, valid, reentered; };
static int visit(struct dl_phdr_info *info, size_t size, void *opaque) {
  struct Walk *walk = opaque;
  ++walk->seen;
  if (size != sizeof(*info) || !info->dlpi_name || !info->dlpi_phdr ||
      !info->dlpi_phnum || info->dlpi_phnum > 128 || !info->dlpi_adds ||
      info->dlpi_subs || info->dlpi_tls_modid || info->dlpi_tls_data) walk->valid = 0;
  int loads = 0;
  for (unsigned i = 0; i < info->dlpi_phnum; ++i) {
    const ElfW(Phdr) *header = &info->dlpi_phdr[i];
    if (header->p_type == PT_LOAD) {
      ++loads;
      if (header->p_filesz > header->p_memsz || !header->p_memsz) walk->valid = 0;
      if (header->p_flags & PF_X) {
        const volatile uint32_t *code = (const void *)(info->dlpi_addr + header->p_vaddr);
        (void)*code;
      }
    }
  }
  if (!loads) walk->valid = 0;
  void *handle = dlopen("libdynamic.so", RTLD_NOW | RTLD_NOLOAD);
  if (handle && !dlclose(handle)) ++walk->reentered;
  else walk->valid = 0;
  return walk->stop;
}
int dynamic_walk(int stop) {
  struct Walk walk = {0, stop, 1, 0};
  int result = dl_iterate_phdr(visit, &walk);
  if (!walk.valid || !walk.seen || walk.reentered != walk.seen || result != stop) return -1;
  return walk.seen;
}
int dynamic_roundtrip(void) {
  void *handle = dlopen("libdynamic.so", RTLD_NOW | RTLD_NOLOAD);
  if (!handle) return -1;
  int (*value)(void) = dlsym(handle, "dynamic_value");
  Dl_info info = {0};
  if (!value || value() != 107 || !dladdr((const void *)value, &info) ||
      !info.dli_fname || !info.dli_fbase || !info.dli_sname || info.dli_saddr != (void *)value) return -2;
  return dlclose(handle) ? -3 : value();
}
