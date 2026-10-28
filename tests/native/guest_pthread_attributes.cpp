// SPDX-License-Identifier: GPL-3.0-only
#include "guest_pthread_attributes.h"
#include <cassert>
#include <cstring>
#include <limits>
int main() {
  using namespace shiraz_guest_attr;
  Attr attr{}; assert(init(&attr)==0);
  int value=-1; assert(getinheritsched(&attr,&value)==0 && value==1);
  assert(setinheritsched(&attr,0)==0); assert(getinheritsched(&attr,&value)==0 && value==0);
  assert(setinheritsched(&attr,9)==EINVAL); assert(getinheritsched(&attr,&value)==0 && value==0);
  assert(setdetachstate(&attr,1)==0); assert(getdetachstate(&attr,&value)==0 && value==1);
  assert(setscope(&attr,1)==ENOTSUP && setscope(&attr,9)==EINVAL);
  assert(setstacksize(&attr,1)==EINVAL && setstacksize(&attr,16384)==0);
  assert(setstack(&attr,reinterpret_cast<void*>(1),16384)==EINVAL);
  assert(setstack(&attr,reinterpret_cast<void*>(4096),16385)==EINVAL);
  assert(setguardsize(&attr,8192)==0); size_t size=0;
  assert(getguardsize(&attr,&size)==0 && size==8192);
  assert(setschedpolicy(&attr,SCHED_FIFO)==0);
  int priority=3; assert(setschedparam(&attr,&priority)==0);
  priority=0; assert(getschedparam(&attr,&priority)==0 && priority==3);
  assert(destroy(&attr)==0);
  ShirazHostPthreadAttributes defaults(nullptr);
  assert(defaults.error==0 && !defaults.stack && defaults.stack_size>=16384);
  size_t host_size=0; assert(pthread_attr_getstacksize(&defaults.host,&host_size)==0);
  assert(host_size>=defaults.stack_size);
  ShirazGuestPthreadAttributes guest{};
  guest.flags=1; guest.stack_size=2*1024*1024; guest.guard_size=8192;
  auto original=guest;
  ShirazHostPthreadAttributes translated(&guest);
  assert(translated.error==0 && translated.stack_size==guest.stack_size);
  assert(std::memcmp(&guest,&original,sizeof guest)==0);
  int detached=0; size_t guard=0;
  assert(pthread_attr_getdetachstate(&translated.host,&detached)==0 && detached==PTHREAD_CREATE_DETACHED);
  assert(pthread_attr_getguardsize(&translated.host,&guard)==0 && guard==8192);
  guest.flags=2; assert(ShirazHostPthreadAttributes(&guest).error==EINVAL);
  guest.flags=12; assert(ShirazHostPthreadAttributes(&guest).error==EINVAL);
  guest.flags=8; guest.sched_policy=SCHED_OTHER; guest.sched_priority=0;
  ShirazHostPthreadAttributes explicit_schedule(&guest);
  assert(explicit_schedule.error==0);
  int inheritance=0; pthread_attr_getinheritsched(&explicit_schedule.host,&inheritance);
  assert(inheritance==PTHREAD_EXPLICIT_SCHED);
  guest.flags=0; guest.sched_policy=12345;
  assert(ShirazHostPthreadAttributes(&guest).error==EINVAL);
  guest.flags=4;
  assert(ShirazHostPthreadAttributes(&guest).error==0);
  guest.stack_size=1; assert(ShirazHostPthreadAttributes(&guest).error==EINVAL);
  guest.stack_size=2*1024*1024; guest.stack_base=reinterpret_cast<void*>(1);
  assert(ShirazHostPthreadAttributes(&guest).error==EINVAL);
}
