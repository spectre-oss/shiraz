// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <netdb.h>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>

// LP64 Bionic netdb.h: canonname precedes addr, unlike glibc.
struct ShirazGuestAddrInfo {
 int flags,family,socktype,protocol;
 uint32_t addrlen;
 char* canonname;
 sockaddr* addr;
 ShirazGuestAddrInfo* next;
};
static_assert(sizeof(ShirazGuestAddrInfo)==48);
static_assert(offsetof(ShirazGuestAddrInfo,canonname)==24);
static_assert(offsetof(ShirazGuestAddrInfo,addr)==32);
inline int ShirazGuestGaiError(int error){
 switch(error){
 case 0:return 0;
 case EAI_AGAIN:return 2;
 case EAI_BADFLAGS:return 3;
 case EAI_FAIL:return 4;
 case EAI_FAMILY:return 5;
 case EAI_MEMORY:return 6;
 case EAI_NONAME:return 8;
 case EAI_SERVICE:return 9;
 case EAI_SOCKTYPE:return 10;
 case EAI_SYSTEM:return 11;
 case EAI_OVERFLOW:return 14;
#ifdef EAI_ADDRFAMILY
 case EAI_ADDRFAMILY:return 1;
#endif
#ifdef EAI_NODATA
 case EAI_NODATA:return 7;
#endif
 default:return 4;
 }
}
inline void ShirazGuestFreeAddrInfo(ShirazGuestAddrInfo* entry){
 while(entry){auto* next=entry->next;std::free(entry->canonname);std::free(entry->addr);std::free(entry);entry=next;}
}
inline int ShirazGuestGetAddrInfo(const char* node,const char* service,const ShirazGuestAddrInfo* hints,ShirazGuestAddrInfo** result){
 if(!result)return 4;
 *result=nullptr;
 addrinfo hostHints{};
 if(hints){
  constexpr unsigned known=0xf|0xf00;
  if(static_cast<unsigned>(hints->flags)&~known)return 3;
  hostHints.ai_family=hints->family;hostHints.ai_socktype=hints->socktype;hostHints.ai_protocol=hints->protocol;
  if(hints->flags&1)hostHints.ai_flags|=AI_PASSIVE;
  if(hints->flags&2)hostHints.ai_flags|=AI_CANONNAME;
  if(hints->flags&4)hostHints.ai_flags|=AI_NUMERICHOST;
  if(hints->flags&8)hostHints.ai_flags|=AI_NUMERICSERV;
  if(hints->flags&0x100)hostHints.ai_flags|=AI_ALL;
  if(hints->flags&0x400)hostHints.ai_flags|=AI_ADDRCONFIG;
  if(hints->flags&(0x200|0x800))hostHints.ai_flags|=AI_V4MAPPED;
 }
 addrinfo* host=nullptr;
 int error=getaddrinfo(node,service,&hostHints,&host);
 if(error)return ShirazGuestGaiError(error);
 ShirazGuestAddrInfo* head=nullptr;ShirazGuestAddrInfo** tail=&head;
 for(auto* item=host;item;item=item->ai_next){
  auto* copy=static_cast<ShirazGuestAddrInfo*>(std::calloc(1,sizeof(ShirazGuestAddrInfo)));
  if(!copy){error=6;break;}
  *tail=copy;tail=&copy->next;
  copy->flags=hints?hints->flags:0;copy->family=item->ai_family;copy->socktype=item->ai_socktype;copy->protocol=item->ai_protocol;copy->addrlen=item->ai_addrlen;
  if(item->ai_addrlen){
   copy->addr=static_cast<sockaddr*>(std::malloc(item->ai_addrlen));
   if(!copy->addr){error=6;break;}
   std::memcpy(copy->addr,item->ai_addr,item->ai_addrlen);
  }
  if(item->ai_canonname){
   size_t size=std::strlen(item->ai_canonname)+1;
   copy->canonname=static_cast<char*>(std::malloc(size));
   if(!copy->canonname){error=6;break;}
   std::memcpy(copy->canonname,item->ai_canonname,size);
  }
 }
 freeaddrinfo(host);
 if(error){ShirazGuestFreeAddrInfo(head);return error;}
 *result=head;return 0;
}
inline const char* ShirazGuestGaiStrerror(int error){
 static const char* const messages[]={"Success","Address family not supported","Temporary name resolution failure","Invalid resolver flags","Name resolution failed","Unsupported address family","Out of memory","No address data","Name or service not known","Service not supported","Socket type not supported","System error","Invalid resolver hints","Unknown protocol","Resolver buffer overflow"};
 return error>=0&&error<15?messages[error]:"Unknown resolver error";
}
