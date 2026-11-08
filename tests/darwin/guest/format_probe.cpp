// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/stdio.hpp"
#include <atomic>
#include <filesystem>
#include <iostream>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: format-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]), previous = fs::current_path();
    std::string temporary = (fixtures.parent_path() / "format-work-XXXXXX").string();
    require(mkdtemp(temporary.data()), "exclusive format directory");
    fs::path work = fs::canonical(temporary);
    fs::current_path(work);
    Loader loader({fixtures}, stdio_personality());
    auto *module = loader.open("libformat.so");
    auto entry = reinterpret_cast<void *>(loader.symbol(module, "run_format").address);
    errno = ELOOP;
    auto result = invoke(entry, {}).result_integers[0];
    if (result) throw std::runtime_error("guest formatting failed at line " + std::to_string(result));
    require(errno == ELOOP, "formatting preserves host errno");
    // File formatting is checked above. Concurrent calls use the snprintf
    // export directly so every thread owns a distinct destination buffer.
    auto *libc = loader.link("libc.so");
    auto snprintf_entry = reinterpret_cast<void *>(loader.symbol(libc, "snprintf").address);
    auto pointer = [](const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); };
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      for (int j = 0; j < 64; ++j) {
        char output[64];
        auto count = invoke(snprintf_entry, {pointer(output), Argument::integer(sizeof(output), ArgumentType::Int64),
            pointer("%d/%.2f"), Argument::integer(i), Argument::real(2.5)}).result_integers[0];
        if (count != 6 || std::string(output) != std::to_string(i) + "/2.50") passed = false;
      }
    });
    for (auto &thread : threads) thread.join();
    require(passed, "concurrent register/vector formatting");
    fs::current_path(previous);
    fs::remove_all(work);
    std::cout << "M5_FORMAT_PASS printf_family=10 aapcs_varargs=1 va_list=1 register_overflow=1 truncate=1 embedded_nul=1 unsupported_errno=1 threads=4\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "format-probe: " << error.what() << '\n';
    return 1;
  }
}
