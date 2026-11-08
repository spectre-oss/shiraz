// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <unordered_map>

namespace shiraz::guest {
namespace {
bool valid_name(const std::string &name) {
  return !name.empty() && name != "." && name != ".." && name.size() < 4096 &&
         name.find('/') == std::string::npos &&
         name.find('\0') == std::string::npos;
}
SymbolKind kind(const Symbol &symbol) {
  return (symbol.info & 15) == 2   ? SymbolKind::Function
         : (symbol.info & 15) == 1 ? SymbolKind::Object
                                   : SymbolKind::Unknown;
}
bool compatible(SymbolKind wanted, SymbolKind available) {
  return wanted == SymbolKind::Unknown || available == SymbolKind::Unknown ||
         wanted == available;
}
} // namespace
void Providers::add(Provider provider) {
  if (!valid_name(provider.soname) || modules_.count(provider.soname))
    throw ElfError("invalid/duplicate personality SONAME");
  std::set<std::pair<std::string, std::string>> entries;
  for (const auto &symbol : provider.exports) {
    if (symbol.name.empty() || !symbol.address ||
        symbol.kind == SymbolKind::Unknown || symbol.abi.empty() ||
        (symbol.nonreturning && symbol.kind != SymbolKind::Function) ||
        (!symbol.version.empty() && !provider.versions.count(symbol.version)) ||
        !entries.emplace(symbol.name, symbol.version).second)
      throw ElfError("invalid/duplicate personality export: " + symbol.name);
  }
  auto name = provider.soname;
  modules_.emplace(std::move(name), std::move(provider));
}
struct Loader::Module {
  std::string key, name;
  std::unique_ptr<Image> image;
  const Provider *provider = nullptr;
  std::vector<Module *> dependencies;
  std::unordered_map<std::string, std::vector<size_t>> exports;
  bool global = false;
  enum class Status { Mapped, Bound, Initializing, Ready };
  Status status = Status::Mapped;
};
struct Loader::State {
  Providers providers;
  std::vector<std::filesystem::path> roots;
  std::map<std::string, std::unique_ptr<Module>> modules;
  std::map<std::string, Module *> aliases;
  std::vector<Module *> order;
  std::vector<BindingRecord> records;
  size_t constructors = 0, record_bytes = 0;
  bool initializing = false;
  std::function<void()> finalizer;
  std::vector<std::function<void()>> preunmap, postfinalizer;

