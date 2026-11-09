// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "guest_io.hpp"
#include <filesystem>
#include <iostream>
#include <time.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: sync-probe fixture-directory");
    Loader *current = nullptr;
    Loader loader({std::filesystem::canonical(argv[1])}, native_personality(
        [&](const void *code) { return current && current->contains_code(code); }));
    current = &loader;
    auto *module = loader.open("libsync.so");
    require(loader.image_count() == 1, "synchronization uses actual provider objects");
    errno = ELOOP;
    int result = int(invoke(reinterpret_cast<void *>(loader.symbol(module, "run_sync").address), {})
                         .result_integers[0]);
    if (result) throw std::runtime_error("guest synchronization failed at line " + std::to_string(result));
    require(errno == ELOOP, "host errno preserved");
    timespec before, after;
    int64_t stamp[2];
    require(clock_gettime(CLOCK_UPTIME_RAW, &before) == 0, "native awake uptime");
    auto clock = loader.symbol(module, "clock_gettime");
    result = int(invoke(reinterpret_cast<void *>(clock.address), {
        Argument::integer(1),
        Argument::integer(reinterpret_cast<uintptr_t>(stamp), ArgumentType::Int64)})
        .result_integers[0]);
    require(clock_gettime(CLOCK_UPTIME_RAW, &after) == 0 && !result,
            "translated monotonic clock sample");
    auto nanos = [](int64_t seconds, int64_t subsecond) { return seconds * 1000000000LL + subsecond; };
    require(nanos(stamp[0], stamp[1]) >= nanos(before.tv_sec, before.tv_nsec) &&
                nanos(stamp[0], stamp[1]) <= nanos(after.tv_sec, after.tv_nsec),
            "guest monotonic uses native awake uptime rather than Darwin boot-time clock");
    require(clock_gettime(CLOCK_MONOTONIC,&before)==0,"native suspend-inclusive clock");
    result=int(invoke(reinterpret_cast<void *>(clock.address),{Argument::integer(bionic::kCLOCK_BOOTTIME),Argument::integer(reinterpret_cast<uintptr_t>(stamp),ArgumentType::Int64)}).result_integers[0]);
    require(clock_gettime(CLOCK_MONOTONIC,&after)==0&&!result,"translated BOOTTIME clock sample");
    require(nanos(stamp[0],stamp[1])>=nanos(before.tv_sec,before.tv_nsec)&&nanos(stamp[0],stamp[1])<=nanos(after.tv_sec,after.tv_nsec),"guest BOOTTIME shares suspend-inclusive host clock epoch");
    std::cout << "M5_SYNC_PASS threads=4 counter=4000 once=1 static=1 recursive=1 "
                 "errorcheck=1 condition=signal,broadcast realtime=1 monotonic=1 "
                 "boottime=1 boottime_cond_rejected=1 timeout=1 reacquire=1 guards=1 busy_destroy=1\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
