// SPDX-License-Identifier: GPL-3.0-only
// Original adapters against pinned Android14 LP64 headers. Guest layouts are
// copied fieldwise: no Darwin utsname/timeval/rlimit alias crosses the ABI.
#include "shiraz/darwin/guest/core_services.hpp"
#include "shiraz/darwin/guest/fortify.hpp"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <crt_externs.h>
#include <sys/resource.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <pthread.h>
#include <fcntl.h>
namespace shiraz::guest {
namespace {
int lower(unsigned char value) { return value >= 'A' && value <= 'Z' ? value + 32 : value; }
int case_compare(const char *a, const char *b, size_t count) {
  for (size_t i = 0; i < count; ++i) {
    int x = lower(static_cast<unsigned char>(a[i])), y = lower(static_cast<unsigned char>(b[i]));
    if (x != y || !x) return x - y;
  }
  return 0;
}
int case_compare_all(const char *a, const char *b) { return case_compare(a,b,SIZE_MAX); }
char *checked_find(const char *text, int wanted, size_t available) {
  for (size_t i = 0; i < available; ++i) {
    if (static_cast<unsigned char>(text[i]) == static_cast<unsigned char>(wanted)) return const_cast<char *>(text+i);
    if (!text[i]) return nullptr;
  }
  fortify_failure("strchr");
}
char *checked_rfind(const char *text, int wanted, size_t available) {
  const char *found = nullptr;
  for (size_t i = 0; i < available; ++i) {
    if (static_cast<unsigned char>(text[i]) == static_cast<unsigned char>(wanted)) found = text+i;
    if (!text[i]) return const_cast<char *>(found);
  }
  fortify_failure("strrchr");
}
char *checked_ncat(char *to, const char *from, size_t count, size_t available) {
  size_t used = strnlen(to,available);
  if (used == available) fortify_failure("strncat destination");
  size_t copied = strnlen(from,count);
  if (copied >= available-used) fortify_failure("strncat size");
  std::memcpy(to+used,from,copied); to[used+copied]=0; return to;
}
int host_name(void *output) {
  if (!output) { errno=EFAULT; return -1; }
  utsname native{}; if (::uname(&native)) return -1;
  std::array<char,390> value{};std::array<char,256> domain{};
  if(::getdomainname(domain.data(),int(domain.size())))return -1;
  domain.back()=0;
  const char *fields[]={native.sysname,native.nodename,native.release,native.version,native.machine,domain.data()};
  for (size_t i=0;i<6;++i) { size_t size=strnlen(fields[i],65); std::memcpy(value.data()+65*i,fields[i],std::min(size,size_t(64))); }
  std::memcpy(output,value.data(),value.size()); return 0;
}
int wall_time(void *output,void *zone) {
  timeval native{}; struct timezone native_zone{};
  if (::gettimeofday(&native,zone ? &native_zone : nullptr)) return -1;
  if(output) { int64_t fields[]={native.tv_sec,native.tv_usec};std::memcpy(output,fields,sizeof(fields)); }
  if(zone) { int32_t fields[2]={native_zone.tz_minuteswest,native_zone.tz_dsttime};std::memcpy(zone,fields,sizeof(fields)); }
  return 0;
}
int limits(int resource,void *output) {
  if(!output) {errno=EFAULT;return -1;}
  int native_resource;
  switch(resource) {
  case 0:native_resource=RLIMIT_CPU;break;case 1:native_resource=RLIMIT_FSIZE;break;
  case 2:native_resource=RLIMIT_DATA;break;case 3:native_resource=RLIMIT_STACK;break;
  case 4:native_resource=RLIMIT_CORE;break;case 5:native_resource=RLIMIT_RSS;break;
  case 6:native_resource=RLIMIT_NPROC;break;case 7:native_resource=RLIMIT_NOFILE;break;
  case 8:native_resource=RLIMIT_MEMLOCK;break;case 9:native_resource=RLIMIT_AS;break;
  default:errno=EINVAL;return -1;
  }
  rlimit native{};if(::getrlimit(native_resource,&native))return -1;
  uint64_t values[]={native.rlim_cur==RLIM_INFINITY?UINT64_MAX:uint64_t(native.rlim_cur),
                     native.rlim_max==RLIM_INFINITY?UINT64_MAX:uint64_t(native.rlim_max)};
  std::memcpy(output,values,sizeof(values));return 0;
}
int atomic_pipe(int *descriptors,int flags) {
  if(!descriptors){errno=EFAULT;return -1;}
  if(flags & ~(0x800|0x80000)){errno=EINVAL;return -1;}
  if(__builtin_available(macOS 27.0,*))return ::pipe2(descriptors,(flags&0x800?O_NONBLOCK:0)|(flags&0x80000?O_CLOEXEC:0));
  errno=ENOSYS;return -1;
}
int micro_sleep(uint32_t microseconds) {
  timespec requested{microseconds/1000000,long(microseconds%1000000)*1000};return ::nanosleep(&requested,nullptr);
}
unsigned second_sleep(unsigned seconds) {
  timespec requested{seconds,0},remaining{};
  if(!::nanosleep(&requested,&remaining))return 0;
  if(errno==EINTR)return unsigned(remaining.tv_sec)+(remaining.tv_nsec!=0);
  return seconds;
}
[[noreturn]] void assertion(const char *file,int line,const char *function,const char *message) {
  std::fprintf(stderr,"SHIRAZ_GUEST_ASSERT %.1024s:%d %.1024s: %.1024s\n",file?file:"(null)",line,function?function:"(null)",message?message:"(null)");std::abort();
}
[[noreturn]] void search_failure(const char *reason) {
  std::fprintf(stderr,"SHIRAZ_GUEST_FATAL bsearch %s\n",reason);std::abort();
}
}
std::vector<ProviderExport> core_service_exports(CodeValidator validate) {
  std::vector<ProviderExport> result={
    fixed_export("strcasecmp","LIBC",&case_compare_all),fixed_export("strncasecmp","LIBC",&case_compare),
    fixed_export("__strchr_chk","LIBC",&checked_find),fixed_export("__strrchr_chk","LIBC",&checked_rfind),
    fixed_export("__strncat_chk","LIBC",&checked_ncat),fixed_export("uname","LIBC",&host_name,ErrnoPolicy::MinusOne),
    fixed_export("gettimeofday","LIBC",&wall_time,ErrnoPolicy::MinusOne),
    fixed_export("getrlimit","LIBC",&limits,ErrnoPolicy::MinusOne),fixed_export("getrlimit64","LIBC",&limits,ErrnoPolicy::MinusOne),
    fixed_export("usleep","LIBC",&micro_sleep,ErrnoPolicy::MinusOne),fixed_export("sleep","LIBC",&second_sleep,ErrnoPolicy::Observe),
    fixed_export("arc4random_buf","LIBC",&::arc4random_buf),fixed_export("fchown","LIBC",&::fchown,ErrnoPolicy::MinusOne),
    fixed_export("ldexp","LIBC",static_cast<double(*)(double,int)>(&::ldexp),ErrnoPolicy::Observe),
    fixed_export("pipe2","LIBC",&atomic_pipe,ErrnoPolicy::MinusOne),
    fixed_export("setenv","LIBC",&::setenv,ErrnoPolicy::MinusOne),
    fixed_export("unsetenv","LIBC",&::unsetenv,ErrnoPolicy::MinusOne),
    fixed_export("tzset","LIBC",&::tzset),fixed_export("__assert2","LIBC",&assertion)};
  // Audited POSIX process-global objects: both LP64 ABIs use a pointer-sized
  // environ slot, two char* timezone names and signed64 seconds west. These
  // are explicit shared process state, not copies that become stale on tzset.
  static_assert(sizeof(long)==8 && sizeof(char *)==8);
  result.push_back({"environ","LIBC",reinterpret_cast<uintptr_t>(_NSGetEnviron()),SymbolKind::Object,"posix/process-environ-slot",false});
  result.push_back({"tzname","LIBC",reinterpret_cast<uintptr_t>(::tzname),SymbolKind::Object,"posix/timezone-names",false});
  result.push_back({"timezone","LIBC",reinterpret_cast<uintptr_t>(&::timezone),SymbolKind::Object,"posix/seconds-west-signed64",false});
  auto entry=closure([validate=std::move(validate)](Capture &capture){
    CallCursor cursor(capture,Convention::Aapcs64);
    const void *key=cursor.read<const void *>(),*base=cursor.read<const void *>();
    size_t count=cursor.read<size_t>(),size=cursor.read<size_t>();const void *compare=cursor.read<const void *>();
    HostErrno preserve;
    if(count>(1u<<20) || (count && (!key || !base || !size)) || (size && count>(256u<<20)/size) || count*size>UINTPTR_MAX-reinterpret_cast<uintptr_t>(base))search_failure("array bound invalid");
    if(count && (!validate || !compare || !validate(compare)))search_failure("callback is not guest code");
    size_t first=0,last=count;void *found=nullptr;
    while(first<last) {
      size_t middle=first+(last-first)/2;auto *item=static_cast<const uint8_t *>(base)+middle*size;
      auto frame=invoke(const_cast<void *>(compare),{Argument::integer(int64_t(reinterpret_cast<uintptr_t>(key)),ArgumentType::Int64),Argument::integer(int64_t(reinterpret_cast<uintptr_t>(item)),ArgumentType::Int64)});
      int order=int32_t(frame.result_integers[0]);if(!order){found=const_cast<uint8_t *>(item);break;}if(order<0)last=middle;else first=middle+1;
    }
    return_value(capture,found);
  });
  result.push_back({"bsearch","LIBC",reinterpret_cast<uintptr_t>(entry),SymbolKind::Function,"bsearch/validated-guest-comparator",false});
  auto control=closure([](Capture &capture){
    CallCursor cursor(capture,Convention::Aapcs64);int option=cursor.read<int>();HostErrno preserve;int value=-1,error=0;
    if(option==15 || option==16) {
      void *pointer=cursor.read<void *>();if(!pointer)error=EFAULT;
      else if(option==15){char name[16]{};size_t size=strnlen(static_cast<const char *>(pointer),15);std::memcpy(name,pointer,size);error=pthread_setname_np(name);value=error?-1:0;}
      else {char name[64]{};error=pthread_getname_np(pthread_self(),name,sizeof(name));if(!error){char out[16]{};std::memcpy(out,name,strnlen(name,15));std::memcpy(pointer,out,16);value=0;}}
    }else error=ENOSYS;
    if(error)*guest_errno_location()=errno_to_guest(error);return_value(capture,value);
  });
  result.push_back({"prctl","LIBC",reinterpret_cast<uintptr_t>(control),SymbolKind::Function,"prctl/real-thread-name-only",false});
  return result;
}
}
