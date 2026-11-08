// SPDX-License-Identifier: GPL-3.0-only
// Run through the ARM64 bridge with builtins disabled.
typedef __SIZE_TYPE__ size_t;
typedef unsigned long pthread_t;
typedef struct { unsigned flags; void* base; size_t size,guard; int policy,priority; char reserved[16]; } GuestAttr;
extern pthread_t pthread_self(void);
extern int pthread_getattr_np(pthread_t,GuestAttr*);
extern int pthread_kill(pthread_t,int);
extern int pthread_create(pthread_t*,const void*,void*(*)(void*),void*);
extern int pthread_join(pthread_t,void**);
extern void pthread_exit(void*) __attribute__((noreturn));
static void* exiting_worker(void* argument) { pthread_exit(argument); }
extern float expf(float);
extern size_t strlen(const char*);
extern void* AMediaFormat_new(void);
extern void AMediaFormat_setInt32(void*,const char*,int);
extern _Bool AMediaFormat_getInt32(void*,const char*,int*);
extern int AMediaFormat_delete(void*);
extern void* AMediaCodec_createDecoderByType(const char*);
int Java_shiraz_Test_answer(void* env,void* klass) {
 (void)env;(void)klass;
 char local=0;GuestAttr attributes;
 if(pthread_getattr_np(pthread_self(),&attributes))return -1;
 if((unsigned long)&local<(unsigned long)attributes.base ||
    (unsigned long)&local>=(unsigned long)attributes.base+attributes.size)return -2;
 if(pthread_kill(pthread_self(),0)||pthread_kill(pthread_self(),32)!=22)return -3;
 volatile float zero=0;
 if(expf(zero)!=1.0f||strlen("translation")!=11)return -4;
 void* format=AMediaFormat_new();if(!format)return -5;
 AMediaFormat_setInt32(format,"width",390);
 int width=0;int ok=AMediaFormat_getInt32(format,"width",&width)&&width==390;
 AMediaFormat_delete(format);
 if(!ok || AMediaCodec_createDecoderByType("video/avc")!=0)return -6;
 pthread_t worker;void* result=0;
 if(pthread_create(&worker,0,exiting_worker,&local)||pthread_join(worker,&result)||result!=&local)return -7;
 return 42;
}
