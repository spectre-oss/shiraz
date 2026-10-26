// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/media_crypto.h"
#include <cassert>
#include <cstdio>
int main(){
    uint8_t uuid[16]{},key[16]{},iv[16]{};key[0]=7;iv[0]=9;
    assert(!AMediaDrm_isCryptoSchemeSupported(uuid,"video/mp4")&&!AMediaCrypto_isCryptoSchemeSupported(uuid));
    assert(!AMediaDrm_createByUUID(uuid)&&!AMediaCrypto_new(uuid,nullptr,0));
    AMediaDrmSessionId session{uuid,16};assert(AMediaDrm_openSession(nullptr,&session)==-10003&&!session.ptr&&!session.length);
    const uint8_t* request=uuid;size_t length=16;const char* url="fixture";
    assert(AMediaDrm_getProvisionRequest(nullptr,&request,&length,&url)==-10003&&!request&&!length&&!url);
    size_t clear[2]={1,2},encrypted[2]={3,4};
    auto* info=AMediaCodecCryptoInfo_new(2,key,iv,1,clear,encrypted);assert(info);
    key[0]=0;iv[0]=0;clear[0]=0;encrypted[0]=0;
    uint8_t copy[16]{};size_t sizes[2]{};
    assert(AMediaCodecCryptoInfo_getNumSubSamples(info)==2&&AMediaCodecCryptoInfo_getMode(info)==1);
    assert(AMediaCodecCryptoInfo_getKey(info,copy)==0&&copy[0]==7);
    assert(AMediaCodecCryptoInfo_getIV(info,copy)==0&&copy[0]==9);
    assert(AMediaCodecCryptoInfo_getClearBytes(info,sizes)==0&&sizes[0]==1&&sizes[1]==2);
    assert(AMediaCodecCryptoInfo_getEncryptedBytes(info,sizes)==0&&sizes[0]==3&&sizes[1]==4);
    assert(AMediaCodecCryptoInfo_delete(info)==0);
    assert(!AMediaCodecCryptoInfo_new(65537,key,iv,1,nullptr,encrypted));
    info=AMediaCodecCryptoInfo_new(2,key,iv,1,nullptr,encrypted);assert(info);
    assert(AMediaCodecCryptoInfo_getClearBytes(info,sizes)==0&&sizes[0]==0&&sizes[1]==0);AMediaCodecCryptoInfo_delete(info);
    std::puts("MEDIA_CRYPTO_CAPABILITIES_METADATA_OK");
}
