// SPDX-License-Identifier: GPL-3.0-only
// Host runner for explicit native-bridge fixtures, not an APK acceptance test.
#include <cstdio>
#include <dlfcn.h>
#include <nativebridge/native_bridge.h>
int main(int argc,char**argv){
 if(argc<2)return 2;
 void* lib=dlopen(argv[1],RTLD_NOW|RTLD_GLOBAL);
 if(!lib){fprintf(stderr,"%s\n",dlerror());return 1;}
 auto* bridge=static_cast<android::NativeBridgeCallbacks*>(dlsym(lib,"NativeBridgeItf"));
 if(!bridge)return 2;
 android::NativeBridgeRuntimeCallbacks cb{};
 fprintf(stderr,"Bridge version: %u\n",bridge->version);
 bool ok=bridge->initialize(&cb,"/tmp","arm64");
 printf("Bridge initialized: %s\n",ok?"yes":"no");
 if (ok && argc == 3) {
  void* handle=bridge->loadLibrary(argv[2],RTLD_NOW);
  if (!handle) { fprintf(stderr,"Guest library failed to load: %s\n",bridge->getError()); return 3; }
  auto fn=reinterpret_cast<int(*)(void*,void*)>(bridge->getTrampoline(handle,"Java_shiraz_Test_answer","I",1));
  if (!fn) return 4;
  int result=fn(nullptr,nullptr);
  printf("ARM64 JNI result: %d\n",result);
  return result==42?0:5;
 }
 return ok?0:1;
}
