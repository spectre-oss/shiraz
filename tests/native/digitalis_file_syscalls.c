// SPDX-License-Identifier: GPL-3.0-only
static long call(long nr,long a,long b,long c,long d){register long x8 __asm__("x8")=nr;register long x0 __asm__("x0")=a;register long x1 __asm__("x1")=b;register long x2 __asm__("x2")=c;register long x3 __asm__("x3")=d;__asm__ volatile("svc #0":"+r"(x0):"r"(x8),"r"(x1),"r"(x2),"r"(x3):"memory");return x0;}
int Java_shiraz_Test_answer(void* env,void* klass){
 (void)env;(void)klass;unsigned char stat[128]={0};const char* path="/sys/fs/selinux/enforce";
 if(call(79,-100,(long)path,(long)stat,0)!=0)return -1;
 unsigned mode=*(unsigned*)(stat+16);if((mode&0170000)!=0100000)return -2;
 if(*(long*)(stat+48)!=2)return -3;
 if(*(long*)(stat+104)!=1234567890 || *(long*)(stat+112)!=0)return -6;
 long fd=call(56,-100,(long)path,0,0);if(fd<0)return -4;
 char c=0;long count=call(63,fd,(long)&c,1,0);call(57,fd,0,0,0);
 if(count!=1||c!='1')return -5;return 42;
}
