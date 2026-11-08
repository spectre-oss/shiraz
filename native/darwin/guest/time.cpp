// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/time.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include "guest_io.hpp"
#include <array>
#include <ctime>
#include <map>
#include <mutex>
#include <set>
namespace shiraz::guest {
namespace {
namespace b = bionic;
template<class T,size_t Offset,size_t Width> T get(const void *input) {
  static_assert(sizeof(T)==Width);T result;std::memcpy(&result,static_cast<const uint8_t *>(input)+Offset,Width);return result;
}
template<class T,size_t Offset,size_t Width> void put(void *output,T value) {
  static_assert(sizeof(T)==Width);std::memcpy(static_cast<uint8_t *>(output)+Offset,&value,Width);
}
#define FROM(F,T) get<T,b::kTm_##F,b::kTm_##F##Width>(input)
#define TO(F,T,V) put<T,b::kTm_##F,b::kTm_##F##Width>(output,static_cast<T>(V))
std::tm unpack(const void *input) {
  std::tm result{};
#define INT(F) result.F=FROM(F,int32_t)
  INT(tm_sec);INT(tm_min);INT(tm_hour);INT(tm_mday);INT(tm_mon);INT(tm_year);INT(tm_wday);INT(tm_yday);INT(tm_isdst);
#undef INT
  result.tm_gmtoff=FROM(tm_gmtoff,int64_t);result.tm_zone=const_cast<char *>(FROM(tm_zone,const char *));
  return result;
}
struct TimeState {
  std::mutex mutex;
  std::set<std::string> zones;
  size_t zone_bytes=0;
  const char *zone(const char *value) {
    if (!value) return nullptr;
    size_t length=strnlen(value,4096);
    if (length==4096) throw ElfError("unbounded native timezone name");
    std::lock_guard<std::mutex> lock(mutex);
    auto found=zones.find(value);if(found!=zones.end())return found->c_str();
    if(zones.size()>=4096 || length+1>(1u<<20)-zone_bytes)throw ElfError("timezone name limit");
    auto inserted=zones.emplace(value);zone_bytes+=length+1;return inserted.first->c_str();
  }
  void pack(void *output,const std::tm &input) {
    const char *name=zone(input.tm_zone);
    std::memset(output,0,b::kTmSize);
#define INT(F) TO(F,int32_t,input.F)
    INT(tm_sec);INT(tm_min);INT(tm_hour);INT(tm_mday);INT(tm_mon);INT(tm_year);INT(tm_wday);INT(tm_yday);INT(tm_isdst);
#undef INT
    TO(tm_gmtoff,int64_t,input.tm_gmtoff);TO(tm_zone,const char *,name);
  }
};
#undef FROM
#undef TO
alignas(8) thread_local std::array<uint8_t,b::kTmSize> time_buffer{};
thread_local std::array<char,26> calendar_buffer{};
char *calendar(const time_t *input) { if(!input){errno=EFAULT;return nullptr;}return ::ctime_r(input,calendar_buffer.data()); }
static_assert(sizeof(time_t)==8 && b::kTimespecSize==16);
int sleep_time(const void *input,void *remaining) {
  if(!input){errno=EFAULT;return -1;}
  int64_t fields[2];std::memcpy(fields,input,16);
  if(fields[0]<0 || fields[1]<0 || fields[1]>=1000000000){errno=EINVAL;return -1;}
  timespec requested{fields[0],fields[1]},rest{};
  int result=::nanosleep(&requested,&rest);int error=errno;
  if(result<0 && error==EINTR && remaining){int64_t values[2]={rest.tv_sec,rest.tv_nsec};std::memcpy(remaining,values,16);}
  errno=error;return result;
}
}
std::vector<ProviderExport> time_exports() {
  auto state=std::make_shared<TimeState>();
  std::vector<ProviderExport> result;
  result.push_back(fixed_export("time","LIBC",&::time,ErrnoPolicy::Observe));
  result.push_back(fixed_export("difftime","LIBC",&::difftime));
  result.push_back(fixed_export("nanosleep","LIBC",&sleep_time,ErrnoPolicy::MinusOne));
  for(bool local:{false,true})for(bool reentrant:{false,true}) {
    void *entry=closure([state,local,reentrant](Capture &capture){
      CallCursor cursor(capture,Convention::Aapcs64);const auto *input=cursor.read<const time_t *>();
      void *output=reentrant?cursor.read<void *>():time_buffer.data();HostErrno preserve;
      void *value=nullptr;int error=0;
      try {
        if(!input || !output)error=EFAULT;
        else {std::tm native{};auto *converted=local ? ::localtime_r(input,&native) : ::gmtime_r(input,&native);
          if(!converted)error=errno?errno:EOVERFLOW;else {state->pack(output,native);value=output;}}
      }catch(const std::bad_alloc &){error=ENOMEM;}
      if(error)*guest_errno_location()=errno_to_guest(error);return_value(capture,value);
    });
    result.push_back({local?(reentrant?"localtime_r":"localtime"):(reentrant?"gmtime_r":"gmtime"),"LIBC",reinterpret_cast<uintptr_t>(entry),SymbolKind::Function,"time/translated-tm",false});
  }
  for(bool utc:{false,true}) {
  void *make=closure([state,utc](Capture &capture){
    CallCursor cursor(capture,Convention::Aapcs64);void *input=cursor.read<void *>();HostErrno preserve;
    time_t value=-1;int error=0;
    try {if(!input)error=EFAULT;else {auto native=unpack(input);value=utc ? ::timegm(&native) : ::mktime(&native);error=errno;state->pack(input,native);}}
    catch(const std::bad_alloc &){error=ENOMEM;value=-1;}
    if(error)*guest_errno_location()=errno_to_guest(error);return_value(capture,value);
  });
  result.push_back({utc ? "timegm" : "mktime","LIBC",reinterpret_cast<uintptr_t>(make),SymbolKind::Function,"time/translated-tm",false});
  }
  result.push_back(fixed_export("ctime","LIBC",&calendar,ErrnoPolicy::Null));
  result.push_back(fixed_export("ctime_r","LIBC",&::ctime_r,ErrnoPolicy::Null));
  for(bool explicit_locale:{false,true}) {
    void *entry=closure([explicit_locale](Capture &capture){
      CallCursor cursor(capture,Convention::Aapcs64);char *output=cursor.read<char *>();size_t size=cursor.read<size_t>();
      const char *format=cursor.read<const char *>();const void *time=cursor.read<const void *>();
      const void *locale=explicit_locale?cursor.read<const void *>():nullptr;HostErrno preserve;size_t value=0;
      if(!time || (explicit_locale && !locale))errno=EINVAL;
      else value=locale_strftime(output,size,format,unpack(time),locale);
      if(errno)*guest_errno_location()=errno_to_guest(errno);return_value(capture,value);
    });
    result.push_back({explicit_locale?"strftime_l":"strftime","LIBC",reinterpret_cast<uintptr_t>(entry),SymbolKind::Function,"time/translated-tm-guest-locale",false});
  }
  return result;
}
}
