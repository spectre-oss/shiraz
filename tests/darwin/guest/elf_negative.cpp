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
    reject("missing SYSV hash", [](Bytes &b) { put(b, tag(b, 4), 0); });
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
