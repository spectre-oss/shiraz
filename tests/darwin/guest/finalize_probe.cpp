// SPDX-License-Identifier: GPL-3.0-only
// First-party ELF teardown regression; no APK execution or parity claim.
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include <algorithm>
#include <cstring>
#include <fstream>
#include <iostream>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <thread>

using namespace shiraz::guest;
namespace {
void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
struct Record {
  std::vector<int> events;
  Loader *loader = nullptr;
  const void *root_code = nullptr, *dep_code = nullptr;
  unsigned errors = 0;
  unsigned prehooks = 0, refused_reopens = 0;
  unsigned refused_hooks = 0;
  bool reopen_link_only = false;
  std::mutex mutex;
  std::condition_variable changed;
  bool hold_selected = false, selected_entered = false, release_selected = false;
  bool drain_started = false, hook_seen = false;
  bool stop_worker_done = false;
  unsigned shutdown_refusals = 0;
  std::weak_ptr<Lifecycle> lifecycle;
  bool check_foreign_drains = false;
  unsigned foreign_drain_refusals = 0, foreign_shutdown_refusals = 0;
  bool foreign_worker_done = false;
};
Provider record_provider(const std::shared_ptr<Record> &record) {
  auto address = closure([record](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    const auto value = cursor.read<int>();
    record->events.push_back(value);
    if (record->loader && (value == 112 || value == 111 || value == 120 ||
        value == 212 || value == 211 || value == 220 || value == 190)) {
      if (record->prehooks != 1) ++record->errors;
      if (record->hold_selected) {
        std::lock_guard<std::mutex> lock(record->mutex);
        if (!record->release_selected) ++record->errors;
      }
    }
    if (value == 112 && record->reopen_link_only) {
      try { record->loader->open("libfinalize-root.so"); ++record->errors; }
      catch (const ElfError &error) {
        if (std::strcmp(error.what(), "guest initialization during finalization is not implemented"))
          ++record->errors;
        else ++record->refused_reopens;
      }
      record->loader->finalize_images(); // Reentry must not recurse into dependencies.
    }
    if (record->loader && (record->loader->image_count() != 2 ||
        (record->root_code && !record->loader->contains_code(record->root_code)) ||
        (record->dep_code && !record->loader->contains_code(record->dep_code)))) ++record->errors;
    if (value == 230 && record->hold_selected) {
      std::unique_lock<std::mutex> lock(record->mutex);
      record->selected_entered = true;
      record->changed.notify_all();
      record->changed.wait(lock, [&] { return record->release_selected; });
    }
    if (value == 212 && record->check_foreign_drains) {
      auto lifecycle = record->lifecycle.lock();
      require(bool(lifecycle), "finalizer lifecycle remains resident");
      std::thread worker([record, lifecycle] {
        try { lifecycle->finalize(reinterpret_cast<void *>(uintptr_t(0x12345))); ++record->errors; }
        catch (const ElfError &error) {
          if (std::strcmp(error.what(), "foreign guest lifecycle drain after shutdown admission closed"))
            ++record->errors;
          else ++record->foreign_drain_refusals;
        }
        try { lifecycle->shutdown(); ++record->errors; }
        catch (const ElfError &error) {
          if (std::strcmp(error.what(), "concurrent guest lifecycle shutdown after callback admission closed"))
            ++record->errors;
          else ++record->foreign_shutdown_refusals;
        }
        std::lock_guard<std::mutex> lock(record->mutex);
        record->foreign_worker_done = true;
        record->changed.notify_all();
      });
      bool done;
      {
        std::unique_lock<std::mutex> lock(record->mutex);
        done = record->changed.wait_for(lock, std::chrono::seconds(2), [&] { return record->foreign_worker_done; });
      }
      if (!done) {
        worker.detach();
        std::cerr << "M5_ELF_FINALIZE_FAIL foreign finalizer worker lifecycle operation blocked\n";
        throw ElfError("foreign finalizer worker lifecycle operation blocked");
      }
      worker.join();
    }
  });
  return {"libfinalize-host.so", {}, {{"finalize_record", "", reinterpret_cast<uintptr_t>(address),
          SymbolKind::Function, "fixture/finalizer-event-recorder", false}}, false};
}
Providers providers_for(const std::shared_ptr<Lifecycle> &lifecycle,
                        const std::shared_ptr<Record> &record) {
  Providers providers;
  providers.add({"libc.so", {"LIBC"}, lifecycle->exports(), false});
  providers.add(record_provider(record));
  return providers;
}
void loader_case(const std::filesystem::path &directory, bool initialize, bool early,
                 bool shutdown_first = false, bool check_foreign_drains = false) {
  auto record = std::make_shared<Record>();
  auto lifecycle = std::make_shared<Lifecycle>([record](const void *p) {
    return record->loader && record->loader->contains_code(p);
  });
  auto loader = std::make_unique<Loader>(std::vector<std::filesystem::path>{directory},
                                        providers_for(lifecycle, record));
  record->loader = loader.get();
  record->lifecycle = lifecycle;
  record->check_foreign_drains = check_foreign_drains;
  lifecycle->attach(*loader);
  loader->add_preunmap_hook([record] {
    ++record->prehooks;
    try { record->loader->add_preunmap_hook([record] { ++record->errors; }); ++record->errors; }
    catch (const ElfError &error) {
      if (std::strcmp(error.what(), "guest pre-unmap hook registration after shutdown admission closed"))
        ++record->errors;
      else ++record->refused_hooks;
    }
  });
  auto *root = loader->link("libfinalize-root.so");
  require(loader->image_count() == 2 && record->events.empty(), "link-only group is resident without callbacks");
  record->root_code = reinterpret_cast<void *>(loader->symbol(root, "finalize_root_alive").address);
  record->dep_code = reinterpret_cast<void *>(loader->symbol(root, "finalize_dep_ping").address);
  if (initialize) {
    require(loader->open("libfinalize-root.so") == root, "open reuses linked group");
    require(record->events == std::vector<int>({101, 201}) && lifecycle->pending() == 4,
            "dependency initializes before root and registers real callbacks");
    require(loader->open("libfinalize-root.so") == root && loader->constructor_count() == 2 &&
            record->events.size() == 2, "repeated open never reruns constructors");
    if (early) {
      auto entry = loader->symbol(root, "finalize_root_selected");
      invoke(reinterpret_cast<void *>(entry.address), {});
      invoke(reinterpret_cast<void *>(entry.address), {});
      require(record->events == std::vector<int>({101, 201, 230}), "early DSO finalization is exactly once");
    }
  }
  if (shutdown_first) lifecycle->shutdown();
  loader.reset();
  record->loader = nullptr;
  require(lifecycle->closed() && !lifecycle->pending() && !record->errors && record->prehooks == 1 &&
          record->refused_hooks == 1,
          "all images stay resident through array and lifecycle callbacks");
  if (check_foreign_drains)
    require(record->foreign_drain_refusals == 1 && record->foreign_shutdown_refusals == 1,
            "foreign lifecycle operations during FINI fail explicitly without a join cycle");
  if (initialize) {
    std::vector<int> expected{101, 201};
    if (early) expected.push_back(230);
    for (int value : {212, 190, 211}) expected.push_back(value);
    if (!early) expected.push_back(230);
    for (int value : {220, 112, 111, 130, 120, 132, 232, 231, 131}) expected.push_back(value);
    require(record->events == expected, "reverse init order, reverse FINI_ARRAY then FINI, then lifecycle LIFO");
    const auto before = record->events;
    lifecycle->shutdown();
    lifecycle->finalize();
    require(record->events == before, "closed lifecycle cannot double-finalize DSO callbacks");
  } else require(record->events.empty(), "link-only images never run ELF finalizers");
}
void reentry_case(const std::filesystem::path &directory) {
  auto record = std::make_shared<Record>();
  auto lifecycle = std::make_shared<Lifecycle>([record](const void *p) {
    return record->loader && record->loader->contains_code(p);
  });
  auto loader = std::make_unique<Loader>(std::vector<std::filesystem::path>{directory},
                                        providers_for(lifecycle, record));
  record->loader = loader.get();
  record->reopen_link_only = true;
  lifecycle->attach(*loader);
  loader->add_preunmap_hook([record] { ++record->prehooks; });
  loader->link("libfinalize-root.so");
  loader->open("libfinalize-dep.so");
  require(record->events == std::vector<int>({101}), "root remains link-only before teardown");
  loader.reset();
  record->loader = nullptr;
  require(record->events == std::vector<int>({101,112,111,130,120,132,131}) &&
          !record->errors && record->refused_reopens == 1 && record->prehooks == 1,
          "finalizer refuses link-only initialization and recursive drain is idempotent");
}
void quiescence_case(const std::filesystem::path &directory, bool destroy_loader) {
  auto record = std::make_shared<Record>();
  auto lifecycle = std::make_shared<Lifecycle>([record](const void *p) {
    return record->loader && record->loader->contains_code(p);
  });
  auto loader = std::make_unique<Loader>(std::vector<std::filesystem::path>{directory},
                                        providers_for(lifecycle, record));
  record->loader = loader.get();
  lifecycle->attach(*loader);
  loader->add_preunmap_hook([record] {
    std::lock_guard<std::mutex> lock(record->mutex);
    ++record->prehooks;
    record->hook_seen = true;
    record->changed.notify_all();
  });
  auto *root = loader->open("libfinalize-root.so");
  auto key_entry = loader->symbol(root, "finalize_root_dso_key");
  void *key = reinterpret_cast<void *>(invoke(reinterpret_cast<void *>(key_entry.address), {}).result_integers[0]);
  record->hold_selected = true;
  std::exception_ptr worker_error, drain_error;
  std::thread worker([&] {
    // Enter the registry directly from host code. Do not leave an uncounted
    // outer guest caller on this thread while the Loader is being destroyed.
    try { lifecycle->finalize(key); }
    catch (...) { worker_error = std::current_exception(); }
  });
  bool entered;
  {
    std::unique_lock<std::mutex> lock(record->mutex);
    entered = record->changed.wait_for(lock, std::chrono::seconds(5), [&] { return record->selected_entered; });
  }
  std::thread drain([&] {
    { std::lock_guard<std::mutex> lock(record->mutex); record->drain_started = true; record->changed.notify_all(); }
    try {
      if (destroy_loader) loader.reset();
      else lifecycle->shutdown();
    }
    catch (...) { drain_error = std::current_exception(); }
  });
  bool started;
  {
    std::unique_lock<std::mutex> lock(record->mutex);
    started = record->changed.wait_for(lock, std::chrono::seconds(5), [&] { return record->drain_started; });
    // Give an incorrect shutdown a bounded opportunity to call its hook while
    // the real guest lifecycle callback is held. Mutation checks cover this.
    record->changed.wait_for(lock, std::chrono::milliseconds(50), [] { return false; });
    record->release_selected = true;
    record->changed.notify_all();
  }
  worker.join();
  drain.join();
  require(entered && started && !worker_error && !drain_error && !record->errors &&
          record->prehooks == 1 && lifecycle->closed(),
          "active lifecycle callback completes before ELF finalizers");
  loader.reset();
  record->loader = nullptr;
}
void stop_drain_case(const std::filesystem::path &directory, bool shutdown_first, bool shutdown_worker = false) {
  auto record = std::make_shared<Record>();
  auto lifecycle = std::make_shared<Lifecycle>([record](const void *p) {
    return record->loader && record->loader->contains_code(p);
  });
  auto loader = std::make_unique<Loader>(std::vector<std::filesystem::path>{directory},
                                        providers_for(lifecycle, record));
  record->loader = loader.get();
  lifecycle->attach(*loader);
  loader->add_preunmap_hook([record, lifecycle, shutdown_worker] {
    ++record->prehooks;
    std::thread worker([record, lifecycle, shutdown_worker] {
      // A resource worker finishing a drain must be allowed to return before
      // the stop hook joins it. No matching registration is needed to expose
      // an admission gate that blocks too early.
      if (shutdown_worker) {
        try { lifecycle->shutdown(); ++record->errors; }
        catch (const ElfError &error) {
          if (std::strcmp(error.what(), "concurrent guest lifecycle shutdown during native callback stop"))
            ++record->errors;
          else ++record->shutdown_refusals;
        }
      } else {
        try { lifecycle->finalize(reinterpret_cast<void *>(uintptr_t(0x12345))); }
        catch (const ElfError &) { ++record->errors; }
      }
      std::lock_guard<std::mutex> lock(record->mutex);
      record->stop_worker_done = true;
      record->changed.notify_all();
    });
    bool done;
    {
      std::unique_lock<std::mutex> lock(record->mutex);
      done = record->changed.wait_for(lock, std::chrono::seconds(2), [&] { return record->stop_worker_done; });
    }
    if (!done) {
      worker.detach();
      std::cerr << "M5_ELF_FINALIZE_FAIL native stop hook lifecycle drain blocked\n";
      throw ElfError("native stop hook lifecycle drain blocked");
    }
    worker.join();
  });
  loader->open("libfinalize-root.so");
  if (shutdown_first) lifecycle->shutdown();
  loader.reset();
  record->loader = nullptr;
  require(!record->errors && record->prehooks == 1 && record->stop_worker_done && lifecycle->closed() &&
          record->shutdown_refusals == unsigned(shutdown_worker),
          "native stop worker drains before lifecycle admission closes");
}
std::vector<uint8_t> read(const std::filesystem::path &path) {
  std::ifstream file(path, std::ios::binary | std::ios::ate);
  const auto size = file.tellg();
  require(bool(file) && size > 0 && size < (1 << 20), "bounded finalizer fixture read");
  std::vector<uint8_t> bytes(static_cast<size_t>(size));
  file.seekg(0);
  require(bool(file.read(reinterpret_cast<char *>(bytes.data()), size)), "complete fixture read");
  return bytes;
}
uint64_t u(const std::vector<uint8_t> &bytes, size_t offset, size_t width = 8) {
  require(offset <= bytes.size() && width <= bytes.size() - offset, "fixture integer bounds");
  uint64_t value = 0;
  for (size_t i = 0; i < width; ++i) value |= uint64_t(bytes[offset + i]) << (i * 8);
  return value;
}
void put(std::vector<uint8_t> &bytes, size_t offset, uint64_t value) {
  require(offset <= bytes.size() && 8 <= bytes.size() - offset, "fixture patch bounds");
  for (size_t i = 0; i < 8; ++i) bytes[offset + i] = uint8_t(value >> (i * 8));
}
size_t file_offset(const Elf &elf, uint64_t address, size_t size) {
  for (const auto &segment : elf.segments)
    if (address >= segment.address && address - segment.address <= segment.file_size &&
        size <= segment.file_size - (address - segment.address))
      return segment.offset + address - segment.address;
  throw std::runtime_error("fixture virtual address lacks file bytes");
}
void unalign_array(std::vector<uint8_t> &bytes, bool fini) {
  const auto elf = parse_elf(bytes, 16384);
  const auto array = fini ? elf.fini_array : elf.init_array;
  require(array && (fini ? elf.fini_array_size : elf.init_array_size) >= 8,
          "fixture has requested array");
  uint64_t rela = 0, rela_size = 0;
  const auto phoff = u(bytes, 32);
  for (size_t i = 0; i < u(bytes, 56, 2); ++i) {
    const auto ph = phoff + i * 56;
    if (u(bytes, ph, 4) != 2) continue;
    const auto begin = u(bytes, ph + 8), size = u(bytes, ph + 32);
    for (size_t offset = begin; offset < begin + size; offset += 16) {
      if (u(bytes, offset) == 7) rela = u(bytes, offset + 8);
      if (u(bytes, offset) == 8) rela_size = u(bytes, offset + 8);
    }
  }
  require(rela && rela_size && !(rela_size % 24), "fixture RELA metadata");
  const auto begin = file_offset(elf, rela, rela_size);
  for (size_t offset = begin; offset < begin + rela_size; offset += 24) {
    if (u(bytes, offset) == array && u(bytes, offset + 8, 4) == 1027) {
      const auto original = u(bytes, offset + 16);
      require(!(original & 3), "fixture starts with aligned function addend");
      put(bytes, offset + 16, original | 1);
      return;
    }
  }
  throw std::runtime_error("fixture array lacks relative relocation");
}
void image_case(const std::filesystem::path &directory, int malformed) {
  auto bytes = read(directory / "libfinalize-dep.so");
  if (malformed) unalign_array(bytes, malformed == 2);
  Image image(bytes, Image::Mapping::Deferred);
  require(!image.rewritten_thread_pointers(), "unaligned-pointer fixture has no MRS rewrite dependency");
  auto record = std::make_shared<Record>();
  auto lifecycle = std::make_shared<Lifecycle>([&](const void *p) { return image.contains_code(p); });
  // The lifecycle's mandatory drain owner remains alive while image code is resident.
  auto owner = std::make_unique<Loader>(std::vector<std::filesystem::path>{directory});
  lifecycle->attach(*owner);
  const auto providers = providers_for(lifecycle, record);
  image.relocate([&](size_t index) {
    const auto &symbol = image.elf().symbols.at(index);
    if (symbol.section) return image.address(index);
    for (const auto &pair : providers.modules())
      for (const auto &entry : pair.second.exports)
        if (entry.name == symbol.name) return entry.address;
    throw ElfError("unexpected finalizer fixture import: " + symbol.name);
  });
  image.seal();
  require(!image.finalize(), "uninitialized image has no ELF finalizers");
  if (malformed == 1) {
    try { image.initialize(); throw std::runtime_error("unaligned constructor was accepted"); }
    catch (const ElfError &error) {
      require(std::strcmp(error.what(), "unaligned guest constructor pointer") == 0,
              "exact unaligned constructor error");
    }
    require(record->events.empty() && !image.finalize(), "failed initialization never executes finalizers");
  } else {
    require(image.initialize() == 1 && image.initialize() == 0, "Image initialization is exactly once");
    if (malformed == 2) {
      const auto before = record->events;
      try { image.finalize(); throw std::runtime_error("unaligned finalizer was accepted"); }
      catch (const ElfError &error) {
        require(std::strcmp(error.what(), "unaligned guest finalizer pointer") == 0,
                "exact unaligned finalizer error");
      }
      require(record->events == before, "all finalizer targets validate before any callback executes");
    } else {
      require(image.finalize() == 3 && image.finalize() == 0, "Image reverse-array plus FINI executes exactly once");
      require(record->events == std::vector<int>({101, 112, 111, 130, 120}), "direct Image finalizer order");
    }
  }
  owner.reset();
  require(lifecycle->closed() && !lifecycle->pending(), "direct Image callbacks drain before code destruction");
}
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: finalize-probe fixture-directory");
    loader_case(argv[1], false, false);
    loader_case(argv[1], true, false);
    loader_case(argv[1], true, true);
    loader_case(argv[1], true, false, true);
    loader_case(argv[1], true, false, false, true);
    reentry_case(argv[1]);
    quiescence_case(argv[1], false);
    quiescence_case(argv[1], true);
    stop_drain_case(argv[1], false);
    stop_drain_case(argv[1], true);
    stop_drain_case(argv[1], false, true);
    stop_drain_case(argv[1], true, true);
    image_case(argv[1], 0);
    image_case(argv[1], 1);
    image_case(argv[1], 2);
    std::cout << "M5_ELF_FINALIZE_PASS init_order=dependency_first fini_order=reverse_initialized "
                 "array_reverse=1 fini_after_array=1 dependency_live=1 all_images_resident=1 "
                 "lifecycle_open_during_fini=1 cxa_exactly_once=1 image_exactly_once=1 "
                 "link_only_skipped=1 unaligned_init_rejected=1 unaligned_fini_rejected=1 "
                 "fini_preflight_before_callbacks=1 shutdown_prehooks_once=1 link_only_reentry_refused=1 "
                 "recursive_drain=1 late_hook_refused=1 held_lifecycle_callback=2 stop_worker_drain=2 "
                 "stop_worker_shutdown_refused=2 "
                 "foreign_fini_worker_refused=2 "
                 "no_mrs_fixture=1 app_execution=0\n";
  } catch (const std::exception &error) {
    std::cerr << "M5_ELF_FINALIZE_FAIL " << error.what() << '\n';
    return 1;
  }
}
