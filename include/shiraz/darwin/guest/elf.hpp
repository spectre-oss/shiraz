// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace shiraz::guest {
class ElfError : public std::runtime_error {
public:
  using std::runtime_error::runtime_error;
};
struct Segment {
  uint64_t offset, address, file_size, memory_size;
  uint32_t flags;
};
struct Version {
  uint16_t index = 1;
  uint32_t hash = 0;
  std::string name, library;
  bool hidden = false;
};
struct Symbol {
  std::string name;
  uint64_t address, size;
  uint16_t section;
  uint8_t info, visibility;
  Version version;
};
struct Relocation {
  uint64_t address;
  int64_t addend;
  uint32_t type, symbol;
};
// Immutable parse result. Addresses remain guest virtual addresses until
// mapped.
struct Elf {
  uint64_t first_page = 0, span = 0;
  std::vector<Segment> segments;
  // Optional allocated, non-instruction section extents. LOAD segments remain
  // authoritative for mapping; these declarations only separate data/code.
  std::vector<Segment> data_sections;
  // Original bounded ELF64 PHDR records for guest dl_iterate_phdr. These are
  // file metadata, not host Mach-O headers or reconstructed LOAD records.
  std::vector<uint8_t> program_headers;
  uint16_t program_header_count = 0;
  std::vector<Symbol> symbols;
  std::vector<Relocation> relocations;
  std::vector<std::string> needed;
  uint64_t init = 0, init_array = 0, init_array_size = 0;
  uint64_t fini = 0, fini_array = 0, fini_array_size = 0;
  uint64_t relro = 0, relro_size = 0;
  uint64_t eh_frame_header = 0, eh_frame_header_size = 0;
  bool versioned = false, symbolic = false, global = false;
  std::string soname;
  std::vector<Version> definitions, requirements;
};
// Section headers are deliberately not required: Android libraries can be
// stripped.
Elf parse_elf(const std::vector<uint8_t> &bytes, uint64_t page_size);
} // namespace shiraz::guest
