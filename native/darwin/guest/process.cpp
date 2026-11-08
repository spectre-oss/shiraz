// SPDX-License-Identifier: GPL-3.0-only
// Original adapters; ABI/behavior references are pinned Android14 bionic
// asm-generic/unistd.h, linux/random.h, syslog.h, syslog.cpp and
// android_set_abort_message.cpp. Native ASL clients are private to each log
// delivery; host process-global openlog/closelog state is never changed.
#include "shiraz/darwin/guest/process.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include <algorithm>
#include <array>
#include <asl.h>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <mutex>
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
public:
  explicit Process(std::shared_ptr<Lifecycle> lifecycle) : lifecycle_(std::move(lifecycle)) {}
  long syscall(long number, CallCursor &arguments) {
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
std::vector<ProviderExport> process_exports(std::shared_ptr<Lifecycle> lifecycle) {
  auto state = std::make_shared<Process>(std::move(lifecycle));
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
