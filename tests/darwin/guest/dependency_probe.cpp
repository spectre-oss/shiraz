// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include <fstream>
#include <functional>
#include <iostream>
#include <mach-o/dyld.h>
#include <unistd.h>

using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) {
  if (!value)
    throw std::runtime_error(message);
}
static int invoke_int(Loader &loader, Loader::Module *module,
                      const std::string &name,
                      const std::string &version = {}) {
  auto result = loader.symbol(module, name, version);
  require(result.kind == SymbolKind::Function,
          "expected guest function binding");
  return int(
      invoke(reinterpret_cast<void *>(result.address), {}).result_integers[0]);
}
static std::vector<uint8_t> read(const fs::path &path) {
  std::ifstream stream(path, std::ios::binary | std::ios::ate);
  auto size = stream.tellg();
  require(bool(stream) && size > 0 && size < (1 << 20), "fixture size/open");
  std::vector<uint8_t> bytes(static_cast<size_t>(size));
  stream.seekg(0);
  require(bool(stream.read(reinterpret_cast<char *>(bytes.data()), size)),
          "fixture read");
  return bytes;
}
static uint64_t u(const std::vector<uint8_t> &bytes, size_t offset,
                  unsigned width) {
  uint64_t value = 0;
  for (unsigned i = 0; i < width; ++i)
    value |= uint64_t(bytes.at(offset + i)) << (8 * i);
  return value;
}
static void put(std::vector<uint8_t> &bytes, size_t offset, uint64_t value,
                unsigned width) {
  for (unsigned i = 0; i < width; ++i)
    bytes.at(offset + i) = uint8_t(value >> (8 * i));
}
static void symbolic(const fs::path &path) {
  auto bytes = read(path);
  bool found = false;
  for (size_t i = 0; i < u(bytes, 56, 2); ++i) {
    size_t ph = u(bytes, 32, 8) + i * 56;
    if (u(bytes, ph, 4) != 2)
      continue;
    size_t begin = u(bytes, ph + 8, 8), size = u(bytes, ph + 32, 8);
    for (size_t offset = begin; offset < begin + size; offset += 16) {
      if (u(bytes, offset, 8) == 30) {
        put(bytes, offset + 8, u(bytes, offset + 8, 8) | 2, 8);
        found = true;
      }
    }
  }
  require(found, "fixture DT_FLAGS required");
  std::ofstream output(path, std::ios::binary | std::ios::trunc);
  require(
      bool(output.write(reinterpret_cast<char *>(bytes.data()), bytes.size())),
      "fixture write");
}
static size_t dynamic_value(const std::vector<uint8_t> &bytes,
                            uint64_t wanted) {
  for (size_t i = 0; i < u(bytes, 56, 2); ++i) {
    size_t ph = u(bytes, 32, 8) + i * 56;
    if (u(bytes, ph, 4) != 2)
      continue;
    size_t base = u(bytes, ph + 8, 8), size = u(bytes, ph + 32, 8);
    for (size_t p = base; p < base + size; p += 16)
      if (u(bytes, p, 8) == wanted)
        return p + 8;
  }
  throw std::runtime_error("version fixture lacks dynamic tag");
}
static size_t file_offset(const std::vector<uint8_t> &bytes, uint64_t value) {
  for (size_t i = 0; i < u(bytes, 56, 2); ++i) {
    size_t ph = u(bytes, 32, 8) + i * 56;
    uint64_t base = u(bytes, ph + 16, 8), size = u(bytes, ph + 32, 8);
    if (u(bytes, ph, 4) == 1 && value >= base && value - base < size)
      return u(bytes, ph + 8, 8) + value - base;
  }
  throw std::runtime_error("version fixture address lacks file data");
}
static size_t version_rejections(const fs::path &directory) {
  size_t rejected = 0;
  auto check = [&](const char *file,
                   const std::function<void(std::vector<uint8_t> &)> &mutate) {
    auto bytes = read(directory / file);
    mutate(bytes);
    try {
      parse_elf(bytes, 16384);
    } catch (const ElfError &) {
      ++rejected;
      return;
    }
    throw std::runtime_error("accepted malformed ELF version metadata");
  };
  check("libleaf.so",
        [](auto &b) { put(b, dynamic_value(b, 0x6ffffffd), 4097, 8); });
  check("libleaf.so",
        [](auto &b) { put(b, dynamic_value(b, 0x6ffffff0), 0, 8); });
  check("libleaf.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffffc), 8)) + 16, 0, 4);
  });
  check("libleaf.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffffc), 8)) + 12, 0, 4);
  });
  check("libleaf.so", [](auto &b) {
    auto p = file_offset(b, u(b, dynamic_value(b, 0x6ffffffc), 8)) + 8;
    put(b, p, u(b, p, 4) ^ 1, 4);
  });
  check("libleaf.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffff0), 8)) + 2, 0x7ffe,
        2);
  });
  check("libleft.so",
        [](auto &b) { put(b, dynamic_value(b, 0x6fffffff), 129, 8); });
  check("libleft.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffffe), 8)) + 2, 0, 2);
  });
  check("libleft.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffffe), 8)) + 4, 0, 4);
  });
  check("libleft.so", [](auto &b) {
    put(b, file_offset(b, u(b, dynamic_value(b, 0x6ffffffe), 8)) + 8, 0, 4);
  });
  check("libleft.so", [](auto &b) {
    auto v = file_offset(b, u(b, dynamic_value(b, 0x6ffffffe), 8));
    auto p = v + u(b, v + 8, 4);
    put(b, p, u(b, p, 4) ^ 1, 4);
  });
  check("libleft.so", [](auto &b) {
    auto v = file_offset(b, u(b, dynamic_value(b, 0x6ffffffe), 8));
    auto p = v + u(b, v + 8, 4);
    put(b, p + 12, 1, 4);
  });
  return rejected;
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: dependency-probe fixture-directory");
    fs::path fixtures = fs::canonical(argv[1]);
    fs::path work =
        fixtures.parent_path() / ("dep-cases-" + std::to_string(getpid()));
    require(fs::create_directory(work), "exclusive fixture work directory");
    auto copy = [&](const std::string &name,
                    const std::vector<std::string> &libraries) {
      fs::path directory = work / name;
      fs::create_directory(directory);
      for (const auto &library : libraries)
        fs::copy_file(fixtures / library, directory / library);
      return directory;
    };
    std::vector<std::string> graph{"libroot.so", "libleft.so", "libright.so",
                                   "libleaf.so"};
    require(version_rejections(fixtures) == 12,
            "malformed version rejection count");
    uint32_t before = _dyld_image_count();
    Loader loader({fixtures});
    auto *root = loader.link("libroot.so");
    require(loader.constructor_count() == 0,
            "link-only API must not run constructors");
    auto root_entry = reinterpret_cast<void *>(loader.symbol(root, "root_value").address);
    auto leaf_entry = reinterpret_cast<void *>(loader.symbol(root, "versioned_api").address);
    auto root_only = loader.code_validator("libroot.so");
    auto leaf_only = loader.code_validator("libleaf.so");
    auto absent = loader.code_validator("libabsent.so");
    require(root_only(root_entry) && !root_only(leaf_entry) &&
                leaf_only(leaf_entry) && !leaf_only(root_entry) &&
                !root_only(nullptr) && !absent(root_entry),
            "module code snapshot must exclude dependency scope and absent modules");
    require(loader.open("libroot.so") == root,
            "open initializes an already linked group");
    size_t expected_initializers = 0, executed_initializers = 0;
    for (const auto &info : loader.module_info()) {
      require(info.metrics.initialized, "module metrics require completed init");
      require(info.metrics.expected_constructors == info.metrics.executed_constructors,
              "ELF constructor metadata disagrees with executed count");
      expected_initializers += info.metrics.expected_constructors;
      executed_initializers += info.metrics.executed_constructors;
    }
    require(expected_initializers == 4 && executed_initializers == loader.constructor_count(),
            "per-image constructor metrics disagree with fixture/loader total");
    require(loader.image_count() == 4 && loader.constructor_count() == 4,
            "dependency graph and constructors");
    require(invoke_int(loader, root, "root_value") == 215,
            "root BFS/preemption and constructor order");
    require(invoke_int(loader, root, "weak_value") == 0,
            "unresolved weak symbol must be null");
    require(invoke_int(loader, root, "versioned_api") == 2,
            "default symbol version");
    require(invoke_int(loader, root, "versioned_api", "FIXTURE_OLD") == 1,
            "non-default symbol version");
    auto base_directory = copy("base-global", {"libleaf.so"});
    auto base_bytes = read(base_directory / "libleaf.so");
    auto base_elf = parse_elf(base_bytes, 16384);
    for (size_t i = 0; i < base_elf.symbols.size(); ++i)
      if (base_elf.symbols[i].name == "selected")
        put(base_bytes, file_offset(base_bytes, u(base_bytes,
            dynamic_value(base_bytes, 0x6ffffff0), 8)) + i * 2, 1, 2);
    {
      std::ofstream output(base_directory / "libleaf.so", std::ios::binary | std::ios::trunc);
      output.write(reinterpret_cast<const char *>(base_bytes.data()), base_bytes.size());
      require(bool(output), "write base-version fixture");
    }
    Loader base_loader({base_directory});
    auto *base_module = base_loader.open("libleaf.so");
    require(invoke_int(base_loader, base_module, "selected", "libleaf.so") == 33 &&
                invoke_int(base_loader, base_module, "selected", "UNKNOWN") == 33,
            "bionic base/unknown version requests match global index 1");
    bool exact_rejected = false;
    try { base_loader.symbol(base_module, "selected", "FIXTURE_1"); }
    catch (const ElfError &) { exact_rejected = true; }
    require(exact_rejected, "known non-base version requires exact symbol version");
    require(loader.open("libroot.so") == root &&
                loader.constructor_count() == 4,
            "idempotent load");
    bool missing = false;
    try {
      loader.open("libmissing.so");
    } catch (const ElfError &error) {
      missing = std::string(error.what()).find("missing_personality_service") !=
                std::string::npos;
    }
    require(missing && loader.image_count() == 4 &&
                loader.constructor_count() == 4,
            "failed load preserves ready modules");
    require(invoke_int(loader, root, "root_value") == 215,
            "existing module after rollback");
    Loader escape({fixtures});
    missing = false;
    try {
      escape.open("libhost-escape.so");
    } catch (const ElfError &error) {
      missing = std::string(error.what()).find("getpid") != std::string::npos;
    }
    require(missing && escape.image_count() == 0,
            "host global symbols must not satisfy imports");
    auto missing_dir =
        copy("missing", {"libroot.so", "libleft.so", "libleaf.so"});
    Loader missing_loader({missing_dir});
    missing = false;
    try {
      missing_loader.open("libroot.so");
    } catch (const ElfError &error) {
      missing =
          std::string(error.what()).find("libright.so") != std::string::npos;
    }
    require(missing && missing_loader.image_count() == 0 &&
                missing_loader.constructor_count() == 0,
            "missing dependency rollback before initialization");
    auto bad_dir = copy("version", graph);
    fs::copy_file(fixtures / "leaf-bad.so", bad_dir / "libleaf.so",
                  fs::copy_options::overwrite_existing);
    Loader bad({bad_dir});
    missing = false;
    try {
      bad.open("libroot.so");
    } catch (const ElfError &error) {
      missing =
          std::string(error.what()).find("FIXTURE_OLD") != std::string::npos;
    }
    require(missing && bad.image_count() == 0 && bad.constructor_count() == 0,
            "version mismatch fails before constructors");
    auto symbolic_dir = copy("symbolic", graph);
    symbolic(symbolic_dir / "libleaf.so");
    Loader self({symbolic_dir});
    require(invoke_int(self, self.open("libroot.so"), "root_value") == 120,
            "DT_SYMBOLIC relocation priority");
    Loader global({fixtures});
    global.open("libglobal.so");
    require(invoke_int(global, global.open("libroot.so"), "root_value") == 347,
            "global group precedes local group");
    auto *cycle = loader.open("libcycle-a.so");
    require(invoke_int(loader, cycle, "read_a") == 33 &&
                invoke_int(loader, cycle, "read_b") == 22,
            "dependency cycle binds all images before constructor execution");
    require(loader.constructor_count() == 6,
            "cyclic constructors exactly once");
    auto escaped_dir =
        copy("path", {"libroot.so", "libleft.so", "libright.so"});
    fs::create_symlink(fixtures / "libleaf.so", escaped_dir / "libleaf.so");
    Loader confined({escaped_dir});
    missing = false;
    try {
      confined.open("libroot.so");
    } catch (const ElfError &error) {
      missing = std::string(error.what()).find("escapes") != std::string::npos;
    }
    require(missing && confined.image_count() == 0,
            "symlink cannot escape search roots");
    require(_dyld_image_count() == before, "no guest module enters dyld");
    bool saw_weak = false, saw_preemption = false;
    for (const auto &record : loader.bindings()) {
      saw_weak |= record.symbol == "optional_feature" &&
                  !record.binding.found && record.binding.address == 0;
      saw_preemption |= record.module == "libleaf.so" &&
                        record.symbol == "shadowed" &&
                        record.binding.provider == "libroot.so";
    }
    require(saw_weak && saw_preemption,
            "binding records identify actual providers");
    fs::remove_all(work);
    std::cout << "M5_DEPENDENCY_PASS bfs=1 versions=1 symbolic=1 global=1 "
                 "cycles=1 malformed_versions=12 rollback=1 weak=1 "
                 "host_fallback=0 dyld=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
