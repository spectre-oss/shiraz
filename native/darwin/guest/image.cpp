// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <cstdlib>
#include <libkern/OSCacheControl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <unistd.h>

namespace shiraz::guest {
namespace {
using Clock = std::chrono::steady_clock;
uint64_t elapsed(Clock::time_point start) {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}
Elf timed_parse(const std::vector<uint8_t> &bytes, uint64_t page, uint64_t &duration) {
  auto start = Clock::now();
  auto elf = parse_elf(bytes, page);
  duration = elapsed(start);
  return elf;
}
[[noreturn]] void system_error(const char *operation) {
  throw ElfError(std::string(operation) + ": " + std::strerror(errno));
}
uint64_t page_size() {
  long page = sysconf(_SC_PAGESIZE);
  if (page <= 0)
    throw ElfError("cannot determine host page size");
  return uint64_t(page);
}
bool contains(const Segment &segment, uint64_t address, uint64_t size) {
  return address >= segment.address &&
         address - segment.address <= segment.memory_size &&
         size <= segment.memory_size - (address - segment.address);
}
} // namespace

Image::Image(const std::vector<uint8_t> &bytes, Mapping mode, uintptr_t syscall_dispatcher)
    : elf_(timed_parse(bytes, page_size(), metrics_.parse_ns)) {
  if (mode == Mapping::SelfContained) {
    if (!elf_.needed.empty())
      throw ElfError("guest dependency loading is not implemented");
    if (elf_.versioned)
      throw ElfError("guest symbol version binding is not implemented");
    for (const auto &symbol : elf_.symbols) {
      if (!symbol.section && !symbol.name.empty())
        throw ElfError("guest import binding is not implemented: " +
                       symbol.name);
    }
  }
  if (!pthread_jit_write_protect_supported_np())
    throw ElfError("JIT write protection unavailable");
  const uint64_t page = page_size();
  auto rewrite_start = Clock::now();
  analysis_ = analyze_thread_pointers(elf_, bytes);
  syscall_dispatcher_ = syscall_dispatcher;
  if (!analysis_.syscalls.empty() && !syscall_dispatcher_)
    throw ElfError("guest direct syscalls require a Linux syscall dispatcher");
  metrics_.rewrite_ns = elapsed(rewrite_start);
  auto map_start = Clock::now();
  unwind_ = std::make_unique<UnwindRegistration>(elf_, bytes);
  uint64_t islands = ((analysis_.sites.size() + analysis_.syscalls.size()) * uint64_t(64) + page - 1) & ~(page - 1);
  mapping_size_ = elf_.span + islands;
  // Each page has one owner/protection class. Parser already rejected
  // incompatible segment sharing at the host page granularity.
  page_flags_.assign(elf_.span / page, 0);
  auto &flags = page_flags_;
  for (const auto &segment : elf_.segments) {
    uint64_t first = (segment.address - elf_.first_page) / page;
    uint64_t last =
        (segment.address - elf_.first_page + segment.memory_size + page - 1) /
        page;
    for (uint64_t i = first; i < last; ++i)
      flags[i] = segment.flags;
  }
  base_ = static_cast<uint8_t *>(mmap(nullptr, mapping_size_,
                                      PROT_READ | PROT_WRITE | PROT_EXEC,
                                      MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
  if (base_ == MAP_FAILED) {
    base_ = nullptr;
    system_error("reserve guest image");
  }
  try {
    reservation_ = std::make_unique<GuestImageReservation>(reinterpret_cast<uintptr_t>(base_), mapping_size_);
    for (uint64_t i = 0; i < flags.size();) {
      if (flags[i] & 1) {
        ++i;
        continue;
      }
      uint64_t end = i + 1;
      while (end < flags.size() && flags[end] == flags[i])
        ++end;
      int protection = flags[i] ? PROT_READ | PROT_WRITE : PROT_NONE;
      if (mmap(base_ + i * page, (end - i) * page, protection,
               MAP_PRIVATE | MAP_ANON | MAP_FIXED, -1, 0) == MAP_FAILED)
        system_error("replace owned guest data/gap pages");
      i = end;
    }
    {
      JitWriteScope writable;
      for (const auto &segment : elf_.segments) {
        std::memcpy(at(segment.address, segment.file_size),
                    bytes.data() + segment.offset, segment.file_size);
        // Anonymous mappings supply the zero-filled BSS, including partial
        // pages.
      }
    }
    metrics_.map_ns = elapsed(map_start);
    if (mode == Mapping::SelfContained) {
      relocate([&](size_t symbol) { return address(symbol); });
      seal();
    }
  } catch (...) {
    unwind_->reset();
    if (munmap(base_, mapping_size_)) std::abort();
    base_ = nullptr;
    throw;
  }
}

void Image::relocate(const std::function<uintptr_t(size_t)> &resolve) {
  if (link_state_ != LinkState::Mapped)
    throw ElfError("guest image already relocated");
  auto bind_start = Clock::now();
  uintptr_t bias = reinterpret_cast<uintptr_t>(base_) - elf_.first_page;
  std::vector<uint64_t> values;
  values.reserve(elf_.relocations.size());
  for (const auto &relocation : elf_.relocations)
    values.push_back(
        (relocation.type == 1027 ? bias : resolve(relocation.symbol)) +
        uint64_t(relocation.addend));
  JitWriteScope writable;
  for (size_t i = 0; i < values.size(); ++i)
    std::memcpy(at(elf_.relocations[i].address, 8), &values[i], 8);
  link_state_ = LinkState::Relocated;
  metrics_.bind_ns = elapsed(bind_start);
}

void Image::seal() {
  if (link_state_ != LinkState::Relocated)
    throw ElfError("guest image must bind before sealing");
  auto rewrite_start = Clock::now();
  const uint64_t page = page_size();
  std::vector<ThreadPointerPatch> patches;
  auto sites = analysis_.sites;
  sites.insert(sites.end(), analysis_.syscalls.begin(), analysis_.syscalls.end());
  patches.reserve(sites.size());
  for (size_t i = 0; i < sites.size(); ++i) {
    const auto &site = sites[i];
    auto *code = static_cast<uint8_t *>(at(site.address, 4));
    uint32_t original;
    std::memcpy(&original, code, 4);
    if (original != site.instruction) throw ElfError("guest TPIDR code changed after analysis");
    uintptr_t island = reinterpret_cast<uintptr_t>(base_ + elf_.span + i * 64);
    patches.push_back(original == 0xd4000001u
        ? plan_system_call(reinterpret_cast<uintptr_t>(code), original, island, syscall_dispatcher_)
        : plan_thread_pointer(reinterpret_cast<uintptr_t>(code), original, island));
  }
  {
    JitWriteScope writable;
    for (size_t i = 0; i < patches.size(); ++i) {
      auto *island = base_ + elf_.span + i * 64;
      const auto &patch = patches[i];
      if (patch.uses_island) std::memcpy(island, patch.island.data(), patch.island.size());
      std::memcpy(at(sites[i].address, 4), &patch.replacement, 4);
    }
  }
  metrics_.rewrite_ns += elapsed(rewrite_start);
  auto map_start = Clock::now();
  if (mapping_size_ > elf_.span)
    sys_icache_invalidate(base_ + elf_.span, mapping_size_ - elf_.span);
  const auto &flags = page_flags_;
  for (uint64_t i = 0; i < flags.size();) {
    uint64_t end = i + 1;
    while (end < flags.size() && flags[end] == flags[i])
      ++end;
    if (flags[i] & 1)
      sys_icache_invalidate(base_ + i * page, (end - i) * page);
    else if (flags[i] && !(flags[i] & 2) &&
             mprotect(base_ + i * page, (end - i) * page, PROT_READ))
      system_error("seal guest readonly data");
    i = end;
  }
  if (elf_.relro_size) {
    uint64_t start = elf_.relro & ~(page - 1);
    uint64_t finish = (elf_.relro + elf_.relro_size + page - 1) & ~(page - 1);
    for (const auto &segment : elf_.segments) {
      if (!(segment.flags & 2))
        continue;
      uint64_t a = std::max(start, segment.address);
      uint64_t b = std::min(finish, segment.address + segment.memory_size);
      if (a < b && (a < elf_.relro || b > elf_.relro + elf_.relro_size))
        throw ElfError("guest RELRO shares a host page with mutable data");
    }
    for (uint64_t address = start; address < finish; address += page) {
      unsigned protection = flags.at((address - elf_.first_page) / page);
      if (protection & 1)
        throw ElfError("guest RELRO overlaps executable host page");
      // Keep unused reservation gaps inaccessible, including RELRO padding.
      if (protection && mprotect(at(address, page), page, PROT_READ))
        system_error("seal guest RELRO");
    }
  }
  unwind_->activate(base_, mapping_size_);
  link_state_ = LinkState::Sealed;
  metrics_.map_ns += elapsed(map_start);
}

std::vector<uint64_t> Image::verified_mrs_window_sites() const {
  if (link_state_ != LinkState::Sealed)
    throw ElfError("MRS installation evidence requires a sealed image");
  std::vector<uint64_t> sites;
  sites.reserve(analysis_.sites.size());
  for (size_t i = 0; i < analysis_.sites.size(); ++i) {
    const auto &site = analysis_.sites[i];
    const auto *code = static_cast<const uint8_t *>(at(site.address, 4));
    const auto *island = base_ + elf_.span + i * 64;
    const auto expected = plan_thread_pointer(reinterpret_cast<uintptr_t>(code), site.instruction,
                                               reinterpret_cast<uintptr_t>(island));
    uint32_t installed;
    std::memcpy(&installed, code, sizeof(installed));
    if (installed != expected.replacement)
      throw ElfError("guest MRS installation changed");
    if (!expected.uses_island) continue; // XZR discards TP; no accessor runs.
    if (std::memcmp(island, expected.island.data(), expected.island.size()))
      throw ElfError("guest MRS island installation changed");
    sites.push_back(site.address);
  }
  return sites;
}

uintptr_t Image::address(size_t index) const {
  const auto &symbol = elf_.symbols.at(index);
  if (!symbol.section)
    throw ElfError("undefined guest symbol has no address");
  if (symbol.section == 0xfff1)
    return uintptr_t(symbol.address);
  return reinterpret_cast<uintptr_t>(at(symbol.address, symbol.size));
}

Image::~Image() {
  if (unwind_) unwind_->reset();
  // reservation_ remains registered until member destruction after unmapping.
  if (base_ && munmap(base_, mapping_size_)) std::abort();
}

void *Image::at(uint64_t address, uint64_t size) const {
  if (address < elf_.first_page || address - elf_.first_page > elf_.span ||
      size > elf_.span - (address - elf_.first_page))
    throw ElfError("guest address outside reservation");
  return base_ + (address - elf_.first_page);
}

void *Image::symbol(std::string_view name) const {
  for (const auto &s : elf_.symbols) {
    if (s.name != name || !s.section || s.section == 0xfff1 ||
        (s.visibility & 3) == 2)
      continue;
    if ((s.info >> 4) != 1 && (s.info >> 4) != 2)
      continue;
    return at(s.address, s.size);
  }
  throw ElfError("guest export not found: " + std::string(name));
}

bool Image::contains_code(const void *pointer) const {
  uintptr_t value = reinterpret_cast<uintptr_t>(pointer);
  uintptr_t base = reinterpret_cast<uintptr_t>(base_);
  if ((value & 3) || value < base || value - base >= elf_.span)
    return false;
  uint64_t address = elf_.first_page + (value - base);
  for (const auto &segment : elf_.segments)
    if ((segment.flags & 1) && contains(segment, address, 4))
      return true;
  return false;
}

size_t Image::initialize() {
  if (link_state_ != LinkState::Sealed)
    throw ElfError("guest image is not bound/sealed");
  if (state_ == State::Ready)
    return 0;
  if (state_ != State::Uninitialized)
    throw ElfError("guest initialization is failed or reentrant");
  // Count DT_INIT and non-sentinel entries in the relocated DT_INIT_ARRAY
  // before executing any constructor; this is independent of the execution loop.
  metrics_.expected_constructors = elf_.init ? 1 : 0;
  for (uint64_t offset = 0; offset < elf_.init_array_size; offset += 8) {
    uintptr_t pointer;
    std::memcpy(&pointer, at(elf_.init_array + offset, 8), 8);
    if (pointer && pointer != UINTPTR_MAX) ++metrics_.expected_constructors;
  }
  state_ = State::Initializing;
  auto init_start = Clock::now();
  try {
    auto execute = [&](uint64_t address) {
      if (address & 3) throw ElfError("unaligned guest constructor pointer");
      bool valid = false;
      for (const auto &segment : elf_.segments)
        if ((segment.flags & 1) && contains(segment, address, 4))
          valid = true;
      if (!valid)
        throw ElfError("guest constructor outside executable LOAD");
      void *pointer = at(address, 4);
      // Constructors are guest calls too: use the same AAPCS64 frame and
      // capture-boundary lifetime as JNI and other synchronous callbacks.
      invoke(pointer, {});
      ++metrics_.executed_constructors;
    };
    size_t count = 0;
    if (elf_.init) {
      execute(elf_.init);
      ++count;
    }
    for (uint64_t offset = 0; offset < elf_.init_array_size; offset += 8) {
      uintptr_t pointer;
      std::memcpy(&pointer, at(elf_.init_array + offset, 8), 8);
      if (!pointer || pointer == UINTPTR_MAX)
        continue;
      const uintptr_t base = reinterpret_cast<uintptr_t>(base_);
      if (pointer < base || pointer - base >= elf_.span)
        throw ElfError("invalid guest constructor pointer");
      execute(elf_.first_page + (pointer - base));
      ++count;
    }
    if (count != metrics_.expected_constructors || count != metrics_.executed_constructors)
      throw ElfError("guest constructor metadata/count mismatch");
    metrics_.init_ns = elapsed(init_start);
    metrics_.initialized = true;
    state_ = State::Ready;
    return count;
  } catch (...) {
    metrics_.init_ns = elapsed(init_start);
    state_ = State::Failed;
    throw;
  }
}
size_t Image::finalize() {
  if (state_ == State::Uninitialized || state_ == State::Failed ||
      state_ == State::Finalizing || state_ == State::Finalized)
    return 0;
  if (state_ != State::Ready || link_state_ != LinkState::Sealed)
    throw ElfError("guest finalization requires initialized sealed image");
  // Validate all targets before the first callback. Values are relocated
  // native pointers; DT_FINI itself remains an ELF virtual address.
  std::vector<void *> functions;
  auto add = [&](uint64_t address) {
    if (address & 3) throw ElfError("unaligned guest finalizer pointer");
    bool valid = false;
    for (const auto &segment : elf_.segments)
      if ((segment.flags & 1) && contains(segment, address, 4)) valid = true;
    if (!valid) throw ElfError("guest finalizer outside executable LOAD");
    functions.push_back(at(address, 4));
  };
  for (uint64_t offset = elf_.fini_array_size; offset; offset -= 8) {
    uintptr_t pointer;
    std::memcpy(&pointer, at(elf_.fini_array + offset - 8, 8), 8);
    if (!pointer || pointer == UINTPTR_MAX) continue;
    const auto base = reinterpret_cast<uintptr_t>(base_);
    if (pointer < base || pointer - base >= elf_.span)
      throw ElfError("invalid guest finalizer pointer");
    add(elf_.first_page + (pointer - base));
  }
  if (elf_.fini) add(elf_.fini);
  state_ = State::Finalizing;
  try {
    for (auto *function : functions) invoke(function, {});
    state_ = State::Finalized;
    return functions.size();
  } catch (...) {
    state_ = State::Failed;
    throw;
  }
}
} // namespace shiraz::guest
