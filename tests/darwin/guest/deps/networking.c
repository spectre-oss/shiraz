// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/uio.h>
#include <sys/time.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>
#include <errno.h>
_Static_assert(sizeof(struct msghdr) == 56 && sizeof(struct cmsghdr) == 16 && CMSG_LEN(0) == 16,
               "Android arm64 message ABI");
_Static_assert(sizeof(struct sockaddr_in6) == 28 && AF_INET6 == 10,
               "Android IPv6 address ABI differs from Darwin");
_Static_assert(sizeof(struct addrinfo) == 48 && offsetof(struct addrinfo, ai_addr) == 32 && AI_NUMERICSERV == 8,
               "Android resolver layout/flags");
#define CHECK(X) do { if (!(X)) return __LINE__; } while (0)
static int resolver(void) {
  struct addrinfo hints = {0}, *result = 0;
  hints.ai_family = AF_UNSPEC; hints.ai_socktype = SOCK_STREAM;
  hints.ai_flags = AI_CANONNAME | AI_NUMERICSERV;
  CHECK(getaddrinfo("localhost", "0", &hints, &result) == 0 && result);
  unsigned count = 0;
  for (struct addrinfo *item = result; item; item = item->ai_next) {
    CHECK(++count <= 256 && (item->ai_family == AF_INET || item->ai_family == AF_INET6));
    CHECK(item->ai_addr && item->ai_addr->sa_family == item->ai_family && item->ai_socktype == SOCK_STREAM);
    char host[128], service[32];
    CHECK(getnameinfo(item->ai_addr, item->ai_addrlen, host, sizeof(host), service, sizeof(service), NI_NUMERICHOST | NI_NUMERICSERV) == 0);
    CHECK(*host && !strcmp(service, "0"));
  }
  freeaddrinfo(result);
  hints.ai_flags = AI_NUMERICHOST;
  result = (void *)(uintptr_t)1;
  CHECK(getaddrinfo("not-an-ip-address", "0", &hints, &result) == EAI_NONAME && !result);
  hints.ai_flags = 0x40000000;
  CHECK(getaddrinfo("127.0.0.1", "0", &hints, &result) == EAI_BADFLAGS && !result);
  CHECK(gai_strerror(EAI_NONAME) && *gai_strerror(EAI_NONAME));
  return 0;
}
static int tcp4(void) {
  int server = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
  CHECK(server >= 0 && (fcntl(server, F_GETFD) & FD_CLOEXEC));
  int enabled = 1;
  CHECK(setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &enabled, sizeof(enabled)) == 0);
  struct sockaddr_in address = {0};
  address.sin_family = AF_INET; address.sin_addr.s_addr = UINT32_C(0x0100007f);
  CHECK(bind(server, (struct sockaddr *)&address, sizeof(address)) == 0);
  socklen_t size = sizeof(address);
  CHECK(getsockname(server, (struct sockaddr *)&address, &size) == 0 && size == sizeof(address) && address.sin_family == AF_INET && address.sin_port);
  CHECK(listen(server, 2) == 0);
  int client = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
  CHECK(client >= 0 && connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);
  struct sockaddr_in peer = {0}; size = sizeof(peer);
  int accepted = accept4(server, (struct sockaddr *)&peer, &size, SOCK_CLOEXEC | SOCK_NONBLOCK);
  CHECK(accepted >= 0 && size == sizeof(peer) && peer.sin_family == AF_INET);
  CHECK((fcntl(accepted, F_GETFD) & FD_CLOEXEC) && (fcntl(accepted, F_GETFL) & O_NONBLOCK));
  size = sizeof(peer);
  CHECK(getpeername(client, (struct sockaddr *)&peer, &size) == 0 && peer.sin_port == address.sin_port);
  int value = 0; size = sizeof(value);
  CHECK(getsockopt(server, SOL_SOCKET, SO_REUSEADDR, &value, &size) == 0 && size == 4 && value);
  size = sizeof(value);
  CHECK(getsockopt(client, SOL_SOCKET, SO_TYPE, &value, &size) == 0 && value == SOCK_STREAM);
  size = sizeof(value);
  CHECK(getsockopt(client, SOL_SOCKET, SO_ERROR, &value, &size) == 0 && !value);
  CHECK(setsockopt(client, IPPROTO_TCP, TCP_NODELAY, &enabled, 4) == 0);
  size = 4; CHECK(getsockopt(client, IPPROTO_TCP, TCP_NODELAY, &value, &size) == 0 && value);
  struct timeval timeout = {1, 123000}, observed = {0};
  CHECK(setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) == 0);
  size = sizeof(observed);
  CHECK(getsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &observed, &size) == 0 && size == sizeof(observed) && observed.tv_sec >= 1 && observed.tv_usec >= 0 && observed.tv_usec < 1000000);
  struct iovec vectors[2] = {{"one", 3}, {"two", 3}};
  struct msghdr message = {0}; message.msg_iov = vectors; message.msg_iovlen = 2;
  CHECK(sendmsg(client, &message, MSG_NOSIGNAL) == 6);
  char bytes[8] = {0};
  struct pollfd ready={accepted,POLLIN,0};CHECK(poll(&ready,1,1000)==1 && (ready.revents&POLLIN));
  ssize_t peek=recv(accepted,bytes,6,MSG_PEEK);if(peek<0)return 10000+errno;if(peek!=6)return 11000+(int)peek;CHECK(!memcmp(bytes,"onetwo",6));
  CHECK(recv(accepted, bytes, 6, MSG_WAITALL) == 6 && !memcmp(bytes, "onetwo", 6));
  errno = 0; CHECK(recv(accepted, bytes, 1, MSG_DONTWAIT) == -1 && errno == EAGAIN);
  size = 4; errno = 0;
  CHECK(getsockopt(client, SOL_SOCKET, 0x12345, &value, &size) == -1 && errno == ENOPROTOOPT);
  CHECK(shutdown(client, SHUT_WR) == 0);
  errno = 0; CHECK(sendmsg(client, &message, MSG_NOSIGNAL) == -1 && errno == EPIPE);
  CHECK(close(client) == 0 && close(accepted) == 0 && close(server) == 0);
  return 0;
}
static int udp6(void) {
  int receiver = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0);
  int sender = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0);
  CHECK(receiver >= 0 && sender >= 0);
  int enabled = 1, hops = 42;
  CHECK(setsockopt(receiver, IPPROTO_IPV6, IPV6_V6ONLY, &enabled, 4) == 0);
  CHECK(setsockopt(receiver, IPPROTO_IPV6, IPV6_RECVPKTINFO, &enabled, 4) == 0);
  CHECK(setsockopt(receiver, IPPROTO_IPV6, IPV6_RECVHOPLIMIT, &enabled, 4) == 0);
  CHECK(setsockopt(sender, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &hops, 4) == 0);
  struct sockaddr_in6 address = {0}; address.sin6_family = AF_INET6; address.sin6_addr.s6_addr[15] = 1;
  CHECK(bind(receiver, (struct sockaddr *)&address, sizeof(address)) == 0);
  socklen_t size = sizeof(address);
  CHECK(getsockname(receiver, (struct sockaddr *)&address, &size) == 0 && size == sizeof(address) && address.sin6_family == AF_INET6 && address.sin6_port);
  struct iovec data = {"ipv6", 4};
  struct msghdr output = {0}; output.msg_name = &address; output.msg_namelen = sizeof(address); output.msg_iov = &data; output.msg_iovlen = 1;
  CHECK(sendmsg(sender, &output, 0) == 4);
  char bytes[16] = {0}; struct sockaddr_in6 peer = {0};
  union { struct cmsghdr alignment; char bytes[CMSG_SPACE(sizeof(struct in6_pktinfo)) + CMSG_SPACE(sizeof(int))]; } controls;
  struct iovec vector = {bytes, sizeof(bytes)};
  struct msghdr input = {0}; input.msg_name = &peer; input.msg_namelen = sizeof(peer); input.msg_iov = &vector;
  input.msg_iovlen = 1; input.msg_control = controls.bytes; input.msg_controllen = sizeof(controls.bytes);
  CHECK(recvmsg(receiver, &input, 0) == 4 && !memcmp(bytes, "ipv6", 4));
  CHECK(input.msg_namelen == sizeof(peer) && peer.sin6_family == AF_INET6 && !(input.msg_flags & MSG_CTRUNC));
  int packet = 0, limit = 0, count = 0;
  for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&input); cmsg; cmsg = CMSG_NXTHDR(&input, cmsg)) {
    CHECK(++count <= 8 && cmsg->cmsg_level == IPPROTO_IPV6);
    if (cmsg->cmsg_type == IPV6_PKTINFO) {
      struct in6_pktinfo info; memcpy(&info, CMSG_DATA(cmsg), sizeof(info));
      CHECK(cmsg->cmsg_len == CMSG_LEN(sizeof(info)) && info.ipi6_ifindex && info.ipi6_addr.s6_addr[15] == 1); packet = 1;
    } else if (cmsg->cmsg_type == IPV6_HOPLIMIT) { int value; memcpy(&value, CMSG_DATA(cmsg), 4); CHECK(value == 42); limit = 1; }
  }
  CHECK(packet && limit);
  CHECK(sendmsg(sender, &output, 0) == 4);
  input.msg_controllen = 1; input.msg_namelen = sizeof(peer);
  CHECK(recvmsg(receiver, &input, 0) == 4 && (input.msg_flags & MSG_CTRUNC) && input.msg_controllen == 0);
  CHECK(close(receiver) == 0 && close(sender) == 0);
  return 0;
}
static int unix_path(void) {
  struct sockaddr_un address = {0}; address.sun_family = AF_UNIX;
  memcpy(address.sun_path, "network.sock", 13);
  socklen_t size = offsetof(struct sockaddr_un, sun_path) + 13;
  int server = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0), client = socket(AF_UNIX, SOCK_STREAM, 0);
  CHECK(server >= 0 && client >= 0 && bind(server, (struct sockaddr *)&address, size) == 0 && listen(server, 1) == 0);
  CHECK(connect(client, (struct sockaddr *)&address, size) == 0);
  int accepted = accept4(server, 0, 0, SOCK_CLOEXEC); CHECK(accepted >= 0);
  struct sockaddr_un observed = {0}; socklen_t observed_size = sizeof(observed);
  CHECK(getsockname(server, (struct sockaddr *)&observed, &observed_size) == 0 && observed.sun_family == AF_UNIX && !strcmp(observed.sun_path, address.sun_path));
  CHECK(close(server) == 0 && close(client) == 0 && close(accepted) == 0 && unlink("network.sock") == 0);
  server = socket(AF_UNIX, SOCK_DGRAM, 0); CHECK(server >= 0);
  address.sun_path[0] = 0; errno = 0;
  CHECK(bind(server, (struct sockaddr *)&address, size) == -1 && errno == EOPNOTSUPP);
  CHECK(close(server) == 0); return 0;
}
int run_networking(void) {
  int value = resolver(); if (value) return value;
  value = tcp4(); if (value) return value;
  value = udp6(); if (value) return value;
  return unix_path();
}
int run_network_rights(void) {
  int pair[2], source[2]; CHECK(socketpair(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0, pair) == 0 && pipe(source) == 0);
  CHECK(write(source[1], "R", 1) == 1);
  union { struct cmsghdr alignment; char bytes[CMSG_SPACE(sizeof(int))]; } control;
  memset(&control, 0, sizeof(control));
  struct iovec vector = {"F", 1};
  struct msghdr output = {0}; output.msg_iov = &vector; output.msg_iovlen = 1;
  output.msg_control = control.bytes; output.msg_controllen = sizeof(control.bytes);
  struct cmsghdr *cmsg = CMSG_FIRSTHDR(&output); cmsg->cmsg_len = CMSG_LEN(sizeof(int)); cmsg->cmsg_level = SOL_SOCKET; cmsg->cmsg_type = SCM_RIGHTS;
  memcpy(CMSG_DATA(cmsg), &source[0], sizeof(int));
  CHECK(sendmsg(pair[0], &output, MSG_NOSIGNAL) == 1);
  char marker = 0; struct iovec incoming = {&marker, 1};
  union { struct cmsghdr alignment; char bytes[CMSG_SPACE(sizeof(int))]; } received;
  struct msghdr input = {0}; input.msg_iov = &incoming; input.msg_iovlen = 1;
  input.msg_control = received.bytes; input.msg_controllen = sizeof(received.bytes);
  CHECK(recvmsg(pair[1], &input, MSG_CMSG_CLOEXEC) == 1 && marker == 'F' && !(input.msg_flags & MSG_CTRUNC));
  cmsg = CMSG_FIRSTHDR(&input); CHECK(cmsg && cmsg->cmsg_len == CMSG_LEN(sizeof(int)) && cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS);
  CHECK(CMSG_NXTHDR(&input, cmsg) == 0);
  int fd; memcpy(&fd, CMSG_DATA(cmsg), sizeof(fd));
  CHECK(fd >= 0 && fd != source[0] && (fcntl(fd, F_GETFD) & FD_CLOEXEC));
  CHECK(read(fd, &marker, 1) == 1 && marker == 'R' && close(fd) == 0);
  CHECK(sendmsg(pair[0], &output, 0) == 1);
  input.msg_controllen = sizeof(struct cmsghdr);
  CHECK(recvmsg(pair[1], &input, 0) == 1 && (input.msg_flags & MSG_CTRUNC) && !input.msg_controllen);
  cmsg = CMSG_FIRSTHDR(&output); cmsg->cmsg_len = 0; errno = 0;
  CHECK(sendmsg(pair[0], &output, 0) == -1 && errno == EINVAL);
  errno = 0; CHECK(recv(pair[1], &marker, 1, MSG_DONTWAIT) == -1 && errno == EAGAIN);
  CHECK(close(pair[0]) == 0 && close(pair[1]) == 0 && close(source[0]) == 0 && close(source[1]) == 0);
  return 0;
}

