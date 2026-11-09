// SPDX-License-Identifier: GPL-3.0-only
// Verify the production VM coverage helper, not a copied approximation.
#include "native/darwin/guest/native_mapping.hpp"
#include <cstdio>
#include <cstdlib>
#include <sys/mman.h>
#include <unistd.h>

using shiraz::guest::detail::native_mapping;
static void require(bool value, const char *message) {
  if (!value) { std::fprintf(stderr, "FAIL %s\n", message); std::exit(1); }
}
__attribute__((noinline)) static int marker() { return 73; }
int main() {
  const auto native_page = sysconf(_SC_PAGESIZE);
  require(native_page > 0 && uintptr_t(native_page) <= UINTPTR_MAX / 3, "native page size");
  const size_t page = size_t(native_page);
  void *memory = mmap(nullptr, page * 3, PROT_READ | PROT_WRITE,
                      MAP_PRIVATE | MAP_ANON, -1, 0);
  require(memory != MAP_FAILED, "three-page reservation");
  const auto base = reinterpret_cast<uintptr_t>(memory);
  require(base <= UINTPTR_MAX - page * 3, "reservation end bound");
  auto *middle = static_cast<char *>(memory) + page;
  const vm_prot_t read = VM_PROT_READ, execute = VM_PROT_EXECUTE, write = VM_PROT_WRITE;
  require(!native_mapping(base, 0, read, execute), "empty span rejected");
  require(!native_mapping(0, 1, read, execute), "null address is not readable data");
  require(!native_mapping(UINTPTR_MAX - 3, 8, read, execute), "overflow span rejected");
  require(native_mapping(base, page, read, execute), "exact first-page boundary");
  require(native_mapping(base + page * 2, page, read, execute), "exact final-page boundary");
  require(!native_mapping(base, page * 3, read | execute, write), "data cannot satisfy code policy");
  require(native_mapping(reinterpret_cast<uintptr_t>(&marker), 4, read | execute, write),
          "own compiled text has code policy");
  require(mprotect(middle, page, PROT_READ) == 0, "middle read-only protection");
  // Different adjacent protections force a leaf boundary inside this span.
  require(native_mapping(base, page * 3, read, execute), "complete adjacent readable coverage");
  require(!native_mapping(base, page * 3, read | write, execute),
          "required write permission checked on every leaf");
  require(native_mapping(base + page, page, read, execute), "exact read-only-page boundary");
  require(mprotect(middle, page, PROT_NONE) == 0, "middle inaccessible protection");
  require(!native_mapping(base, page * 3, read, execute), "inaccessible middle leaf rejected");
  require(native_mapping(base, page, read, execute), "valid prefix remains covered");
  require(native_mapping(base + page * 2, page, read, execute), "readable suffix remains covered");
  require(munmap(middle, page) == 0, "middle mapping removal");
  require(!native_mapping(base, page * 3, read, execute), "gap is not skipped to readable suffix");
  require(!native_mapping(base + page, 1, read, execute), "gap start rejected");
  require(munmap(memory, page) == 0, "first-page release");
  require(munmap(reinterpret_cast<void *>(base + page * 2), page) == 0, "final-page release");
  std::puts("M5_NATIVE_MAPPING_PASS adjacent_readable=1 every_leaf_permissions=1 noaccess_refused=1 gap_refused=1 boundaries=1 overflow_refused=1 code_policy=1");
}
