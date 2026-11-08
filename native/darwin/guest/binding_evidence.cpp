// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/binding_evidence.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/tls.hpp"
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
  bool executable = false, have_uuid = false;
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
      if (entry >= start && entry - start <= segment.vmsize &&
          prefix_size <= segment.vmsize - (entry - start) &&
          (segment.initprot & (VM_PROT_READ | VM_PROT_EXECUTE)) ==
              (VM_PROT_READ | VM_PROT_EXECUTE) && !(segment.initprot & VM_PROT_WRITE))
        executable = true;
    }
    offset += command.cmdsize;
  }
  const uintptr_t base = reinterpret_cast<uintptr_t>(header);
  if (offset != header->sizeofcmds || !have_uuid || !executable || entry < base || (entry & 3))
    throw ElfError("native identity requires owned executable prefix and UUID");
  // Segment initprot is only a declaration. Check the current mapping before
  // reading the prefix; this is still a snapshot, not a future-protection or
  // complete native-body guarantee. The loaded system Mach header is trusted.
  natural_t depth = 0;
  bool live_executable = false;
  for (unsigned attempt = 0; attempt < 32; ++attempt) {
    mach_vm_address_t region_start = entry;
    mach_vm_size_t region_size = 0;
    vm_region_submap_info_data_64_t region{};
    // Request the stable v0 layout: only protection and is_submap are needed.
    mach_msg_type_number_t region_count = VM_REGION_SUBMAP_INFO_V0_COUNT_64;
    auto status = mach_vm_region_recurse(mach_task_self(), &region_start, &region_size,
                                         &depth,
                                         reinterpret_cast<vm_region_recurse_info_t>(&region),
                                         &region_count);
    if (status != KERN_SUCCESS || region_count != VM_REGION_SUBMAP_INFO_V0_COUNT_64 ||
        depth > 32 || entry < region_start || entry - region_start > region_size ||
        prefix_size > region_size - (entry - region_start))
      throw ElfError("native identity VM region bounds/query failure");
    // A shared-cache enclosing submap may report READ alone. Descend to the
    // leaf containing this exact entry, never to the next region in the map.
    if (region.is_submap) {
      ++depth;
      continue;
    }
    live_executable = (region.protection & (VM_PROT_READ | VM_PROT_EXECUTE)) ==
                          (VM_PROT_READ | VM_PROT_EXECUTE) &&
                      !(region.protection & VM_PROT_WRITE);
    break;
  }
  if (!live_executable)
    throw ElfError("native identity prefix is not currently readable executable memory");
  auto hex = [](const uint8_t *bytes, size_t size) {
    std::string result;
    for (size_t index = 0; index < size; ++index) {
      char byte[3];
      std::snprintf(byte, sizeof(byte), "%02x", unsigned(bytes[index]));
      result += byte;
    }
    return result;
  };
  json.add(",\"native_target\":{\"profile\":1,\"verification\":\"resolved-system-image-prefix\",\"image\":");
  json.string(info.dli_fname);
  json.add(",\"image_uuid\":"); json.string(hex(uuid, sizeof(uuid)));
  json.add(",\"symbol\":\"pthread_self\",\"entry_offset\":" + std::to_string(entry - base));
  json.add(",\"prefix_hex\":"); json.string(hex(reinterpret_cast<const uint8_t *>(entry), prefix_size));
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
  json.add(",\"report_executes_guest_code\":false,\"load_order\":[");
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
  size_t count = 0;
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
    json.add("]},\"elf_sha256\":null,\"bindings\":[");
    bool binding_comma = false;
    for (const auto &relocation : module.elf.relocations) {
      // Only callable pointer slots used by PLT/GOT analysis. Relative and
      // ABS64/data relocation semantics are outside this evidence format.
      if (relocation.type != 1025 && relocation.type != 1026) continue;
      if (!relocation.symbol || relocation.symbol >= module.elf.symbols.size()) continue;
      if (++count > 200000) throw ElfError("binding report record limit");
      if (!mapped(module.elf, relocation.address, 8)) throw ElfError("binding slot outside guest LOAD");
      const auto &symbol = module.elf.symbols[relocation.symbol];
      uintptr_t slot;
      std::memcpy(&slot, reinterpret_cast<const void *>(module.info.load_bias + relocation.address), 8);
      const Binding *binding = nullptr;
      auto found = records.find({name, symbol.name, symbol.version.name});
      if (found != records.end()) for (const auto *record : found->second) {
        const auto &candidate = record->binding;
        if (candidate.address + uint64_t(relocation.addend) != slot) continue;
        if (binding && (binding->provider != candidate.provider || binding->abi != candidate.abi ||
                        binding->kind != candidate.kind || binding->found != candidate.found ||
                        binding->nonreturning != candidate.nonreturning))
          throw ElfError("ambiguous actual binding record for relocation");
        binding = &candidate;
      }
      if (binding_comma) json.add(",");
      binding_comma = true;
      json.add("{\"relocation\":" + std::to_string(relocation.address));
      json.add(",\"relocation_type\":" + std::to_string(relocation.type));
      json.add(",\"addend\":" + std::to_string(relocation.addend));
      json.add(",\"symbol\":"); json.string(symbol.name);
      json.add(",\"version\":"); json.string(symbol.version.name);
      json.add(",\"provider\":"); json.string(binding ? binding->provider : "");
      json.add(",\"provider_elf_sha256\":null,\"target\":");
      auto provider = binding ? modules.find(binding->provider) : modules.end();
      bool guest = binding && binding->found && provider != modules.end() &&
          (binding->abi == "guest-AAPCS64" || binding->abi == "guest-local-AAPCS64") &&
          slot >= provider->second.info.load_bias &&
          mapped(provider->second.elf, slot - provider->second.info.load_bias, 1);
      if (guest) json.add(std::to_string(slot - provider->second.info.load_bias));
      else json.add("null");
      json.add(",\"kind\":");
      json.string(!binding ? "unknown" : binding->kind == SymbolKind::Function ? "function" :
                  binding->kind == SymbolKind::Object ? "object" : "unknown");
      json.add(",\"abi\":"); json.string(binding ? binding->abi : "unrecorded");
      json.add(",\"nonreturning\":" + std::string(binding && binding->nonreturning ? "true" : "false"));
      if (binding && binding->abi == "native/pthread-self-noargs") {
        if (symbol.name != "pthread_self" ||
            (!symbol.version.name.empty() && symbol.version.name != "LIBC") || relocation.addend)
          throw ElfError("native identity binding symbol/version/addend mismatch");
        native_self_identity(json, *binding, slot);
      }
      json.add("}");
    }
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
