// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/image.hpp"
#include <filesystem>
#include <map>
#include <memory>
#include <set>

namespace shiraz::guest {
enum class SymbolKind { Unknown, Function, Object };
struct Binding {
  uintptr_t address = 0;
  std::string provider, abi;
  SymbolKind kind = SymbolKind::Unknown;
  bool found = false;
  // Explicit provider contract, never inferred for an ELF symbol by name.
  bool nonreturning = false;
};
struct ProviderExport {
  std::string name, version;
  uintptr_t address;
  SymbolKind kind;
  std::string abi;
  bool hidden = false;
  bool nonreturning = false;
};
struct Provider {
  std::string soname;
  std::set<std::string> versions;
  std::vector<ProviderExport> exports;
  bool global = false;
};
class Providers {
public:
  void add(Provider provider);
  const std::map<std::string, Provider> &modules() const { return modules_; }

private:
  std::map<std::string, Provider> modules_;
};
struct BindingRecord {
  std::string module, symbol, version;
  Binding binding;
};
struct LoadedModuleInfo {
  uintptr_t load_bias = 0;
  std::string name;
  std::vector<uint8_t> program_headers;
  uint16_t program_header_count = 0;
  uint64_t adds = 0, subs = 0;
  ImageMetrics metrics;
};
struct RelocationSnapshot {
  const Elf &elf;
  const std::vector<uint64_t> &values;
  const ImageMetrics &metrics;
};
struct LoadedAddressInfo {
  bool found = false;
  uintptr_t base = 0, symbol_address = 0;
  std::string name, symbol;
};
// A guest-only namespace, serialized by its owner. Nothing here calls dyld.
// Code stays resident for the Loader lifetime; returned pointers share that
// lifetime.
class Loader {
public:
  struct Module;
  Loader(std::vector<std::filesystem::path> roots, Providers providers = {});
  ~Loader();
  Loader(const Loader &) = delete;
  Loader &operator=(const Loader &) = delete;
  // Single mandatory callback drain, run before any Image/namespace teardown.
  // Failure aborts rather than unmapping code with pending callbacks.
  // A lifecycle driver quiesces its callbacks, calls finalize_images(), then
  // drains remaining registrations. Other drains run after ELF finalizers.
  void set_finalizer(std::function<void()> finalizer, bool drives_images = false);
  // Stop resources before closing lifecycle callback admission: a stop hook
  // may join a native worker that is finishing a guest lifecycle drain.
  void stop_native_callbacks();
  // Stop native callbacks once, then run ELF destructors for completed
  // initializations while dependencies remain resident. Also used by the
  // lifecycle drain on normal guest exit. Closes new initialization admission.
  void finalize_images();
  // Native resources with guest callback pointers must stop delivery before
  // guest finalizers and Image destruction. Hooks run in reverse order.
  void add_preunmap_hook(std::function<void()> hook);
  // Drain/check state after guest finalizers, before any image is destroyed.
  void add_postfinalizer_hook(std::function<void()> hook);
  // Link-only inspection does not execute constructors or claim an app runs.
  Module *link(const std::filesystem::path &path, bool global = false);
  Module *open(const std::filesystem::path &path, bool global = false);
  Binding symbol(Module *handle, const std::string &name,
                 const std::string &version = {}) const;
  // Existing guest objects only: never discovers a file or executes init.
  Module *loaded(const std::filesystem::path &path, bool global = false);
  Binding global_symbol(const std::string &name, const std::string &version = {}) const;
  LoadedAddressInfo address_info(const void *pointer) const;
  std::vector<LoadedModuleInfo> module_info() const;
  std::vector<uint64_t> verified_mrs_window_sites(const std::string &module_path) const;
  bool valid_handle(const void *handle) const;
  bool contains_code(const void *pointer) const;
  // Immutable snapshot for callback validation during constructors on other
  // threads. Images must remain resident for the returned callable's lifetime.
  std::function<bool(const void *)> code_validator() const;
  // Exact module only, excluding its dependency lookup scope. Same lifetime
  // rules as the all-image snapshot; an absent module always rejects.
  std::function<bool(const void *)> code_validator(const std::string &module) const;
  size_t image_count() const;
  size_t constructor_count() const;
  size_t rewritten_thread_pointers() const;
  const std::vector<BindingRecord> &bindings() const;
  // Caller serializes namespace access and retains the Loader/Image lifetime.
  RelocationSnapshot relocation_snapshot(const std::string &module_path) const;

private:
  struct State;
  std::unique_ptr<State> state_;
};
} // namespace shiraz::guest
