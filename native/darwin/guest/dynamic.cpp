// SPDX-License-Identifier: GPL-3.0-only
// ABI derived from pinned android-14.0.0_r1 bionic dlfcn.h and link.h.
// This implementation is original; it neither includes Darwin dlfcn.h nor
// calls dyld. All guest lookups remain inside the explicit Loader namespace.
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <climits>
#include <cstring>
#include <map>
#include <set>

namespace shiraz::guest {
namespace {
constexpr int kLazy = 1, kNow = 2, kNoLoad = 4, kGlobal = 0x100, kNoDelete = 0x1000;
struct Error { std::array<char, 1024> text{}; bool pending = false; };
thread_local Error error;
void set_error(const char *text) {
  const char *value = text ? text : "guest dynamic loader failure";
  size_t length = strnlen(value, error.text.size() - 1);
  std::memcpy(error.text.data(), value, length);
  error.text[length] = 0;
  error.pending = true;
}
std::string bounded_string(const char *text, const char *description) {
  if (!text) throw ElfError(std::string("null guest ") + description);
  size_t length = strnlen(text, 4096);
  if (!length || length == 4096) throw ElfError(std::string("invalid guest ") + description);
  return {text, length};
}
struct DlInfo {
  const char *name;
  void *base;
  const char *symbol;
  void *symbol_address;
};
struct PhdrInfo {
  uint64_t address;
  const char *name;
  const void *headers;
  uint16_t count;
  uint8_t padding[6]{};
  uint64_t adds, subs, tls_modid;
  void *tls_data;
};
static_assert(sizeof(DlInfo) == 32 && sizeof(PhdrInfo) == 64);
static_assert(offsetof(PhdrInfo, count) == 24 && offsetof(PhdrInfo, adds) == 32 &&
              offsetof(PhdrInfo, tls_data) == 56);
std::atomic<uintptr_t> next_handle{0x10001};
class Dynamic {
  struct Handle { Loader::Module *module; uint32_t references; bool scope; };
  DynamicHooks hooks_;
  std::map<uintptr_t, Handle> handles_;
  std::set<std::string> strings_;
  size_t string_bytes_ = 0;
  Loader &loader() const {
    auto *value = hooks_.get_loader();
    if (!value) throw ElfError("guest dynamic loader is unavailable");
    return *value;
  }
  const char *intern(const std::string &value) {
    auto existing = strings_.find(value);
    if (existing != strings_.end()) return existing->c_str();
    if (strings_.size() >= 16384 || value.size() + 1 > (4u << 20) - string_bytes_)
      throw ElfError("guest dladdr string limit");
    auto inserted = strings_.insert(value);
    string_bytes_ += value.size() + 1;
    return inserted.first->c_str();
  }
public:
  explicit Dynamic(DynamicHooks hooks) : hooks_(std::move(hooks)) {
    if (!hooks_.get_loader || !hooks_.mutex || !hooks_.refresh_code_validation)
      throw ElfError("guest dynamic loader requires lifetime, lock and code-publication hooks");
  }
  std::recursive_mutex &mutex() { return *hooks_.mutex; }
  void *open(const char *filename, int flags) {
    int mode = flags & (kLazy | kNow);
    if ((mode != kLazy && mode != kNow) ||
        (flags & ~(kLazy | kNow | kNoLoad | kGlobal | kNoDelete)))
      throw ElfError("unsupported guest dlopen flags");
    auto &owner = loader();
    Loader::Module *module = nullptr;
    if (filename) {
      auto path = bounded_string(filename, "dlopen filename");
      if (flags & kNoLoad) {
        module = owner.loaded(path, flags & kGlobal);
        if (!module) throw ElfError("guest RTLD_NOLOAD object is not loaded: " + path);
      } else {
        module = owner.link(path, flags & kGlobal);
        hooks_.refresh_code_validation();
        owner.open(path, flags & kGlobal);
      }
    }
    for (auto &pair : handles_)
      if (pair.second.module == module && pair.second.scope == !filename) {
        if (pair.second.references == UINT32_MAX) throw ElfError("guest dlopen reference limit");
        ++pair.second.references;
        return reinterpret_cast<void *>(pair.first);
      }
    if (handles_.size() >= 4096) throw ElfError("guest dlopen handle limit");
    uintptr_t token = next_handle.fetch_add(16, std::memory_order_relaxed);
    if (token > uintptr_t(INTPTR_MAX) - 16) throw ElfError("guest dlopen token limit");
    handles_.emplace(token, Handle{module, 1, !filename});
    return reinterpret_cast<void *>(token);
  }
  int close(void *opaque) {
    (void)loader();
    auto found = handles_.find(reinterpret_cast<uintptr_t>(opaque));
    if (found == handles_.end()) throw ElfError("invalid guest dlclose handle");
    if (!--found->second.references) handles_.erase(found);
    return 0;
  }
  void *symbol(void *opaque, const char *name, const char *version) {
    auto &owner = loader();
    auto key = bounded_string(name, "dlsym symbol");
    std::string requested = version ? bounded_string(version, "dlvsym version") : std::string{};
    if (reinterpret_cast<uintptr_t>(opaque) == UINTPTR_MAX)
      throw ElfError("guest RTLD_NEXT requires caller provenance and is unsupported");
    bool global = !opaque;
    Loader::Module *module = nullptr;
    if (opaque) {
      auto found = handles_.find(reinterpret_cast<uintptr_t>(opaque));
      if (found == handles_.end()) throw ElfError("invalid guest dlsym handle");
      global = found->second.scope;
      module = found->second.module;
    }
    auto binding = global ? owner.global_symbol(key, requested) : owner.symbol(module, key, requested);
    return reinterpret_cast<void *>(binding.address);
  }
  void *versioned_symbol(void *opaque, const char *name, const char *version) {
    (void)bounded_string(version, "dlvsym version");
    return symbol(opaque, name, version);
  }
  int address(const void *pointer, DlInfo *output) {
    if (!pointer || !output) throw ElfError("invalid guest dladdr argument");
    auto found = loader().address_info(pointer);
    if (!found.found) return 0;
    DlInfo result{intern(found.name), reinterpret_cast<void *>(found.base),
                  found.symbol.empty() ? nullptr : intern(found.symbol),
                  reinterpret_cast<void *>(found.symbol_address)};
    std::memcpy(output, &result, sizeof(result));
    return 1;
  }
  int iterate(void *callback, void *data) {
    auto &owner = loader();
    if (!callback || !owner.contains_code(callback)) throw ElfError("invalid guest PHDR callback");
    auto snapshot = owner.module_info();
    for (const auto &module : snapshot) {
      if (!module.program_header_count || module.program_header_count > 128 ||
          module.program_headers.size() != size_t(module.program_header_count) * 56)
        throw ElfError("invalid guest PHDR snapshot");
      // PT_TLS is rejected by parse_elf, so these images really have no ELF
      // TLS module/data. Compiler emutls is unrelated to dlpi_tls_modid.
      PhdrInfo info{module.load_bias, module.name.c_str(), module.program_headers.data(),
                    module.program_header_count, {}, module.adds, module.subs, 0, nullptr};
      prepare_guest_tls();
      auto result = invoke(callback, {
          Argument::integer(reinterpret_cast<uintptr_t>(&info), ArgumentType::Int64),
          Argument::integer(sizeof(info), ArgumentType::Int64),
          Argument::integer(reinterpret_cast<uintptr_t>(data), ArgumentType::Int64)});
      int value = int(int32_t(result.result_integers[0]));
      if (value) return value;
    }
    return 0;
  }
};
template <typename R, typename... Args>
ProviderExport method(const std::shared_ptr<Dynamic> &state, const char *name,
                      R (Dynamic::*function)(Args...)) {
  void *entry = closure([state, function, zero_failure = std::string(name) == "dladdr"](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> arguments{cursor.read<Args>()...};
    HostErrno preserve;
    try {
      std::lock_guard<std::recursive_mutex> lock(state->mutex());
      R result = std::apply([&](Args... values) { return ((*state).*function)(values...); }, arguments);
      return_value(capture, result);
    } catch (const std::exception &failure) {
      set_error(failure.what());
      if constexpr (std::is_pointer_v<R>) return_value(capture, static_cast<R>(nullptr));
      else return_value(capture, zero_failure ? R(0) : R(-1));
    }
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function,
          "guest-namespace/no-host-dyld", false};
}
} // namespace
Providers dynamic_personality(Providers providers, DynamicHooks hooks) {
  auto state = std::make_shared<Dynamic>(std::move(hooks));
  Provider module{"libdl.so", {"LIBC", "LIBC_N"}, {}, true};
  module.exports.push_back(method(state, "dlopen", &Dynamic::open));
  module.exports.push_back(method(state, "dlclose", &Dynamic::close));
  module.exports.push_back(method(state, "dladdr", &Dynamic::address));
  module.exports.push_back(method(state, "dl_iterate_phdr", &Dynamic::iterate));
  auto symbol = method(state, "dlvsym", &Dynamic::versioned_symbol);
  symbol.version = "LIBC_N";
  module.exports.push_back(std::move(symbol));
  void *lookup = closure([state](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    void *handle = cursor.read<void *>();
    const char *name = cursor.read<const char *>();
    HostErrno preserve;
    try {
      std::lock_guard<std::recursive_mutex> lock(state->mutex());
      return_value(capture, state->symbol(handle, name, nullptr));
    } catch (const std::exception &failure) { set_error(failure.what()); return_value(capture, nullptr); }
  });
  module.exports.push_back({"dlsym", "LIBC", reinterpret_cast<uintptr_t>(lookup), SymbolKind::Function,
                            "guest-namespace/no-host-dyld", false});
  void *errors = closure([](Capture &capture) {
    HostErrno preserve;
    const char *result = error.pending ? error.text.data() : nullptr;
    error.pending = false;
    return_value(capture, result);
  });
  module.exports.push_back({"dlerror", "LIBC", reinterpret_cast<uintptr_t>(errors), SymbolKind::Function,
                            "thread-local/consuming-error", false});
  providers.add(std::move(module));
  return providers;
}
} // namespace shiraz::guest
