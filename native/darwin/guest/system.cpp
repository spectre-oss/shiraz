// SPDX-License-Identifier: GPL-3.0-only
// Original adapters. Linux numeric ABI: pinned android-14.0.0_r1 bionic
// bits/sysconf.h, linux/auxvec.h, asm/hwcap.h. Feature query names follow
// Apple's documented sysctl instruction-set interface and LLVM compiler-rt's
// Apple AArch64 CPU feature resolver. No upstream source is copied here.
#include "shiraz/darwin/guest/system.hpp"
#include <algorithm>
#include <array>
#include <climits>
#include <cstdlib>
#include <mach/mach.h>
#include <sched.h>
#include <sys/sysctl.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
long host_conf(int selector) {
  errno = 0;
  long result = ::sysconf(selector);
  if (result == -1 && errno) *guest_errno_location() = errno_to_guest(errno);
  return result;
}
long free_pages() {
  vm_statistics64_data_t statistics{};
  mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
  mach_port_t port = mach_host_self();
  kern_return_t status = host_statistics64(port, HOST_VM_INFO64,
      reinterpret_cast<host_info64_t>(&statistics), &count);
  (void)mach_port_deallocate(mach_task_self(), port);
  if (status != KERN_SUCCESS || count < HOST_VM_INFO64_COUNT) {
    *guest_errno_location() = errno_to_guest(EIO);
    return -1;
  }
  // This is genuinely free physical memory, not an invented estimate of
  // reclaimable caches or of the guest namespace's owned mmap allowance.
  return long(statistics.free_count);
}
long configuration(int selector) {
  switch (selector) {
  case 0x00: return host_conf(_SC_ARG_MAX);
  case 0x05: return host_conf(_SC_CHILD_MAX);
  case 0x06: return host_conf(_SC_CLK_TCK);
  case 0x0a: return host_conf(_SC_NGROUPS_MAX);
  case 0x0b: return host_conf(_SC_OPEN_MAX);
  case 0x1b: return host_conf(_SC_STREAM_MAX);
  case 0x26: return host_conf(_SC_IOV_MAX);
  case 0x27: case 0x28: return host_conf(_SC_PAGESIZE);
  case 0x36: return 0x3fffffff; // Guest semaphore's actual full bionic count.
  case 0x4a: return host_conf(_SC_THREAD_DESTRUCTOR_ITERATIONS);
  case 0x4b: {
    long host = host_conf(_SC_THREAD_KEYS_MAX);
    return host < 0 ? host : std::min(host, 4096L); // thread.cpp registry cap.
  }
  case 0x4c: {
    long host = host_conf(_SC_THREAD_STACK_MIN);
    return host < 0 ? host : std::max(host, 16384L); // AArch64 bionic minimum.
  }
  case 0x4d: return 128; // Actual guest thread-record cap, not host's larger limit.
  case 0x60: return host_conf(_SC_NPROCESSORS_CONF);
  case 0x61: return host_conf(_SC_NPROCESSORS_ONLN);
  case 0x62: return host_conf(_SC_PHYS_PAGES);
  case 0x63: return free_pages();
  default:
    // Unimplemented selectors must not accidentally select a different
    // Darwin feature merely because their integer values coincide.
    *guest_errno_location() = errno_to_guest(EINVAL);
    return -1;
  }
}
bool feature(const char *name) {
  int32_t enabled = 0;
  size_t size = sizeof(enabled);
  return !sysctlbyname(name, &enabled, &size, nullptr, 0) &&
         size == sizeof(enabled) && enabled == 1;
}
struct Capabilities { unsigned long first = 0, second = 0; };
const Capabilities &capabilities() {
  static const Capabilities value = [] {
    Capabilities result;
    struct Feature { const char *name; unsigned bit; bool second; };
    const Feature features[] = {
      {"hw.optional.floatingpoint", 0, false},
      {"hw.optional.neon", 1, false},
      {"hw.optional.arm.FEAT_AES", 3, false},
      {"hw.optional.arm.FEAT_PMULL", 4, false},
      {"hw.optional.arm.FEAT_SHA1", 5, false},
      {"hw.optional.arm.FEAT_SHA256", 6, false},
      {"hw.optional.armv8_crc32", 7, false},
      {"hw.optional.arm.FEAT_LSE", 8, false},
      {"hw.optional.arm.FEAT_FP16", 9, false},
      {"hw.optional.arm.FEAT_FP16", 10, false},
      {"hw.optional.arm.FEAT_RDM", 12, false},
      {"hw.optional.arm.FEAT_JSCVT", 13, false},
      {"hw.optional.arm.FEAT_FCMA", 14, false},
      {"hw.optional.arm.FEAT_LRCPC", 15, false},
      {"hw.optional.arm.FEAT_SHA3", 17, false},
      {"hw.optional.arm.FEAT_DotProd", 20, false},
      {"hw.optional.arm.FEAT_SHA512", 21, false},
      {"hw.optional.arm.FEAT_FHM", 23, false},
      {"hw.optional.arm.FEAT_LRCPC2", 26, false},
      {"hw.optional.arm.FEAT_FlagM", 27, false},
      {"hw.optional.arm.FEAT_FlagM2", 7, true},
      {"hw.optional.arm.FEAT_FRINTTS", 8, true},
      {"hw.optional.arm.FEAT_I8MM", 13, true},
      {"hw.optional.arm.FEAT_BF16", 14, true},
    };
    for (const auto &entry : features)
      if (feature(entry.name)) (entry.second ? result.second : result.first) |= 1ul << entry.bit;
    // CPUID, PAC, SVE/SME, BTI, MTE, RNG and other OS/ABI facilities remain
    // absent even when the host CPU advertises their hardware. The guest
    // has no Linux kernel register-emulation, signal context or PAC contract.
    return result;
  }();
  return value;
}
unsigned long auxiliary(unsigned long type) {
  switch (type) {
  case 6: return unsigned(::getpagesize());
  case 11: return ::getuid();
  case 12: return ::geteuid();
  case 13: return ::getgid();
  case 14: return ::getegid();
  case 16: return capabilities().first;
  case 17: {
    long ticks = host_conf(_SC_CLK_TCK);
    if (ticks > 0) return static_cast<unsigned long>(ticks);
    *guest_errno_location() = errno_to_guest(ENOENT);
    return 0;
  }
  case 23: return ::issetugid() ? 1 : 0;
  case 25: {
    static const std::array<unsigned char, 16> random = [] {
      std::array<unsigned char, 16> bytes{};
      arc4random_buf(bytes.data(), bytes.size());
      return bytes;
    }();
    return reinterpret_cast<unsigned long>(random.data());
  }
  case 26: return capabilities().second;
  default:
    *guest_errno_location() = errno_to_guest(ENOENT);
    return 0;
  }
}
} // namespace
std::vector<ProviderExport> system_exports() {
  return {fixed_export("sysconf", "LIBC", &configuration),
          fixed_export("getauxval", "LIBC", &auxiliary),
          fixed_export("getpid", "LIBC", &::getpid),
          fixed_export("getuid", "LIBC", &::getuid),
          fixed_export("geteuid", "LIBC", &::geteuid),
          fixed_export("getgid","LIBC",&::getgid),fixed_export("getegid","LIBC",&::getegid),
          fixed_export("sched_get_priority_min","LIBC",+[](int policy){int native=policy==0?SCHED_OTHER:policy==1?SCHED_FIFO:policy==2?SCHED_RR:-1;if(native<0){errno=EINVAL;return -1;}return ::sched_get_priority_min(native);},ErrnoPolicy::MinusOne),
          fixed_export("sched_get_priority_max","LIBC",+[](int policy){int native=policy==0?SCHED_OTHER:policy==1?SCHED_FIFO:policy==2?SCHED_RR:-1;if(native<0){errno=EINVAL;return -1;}return ::sched_get_priority_max(native);},ErrnoPolicy::MinusOne),
          fixed_export("__sched_cpucount","LIBC",+[](size_t size,const void *input){
            if(!input||size>4096){errno=EINVAL;return 0;}int count=0;auto *bytes=static_cast<const uint8_t *>(input);
            for(size_t i=0;i<size;++i)count+=__builtin_popcount(unsigned(bytes[i]));return count;
          },ErrnoPolicy::Observe),
          fixed_export("sched_getaffinity","LIBC",+[](int pid,size_t size,void *output){
            if(pid && pid!=::getpid()){errno=ESRCH;return -1;}long cpus=::sysconf(_SC_NPROCESSORS_ONLN);
            if(!output){errno=EFAULT;return -1;}if(cpus<=0||size>4096||size<size_t((cpus+63)/64)*8){errno=EINVAL;return -1;}
            std::memset(output,0,size);auto *bytes=static_cast<uint8_t *>(output);for(long i=0;i<cpus;++i)bytes[i/8]|=uint8_t(1u<<(i%8));return 0;
          },ErrnoPolicy::MinusOne),
          fixed_export("sched_yield", "LIBC", &::sched_yield, ErrnoPolicy::MinusOne)};
}
} // namespace shiraz::guest
