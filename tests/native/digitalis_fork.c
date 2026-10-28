// SPDX-License-Identifier: GPL-3.0-only
extern int fork(void);
extern int getpid(void);
extern int gettid(void);
extern int waitpid(int, int*, int);
extern void _exit(int);
int Java_shiraz_Test_answer(void* env, void* klass) {
 (void)env; (void)klass;
 int parent=getpid(), child=fork();
 if(child<0)return -1;
 if(!child) {
  void** tls;
  __asm__("mrs %0, tpidr_el0" : "=r"(tls));
  int* record=(int*)tls[1];
  _exit(getpid()!=parent && getpid()==gettid() && record[4]==gettid() && record[5]==getpid() ? 42 : 3);
 }
 int status=0;
 if(waitpid(child,&status,0)!=child)return -2;
 return status==(42<<8) && getpid()==parent ? 42 : -3;
}
