// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK libOpenMAXAL.so implementation: OpenMAX AL 1.0.1 COM engine,
// object/engine/outputmix vtables, all required XA_IID_* interface IDs, and
// explicit XA_RESULT_FEATURE_UNSUPPORTED on unbridged hardware pipelines.
#include "shiraz/ndk/openmaxal.h"

#include <cstring>
#include <new>

namespace {

const XAInterfaceID_ kIID_OBJECT = {
    0x79216360, 0xddd7, 0x11db, 0xac16, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_ENGINE = {
    0x8d97c260, 0xddd4, 0x11db, 0x958f, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_PLAY = {
    0xef0bd9c0, 0xddd7, 0x11db, 0xbf49, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_VOLUME = {
    0x09e8ede0, 0xddde, 0x11db, 0xb4f6, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_OUTPUTMIX = {
    0x97750f60, 0xddd7, 0x11db, 0x92b1, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_STREAMINFORMATION = {
    0xa9769d80, 0xddd9, 0x11db, 0xa44b, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_ANDROIDBUFFERQUEUESOURCE = {
    0x198e4f20, 0x1e0e, 0x11e0, 0x9a48, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_METADATAEXTRACTION = {
    0xaa5b1f80, 0xddd6, 0x11db, 0xac8e, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_SEEK = {
    0xd43135a0, 0xddd7, 0x11db, 0x9bab, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
const XAInterfaceID_ kIID_PREFETCHSTATUS = {
    0x2a41ee80, 0xddd8, 0x11db, 0xa41f, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};

bool iidEqual(XAInterfaceID a, XAInterfaceID b) {
    if (!a || !b) {
        return false;
    }
    if (a == b) {
        return true;
    }
    return std::memcmp(a, b, sizeof(XAInterfaceID_)) == 0;
}

enum class XaObjKind { Engine, OutputMix };

struct XaObject {
    const XAObjectItf_* objVtbl = nullptr;
    const XAEngineItf_* engVtbl = nullptr;
    const XAOutputMixItf_* mixVtbl = nullptr;
    XaObjKind kind = XaObjKind::Engine;
    XAuint32 state = XA_OBJECT_STATE_UNREALIZED;
    XAint32 priority = 0;
    XAboolean preemptable = XA_BOOLEAN_FALSE;
    xaObjectCallback callback = nullptr;
    void* cbContext = nullptr;
};

XaObject* fromObj(XAObjectItf self) {
    return static_cast<XaObject*>(const_cast<void*>(static_cast<const void*>(self)));
}

XaObject* fromEng(XAEngineItf self) {
    auto* base = static_cast<uint8_t*>(const_cast<void*>(static_cast<const void*>(self)));
    return reinterpret_cast<XaObject*>(base - offsetof(XaObject, engVtbl));
}

// --- XAOutputMixItf ---
XAresult mixGetDest(XAOutputMixItf, XAint32* pNumDevices, XAuint32*) {
    if (!pNumDevices) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pNumDevices = 0;
    return XA_RESULT_SUCCESS;
}
XAresult mixRegCb(XAOutputMixItf, void*, void*) {
    return XA_RESULT_SUCCESS;
}
XAresult mixReRoute(XAOutputMixItf, XAint32, XAuint32*) {
    return XA_RESULT_FEATURE_UNSUPPORTED;
}

const XAOutputMixItf_ kMixVtbl = {
    mixGetDest,
    mixRegCb,
    mixReRoute,
};

// --- XAObjectItf ---
XAresult objRealize(XAObjectItf self, XAboolean /*async*/) {
    if (!self) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    o->state = XA_OBJECT_STATE_REALIZED;
    return XA_RESULT_SUCCESS;
}
XAresult objResume(XAObjectItf self, XAboolean /*async*/) {
    if (!self) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    if (o->state != XA_OBJECT_STATE_REALIZED && o->state != XA_OBJECT_STATE_SUSPENDED) {
        return XA_RESULT_PRECONDITIONS_VIOLATED;
    }
    o->state = XA_OBJECT_STATE_REALIZED;
    return XA_RESULT_SUCCESS;
}
XAresult objGetState(XAObjectItf self, XAuint32* pState) {
    if (!self || !pState) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pState = fromObj(self)->state;
    return XA_RESULT_SUCCESS;
}
XAresult objGetInterface(XAObjectItf self, const XAInterfaceID iid, void* pInterface) {
    if (!self || !iid || !pInterface) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    if (o->state != XA_OBJECT_STATE_REALIZED) {
        return XA_RESULT_PRECONDITIONS_VIOLATED;
    }
    if (iidEqual(iid, &kIID_OBJECT)) {
        *static_cast<XAObjectItf*>(pInterface) = &o->objVtbl;
        return XA_RESULT_SUCCESS;
    }
    if (o->kind == XaObjKind::Engine && iidEqual(iid, &kIID_ENGINE)) {
        *static_cast<XAEngineItf*>(pInterface) = &o->engVtbl;
        return XA_RESULT_SUCCESS;
    }
    if (o->kind == XaObjKind::OutputMix && iidEqual(iid, &kIID_OUTPUTMIX)) {
        *static_cast<XAOutputMixItf*>(pInterface) = &o->mixVtbl;
        return XA_RESULT_SUCCESS;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult objRegisterCallback(XAObjectItf self, xaObjectCallback callback, void* pContext) {
    if (!self) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    o->callback = callback;
    o->cbContext = pContext;
    return XA_RESULT_SUCCESS;
}
void objAbortAsync(XAObjectItf) {}
void objDestroy(XAObjectItf self) {
    if (!self) {
        return;
    }
    delete fromObj(self);
}
XAresult objSetPriority(XAObjectItf self, XAint32 priority, XAboolean preemptable) {
    if (!self) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    o->priority = priority;
    o->preemptable = preemptable;
    return XA_RESULT_SUCCESS;
}
XAresult objGetPriority(XAObjectItf self, XAint32* pPriority, XAboolean* pPreemptable) {
    if (!self || !pPriority || !pPreemptable) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    auto* o = fromObj(self);
    *pPriority = o->priority;
    *pPreemptable = o->preemptable;
    return XA_RESULT_SUCCESS;
}
XAresult objSetLossOfControl(XAObjectItf, XAuint16, XAInterfaceID*, XAboolean) {
    return XA_RESULT_SUCCESS;
}

const XAObjectItf_ kObjVtbl = {
    objRealize,    objResume,  objGetState,    objGetInterface, objRegisterCallback,
    objAbortAsync, objDestroy, objSetPriority, objGetPriority,  objSetLossOfControl,
};

// --- XAEngineItf ---
XAresult engCreateCamera(XAEngineItf, XAObjectItf* out, XAuint32, XAuint32, const XAInterfaceID*,
                         const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateRadio(XAEngineItf, XAObjectItf* out, XAuint32, const XAInterfaceID*,
                        const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateLed(XAEngineItf, XAObjectItf* out, XAuint32, XAuint32, const XAInterfaceID*,
                      const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateVibra(XAEngineItf, XAObjectItf* out, XAuint32, XAuint32, const XAInterfaceID*,
                        const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateMediaPlayer(XAEngineItf, XAObjectItf* out, void*, void*, void*, void*, void*,
                              void*, XAuint32, const XAInterfaceID*, const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateMediaRecorder(XAEngineItf, XAObjectItf* out, void*, void*, void*, XAuint32,
                                const XAInterfaceID*, const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateOutputMix(XAEngineItf self, XAObjectItf* pMix, XAuint32 /*numInterfaces*/,
                            const XAInterfaceID* /*pInterfaceIds*/,
                            const XAboolean* /*pInterfaceRequired*/) {
    if (!self || !pMix) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    (void)fromEng(self);
    auto* mix = new (std::nothrow) XaObject();
    if (!mix) {
        *pMix = nullptr;
        return XA_RESULT_MEMORY_FAILURE;
    }
    mix->objVtbl = &kObjVtbl;
    mix->mixVtbl = &kMixVtbl;
    mix->kind = XaObjKind::OutputMix;
    mix->state = XA_OBJECT_STATE_UNREALIZED;
    *pMix = &mix->objVtbl;
    return XA_RESULT_SUCCESS;
}
XAresult engCreateMetaExtractor(XAEngineItf, XAObjectItf* out, void*, XAuint32,
                                const XAInterfaceID*, const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engCreateExtObj(XAEngineItf, XAObjectItf* out, void*, XAuint32, XAuint32,
                         const XAInterfaceID*, const XAboolean*) {
    if (out) {
        *out = nullptr;
    }
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engGetImplInfo(XAEngineItf, XAuint32* pMajor, XAuint32* pMinor, XAuint32* pStep,
                        const XAuint8*) {
    if (pMajor) {
        *pMajor = 1;
    }
    if (pMinor) {
        *pMinor = 0;
    }
    if (pStep) {
        *pStep = 1;
    }
    return XA_RESULT_SUCCESS;
}
XAresult engQueryProfiles(XAEngineItf, XAint32* pProfiles) {
    if (!pProfiles) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pProfiles = 1; // XA_PROFILES_MEDIA_PLAYER
    return XA_RESULT_SUCCESS;
}
XAresult engQueryNumIfs(XAEngineItf, XAuint32, XAuint32* pNum) {
    if (!pNum) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pNum = 2;
    return XA_RESULT_SUCCESS;
}
XAresult engQueryIf(XAEngineItf, XAuint32, XAuint32 index, XAInterfaceID* pIid) {
    if (!pIid) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    if (index == 0) {
        *pIid = &kIID_OBJECT;
        return XA_RESULT_SUCCESS;
    }
    if (index == 1) {
        *pIid = &kIID_ENGINE;
        return XA_RESULT_SUCCESS;
    }
    return XA_RESULT_PARAMETER_INVALID;
}
XAresult engQueryNumExts(XAEngineItf, XAuint32* pNum) {
    if (!pNum) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pNum = 0;
    return XA_RESULT_SUCCESS;
}
XAresult engQueryExt(XAEngineItf, XAuint32, XAuint8*, XAint32*) {
    return XA_RESULT_PARAMETER_INVALID;
}
XAresult engIsExtSupported(XAEngineItf, const XAuint8*, XAboolean* pSupported) {
    if (!pSupported) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pSupported = XA_BOOLEAN_FALSE;
    return XA_RESULT_SUCCESS;
}
XAresult engQueryLed(XAEngineItf, XAuint32*, XAuint32*, void*) {
    return XA_RESULT_FEATURE_UNSUPPORTED;
}
XAresult engQueryVibra(XAEngineItf, XAuint32*, XAuint32*, void*) {
    return XA_RESULT_FEATURE_UNSUPPORTED;
}

const XAEngineItf_ kEngVtbl = {
    engCreateCamera,      engCreateRadio,         engCreateLed,       engCreateVibra,
    engCreateMediaPlayer, engCreateMediaRecorder, engCreateOutputMix, engCreateMetaExtractor,
    engCreateExtObj,      engGetImplInfo,         engQueryProfiles,   engQueryNumIfs,
    engQueryIf,           engQueryNumExts,        engQueryExt,        engIsExtSupported,
    engQueryLed,          engQueryVibra,
};

} // namespace

extern "C" {

const XAInterfaceID XA_IID_ENGINE = &kIID_ENGINE;
const XAInterfaceID XA_IID_OBJECT = &kIID_OBJECT;
const XAInterfaceID XA_IID_PLAY = &kIID_PLAY;
const XAInterfaceID XA_IID_VOLUME = &kIID_VOLUME;
const XAInterfaceID XA_IID_OUTPUTMIX = &kIID_OUTPUTMIX;
const XAInterfaceID XA_IID_STREAMINFORMATION = &kIID_STREAMINFORMATION;
const XAInterfaceID XA_IID_ANDROIDBUFFERQUEUESOURCE = &kIID_ANDROIDBUFFERQUEUESOURCE;
const XAInterfaceID XA_IID_METADATAEXTRACTION = &kIID_METADATAEXTRACTION;
const XAInterfaceID XA_IID_SEEK = &kIID_SEEK;
const XAInterfaceID XA_IID_PREFETCHSTATUS = &kIID_PREFETCHSTATUS;

XAresult xaCreateEngine(XAObjectItf* pEngine, XAuint32 /*numOptions*/,
                        const XAEngineOption* /*pEngineOptions*/, XAuint32 numInterfaces,
                        const XAInterfaceID* pInterfaceIds, const XAboolean* pInterfaceRequired) {
    if (!pEngine) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    for (XAuint32 i = 0; i < numInterfaces; ++i) {
        if (pInterfaceRequired && pInterfaceRequired[i] && pInterfaceIds &&
            !iidEqual(pInterfaceIds[i], &kIID_ENGINE) &&
            !iidEqual(pInterfaceIds[i], &kIID_OBJECT)) {
            *pEngine = nullptr;
            return XA_RESULT_FEATURE_UNSUPPORTED;
        }
    }
    auto* eng = new (std::nothrow) XaObject();
    if (!eng) {
        *pEngine = nullptr;
        return XA_RESULT_MEMORY_FAILURE;
    }
    eng->objVtbl = &kObjVtbl;
    eng->engVtbl = &kEngVtbl;
    eng->kind = XaObjKind::Engine;
    eng->state = XA_OBJECT_STATE_UNREALIZED;
    *pEngine = &eng->objVtbl;
    return XA_RESULT_SUCCESS;
}

XAresult xaQueryNumSupportedEngineInterfaces(XAuint32* pNumSupportedInterfaces) {
    if (!pNumSupportedInterfaces) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    *pNumSupportedInterfaces = 2;
    return XA_RESULT_SUCCESS;
}

XAresult xaQuerySupportedEngineInterfaces(XAuint32 index, XAInterfaceID* pInterfaceId) {
    if (!pInterfaceId) {
        return XA_RESULT_PARAMETER_INVALID;
    }
    if (index == 0) {
        *pInterfaceId = XA_IID_OBJECT;
        return XA_RESULT_SUCCESS;
    }
    if (index == 1) {
        *pInterfaceId = XA_IID_ENGINE;
        return XA_RESULT_SUCCESS;
    }
    return XA_RESULT_PARAMETER_INVALID;
}

} // extern "C"
