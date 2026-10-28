// SPDX-License-Identifier: GPL-3.0-only
#include <cstdlib>
#include <cstdint>
#include <initializer_list>
#include <cstring>
#include <cstdio>
#include <cerrno>
#include <atomic>
#include <unistd.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <sys/stat.h>
#include "virtual_file_metadata.h"
namespace berberis {
// Explicit opt-in virtual-device metadata, not host SELinux enforcement.
// Only guest ARM64 syscalls see this mapping; host processes remain unchanged.
long ShirazGuestProfilePath(long nr,long address) {
  if(nr!=48 && nr!=56 && nr!=79 && nr!=439)return address;
  if(!address)return address;
  const char* path=reinterpret_cast<const char*>(address);
  const char* variable=std::strcmp(path,"/sys/fs/selinux/enforce")==0 ? "SHIRAZ_GUEST_SELINUX_ENFORCE_FILE" : std::strcmp(path,"/proc/self/attr/current")==0 ? "SHIRAZ_GUEST_SELINUX_CONTEXT_FILE" : nullptr;
  const char* file=variable?std::getenv(variable):nullptr;
  if(!file || file[0]!='/')return address;
  return reinterpret_cast<long>(file);
}
void ShirazGuestProfileStat(const char* path, struct stat* info) {
  timespec time;
  if (shiraz_virtual_file_time(path, &time))
    info->st_atim = info->st_mtim = info->st_ctim = time;
}
// Diagnostic reads must not dereference invalid guest pointers or change errno.
void ShirazTraceGuestFile(long nr, long a0, long a1, long result) {
  if (!std::getenv("SHIRAZ_TRACE_GUEST_FILES")) return;
  long address;
  if (nr == 43) address = a0;  // statfs
  else if (nr == 48 || nr == 56 || nr == 79 || nr == 439) address = a1;
  else return;
  int saved_errno = errno;
  static std::atomic<unsigned> count{0};
  if (count.fetch_add(1, std::memory_order_relaxed) < 2048) {
    char path[513]{};
    iovec local{path, sizeof(path)-1};
    iovec remote{reinterpret_cast<void*>(address), sizeof(path)-1};
    ssize_t length = process_vm_readv(getpid(), &local, 1, &remote, 1, 0);
    if (length > 0) {
      path[length] = 0;
      for (char* p = path; *p; ++p) if (static_cast<unsigned char>(*p) < 32) *p = '?';
    } else std::snprintf(path, sizeof(path), "<unreadable>");
    std::fprintf(stderr, "SHIRAZ_GUEST_FILE nr=%ld path=%s result=%ld errno=%d\n",
                 nr, path, result, result == -1 ? saved_errno : 0);
  }
  errno = saved_errno;
}

// Called while processing a queued guest fault, before the default termination.
void ShirazTraceGuestFaultMaps(unsigned long pc, unsigned long address) {
  if (!std::getenv("SHIRAZ_TRACE_GUEST_FILES")) return;
  FILE* maps = std::fopen("/proc/self/maps", "r");
  if (!maps) return;
  char line[1024], previous[1024]{};
  while (std::fgets(line, sizeof(line), maps)) {
    unsigned long first, last;
    if (std::sscanf(line, "%lx-%lx", &first, &last) == 2 &&
        ((pc >= first && pc < last) || (address >= first && address < last)))
    {
      if (address >= first && address < last && previous[0])
        std::fprintf(stderr, "SHIRAZ_GUEST_FAULT_PREVIOUS_MAP %s", previous);
      std::fprintf(stderr, "SHIRAZ_GUEST_FAULT_MAP %s", line);
    }
    std::snprintf(previous, sizeof(previous), "%s", line);
  }
  std::fclose(maps);
}

}

namespace berberis {
// Bounded, opt-in instruction-entry diagnostics; never changes guest state.
void ShirazTraceGuestPc(unsigned long pc, const uint64_t* registers) {
  int saved_errno = errno;
  static thread_local unsigned long target = 0;
  static thread_local unsigned polls = 0, hits = 0;
  if (!target && (++polls & 65535) == 1) {
    const char* module = std::getenv("SHIRAZ_TRACE_GUEST_MODULE");
    const char* offset_text = std::getenv("SHIRAZ_TRACE_GUEST_OFFSET");
    char* end = nullptr;
    unsigned long offset = offset_text ? std::strtoul(offset_text, &end, 0) : 0;
    if (module && *module && end && !*end) {
      FILE* maps = std::fopen("/proc/self/maps", "r");
      char line[2048];
      while (maps && std::fgets(line, sizeof(line), maps)) {
        unsigned long first, last, file_offset;
        if (std::strstr(line, module) &&
            std::sscanf(line, "%lx-%lx %*s %lx", &first, &last, &file_offset) == 3 &&
            offset >= file_offset && offset - file_offset < last - first) {
          target = first + offset - file_offset;
          break;
        }
      }
      if (maps) std::fclose(maps);
    }
  }
  if (target && pc == target && hits++ < 64) {
    std::fprintf(stderr, "SHIRAZ_GUEST_PC pc=%lx lr=%lx", pc, (unsigned long)registers[30]);
    for (unsigned i = 0; i < 30; ++i)
      std::fprintf(stderr, " x%u=%lx", i, (unsigned long)registers[i]);
    std::fputc('\n', stderr);
    if (hits == 1) {
      const char* map_register = std::getenv("SHIRAZ_TRACE_GUEST_PC_MAP_REGISTER");
      char* end = nullptr;
      long reg = map_register ? std::strtol(map_register, &end, 10) : -1;
      if (end && !*end && reg >= 0 && reg <= 30)
        ShirazTraceGuestFaultMaps(pc, registers[reg]);
    }

    // Optional VM-local snapshots for diagnosing stack-layout compatibility.
    // At most two 64 KiB reads per thread; never dereference guest pointers.
    const char* directory = std::getenv("SHIRAZ_TRACE_GUEST_MEMORY_DIR");
    if (hits == 1 && directory && directory[0] == '/') {
      for (unsigned reg : {1u, 19u}) {
        unsigned char data[65536];
        iovec local{data, sizeof(data)};
        iovec remote{reinterpret_cast<void*>(registers[reg]), sizeof(data)};
        ssize_t length = process_vm_readv(getpid(), &local, 1, &remote, 1, 0);
        if (length <= 0) continue;
        char filename[4096];
        int used = std::snprintf(filename, sizeof(filename), "%s/trace-%ld-x%u.bin",
                                 directory, syscall(SYS_gettid), reg);
        if (used <= 0 || static_cast<size_t>(used) >= sizeof(filename)) continue;
        int fd = open(filename, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
        if (fd < 0) continue;
        ssize_t written = 0;
        while (written < length) {
          ssize_t step = write(fd, data + written, length - written);
          if (step < 0 && errno == EINTR) continue;
          if (step <= 0) break;
          written += step;
        }
        close(fd);
        std::fprintf(stderr, "SHIRAZ_GUEST_MEMORY reg=%u address=%lx bytes=%zd\n",
                     reg, (unsigned long)registers[reg], written);
      }
    }

  }
  errno = saved_errno;
}
}
