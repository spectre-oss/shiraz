// SPDX-License-Identifier: GPL-3.0-only
// Original Linux/Bionic LP64 signal/wait translation, checked against pinned
// Android14 bits/signal_types.h, asm-generic/{signal,siginfo}.h and
// linux/wait.h.
#include "shiraz/darwin/guest/signals.hpp"
#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <signal.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
namespace shiraz::guest {
namespace {
int fail(int error) {
  errno = error;
  return -1;
}
int host_signal(int guest) {
  switch (guest) {
  case 0:
    return 0;
  case 1:
    return SIGHUP;
  case 2:
    return SIGINT;
  case 3:
    return SIGQUIT;
  case 4:
    return SIGILL;
  case 5:
    return SIGTRAP;
  case 6:
    return SIGABRT;
  case 7:
    return SIGBUS;
  case 8:
    return SIGFPE;
  case 9:
    return SIGKILL;
  case 10:
    return SIGUSR1;
  case 11:
    return SIGSEGV;
  case 12:
    return SIGUSR2;
  case 13:
    return SIGPIPE;
  case 14:
    return SIGALRM;
  case 15:
    return SIGTERM;
  case 17:
    return SIGCHLD;
  case 18:
    return SIGCONT;
  case 19:
    return SIGSTOP;
  case 20:
    return SIGTSTP;
  case 21:
    return SIGTTIN;
  case 22:
    return SIGTTOU;
  case 23:
    return SIGURG;
  case 24:
    return SIGXCPU;
  case 25:
    return SIGXFSZ;
  case 26:
    return SIGVTALRM;
  case 27:
    return SIGPROF;
  case 28:
    return SIGWINCH;
  case 29:
    return SIGIO;
  case 31:
    return SIGSYS;
  default:
    return -1;
  }
}
int guest_signal(int host) {
  if (host <= 0)
    return -1;
  for (int i = 1; i <= 31; ++i)
    if (host_signal(i) == host)
      return i;
  return -1;
}
struct GuestAction {
  uint32_t flags = 0, padding = 0;
  uintptr_t handler = 0;
  uint64_t mask = 0;
  uintptr_t restorer = 0;
};
static_assert(sizeof(GuestAction) == 32 &&
              offsetof(GuestAction, handler) == 8 &&
              offsetof(GuestAction, mask) == 16);
constexpr uint32_t gRestart = 0x10000000, gNodefer = 0x40000000,
                   gReset = 0x80000000;
constexpr uint32_t gSupported = 1 | 2 | gRestart | gNodefer | gReset;
int flags_to_host(uint32_t flags) {
  int result = 0;
  if (flags & 1)
    result |= SA_NOCLDSTOP;
  if (flags & 2)
    result |= SA_NOCLDWAIT;
  if (flags & gRestart)
    result |= SA_RESTART;
  if (flags & gNodefer)
    result |= SA_NODEFER;
  if (flags & gReset)
    result |= SA_RESETHAND;
  return result;
}
bool mask_to_host(uint64_t bits, sigset_t &mask) {
  sigemptyset(&mask);
  for (int i = 1; i <= 64; ++i)
    if (bits & (uint64_t(1) << (i - 1))) {
      int host = host_signal(i);
      if (host < 0)
        return false;
      sigaddset(&mask, host);
    }
  return true;
}
uint64_t mask_to_guest(const sigset_t &mask) {
  uint64_t bits = 0;
  for (int i = 1; i <= 31; ++i) {
    int host = host_signal(i);
    if (host > 0 && sigismember(&mask, host) == 1)
      bits |= uint64_t(1) << (i - 1);
  }
  return bits;
}
bool representable_mask(const sigset_t &mask) {
  for (int i = 1; i < NSIG; ++i)
    if (sigismember(&mask, i) == 1 && guest_signal(i) < 0)
      return false;
  return true;
}
struct Record {
  GuestAction action;
  const void *owner;
  int guest;
};
struct Slot {
  std::atomic<const Record *> record{nullptr};
  std::atomic<unsigned> active{0};
  const void *owner = nullptr;
};
std::array<Slot, NSIG> slots;
auto &dispositions = *new std::mutex;
struct MaskCounter { std::atomic<size_t> dirty{0}; };
struct MaskLease {
  std::shared_ptr<MaskCounter> counter;
  uint64_t baseline = 0, managed = 0;
  bool dirty = false;
  ~MaskLease() { if (dirty) counter->dirty.fetch_sub(1, std::memory_order_acq_rel); }
};
struct MaskLeases { std::vector<std::unique_ptr<MaskLease>> entries; };
pthread_once_t mask_once = PTHREAD_ONCE_INIT;
pthread_key_t mask_key;
int mask_key_error = 0;
void create_mask_key() {
  mask_key_error = ::pthread_key_create(&mask_key, [](void *value) {
    delete static_cast<MaskLeases *>(value);
  });
}
MaskLease *mask_lease(const std::shared_ptr<MaskCounter> &counter, uint64_t baseline) {
  auto *leases = static_cast<MaskLeases *>(::pthread_getspecific(mask_key));
  if (!leases) {
    auto next = std::make_unique<MaskLeases>();
    int error = ::pthread_setspecific(mask_key, next.get());
    if (error) { errno = error; return nullptr; }
    leases = next.release();
  }
  for (auto &lease : leases->entries)
    if (lease->counter == counter) return lease.get();
  if (leases->entries.size() == 128) { errno = EAGAIN; return nullptr; }
  auto next = std::make_unique<MaskLease>();
  next->counter = counter;
  next->baseline = baseline;
  auto *result = next.get();
  leases->entries.push_back(std::move(next));
  return result;
}
static_assert(std::atomic<const Record *>::is_always_lock_free &&
              std::atomic<unsigned>::is_always_lock_free);
thread_local unsigned handling_depth = 0;
thread_local const void *handling_owner = nullptr;
extern "C" void shiraz_guest_signal_call(uintptr_t, int);
void handler(int native) {
  int previous_errno = errno;
  if (native <= 0 || native >= NSIG)
    return;
  auto &slot = slots[size_t(native)];
  // Entry and shutdown form one two-atomic handshake. SC forbids both
  // sides observing the old value: a callback retaining an old record must
  // announce itself before shutdown's zero check, or finish before it.
  slot.active.fetch_add(1, std::memory_order_seq_cst);
  const Record *record = slot.record.load(std::memory_order_seq_cst);
  if (record && record->action.handler > 1) {
    if (!guest_tls_prepared()) {
      constexpr char text[] =
          "SHIRAZ_GUEST_FATAL signal delivery on unprepared native thread\n";
      (void)::write(STDERR_FILENO, text, sizeof(text) - 1);
      ::_exit(128 + record->guest);
    }
    auto *previous = handling_owner;
    handling_owner = record->owner;
    ++handling_depth;
    {
      CaptureBoundary transition;
      shiraz_guest_signal_call(record->action.handler, record->guest);
    }
    --handling_depth;
    handling_owner = previous;
  }
  slot.active.fetch_sub(1, std::memory_order_seq_cst);
  errno = previous_errno;
}
int status_to_guest(int native, int &guest) {
  if (WIFCONTINUED(native)) {
    guest = 0xffff;
    return 0;
  }
  if (WIFEXITED(native)) {
    guest = WEXITSTATUS(native) << 8;
    return 0;
  }
  int signal =
      guest_signal(WIFSTOPPED(native) ? WSTOPSIG(native) : WTERMSIG(native));
  if (signal < 0)
    return fail(EOVERFLOW);
  guest = WIFSTOPPED(native) ? (signal << 8) | 0x7f : signal | (native & 0x80);
  return 0;
}
int wait_options(int options, bool id) {
  int accepted = id ? (1 | 2 | 4 | 8 | 0x01000000) : (1 | 2 | 8);
  if (options & ~accepted)
    return fail(EINVAL);
  int host = 0;
  if (options & 1)
    host |= WNOHANG;
  if (options & 2)
    host |= id ? WSTOPPED : WUNTRACED;
  if (options & 4)
    host |= WEXITED;
  if (options & 8)
    host |= WCONTINUED;
  if (options & 0x01000000)
    host |= WNOWAIT;
  return host;
}
struct GuestUsage {
  int64_t user_seconds, user_microseconds, system_seconds, system_microseconds;
  int64_t counters[14];
};
static_assert(sizeof(GuestUsage) == 144);
pid_t wait_four(pid_t pid, int *status, int options, void *usage) {
  int native_options = wait_options(options, false);
  if (native_options < 0)
    return -1;
  int native_status = 0;
  struct rusage native_usage{};
  pid_t result = ::wait4(pid, &native_status, native_options,
                         usage ? &native_usage : nullptr);
  if (result <= 0)
    return result;
  int translated = 0;
  if (status_to_guest(native_status, translated))
    return -1;
  if (status)
    std::memcpy(status, &translated, sizeof(translated));
  if (usage) {
    GuestUsage output{native_usage.ru_utime.tv_sec,
                      native_usage.ru_utime.tv_usec,
                      native_usage.ru_stime.tv_sec,
                      native_usage.ru_stime.tv_usec,
                      {native_usage.ru_maxrss / 1024, native_usage.ru_ixrss,
                       native_usage.ru_idrss, native_usage.ru_isrss,
                       native_usage.ru_minflt, native_usage.ru_majflt,
                       native_usage.ru_nswap, native_usage.ru_inblock,
                       native_usage.ru_oublock, native_usage.ru_msgsnd,
                       native_usage.ru_msgrcv, native_usage.ru_nsignals,
                       native_usage.ru_nvcsw, native_usage.ru_nivcsw}};
    std::memcpy(usage, &output, sizeof(output));
  }
  return result;
}
pid_t wait_pid(pid_t pid, int *status, int options) {
  return wait_four(pid, status, options, nullptr);
}
int wait_id(int type, uint32_t id, void *output, int options) {
  idtype_t native_type;
  switch (type) {
  case 0:
    native_type = P_ALL;
    break;
  case 1:
    native_type = P_PID;
    break;
  case 2:
    native_type = P_PGID;
    break;
  default:
    return fail(EINVAL);
  }
  int native_options = wait_options(options, true);
  if (native_options < 0)
    return -1;
  if (!(options & (2 | 4 | 8)))
    return fail(EINVAL);
  siginfo_t information{};
  int result = ::waitid(native_type, id, &information, native_options);
  if (result < 0)
    return result;
  alignas(8) std::array<unsigned char, 128> guest{};
  if (information.si_pid) {
    int signo = guest_signal(information.si_signo);
    if (signo < 0)
      return fail(EOVERFLOW);
    int code = 0;
    switch (information.si_code) {
    case CLD_EXITED:
      code = 1;
      break;
    case CLD_KILLED:
      code = 2;
      break;
    case CLD_DUMPED:
      code = 3;
      break;
    case CLD_TRAPPED:
      code = 4;
      break;
    case CLD_STOPPED:
      code = 5;
      break;
    case CLD_CONTINUED:
      code = 6;
      break;
    default:
      return fail(EOVERFLOW);
    }
    int status = information.si_status;
    if (code != 1) {
      status = guest_signal(status);
      if (status < 0)
        return fail(EOVERFLOW);
    }
    auto put = [&](size_t offset, const auto &value) {
      std::memcpy(guest.data() + offset, &value, sizeof(value));
    };
    int error = errno_to_guest(information.si_errno);
    put(0, signo);
    put(4, error);
    put(8, code);
    put(16, information.si_pid);
    put(20, information.si_uid);
    put(24, status);
    // Other waitid siginfo fields are unspecified. Darwin supplies no child
    // CPU tick fields here; no fabricated accounting values are asserted.
  }
  if (output)
    std::memcpy(output, guest.data(), guest.size());
  return result;
}
int kill_guest(pid_t pid, int signal) {
  int host = host_signal(signal);
  if (host < 0)
    return fail(signal > 0 && signal <= 64 ? ENOTSUP : EINVAL);
  return ::kill(pid, host);
}
int raise_guest(int signal) {
  int native = host_signal(signal);
  if (native < 0) return fail(signal > 0 && signal <= 64 ? ENOTSUP : EINVAL);
  int error = ::pthread_kill(::pthread_self(), native);
  return error ? fail(error) : 0;
}
int empty_set(uint64_t *set) {
  if (!set)
    return fail(EFAULT);
  uint64_t zero = 0;
  std::memcpy(set, &zero, 8);
  return 0;
}
int fill_set(uint64_t *set) {
  if (!set)
    return fail(EFAULT);
  uint64_t all = UINT64_MAX;
  std::memcpy(set, &all, 8);
  return 0;
}
int change_set(uint64_t *set, int signal, bool add) {
  if (!set)
    return fail(EFAULT);
  if (signal < 1 || signal > 64)
    return fail(EINVAL);
  uint64_t bits;
  std::memcpy(&bits, set, 8);
  if (add)
    bits |= uint64_t(1) << (signal - 1);
  else
    bits &= ~(uint64_t(1) << (signal - 1));
  std::memcpy(set, &bits, 8);
  return 0;
}
int add_set(uint64_t *set, int signal) { return change_set(set, signal, true); }
int del_set(uint64_t *set, int signal) {
  return change_set(set, signal, false);
}
int member_set(const uint64_t *set, int signal) {
  if (!set)
    return fail(EFAULT);
  if (signal < 1 || signal > 64)
    return fail(EINVAL);
  uint64_t bits;
  std::memcpy(&bits, set, 8);
  return int((bits >> (signal - 1)) & 1);
}
int unsupported_fork() {
  std::fprintf(stderr, "SHIRAZ_GUEST_PROCESS_UNSUPPORTED "
                       "fork/vfork=unsafe-native-runtime-frames\n");
  return fail(ENOSYS);
}
int unsupported_exec(const char *, char *const *) {
  std::fprintf(stderr,
               "SHIRAZ_GUEST_PROCESS_UNSUPPORTED exec=guest-image-handoff\n");
  return fail(ENOSYS);
}
int unsupported_execve(const char *path, char *const *args, char *const *) {
  return unsupported_exec(path, args);
}
} // namespace
struct SignalRuntime::State {
  CodeValidator validate;
  bool attached = false, closing = false, closed = false;
  std::condition_variable stopped;
  std::array<struct sigaction, NSIG> originals{};
  std::array<bool, NSIG> saved{};
  std::vector<std::unique_ptr<Record>> records;
  std::shared_ptr<MaskCounter> masks = std::make_shared<MaskCounter>();
  explicit State(CodeValidator check) : validate(std::move(check)) {
    if (!validate)
      throw ElfError("signals require resident callback validation");
    int error = ::pthread_once(&mask_once, create_mask_key);
    if (error || mask_key_error) throw ElfError("cannot allocate guest signal-mask key");
  }
  int mask(int how, const uint64_t *input, uint64_t *output) {
    if (handling_depth) return fail(ENOTSUP);
    uint64_t requested = 0;
    sigset_t selected{};
    if (input) {
      std::memcpy(&requested, input, 8);
      requested &= ~((uint64_t(1) << 8) | (uint64_t(1) << 18));
      if (!mask_to_host(requested, selected)) return fail(ENOTSUP);
      if (how < 0 || how > 2) return fail(EINVAL);
    }
    std::lock_guard<std::mutex> lock(dispositions);
    if (closed || closing || !attached) return fail(ENOSYS);
    sigset_t current{};
    int error = ::pthread_sigmask(SIG_SETMASK, nullptr, &current);
    if (error) return fail(error);
    if (!representable_mask(current)) return fail(ENOTSUP);
    uint64_t before = mask_to_guest(current), after = before;
    if (input) {
      after = how == 0 ? before | requested : how == 1 ? before & ~requested : requested;
      uint64_t changes = before ^ after;
      // Native ART masks are not guest-owned state. Only dispositions installed
      // by this runtime may be changed; unchanged native bits are preserved.
      for (int number = 1; number <= 31; ++number) {
        if (!(changes & (uint64_t(1) << (number - 1)))) continue;
        int native = host_signal(number);
        if (native <= 0) return fail(ENOTSUP);
        auto &slot = slots[size_t(native)];
        auto *record = slot.record.load(std::memory_order_acquire);
        struct sigaction actual{};
        if (slot.owner != this || !record || ::sigaction(native, nullptr, &actual))
          return fail(EBUSY);
        auto expected = record->action.handler > 1 ? handler :
                        record->action.handler == 1 ? SIG_IGN : SIG_DFL;
        if (actual.sa_handler != expected) return fail(EBUSY);
      }
      if (changes) {
        auto *lease = mask_lease(masks, before); // Allocate before changing native state.
        if (!lease) return -1;
        sigset_t desired{};
        if (!mask_to_host(after, desired)) return fail(ENOTSUP);
        error = ::pthread_sigmask(SIG_SETMASK, &desired, nullptr);
        if (error) return fail(error);
        uint64_t newly_managed = changes & ~lease->managed;
        lease->baseline = (lease->baseline & ~newly_managed) | (before & newly_managed);
        lease->managed |= changes;
        bool dirty = ((after ^ lease->baseline) & lease->managed) != 0;
        if (dirty != lease->dirty) {
          if (dirty) masks->dirty.fetch_add(1, std::memory_order_acq_rel);
          else masks->dirty.fetch_sub(1, std::memory_order_acq_rel);
          lease->dirty = dirty;
        }
      }
    }
    if (output) std::memcpy(output, &before, 8);
    return 0;
  }
  int pending(uint64_t *output) {
    if (!output) return fail(EFAULT);
    if (handling_depth) return fail(ENOTSUP);
    std::lock_guard<std::mutex> lock(dispositions);
    if (closed || closing || !attached) return fail(ENOSYS);
    sigset_t native{};
    if (::sigpending(&native)) return -1;
    if (!representable_mask(native)) return fail(ENOTSUP);
    uint64_t bits = mask_to_guest(native);
    std::memcpy(output, &bits, 8);
    return 0;
  }
  int action(int number, const GuestAction *input, GuestAction *output) {
    if (handling_depth)
      return fail(ENOTSUP); // Never lock from an async callback.
    int native = host_signal(number);
    if (native <= 0)
      return fail(number > 0 && number <= 64 ? ENOTSUP : EINVAL);
    GuestAction requested{};
    if (input)
      std::memcpy(&requested, input, sizeof(requested));
    requested.mask &=
        ~((uint64_t(1) << 8) |
          (uint64_t(1) << 18)); // SIGKILL/SIGSTOP cannot be blocked.
    if (input && ((requested.flags & ~gSupported) || requested.restorer))
      return fail(ENOTSUP);
    sigset_t mask{};
    if (input && !mask_to_host(requested.mask, mask))
      return fail(ENOTSUP);
    prepare_guest_tls();
    std::lock_guard<std::mutex> lock(dispositions);
    if (closed || closing || !attached)
      return fail(ENOSYS);
    auto &slot = slots[size_t(native)];
    if (slot.owner && slot.owner != this)
      return fail(EBUSY);
    struct sigaction previous{};
    if (::sigaction(native, nullptr, &previous))
      return -1;
    auto old_record = slot.record.load(std::memory_order_acquire);
    GuestAction old{};
    if (slot.owner == this && old_record &&
        ((old_record->action.handler > 1 && previous.sa_handler == handler) ||
         (old_record->action.handler == 1 && previous.sa_handler == SIG_IGN) ||
         (!old_record->action.handler && previous.sa_handler == SIG_DFL)))
      old = old_record->action;
    else if (previous.sa_handler == SIG_DFL || previous.sa_handler == SIG_IGN) {
      if (!representable_mask(previous.sa_mask) ||
          (previous.sa_flags & ~(SA_NOCLDSTOP | SA_NOCLDWAIT | SA_RESTART |
                                 SA_NODEFER | SA_RESETHAND)))
        return fail(ENOTSUP);
      old.handler = previous.sa_handler == SIG_IGN ? 1 : 0;
      old.mask = mask_to_guest(previous.sa_mask);
      if (previous.sa_flags & SA_NOCLDSTOP)
        old.flags |= 1;
      if (previous.sa_flags & SA_NOCLDWAIT)
        old.flags |= 2;
      if (previous.sa_flags & SA_RESTART)
        old.flags |= gRestart;
      if (previous.sa_flags & SA_NODEFER)
        old.flags |= gNodefer;
      if (previous.sa_flags & SA_RESETHAND)
        old.flags |= gReset;
    } else
      return fail(EBUSY); // Preserve ART/native signal chaining ownership.
    if (input) {
      if (number == 9 || number == 19)
        return fail(EINVAL);
      if (requested.handler > 1 &&
          !validate(reinterpret_cast<const void *>(requested.handler)))
        return fail(EINVAL);
      if (records.size() >= 4096)
        return fail(EAGAIN);
      auto record = std::make_unique<Record>(Record{requested, this, number});
      const Record *pointer = record.get();
      struct sigaction next{};
      next.sa_mask = mask;
      next.sa_flags = flags_to_host(requested.flags);
      next.sa_handler = requested.handler > 1    ? handler
                        : requested.handler == 1 ? SIG_IGN
                                                 : SIG_DFL;
      records.push_back(std::move(record));
      if (!saved[size_t(native)]) {
        originals[size_t(native)] = previous;
        saved[size_t(native)] = true;
      }
      slot.owner = this;
      slot.record.store(pointer, std::memory_order_release);
      if (::sigaction(native, &next, nullptr)) {
        slot.record.store(old_record, std::memory_order_release);
        if (!old_record)
          slot.owner = nullptr;
        return -1;
      }
    }
    if (output)
      std::memcpy(output, &old, sizeof(old));
    return 0;
  }
  uintptr_t signal(int number, uintptr_t callback) {
    GuestAction input{};
    input.handler = callback;
    input.flags = gRestart;
    GuestAction old{};
    return action(number, &input, &old) ? UINTPTR_MAX : old.handler;
  }
  void shutdown() {
    if (handling_depth && handling_owner == this)
      throw ElfError("cannot unmap an executing guest signal callback");
    std::array<bool, NSIG> wait{};
    {
      std::unique_lock<std::mutex> lock(dispositions);
      if (closed)
        return;
      if (masks->dirty.load(std::memory_order_acquire))
        throw ElfError("guest signal masks must be restored before runtime shutdown");
      if (closing) {
        stopped.wait(lock, [&] { return closed; });
        return;
      }
      closing = true;
      for (size_t i = 1; i < slots.size(); ++i)
        if (slots[i].owner == this) {
          slots[i].record.store(nullptr, std::memory_order_seq_cst);
          if (saved[i] && ::sigaction(int(i), &originals[i], nullptr)) {
            std::fprintf(stderr, "SHIRAZ_GUEST_FATAL cannot restore native "
                                 "signal disposition\n");
            std::abort();
          }
          // Reserve closing ownership until the old callbacks drain. A new
          // owner must not feed this slot's shared counter during the wait.
          wait[i] = true;
        }
    }
    for (size_t i = 1; i < slots.size(); ++i)
      if (wait[i])
        while (slots[i].active.load(std::memory_order_seq_cst))
          std::this_thread::yield();
    {
      std::lock_guard<std::mutex> lock(dispositions);
      for (size_t i = 1; i < slots.size(); ++i)
        if (wait[i]) {
          if (slots[i].owner != this) std::abort();
          slots[i].owner = nullptr;
        }
      closed = true;
      validate = {};
      stopped.notify_all();
    }
  }
};
SignalRuntime::SignalRuntime(CodeValidator validate)
    : state_(std::make_shared<State>(std::move(validate))) {}
bool in_guest_signal_handler() noexcept { return handling_depth != 0; }
int guest_thread_signal(pthread_t thread,int signal){
  int native=host_signal(signal);if(native<0)return signal>0&&signal<=64?ENOTSUP:EINVAL;
  return ::pthread_kill(thread,native);
}
SignalRuntime::~SignalRuntime() {
  try {
    shutdown();
  } catch (...) {
    std::abort();
  }
}
void SignalRuntime::attach(Loader &loader) {
  auto state = state_;
  std::lock_guard<std::mutex> lock(dispositions);
  if (state->attached || state->closed)
    throw ElfError("signals already attached or closed");
  loader.add_preunmap_hook([state] { state->shutdown(); });
  state->attached = true;
}
void SignalRuntime::shutdown() const { state_->shutdown(); }
std::vector<ProviderExport> SignalRuntime::exports() const {
  auto state = state_;
  std::vector<ProviderExport> result;
  auto entry = [&](const char *name, std::function<void(Capture &)> fn) {
    result.push_back(
        {name, "LIBC", reinterpret_cast<uintptr_t>(closure(std::move(fn))),
         SymbolKind::Function,
         "guest-signals/translated-owned-callback-lifetime", false});
  };
  entry("sigaction", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    int number = cursor.read<int>();
    auto *input = cursor.read<const GuestAction *>();
    auto *output = cursor.read<GuestAction *>();
    HostErrno preserve;
    int result;
    try {
      result = state->action(number, input, output);
    } catch (const std::bad_alloc &) {
      result = fail(ENOMEM);
    }
    if (result < 0)
      *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
    return_value(capture, result);
  });
  entry("signal", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    int number = cursor.read<int>();
    uintptr_t callback = cursor.read<uintptr_t>();
    HostErrno preserve;
    uintptr_t result;
    try {
      result = state->signal(number, callback);
    } catch (const std::bad_alloc &) {
      fail(ENOMEM);
      result = UINTPTR_MAX;
    }
    if (result == UINTPTR_MAX)
      *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
    return_value(capture, reinterpret_cast<void *>(result));
  });
  for (bool pthread_api : {false, true}) {
    entry(pthread_api ? "pthread_sigmask" : "sigprocmask", [state, pthread_api](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      int how = cursor.read<int>();
      auto *input = cursor.read<const uint64_t *>();
      auto *output = cursor.read<uint64_t *>();
      HostErrno preserve;
      int result;
      try { result = state->mask(how, input, output); }
      catch (const std::bad_alloc &) { result = fail(ENOMEM); }
      if (result < 0) {
        int error = errno_to_guest(errno ? errno : EIO);
        if (pthread_api) result = error;
        else *guest_errno_location() = error;
      }
      return_value(capture, result);
    });
  }
  entry("sigpending", [state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    auto *output = cursor.read<uint64_t *>();
    HostErrno preserve;
    int result = state->pending(output);
    if (result < 0) *guest_errno_location() = errno_to_guest(errno ? errno : EIO);
    return_value(capture, result);
  });
  auto add = [&](const char *name, auto function) {
    result.push_back(
        fixed_export(name, "LIBC", function, ErrnoPolicy::MinusOne));
  };
  add("sigemptyset", &empty_set);
  add("sigfillset", &fill_set);
  add("sigaddset", &add_set);
  add("sigdelset", &del_set);
  add("sigismember", &member_set);
  add("kill", &kill_guest);
  add("raise", &raise_guest);
  add("waitpid", &wait_pid);
  add("wait4", &wait_four);
  add("waitid", &wait_id);
  add("setsid", &::setsid);
  add("fork", &unsupported_fork);
  add("vfork", &unsupported_fork);
  add("execv", &unsupported_exec);
  add("execvp", &unsupported_exec);
  add("execve", &unsupported_execve);
  return result;
}
} // namespace shiraz::guest
