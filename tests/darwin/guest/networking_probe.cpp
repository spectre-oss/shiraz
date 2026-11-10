// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/networking.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include <filesystem>
#include <fcntl.h>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <array>
#include <iostream>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const std::string &message) { if (!value) throw std::runtime_error(message); }
static size_t descriptors() {
  HostErrno preserve;
  size_t count = 0;
  for (int fd = 0; fd < 4096; ++fd) if (::fcntl(fd, F_GETFD) != -1) ++count;
  return count;
}
int main(int argc, char **argv) {
  try {
    require(argc == 2 || argc == 4, "usage: networking-probe fixture-directory [public-ip port]");
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
    if(argc==4){
      std::array<uint8_t,42> request{};request[0]=0xef;request[1]=10;
      uint64_t message=uint64_t(std::chrono::duration<double>(std::chrono::system_clock::now().time_since_epoch()).count()*4294967296.)&~uint64_t(3);
      std::memcpy(request.data()+10,&message,8);request[18]=20;
      uint32_t constructor=0xbe7e8ef1;std::memcpy(request.data()+22,&constructor,4);arc4random_buf(request.data()+26,16);
      std::array<uint8_t,4096> response{};int port=std::stoi(argv[3]);require(port>0&&port<=65535,"public server port");
      auto pointer=[](const void *value){return Argument::integer(int64_t(reinterpret_cast<uintptr_t>(value)),ArgumentType::Int64);};
      auto result=int32_t(invoke(reinterpret_cast<void *>(loader.symbol(module,"run_mtproto_pq").address),{pointer(argv[2]),Argument::integer(port),pointer(request.data()),Argument::integer(request.size()),pointer(response.data()),Argument::integer(response.size())}).result_integers[0]);
      require(result>=41,"guest public MTProto exchange failed: "+std::to_string(result));
      uint32_t responseType=0;std::memcpy(&responseType,response.data()+21,4);
      require(responseType==0x05162463&&!std::memcmp(response.data()+25,request.data()+26,16),"guest MTProto resPQ constructor/nonce");
      fs::current_path(previous);fs::remove_all(work);std::cout<<"M5_TELEGRAM_PQ_PASS nonblocking_connect=1 send=1 recv=1 nonce_match=1\n";return 0;
    }
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
