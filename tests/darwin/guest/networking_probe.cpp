// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/networking.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include <filesystem>
#include <fcntl.h>
#include <iostream>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
static size_t descriptors() {
  HostErrno preserve;
  size_t count = 0;
  for (int fd = 0; fd < 4096; ++fd) if (::fcntl(fd, F_GETFD) != -1) ++count;
  return count;
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: networking-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]), previous = fs::current_path();
    std::string temporary = (fixtures.parent_path() / "network-work-XXXXXX").string();
    require(mkdtemp(temporary.data()), "exclusive local socket directory");
    fs::path work = fs::canonical(temporary); fs::current_path(work);
    auto base = file_personality(); Providers providers;
    for (const auto &entry : base.modules()) {
      auto provider = entry.second;
      if (entry.first == "libc.so") { auto network = networking_exports(); provider.exports.insert(provider.exports.end(), network.begin(), network.end());auto events=event_exports();provider.exports.insert(provider.exports.end(),events.begin(),events.end()); }
      providers.add(std::move(provider));
    }
    Loader loader({fixtures}, std::move(providers));
    auto *module = loader.open("libnetworking.so");
    auto call = [&](const char *name) {
      errno = ELOOP;
      auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), {}).result_integers[0];
      if (result) throw std::runtime_error(std::string(name) + " failed at guest line " + std::to_string(result));
      require(errno == ELOOP, "network providers preserve host errno");
    };
    call("run_networking");
    size_t before = descriptors();
    for (int i = 0; i < 8; ++i) call("run_network_rights");
    require(descriptors() == before, "received/truncated descriptor ownership does not leak");
    fs::current_path(previous); fs::remove_all(work);
    std::cout << "M5_NETWORK_PASS localhost_dns=1 tcp4=1 udp6=1 unix_path=1 sockaddr_translation=1 ancillary_pktinfo=1 scm_rights=1 cloexec=1 truncation_fd_cleanup=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "networking-probe: " << error.what() << '\n'; return 1;
  }
}
