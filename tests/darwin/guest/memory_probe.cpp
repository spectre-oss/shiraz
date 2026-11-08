// SPDX-License-Identifier: GPL-3.0-only
#include "guest_io.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <csignal>
#include <filesystem>
#include <iostream>
#include <sys/mman.h>
#include <sys/wait.h>
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
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: memory-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]);
    std::string temporary = (fixtures.parent_path() / "memory-work-XXXXXX").string();
    char *created = mkdtemp(temporary.data());
    require(created, "exclusive memory test directory");
    fs::path work = fs::canonical(created), previous = fs::current_path();
    fs::current_path(work);
    Loader loader({fixtures}, native_personality());
    auto *module = loader.open("libmemory.so");
    require(loader.image_count() == 1, "no link-only stubs loaded");
    errno = ELOOP;
    int value = int(invoke(reinterpret_cast<void *>(loader.symbol(module, "run_memory").address), {})
                        .result_integers[0]);
    if (value) throw std::runtime_error("guest memory failed at line " + std::to_string(value));
    require(errno == ELOOP, "memory providers preserve host errno");
    auto call = [&](const char *name, std::vector<Argument> args) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), args)
          .result_integers[0];
    };
    size_t page = getpagesize();
    auto integer = [](uint64_t value) { return Argument::integer(value, ArgumentType::Int64); };
    auto map = [&]() {
      return reinterpret_cast<char *>(call("mmap", {pointer(nullptr), integer(page),
          integer(bionic::kPROT_READ | bionic::kPROT_WRITE),
          integer(bionic::kMAP_PRIVATE | bionic::kMAP_ANONYMOUS), integer(-1), integer(0)}));
    };
    // The guest must not change a native allocation or its loader's ELF image.
    char *foreign = static_cast<char *>(::mmap(nullptr, page, PROT_READ | PROT_WRITE,
                                               MAP_PRIVATE | MAP_ANON, -1, 0));
    require(foreign != MAP_FAILED, "foreign mapping");
    foreign[0] = 'h';
    require(int(call("munmap", {pointer(foreign), integer(page)})) == -1,
            "guest cannot unmap host allocation");
    require(int(call("mprotect", {pointer(foreign), integer(page), integer(0)})) == -1,
            "guest cannot protect host allocation");
    auto replacement = call("mmap", {pointer(foreign), integer(page), integer(3),
        integer(bionic::kMAP_FIXED | bionic::kMAP_PRIVATE | bionic::kMAP_ANONYMOUS),
        integer(-1), integer(0)});
    require(replacement == UINT64_MAX && foreign[0] == 'h', "fixed mmap cannot replace host allocation");
    require(::munmap(foreign, page) == 0, "native mapping cleanup");
    uintptr_t code = loader.symbol(module, "run_memory").address & ~(uintptr_t(page) - 1);
    require(int(call("munmap", {integer(code), integer(page)})) == -1 &&
                int(call("mprotect", {integer(code), integer(page), integer(0)})) == -1,
            "memory API cannot unmap or protect ELF loader pages");
    char *owned = map();
    require(owned != MAP_FAILED, "owned mapping for protection test");
    owned[0] = 'r';
    require(int(call("mprotect", {pointer(owned), integer(page), integer(bionic::kPROT_READ)})) == 0,
            "owned read-only protection");
    pid_t child = fork();
    require(child >= 0, "protection child");
    if (!child) {
      std::signal(SIGBUS, [](int) { _exit(97); });
      std::signal(SIGSEGV, [](int) { _exit(97); });
      *static_cast<volatile char *>(owned) = 'w';
      _exit(98);
    }
    int status;
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
            "read-only mapping faults on actual write");
    require(owned[0] == 'r' && int(call("munmap", {pointer(owned), integer(page)})) == 0,
            "read-only mapping cleanup");
    std::atomic<bool> passed{true};
    std::vector<std::thread> threads;
    // Resolve symbols before worker threads: Loader isn't a concurrent lookup API.
    auto mmap_address = loader.symbol(module, "mmap").address;
    auto munmap_address = loader.symbol(module, "munmap").address;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&] {
      for (int j = 0; j < 32; ++j) {
        auto result = invoke(reinterpret_cast<void *>(mmap_address), {pointer(nullptr), integer(page),
            integer(3), integer(bionic::kMAP_PRIVATE | bionic::kMAP_ANONYMOUS), integer(-1), integer(0)});
        auto *data = reinterpret_cast<char *>(result.result_integers[0]);
        if (data == MAP_FAILED) { passed = false; return; }
        data[0] = 't';
        if (int(invoke(reinterpret_cast<void *>(munmap_address), {pointer(data), integer(page)})
                    .result_integers[0]) != 0) passed = false;
      }
    });
    for (auto &thread : threads) thread.join();
    require(passed, "concurrent owned mappings");
    fs::current_path(previous);
    require(fs::is_empty(work), "memory fixture file cleanup");
    fs::remove(work);
    std::cout << "M5_MEMORY_API_PASS anonymous=1 shared=1 private=1 offset=1 "
                 "partial=1 ownership=1 protection_fault=1 limits=1 threads=4 exec_refused=1\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
