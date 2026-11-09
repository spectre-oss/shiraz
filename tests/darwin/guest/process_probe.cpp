// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/process.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <csignal>
#include <cstring>
#include <cstdlib>
#include <climits>
#include <iostream>
#include <poll.h>
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
class WaitableProcessChild {
  struct sigaction previous{};
public:
  WaitableProcessChild() {
    require(!sigaction(SIGCHLD, nullptr, &previous), "snapshot process child disposition");
    struct sigaction action{};
    sigemptyset(&action.sa_mask);
    action.sa_handler = SIG_DFL;
    require(!sigaction(SIGCHLD, &action, nullptr), "keep process evidence child waitable");
  }
  ~WaitableProcessChild() { if (sigaction(SIGCHLD, &previous, nullptr)) _exit(95); }
};
struct ChildEvidence {
  std::string output;
  int status = -1, wait_error = 0;
  bool complete = false;
};
static ChildEvidence process_child(const char *executable, const char *directory,
                                   const char *mode, bool notification = false) {
  WaitableProcessChild disposition;
  int descriptors[2];
  require(!pipe(descriptors), "process child evidence pipe");
  // Allocate/format everything before fork. The native child only sets up its
  // descriptors and execs; guest and C++ work happens in the fresh process.
  auto descriptor = std::to_string(descriptors[1]);
  std::array<char *, 5> arguments{const_cast<char *>(executable), const_cast<char *>(directory),
      const_cast<char *>(mode), notification ? descriptor.data() : nullptr, nullptr};
  const auto child = fork();
  require(child >= 0, "process evidence child fork");
  if (!child) {
    close(descriptors[0]);
    if (!notification && descriptors[1] != STDERR_FILENO) {
      if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
      close(descriptors[1]);
    }
    execv(executable, arguments.data());
    _exit(96);
  }
  close(descriptors[1]);
  ChildEvidence evidence;
  const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
  bool failed = false, eof = false;
  char bytes[1024];
  while (std::chrono::steady_clock::now() < deadline) {
    pollfd input{descriptors[0], POLLIN | POLLHUP, 0};
    const auto ready = poll(&input, 1, 100);
    if (ready < 0 && errno == EINTR) continue;
    if (ready < 0) { failed = true; break; }
    if (!ready) continue;
    const auto size = read(descriptors[0], bytes, sizeof(bytes));
    if (size < 0 && errno == EINTR) continue;
    if (size < 0) { failed = true; break; }
    if (!size) { eof = true; break; }
    if (evidence.output.size() + size_t(size) > 16384) { failed = true; break; }
    evidence.output.append(bytes, size_t(size));
  }
  close(descriptors[0]);
  pid_t waited = 0;
  while (std::chrono::steady_clock::now() < deadline) {
    waited = waitpid(child, &evidence.status, WNOHANG);
    evidence.wait_error = waited < 0 ? errno : 0;
    if (waited == child || (waited < 0 && evidence.wait_error != EINTR)) break;
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
  }
  if (waited != child) {
    failed = true;
    // Never signal a PID after ECHILD: it may no longer name our child.
    if (waited == 0 || (waited < 0 && evidence.wait_error == EINTR)) {
      kill(child, SIGKILL);
      do { waited = waitpid(child, &evidence.status, 0); } while (waited < 0 && errno == EINTR);
    }
  }
  evidence.complete = !failed && eof && waited == child;
  return evidence;
}
static void require_child(const ChildEvidence &evidence, const char *mode, bool accepted,
                          const char *description) {
  if (!accepted) std::cerr << "M5_PROCESS_CHILD_OBSERVED mode=" << mode
      << " complete=" << evidence.complete << " status=" << evidence.status
      << " wait_errno=" << evidence.wait_error << " diagnostic_bytes=" << evidence.output.size()
      << " diagnostic=" << evidence.output << std::endl;
  require(accepted, description);
}
int main(int argc, char **argv) {
  try {
    require(argc >= 2 && argc <= 4, "usage: process-probe fixture-directory [child-mode [fd]]");
    Loader *current = nullptr;
    auto lifecycle = std::make_shared<Lifecycle>([&](const void *p) { return current && current->contains_code(p); });
    auto base = native_personality();
    Providers providers;
    for (const auto &pair : base.modules()) {
      Provider module = pair.second;
      if (module.soname == "libc.so") {
        auto additions = process_exports(lifecycle, module.exports);
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
    if (argc > 2) {
      const std::string mode = argv[2];
      if (mode == "--child-svc" || mode == "--child-abort") {
        require(argc == 3 && std::signal(SIGABRT, [](int) { _exit(97); }) != SIG_ERR,
                "fresh process child abort observer");
        if (mode == "--child-abort") call("process_abort", {});
        else {
          std::vector<Argument> values(6, Argument::integer(0, ArgumentType::Int64));
          values.push_back(Argument::integer(172, ArgumentType::Int64));
          const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
          values.push_back(Argument::integer(high + 16, ArgumentType::Int64));
          (void)call("__shiraz_linux_syscall", values);
        }
      } else {
        require(argc == 4 && (mode == "--child-exit" || mode == "--child-fast-exit"),
                "known process evidence mode");
        char *end = nullptr;
        const long descriptor = std::strtol(argv[3], &end, 10);
        require(end != argv[3] && !*end && descriptor >= 0 && descriptor <= INT_MAX,
                "valid process notification descriptor");
        if (call("process_prepare_exit", {Argument::integer(descriptor)})) _exit(99);
        call("process_exit", {Argument::integer(7), Argument::integer(mode == "--child-fast-exit")});
      }
      return 98;
    }
    auto syscall = [&](long number, int &error) {
      errno = 61;
      auto value = call("process_call", {Argument::integer(number, ArgumentType::Int64), pointer(&error)});
      require(errno == 61, "syscall adapter preserves native errno");
      return long(int64_t(value));
    };
    int error = 0;
    auto raw_with = [&](long number, std::array<uint64_t,6> arguments) {
      *guest_errno_location() = 73;
      errno = 61;
      std::vector<Argument> values;
      for (auto argument : arguments) values.push_back(Argument::integer(argument,ArgumentType::Int64));
      values.push_back(Argument::integer(number,ArgumentType::Int64));
      uintptr_t sp;
      __asm__ volatile("mov %0, sp" : "=r"(sp));
      values.push_back(Argument::integer(sp,ArgumentType::Int64));
      auto result = call("__shiraz_linux_syscall", values);
      require(*guest_errno_location() == 73 && errno == 61, "raw SVC preserves both errno slots");
      return int64_t(result);
    };
    auto raw_syscall = [&](long number) { return raw_with(number,{}); };
    require(raw_syscall(172) == ::getpid(), "raw syscall returns actual pid");
    const auto raw = process_child(argv[0], argv[1], "--child-svc");
    require_child(raw, "--child-svc", raw.complete && WIFEXITED(raw.status) &&
                WEXITSTATUS(raw.status) == 97 && raw.output ==
                "SHIRAZ_GUEST_FATAL SVC SP outside prepared native stack extent\n",
            "private SVC adapter refuses an aligned original SP beyond the native extent");
    require(raw_syscall(0x7fffffff) == -38, "raw syscall returns negative Linux ENOSYS");
    uint32_t futex_word = 7;
    uint64_t futex_address = uintptr_t(&futex_word);
    require(raw_with(98,{futex_address,128,8,0,0,0}) == -11,"futex compare mismatch is EAGAIN");
    int64_t expired[2] = {0,0};
    require(raw_with(98,{futex_address,128,7,uintptr_t(expired),0,0}) == -110,"relative futex timeout");
    require(raw_with(98,{futex_address,137,7,0,0,0}) == -22,"zero futex bitset rejected");
    std::atomic<int64_t> waited{-999};
    std::thread waiter([&] {
      int64_t timeout[2] = {2,0};
      waited = raw_with(98,{futex_address,128,7,uintptr_t(timeout),0,0});
    });
    auto limit = std::chrono::steady_clock::now() + std::chrono::seconds(2);
    int64_t woken = 0;
    while (std::chrono::steady_clock::now() < limit && !woken) {
      woken = raw_with(98,{futex_address,129,1,0,0,0});
      if(!woken) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    waiter.join();
    require(woken == 1 && waited == 0,"real futex wait/wake without lost wake");
    require(raw_with(98,{futex_address,129,1,0,0,0}) == 0,"wake counts actual waiters");
    int descriptors[2]; require(!::pipe(descriptors),"syscall I/O test pipe");
    const char payload[]="raw Linux syscall"; char received[sizeof(payload)]{};
    require(raw_with(64,{uint64_t(descriptors[1]),uintptr_t(payload),sizeof(payload),0,0,0}) == sizeof(payload),"raw write uses translated provider");
    require(raw_with(63,{uint64_t(descriptors[0]),uintptr_t(received),sizeof(received),0,0,0}) == sizeof(received) &&
        !std::memcmp(payload,received,sizeof(payload)),"raw read returns actual bytes");
    ::close(descriptors[0]);::close(descriptors[1]);
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
    long unsupported = syscall(0x7ffffffe,error);
    require(unsupported == -1 && error == 38, "unknown syscall remains explicitly ENOSYS");
    require(syscall(0x7fffffff,error) == -1 && error == 38, "unknown Linux syscall is not forwarded");
    int logged = int(call("process_log",{}));
    std::fflush(stderr);
    require(dup2(saved_stderr,STDERR_FILENO) >= 0, "restore process log surface"); close(saved_stderr);
    restoring.descriptor = -1;
    auto evidence = read_pipe(log_pipe[0]); close(log_pipe[0]);
    require(logged == 73 && evidence.find("number=2147483646") != std::string::npos &&
                evidence.find("guest-copy[") != std::string::npos && evidence.find("scalar=-31 float=2.75 text=guest %") != std::string::npos &&
                evidence.find("Xuest-copy") == std::string::npos,
            "copied syslog ident, PID/PERROR and real AAPCS formatted delivery");
    require(call("process_log_bad",{}) == 22, "unknown log option is explicit EINVAL");
    for (int fast = 0; fast < 2; ++fast) {
      const char *mode = fast ? "--child-fast-exit" : "--child-exit";
      const auto evidence = process_child(argv[0], argv[1], mode, true);
      require_child(evidence, mode, evidence.complete && WIFEXITED(evidence.status) &&
          WEXITSTATUS(evidence.status) == 7 && evidence.output == (fast ? "" : "F"),
          "exit runs guest finalizers and _exit skips them");
    }
    const auto aborted = process_child(argv[0], argv[1], "--child-abort");
    require_child(aborted, "--child-abort", aborted.complete && WIFEXITED(aborted.status) &&
        WEXITSTATUS(aborted.status) == 97 && aborted.output.find("owned guest abort") != std::string::npos &&
        aborted.output.find("later message") == std::string::npos,
        "abort emits an owned copy of the first guest message then really aborts");
    std::cout << "M5_PROCESS_PASS syscall_ids=7 raw_svc=negative_errno raw_io=real getrandom=real futex=wait_wake_timeout_bitset_validation syslog=native_asl perror=1 abort=owned_first nonreturning_contract=1 exit_finalizers=1 fast_exit_finalizers=0 threads=4\n";
    std::cout << "M5_SVC_STACK_PASS original_sp_rejected=1 fresh_exec=1 exact_diagnostic=1\n";
    std::cout << "M5_PROCESS_CHILDREN_PASS fresh_exec=4 bounded_collection=1 waitable_parent=1 "
                 "postfork_guest_execution=0 inherited_cpp_tls=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
