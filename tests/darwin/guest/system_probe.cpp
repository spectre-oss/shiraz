// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/system.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cstring>
#include <iostream>
#include <sys/sysctl.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
static bool feature(const char *name) {
  int32_t value = 0;
  size_t size = sizeof(value);
  return !sysctlbyname(name, &value, &size, nullptr, 0) && size == sizeof(value) && value == 1;
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: system-probe fixture-directory");
    auto base = native_personality();
    auto providers = std::move(base);
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libsystem-fixture.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0];
    };
    int guest_error = 0;
    auto conf = [&](int selector) {
      errno = 61;
      auto value = call("system_conf", {Argument::integer(selector), pointer(&guest_error)});
      require(errno == 61, "sysconf preserves native errno");
      return long(int64_t(value));
    };
    struct Selector { int guest, host; };
    const Selector selectors[] = {{0,_SC_ARG_MAX},{5,_SC_CHILD_MAX},{6,_SC_CLK_TCK},
      {10,_SC_NGROUPS_MAX},{11,_SC_OPEN_MAX},{27,_SC_STREAM_MAX},{38,_SC_IOV_MAX},
      {39,_SC_PAGESIZE},{40,_SC_PAGESIZE},{74,_SC_THREAD_DESTRUCTOR_ITERATIONS},
      {96,_SC_NPROCESSORS_CONF},{97,_SC_NPROCESSORS_ONLN},{98,_SC_PHYS_PAGES}};
    for (const auto &selector : selectors) {
      long expected = ::sysconf(selector.host);
      require(conf(selector.guest) == expected && guest_error == 73,
              "Linux selector explicitly queries matching host property");
    }
    require(conf(75) == std::min(::sysconf(_SC_THREAD_KEYS_MAX), 4096L) && guest_error == 73 &&
                conf(76) == std::max(::sysconf(_SC_THREAD_STACK_MIN), 16384L) && guest_error == 73 &&
                conf(77) == 128 && guest_error == 73 && conf(54) == 0x3fffffff && guest_error == 73,
            "thread and semaphore limits match guest-backed services");
    require(conf(99) >= 0 && guest_error == 73, "real free physical page count");
    for (int selector : {-1, 0x7fffffff, 123456})
      require(conf(selector) == -1 && guest_error == 22, "unknown sysconf is guest EINVAL");
    auto aux = [&](unsigned long selector) {
      errno = 61;
      auto value = call("system_aux", {Argument::integer(selector, ArgumentType::Int64), pointer(&guest_error)});
      require(errno == 61, "auxiliary query preserves native errno");
      return value;
    };
    for (const auto &entry : std::vector<std::pair<unsigned long,unsigned long>>{
        {6,unsigned(::getpagesize())},{11,::getuid()},{12,::geteuid()},{13,::getgid()},
        {14,::getegid()},{17,static_cast<unsigned long>(::sysconf(_SC_CLK_TCK))},{23,::issetugid()?1ul:0ul}})
      require(aux(entry.first) == entry.second && guest_error == 73, "auxiliary value reflects host process");
    auto first = aux(16), second = aux(26);
    require(guest_error == 73 && !(first & ((1ul<<11)|(1ul<<22)|(1ul<<30)|(1ul<<31))) &&
                !(second & ((1ul<<17)|(1ul<<18)|(1ul<<23))),
            "no Linux CPUID/PAC/SVE/BTI/MTE/SME facilities are advertised");
    const std::pair<const char *,unsigned> checks[] = {{"hw.optional.floatingpoint",0},
      {"hw.optional.neon",1},{"hw.optional.arm.FEAT_AES",3},{"hw.optional.arm.FEAT_PMULL",4},
      {"hw.optional.arm.FEAT_SHA1",5},{"hw.optional.arm.FEAT_SHA256",6},
      {"hw.optional.armv8_crc32",7},{"hw.optional.arm.FEAT_LSE",8}};
    for (const auto &check : checks)
      require(bool(first & (1ul << check.second)) == feature(check.first), "HWCAP follows actual host feature query");
    auto random = aux(25);
    require(random && aux(25) == random && guest_error == 73, "one stable process random block");
    std::array<unsigned char,16> sample{};
    std::memcpy(sample.data(), reinterpret_cast<const void *>(random), sample.size());
    for (unsigned long absent : {0ul,3ul,7ul,9ul,15ul,31ul,33ul,~0ul})
      require(!aux(absent) && guest_error == 2, "missing auxiliary entry is ENOENT, no fake ELF/vDSO");
    unsigned long identifiers[3]{};
    require(call("system_identifiers", {pointer(identifiers)}) == 73 &&
                identifiers[0] == unsigned(::getpid()) && identifiers[1] == ::getuid() && identifiers[2] == ::geteuid(),
            "actual process scalar identifiers");
    require(call("system_yield", {}) == 73, "real host scheduling yield preserves guest errno on success");
    std::atomic<int> passed{0};
    std::vector<std::thread> workers;
    for (int i = 0; i < 4; ++i) workers.emplace_back([&] {
      int error = 0;
      auto address = call("system_aux", {Argument::integer(25, ArgumentType::Int64), pointer(&error)});
      if (address == random && error == 73) ++passed;
    });
    for (auto &worker : workers) worker.join();
    require(passed == 4, "process random pointer is shared across guest threads");
    std::cout << "M5_SYSTEM_PASS selectors=explicit aux=process-backed hwcap=sysctl unsupported_facilities=0 random=16 threads=4 errno=preserved raw_syscalls=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
