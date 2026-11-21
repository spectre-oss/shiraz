// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef uint8_t XAuint8;
typedef uint16_t XAuint16;
typedef uint32_t XAuint32;
typedef int32_t XAint32;
typedef XAuint32 XAboolean;
typedef XAuint32 XAresult;

#define XA_BOOLEAN_FALSE ((XAboolean)0x00000000)
#define XA_BOOLEAN_TRUE ((XAboolean)0x00000001)

#define XA_RESULT_SUCCESS ((XAresult)0x00000000)
#define XA_RESULT_PRECONDITIONS_VIOLATED ((XAresult)0x00000001)
#define XA_RESULT_PARAMETER_INVALID ((XAresult)0x00000002)
#define XA_RESULT_MEMORY_FAILURE ((XAresult)0x00000003)
#define XA_RESULT_RESOURCE_ERROR ((XAresult)0x00000004)
#define XA_RESULT_RESOURCE_LOST ((XAresult)0x00000005)
#define XA_RESULT_IO_ERROR ((XAresult)0x00000006)
#define XA_RESULT_BUFFER_INSUFFICIENT ((XAresult)0x00000007)
#define XA_RESULT_CONTENT_CORRUPTED ((XAresult)0x00000008)
#define XA_RESULT_CONTENT_UNSUPPORTED ((XAresult)0x00000009)
#define XA_RESULT_CONTENT_NOT_FOUND ((XAresult)0x0000000A)
#define XA_RESULT_PERMISSION_DENIED ((XAresult)0x0000000B)
#define XA_RESULT_FEATURE_UNSUPPORTED ((XAresult)0x0000000C)
#define XA_RESULT_INTERNAL_ERROR ((XAresult)0x0000000D)
#define XA_RESULT_UNKNOWN_ERROR ((XAresult)0x0000000E)
#define XA_RESULT_OPERATION_ABORTED ((XAresult)0x0000000F)
#define XA_RESULT_CONTROL_LOST ((XAresult)0x00000010)

#define XA_OBJECT_STATE_UNREALIZED ((XAuint32)0x00000001)
#define XA_OBJECT_STATE_REALIZED ((XAuint32)0x00000002)
#define XA_OBJECT_STATE_SUSPENDED ((XAuint32)0x00000003)

typedef struct XAInterfaceID_ {
    XAuint32 time_low;
    XAuint16 time_mid;
    XAuint16 time_hi_and_version;
    XAuint16 clock_seq;
    XAuint8 node[6];
} const* XAInterfaceID;

typedef struct XAEngineOption_ {
    XAuint32 feature;
    XAuint32 data;
} XAEngineOption;

struct XAObjectItf_;
typedef const struct XAObjectItf_* const* XAObjectItf;

struct XAEngineItf_;
typedef const struct XAEngineItf_* const* XAEngineItf;

struct XAOutputMixItf_;
typedef const struct XAOutputMixItf_* const* XAOutputMixItf;

typedef void (*xaObjectCallback)(XAObjectItf caller, const void* pContext, XAuint32 event,
                                 XAresult result, XAuint32 param, void* pInterface);

struct XAObjectItf_ {
    XAresult (*Realize)(XAObjectItf self, XAboolean async);
    XAresult (*Resume)(XAObjectItf self, XAboolean async);
    XAresult (*GetState)(XAObjectItf self, XAuint32* pState);
    XAresult (*GetInterface)(XAObjectItf self, const XAInterfaceID iid, void* pInterface);
    XAresult (*RegisterCallback)(XAObjectItf self, xaObjectCallback callback, void* pContext);
    void (*AbortAsyncOperation)(XAObjectItf self);
    void (*Destroy)(XAObjectItf self);
    XAresult (*SetPriority)(XAObjectItf self, XAint32 priority, XAboolean preemptable);
    XAresult (*GetPriority)(XAObjectItf self, XAint32* pPriority, XAboolean* pPreemptable);
    XAresult (*SetLossOfControlInterfaces)(XAObjectItf self, XAuint16 numInterfaces,
                                           XAInterfaceID* pInterfaceIDs, XAboolean enabled);
};

