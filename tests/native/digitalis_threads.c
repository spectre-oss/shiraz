// SPDX-License-Identifier: GPL-3.0-only
typedef unsigned long pthread_t;
extern int pthread_create(pthread_t*,const void*,void*(*)(void*),void*);
extern int pthread_join(pthread_t,void**);
extern int pthread_detach(pthread_t);
extern pthread_t pthread_self(void);
extern int pthread_setname_np(pthread_t,const char*);
extern int pthread_getname_np(pthread_t,char*,unsigned long);
extern int strcmp(const char*,const char*);
extern int pthread_key_create(int*,void(*)(void*));
extern int pthread_key_delete(int);
extern int pthread_setspecific(int,const void*);
extern void* pthread_getspecific(int);
extern int gettid(void);
extern int getpid(void);
extern int pipe(int*);
extern long read(int,void*,unsigned long);
extern long write(int,const void*,unsigned long);
extern int close(int);
typedef struct { unsigned flags; void* base; unsigned long size,guard; int policy,priority; char reserved[16]; } GuestAttr;
extern int pthread_attr_init(GuestAttr*);
extern int pthread_attr_destroy(GuestAttr*);
extern int pthread_attr_setstacksize(GuestAttr*,unsigned long);
extern int pthread_attr_getstacksize(const GuestAttr*,unsigned long*);
extern int pthread_attr_setdetachstate(GuestAttr*,int);
extern int pthread_attr_getdetachstate(const GuestAttr*,int*);
extern int pthread_attr_setinheritsched(GuestAttr*,int);
extern int pthread_attr_getinheritsched(const GuestAttr*,int*);
static int key;
static _Thread_local int local;
struct Work { int parent; int result; int destroyed; int notify; };
static void destroy(void* value) {
  struct Work* work=value; work->destroyed=1;
  if(work->notify>=0) { char done=42; write(work->notify,&done,1); }
}
static void* worker(void* argument) {
  struct Work* work=argument;
  void** tls;
  __asm__("mrs %0, tpidr_el0" : "=r"(tls));
  int* record=(int*)tls[1];
  if(local || gettid()==work->parent || record[4]!=gettid() || record[5]!=getpid()) return (void*)1;
  local=42;
  if(pthread_setspecific(key,work) || pthread_getspecific(key)!=work) return (void*)2;
  work->result=local;
  return work;
}
struct Naming { int ready,proceed; pthread_t guest; int ok; };
static void* naming_worker(void* argument) {
  struct Naming* n=argument; char buffer[16],signal=1;
  n->guest=pthread_self();
  n->ok=!pthread_setname_np(n->guest,"guest-name") &&
      !pthread_getname_np(n->guest,buffer,sizeof buffer) && !strcmp(buffer,"guest-name") &&
      pthread_setname_np(n->guest,"this-name-is-far-too-long")==34;
  write(n->ready,&signal,1);
  read(n->proceed,&signal,1);
  n->ok=n->ok && !pthread_getname_np(n->guest,buffer,sizeof buffer) && !strcmp(buffer,"parent-name");
  return 0;
}
static int naming_test(void) {
  int ready[2],proceed[2]; if(pipe(ready)||pipe(proceed))return 0;
  struct Naming n={ready[1],proceed[0],0,0}; pthread_t thread; char signal,buffer[16];
  int ok=pthread_create(&thread,0,naming_worker,&n)==0;
  if(ok){
    ok=read(ready[0],&signal,1)==1;
    ok=ok && !pthread_getname_np(thread,buffer,sizeof buffer) && !strcmp(buffer,"guest-name");
    ok=ok && !pthread_setname_np(n.guest,"parent-name");
    write(proceed[1],"x",1); pthread_join(thread,0);
    ok=ok && n.ok && pthread_getname_np(n.guest,buffer,sizeof buffer)==3;
  }
  close(ready[0]);close(ready[1]);close(proceed[0]);close(proceed[1]);
  return ok;
}
int Java_shiraz_ThreadSmoke_run(void* env,void* klass) {
  (void)env;(void)klass;
  if(!naming_test()) return -7;
  struct Work work[4]={0}; pthread_t threads[4];
  if(pthread_key_create(&key,destroy)) return -1;
  GuestAttr attr;
  unsigned long stack=0; int flag=-1;
  if(pthread_attr_init(&attr) || pthread_attr_getstacksize(&attr,&stack) || stack<16384) return -3;
  if(pthread_attr_setstacksize(&attr,1)!=22 || pthread_attr_setstacksize(&attr,2*1024*1024)) return -4;
  if(pthread_attr_setinheritsched(&attr,0) || pthread_attr_getinheritsched(&attr,&flag) || flag!=0) return -5;
  if(pthread_attr_setinheritsched(&attr,1) || pthread_attr_getinheritsched(&attr,&flag) || flag!=1) return -6;
  int created=0;
  for(int i=0;i<4;++i) {
    work[i].parent=gettid();
    work[i].notify=-1;
    if(pthread_create(&threads[i],&attr,worker,&work[i])) break;
    ++created;
  }
  int ok=created==4;
  for(int i=0;i<created;++i) {
    void* result=0;
    if(pthread_join(threads[i],&result) || result!=&work[i] || work[i].result!=42 || !work[i].destroyed) ok=0;
  }
  for(int mode=0;mode<2 && ok;++mode) {
  int fds[2];
  if(ok && pipe(fds)==0) {
    struct Work detached={gettid(),0,0,fds[1]}; pthread_t thread;
    if(pthread_attr_setdetachstate(&attr,mode) || pthread_attr_getdetachstate(&attr,&flag) || flag!=mode) ok=0;
    if(!ok || pthread_create(&thread,&attr,worker,&detached)) ok=0;
    else {
      if(!mode && pthread_detach(thread)) { pthread_join(thread,0); ok=0; }
      char done=0;
      if(read(fds[0],&done,1)!=1 || done!=42) ok=0;
    }
    close(fds[0]);close(fds[1]);
  } else ok=0;
  }
  pthread_attr_destroy(&attr);
  pthread_key_delete(key);
  return ok&&!local?42:-2;
}
