// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/fs.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include <cstring>
#include <filesystem>
#include <iostream>
#include <sys/mount.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: fs-probe fixture-directory");
    struct statfs native; require(!::statfs(".", &native), "read actual host mount statistics");
    bool known = false;
    for (const char *name : {"nfs", "msdos", "exfat", "cd9660", "udf", "ext2fs"}) known |= !std::strcmp(native.f_fstypename, name);
    auto base = file_personality(); Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") { auto filesystems = filesystem_exports(); provider.exports.insert(provider.exports.end(), filesystems.begin(), filesystems.end()); }
      providers.add(std::move(provider));
    }
    Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
    auto *module = loader.open("libfs.so");
    errno = ELOOP;
    auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_fs_stats").address), {Argument::integer(known)}).result_integers[0];
    if (result) throw std::runtime_error("guest filesystem stats failed at line " + std::to_string(result));
    require(errno == ELOOP, "filesystem stats preserve host errno");
    std::cout << "M5_FS_PASS host_type=" << native.f_fstypename << " real_mapped=" << known
              << " unsupported_type_explicit=" << !known << " layout=120 invalid_fd=1 no_fake_magic=1\n";
    return 0;
  } catch (const std::exception &error) { std::cerr << "fs-probe: " << error.what() << '\n'; return 1; }
}
