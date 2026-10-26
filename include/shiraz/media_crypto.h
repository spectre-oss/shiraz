// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
struct AMediaDrm;
struct AMediaCrypto;
struct AMediaCodecCryptoInfo;
struct AMediaDrmByteArray { const uint8_t* ptr; size_t length; };
using AMediaDrmSessionId=AMediaDrmByteArray;
using AMediaDrmScope=AMediaDrmByteArray;
using AMediaDrmKeySetId=AMediaDrmByteArray;
struct AMediaDrmKeyValue { const char* key; const char* value; };
using AMediaDrmEventListener=void(*)(AMediaDrm*,const AMediaDrmSessionId*,int,int,const uint8_t*,size_t);
struct cryptoinfo_pattern_t {int32_t encryptBlocks,skipBlocks;};
extern "C" {
bool AMediaDrm_isCryptoSchemeSupported(const uint8_t*,const char*);
AMediaDrm* AMediaDrm_createByUUID(const uint8_t*);
void AMediaDrm_release(AMediaDrm*);
int AMediaDrm_setOnEventListener(AMediaDrm*,AMediaDrmEventListener);
int AMediaDrm_openSession(AMediaDrm*,AMediaDrmSessionId*);
int AMediaDrm_closeSession(AMediaDrm*,const AMediaDrmSessionId*);
int AMediaDrm_getKeyRequest(AMediaDrm*,const AMediaDrmScope*,const uint8_t*,size_t,const char*,int,const AMediaDrmKeyValue*,size_t,const uint8_t**,size_t*);
int AMediaDrm_provideKeyResponse(AMediaDrm*,const AMediaDrmScope*,const uint8_t*,size_t,AMediaDrmKeySetId*);
int AMediaDrm_getProvisionRequest(AMediaDrm*,const uint8_t**,size_t*,const char**);
int AMediaDrm_provideProvisionResponse(AMediaDrm*,const uint8_t*,size_t);
int AMediaDrm_setPropertyString(AMediaDrm*,const char*,const char*);
int AMediaDrm_setPropertyByteArray(AMediaDrm*,const char*,const uint8_t*,size_t);
bool AMediaCrypto_isCryptoSchemeSupported(const uint8_t*);
bool AMediaCrypto_requiresSecureDecoderComponent(const char*);
AMediaCrypto* AMediaCrypto_new(const uint8_t*,const void*,size_t);
void AMediaCrypto_delete(AMediaCrypto*);
AMediaCodecCryptoInfo* AMediaCodecCryptoInfo_new(int,uint8_t*,uint8_t*,int,size_t*,size_t*);
int AMediaCodecCryptoInfo_delete(AMediaCodecCryptoInfo*);
void AMediaCodecCryptoInfo_setPattern(AMediaCodecCryptoInfo*,cryptoinfo_pattern_t*);
size_t AMediaCodecCryptoInfo_getNumSubSamples(AMediaCodecCryptoInfo*);
int AMediaCodecCryptoInfo_getKey(AMediaCodecCryptoInfo*,uint8_t*);
int AMediaCodecCryptoInfo_getIV(AMediaCodecCryptoInfo*,uint8_t*);
int AMediaCodecCryptoInfo_getMode(AMediaCodecCryptoInfo*);
int AMediaCodecCryptoInfo_getClearBytes(AMediaCodecCryptoInfo*,size_t*);
int AMediaCodecCryptoInfo_getEncryptedBytes(AMediaCodecCryptoInfo*,size_t*);
}