  std::filesystem::path file(const std::filesystem::path &path) const {
    auto inside = [&](const std::filesystem::path &candidate) {
      auto canonical = std::filesystem::canonical(candidate);
      for (const auto &root : roots)
        if (canonical.parent_path() == root)
          return canonical;
      throw ElfError("guest path escapes configured library directories");
    };
    if (path.has_parent_path())
      return inside(path);
    if (!valid_name(path.string()))
      throw ElfError("invalid guest library name");
    for (const auto &root : roots)
      if (std::filesystem::exists(root / path))
        return inside(root / path);
    throw ElfError("guest dependency not found: " + path.string());
  }
  Module *discover(const std::filesystem::path &path,
                   std::vector<std::string> &added, size_t depth) {
    if (depth > 128)
      throw ElfError("guest dependency depth limit");
    if (!path.has_parent_path()) {
      auto alias = aliases.find(path.string());
      if (alias != aliases.end())
        return alias->second;
    }
    auto canonical = file(path);
    std::string key = canonical.string();
    auto found = modules.find(key);
    if (found != modules.end())
      return found->second.get();
    if (initializing)
      throw ElfError("new guest loads during constructors are not implemented");
    if (modules.size() >= 128)
      throw ElfError("guest module count limit");
    std::ifstream stream(canonical, std::ios::binary | std::ios::ate);
    auto size = stream.tellg();
    if (!stream || size <= 0 || size > (256 << 20))
      throw ElfError("guest ELF size/open: " + key);
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    stream.seekg(0);
    if (!stream.read(reinterpret_cast<char *>(bytes.data()), size))
      throw ElfError("guest ELF read: " + key);
    auto module = std::make_unique<Module>();
    module->key = key;
    module->image = std::make_unique<Image>(bytes, Image::Mapping::Deferred);
    // Free input bytes before recursing into dependencies.
    std::vector<uint8_t>().swap(bytes);
    const auto &elf = module->image->elf();
    module->name =
        elf.soname.empty() ? canonical.filename().string() : elf.soname;
    if (aliases.count(module->name))
      throw ElfError("conflicting guest SONAME: " + module->name);
    module->global = elf.global;
    uint64_t span = module->image->mapping_size(), symbols = elf.symbols.size(), names = 0;
    for (const auto &symbol : elf.symbols)
      names += symbol.name.size() + symbol.version.name.size() +
               symbol.version.library.size();
    for (const auto &pair : modules)
      if (pair.second->image) {
        const auto &other = pair.second->image->elf();
        span += pair.second->image->mapping_size();
        symbols += other.symbols.size();
        for (const auto &symbol : other.symbols)
          names += symbol.name.size() + symbol.version.name.size() +
                   symbol.version.library.size();
      }
    if (span > (1ull << 30) || symbols > (2ull << 20) || names > (128ull << 20))
      throw ElfError("guest namespace memory/symbol limit");
    for (size_t i = 0; i < elf.symbols.size(); ++i) {
      const auto &symbol = elf.symbols[i];
      unsigned binding = symbol.info >> 4, visibility = symbol.visibility & 3;
      if (binding > 2)
        throw ElfError("unsupported guest symbol binding");
      if (symbol.section && (binding == 1 || binding == 2) && visibility != 1 &&
          visibility != 2 && (!elf.versioned || symbol.version.index != 0))
        module->exports[symbol.name].push_back(i);
    }
    Module *result = module.get();
    // Track ownership before publishing aliases; failures remove all newly
    // discovered nodes.
    added.push_back(key);
    modules.emplace(key, std::move(module));
    aliases.emplace(result->name, result);
    for (const auto &needed : elf.needed)
      result->dependencies.push_back(discover(needed, added, depth + 1));
    return result;
  }
  std::vector<Module *> breadth(Module *root) const {
    std::vector<Module *> list{root};
    std::set<Module *> seen{root};
    for (size_t i = 0; i < list.size(); ++i)
      for (auto *child : list[i]->dependencies)
        if (seen.insert(child).second)
          list.push_back(child);
    return list;
  }
  bool defines_version(Module *module, const std::string &name) const {
    if (module->provider)
      return module->provider->versions.empty() ||
             module->provider->versions.count(name);
    if (!module->image->elf().versioned)
      return true;
    for (const auto &version : module->image->elf().definitions)
      if (version.name == name)
        return true;
    return false;
  }
  Binding lookup(Module *module, const std::string &name,
                 const std::string &version, SymbolKind wanted) const {
    if (module->provider) {
      for (const auto &symbol : module->provider->exports) {
        if (symbol.name != name || !compatible(wanted, symbol.kind))
          continue;
        bool match = version.empty()
                         ? !symbol.hidden
                         : module->provider->versions.empty() ||
                               (module->provider->versions.count(version) &&
                                symbol.version == version);
        if (match)
          return {symbol.address, module->name, symbol.abi, symbol.kind, true, symbol.nonreturning};
      }
      return {};
    }
    auto found = module->exports.find(name);
    if (found == module->exports.end())
      return {};
    const auto &elf = module->image->elf();
    // Bionic excludes VER_FLG_BASE (index 1) from symbol-version matching.
    // A missing requested version selects only unversioned global definitions.
    bool has_version = version.empty();
    for (const auto &definition : elf.definitions)
      if (definition.index > 1 && definition.name == version)
        has_version = true;
    for (size_t index : found->second) {
      const auto &symbol = elf.symbols[index];
      if (!compatible(wanted, kind(symbol)))
        continue;
      bool match =
          !elf.versioned || (version.empty() ? !symbol.version.hidden
                             : has_version   ? symbol.version.name == version
                                             : symbol.version.index == 1);
      if (match)
        return {module->image->address(index), module->name, "guest-AAPCS64",
                kind(symbol), true};
    }
    return {};
  }
  Binding resolve(Module *from, size_t index,
                  const std::vector<Module *> &scope) const {
    const auto &symbol = from->image->elf().symbols[index];
    if (!index)
      return {0, from->name, "ELF-null-symbol", SymbolKind::Unknown, true};
    if (symbol.section &&
        ((symbol.info >> 4) == 0 || (symbol.visibility & 3) != 0 ||
         symbol.version.index == 0))
      return {from->image->address(index), from->name, "guest-local-AAPCS64",
              kind(symbol), true};
    if (from->image->elf().symbolic) {
      auto self = lookup(from, symbol.name, symbol.version.name, kind(symbol));
      if (self.found)
        return self;
    }
    for (auto *candidate : scope) {
      auto found =
          lookup(candidate, symbol.name, symbol.version.name, kind(symbol));
      if (found.found)
        return found;
    }
    return {};
  }
};

Loader::Loader(std::vector<std::filesystem::path> roots, Providers providers)
    : state_(std::make_unique<State>()) {
  state_->providers = std::move(providers);
  if (roots.empty() || roots.size() > 16)
    throw ElfError("guest library directory count limit");
  for (const auto &root : roots) {
    auto canonical = std::filesystem::canonical(root);
    if (!std::filesystem::is_directory(canonical))
      throw ElfError("guest search root is not a directory");
    if (std::find(state_->roots.begin(), state_->roots.end(), canonical) ==
        state_->roots.end())
      state_->roots.push_back(canonical);
  }
  for (const auto &pair : state_->providers.modules()) {
    auto module = std::make_unique<Module>();
    module->key = "provider:" + pair.first;
    module->name = pair.first;
    module->provider = &pair.second;
    module->global = pair.second.global;
    module->status = Module::Status::Ready;
    auto *pointer = module.get();
    state_->modules.emplace(module->key, std::move(module));
    state_->aliases.emplace(pointer->name, pointer);
    state_->order.push_back(pointer);
  }
}
Loader::~Loader() {
  try {
    for (auto it = state_->preunmap.rbegin(); it != state_->preunmap.rend(); ++it) (*it)();
    if (state_->finalizer) state_->finalizer();
    for(auto it=state_->postfinalizer.rbegin();it!=state_->postfinalizer.rend();++it)(*it)();
  }
  catch (const std::exception &error) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL pre-unmap finalization failure: %s\n", error.what());
    std::abort();
  } catch (...) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL unknown pre-unmap finalization failure\n");
    std::abort();
  }
}
void Loader::set_finalizer(std::function<void()> finalizer) {
  if (!finalizer || state_->finalizer) throw ElfError("invalid or duplicate guest Loader finalizer");
  state_->finalizer = std::move(finalizer);
}
void Loader::add_preunmap_hook(std::function<void()> hook) {
  if (!hook || state_->preunmap.size() >= 16) throw ElfError("invalid or excessive guest pre-unmap hooks");
  state_->preunmap.push_back(std::move(hook));
}
void Loader::add_postfinalizer_hook(std::function<void()> hook) {
  if(!hook || state_->postfinalizer.size()>=16)throw ElfError("invalid or excessive guest post-finalizer hooks");
  state_->postfinalizer.push_back(std::move(hook));
}
Loader::Module *Loader::link(const std::filesystem::path &path, bool global) {
  std::vector<std::string> added;
  try {
    auto *root = state_->discover(path, added, 0);
    if (root->status != Module::Status::Mapped) {
      if (global)
        for (auto *module : state_->breadth(root))
          module->global = true;
      return root;
    }
    auto local = state_->breadth(root);
    std::vector<Module *> scope;
    for (auto *module : state_->order)
      if (module->global)
        scope.push_back(module);
    for (auto *module : local)
      if ((module->global || (module == root && global)) &&
          std::find(scope.begin(), scope.end(), module) == scope.end())
        scope.push_back(module);
    for (auto *module : local)
      if (std::find(scope.begin(), scope.end(), module) == scope.end())
        scope.push_back(module);
    std::vector<BindingRecord> records;
    std::map<Module *, std::vector<uintptr_t>> values;
    size_t missing = 0, record_bytes = 0;
    std::string diagnostic;
    for (auto *module : local) {
      if (!module->image || module->status != Module::Status::Mapped)
        continue;
      const auto &elf = module->image->elf();
      for (const auto &requirement : elf.requirements) {
        Module *target = nullptr;
        for (auto *dependency : module->dependencies)
          if (dependency->name == requirement.library)
            target = dependency;
        if (!target || !state_->defines_version(target, requirement.name))
          throw ElfError("guest dependency version is unavailable: " +
                         requirement.library + "@" + requirement.name);
      }
      auto &bindings = values[module];
      bindings.resize(elf.symbols.size());
      for (size_t i = 0; i < elf.symbols.size(); ++i) {
        const auto &symbol = elf.symbols[i];
        auto binding = state_->resolve(module, i, scope);
        if (!binding.found && (symbol.info >> 4) != 2) {
          ++missing;
          if (diagnostic.size() < 60000)
            diagnostic +=
                "\n" + module->name + ": " + symbol.name +
                (symbol.version.name.empty() ? "" : "@" + symbol.version.name);
        }
        if (!binding.found && (symbol.info >> 4) == 2)
          binding.abi = "unresolved-weak-null";
        bindings[i] = binding.address;
        if (!symbol.name.empty() && ((symbol.info >> 4) != 0)) {
          record_bytes += module->name.size() + symbol.name.size() +
                          symbol.version.name.size() + binding.provider.size() +
                          binding.abi.size();
          if (records.size() + state_->records.size() >= 200000 ||
              record_bytes + state_->record_bytes > (64ull << 20))
            throw ElfError("guest binding evidence memory limit");
          records.push_back({module->name, symbol.name, symbol.version.name,
                             std::move(binding)});
        }
      }
    }
    if (missing)
      throw ElfError("unresolved guest symbols: " + std::to_string(missing) +
                     diagnostic);
    for (auto &pair : values)
      pair.first->image->relocate(
          [&](size_t index) { return pair.second.at(index); });
    for (auto &pair : values) {
      pair.first->image->seal();
      pair.first->status = Module::Status::Bound;
    }
    // Commit only fully bound images; constructors belong to open().
    state_->order.reserve(state_->order.size() + local.size());
    state_->records.reserve(state_->records.size() + records.size());
    if (global)
      for (auto *module : local)
        module->global = true;
    for (auto *module : local)
      if (std::find(state_->order.begin(), state_->order.end(), module) ==
          state_->order.end())
        state_->order.push_back(module);
    state_->records.insert(state_->records.end(),
                           std::make_move_iterator(records.begin()),
                           std::make_move_iterator(records.end()));
    state_->record_bytes += record_bytes;
    return root;
  } catch (...) {
    // Constructor failures abort; only pre-initialization work reaches
    // rollback.
    for (auto it = added.rbegin(); it != added.rend(); ++it) {
      auto found = state_->modules.find(*it);
      if (found == state_->modules.end())
        continue;
      state_->aliases.erase(found->second->name);
      state_->modules.erase(found);
    }
    throw;
  }
}
Loader::Module *Loader::open(const std::filesystem::path &path, bool global) {
  prepare_guest_tls();
  Module *root = link(path, global);
  std::function<void(Module *)> initialize = [&](Module *module) {
    if (module->status == Module::Status::Ready ||
        module->status == Module::Status::Initializing)
      return;
    module->status = Module::Status::Initializing;
    for (auto *dependency : module->dependencies)
      initialize(dependency);
    if (module->image)
      state_->constructors += module->image->initialize();
    module->status = Module::Status::Ready;
  };
  bool previous = state_->initializing;
  state_->initializing = true;
  try {
    initialize(root);
  } catch (const std::exception &error) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL constructor failure: %s\n",
                 error.what());
    std::abort();
  } catch (...) {
    std::fprintf(stderr, "SHIRAZ_GUEST_FATAL unknown constructor failure\n");
    std::abort();
  }
  state_->initializing = previous;
  return root;
}

