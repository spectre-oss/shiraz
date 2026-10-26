// SPDX-License-Identifier: GPL-3.0-only
#include <cassert>
#include <initializer_list>
#include <cstdint>
#include <cstddef>
#include <cstring>
#include <dlfcn.h>
struct AMediaFormat;
extern "C" {
AMediaFormat* AMediaFormat_new();int AMediaFormat_delete(AMediaFormat*);
void AMediaFormat_setInt32(AMediaFormat*,const char*,int32_t);
bool AMediaFormat_getInt32(AMediaFormat*,const char*,int32_t*);
void AMediaFormat_setInt64(AMediaFormat*,const char*,int64_t);
bool AMediaFormat_getInt64(AMediaFormat*,const char*,int64_t*);
void AMediaFormat_setFloat(AMediaFormat*,const char*,float);
bool AMediaFormat_getFloat(AMediaFormat*,const char*,float*);
void AMediaFormat_setString(AMediaFormat*,const char*,const char*);
bool AMediaFormat_getString(AMediaFormat*,const char*,const char**);
void AMediaFormat_setBuffer(AMediaFormat*,const char*,const void*,size_t);
bool AMediaFormat_getBuffer(AMediaFormat*,const char*,void**,size_t*);
const char* AMediaFormat_toString(AMediaFormat*);
void* AMediaCodec_createDecoderByType(const char*);
int AMediaCodec_start(void*);
}
int main(){
 auto* f=AMediaFormat_new();assert(f);
 int32_t width=7;assert(!AMediaFormat_getInt32(f,"width",&width)&&width==7);
 AMediaFormat_setInt32(f,"width",1920);assert(AMediaFormat_getInt32(f,"width",&width)&&width==1920);
 int64_t duration=0;AMediaFormat_setInt64(f,"durationUs",INT64_C(9000000000));assert(AMediaFormat_getInt64(f,"durationUs",&duration)&&duration==INT64_C(9000000000));
 float rate=0;AMediaFormat_setFloat(f,"rate",29.97f);assert(AMediaFormat_getFloat(f,"rate",&rate)&&rate==29.97f);
 AMediaFormat_setString(f,"mime","video/avc");const char* mime=nullptr;assert(AMediaFormat_getString(f,"mime",&mime)&&!strcmp(mime,"video/avc"));
 uint8_t config[]={1,2,3};AMediaFormat_setBuffer(f,"csd-0",config,sizeof(config));config[0]=9;void* data=nullptr;size_t size=0;assert(AMediaFormat_getBuffer(f,"csd-0",&data,&size)&&size==3&&static_cast<uint8_t*>(data)[0]==1);
 AMediaFormat_setString(f,"width","wrong type");assert(!AMediaFormat_getInt32(f,"width",&width));
 assert(strstr(AMediaFormat_toString(f),"video/avc"));assert(AMediaFormat_delete(f)==0);
 assert(!AMediaCodec_createDecoderByType("video/avc"));assert(AMediaCodec_start(nullptr)<0);
 // Lazy-loaded codec libraries need every referenced entry point even when
 // their built-in software decoder is ultimately selected.
 for(const char* name:{"AMediaCodec_configure","AMediaCodec_createCodecByName","AMediaCodec_createEncoderByType","AMediaCodec_delete","AMediaCodec_stop","AMediaCodec_flush","AMediaCodec_dequeueInputBuffer","AMediaCodec_dequeueOutputBuffer","AMediaCodec_getInputBuffer","AMediaCodec_getOutputBuffer","AMediaCodec_getOutputFormat","AMediaCodec_queueInputBuffer","AMediaCodec_queueSecureInputBuffer","AMediaCodec_releaseOutputBuffer","AMediaCodec_releaseOutputBufferAtTime","AMediaCodec_setParameters"})assert(dlsym(RTLD_DEFAULT,name));
}
