// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/zlib.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <zlib.h>

namespace shiraz::guest {
namespace {
// ABI facts from platform/external/zlib android-14.0.0_r1:
// zlib.h SHA256 40eb5ee45d48eb9e2a8d40925b998b82e5773078aab91dc42858c78f4fa2f3ee
// zconf.h SHA256 dec56f8e7c1813283e57c0c68773844690e72270db1908d0d840f5a9e5e160e1
// https://android.googlesource.com/platform/external/zlib/+/refs/tags/android-14.0.0_r1/zlib.h
// uInt is unsigned int; uLong is unsigned long (LP64). No third-party source
// is embedded here. Guest function pointers never become host callbacks.
struct GuestStream {
  void *input;
  uint32_t input_available, input_padding;
  uint64_t input_total;
  void *output;
  uint32_t output_available, output_padding;
  uint64_t output_total;
  const char *message;
  void *state, *allocate, *release, *opaque;
  int32_t data_type;
  uint32_t data_padding;
  uint64_t checksum, reserved;
};
static_assert(sizeof(GuestStream) == 112 && alignof(GuestStream) == 8);
static_assert(offsetof(GuestStream, input_total) == 16 && offsetof(GuestStream, output) == 24 &&
              offsetof(GuestStream, output_total) == 40 && offsetof(GuestStream, message) == 48 &&
              offsetof(GuestStream, state) == 56 && offsetof(GuestStream, allocate) == 64 &&
              offsetof(GuestStream, release) == 72 && offsetof(GuestStream, opaque) == 80 &&
              offsetof(GuestStream, data_type) == 88 && offsetof(GuestStream, checksum) == 96 &&
              offsetof(GuestStream, reserved) == 104);
static_assert(sizeof(uInt) == 4 && sizeof(uLong) == 8 && sizeof(z_stream) == 112);
constexpr size_t maximum_streams = 4096;
constexpr size_t maximum_identities = size_t(1) << 20;
constexpr size_t maximum_allocation = size_t(64) << 20;
constexpr size_t maximum_allocated = size_t(256) << 20;
constexpr size_t maximum_total_allocated = size_t(1) << 30;
constexpr uint32_t maximum_transfer = uint32_t(256) << 20;
Argument pointer(const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); }
[[noreturn]] void callback_failure(const char *text) {
  std::fprintf(stderr, "SHIRAZ_GUEST_ZLIB_CALLBACK_FAILURE %s\n", text);
  std::abort();
}
struct Token { uint64_t marker = 0x53485a4c49425354ull; };
struct Budget {
  std::atomic<size_t> allocated{0};
  bool reserve(size_t bytes) {
    size_t current = allocated.load(std::memory_order_relaxed);
    for (;;) {
      if (current > maximum_total_allocated || bytes > maximum_total_allocated - current) return false;
      if (allocated.compare_exchange_weak(current, current + bytes,
                                           std::memory_order_relaxed)) return true;
    }
  }
  void release(size_t bytes) {
    if (allocated.fetch_sub(bytes, std::memory_order_relaxed) < bytes)
      callback_failure("aggregate allocation budget underflow");
  }
};
struct Stream {
  z_stream host{};
  void *guest_allocate, *guest_release, *guest_opaque;
  void *token;
  bool deflator, busy = false, ended = false;
  size_t allocated = 0;
  std::map<void *, size_t> allocations;
  std::shared_ptr<Budget> budget;
  std::recursive_mutex mutex;
  Stream(const GuestStream &guest, void *identity, bool compress, std::shared_ptr<Budget> shared_budget)
      : guest_allocate(guest.allocate), guest_release(guest.release),
        guest_opaque(guest.opaque), token(identity), deflator(compress), budget(std::move(shared_budget)) {}
  // Deliberately no automatic deflateEnd/inflateEnd here. Guest callbacks may
  // point into images already unmapped by Loader destruction. A live stream
  // abandoned without End retains its allocations and shared budget charge;
  // process-lifetime provider closures retain the record. Loader teardown must
  // eventually gain an explicit pre-unmap drain to reclaim such streams safely.
  static void *allocate(void *context, unsigned items, unsigned size) noexcept {
    auto &stream = *static_cast<Stream *>(context);
    size_t bytes = size_t(items) * size;
    if (!items || !size || bytes / size != items || bytes > maximum_allocation ||
        bytes > maximum_allocated - stream.allocated || stream.allocations.size() >= 4096) return nullptr;
    if (!stream.budget->reserve(bytes)) return nullptr;
    void *result = nullptr;
    try {
      if (stream.guest_allocate) {
        prepare_guest_tls();
        result = reinterpret_cast<void *>(invoke(stream.guest_allocate,
            {pointer(stream.guest_opaque), Argument::integer(items), Argument::integer(size)}).result_integers[0]);
      } else result = std::calloc(items, size);
      if (!result) { stream.budget->release(bytes); return nullptr; }
      if (stream.allocations.count(result)) callback_failure("allocator returned a live allocation twice");
      try { stream.allocations.emplace(result, bytes); }
      catch (const std::bad_alloc &) {
        if (stream.guest_release) {
          prepare_guest_tls();
          invoke(stream.guest_release, {pointer(stream.guest_opaque), pointer(result)});
        } else std::free(result);
        stream.budget->release(bytes);
        return nullptr;
      }
      stream.allocated += bytes;
      return result;
    } catch (const std::exception &error) { callback_failure(error.what()); }
    catch (...) { callback_failure("non-standard allocator exception"); }
  }
  static void release(void *context, void *address) noexcept {
    if (!address) return;
    auto &stream = *static_cast<Stream *>(context);
    auto found = stream.allocations.find(address);
    if (found == stream.allocations.end()) callback_failure("free of an unknown allocation");
    size_t bytes = found->second;
    stream.allocated -= bytes;
    stream.allocations.erase(found);
    try {
      if (stream.guest_release) {
        prepare_guest_tls();
        invoke(stream.guest_release, {pointer(stream.guest_opaque), pointer(address)});
      } else std::free(address);
      stream.budget->release(bytes);
    } catch (const std::exception &error) { callback_failure(error.what()); }
    catch (...) { callback_failure("non-standard deallocator exception"); }
  }
};
class ZlibState {
  CodeValidator validate_;
  std::recursive_mutex mutex_;
  std::map<GuestStream *, std::shared_ptr<Stream>> streams_;
  std::vector<std::unique_ptr<Token>> tokens_;
  std::shared_ptr<Budget> budget_ = std::make_shared<Budget>();
  static void outputs(GuestStream &guest, const Stream &stream) {
    guest.input = const_cast<Bytef *>(stream.host.next_in);
    guest.input_available = stream.host.avail_in;
    guest.input_total = stream.host.total_in;
    guest.output = stream.host.next_out;
    guest.output_available = stream.host.avail_out;
    guest.output_total = stream.host.total_out;
    guest.message = stream.host.msg;
    guest.state = stream.ended ? nullptr : stream.token;
    guest.data_type = stream.host.data_type;
    guest.checksum = stream.host.adler;
    guest.reserved = stream.host.reserved;
  }
  static bool buffers(const GuestStream &guest) {
    return (!guest.input_available || guest.input) && (!guest.output_available || guest.output) &&
           guest.input_available <= maximum_transfer && guest.output_available <= maximum_transfer;
  }
  template<class F> int operation(GuestStream *guest, bool deflator, F function, bool ending = false) {
    if (!guest) return Z_STREAM_ERROR;
    std::shared_ptr<Stream> stream;
    {
      std::lock_guard<std::recursive_mutex> lock(mutex_);
      auto found = streams_.find(guest);
      if (found == streams_.end()) return Z_STREAM_ERROR;
      stream = found->second;
    }
    std::lock_guard<std::recursive_mutex> lock(stream->mutex);
    if (stream->ended || stream->busy || stream->deflator != deflator ||
        guest->state != stream->token || guest->allocate != stream->guest_allocate ||
        guest->release != stream->guest_release || guest->opaque != stream->guest_opaque ||
        (!ending && !buffers(*guest))) return Z_STREAM_ERROR;
    struct Busy { bool &value; Busy(bool &v) : value(v) { value = true; } ~Busy() { value = false; } } busy(stream->busy);
    stream->host.next_in = static_cast<Bytef *>(guest->input);
    stream->host.avail_in = guest->input_available;
    stream->host.next_out = static_cast<Bytef *>(guest->output);
    stream->host.avail_out = guest->output_available;
    int result = function(stream->host);
    if (ending && !stream->host.state) {
      stream->ended = true;
      std::lock_guard<std::recursive_mutex> registry(mutex_);
      streams_.erase(guest);
    }
    outputs(*guest, *stream);
    return result;
  }
  int initialize(GuestStream *guest, const char *version, int size, bool deflator,
                 int level, int method, int window, int memory, int strategy) {
    // zlib's documented compatibility rule is equal major version plus equal
    // stream size, rather than exact patch version. Host headers/runtime are
    // checked separately and the native call uses its own version string.
    if (!version || size != int(sizeof(GuestStream)) || strnlen(version, 32) == 32 ||
        version[0] != '1' || version[1] != '.' || ::zlibVersion()[0] != '1') return Z_VERSION_ERROR;
    if (!guest || guest->state || !buffers(*guest)) return Z_STREAM_ERROR;
    if (bool(guest->allocate) != bool(guest->release) ||
        (guest->allocate && (!validate_(guest->allocate) || !validate_(guest->release)))) return Z_STREAM_ERROR;
    std::unique_lock<std::recursive_mutex> registry(mutex_);
    if (streams_.count(guest)) return Z_STREAM_ERROR;
    if (streams_.size() >= maximum_streams || tokens_.size() >= maximum_identities) return Z_MEM_ERROR;
    auto token = std::make_unique<Token>();
    auto stream = std::make_shared<Stream>(*guest, token.get(), deflator, budget_);
    std::lock_guard<std::recursive_mutex> initializing(stream->mutex);
    stream->host.zalloc = &Stream::allocate;
    stream->host.zfree = &Stream::release;
    stream->host.opaque = stream.get();
    stream->host.next_in = static_cast<Bytef *>(guest->input);
    stream->host.avail_in = guest->input_available;
    stream->host.next_out = static_cast<Bytef *>(guest->output);
    stream->host.avail_out = guest->output_available;
    // Publish a busy record before zlib calls guest allocation functions.
    tokens_.push_back(std::move(token));
    streams_.emplace(guest, stream);
    stream->busy = true;
    registry.unlock();
    int result = deflator ? ::deflateInit2_(&stream->host, level, method, window, memory,
                                           strategy, ZLIB_VERSION, sizeof(z_stream))
                          : ::inflateInit2_(&stream->host, window, ZLIB_VERSION, sizeof(z_stream));
    stream->busy = false;
    if (result != Z_OK) {
      { std::lock_guard<std::recursive_mutex> lock(mutex_); streams_.erase(guest); }
      // Failed initialization must have returned any allocations it made.
      if (!stream->allocations.empty()) callback_failure("failed initializer retained allocations");
      stream->ended = true;
    }
    outputs(*guest, *stream);
    return result;
  }
public:
  explicit ZlibState(CodeValidator validate) : validate_(std::move(validate)) {
    if (!validate_) throw ElfError("zlib requires a guest code validator");
    if (::zlibVersion()[0] != ZLIB_VERSION[0]) throw ElfError("incompatible host zlib major version");
  }
  int deflate_init(GuestStream *guest, int level, int method, int window, int memory,
                   int strategy, const char *version, int size) {
    return initialize(guest, version, size, true, level, method, window, memory, strategy);
  }
  int inflate_init(GuestStream *guest, int window, const char *version, int size) {
    return initialize(guest, version, size, false, 0, 0, window, 0, 0);
  }
  int deflate(GuestStream *guest, int flush) { return operation(guest, true, [&](auto &s) { return ::deflate(&s, flush); }); }
  int inflate(GuestStream *guest, int flush) { return operation(guest, false, [&](auto &s) { return ::inflate(&s, flush); }); }
  int deflate_end(GuestStream *guest) { return operation(guest, true, [](auto &s) { return ::deflateEnd(&s); }, true); }
  int inflate_end(GuestStream *guest) { return operation(guest, false, [](auto &s) { return ::inflateEnd(&s); }, true); }
  int deflate_reset(GuestStream *guest) { return operation(guest, true, [](auto &s) { return ::deflateReset(&s); }); }
  int inflate_reset(GuestStream *guest) { return operation(guest, false, [](auto &s) { return ::inflateReset(&s); }); }
  int inflate_reset2(GuestStream *guest, int window) { return operation(guest, false, [&](auto &s) { return ::inflateReset2(&s, window); }); }
};
template<class R, class... Args>
ProviderExport method_export(const char *name, std::shared_ptr<ZlibState> state,
                             R (ZlibState::*method)(Args...)) {
  auto *code = closure([state, method](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    R result;
    try { result = std::apply([&](Args... values) { return ((*state).*method)(values...); }, args); }
    catch (const std::bad_alloc &) { result = Z_MEM_ERROR; }
    return_value(capture, result);
  });
  return {name, "", reinterpret_cast<uintptr_t>(code), SymbolKind::Function,
          "zlib/lp64-stream/guest-callbacks", false};
}
uLong checksum(uLong value, const Bytef *bytes, uInt size, bool crc) {
  // Null input has the zlib-defined initialization meaning, regardless of len.
  if (bytes && size > maximum_transfer) throw ElfError("zlib checksum transfer limit");
  return crc ? ::crc32(value, bytes, size) : ::adler32(value, bytes, size);
}
} // namespace
Provider zlib_provider(CodeValidator validate) {
  auto state = std::make_shared<ZlibState>(std::move(validate));
  Provider provider{"libz.so", {}, {}, false};
  auto add = [&](const char *name, auto method) { provider.exports.push_back(method_export(name, state, method)); };
  add("deflateInit2_", &ZlibState::deflate_init);
  add("inflateInit2_", &ZlibState::inflate_init);
  add("deflate", &ZlibState::deflate);
  add("inflate", &ZlibState::inflate);
  add("deflateEnd", &ZlibState::deflate_end);
  add("inflateEnd", &ZlibState::inflate_end);
  add("deflateReset", &ZlibState::deflate_reset);
  add("inflateReset", &ZlibState::inflate_reset);
  add("inflateReset2", &ZlibState::inflate_reset2);
  provider.exports.push_back(fixed_export("adler32", "", +[](uLong v, const Bytef *p, uInt n) { return checksum(v, p, n, false); }));
  provider.exports.push_back(fixed_export("crc32", "", +[](uLong v, const Bytef *p, uInt n) { return checksum(v, p, n, true); }));
  provider.exports.push_back(fixed_export("zlibVersion", "", &::zlibVersion));
  struct Gzip {
    struct Identity{};std::mutex mutex;std::vector<std::unique_ptr<Identity>> identities;
    std::map<void *,gzFile> files;
    ~Gzip(){for(auto &entry:files)::gzclose(entry.second);}
  };
  auto gzip=std::make_shared<Gzip>();
  for(int operation:{0,1,2}) {
    auto entry=closure([gzip,operation](Capture &capture){
      CallCursor cursor(capture,Convention::Aapcs64);HostErrno preserve;
      std::lock_guard<std::mutex> lock(gzip->mutex);
      try {
        if(!operation){const char *path=cursor.read<const char *>(),*mode=cursor.read<const char *>();
          if(!path||!mode||strnlen(path,4096)==4096||strnlen(mode,32)==32||gzip->identities.size()>=4096){*guest_errno_location()=errno_to_guest(EINVAL);return_value(capture,static_cast<void *>(nullptr));return;}
          auto identity=std::make_unique<Gzip::Identity>();void *key=identity.get();
          gzip->identities.push_back(std::move(identity));gzip->files.emplace(key,nullptr);
          gzFile file=::gzopen(path,mode);if(!file){gzip->files.erase(key);*guest_errno_location()=errno_to_guest(errno?errno:EIO);return_value(capture,static_cast<void *>(nullptr));return;}
          gzip->files[key]=file;return_value(capture,key);
        } else {void *identity=cursor.read<void *>();auto found=gzip->files.find(identity);
          if(found==gzip->files.end()){*guest_errno_location()=errno_to_guest(EBADF);return_value(capture,-1);return;}
          int value;
          if(operation==1){void *output=cursor.read<void *>();unsigned size=cursor.read<unsigned>();
            if((!output&&size)||size>maximum_transfer){*guest_errno_location()=errno_to_guest(EINVAL);return_value(capture,-1);return;}
            value=::gzread(found->second,output,size);
          }else{value=::gzclose(found->second);gzip->files.erase(found);}
          if(value<0&&errno)*guest_errno_location()=errno_to_guest(errno);return_value(capture,value);
        }
      }catch(const std::bad_alloc &){*guest_errno_location()=errno_to_guest(ENOMEM);if(!operation)return_value(capture,static_cast<void *>(nullptr));else return_value(capture,-1);}
    });
    provider.exports.push_back({operation==0?"gzopen":operation==1?"gzread":"gzclose","",uintptr_t(entry),SymbolKind::Function,"zlib/owned-gzip-file",false});
  }
  return provider;
}
} // namespace shiraz::guest
