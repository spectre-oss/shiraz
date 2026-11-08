// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <algorithm>
#include <atomic>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <thread>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static std::vector<uint8_t> read(const fs::path &path) {
  std::ifstream file(path, std::ios::binary | std::ios::ate);
  auto size = file.tellg();
  require(bool(file) && size > 0 && size <= (256 << 20), "bounded ELF input");
  std::vector<uint8_t> bytes(static_cast<size_t>(size));
  file.seekg(0);
  require(bool(file.read(reinterpret_cast<char *>(bytes.data()), size)), "ELF read");
  return bytes;
}
static uint64_t u(const std::vector<uint8_t> &bytes, size_t offset, unsigned width) {
  uint64_t value = 0;
  for (unsigned i = 0; i < width; ++i) value |= uint64_t(bytes.at(offset + i)) << (8 * i);
  return value;
}
static void put(std::vector<uint8_t> &bytes, size_t offset, uint64_t value, unsigned width) {
  for (unsigned i = 0; i < width; ++i) bytes.at(offset + i) = uint8_t(value >> (8 * i));
}
static size_t offset(const Elf &elf, uint64_t address) {
  for (const auto &segment : elf.segments)
    if (address >= segment.address && address - segment.address < segment.file_size)
      return size_t(segment.offset + address - segment.address);
  throw std::runtime_error("fixture address not file backed");
}
static uint64_t symtab(const std::vector<uint8_t> &bytes) {
  uint64_t headers = u(bytes, 32, 8), stride = u(bytes, 54, 2), count = u(bytes, 56, 2);
  for (uint64_t i = 0; i < count; ++i) {
    auto p = headers + i * stride;
    if (u(bytes, p, 4) != 2) continue;
    auto start = u(bytes, p + 8, 8), size = u(bytes, p + 32, 8);
    for (uint64_t j = 0; j < size; j += 16) {
      auto tag = u(bytes, start + j, 8);
      if (!tag) break;
      if (tag == 6) return u(bytes, start + j + 8, 8);
    }
  }
  throw std::runtime_error("fixture symbol table absent");
}
int main(int argc, char **argv) {
  try {
    if (argc > 2 && std::string(argv[1]) == "--analyze") {
      bool passed = true;
      for (int i = 2; i < argc; ++i) {
        try {
          auto bytes = read(argv[i]); auto elf = parse_elf(bytes, 16384);
          auto analysis = analyze_thread_pointers(elf, bytes);
          std::cout << argv[i] << " sites=" << analysis.sites.size() << " data="
                    << analysis.data_words << " decoded=" << analysis.decoded << '\n';
        } catch (const std::exception &error) { passed = false; std::cout << argv[i] << " rejected=" << error.what() << '\n'; }
      }
      return passed ? 0 : 1;
    }
    require(argc == 2, "usage: protected-probe fixture-directory");
    fs::path fixtures = fs::canonical(argv[1]);
    auto bytes = read(fixtures / "libprotected.so");
    auto elf = parse_elf(bytes, 16384);
    auto analysis = analyze_thread_pointers(elf, bytes);
    require(analysis.sites.size() >= 8 && analysis.data_words == 1 && analysis.decoded > 30 && elf.eh_frame_header_size,
            "compiler-protected code and declared literal provenance");
    size_t literal = 0, function = 0, registration = 0, methods = 0;
    for (size_t i = 0; i < elf.symbols.size(); ++i) {
      if (elf.symbols[i].name == "guard_literal") literal = i;
      if (elf.symbols[i].name == "protected_sum") function = i;
      if (elf.symbols[i].name == "register_fixture") registration = i;
      if (elf.symbols[i].name == "jni_method_table") methods = i;
    }
    require(literal && function && registration && methods, "fixture code/data symbols");
    auto table_relocation = [&](uint64_t address) -> const Relocation & {
      for (const auto &relocation : elf.relocations)
        if (relocation.address == address) return relocation;
      throw std::runtime_error("fixture JNI table relocation missing");
    };
    uint64_t methods_address = elf.symbols[methods].address;
    uint64_t callback_address = uint64_t(table_relocation(methods_address + 16).addend);
    require(std::any_of(analysis.sites.begin(), analysis.sites.end(), [&](const auto &site) {
              return site.address == callback_address;
            }), "private JNI callback discovered through registration table");
    auto table = offset(elf, symtab(bytes));
    size_t rejected = 0;
    auto reject = [&](const std::function<void(std::vector<uint8_t> &)> &mutate, const char *reason) {
      auto malformed = bytes; mutate(malformed);
      try { Image image(malformed, Image::Mapping::Deferred); }
      catch (const ElfError &error) {
        require(std::string(error.what()).find(reason) != std::string::npos, "specific provenance rejection");
        ++rejected; return;
      }
      throw std::runtime_error("accepted malformed instruction provenance");
    };
    reject([&](auto &b) { put(b, table + literal * 24 + 4, 0x10, 1); }, "ambiguous");
    reject([&](auto &b) { put(b, offset(elf, analysis.sites[0].address), 0xd51bd040, 4); }, "writes");
    reject([&](auto &b) { put(b, offset(elf, analysis.sites[0].address), 0xd53bd052, 4); }, "x18");
    reject([&](auto &b) { put(b, offset(elf, elf.symbols[function].address), 0xffffffff, 4); }, "invalid reachable");
    reject([&](auto &b) { put(b, offset(elf, elf.symbols[function].address), 0xd4000001, 4); }, "system-control");
    reject([&](auto &b) { put(b, offset(elf, elf.symbols[function].address), 0x16000000, 4); }, "target outside");
    reject([&](auto &b) { put(b, table + literal * 24 + 8, elf.symbols[function].address, 8); }, "overlaps declared data");
    size_t header = offset(elf, elf.eh_frame_header);
    uint64_t fde = uint64_t(int64_t(elf.eh_frame_header) + int32_t(u(bytes, header + 16, 4)));
    size_t fde_offset = offset(elf, fde);
    size_t cie_offset = offset(elf, fde + 4 - u(bytes, fde_offset + 4, 4));
    reject([&](auto &b) { put(b, header, 2, 1); }, "header encoding");
    reject([&](auto &b) { put(b, header + 8, UINT32_MAX, 4); }, "table bounds");
    reject([&](auto &b) { put(b, header + 12, u(b, header + 12, 4) + 4, 4); }, "function mismatch");
    reject([&](auto &b) { put(b, fde_offset, UINT32_MAX, 4); }, "invalid unwind FDE");
    reject([&](auto &b) { put(b, fde_offset + 4, 0, 4); }, "invalid unwind FDE");
    reject([&](auto &b) { put(b, cie_offset + 8, 9, 1); }, "CIE version");
    reject([&](auto &b) { put(b, cie_offset, 8, 4); }, "LEB");
    auto registration_code = offset(elf, elf.symbols[registration].address);
    // The count is MOV w3,#1 at the fixed assembly fixture's fifth word.
    reject([&](auto &b) { put(b, registration_code + 16, 0x52800003, 4); }, "JNI table bounds");
    reject([&](auto &b) { put(b, registration_code + 16, 0x52820023, 4); }, "JNI table bounds");
    uint64_t signature_address = uint64_t(table_relocation(methods_address + 8).addend);
    reject([&](auto &b) { put(b, offset(elf, signature_address), '[', 1); }, "invalid JNI method descriptor");
    // Losing either the table count or the vtable load chain must not turn
    // arbitrary relocated code pointers into independent decoder roots.
    reject([&](auto &b) { put(b, registration_code + 16, 0xd503201f, 4); }, "ambiguous");
    reject([&](auto &b) { put(b, registration_code + 24, 0xf9400508, 4); }, "ambiguous");
    auto reject_table_pointer = [&](bool missing) {
      auto malformed = elf;
      auto found = std::find_if(malformed.relocations.begin(), malformed.relocations.end(), [&](const auto &relocation) {
        return relocation.address == methods_address + 16;
      });
      require(found != malformed.relocations.end(), "JNI callback relocation exists");
      if (missing) malformed.relocations.erase(found);
      else { found->type = 1027; found->symbol = 0; found->addend = 0; }
      try { analyze_thread_pointers(malformed, bytes); }
      catch (const ElfError &error) {
        require(std::string(error.what()).find(missing ? "lacks relocation" : "outside code") != std::string::npos,
                "specific JNI pointer rejection");
        ++rejected;
        return;
      }
      throw std::runtime_error("accepted malformed JNI callback pointer");
    };
    reject_table_pointer(true);
    reject_table_pointer(false);
    Loader *current = nullptr;
    Loader loader({fixtures}, native_personality([&](const void *p) { return current && current->contains_code(p); }));
    current = &loader;
    auto *module = loader.link("libprotected.so");
    require(!loader.constructor_count() && loader.rewritten_thread_pointers() == analysis.sites.size(),
            "all planned instructions patched before constructors");
    loader.open("libprotected.so");
    require(loader.constructor_count() == 1 && loader.image_count() == 1, "real protected constructor executed");
    auto function_address = loader.symbol(module, "protected_sum").address;
    auto literal_address = loader.symbol(module, "literal_bits").address;
    require(uint32_t(invoke(reinterpret_cast<void *>(literal_address), {}).result_integers[0]) == 0xd53bd040,
            "declared literal remains byte-exact");
    require(int(invoke(reinterpret_cast<void *>(function_address), {Argument::integer(10)}).result_integers[0]) == 61,
            "real compiler stack-protected call");
    auto mapped_methods = loader.symbol(module, "jni_method_table").address;
    uint64_t mapped_callback = 0;
    std::memcpy(&mapped_callback, reinterpret_cast<const void *>(mapped_methods + 16), 8);
    require(int(invoke(reinterpret_cast<void *>(mapped_callback), {}).result_integers[0]) == 97,
            "registration-derived private JNI callback executes with rewritten TLS");
    auto getter = loader.symbol(module, "protected_callback").address;
    auto callback = invoke(reinterpret_cast<void *>(getter), {}).result_integers[0];
    require(loader.contains_code(reinterpret_cast<void *>(callback)) &&
                int(invoke(reinterpret_cast<void *>(callback), {Argument::integer(5)}).result_integers[0]) == 27,
            "private indirect callback discovered from validated FDE metadata");
    std::atomic<int> passed{0};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      prepare_guest_tls();
      for (int repeat = 0; repeat < 64; ++repeat)
        if (int(invoke(reinterpret_cast<void *>(function_address), {Argument::integer(i)}).result_integers[0]) != 2 * i + 41) return;
      ++passed;
    });
    for (auto &thread : threads) thread.join();
    require(passed == 4, "concurrent protected ELF calls");
    auto guard = loader.symbol(module, "guard_checked").address;
    require(int(invoke(reinterpret_cast<void *>(guard), {Argument::integer(0)}).result_integers[0]) == 73,
            "automatically rewritten ELF stack-guard check");
    pid_t child = fork();
    require(child >= 0, "protected ELF corruption child");
    if (!child) {
      std::signal(SIGABRT, [](int) { _exit(97); });
      invoke(reinterpret_cast<void *>(guard), {Argument::integer(1)});
      _exit(98);
    }
    int status;
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
            "actual protected ELF corruption aborts");
    std::cout << "M5_PROTECTED_ELF_PASS rewritten=" << analysis.sites.size()
              << " constructor=1 private_helper=1 data_literal=1 provenance_rejections=" << rejected
              << " indirect_callback=1 jni_callback=1 corruption_abort=1 threads=4 elf_auto_rewrite=1 unwind_verified=0\n";
  } catch (const std::exception &error) { std::cerr << "FAIL " << error.what() << '\n'; return 1; }
}
