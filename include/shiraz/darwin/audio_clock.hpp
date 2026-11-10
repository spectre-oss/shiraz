// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include <ctime>
#include <mach/mach_time.h>

namespace shiraz::darwin {
// AVFoundation/AudioQueue host ticks and Java monotonic timestamps have
// different epochs. Sample both clocks together rather than reporting a read
// or callback arrival time as the sample's acquisition/presentation time.
inline uint64_t audio_monotonic_nanos(uint64_t host_time){
  // Darwin CLOCK_MONOTONIC includes suspend time (Android BOOTTIME).
  // Java nanoTime / Android MONOTONIC use the uptime clock instead.
  struct timespec now{};if(clock_gettime(CLOCK_UPTIME_RAW,&now))return 0;
  uint64_t host_now=mach_absolute_time();mach_timebase_info_data_t scale{};
  if(mach_timebase_info(&scale)!=KERN_SUCCESS||!scale.denom)return 0;
  long double delta=host_time>=host_now?static_cast<long double>(host_time-host_now):-static_cast<long double>(host_now-host_time);
  long double nanos=static_cast<long double>(now.tv_sec)*1000000000+now.tv_nsec+delta*scale.numer/scale.denom;
  return nanos>0&&nanos<static_cast<long double>(UINT64_MAX)?uint64_t(nanos):0;
}
}
