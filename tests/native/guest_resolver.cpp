// SPDX-License-Identifier: GPL-3.0-only
#include "guest_resolver.h"
#include <cassert>
int main(){
 ShirazGuestAddrInfo hints{};hints.flags=2|4|8;hints.family=AF_INET;hints.socktype=SOCK_STREAM;
 for(int i=0;i<100;i++){
  ShirazGuestAddrInfo* result=nullptr;
  assert(ShirazGuestGetAddrInfo("127.0.0.1","80",&hints,&result)==0&&result);
  assert(result->family==AF_INET&&result->socktype==SOCK_STREAM&&result->addrlen==sizeof(sockaddr_in));
  auto* addr=reinterpret_cast<sockaddr_in*>(result->addr);
  assert(addr->sin_family==AF_INET&&ntohs(addr->sin_port)==80&&ntohl(addr->sin_addr.s_addr)==0x7f000001);
  assert(result->canonname&&std::strcmp(result->canonname,"127.0.0.1")==0);
  ShirazGuestFreeAddrInfo(result);
 }
 assert(hints.flags==(2|4|8)&&hints.addr==nullptr);
 hints.family=AF_INET6;ShirazGuestAddrInfo* result=nullptr;
 assert(ShirazGuestGetAddrInfo("::1","443",&hints,&result)==0&&result->addrlen==sizeof(sockaddr_in6));
 assert(ntohs(reinterpret_cast<sockaddr_in6*>(result->addr)->sin6_port)==443);ShirazGuestFreeAddrInfo(result);
 result=reinterpret_cast<ShirazGuestAddrInfo*>(1);hints.flags=0x10;
 assert(ShirazGuestGetAddrInfo("127.0.0.1","80",&hints,&result)==3&&!result);
 hints.flags=4;hints.family=AF_INET;
 assert(ShirazGuestGetAddrInfo("not-a-numeric-address","80",&hints,&result)==8&&!result);
 assert(std::strcmp(ShirazGuestGaiStrerror(8),ShirazGuestGaiStrerror(3))!=0);
 ShirazGuestFreeAddrInfo(nullptr);
}
