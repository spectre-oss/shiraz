// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N6: libamidi.so (14/14),
// libcamera2ndk.so (45/45), and libOpenMAXAL.so (13/13).
#include "shiraz/ndk/amidi.h"
#include "shiraz/ndk/camera2ndk.h"
#include "shiraz/ndk/openmaxal.h"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace {

void testAmidi() {
    AMidiDevice* dev = nullptr;
    assert(AMidiDevice_fromJava(nullptr, nullptr, &dev) == 0 && dev != nullptr);
    assert(AMidiDevice_getType(dev) == AMIDI_DEVICE_TYPE_VIRTUAL);
    assert(AMidiDevice_getNumInputPorts(dev) == 1);
    assert(AMidiDevice_getNumOutputPorts(dev) == 1);
    assert(AMidiDevice_getDefaultProtocol(dev) == AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS);

    AMidiInputPort* inPort = nullptr;
    AMidiOutputPort* outPort = nullptr;
    assert(AMidiInputPort_open(dev, 0, &inPort) == 0 && inPort != nullptr);
    assert(AMidiOutputPort_open(dev, 0, &outPort) == 0 && outPort != nullptr);

    const uint8_t noteOn[3] = {0x90, 0x3C, 0x7F};
    assert(AMidiInputPort_sendWithTimestamp(inPort, noteOn, 3, 123456789LL) == 3);

    int32_t opcode = 0;
    uint8_t rx[16] = {};
    size_t rxLen = 0;
    int64_t ts = 0;
    assert(AMidiOutputPort_receive(outPort, &opcode, rx, sizeof(rx), &rxLen, &ts) == 1);
    assert(opcode == AMIDI_OPCODE_DATA && rxLen == 3 && ts == 123456789LL);
    assert(std::memcmp(rx, noteOn, 3) == 0);

    // Flush message
    assert(AMidiInputPort_sendFlush(inPort) == 0);
    assert(AMidiOutputPort_receive(outPort, &opcode, rx, sizeof(rx), &rxLen, &ts) == 1);
    assert(opcode == AMIDI_OPCODE_FLUSH && rxLen == 0);
    assert(AMidiOutputPort_receive(outPort, &opcode, rx, sizeof(rx), &rxLen, &ts) == 0);

    AMidiInputPort_close(inPort);
    AMidiOutputPort_close(outPort);
    assert(AMidiDevice_release(dev) == 0);
}

void testCamera2Ndk() {
    ACameraManager* mgr = ACameraManager_create();
    assert(mgr != nullptr);

    ACameraIdList* list = nullptr;
    assert(ACameraManager_getCameraIdList(mgr, &list) == ACAMERA_OK && list != nullptr);
    assert(list->numCameras == 0);
    ACameraManager_deleteCameraIdList(list);

    ACameraManager_AvailabilityCallbacks avail{nullptr, nullptr, nullptr};
    assert(ACameraManager_registerAvailabilityCallback(mgr, &avail) == ACAMERA_OK);
    assert(ACameraManager_unregisterAvailabilityCallback(mgr, &avail) == ACAMERA_OK);

    // Honest hardware boundary: no physical camera attached on desktop.
    ACameraMetadata* chars = nullptr;
    assert(ACameraManager_getCameraCharacteristics(mgr, "0", &chars) ==
           ACAMERA_ERROR_CAMERA_DISCONNECTED);
    ACameraDevice_StateCallbacks devCbs{nullptr, nullptr, nullptr};
    ACameraDevice* dev = nullptr;
    assert(ACameraManager_openCamera(mgr, "0", &devCbs, &dev) == ACAMERA_ERROR_CAMERA_DISCONNECTED);

    // Full typed ACaptureRequest metadata store & copy
    ACaptureRequest* req = shiraz_capture_request_create();
    assert(req != nullptr);
    const uint8_t u8v[2] = {1, 2};
    const int32_t i32v = 60;
    const float fv = 1.5f;
    const int64_t i64v = 33333333LL;
    const double dv = 2.71828;
    const ACameraMetadata_rational rv = {30, 1};
    assert(ACaptureRequest_setEntry_u8(req, 100, 2, u8v) == ACAMERA_OK);
    assert(ACaptureRequest_setEntry_i32(req, 101, 1, &i32v) == ACAMERA_OK);
    assert(ACaptureRequest_setEntry_float(req, 102, 1, &fv) == ACAMERA_OK);
    assert(ACaptureRequest_setEntry_i64(req, 103, 1, &i64v) == ACAMERA_OK);
    assert(ACaptureRequest_setEntry_double(req, 104, 1, &dv) == ACAMERA_OK);
    assert(ACaptureRequest_setEntry_rational(req, 105, 1, &rv) == ACAMERA_OK);

    int32_t numTags = 0;
    const uint32_t* tags = nullptr;
    assert(ACaptureRequest_getAllTags(req, &numTags, &tags) == ACAMERA_OK && numTags == 6);

    ACameraMetadata_const_entry entry{};
    assert(ACaptureRequest_getConstEntry(req, 105, &entry) == ACAMERA_OK);
    assert(entry.type == ACAMERA_TYPE_RATIONAL && entry.count == 1 &&
           entry.data.r[0].numerator == 30);

    ACaptureRequest* reqCopy = ACaptureRequest_copy(req);
    assert(reqCopy != nullptr);
    assert(ACaptureRequest_getConstEntry(reqCopy, 103, &entry) == ACAMERA_OK &&
           entry.data.i64[0] == 33333333LL);

    // Output target & session output container
    int dummyWinStorage = 0;
    auto* fakeWin = reinterpret_cast<ANativeWindow*>(&dummyWinStorage);
    ACameraOutputTarget* target = nullptr;
    assert(ACameraOutputTarget_create(fakeWin, &target) == ACAMERA_OK);
    assert(ACaptureRequest_addTarget(req, target) == ACAMERA_OK);
    assert(ACaptureRequest_removeTarget(req, target) == ACAMERA_OK);
    ACameraOutputTarget_free(target);

    ACaptureSessionOutput* sOut = nullptr;
    ACaptureSessionOutputContainer* container = nullptr;
    assert(ACaptureSessionOutput_create(fakeWin, &sOut) == ACAMERA_OK);
    assert(ACaptureSessionOutputContainer_create(&container) == ACAMERA_OK);
    assert(ACaptureSessionOutputContainer_add(container, sOut) == ACAMERA_OK);
    assert(ACaptureSessionOutputContainer_remove(container, sOut) == ACAMERA_OK);
    ACaptureSessionOutputContainer_free(container);
    ACaptureSessionOutput_free(sOut);

    ACaptureRequest_free(reqCopy);
    ACaptureRequest_free(req);

    ACameraMetadata* meta = shiraz_camera_metadata_create();
    assert(meta != nullptr);
    assert(!ACameraMetadata_isLogicalMultiCamera(meta, nullptr, nullptr));
    ACameraMetadata* metaCopy = ACameraMetadata_copy(meta);
    assert(metaCopy != nullptr);
    ACameraMetadata_free(metaCopy);
    ACameraMetadata_free(meta);

    ACameraManager_delete(mgr);
}

