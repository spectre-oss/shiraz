// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/admission_plan.hpp"
#include "shiraz/darwin/guest/admission_session.hpp"
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include <algorithm>
#include <atomic>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <thread>
#include <utility>
#include <csignal>
#include <sys/wait.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <unistd.h>
using namespace shiraz::guest;
namespace fs = std::filesystem;
static uintptr_t link_only_validator(uintptr_t, uintptr_t, uintptr_t) {
    std::abort();
}
static uintptr_t isolated_validator(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            throw ElfError("no active admission domain");
        return domain->route(site, target, sp).target;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "M5_ISOLATED_ADMISSION_REJECTED %s\n", error.what());
        std::abort();
    }
}
static void restrict_process() {
    void* library = dlopen("/usr/lib/system/libsystem_sandbox.dylib", RTLD_NOW | RTLD_LOCAL);
    if (!library)
        throw ElfError("sandbox library unavailable");
    using Init = int (*)(const char*, uint64_t, char**);
    auto init = reinterpret_cast<Init>(dlsym(library, "sandbox_init"));
    if (!init)
        throw ElfError("sandbox initialization unavailable");
    const char* profile = "(version 1) (allow default) (deny network*) (deny file-write*) "
                          "(deny file-read-data) (deny process-fork) (deny process-exec*) "
                          "(deny signal (target others)) (deny mach-lookup) (deny iokit-open)";
    char* error = nullptr;
    if (init(profile, 0, &error))
        throw ElfError("sandbox initialization failed");
    int file = open("/etc/hosts", O_RDONLY);
    if (file >= 0) {
        close(file);
        throw ElfError("sandbox did not deny file reads");
    }
    int write_file = open("/dev/null", O_WRONLY);
    if (write_file >= 0) {
        close(write_file);
        throw ElfError("sandbox did not deny file writes");
    }
    int network = socket(AF_INET, SOCK_STREAM, 0);
    if (network >= 0) {
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_port = htons(9);
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        int connected = connect(network, reinterpret_cast<sockaddr*>(&address), sizeof(address));
        int failure = errno;
        close(network);
        if (!connected || (failure != EPERM && failure != EACCES))
            throw ElfError("sandbox did not explicitly deny network connection");
    } else if (errno != EPERM && errno != EACCES)
        throw ElfError("sandbox network check was inconclusive");
    std::fprintf(stderr,
                 "M5_ISOLATED_SANDBOX_ACTIVE file_read=denied file_write=denied network=denied\n");
}
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
static std::vector<uint8_t> read(const fs::path& path) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    auto size = file.tellg();
    require(bool(file) && size > 0 && size <= (256 << 20), "bounded ELF input");
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    file.seekg(0);
    require(bool(file.read(reinterpret_cast<char*>(bytes.data()), size)), "ELF read");
    return bytes;
}
static uint64_t u(const std::vector<uint8_t>& bytes, size_t offset, unsigned width) {
    uint64_t value = 0;
    for (unsigned i = 0; i < width; ++i)
        value |= uint64_t(bytes.at(offset + i)) << (8 * i);
    return value;
}
static void put(std::vector<uint8_t>& bytes, size_t offset, uint64_t value, unsigned width) {
    for (unsigned i = 0; i < width; ++i)
        bytes.at(offset + i) = uint8_t(value >> (8 * i));
}
static size_t offset(const Elf& elf, uint64_t address) {
    for (const auto& segment : elf.segments)
        if (address >= segment.address && address - segment.address < segment.file_size)
            return size_t(segment.offset + address - segment.address);
    throw std::runtime_error("fixture address not file backed");
}
static uint64_t symtab(const std::vector<uint8_t>& bytes) {
    uint64_t headers = u(bytes, 32, 8), stride = u(bytes, 54, 2), count = u(bytes, 56, 2);
    for (uint64_t i = 0; i < count; ++i) {
        auto p = headers + i * stride;
        if (u(bytes, p, 4) != 2)
            continue;
        auto start = u(bytes, p + 8, 8), size = u(bytes, p + 32, 8);
        for (uint64_t j = 0; j < size; j += 16) {
            auto tag = u(bytes, start + j, 8);
            if (!tag)
                break;
            if (tag == 6)
                return u(bytes, start + j + 8, 8);
        }
    }
    throw std::runtime_error("fixture symbol table absent");
}
int main(int argc, char** argv) {
    try {
        for (uint32_t reg = 0; reg < 31; ++reg) {
            for (auto [opcode, kind] : {std::pair{0xd61f0000u, IndirectTransferKind::Branch},
                                        std::pair{0xd63f0000u, IndirectTransferKind::Call},
                                        std::pair{0xd65f0000u, IndirectTransferKind::Return}}) {
                auto transfer = classify_indirect_transfer(0x1000, opcode | (reg << 5));
                require(transfer.kind == kind && transfer.target_register == reg &&
                            transfer.address == 0x1000,
                        "indirect branch classification");
            }
        }
        require(classify_indirect_transfer(0, 0xd61f03e0u).kind ==
                        IndirectTransferKind::Unsupported &&
                    classify_indirect_transfer(0, 0xd71f0800u).kind ==
                        IndirectTransferKind::Unsupported &&
                    classify_indirect_transfer(0, 0xd503201fu).kind ==
                        IndirectTransferKind::Unsupported,
                "reserved, authenticated and non-branch instructions are unsupported");
        if (argc == 4 && std::string(argv[1]) == "--inspect-entry") {
            auto bytes = read(argv[2]);
            auto elf = parse_elf(bytes, 16384);
            uint64_t candidate = std::stoull(argv[3], nullptr, 0);
            auto baseline = inspect_thread_pointers(elf, bytes, {});
            auto inspected = inspect_thread_pointers(elf, bytes, {candidate});
            constexpr uintptr_t base = 0x100000000ull;
            auto planned = plan_guest_admission(elf, bytes, inspected, base, base + elf.span,
                                                1 << 20, 0x1000, 0x2000);
            std::cout
                << "baseline_unresolved=" << baseline.unresolved.size()
                << " candidate_unresolved=" << inspected.unresolved.size()
                << " baseline_indirect=" << baseline.indirect_transfers.size()
                << " candidate_indirect=" << inspected.indirect_transfers.size() << " branches="
                << std::count_if(
                       inspected.indirect_transfers.begin(), inspected.indirect_transfers.end(),
                       [](const auto& entry) { return entry.kind == IndirectTransferKind::Branch; })
                << " calls="
                << std::count_if(
                       inspected.indirect_transfers.begin(), inspected.indirect_transfers.end(),
                       [](const auto& entry) { return entry.kind == IndirectTransferKind::Call; })
                << " returns="
                << std::count_if(
                       inspected.indirect_transfers.begin(), inspected.indirect_transfers.end(),
                       [](const auto& entry) { return entry.kind == IndirectTransferKind::Return; })
                << " unsupported_transfers="
                << std::count_if(inspected.indirect_transfers.begin(),
                                 inspected.indirect_transfers.end(),
                                 [](const auto& entry) {
                                     return entry.kind == IndirectTransferKind::Unsupported;
                                 })
                << " calls_via_lr="
                << std::count_if(inspected.indirect_transfers.begin(),
                                 inspected.indirect_transfers.end(),
                                 [](const auto& entry) {
                                     return entry.kind == IndirectTransferKind::Call &&
                                            entry.target_register == 30;
                                 })
                << " transfers_via_x18="
                << std::count_if(inspected.indirect_transfers.begin(),
                                 inspected.indirect_transfers.end(),
                                 [](const auto& entry) { return entry.target_register == 18; })
                << " tls_sites=" << inspected.sites.size()
                << " syscalls=" << inspected.syscalls.size() << " planned=" << planned.size()
                << '\n';
            return 0;
        }
        if (argc > 2 && std::string(argv[1]) == "--analyze") {
            bool passed = true;
            for (int i = 2; i < argc; ++i) {
                try {
                    auto bytes = read(argv[i]);
                    auto elf = parse_elf(bytes, 16384);
                    auto analysis = analyze_thread_pointers(elf, bytes);
                    std::cout << argv[i] << " sites=" << analysis.sites.size()
                              << " data=" << analysis.data_words << " decoded=" << analysis.decoded
                              << '\n';
                } catch (const std::exception& error) {
                    passed = false;
                    std::cout << argv[i] << " rejected=" << error.what() << '\n';
                }
            }
            return passed ? 0 : 1;
        }
        if (argc == 3 && (std::string(argv[1]) == "--admission-link-only" ||
                          std::string(argv[1]) == "--admission-run-isolated")) {
            bool execute = std::string(argv[1]) == "--admission-run-isolated";
            auto path = fs::canonical(argv[2]);
            Loader* current = nullptr;
            auto mutex = std::make_shared<std::recursive_mutex>();
            CodeValidator validate = [&](const void* p) {
                return current && current->contains_code(p);
            };
            auto lifecycle = std::make_shared<Lifecycle>(validate);
            auto signals = std::make_shared<SignalRuntime>(validate);
            auto cleanup = std::make_shared<CleanupRuntime>(validate);
            auto providers =
                dynamic_personality(native_personality(validate, lifecycle, signals, cleanup),
                                    {[&] { return current; }, mutex, [] {}});
            Loader loader(
                {path.parent_path()}, std::move(providers),
                execute ? Loader::Mode::AdmissionFixture : Loader::Mode::AdmissionLinkOnly,
                execute ? uintptr_t(&isolated_validator) : uintptr_t(&link_only_validator));
            current = &loader;
            (void)loader.link(path.filename());
            if (execute) {
                lifecycle->attach(loader);
                signals->attach(loader);
                cleanup->attach(loader);
                prepare_guest_tls();
                restrict_process();
                (void)loader.open(path.filename());
                std::cout << "M5_ISOLATED_CONSTRUCTORS_PASS count=" << loader.constructor_count()
                          << " jni=0 app=0\n";
                return 0;
            }
            std::cout << "M5_ADMISSION_LINK_ONLY_PASS images=" << loader.image_count()
                      << " bindings=" << loader.bindings().size() << " executed=0\n";
            return 0;
        }
        require(argc == 2, "usage: protected-probe fixture-directory");
        fs::path fixtures = fs::canonical(argv[1]);
        auto bytes = read(fixtures / "libprotected.so");
        auto elf = parse_elf(bytes, 16384);
        auto analysis = analyze_thread_pointers(elf, bytes);
        require(analysis.sites.size() >= 8 && analysis.data_words == 1 && analysis.decoded > 30 &&
                    elf.eh_frame_header_size,
                "compiler-protected code and declared literal provenance");
        size_t literal = 0, function = 0, registration = 0, methods = 0;
        for (size_t i = 0; i < elf.symbols.size(); ++i) {
            if (elf.symbols[i].name == "guard_literal")
                literal = i;
            if (elf.symbols[i].name == "protected_sum")
                function = i;
            if (elf.symbols[i].name == "register_fixture")
                registration = i;
            if (elf.symbols[i].name == "jni_method_table")
                methods = i;
        }
        require(literal && function && registration && methods, "fixture code/data symbols");
        auto table_relocation = [&](uint64_t address) -> const Relocation& {
            for (const auto& relocation : elf.relocations)
                if (relocation.address == address)
                    return relocation;
            throw std::runtime_error("fixture JNI table relocation missing");
        };
        uint64_t methods_address = elf.symbols[methods].address;
        uint64_t callback_address = uint64_t(table_relocation(methods_address + 16).addend);
        require(std::any_of(analysis.sites.begin(), analysis.sites.end(),
                            [&](const auto& site) { return site.address == callback_address; }),
                "private JNI callback discovered through registration table");
        auto table = offset(elf, symtab(bytes));
        size_t rejected = 0;
        auto reject = [&](const std::function<void(std::vector<uint8_t>&)>& mutate,
                          const char* reason) {
            auto malformed = bytes;
            mutate(malformed);
            try {
                Image image(malformed, Image::Mapping::Deferred);
            } catch (const ElfError& error) {
                if (std::string(error.what()).find(reason) == std::string::npos)
                    std::cerr << "provenance rejection mismatch expected=" << reason
                              << " actual=" << error.what() << '\n';
                require(std::string(error.what()).find(reason) != std::string::npos,
                        "specific provenance rejection");
                ++rejected;
                return;
            }
            throw std::runtime_error("accepted malformed instruction provenance");
        };
        reject([&](auto& b) { put(b, table + literal * 24 + 4, 0x10, 1); }, "ambiguous");
        reject([&](auto& b) { put(b, offset(elf, analysis.sites[0].address), 0xd51bd040, 4); },
               "writes");
        reject([&](auto& b) { put(b, offset(elf, analysis.sites[0].address), 0xd53bd052, 4); },
               "x18");
        reject([&](auto& b) { put(b, offset(elf, elf.symbols[function].address), 0xffffffff, 4); },
               "invalid reachable");
        reject([&](auto& b) { put(b, offset(elf, elf.symbols[function].address), 0xd4000001, 4); },
               "Linux syscall dispatcher");
        reject([&](auto& b) { put(b, offset(elf, elf.symbols[function].address), 0xd4000021, 4); },
               "unsupported guest SVC immediate");
        reject([&](auto& b) { put(b, offset(elf, elf.symbols[function].address), 0x16000000, 4); },
               "target outside");
        reject([&](auto& b) { put(b, table + literal * 24 + 8, elf.symbols[function].address, 8); },
               "overlaps declared data");
        size_t header = offset(elf, elf.eh_frame_header);
        // An FDE is an unwind range, not proof that every word in the range is
        // code. Extend the last fixture FDE across the literal and remove the
        // literal's object declaration: it must remain ambiguous, not rewritten.
        reject(
            [&](auto& b) {
                uint64_t count = u(b, header + 8, 4);
                require(count, "fixture unwind entries");
                size_t last = header + 12 + size_t(count - 1) * 8;
                uint64_t start = uint64_t(int64_t(elf.eh_frame_header) + int32_t(u(b, last, 4)));
                uint64_t fde_address =
                    uint64_t(int64_t(elf.eh_frame_header) + int32_t(u(b, last + 4, 4)));
                require(start < elf.symbols[literal].address, "fixture FDE precedes literal");
                uint64_t extent = elf.symbols[literal].address + 4 - start;
                require(extent <= INT32_MAX, "fixture FDE extent");
                put(b, offset(elf, fde_address) + 12, extent, 4);
                put(b, table + literal * 24 + 4, 0x10, 1);
            },
            "ambiguous");
        uint64_t fde = uint64_t(int64_t(elf.eh_frame_header) + int32_t(u(bytes, header + 16, 4)));
        size_t fde_offset = offset(elf, fde);
        size_t cie_offset = offset(elf, fde + 4 - u(bytes, fde_offset + 4, 4));
        reject([&](auto& b) { put(b, header, 2, 1); }, "header encoding");
        reject([&](auto& b) { put(b, header + 8, UINT32_MAX, 4); }, "table bounds");
        reject([&](auto& b) { put(b, header + 12, u(b, header + 12, 4) + 4, 4); },
               "function mismatch");
        reject([&](auto& b) { put(b, fde_offset, UINT32_MAX, 4); }, "invalid unwind FDE");
        reject([&](auto& b) { put(b, fde_offset + 4, 0, 4); }, "invalid unwind FDE");
        reject([&](auto& b) { put(b, cie_offset + 8, 9, 1); }, "CIE version");
        reject([&](auto& b) { put(b, cie_offset, 8, 4); }, "LEB");
        // A later runtime entry can carry JNI table/register provenance into a
        // call site already reached from an earlier entry. Re-analysis must not
        // discard that provenance just because the call instruction was visited.
        {
            auto dynamic = elf;
            const uint64_t entry = dynamic.symbols[registration].address;
            dynamic.symbols[registration].info = 0; // no automatic function root
            auto call_only = inspect_thread_pointers(dynamic, bytes, {entry + 28});
            auto with_setup = inspect_thread_pointers(dynamic, bytes, {entry + 28, entry});
            auto discovers = [&](const auto& result) {
                return std::any_of(result.sites.begin(), result.sites.end(), [&](const auto& site) {
                    return site.address == callback_address;
                });
            };
            require(!discovers(call_only) && discovers(with_setup),
                    "new entry propagates JNI registration provenance into visited call");
        }
        // A new path can reach the call before the old setup path. Both entry
        // orders must preserve the callback and avoid duplicate transfer patches.
        {
            auto dynamic = elf;
            auto augmented = bytes;
            const uint64_t entry = dynamic.symbols[registration].address;
            dynamic.symbols[registration].info = 0;
            const uint64_t added = dynamic.first_page + dynamic.span;
            const int64_t displacement = int64_t(entry + 28) - int64_t(added);
            require(!(displacement & 3) && displacement >= -(1ll << 27) &&
                        displacement < (1ll << 27),
                    "fixture branch displacement");
            uint32_t branch = 0x14000000u | (uint32_t(displacement / 4) & 0x03ffffffu);
            auto new_offset = augmented.size();
            augmented.resize(new_offset + 4);
            std::memcpy(augmented.data() + new_offset, &branch, 4);
            dynamic.segments.push_back({new_offset, added, 4, 4, 5});
            dynamic.span += 4;
            auto before = inspect_thread_pointers(dynamic, augmented, {entry});
            auto after = inspect_thread_pointers(dynamic, augmented, {entry, added});
            auto reverse = inspect_thread_pointers(dynamic, augmented, {added, entry});
            auto discovers = [&](const auto& result) {
                return std::any_of(result.sites.begin(), result.sites.end(), [&](const auto& site) {
                    return site.address == callback_address;
                });
            };
            require(discovers(before) && discovers(after) && discovers(reverse) &&
                        after.decoded_bitmap == reverse.decoded_bitmap,
                    "both entry orders retain JNI provenance");
            for (size_t i = 0; i < before.decoded_bitmap.size(); ++i)
                require(!(before.decoded_bitmap[i] & ~after.decoded_bitmap[i]),
                        "previous code retained");
            auto unique_transfers = [](const auto& result) {
                auto transfers = result.indirect_transfers;
                std::sort(transfers.begin(), transfers.end(),
                          [](const auto& a, const auto& b) { return a.address < b.address; });
                return std::adjacent_find(transfers.begin(), transfers.end(),
                                          [](const auto& a, const auto& b) {
                                              return a.address == b.address;
                                          }) == transfers.end();
            };
            require(unique_transfers(after) && unique_transfers(reverse),
                    "replay emits no duplicate transfers");
            auto malformed = augmented;
            put(malformed, offset(dynamic, entry + 16), 0x52800003, 4);
            for (auto roots :
                 {std::vector<uint64_t>{entry, added}, std::vector<uint64_t>{added, entry}}) {
                bool rejected = false;
                try {
                    (void)inspect_thread_pointers(dynamic, malformed, roots);
                } catch (const ElfError& error) {
                    rejected =
                        std::string(error.what()).find("JNI table bounds") != std::string::npos;
                }
                require(rejected, "malformed JNI table rejected in either entry order");
            }
            const size_t image_size = (dynamic.span + 15) & ~size_t(15);
            const size_t mapping_size = image_size + (1 << 18);
            auto* mapping = static_cast<uint8_t*>(mmap(nullptr, mapping_size,
                                                       PROT_READ | PROT_WRITE | PROT_EXEC,
                                                       MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
            require(mapping != MAP_FAILED, "admission provenance mapping");
            {
                JitWriteScope writable;
                for (const auto& segment : dynamic.segments)
                    std::memcpy(mapping + segment.address - dynamic.first_page,
                                augmented.data() + segment.offset, segment.file_size);
            }
            {
                const uintptr_t base = reinterpret_cast<uintptr_t>(mapping);
                AdmissionSession session(dynamic, augmented, base, base + image_size, 1 << 18,
                                         uintptr_t(&link_only_validator),
                                         uintptr_t(&link_only_validator));
                session.initialize();
                require(session.admit(base + entry - dynamic.first_page), "first entry admitted");
                uint32_t callback_before;
                std::memcpy(&callback_before, mapping + callback_address - dynamic.first_page, 4);
                require(session.admit(base + added - dynamic.first_page),
                        "overlapping entry admitted");
                uint32_t callback_after, new_word;
                std::memcpy(&callback_after, mapping + callback_address - dynamic.first_page, 4);
                std::memcpy(&new_word, mapping + added - dynamic.first_page, 4);
                require(callback_after == callback_before && new_word == branch &&
                            !session.admit(base + entry - dynamic.first_page) &&
                            !session.admit(base + added - dynamic.first_page),
                        "overlapping admission preserves published state and remains idempotent");
            }
            require(!munmap(mapping, mapping_size), "admission provenance unmap");
        }
        auto registration_code = offset(elf, elf.symbols[registration].address);
        {
            auto undiscovered = bytes;
            put(undiscovered, registration_code + 16, 0xd503201f, 4);
            auto baseline = inspect_thread_pointers(elf, undiscovered, {});
            auto candidate = inspect_thread_pointers(elf, undiscovered, {callback_address});
            require(std::any_of(baseline.unresolved.begin(), baseline.unresolved.end(),
                                [&](const auto& site) { return site.address == callback_address; }),
                    "inspection reports unresolved private callback");
            require(
                std::none_of(candidate.unresolved.begin(), candidate.unresolved.end(),
                             [&](const auto& site) { return site.address == callback_address; }) &&
                    std::any_of(candidate.sites.begin(), candidate.sites.end(),
                                [&](const auto& site) { return site.address == callback_address; }),
                "candidate inspection discovers private callback");
            require(!candidate.indirect_transfers.empty(), "inspection reports indirect transfers");
            require(std::any_of(
                        candidate.indirect_transfers.begin(), candidate.indirect_transfers.end(),
                        [&](const auto& site) { return site.address == callback_address + 12; }),
                    "inspection reports an indirect return");
            bool rejected = false;
            try {
                (void)inspect_thread_pointers(elf, undiscovered, {elf.symbols[literal].address});
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "candidate in declared data must be rejected");
            rejected = false;
            try {
                (void)inspect_thread_pointers(elf, undiscovered, {UINT64_MAX});
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "candidate outside guest code must be rejected");
            constexpr uintptr_t base = 0x100000000ull;
            const uintptr_t islands = base + elf.span;
            auto batch = plan_guest_admission(elf, undiscovered, candidate, base, islands, 1 << 20,
                                              0x1000, 0x2000);
            require(batch.size() == candidate.sites.size() + candidate.syscalls.size() +
                                        candidate.indirect_transfers.size(),
                    "complete candidate patch preflight");
            require(std::is_sorted(batch.begin(), batch.end(),
                                   [](const auto& a, const auto& b) {
                                       return a.guest_address < b.guest_address;
                                   }),
                    "patch batch is ordered");
            auto refuse = [&](const ThreadPointerAnalysis& input, uintptr_t pool, size_t capacity) {
                bool failed = false;
                try {
                    (void)plan_guest_admission(elf, undiscovered, input, base, pool, capacity,
                                               0x1000, 0x2000);
                } catch (const ElfError&) {
                    failed = true;
                }
                require(failed, "unsafe admission plan rejected");
            };
            refuse(candidate, islands, 0);
            refuse(candidate, base, 1 << 20);
            auto duplicate = candidate;
            duplicate.sites.push_back(candidate.sites.front());
            refuse(duplicate, islands, 1 << 20);
            auto outside = candidate;
            outside.sites.push_back({UINT64_MAX, 0xd53bd040});
            refuse(outside, islands, 1 << 20);
            auto unsupported = candidate;
            unsupported.indirect_transfers.front().instruction = 0xd71f0800u;
            refuse(unsupported, islands, 1 << 20);
            auto stale = undiscovered;
            stale.at(offset(elf, candidate.sites.front().address)) ^= 1;
            rejected = false;
            try {
                (void)plan_guest_admission(elf, stale, candidate, base, islands, 1 << 20, 0x1000,
                                           0x2000);
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "admission source mutation must be rejected");
            rejected = false;
            try {
                (void)plan_guest_admission(elf, undiscovered, candidate, base, islands, 1 << 20,
                                           0x1000, 0);
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "indirect patches require a validator");
        }
        // The count is MOV w3,#1 at the fixed assembly fixture's fifth word.
        reject([&](auto& b) { put(b, registration_code + 16, 0x52800003, 4); }, "JNI table bounds");
        reject([&](auto& b) { put(b, registration_code + 16, 0x52820023, 4); }, "JNI table bounds");
        uint64_t signature_address = uint64_t(table_relocation(methods_address + 8).addend);
        reject([&](auto& b) { put(b, offset(elf, signature_address), '[', 1); },
               "invalid JNI method descriptor");
        // Losing either the table count or the vtable load chain must not turn
        // arbitrary relocated code pointers into independent decoder roots.
        reject([&](auto& b) { put(b, registration_code + 16, 0xd503201f, 4); }, "ambiguous");
        reject([&](auto& b) { put(b, registration_code + 24, 0xf9400508, 4); }, "ambiguous");
        auto reject_table_pointer = [&](bool missing) {
            auto malformed = elf;
            auto found = std::find_if(
                malformed.relocations.begin(), malformed.relocations.end(),
                [&](const auto& relocation) { return relocation.address == methods_address + 16; });
            require(found != malformed.relocations.end(), "JNI callback relocation exists");
            if (missing)
                malformed.relocations.erase(found);
            else {
                found->type = 1027;
                found->symbol = 0;
                found->addend = 0;
            }
            try {
                analyze_thread_pointers(malformed, bytes);
            } catch (const ElfError& error) {
                require(
                    std::string(error.what()).find(missing ? "lacks relocation" : "outside code") !=
                        std::string::npos,
                    "specific JNI pointer rejection");
                ++rejected;
                return;
            }
            throw std::runtime_error("accepted malformed JNI callback pointer");
        };
        reject_table_pointer(true);
        reject_table_pointer(false);
        // Inspect a sealed but deliberately unexecuted fixture. Zero relocation
        // targets here are not execution stubs and never enter the real Loader.
        {
            Image inspected(bytes, Image::Mapping::Deferred);
            bool unsealed_refused = false;
            try {
                (void)inspected.verified_mrs_window_sites();
            } catch (const ElfError& error) {
                unsealed_refused = std::string(error.what()) ==
                                   "MRS installation evidence requires a sealed image";
            }
            require(unsealed_refused, "unsealed mappings cannot attest MRS installation");
            inspected.relocate([](size_t) { return uintptr_t(0); });
            inspected.seal();
            auto expected_sites = std::vector<uint64_t>{};
            for (const auto& site : analysis.sites)
                if ((site.instruction & 31) != 31)
                    expected_sites.push_back(site.address);
            require(!expected_sites.empty() &&
                        inspected.verified_mrs_window_sites() == expected_sites,
                    "mapped default-accessor patches attest exact non-XZR site identities");
            auto site_index = size_t(
                std::find_if(analysis.sites.begin(), analysis.sites.end(),
                             [](const auto& site) { return (site.instruction & 31) != 31; }) -
                analysis.sites.begin());
            auto* code = reinterpret_cast<uint8_t*>(inspected.load_bias() + expected_sites.front());
            auto* accessor_byte =
                reinterpret_cast<uint8_t*>(inspected.load_bias() + inspected.elf().first_page +
                                           inspected.elf().span + site_index * 64 + 56);
            auto changed_refused = [&](uint8_t* byte, const char* message) {
                const uint8_t original = *byte;
                {
                    JitWriteScope writable;
                    *byte ^= 1;
                }
                bool refused = false;
                try {
                    (void)inspected.verified_mrs_window_sites();
                } catch (const ElfError& error) {
                    refused = std::string(error.what()) == message;
                }
                {
                    JitWriteScope writable;
                    *byte = original;
                }
                require(refused && inspected.verified_mrs_window_sites() == expected_sites,
                        "changed mapped instruction/accessor cannot attest; restoration remains "
                        "verifiable");
            };
            changed_refused(code, "guest MRS installation changed");
            changed_refused(accessor_byte, "guest MRS island installation changed");
            std::cout << "M5_MRS_INSTALLATION_PASS unsealed_refused=1 branch_tamper=1 "
                         "accessor_tamper=1 restored=1 execution_claim=0\n";
        }
        Loader* current = nullptr;
        Loader loader({fixtures}, native_personality([&](const void* p) {
                          return current && current->contains_code(p);
                      }));
        current = &loader;
        auto* module = loader.link("libprotected.so");
        require(!loader.constructor_count() &&
                    loader.rewritten_thread_pointers() == analysis.sites.size(),
                "all planned instructions patched before constructors");
        const auto loaded = loader.module_info();
        require(loaded.size() == 1 && !loader.verified_mrs_window_sites(loaded[0].name).empty(),
                "Loader exposes verified installed sites only for an actual sealed module");
        bool missing_module_refused = false;
        try {
            (void)loader.verified_mrs_window_sites("not-a-loaded-module");
        } catch (const ElfError&) {
            missing_module_refused = true;
        }
        require(missing_module_refused, "MRS installation evidence refuses fake module paths");
        loader.open("libprotected.so");
        require(loader.constructor_count() == 1 && loader.image_count() == 1,
                "real protected constructor executed");
        auto function_address = loader.symbol(module, "protected_sum").address;
        auto literal_address = loader.symbol(module, "literal_bits").address;
        require(uint32_t(invoke(reinterpret_cast<void*>(literal_address), {}).result_integers[0]) ==
                    0xd53bd040,
                "declared literal remains byte-exact");
        require(int(invoke(reinterpret_cast<void*>(function_address), {Argument::integer(10)})
                        .result_integers[0]) == 61,
                "real compiler stack-protected call");
        auto mapped_methods = loader.symbol(module, "jni_method_table").address;
        uint64_t mapped_callback = 0;
        std::memcpy(&mapped_callback, reinterpret_cast<const void*>(mapped_methods + 16), 8);
        require(int(invoke(reinterpret_cast<void*>(mapped_callback), {}).result_integers[0]) == 97,
                "registration-derived private JNI callback executes with rewritten TLS");
        auto getter = loader.symbol(module, "protected_callback").address;
        auto callback = invoke(reinterpret_cast<void*>(getter), {}).result_integers[0];
        require(loader.contains_code(reinterpret_cast<void*>(callback)) &&
                    int(invoke(reinterpret_cast<void*>(callback), {Argument::integer(5)})
                            .result_integers[0]) == 27,
                "private indirect callback discovered from validated FDE metadata");
        std::atomic<int> passed{0};
        std::vector<std::thread> threads;
        for (int i = 0; i < 4; ++i)
            threads.emplace_back([&, i] {
                prepare_guest_tls();
                for (int repeat = 0; repeat < 64; ++repeat)
                    if (int(invoke(reinterpret_cast<void*>(function_address),
                                   {Argument::integer(i)})
                                .result_integers[0]) != 2 * i + 41)
                        return;
                ++passed;
            });
        for (auto& thread : threads)
            thread.join();
        require(passed == 4, "concurrent protected ELF calls");
        auto guard = loader.symbol(module, "guard_checked").address;
        require(int(invoke(reinterpret_cast<void*>(guard), {Argument::integer(0)})
                        .result_integers[0]) == 73,
                "automatically rewritten ELF stack-guard check");
        pid_t child = fork();
        require(child >= 0, "protected ELF corruption child");
        if (!child) {
            std::signal(SIGABRT, [](int) { _exit(97); });
            invoke(reinterpret_cast<void*>(guard), {Argument::integer(1)});
            _exit(98);
        }
        int status;
        require(waitpid(child, &status, 0) == child && WIFEXITED(status) &&
                    WEXITSTATUS(status) == 97,
                "actual protected ELF corruption aborts");
        std::cout << "M5_PROTECTED_ELF_PASS rewritten=" << analysis.sites.size()
                  << " constructor=1 private_helper=1 data_literal=1 provenance_rejections="
                  << rejected
                  << " indirect_callback=1 jni_callback=1 corruption_abort=1 threads=4 "
                     "elf_auto_rewrite=1 unwind_verified=0\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
