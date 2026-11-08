// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/thread.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "guest_io.hpp"
#include <climits>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <mutex>
#include <pthread.h>
#include <unistd.h>
#include <sched.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
struct Attributes {
  size_t stack = 1024 * 1024;
  size_t guard = size_t(getpagesize());
  bool detached = false;
  void *base = nullptr;
  bool guard_known = true;
};
struct Record {
  pthread_t host = nullptr;
  bool detached = false, finished = false, joining = false;
};
Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
void *call_guest(void *function, void *argument) {
  try {
    prepare_guest_tls();
    return reinterpret_cast<void *>(invoke(function, {pointer(argument)}).result_integers[0]);
  } catch (const std::exception &error) {
    std::fprintf(stderr, "SHIRAZ_GUEST_THREAD_FAILURE %s\n", error.what());
    std::abort();
  }
}
class Threads : public std::enable_shared_from_this<Threads> {
  struct Start {
    std::shared_ptr<Threads> state;
    std::shared_ptr<Record> record;
    void *function, *argument;
  };
  std::mutex mutex_;
  CodeValidator validate_;
  std::map<void *, Attributes> attributes_;
  std::map<uintptr_t, std::shared_ptr<Record>> records_;
  std::map<uint32_t, GuestThreadKeyHandle> keys_;
  uint32_t next_key_ = 1;
  static void *entry(void *opaque) {
    std::unique_ptr<Start> start(static_cast<Start *>(opaque));
    void *result = call_guest(start->function, start->argument);
    cleanup_require_empty();
    {
      std::lock_guard<std::mutex> lock(start->state->mutex_);
      start->record->finished = true;
      if (start->record->detached)
        start->state->records_.erase(reinterpret_cast<uintptr_t>(start->record->host));
    }
    return result;
  }
  Attributes *attr(void *address) {
    auto found = attributes_.find(address);
    return found == attributes_.end() ? nullptr : &found->second;
  }
public:
  explicit Threads(CodeValidator validate) : validate_(std::move(validate)) {}
  int attr_init(void *address) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!address || attributes_.count(address)) return EINVAL;
    if (attributes_.size() >= 4096) return EAGAIN;
    attributes_.emplace(address, Attributes{});
    std::memset(address, 0, b::kPthreadAttrSize);
    return 0;
  }
  int attr_destroy(void *address) {
    std::lock_guard<std::mutex> lock(mutex_);
    return attributes_.erase(address) ? 0 : EINVAL;
  }
  int attr_setdetach(void *address, int state) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value || (state != b::kPTHREAD_CREATE_JOINABLE && state != b::kPTHREAD_CREATE_DETACHED))
      return EINVAL;
    value->detached = state == b::kPTHREAD_CREATE_DETACHED;
    return 0;
  }
  int attr_getdetach(void *address, int *result) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value || !result) return EINVAL;
    *result = value->detached ? b::kPTHREAD_CREATE_DETACHED : b::kPTHREAD_CREATE_JOINABLE;
    return 0;
  }
  int attr_setstack(void *address, size_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value || size < size_t(b::kPTHREAD_STACK_MIN) || size % size_t(getpagesize()))
      return EINVAL;
    if (size > (size_t(64) << 20)) return EAGAIN;
    value->stack = size;
    return 0;
  }
  int attr_getstack(void *address, size_t *size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value || !size) return EINVAL;
    *size = value->stack;
    return 0;
  }
  int get_attributes(pthread_t thread,void *address) {
    if(!address)return EINVAL;
    std::lock_guard<std::mutex> lock(mutex_);
    if(thread!=pthread_self() && !records_.count(uintptr_t(thread)))return ESRCH;
    if(attributes_.count(address))return EBUSY;
    if(attributes_.size()>=4096)return EAGAIN;
    void *top=pthread_get_stackaddr_np(thread);size_t size=pthread_get_stacksize_np(thread);
    if(!top||!size)return ESRCH;
    Attributes value;value.stack=size;value.base=static_cast<char *>(top)-size;value.guard_known=false;
    attributes_.emplace(address,value);std::memset(address,0,b::kPthreadAttrSize);return 0;
  }
  int attr_getstack_address(void *address,void **base,size_t *size) {
    std::lock_guard<std::mutex> lock(mutex_);auto *value=attr(address);
    if(!value||!base||!size)return EINVAL;*base=value->base;*size=value->stack;return 0;
  }
  int set_name(pthread_t thread,const char *name) {
    if(!name)return EINVAL;
    if(strnlen(name,16)==16)return ERANGE;
    if(thread!=pthread_self())return ENOTSUP;
    return pthread_setname_np(name);
  }
  int set_schedule(pthread_t thread,int policy,const void *parameters) {
    if(!parameters)return EINVAL;
    std::lock_guard<std::mutex> lock(mutex_);
    if(thread!=pthread_self()&&!records_.count(uintptr_t(thread)))return ESRCH;
    int native=policy==0?SCHED_OTHER:policy==1?SCHED_FIFO:policy==2?SCHED_RR:-1;
    if(native<0)return EINVAL;int32_t priority;std::memcpy(&priority,parameters,4);
    sched_param value{};value.sched_priority=priority;return pthread_setschedparam(thread,native,&value);
  }
  int thread_signal(int process,int tid,int signal){
    if(process!=::getpid()||tid<=0)return ESRCH;
    uint64_t own=0;if(!pthread_threadid_np(nullptr,&own)&&own==uint64_t(tid))return guest_thread_signal(pthread_self(),signal);
    std::lock_guard<std::mutex> lock(mutex_);
    for(const auto &entry:records_){uint64_t candidate=0;if(!entry.second->finished&&!pthread_threadid_np(entry.second->host,&candidate)&&candidate==uint64_t(tid))return guest_thread_signal(entry.second->host,signal);}
    return ESRCH;
  }
  int attr_setguard(void *address, size_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value) return EINVAL;
    if (size > (size_t(64) << 20)) return EAGAIN;
    value->guard = size;
    return 0;
  }
  int attr_getguard(void *address, size_t *size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto *value = attr(address);
    if (!value || !size) return EINVAL;
    if (!value->guard_known) return ENOTSUP;
    *size = value->guard;
    return 0;
  }
  int create(uint64_t *output, void *address, void *function, void *argument) {
    if (!output || !function || !validate_(function)) return EINVAL;
    std::lock_guard<std::mutex> lock(mutex_);
    Attributes config;
    if (address) {
      auto *found = attr(address);
      if (!found) return EINVAL;
      config = *found;
    }
    if (records_.size() >= 128) return EAGAIN;
    pthread_attr_t host;
    int error = ::pthread_attr_init(&host);
    if (error) return error;
    struct Cleanup { pthread_attr_t *value; ~Cleanup() { ::pthread_attr_destroy(value); } } cleanup{&host};
    if ((error = ::pthread_attr_setstacksize(&host, config.stack)) ||
        (error = ::pthread_attr_setguardsize(&host, config.guard)) ||
        (error = ::pthread_attr_setdetachstate(&host, config.detached ? PTHREAD_CREATE_DETACHED : PTHREAD_CREATE_JOINABLE)))
      return error;
    auto record = std::make_shared<Record>();
    record->detached = config.detached;
    auto start = std::make_unique<Start>(Start{shared_from_this(), record, function, argument});
    // Reserve the registry node before creating a live thread.
    records_.emplace(0, record);
    error = ::pthread_create(&record->host, &host, entry, start.get());
    if (error) {
      records_.erase(0);
      return error;
    }
    start.release();
    auto node = records_.extract(0);
    uintptr_t id = reinterpret_cast<uintptr_t>(record->host);
    node.key() = id;
    records_.insert(std::move(node));
    std::memcpy(output, &id, sizeof(id));
    return 0;
  }
  int join(uint64_t id, void **result) {
    if (id == reinterpret_cast<uintptr_t>(::pthread_self())) return EDEADLK;
    std::shared_ptr<Record> record;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      auto found = records_.find(id);
      if (found == records_.end()) return ESRCH;
      record = found->second;
      if (::pthread_equal(record->host, ::pthread_self())) return EDEADLK;
      if (record->detached || record->joining) return EINVAL;
      record->joining = true;
    }
    int error = ::pthread_join(record->host, result);
    std::lock_guard<std::mutex> lock(mutex_);
    if (!error) records_.erase(id);
    else record->joining = false;
    return error;
  }
  int detach(uint64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = records_.find(id);
    if (found == records_.end()) return ESRCH;
    auto record = found->second;
    if (record->detached || record->joining) return EINVAL;
    int error = ::pthread_detach(record->host);
    if (!error) {
      record->detached = true;
      if (record->finished) records_.erase(found);
    }
    return error;
  }
  int key_create(uint32_t *output, void *destructor) {
    if (!output || (destructor && !validate_(destructor))) return EINVAL;
    std::lock_guard<std::mutex> lock(mutex_);
    if (keys_.size() >= 4096 || next_key_ > INT32_MAX) return EAGAIN;
    uint32_t guest = next_key_++;
    keys_.emplace(guest, make_guest_thread_key(destructor));
    std::memcpy(output, &guest, sizeof(guest));
    return 0;
  }
  int key_delete(uint32_t key) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = keys_.find(key);
    if (it == keys_.end()) return EINVAL;
    delete_guest_thread_key(it->second);
    keys_.erase(it);
    return 0;
  }
  int set(uint32_t key, const void *value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = keys_.find(key);
    return it == keys_.end() ? EINVAL : set_guest_thread_value(it->second, value);
  }
  void *get(uint32_t key) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = keys_.find(key);
    return it == keys_.end() ? nullptr : guest_thread_value(it->second);
  }
};
template <typename R, typename... Args>
ProviderExport method(const char *name, std::shared_ptr<Threads> state,
                      R (Threads::*function)(Args...)) {
  void *address = closure([state, function](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    R result{};
    try {
      result = std::apply([&](Args... values) { return ((*state).*function)(values...); }, args);
    } catch (const std::bad_alloc &) {
      if constexpr (std::is_integral_v<R>) result = EAGAIN;
    }
    if constexpr (std::is_integral_v<R>)
      if (result) result = errno_to_guest(result);
    return_value(capture, result);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(address), SymbolKind::Function,
          "pthread/translated", false};
}
} // namespace
std::vector<ProviderExport> thread_exports(CodeValidator validate) {
  if (!validate) throw ElfError("pthread adapters require guest code validation");
  auto state = std::make_shared<Threads>(std::move(validate));
  std::vector<ProviderExport> result{
    method("pthread_attr_init", state, &Threads::attr_init),
    method("pthread_attr_destroy", state, &Threads::attr_destroy),
    method("pthread_attr_setdetachstate", state, &Threads::attr_setdetach),
    method("pthread_attr_getdetachstate", state, &Threads::attr_getdetach),
    method("pthread_attr_setstacksize", state, &Threads::attr_setstack),
    method("pthread_attr_getstacksize", state, &Threads::attr_getstack),
    method("pthread_attr_getstack",state,&Threads::attr_getstack_address),
    method("pthread_getattr_np",state,&Threads::get_attributes),
    method("pthread_setname_np",state,&Threads::set_name),
    method("pthread_setschedparam",state,&Threads::set_schedule),
    method("pthread_attr_setguardsize", state, &Threads::attr_setguard),
    method("pthread_attr_getguardsize", state, &Threads::attr_getguard),
    method("pthread_create", state, &Threads::create),
    method("pthread_join", state, &Threads::join),
    method("pthread_detach", state, &Threads::detach),
    method("pthread_key_create", state, &Threads::key_create),
    method("pthread_key_delete", state, &Threads::key_delete),
    method("pthread_setspecific", state, &Threads::set),
    method("pthread_getspecific", state, &Threads::get),
    fixed_export("pthread_self", "LIBC", &::pthread_self),
    fixed_export("pthread_equal", "LIBC", &::pthread_equal),
  };
  auto signal=closure([state](Capture &capture){CallCursor cursor(capture,Convention::Aapcs64);
    int process=cursor.read<int>(),tid=cursor.read<int>(),number=cursor.read<int>();HostErrno preserve;
    int error=state->thread_signal(process,tid,number);if(error)*guest_errno_location()=errno_to_guest(error);return_value(capture,error?-1:0);
  });
  result.push_back({"tgkill","LIBC",uintptr_t(signal),SymbolKind::Function,"pthread/owned-thread-signal",false});
  return result;
}
} // namespace shiraz::guest
