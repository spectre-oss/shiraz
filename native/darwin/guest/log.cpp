// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/log.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include <cstdio>
#include <stdexcept>
namespace shiraz::guest {
namespace {
int write_log(int priority, const char *tag, std::string message) {
  if (priority < 2 || priority > 7 || !tag || strnlen(tag, 1024) == 1024)
    return -errno_to_guest(EINVAL);
  // Android14 liblog formats into a 1024-byte buffer and returns 1 after
  // delivery. Shiraz delivers a bounded app log line to its stderr sink.
  if (message.size() > 1023) message.resize(1023);
  std::string line = "SHIRAZ_ANDROID_LOG " + std::to_string(priority) + "/" + tag + ": " + message + "\n";
  ::flockfile(stderr);
  size_t written = ::fwrite(line.data(), 1, line.size(), stderr);
  int flushed = ::fflush(stderr);
  ::funlockfile(stderr);
  return written == line.size() && !flushed ? 1 : -errno_to_guest(EIO);
}
}
Provider log_provider() {
  Provider provider{"liblog.so", {"LIBLOG"}, {}, false};
  for (bool list : {false, true}) {
    void *entry = closure([list](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      int priority = cursor.read<int>();
      const char *tag = cursor.read<const char *>();
      const char *format = cursor.read<const char *>();
      HostErrno preserve;
      int result;
      try {
        std::string message;
        if (list) {
          const GuestVaList *arguments = cursor.read<const GuestVaList *>();
          if (!arguments) throw std::invalid_argument("null log va_list");
          VaListCursor reader(*arguments);
          message = format_guest(format, [&](ArgumentType type) { return reader.next(type); });
        } else {
          message = format_guest(format, [&](ArgumentType type) {
            return cursor.next(type == ArgumentType::Int32 ? 4 : 8, type == ArgumentType::Double);
          });
        }
        result = write_log(priority, tag, std::move(message));
      } catch (const std::invalid_argument &) { result = -errno_to_guest(EINVAL); }
        catch (const std::bad_alloc &) { result = -errno_to_guest(ENOMEM); }
      return_value(capture, result);
    });
    provider.exports.push_back({list ? "__android_log_vprint" : "__android_log_print", "LIBLOG",
        reinterpret_cast<uintptr_t>(entry), SymbolKind::Function, "log/bounded-aapcs-format", false});
  }
  return provider;
}
}
