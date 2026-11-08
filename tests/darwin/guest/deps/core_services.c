// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <errno.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/resource.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <unistd.h>
#include <time.h>
#include <sys/prctl.h>
#include <assert.h>
#include <fcntl.h>
extern char **environ;
extern char *__strchr_chk(const char *,int,size_t);
extern char *__strrchr_chk(const char *,int,size_t);
extern char *__strncat_chk(char *,const char *,size_t,size_t);
_Static_assert(sizeof(struct utsname)==390,"bionic uname layout");
_Static_assert(sizeof(struct timeval)==16 && sizeof(struct rlimit)==16,"bionic LP64 layouts");
#define CHECK(x) do{if(!(x))return __LINE__;}while(0)
static int compare(const void *a,const void *b){int x=*(const int *)a,y=*(const int *)b;return (x>y)-(x<y);}
int run_core_services(void){
 CHECK(!strcasecmp("aBc","AbC") && strcasecmp("A","b")<0 && !strncasecmp("abX","ABY",2));
 const char text[]="abca";CHECK(__strchr_chk(text,'a',5)==text && __strrchr_chk(text,'a',5)==text+3 && __strrchr_chk(text,0,5)==text+4);
 CHECK(!__strchr_chk(text,'z',5));char output[8]="ab";CHECK(__strncat_chk(output,"cdef",2,8)==output && !strcmp(output,"abcd"));
 int values[]={1,4,7,10,13},key=7;CHECK(bsearch(&key,values,5,sizeof(int),compare)==values+2);key=8;CHECK(!bsearch(&key,values,5,sizeof(int),compare));
 struct {uint64_t before;struct utsname value;uint64_t after;} name={.before=17,.after=19};
 CHECK(!uname(&name.value) && name.before==17 && name.after==19 && name.value.sysname[0] && name.value.machine[0]);
 struct timeval tv;CHECK(!gettimeofday(&tv,0) && tv.tv_sec>1700000000 && tv.tv_usec>=0 && tv.tv_usec<1000000);
 struct rlimit limit;errno=73;CHECK(!getrlimit(RLIMIT_NOFILE,&limit) && limit.rlim_cur>0 && limit.rlim_max>=limit.rlim_cur && errno==73);
 errno=0;CHECK(getrlimit(999,&limit)==-1 && errno==EINVAL);
 unsigned char random[64]={0};arc4random_buf(random,sizeof(random));unsigned nonzero=0;for(int i=0;i<64;i++)nonzero|=random[i];CHECK(nonzero);
 double (*volatile scale)(double,int)=ldexp;CHECK(scale(1.5,4)==24.0);
 CHECK(!setenv("SHIRAZ_M5_CORE_TEST","one",1));int seen=0;for(char **p=environ;*p;++p)if(!strcmp(*p,"SHIRAZ_M5_CORE_TEST=one"))seen=1;CHECK(seen);
 CHECK(!setenv("SHIRAZ_M5_CORE_TEST","two",0));CHECK(!strcmp(getenv("SHIRAZ_M5_CORE_TEST"),"one"));
 CHECK(!unsetenv("SHIRAZ_M5_CORE_TEST") && !getenv("SHIRAZ_M5_CORE_TEST"));
 errno=0;CHECK(setenv("bad=name","x",1)==-1 && errno==EINVAL);
 tzset();CHECK(tzname[0] && tzname[1] && timezone>-86400 && timezone<86400);
 char thread_name[16];CHECK(!prctl(PR_SET_NAME,"m5-core-test") && !prctl(PR_GET_NAME,thread_name) && !strcmp(thread_name,"m5-core-test"));
 errno=0;CHECK(prctl(PR_SET_DUMPABLE,0)==-1 && errno==ENOSYS);
 int pipe_fds[2];CHECK(!pipe2(pipe_fds,O_NONBLOCK|O_CLOEXEC));CHECK((fcntl(pipe_fds[0],F_GETFD)&FD_CLOEXEC) && (fcntl(pipe_fds[1],F_GETFL)&O_NONBLOCK));
 CHECK(write(pipe_fds[1],"x",1)==1);char byte;CHECK(read(pipe_fds[0],&byte,1)==1 && byte=='x');CHECK(!close(pipe_fds[0]) && !close(pipe_fds[1]));
 CHECK(!usleep(1) && !sleep(0));return 0;
}
int fail_core_service(int mode){char bad[3]={'a','b','c'},out[3]="ab";if(mode==3)__assert2("fixture",123,"fail_core_service","expected assertion");if(mode==0)return __strchr_chk(bad,'z',3)!=0;if(mode==1)return __strrchr_chk(bad,'a',3)!=0;return __strncat_chk(out,"x",1,3)!=0;}
