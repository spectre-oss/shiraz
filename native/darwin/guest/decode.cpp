// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/decode.hpp"
#include "shiraz/darwin/guest/unwind.hpp"
#include <algorithm>
#include <array>
#include <capstone/capstone.h>
#include <cstring>
#include <map>
#include <memory>
#include <sstream>

namespace shiraz::guest {
namespace {
[[noreturn]] void fail(const char *reason, uint64_t address) {
  std::ostringstream text;
  text << reason << " at guest 0x" << std::hex << address;
  throw ElfError(text.str());
}
bool tpidr(uint32_t word) {
  return (word & 0xffffffe0u) == 0xd53bd040u ||
         (word & 0xffffffe0u) == 0xd51bd040u;
}
struct Decoder {
  csh handle{};
  cs_insn *instruction = nullptr;
  Decoder() {
    if (cs_open(CS_ARCH_ARM64, CS_MODE_ARM, &handle) != CS_ERR_OK)
      throw ElfError("cannot create AArch64 decoder");
    if (cs_option(handle, CS_OPT_DETAIL, CS_OPT_ON) != CS_ERR_OK ||
        !(instruction = cs_malloc(handle))) {
      cs_close(&handle);
      throw ElfError("cannot allocate detailed AArch64 decoder");
    }
  }
  ~Decoder() { cs_free(instruction, 1); cs_close(&handle); }
};
} // namespace
ThreadPointerAnalysis analyze_thread_pointers(const Elf &elf,
                                            const std::vector<uint8_t> &bytes) {
  if (!elf.span || elf.span > (512ull << 20) || (elf.first_page & 3))
    throw ElfError("invalid ELF decoder span");
  for (const auto &segment : elf.segments)
    if (segment.offset > bytes.size() || segment.file_size > bytes.size() - segment.offset ||
        segment.address < elf.first_page || segment.address - elf.first_page > elf.span ||
        segment.file_size > elf.span - (segment.address - elf.first_page))
      throw ElfError("invalid ELF decoder segment");
  auto code = [&](uint64_t address) -> const uint8_t * {
    if (address & 3) return nullptr;
    for (const auto &segment : elf.segments)
      if ((segment.flags & 1) && address >= segment.address &&
          address - segment.address <= segment.file_size &&
          4 <= segment.file_size - (address - segment.address))
        return bytes.data() + segment.offset + address - segment.address;
    return nullptr;
  };
  std::vector<ThreadPointerSite> words;
  for (const auto &segment : elf.segments) {
    if (!(segment.flags & 1) || segment.file_size < 4) continue;
    uint64_t begin = (segment.address + 3) & ~uint64_t(3);
    for (uint64_t address = begin; address - segment.address + 4 <= segment.file_size; address += 4) {
      uint32_t word;
      std::memcpy(&word, code(address), 4);
      if (tpidr(word) || word == 0xd4000001u) {
        if (words.size() >= 131072) throw ElfError("guest TPIDR site limit");
        words.push_back({address, word});
      }
    }
  }
  ThreadPointerAnalysis result;
  if (words.empty()) return result;
  std::vector<std::pair<uint64_t, uint64_t>> objects;
  for (const auto &section : elf.data_sections)
    objects.emplace_back(section.address, section.address + section.file_size);
  for (const auto &symbol : elf.symbols)
    if (symbol.section && symbol.section != 0xfff1 && (symbol.info & 15) == 1 && symbol.size) {
      if (symbol.address > UINT64_MAX - symbol.size) throw ElfError("guest object extent overflow");
      objects.emplace_back(symbol.address, symbol.address + symbol.size);
    }
  std::sort(objects.begin(), objects.end());
  std::vector<std::pair<uint64_t, uint64_t>> merged;
  for (auto object : objects) {
    if (!merged.empty() && object.first <= merged.back().second)
      merged.back().second = std::max(merged.back().second, object.second);
    else merged.push_back(object);
  }
  auto data = [&](uint64_t address, bool overlap) {
    auto it = std::upper_bound(merged.begin(), merged.end(), address,
        [](uint64_t value, const auto &entry) { return value < entry.first; });
    if (it != merged.begin()) {
      auto previous = std::prev(it);
      if (overlap ? previous->second > address : previous->second >= address + 4) return true;
    }
    return overlap && it != merged.end() && it->first < address + 4;
  };
  // One bit per aligned instruction for each set, bounded by parsed image span.
  size_t mask_size = size_t((elf.span / 4 + 7) / 8);
  std::vector<uint8_t> visited(mask_size), queued(mask_size);
  auto test = [&](const auto &mask, uint64_t address) {
    uint64_t index = (address - elf.first_page) / 4;
    return bool(mask.at(size_t(index / 8)) & (1u << (index % 8)));
  };
  auto set = [&](auto &mask, uint64_t address) {
    uint64_t index = (address - elf.first_page) / 4;
    mask.at(size_t(index / 8)) |= uint8_t(1u << (index % 8));
  };
  std::vector<uint64_t> pending;
  auto enqueue = [&](uint64_t address) {
    if (!code(address)) fail("ELF code target outside executable file bytes", address);
    if (test(visited, address) || test(queued, address)) return;
    if (pending.size() >= (1u << 20)) throw ElfError("ELF code worklist limit");
    set(queued, address);
    pending.push_back(address);
  };
  // Stripped JNI libraries can omit FDEs for RegisterNatives callbacks. Only
  // accept their function pointers after a reachable call through JNIEnv's
  // RegisterNatives slot, with a constant table and count in x2/w3. Ordinary
  // relocated pointers into text remain insufficient code provenance.
  std::map<uint64_t, const Relocation *> relocations;
  for (const auto &relocation : elf.relocations)
    relocations.emplace(relocation.address, &relocation);
  auto file = [&](uint64_t address, uint64_t width, bool nonexec) -> const uint8_t * {
    for (const auto &segment : elf.segments)
      if ((segment.flags & 4) && (!nonexec || !(segment.flags & 1)) && address >= segment.address &&
          address - segment.address <= segment.file_size &&
          width <= segment.file_size - (address - segment.address))
        return bytes.data() + segment.offset + address - segment.address;
    return nullptr;
  };
  auto relocated = [&](uint64_t address) {
    auto found = relocations.find(address);
    if (found == relocations.end()) fail("JNI table pointer lacks relocation", address);
    const auto &relocation = *found->second;
    uint64_t base = 0;
    if (relocation.type != 1027) {
      if ((relocation.type != 257 && relocation.type != 1025) ||
          relocation.symbol >= elf.symbols.size() || !elf.symbols[relocation.symbol].section)
        fail("JNI table pointer lacks local definition", address);
      base = elf.symbols[relocation.symbol].address;
    }
    if (relocation.addend >= 0) {
      if (uint64_t(relocation.addend) > UINT64_MAX - base) fail("JNI table pointer overflow", address);
      return base + uint64_t(relocation.addend);
    }
    uint64_t magnitude = uint64_t(-(relocation.addend + 1)) + 1;
    if (magnitude > base) fail("JNI table pointer underflow", address);
    return base - magnitude;
  };
  auto string = [&](uint64_t address) {
    std::string value;
    for (unsigned i = 0; i < 512; ++i) {
      if (address > UINT64_MAX - i) fail("JNI descriptor address overflow", address);
      // Older Android linkers combine read-only strings and code in RX LOADs.
      // Descriptor bytes are data by their validated JNI table reference.
      const auto *p = file(address + i, 1, false);
      if (!p) fail("JNI descriptor outside data", address);
      if (!*p) return value;
      value += char(*p);
    }
    fail("unbounded JNI descriptor", address);
  };
  auto descriptor = [](const std::string &text) {
    size_t cursor = 0;
    auto type = [&](bool result) {
      unsigned dimensions = 0;
      while (cursor < text.size() && text[cursor] == '[') { ++cursor; ++dimensions; }
      if (dimensions > 255 || cursor == text.size()) return false;
      char c = text[cursor++];
      if (c == 'V') return result && !dimensions;
      if (std::strchr("ZBCSIJFD", c)) return true;
      if (c != 'L') return false;
      size_t begin = cursor;
      while (cursor < text.size() && text[cursor] != ';') {
        unsigned char ch = text[cursor++];
        if (ch <= 32 || ch == '.' || ch == '[' || ch == '(' || ch == ')') return false;
      }
      return cursor > begin && cursor < text.size() && text[cursor++] == ';';
    };
    if (text.empty() || text[cursor++] != '(') return false;
    while (cursor < text.size() && text[cursor] != ')') if (!type(false)) return false;
    if (cursor == text.size()) return false;
    ++cursor;
    return type(true) && cursor == text.size();
  };
  size_t jni_methods = 0;
  std::map<uint64_t, uint64_t> jni_tables;
  auto jni_table = [&](uint64_t table, uint64_t count) {
    if (!count || count > 4096 || (table & 7) || !file(table, count * 24, true))
      fail("JNI table bounds", table);
    auto previous = jni_tables.find(table);
    if (previous != jni_tables.end()) {
      if (previous->second != count) fail("JNI table conflicting counts", table);
      return;
    }
    if (jni_methods + count > 16384) fail("JNI method provenance limit", table);
    std::vector<uint64_t> targets;
    for (uint64_t i = 0; i < count; ++i) {
      uint64_t entry = table + i * 24;
      auto name = string(relocated(entry));
      auto signature = string(relocated(entry + 8));
      if (name.empty() || name.find_first_of(".;[/<>()") != std::string::npos ||
          std::any_of(name.begin(), name.end(), [](unsigned char c) { return c <= 32; }) ||
          !descriptor(signature)) fail("invalid JNI method descriptor", entry);
      uint64_t target = relocated(entry + 16);
      if (!code(target) || data(target, true)) fail("JNI callback outside code", target);
      targets.push_back(target);
    }
    jni_tables.emplace(table, count);
    jni_methods += count;
    for (uint64_t target : targets) enqueue(target);
  };
  auto register_index = [](unsigned reg) {
    if (reg >= ARM64_REG_X0 && reg <= ARM64_REG_X28) return int(reg - ARM64_REG_X0);
    if (reg >= ARM64_REG_W0 && reg <= ARM64_REG_W30) return int(reg - ARM64_REG_W0);
    if (reg == ARM64_REG_FP) return 29;
    if (reg == ARM64_REG_LR) return 30;
    return -1;
  };
  struct Value { uint64_t value = 0; bool constant = false, vtable = false, registration = false; };
  for (const auto &symbol : elf.symbols)
    if (symbol.section && symbol.section != 0xfff1 && (symbol.info & 15) == 2 && code(symbol.address))
      enqueue(symbol.address);
  for (uint64_t function : unwind_function_entries(elf, bytes)) enqueue(function);
  if (elf.init) enqueue(elf.init);
  for (const auto &relocation : elf.relocations) {
    if (relocation.address < elf.init_array ||
        relocation.address - elf.init_array >= elf.init_array_size) continue;
    uint64_t target = 0;
    if (relocation.type == 1027) target = uint64_t(relocation.addend);
    else if (relocation.symbol < elf.symbols.size() && elf.symbols[relocation.symbol].section)
      target = elf.symbols[relocation.symbol].address + uint64_t(relocation.addend);
    if (target && code(target)) enqueue(target);
  }
  Decoder decoder;
  while (!pending.empty()) {
    uint64_t address = pending.back(); pending.pop_back();
    std::array<Value, 31> values{};
    for (;;) {
      const uint8_t *input = code(address);
      if (!input) fail("ELF code falls outside executable file bytes", address);
      if (test(visited, address)) break;
      if (data(address, true)) fail("ELF code overlaps declared data", address);
      if (++result.decoded > (16u << 20)) throw ElfError("ELF decoded-instruction limit");
      size_t size = 4;
      uint64_t pc = address;
      if (!cs_disasm_iter(decoder.handle, &input, &size, &pc, decoder.instruction) ||
          decoder.instruction->size != 4 || !decoder.instruction->detail)
        fail("invalid reachable AArch64 instruction", address);
      set(visited, address);
      auto *instruction = decoder.instruction;
      uint32_t word;
      std::memcpy(&word, code(address), 4);
      if ((word & 0xffffffe0u) == 0xd51bd040u)
        fail("guest TPIDR_EL0 writes are unsupported", address);
      if (instruction->id == ARM64_INS_SVC && word != 0xd4000001u)
        fail("unsupported guest SVC immediate", address);
      if (instruction->id == ARM64_INS_HVC ||
          instruction->id == ARM64_INS_SMC || instruction->id == ARM64_INS_ERET ||
          instruction->id == ARM64_INS_ERETAA || instruction->id == ARM64_INS_ERETAB ||
          instruction->id == ARM64_INS_DRPS)
        fail("unsupported guest exception/system-control instruction", address);
      if (instruction->id == ARM64_INS_RET || instruction->id == ARM64_INS_RETAA ||
          instruction->id == ARM64_INS_RETAB || instruction->id == ARM64_INS_BRK ||
          instruction->id == ARM64_INS_HLT || instruction->id == ARM64_INS_UDF) break;
      bool call = cs_insn_group(decoder.handle, instruction, CS_GRP_CALL);
      bool jump = cs_insn_group(decoder.handle, instruction, CS_GRP_JUMP);
      const auto &operands = instruction->detail->arm64;
      if (instruction->id == ARM64_INS_BLR && operands.op_count == 1) {
        int source = register_index(operands.operands[0].reg);
        if (source >= 0 && values[source].registration && values[2].constant && values[3].constant)
          jni_table(values[2].value, values[3].value);
      }
      // Track only constants and the exact [x0] -> [vtable+215*8] load
      // chain within one straight-line region. Unknown writes and every
      // control-flow boundary erase provenance rather than guessing values.
      auto before = values;
      cs_regs reads{}, writes{};
      uint8_t read_count = 0, write_count = 0;
      if (cs_regs_access(decoder.handle, instruction, reads, &read_count, writes, &write_count) != CS_ERR_OK)
        throw ElfError("cannot inspect AArch64 register writes");
      for (unsigned i = 0; i < write_count; ++i) {
        int index = register_index(writes[i]);
        if (index >= 0) values[index] = {};
      }
      if (operands.op_count && operands.operands[0].type == ARM64_OP_REG) {
        int destination = register_index(operands.operands[0].reg);
        if (destination >= 0) {
          if ((instruction->id == ARM64_INS_ADR || instruction->id == ARM64_INS_ADRP || instruction->id == ARM64_INS_MOV) &&
              operands.op_count == 2 && operands.operands[1].type == ARM64_OP_IMM)
            values[destination] = {uint64_t(operands.operands[1].imm), true, false, false};
          if (instruction->id == ARM64_INS_ADD && operands.op_count == 3 &&
              operands.operands[1].type == ARM64_OP_REG && operands.operands[2].type == ARM64_OP_IMM &&
              operands.operands[2].shift.value == 0) {
            int source = register_index(operands.operands[1].reg);
            int64_t increment = operands.operands[2].imm;
            if (source >= 0 && before[source].constant && increment >= 0 &&
                uint64_t(increment) <= UINT64_MAX - before[source].value)
              values[destination] = {before[source].value + uint64_t(increment), true, false, false};
          }
          if (instruction->id == ARM64_INS_LDR && operands.op_count == 2 &&
              operands.operands[0].reg >= ARM64_REG_X0 && operands.operands[0].reg <= ARM64_REG_X28 &&
              operands.operands[1].type == ARM64_OP_MEM && !operands.writeback &&
              operands.operands[1].mem.index == ARM64_REG_INVALID) {
            int source = register_index(operands.operands[1].mem.base);
            int64_t displacement = operands.operands[1].mem.disp;
            if (source == 0 && displacement == 0) values[destination].vtable = true;
            if (source >= 0 && before[source].vtable && displacement == 215 * 8)
              values[destination].registration = true;
          }
        }
      }
      if (call || jump) values = {};
      // Hand-written routines also return with BLR LR. Do not infer code
      // provenance for its fallthrough; later TPIDR sites need independent roots.
      if (instruction->id == ARM64_INS_BLR && ((word >> 5) & 31) == 30) break;
      if (call || jump) {
        bool immediate = false;
        uint64_t target = 0;
        for (unsigned i = 0; i < operands.op_count; ++i)
          if (operands.operands[i].type == ARM64_OP_IMM) {
            immediate = true; target = uint64_t(operands.operands[i].imm);
          }
        if (immediate) enqueue(target);
        // Direct unconditional branches and indirect jumps do not fall through.
        if (!call && (!immediate || (word & 0xfc000000u) == 0x14000000u)) break;
      }
      address += 4;
    }
  }
  for (const auto &entry : words) {
    if (test(visited, entry.address)) {
      if (entry.instruction == 0xd4000001u) {
        result.syscalls.push_back(entry);
        continue;
      }
      if ((entry.instruction & 31) == 18) fail("guest TPIDR x18 destination is reserved", entry.address);
      result.sites.push_back(entry);
    } else if (data(entry.address, false)) ++result.data_words;
    else fail("ambiguous unreachable TPIDR/SVC word", entry.address);
  }
  std::sort(result.sites.begin(), result.sites.end(),
            [](const auto &a, const auto &b) { return a.address < b.address; });
  return result;
}
} // namespace shiraz::guest
