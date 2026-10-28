// SPDX-License-Identifier: GPL-3.0-only
#include <EGL/egl.h>
#include <cstring>
#include <dlfcn.h>
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
namespace berberis {
static void* lookup(const char* name);
#define ENTRY(name) {#name, GetTrampolineFunc<decltype(name)>(), nullptr}
static const KnownTrampoline functions[] = {
 ENTRY(eglBindAPI), ENTRY(eglBindTexImage), ENTRY(eglChooseConfig),
 ENTRY(eglCopyBuffers), ENTRY(eglCreateContext), ENTRY(eglCreatePbufferSurface),
 ENTRY(eglCreatePixmapSurface), ENTRY(eglCreateWindowSurface), ENTRY(eglDestroyContext),
 ENTRY(eglDestroySurface), ENTRY(eglGetConfigAttrib), ENTRY(eglGetConfigs),
 ENTRY(eglGetCurrentContext), ENTRY(eglGetCurrentDisplay), ENTRY(eglGetCurrentSurface),
 ENTRY(eglGetDisplay), ENTRY(eglGetError), ENTRY(eglInitialize), ENTRY(eglMakeCurrent),
 ENTRY(eglQueryAPI), ENTRY(eglQueryContext), ENTRY(eglQueryString), ENTRY(eglQuerySurface),
 ENTRY(eglReleaseTexImage), ENTRY(eglReleaseThread), ENTRY(eglSurfaceAttrib),
 ENTRY(eglSwapBuffers), ENTRY(eglSwapInterval), ENTRY(eglTerminate),
 ENTRY(eglWaitClient), ENTRY(eglWaitGL), ENTRY(eglWaitNative),
 {"eglGetProcAddress",GetTrampolineFunc<void*(const char*)>(),reinterpret_cast<void*>(lookup)}
};
#undef ENTRY
static void* lookup(const char* name){
 if(!name)return nullptr;
 static void* library=dlopen("libEGL_shiraz.so.1",RTLD_NOW|RTLD_LOCAL);
 if(!library)return nullptr;
 for(const auto& entry:functions)if(strcmp(name,entry.name)==0){
  void* symbol=entry.thunk?entry.thunk:dlsym(library,name);
  if(symbol)WrapHostFunctionImpl(reinterpret_cast<HostCode>(symbol),entry.marshal_and_call,entry.name);
  return symbol;
 }
 static void* proxy=dlopen("libberberis_proxy_libGLESv1_CM.so",RTLD_NOW|RTLD_LOCAL);
 using Lookup=void*(*)(const char*);
 static Lookup gles=proxy?reinterpret_cast<Lookup>(dlsym(proxy,"ShirazLookupGuestGles1")):nullptr;
 return gles?gles(name):nullptr;
}
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder){
 builder->Build("libEGL_shiraz.so.1",sizeof(functions)/sizeof(functions[0]),functions,0,nullptr);
}
}
