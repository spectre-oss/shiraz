// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/unwind_runtime.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <array>
#include <cstring>
#include <fstream>
#include <iostream>
#include <utility>
#include <unwind.h>

using namespace shiraz::guest;
namespace {
const Image *current_image = nullptr;
unsigned guest_frames = 0;
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
_Unwind_Reason_Code visit(_Unwind_Context *context, void *) {
  uintptr_t pc = _Unwind_GetIP(context);
  if (pc && current_image->contains_code(reinterpret_cast<void *>(pc - 4)))
    ++guest_frames;
  return _URC_NO_REASON;
}
extern "C" __attribute__((noinline)) int capture() {
  guest_frames = 0;
  _Unwind_Backtrace(visit, nullptr);
  return int(guest_frames);
}
std::vector<uint8_t> read(const char *name) {
  std::ifstream file(name, std::ios::binary | std::ios::ate);
  auto size = file.tellg();
  require(bool(file) && size > 0 && size <= (256 << 20), "bounded unwind fixture");
  std::vector<uint8_t> bytes(static_cast<size_t>(size));
  file.seekg(0);
  require(bool(file.read(reinterpret_cast<char *>(bytes.data()), size)), "unwind fixture read");
  return bytes;
}
// Source-only metadata fixtures exercise 64-bit PC-relative augmentation
// decoding without registering a fabricated personality or executing it.
struct WideMetadata {
  Elf elf;
  std::vector<uint8_t> bytes = std::vector<uint8_t>(0x500);
  size_t personality = 0, lsda = 0, cie_augmentation = 0, fde_augmentation = 0;
  void put(size_t offset, uint64_t value, unsigned width) {
    for (unsigned i = 0; i < width; ++i)
      bytes.at(offset + i) = uint8_t(value >> (8 * i));
  }
  explicit WideMetadata(uint64_t base = 0) {
    elf.first_page = base;
    elf.span = 0x4000;
    elf.segments.push_back({0, base + 0x1000, 0x400, 0x400, 4});
    elf.segments.push_back({0x400, base + 0x2000, 0x100, 0x100, 5});
    elf.eh_frame_header = base + 0x1000;
    elf.eh_frame_header_size = 20;
    put(0, 0x3b031b01, 4);
    put(4, 0x7c, 4); // pcrel .eh_frame pointer -> CIE at 0x1080.
    put(8, 1, 4);
    put(12, 0x1000, 4); // function table entry -> 0x2000.
    put(16, 0x100, 4); // FDE table entry -> 0x1100.
    size_t cursor = 0x88;
    auto byte = [&](uint8_t value) { put(cursor++, value, 1); };
    byte(1); // CIE version.
    for (char c : std::string("zPLR")) byte(uint8_t(c));
    byte(0);
    byte(1); byte(0x78); byte(30); // code=1, data=-8, LR=x30.
    cie_augmentation = cursor;
    byte(11); // P encoding+8-byte pointer, L encoding, R encoding.
    byte(0x9c);
    personality = cursor;
    put(cursor, 0x180 - cursor, 8); cursor += 8; // indirect slot at 0x1180.
    byte(0x1c); byte(0x1b);
    byte(12); byte(31); byte(0); // def_cfa sp, 0.
    put(0x80, cursor - 0x84, 4);
    put(0x84, 0, 4); // CIE identifier.
    put(0x104, 0x84, 4); // FDE backward CIE reference.
    put(0x108, 0xef8, 4); // function start: pcrel 0x1108 -> 0x2000.
    put(0x10c, 4, 4); // function extent.
    cursor = 0x110;
    fde_augmentation = cursor;
    byte(8);
    lsda = cursor;
    put(cursor, 0x190 - cursor, 8); cursor += 8; // LSDA at 0x1190.
    byte(12); byte(31); byte(0);
    put(0x100, cursor - 0x104, 4);
    put(0x190, 0xff, 1);
    put(0x400, 0xd65f03c0, 4); // nominal RET; never executed.
  }
};
unsigned wide_metadata_checks() {
  WideMetadata valid;
  UnwindRegistration plan(valid.elf, valid.bytes);
  require(plan.frame_count() == 1 && !plan.active(), "64-bit augmentation plan");
  auto null_lsda = valid;
  null_lsda.put(null_lsda.lsda, 0, 8);
  UnwindRegistration null_plan(null_lsda.elf, null_lsda.bytes);
  require(null_plan.frame_count() == 1, "zero 64-bit LSDA means absent");
  unsigned rejected = 0;
  auto reject = [&](const WideMetadata &fixture, const char *reason) {
    try { UnwindRegistration invalid(fixture.elf, fixture.bytes); }
    catch (const ElfError &error) {
      require(std::string(error.what()).find(reason) != std::string::npos,
              "specific 64-bit augmentation rejection");
      ++rejected; return;
    }
    throw std::runtime_error("accepted malformed 64-bit augmentation");
  };
  auto personality_underflow = valid;
  personality_underflow.put(valid.personality, uint64_t(INT64_MIN), 8);
  reject(personality_underflow, "underflow");
  auto lsda_underflow = valid;
  lsda_underflow.put(valid.lsda, uint64_t(INT64_MIN), 8);
  reject(lsda_underflow, "underflow");
  WideMetadata high(UINT64_MAX - 0x7fff);
  auto personality_overflow = high;
  personality_overflow.put(high.personality, INT64_MAX, 8);
  reject(personality_overflow, "overflow");
  auto lsda_overflow = high;
  lsda_overflow.put(high.lsda, INT64_MAX, 8);
  reject(lsda_overflow, "overflow");
  auto personality_truncated = valid;
  personality_truncated.put(valid.cie_augmentation, 8, 1);
  reject(personality_truncated, "truncated");
  auto lsda_truncated = valid;
  lsda_truncated.put(valid.fde_augmentation, 7, 1);
  reject(lsda_truncated, "truncated");
  auto personality_encoding = valid;
  personality_encoding.put(valid.personality - 1, 0x8c, 1);
  reject(personality_encoding, "encoding");
  auto indirect_extent = valid;
  indirect_extent.put(valid.personality, 0x3f9 - valid.personality, 8);
  reject(indirect_extent, "outside file-backed");
  require(rejected == 8, "64-bit augmentation rejection count");
  return rejected;
}
// This observer executes inside the real production TPIDR shared entry.
// It changes only the explicit accessor pointer in an owned generated island;
// the original guest instruction replacement and save/restore path are intact.
std::array<uintptr_t, 128> island_trace{};
size_t island_trace_count = 0;
_Unwind_Reason_Code island_visit(_Unwind_Context *context, void *) {
  if (island_trace_count == island_trace.size()) return _URC_END_OF_STACK;
  island_trace[island_trace_count++] = _Unwind_GetIP(context);
  return _URC_NO_REASON;
}
void *island_access() noexcept {
  island_trace_count = 0;
  _Unwind_Backtrace(island_visit, nullptr);
  return guest_tls_base();
}
void island_checks(const std::vector<uint8_t> &bytes) {
  Image image(bytes);
  const auto sites = analyze_thread_pointers(image.elf(), bytes).sites;
  require(sites.size() == 2, "exactly two framed/frameless MRS fixture sites");
  {
    JitWriteScope writable;
    for (size_t index = 0; index < sites.size(); ++index) {
      const uintptr_t island = image.load_bias() + image.elf().first_page +
                               image.elf().span + index * 64;
      const auto accessor = &island_access;
      const uint32_t custom_accessor_kind = 0;
      std::memcpy(reinterpret_cast<void *>(island + 52), &custom_accessor_kind,
                  sizeof(custom_accessor_kind));
      std::memcpy(reinterpret_cast<void *>(island + 56), &accessor, sizeof(accessor));
    }
  }
  prepare_guest_tls();
  bool all_preserved = true;
  for (const char *kind : {"framed", "leaf"}) {
    const std::string prefix = std::string("guest_island_") + kind;
    const std::string outer = std::string("guest_island_outer_") + kind;
    const uintptr_t resumed = reinterpret_cast<uintptr_t>(image.symbol(prefix + "_resume"));
    const uintptr_t caller = reinterpret_cast<uintptr_t>(image.symbol(outer + "_resume"));
    require(resumed && caller, "exact guest unwind PC labels exported");
    invoke(image.symbol(outer), {});
    size_t interrupted_index = island_trace_count, caller_index = island_trace_count;
    for (size_t index = 0; index < island_trace_count; ++index) {
      if (island_trace[index] == resumed && interrupted_index == island_trace_count)
        interrupted_index = index;
      if (island_trace[index] == caller && caller_index == island_trace_count)
        caller_index = index;
    }
    std::cout << "M5_ISLAND_UNWIND_OBSERVED kind=" << kind
              << " interrupted=" << (interrupted_index < island_trace_count)
              << " caller=" << (caller_index < island_trace_count) << "\n";
    all_preserved &= interrupted_index < caller_index && caller_index < island_trace_count;
  }
  require(all_preserved,
          "TPIDR accessor trace preserves interrupted guest PC before its caller");
  std::cout << "M5_ISLAND_UNWIND_PASS framed=1 frameless=1 exact_pc=1 order=1\n";
}
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2 || (argc == 3 && std::string(argv[2]) == "--islands"),
            "usage: unwind-probe libunwind-fixture.so [--islands]");
    if (argc == 3) { island_checks(read(argv[1])); return 0; }
    unsigned wide_rejected = wide_metadata_checks();
    auto bytes = read(argv[1]);
    auto elf = parse_elf(bytes, 16384);
    unsigned rejected = 0;
    auto reject = [&](Elf malformed) {
      try { UnwindRegistration invalid(malformed, bytes); }
      catch (const ElfError &) { ++rejected; return; }
      throw std::runtime_error("accepted invalid unwind registration metadata");
    };
    auto mutable_metadata = elf;
    for (auto &segment : mutable_metadata.segments)
      if (elf.eh_frame_header >= segment.address &&
          elf.eh_frame_header - segment.address < segment.file_size)
        segment.flags |= 2;
    reject(mutable_metadata);
    auto relocated_metadata = elf;
    relocated_metadata.relocations.push_back({elf.eh_frame_header, 0, 1027, 0});
    reject(relocated_metadata);
    auto file_offset = [&](uint64_t address) -> size_t {
      for (const auto &segment : elf.segments)
        if (address >= segment.address && address - segment.address < segment.file_size)
          return size_t(segment.offset + address - segment.address);
      throw std::runtime_error("unwind test address not file-backed");
    };
    auto value = [&](uint64_t address, unsigned width) {
      uint64_t result = 0;
      for (unsigned i = 0; i < width; ++i)
        result |= uint64_t(bytes.at(file_offset(address + i))) << (8 * i);
      return result;
    };
    uint64_t fde = uint64_t(int64_t(elf.eh_frame_header) +
        int32_t(value(elf.eh_frame_header + 16, 4)));
    require(value(fde + 16, 1) == 0 && value(fde, 4) >= 18,
            "zR fixture has empty FDE augmentation and CFI instructions");
    auto bad_cfi = [&](const std::vector<std::pair<uint64_t, uint8_t>> &changes) {
      auto malformed = bytes;
      for (const auto &change : changes)
        malformed.at(file_offset(change.first)) = change.second;
      try { UnwindRegistration invalid(elf, malformed); }
      catch (const ElfError &) { ++rejected; return; }
      throw std::runtime_error("accepted malformed CFI stream");
    };
    bad_cfi({{fde + 16, 127}}); // augmentation exceeds record.
    bad_cfi({{fde + 17, 0x16}}); // unsupported value expression.
    bad_cfi({{fde + 17, 0x0b}}); // state-stack underflow.
    bad_cfi({{fde + 17, 0x0d}, {fde + 18, 127}}); // invalid CFA register.
    bad_cfi({{fde, 16}, {fde + 1, 0}, {fde + 2, 0}, {fde + 3, 0},
             {fde + 17, 0x0e}, {fde + 18, 0x80}, {fde + 19, 0x80}});
    bad_cfi({{fde, 18}, {fde + 1, 0}, {fde + 2, 0}, {fde + 3, 0},
             {fde + 17, 4}, {fde + 18, 255}, {fde + 19, 255},
             {fde + 20, 255}, {fde + 21, 255}});
    require(rejected == 8, "metadata and CFI rejection count");
    for (unsigned attempt = 0; attempt < 3; ++attempt) {
      Image image(bytes);
      require(image.unwind_frame_count() >= 2, "automatic guest FDE registration");
      auto *entry = image.symbol("guest_unwind_frames");
      current_image = &image;
      auto result = invoke(entry, {Argument::integer(
          int64_t(reinterpret_cast<uintptr_t>(&capture)), ArgumentType::Int64)});
      require(result.result_integers[0] >= 2 && guest_frames >= 2,
              "native unwinder traverses both guest frames");
      current_image = nullptr;
    }
    std::cout << "M5_UNWIND_PASS guest_frames=" << guest_frames
              << " registration_cycles=3 metadata_rejections=8 wide_augmentation=2 wide_rejections="
              << wide_rejected << " exceptions_verified=0 islands_verified=0\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "unwind-probe: " << error.what() << '\n';
    return 1;
  }
}
