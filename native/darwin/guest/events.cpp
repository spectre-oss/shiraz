// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/events.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "guest_io.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <poll.h>
#include <signal.h>
#include <sys/event.h>
#include <sys/select.h>
#include <time.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr uint64_t maximum = UINT64_MAX - 1;
constexpr size_t descriptor_limit = 4096, poll_limit = 4096;
static_assert(std::atomic<uint8_t>::is_always_lock_free,
              "ordinary signal-handler IO requires lock-free descriptor hints");
alignas(64) std::atomic<uint8_t> descriptor_hints[event_descriptor_bound]{};
bool hinted(int fd) noexcept {
  return fd >= 0 && fd < event_descriptor_bound &&
         descriptor_hints[fd].load(std::memory_order_acquire) != 0;
}
void set_hint(int fd, bool value) noexcept {
  descriptor_hints[fd].store(uint8_t(value), std::memory_order_release);
}
constexpr uint16_t in = 1, pri = 2, out = 4, error = 8, hup = 16, invalid = 32,
                   rdnorm = 64, rdband = 128, wrnorm = 256, wrband = 512;
constexpr uint16_t supported = in | pri | out | error | hup | invalid | rdnorm | rdband | wrnorm | wrband;
struct Failure { int error; };
template <typename T = int> T failure(int value) { errno = value; return T(-1); }
[[noreturn]] void invariant() {
  std::fputs("SHIRAZ_GUEST_FATAL eventfd readiness invariant\n", stderr);
  std::abort();
}
struct Pipe {
  int read = -1, write = -1;
  Pipe() {
    int descriptors[2];
    if (::pipe(descriptors)) throw Failure{errno};
    read = descriptors[0]; write = descriptors[1];
    for (int fd : descriptors)
      if (::fcntl(fd, F_SETFD, FD_CLOEXEC) || ::fcntl(fd, F_SETFL, O_NONBLOCK)) {
        int saved = errno; ::close(read); ::close(write); read = write = -1; throw Failure{saved};
      }
  }
  ~Pipe() {
    int saved = errno;
    if (read >= 0) ::close(read);
    if (write >= 0) ::close(write);
    errno = saved;
  }
  Pipe(const Pipe &) = delete;
  Pipe &operator=(const Pipe &) = delete;
};
void marker(Pipe &pipe, bool &present, bool wanted) {
  if (present == wanted) return;
  uint8_t byte = 1;
  ssize_t result;
  do { result = wanted ? ::write(pipe.write, &byte, 1) : ::read(pipe.read, &byte, 1); }
  while (result < 0 && errno == EINTR);
  if (result != 1) invariant();
  present = wanted;
}
struct Token {
  int descriptor = -1;
  Token() {
    descriptor = ::kqueue();
    if (descriptor < 0) throw Failure{errno};
    if (::fcntl(descriptor, F_SETFD, FD_CLOEXEC)) {
      int saved = errno; ::close(descriptor); descriptor = -1; throw Failure{saved};
    }
  }
  ~Token() { int saved = errno; if (descriptor >= 0) ::close(descriptor); errno = saved; }
  Token(const Token &) = delete;
  Token &operator=(const Token &) = delete;
};
struct Counter {
  std::mutex mutex;
  // Public aliases are empty kernel queues, never readiness pipes. A raw IO
  // race with dup2/fd reuse therefore cannot consume private marker bytes.
  Token token;
  Pipe readable, writable;
  bool read_marker = false, write_marker = false, semaphore, closed = false;
  uint64_t value;
  int status;
  size_t aliases = 0;
  std::vector<std::weak_ptr<Pipe>> writers;
  Counter(unsigned initial, int flags) : semaphore(flags & 1), value(initial),
      status(int(b::kO_RDWR) | (flags & int(b::kO_NONBLOCK))) { update(); }
  void update() {
    marker(readable, read_marker, value != 0 || closed);
    marker(writable, write_marker, value < maximum || closed);
  }
  void wake_writers() {
    for (auto it = writers.begin(); it != writers.end();) {
      if (auto pipe = it->lock()) {
        uint8_t byte = 1;
        ssize_t result;
        do { result = ::write(pipe->write, &byte, 1); } while (result < 0 && errno == EINTR);
        if (result != 1 && !(result < 0 && errno == EAGAIN)) invariant();
        ++it;
      } else it = writers.erase(it);
    }
  }
  void close_last() {
    std::lock_guard<std::mutex> lock(mutex);
    closed = true; update(); wake_writers();
  }
};
struct Registry { const pid_t process = ::getpid(); std::mutex mutex; std::map<int, std::shared_ptr<Counter>> descriptors; };
Registry &registry() { static auto *value = new Registry; return *value; }
bool same_process() { return registry().process == ::getpid(); }
std::shared_ptr<Counter> lookup(int fd) {
  if (!hinted(fd)) return nullptr;
  auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
  auto found = table.descriptors.find(fd);
  return found == table.descriptors.end() ? nullptr : found->second;
}
int duplicate_locked(Registry &table, int fd, const std::shared_ptr<Counter> &counter,
                     int minimum, bool cloexec) {
  if (table.descriptors.size() >= descriptor_limit) return failure(EMFILE);
  if (minimum >= event_descriptor_bound) return failure(EINVAL);
  int result = ::fcntl(fd, cloexec ? F_DUPFD_CLOEXEC : F_DUPFD, minimum);
  if (result < 0) return result;
  if (result >= event_descriptor_bound) { ::close(result); return failure(EMFILE); }
  set_hint(result, true);
  try { table.descriptors.emplace(result, counter); }
  catch (const std::bad_alloc &) { ::close(result); set_hint(result, false); return failure(ENOMEM); }
  ++counter->aliases;
  return result;
}
int create_counter(unsigned initial, int flags) {
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  if (!same_process()) return failure(ENOTSUP);
  if (flags & ~int(1 | b::kO_NONBLOCK | b::kO_CLOEXEC)) return failure(EINVAL);
  try {
    auto counter = std::make_shared<Counter>(initial, flags);
    auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
    return duplicate_locked(table, counter->token.descriptor, counter, 0, flags & b::kO_CLOEXEC);
  } catch (const Failure &error) { return failure(error.error); }
    catch (const std::bad_alloc &) { return failure(ENOMEM); }
}
int wait_readable(int fd) {
  pollfd descriptor{fd, POLLIN, 0};
  int result = ::poll(&descriptor, 1, -1);
  return result < 0 ? -1 : 0;
}
ssize_t counter_read(const std::shared_ptr<Counter> &counter, void *buffer, size_t count) {
  if (count < 8) return failure<ssize_t>(EINVAL);
  if (!buffer) return failure<ssize_t>(EFAULT);
  for (;;) {
    {
      std::lock_guard<std::mutex> lock(counter->mutex);
      if (counter->closed) return failure<ssize_t>(EBADF);
      if (counter->value) {
        uint64_t result = counter->semaphore ? 1 : counter->value;
        counter->value -= result;
        counter->update(); counter->wake_writers();
        std::memcpy(buffer, &result, sizeof(result));
        return 8;
      }
      if (counter->status & b::kO_NONBLOCK) return failure<ssize_t>(EAGAIN);
    }
    if (wait_readable(counter->readable.read)) return -1;
  }
}
ssize_t counter_write(const std::shared_ptr<Counter> &counter, const void *buffer, size_t count) {
  if (count < 8) return failure<ssize_t>(EINVAL);
  if (!buffer) return failure<ssize_t>(EFAULT);
  uint64_t value; std::memcpy(&value, buffer, sizeof(value));
  if (value == UINT64_MAX) return failure<ssize_t>(EINVAL);
  std::shared_ptr<Pipe> waiter;
  try {
    for (;;) {
      {
        std::lock_guard<std::mutex> lock(counter->mutex);
        if (counter->closed) return failure<ssize_t>(EBADF);
        if (value <= maximum - counter->value) {
          counter->value += value; counter->update(); return 8;
        }
        if (counter->status & b::kO_NONBLOCK) return failure<ssize_t>(EAGAIN);
        if (!waiter) {
          counter->writers.erase(std::remove_if(counter->writers.begin(), counter->writers.end(),
              [](const auto &entry) { return entry.expired(); }), counter->writers.end());
          if (counter->writers.size() >= 128) return failure<ssize_t>(EAGAIN);
          waiter = std::make_shared<Pipe>(); counter->writers.push_back(waiter);
        }
      }
      if (wait_readable(waiter->read)) return -1;
      uint8_t bytes[128];
      while (::read(waiter->read, bytes, sizeof(bytes)) > 0) {}
    }
  } catch (const Failure &error) { return failure<ssize_t>(error.error); }
    catch (const std::bad_alloc &) { return failure<ssize_t>(ENOMEM); }
}
int convenience_read(int fd, uint64_t *value) {
  ssize_t result = event_descriptor_read(fd, value, 8);
  if (result == 8) return 0;
  if (result < 0 && errno) *guest_errno_location() = errno_to_guest(errno);
  return -1;
}
int convenience_write(int fd, uint64_t value) {
  ssize_t result = event_descriptor_write(fd, &value, 8);
  if (result == 8) return 0;
  if (result < 0 && errno) *guest_errno_location() = errno_to_guest(errno);
  return -1;
}
struct GuestPoll { int32_t fd; uint16_t events, result; };
static_assert(sizeof(GuestPoll) == 8);
short native_events(uint16_t value) {
  short result = 0;
  if (value & in) result |= POLLIN;
  if (value & pri) result |= POLLPRI;
  if (value & out) result |= POLLOUT;
  if (value & rdnorm) result |= POLLRDNORM;
  if (value & rdband) result |= POLLRDBAND;
  if (value & wrnorm) result |= POLLWRNORM;
  if (value & wrband) result |= POLLWRBAND;
  return result;
}
uint16_t guest_events(short value, uint16_t requested) {
  uint16_t result = 0;
  if (value & POLLIN) result |= requested & in;
  if (value & POLLPRI) result |= requested & pri;
  if (value & POLLOUT) result |= requested & out;
  if (value & POLLRDNORM) result |= requested & rdnorm;
  if (value & POLLRDBAND) result |= requested & rdband;
  if (value & POLLWRNORM) result |= requested & wrnorm;
  if (value & POLLWRBAND) result |= requested & wrband;
  if (value & POLLERR) result |= error;
  if (value & POLLHUP) result |= hup;
  if (value & POLLNVAL) result |= invalid;
  return result;
}
struct PollEntry { GuestPoll guest; std::shared_ptr<Counter> counter; size_t first, second; };
struct PollSet {
  void *output;
  std::vector<PollEntry> entries;
  std::vector<pollfd> native;
  PollSet(void *address, uint32_t count) : output(address) {
    if (count > poll_limit) throw Failure{EINVAL};
    if (count && !address) throw Failure{EFAULT};
    entries.reserve(count); native.reserve(size_t(count) * 2);
    for (uint32_t i = 0; i < count; ++i) {
      GuestPoll guest; std::memcpy(&guest, static_cast<uint8_t *>(address) + i * 8, 8);
      if (guest.fd >= 0 && (guest.events & ~supported)) throw Failure{ENOTSUP};
      guest.result = 0;
      PollEntry entry{guest, guest.fd >= 0 ? lookup(guest.fd) : nullptr, native.size(), SIZE_MAX};
      if (entry.counter) {
        short read = guest.events & (in | rdnorm) ? POLLIN : 0;
        native.push_back({entry.counter->readable.read, read, 0});
        entry.second = native.size();
        short write = guest.events & (out | wrnorm) ? POLLIN : 0;
        native.push_back({entry.counter->writable.read, write, 0});
      } else native.push_back({guest.fd, native_events(guest.events), 0});
      entries.push_back(std::move(entry));
    }
  }
  int publish() {
    int ready = 0;
    for (size_t i = 0; i < entries.size(); ++i) {
      auto &entry = entries[i];
      uint16_t result = 0;
      if (entry.counter) {
        std::lock_guard<std::mutex> lock(entry.counter->mutex);
        if (entry.counter->closed) result = invalid;
        else {
          if (native[entry.first].revents & POLLIN) result |= entry.guest.events & (in | rdnorm);
          if (native[entry.second].revents & POLLIN) result |= entry.guest.events & (out | wrnorm);
        }
      } else result = guest_events(native[entry.first].revents, entry.guest.events);
      entry.guest.result = result;
      std::memcpy(static_cast<uint8_t *>(output) + i * 8 + 6, &result, 2);
      ready += result != 0;
    }
    return ready;
  }
};
int guest_poll(void *descriptors, uint32_t count, int timeout) {
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  if (!same_process()) return failure(ENOTSUP);
  try {
    PollSet set(descriptors, count);
    if (::poll(set.native.data(), nfds_t(set.native.size()), timeout) < 0) return -1;
    return set.publish();
  } catch (const Failure &error) { return failure(error.error); }
    catch (const std::bad_alloc &) { return failure(ENOMEM); }
}
int guest_mask(const void *guest, sigset_t &native) {
  uint64_t bits; std::memcpy(&bits, guest, 8);
  const int signals[] = {SIGHUP,SIGINT,SIGQUIT,SIGILL,SIGTRAP,SIGABRT,SIGBUS,SIGFPE,SIGKILL,
    SIGUSR1,SIGSEGV,SIGUSR2,SIGPIPE,SIGALRM,SIGTERM,0,SIGCHLD,SIGCONT,SIGSTOP,SIGTSTP,
    SIGTTIN,SIGTTOU,SIGURG,SIGXCPU,SIGXFSZ,SIGVTALRM,SIGPROF,SIGWINCH,SIGIO,0,SIGSYS};
  if (bits >> 31) return failure(ENOTSUP); // No fake Linux realtime signals.
  sigemptyset(&native);
  for (unsigned i = 0; i < 31; ++i) if (bits & (uint64_t(1) << i)) {
    if (!signals[i]) return failure(ENOTSUP);
    if (sigaddset(&native, signals[i])) return -1;
  }
  return 0;
}
int guest_ppoll(void *descriptors, uint32_t count, const void *timeout, const void *mask) {
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  if (!same_process()) return failure(ENOTSUP);
  try {
    PollSet set(descriptors, count);
    timespec duration{}, *wait = nullptr;
    if (timeout) {
      int64_t words[2]; std::memcpy(words, timeout, 16);
      if (words[0] < 0 || words[1] < 0 || words[1] >= 1000000000) return failure(EINVAL);
      duration.tv_sec = words[0]; duration.tv_nsec = words[1]; wait = &duration;
    }
    sigset_t native_mask;
    if (mask && guest_mask(mask, native_mask)) return -1;
    fd_set reads, writes, exceptions;
    FD_ZERO(&reads); FD_ZERO(&writes); FD_ZERO(&exceptions);
    int highest = -1;
    bool invalid_fd = false;
    for (const auto &entry : set.entries)
      if (entry.guest.fd >= 0 &&
          (!(entry.guest.events & (in | pri | out | rdnorm | wrnorm)) ||
           (entry.guest.events & (rdband | wrband)))) return failure(ENOTSUP);
    for (auto &entry : set.native) {
      if (entry.fd < 0) continue;
      if (::fcntl(entry.fd, F_GETFD) < 0) {
        if (errno == EBADF) { invalid_fd = true; continue; }
        return -1;
      }
      if (entry.fd >= FD_SETSIZE) return failure(ENOTSUP);
      if (entry.events & (POLLIN | POLLRDNORM)) FD_SET(entry.fd, &reads);
      if (entry.events & (POLLOUT | POLLWRNORM)) FD_SET(entry.fd, &writes);
      if (entry.events & POLLPRI) FD_SET(entry.fd, &exceptions);
      highest = std::max(highest, entry.fd);
    }
    // pselect applies the translated signal mask atomically during the wait;
    // never emulate ppoll with the racy sigprocmask()+poll sequence.
    timespec zero{};
    if (invalid_fd) wait = &zero;
    int result = ::pselect(highest + 1, &reads, &writes, &exceptions, wait, mask ? &native_mask : nullptr);
    if (result < 0) return -1;
    if (::poll(set.native.data(), nfds_t(set.native.size()), 0) < 0) return -1;
    return set.publish();
  } catch (const Failure &error) { return failure(error.error); }
    catch (const std::bad_alloc &) { return failure(ENOMEM); }
}
int unsupported_inotify() { return failure(ENOSYS); }
int unsupported_inotify_flags(int) { return failure(ENOSYS); }
int unsupported_inotify_add(int, const char *, uint32_t) { return failure(ENOSYS); }
int unsupported_inotify_remove(int, uint32_t) { return failure(ENOSYS); }
} // namespace
bool event_descriptor_tracked(int fd) noexcept { return hinted(fd); }
ssize_t event_descriptor_read(int fd, void *buffer, size_t count) {
  if (!hinted(fd)) return ::read(fd, buffer, count);
  if (in_guest_signal_handler()) return failure<ssize_t>(ENOTSUP);
  if (!same_process()) return failure<ssize_t>(ENOTSUP);
  auto counter = lookup(fd);
  return counter ? counter_read(counter, buffer, count) : ::read(fd, buffer, count);
}
ssize_t event_descriptor_write(int fd, const void *buffer, size_t count) {
  if (!hinted(fd)) return ::write(fd, buffer, count);
  if (in_guest_signal_handler()) return failure<ssize_t>(ENOTSUP);
  if (!same_process()) return failure<ssize_t>(ENOTSUP);
  auto counter = lookup(fd);
  return counter ? counter_write(counter, buffer, count) : ::write(fd, buffer, count);
}
int event_descriptor_close(int fd) {
  if (!hinted(fd)) return ::close(fd);
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  // Close is safe in a fork child, but inherited virtual counter operations
  // cannot use a copied process-local registry/mutex/counter as Linux sharing.
  if (!same_process()) { int result = ::close(fd); set_hint(fd, false); return result; }
  auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
  auto found = table.descriptors.find(fd);
  if (found != table.descriptors.end()) {
    auto counter = found->second; table.descriptors.erase(found);
    if (!--counter->aliases) counter->close_last();
  }
  int result = ::close(fd);
  set_hint(fd, false);
  return result;
}
int event_descriptor_dup(int fd) {
  if (!hinted(fd)) return ::dup(fd);
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  if (!same_process()) return failure(ENOTSUP);
  auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
  auto found = table.descriptors.find(fd);
  return found == table.descriptors.end() ? ::dup(fd) : duplicate_locked(table, fd, found->second, 0, false);
}
static int duplicate_to(int source, int destination, bool atomic, int native_flags) {
  auto native = [&] {
    if (!atomic) return ::dup2(source, destination);
    if (__builtin_available(macOS 27.0, *)) return ::dup3(source, destination, native_flags);
    return failure(ENOSYS);
  };
  bool source_hint = hinted(source), destination_hint = hinted(destination);
  if (!source_hint && !destination_hint) return native();
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  if (!same_process()) return failure(ENOTSUP);
  auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
  if (source == destination) return native();
  auto old = table.descriptors.find(source), target = table.descriptors.find(destination);
  std::shared_ptr<Counter> counter = old == table.descriptors.end() ? nullptr : old->second;
  if (counter && destination < 0) return failure(EBADF);
  if (counter && destination >= event_descriptor_bound) return failure(EINVAL);
  std::map<int, std::shared_ptr<Counter>> prepared;
  if (counter && target == table.descriptors.end()) {
    if (table.descriptors.size() >= descriptor_limit) return failure(EMFILE);
    try { prepared.emplace(destination, counter); }
    catch (const std::bad_alloc &) { return failure(ENOMEM); }
  }
  // Publish the conservative hint before the kernel replacement. An
  // interrupted signal handler rejects a transition rather than locking.
  bool previous_hint = hinted(destination);
  if (counter) set_hint(destination, true);
  int result = native();
  if (result < 0) {
    if (counter) set_hint(destination, previous_hint);
    return result;
  }
  if (target != table.descriptors.end()) {
    auto previous = target->second;
    if (!--previous->aliases && previous != counter) previous->close_last();
    if (counter) target->second = counter;
    else table.descriptors.erase(target);
  } else if (counter) table.descriptors.insert(prepared.extract(destination));
  if (counter) ++counter->aliases;
  if (destination >= 0 && destination < event_descriptor_bound) set_hint(destination, bool(counter));
  return result;
}
int event_descriptor_dup2(int source, int destination) {
  return duplicate_to(source, destination, false, 0);
}
int event_descriptor_dup3(int source, int destination, int guest_flags) {
  if (guest_flags & ~int(b::kO_CLOEXEC)) return failure(EINVAL);
  if (source == destination) return failure(EINVAL);
  // Avoid invoking OS availability machinery from an async guest handler.
  if (in_guest_signal_handler()) return failure(ENOTSUP);
  return duplicate_to(source, destination, true, guest_flags & b::kO_CLOEXEC ? O_CLOEXEC : 0);
}
bool events_fcntl(Capture &capture, int64_t &result) {
  CallCursor cursor(capture, Convention::Aapcs64);
  int fd = cursor.read<int>(), command = cursor.read<int>();
  if (!hinted(fd)) return false;
  if (in_guest_signal_handler() || !same_process()) { result = failure(ENOTSUP); return true; }
  auto counter = lookup(fd);
  if (!counter) return false;
  if (command == b::kF_DUPFD || command == b::kF_DUPFD_CLOEXEC) {
    int minimum = cursor.read<int>();
    auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
    auto current = table.descriptors.find(fd);
    result = current == table.descriptors.end() || current->second != counter
                 ? failure(EBADF)
                 : duplicate_locked(table, fd, counter, minimum, command == b::kF_DUPFD_CLOEXEC);
  } else if (command == b::kF_GETFD) {
    int flags = ::fcntl(fd, F_GETFD);
    result = flags < 0 ? -1 : flags & FD_CLOEXEC ? b::kFD_CLOEXEC : 0;
  } else if (command == b::kF_SETFD) {
    int flags = cursor.read<int>();
    result = flags & ~int(b::kFD_CLOEXEC) ? failure(EINVAL) : ::fcntl(fd, F_SETFD, flags & b::kFD_CLOEXEC ? FD_CLOEXEC : 0);
  } else if (command == b::kF_GETFL) {
    std::lock_guard<std::mutex> lock(counter->mutex); result = counter->status;
  } else if (command == b::kF_SETFL) {
    int flags = cursor.read<int>();
    if (flags & ~int(b::kO_ACCMODE | b::kO_NONBLOCK | b::kO_APPEND | b::kO_LARGEFILE)) result = failure(ENOTSUP);
    else {
      std::lock_guard<std::mutex> lock(counter->mutex);
      counter->status = int(b::kO_RDWR) | (flags & int(b::kO_NONBLOCK | b::kO_APPEND)); result = 0;
    }
  } else result = failure(ENOTSUP);
  return true;
}
bool events_ioctl(Capture &capture, int &result) {
  CallCursor cursor(capture, Convention::Aapcs64);
  int fd = cursor.read<int>();
  if (!hinted(fd)) return false;
  if (in_guest_signal_handler() || !same_process()) { result = failure(ENOTSUP); return true; }
  auto counter = lookup(fd);
  if (!counter) return false;
  uint32_t request = cursor.read<uint32_t>();
  if (request == b::kFIOCLEX || request == b::kFIONCLEX) {
    int flags = ::fcntl(fd, F_GETFD);
    result = flags < 0 ? -1 : ::fcntl(fd, F_SETFD,
        request == b::kFIOCLEX ? flags | FD_CLOEXEC : flags & ~FD_CLOEXEC);
  } else if (request == b::kFIONBIO) {
    const void *argument = cursor.read<const void *>();
    if (!argument) result = failure(EFAULT);
    else {
      int32_t value; std::memcpy(&value, argument, 4);
      std::lock_guard<std::mutex> lock(counter->mutex);
      counter->status = value ? counter->status | int(b::kO_NONBLOCK)
                              : counter->status & ~int(b::kO_NONBLOCK);
      result = 0;
    }
  } else result = failure(ENOTTY);
  return true;
}
void event_descriptors_shutdown() {
  if (!same_process()) return;
  auto &table = registry(); std::lock_guard<std::mutex> lock(table.mutex);
  for (auto &entry : table.descriptors) {
    ::close(entry.first);
    if (!--entry.second->aliases) entry.second->close_last();
    set_hint(entry.first, false);
  }
  table.descriptors.clear();
}
std::vector<ProviderExport> event_exports() {
  (void)registry(); // Initialize VM ownership before any guest callback runs.
  return {fixed_export("dup3", "LIBC", &event_descriptor_dup3, ErrnoPolicy::MinusOne),
    fixed_export("eventfd", "LIBC", &create_counter, ErrnoPolicy::MinusOne),
    fixed_export("eventfd_read", "LIBC", &convenience_read),
    fixed_export("eventfd_write", "LIBC", &convenience_write),
    fixed_export("poll", "LIBC", &guest_poll, ErrnoPolicy::MinusOne),
    fixed_export("ppoll", "LIBC", &guest_ppoll, ErrnoPolicy::MinusOne),
    fixed_export("inotify_init", "LIBC", &unsupported_inotify, ErrnoPolicy::MinusOne),
    fixed_export("inotify_init1", "LIBC", &unsupported_inotify_flags, ErrnoPolicy::MinusOne),
    fixed_export("inotify_add_watch", "LIBC", &unsupported_inotify_add, ErrnoPolicy::MinusOne),
    fixed_export("inotify_rm_watch", "LIBC", &unsupported_inotify_remove, ErrnoPolicy::MinusOne)};
}
} // namespace shiraz::guest
