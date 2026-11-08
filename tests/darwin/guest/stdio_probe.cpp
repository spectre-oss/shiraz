// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/stdio.hpp"
#include "guest_errno.hpp"
#include <filesystem>
#include <iostream>
#include <thread>
#include <atomic>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
static Argument integer(uint64_t value) { return Argument::integer(value, ArgumentType::Int64); }
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: stdio-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]);
    auto previous = fs::current_path();
    std::string temporary = (fixtures.parent_path() / "stdio-work-XXXXXX").string();
    require(mkdtemp(temporary.data()), "exclusive stdio directory");
    fs::path work = fs::canonical(temporary);
    fs::current_path(work);
    Loader loader({fixtures}, stdio_personality());
    auto *module = loader.open("libstdio.so");
    errno = ELOOP;
    auto value = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_stdio").address), {}).result_integers[0];
    if (value) throw std::runtime_error("guest stdio failed at line " + std::to_string(value));
    require(errno == ELOOP, "stdio preserves host errno");
    auto *libc = loader.link("libc.so");
    auto call = [&](const char *name, std::vector<Argument> args) {
      return invoke(reinterpret_cast<void *>(loader.symbol(libc, name).address), args).result_integers[0];
    };
    auto base = loader.symbol(libc, "__sF").address;
    const char *names[] = {"stdin", "stdout", "stderr"};
    for (size_t i = 0; i < 3; ++i) {
      auto address = *reinterpret_cast<void **>(loader.symbol(libc, names[i]).address);
      require(reinterpret_cast<uintptr_t>(address) == base + i * 152,
              "legacy and modern standard identities agree");
      require(int(call("fileno", {pointer(address)})) == int(i), "standard stream descriptor");
    }
    *guest_errno_location() = 0;
    require(int(call("fileno", {integer(0x12345)})) == -1 &&
                *guest_errno_location() == bionic::kEBADF,
            "forged FILE rejected without dereference");
    auto opened = call("fopen", {pointer("stale"), pointer("w+")});
    require(opened, "open stale identity test");
    require(int(call("fclose", {integer(opened)})) == 0, "close first identity");
    auto replacement = call("fopen", {pointer("stale"), pointer("r")});
    require(replacement && replacement != opened, "opaque FILE identities never recycled");
    require(int(call("fileno", {integer(opened)})) == -1, "closed identity stays invalid");
    require(int(call("fclose", {integer(replacement)})) == 0, "close replacement");
    fs::remove("stale");
    // Resolve before concurrent invokes; Loader lookup is owner-serialized.
    auto open = loader.symbol(libc, "fopen").address;
    auto close = loader.symbol(libc, "fclose").address;
    auto put = loader.symbol(libc, "fputc").address;
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      std::string name = "thread-" + std::to_string(i);
      for (int j = 0; j < 16; ++j) {
        auto file = invoke(reinterpret_cast<void *>(open), {pointer(name.c_str()), pointer("a")}).result_integers[0];
        if (!file) { passed = false; return; }
        auto written = invoke(reinterpret_cast<void *>(put), {integer('x'), integer(file)}).result_integers[0];
        auto closed = invoke(reinterpret_cast<void *>(close), {integer(file)}).result_integers[0];
        if (int(written) != 'x' || int(closed)) passed = false;
      }
    });
    for (auto &thread : threads) thread.join();
    require(passed, "concurrent opaque stdio registry");
    for (int i = 0; i < 4; ++i) require(fs::file_size("thread-" + std::to_string(i)) == 16,
                                      "actual concurrent stdio output");
    fs::current_path(previous);
    fs::remove_all(work);
    std::cout << "M5_STDIO_PASS guest_file=1 standards=3 opaque_handles=1 stale_rejected=1 large_offset=1 threads=4 formatting=0\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "stdio-probe: " << error.what() << '\n';
    return 1;
  }
}