bool Loader::valid_handle(const void *handle) const {
  for (const auto &pair : state_->modules)
    if (pair.second.get() == handle)
      return true;
  return false;
}
Loader::Module *Loader::loaded(const std::filesystem::path &path, bool global) {
  Module *result = nullptr;
  if (!path.has_parent_path()) {
    auto alias = state_->aliases.find(path.string());
    if (alias != state_->aliases.end()) result = alias->second;
  }
  if (!result) {
    auto canonical = state_->file(path);
    auto found = state_->modules.find(canonical.string());
    if (found != state_->modules.end()) result = found->second.get();
  }
  if (result && global)
    for (auto *module : state_->breadth(result)) module->global = true;
  return result;
}
Binding Loader::global_symbol(const std::string &name, const std::string &version) const {
  if (name.empty()) throw ElfError("empty guest global symbol");
  for (auto *module : state_->order) {
    if (!module->global) continue;
    auto result = state_->lookup(module, name, version, SymbolKind::Unknown);
    if (result.found) return result;
  }
  throw ElfError("guest global export not found: " + name + (version.empty() ? "" : "@" + version));
}
LoadedAddressInfo Loader::address_info(const void *pointer) const {
  uintptr_t value = reinterpret_cast<uintptr_t>(pointer);
  for (auto *module : state_->order) {
    if (!module->image) continue;
    const auto &elf = module->image->elf();
    uintptr_t bias = module->image->load_bias();
    bool contains = false;
    for (const auto &segment : elf.segments) {
      uintptr_t begin = bias + segment.address;
      if (value >= begin && value - begin < segment.memory_size) contains = true;
    }
    if (!contains) continue;
    LoadedAddressInfo result{true, bias + elf.first_page, 0, module->key, {}};
    for (size_t i = 0; i < elf.symbols.size(); ++i) {
      const auto &symbol = elf.symbols[i];
      if (!symbol.section || symbol.section == 0xfff1 || symbol.name.empty() ||
          (symbol.version.index == 0) || (symbol.visibility & 3) == 2) continue;
      uintptr_t address = module->image->address(i);
      if (value >= address && (symbol.size ? value - address < symbol.size : value == address) &&
          (!result.symbol_address || address > result.symbol_address)) {
        result.symbol_address = address;
        result.symbol = symbol.name;
      }
    }
    return result;
  }
  return {};
}
std::vector<LoadedModuleInfo> Loader::module_info() const {
  std::vector<LoadedModuleInfo> result;
  uint64_t adds = image_count();
  for (auto *module : state_->order) {
    if (!module->image) continue;
    const auto &elf = module->image->elf();
    result.push_back({module->image->load_bias(), module->key, elf.program_headers,
                      elf.program_header_count, adds, 0, module->image->metrics()});
  }
  return result;
}
Binding Loader::symbol(Module *handle, const std::string &name,
                       const std::string &version) const {
  if (!valid_handle(handle) || name.empty())
    throw ElfError("invalid guest handle/symbol");
  for (auto *module : state_->breadth(handle)) {
    auto result = state_->lookup(module, name, version, SymbolKind::Unknown);
    if (result.found)
      return result;
  }
  throw ElfError("guest export not found: " + name +
                 (version.empty() ? "" : "@" + version));
}
bool Loader::contains_code(const void *pointer) const {
  for (const auto &pair : state_->modules)
    if (pair.second->image && pair.second->image->contains_code(pointer))
      return true;
  return false;
}
std::function<bool(const void *)> Loader::code_validator() const {
  std::vector<const Image *> images;
  for (const auto &pair : state_->modules)
    if (pair.second->image)
      images.push_back(pair.second->image.get());
  return [images = std::move(images)](const void *pointer) {
    for (auto *image : images)
      if (image->contains_code(pointer)) return true;
    return false;
  };
}
std::function<bool(const void *)> Loader::code_validator(const std::string &module) const {
  const Image *image = nullptr;
  for (const auto &pair : state_->modules)
    if (pair.second->name == module && pair.second->image) {
      image = pair.second->image.get();
      break;
    }
  return [image](const void *pointer) {
    return image && image->contains_code(pointer);
  };
}
size_t Loader::image_count() const {
  size_t count = 0;
  for (const auto &pair : state_->modules)
    count += pair.second->image != nullptr;
  return count;
}
size_t Loader::constructor_count() const { return state_->constructors; }
size_t Loader::rewritten_thread_pointers() const {
  size_t count = 0;
  for (const auto &pair : state_->modules)
    if (pair.second->image) count += pair.second->image->rewritten_thread_pointers();
  return count;
}
const std::vector<BindingRecord> &Loader::bindings() const {
  return state_->records;
}
} // namespace shiraz::guest
