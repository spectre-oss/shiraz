// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/binding_evidence.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "native_mapping.hpp"
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <fstream>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <map>
#include <pthread.h>
#include <tuple>
#include <unistd.h>
#include <vector>

namespace shiraz::guest {
namespace {
struct Json {
  std::string value;
  void add(const std::string &text) {
    if (text.size() > (64u << 20) - value.size()) throw ElfError("binding report byte limit");
    value += text;
  }
  void string(const std::string &text) {
    if (text.size() > 4096) throw ElfError("binding report string limit");
    add("\"");
    for (unsigned char byte : text) {
      if (byte == '"' || byte == '\\') add(std::string("\\") + char(byte));
      else if (byte < 32 || byte >= 127) {
        char escape[7];
        std::snprintf(escape, sizeof(escape), "\\u%04x", unsigned(byte));
        add(escape);
      } else add(std::string(1, char(byte)));
    }
    add("\"");
  }
};
std::vector<uint8_t> read_elf(const std::filesystem::path &path) {
  std::ifstream input(path, std::ios::binary | std::ios::ate);
  auto size = input.tellg();
  if (!input || size <= 0 || size > (256 << 20)) throw ElfError("binding report ELF size/open");
  std::vector<uint8_t> bytes(static_cast<size_t>(size));
  input.seekg(0);
  if (!input.read(reinterpret_cast<char *>(bytes.data()), size)) throw ElfError("binding report ELF read");
  return bytes;
}
struct ModuleSnapshot {
  LoadedModuleInfo info;
  Elf elf;
};
bool mapped(const Elf &elf, uint64_t address, uint64_t width) {
  for (const auto &segment : elf.segments)
    if (address >= segment.address && address - segment.address <= segment.memory_size &&
        width <= segment.memory_size - (address - segment.address)) return true;
  return false;
}
struct NativeCodeSegment { uint64_t start, size; };
bool native_code_segment(const std::vector<NativeCodeSegment> &segments,
                         uintptr_t address, size_t bytes) {
  if (!bytes || bytes > UINTPTR_MAX - address) return false;
  for (const auto &segment : segments)
    if (address >= segment.start && address - segment.start <= segment.size &&
        bytes <= segment.size - (address - segment.start)) return true;
  return false;
}
using detail::native_mapping;
bool native_code_mapping(uintptr_t address, size_t bytes) {
  return native_mapping(address, bytes, VM_PROT_READ | VM_PROT_EXECUTE, VM_PROT_WRITE);
}
void cached_self_snapshot(Json &json, const Binding &binding, uintptr_t slot) {
  const uintptr_t entry = reinterpret_cast<uintptr_t>(&shiraz_guest_cached_pthread_self);
  Dl_info info{}, producer{};
  if (!binding.found || binding.address != entry || slot != entry ||
      binding.kind != SymbolKind::Function || binding.nonreturning || binding.provider != "libc.so" ||
      !dladdr(reinterpret_cast<const void *>(entry), &info) || !info.dli_fbase || !info.dli_fname ||
      !dladdr(reinterpret_cast<const void *>(&write_binding_evidence), &producer) ||
      producer.dli_fbase != info.dli_fbase)
    throw ElfError("cached pthread identity requires exact first-party entry");
  const mach_header_64 *header = nullptr;
  intptr_t slide = 0;
  const auto count = _dyld_image_count();
  if (count > 8192) throw ElfError("cached identity image count bound");
  for (uint32_t index = 0; index < count; ++index) {
    auto candidate = _dyld_get_image_header(index);
    if (candidate == info.dli_fbase) {
      if (header) throw ElfError("ambiguous cached identity image");
      header = reinterpret_cast<const mach_header_64 *>(candidate);
      slide = _dyld_get_image_vmaddr_slide(index);
    }
  }
  if (!header || header->magic != MH_MAGIC_64 || header->cputype != CPU_TYPE_ARM64 ||
      header->filetype != MH_DYLIB || !header->ncmds || header->ncmds > 4096 ||
      header->sizeofcmds > (1u << 20))
    throw ElfError("cached identity Mach-O header bound");
  const auto *commands = reinterpret_cast<const uint8_t *>(header + 1);
  std::vector<NativeCodeSegment> code_segments, data_segments;
  size_t offset = 0;
  for (uint32_t index = 0; index < header->ncmds; ++index) {
    if (offset > header->sizeofcmds || sizeof(load_command) > header->sizeofcmds - offset)
      throw ElfError("cached identity truncated load command");
    load_command command;
    std::memcpy(&command, commands + offset, sizeof(command));
    if (command.cmdsize < sizeof(command) || command.cmdsize > header->sizeofcmds - offset)
      throw ElfError("cached identity load command bound");
    if (command.cmd == LC_SEGMENT_64) {
      if (command.cmdsize < sizeof(segment_command_64))
        throw ElfError("cached identity segment command");
      segment_command_64 segment;
      std::memcpy(&segment, commands + offset, sizeof(segment));
      uint64_t start;
      if (slide >= 0) {
        if (segment.vmaddr > UINT64_MAX - uint64_t(slide))
          throw ElfError("cached identity segment slide overflow");
        start = segment.vmaddr + uint64_t(slide);
      } else {
        const uint64_t magnitude = uint64_t(-(slide + 1)) + 1;
        if (segment.vmaddr < magnitude) throw ElfError("cached identity segment slide underflow");
        start = segment.vmaddr - magnitude;
      }
      if (segment.filesize > segment.vmsize || segment.vmsize > UINT64_MAX - start)
        throw ElfError("cached identity segment size bound");
      if ((segment.initprot & (VM_PROT_READ | VM_PROT_EXECUTE)) ==
              (VM_PROT_READ | VM_PROT_EXECUTE) && !(segment.initprot & VM_PROT_WRITE))
        code_segments.push_back({start, segment.filesize});
      if ((segment.initprot & VM_PROT_READ) && !(segment.initprot & VM_PROT_EXECUTE))
        data_segments.push_back({start, segment.vmsize});
    }
    offset += command.cmdsize;
  }
  if (offset != header->sizeofcmds) throw ElfError("cached identity load commands size mismatch");
  const uintptr_t base = reinterpret_cast<uintptr_t>(header);
  constexpr size_t body_size = 152;
  // This is one bounded compiler profile, not a claim about arbitrary bodies.
  // Leave the ABI unresolved if its full window/tail is unavailable; the
  // independent reader still has to validate every instruction and range.
  if (entry < base || (entry & 3) || !native_code_segment(code_segments, entry, body_size) ||
      !native_code_mapping(entry, body_size)) return;
  uint8_t body[body_size];
  std::memcpy(body, reinterpret_cast<const void *>(entry), sizeof(body));
  uint32_t ret, trap;
  std::memcpy(&ret, body + 144, sizeof(ret));
  std::memcpy(&trap, body + 148, sizeof(trap));
  if (ret != 0xd65f03c0u || trap != 0xd4200020u) return;
  const auto cache = cached_pthread_self_tables();
  if (cache.rows != 4096) throw ElfError("cached identity row count mismatch");
  for (size_t index = 0; index < 4; ++index) {
    const auto &table = cache.tables[index];
    if (table.address < base || table.bytes != 32768 || table.bytes > UINTPTR_MAX - table.address ||
        !native_code_segment(data_segments, table.address, size_t(table.bytes)))
      throw ElfError(std::string("cached identity table declared extent: ") + table.role);
    if (!native_mapping(table.address, size_t(table.bytes), VM_PROT_READ, VM_PROT_EXECUTE))
      throw ElfError(std::string("cached identity table current mapping: ") + table.role);
    if (table.address < entry + body_size && entry < table.address + table.bytes)
      throw ElfError("cached identity code/table overlap");
    for (size_t previous = 0; previous < index; ++previous) {
      const auto &other = cache.tables[previous];
      if (table.address < other.address + other.bytes && other.address < table.address + table.bytes)
        throw ElfError("cached identity table overlap");
    }
  }
  json.add(",\"cached_self\":{\"profile\":1,\"verification\":\"mapped-first-party-code-and-cache-ranges\",\"execution_claim\":false,\"entry_offset\":" +
           std::to_string(entry - base) + ",\"body_hex\":\"");
  for (uint8_t value : body) {
    char byte[3]; std::snprintf(byte, sizeof(byte), "%02x", unsigned(value)); json.add(byte);
  }
  json.add("\",\"tables\":[");
  for (size_t index = 0; index < 4; ++index) {
    if (index) json.add(",");
    const auto &table = cache.tables[index];
    json.add("{\"role\":"); json.string(table.role);
    json.add(",\"offset\":" + std::to_string(table.address - base) + ",\"bytes\":" +
             std::to_string(table.bytes) + "}");
  }
  json.add("],\"stack_exclusion_profile\":" + std::to_string(kCachedSelfStackExclusionProfile) + "}");
}
void prepared_cleanup_snapshot(Json &json) {
  const uintptr_t entry = reinterpret_cast<uintptr_t>(&prepared_guest_cleanup_admitted);
  Dl_info info{}, producer{};
  if (!dladdr(reinterpret_cast<const void *>(entry), &info) || !info.dli_fbase || !info.dli_fname ||
      !dladdr(reinterpret_cast<const void *>(&write_binding_evidence), &producer) ||
      producer.dli_fbase != info.dli_fbase)
    throw ElfError("prepared cleanup requires exact first-party entry");
  const mach_header_64 *header = nullptr;
  intptr_t slide = 0;
  const auto count = _dyld_image_count();
  if (count > 8192) throw ElfError("prepared cleanup image count bound");
  for (uint32_t index = 0; index < count; ++index) {
    const auto *candidate = _dyld_get_image_header(index);
    if (candidate == info.dli_fbase) {
      if (header) throw ElfError("ambiguous prepared cleanup image");
      header = reinterpret_cast<const mach_header_64 *>(candidate);
      slide = _dyld_get_image_vmaddr_slide(index);
    }
  }
  // Executable fixture producers have no NativeBridge image contract. Keep
  // their report format usable without inventing a DLL snapshot for them.
  if (header && header->filetype != MH_DYLIB) return;
  if (!header || header->magic != MH_MAGIC_64 || header->cputype != CPU_TYPE_ARM64 ||
      !header->ncmds || header->ncmds > 4096 || header->sizeofcmds > (1u << 20))
    throw ElfError("prepared cleanup Mach-O header bound");
  const auto *commands = reinterpret_cast<const uint8_t *>(header + 1);
  std::vector<NativeCodeSegment> code_segments, data_segments;
  size_t offset = 0;
  for (uint32_t index = 0; index < header->ncmds; ++index) {
    if (offset > header->sizeofcmds || sizeof(load_command) > header->sizeofcmds - offset)
      throw ElfError("prepared cleanup truncated load command");
    load_command command;
    std::memcpy(&command, commands + offset, sizeof(command));
    if (command.cmdsize < sizeof(command) || command.cmdsize > header->sizeofcmds - offset)
      throw ElfError("prepared cleanup load command bound");
    if (command.cmd == LC_SEGMENT_64) {
      if (command.cmdsize < sizeof(segment_command_64))
        throw ElfError("prepared cleanup segment command");
      segment_command_64 segment;
      std::memcpy(&segment, commands + offset, sizeof(segment));
      uint64_t start;
      if (slide >= 0) {
        if (segment.vmaddr > UINT64_MAX - uint64_t(slide))
          throw ElfError("prepared cleanup segment slide overflow");
        start = segment.vmaddr + uint64_t(slide);
      } else {
        const uint64_t magnitude = uint64_t(-(slide + 1)) + 1;
        if (segment.vmaddr < magnitude) throw ElfError("prepared cleanup segment slide underflow");
        start = segment.vmaddr - magnitude;
      }
      if (segment.filesize > segment.vmsize || segment.vmsize > UINT64_MAX - start)
        throw ElfError("prepared cleanup segment size bound");
      if ((segment.initprot & (VM_PROT_READ | VM_PROT_EXECUTE)) ==
              (VM_PROT_READ | VM_PROT_EXECUTE) && !(segment.initprot & VM_PROT_WRITE))
        code_segments.push_back({start, segment.filesize});
      if ((segment.initprot & VM_PROT_READ) && !(segment.initprot & VM_PROT_EXECUTE))
        data_segments.push_back({start, segment.vmsize});
    }
    offset += command.cmdsize;
  }
  if (offset != header->sizeofcmds) throw ElfError("prepared cleanup load commands size mismatch");
  const uintptr_t base = reinterpret_cast<uintptr_t>(header);
  constexpr size_t body_size = 216;
  // Capture a bounded installation observation only. The independent reader
  // must validate all words; unavailable or different complete profiles stay
  // absent rather than becoming a claim about an arbitrary function tail.
  if (entry < base || (entry & 3) || !native_code_segment(code_segments, entry, body_size) ||
      !native_code_mapping(entry, body_size)) return;
  uint8_t body[body_size];
  std::memcpy(body, reinterpret_cast<const void *>(entry), sizeof(body));
  uint32_t first_ret, last_ret;
  std::memcpy(&first_ret, body + 200, sizeof(first_ret));
  std::memcpy(&last_ret, body + 212, sizeof(last_ret));
  if (first_ret != 0xd65f03c0u || last_ret != 0xd65f03c0u) return;
  const auto cache = cached_pthread_self_tables();
  if (cache.rows != 4096) throw ElfError("prepared cleanup row count mismatch");
  const CachedSelfTable tables[] = {cache.tables[0], cache.tables[1], cache.tables[2], cache.tables[3],
                                    prepared_cleanup_admission_table()};
  const char *roles[] = {"owners", "keys", "low", "high", "admission"};
  for (size_t index = 0; index < 5; ++index) {
    const auto &table = tables[index];
    if (!table.role || std::strcmp(table.role, roles[index]))
      throw ElfError("prepared cleanup table role mismatch");
    if (table.address < base || table.bytes != 32768 || table.bytes > UINTPTR_MAX - table.address ||
        !native_code_segment(data_segments, table.address, size_t(table.bytes)))
      throw ElfError(std::string("prepared cleanup table declared extent: ") + table.role);
    if (!native_mapping(table.address, size_t(table.bytes), VM_PROT_READ, VM_PROT_EXECUTE))
      throw ElfError(std::string("prepared cleanup table current mapping: ") + table.role);
    if (table.address < entry + body_size && entry < table.address + table.bytes)
      throw ElfError("prepared cleanup code/table overlap");
    for (size_t previous = 0; previous < index; ++previous) {
      const auto &other = tables[previous];
      if (table.address < other.address + other.bytes && other.address < table.address + table.bytes)
        throw ElfError("prepared cleanup table overlap");
    }
  }
  json.add(",\"prepared_cleanup\":{\"profile\":1,\"verification\":\"mapped-first-party-code-and-cache-ranges\",\"execution_claim\":false,\"entry_offset\":" +
           std::to_string(entry - base) + ",\"body_hex\":\"");
  for (uint8_t value : body) {
    char byte[3]; std::snprintf(byte, sizeof(byte), "%02x", unsigned(value)); json.add(byte);
  }
  json.add("\",\"tables\":[");
  for (size_t index = 0; index < 5; ++index) {
    if (index) json.add(",");
    const auto &table = tables[index];
    json.add("{\"role\":"); json.string(table.role);
    json.add(",\"offset\":" + std::to_string(table.address - base) + ",\"bytes\":" +
             std::to_string(table.bytes) + "}");
  }
  // This version names the trusted first-party publication contract, not an
  // independently authenticated current row or asynchronous lifetime lease.
  json.add("],\"stack_exclusion_profile\":1}");
}
void native_self_identity(Json &json, const Binding &binding, uintptr_t slot) {
  // This identifies a mapped prefix, not the function's complete control flow
  // or effects. In particular, pthread_self has native PAC failure paths.
  const uintptr_t entry = reinterpret_cast<uintptr_t>(&::pthread_self);
  Dl_info info{};
  if (!binding.found || binding.address != entry || slot != entry || binding.kind != SymbolKind::Function ||
      binding.nonreturning || binding.provider != "libc.so" ||
      !dladdr(reinterpret_cast<const void *>(entry), &info) || !info.dli_fname ||
      !info.dli_sname || info.dli_saddr != reinterpret_cast<const void *>(entry) ||
      std::strcmp(info.dli_fname, "/usr/lib/system/libsystem_pthread.dylib") ||
      std::strcmp(info.dli_sname, "pthread_self"))
    throw ElfError("native pthread identity requires exact system entry");
  const mach_header_64 *header = nullptr;
  intptr_t slide = 0;
  const auto count = _dyld_image_count();
  if (count > 8192) throw ElfError("native identity image count bound");
  for (uint32_t index = 0; index < count; ++index) {
    auto candidate = _dyld_get_image_header(index);
    if (candidate == info.dli_fbase) {
      if (header) throw ElfError("ambiguous native image identity");
      header = reinterpret_cast<const mach_header_64 *>(candidate);
      slide = _dyld_get_image_vmaddr_slide(index);
    }
  }
  if (!header || header->magic != MH_MAGIC_64 || header->cputype != CPU_TYPE_ARM64 ||
      header->filetype != MH_DYLIB || !header->ncmds || header->ncmds > 4096 ||
      header->sizeofcmds > (1u << 20))
    throw ElfError("native identity Mach-O header bound");
  constexpr size_t prefix_size = 64;
  const auto *commands = reinterpret_cast<const uint8_t *>(header + 1);
  size_t offset = 0;
  bool have_uuid = false;
  std::vector<NativeCodeSegment> code_segments;
  uint8_t uuid[16]{};
  for (uint32_t index = 0; index < header->ncmds; ++index) {
    if (offset > header->sizeofcmds || sizeof(load_command) > header->sizeofcmds - offset)
      throw ElfError("native identity truncated load command");
    load_command command;
    std::memcpy(&command, commands + offset, sizeof(command));
    if (command.cmdsize < sizeof(command) || command.cmdsize > header->sizeofcmds - offset)
      throw ElfError("native identity load command bound");
    if (command.cmd == LC_UUID) {
      if (have_uuid || command.cmdsize != sizeof(uuid_command))
        throw ElfError("native identity UUID command");
      std::memcpy(uuid, commands + offset + sizeof(load_command), sizeof(uuid));
      have_uuid = true;
    } else if (command.cmd == LC_SEGMENT_64) {
      if (command.cmdsize < sizeof(segment_command_64))
        throw ElfError("native identity segment command");
      segment_command_64 segment;
      std::memcpy(&segment, commands + offset, sizeof(segment));
      uint64_t start;
      if (slide >= 0) {
        if (segment.vmaddr > UINT64_MAX - uint64_t(slide))
          throw ElfError("native identity segment slide overflow");
        start = segment.vmaddr + uint64_t(slide);
      } else {
        const uint64_t magnitude = uint64_t(-(slide + 1)) + 1;
        if (segment.vmaddr < magnitude) throw ElfError("native identity segment slide underflow");
        start = segment.vmaddr - magnitude;
      }
      if ((segment.initprot & (VM_PROT_READ | VM_PROT_EXECUTE)) ==
              (VM_PROT_READ | VM_PROT_EXECUTE) && !(segment.initprot & VM_PROT_WRITE))
        code_segments.push_back({start, segment.vmsize});
    }
    offset += command.cmdsize;
  }
  const uintptr_t base = reinterpret_cast<uintptr_t>(header);
  if (offset != header->sizeofcmds || !have_uuid || entry < base || (entry & 3) ||
      !native_code_segment(code_segments, entry, prefix_size))
    throw ElfError("native identity requires owned executable prefix and UUID");
  // Segment initprot is only a declaration. Check the current mapping before
  // reading the prefix; this is still a snapshot, not a future-protection or
  // complete native-body guarantee. The loaded system Mach header is trusted.
  if (!native_code_mapping(entry, prefix_size))
    throw ElfError("native identity prefix is not currently readable executable memory");
  uint8_t prefix[prefix_size];
  std::memcpy(prefix, reinterpret_cast<const void *>(entry), prefix_size);
  // Optional, bounded identity windows only. They are neither symbol extents
  // nor complete CFG/effect certificates. Unsupported layouts keep profile1.
  constexpr size_t entry_size = 256, cold_size = 128, call_offset = 72;
  uint8_t entry_window[entry_size]{}, cold_window[cold_size]{};
  uintptr_t cold = 0;
  bool extended = false;
  if (native_code_segment(code_segments, entry, entry_size) &&
      native_code_mapping(entry, entry_size)) {
    std::memcpy(entry_window, reinterpret_cast<const void *>(entry), entry_size);
    uint32_t word;
    std::memcpy(&word, entry_window + call_offset, sizeof(word));
    if (!std::memcmp(prefix, entry_window, prefix_size) && (word & 0xfc000000u) == 0x94000000u) {
      const int64_t immediate = int64_t(word & 0x03ffffffu);
      const int64_t delta = (immediate & (int64_t(1) << 25)
                               ? immediate - (int64_t(1) << 26) : immediate) * 4;
      const uintptr_t pc = entry + call_offset; // entry_size was checked above.
      bool target_valid = false;
      if (delta >= 0 && uint64_t(delta) <= UINTPTR_MAX - pc) {
        cold = pc + uintptr_t(delta);
        target_valid = true;
      } else if (delta < 0 && uint64_t(-delta) <= pc) {
        cold = pc - uintptr_t(-delta);
        target_valid = true;
      }
      Dl_info cold_info{};
      if (target_valid && cold >= base && !(cold & 3) &&
          native_code_segment(code_segments, cold, cold_size) &&
          // Both spans have checked ends; do not admit overlapping windows.
          (cold >= entry + entry_size || cold + cold_size <= entry) &&
          dladdr(reinterpret_cast<const void *>(cold), &cold_info) &&
          cold_info.dli_fname && cold_info.dli_sname && cold_info.dli_fbase == info.dli_fbase &&
          cold_info.dli_saddr == reinterpret_cast<const void *>(cold) &&
          !std::strcmp(cold_info.dli_fname, info.dli_fname) &&
          !std::strcmp(cold_info.dli_sname, "pthread_self.cold.1") &&
          native_code_mapping(cold, cold_size)) {
        std::memcpy(cold_window, reinterpret_cast<const void *>(cold), cold_size);
        extended = true;
      }
    }
  }
  auto hex = [](const uint8_t *bytes, size_t size) {
    std::string result;
    for (size_t index = 0; index < size; ++index) {
      char byte[3];
      std::snprintf(byte, sizeof(byte), "%02x", unsigned(bytes[index]));
      result += byte;
    }
    return result;
  };
  json.add(",\"native_target\":{\"profile\":" + std::to_string(extended ? 2 : 1) + ",\"verification\":");
  json.string(extended ? "resolved-system-image-code-windows" : "resolved-system-image-prefix");
  json.add(",\"image\":");
  json.string(info.dli_fname);
  json.add(",\"image_uuid\":"); json.string(hex(uuid, sizeof(uuid)));
  json.add(",\"symbol\":\"pthread_self\",\"entry_offset\":" + std::to_string(entry - base));
  json.add(",\"prefix_hex\":"); json.string(hex(prefix, prefix_size));
  if (extended) {
    json.add(",\"cold_call_offset\":72,\"complete_control_flow_verified\":false,\"code_windows\":[");
    auto window = [&](const char *role, const char *symbol, uintptr_t address,
                      const uint8_t *bytes, size_t size) {
      json.add("{\"role\":"); json.string(role);
      json.add(",\"symbol\":"); json.string(symbol);
      json.add(",\"entry_offset\":" + std::to_string(address - base));
      json.add(",\"byte_count\":" + std::to_string(size) + ",\"prefix_hex\":");
      json.string(hex(bytes, size)); json.add("}");
    };
    window("entry", "pthread_self", entry, entry_window, entry_size);
    json.add(",");
    window("cold-error", "pthread_self.cold.1", cold, cold_window, cold_size);
    json.add("]");
  }
  json.add(",\"execution_claim\":false,\"semantic_effects_verified\":false}");
}
}
size_t write_binding_evidence(const Loader &loader, const std::filesystem::path &output,
                             const std::string &scope, const std::vector<std::string> &load_order,
                             const std::vector<int> &load_flags) {
  std::map<std::string, ModuleSnapshot> modules;
  for (auto &info : loader.module_info()) {
    auto bytes = read_elf(info.name);
    auto elf = parse_elf(bytes, uint64_t(getpagesize()));
    auto name = elf.soname.empty() ? std::filesystem::path(info.name).filename().string() : elf.soname;
    if (!modules.emplace(name, ModuleSnapshot{info, std::move(elf)}).second)
      throw ElfError("ambiguous binding namespace SONAME");
  }
  using Key = std::tuple<std::string, std::string, std::string>;
  std::map<Key, std::vector<const BindingRecord *>> records;
  for (const auto &record : loader.bindings())
    records[{record.module, record.symbol, record.version}].push_back(&record);
  Json json;
  json.add("{\"schema\":1,\"namespace\":"); json.string(scope);
  json.add(",\"snapshot\":\"post-link-before-open\",\"constructors_executed\":" + std::to_string(loader.constructor_count()));
  json.add(",\"report_executes_guest_code\":false");
  prepared_cleanup_snapshot(json);
  json.add(",\"load_order\":[");
  bool comma = false;
  for (const auto &name : load_order) {
    if (comma) json.add(",");
    comma = true; json.string(name);
  }
  json.add("],\"load_flags\":[");
  comma = false;
  for (int flags : load_flags) {
    if (comma) json.add(",");
    comma = true; json.add(std::to_string(flags));
  }
  json.add("],\"modules\":[");
  size_t count = 0, all_count = 0;
  comma = false;
  for (const auto &pair : modules) {
    const auto &name = pair.first;
    const auto &module = pair.second;
    if (comma) json.add(",");
    comma = true;
    json.add("{\"name\":"); json.string(name);
    json.add(",\"file\":"); json.string(module.info.name);
    json.add(",\"mrs_stack_window\":{\"profile\":" + std::to_string(kMrsStackWindowProfile) +
             ",\"before\":" + std::to_string(kMrsStackBytesBefore) +
             ",\"after\":" + std::to_string(kMrsStackBytesAfter) +
             ",\"image_stack_exclusion_profile\":" + std::to_string(kImageStackExclusionProfile) +
             ",\"verification\":\"mapped-code-default-accessor\",\"execution_claim\":false,\"sites\":[");
    bool site_comma = false;
    for (auto site : loader.verified_mrs_window_sites(module.info.name)) {
      if (site_comma) json.add(",");
      site_comma = true; json.add(std::to_string(site));
    }
    const auto snapshot = loader.relocation_snapshot(module.info.name);
    if (!snapshot.metrics.relocations_verified ||
        snapshot.metrics.verified_relocations != snapshot.values.size() ||
        snapshot.values.size() != module.elf.relocations.size() ||
        snapshot.elf.relocations.size() != module.elf.relocations.size())
      throw ElfError("binding report requires complete verified relocations");
    for (size_t index = 0; index < module.elf.relocations.size(); ++index) {
      const auto &original = snapshot.elf.relocations[index];
      const auto &current = module.elf.relocations[index];
      if (original.address != current.address || original.type != current.type ||
          original.symbol != current.symbol || original.addend != current.addend)
        throw ElfError("binding report source relocation changed since load");
    }
    json.add("]},\"elf_sha256\":null,\"relocation_inventory\":{\"profile\":1,");
    json.add("\"verification\":\"read-back-after-relocate-before-seal\",\"verified_count\":" +
             std::to_string(snapshot.values.size()) + ",\"records\":[");
    bool relocation_comma = false;
    for (const auto &relocation : module.elf.relocations) {
      if (++all_count > 200000) throw ElfError("complete relocation report record limit");
      if (relocation_comma) json.add(",");
      relocation_comma = true;
      json.add("{\"relocation\":" + std::to_string(relocation.address) +
               ",\"relocation_type\":" + std::to_string(relocation.type) +
               ",\"symbol_index\":" + std::to_string(relocation.symbol) +
               ",\"addend\":" + std::to_string(relocation.addend) + "}");
    }
    json.add("]},\"absolute_binding_snapshot\":\"read-back-after-relocate-before-seal\",\"bindings\":[");
    auto append_bindings = [&](bool absolute) {

      bool binding_comma = false;
      for (const auto &relocation : module.elf.relocations) {
        if (absolute ? relocation.type != 257 : (relocation.type != 1025 && relocation.type != 1026)) continue;
        if ((!absolute && !relocation.symbol) || relocation.symbol >= module.elf.symbols.size()) continue;
        if (!absolute && ++count > 200000) throw ElfError("binding report record limit");
        if (!mapped(module.elf, relocation.address, 8)) throw ElfError("binding slot outside guest LOAD");
        const auto &symbol = module.elf.symbols[relocation.symbol];
        uintptr_t slot;
        std::memcpy(&slot, reinterpret_cast<const void *>(module.info.load_bias + relocation.address), 8);
        const auto relocation_index = size_t(&relocation - module.elf.relocations.data());
        const auto selected_value = absolute ? snapshot.values.at(relocation_index) : uint64_t(slot);
        const Binding *binding = nullptr;
        Binding local;
        if (!relocation.symbol) {
          local = {0, name, "ELF-null-symbol", SymbolKind::Unknown, true};
          binding = &local;
        } else if (symbol.section && ((symbol.info >> 4) == 0 ||
                   (symbol.visibility & 3) != 0 || symbol.version.index == 0)) {
          auto address = symbol.section == 0xfff1 ? uintptr_t(symbol.address) :
              module.info.load_bias + symbol.address;
          local = {address, name, "guest-local-AAPCS64",
                   (symbol.info & 15) == 2 ? SymbolKind::Function :
                   (symbol.info & 15) == 1 ? SymbolKind::Object : SymbolKind::Unknown, true};
          if (address + uint64_t(relocation.addend) == selected_value) binding = &local;
        }
        auto found = records.find({name, symbol.name, symbol.version.name});
        if (found != records.end()) for (const auto *record : found->second) {
          const auto &candidate = record->binding;
          if (candidate.address + uint64_t(relocation.addend) != selected_value) continue;
          if (binding && (binding->provider != candidate.provider || binding->abi != candidate.abi ||
                          binding->kind != candidate.kind || binding->found != candidate.found ||
                          binding->nonreturning != candidate.nonreturning))
            throw ElfError("ambiguous actual binding record for relocation");
          binding = &candidate;
        }
        if (absolute) {
          if (!binding) throw ElfError("absolute relocation has no selected binding evidence");
          if (binding->address + uint64_t(relocation.addend) != selected_value)
            throw ElfError("absolute relocation selected value disagrees with snapshot");
        }
        if (binding_comma) json.add(",");
        binding_comma = true;
        json.add("{\"relocation\":" + std::to_string(relocation.address));
        json.add(",\"relocation_type\":" + std::to_string(relocation.type));
        json.add(",\"addend\":" + std::to_string(relocation.addend));
        if (absolute) json.add(",\"usage\":\"absolute-address\",\"symbol_index\":" +
            std::to_string(relocation.symbol) + ",\"current_slot_matches_link\":" +
            std::string(uint64_t(slot) == selected_value ? "true" : "false"));
        json.add(",\"symbol\":"); json.string(symbol.name);
        json.add(",\"version\":"); json.string(symbol.version.name);
        json.add(",\"provider\":"); json.string(binding ? binding->provider : "");
        json.add(",\"provider_elf_sha256\":null,\"target\":");
        auto provider = binding ? modules.find(binding->provider) : modules.end();
        bool guest = binding && binding->found && provider != modules.end() &&
            (binding->abi == "guest-AAPCS64" || binding->abi == "guest-local-AAPCS64") &&
            selected_value >= provider->second.info.load_bias &&
            mapped(provider->second.elf, selected_value - provider->second.info.load_bias, 1);
        if (guest) json.add(std::to_string(selected_value - provider->second.info.load_bias));
        else json.add("null");
        json.add(",\"kind\":");
        json.string(!binding ? "unknown" : binding->kind == SymbolKind::Function ? "function" :
                    binding->kind == SymbolKind::Object ? "object" : "unknown");
        json.add(",\"abi\":"); json.string(binding ? binding->abi : "unrecorded");
        json.add(",\"nonreturning\":" + std::string(binding && binding->nonreturning ? "true" : "false"));
        if (!absolute && binding && binding->abi == "native/pthread-self-noargs") {
          if (symbol.name != "pthread_self" ||
              (!symbol.version.name.empty() && symbol.version.name != "LIBC") || relocation.addend)
            throw ElfError("native identity binding symbol/version/addend mismatch");
          native_self_identity(json, *binding, slot);
        }
        if (!absolute && binding && binding->abi == "prepared/pthread-self-noargs") {
          if (symbol.name != "pthread_self" ||
              (!symbol.version.name.empty() && symbol.version.name != "LIBC") || relocation.addend)
            throw ElfError("cached identity binding symbol/version/addend mismatch");
          cached_self_snapshot(json, *binding, slot);
        }
        json.add("}");
      }
    };
    append_bindings(false);
    json.add("],\"absolute_bindings\":[");
    append_bindings(true);
    json.add("]}");
  }
  json.add("]}\n");
  // Publish a complete snapshot atomically; no truncated report is success.
  std::filesystem::path destination(output);
  if (!destination.is_absolute() || destination.filename().empty())
    throw ElfError("binding report output requires an absolute file path");
  std::string temporary = destination.string() + ".pending-XXXXXX";
  std::vector<char> pattern(temporary.begin(), temporary.end());
  pattern.push_back(0);
  int fd = mkstemp(pattern.data());
  if (fd < 0) throw ElfError("binding report temporary output failed");
  try {
    size_t written = 0;
    while (written < json.value.size()) {
      ssize_t amount = write(fd, json.value.data() + written, json.value.size() - written);
      if (amount < 0 && errno == EINTR) continue;
      if (amount <= 0) throw ElfError("binding report output failed");
      written += size_t(amount);
    }
    if (close(fd)) { fd = -1; throw ElfError("binding report output close failed"); }
    fd = -1;
    if (rename(pattern.data(), destination.c_str())) throw ElfError("binding report publish failed");
  } catch (...) {
    if (fd >= 0) close(fd);
    unlink(pattern.data());
    throw;
  }
  return count;
}
}
