// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/process.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdio>
#include <csignal>
#include <cstring>
#include <iostream>
#include <pthread.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
static std::string read_pipe(int fd) {
  std::string result;
  char bytes[1024];
  for (;;) {
    ssize_t size = ::read(fd, bytes, sizeof(bytes));
    if (!size) return result;
    if (size < 0 && errno == EINTR) continue;
    require(size > 0 && result.size() + size <= 16384, "bounded process evidence pipe");
    result.append(bytes, size_t(size));
  }
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: process-probe fixture-directory");
    Loader *current = nullptr;
    auto lifecycle = std::make_shared<Lifecycle>([&](const void *p) { return current && current->contains_code(p); });
    auto base = native_personality();
    Providers providers;
    for (const auto &pair : base.modules()) {
      Provider module = pair.second;
      if (module.soname == "libc.so") {
        auto additions = process_exports(lifecycle);
        auto callbacks = lifecycle->exports();
        additions.insert(additions.end(), callbacks.begin(), callbacks.end());
        module.exports.erase(std::remove_if(module.exports.begin(), module.exports.end(),
          [&](const auto &entry) {
            return std::any_of(additions.begin(), additions.end(), [&](const auto &addition) { return addition.name == entry.name; });
          }), module.exports.end());
        module.exports.insert(module.exports.end(), additions.begin(), additions.end());
      }
      providers.add(std::move(module));
    }
    Loader loader({argv[1]}, std::move(providers));
    current = &loader;
    lifecycle->attach(loader);
    auto *module = loader.open("libprocess-fixture.so");
    const auto abort_binding = loader.symbol(module, "abort", "LIBC");
    require(abort_binding.nonreturning && abort_binding.kind == SymbolKind::Function &&
                abort_binding.abi == "fixed/nonreturning-noargs",
            "selected owned-message abort provider exposes an enforced terminal contract");
    require(!loader.symbol(module, "syscall", "LIBC").nonreturning &&
                !loader.symbol(module, "android_set_abort_message", "LIBC").nonreturning,
            "ordinary process exports do not acquire terminal contracts");
    auto call = [&](const char *name, const std::vector<Argument> &arguments) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0];
    };
    auto syscall = [&](long number, int &error) {
      errno = 61;
      auto value = call("process_call", {Argument::integer(number, ArgumentType::Int64), pointer(&error)});
      require(errno == 61, "syscall adapter preserves native errno");
      return long(int64_t(value));
    };
    int error = 0;
    const std::pair<long,long> ids[] = {{172,::getpid()},{173,::getppid()},{174,::getuid()},
      {175,::geteuid()},{176,::getgid()},{177,::getegid()}};
    for (const auto &id : ids) require(syscall(id.first, error) == id.second && error == 73, "real host process identifiers");
    uint64_t tid = 0;
    require(!pthread_threadid_np(nullptr, &tid) && tid <= INT32_MAX &&
                syscall(178,error) == long(tid) && error == 73, "checked native thread id");
    std::atomic<int> threads{0};
    std::vector<std::thread> workers;
    for (int i = 0; i < 4; ++i) workers.emplace_back([&] {
      uint64_t own = 0; int local_error = 0;
      if (!pthread_threadid_np(nullptr, &own) && own <= INT32_MAX &&
          syscall(178,local_error) == long(own) && own != tid && local_error == 73) ++threads;
    });
    for (auto &worker : workers) worker.join();
    require(threads == 4, "each guest thread obtains its actual native id");
    auto random = [&](void *buffer, size_t size, unsigned flags) {
      errno = 61;
      auto value = call("process_random", {pointer(buffer), Argument::integer(size,ArgumentType::Int64),
          Argument::integer(flags), pointer(&error)});
      require(errno == 61, "random adapter preserves native errno");
      return long(int64_t(value));
    };
    std::array<unsigned char,34> bytes{};
    bytes.front() = bytes.back() = 0x5a;
    require(random(bytes.data()+1,32,0) == 32 && error == 73 && bytes.front() == 0x5a && bytes.back() == 0x5a,
            "real random bytes stay inside requested storage");
    require(!random(nullptr,0,0) && error == 73 && random(nullptr,1,0) == -1 && error == 14,
            "zero length succeeds and null writable buffer fails");
    require(random(bytes.data(),1,8) == -1 && error == 22 && random(bytes.data(),1,6) == -1 && error == 22,
            "invalid getrandom flags are EINVAL");
    std::vector<unsigned char> large((1u<<20)+16,0x5a);
    require(random(large.data(),large.size(),1) == (1<<20) && error == 73 &&
                std::all_of(large.end()-16,large.end(),[](unsigned char c){return c==0x5a;}),
            "bounded random request returns honest short count");
    int log_pipe[2]; require(!pipe(log_pipe), "syslog evidence pipe");
    int saved_stderr = dup(STDERR_FILENO);
    require(saved_stderr >= 0 && dup2(log_pipe[1],STDERR_FILENO) >= 0, "capture process log surface");
    struct RestoreStderr {
      int descriptor;
      ~RestoreStderr() { if (descriptor >= 0) { dup2(descriptor,STDERR_FILENO); close(descriptor); } }
    } restoring{saved_stderr};
    close(log_pipe[1]);
    long unsupported = syscall(98,error);
    require(unsupported == -1 && error == 38, "futex remains explicitly ENOSYS");
    require(syscall(0x7fffffff,error) == -1 && error == 38, "unknown Linux syscall is not forwarded");
    int logged = int(call("process_log",{}));
    std::fflush(stderr);
    require(dup2(saved_stderr,STDERR_FILENO) >= 0, "restore process log surface"); close(saved_stderr);
    restoring.descriptor = -1;
    auto evidence = read_pipe(log_pipe[0]); close(log_pipe[0]);
    require(logged == 73 && evidence.find("futex=unfinished") != std::string::npos &&
                evidence.find("guest-copy[") != std::string::npos && evidence.find("scalar=-31 float=2.75 text=guest %") != std::string::npos &&
                evidence.find("Xuest-copy") == std::string::npos,
            "copied syslog ident, PID/PERROR and real AAPCS formatted delivery");
    require(call("process_log_bad",{}) == 22, "unknown log option is explicit EINVAL");
    for (int fast = 0; fast < 2; ++fast) {
      int notification[2]; require(!pipe(notification), "exit callback evidence pipe");
      pid_t child = fork(); require(child >= 0, "exit child");
      if (!child) {
        close(notification[0]);
        if (call("process_prepare_exit",{Argument::integer(notification[1])})) ::_exit(99);
        call("process_exit",{Argument::integer(7),Argument::integer(fast)});
        ::_exit(98);
      }
      close(notification[1]);
      auto finalizers = read_pipe(notification[0]); close(notification[0]);
      int status; require(waitpid(child,&status,0)==child && WIFEXITED(status) && WEXITSTATUS(status)==7 &&
          finalizers == (fast ? "" : "F"), "exit runs guest finalizers and _exit skips them");
    }
    int aborted[2]; require(!pipe(aborted), "abort evidence pipe");
    pid_t child = fork(); require(child >= 0, "abort child");
    if (!child) {
      close(aborted[0]); dup2(aborted[1],STDERR_FILENO); close(aborted[1]);
      std::signal(SIGABRT,[](int){::_exit(97);}); call("process_abort",{}); ::_exit(98);
    }
    close(aborted[1]); auto message = read_pipe(aborted[0]); close(aborted[0]);
    int status; require(waitpid(child,&status,0)==child && WIFEXITED(status) && WEXITSTATUS(status)==97 &&
        message.find("owned guest abort")!=std::string::npos && message.find("later message")==std::string::npos,
        "abort emits an owned copy of the first guest message then really aborts");
    std::cout << "M5_PROCESS_PASS syscall_ids=7 getrandom=real futex=unfinished syslog=native_asl perror=1 abort=owned_first nonreturning_contract=1 exit_finalizers=1 fast_exit_finalizers=0 threads=4\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
