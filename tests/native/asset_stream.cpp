// SPDX-License-Identifier: GPL-3.0-only
#include <androidfw/androidfw_c_api.h>
#include <cassert>
#include <cstring>
#include <cstdio>
#include <climits>
#include <initializer_list>
extern "C" {
Asset* AAssetManager_open(AssetManager*,const char*,int);
int AAsset_read(Asset*,void*,size_t);
off64_t AAsset_seek64(Asset*,off64_t,int);
off_t AAsset_seek(Asset*,off_t,int);
off64_t AAsset_getLength64(Asset*);
off64_t AAsset_getRemainingLength64(Asset*);
void AAsset_close(Asset*);
}
int main(int argc,char** argv){
 assert(argc==2);
 const ApkAssets* apk=ApkAssets_load(argv[1],false);assert(apk);
 AssetManager* manager=AssetManager_new();assert(manager);
 assert(AssetManager_setApkAssets(manager,&apk,1,true,false));
 for(const char* name : {"stored.txt","compressed.txt"}) {
  Asset* asset=AAssetManager_open(manager,name,2);assert(asset);
  assert(AAsset_getLength64(asset)==10);
  char bytes[16]{};
  assert(AAsset_read(asset,bytes,3)==3 && !std::memcmp(bytes,"012",3));
  assert(AAsset_getRemainingLength64(asset)==7);
  assert(AAsset_seek64(asset,-2,SEEK_END)==8);
  assert(AAsset_read(asset,bytes,sizeof(bytes))==2 && !std::memcmp(bytes,"89",2));
  assert(AAsset_read(asset,bytes,sizeof(bytes))==0);
  assert(AAsset_seek(asset,0,SEEK_SET)==0);
  assert(AAsset_read(asset,nullptr,0)==0);
  assert(AAsset_read(asset,nullptr,1)==-1);
  assert(AAsset_read(asset,bytes,size_t(INT_MAX)+1)==-1);
  assert(AAsset_seek64(asset,0,999)==-1);
  assert(AAsset_getRemainingLength64(asset)==10);
  AAsset_close(asset);
 }
 assert(!AAssetManager_open(manager,"../stored.txt",2));
 Asset* large=AAssetManager_open(manager,"large.bin",2);assert(large);
 assert(AAsset_getLength64(large)==128*1024*1024+1);
 char byte=0;assert(AAsset_read(large,&byte,1)==1&&byte=='x');
 assert(AAsset_seek64(large,-1,SEEK_END)==128*1024*1024);
 assert(AAsset_read(large,&byte,1)==1&&byte=='x');
 AAsset_close(large);
 assert(!AAssetManager_open(manager,"oversized.bin",2));
 AssetManager_delete(manager);ApkAssets_delete(apk);
 std::puts("NDK_ASSET_STREAM_OK");
}
