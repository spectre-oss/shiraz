// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/unwind.hpp"
#include <map>
namespace shiraz::guest {
namespace {
struct Reader {
  const Elf &elf;
  const std::vector<uint8_t> &bytes;
  size_t range(uint64_t address, uint64_t width) const {
    for (const auto &segment : elf.segments)
      if (address >= segment.address && address - segment.address <= segment.file_size &&
          width <= segment.file_size - (address - segment.address)) {
        if (segment.offset > bytes.size() || segment.file_size > bytes.size() - segment.offset)
          throw ElfError("invalid unwind source segment");
        return size_t(segment.offset + address - segment.address);
      }
    throw ElfError("unwind metadata outside file-backed LOAD");
  }
  uint64_t u(uint64_t address, unsigned width) const {
    size_t offset = range(address, width);
    uint64_t value = 0;
    for (unsigned i = 0; i < width; ++i) value |= uint64_t(bytes[offset + i]) << (8 * i);
    return value;
  }
  uint64_t relative(uint64_t base, int64_t delta) const {
    if (delta >= 0) {
      if (uint64_t(delta) > UINT64_MAX - base) throw ElfError("unwind address overflow");
      return base + uint64_t(delta);
    }
    uint64_t magnitude = uint64_t(-(delta + 1)) + 1;
    if (magnitude > base) throw ElfError("unwind address underflow");
    return base - magnitude;
  }
  uint64_t leb(uint64_t &cursor, uint64_t end, bool sign) const {
    uint64_t value = 0;
    for (unsigned i = 0; i < 10; ++i) {
      if (cursor >= end) throw ElfError("truncated unwind LEB");
      uint8_t byte = uint8_t(u(cursor++, 1));
      if (i == 9 && (sign ? ((byte & 0x7f) != 0 && (byte & 0x7f) != 0x7f)
                         : (byte & 0x7f) > 1))
        throw ElfError("unwind LEB overflow");
      value |= uint64_t(byte & 0x7f) << (i * 7);
      if (!(byte & 0x80)) {
        unsigned bits = (i + 1) * 7;
        if (sign && (byte & 0x40) && bits < 64) value |= UINT64_MAX << bits;
        return value;
      }
    }
    throw ElfError("unbounded unwind LEB");
  }
  void skip_pointer(uint64_t &cursor, uint64_t end, uint8_t encoding) const {
    unsigned width;
    switch (encoding & 15) {
    case 0: case 4: case 12: width = 8; break;
    case 2: case 10: width = 2; break;
    case 3: case 11: width = 4; break;
    case 1: leb(cursor, end, false); return;
    case 9: leb(cursor, end, true); return;
    default: throw ElfError("unsupported unwind pointer format");
    }
    if (cursor > end || width > end - cursor) throw ElfError("truncated unwind pointer");
    (void)u(cursor, width); cursor += width;
  }
  uint8_t cie(uint64_t address) const {
    uint64_t length = u(address, 4);
    if (length < 8 || length > 65536 || address > UINT64_MAX - 4 - length || u(address + 4, 4))
      throw ElfError("invalid unwind CIE");
    (void)range(address, length + 4);
    uint64_t cursor = address + 8, end = address + 4 + length;
    unsigned version = unsigned(u(cursor++, 1));
    if (version != 1 && version != 3) throw ElfError("unsupported unwind CIE version");
    std::string augmentation;
    for (;;) {
      if (cursor >= end || augmentation.size() >= 32) throw ElfError("unbounded unwind augmentation");
      char c = char(u(cursor++, 1));
      if (!c) break;
      augmentation += c;
    }
    (void)leb(cursor, end, false); (void)leb(cursor, end, true);
    uint64_t return_register;
    if (version == 1) {
      if (cursor >= end) throw ElfError("truncated CIE return register");
      return_register = u(cursor++, 1);
    } else return_register = leb(cursor, end, false);
    if (return_register != 30) throw ElfError("unsupported AArch64 CIE return register");
    uint8_t pointer_encoding = 0;
    if (!augmentation.empty()) {
      if (augmentation[0] != 'z') throw ElfError("unsupported unwind augmentation");
      uint64_t size = leb(cursor, end, false);
      if (cursor > end || size > end - cursor) throw ElfError("truncated CIE augmentation");
      uint64_t finish = cursor + size;
      for (size_t i = 1; i < augmentation.size(); ++i) {
        char c = augmentation[i];
        if (c == 'B' || c == 'S') continue;
        if (cursor >= finish) throw ElfError("missing CIE augmentation field");
        uint8_t value = uint8_t(u(cursor++, 1));
        if (c == 'R') pointer_encoding = value;
        else if (c == 'P') skip_pointer(cursor, finish, value);
        else if (c != 'L') throw ElfError("unknown CIE augmentation");
      }
      if (cursor != finish) throw ElfError("extra CIE augmentation data");
    }
    if (pointer_encoding != 0x1b) throw ElfError("unsupported AArch64 FDE pointer encoding");
    return pointer_encoding;
  }
};
} // namespace
std::vector<uint64_t> unwind_function_entries(const Elf &elf, const std::vector<uint8_t> &bytes) {
  std::vector<uint64_t> entries;
  if (!elf.eh_frame_header_size) return entries;
  Reader r{elf, bytes};
  uint64_t header = elf.eh_frame_header;
  if (r.u(header, 4) != 0x3b031b01) throw ElfError("unsupported AArch64 unwind header encoding");
  uint64_t frame = r.relative(header + 4, int32_t(r.u(header + 4, 4)));
  (void)r.u(frame, 4);
  uint64_t count = r.u(header + 8, 4);
  if (count > (1u << 20) || count > (elf.eh_frame_header_size - 12) / 8)
    throw ElfError("unwind function-table bounds");
  std::map<uint64_t, uint8_t> cies;
  uint64_t previous = 0;
  for (uint64_t i = 0; i < count; ++i) {
    uint64_t record = header + 12 + i * 8;
    uint64_t function = r.relative(header, int32_t(r.u(record, 4)));
    uint64_t fde = r.relative(header, int32_t(r.u(record + 4, 4)));
    if (i && function < previous) throw ElfError("unsorted unwind function table");
    previous = function;
    uint64_t length = r.u(fde, 4), back = r.u(fde + 4, 4);
    if (length < 12 || length > (1u << 20) || fde > UINT64_MAX - 4 - length || !back || back > fde + 4)
      throw ElfError("invalid unwind FDE");
    (void)r.range(fde, length + 4);
    uint64_t cie = fde + 4 - back;
    if (cie < frame || fde < frame || cie >= fde || fde - cie < 4 || r.u(cie, 4) > fde - cie - 4)
      throw ElfError("unwind CIE/FDE extent mismatch");
    if (!cies.count(cie)) cies.emplace(cie, r.cie(cie));
    uint64_t begin = r.relative(fde + 8, int32_t(r.u(fde + 8, 4)));
    int32_t size = int32_t(r.u(fde + 12, 4));
    if (begin != function || (begin & 3) || size <= 0) throw ElfError("unwind FDE/header function mismatch");
    bool executable = false;
    for (const auto &segment : elf.segments)
      if ((segment.flags & 1) && begin >= segment.address && begin - segment.address <= segment.file_size &&
          uint64_t(size) <= segment.file_size - (begin - segment.address)) executable = true;
    if (!executable) throw ElfError("unwind function outside executable file bytes");
    entries.push_back(function);
  }
  return entries;
}
} // namespace shiraz::guest