struct XAEngineItf_ {
    XAresult (*CreateCameraDevice)(XAEngineItf self, XAObjectItf* pDevice, XAuint32 deviceID,
                                   XAuint32 numInterfaces, const XAInterfaceID* pInterfaceIds,
                                   const XAboolean* pInterfaceRequired);
    XAresult (*CreateRadioDevice)(XAEngineItf self, XAObjectItf* pDevice, XAuint32 numInterfaces,
                                  const XAInterfaceID* pInterfaceIds,
                                  const XAboolean* pInterfaceRequired);
    XAresult (*CreateLEDDevice)(XAEngineItf self, XAObjectItf* pDevice, XAuint32 deviceID,
                                XAuint32 numInterfaces, const XAInterfaceID* pInterfaceIds,
                                const XAboolean* pInterfaceRequired);
    XAresult (*CreateVibraDevice)(XAEngineItf self, XAObjectItf* pDevice, XAuint32 deviceID,
                                  XAuint32 numInterfaces, const XAInterfaceID* pInterfaceIds,
                                  const XAboolean* pInterfaceRequired);
    XAresult (*CreateMediaPlayer)(XAEngineItf self, XAObjectItf* pPlayer, void* pDataSrc,
                                  void* pBankSrc, void* pAudioSnk, void* pImageVideoSnk,
                                  void* pVibra, void* pLEDArray, XAuint32 numInterfaces,
                                  const XAInterfaceID* pInterfaceIds,
                                  const XAboolean* pInterfaceRequired);
    XAresult (*CreateMediaRecorder)(XAEngineItf self, XAObjectItf* pRecorder, void* pAudioSrc,
                                    void* pImageVideoSrc, void* pDataSnk, XAuint32 numInterfaces,
                                    const XAInterfaceID* pInterfaceIds,
                                    const XAboolean* pInterfaceRequired);
    XAresult (*CreateOutputMix)(XAEngineItf self, XAObjectItf* pMix, XAuint32 numInterfaces,
                                const XAInterfaceID* pInterfaceIds,
                                const XAboolean* pInterfaceRequired);
    XAresult (*CreateMetadataExtractor)(XAEngineItf self, XAObjectItf* pMetadataExtractor,
                                        void* pDataSource, XAuint32 numInterfaces,
                                        const XAInterfaceID* pInterfaceIds,
                                        const XAboolean* pInterfaceRequired);
    XAresult (*CreateExtensionObject)(XAEngineItf self, XAObjectItf* pObject, void* pParameters,
                                      XAuint32 objectID, XAuint32 numInterfaces,
                                      const XAInterfaceID* pInterfaceIds,
                                      const XAboolean* pInterfaceRequired);
    XAresult (*GetImplementationInfo)(XAEngineItf self, XAuint32* pMajor, XAuint32* pMinor,
                                      XAuint32* pStep, const XAuint8* pImplementationText);
    XAresult (*QuerySupportedProfiles)(XAEngineItf self, XAint32* pProfilesSupported);
    XAresult (*QueryNumSupportedInterfaces)(XAEngineItf self, XAuint32 objectID,
                                            XAuint32* pNumSupportedInterfaces);
    XAresult (*QuerySupportedInterfaces)(XAEngineItf self, XAuint32 objectID, XAuint32 index,
                                         XAInterfaceID* pInterfaceId);
    XAresult (*QueryNumSupportedExtensions)(XAEngineItf self, XAuint32* pNumExtensions);
    XAresult (*QuerySupportedExtension)(XAEngineItf self, XAuint32 index, XAuint8* pExtensionName,
                                        XAint32* pNameLength);
    XAresult (*IsExtensionSupported)(XAEngineItf self, const XAuint8* pExtensionName,
                                     XAboolean* pSupported);
    XAresult (*QueryLEDCapabilities)(XAEngineItf self, XAuint32* pIndex, XAuint32* pLEDDeviceID,
                                     void* pDescriptor);
    XAresult (*QueryVibraCapabilities)(XAEngineItf self, XAuint32* pIndex, XAuint32* pVibraDeviceID,
                                       void* pDescriptor);
};

struct XAOutputMixItf_ {
    XAresult (*GetDestinationOutputDeviceIDs)(XAOutputMixItf self, XAint32* pNumDevices,
                                              XAuint32* pDeviceIDs);
    XAresult (*RegisterDeviceChangeCallback)(XAOutputMixItf self, void* callback, void* pContext);
    XAresult (*ReRoute)(XAOutputMixItf self, XAint32 numOutputDevices, XAuint32* pOutputDeviceIDs);
};

extern const XAInterfaceID XA_IID_ENGINE;
extern const XAInterfaceID XA_IID_OBJECT;
extern const XAInterfaceID XA_IID_PLAY;
extern const XAInterfaceID XA_IID_VOLUME;
extern const XAInterfaceID XA_IID_OUTPUTMIX;
extern const XAInterfaceID XA_IID_STREAMINFORMATION;
extern const XAInterfaceID XA_IID_ANDROIDBUFFERQUEUESOURCE;
extern const XAInterfaceID XA_IID_METADATAEXTRACTION;
extern const XAInterfaceID XA_IID_SEEK;
extern const XAInterfaceID XA_IID_PREFETCHSTATUS;

XAresult xaCreateEngine(XAObjectItf* pEngine, XAuint32 numOptions,
                        const XAEngineOption* pEngineOptions, XAuint32 numInterfaces,
                        const XAInterfaceID* pInterfaceIds, const XAboolean* pInterfaceRequired);
XAresult xaQueryNumSupportedEngineInterfaces(XAuint32* pNumSupportedInterfaces);
XAresult xaQuerySupportedEngineInterfaces(XAuint32 index, XAInterfaceID* pInterfaceId);

#ifdef __cplusplus
}
#endif
