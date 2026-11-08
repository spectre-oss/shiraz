// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include <csignal>
#include <filesystem>
#include <iostream>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
static Argument pointer(const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); }
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: remap-probe fixture-directory");
    auto fixtures = fs::canonical(argv[1]), previous = fs::current_path();
    std::string temporary = (fixtures.parent_path() / "remap-work-XXXXXX").string();
    require(mkdtemp(temporary.data()), "exclusive remap directory");
    fs::path work = fs::canonical(temporary); fs::current_path(work);
    Loader loader({fixtures}, native_personality());
    auto *module = loader.open("libremap.so");
    auto call = [&](const char *name, std::vector<Argument> args = {}) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), args).result_integers[0];
    };
    errno = ELOOP;
    auto *moved = reinterpret_cast<char *>(call("prepare_moved_remap"));
    if (!moved) throw std::runtime_error("remap preparation failed at guest line " + std::to_string(call("remap_prepare_status")));
    require(errno == ELOOP, "remap preserves host errno");
    size_t page = getpagesize();
    for (size_t protected_page : {size_t(1), size_t(2)}) {
      pid_t child = fork(); require(child >= 0, "protection test child");
      if (!child) {
        std::signal(SIGSEGV, [](int) { _exit(97); }); std::signal(SIGBUS, [](int) { _exit(97); });
        *reinterpret_cast<volatile char *>(moved + protected_page * page) = 'x'; _exit(98);
      }
      int status; require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
                          "moved original and extension preserve read-only protection");
    }
    for (const char *name : {"finish_moved_remap", "run_remap_limits"}) {
      auto result = call(name);
      if (result) throw std::runtime_error(std::string(name) + " failed at guest line " + std::to_string(result));
    }
    char *foreign = static_cast<char *>(::mmap(nullptr, page, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0));
    require(foreign != MAP_FAILED, "foreign mapping"); foreign[0] = 'F';
    auto result = call("reject_foreign_remap", {pointer(foreign), Argument::integer(page, ArgumentType::Int64)});
    require(!result && foreign[0] == 'F' && ::munmap(foreign, page) == 0, "foreign mapping not moved or changed");
    fs::current_path(previous); fs::remove_all(work);
    std::cout << "M5_REMAP_PASS anon_private=1 moved=1 zero_growth=1 mixed_protection=1 fault_checks=2 shrink_none=1 file_backing_rejected_intact=1 foreign_rejected=1\n";
    return 0;
  } catch (const std::exception &error) { std::cerr << "remap-probe: " << error.what() << '\n'; return 1; }
}