/* Optional public req_pq exchange only; no Telegram account/auth material.
 * Wire format: https://core.telegram.org/mtproto/auth_key and /mtproto-transports. */
int run_mtproto_pq(const char *host,int port,const unsigned char *request,int length,unsigned char *reply,int capacity) {
  if(!host||!request||!reply||port<1||port>65535||length<1||length>4096||capacity<41||capacity>4096)return -8;
  struct addrinfo hints={0},*addresses=0;hints.ai_family=AF_INET;hints.ai_socktype=SOCK_STREAM;hints.ai_flags=AI_NUMERICHOST;
  if(getaddrinfo(host,0,&hints,&addresses)!=0||!addresses)return -1;
  struct sockaddr_in address;memcpy(&address,addresses->ai_addr,sizeof(address));freeaddrinfo(addresses);address.sin_port=(uint16_t)((port>>8)|((port&255)<<8));
  int fd=socket(AF_INET,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0);if(fd<0)return -2;
  int result=-3;struct pollfd waiting={fd,POLLOUT,0};
  if(connect(fd,(struct sockaddr *)&address,sizeof(address))!=0&&errno!=EINPROGRESS)goto done;
  if(poll(&waiting,1,5000)!=1)goto done;
  int error=0;socklen_t size=sizeof(error);if(getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)!=0||error)goto done;
  int sent=0;while(sent<length){ssize_t n=send(fd,request+sent,(size_t)(length-sent),MSG_NOSIGNAL);if(n>0)sent+=(int)n;else if(n<0&&(errno==EAGAIN||errno==EINTR)){if(poll(&waiting,1,5000)!=1)goto done;}else goto done;}
  waiting.events=POLLIN;int received=0,wanted=1;
  while(received<wanted){if(poll(&waiting,1,5000)!=1){result=-4;goto done;}ssize_t n=recv(fd,reply+received,(size_t)(wanted-received),0);if(n<0&&(errno==EAGAIN||errno==EINTR))continue;if(n<=0){result=-5;goto done;}received+=(int)n;if(received==1){if(reply[0]>=127){result=-6;goto done;}wanted=1+reply[0]*4;if(wanted<41||wanted>capacity){result=-7;goto done;}}}
  result=received;
 done:close(fd);return result;
}
