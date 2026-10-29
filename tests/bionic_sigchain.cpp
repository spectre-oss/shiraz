// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <csignal>
#include <cstdio>
#include <cstdint>
#include <dlfcn.h>
struct ChainAction { bool (*handler)(int,siginfo_t*,void*);sigset_t mask;uint64_t flags; };
struct BionicAction {unsigned flags;void (*handler)(int);unsigned long mask;void (*restorer)();};
extern "C" int bionic_sigaction(int,const BionicAction*,BionicAction*);
static volatile sig_atomic_t runtimeCalls,appCalls,blockedDuringHandler;
static bool runtimeHandler(int,siginfo_t*,void*){runtimeCalls++;return false;}
static void appHandler(int){sigset_t mask;sigprocmask(SIG_SETMASK,nullptr,&mask);blockedDuringHandler=sigismember(&mask,SIGUSR2)&&sigismember(&mask,SIGTERM)&&sigismember(&mask,SIGRTMAX);appCalls++;}
int main(int argc,char**argv){
 assert(argc==2);
 const unsigned long mask=(1UL<<(SIGUSR2-1))|(1UL<<(SIGTERM-1))|(1UL<<(SIGRTMIN))|(1UL<<(SIGRTMAX-1));
 BionicAction initial{};initial.handler=appHandler;initial.mask=mask;assert(bionic_sigaction(SIGUSR1,&initial,nullptr)==0);
 BionicAction beforeChain{};assert(bionic_sigaction(SIGUSR1,nullptr,&beforeChain)==0&&beforeChain.mask==mask);
 raise(SIGUSR1);assert(appCalls==1&&blockedDuringHandler);appCalls=0;blockedDuringHandler=0;
 void* library=dlopen(argv[1],RTLD_NOW|RTLD_GLOBAL);assert(library);
 auto add=reinterpret_cast<void(*)(int,ChainAction*)>(dlsym(library,"AddSpecialSignalHandlerFn"));assert(add);
 ChainAction chain{};chain.handler=runtimeHandler;sigemptyset(&chain.mask);add(SIGUSR1,&chain);
 BionicAction action{};action.handler=appHandler;action.mask=mask;assert(bionic_sigaction(SIGUSR1,&action,nullptr)==0);
 raise(SIGUSR1);assert(appCalls==1&&blockedDuringHandler);assert(runtimeCalls==1);
 BionicAction queried{};assert(bionic_sigaction(SIGUSR1,nullptr,&queried)==0);assert(queried.handler==appHandler&&queried.mask==mask);
 puts("BIONIC_SIGCHAIN_RUNTIME_AND_APP_OK");
}
