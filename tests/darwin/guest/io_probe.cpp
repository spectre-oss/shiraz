// SPDX-License-Identifier: GPL-3.0-only
#include "guest_io.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include <csignal>
#include <filesystem>
#include <iostream>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

using namespace shiraz::guest;
namespace fs = std::filesystem;
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
static Argument pointer(const void* value) {
    return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char** argv) {
    try {
        require(argc == 2, "usage: io-probe fixture-directory");
        auto fixtures = fs::canonical(argv[1]);
        std::string temporary = (fixtures.parent_path() / "io-work-XXXXXX").string();
        char* created = mkdtemp(temporary.data());
        require(created, "exclusive IO fixture directory");
        fs::path work = fs::canonical(created), previous = fs::current_path();
        fs::current_path(work);
        mode_t mask = ::umask(0);
        Loader loader({fixtures}, file_personality());
        auto* module = loader.open("libio.so");
        require(loader.image_count() == 1,
                "I/O must use the explicit libc provider, never link-only stubs");
        std::string root = work.string();
        auto function = loader.symbol(module, "run_io");
        errno = ELOOP;
        int value = int(invoke(reinterpret_cast<void*>(function.address), {pointer(root.c_str())})
                            .result_integers[0]);
        if (value)
            throw std::runtime_error("guest I/O failed at source line " + std::to_string(value));
        require(errno == ELOOP, "host errno preserved across real guest filesystem calls");
        // AAPCS64 permits dirty high bits on a named int ioctl request.
        int fds[2];
        require(::pipe(fds) == 0, "native pipe");
        require(::write(fds[1], "abc", 3) == 3, "native pipe data");
        int available = -1;
        auto ioctl = loader.symbol(module, "ioctl");
        value = int(invoke(reinterpret_cast<void*>(ioctl.address),
                           {
                               Argument::integer(fds[0]),
                               Argument::integer(0x7a7a7a7a00000000ULL | bionic::kFIONREAD,
                                                 ArgumentType::Int64),
                               pointer(&available),
                           })
                        .result_integers[0]);
        require(value == 0 && available == 3, "ioctl request ignores unspecified upper 32 bits");
        require(::close(fds[0]) == 0 && ::close(fds[1]) == 0, "native pipe cleanup");
        auto open = loader.symbol(module, "__open_2");
        pid_t child = ::fork();
        require(child >= 0, "fortify child");
        if (!child) {
            std::signal(SIGABRT, [](int) { _exit(97); });
            invoke(reinterpret_cast<void*>(open.address),
                   {pointer("fortify-must-not-exist"),
                    Argument::integer(bionic::kO_CREAT | bionic::kO_WRONLY)});
            _exit(98);
        }
        int status;
        require(::waitpid(child, &status, 0) == child, "fortify child wait");
        require(WIFEXITED(status) && WEXITSTATUS(status) == 97 &&
                    !fs::exists("fortify-must-not-exist"),
                "checked open without mode aborts before creating a file");
        require(::pipe(fds) == 0 && ::write(fds[1], "safe", 4) == 4, "checked read pipe");
        auto read_checked = loader.symbol(module, "__read_chk");
        for (bool signed_overflow : {false, true}) {
            child = ::fork();
            require(child >= 0, "checked read child");
            if (!child) {
                std::signal(SIGABRT, [](int) { _exit(97); });
                char buffer[2];
                uint64_t count = signed_overflow ? (uint64_t(1) << 63) : 4;
                uint64_t size = signed_overflow ? UINT64_MAX : sizeof(buffer);
                invoke(reinterpret_cast<void*>(read_checked.address),
                       {Argument::integer(fds[0]), pointer(buffer),
                        Argument::integer(count, ArgumentType::Int64),
                        Argument::integer(size, ArgumentType::Int64)});
                _exit(98);
            }
            require(::waitpid(child, &status, 0) == child && WIFEXITED(status) &&
                        WEXITSTATUS(status) == 97,
                    "checked read rejects buffer overflow and counts above SSIZE_MAX");
        }
        char remaining[4];
        require(::read(fds[0], remaining, sizeof(remaining)) == 4 &&
                    std::memcmp(remaining, "safe", 4) == 0,
                "checked read rejects before consuming pipe bytes");
        require(::close(fds[0]) == 0 && ::close(fds[1]) == 0, "checked read pipe cleanup");
        ::umask(mask);
        fs::current_path(previous);
        require(fs::is_empty(work), "guest I/O cleans up its created filesystem entries");
        fs::remove(work);
        size_t imports = 0;
        for (const auto& record : loader.bindings())
            if (!record.symbol.empty()) {
                require(record.binding.found, "all I/O fixture symbols bind");
                if (record.module == "libio.so" && record.binding.provider == "libc.so")
                    ++imports;
            }
        require(imports >= 30, "binding records cover actual file API imports");
        {
            const std::string expected =
                "00004000-00008000 ---p 00000000 00:00 0 [shiraz:host-reserved]\n";
            unsigned snapshots = 0;
            Loader maps_loader({fixtures}, file_personality([&](std::string& text) {
                                   ++snapshots;
                                   text = expected;
                                   return 0;
                               }));
            auto* maps_module = maps_loader.open("libio.so");
            auto call = [&](const char* name, std::vector<Argument> arguments) {
                auto symbol = maps_loader.symbol(maps_module, name);
                return int64_t(
                    invoke(reinterpret_cast<void*>(symbol.address), arguments).result_integers[0]);
            };
            const char* path = "/proc/self/maps";
            int mapfd = int(call("openat", {Argument::integer(-42), pointer(path),
                                            Argument::integer(bionic::kO_RDONLY)}));
            require(mapfd >= 0 && snapshots == 0,
                    "absolute proc open ignores dirfd and defers snapshot until read");
            char buffer[256]{};
            require(call("read", {Argument::integer(mapfd), pointer(buffer),
                                  Argument::integer(sizeof(buffer), ArgumentType::Int64)}) ==
                            int64_t(expected.size()) &&
                        std::string(buffer, expected.size()) == expected && snapshots == 1,
                    "guest ABI reads scoped maps descriptor");
            require(
                call("lseek", {Argument::integer(mapfd), Argument::integer(0, ArgumentType::Int64),
                               Argument::integer(SEEK_SET)}) == 0,
                "guest maps rewind");
            require(call("read", {Argument::integer(mapfd), pointer(buffer),
                                  Argument::integer(1, ArgumentType::Int64)}) == 1 &&
                        snapshots == 2,
                    "rewind refreshes scoped maps snapshot");
            require(call("close", {Argument::integer(mapfd)}) == 0, "guest maps close");
            require(int(call("open", {pointer(path), Argument::integer(bionic::kO_WRONLY)})) == -1,
                    "guest maps remains read only");
            std::cout << "M5_IO_MAPS_PASS scoped=1 openat=1 read=1 rewind=1 readonly=1\n";
        }
        std::cout << "M5_IO_PASS files=1 stat_guards=1 locks=1 nanoseconds=1 "
                     "directories=1 pipe_errno=1 ioctl_width=1 fortify=1 statvfs=1 "
                     "link_stubs_executed=0\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
