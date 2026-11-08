// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/libc_scalar.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
namespace {
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
char *substring(const char *text, const char *needle) {
  return const_cast<char *>(std::strstr(text, needle));
}
void abort_exit(int) { _exit(97); }
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: scalar-extra-probe fixture-directory");
    Loader *active = nullptr;
    Provider libc{"libc.so", {"LIBC"}, libc_scalar_exports([&](const void *code) {
      return active && active->contains_code(code);
    }), false};
    libc.exports.push_back(fixed_export("__errno", "LIBC", &guest_errno_location));
    libc.exports.push_back(fixed_export("strcmp", "LIBC", &std::strcmp));
    libc.exports.push_back(fixed_export("strstr", "LIBC", &substring));
    libc.exports.push_back(fixed_export("memcpy", "LIBC", &std::memcpy));
    libc.exports.push_back(fixed_export("memset", "LIBC", &std::memset));
    libc.exports.push_back(fixed_export("free", "LIBC", &std::free));
    Providers providers; providers.add(std::move(libc));
    Loader loader({argv[1]}, std::move(providers)); active = &loader;
    auto *module = loader.open("libscalar-extra.so");
    auto call = [&](const char *name) {
      int result = int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), {}).result_integers[0]);
      if (result != 73) throw std::runtime_error(std::string(name) + " case=" + std::to_string(result));
    };
    errno = ELOOP;
    for (const char *name : {"scalar_extra_strings", "scalar_extra_random", "scalar_extra_sort"}) {
      call(name); require(errno == ELOOP, "host errno preserved");
    }
    int descriptors[2]; require(!pipe(descriptors), "perror capture pipe");
    int saved = dup(STDERR_FILENO); require(saved >= 0, "preserve stderr descriptor");
    std::fflush(stderr);
    require(dup2(descriptors[1], STDERR_FILENO) >= 0, "capture perror");
    close(descriptors[1]);
    try {
      errno = ELOOP;
      call("scalar_extra_perror");
      require(errno == ELOOP, "perror preserves host errno");
    } catch (...) {
      std::fflush(stderr);
      (void)dup2(saved, STDERR_FILENO); close(saved); close(descriptors[0]);
      throw;
    }
    std::fflush(stderr);
    require(dup2(saved, STDERR_FILENO) >= 0, "restore stderr"); close(saved);
    char output[2048]{};
    ssize_t count = read(descriptors[0], output, sizeof(output) - 1); close(descriptors[0]);
    require(count > 0 && std::strstr(output, "guest-error: ") &&
                (std::strstr(output, "deadlock") || std::strstr(output, "Deadlock")),
            "perror translated guest EDEADLK rather than raw Darwin error 35");
    for (const char *name : {"scalar_extra_bad_callback", "scalar_extra_bad_extent"}) {
      pid_t child = fork(); require(child >= 0, "qsort rejection child");
      if (!child) {
        std::signal(SIGABRT, abort_exit);
        call(name); _exit(1);
      }
      int status = 0;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "invalid qsort callback/extent aborts explicitly");
    }
    std::cout << "M5_SCALAR_EXTRA_PASS spans=1 strdup=1 errno_reverse=1 strerror_r=1 perror=1 bionic_random=1 qsort_guest_callback=1 fatal_rejections=2 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "scalar-extra-probe: " << error.what() << '\n';
    return 1;
  }
}
