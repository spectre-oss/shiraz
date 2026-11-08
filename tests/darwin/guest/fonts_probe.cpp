// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/fonts.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <array>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sys/stat.h>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) {
  if (!value)
    throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value),
                           ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: fonts-probe fixture-directory");
    char temporary[] = "/tmp/shiraz-font-view-XXXXXX";
    char *created = mkdtemp(temporary);
    require(created, "exclusive font test directory");
    fs::path work = fs::canonical(created), root = work / "fonts";
    fs::create_directory(root);
    struct Cleanup {
      fs::path path;
      ~Cleanup() {
        std::error_code error;
        fs::remove_all(path, error);
      }
    } cleanup{work};
    const std::string xml =
        "<familyset version=\"21\"><family "
        "name=\"test\"><font>Roboto-Regular.ttf</font></family></familyset>\n";
    {
      std::ofstream(root / "android-fonts.xml") << xml;
      std::ofstream(root / "Roboto-Regular.ttf") << "font-fixture-bytes";
      std::ofstream(root / "unrelated") << "not-a-font";
    }
    const char *old = std::getenv("SHIRAZ_ANDROID_FONT_DIR");
    std::string previous = old ? old : "";
    bool was_set = old != nullptr;
    struct Environment {
      std::string previous;
      bool set;
      ~Environment() {
        if (set)
          setenv("SHIRAZ_ANDROID_FONT_DIR", previous.c_str(), 1);
        else
          unsetenv("SHIRAZ_ANDROID_FONT_DIR");
      }
    } environment{previous, was_set};
    require(!setenv("SHIRAZ_ANDROID_FONT_DIR", root.c_str(), 1),
            "set explicit absolute font root");
    const char *old_root=std::getenv("ANDROID_ROOT");
    struct AndroidRoot {std::string old;bool set;~AndroidRoot(){if(set)setenv("ANDROID_ROOT",old.c_str(),1);else unsetenv("ANDROID_ROOT");}} root_environment{old_root?old_root:"",old_root!=nullptr};
    std::string android_root=(work/"art").string();require(!setenv("ANDROID_ROOT",android_root.c_str(),1),"set test ART root");
    bool handled = false;
    errno = 61;
    require(openGuestFont("/system/etc/unrelated", O_RDONLY, handled) == -1 &&
                handled && errno == ENOSYS,
            "other system paths are never offered to a host fallback");
    errno = 61;
    int fd = openGuestFont("/system/etc/fonts.xml", O_RDONLY, handled);
    require(handled && fd >= 0 && errno == 61,
            "open actual bundled-config file descriptor");
    require((fcntl(fd, F_GETFD) & FD_CLOEXEC) != 0,
            "mapped descriptor is close-on-exec");
    std::array<char, 256> bytes{};
    ssize_t length = read(fd, bytes.data(), bytes.size());
    close(fd);
    require(length == ssize_t(xml.size()) &&
                !std::memcmp(bytes.data(), xml.data(), xml.size()),
            "config mapping reads real bytes");
    std::string projected=android_root+"/fonts/Roboto-Regular.ttf";
    fd=openGuestFont(projected.c_str(),O_RDONLY,handled);require(fd>=0 && handled,"ART-root font alias has real backing");close(fd);
    require(openGuestFont((android_root+"/fonts/../android-fonts.xml").c_str(),O_RDONLY,handled)==-1 && handled && errno==ENOENT,"ART-root alias traversal refused");
    for (int flags : {O_WRONLY, O_RDWR, O_RDONLY | O_TRUNC, O_RDONLY | O_CREAT,
                      O_RDONLY | O_APPEND})
      require(openGuestFont("/system/fonts/Roboto-Regular.ttf", flags,
                            handled) == -1 &&
                  handled && errno == EROFS,
              "mapped assets reject write and creation modes");
    for (const char *path :
         {"/system/fonts/unrelated", "/system/fonts/../android-fonts.xml",
          "/system/fonts/Roboto-Regular.ttf/extra", "/system/fonts/Missing.ttf",
          "/system/etc/system_fonts.xml", "/system/etc/fallback_fonts.xml"})
      require(
          openGuestFont(path, O_RDONLY, handled) == -1 && handled &&
              errno == ENOENT,
          "unknown/traversal/legacy config never falls back to host system");
    require(openGuestFont("/system/fonts/NotoSans-Regular.ttf", O_RDONLY,
                          handled) == -1 &&
                handled && errno == ENOENT,
            "missing allowlisted asset returns its real open error");
    errno = 61;
    require(openGuestFont("/ordinary/unmapped", O_RDONLY, handled) == -1 &&
                !handled && errno == 61,
            "unmapped path remains with ordinary caller policy");
    fs::remove(root / "Roboto-Regular.ttf");
    fs::create_symlink(root / "android-fonts.xml", root / "Roboto-Regular.ttf");
    require(openGuestFont("/system/fonts/Roboto-Regular.ttf", O_RDONLY,
                          handled) == -1 &&
                handled && errno == ELOOP,
            "font symlink refused");
    fs::remove(root / "Roboto-Regular.ttf");
    fs::create_directory(root / "Roboto-Regular.ttf");
    require(openGuestFont("/system/fonts/Roboto-Regular.ttf", O_RDONLY,
                          handled) == -1 &&
                handled && errno == EINVAL,
            "font target must be regular");
    fs::remove(root / "Roboto-Regular.ttf");
    int oversized = ::open((root / "Roboto-Regular.ttf").c_str(),
                           O_WRONLY | O_CREAT | O_TRUNC, 0600);
    require(oversized >= 0 && !ftruncate(oversized, 64 * 1024 * 1024 + 1),
            "sparse oversized font fixture");
    close(oversized);
    require(openGuestFont("/system/fonts/Roboto-Regular.ttf", O_RDONLY,
                          handled) == -1 &&
                handled && errno == EINVAL,
            "font descriptor must fit the64MiB bound");
    fs::remove(root / "Roboto-Regular.ttf");
    {
      std::ofstream(root / "Roboto-Regular.ttf") << "font-fixture-bytes";
    }
    fs::create_directory_symlink(root, work / "linked-root");
    setenv("SHIRAZ_ANDROID_FONT_DIR", (work / "linked-root").c_str(), 1);
    require(openGuestFont("/system/etc/fonts.xml", O_RDONLY, handled) == -1 &&
                handled,
            "configured root cannot itself be a symlink");
    setenv("SHIRAZ_ANDROID_FONT_DIR", "relative-fonts", 1);
    require(openGuestFont("/system/etc/fonts.xml", O_RDONLY, handled) == -1 &&
                handled && errno == EINVAL,
            "configured root must be absolute");
    unsetenv("SHIRAZ_ANDROID_FONT_DIR");
    require(openGuestFont("/system/etc/fonts.xml", O_RDONLY, handled) == -1 &&
                handled && errno == ENOENT,
            "missing root is explicit, no fallback");
    setenv("SHIRAZ_ANDROID_FONT_DIR", root.c_str(), 1);
    Loader loader({argv[1]}, native_personality());
    auto *module = loader.open("libfonts.so");
    auto call = [&](const char *name, const std::vector<Argument> &args) {
      errno = 61;
      int result = int(int32_t(
          invoke(reinterpret_cast<void *>(loader.symbol(module, name).address),
                 args)
              .result_integers[0]));
      require(errno == 61, "guest font adapters preserve native errno");
      return result;
    };
    int error = 0;
    bytes.fill(0);
    require(call("fonts_stdio",
                 {pointer("/system/etc/fonts.xml"), pointer("rb"),
                  pointer(bytes.data()), Argument::integer(bytes.size()),
                  pointer(&error)}) == int(xml.size()) &&
                error == 73 &&
                !std::memcmp(bytes.data(), xml.data(), xml.size()),
            "actual guest fopen reads configured XML");
    require(call("fonts_open",
                 {pointer("/system/fonts/Roboto-Regular.ttf"),
                  Argument::integer(0), pointer(bytes.data()),
                  Argument::integer(bytes.size()), pointer(&error)}) == 18 &&
                error == 73,
            "actual guest open reads allowlisted font bytes");
    require(call("fonts_stdio",
                 {pointer("/system/etc/fonts.xml"), pointer("w"),
                  pointer(bytes.data()), Argument::integer(bytes.size()),
                  pointer(&error)}) == -1 &&
                error == 30,
            "guest stdio cannot truncate mapped config");
    require(call("fonts_open",
                 {pointer("/system/fonts/../android-fonts.xml"),
                  Argument::integer(0), pointer(bytes.data()),
                  Argument::integer(bytes.size()), pointer(&error)}) == -1 &&
                error == 2,
            "guest traversal remains explicit ENOENT");
    for (const auto &asset : std::array<std::pair<std::string, size_t>, 3>{{
             {"/system/etc/fonts.xml", xml.size()},
             {"/system/fonts/Roboto-Regular.ttf", 18}, {projected, 18}}})
      require(call("fonts_metadata", {pointer(asset.first.c_str()),
                                     Argument::integer(asset.second, ArgumentType::Int64)}) == 0,
              "guest stat/access use actual read-only font backing");
    std::cout << "M5_FONTS_PASS actual_fd=1 config=version21 guest_stdio=1 "
                 "guest_open=1 readonly=1 symlinks=denied traversal=denied "
                 "legacy_alias=0 art_root_alias=1 metadata=1\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