void testOpenMaxAl() {
    XAuint32 numIfs = 0;
    assert(xaQueryNumSupportedEngineInterfaces(&numIfs) == XA_RESULT_SUCCESS && numIfs == 2);
    XAInterfaceID iid = nullptr;
    assert(xaQuerySupportedEngineInterfaces(1, &iid) == XA_RESULT_SUCCESS && iid == XA_IID_ENGINE);

    XAObjectItf engineObj = nullptr;
    assert(xaCreateEngine(&engineObj, 0, nullptr, 0, nullptr, nullptr) == XA_RESULT_SUCCESS);
    assert(engineObj != nullptr);

    XAuint32 state = 0;
    assert((*engineObj)->GetState(engineObj, &state) == XA_RESULT_SUCCESS &&
           state == XA_OBJECT_STATE_UNREALIZED);
    assert((*engineObj)->Realize(engineObj, XA_BOOLEAN_FALSE) == XA_RESULT_SUCCESS);
    assert((*engineObj)->GetState(engineObj, &state) == XA_RESULT_SUCCESS &&
           state == XA_OBJECT_STATE_REALIZED);

    XAEngineItf engine = nullptr;
    assert((*engineObj)->GetInterface(engineObj, XA_IID_ENGINE, &engine) == XA_RESULT_SUCCESS &&
           engine != nullptr);

    XAObjectItf mixObj = nullptr;
    assert((*engine)->CreateOutputMix(engine, &mixObj, 0, nullptr, nullptr) == XA_RESULT_SUCCESS);
    assert((*mixObj)->Realize(mixObj, XA_BOOLEAN_FALSE) == XA_RESULT_SUCCESS);
    XAOutputMixItf mix = nullptr;
    assert((*mixObj)->GetInterface(mixObj, XA_IID_OUTPUTMIX, &mix) == XA_RESULT_SUCCESS &&
           mix != nullptr);
    (*mixObj)->Destroy(mixObj);

    // Unbridged hardware media player returns explicit XA_RESULT_FEATURE_UNSUPPORTED
    XAObjectItf player = nullptr;
    assert((*engine)->CreateMediaPlayer(engine, &player, nullptr, nullptr, nullptr, nullptr,
                                        nullptr, nullptr, 0, nullptr,
                                        nullptr) == XA_RESULT_FEATURE_UNSUPPORTED);
    assert(player == nullptr);

    (*engineObj)->Destroy(engineObj);
}

} // namespace

int main() {
    testAmidi();
    testCamera2Ndk();
    testOpenMaxAl();
    std::printf("ndk_midi_camera_xa: ALL TESTS PASSED\n");
    return 0;
}
