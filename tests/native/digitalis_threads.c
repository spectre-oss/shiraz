// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
extern void* malloc(unsigned long);
extern void free(void*);
typedef unsigned long pthread_t;
extern int pthread_create(pthread_t*,const void*,void*(*)(void*),void*);
extern int pthread_join(pthread_t,void**);
extern int pthread_detach(pthread_t);
extern pthread_t pthread_self(void);
extern int pthread_setname_np(pthread_t,const char*);
extern int pthread_getname_np(pthread_t,char*,unsigned long);
extern int pthread_setschedparam(pthread_t,int,const int*);
extern int pthread_getschedparam(pthread_t,int*,int*);
extern int pthread_setschedprio(pthread_t,int);
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
extern int fork(void);
extern int waitpid(int,int*,int);
extern int kill(int,int);
extern long ptrace(int,...);
extern void _exit(int) __attribute__((noreturn));
// Exercise a real traced exit and its kernel-provided event message. Android
// ARM64 and the Linux x86_64 host share these ptrace constants and LP64 payload.
static int ptrace_event_test(void) {
  int child=fork();
  if(child<0)return 0;
  if(!child) {
    if(ptrace(0,0,0,0))_exit(99); // PTRACE_TRACEME
    kill(getpid(),19); // SIGSTOP
    _exit(42);
  }
  int status=0,ok=0;
  if(waitpid(child,&status,0)!=child || (status&255)!=127)goto cleanup;
  if(ptrace(0x4200,child,0,(void*)64))goto cleanup; // TRACEEXIT
  if(ptrace(7,child,0,0))goto cleanup; // CONT
  if(waitpid(child,&status,0)!=child || (status>>16)!=6)goto cleanup;
  struct { unsigned long value,guard; } message={~0UL,0x123456789abcdef0UL};
  ok=ptrace(0x4201,child,0,&message.value)==0 && message.value==(42UL<<8)
      && message.guard==0x123456789abcdef0UL;
  // The bridge must preserve the kernel's rejection of an invalid output.
  if(ptrace(0x4201,child,0,0)!=-1)ok=0;
  if(ptrace(7,child,0,0))goto cleanup;
  if(waitpid(child,&status,0)!=child || status!=(42<<8))return 0;
  return ok;
cleanup:
  kill(child,9);
  waitpid(child,&status,0);
  return 0;
}
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
struct Work { int parent; int result; int destroyed; int notify; int self_detach; };
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
  if(work->self_detach && pthread_detach(pthread_self()))return (void*)3;
  local=42;
  if(pthread_setspecific(key,work) || pthread_getspecific(key)!=work) return (void*)2;
  work->result=local;
  return work;
}
typedef unsigned long JumpBuffer[32];
extern int setjmp(JumpBuffer) __attribute__((returns_twice));
extern int _setjmp(JumpBuffer) __attribute__((returns_twice));
extern int sigsetjmp(JumpBuffer,int) __attribute__((returns_twice));
extern void longjmp(JumpBuffer,int) __attribute__((noreturn));
extern void _longjmp(JumpBuffer,int) __attribute__((noreturn));
extern void siglongjmp(JumpBuffer,int) __attribute__((noreturn));
extern int sigprocmask(int,const unsigned long*,unsigned long*);
static int jump_test(void) {
  JumpBuffer a,b; volatile int phase=0;
  int value=setjmp(a);
  if(!phase){phase=1;longjmp(a,0);}
  if(value!=1 || phase!=1)return -81;
  value=_setjmp(b);
  if(phase==1){phase=2;_longjmp(b,42);}
  if(value!=42)return -82;
  unsigned long original=0,blocked=1ul<<11,mask=0;
  volatile unsigned long expected=0;
  if(sigprocmask(0,0,&original))return -83;
  value=sigsetjmp(a,1);
  if(phase==2){phase=3;sigprocmask(0,&blocked,0);siglongjmp(a,-7);}
  sigprocmask(0,0,&mask);
  if(value!=-7 || mask!=original){sigprocmask(2,&original,0);return -84;}
  value=sigsetjmp(b,0);
  if(phase==3){phase=4;sigprocmask(0,&blocked,0);sigprocmask(0,0,&mask);expected=mask;siglongjmp(b,9);}
  sigprocmask(0,0,&mask);sigprocmask(2,&original,0);
  return value!=9?-851:((mask!=expected || !(mask&blocked))?-852:1);
}
struct Naming { int ready,proceed; pthread_t guest; int ok; };
static void* naming_worker(void* argument) {
  struct Naming* n=argument; char buffer[16],signal=1;
  n->guest=pthread_self();
  void** tls;
  __asm__("mrs %0, tpidr_el0" : "=r"(tls));
  int policy=-1,priority=-1,normal=0;
  n->ok=n->guest==(pthread_t)tls[1] && !pthread_setname_np(n->guest,"guest-name") &&
      !pthread_getname_np(n->guest,buffer,sizeof buffer) && !strcmp(buffer,"guest-name") &&
      pthread_setname_np(n->guest,"this-name-is-far-too-long")==34 &&
      !pthread_setschedparam(n->guest,0,&normal) &&
      !pthread_getschedparam(n->guest,&policy,&priority) && policy==0 && priority==0 &&
      pthread_setschedparam(n->guest,-1,&normal)==22 && pthread_setschedprio(n->guest,1)==22;
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
    // A pthread identifier must agree across create, self, and TLS consumers.
    ok=ok && thread==n.guest;
    ok=ok && !pthread_getname_np(thread,buffer,sizeof buffer) && !strcmp(buffer,"guest-name");
    ok=ok && !pthread_setname_np(n.guest,"parent-name");
    int policy=-1,priority=-1;
    ok=ok && !pthread_getschedparam(thread,&policy,&priority) && policy==0 && priority==0 &&
        !pthread_setschedprio(thread,0);
    write(proceed[1],"x",1); pthread_join(thread,0);
    ok=ok && n.ok && pthread_getname_np(n.guest,buffer,sizeof buffer)==3 &&
        pthread_getschedparam(n.guest,&policy,&priority)==3;
  }
  close(ready[0]);close(ready[1]);close(proceed[0]);close(proceed[1]);
  return ok;
}
struct SharedVmWork {
  unsigned references;
  int proceed;
  JavaVM* vm;
  unsigned long marker;
  int* destroyed;
};
static void release_vm_work(struct SharedVmWork* work) {
  if(__atomic_sub_fetch(&work->references,1,__ATOMIC_ACQ_REL)==0) {
    ++*work->destroyed;
    free(work);
  }
}
static void* vm_worker(void* argument) {
  struct SharedVmWork* work=argument;
  char signal;
  int ok=read(work->proceed,&signal,1)==1 && work->marker==0x123456789abcdef0UL;
  JNIEnv* env=0;
  JavaVM* vm=work->vm;
  JavaVMAttachArgs args={JNI_VERSION_1_2,"guest-vm-worker",0};
  if(ok && (*vm)->AttachCurrentThread(vm,&env,&args)==JNI_OK) {
    JNIEnv* same=0;
    JavaVM* returned=0;
    ok=(*vm)->GetEnv(vm,(void**)&same,JNI_VERSION_1_2)==JNI_OK && same==env &&
        (*env)->GetJavaVM(env,&returned)==JNI_OK && returned==vm;
    jstring value=(*env)->NewStringUTF(env,"thread handoff");
    ok=ok && value && (*env)->GetStringUTFLength(env,value)==14;
    if(value)(*env)->DeleteLocalRef(env,value);
    ok=(*vm)->DetachCurrentThread(vm)==JNI_OK && ok;
  } else ok=0;
  release_vm_work(work);
  return (void*)(unsigned long)(ok?42:1);
}
// Keep the worker's ownership alive while the parent releases its reference
// and churns allocations. Exercise the real JavaVM bridge from a guest thread.
static int vm_handoff_test(JNIEnv* env) {
  JavaVM* vm=0;
  if((*env)->GetJavaVM(env,&vm)!=JNI_OK || !vm)return 0;
  for(int iteration=0;iteration<8;++iteration) {
    int fds[2],destroyed=0;
    if(pipe(fds))return 0;
    struct SharedVmWork* work=malloc(sizeof(*work));
    if(!work){close(fds[0]);close(fds[1]);return 0;}
    *work=(struct SharedVmWork){2,fds[0],vm,0x123456789abcdef0UL,&destroyed};
    pthread_t thread;
    if(pthread_create(&thread,0,vm_worker,work)) {
      free(work);close(fds[0]);close(fds[1]);return 0;
    }
    release_vm_work(work);
    for(int i=0;i<32;++i) {
      struct SharedVmWork* other=malloc(sizeof(*other));
      if(other){other->marker=0;free(other);}
    }
    int ok=write(fds[1],"x",1)==1;
    close(fds[1]);
    void* result=0;
    ok=pthread_join(thread,&result)==0 && ok;
    close(fds[0]);
    if(!ok || result!=(void*)42 || destroyed!=1)return 0;
  }
  return 1;
}
int Java_shiraz_ThreadSmoke_run(JNIEnv* env,jclass klass) {
  (void)klass;
  if(!ptrace_event_test())return -91;
  if(!vm_handoff_test(env))return -90;
  int jumps=jump_test();if(jumps!=1)return jumps;
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
  for(int mode=0;mode<3 && ok;++mode) {
  int fds[2];
  if(ok && pipe(fds)==0) {
    struct Work detached={gettid(),0,0,fds[1],mode==2}; pthread_t thread;
    if(pthread_attr_setdetachstate(&attr,mode==1) || pthread_attr_getdetachstate(&attr,&flag) || flag!=(mode==1)) ok=0;
    if(!ok || pthread_create(&thread,&attr,worker,&detached)) ok=0;
    else {
      if(!mode && pthread_detach(thread)) { pthread_join(thread,0); ok=0; }
      char done=0;
      if(read(fds[0],&done,1)!=1 || done!=42 || detached.result!=42) ok=0;
    }
    close(fds[0]);close(fds[1]);
  } else ok=0;
  }
  pthread_attr_destroy(&attr);
  pthread_key_delete(key);
  return ok&&!local?42:-2;
}
