// SPDX-License-Identifier: GPL-3.0-only
#include <pthread.h>
#include <unistd.h>
#include <sys/wait.h>
#include <cassert>
#include <cerrno>
#include <cstring>
#include <initializer_list>
namespace berberis {
void* ShirazGuestPthread();
int ShirazGuestSetThreadName(pthread_t,const char*);
int ShirazGuestGetThreadName(pthread_t,char*,size_t);
}
using namespace berberis;
struct Work { int ready,proceed; pthread_t guest; };
static void* worker(void* p){
 auto* w=static_cast<Work*>(p);
 w->guest=reinterpret_cast<pthread_t>(ShirazGuestPthread());
 assert(ShirazGuestSetThreadName(w->guest,"guest-worker")==0);
 assert(write(w->ready,"x",1)==1);
 char byte; assert(read(w->proceed,&byte,1)==1);
 return nullptr;
}
int main(){
 auto self=reinterpret_cast<pthread_t>(ShirazGuestPthread());
 int ready[2],proceed[2]; assert(pipe(ready)==0&&pipe(proceed)==0);
 Work work{ready[1],proceed[0],0}; pthread_t thread;
 assert(pthread_create(&thread,nullptr,worker,&work)==0);
 char byte,name[16]; assert(read(ready[0],&byte,1)==1);
 assert(ShirazGuestGetThreadName(thread,name,sizeof name)==0&&!strcmp(name,"guest-worker"));
 assert(ShirazGuestSetThreadName(work.guest,"parent-name")==0);
 assert(ShirazGuestGetThreadName(thread,name,sizeof name)==0&&!strcmp(name,"parent-name"));
 assert(ShirazGuestSetThreadName(self,"name-too-long-for-linux")==ERANGE);
 assert(ShirazGuestGetThreadName(self,name,1)==ERANGE);
 pid_t child=fork(); assert(child>=0);
 if(!child){
  bool ok=ShirazGuestGetThreadName(work.guest,name,sizeof name)==ESRCH &&
      ShirazGuestSetThreadName(self,"fork-child")==0;
  _exit(ok?0:1);
 }
 int status; assert(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);
 assert(write(proceed[1],"x",1)==1); assert(pthread_join(thread,nullptr)==0);
 assert(ShirazGuestGetThreadName(work.guest,name,sizeof name)==ESRCH);
 assert(ShirazGuestGetThreadName(0,name,sizeof name)==ESRCH);
 for(int fd:{ready[0],ready[1],proceed[0],proceed[1]})close(fd);
}
