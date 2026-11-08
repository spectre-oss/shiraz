// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/log.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include <cstdio>
#include <stdexcept>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main(int argc, char **argv) {
  int previous = -1; FILE *capture = nullptr;
  try {
    require(argc == 2, "usage: log-probe fixture-directory");
    auto providers = core_personality(); providers.add(log_provider());
    Loader loader({argv[1]}, std::move(providers)); auto *module = loader.open("liblog-fixture.so");
    capture = tmpfile(); require(capture, "log capture file");
    previous = dup(2); require(previous >= 0 && dup2(fileno(capture), 2) == 2, "log sink capture");
    errno = EBUSY; *guest_errno_location() = 73;
    auto frame = invoke(reinterpret_cast<void *>(loader.symbol(module, "run_log").address), {});
    require(frame.result_integers[0] == 0, "guest ellipsis/va_list logging");
    require(errno == EBUSY && *guest_errno_location() == 73, "logging preserves both errnos");
    fflush(stderr); require(dup2(previous, 2) == 2, "restore log sink"); close(previous); previous = -1;
    rewind(capture); char bytes[4096]{}; size_t count = fread(bytes, 1, sizeof(bytes) - 1, capture);
    std::string text(bytes, count); fclose(capture); capture = nullptr;
    require(text.find("SHIRAZ_ANDROID_LOG 4/Guest: -7 9 1234567890123 2.50 hello    3.2 1 2 3 4 5 6 7 8\n") != std::string::npos, "ellipsis registers and stack formatting");
    require(text.find("SHIRAZ_ANDROID_LOG 4/GuestV: -5 1.5 list 1 2 3 4 5 6 7 8 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0\n") != std::string::npos, "va_list integer/vector/stack formatting");
    unsigned rejected = 0;
    for (const char *format : {"%n", "%2$d", "%Lf", "%999999999d", "%", "%lls"}) {
      try { (void)format_guest(format, [](ArgumentType) { return uint64_t(0); }); }
      catch (const std::invalid_argument &) { ++rejected; }
    }
    require(rejected == 6, "unsupported formatting explicitly rejected");
    std::printf("M5_LOG_PASS guest_ellipsis=1 guest_va_list=1 register_stack=1 stars=1 sink=1 errno_preserved=1 format_rejections=6\n");
  } catch (const std::exception &error) {
    if (previous >= 0) { dup2(previous, 2); close(previous); }
    if (capture) fclose(capture);
    std::fprintf(stderr, "log-probe: %s\n", error.what()); return 1;
  }
}
