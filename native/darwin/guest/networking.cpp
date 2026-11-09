// SPDX-License-Identifier: GPL-3.0-only
#define __APPLE_USE_RFC_3542 1
#include "shiraz/darwin/guest/networking.hpp"
#include "shiraz/darwin/guest/events.hpp"
#include "guest_io.hpp"
#include <algorithm>
#include <iterator>
#include <array>
#include <climits>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <net/if.h>

namespace shiraz::guest {
namespace {
namespace b = bionic;
constexpr size_t address_limit = 128, control_limit = 65536, vector_limit = 1024;
constexpr size_t transfer_limit = size_t(64) << 20;
template<class T, size_t Offset, size_t Width> T get(const void *bytes) {
  static_assert(sizeof(T) == Width); T value;
  std::memcpy(&value, static_cast<const uint8_t *>(bytes) + Offset, Width); return value;
}
template<class T, size_t Offset, size_t Width> void put(void *bytes, T value) {
  static_assert(sizeof(T) == Width);
  std::memcpy(static_cast<uint8_t *>(bytes) + Offset, &value, Width);
}
#define GET(K,F,T,P) get<T,b::k##K##_##F,b::k##K##_##F##Width>(P)
#define PUT(K,F,T,P,V) put<T,b::k##K##_##F,b::k##K##_##F##Width>(P,static_cast<T>(V))
int fail(int error) { errno = error; return -1; }
struct Flag { int guest, native; };
bool flags(int value, const std::vector<Flag> &mapping, int &result, int error = EINVAL) {
  result = 0;
  for (auto pair : mapping) if (value & pair.guest) { result |= pair.native; value &= ~pair.guest; }
  if (value) { errno = error; return false; }
  return true;
}
int reverse_flags(int value, const std::vector<Flag> &mapping) {
  int result = 0;
  for (auto pair : mapping) if (value & pair.native) result |= pair.guest;
  return result;
}
const std::vector<Flag> ai_flags{{int(b::kAI_PASSIVE), AI_PASSIVE}, {int(b::kAI_CANONNAME), AI_CANONNAME},
  {int(b::kAI_NUMERICHOST), AI_NUMERICHOST}, {int(b::kAI_NUMERICSERV), AI_NUMERICSERV},
  {int(b::kAI_ADDRCONFIG), AI_ADDRCONFIG}, {int(b::kAI_V4MAPPED), AI_V4MAPPED},
  {int(b::kAI_ALL), AI_ALL}, {int(b::kAI_V4MAPPED_CFG), AI_V4MAPPED_CFG}};
const std::vector<Flag> ni_flags{{int(b::kNI_NUMERICHOST), NI_NUMERICHOST}, {int(b::kNI_NUMERICSERV), NI_NUMERICSERV},
  {int(b::kNI_NOFQDN), NI_NOFQDN}, {int(b::kNI_NAMEREQD), NI_NAMEREQD}, {int(b::kNI_DGRAM), NI_DGRAM}};
const std::vector<Flag> message_flags{{int(b::kMSG_OOB), MSG_OOB}, {int(b::kMSG_PEEK), MSG_PEEK},
  {int(b::kMSG_DONTROUTE), MSG_DONTROUTE}, {int(b::kMSG_DONTWAIT), MSG_DONTWAIT},
  {int(b::kMSG_EOR), MSG_EOR}, {int(b::kMSG_WAITALL), MSG_WAITALL}, {int(b::kMSG_NOSIGNAL), MSG_NOSIGNAL}};
const std::vector<Flag> message_results{{int(b::kMSG_OOB), MSG_OOB}, {int(b::kMSG_CTRUNC), MSG_CTRUNC},
  {int(b::kMSG_TRUNC), MSG_TRUNC}, {int(b::kMSG_EOR), MSG_EOR}};
const std::vector<Flag> errors{{int(b::kEAI_ADDRFAMILY), EAI_ADDRFAMILY}, {int(b::kEAI_AGAIN), EAI_AGAIN},
  {int(b::kEAI_BADFLAGS), EAI_BADFLAGS}, {int(b::kEAI_FAIL), EAI_FAIL}, {int(b::kEAI_FAMILY), EAI_FAMILY},
  {int(b::kEAI_MEMORY), EAI_MEMORY}, {int(b::kEAI_NODATA), EAI_NODATA}, {int(b::kEAI_NONAME), EAI_NONAME},
  {int(b::kEAI_SERVICE), EAI_SERVICE}, {int(b::kEAI_SOCKTYPE), EAI_SOCKTYPE}, {int(b::kEAI_SYSTEM), EAI_SYSTEM},
  {int(b::kEAI_BADHINTS), EAI_BADHINTS}, {int(b::kEAI_PROTOCOL), EAI_PROTOCOL}, {int(b::kEAI_OVERFLOW), EAI_OVERFLOW}};
int guest_error(int value) { if (!value) return 0; for (auto pair : errors) if (pair.native == value) return pair.guest; return b::kEAI_FAIL; }
const char *error_text(int value) { for (auto pair : errors) if (pair.guest == value) return ::gai_strerror(pair.native); return "Unknown address resolution error"; }
int family_native(int value) {
  if (value == b::kAF_UNSPEC) return AF_UNSPEC;
  if (value == b::kAF_INET) return AF_INET;
  if (value == b::kAF_INET6) return AF_INET6;
  if (value == b::kAF_UNIX) return AF_UNIX;
  return fail(EAFNOSUPPORT);
}
int family_guest(int value) {
  if (value == AF_UNSPEC) return b::kAF_UNSPEC;
  if (value == AF_INET) return b::kAF_INET;
  if (value == AF_INET6) return b::kAF_INET6;
  if (value == AF_UNIX) return b::kAF_UNIX;
  return fail(EAFNOSUPPORT);
}
int type_native(int value) {
  if (!value) return 0;
  if (value == b::kSOCK_STREAM) return SOCK_STREAM;
  if (value == b::kSOCK_DGRAM) return SOCK_DGRAM;
  if (value == b::kSOCK_RAW) return SOCK_RAW;
  if (value == b::kSOCK_SEQPACKET) return SOCK_SEQPACKET;
  return fail(ESOCKTNOSUPPORT);
}
int type_guest(int value) {
  if (!value) return 0;
  if (value == SOCK_STREAM) return b::kSOCK_STREAM;
  if (value == SOCK_DGRAM) return b::kSOCK_DGRAM;
  if (value == SOCK_RAW) return b::kSOCK_RAW;
  if (value == SOCK_SEQPACKET) return b::kSOCK_SEQPACKET;
  return fail(ESOCKTNOSUPPORT);
}
struct Address { sockaddr_storage storage{}; socklen_t size = 0; sockaddr *data() { return reinterpret_cast<sockaddr *>(&storage); } };
bool address_native(const void *guest, uint32_t size, Address &result) {
  if (!guest) { errno = EFAULT; return false; }
  if (size < 2 || size > address_limit) { errno = EINVAL; return false; }
  int family = family_native(GET(Sockaddr, sa_family, uint16_t, guest));
  if (family == -1) return false;
  if (family == AF_INET) {
    if (size < b::kSockaddrInSize) { errno = EINVAL; return false; }
    auto &native = reinterpret_cast<sockaddr_in &>(result.storage);
    native.sin_len = sizeof(native); native.sin_family = AF_INET;
    native.sin_port = GET(SockaddrIn, sin_port, uint16_t, guest);
    std::memcpy(&native.sin_addr, static_cast<const uint8_t *>(guest) + b::kSockaddrIn_sin_addr, 4);
    result.size = sizeof(native);
  } else if (family == AF_INET6) {
    if (size < b::kSockaddrIn6Size) { errno = EINVAL; return false; }
    auto &native = reinterpret_cast<sockaddr_in6 &>(result.storage);
    native.sin6_len = sizeof(native); native.sin6_family = AF_INET6;
    native.sin6_port = GET(SockaddrIn6, sin6_port, uint16_t, guest);
    native.sin6_flowinfo = GET(SockaddrIn6, sin6_flowinfo, uint32_t, guest);
    native.sin6_scope_id = GET(SockaddrIn6, sin6_scope_id, uint32_t, guest);
    std::memcpy(&native.sin6_addr, static_cast<const uint8_t *>(guest) + b::kSockaddrIn6_sin6_addr, 16);
    result.size = sizeof(native);
  } else if (family == AF_UNIX) {
    if (size < b::kSockaddrUn_sun_path || size > b::kSockaddrUnSize) { errno = EINVAL; return false; }
    auto &native = reinterpret_cast<sockaddr_un &>(result.storage);
    const char *path = static_cast<const char *>(guest) + b::kSockaddrUn_sun_path;
    size_t capacity = size - b::kSockaddrUn_sun_path;
    if (capacity && !*path) { errno = EOPNOTSUPP; return false; } // Linux abstract/autobind namespaces are absent.
    size_t length = capacity ? strnlen(path, capacity) : 0;
    if (length == capacity && capacity) { errno = EINVAL; return false; }
    if (length >= sizeof(native.sun_path)) { errno = ENAMETOOLONG; return false; }
    if (length) std::memcpy(native.sun_path, path, length + 1);
    native.sun_family = AF_UNIX;
    result.size = socklen_t(offsetof(sockaddr_un, sun_path) + (capacity ? length + 1 : 0));
    native.sun_len = uint8_t(result.size);
  } else {
    result.size = sizeof(sockaddr); result.data()->sa_len = result.size; result.data()->sa_family = AF_UNSPEC;
  }
  return true;
}
bool address_guest(const sockaddr *native, socklen_t size, std::vector<uint8_t> &result) {
  if (!native || size < 2 || size > sizeof(sockaddr_storage)) { errno = EINVAL; return false; }
  int family = family_guest(native->sa_family);
  if (family == -1) return false;
  if (native->sa_family == AF_INET) {
    if (size < sizeof(sockaddr_in)) { errno = EINVAL; return false; }
    const auto &value = *reinterpret_cast<const sockaddr_in *>(native);
    result.assign(b::kSockaddrInSize, 0);
    PUT(SockaddrIn, sin_family, uint16_t, result.data(), family);
    PUT(SockaddrIn, sin_port, uint16_t, result.data(), value.sin_port);
    std::memcpy(result.data() + b::kSockaddrIn_sin_addr, &value.sin_addr, 4);
  } else if (native->sa_family == AF_INET6) {
    if (size < sizeof(sockaddr_in6)) { errno = EINVAL; return false; }
    const auto &value = *reinterpret_cast<const sockaddr_in6 *>(native);
    result.assign(b::kSockaddrIn6Size, 0);
    PUT(SockaddrIn6, sin6_family, uint16_t, result.data(), family);
    PUT(SockaddrIn6, sin6_port, uint16_t, result.data(), value.sin6_port);
    PUT(SockaddrIn6, sin6_flowinfo, uint32_t, result.data(), value.sin6_flowinfo);
    PUT(SockaddrIn6, sin6_scope_id, uint32_t, result.data(), value.sin6_scope_id);
    std::memcpy(result.data() + b::kSockaddrIn6_sin6_addr, &value.sin6_addr, 16);
  } else if (native->sa_family == AF_UNIX) {
    const auto &value = *reinterpret_cast<const sockaddr_un *>(native);
    size_t capacity = size > offsetof(sockaddr_un, sun_path) ? size - offsetof(sockaddr_un, sun_path) : 0;
    capacity = std::min(capacity, sizeof(value.sun_path));
    size_t length = strnlen(value.sun_path, capacity);
    result.assign(b::kSockaddrUn_sun_path + (capacity ? length + 1 : 0), 0);
    PUT(SockaddrUn, sun_family, uint16_t, result.data(), family);
    if (length) std::memcpy(result.data() + b::kSockaddrUn_sun_path, value.sun_path, length);
  } else { result.assign(b::kSockaddrSize, 0); PUT(Sockaddr, sa_family, uint16_t, result.data(), family); }
  return true;
}
bool copy_address(const Address &address, void *output, uint32_t *size) {
  std::vector<uint8_t> bytes;
  if (!address_guest(reinterpret_cast<const sockaddr *>(&address.storage), address.size, bytes)) return false;
  size_t copied = std::min<size_t>(*size, bytes.size());
  if (copied) std::memcpy(output, bytes.data(), copied);
  *size = uint32_t(bytes.size()); return true;
}
int descriptor_flags(int fd, int flags) {
  if (flags & b::kSOCK_CLOEXEC) {
    int value = ::fcntl(fd, F_GETFD);
    if (value == -1 || ::fcntl(fd, F_SETFD, value | FD_CLOEXEC) == -1) return -1;
  }
  if (flags & b::kSOCK_NONBLOCK) {
    int value = ::fcntl(fd, F_GETFL);
    if (value == -1 || ::fcntl(fd, F_SETFL, value | O_NONBLOCK) == -1) return -1;
  }
  return 0;
}
struct ResolverNode { std::array<uint8_t, b::kAddrinfoSize> info{}; std::vector<uint8_t> address; std::string name; };
struct ResolverResult { std::vector<std::unique_ptr<ResolverNode>> nodes; };
class Network {
  std::mutex mutex_;
  std::map<void *, std::unique_ptr<ResolverResult>> results_;
public:
  int resolve(const char *node, const char *service, const void *guest_hints, void **output) {
    if (!output) { errno = EFAULT; return b::kEAI_SYSTEM; }
    *output = nullptr;
    if ((node && strnlen(node, 4096) == 4096) || (service && strnlen(service, 256) == 256)) return b::kEAI_NONAME;
    addrinfo hints{};
    if (guest_hints) {
      if (!flags(GET(Addrinfo, ai_flags, int32_t, guest_hints), ai_flags, hints.ai_flags)) return b::kEAI_BADFLAGS;
      hints.ai_family = family_native(GET(Addrinfo, ai_family, int32_t, guest_hints));
      hints.ai_socktype = type_native(GET(Addrinfo, ai_socktype, int32_t, guest_hints));
      hints.ai_protocol = GET(Addrinfo, ai_protocol, int32_t, guest_hints);
      if (hints.ai_family == -1) return b::kEAI_FAMILY;
      if (hints.ai_socktype == -1) return b::kEAI_SOCKTYPE;
      if (GET(Addrinfo, ai_addrlen, uint32_t, guest_hints) || GET(Addrinfo, ai_addr, uintptr_t, guest_hints) ||
          GET(Addrinfo, ai_next, uintptr_t, guest_hints) || GET(Addrinfo, ai_canonname, uintptr_t, guest_hints)) return b::kEAI_BADHINTS;
    }
    addrinfo *native = nullptr;
    int error = ::getaddrinfo(node, service, guest_hints ? &hints : nullptr, &native);
    if (error) return guest_error(error);
    std::unique_ptr<addrinfo, decltype(&::freeaddrinfo)> cleanup(native, &::freeaddrinfo);
    auto result = std::make_unique<ResolverResult>();
    for (auto *entry = native; entry; entry = entry->ai_next) {
      if (result->nodes.size() == 256) return b::kEAI_OVERFLOW;
      auto value = std::make_unique<ResolverNode>();
      int family = family_guest(entry->ai_family), type = type_guest(entry->ai_socktype);
      if (family == -1 || type == -1) return b::kEAI_FAMILY;
      if (!address_guest(entry->ai_addr, entry->ai_addrlen, value->address)) return b::kEAI_FAMILY;
      if (entry->ai_canonname) { if (strnlen(entry->ai_canonname, 4096) == 4096) return b::kEAI_OVERFLOW; value->name = entry->ai_canonname; }
      auto *info = value->info.data();
      PUT(Addrinfo, ai_flags, int32_t, info, reverse_flags(entry->ai_flags, ai_flags));
      PUT(Addrinfo, ai_family, int32_t, info, family);
      PUT(Addrinfo, ai_socktype, int32_t, info, type);
      PUT(Addrinfo, ai_protocol, int32_t, info, entry->ai_protocol);
      PUT(Addrinfo, ai_addrlen, uint32_t, info, value->address.size());
      PUT(Addrinfo, ai_addr, uintptr_t, info, reinterpret_cast<uintptr_t>(value->address.data()));
      PUT(Addrinfo, ai_canonname, uintptr_t, info, value->name.empty() ? 0 : reinterpret_cast<uintptr_t>(value->name.data()));
      if (!result->nodes.empty()) PUT(Addrinfo, ai_next, uintptr_t, result->nodes.back()->info.data(), reinterpret_cast<uintptr_t>(info));
      result->nodes.push_back(std::move(value));
    }
    if (result->nodes.empty()) return b::kEAI_NONAME;
    void *identity = result->nodes.front()->info.data();
    std::lock_guard<std::mutex> lock(mutex_);
    if (results_.size() == 128) return b::kEAI_MEMORY;
    results_.emplace(identity, std::move(result)); *output = identity; return 0;
  }
  void free_result(void *value) { if (!value) return; std::lock_guard<std::mutex> lock(mutex_); if (!results_.erase(value)) errno = EINVAL; }
  int name(const void *guest, uint32_t size, char *host, uint32_t host_size, char *service, uint32_t service_size, int requested) {
    if ((!host && host_size) || (!service && service_size)) { errno = EFAULT; return b::kEAI_SYSTEM; }
    if (host_size > 4096 || service_size > 256) return b::kEAI_OVERFLOW;
    Address native;
    if (!address_native(guest, size, native)) return b::kEAI_FAMILY;
    int native_flags;
    if (!flags(requested, ni_flags, native_flags)) return b::kEAI_BADFLAGS;
    return guest_error(::getnameinfo(native.data(), native.size, host, host_size, service, service_size, native_flags));
  }
  int socket(int family, int type, int protocol) {
    int native_family = family_native(family), creation = type & (b::kSOCK_NONBLOCK | b::kSOCK_CLOEXEC);
    int native_type = type_native(type & ~(b::kSOCK_NONBLOCK | b::kSOCK_CLOEXEC));
    if (native_family == -1 || native_type == -1) return -1;
    int fd = ::socket(native_family, native_type, protocol);
    if (fd != -1 && descriptor_flags(fd, creation)) { int error = errno; ::close(fd); return fail(error); }
    return fd;
  }
  int pair(int family, int type, int protocol, int *output) {
    if (!output) return fail(EFAULT);
    int native_family = family_native(family), creation = type & (b::kSOCK_NONBLOCK | b::kSOCK_CLOEXEC);
    int native_type = type_native(type & ~(b::kSOCK_NONBLOCK | b::kSOCK_CLOEXEC));
    if (native_family == -1 || native_type == -1) return -1;
    int pair[2]; if (::socketpair(native_family, native_type, protocol, pair)) return -1;
    if (descriptor_flags(pair[0], creation) || descriptor_flags(pair[1], creation)) {
      int error = errno; ::close(pair[0]); ::close(pair[1]); return fail(error);
    }
    output[0] = pair[0]; output[1] = pair[1]; return 0;
  }
  int connect(int fd, const void *guest, uint32_t size) { Address native; return address_native(guest, size, native) ? ::connect(fd, native.data(), native.size) : -1; }
  int bind(int fd, const void *guest, uint32_t size) { Address native; return address_native(guest, size, native) ? ::bind(fd, native.data(), native.size) : -1; }
  int listen(int fd, int backlog) { return ::listen(fd, backlog); }
  int shutdown(int fd, int direction) {
    int native = direction == b::kSHUT_RD ? SHUT_RD : direction == b::kSHUT_WR ? SHUT_WR : direction == b::kSHUT_RDWR ? SHUT_RDWR : -1;
    return native == -1 ? fail(EINVAL) : ::shutdown(fd, native);
  }
  int get_name(int fd, void *output, uint32_t *size, bool peer) {
    if (!output || !size) return fail(EFAULT);
    Address native; native.size = sizeof(native.storage);
    int status = peer ? ::getpeername(fd, native.data(), &native.size) : ::getsockname(fd, native.data(), &native.size);
    return status || !copy_address(native, output, size) ? -1 : 0;
  }
  int local_name(int fd, void *output, uint32_t *size) { return get_name(fd, output, size, false); }
  int peer_name(int fd, void *output, uint32_t *size) { return get_name(fd, output, size, true); }
  int accept(int fd, void *output, uint32_t *size, int requested) {
    if (requested & ~(b::kSOCK_NONBLOCK | b::kSOCK_CLOEXEC)) return fail(EINVAL);
    if (output && !size) return fail(EFAULT);
    Address native; native.size = sizeof(native.storage);
    int accepted = ::accept(fd, output ? native.data() : nullptr, output ? &native.size : nullptr);
    if (accepted != -1 && (descriptor_flags(accepted, requested) || (output && !copy_address(native, output, size)))) {
      int error = errno; ::close(accepted); return fail(error);
    }
    return accepted;
  }
  int plain_accept(int fd, void *output, uint32_t *size) { return accept(fd, output, size, 0); }
  ssize_t receive(int fd, void *output, size_t size, int requested) {
    if (size > transfer_limit) return fail(EOVERFLOW);
    if (!output && size) return fail(EFAULT);
    int native_flags;
    if (!flags(requested, message_flags, native_flags, EOPNOTSUPP)) return -1;
    return ::recv(fd, output, size, native_flags);
  }
  ssize_t transmit(int fd,const void *input,size_t size,int requested) {
    return send_to(fd,input,size,requested,nullptr,0);
  }
  ssize_t send_to(int fd,const void *input,size_t size,int requested,const void *destination,uint32_t length) {
    if(size>transfer_limit)return fail(EOVERFLOW);
    if(!input && size)return fail(EFAULT);
    int native_flags;if(!flags(requested,message_flags,native_flags,EOPNOTSUPP))return -1;
    Address address;
    if(destination && !address_native(destination,length,address))return -1;
    return ::sendto(fd,input,size,native_flags,destination?address.data():nullptr,destination?address.size:0);
  }
  ssize_t receive_from(int fd,void *output,size_t size,int requested,void *source,uint32_t *length) {
    if(size>transfer_limit)return fail(EOVERFLOW);
    if((!output && size)||(source&&!length))return fail(EFAULT);
    int native_flags;if(!flags(requested,message_flags,native_flags,EOPNOTSUPP))return -1;
    Address address;address.size=sizeof(address.storage);
    ssize_t result=::recvfrom(fd,output,size,native_flags,source?address.data():nullptr,source?&address.size:nullptr);
    if(result>=0 && source && !copy_address(address,source,length))return -1;
    return result;
  }
  int option(int fd, int level, int name, void *value, uint32_t *size, bool setting);
  int get_option(int fd, int level, int name, void *value, uint32_t *size) { return option(fd, level, name, value, size, false); }
  int set_option(int fd, int level, int name, const void *value, uint32_t size) { return option(fd, level, name, const_cast<void *>(value), &size, true); }
  ssize_t message(int fd, void *header, int flags, bool receiving);
  ssize_t send_message(int fd, const void *header, int flags) { return message(fd, const_cast<void *>(header), flags, false); }
  ssize_t receive_message(int fd, void *header, int flags) { return message(fd, header, flags, true); }
};
enum class OptionKind { Int, Error, Type, Byte, Timeval, Linger, Address4, Membership4, Membership6 };
struct Option { int level, name, native_level, native_name; OptionKind kind; };
const Option options[]{
  {int(b::kSOL_SOCKET), int(b::kSO_REUSEADDR), SOL_SOCKET, SO_REUSEADDR, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_REUSEPORT), SOL_SOCKET, SO_REUSEPORT, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_KEEPALIVE), SOL_SOCKET, SO_KEEPALIVE, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_BROADCAST), SOL_SOCKET, SO_BROADCAST, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_OOBINLINE), SOL_SOCKET, SO_OOBINLINE, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_SNDBUF), SOL_SOCKET, SO_SNDBUF, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_RCVBUF), SOL_SOCKET, SO_RCVBUF, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_RCVLOWAT), SOL_SOCKET, SO_RCVLOWAT, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_SNDLOWAT), SOL_SOCKET, SO_SNDLOWAT, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_ACCEPTCONN), SOL_SOCKET, SO_ACCEPTCONN, OptionKind::Int},
  {int(b::kSOL_SOCKET), int(b::kSO_ERROR), SOL_SOCKET, SO_ERROR, OptionKind::Error},
  {int(b::kSOL_SOCKET), int(b::kSO_TYPE), SOL_SOCKET, SO_TYPE, OptionKind::Type},
  {int(b::kSOL_SOCKET), int(b::kSO_LINGER), SOL_SOCKET, SO_LINGER, OptionKind::Linger},
  {int(b::kSOL_SOCKET), int(b::kSO_RCVTIMEO), SOL_SOCKET, SO_RCVTIMEO, OptionKind::Timeval},
  {int(b::kSOL_SOCKET), int(b::kSO_SNDTIMEO), SOL_SOCKET, SO_SNDTIMEO, OptionKind::Timeval},
  {int(b::kIPPROTO_TCP), int(b::kTCP_NODELAY), IPPROTO_TCP, TCP_NODELAY, OptionKind::Int},
  {int(b::kIPPROTO_TCP), int(b::kTCP_KEEPIDLE), IPPROTO_TCP, TCP_KEEPALIVE, OptionKind::Int},
  {int(b::kIPPROTO_TCP), int(b::kTCP_KEEPINTVL), IPPROTO_TCP, TCP_KEEPINTVL, OptionKind::Int},
  {int(b::kIPPROTO_TCP), int(b::kTCP_KEEPCNT), IPPROTO_TCP, TCP_KEEPCNT, OptionKind::Int},
  {int(b::kIPPROTO_IP), int(b::kIP_TTL), IPPROTO_IP, IP_TTL, OptionKind::Int},
  {int(b::kIPPROTO_IP), int(b::kIP_TOS), IPPROTO_IP, IP_TOS, OptionKind::Int},
  {int(b::kIPPROTO_IP), int(b::kIP_PKTINFO), IPPROTO_IP, IP_PKTINFO, OptionKind::Int},
  {int(b::kIPPROTO_IP), int(b::kIP_RECVTTL), IPPROTO_IP, IP_RECVTTL, OptionKind::Int},
