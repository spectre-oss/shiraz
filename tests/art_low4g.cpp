// SPDX-License-Identifier: GPL-3.0-only
#include "base/mem_map.h"
#include <sys/mman.h>
#include <cstdint>
#include <iostream>
#include <vector>
#include <stdexcept>
static void require(bool ok,const char* message){if(!ok)throw std::runtime_error(message);}
int main(){
 constexpr size_t block=1024*1024;
 const uintptr_t upper=uintptr_t{1}<<31,limit=uintptr_t{1}<<32;
 void* sentinel=mmap(reinterpret_cast<void*>(upper),block,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
 require(sentinel==reinterpret_cast<void*>(upper),"sentinel must occupy first fallback hint");static_cast<unsigned char*>(sentinel)[0]=0x5a;
 std::vector<void*> reserved;
 for(size_t i=0;i<2048;i++){void* p=mmap(nullptr,block,PROT_NONE,MAP_PRIVATE|MAP_ANONYMOUS|MAP_32BIT,-1,0);if(p==MAP_FAILED)break;reserved.push_back(p);}
 void* exhausted=mmap(nullptr,block,PROT_NONE,MAP_PRIVATE|MAP_ANONYMOUS|MAP_32BIT,-1,0);
 require(exhausted==MAP_FAILED,"MAP_32BIT pressure not exhausted");
 art::MemMap::Init();std::string error;
 {
  auto bitmap=art::MemMap::MapAnonymous("shiraz-low4g-test",89*block,PROT_READ|PROT_WRITE,true,&error);
  require(bitmap.IsValid(),error.c_str());uintptr_t address=reinterpret_cast<uintptr_t>(bitmap.Begin());
  require(address>=upper&&address+bitmap.Size()<limit,"fallback outside low4G upper half");
  bitmap.Begin()[0]=0x12;bitmap.Begin()[bitmap.Size()-1]=0x34;
  require(bitmap.Begin()[0]==0x12&&bitmap.Begin()[bitmap.Size()-1]==0x34,"mapping not writable");
  require(static_cast<unsigned char*>(sentinel)[0]==0x5a,"existing mapping overwritten");
  auto excessive=art::MemMap::MapAnonymous("shiraz-low4g-too-large",upper,PROT_NONE,true,&error);
  require(!excessive.IsValid(),"fallback exceeded bounded region");
 }
 art::MemMap::Shutdown();for(void* p:reserved)munmap(p,block);munmap(sentinel,block);
 std::cout<<"ART_LOW4G_PRESSURE_OK reservations="<<reserved.size()<<"\n";
}
