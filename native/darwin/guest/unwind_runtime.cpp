// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/unwind_runtime.hpp"
#include "shiraz/darwin/guest/unwind.hpp"
#include <algorithm>
#include <iterator>
#include <map>
#include <set>

// Darwin libunwind takes one FDE, rather than the complete ELF section accepted
// by libgcc. These are direct host runtime dependencies, never guest lookup.
// https://llvm.org/doxygen/RTDyldMemoryManager_8cpp_source.html
extern "C" void __register_frame(const void *);
extern "C" void __deregister_frame(const void *);

namespace shiraz::guest {
namespace {
struct Reader {
  const Elf &elf;
  const std::vector<uint8_t> &bytes;
  uint64_t u(uint64_t address, unsigned width) const {
    for (const auto &segment : elf.segments) {
      if (address < segment.address || address - segment.address > segment.file_size ||
          width > segment.file_size - (address - segment.address)) continue;
      uint64_t offset = segment.offset + address - segment.address;
      if (offset > bytes.size() || width > bytes.size() - offset)
        throw ElfError("unwind registration source bounds");
      uint64_t value = 0;
      for (unsigned i = 0; i < width; ++i)
        value |= uint64_t(bytes[size_t(offset + i)]) << (8 * i);
      return value;
    }
    throw ElfError("unwind registration record outside file-backed LOAD");
  }
  uint64_t relative(uint64_t base, int64_t delta) const {
    if (delta >= 0) {
      if (uint64_t(delta) > UINT64_MAX - base)
        throw ElfError("unwind registration address overflow");
      return base + uint64_t(delta);
    }
    uint64_t magnitude = uint64_t(-(delta + 1)) + 1;
    if (magnitude > base) throw ElfError("unwind registration address underflow");
    return base - magnitude;
  }
  uint64_t take(uint64_t &cursor, uint64_t end, unsigned width) const {
    if (cursor > end || width > end - cursor)
      throw ElfError("truncated unwind runtime operand");
    auto value = u(cursor, width); cursor += width; return value;
  }
  uint64_t leb(uint64_t &cursor, uint64_t end, bool sign = false) const {
    uint64_t value = 0;
    for (unsigned i = 0; i < 10; ++i) {
      uint8_t byte = uint8_t(take(cursor, end, 1));
      if (i == 9 && (sign ? ((byte & 127) != 0 && (byte & 127) != 127)
                         : (byte & 127) > 1))
        throw ElfError("unwind runtime LEB overflow");
      value |= uint64_t(byte & 127) << (i * 7);
      if (!(byte & 128)) {
        unsigned bits = (i + 1) * 7;
        if (sign && (byte & 64) && bits < 64) value |= UINT64_MAX << bits;
        return value;
      }
    }
    throw ElfError("unbounded unwind runtime LEB");
  }
  void reg(uint64_t value, bool cfa = false) const {
    if (value > 31 && (cfa || value < 64 || value > 95))
      throw ElfError("unsupported AArch64 CFI register");
  }
  void instructions(uint64_t cursor, uint64_t end, uint64_t code_align,
                    int64_t data_align, uint64_t extent) const {
    uint64_t pc = 0;
    unsigned depth = 0, count = 0;
    auto advance = [&](uint64_t value) {
      if (value > (extent - pc) / code_align)
        throw ElfError("CFI advance outside function extent");
      pc += value * code_align;
    };
    auto offset = [&](bool sign, bool scaled) {
      uint64_t raw = leb(cursor, end, sign);
      int64_t value = int64_t(raw);
      // Darwin stores CFA offsets in int32_t; conservatively bound every
      // offset and multiplication before its interpreter sees the operands.
      if ((!sign && raw > (1u << 24)) || value < -(1 << 24) || value > (1 << 24) ||
          (scaled && (value * data_align < INT32_MIN || value * data_align > INT32_MAX)))
        throw ElfError("CFI offset exceeds bounded runtime range");
    };
    while (cursor < end) {
      if (++count > (1u << 20)) throw ElfError("CFI instruction count limit");
      uint8_t opcode = uint8_t(take(cursor, end, 1));
      if ((opcode & 0xc0) == 0x40) { advance(opcode & 63); continue; }
      if ((opcode & 0xc0) == 0x80) {
        reg(opcode & 63); offset(false, true); continue;
      }
      if ((opcode & 0xc0) == 0xc0) { reg(opcode & 63); continue; }
      switch (opcode) {
      case 0: break;
      case 2: advance(take(cursor, end, 1)); break;
      case 3: advance(take(cursor, end, 2)); break;
      case 4: advance(take(cursor, end, 4)); break;
      case 5: case 17: case 20: case 21:
        reg(leb(cursor, end)); offset(opcode == 17 || opcode == 21, true); break;
      case 6: case 7: case 8: reg(leb(cursor, end)); break;
      case 9: reg(leb(cursor, end)); reg(leb(cursor, end)); break;
      case 10:
        if (++depth > 64) throw ElfError("CFI remember-state depth limit");
        break;
      case 11:
        if (!depth) throw ElfError("CFI restore-state underflow");
        --depth; break;
      case 12: case 18:
        reg(leb(cursor, end), true); offset(opcode == 18, opcode == 18); break;
      case 13: reg(leb(cursor, end), true); break;
      case 14: case 19: offset(opcode == 19, opcode == 19); break;
      case 0x2d: break; // AArch64 negate_ra_state (PAC state).
      case 0x2e: offset(false, false); break; // GNU_args_size.
      default:
        // Expressions and set_loc need additional address/expression semantics;
        // never pass unsupported streams to Darwin's fatal-error parser.
        throw ElfError("unsupported CFI opcode/expression");
      }
    }
    if (depth) throw ElfError("unbalanced CFI remember-state stack");
  }
  struct Cie {
    uint64_t code_align;
    int64_t data_align;
    bool augmented;
    uint8_t lsda = 0xff;
  };
  void encoded_pointer(uint64_t &cursor, uint64_t end, uint8_t encoding,
                       bool personality) const {
    // Common Android arm64 signed PC-relative pointers; personality pointers
    // may be indirect through a relocated GOT slot. LSDA must be direct.
    if (encoding != 0x1b && encoding != 0x1c &&
        !(personality && (encoding == 0x9b || encoding == 0x9c)))
      throw ElfError("unsupported runtime personality/LSDA encoding");
    uint64_t address = cursor;
    unsigned width = (encoding & 15) == 12 ? 8 : 4;
    int64_t delta = width == 8 ? int64_t(take(cursor, end, 8))
                               : int32_t(take(cursor, end, 4));
    if (!delta && !personality) return;
    uint64_t target = relative(address, delta);
    (void)u(target, (encoding & 0x80) ? 8 : 1);
  }
  Cie cie(uint64_t address) const {
    uint64_t cursor = address + 8, end = address + 4 + u(address, 4);
    uint8_t version = uint8_t(take(cursor, end, 1));
    std::string augmentation;
    for (;;) {
      char byte = char(take(cursor, end, 1));
      if (!byte) break;
      if (augmentation.size() >= 32) throw ElfError("runtime CIE augmentation limit");
      augmentation += byte;
    }
    Cie result{leb(cursor, end), int64_t(leb(cursor, end, true)), !augmentation.empty()};
    if (!result.code_align || result.code_align > 16 ||
        result.data_align >= 0 || result.data_align < -1024)
      throw ElfError("unsupported runtime CIE alignment");
    uint64_t return_reg = version == 1 ? take(cursor, end, 1) : leb(cursor, end);
    if (return_reg != 30) throw ElfError("unsupported runtime CIE return register");
    if (result.augmented) {
      if (augmentation[0] != 'z') throw ElfError("unsupported runtime CIE augmentation");
      uint64_t length = leb(cursor, end);
      if (length > end - cursor) throw ElfError("truncated runtime CIE augmentation");
      uint64_t finish = cursor + length;
      unsigned seen = 0;
      for (size_t i = 1; i < augmentation.size(); ++i) {
        char name = augmentation[i];
        unsigned bit;
        switch (name) {
        case 'R': bit = 1; break;
        case 'L': bit = 2; break;
        case 'P': bit = 4; break;
        case 'B': bit = 8; break;
        case 'S': bit = 16; break;
        default: throw ElfError("unsupported runtime CIE augmentation field");
        }
        if (seen & bit) throw ElfError("duplicate runtime CIE augmentation field");
        seen |= bit;
        if (name == 'B' || name == 'S') continue;
        uint8_t encoding = uint8_t(take(cursor, finish, 1));
        if (name == 'R' && encoding != 0x1b)
          throw ElfError("unsupported runtime FDE encoding");
        if (name == 'L') {
          if (encoding != 0xff && encoding != 0x1b && encoding != 0x1c)
            throw ElfError("unsupported runtime LSDA encoding");
          result.lsda = encoding;
        }
        if (name == 'P') encoded_pointer(cursor, finish, encoding, true);
      }
      if (!(seen & 1) || cursor != finish)
        throw ElfError("runtime CIE augmentation length/encoding mismatch");
    }
    instructions(cursor, end, result.code_align, result.data_align, 0);
    return result;
  }
  void fde(uint64_t address, const Cie &cie) const {
    uint64_t end = address + 4 + u(address, 4), cursor = address + 16;
    uint64_t extent = u(address + 12, 4);
    if (cie.augmented) {
      uint64_t length = leb(cursor, end);
      if (length > end - cursor) throw ElfError("truncated runtime FDE augmentation");
      uint64_t finish = cursor + length;
      if (cie.lsda != 0xff) encoded_pointer(cursor, finish, cie.lsda, false);
      if (cursor != finish) throw ElfError("runtime FDE augmentation length mismatch");
    }
    instructions(cursor, end, cie.code_align, cie.data_align, extent);
  }
};
} // namespace
UnwindRegistration::UnwindRegistration(const Elf &elf,
                                       const std::vector<uint8_t> &bytes)
    : first_page_(elf.first_page), span_(elf.span) {
  if (!span_ || span_ > (512ull << 20))
    throw ElfError("invalid unwind registration image span");
  for (const auto &segment : elf.segments)
    if (segment.offset > bytes.size() || segment.file_size > bytes.size() - segment.offset ||
        segment.address < first_page_ || segment.address - first_page_ > span_ ||
        segment.file_size > span_ - (segment.address - first_page_))
      throw ElfError("invalid unwind registration segment bounds");
  // The common parser validates header, sorted keys, CIE augmentations, FDE
  // backreferences and matching executable extents before libunwind sees them.
  auto functions = unwind_function_entries(elf, bytes);
  if (functions.empty()) return;
  Reader reader{elf, bytes};
  std::set<uint64_t> seen;
  std::map<uint64_t, Reader::Cie> cies;
  std::vector<std::pair<uint64_t, uint64_t>> records;
  records.emplace_back(elf.eh_frame_header,
                       elf.eh_frame_header + elf.eh_frame_header_size);
  for (size_t i = 0; i < functions.size(); ++i) {
    uint64_t fde = reader.relative(elf.eh_frame_header,
        int32_t(reader.u(elf.eh_frame_header + 16 + i * 8, 4)));
    if (!seen.insert(fde).second) continue;
    uint64_t cie = fde + 4 - reader.u(fde + 4, 4);
    if (!cies.count(cie)) cies.emplace(cie, reader.cie(cie));
    reader.fde(fde, cies.at(cie));
    records.emplace_back(fde, fde + 4 + reader.u(fde, 4));
    records.emplace_back(cie, cie + 4 + reader.u(cie, 4));
    frames_.push_back(fde);
  }
  // Read-only mapped metadata preserves relative encodings under the common
  // image bias. Dynamic relocations into these records would invalidate the
  // original-byte validation, so reject them explicitly. Personality indirect
  // pointers may still reside in ordinary relocated GOT slots outside records.
  std::sort(records.begin(), records.end());
  std::vector<std::pair<uint64_t, uint64_t>> merged;
  for (const auto &record : records) {
    if (!merged.empty() && record.first <= merged.back().second)
      merged.back().second = std::max(merged.back().second, record.second);
    else merged.push_back(record);
  }
  for (const auto &record : merged) {
    bool readonly = false;
    for (const auto &segment : elf.segments)
      if (!(segment.flags & 2) && (segment.flags & 4) &&
          record.first >= segment.address &&
          record.first - segment.address <= segment.file_size &&
          record.second - record.first <=
              segment.file_size - (record.first - segment.address))
        readonly = true;
    if (!readonly) throw ElfError("unwind registration requires readonly file-backed metadata");
  }
  for (const auto &relocation : elf.relocations) {
    if (relocation.address > UINT64_MAX - 8)
      throw ElfError("unwind registration relocation overflow");
    auto after = std::lower_bound(merged.begin(), merged.end(), relocation.address + 8,
        [](const auto &record, uint64_t end) { return record.first < end; });
    if (after != merged.begin() && std::prev(after)->second > relocation.address)
      throw ElfError("dynamic relocation overlaps unwind metadata");
  }
}
UnwindRegistration::~UnwindRegistration() { reset(); }
void UnwindRegistration::activate(void *mapping, uint64_t mapping_size) {
  if (active_) throw ElfError("unwind frames already registered");
  if (!mapping || mapping_size < span_ ||
      mapping_size > UINTPTR_MAX - reinterpret_cast<uintptr_t>(mapping))
    throw ElfError("invalid unwind registration mapping");
  for (uint64_t frame : frames_)
    if (frame < first_page_ || frame - first_page_ >= span_)
      throw ElfError("unwind frame outside image mapping");
  mapping_ = static_cast<uint8_t *>(mapping);
  for (uint64_t frame : frames_)
    __register_frame(mapping_ + (frame - first_page_));
  active_ = true;
}
void UnwindRegistration::reset() noexcept {
  if (!active_) return;
  for (auto i = frames_.rbegin(); i != frames_.rend(); ++i)
    __deregister_frame(mapping_ + (*i - first_page_));
  active_ = false;
  mapping_ = nullptr;
}
} // namespace shiraz::guest
