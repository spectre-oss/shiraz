// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/zlib.hpp"
#include <atomic>
#include <filesystem>
#include <iostream>
#include <thread>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: zlib-probe fixture-directory");
    Loader *owner = nullptr;
    auto providers = core_personality();
    providers.add(zlib_provider([&](const void *p) { return owner && owner->contains_code(p); }));
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    owner = &loader;
    auto *module = loader.open("libzlib-fixture.so");
    require(loader.image_count() == 1, "explicit zlib provider replaces link-only library");
    auto entry = reinterpret_cast<void *>(loader.symbol(module, "run_zlib").address);
    errno = ELOOP;
    *guest_errno_location() = 73;
    auto value = invoke(entry, {}).result_integers[0];
    if (value) throw std::runtime_error("guest zlib failed at line " + std::to_string(value));
    require(errno == ELOOP && *guest_errno_location() == 73, "zlib preserves unrelated errno");
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&] {
      for (int j = 0; j < 16; ++j)
        if (invoke(entry, {}).result_integers[0]) passed = false;
    });
    for (auto &thread : threads) thread.join();
    require(passed, "concurrent guest compression and callbacks");
    std::cout << "M5_ZLIB_PASS roundtrip=1 checksums=2 callbacks=1 reset=1 version_rejections=2 invalid_callback=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "zlib-probe: " << error.what() << '\n';
    return 1;
  }
}
