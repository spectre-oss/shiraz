// SPDX-License-Identifier: GPL-3.0-only
// Original adapters; ABI/behavior references are pinned Android14 bionic
// asm-generic/unistd.h, linux/random.h, syslog.h, syslog.cpp and
// android_set_abort_message.cpp. Native ASL clients are private to each log
// delivery; host process-global openlog/closelog state is never changed.
#include "shiraz/darwin/guest/process.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include <algorithm>
#include <array>
#include <asl.h>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <map>
#include <condition_variable>
#include <chrono>
#include <list>
#include <csignal>
#include <pthread.h>
#include <set>
#include <unistd.h>

namespace shiraz::guest {
namespace {
long failure(int host_error) { *guest_errno_location() = errno_to_guest(host_error); return -1; }
const char *facility_name(int number) {
  static const char *const names[] = {"kern","user","mail","daemon","auth","syslog",
    "lpr","news","uucp","cron","authpriv","ftp"};
  if (number >= 0 && number < 12) return names[number];
  static const char *const local[] = {"local0","local1","local2","local3","local4","local5","local6","local7"};
  return number >= 16 && number <= 23 ? local[number - 16] : nullptr;
}
// ASL is the native syslog client API with isolated ident/facility/filter
// configuration. Its deprecation does not justify changing host-global
// syslog state or claiming delivery without actually invoking an OS sink.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
bool native_log(const std::string &ident, int facility, int priority, const std::string &message) {
  asl_object_t client = asl_open(ident.c_str(), facility_name(facility), ASL_OPT_NO_REMOTE);
  if (!client) return false;
  asl_set_filter(client, ASL_FILTER_MASK_UPTO(ASL_LEVEL_DEBUG));
  int result = asl_log(client, nullptr, priority, "%s", message.c_str());
  asl_close(client);
  return result == 0;
}
#pragma clang diagnostic pop
class Process {
  struct LogConfig { std::string ident; int options = 0, facility = 1; };
  std::mutex mutex_;
  LogConfig log_;
  std::set<long> diagnosed_;
  bool suppressed_ = false;
  std::array<char,4096> abort_message_{};
  bool have_abort_message_ = false;
  std::shared_ptr<Lifecycle> lifecycle_;
  std::map<long, uintptr_t> syscalls_;
  struct Waiter { uint32_t *address; uint32_t mask; bool ready = false; std::condition_variable condition; };
  std::mutex futex_mutex_;
  std::list<std::shared_ptr<Waiter>> waiters_;
  long futex(CallCursor &arguments) {
    auto *address = arguments.read<uint32_t *>();
    unsigned operation = arguments.read<unsigned>();
    uint32_t value = arguments.read<uint32_t>();
    auto *timeout = arguments.read<const int64_t *>();
    (void)arguments.read<void *>();
    uint32_t mask = arguments.read<uint32_t>();
    if (!address) return failure(EFAULT);
    if (uintptr_t(address) & 3) return failure(EINVAL);
    if (operation & ~0x1ffu) return failure(ENOSYS);
    unsigned command = operation & 0x7f;
    if (command != 0 && command != 1 && command != 9 && command != 10)
      return failure(ENOSYS);
    bool bitset = command == 9 || command == 10;
    if (bitset && !mask) return failure(EINVAL);
    if (!bitset) mask = UINT32_MAX;
    if ((operation & 256) && command != 9) return failure(ENOSYS);
    std::unique_lock<std::mutex> lock(futex_mutex_);
    if (command == 1 || command == 10) {
      if (int32_t(value) < 0) return failure(EINVAL);
      long woken = 0;
      for (const auto &waiter : waiters_)
        if (woken < value && waiter->address == address && (waiter->mask & mask) && !waiter->ready) {
          waiter->ready = true; ++woken; waiter->condition.notify_one();
        }
      return woken;
    }
    // Checking and queueing under the same mutex as WAKE prevents lost wakes.
    if (__atomic_load_n(address, __ATOMIC_SEQ_CST) != value) return failure(EAGAIN);
    bool timed = timeout != nullptr;
    std::chrono::steady_clock::time_point deadline;
    if (timed) {
      int64_t fields[2]; std::memcpy(fields, timeout, sizeof(fields));
      if (fields[0] < 0 || fields[1] < 0 || fields[1] >= 1000000000) return failure(EINVAL);
      // Bound arithmetic without shortening valid long waits.
      if (fields[0] > INT64_MAX / 1000000000 - 1) return failure(EOVERFLOW);
      auto duration = std::chrono::seconds(fields[0]) + std::chrono::nanoseconds(fields[1]);
      auto now = std::chrono::steady_clock::now();
      if (command == 9) {
        timespec clock{};
        if (::clock_gettime(operation & 256 ? CLOCK_REALTIME : CLOCK_UPTIME_RAW, &clock)) return failure(errno);
        duration -= std::chrono::seconds(clock.tv_sec) + std::chrono::nanoseconds(clock.tv_nsec);
      }
      if (duration <= std::chrono::nanoseconds::zero()) return failure(ETIMEDOUT);
      if (duration > std::chrono::steady_clock::time_point::max() - now) return failure(EOVERFLOW);
      deadline = now + duration;
    }
    if (waiters_.size() >= 4096) return failure(EAGAIN);
    auto waiter = std::make_shared<Waiter>(); waiter->address = address; waiter->mask = mask;
    auto position = waiters_.insert(waiters_.end(), waiter);
    bool woke = true;
    if (timed) woke = waiter->condition.wait_until(lock, deadline, [&] { return waiter->ready; });
    else waiter->condition.wait(lock, [&] { return waiter->ready; });
    waiters_.erase(position);
    return woke ? 0 : failure(ETIMEDOUT);
  }
public:
  explicit Process(std::shared_ptr<Lifecycle> lifecycle, const std::vector<ProviderExport> &adapters)
      : lifecycle_(std::move(lifecycle)) {
    // Only ABI-equivalent syscall signatures use existing Linux ABI adapters.
    // Structures, flags and owned state remain translated by those adapters.
    const std::pair<long, const char *> entries[] = {
      {20,"epoll_create1"},{21,"epoll_ctl"},{23,"dup"},{24,"dup3"},{25,"fcntl"},{29,"ioctl"},
      {34,"mkdirat"},{35,"unlinkat"},{37,"linkat"},{38,"renameat"},
      {46,"ftruncate"},{47,"posix_fallocate"},{49,"chdir"},{50,"fchdir"},
      {52,"fchmod"},{56,"openat"},{57,"close"},
      {59,"pipe2"},{61,"getdents64"},{62,"lseek"},{63,"read"},{64,"write"},
      {65,"readv"},{66,"writev"},{67,"pread64"},{68,"pwrite64"},
      {73,"ppoll"},{78,"readlinkat"},{79,"fstatat"},{80,"fstat"},
      {82,"fsync"},{83,"fdatasync"},{101,"nanosleep"},
      {113,"clock_gettime"},{114,"clock_getres"},{115,"clock_nanosleep"},{123,"sched_getaffinity"},{124,"sched_yield"},
      {131,"tgkill"},{134,"sigaction"},{135,"sigprocmask"},
      {160,"uname"},{163,"getrlimit"},{164,"setrlimit"},{165,"getrusage"},
      {167,"prctl"},{169,"gettimeofday"},{198,"socket"},{199,"socketpair"},
      {200,"bind"},{201,"listen"},{202,"accept"},{203,"connect"},
      {204,"getsockname"},{205,"getpeername"},{206,"sendto"},{207,"recvfrom"},
      {208,"setsockopt"},{209,"getsockopt"},{210,"shutdown"},
      {211,"sendmsg"},{212,"recvmsg"},{215,"munmap"},{216,"mremap"},
      {222,"mmap"},{223,"posix_fadvise"},{226,"mprotect"},{227,"msync"},{233,"madvise"},
      {242,"accept4"}
    };
    for (const auto &entry : entries)
      for (const auto &adapter : adapters)
        if (adapter.name == entry.second && adapter.kind == SymbolKind::Function)
          syscalls_.emplace(entry.first, adapter.address);
  }
  long syscall(long number, CallCursor &arguments) {
    if(in_guest_signal_handler() && (number<172||number>178))return failure(ENOTSUP);
    if (const char *trace = std::getenv("SHIRAZ_GUEST_TRACE_SYSCALLS"); trace && !std::strcmp(trace,"1"))
      std::fprintf(stderr,"SHIRAZ_GUEST_SYSCALL number=%ld\n",number);
    if (number == 98) return futex(arguments);
    if (number == 132) {
      struct GuestStack { void *base; int32_t flags; uint32_t padding; uint64_t size; };
      static_assert(sizeof(GuestStack)==24);
      auto *input=arguments.read<const GuestStack *>(); auto *output=arguments.read<GuestStack *>();
      stack_t requested{},previous{};
      if(input){
        GuestStack guest;std::memcpy(&guest,input,sizeof(guest));
        if(guest.flags & ~2)return failure(EINVAL);
        requested.ss_sp=guest.base;requested.ss_size=guest.size;requested.ss_flags=guest.flags & 2?SS_DISABLE:0;
      }
      if(::sigaltstack(input?&requested:nullptr,output?&previous:nullptr))return failure(errno);
      if(output){GuestStack guest{previous.ss_sp,(previous.ss_flags & SS_DISABLE?2:0)|(previous.ss_flags & SS_ONSTACK?1:0),0,previous.ss_size};std::memcpy(output,&guest,sizeof(guest));}
      return 0;
    }
    auto adapter = syscalls_.find(number);
    if (adapter != syscalls_.end()) {
      std::vector<Argument> values;
      for (unsigned i = 0; i < 6; ++i)
        values.push_back(Argument::integer(arguments.read<int64_t>(), ArgumentType::Int64));
      if(number==134 || number==135) {
        if(values[3].bits!=8)return failure(EINVAL);
        if(number==134) {
          // Kernel AArch64 sigaction is handler/flags/restorer/mask. Bionic's
          // public sigaction has flags/handler/mask/restorer instead.
          uint64_t input[4]{},output[4]{};
          if(values[1].bits){uint64_t kernel[4];std::memcpy(kernel,reinterpret_cast<void *>(values[1].bits),32);
            if(kernel[1]>UINT32_MAX)return failure(EINVAL);
            input[0]=kernel[1];input[1]=kernel[0];input[2]=kernel[3];input[3]=kernel[2];}
          void *destination=reinterpret_cast<void *>(values[2].bits);
          values[1]=Argument::integer(values[1].bits?uintptr_t(input):0,ArgumentType::Int64);
          values[2]=Argument::integer(destination?uintptr_t(output):0,ArgumentType::Int64);
          long result=long(invoke(reinterpret_cast<void *>(adapter->second),values).result_integers[0]);
          if(!result && destination){uint64_t kernel[4]={output[1],output[0],output[3],output[2]};std::memcpy(destination,kernel,32);}
          return result;
        }
      }
      long result=long(invoke(reinterpret_cast<void *>(adapter->second), values).result_integers[0]);
      if((number==47||number==115||number==223)&&result){*guest_errno_location()=int(result);return -1;}
      if(number==123&&!result){long cpus=::sysconf(_SC_NPROCESSORS_ONLN);return ((cpus+63)/64)*8;}
      return result;
    }
    switch (number) {
    case 172: return ::getpid();
    case 173: return ::getppid();
    case 174: return ::getuid();
    case 175: return ::geteuid();
    case 176: return ::getgid();
    case 177: return ::getegid();
    case 178: {
      uint64_t tid = 0;
      int error = pthread_threadid_np(nullptr, &tid);
      if (error) return failure(error);
      if (!tid || tid > INT32_MAX) return failure(EOVERFLOW);
      return long(tid);
    }
    case 278: {
      void *destination = arguments.read<void *>();
      size_t length = arguments.read<size_t>();
      unsigned flags = arguments.read<unsigned>();
      if ((flags & ~7u) || ((flags & 2) && (flags & 4))) return failure(EINVAL);
      if (!destination && length) return failure(EFAULT);
      // Linux getrandom permits short reads. Bound each operation to1MiB
      // instead of silently truncating a successful return count or looping
      // over an attacker-selected unbounded byte count.
      size_t count = std::min(length, size_t(1) << 20);
      if (count) arc4random_buf(destination, count);
      return long(count);
    }
    default: {
      bool emit = false, suppress = false;
      {
        std::lock_guard<std::mutex> lock(mutex_);
        if (diagnosed_.size() < 32) emit = diagnosed_.insert(number).second;
        else if (!suppressed_) { suppressed_ = true; suppress = true; }
      }
      if (emit) std::fprintf(stderr, "SHIRAZ_GUEST_SYSCALL_UNSUPPORTED number=%ld%s\n", number,
          number == 98 || number == 422 || number == 449 ? " futex=unfinished" : "");
      if (suppress) std::fprintf(stderr, "SHIRAZ_GUEST_SYSCALL_UNSUPPORTED further_unique_diagnostics=suppressed\n");
      return failure(ENOSYS);
    }
    }
  }
  void openlog(const char *ident, int options, int facility) {
    if ((options & ~0x3f) || (facility & 7) || !facility_name(facility >> 3)) {
      failure(EINVAL); return;
    }
    std::string copied;
    if (ident) {
      size_t size = strnlen(ident, 256);
      if (size == 256) { failure(EINVAL); return; }
      copied.assign(ident, size);
    }
    std::lock_guard<std::mutex> lock(mutex_);
    log_ = {std::move(copied), options, facility >> 3};
  }
  void closelog() { std::lock_guard<std::mutex> lock(mutex_); log_ = {}; }
  void syslog(int priority, std::string message) {
    if (priority < 0 || (priority & ~0x3ff)) { failure(EINVAL); return; }
    LogConfig config;
    { std::lock_guard<std::mutex> lock(mutex_); config = log_; }
    int facility = (priority & 0x3f8) ? (priority & 0x3f8) >> 3 : config.facility;
    if (!facility_name(facility)) { failure(EINVAL); return; }
    if (config.ident.empty()) {
      const char *program = getprogname();
      config.ident.assign(program ? program : "shiraz", strnlen(program ? program : "shiraz", 255));
    }
    size_t terminator = message.find('\0');
    if (terminator != std::string::npos) message.resize(terminator);
    if (message.size() > 1023) message.resize(1023);
    bool delivered = native_log(config.ident, facility, priority & 7, message);
    if (config.options & 0x20) {
      std::string line = config.ident;
      if (config.options & 1) line += "[" + std::to_string(::getpid()) + "]";
      line += ": " + message + "\n";
      ::flockfile(stderr);
      bool written = ::fwrite(line.data(), 1, line.size(), stderr) == line.size();
      bool flushed = !::fflush(stderr);
      ::funlockfile(stderr);
      if (!written || !flushed) delivered = false;
    }
    if (!delivered) failure(EIO);
    // Android ignores CONS/ODELAY/NDELAY/NOWAIT. Native delivery here neither
    // spawns children nor mutates global syslog connection/console settings.
  }
  void abort_message(const char *message) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (have_abort_message_) return; // Preserve the first crash's explanation.
    if (!message) message = "(null)";
    size_t size = strnlen(message, abort_message_.size() - 1);
    std::memcpy(abort_message_.data(), message, size);
    abort_message_[size] = 0;
    have_abort_message_ = true;
  }
  [[noreturn]] void abort() {
    int previous_errno = errno;
    std::array<char,4096> message{};
    bool present;
    { std::lock_guard<std::mutex> lock(mutex_); message = abort_message_; present = have_abort_message_; }
    if (present) std::fprintf(stderr, "SHIRAZ_GUEST_ABORT %s\n", message.data());
    std::fflush(stderr);
    errno = previous_errno;
    ::abort();
  }
  [[noreturn]] void exit(int status) {
    if (lifecycle_) lifecycle_->shutdown();
    ::exit(status);
  }
};
} // namespace
std::vector<ProviderExport> process_exports(std::shared_ptr<Lifecycle> lifecycle,
                                          std::vector<ProviderExport> adapters) {
  auto state = std::make_shared<Process>(std::move(lifecycle), adapters);
  std::vector<ProviderExport> result;
  auto add = [&](const char *name, std::function<void(Capture &)> handler) {
    result.push_back({name, "LIBC", reinterpret_cast<uintptr_t>(closure(std::move(handler))),
                      SymbolKind::Function, "guest-process/audited-no-raw-syscalls", false});
  };
  add("syscall", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    long number = cursor.read<long>();
    HostErrno preserve;
    try { return_value(capture, state->syscall(number, cursor)); }
    catch (const std::bad_alloc &) { return_value(capture, failure(ENOMEM)); }
  });
  add("gettid",[state](Capture &capture){CallCursor cursor(capture,Convention::Aapcs64);HostErrno preserve;return_value(capture,state->syscall(178,cursor));});
  add("sigaltstack",[state](Capture &capture){CallCursor cursor(capture,Convention::Aapcs64);HostErrno preserve;return_value(capture,state->syscall(132,cursor));});
  add("__shiraz_linux_syscall", [state](Capture &capture) {
    // Island supplies x0..x5 arguments and x8 syscall number in x6. Unlike
    // libc syscall(), Linux SVC returns negative errno and preserves TLS errno.
    Capture arguments{};
    for (unsigned i = 0; i < 6; ++i) arguments.frame.integers[i] = capture.frame.integers[i];
    CallCursor cursor(arguments, Convention::Aapcs64);
    HostErrno preserve;
    int saved = *guest_errno_location();
    long value;
    try { value = state->syscall(long(capture.frame.integers[6]), cursor); }
    catch (const std::bad_alloc &) { value = failure(ENOMEM); }
    if (value == -1) value = -long(*guest_errno_location());
    *guest_errno_location() = saved;
    return_value(capture, value);
  });
  add("openlog", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const char *ident = cursor.read<const char *>();
    int options = cursor.read<int>(), facility = cursor.read<int>();
    HostErrno preserve;
    try { state->openlog(ident, options, facility); }
    catch (const std::bad_alloc &) { failure(ENOMEM); }
  });
  add("closelog", [state](Capture &) { HostErrno preserve; state->closelog(); });
  add("syslog", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    int priority = cursor.read<int>();
    const char *format = cursor.read<const char *>();
    HostErrno preserve;
    try {
      auto message = format_guest(format, [&](ArgumentType type) {
        return cursor.next(type == ArgumentType::Int32 ? 4 : 8, type == ArgumentType::Double);
      });
      state->syslog(priority, std::move(message));
    } catch (const GuestFormatError &error) { failure(error.error); }
      catch (const std::bad_alloc &) { failure(ENOMEM); }
  });
  add("android_set_abort_message", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const char *message = cursor.read<const char *>();
    HostErrno preserve;
    state->abort_message(message);
  });
  result.push_back(explicit_noreturn_closure_export("abort", "LIBC", [state] { state->abort(); }));
  add("exit", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    state->exit(cursor.read<int>());
  });
  add("_exit", [](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    ::_exit(cursor.read<int>());
  });
  return result;
}
} // namespace shiraz::guest
