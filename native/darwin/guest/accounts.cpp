// SPDX-License-Identifier: GPL-3.0-only
// Original adapters; layouts and return contracts from pinned Android14
// pwd.h/grp.h/sys/file.h and libc/bionic/grp_pwd.cpp. No credential-store,
// shadow-password or account-enumeration interface is used.
#include "shiraz/darwin/guest/accounts.hpp"
#include <algorithm>
#include <array>
#include <grp.h>
#include <limits>
#include <pwd.h>
#include <stdexcept>
#include <sys/file.h>
#include <unistd.h>
namespace shiraz::guest {
namespace {
constexpr size_t maximum_bytes = 1u << 20, maximum_members = 4096;
struct GuestPasswd {
  char *name, *password;
  uint32_t uid, gid;
  char *gecos, *directory, *shell;
};
struct GuestGroup {
  char *name, *password;
  uint32_t gid, padding;
  char **members;
};
static_assert(sizeof(GuestPasswd) == 48 && offsetof(GuestPasswd, uid) == 16 &&
              offsetof(GuestPasswd, gecos) == 24 &&
              offsetof(GuestPasswd, shell) == 40);
static_assert(sizeof(GuestGroup) == 32 && offsetof(GuestGroup, gid) == 16 &&
              offsetof(GuestGroup, members) == 24);
class Scratch {
  std::vector<char> bytes_;
  void wipe() noexcept {
    volatile char *p = bytes_.data();
    for (size_t i = 0; i < bytes_.size(); ++i)
      p[i] = 0;
  }

public:
  ~Scratch() { wipe(); }
  void resize(size_t size) {
    wipe();
    bytes_.resize(size);
  }
  char *data() { return bytes_.data(); }
  size_t size() const { return bytes_.size(); }
};
template <class Record, class Identity, class Lookup>
int lookup(Identity identity, Record &record, Record *&found, Scratch &scratch,
           Lookup function) {
  for (size_t capacity = 4096; capacity <= maximum_bytes; capacity *= 2) {
    scratch.resize(capacity);
    found = nullptr;
    int error =
        function(identity, &record, scratch.data(), scratch.size(), &found);
    if (error != ERANGE)
      return error;
  }
  return ERANGE;
}
size_t string_size(const char *text) {
  if (!text)
    return 0;
  size_t size = strnlen(text, maximum_bytes);
  if (size == maximum_bytes)
    throw std::length_error("account field exceeds bounded storage");
  return size + 1;
}
char *copy_string(const char *source, char *&next) {
  if (!source)
    return nullptr;
  size_t length = string_size(source);
  char *result = next;
  std::memcpy(next, source, length);
  next += length;
  return result;
}
int passwd_by_uid(uint32_t uid, GuestPasswd *destination, char *buffer,
                  size_t capacity, GuestPasswd **result) {
  // This API returns an errno VALUE. It must not use errno-minus-one policy.
  if (!result)
    return errno_to_guest(EINVAL);
  *result = nullptr;
  if (!destination || !buffer)
    return errno_to_guest(EINVAL);
  try {
    Scratch scratch;
    struct passwd native{};
    struct passwd *found = nullptr;
    int error = lookup(uid, native, found, scratch, &::getpwuid_r);
    if (error)
      return errno_to_guest(error);
    if (!found)
      return errno_to_guest(ENOENT);
    const char *fields[] = {found->pw_name, found->pw_gecos, found->pw_dir,
                            found->pw_shell};
    size_t required = 0;
    for (const char *field : fields) {
      size_t size = string_size(field);
      if (size > maximum_bytes - required)
        return errno_to_guest(ERANGE);
      required += size;
    }
    if (required > capacity)
      return errno_to_guest(ERANGE);
    char *next = buffer;
    GuestPasswd output{copy_string(found->pw_name, next),
                       nullptr,
                       uint32_t(found->pw_uid),
                       uint32_t(found->pw_gid),
                       copy_string(found->pw_gecos, next),
                       copy_string(found->pw_dir, next),
                       copy_string(found->pw_shell, next)};
    std::memcpy(destination, &output, sizeof(output));
    *result = destination;
    return 0;
  } catch (const std::bad_alloc &) {
    return errno_to_guest(ENOMEM);
  } catch (const std::length_error &) {
    return errno_to_guest(ERANGE);
  }
}
struct GroupStorage {
  GuestGroup value{};
  std::vector<char> text;
  std::vector<char *> members;
};
thread_local GroupStorage group;
GuestGroup *group_by_gid(uint32_t gid) {
  try {
    Scratch scratch;
    struct group native{};
    struct group *found = nullptr;
    int error = lookup(gid, native, found, scratch, &::getgrgid_r);
    if (error) {
      *guest_errno_location() = errno_to_guest(error);
      return nullptr;
    }
    if (!found) {
      *guest_errno_location() = errno_to_guest(ENOENT);
      return nullptr;
    }
    size_t required = string_size(found->gr_name), count = 0;
    if (found->gr_mem)
      for (; count < maximum_members && found->gr_mem[count]; ++count) {
        size_t size = string_size(found->gr_mem[count]);
        if (size > maximum_bytes - required) {
          *guest_errno_location() = errno_to_guest(ERANGE);
          return nullptr;
        }
        required += size;
      }
    if (found->gr_mem && count == maximum_members && found->gr_mem[count]) {
      *guest_errno_location() = errno_to_guest(ERANGE);
      return nullptr;
    }
    GroupStorage next;
    next.text.resize(std::max(required, size_t(1)));
    next.members.resize(count + 1, nullptr);
    char *cursor = next.text.data();
    next.value.name = copy_string(found->gr_name, cursor);
    next.value.password = nullptr;
    next.value.gid = uint32_t(found->gr_gid);
    for (size_t i = 0; i < count; ++i)
      next.members[i] = copy_string(found->gr_mem[i], cursor);
    next.value.members = next.members.data();
    group = std::move(next);
    return &group.value;
  } catch (const std::bad_alloc &) {
    *guest_errno_location() = errno_to_guest(ENOMEM);
    return nullptr;
  } catch (const std::length_error &) {
    *guest_errno_location() = errno_to_guest(ERANGE);
    return nullptr;
  }
}
int lock_file(int fd, int operation) {
  if (operation & ~15) {
    errno = EINVAL;
    return -1;
  }
  int mode = operation & ~4, native = 0;
  switch (mode) {
  case 1:
    native = LOCK_SH;
    break;
  case 2:
    native = LOCK_EX;
    break;
  case 8:
    native = LOCK_UN;
    break;
  default:
    errno = EINVAL;
    return -1;
  }
  if (operation & 4)
    native |= LOCK_NB;
  return ::flock(fd, native);
}
} // namespace
std::vector<ProviderExport> accounts_exports() {
  return {fixed_export("getpwuid_r", "LIBC", &passwd_by_uid),
          fixed_export("getgrgid", "LIBC", &group_by_gid),
          fixed_export("flock", "LIBC", &lock_file, ErrnoPolicy::MinusOne)};
}
} // namespace shiraz::guest
