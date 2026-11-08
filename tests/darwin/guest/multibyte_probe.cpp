// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/locale.hpp"
#include "shiraz/darwin/guest/multibyte.hpp"
#include <condition_variable>
#include <chrono>
#include <cstring>
#include <iostream>
#include <iterator>
#include <mutex>
#include <thread>

using namespace shiraz::guest;
namespace {
void require(bool condition, const char *message) {
  if (!condition) throw std::runtime_error(message);
}
} // namespace
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: multibyte-probe fixture-directory");
    Provider libc{"libc.so", {"LIBC"}, multibyte_exports(), false};
    auto locales = locale_exports();
    libc.exports.insert(libc.exports.end(), std::make_move_iterator(locales.begin()), std::make_move_iterator(locales.end()));
    libc.exports.push_back(fixed_export("__errno", "LIBC", &guest_errno_location));
    libc.exports.push_back(fixed_export("memcpy", "LIBC", &std::memcpy));
    libc.exports.push_back(fixed_export("memset", "LIBC", &std::memset));
    libc.exports.push_back(fixed_export("memcmp", "LIBC", &std::memcmp));
    Providers providers; providers.add(std::move(libc));
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libmultibyte.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return int(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0]);
    };
    errno = ELOOP;
    for (const char *test : {"multibyte_decode", "multibyte_strings", "multibyte_locale"}) {
      int value = call(test);
      if (value != 73) throw std::runtime_error(std::string(test) + " case=" + std::to_string(value));
      require(errno == ELOOP, "host errno preserved");
    }
    std::mutex mutex;
    std::condition_variable condition;
    unsigned arrived = 0;
    bool failed = false;
    std::thread workers[4];
    for (auto &worker : workers) worker = std::thread([&] {
      errno = ELOOP;
      bool good = call("multibyte_thread_stage", {Argument::integer(0)}) == 73;
      {
        std::unique_lock<std::mutex> lock(mutex);
        if (++arrived == 4) condition.notify_all();
        else if (!condition.wait_for(lock, std::chrono::seconds(5), [&] { return arrived == 4; })) good = false;
      }
      good = (call("multibyte_thread_stage", {Argument::integer(1)}) == 73) && good && errno == ELOOP;
      std::lock_guard<std::mutex> lock(mutex);
      failed |= !good;
    });
    for (auto &worker : workers) worker.join();
    require(!failed, "per-thread default decoder state");
    std::cout << "M5_MULTIBYTE_PASS utf8=1 state_layout=1 partial_reset=1 source_pointers=1 null_sizing=1 c_locale_quirks=1 threads=4 host_errno_preserved=1\n";
    return 0;
  } catch (const std::exception &error) {
    std::cerr << "multibyte-probe: " << error.what() << '\n';
    return 1;
  }
}
