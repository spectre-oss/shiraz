// SPDX-License-Identifier: GPL-3.0-only
// Read-only file view using the same project font allowlist as the Linux
// bridge. No ELF/dyld interposition or host Android filesystem fallback.
#include "shiraz/darwin/guest/fonts.hpp"
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
namespace shiraz::guest {
namespace {
int failure(int error) {
  errno = error;
  return -1;
}
const char *font_name(const char *name) {
  static const char *const names[] = {
      "Roboto-Regular.ttf",          "DroidSansMono.ttf",
      "NotoSerif-Regular.ttf",       "NotoSerif-Bold.ttf",
      "NotoSerif-Italic.ttf",        "NotoSerif-BoldItalic.ttf",
      "NotoNaskhArabic-Regular.ttf", "NotoNaskhArabic-Bold.ttf",
      "NotoSans-Regular.ttf",        "NotoSans-Bold.ttf",
      "NotoSansArabic-Regular.ttf",  "NotoSansArabic-Bold.ttf",
      "NotoSansHebrew-Regular.ttf",  "NotoSansHebrew-Bold.ttf",
      "NotoSansLao-Regular.ttf",     "NotoSansLao-Bold.ttf",
      "NotoColorEmoji.ttf",          "NotoEmoji[wght].ttf"};
  for (const char *allowed : names)
    if (!std::strcmp(name, allowed))
      return allowed;
  return nullptr;
}
bool directory_ok(const char *directory) {
  if (!directory || directory[0] != '/')
    return false;
  size_t length = strnlen(directory, 4096);
  if (!length || length == 4096)
    return false;
  // The configured root is explicit, not a guest-selected traversal path.
  // Reject dot components rather than normalizing them into another root.
  for (size_t i = 1; i < length;) {
    while (i < length && directory[i] == '/')
      ++i;
    size_t start = i;
    while (i < length && directory[i] != '/')
      ++i;
    size_t count = i - start;
    if ((count == 1 && directory[start] == '.') ||
        (count == 2 && directory[start] == '.' && directory[start + 1] == '.'))
      return false;
  }
  return true;
}
void close_preserving_errno(int fd) {
  int previous = errno;
  (void)::close(fd);
  errno = previous;
}
} // namespace
int openGuestFont(const char *path, int native_flags, bool &handled) noexcept {
  handled = false;
  int previous_errno = errno;
  if (!path)
    return -1;
  if (strnlen(path, 4096) == 4096) {
    handled = true;
    return failure(ENAMETOOLONG);
  }
  const char *name = nullptr;
  const char *android_root=std::getenv("ANDROID_ROOT");
  size_t root_size=android_root ? strnlen(android_root,4096) : 0;
  bool root_font=android_root && directory_ok(android_root) && root_size<4096 &&
      !std::strncmp(path,android_root,root_size) && !std::strncmp(path+root_size,"/fonts/",7);
  if (!std::strcmp(path, "/system/etc/fonts.xml")) {
    handled = true;
    name = "android-fonts.xml";
  } else if (!std::strcmp(path, "/system/etc/system_fonts.xml") ||
             !std::strcmp(path, "/system/etc/fallback_fonts.xml")) {
    // Bundled familyset version21 is not the old nameset/fileset schema.
    // No misleading alias to an incompatible legacy parser is provided.
    handled = true;
    return failure(ENOENT);
  } else if (!std::strncmp(path, "/system/fonts/", 14)) {
    handled = true;
    name = font_name(path + 14);
    if (!name)
      return failure(ENOENT);
  } else if(root_font) {
    handled=true;name=font_name(path+root_size+7);if(!name)return failure(ENOENT);
  } else if (!std::strcmp(path, "/system") ||
             !std::strncmp(path, "/system/", 8)) {
    handled = true;
    return failure(ENOSYS);
  } else
    return -1;
  if ((native_flags & O_ACCMODE) != O_RDONLY ||
      (native_flags & (O_CREAT | O_TRUNC | O_APPEND | O_EXCL)))
    return failure(EROFS);
  if (native_flags & O_DIRECTORY)
    return failure(ENOTDIR);
  if (native_flags & ~(O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK))
    return failure(EINVAL);
  const char *directory = std::getenv("SHIRAZ_ANDROID_FONT_DIR");
  if (!directory || !directory[0])
    return failure(ENOENT);
  if (!directory_ok(directory))
    return failure(EINVAL);
  int root = ::open(directory, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
  if (root < 0)
    return -1;
  int fd = ::openat(root, name, O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC);
  close_preserving_errno(root);
  if (fd < 0)
    return -1;
  struct stat info{};
  if (::fstat(fd, &info)) {
    close_preserving_errno(fd);
    return -1;
  }
  if (!S_ISREG(info.st_mode) || info.st_size <= 0 ||
      info.st_size > 64 * 1024 * 1024) {
    close_preserving_errno(fd);
    return failure(EINVAL);
  }
  errno = previous_errno;
  return fd;
}
} // namespace shiraz::guest