#ifdef IP_RECVTOS
  {int(b::kIPPROTO_IP), int(b::kIP_RECVTOS), IPPROTO_IP, IP_RECVTOS, OptionKind::Int},
#endif
  {int(b::kIPPROTO_IP), int(b::kIP_MULTICAST_TTL), IPPROTO_IP, IP_MULTICAST_TTL, OptionKind::Byte},
  {int(b::kIPPROTO_IP), int(b::kIP_MULTICAST_LOOP), IPPROTO_IP, IP_MULTICAST_LOOP, OptionKind::Byte},
  {int(b::kIPPROTO_IP), int(b::kIP_MULTICAST_IF), IPPROTO_IP, IP_MULTICAST_IF, OptionKind::Address4},
  {int(b::kIPPROTO_IP), int(b::kIP_ADD_MEMBERSHIP), IPPROTO_IP, IP_ADD_MEMBERSHIP, OptionKind::Membership4},
  {int(b::kIPPROTO_IP), int(b::kIP_DROP_MEMBERSHIP), IPPROTO_IP, IP_DROP_MEMBERSHIP, OptionKind::Membership4},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_V6ONLY), IPPROTO_IPV6, IPV6_V6ONLY, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_UNICAST_HOPS), IPPROTO_IPV6, IPV6_UNICAST_HOPS, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_MULTICAST_IF), IPPROTO_IPV6, IPV6_MULTICAST_IF, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_MULTICAST_HOPS), IPPROTO_IPV6, IPV6_MULTICAST_HOPS, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_MULTICAST_LOOP), IPPROTO_IPV6, IPV6_MULTICAST_LOOP, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_RECVPKTINFO), IPPROTO_IPV6, IPV6_RECVPKTINFO, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_RECVHOPLIMIT), IPPROTO_IPV6, IPV6_RECVHOPLIMIT, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_RECVTCLASS), IPPROTO_IPV6, IPV6_RECVTCLASS, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_TCLASS), IPPROTO_IPV6, IPV6_TCLASS, OptionKind::Int},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_JOIN_GROUP), IPPROTO_IPV6, IPV6_JOIN_GROUP, OptionKind::Membership6},
  {int(b::kIPPROTO_IPV6), int(b::kIPV6_LEAVE_GROUP), IPPROTO_IPV6, IPV6_LEAVE_GROUP, OptionKind::Membership6},
};
int Network::option(int fd, int level, int name, void *value, uint32_t *size, bool setting) {
  if (!size || (!value && *size)) return fail(EFAULT);
  auto found = std::find_if(std::begin(options), std::end(options), [&](const auto &item) { return item.level == level && item.name == name; });
  if (found == std::end(options)) return fail(ENOPROTOOPT);
  const auto &option = *found;
  std::array<uint8_t, 128> native{}, guest{};
  socklen_t native_size = 0; size_t guest_size = 0;
  auto enough = [&](size_t required) { if (*size < required) { errno = EINVAL; return false; } return true; };
  if (option.kind == OptionKind::Timeval) {
    guest_size = b::kTimevalSize; native_size = sizeof(timeval);
    if (setting) {
      if (!enough(guest_size)) return -1;
      int64_t seconds = GET(Timeval, tv_sec, int64_t, value), micros = GET(Timeval, tv_usec, int64_t, value);
      if (seconds < 0 || micros < 0 || micros > 999999) return fail(EINVAL);
      timeval time{seconds, suseconds_t(micros)}; std::memcpy(native.data(), &time, sizeof(time));
    }
  } else if (option.kind == OptionKind::Linger) {
    guest_size = b::kLingerSize; native_size = sizeof(linger);
    if (setting) {
      if (!enough(guest_size)) return -1;
      linger native_value{GET(Linger, l_onoff, int32_t, value), GET(Linger, l_linger, int32_t, value)};
      if (native_value.l_linger < 0) return fail(EINVAL);
      std::memcpy(native.data(), &native_value, sizeof(native_value));
    }
  } else if (option.kind == OptionKind::Membership4) {
    guest_size = b::kIpMreqSize; native_size = sizeof(ip_mreq);
    if (setting) {
      if (!enough(guest_size)) return -1;
      ip_mreq group{};
      std::memcpy(&group.imr_multiaddr, static_cast<const uint8_t *>(value) + b::kIpMreq_imr_multiaddr, 4);
      std::memcpy(&group.imr_interface, static_cast<const uint8_t *>(value) + b::kIpMreq_imr_interface, 4);
      std::memcpy(native.data(), &group, sizeof(group));
    }
  } else if (option.kind == OptionKind::Membership6) {
    guest_size = b::kIpv6MreqSize; native_size = sizeof(ipv6_mreq);
    if (setting) {
      if (!enough(guest_size)) return -1;
      ipv6_mreq group{};
      std::memcpy(&group.ipv6mr_multiaddr, static_cast<const uint8_t *>(value) + b::kIpv6Mreq_ipv6mr_multiaddr, 16);
      group.ipv6mr_interface = GET(Ipv6Mreq, ipv6mr_interface, uint32_t, value);
      std::memcpy(native.data(), &group, sizeof(group));
    }
  } else if (option.kind == OptionKind::Address4) {
    guest_size = native_size = 4;
    if (setting) { if (!enough(4)) return -1; std::memcpy(native.data(), value, 4); }
  } else {
    guest_size = 4; native_size = option.kind == OptionKind::Byte ? 1 : sizeof(int);
    if (setting) {
      if (option.kind == OptionKind::Error || option.kind == OptionKind::Type) return fail(ENOPROTOOPT);
      int32_t integer;
      if (option.kind == OptionKind::Byte && *size == 1) integer = *static_cast<const uint8_t *>(value);
      else { if (!enough(4)) return -1; std::memcpy(&integer, value, 4); }
      if (option.kind == OptionKind::Byte) { if (integer < 0 || integer > 255) return fail(EINVAL); native[0] = uint8_t(integer); }
      else std::memcpy(native.data(), &integer, 4);
    }
  }
  if (setting) return ::setsockopt(fd, option.native_level, option.native_name, native.data(), native_size);
  if (::getsockopt(fd, option.native_level, option.native_name, native.data(), &native_size)) return -1;
  if (option.kind == OptionKind::Timeval) {
    if (native_size != sizeof(timeval)) return fail(EIO);
    timeval time; std::memcpy(&time, native.data(), sizeof(time));
    PUT(Timeval, tv_sec, int64_t, guest.data(), time.tv_sec);
    PUT(Timeval, tv_usec, int64_t, guest.data(), time.tv_usec);
  } else if (option.kind == OptionKind::Linger) {
    if (native_size != sizeof(linger)) return fail(EIO);
    linger native_value; std::memcpy(&native_value, native.data(), sizeof(native_value));
    PUT(Linger, l_onoff, int32_t, guest.data(), native_value.l_onoff);
    PUT(Linger, l_linger, int32_t, guest.data(), native_value.l_linger);
  } else if (option.kind == OptionKind::Membership4 || option.kind == OptionKind::Membership6) return fail(ENOPROTOOPT);
  else if (option.kind == OptionKind::Address4) std::memcpy(guest.data(), native.data(), 4);
  else {
    int32_t integer = 0;
    if (option.kind == OptionKind::Byte) integer = native[0];
    else { if (native_size != 4) return fail(EIO); std::memcpy(&integer, native.data(), 4); }
    if (option.kind == OptionKind::Error) integer = errno_to_guest(integer);
    if (option.kind == OptionKind::Type) { integer = type_guest(integer); if (integer == -1) return -1; }
    std::memcpy(guest.data(), &integer, 4);
  }
  size_t copied = std::min<size_t>(*size, guest_size);
  if (copied) std::memcpy(value, guest.data(), copied);
  *size = uint32_t(copied); return 0;
}
size_t align_guest(size_t value) { return (value + size_t(b::kCmsgAlignment) - 1) & ~(size_t(b::kCmsgAlignment) - 1); }
bool control_to_native(int level, int type, const uint8_t *input, size_t size,
                       int &native_level, int &native_type, std::vector<uint8_t> &output) {
  if (level == b::kSOL_SOCKET && type == b::kSCM_RIGHTS) {
    if (!size || size % sizeof(int) || size / sizeof(int) > 253) { errno = EINVAL; return false; }
    for(size_t i=0;i<size/sizeof(int);++i){int descriptor;std::memcpy(&descriptor,input+i*sizeof(int),sizeof(int));if(event_descriptor_tracked(descriptor)){errno=EOPNOTSUPP;return false;}}
    native_level = SOL_SOCKET; native_type = SCM_RIGHTS; output.assign(input, input + size); return true;
  }
  if (level == b::kIPPROTO_IP && type == b::kIP_PKTINFO) {
    if (size != b::kInPktinfoSize) { errno = EINVAL; return false; }
    in_pktinfo value{}; int index = GET(InPktinfo, ipi_ifindex, int32_t, input);
    if (index < 0) { errno = EINVAL; return false; }
    value.ipi_ifindex = unsigned(index);
    std::memcpy(&value.ipi_spec_dst, input + b::kInPktinfo_ipi_spec_dst, 4);
    std::memcpy(&value.ipi_addr, input + b::kInPktinfo_ipi_addr, 4);
    native_level = IPPROTO_IP; native_type = IP_PKTINFO;
    output.resize(sizeof(value)); std::memcpy(output.data(), &value, sizeof(value)); return true;
  }
  if (level == b::kIPPROTO_IPV6 && type == b::kIPV6_PKTINFO) {
    if (size != b::kIn6PktinfoSize) { errno = EINVAL; return false; }
    in6_pktinfo value{};
    std::memcpy(&value.ipi6_addr, input + b::kIn6Pktinfo_ipi6_addr, 16);
    value.ipi6_ifindex = GET(In6Pktinfo, ipi6_ifindex, uint32_t, input);
    native_level = IPPROTO_IPV6; native_type = IPV6_PKTINFO;
    output.resize(sizeof(value)); std::memcpy(output.data(), &value, sizeof(value)); return true;
  }
  if ((level == b::kIPPROTO_IP && (type == b::kIP_TTL || type == b::kIP_TOS)) ||
      (level == b::kIPPROTO_IPV6 && (type == b::kIPV6_HOPLIMIT || type == b::kIPV6_TCLASS))) {
    if (size != 4) { errno = EINVAL; return false; }
    native_level = level == b::kIPPROTO_IP ? IPPROTO_IP : IPPROTO_IPV6;
    native_type = level == b::kIPPROTO_IP ? (type == b::kIP_TTL ? IP_TTL : IP_TOS)
                                           : (type == b::kIPV6_HOPLIMIT ? IPV6_HOPLIMIT : IPV6_TCLASS);
    output.assign(input, input + 4); return true;
  }
  errno = EOPNOTSUPP; return false;
}
bool send_controls(const void *input, size_t size, std::vector<uint8_t> &output) {
  if (!size) return true;
  if (!input) { errno = EFAULT; return false; }
  if (size > control_limit || reinterpret_cast<uintptr_t>(input) > UINTPTR_MAX - size) { errno = EOVERFLOW; return false; }
  size_t offset = 0;
  while (size - offset >= b::kCmsghdrSize) {
    const auto *header = static_cast<const uint8_t *>(input) + offset;
    uint64_t length = GET(Cmsghdr, cmsg_len, uint64_t, header);
    if (length < b::kCmsgDataOffset || length > size - offset) { errno = EINVAL; return false; }
    int native_level, native_type;
    std::vector<uint8_t> payload;
    if (!control_to_native(GET(Cmsghdr, cmsg_level, int32_t, header), GET(Cmsghdr, cmsg_type, int32_t, header),
                          header + b::kCmsgDataOffset, size_t(length) - b::kCmsgDataOffset,
                          native_level, native_type, payload)) return false;
    size_t space = CMSG_SPACE(payload.size());
    if (space > control_limit - output.size()) { errno = EOVERFLOW; return false; }
    size_t start = output.size(); output.resize(start + space, 0);
    cmsghdr native{}; native.cmsg_len = socklen_t(CMSG_LEN(payload.size()));
    native.cmsg_level = native_level; native.cmsg_type = native_type;
    std::memcpy(output.data() + start, &native, sizeof(native));
    if (!payload.empty()) std::memcpy(output.data() + start + CMSG_LEN(0), payload.data(), payload.size());
    size_t next = align_guest(size_t(length));
    if (next > size - offset) break;
    offset += next;
  }
  return true;
}
struct ReceivedDescriptors {
  std::vector<int> values;
  bool committed = false;
  ~ReceivedDescriptors() { if (!committed) for (int fd : values) if (fd >= 0) ::close(fd); }
};
bool collect_descriptors(const uint8_t *native, size_t size, ReceivedDescriptors &owned) {
  size_t offset = 0;
  while (size - offset >= sizeof(cmsghdr)) {
    cmsghdr header; std::memcpy(&header, native + offset, sizeof(header));
    if (header.cmsg_len < CMSG_LEN(0) || header.cmsg_len > size - offset) { errno = EIO; return false; }
    size_t payload = header.cmsg_len - CMSG_LEN(0);
    if (header.cmsg_level == SOL_SOCKET && header.cmsg_type == SCM_RIGHTS) {
      if (payload % sizeof(int)) { errno = EIO; return false; }
      for (size_t i = 0; i < payload / sizeof(int); ++i) {
        int fd; std::memcpy(&fd, native + offset + CMSG_LEN(0) + i * sizeof(int), sizeof(fd));
        owned.values.push_back(fd); // Capacity was reserved before recvmsg.
      }
    }
    size_t next = CMSG_SPACE(payload);
    if (next > size - offset) break;
    offset += next;
  }
  return true;
}
bool control_from_native(int level, int type, const uint8_t *input, size_t size,
                         int &guest_level, int &guest_type, std::vector<uint8_t> &output) {
  if (level == SOL_SOCKET && type == SCM_RIGHTS) {
    if (!size || size % sizeof(int)) return false;
    guest_level = b::kSOL_SOCKET; guest_type = b::kSCM_RIGHTS;
    output.assign(input, input + size); return true;
  }
  if (level == IPPROTO_IP && type == IP_PKTINFO) {
    if (size != sizeof(in_pktinfo)) return false;
    in_pktinfo value; std::memcpy(&value, input, sizeof(value));
    if (value.ipi_ifindex > INT_MAX) return false;
    guest_level = b::kIPPROTO_IP; guest_type = b::kIP_PKTINFO; output.assign(b::kInPktinfoSize, 0);
    PUT(InPktinfo, ipi_ifindex, int32_t, output.data(), value.ipi_ifindex);
    std::memcpy(output.data() + b::kInPktinfo_ipi_spec_dst, &value.ipi_spec_dst, 4);
    std::memcpy(output.data() + b::kInPktinfo_ipi_addr, &value.ipi_addr, 4); return true;
  }
  if (level == IPPROTO_IPV6 && type == IPV6_PKTINFO) {
    if (size != sizeof(in6_pktinfo)) return false;
    in6_pktinfo value; std::memcpy(&value, input, sizeof(value));
    guest_level = b::kIPPROTO_IPV6; guest_type = b::kIPV6_PKTINFO; output.assign(b::kIn6PktinfoSize, 0);
    PUT(In6Pktinfo, ipi6_ifindex, uint32_t, output.data(), value.ipi6_ifindex);
    std::memcpy(output.data() + b::kIn6Pktinfo_ipi6_addr, &value.ipi6_addr, 16); return true;
  }
  if ((level == IPPROTO_IP && (type == IP_TTL || type == IP_RECVTTL || type == IP_TOS
#ifdef IP_RECVTOS
      || type == IP_RECVTOS
#endif
      )) ||
      (level == IPPROTO_IPV6 && (type == IPV6_HOPLIMIT || type == IPV6_TCLASS))) {
    int32_t value;
    if (size == 1) value = input[0]; else if (size == 4) std::memcpy(&value, input, 4); else return false;
    guest_level = level == IPPROTO_IP ? b::kIPPROTO_IP : b::kIPPROTO_IPV6;
    guest_type = level == IPPROTO_IP ? ((type == IP_TOS
#ifdef IP_RECVTOS
      || type == IP_RECVTOS
#endif
      ) ? b::kIP_TOS : b::kIP_TTL)
                                    : (type == IPV6_HOPLIMIT ? b::kIPV6_HOPLIMIT : b::kIPV6_TCLASS);
    output.resize(4); std::memcpy(output.data(), &value, 4); return true;
  }
  return false;
}
bool receive_controls(const uint8_t *native, size_t size, size_t capacity, bool cloexec,
                      std::vector<uint8_t> &output, int &flags, ReceivedDescriptors &owned) {
  size_t offset = 0, descriptor_cursor = 0;
  while (size - offset >= sizeof(cmsghdr)) {
    cmsghdr header; std::memcpy(&header, native + offset, sizeof(header));
    if (header.cmsg_len < CMSG_LEN(0) || header.cmsg_len > size - offset) { errno = EIO; return false; }
    size_t payload_size = header.cmsg_len - CMSG_LEN(0), descriptor_start = descriptor_cursor;
    const auto *payload = native + offset + CMSG_LEN(0);
    bool rights = header.cmsg_level == SOL_SOCKET && header.cmsg_type == SCM_RIGHTS;
    if (rights) {
      if (payload_size % sizeof(int)) { errno = EIO; return false; }
      for (size_t i = 0; i < payload_size / sizeof(int); ++i) {
        int fd; std::memcpy(&fd, payload + i * sizeof(int), sizeof(int));
        ++descriptor_cursor;
        if (fd < 0 || (cloexec && descriptor_flags(fd, b::kSOCK_CLOEXEC))) { if (fd < 0) errno = EIO; return false; }
      }
    }
    int guest_level, guest_type;
    std::vector<uint8_t> converted;
    bool supported = control_from_native(header.cmsg_level, header.cmsg_type, payload, payload_size,
                                         guest_level, guest_type, converted);
    if (supported && rights) {
      size_t count = converted.size() / sizeof(int), keep = count;
      while (keep && align_guest(size_t(b::kCmsgDataOffset) + keep * sizeof(int)) > capacity - output.size()) --keep;
      if (keep != count) {
        flags |= b::kMSG_CTRUNC;
        for (size_t i = keep; i < count; ++i) { ::close(owned.values[descriptor_start + i]); owned.values[descriptor_start + i] = -1; }
        converted.resize(keep * sizeof(int));
        if (!keep) supported = false;
      }
    }
    size_t length = b::kCmsgDataOffset + converted.size(), space = align_guest(length);
    if (!supported || space > capacity - output.size()) flags |= b::kMSG_CTRUNC;
    else {
      size_t start = output.size(); output.resize(start + space, 0);
      PUT(Cmsghdr, cmsg_len, uint64_t, output.data() + start, length);
      PUT(Cmsghdr, cmsg_level, int32_t, output.data() + start, guest_level);
      PUT(Cmsghdr, cmsg_type, int32_t, output.data() + start, guest_type);
      if (!converted.empty()) std::memcpy(output.data() + start + b::kCmsgDataOffset, converted.data(), converted.size());
    }
    size_t next = CMSG_SPACE(payload_size);
    if (next > size - offset) break;
    offset += next;
  }
  return true;
}
ssize_t Network::message(int fd, void *header, int requested, bool receiving) {
  if (!header) return fail(EFAULT);
  uint64_t count = GET(Msghdr, msg_iovlen, uint64_t, header), control_size = GET(Msghdr, msg_controllen, uint64_t, header);
  auto vector_address = GET(Msghdr, msg_iov, uintptr_t, header);
  auto control_address = GET(Msghdr, msg_control, uintptr_t, header);
  if (count > vector_limit || control_size > control_limit || vector_address > UINTPTR_MAX - count * b::kIovecSize ||
      control_address > UINTPTR_MAX - control_size) return fail(EOVERFLOW);
  if ((count && !vector_address) || (control_size && !control_address)) return fail(EFAULT);
  std::vector<iovec> vectors;
  size_t total = 0;
  for (size_t i = 0; i < count; ++i) {
    const auto *entry = reinterpret_cast<const uint8_t *>(vector_address) + i * b::kIovecSize;
    uint64_t length = GET(Iovec, iov_len, uint64_t, entry);
    auto base = GET(Iovec, iov_base, uintptr_t, entry);
    if (length > transfer_limit - total || base > UINTPTR_MAX - length) return fail(EOVERFLOW);
    if (length && !base) return fail(EFAULT);
    total += size_t(length); vectors.push_back({reinterpret_cast<void *>(base), size_t(length)});
  }
  bool cloexec = receiving && (requested & b::kMSG_CMSG_CLOEXEC);
  if (cloexec) requested &= ~int(b::kMSG_CMSG_CLOEXEC);
  int native_flags;
  if (!flags(requested, message_flags, native_flags, EOPNOTSUPP)) return -1;
  auto guest_name = GET(Msghdr, msg_name, uintptr_t, header);
  uint32_t name_size = GET(Msghdr, msg_namelen, uint32_t, header);
  Address address;
  if (guest_name && !receiving && !address_native(reinterpret_cast<void *>(guest_name), name_size, address)) return -1;
  if (guest_name && receiving) address.size = sizeof(address.storage);
  std::vector<uint8_t> controls;
  if (receiving) { if (control_size) controls.resize(control_limit); }
  else if (!send_controls(reinterpret_cast<void *>(control_address), size_t(control_size), controls)) return -1;
  msghdr message{};
  message.msg_name = guest_name ? address.data() : nullptr; message.msg_namelen = guest_name ? address.size : 0;
  message.msg_iov = vectors.empty() ? nullptr : vectors.data(); message.msg_iovlen = int(vectors.size());
  message.msg_control = controls.empty() ? nullptr : controls.data(); message.msg_controllen = socklen_t(controls.size());
  ReceivedDescriptors descriptors;
  if (receiving) descriptors.values.reserve(control_limit / sizeof(int));
  ssize_t result = receiving ? ::recvmsg(fd, &message, native_flags) : ::sendmsg(fd, &message, native_flags);
  if (result < 0 || !receiving) return result;
  if (message.msg_controllen > controls.size()) return fail(EIO);
  if (!collect_descriptors(controls.data(), message.msg_controllen, descriptors)) return -1;
  std::vector<uint8_t> guest_controls, guest_address;
  int returned_flags = reverse_flags(message.msg_flags, message_results);
  if (!receive_controls(controls.data(), message.msg_controllen, size_t(control_size), cloexec,
                        guest_controls, returned_flags, descriptors)) return -1;
  if (guest_name && message.msg_namelen && !address_guest(address.data(), message.msg_namelen, guest_address)) return -1;
  if (guest_name) {
    size_t copied = std::min<size_t>(name_size, guest_address.size());
    if (copied) std::memcpy(reinterpret_cast<void *>(guest_name), guest_address.data(), copied);
  }
  if (!guest_controls.empty()) std::memcpy(reinterpret_cast<void *>(control_address), guest_controls.data(), guest_controls.size());
  PUT(Msghdr, msg_namelen, uint32_t, header, guest_address.size());
  PUT(Msghdr, msg_controllen, uint64_t, header, guest_controls.size());
  PUT(Msghdr, msg_flags, int32_t, header, returned_flags);
  descriptors.committed = true;
  return result;
}
void *next_control(void *header, void *current) {
  if (!header || !current) return nullptr;
  auto base = GET(Msghdr, msg_control, uintptr_t, header);
  uint64_t size = GET(Msghdr, msg_controllen, uint64_t, header);
  auto address = reinterpret_cast<uintptr_t>(current);
  if (!base || size > control_limit || base > UINTPTR_MAX - size || address < base || address > base + size) { errno = EINVAL; return nullptr; }
  size_t offset = address - base;
  if (size - offset < b::kCmsghdrSize) return nullptr;
  uint64_t length = GET(Cmsghdr, cmsg_len, uint64_t, current);
  if (length < b::kCmsgDataOffset || length > size - offset) { errno = EINVAL; return nullptr; }
  size_t step = align_guest(size_t(length));
  if (step > size - offset || b::kCmsghdrSize > size - offset - step) return nullptr;
  auto *next = reinterpret_cast<void *>(address + step);
  uint64_t next_length = GET(Cmsghdr, cmsg_len, uint64_t, next);
  if (next_length < b::kCmsgDataOffset || next_length > size - offset - step) { errno = EINVAL; return nullptr; }
  return next;
}
template<class R, class... Args>
ProviderExport method_export(const char *name, std::shared_ptr<Network> state, R (Network::*method)(Args...), bool gai = false) {
  auto *entry = closure([state, method, gai](Capture &capture) {
    (void)gai;
    CallCursor cursor(capture, Convention::Aapcs64);
    std::tuple<Args...> args{cursor.read<Args>()...};
    HostErrno preserve;
    int error = 0;
    if constexpr (std::is_void_v<R>) {
      try { std::apply([&](Args... values) { ((*state).*method)(values...); }, args); error = errno; }
      catch (const std::bad_alloc &) { error = ENOMEM; }
    } else {
      R value = R(-1);
      try { value = std::apply([&](Args... values) { return ((*state).*method)(values...); }, args); error = errno; }
      catch (const std::bad_alloc &) { error = ENOMEM; if (gai) value = R(b::kEAI_MEMORY); }
      if (value == R(-1) && !error) error = EIO;
      return_value(capture, value);
    }
    if (error) *guest_errno_location() = errno_to_guest(error);
  });
  return {name, "LIBC", reinterpret_cast<uintptr_t>(entry), SymbolKind::Function, "network/explicit-bionic-abi", false};
}
}
std::vector<ProviderExport> networking_exports() {
  auto state = std::make_shared<Network>();
  std::vector<ProviderExport> result;
  auto add = [&](const char *name, auto method, bool gai = false) { result.push_back(method_export(name, state, method, gai)); };
  add("getaddrinfo", &Network::resolve, true); add("freeaddrinfo", &Network::free_result);
  add("getnameinfo", &Network::name, true);
  result.push_back(fixed_export("gai_strerror", "LIBC", &error_text));
  add("socket", &Network::socket); add("socketpair", &Network::pair);
  add("connect", &Network::connect); add("bind", &Network::bind); add("listen", &Network::listen);
  add("shutdown", &Network::shutdown); add("accept4", &Network::accept); add("accept", &Network::plain_accept);
  add("getsockname", &Network::local_name); add("getpeername", &Network::peer_name);
  add("getsockopt", &Network::get_option); add("setsockopt", &Network::set_option);
  add("recv", &Network::receive); add("sendmsg", &Network::send_message); add("recvmsg", &Network::receive_message);
  add("send",&Network::transmit);add("sendto",&Network::send_to);add("recvfrom",&Network::receive_from);
  result.push_back(fixed_export("inet_pton","LIBC",+[](int family,const char *text,void *output){
    int native=family_native(family);if(native!=AF_INET&&native!=AF_INET6)return fail(EAFNOSUPPORT);
    if(!text||!output)return fail(EFAULT);return ::inet_pton(native,text,output);
  },ErrnoPolicy::MinusOne));
  result.push_back(fixed_export("inet_ntop","LIBC",+[](int family,const void *input,char *output,uint32_t size)->const char *{
    int native=family_native(family);if(native!=AF_INET&&native!=AF_INET6){errno=EAFNOSUPPORT;return nullptr;}
    if(!input||!output){errno=EFAULT;return nullptr;}return ::inet_ntop(native,input,output,size);
  },ErrnoPolicy::Null));
  result.push_back(fixed_export("inet_ntoa","LIBC",+[](uint32_t address)->char *{
    thread_local char output[INET_ADDRSTRLEN];in_addr value{address};
    return ::inet_ntop(AF_INET,&value,output,sizeof(output))?output:nullptr;
  },ErrnoPolicy::Null));
  result.push_back(fixed_export("if_indextoname","LIBC",&::if_indextoname,ErrnoPolicy::Null));
  result.push_back(fixed_export("__cmsg_nxthdr", "LIBC", &next_control, ErrnoPolicy::Observe));
  return result;
}
}
