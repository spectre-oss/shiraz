// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/system.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "guest_errno.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cstring>
#include <iostream>
#include <sys/resource.h>
#include <sys/sysctl.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
static Argument pointer(const void* value) {
    return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
static bool feature(const char* name) {
    int32_t value = 0;
    size_t size = sizeof(value);
    return !sysctlbyname(name, &value, &size, nullptr, 0) && size == sizeof(value) && value == 1;
}
int main(int argc, char** argv) {
    try {
        require(argc == 2, "usage: system-probe fixture-directory");
        auto base = native_personality();
        auto providers = std::move(base);
        Loader loader({argv[1]}, std::move(providers));
        auto* module = loader.open("libsystem-fixture.so");
        auto call = [&](const char* name, const std::vector<Argument>& arguments) {
            return invoke(reinterpret_cast<void*>(loader.symbol(module, name).address), arguments)
                .result_integers[0];
        };
        int guest_error = 0;
        auto conf = [&](int selector) {
            errno = 61;
            auto value = call("system_conf", {Argument::integer(selector), pointer(&guest_error)});
            require(errno == 61, "sysconf preserves native errno");
            return long(int64_t(value));
        };
        struct Selector {
            int guest, host;
        };
        const Selector selectors[] = {
            {0, _SC_ARG_MAX},           {5, _SC_CHILD_MAX},
            {6, _SC_CLK_TCK},           {10, _SC_NGROUPS_MAX},
            {11, _SC_OPEN_MAX},         {27, _SC_STREAM_MAX},
            {38, _SC_IOV_MAX},          {39, _SC_PAGESIZE},
            {40, _SC_PAGESIZE},         {74, _SC_THREAD_DESTRUCTOR_ITERATIONS},
            {96, _SC_NPROCESSORS_CONF}, {97, _SC_NPROCESSORS_ONLN},
            {98, _SC_PHYS_PAGES}};
        for (const auto& selector : selectors) {
            long expected = ::sysconf(selector.host);
            require(conf(selector.guest) == expected && guest_error == 73,
                    "Linux selector explicitly queries matching host property");
        }
        require(conf(75) == std::min(::sysconf(_SC_THREAD_KEYS_MAX), 4096L) && guest_error == 73 &&
                    conf(76) == std::max(::sysconf(_SC_THREAD_STACK_MIN), 16384L) &&
                    guest_error == 73 && conf(77) == 128 && guest_error == 73 &&
                    conf(54) == 0x3fffffff && guest_error == 73,
                "thread and semaphore limits match guest-backed services");
        require(conf(99) >= 0 && guest_error == 73, "real free physical page count");
        for (int selector : {-1, 0x7fffffff, 123456})
            require(conf(selector) == -1 && guest_error == 22, "unknown sysconf is guest EINVAL");
        auto aux = [&](unsigned long selector) {
            errno = 61;
            auto value = call("system_aux", {Argument::integer(selector, ArgumentType::Int64),
                                             pointer(&guest_error)});
            require(errno == 61, "auxiliary query preserves native errno");
            return value;
        };
        for (const auto& entry : std::vector<std::pair<unsigned long, unsigned long>>{
                 {6, unsigned(::getpagesize())},
                 {11, ::getuid()},
                 {12, ::geteuid()},
                 {13, ::getgid()},
                 {14, ::getegid()},
                 {17, static_cast<unsigned long>(::sysconf(_SC_CLK_TCK))},
                 {23, ::issetugid() ? 1ul : 0ul}})
            require(aux(entry.first) == entry.second && guest_error == 73,
                    "auxiliary value reflects host process");
        auto first = aux(16), second = aux(26);
        require(guest_error == 73 &&
                    !(first & ((1ul << 11) | (1ul << 22) | (1ul << 30) | (1ul << 31))) &&
                    !(second & ((1ul << 17) | (1ul << 18) | (1ul << 23))),
                "no Linux CPUID/PAC/SVE/BTI/MTE/SME facilities are advertised");
        const std::pair<const char*, unsigned> checks[] = {
            {"hw.optional.floatingpoint", 0}, {"hw.optional.neon", 1},
            {"hw.optional.arm.FEAT_AES", 3},  {"hw.optional.arm.FEAT_PMULL", 4},
            {"hw.optional.arm.FEAT_SHA1", 5}, {"hw.optional.arm.FEAT_SHA256", 6},
            {"hw.optional.armv8_crc32", 7},   {"hw.optional.arm.FEAT_LSE", 8}};
        for (const auto& check : checks)
            require(bool(first & (1ul << check.second)) == feature(check.first),
                    "HWCAP follows actual host feature query");
        auto random = aux(25);
        require(random && aux(25) == random && guest_error == 73,
                "one stable process random block");
        std::array<unsigned char, 16> sample{};
        std::memcpy(sample.data(), reinterpret_cast<const void*>(random), sample.size());
        for (unsigned long absent : {0ul, 3ul, 7ul, 9ul, 15ul, 31ul, 33ul, ~0ul})
            require(!aux(absent) && guest_error == 2,
                    "missing auxiliary entry is ENOENT, no fake ELF/vDSO");
        unsigned long identifiers[4]{};
        require(call("system_identifiers", {pointer(identifiers)}) == 73 &&
                    identifiers[0] == unsigned(::getpid()) && identifiers[1] == ::getuid() &&
                    identifiers[2] == ::geteuid() && identifiers[3] == unsigned(::getppid()),
                "actual process scalar identifiers");
        require(call("system_yield", {}) == 73,
                "real host scheduling yield preserves guest errno on success");
        long cpu_count = ::sysconf(_SC_NPROCESSORS_ONLN);
        require(cpu_count > 0 && cpu_count <= 32768, "bounded online CPU count");
        std::vector<uint8_t> mask(size_t((cpu_count + 7) / 8), 0);
        for (long cpu = 0; cpu < cpu_count; ++cpu)
            mask[size_t(cpu / 8)] |= uint8_t(1u << (cpu % 8));
        auto affinity = [&](int pid, const void* data, size_t size) {
            errno = 61;
            int value = int(
                int32_t(call("system_setaffinity",
                             {Argument::integer(pid), Argument::integer(size, ArgumentType::Int64),
                              pointer(data), pointer(&guest_error)})));
            require(errno == 61, "setaffinity preserves native errno");
            return value;
        };
        auto original = mask;
        require(affinity(0, mask.data(), mask.size()) == 0 && guest_error == 73 &&
                    mask == original && affinity(int(::getpid()), mask.data(), mask.size()) == 0 &&
                    guest_error == 73,
                "full online mask succeeds without changing host scheduling");
        std::fill(mask.begin(), mask.end(), 0);
        require(affinity(0, mask.data(), mask.size()) == -1 && guest_error == bionic::kEINVAL &&
                    affinity(0, nullptr, mask.size()) == -1 && guest_error == bionic::kEFAULT &&
                    affinity(0, mask.data(), 0) == -1 && guest_error == bionic::kEINVAL &&
                    affinity(123456, mask.data(), mask.size()) == -1 &&
                    guest_error == bionic::kENOTSUP,
                "empty, null and untracked thread requests fail explicitly");
        if (cpu_count > 1) {
            mask = original;
            mask[0] &= uint8_t(~1u);
            require(affinity(0, mask.data(), mask.size()) == -1 && guest_error == bionic::kENOTSUP,
                    "restricted CPU mask is explicitly unsupported");
        }
        struct GuardedUsage {
            uint64_t before[2];
            int64_t fields[18];
            uint64_t after[2];
        } usage{};
        auto usage_call = [&](int who, void* output) {
            errno = 61;
            auto value = call("system_usage",
                              {Argument::integer(who), pointer(output), pointer(&guest_error)});
            require(errno == 61, "getrusage preserves native errno");
            return int(int32_t(value));
        };
        for (int who : {0, -1}) {
            std::memset(&usage, 0xa5, sizeof(usage));
            ::rusage before{}, after{};
            int native_who = who == 0 ? RUSAGE_SELF : RUSAGE_CHILDREN;
            require(::getrusage(native_who, &before) == 0 && usage_call(who, usage.fields) == 0 &&
                        guest_error == 73 && ::getrusage(native_who, &after) == 0,
                    "guest and host resource queries succeed");
            auto micros = [](int64_t seconds, int64_t usecs) { return seconds * 1000000 + usecs; };
            auto guest_user = micros(usage.fields[0], usage.fields[1]);
            auto guest_system = micros(usage.fields[2], usage.fields[3]);
            require(usage.before[0] == 0xa5a5a5a5a5a5a5a5ULL &&
                        usage.before[1] == usage.before[0] && usage.after[0] == usage.before[0] &&
                        usage.after[1] == usage.before[0] && usage.fields[1] >= 0 &&
                        usage.fields[1] < 1000000 && usage.fields[3] >= 0 &&
                        usage.fields[3] < 1000000 &&
                        guest_user >= micros(before.ru_utime.tv_sec, before.ru_utime.tv_usec) &&
                        guest_user <= micros(after.ru_utime.tv_sec, after.ru_utime.tv_usec) &&
                        guest_system >= micros(before.ru_stime.tv_sec, before.ru_stime.tv_usec) &&
                        guest_system <= micros(after.ru_stime.tv_sec, after.ru_stime.tv_usec) &&
                        usage.fields[4] >= before.ru_maxrss / 1024 &&
                        usage.fields[4] <= after.ru_maxrss / 1024,
                    "guest rusage layout and units match real host counters");
        }
        std::memset(&usage, 0xa5, sizeof(usage));
        require(usage_call(1, usage.fields) == -1 && guest_error == bionic::kENOTSUP &&
                    usage.fields[0] == int64_t(0xa5a5a5a5a5a5a5a5ULL),
                "unsupported thread usage leaves output unchanged");
        require(usage_call(12345, usage.fields) == -1 && guest_error == bionic::kEINVAL &&
                    usage_call(0, nullptr) == -1 && guest_error == bionic::kEFAULT,
                "invalid selector and null output fail explicitly");
        std::array<unsigned char, 256> sysinfo_buffer{};
        sysinfo_buffer.fill(0xa5);
        const auto original_sysinfo = sysinfo_buffer;
        errno = 61;
        require(int(int32_t(call("system_sysinfo",
                                 {pointer(sysinfo_buffer.data()), pointer(&guest_error)}))) == -1 &&
                    guest_error == bionic::kENOSYS && errno == 61 &&
                    sysinfo_buffer == original_sysinfo,
                "unsupported sysinfo preserves output and host errno");
        require(int(int32_t(call("system_sysinfo", {pointer(reinterpret_cast<void*>(1)),
                                                    pointer(&guest_error)}))) == -1 &&
                    guest_error == bionic::kENOSYS,
                "unsupported sysinfo does not access an invalid guest output pointer");
        auto name = reinterpret_cast<const char*>(call("system_program_name", {}));
        require(name && ::getprogname() && !std::strcmp(name, ::getprogname()),
                "program name reflects the host executable basename");
        std::atomic<int> passed{0};
        std::vector<std::thread> workers;
        for (int i = 0; i < 4; ++i)
            workers.emplace_back([&] {
                int error = 0;
                auto address = call("system_aux",
                                    {Argument::integer(25, ArgumentType::Int64), pointer(&error)});
                if (address == random && error == 73)
                    ++passed;
            });
        for (auto& worker : workers)
            worker.join();
        require(passed == 4, "process random pointer is shared across guest threads");
        std::cout
            << "M5_SYSTEM_PASS selectors=explicit aux=process-backed hwcap=sysctl "
               "unsupported_facilities=0 random=16 threads=4 errno=preserved raw_syscalls=0\n";
    } catch (const std::exception& failure) {
        std::cerr << "FAIL " << failure.what() << '\n';
        return 1;
    }
}
