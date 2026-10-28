// SPDX-License-Identifier: GPL-3.0-only
// ARM64 ABI regression: real EGL pbuffer and GLES1 pixel readback, no APK.
typedef void* Handle;
extern Handle eglGetDisplay(Handle);
extern unsigned eglInitialize(Handle,int*,int*);
extern unsigned eglChooseConfig(Handle,const int*,Handle*,int,int*);
extern Handle eglCreatePbufferSurface(Handle,Handle,const int*);
extern Handle eglCreateContext(Handle,Handle,Handle,const int*);
extern unsigned eglMakeCurrent(Handle,Handle,Handle,Handle);
extern unsigned eglDestroySurface(Handle,Handle);
extern unsigned eglDestroyContext(Handle,Handle);
extern unsigned eglTerminate(Handle);
extern int eglGetError(void);
extern void glClearColor(float,float,float,float);
extern void glClear(unsigned);
extern void glReadPixels(int,int,int,int,unsigned,unsigned,void*);
extern unsigned glGetError(void);
int Java_shiraz_Test_answer(void* env,void* klass) {
    (void)env; (void)klass;
    Handle display=eglGetDisplay(0),config=0;
    int count=0;
    const int attributes[]={0x3033,1,0x3040,1,0x3024,8,0x3023,8,0x3022,8,0x3038};
    const int size[]={0x3057,8,0x3056,8,0x3038};
    const int version[]={0x3098,1,0x3038};
    if (!eglInitialize(display,0,0)) return -1;
    if (!eglChooseConfig(display,attributes,&config,1,&count)||count!=1) return -2;
    Handle surface=eglCreatePbufferSurface(display,config,size);
    if (!surface) return -100000-eglGetError();
    Handle context=eglCreateContext(display,config,0,version);
    if (!context) return -200000-eglGetError();
    if (!eglMakeCurrent(display,surface,surface,context)) return -300000-eglGetError();
    unsigned char pixel[4]={0};
    glClearColor(1,0,0,1); glClear(0x4000);
    glReadPixels(0,0,1,1,0x1908,0x1401,pixel);
    int result=glGetError()==0 && pixel[0]==255 && pixel[1]==0 && pixel[2]==0 ? 42 : -4;
    eglMakeCurrent(display,0,0,0);
    eglDestroyContext(display,context); eglDestroySurface(display,surface); eglTerminate(display);
    return result;
}
