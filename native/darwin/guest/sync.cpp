// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/sync.hpp"
#include "guest_io.hpp"
#include <condition_variable>
#include <atomic>
#include <map>
#include <mutex>
#include <pthread.h>
#include <thread>
#include <time.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr size_t limit = 8192;
struct Failure { int code; };
void check(int error) { if (error) throw Failure{error}; }
int host_type(int type) {
  if (type == b::kPTHREAD_MUTEX_NORMAL) return PTHREAD_MUTEX_NORMAL;
  if (type == b::kPTHREAD_MUTEX_RECURSIVE) return PTHREAD_MUTEX_RECURSIVE;
  if (type == b::kPTHREAD_MUTEX_ERRORCHECK) return PTHREAD_MUTEX_ERRORCHECK;
  throw Failure{EINVAL};
}
clockid_t host_clock(int clock,bool readable_boottime=false) {
  if (clock == b::kCLOCK_REALTIME) return CLOCK_REALTIME;
  // Darwin CLOCK_MONOTONIC is calendar time minus boot time (includes sleep).
  // Linux MONOTONIC deadlines use the awake uptime domain instead.
  if (clock == b::kCLOCK_MONOTONIC) return CLOCK_UPTIME_RAW;
  // Linux BOOTTIME includes suspended time; Darwin MONOTONIC has that epoch.
  // It is readable but is not a valid pthread condition-variable clock.
  if (readable_boottime && clock == b::kCLOCK_BOOTTIME) return CLOCK_MONOTONIC;
  throw Failure{EINVAL};
}
int initial(const void *address, size_t size) {
  if (!address || reinterpret_cast<uintptr_t>(address) % 4) throw Failure{EINVAL};
  uint32_t value;
  std::memcpy(&value, address, 4);
  auto *bytes = static_cast<const uint8_t *>(address);
  for (size_t i = 4; i < size; ++i) if (bytes[i]) throw Failure{EINVAL};
  return int(value);
}
int mutex_initial(int type) {
  return type == b::kPTHREAD_MUTEX_NORMAL ? b::kMutexNormalInitializer
       : type == b::kPTHREAD_MUTEX_RECURSIVE ? b::kMutexRecursiveInitializer
                                           : b::kMutexErrorcheckInitializer;
}
struct Mutex {
  pthread_mutex_t host{};
  bool live = false;
  explicit Mutex(int type) {
    pthread_mutexattr_t attr;
    check(pthread_mutexattr_init(&attr));
    int error = pthread_mutexattr_settype(&attr, host_type(type));
    if (!error) error = pthread_mutex_init(&host, &attr);
    pthread_mutexattr_destroy(&attr);
    check(error);
    live = true;
  }
  ~Mutex() { if (live) pthread_mutex_destroy(&host); }
};
struct Condition {
  pthread_cond_t host{};
  int clock;
  bool live = false;
  explicit Condition(int value) : clock(value) {
    check(pthread_cond_init(&host, nullptr));
    live = true;
  }
  ~Condition() { if (live) pthread_cond_destroy(&host); }
};
struct Once {
  std::mutex mutex;
  std::condition_variable condition;
  bool running = false, done = false;
  std::thread::id owner;
};
struct Rwlock {
  pthread_rwlock_t host{};
  std::atomic<uint32_t> holders{0};
  bool live = false;
  Rwlock() { check(pthread_rwlock_init(&host, nullptr)); live = true; }
  ~Rwlock() { if (live) pthread_rwlock_destroy(&host); }
};
class Synchronization {
  std::mutex registry_;
  std::map<void *, std::shared_ptr<Mutex>> mutexes_;
  std::map<void *, std::shared_ptr<Condition>> conditions_;
  std::map<void *, int> mutex_attrs_, cond_attrs_;
  std::map<void *, std::shared_ptr<Once>> once_;
  std::map<void *, std::shared_ptr<Rwlock>> rwlocks_;
  CodeValidator validate_;
  std::shared_ptr<Mutex> mutex_locked(void *address) {
    auto it = mutexes_.find(address);
    if (it != mutexes_.end()) return it->second;
    int value = initial(address, b::kPthreadMutexSize), type;
    if (value == b::kMutexNormalInitializer) type = b::kPTHREAD_MUTEX_NORMAL;
    else if (value == b::kMutexRecursiveInitializer) type = b::kPTHREAD_MUTEX_RECURSIVE;
    else if (value == b::kMutexErrorcheckInitializer) type = b::kPTHREAD_MUTEX_ERRORCHECK;
    else throw Failure{EINVAL};
    if (mutexes_.size() >= limit) throw Failure{EAGAIN};
    auto result = std::make_shared<Mutex>(type);
    mutexes_.emplace(address, result);
    return result;
  }
  std::shared_ptr<Mutex> mutex(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    return mutex_locked(address);
  }
  std::shared_ptr<Condition> condition_locked(void *address) {
    auto it = conditions_.find(address);
    if (it != conditions_.end()) return it->second;
    int value = initial(address, b::kPthreadCondSize), clock;
    if (value == b::kCondInitializer) clock = b::kCLOCK_REALTIME;
    else if (value == b::kCondMonotonicInitializer) clock = b::kCLOCK_MONOTONIC;
    else throw Failure{EINVAL};
    if (conditions_.size() >= limit) throw Failure{EAGAIN};
    auto result = std::make_shared<Condition>(clock);
    conditions_.emplace(address, result);
    return result;
  }
  std::shared_ptr<Condition> condition(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    return condition_locked(address);
  }
  std::shared_ptr<Rwlock> rwlock_locked(void *address) {
    auto it = rwlocks_.find(address);
    if (it != rwlocks_.end()) return it->second;
    if (initial(address, b::kPthreadRwlockSize)) throw Failure{EINVAL};
    if (rwlocks_.size() >= limit) throw Failure{EAGAIN};
    auto result = std::make_shared<Rwlock>();
    rwlocks_.emplace(address, result);
    return result;
  }
  std::shared_ptr<Rwlock> rwlock(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    return rwlock_locked(address);
  }
public:
  explicit Synchronization(CodeValidator validate) : validate_(std::move(validate)) {}
  int rw_init(void *address, void *attr) {
    if (!address) return EINVAL;
    if (attr) return EOPNOTSUPP;
    std::lock_guard<std::mutex> lock(registry_);
    if (rwlocks_.count(address)) return EBUSY;
    if (rwlocks_.size() >= limit) return EAGAIN;
    rwlocks_.emplace(address, std::make_shared<Rwlock>());
    std::memset(address, 0, b::kPthreadRwlockSize);
    return 0;
  }
  int rw_destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    rwlock_locked(address);
    auto it = rwlocks_.find(address);
    if (it->second.use_count() != 1 || it->second->holders.load()) return EBUSY;
    int error = pthread_rwlock_destroy(&it->second->host);
    if (!error) {
      it->second->live = false;
      rwlocks_.erase(it);
      std::memset(address, 0xff, b::kPthreadRwlockSize);
    }
    return error;
  }
  int rw_lock(void *address, bool write, bool attempt) {
    auto value = rwlock(address);
    int error = write ? (attempt ? pthread_rwlock_trywrlock(&value->host) : pthread_rwlock_wrlock(&value->host))
                      : (attempt ? pthread_rwlock_tryrdlock(&value->host) : pthread_rwlock_rdlock(&value->host));
    if (!error) ++value->holders;
    return error;
  }
  int rdlock(void *address) { return rw_lock(address, false, false); }
  int wrlock(void *address) { return rw_lock(address, true, false); }
  int tryrdlock(void *address) { return rw_lock(address, false, true); }
  int trywrlock(void *address) { return rw_lock(address, true, true); }
  int rw_unlock(void *address) {
    auto value = rwlock(address);
    if (!value->holders.load()) return EPERM;
    int error = pthread_rwlock_unlock(&value->host);
    if (!error) --value->holders;
    return error;
  }
  int attr_init(void *address, bool cond) {
    std::lock_guard<std::mutex> lock(registry_);
    auto &attrs = cond ? cond_attrs_ : mutex_attrs_;
    if (!address || attrs.count(address)) return EINVAL;
    if (attrs.size() >= limit) return EAGAIN;
    attrs.emplace(address, cond ? b::kCLOCK_REALTIME : b::kPTHREAD_MUTEX_NORMAL);
    std::memset(address, 0, cond ? b::kPthreadCondAttrSize : b::kPthreadMutexAttrSize);
    return 0;
  }
  int mutexattr_init(void *address) { return attr_init(address, false); }
  int condattr_init(void *address) { return attr_init(address, true); }
  int mutexattr_destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    return mutex_attrs_.erase(address) ? 0 : EINVAL;
  }
  int condattr_destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    return cond_attrs_.erase(address) ? 0 : EINVAL;
  }
  int mutexattr_settype(void *address, int type) {
    host_type(type);
    std::lock_guard<std::mutex> lock(registry_);
    auto it = mutex_attrs_.find(address);
    if (it == mutex_attrs_.end()) return EINVAL;
    it->second = type;
    return 0;
  }
  int mutexattr_gettype(void *address, int *type) {
    std::lock_guard<std::mutex> lock(registry_);
    auto it = mutex_attrs_.find(address);
    if (it == mutex_attrs_.end() || !type) return EINVAL;
    *type = it->second;
    return 0;
  }
  int condattr_setclock(void *address, int clock) {
    host_clock(clock);
    std::lock_guard<std::mutex> lock(registry_);
    auto it = cond_attrs_.find(address);
    if (it == cond_attrs_.end()) return EINVAL;
    it->second = clock;
    return 0;
  }
  int condattr_getclock(void *address, int *clock) {
    std::lock_guard<std::mutex> lock(registry_);
    auto it = cond_attrs_.find(address);
    if (it == cond_attrs_.end() || !clock) return EINVAL;
    *clock = it->second;
    return 0;
  }
  int shared(void *address, int shared, bool cond) {
    std::lock_guard<std::mutex> lock(registry_);
    if (!(cond ? cond_attrs_ : mutex_attrs_).count(address)) return EINVAL;
    return shared == b::kPTHREAD_PROCESS_PRIVATE ? 0
         : shared == b::kPTHREAD_PROCESS_SHARED ? EOPNOTSUPP : EINVAL;
  }
  int mutexattr_setpshared(void *a, int shared) { return this->shared(a, shared, false); }
  int condattr_setpshared(void *a, int shared) { return this->shared(a, shared, true); }
  int mutex_init(void *address, void *attribute) {
    if (!address) return EINVAL;
    std::lock_guard<std::mutex> lock(registry_);
    if (mutexes_.count(address)) return EBUSY;
    int type = b::kPTHREAD_MUTEX_NORMAL;
    if (attribute) {
      auto it = mutex_attrs_.find(attribute);
      if (it == mutex_attrs_.end()) return EINVAL;
      type = it->second;
    }
    if (mutexes_.size() >= limit) return EAGAIN;
    mutexes_.emplace(address, std::make_shared<Mutex>(type));
    std::memset(address, 0, b::kPthreadMutexSize);
    int value = mutex_initial(type);
    std::memcpy(address, &value, 4);
    return 0;
  }
  int mutex_destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    mutex_locked(address);
    auto it = mutexes_.find(address);
    if (it->second.use_count() != 1) return EBUSY;
    int error = pthread_mutex_destroy(&it->second->host);
    if (!error) {
      it->second->live = false;
      mutexes_.erase(it);
      std::memset(address, 0xff, b::kPthreadMutexSize);
    }
    return error;
  }
  int lock(void *address) { auto value = mutex(address); return pthread_mutex_lock(&value->host); }
  int trylock(void *address) { auto value = mutex(address); return pthread_mutex_trylock(&value->host); }
  int unlock(void *address) { auto value = mutex(address); return pthread_mutex_unlock(&value->host); }
  int cond_init(void *address, void *attribute) {
    if (!address) return EINVAL;
    std::lock_guard<std::mutex> lock(registry_);
    if (conditions_.count(address)) return EBUSY;
    int clock = b::kCLOCK_REALTIME;
    if (attribute) {
      auto it = cond_attrs_.find(attribute);
      if (it == cond_attrs_.end()) return EINVAL;
      clock = it->second;
    }
    if (conditions_.size() >= limit) return EAGAIN;
    conditions_.emplace(address, std::make_shared<Condition>(clock));
    std::memset(address, 0, b::kPthreadCondSize);
    int value = clock == b::kCLOCK_MONOTONIC ? b::kCondMonotonicInitializer : b::kCondInitializer;
    std::memcpy(address, &value, 4);
    return 0;
  }
  int cond_destroy(void *address) {
    std::lock_guard<std::mutex> lock(registry_);
    condition_locked(address);
    auto it = conditions_.find(address);
    if (it->second.use_count() != 1) return EBUSY;
    int error = pthread_cond_destroy(&it->second->host);
    if (!error) {
      it->second->live = false;
      conditions_.erase(it);
      std::memset(address, 0xff, b::kPthreadCondSize);
    }
    return error;
  }
  int signal(void *address) { auto value = condition(address); return pthread_cond_signal(&value->host); }
  int broadcast(void *address) { auto value = condition(address); return pthread_cond_broadcast(&value->host); }
  int wait(void *cond, void *mut) {
    auto c = condition(cond); auto m = mutex(mut);
    return pthread_cond_wait(&c->host, &m->host);
  }
  int timedwait(void *cond, void *mut, const void *guest_time) {
    if (!guest_time) return EINVAL;
    static_assert(b::kTimespecSize == 16 && b::kTimespec_tv_sec == 0 && b::kTimespec_tv_nsec == 8);
    int64_t fields[2];
    std::memcpy(fields, guest_time, sizeof(fields));
    if (fields[1] < 0 || fields[1] >= 1000000000) return EINVAL;
    auto c = condition(cond); auto m = mutex(mut);
    timespec deadline{fields[0], fields[1]}, now;
    if (clock_gettime(host_clock(c->clock), &now)) return errno;
    bool past = deadline.tv_sec < now.tv_sec ||
                (deadline.tv_sec == now.tv_sec && deadline.tv_nsec <= now.tv_nsec);
    if (c->clock == b::kCLOCK_REALTIME && !past)
      return pthread_cond_timedwait(&c->host, &m->host, &deadline);
    timespec relative{};
    if (!past) {
      relative.tv_sec = deadline.tv_sec - now.tv_sec;
      relative.tv_nsec = deadline.tv_nsec - now.tv_nsec;
      if (relative.tv_nsec < 0) { --relative.tv_sec; relative.tv_nsec += 1000000000; }
    }
    return pthread_cond_timedwait_relative_np(&c->host, &m->host, &relative);
  }
  int once(int32_t *control, void *function) {
    if (!control || reinterpret_cast<uintptr_t>(control) % alignof(int32_t)) return EINVAL;
    static_assert(b::kPthreadOnceSize == 4 && b::kOnceInitializer == 0);
    if (__atomic_load_n(control, __ATOMIC_ACQUIRE) == 2) return 0;
    if (!function || !validate_(function)) return EINVAL;
    std::shared_ptr<Once> value;
    {
      std::lock_guard<std::mutex> lock(registry_);
      int state = __atomic_load_n(control, __ATOMIC_ACQUIRE);
      if (state == 2) return 0;
      if (state != b::kOnceInitializer) return EINVAL;
      auto it = once_.find(control);
      if (it == once_.end()) {
        if (once_.size() >= limit) return EAGAIN;
        it = once_.emplace(control, std::make_shared<Once>()).first;
      }
      value = it->second;
    }
    std::unique_lock<std::mutex> lock(value->mutex);
    if (value->running && value->owner == std::this_thread::get_id()) return EDEADLK;
    value->condition.wait(lock, [&] { return !value->running; });
    if (value->done) return 0;
    value->running = true;
    value->owner = std::this_thread::get_id();
    lock.unlock();
    try { invoke(function, {}); }
    catch (...) {
      lock.lock(); value->running = false; lock.unlock();
      value->condition.notify_all();
      throw;
    }
    lock.lock();
    value->done = true;
    value->running = false;
    __atomic_store_n(control, 2, __ATOMIC_RELEASE);
    lock.unlock();
    value->condition.notify_all();
    std::lock_guard<std::mutex> registry_lock(registry_);
    once_.erase(control);
    return 0;
  }
};
int read_clock(int clock, void *output) {
  if (!output) { errno = EFAULT; return -1; }
  clockid_t host;
  try { host = host_clock(clock,true); }
  catch (const Failure &error) { errno = error.code; return -1; }
  timespec value;
  if (clock_gettime(host, &value)) return -1;
  int64_t fields[2]{value.tv_sec, value.tv_nsec};
  std::memcpy(output, fields, sizeof(fields));
  return 0;
}
int clock_resolution(int clock,void *output){
  clockid_t native;try{native=host_clock(clock,true);}catch(const Failure &error){errno=error.code;return -1;}
  timespec result{};if(::clock_getres(native,output?&result:nullptr))return -1;
  if(output){int64_t fields[2]={result.tv_sec,result.tv_nsec};std::memcpy(output,fields,16);}return 0;
}
int clock_sleep(int clock,int flags,const int64_t *input,int64_t *remaining){
  clockid_t native;try{native=host_clock(clock);}catch(const Failure &error){return errno_to_guest(error.code);}
  if(!input)return errno_to_guest(EFAULT);
  if(flags & ~1)return errno_to_guest(EINVAL);
  int64_t fields[2];std::memcpy(fields,input,16);
  if(fields[0]<0||fields[1]<0||fields[1]>=1000000000)return errno_to_guest(EINVAL);
  timespec request{fields[0],fields[1]},rest{};
  if(flags & 1){timespec now{};if(::clock_gettime(native,&now))return errno_to_guest(errno);
    request.tv_sec-=now.tv_sec;request.tv_nsec-=now.tv_nsec;if(request.tv_nsec<0){--request.tv_sec;request.tv_nsec+=1000000000;}
    if(request.tv_sec<0)return 0;
  }
  if(!::nanosleep(&request,&rest))return 0;
  int error=errno;if(error==EINTR&&!(flags&1)&&remaining){int64_t values[2]={rest.tv_sec,rest.tv_nsec};std::memcpy(remaining,values,16);}
  return errno_to_guest(error);
}
template <typename... Args>
ProviderExport method(const char *name, std::shared_ptr<Synchronization> state,
                      int (Synchronization::*function)(Args...)) {
  void *address = closure([state, function](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    int result;
    try { result = std::apply([&](Args... values) { return ((*state).*function)(values...); }, args); }
    catch (const Failure &failure) { result = failure.code; }
    catch (const std::bad_alloc &) { result = EAGAIN; }
    return_value(capture, result ? errno_to_guest(result) : 0);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(address), SymbolKind::Function,
          "pthread/synchronization", false};
}
} // namespace
std::vector<ProviderExport> sync_exports(CodeValidator validate) {
  if (!validate) throw ElfError("synchronization requires guest callback validation");
  auto s = std::make_shared<Synchronization>(std::move(validate));
  return {
    method("pthread_rwlock_init", s, &Synchronization::rw_init),
    method("pthread_rwlock_destroy", s, &Synchronization::rw_destroy),
    method("pthread_rwlock_rdlock", s, &Synchronization::rdlock),
    method("pthread_rwlock_wrlock", s, &Synchronization::wrlock),
    method("pthread_rwlock_tryrdlock", s, &Synchronization::tryrdlock),
    method("pthread_rwlock_trywrlock", s, &Synchronization::trywrlock),
    method("pthread_rwlock_unlock", s, &Synchronization::rw_unlock),
    method("pthread_mutex_init", s, &Synchronization::mutex_init),
    method("pthread_mutex_destroy", s, &Synchronization::mutex_destroy),
    method("pthread_mutex_lock", s, &Synchronization::lock),
    method("pthread_mutex_trylock", s, &Synchronization::trylock),
    method("pthread_mutex_unlock", s, &Synchronization::unlock),
    method("pthread_mutexattr_init", s, &Synchronization::mutexattr_init),
    method("pthread_mutexattr_destroy", s, &Synchronization::mutexattr_destroy),
    method("pthread_mutexattr_settype", s, &Synchronization::mutexattr_settype),
    method("pthread_mutexattr_gettype", s, &Synchronization::mutexattr_gettype),
    method("pthread_mutexattr_setpshared", s, &Synchronization::mutexattr_setpshared),
    method("pthread_cond_init", s, &Synchronization::cond_init),
    method("pthread_cond_destroy", s, &Synchronization::cond_destroy),
    method("pthread_cond_signal", s, &Synchronization::signal),
    method("pthread_cond_broadcast", s, &Synchronization::broadcast),
    method("pthread_cond_wait", s, &Synchronization::wait),
    method("pthread_cond_timedwait", s, &Synchronization::timedwait),
    method("pthread_condattr_init", s, &Synchronization::condattr_init),
    method("pthread_condattr_destroy", s, &Synchronization::condattr_destroy),
    method("pthread_condattr_setclock", s, &Synchronization::condattr_setclock),
    method("pthread_condattr_getclock", s, &Synchronization::condattr_getclock),
    method("pthread_condattr_setpshared", s, &Synchronization::condattr_setpshared),
    method("pthread_once", s, &Synchronization::once),
    fixed_export("clock_gettime", "LIBC", &read_clock, ErrnoPolicy::MinusOne),
    fixed_export("clock_getres","LIBC",&clock_resolution,ErrnoPolicy::MinusOne),
    fixed_export("clock_nanosleep","LIBC",&clock_sleep),
  };
}
} // namespace shiraz::guest
