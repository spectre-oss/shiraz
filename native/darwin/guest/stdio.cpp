// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/stdio.hpp"
#include "shiraz/darwin/guest/fonts.hpp"
#include "shiraz/darwin/guest/fortify.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/format.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include <algorithm>
#include <array>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cwchar>
#include <fcntl.h>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <unistd.h>

namespace shiraz::guest {
namespace {
// android-14.0.0_r1 bits/struct_file.h defines the LP64 compatibility FILE as
// an opaque 152-byte, pointer-aligned object. No bionic fields are emulated.
struct alignas(void *) Token { unsigned char opaque[152]{}; };
static_assert(sizeof(Token) == 152 && alignof(Token) == 8);
constexpr size_t maximum_identities = 4096;
constexpr size_t maximum_transfer = size_t(256) << 20;
int fail(int error) { errno = error; return -1; }
bool path_ok(const char *path) {
  if (!path) { errno = EFAULT; return false; }
  if (strnlen(path, 4096) == 4096) { errno = ENAMETOOLONG; return false; }
  for (const char *root : {"/proc", "/sys", "/system", "/data", "/dev/ashmem"}) {
    size_t n = std::strlen(root);
    if (!std::strncmp(path, root, n) && (!path[n] || path[n] == '/')) {
      errno = ENOSYS; return false;
    }
  }
  return true;
}
struct Mode { int flags; const char *host; };
bool mode(const char *text, Mode &value) {
  if (!text) { errno = EFAULT; return false; }
  size_t size = strnlen(text, 8);
  if (!size || size == 8) { errno = EINVAL; return false; }
  bool plus = false, binary = false, exclusive = false, cloexec = false;
  for (size_t i = 1; i < size; ++i) {
    bool *seen = text[i] == '+' ? &plus : text[i] == 'b' ? &binary :
                 text[i] == 'x' ? &exclusive : text[i] == 'e' ? &cloexec : nullptr;
    if (!seen || *seen) { errno = EINVAL; return false; }
    *seen = true;
  }
  if (exclusive && text[0] != 'w') { errno = EINVAL; return false; }
  int access = plus ? O_RDWR : text[0] == 'r' ? O_RDONLY : O_WRONLY;
  if (text[0] == 'r') value = {access, plus ? "r+" : "r"};
  else if (text[0] == 'w') value = {access | O_CREAT | O_TRUNC, plus ? "w+" : "w"};
  else if (text[0] == 'a') value = {access | O_CREAT | O_APPEND, plus ? "a+" : "a"};
  else { errno = EINVAL; return false; }
  if (exclusive) value.flags |= O_EXCL;
  if (cloexec) value.flags |= O_CLOEXEC;
  return true;
}
struct Stream {
  FILE *host;
  bool owned, closed = false;
  std::mutex mutex;
  Stream(FILE *file, bool owner) : host(file), owned(owner) {}
  ~Stream() { if (owned && host && !closed) ::fclose(host); }
};
class StdioState {
  std::mutex mutex_;
  std::map<void *, std::shared_ptr<Stream>> streams_;
  // Tokens are never recycled: stale identities cannot alias a new FILE.
  std::vector<std::unique_ptr<Token>> identities_;
  std::shared_ptr<Stream> find(void *identity) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = streams_.find(identity);
    if (it == streams_.end()) { errno = EBADF; return {}; }
    return it->second;
  }
  template<class R, class F> R with(void *identity, R failure, F function) {
    auto stream = find(identity);
    if (!stream) return failure;
    std::lock_guard<std::mutex> lock(stream->mutex);
    if (stream->closed) { errno = EBADF; return failure; }
    return function(stream->host);
  }
  static bool transfer(const void *buffer, size_t size, size_t count) {
    if (!size || !count) return true;
    if (!buffer) { errno = EFAULT; return false; }
    if (count > maximum_transfer / size) { errno = EOVERFLOW; return false; }
    return true;
  }
public:
  std::array<Token, 3> standard;
  std::array<void *, 3> standard_pointers;
  StdioState() {
    FILE *files[] = {::stdin, ::stdout, ::stderr};
    for (size_t i = 0; i < 3; ++i) {
      standard_pointers[i] = &standard[i];
      streams_.emplace(&standard[i], std::make_shared<Stream>(files[i], false));
    }
  }
  void *open(const char *path, const char *text) {
    Mode parsed{};
    if (!mode(text, parsed)) return nullptr;
    // Reserve identity capacity before any truncation/creation side effect.
    std::lock_guard<std::mutex> lock(mutex_);
    if (identities_.size() == maximum_identities) { errno = EMFILE; return nullptr; }
    auto token = std::make_unique<Token>();
    auto stream = std::make_shared<Stream>(nullptr, true);
    bool font=false;int fd=openGuestFont(path,parsed.flags,font);
    if(!font){if(!path_ok(path))return nullptr;fd=::open(path,parsed.flags,0666);}
    if (fd == -1) return nullptr;
    FILE *file = ::fdopen(fd, parsed.host);
    if (!file) { int error = errno; ::close(fd); errno = error; return nullptr; }
    stream->host = file;
    void *identity = token.get();
    identities_.push_back(std::move(token));
    streams_.emplace(identity, std::move(stream));
    return identity;
  }
  void *attach(int fd,const char *text) {
    Mode parsed{}; if(!mode(text,parsed))return nullptr;
    std::lock_guard<std::mutex> lock(mutex_);
    if(identities_.size()==maximum_identities){errno=EMFILE;return nullptr;}
    auto token=std::make_unique<Token>();auto stream=std::make_shared<Stream>(nullptr,true);
    void *identity=token.get();identities_.push_back(std::move(token));
    streams_.emplace(identity,stream);
    FILE *file=::fdopen(fd,parsed.host);
    if(!file){streams_.erase(identity);return nullptr;}
    stream->host=file;return identity;
  }
  int close(void *identity) {
    auto stream = find(identity);
    if (!stream) return -1;
    std::lock_guard<std::mutex> lock(stream->mutex);
    if (stream->closed) return fail(EBADF);
    // Never close the host runtime's own standard streams on a guest request.
    if (!stream->owned) return fail(EOPNOTSUPP);
    stream->closed = true;
    int result = ::fclose(stream->host), error = errno;
    { std::lock_guard<std::mutex> registry(mutex_); streams_.erase(identity); }
    errno = error;
    return result;
  }
  int flush(void *identity) {
    if (identity) return with(identity, -1, [](FILE *f) { return ::fflush(f); });
    std::vector<std::shared_ptr<Stream>> streams;
    { std::lock_guard<std::mutex> lock(mutex_); for (auto &entry : streams_) streams.push_back(entry.second); }
    int result = 0, error = 0;
    for (auto &stream : streams) {
      std::lock_guard<std::mutex> lock(stream->mutex);
      if (!stream->closed && ::fflush(stream->host) == EOF) { result = EOF; error = errno; }
    }
    if (error) errno = error;
    return result;
  }
  size_t read(void *buffer, size_t size, size_t count, void *identity) {
    if (!transfer(buffer, size, count)) return 0;
    return with(identity, size_t(0), [&](FILE *f) { return ::fread(buffer, size, count, f); });
  }
  size_t write(const void *buffer, size_t size, size_t count, void *identity) {
    if (!transfer(buffer, size, count)) return 0;
    return with(identity, size_t(0), [&](FILE *f) { return ::fwrite(buffer, size, count, f); });
  }
  char *gets(char *buffer, int size, void *identity) {
    if (!buffer || size <= 0 || size_t(size) > maximum_transfer) { errno = !buffer ? EFAULT : EINVAL; return nullptr; }
    return with(identity, static_cast<char *>(nullptr), [&](FILE *f) { return ::fgets(buffer, size, f); });
  }
  int puts(const char *text, void *identity) {
    if (!text) return fail(EFAULT);
    if (strnlen(text, maximum_transfer) == maximum_transfer) return fail(EOVERFLOW);
    return with(identity, -1, [&](FILE *f) { return ::fputs(text, f); });
  }
  int get(void *identity) { return with(identity, -1, [](FILE *f) { return ::fgetc(f); }); }
  int put(int value, void *identity) { return with(identity, -1, [&](FILE *f) { return ::fputc(value, f); }); }
  int put_character(int value) { return put(value, &standard[1]); }
  int put_line(const char *text) {
    if (!text) return fail(EFAULT);
    if (strnlen(text, maximum_transfer) == maximum_transfer) return fail(EOVERFLOW);
    return with(&standard[1], -1, [&](FILE *f) {
      if (::fputs(text, f) == EOF || ::fputc('\n', f) == EOF) return EOF;
      return 0;
    });
  }
  int formatted(void *identity, const std::string &text) {
    return with(identity, -1, [&](FILE *file) {
      if (::fwrite(text.data(), 1, text.size(), file) != text.size()) {
        if (!errno) errno = EIO;
        return -1;
      }
      return int(text.size());
    });
  }
  int unget(int value, void *identity) { return with(identity, -1, [&](FILE *f) { return ::ungetc(value, f); }); }
  int error(void *identity) { return with(identity, 0, [](FILE *f) { return ::ferror(f); }); }
  int eof(void *identity) { return with(identity, 0, [](FILE *f) { return ::feof(f); }); }
  int descriptor(void *identity) { return with(identity, -1, [](FILE *f) { return ::fileno(f); }); }
  int seek(void *identity, int64_t offset, int origin) {
    // LP64 bionic SEEK_SET/CUR/END are 0/1/2, respectively.
    int native = origin == 0 ? SEEK_SET : origin == 1 ? SEEK_CUR : origin == 2 ? SEEK_END : -1;
    if (native == -1) return fail(EINVAL);
    return with(identity, -1, [&](FILE *f) { return ::fseeko(f, offset, native); });
  }
  int64_t tell(void *identity) { return with(identity, int64_t(-1), [](FILE *f) { return int64_t(::ftello(f)); }); }
  uint32_t get_wide(void *identity) {
    static_assert(sizeof(wint_t) == 4 && sizeof(wchar_t) == 4);
    return with(identity, UINT32_MAX, [](FILE *f) { return uint32_t(::fgetwc(f)); });
  }
  uint32_t put_wide(uint32_t value, void *identity) {
    return with(identity, UINT32_MAX, [&](FILE *f) { return uint32_t(::fputwc(wchar_t(value), f)); });
  }
  uint32_t unget_wide(uint32_t value, void *identity) {
    return with(identity, UINT32_MAX, [&](FILE *f) { return uint32_t(::ungetwc(wint_t(value), f)); });
  }
};
template<class R, class... Args>
ProviderExport method_export(const char *name, std::shared_ptr<StdioState> state,
                             R (StdioState::*method)(Args...)) {
  auto *code = closure([state, method](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    R value;
    int error = 0;
    try {
      value = std::apply([&](Args... values) { return ((*state).*method)(values...); }, args);
      error = errno;
    } catch (const std::bad_alloc &) {
      error = ENOMEM;
      if constexpr (std::is_pointer_v<R>) value = nullptr;
      else if constexpr (std::is_unsigned_v<R>)
        value = sizeof(R) == sizeof(size_t) ? R(0) : R(-1);
      else value = R(-1);
    }
    if (error) *guest_errno_location() = errno_to_guest(error);
    return_value(capture, value);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
          "stdio/opaque-file/errno-observed", false};
}
enum class FormatSink { Bounded, Unbounded, Allocated, File, Standard };
ProviderExport format_export(const char *name, std::shared_ptr<StdioState> state,
                             FormatSink sink, bool list, bool checked = false) {
  void *code = closure([state, sink, list, checked](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    HostErrno preserve;
    int result = -1;
    int failure = 0;
    char *buffer = nullptr;
    char **allocated = nullptr;
    size_t size = 0;
    size_t compiler_size = SIZE_MAX;
    void *file = nullptr;
    try {
      if (sink == FormatSink::Bounded || sink == FormatSink::Unbounded) {
        buffer = cursor.read<char *>();
        size = sink == FormatSink::Bounded ? cursor.read<size_t>() : SIZE_MAX;
        if (!buffer && size) throw GuestFormatError("null formatted output buffer", EFAULT);
      } else if (sink == FormatSink::Allocated) {
        allocated = cursor.read<char **>();
        if (!allocated) throw GuestFormatError("null allocated output pointer", EFAULT);
        *allocated = nullptr;
      } else if (sink == FormatSink::File) file = cursor.read<void *>();
      else file = &state->standard[1];
      if (checked) {
        (void)cursor.read<int>(); // Bionic fortify flags are reserved/ignored.
        compiler_size = cursor.read<size_t>();
        if (sink == FormatSink::Bounded && size > compiler_size)
          fortify_failure("vsnprintf");
      }
      const char *format = cursor.read<const char *>();
      std::string text;
      if (list) {
        const auto *arguments = cursor.read<const GuestVaList *>();
        if (!arguments) throw GuestFormatError("null formatted va_list", EFAULT);
        VaListCursor reader(*arguments);
        text = format_guest(format, [&](ArgumentType type) { return reader.next(type); });
      } else {
        text = format_guest(format, [&](ArgumentType type) {
          return cursor.next(type == ArgumentType::Int32 ? 4 : 8,
                             type == ArgumentType::Double);
        });
      }
      if (checked && sink == FormatSink::Unbounded && text.size() >= compiler_size)
        fortify_failure("vsprintf");
      if (sink == FormatSink::File || sink == FormatSink::Standard) {
        result = state->formatted(file, text);
      } else if (sink == FormatSink::Allocated) {
        auto *output = static_cast<char *>(std::malloc(text.size() + 1));
        if (!output) throw GuestFormatError("allocated formatted output failed", ENOMEM);
        std::memcpy(output, text.data(), text.size());
        output[text.size()] = 0;
        *allocated = output;
        result = int(text.size());
      } else {
        if (size) {
          size_t copied = std::min(text.size(), size - 1);
          if (copied) std::memcpy(buffer, text.data(), copied);
          buffer[copied] = 0;
        }
        result = int(text.size());
      }
      if (result < 0) failure = errno ? errno : EIO;
    } catch (const GuestFormatError &error) { failure = error.error; }
      catch (const std::invalid_argument &) { failure = EINVAL; }
      catch (const std::bad_alloc &) { failure = ENOMEM; }
    if (result < 0) *guest_errno_location() = errno_to_guest(failure ? failure : EIO);
    return_value(capture, result);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
          "stdio/bounded-aapcs-format", false};
}
} // namespace
std::vector<ProviderExport> stdio_exports() {
  auto state = std::make_shared<StdioState>();
  std::vector<ProviderExport> result;
  auto add = [&](const char *name, auto method) { result.push_back(method_export(name, state, method)); };
  add("fopen", &StdioState::open);
  add("fdopen", &StdioState::attach);
  add("fopen64", &StdioState::open);
  add("fclose", &StdioState::close);
  add("fflush", &StdioState::flush);
  add("fread", &StdioState::read);
  add("fwrite", &StdioState::write);
  add("fgets", &StdioState::gets);
  add("fputs", &StdioState::puts);
  add("fgetc", &StdioState::get);
  add("getc", &StdioState::get);
  add("fputc", &StdioState::put);
  add("putc", &StdioState::put);
  add("putchar", &StdioState::put_character);
  add("puts", &StdioState::put_line);
  add("ungetc", &StdioState::unget);
  add("ferror", &StdioState::error);
  add("feof", &StdioState::eof);
  add("fileno", &StdioState::descriptor);
  add("fseek", &StdioState::seek);
  add("fseeko", &StdioState::seek);
  add("fseeko64", &StdioState::seek);
  add("ftell", &StdioState::tell);
  add("ftello", &StdioState::tell);
  add("ftello64", &StdioState::tell);
  add("fgetwc", &StdioState::get_wide);
  add("getwc", &StdioState::get_wide);
  add("fputwc", &StdioState::put_wide);
  add("ungetwc", &StdioState::unget_wide);
  for (bool list : {false, true}) {
    result.push_back(format_export(list ? "vsnprintf" : "snprintf", state, FormatSink::Bounded, list));
    result.push_back(format_export(list ? "__vsnprintf_chk" : "__snprintf_chk", state, FormatSink::Bounded, list, true));
    result.push_back(format_export(list ? "__vsprintf_chk" : "__sprintf_chk", state, FormatSink::Unbounded, list, true));
    result.push_back(format_export(list ? "vsprintf" : "sprintf", state, FormatSink::Unbounded, list));
    result.push_back(format_export(list ? "vasprintf" : "asprintf", state, FormatSink::Allocated, list));
    result.push_back(format_export(list ? "vfprintf" : "fprintf", state, FormatSink::File, list));
    result.push_back(format_export(list ? "vprintf" : "printf", state, FormatSink::Standard, list));
  }
  result.push_back({"__sF", "LIBC", reinterpret_cast<uintptr_t>(state->standard.data()),
                    SymbolKind::Object, "stdio/bionic-lp64-opaque-file-array", false});
  for (size_t i = 0; i < 3; ++i)
    result.push_back({i == 0 ? "stdin" : i == 1 ? "stdout" : "stderr", "LIBC",
                      reinterpret_cast<uintptr_t>(&state->standard_pointers[i]),
                      SymbolKind::Object, "stdio/opaque-file-pointer", false});
  return result;
}
Providers stdio_personality() {
  auto base = file_personality();
  Providers registry;
  for (const auto &entry : base.modules()) {
    Provider provider = entry.second;
    if (entry.first == "libc.so") {
      auto exports = stdio_exports();
      provider.exports.insert(provider.exports.end(), std::make_move_iterator(exports.begin()),
                              std::make_move_iterator(exports.end()));
    }
    registry.add(std::move(provider));
  }
  return registry;
}
} // namespace shiraz::guest
