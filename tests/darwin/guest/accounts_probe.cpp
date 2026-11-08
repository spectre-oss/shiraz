// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/accounts.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cstring>
#include <fcntl.h>
#include <grp.h>
#include <iostream>
#include <pwd.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
namespace {
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
struct Descriptor {
  int value;
  ~Descriptor() {
    if (value >= 0)
      ::close(value);
  }
};
void require(bool value, const char *message) {
  if (!value)
    throw std::runtime_error(message);
}
Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value),
                           ArgumentType::Int64);
}
bool same(const char *a, const char *b) {
  return (!a && !b) || (a && b && !std::strcmp(a, b));
}
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: accounts-probe fixture-directory");
    auto base = native_personality();
    Providers providers;
    for (const auto &pair : base.modules()) {
      Provider module = pair.second;
      if (module.soname == "libc.so") {
        auto additions = accounts_exports();
        module.exports.erase(
            std::remove_if(module.exports.begin(), module.exports.end(),
                           [&](const auto &entry) {
                             return std::any_of(
                                 additions.begin(), additions.end(),
                                 [&](const auto &addition) {
                                   return entry.name == addition.name;
                                 });
                           }),
            module.exports.end());
        module.exports.insert(module.exports.end(), additions.begin(),
                              additions.end());
      }
      providers.add(std::move(module));
    }
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libaccounts.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments) {
      errno = 61;
      auto value =
          invoke(reinterpret_cast<void *>(loader.symbol(module, name).address),
                 arguments)
              .result_integers[0];
      require(errno == 61, "account/lock adapter preserves native errno");
      return value;
    };
    // Only ordinary OS APIs for this process's own identities are queried.
    // Compare privately; no names, home paths, member names or password fields
    // are printed, logged, serialized or committed as test evidence.
    std::vector<char> native_buffer(1u << 20);
    struct passwd native{}, *found = nullptr;
    require(!::getpwuid_r(getuid(), &native, native_buffer.data(),
                          native_buffer.size(), &found) &&
                found,
            "own UID ordinary account lookup");
    struct Guarded {
      uint64_t before = 0x123456789abcdef0;
      GuestPasswd value{};
      uint64_t after = 0xfedcba9876543210;
    } guarded;
    std::vector<char> guest_buffer(1u << 20);
    GuestPasswd *result = nullptr;
    int error = 0;
    auto passwd = [&](char *buffer, size_t size, GuestPasswd **output) {
      return int(
          call("accounts_passwd",
               {Argument::integer(getuid()), pointer(&guarded.value),
                pointer(buffer), Argument::integer(size, ArgumentType::Int64),
                pointer(output), pointer(&error)}));
    };
    require(!passwd(guest_buffer.data(), guest_buffer.size(), &result) &&
                result == &guarded.value && error == 73 &&
                guarded.before == 0x123456789abcdef0 &&
                guarded.after == 0xfedcba9876543210,
            "48-byte guest passwd record and return-code contract");
    require(guarded.value.uid == found->pw_uid &&
                guarded.value.gid == found->pw_gid &&
                guarded.value.password == nullptr &&
                same(guarded.value.name, found->pw_name) &&
                same(guarded.value.gecos, found->pw_gecos) &&
                same(guarded.value.directory, found->pw_dir) &&
                same(guarded.value.shell, found->pw_shell),
            "requested own-account fields translated privately without native "
            "extras");
    for (const char *text : {guarded.value.name, guarded.value.gecos,
                             guarded.value.directory, guarded.value.shell})
      if (text) {
        uintptr_t start = reinterpret_cast<uintptr_t>(guest_buffer.data());
        uintptr_t value = reinterpret_cast<uintptr_t>(text);
        require(value >= start && value - start < guest_buffer.size(),
                "passwd string lies in caller storage");
        size_t remaining = guest_buffer.size() - (value - start);
        require(strnlen(text, remaining) < remaining,
                "passwd strings are owned by caller's buffer");
      }
    char tiny = 'Q';
    GuestPasswd previous = guarded.value;
    result = reinterpret_cast<GuestPasswd *>(uintptr_t(1));
    require(passwd(&tiny, 1, &result) == 34 && !result && tiny == 'Q' &&
                error == 73 &&
                !std::memcmp(&previous, &guarded.value, sizeof(previous)),
            "ERANGE is returned as a value, not minus-one or a partial record");
    require(passwd(guest_buffer.data(), guest_buffer.size(), nullptr) == 22 &&
                error == 73,
            "invalid result pointer returns guest EINVAL value");
    struct group native_group{}, *group_found = nullptr;
    std::vector<char> group_buffer(1u << 20);
    require(!getgrgid_r(getgid(), &native_group, group_buffer.data(),
                        group_buffer.size(), &group_found) &&
                group_found,
            "own GID ordinary account lookup");
    auto lookup_group = [&](int &out) {
      return reinterpret_cast<GuestGroup *>(
          call("accounts_group", {Argument::integer(getgid()), pointer(&out)}));
    };
    auto *group = lookup_group(error);
    require(group && error == 73 && group->gid == group_found->gr_gid &&
                !group->password && same(group->name, group_found->gr_name),
            "owned guest group record and unset password field");
    size_t members = 0;
    if (group_found->gr_mem)
      for (; group_found->gr_mem[members]; ++members) {
        require(members < 4096 && group->members &&
                    same(group->members[members], group_found->gr_mem[members]),
                "group members translated privately");
      }
    require(group->members && !group->members[members],
            "owned group member vector is terminated");
    std::atomic<int> isolated{0};
    std::vector<std::thread> workers;
    for (int i = 0; i < 4; ++i)
      workers.emplace_back([&] {
        int local = 0;
        auto *other = lookup_group(local);
        if (other && other != group && other->gid == getgid() && local == 73)
          ++isolated;
      });
    for (auto &worker : workers)
      worker.join();
    require(isolated == 4 && group->gid == getgid() &&
                same(group->name, group_found->gr_name),
            "group storage is isolated per native thread");
    char path[] = "/tmp/shiraz-flock-XXXXXX";
    Descriptor first{mkstemp(path)}, second{open(path, O_RDWR)};
    unlink(path);
    require(first.value >= 0 && second.value >= 0,
            "two independent local file descriptions");
    auto lock = [&](int fd, int operation) {
      return int(int32_t(call("accounts_lock", {Argument::integer(fd),
                                                Argument::integer(operation),
                                                pointer(&error)})));
    };
    require(!lock(first.value, 2) && error == 73 &&
                lock(second.value, 6) == -1 && error == 11,
            "real exclusive advisory lock and nonblocking conflict");
    require(!lock(first.value, 8) && !lock(second.value, 5) && error == 73,
            "unlock and shared advisory locking");
    require(!lock(second.value, 8) && lock(second.value, 3) == -1 &&
                error == 22 && lock(second.value, 32) == -1 && error == 22,
            "invalid and mandatory lock modes are explicit EINVAL");
    require(lock(-1, 2) == -1 && error == 9,
            "bad lock descriptor is guest EBADF");
    // Native account scratch can contain host-only fields. Clear it without
    // inspecting those fields; none is retained in guest password pointers.
    for (auto *buffer : {&native_buffer, &group_buffer}) {
      volatile char *bytes = buffer->data();
      for (size_t i = 0; i < buffer->size(); ++i)
        bytes[i] = 0;
    }
    std::cout << "M5_ACCOUNTS_PASS passwd_layout=48 caller_storage=1 "
                 "return_codes=1 group_layout=32 tls_threads=4 "
                 "password_fields=unset flock=real metadata_output=0\n";
  } catch (const std::exception &failure) {
    std::cerr << "FAIL " << failure.what() << '\n';
    return 1;
  }
}
