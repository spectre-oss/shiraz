// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
extern void* bionic_dlopen(const char*,int);
extern void* bionic_dlsym(void*,const char*);
extern int bionic_dladdr(const void*,Dl_info*);
extern int bionic_dlclose(void*);
int main(int argc,char** argv){
 if(argc!=2)return 2;
 void* library=bionic_dlopen("libshiraz-bionic-probe.so",RTLD_NOW);
 if(!library)return 3;
 void* symbol=bionic_dlsym(library,"Java_BionicLoadSmoke_value");
 Dl_info info={0};char* expected=realpath(argv[1],NULL);
 if(!symbol||!expected||!bionic_dladdr(symbol,&info)||!info.dli_fname||strcmp(info.dli_fname,expected)||!info.dli_fbase)return 4;
 free(expected);
 if(!bionic_dladdr((void*)&main,&info)||!info.dli_fname)return 5;
 bionic_dlclose(library);
 puts("BIONIC_DLADDR_RESOLVED_PATH_AND_HOST_FALLBACK_OK");return 0;
}
