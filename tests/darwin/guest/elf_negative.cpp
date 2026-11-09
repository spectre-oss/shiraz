// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/image.hpp"
#include <fstream>
#include <functional>
#include <iostream>
#include <stdexcept>

using namespace shiraz::guest;
using Bytes = std::vector<uint8_t>;
static uint64_t read(const Bytes &bytes, size_t offset, unsigned width) {
  uint64_t result = 0;
  for (unsigned i = 0; i < width; ++i)
    result |= uint64_t(bytes.at(offset + i)) << (i * 8);
  return result;
}
static void put(Bytes &bytes, size_t offset, uint64_t value,
                unsigned width = 8) {
  for (unsigned i = 0; i < width; ++i)
    bytes.at(offset + i) = uint8_t(value >> (i * 8));
}
static size_t ph(const Bytes &bytes, uint32_t type, unsigned occurrence = 0) {
  for (size_t i = 0; i < read(bytes, 56, 2); ++i) {
    size_t p = read(bytes, 32, 8) + i * 56;
    if (read(bytes, p, 4) == type && occurrence-- == 0)
      return p;
  }
  throw std::runtime_error("fixture program header missing");
}
static size_t address(const Bytes &bytes, uint64_t value) {
  for (size_t i = 0; i < read(bytes, 56, 2); ++i) {
    size_t p = read(bytes, 32, 8) + i * 56;
    uint64_t base = read(bytes, p + 16, 8), size = read(bytes, p + 32, 8);
    if (read(bytes, p, 4) == 1 && value >= base && value - base < size)
      return read(bytes, p + 8, 8) + value - base;
  }
  throw std::runtime_error("fixture virtual address missing");
}
static size_t tag(const Bytes &bytes, uint64_t value) {
  size_t dynamic = ph(bytes, 2);
  size_t base = read(bytes, dynamic + 8, 8),
         size = read(bytes, dynamic + 32, 8);
  for (size_t p = base; p < base + size; p += 16)
    if (read(bytes, p, 8) == value)
      return p + 8;
  throw std::runtime_error("fixture dynamic tag missing");
}
int main(int argc, char **argv) {
  try {
    if (argc != 2)
      throw std::runtime_error("usage: elf-negative fixture.so");
    std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
    auto length = file.tellg();
    if (!file || length <= 0 || length > (256 << 20))
      throw std::runtime_error("fixture open/size");
    Bytes original(static_cast<size_t>(length));
    file.seekg(0);
    if (!file.read(reinterpret_cast<char *>(original.data()), length))
      throw std::runtime_error("fixture read");
    parse_elf(original, 16384);
    auto gnu_only = [](Bytes &b) { put(b, tag(b, 4) - 8, 0x70000077); };
    Bytes gnu = original;
    gnu_only(gnu);
    auto sysv_elf = parse_elf(original, 16384);
    auto gnu_elf = parse_elf(gnu, 16384);
    if (sysv_elf.symbols.size() != gnu_elf.symbols.size())
      throw std::runtime_error("GNU-only symbol count differs");
    for (size_t i = 0; i < sysv_elf.symbols.size(); ++i)
      if (sysv_elf.symbols[i].name != gnu_elf.symbols[i].name)
        throw std::runtime_error("GNU-only symbol names differ");
    uint64_t executable = 0;
    for (const auto &segment : sysv_elf.segments)
      if ((segment.flags & 1) && segment.file_size >= 4) {
        executable = segment.address;
        break;
      }
    if (!executable || (executable & 3))
      throw std::runtime_error("fixture aligned executable segment missing");
    auto finalizer_tags = [&](Bytes &b) {
      // SYSV hash remains present. Reuse the optional GNU hash slot and the
      // existing populated init-array slots; these checks never execute ELF.
      size_t function = tag(b, 0x6ffffef5), array = tag(b, 25), size = tag(b, 27);
      put(b, function - 8, 13);
      put(b, function, executable);
      put(b, array - 8, 26);
      put(b, size - 8, 28);
    };
    Bytes finalizer_control = original;
    finalizer_tags(finalizer_control);
    const auto finalizer_elf = parse_elf(finalizer_control, 16384);
    if (finalizer_elf.fini != executable || finalizer_elf.fini_array != sysv_elf.init_array ||
        finalizer_elf.fini_array_size != sysv_elf.init_array_size || !finalizer_elf.fini_array_size)
      throw std::runtime_error("ELF finalizer metadata was not retained");
    // As with INIT_ARRAY, size zero means no entries. No paired-tag presence
    // or pointer range is inferred when there is nothing to execute.
    put(finalizer_control, tag(finalizer_control, 13), 0);
    put(finalizer_control, tag(finalizer_control, 26), UINT64_MAX);
    put(finalizer_control, tag(finalizer_control, 28), 0);
    parse_elf(finalizer_control, 16384);
    // Android linkers can page-pad RELRO beyond the final LOAD byte.
    Bytes padded = original;
    size_t relro = ph(padded, 0x6474e552);
    uint64_t relro_start = read(padded, relro + 16, 8);
    for (size_t i = 0; i < read(padded, 56, 2); ++i) {
      size_t p = read(padded, 32, 8) + i * 56;
      if (read(padded, p, 4) == 1 && read(padded, p + 16, 8) == relro_start)
        put(padded, p + 40, read(padded, p + 32, 8));
    }
    parse_elf(padded, 16384);
    size_t tests = 0;
    auto reject = [&](const char *label,
                      const std::function<void(Bytes &)> &mutate,
                      bool map = false) {
      Bytes bytes = original;
      mutate(bytes);
      try {
        if (map) {
          Image image(bytes);
        } else {
          parse_elf(bytes, 16384);
        }
      } catch (const ElfError &) {
        ++tests;
        return;
      }
      throw std::runtime_error(
          std::string("accepted malformed/unsupported ELF: ") + label);
    };
    reject("short header", [](Bytes &b) { b.resize(63); });
    reject("wrong magic", [](Bytes &b) { b[0] = 0; });
    reject("wrong endian", [](Bytes &b) { b[5] = 2; });
    reject("wrong machine", [](Bytes &b) { put(b, 18, 62, 2); });
    reject("not ET_DYN", [](Bytes &b) { put(b, 16, 2, 2); });
    reject("overflowed phoff", [](Bytes &b) { put(b, 32, UINT64_MAX - 8); });
    reject("ph count cap", [](Bytes &b) { put(b, 56, 129, 2); });
    reject("ph stride", [](Bytes &b) { put(b, 54, 55, 2); });
    reject("PT_TLS", [](Bytes &b) { put(b, ph(b, 0x6474e551), 7, 4); });
    reject("file size > memory",
           [](Bytes &b) { put(b, ph(b, 1) + 32, UINT64_MAX); });
    reject("LOAD overflow",
           [](Bytes &b) { put(b, ph(b, 1) + 16, UINT64_MAX - 128); });
    reject("LOAD alignment", [](Bytes &b) { put(b, ph(b, 1) + 48, 3); });
    reject("LOAD file offset",
           [](Bytes &b) { put(b, ph(b, 1) + 8, UINT64_MAX); });
    reject("W+X LOAD", [](Bytes &b) { put(b, ph(b, 1, 1) + 4, 7, 4); });
    reject("LOAD host-page conflict", [](Bytes &b) {
      size_t p = ph(b, 1, 1);
      put(b, p + 16, read(b, p + 8, 8));
    });
    reject("dynamic terminator", [](Bytes &b) { put(b, tag(b, 0) - 8, 21); });
    reject("duplicate dynamic", [](Bytes &b) { put(b, tag(b, 11) - 8, 6); });
    reject("unaligned DT_INIT", [&](Bytes &b) {
      size_t p = tag(b, 0x6ffffef5);
      put(b, p - 8, 12);
      put(b, p, executable + 1);
    });
    reject("unaligned init array", [](Bytes &b) {
      size_t p = tag(b, 25);
      put(b, p, read(b, p, 8) + 1);
    });
    auto executable_bss = [](Bytes &b) {
      for (size_t i = 0; i < read(b, 56, 2); ++i) {
        size_t p = read(b, 32, 8) + i * 56;
        if (read(b, p, 4) != 1 || !(read(b, p + 4, 4) & 1)) continue;
        const uint64_t base = read(b, p + 16, 8), file_size = read(b, p + 32, 8);
        const uint64_t target = (base + file_size + 3) & ~uint64_t(3);
        put(b, p + 40, target - base + 4);
        return target;
      }
      throw std::runtime_error("fixture executable LOAD missing");
    };
    reject("non-file-backed DT_INIT", [&](Bytes &b) {
      size_t p = tag(b, 0x6ffffef5);
      put(b, p - 8, 12);
      put(b, p, executable_bss(b));
    });
    auto reject_fini = [&](const char *label, const std::function<void(Bytes &)> &mutate) {
      reject(label, [&](Bytes &b) { finalizer_tags(b); mutate(b); });
    };
    reject_fini("unaligned DT_FINI", [&](Bytes &b) { put(b, tag(b, 13), executable + 1); });
    reject_fini("nonexecutable DT_FINI", [](Bytes &b) { put(b, tag(b, 13), 4); });
    reject_fini("out-of-image DT_FINI", [](Bytes &b) { put(b, tag(b, 13), UINT64_MAX - 3); });
    reject_fini("non-file-backed DT_FINI", [&](Bytes &b) {
      put(b, tag(b, 13), executable_bss(b));
    });
    reject_fini("unaligned fini array", [](Bytes &b) {
      size_t p = tag(b, 26);
      put(b, p, read(b, p, 8) + 1);
    });
    reject_fini("fini array entry size", [](Bytes &b) { put(b, tag(b, 28), 7); });
    reject_fini("fini array size cap", [](Bytes &b) { put(b, tag(b, 28), 65544); });
    reject_fini("fini array range overflow", [](Bytes &b) {
      put(b, tag(b, 26), UINT64_MAX - 7);
      put(b, tag(b, 28), 16);
    });
    reject_fini("nonwritable fini array", [&](Bytes &b) { put(b, tag(b, 26), executable); });
    reject_fini("fini array missing pointer", [](Bytes &b) {
      put(b, tag(b, 26) - 8, 0x70000078);
    });
    // SYSV hash remains present, so replacing the optional GNU hash metadata
    // with an unrelated tag is otherwise a valid parser input. The same
    // replacement with a legacy Android RELR tag must refuse before attempting
    // to interpret a zero, out-of-file or overflowing relocation-table address.
    for (uint64_t value : {uint64_t(0), uint64_t(1), UINT64_MAX}) {
      Bytes unknown = original;
      size_t p = tag(unknown, 0x6ffffef5);
      put(unknown, p - 8, 0x70000078);
      put(unknown, p, value);
      parse_elf(unknown, 16384);
      for (uint64_t legacy : {uint64_t(0x6fffe000), uint64_t(0x6fffe001),
                              uint64_t(0x6fffe003), uint64_t(0x6fffe005)}) {
        reject("legacy Android RELR metadata", [=](Bytes &b) {
          size_t entry = tag(b, 0x6ffffef5);
          put(b, entry - 8, legacy);
          put(b, entry, value);
        });
      }
    }
    for (bool populated : {false, true}) {
      auto bundle = [=](Bytes &b, bool legacy) {
        const size_t pointer = tag(b, 0x6ffffef5);
        const size_t size = tag(b, 25), stride = tag(b, 27);
        // Removing both optional init-array entries keeps the control input
        // valid while making room for a complete legacy RELR triplet.
        put(b, pointer - 8, legacy ? 0x6fffe000 : 0x70000078);
        put(b, pointer, populated ? read(b, tag(b, 7), 8) : 0);
        put(b, size - 8, legacy ? 0x6fffe001 : 0x70000079);
        put(b, size, populated ? 8 : 0);
        put(b, stride - 8, legacy ? 0x6fffe003 : 0x7000007a);
        put(b, stride, populated ? 8 : 0);
      };
      Bytes control = original;
      bundle(control, false);
      parse_elf(control, 16384);
      reject("legacy Android RELR triplet", [=](Bytes &b) { bundle(b, true); });
    }
    reject("missing SYSV hash", [](Bytes &b) { put(b, tag(b, 4), 0); });
    auto reject_gnu = [&](const char *label, const std::function<void(Bytes &)> &mutate) {
      reject(label, [&](Bytes &b) { gnu_only(b); mutate(b); });
    };
    reject_gnu("GNU bucket count", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 0x6ffffef5), 8)), 0xffffffff, 4);
    });
    reject_gnu("GNU bloom size", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 0x6ffffef5), 8)) + 8, 3, 4);
    });
    reject_gnu("GNU bucket before symbol offset", [](Bytes &b) {
      size_t p = address(b, read(b, tag(b, 0x6ffffef5), 8));
      put(b, p + 4, 2, 4);
      put(b, p + 16 + read(b, p + 8, 4) * 8, 1, 4);
    });
    reject_gnu("GNU chain symbol limit", [](Bytes &b) {
      size_t p = address(b, read(b, tag(b, 0x6ffffef5), 8));
      size_t bucket = p + 16 + read(b, p + 8, 4) * 8;
      put(b, p + 4, (1u << 20) - 1, 4);
      put(b, bucket, (1u << 20) - 1, 4);
      size_t chains = bucket + read(b, p, 4) * 4;
      put(b, chains, 0, 4);
    });
    reject("symbol count cap", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 4), 8)) + 4, 0xffffffff, 4);
    });
    reject("symbol string index", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 6), 8)) + 24, 0xffffffff, 4);
    });
    reject("unterminated strings", [](Bytes &b) {
      size_t p = address(b, read(b, tag(b, 5), 8));
      size_t size = read(b, tag(b, 10), 8);
      for (size_t i = 1; i < size; ++i)
        b[p + i] = 'x';
    });
    reject("RELA entry size", [](Bytes &b) { put(b, tag(b, 9), 16); });
    reject("relocation kind", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 7), 8)) + 8, 0xffffffff, 4);
    });
    reject("relocation symbol index", [](Bytes &b) {
      put(b, address(b, read(b, tag(b, 7), 8)) + 12, 0xffffffff, 4);
    });
    reject("relocation into readonly header",
           [](Bytes &b) { put(b, address(b, read(b, tag(b, 7), 8)), 0); });
    reject("duplicate relocation", [](Bytes &b) {
      size_t p = address(b, read(b, tag(b, 7), 8));
      put(b, p + 24, read(b, p, 8));
    });
    reject(
        "unresolved import",
        [](Bytes &b) {
          size_t p = address(b, read(b, tag(b, 6), 8));
          // Make one named definition undefined; resolution must fail before
          // execution.
          put(b, p + 24 + 6, 0, 2);
        },
        true);
    reject("RELRO size overflow",
           [](Bytes &b) { put(b, ph(b, 0x6474e552) + 40, UINT64_MAX); });
    reject("RELRO outside image",
           [](Bytes &b) { put(b, ph(b, 0x6474e552) + 16, 0); });
    // Truncations cover metadata/file-data boundaries without executing the
    // input.
    for (size_t length = 0; length < original.size(); length += 37) {
      Bytes bytes(original.begin(), original.begin() + length);
      try {
        parse_elf(bytes, 16384);
      } catch (const ElfError &) {
        continue;
      }
      // Stripped trailing section headers are legal; only require memory
      // safety.
    }
    std::cout << "M5_ELF_NEGATIVE_PASS rejected=" << tests
              << " truncation_stride=37\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
