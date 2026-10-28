// SPDX-License-Identifier: GPL-3.0-only
#include <GLES/gl.h>
#include <cstring>
#include <dlfcn.h>
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
namespace berberis {
#define ENTRY(name) {#name, GetTrampolineFunc<decltype(name)>(), nullptr},
static const KnownTrampoline functions[] = {
#include "digitalis-gles1.inc"
};
#undef ENTRY
extern "C" void* ShirazLookupGuestGles1(const char* name) {
 static void* library=dlopen("libGLESv1_CM.so.1",RTLD_NOW|RTLD_LOCAL);
 if(!library||!name)return nullptr;
 for(const auto& entry:functions)if(strcmp(name,entry.name)==0){
  void* symbol=dlsym(library,name);if(symbol)WrapHostFunctionImpl(reinterpret_cast<HostCode>(symbol),entry.marshal_and_call,entry.name);return symbol;
 }
 return nullptr;
}
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
 builder->Build("libGLESv1_CM.so.1",sizeof(functions)/sizeof(functions[0]),functions,0,nullptr);
}
}
