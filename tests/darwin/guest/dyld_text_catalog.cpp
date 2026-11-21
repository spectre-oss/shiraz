// SPDX-License-Identifier: GPL-3.0-only
// Disposable probe helper. ABI field offsets follow Apple's dyld SPI:
// https://github.com/apple-oss-distributions/dyld/blob/main/include/mach-o/dyld_priv.h
// Emits only cache UUID and unslid text intervals, never image names or paths.
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

using GetUuid = bool (*)(unsigned char*);
using Iterate = int (*)(const unsigned char*, void (^)(const void*));
struct Span {
    uint64_t start, size;
};
static_assert(sizeof(Span) == 16);

int main() {
    auto uuid_fn = reinterpret_cast<GetUuid>(dlsym(RTLD_DEFAULT, "_dyld_get_shared_cache_uuid"));
    auto iterate = reinterpret_cast<Iterate>(dlsym(RTLD_DEFAULT, "dyld_shared_cache_iterate_text"));
    unsigned char uuid[16]{};
    if (!uuid_fn || !iterate || !uuid_fn(uuid))
        return 2;
    std::vector<Span> spans;
    auto* output = &spans;
    __block bool bad = false;
    unsigned before = _dyld_image_count();
    int status = iterate(uuid, ^(const void* entry) {
      if (!entry || bad) {
          bad = true;
          return;
      }
      uint64_t version = 0, start = 0, size = 0;
      auto data = static_cast<const unsigned char*>(entry);
      std::memcpy(&version, data, 8);
      std::memcpy(&start, data + 8, 8);
      std::memcpy(&size, data + 16, 8);
      if ((version != 1 && version != 2) || !size || start > UINT64_MAX - size ||
          output->size() >= 65536) {
          bad = true;
          return;
      }
      try {
          output->push_back({start, size});
      } catch (...) {
          bad = true;
      }
    });
    if (status || bad || spans.empty())
        return 3;
    uint32_t count = static_cast<uint32_t>(spans.size());
    if (std::fwrite(uuid, 1, sizeof(uuid), stdout) != sizeof(uuid) ||
        std::fwrite(&count, sizeof(count), 1, stdout) != 1 ||
        std::fwrite(spans.data(), sizeof(Span), count, stdout) != count || std::fflush(stdout))
        return 4;
    std::fprintf(stderr, "SHIRAZ_CACHE_HELPER spans=%u images_before=%u images_after=%u\n", count,
                 before, _dyld_image_count());
    return 0;
}
