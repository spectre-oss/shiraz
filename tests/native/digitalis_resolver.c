// SPDX-License-Identifier: GPL-3.0-only
struct GuestAddrInfo {int flags,family,socktype,protocol;unsigned addrlen;char* canonname;void* addr;struct GuestAddrInfo* next;};
extern int getaddrinfo(const char*,const char*,const struct GuestAddrInfo*,struct GuestAddrInfo**);
extern void freeaddrinfo(struct GuestAddrInfo*);
extern const char* gai_strerror(int);
extern int strcmp(const char*,const char*);
int Java_shiraz_ResolverSmoke_run(void* env,void* clazz){
 (void)env;(void)clazz;
 struct GuestAddrInfo hints={14,2,1,0,0,0,0,0},*result=0;
 if(getaddrinfo("127.0.0.1","80",&hints,&result)||!result)return 1;
 unsigned char* address=result->addr;
 int ok=result->family==2&&result->socktype==1&&result->addrlen==16&&result->canonname&&!strcmp(result->canonname,"127.0.0.1")&&address&&address[0]==2&&address[2]==0&&address[3]==80&&address[4]==127&&address[7]==1;
 freeaddrinfo(result);result=0;
 hints.flags=4;
 if(getaddrinfo("not-a-numeric-address","80",&hints,&result)!=8||result)ok=0;
 if(!gai_strerror(8)||!strcmp(gai_strerror(8),gai_strerror(3)))ok=0;
 freeaddrinfo(0);
 return ok?42:2;
}
