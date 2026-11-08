// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/binding_evidence.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <map>
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
