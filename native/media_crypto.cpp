// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/media_crypto.h"
#include <algorithm>
#include <array>
#include <memory>
#include <vector>
// No Android DRM provider or provisioning service is installed. Capability
// queries must report this honestly; never fabricate licenses or sessions.
extern "C" bool AMediaDrm_isCryptoSchemeSupported(const uint8_t*,const char*){return false;}
extern "C" AMediaDrm* AMediaDrm_createByUUID(const uint8_t*){return nullptr;}
extern "C" void AMediaDrm_release(AMediaDrm*){}
extern "C" int AMediaDrm_setOnEventListener(AMediaDrm*,AMediaDrmEventListener){return -10003;}
extern "C" int AMediaDrm_openSession(AMediaDrm*,AMediaDrmSessionId* out){if(out)*out={};return -10003;}
extern "C" int AMediaDrm_closeSession(AMediaDrm*,const AMediaDrmSessionId*){return -10003;}
extern "C" int AMediaDrm_getKeyRequest(AMediaDrm*,const AMediaDrmScope*,const uint8_t*,size_t,const char*,int,const AMediaDrmKeyValue*,size_t,const uint8_t** out,size_t* length){if(out)*out=nullptr;if(length)*length=0;return -10003;}
extern "C" int AMediaDrm_provideKeyResponse(AMediaDrm*,const AMediaDrmScope*,const uint8_t*,size_t,AMediaDrmKeySetId* out){if(out)*out={};return -10003;}
extern "C" int AMediaDrm_getProvisionRequest(AMediaDrm*,const uint8_t** out,size_t* length,const char** url){if(out)*out=nullptr;if(length)*length=0;if(url)*url=nullptr;return -10003;}
extern "C" int AMediaDrm_provideProvisionResponse(AMediaDrm*,const uint8_t*,size_t){return -10003;}
extern "C" int AMediaDrm_setPropertyString(AMediaDrm*,const char*,const char*){return -10003;}
extern "C" int AMediaDrm_setPropertyByteArray(AMediaDrm*,const char*,const uint8_t*,size_t){return -10003;}
extern "C" bool AMediaCrypto_isCryptoSchemeSupported(const uint8_t*){return false;}
extern "C" bool AMediaCrypto_requiresSecureDecoderComponent(const char*){return false;}
extern "C" AMediaCrypto* AMediaCrypto_new(const uint8_t*,const void*,size_t){return nullptr;}
extern "C" void AMediaCrypto_delete(AMediaCrypto*){}
// CryptoInfo is metadata, independent of whether a DRM backend is available.
struct AMediaCodecCryptoInfo {
    std::array<uint8_t,16> key,iv;
    int mode;
    cryptoinfo_pattern_t pattern{};
    std::vector<size_t> clear,encrypted;
};
extern "C" AMediaCodecCryptoInfo* AMediaCodecCryptoInfo_new(int count,uint8_t* key,uint8_t* iv,int mode,size_t* clear,size_t* encrypted) try {
    if(count<1||count>65536||!key||!iv||!encrypted||mode<0||mode>3)return nullptr;
    auto info=std::make_unique<AMediaCodecCryptoInfo>();
    std::copy_n(key,16,info->key.begin());std::copy_n(iv,16,info->iv.begin());info->mode=mode;
    info->clear.resize(count);if(clear)std::copy_n(clear,count,info->clear.begin());
    info->encrypted.assign(encrypted,encrypted+count);return info.release();
}catch(const std::bad_alloc&){return nullptr;}
extern "C" int AMediaCodecCryptoInfo_delete(AMediaCodecCryptoInfo* info){
    if(!info)return -10003;
    volatile uint8_t* key=info->key.data();volatile uint8_t* iv=info->iv.data();
    for(unsigned i=0;i<16;++i){key[i]=0;iv[i]=0;}delete info;return 0;
}
extern "C" void AMediaCodecCryptoInfo_setPattern(AMediaCodecCryptoInfo* info,cryptoinfo_pattern_t* pattern){if(info&&pattern&&pattern->encryptBlocks>=0&&pattern->skipBlocks>=0)info->pattern=*pattern;}
extern "C" size_t AMediaCodecCryptoInfo_getNumSubSamples(AMediaCodecCryptoInfo* info){return info?info->clear.size():0;}
extern "C" int AMediaCodecCryptoInfo_getKey(AMediaCodecCryptoInfo* info,uint8_t* out){if(!info||!out)return -10004;std::copy(info->key.begin(),info->key.end(),out);return 0;}
extern "C" int AMediaCodecCryptoInfo_getIV(AMediaCodecCryptoInfo* info,uint8_t* out){if(!info||!out)return -10004;std::copy(info->iv.begin(),info->iv.end(),out);return 0;}
extern "C" int AMediaCodecCryptoInfo_getMode(AMediaCodecCryptoInfo* info){return info?info->mode:0;}
extern "C" int AMediaCodecCryptoInfo_getClearBytes(AMediaCodecCryptoInfo* info,size_t* out){if(!info||!out)return -10004;std::copy(info->clear.begin(),info->clear.end(),out);return 0;}
extern "C" int AMediaCodecCryptoInfo_getEncryptedBytes(AMediaCodecCryptoInfo* info,size_t* out){if(!info||!out)return -10004;std::copy(info->encrypted.begin(),info->encrypted.end(),out);return 0;}
