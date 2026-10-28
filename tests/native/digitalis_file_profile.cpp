// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <cerrno>
#include <initializer_list>
#include <cstdlib>
#include <cstring>
namespace berberis {long ShirazGuestProfilePath(long,long); void ShirazTraceGuestFile(long,long,long,long);}
int main(){
 setenv("SHIRAZ_TRACE_GUEST_FILES","1",1);
 errno=ENOENT; berberis::ShirazTraceGuestFile(43,1,0,-1); assert(errno==ENOENT);
 const char* fixture="/fixture/path";
 errno=EACCES; berberis::ShirazTraceGuestFile(56,0,reinterpret_cast<long>(fixture),-1); assert(errno==EACCES);
 unsetenv("SHIRAZ_TRACE_GUEST_FILES");
 const char* path="/sys/fs/selinux/enforce";long original=reinterpret_cast<long>(path);
 unsetenv("SHIRAZ_GUEST_SELINUX_ENFORCE_FILE");assert(berberis::ShirazGuestProfilePath(79,original)==original);
 setenv("SHIRAZ_GUEST_SELINUX_ENFORCE_FILE","/private/fixture",1);
 for(long nr:{48L,56L,79L,439L})assert(!strcmp(reinterpret_cast<const char*>(berberis::ShirazGuestProfilePath(nr,original)),"/private/fixture"));
 assert(berberis::ShirazGuestProfilePath(1,original)==original);
 const char* other="/sys/fs/selinux/enforce-other";assert(berberis::ShirazGuestProfilePath(79,reinterpret_cast<long>(other))==reinterpret_cast<long>(other));
 assert(berberis::ShirazGuestProfilePath(79,0)==0);
 setenv("SHIRAZ_GUEST_SELINUX_ENFORCE_FILE","relative",1);assert(berberis::ShirazGuestProfilePath(79,original)==original);
}
