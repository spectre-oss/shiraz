// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/elf.hpp"
#include <algorithm>
#include <limits>
#include <map>

namespace shiraz::guest {
namespace {
constexpr uint64_t kMaxFile = 256ull << 20, kMaxImage = 512ull << 20;
constexpr uint64_t kMaxSymbols = 1ull << 20, kMaxRelocations = 2ull << 20;
[[noreturn]] void bad(const char *message) { throw ElfError(message); }
uint64_t add(uint64_t a, uint64_t b) {
  if (b > std::numeric_limits<uint64_t>::max() - a)
    bad("ELF integer overflow");
  return a + b;
}
class Reader {
public:
  explicit Reader(const std::vector<uint8_t> &bytes) : bytes_(bytes) {}
  void range(uint64_t offset, uint64_t size) const {
    if (offset > bytes_.size() || size > bytes_.size() - offset)
      bad("ELF file range out of bounds");
  }
  uint64_t u(uint64_t offset, unsigned size) const {
    range(offset, size);
    uint64_t value = 0;
    for (unsigned i = 0; i < size; ++i)
      value |= uint64_t(bytes_[offset + i]) << (8 * i);
    return value;
  }
  std::string string(uint64_t base, uint64_t length, uint64_t index) const {
    range(base, length);
    if (index >= length)
      bad("ELF string index out of bounds");
    const auto start = bytes_.begin() + base + index;
    // Bound individual names as well as their aggregate string table.
    auto end =
        std::find(start, start + std::min<uint64_t>(length - index, 4096), 0);
    if (end == start + std::min<uint64_t>(length - index, 4096))
      bad("ELF string missing bounded terminator");
    return std::string(start, end);
  }

private:
  const std::vector<uint8_t> &bytes_;
};
uint64_t file_address(const Elf &elf, uint64_t address, uint64_t size) {
  for (const auto &segment : elf.segments) {
    if (address >= segment.address &&
        address - segment.address <= segment.file_size &&
        size <= segment.file_size - (address - segment.address))
      return add(segment.offset, address - segment.address);
  }
  bad("ELF metadata is not file backed");
}
bool memory_address(const Elf &elf, uint64_t address, uint64_t size,
                    uint32_t flags = 0) {
  for (const auto &segment : elf.segments)
    if ((segment.flags & flags) == flags && address >= segment.address &&
        address - segment.address <= segment.memory_size &&
        size <= segment.memory_size - (address - segment.address))
      return true;
  return false;
}
} // namespace

Elf parse_elf(const std::vector<uint8_t> &bytes, uint64_t page_size) {
  if (bytes.size() > kMaxFile)
    bad("ELF exceeds file limit");
  if (page_size < 4096 || page_size > 65536 || (page_size & (page_size - 1)))
    bad("unsupported host page size");
  Reader r(bytes);
  r.range(0, 64);
  if (r.u(0, 4) != 0x464c457f || r.u(4, 1) != 2 || r.u(5, 1) != 1 ||
      r.u(6, 1) != 1 || r.u(16, 2) != 3 || r.u(18, 2) != 183 || r.u(20, 4) != 1)
    bad("requires little-endian ELF64 AArch64 ET_DYN");
  if (r.u(52, 2) != 64 || r.u(54, 2) != 56)
    bad("invalid ELF header sizes");
  uint64_t phoff = r.u(32, 8), count = r.u(56, 2);
  if (!count || count > 128)
    bad("ELF program-header limit");
  r.range(phoff, count * 56);
  Elf elf;
  elf.program_headers.assign(bytes.begin() + phoff, bytes.begin() + phoff + count * 56);
  elf.program_header_count = uint16_t(count);
  uint64_t dynamic_address = 0, dynamic_size = 0;
  bool has_dynamic = false, has_relro = false;
  uint64_t low = UINT64_MAX, high = 0;
  for (uint64_t i = 0; i < count; ++i) {
    uint64_t p = phoff + i * 56;
    uint32_t type = r.u(p, 4), flags = r.u(p + 4, 4);
    uint64_t offset = r.u(p + 8, 8), address = r.u(p + 16, 8);
    uint64_t filesz = r.u(p + 32, 8), memsz = r.u(p + 40, 8),
             align = r.u(p + 48, 8);
    if (type == 3)
      bad("guest ELF interpreter is unsupported");
    if (type == 7)
      bad("guest PT_TLS is not implemented");
    if (type == 0x6474e551 && (flags & 1))
      bad("executable guest stack is unsupported");
    if (type == 1) {
      if (filesz > memsz)
        bad("ELF LOAD file size exceeds memory size");
      r.range(offset, filesz);
      uint64_t end = add(address, memsz);
      if (align > 1 &&
          ((align & (align - 1)) || address % align != offset % align))
        bad("invalid ELF LOAD alignment");
      if ((flags & ~7u) || !(flags & 4) || (flags & 3) == 3)
        bad("unsupported ELF LOAD permissions");
      if (!memsz)
        continue;
      for (const auto &other : elf.segments) {
        if (address < add(other.address, other.memory_size) &&
            other.address < end)
          bad("overlapping ELF LOAD ranges");
        uint64_t begin_page = address & ~(page_size - 1);
        uint64_t end_page = add(end, page_size - 1) & ~(page_size - 1);
        uint64_t other_begin = other.address & ~(page_size - 1);
        uint64_t other_end =
            add(add(other.address, other.memory_size), page_size - 1) &
            ~(page_size - 1);
        if (begin_page < other_end && other_begin < end_page &&
            flags != other.flags)
          bad("incompatible LOAD permissions on host page");
      }
      elf.segments.push_back({offset, address, filesz, memsz, flags});
      low = std::min(low, address & ~(page_size - 1));
      high = std::max(high, add(end, page_size - 1) & ~(page_size - 1));
    } else if (type == 2) {
      if (has_dynamic || !filesz || filesz % 16 || filesz > 65536)
        bad("invalid ELF dynamic table");
      has_dynamic = true;
      dynamic_address = address;
      dynamic_size = filesz;
    } else if (type == 0x6474e550) {
      if (elf.eh_frame_header_size || filesz < 12 || filesz > (16u << 20))
        bad("invalid/duplicate ELF unwind header");
      r.range(offset, filesz);
      elf.eh_frame_header = address;
      elf.eh_frame_header_size = filesz;
    } else if (type == 0x6474e552) {
      if (has_relro)
        bad("multiple ELF RELRO ranges");
      has_relro = true;
      elf.relro = address;
      elf.relro_size = memsz;
    }
  }
  if (elf.segments.empty() || high <= low || high - low > kMaxImage)
    bad("ELF image span exceeds limit");
  elf.first_page = low;
  elf.span = high - low;
  uint64_t shoff = r.u(40, 8), shcount = r.u(60, 2);
  if (shoff && shcount) {
    if (r.u(58, 2) != 64 || shcount > 16384)
      bad("ELF section-header limit/stride");
    r.range(shoff, shcount * 64);
    std::vector<std::pair<uint64_t,uint64_t>> code_sections;
    for (uint64_t i = 0; i < shcount; ++i) {
      uint64_t p = shoff + i * 64, type = r.u(p + 4, 4), flags = r.u(p + 8, 8);
      uint64_t address = r.u(p + 16, 8), offset = r.u(p + 24, 8), size = r.u(p + 32, 8);
      if (!(flags & 2) || !size || type == 8) continue;
      if (file_address(elf, address, size) != offset)
        bad("ELF allocated section disagrees with LOAD mapping");
      if (flags & 4) code_sections.emplace_back(address, add(address,size));
      else elf.data_sections.push_back({offset,address,size,size,4});
    }
    std::sort(code_sections.begin(),code_sections.end());
    std::vector<std::pair<uint64_t,uint64_t>> merged;
    for(auto code:code_sections){if(!merged.empty()&&code.first<=merged.back().second)merged.back().second=std::max(merged.back().second,code.second);else merged.push_back(code);}
    for (const auto &data : elf.data_sections) {
      auto next=std::upper_bound(merged.begin(),merged.end(),data.address,[](uint64_t address,const auto &code){return address<code.first;});
      if((next!=merged.begin()&&std::prev(next)->second>data.address)||(next!=merged.end()&&next->first<add(data.address,data.file_size)))
        bad("ELF data/code sections overlap");
    }
  }
  if (!has_dynamic)
    bad("ELF dynamic table is missing");
  if (elf.eh_frame_header_size)
    (void)file_address(elf, elf.eh_frame_header, elf.eh_frame_header_size);
  if (elf.relro_size) {
    // Linkers may extend RELRO beyond the final LOAD byte to page alignment.
    // Its start must belong to writable data; padding cannot cover executable
    // code.
    uint64_t end = add(elf.relro, elf.relro_size);
    if (!memory_address(elf, elf.relro, 1, 2) || end > high)
      bad("ELF RELRO outside image/writable start");
    for (const auto &segment : elf.segments)
      if ((segment.flags & 1) &&
          elf.relro < add(segment.address, segment.memory_size) &&
          segment.address < end)
        bad("ELF RELRO overlaps executable LOAD");
  }
  uint64_t dynoff = file_address(elf, dynamic_address, dynamic_size);
  std::map<uint64_t, uint64_t> tags;
  std::vector<uint64_t> needed;
  bool terminated = false;
  for (uint64_t p = dynoff; p < dynoff + dynamic_size; p += 16) {
    uint64_t tag = r.u(p, 8), value = r.u(p + 8, 8);
    if (!tag) {
      terminated = true;
      break;
    }
    if (tag == 1) {
      needed.push_back(value);
      continue;
    }
    if (!tags.emplace(tag, value).second)
      bad("duplicate ELF dynamic tag");
  }
  if (!terminated)
    bad("unterminated ELF dynamic table");
  auto tag = [&](uint64_t key) { return tags.count(key) ? tags.at(key) : 0; };
  if (tag(22) || tags.count(22) || (tag(30) & 4))
    bad("ELF text relocations unsupported");
  if (tag(17) || tag(18) || tag(19) || tag(35) || tag(36) || tag(37) ||
      tag(0x6000000f) || tag(0x60000011))
    bad("ELF relocation encoding not implemented");
  uint64_t strsz = tag(10), strbase = file_address(elf, tag(5), strsz);
  if (!strsz || strsz > (64ull << 20) || r.u(strbase, 1))
    bad("invalid ELF string table");
  for (uint64_t name : needed) {
    if (elf.needed.size() >= 128)
      bad("ELF dependency limit");
    auto value = r.string(strbase, strsz, name);
    if (value.empty() || value.find('/') != std::string::npos)
      bad("invalid ELF dependency name");
    elf.needed.push_back(std::move(value));
  }
  if (tag(14))
    elf.soname = r.string(strbase, strsz, tag(14));
  if (!elf.soname.empty() && (elf.soname.find('/') != std::string::npos ||
                              elf.soname == "." || elf.soname == ".."))
    bad("invalid ELF SONAME");
  if (tags.count(15) || tags.count(29))
    bad("guest RPATH/RUNPATH is not implemented");
  if ((tag(30) & ~uint64_t(2 | 8)) || (tag(0x6ffffffb) & ~uint64_t(1 | 2 | 8)))
    bad("unsupported ELF dynamic flags");
  elf.symbolic = tags.count(16) || (tag(30) & 2);
  elf.global = tag(0x6ffffffb) & 2;
  uint64_t symbols = 0;
  if (tags.count(4)) {
    if (!tag(4))
      bad("invalid ELF SYSV hash address");
    uint64_t hash = file_address(elf, tag(4), 8);
    uint64_t buckets = r.u(hash, 4);
    symbols = r.u(hash + 4, 4);
    if (!buckets || buckets > kMaxSymbols || !symbols || symbols > kMaxSymbols)
      bad("ELF symbol/hash limit");
    file_address(elf, tag(4), 8 + 4 * (buckets + symbols));
  } else if (tag(0x6ffffef5)) {
    // GNU hash has no nchain count. Each nonempty bucket starts a contiguous
    // symbol chain whose final hash word has its low bit set.
    uint64_t base = tag(0x6ffffef5);
    uint64_t header = file_address(elf, base, 16);
    uint64_t buckets = r.u(header, 4), first = r.u(header + 4, 4);
    uint64_t bloom = r.u(header + 8, 4), shift = r.u(header + 12, 4);
    if (!buckets || buckets > kMaxSymbols || !first || first > kMaxSymbols ||
        !bloom || bloom > kMaxSymbols || (bloom & (bloom - 1)) || shift >= 32)
      bad("invalid ELF GNU hash header");
    uint64_t prefix = 16 + bloom * 8 + buckets * 4;
    uint64_t table = file_address(elf, base, prefix);
    uint64_t chains = add(base, prefix), visits = 0;
    symbols = first;
    for (uint64_t i = 0; i < buckets; ++i) {
      uint64_t index = r.u(table + 16 + bloom * 8 + i * 4, 4);
      if (!index)
        continue;
      if (index < first || index >= kMaxSymbols)
        bad("invalid ELF GNU hash bucket");
      for (;;) {
        if (index >= kMaxSymbols || ++visits > kMaxSymbols)
          bad("ELF GNU hash chain limit");
        uint64_t word = r.u(file_address(elf, add(chains, (index - first) * 4), 4), 4);
        symbols = std::max(symbols, ++index);
        if (word & 1)
          break;
      }
    }
  } else {
    bad("ELF symbol hash table is missing");
  }
  if (tag(11) != 24)
    bad("invalid ELF symbol entry size");
  uint64_t symbase = file_address(elf, tag(6), symbols * 24);
  uint64_t name_bytes = 0;
  for (uint64_t i = 0; i < symbols; ++i) {
    uint64_t p = symbase + i * 24;
    Symbol s{r.string(strbase, strsz, r.u(p, 4)),
             r.u(p + 8, 8),
             r.u(p + 16, 8),
             uint16_t(r.u(p + 6, 2)),
             uint8_t(r.u(p + 4, 1)),
             uint8_t(r.u(p + 5, 1)),
             {}};
    name_bytes += s.name.size();
    if (name_bytes > (64ull << 20))
      bad("ELF aggregate symbol-name limit");
    if ((s.info & 15) == 6)
      bad("ELF TLS symbols are not implemented");
    if (s.section >= 0xff00 && s.section != 0xfff1)
      bad("unsupported ELF symbol section");
    if ((s.info & 15) == 2 && s.section && s.section != 0xfff1 &&
        !memory_address(elf, s.address, std::max<uint64_t>(s.size, 4), 1))
      bad("ELF function symbol outside executable LOAD");
    if ((s.info & 15) == 10)
      bad("ELF IFUNC is not implemented");
    if (s.section != 0 && s.section != 0xfff1 &&
        !memory_address(elf, s.address, s.size))
      bad("ELF defined symbol outside LOAD");
    elf.symbols.push_back(std::move(s));
  }
  auto relocations = [&](uint64_t address, uint64_t size) {
    if (!size)
      return;
    if (size % 24 || size / 24 > kMaxRelocations - elf.relocations.size())
      bad("ELF relocation limit/entry size");
    uint64_t base = file_address(elf, address, size);
    for (uint64_t i = 0; i < size / 24; ++i) {
      uint64_t p = base + i * 24, dest = r.u(p, 8), info = r.u(p + 8, 8);
      uint32_t type = uint32_t(info), symbol = uint32_t(info >> 32);
      if (type != 1027 && type != 257 && type != 1025 && type != 1026)
        bad("unsupported AArch64 relocation");
      if (symbol >= symbols || (type == 1027 && symbol))
        bad("invalid ELF relocation symbol");
      if ((dest & 7) || !memory_address(elf, dest, 8, 2))
        bad("ELF relocation destination outside writable LOAD");
      uint64_t raw_addend = r.u(p + 16, 8);
      int64_t addend;
      // Preserve the signed two's-complement bit pattern without an
      // out-of-range cast.
      if (raw_addend <= INT64_MAX)
        addend = int64_t(raw_addend);
      else
        addend = -1 - int64_t(UINT64_MAX - raw_addend);
      elf.relocations.push_back({dest, addend, type, symbol});
    }
  };
  if (tag(8) && tag(9) != 24)
    bad("invalid ELF RELA entry size");
  relocations(tag(7), tag(8));
  if (tag(2) && tag(20) != 7)
    bad("ELF PLT requires RELA");
  relocations(tag(23), tag(2));
  std::vector<uint64_t> destinations;
  for (const auto &relocation : elf.relocations)
    destinations.push_back(relocation.address);
  std::sort(destinations.begin(), destinations.end());
  if (std::adjacent_find(destinations.begin(), destinations.end()) !=
      destinations.end())
    bad("duplicate ELF relocation destination");
  elf.init = tag(12);
  elf.init_array = tag(25);
  elf.init_array_size = tag(27);
  if (elf.init && !memory_address(elf, elf.init, 4, 1))
    bad("ELF init outside executable LOAD");
  if (elf.init_array_size &&
      (elf.init_array_size % 8 || elf.init_array_size > 65536 ||
       !memory_address(elf, elf.init_array, elf.init_array_size, 2)))
    bad("invalid ELF init array");
  elf.versioned = tags.count(0x6ffffff0) || tags.count(0x6ffffffe) ||
                  tags.count(0x6ffffffc);
  if (tags.count(0x6ffffff0) && !tag(0x6ffffff0))
    bad("ELF VERSYM table has a zero address");
  auto version_hash = [](const std::string &name) {
    uint32_t hash = 0;
    for (unsigned char c : name) {
      hash = (hash << 4) + c;
      uint32_t high = hash & 0xf0000000u;
      if (high)
        hash ^= high >> 24;
      hash &= ~high;
    }
    return hash;
  };
  std::map<uint16_t, Version> versions;
  uint64_t records = 0;
  auto version_record = [&](uint64_t address, uint64_t size) {
    if (++records > 65536)
      bad("ELF version record limit");
    return file_address(elf, address, size);
  };
  auto chain_next = [&](uint64_t current, uint64_t offset, uint64_t minimum,
                        bool last) {
    if (last) {
      if (offset)
        bad("ELF version chain exceeds declared count");
      return uint64_t(0);
    }
    if (offset < minimum)
      bad("truncated/non-forward ELF version chain");
    return add(current, offset);
  };
  uint64_t def = tag(0x6ffffffc), def_count = tag(0x6ffffffd);
  uint64_t need = tag(0x6ffffffe), need_count = tag(0x6fffffff);
  if (bool(def) != bool(def_count) || bool(need) != bool(need_count) ||
      def_count > 4096 || need_count > 128)
    bad("invalid ELF version table/count");
  for (uint64_t i = 0; i < def_count; ++i) {
    uint64_t p = version_record(def, 20);
    uint16_t flags = r.u(p + 2, 2), index = r.u(p + 4, 2),
             count = r.u(p + 6, 2);
    if (r.u(p, 2) != 1 || !index || index >= 0x8000 || !count || count > 4096 ||
        (flags & ~3u))
      bad("invalid ELF version definition");
    uint64_t auxiliary = add(def, r.u(p + 12, 4));
    if (r.u(p + 12, 4) < 20)
      bad("invalid ELF version definition auxiliary");
    Version version;
    version.index = index;
    version.hash = r.u(p + 8, 4);
    for (unsigned j = 0; j < count; ++j) {
      uint64_t a = version_record(auxiliary, 8);
      auto name = r.string(strbase, strsz, r.u(a, 4));
      if (name.empty())
        bad("empty ELF version name");
      if (!j)
        version.name = std::move(name);
      auxiliary = chain_next(auxiliary, r.u(a + 4, 4), 8, j + 1 == count);
    }
    if (version_hash(version.name) != version.hash ||
        !versions.emplace(index, version).second)
      bad("invalid/duplicate ELF version definition hash/index");
    elf.definitions.push_back(version);
    def = chain_next(def, r.u(p + 16, 4), 20, i + 1 == def_count);
  }
  for (uint64_t i = 0; i < need_count; ++i) {
    uint64_t p = version_record(need, 16);
    uint16_t count = r.u(p + 2, 2);
    if (r.u(p, 2) != 1 || !count || count > 4096)
      bad("invalid ELF version requirement");
    auto library = r.string(strbase, strsz, r.u(p + 4, 4));
    if (std::find(elf.needed.begin(), elf.needed.end(), library) ==
        elf.needed.end())
      bad("ELF version requirement is not a dependency");
    if (r.u(p + 8, 4) < 16)
      bad("invalid ELF version requirement auxiliary");
    uint64_t auxiliary = add(need, r.u(p + 8, 4));
    for (unsigned j = 0; j < count; ++j) {
      uint64_t a = version_record(auxiliary, 16);
      Version version;
      version.index = r.u(a + 6, 2) & 0x7fff;
      version.hash = r.u(a, 4);
      version.library = library;
      version.name = r.string(strbase, strsz, r.u(a + 8, 4));
      if (version.index < 2 || (r.u(a + 4, 2) & ~2u) || version.name.empty() ||
          version_hash(version.name) != version.hash ||
          !versions.emplace(version.index, version).second)
        bad("invalid/duplicate ELF version requirement hash/index");
      elf.requirements.push_back(version);
      auxiliary = chain_next(auxiliary, r.u(a + 12, 4), 16, j + 1 == count);
    }
    need = chain_next(need, r.u(p + 12, 4), 16, i + 1 == need_count);
  }
  if (tag(0x6ffffff0)) {
    uint64_t base = file_address(elf, tag(0x6ffffff0), symbols * 2);
    uint64_t version_names = 0;
    for (uint64_t i = 0; i < symbols; ++i) {
      uint16_t raw = r.u(base + i * 2, 2), index = raw & 0x7fff;
      Version version;
      version.index = index;
      if (index > 1) {
        auto found = versions.find(index);
        if (found == versions.end())
          bad("ELF symbol has an unknown version index");
        version = found->second;
        if (bool(elf.symbols[i].section) != version.library.empty())
          bad("ELF symbol version definition/requirement mismatch");
      }
      version_names += version.name.size() + version.library.size();
      if (version_names > (64ull << 20))
        bad("ELF aggregate version-name limit");
      version.hidden = raw & 0x8000;
      elf.symbols[i].version = std::move(version);
    }
  } else if (!versions.empty())
    bad("ELF version definitions/requirements lack VERSYM");
  return elf;
}
} // namespace shiraz::guest
