// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N7: libbinder_ndk.so (83/83) and
// libneuralnetworks.so (43/43).
#include "shiraz/ndk/binder_ndk.h"
#include "shiraz/ndk/neuralnetworks.h"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>

namespace {

struct EchoService {
    int calls = 0;
    bool destroyed = false;
};

bool gServiceDestroyed = false;
bool gDeathUnlinked = false;

void* echoCreate(void* args) {
    return args;
}

void echoDestroy(void* ud) {
    auto* svc = static_cast<EchoService*>(ud);
    svc->destroyed = true;
    gServiceDestroyed = true;
}

bool stdStringAlloc(void* data, int32_t len, char** buf) {
    auto* s = static_cast<std::string*>(data);
    if (len < 0) {
        s->clear();
        *buf = nullptr;
        return true;
    }
    s->resize(static_cast<size_t>(len));
    *buf = s->data();
    return true;
}

bool byteVecAlloc(void* data, int32_t len, int8_t** buf) {
    auto* v = static_cast<std::vector<int8_t>*>(data);
    if (len < 0) {
        v->clear();
        *buf = nullptr;
        return true;
    }
    v->resize(static_cast<size_t>(len));
    *buf = v->data();
    return true;
}

bool int32VecAlloc(void* data, int32_t len, int32_t** buf) {
    auto* v = static_cast<std::vector<int32_t>*>(data);
    if (len < 0) {
        v->clear();
        *buf = nullptr;
        return true;
    }
    v->resize(static_cast<size_t>(len));
    *buf = v->data();
    return true;
}

binder_status_t echoTransact(AIBinder* binder, transaction_code_t code, const AParcel* in,
                             AParcel* out) {
    auto* svc = static_cast<EchoService*>(AIBinder_getUserData(binder));
    svc->calls++;
    if (code == 1) {
        int32_t a = 0, b = 0;
        assert(AParcel_readInt32(in, &a) == STATUS_OK);
        assert(AParcel_readInt32(in, &b) == STATUS_OK);
        std::string msg;
        assert(AParcel_readString(in, &msg, stdStringAlloc) == STATUS_OK);
        if (!msg.empty() && msg.back() == '\0') {
            msg.pop_back();
        }
        AStatus* ok = AStatus_newOk();
        assert(AParcel_writeStatusHeader(out, ok) == STATUS_OK);
        AStatus_delete(ok);
        assert(AParcel_writeInt32(out, a + b) == STATUS_OK);
        const std::string reply = "echo:" + msg;
        assert(AParcel_writeString(out, reply.c_str(), static_cast<int32_t>(reply.size())) ==
               STATUS_OK);
        return STATUS_OK;
    }
    return STATUS_UNKNOWN_TRANSACTION;
}

void onDiedCb(void*) {}
void onUnlinkedCb(void*) {
    gDeathUnlinked = true;
}

void testBinderNdk() {
    assert(ABinderProcess_setThreadPoolMaxThreadCount(4));
    ABinderProcess_startThreadPool();
    assert(ABinderProcess_isThreadPoolStarted());

    AIBinder_Class* clazz =
        AIBinder_Class_define("com.shiraz.IEcho", echoCreate, echoDestroy, echoTransact);
    assert(clazz != nullptr);
    assert(std::strcmp(AIBinder_Class_getDescriptor(clazz), "com.shiraz.IEcho") == 0);

    EchoService svc;
    AIBinder* binder = AIBinder_new(clazz, &svc);
    assert(binder != nullptr);
    assert(AIBinder_isAlive(binder));
    assert(!AIBinder_isRemote(binder));
    assert(AIBinder_ping(binder) == STATUS_OK);
    assert(AIBinder_associateClass(binder, clazz));
    assert(AIBinder_getClass(binder) == clazz);
    assert(AIBinder_debugGetRefCount(binder) == 1);
    assert(AIBinder_getCallingPid() == ::getpid());
    assert(AIBinder_getCallingUid() == ::getuid());

    AIBinder_Weak* weak = AIBinder_Weak_new(binder);
    AIBinder* promoted = AIBinder_Weak_promote(weak);
    assert(promoted == binder);
    assert(AIBinder_debugGetRefCount(binder) == 2);
    AIBinder_decStrong(promoted);

    AIBinder_DeathRecipient* dr = AIBinder_DeathRecipient_new(onDiedCb);
    AIBinder_DeathRecipient_setOnUnlinked(dr, onUnlinkedCb);
    assert(AIBinder_linkToDeath(binder, dr, nullptr) == STATUS_OK);

    // Real in-process transaction round-trip!
    AParcel* in = nullptr;
    assert(AIBinder_prepareTransaction(binder, &in) == STATUS_OK && in != nullptr);
    assert(AParcel_writeInt32(in, 17) == STATUS_OK);
    assert(AParcel_writeInt32(in, 25) == STATUS_OK);
    assert(AParcel_writeString(in, "shiraz", 6) == STATUS_OK);

    AParcel* out = nullptr;
    assert(AIBinder_transact(binder, 1, &in, &out, 0) == STATUS_OK);
    assert(in == nullptr && out != nullptr && svc.calls == 1);

    AStatus* st = nullptr;
    assert(AParcel_readStatusHeader(out, &st) == STATUS_OK && AStatus_isOk(st));
    AStatus_delete(st);
    int32_t sum = 0;
    assert(AParcel_readInt32(out, &sum) == STATUS_OK && sum == 42);
    std::string reply;
    assert(AParcel_readString(out, &reply, stdStringAlloc) == STATUS_OK);
    if (!reply.empty() && reply.back() == '\0') {
        reply.pop_back();
    }
    assert(reply == "echo:shiraz");
    AParcel_delete(out);

    // Standalone AParcel rich types + AStatus error round-trip
    AParcel* p = AParcel_create();
    const int8_t bytes[3] = {10, 20, 30};
    const int32_t ints[2] = {-5, 999};
    assert(AParcel_writeDouble(p, 3.14159) == STATUS_OK);
    assert(AParcel_writeBool(p, true) == STATUS_OK);
    assert(AParcel_writeByteArray(p, bytes, 3) == STATUS_OK);
    assert(AParcel_writeInt32Array(p, ints, 2) == STATUS_OK);
    assert(AParcel_writeStrongBinder(p, binder) == STATUS_OK);
    AStatus* errSt = AStatus_fromServiceSpecificErrorWithMessage(77, "boom");
    assert(!AStatus_isOk(errSt) && AStatus_getServiceSpecificError(errSt) == 77);
    const char* desc = AStatus_getDescription(errSt);
    assert(desc != nullptr && std::strstr(desc, "boom") != nullptr);
    AStatus_deleteDescription(desc);
    assert(AParcel_writeStatusHeader(p, errSt) == STATUS_OK);
    AStatus_delete(errSt);

    assert(AParcel_getDataSize(p) > 0);
    assert(AParcel_setDataPosition(p, 0) == STATUS_OK);
    assert(AParcel_getDataPosition(p) == 0);

    double d = 0.0;
    bool bv = false;
    std::vector<int8_t> outBytes;
    std::vector<int32_t> outInts;
    AIBinder* readBinder = nullptr;
    AStatus* readSt = nullptr;
    assert(AParcel_readDouble(p, &d) == STATUS_OK && std::fabs(d - 3.14159) < 1e-6);
    assert(AParcel_readBool(p, &bv) == STATUS_OK && bv);
    assert(AParcel_readByteArray(p, &outBytes, byteVecAlloc) == STATUS_OK && outBytes.size() == 3 &&
           outBytes[2] == 30);
    assert(AParcel_readInt32Array(p, &outInts, int32VecAlloc) == STATUS_OK && outInts.size() == 2 &&
           outInts[1] == 999);
    assert(AParcel_readStrongBinder(p, &readBinder) == STATUS_OK && readBinder == binder);
    assert(AParcel_readStatusHeader(p, &readSt) == STATUS_OK &&
           AStatus_getServiceSpecificError(readSt) == 77 &&
           std::strcmp(AStatus_getMessage(readSt), "boom") == 0);
    AStatus_delete(readSt);
    AIBinder_decStrong(readBinder);
    AParcel_delete(p);

    // Destroy binder -> weak promotion returns nullptr and death unlinked fires
    AIBinder_decStrong(binder);
    assert(gServiceDestroyed && gDeathUnlinked);
    assert(AIBinder_Weak_promote(weak) == nullptr);
    AIBinder_Weak_delete(weak);
    AIBinder_DeathRecipient_delete(dr);
}

void testNeuralNetworks() {
    uint32_t numDevices = 0;
    assert(ANeuralNetworks_getDeviceCount(&numDevices) == ANEURALNETWORKS_NO_ERROR &&
           numDevices == 1);
    ANeuralNetworksDevice* dev = nullptr;
    assert(ANeuralNetworks_getDevice(0, &dev) == ANEURALNETWORKS_NO_ERROR && dev != nullptr);
    const char* name = nullptr;
    int32_t devType = 0;
    int64_t feat = 0;
    assert(ANeuralNetworksDevice_getName(dev, &name) == ANEURALNETWORKS_NO_ERROR &&
           std::strcmp(name, "shiraz-nnapi-cpu") == 0);
    assert(ANeuralNetworksDevice_getType(dev, &devType) == ANEURALNETWORKS_NO_ERROR &&
           devType == ANEURALNETWORKS_DEVICE_CPU);
    assert(ANeuralNetworksDevice_getFeatureLevel(dev, &feat) == ANEURALNETWORKS_NO_ERROR &&
           feat == ANEURALNETWORKS_FEATURE_LEVEL_8);
    assert(ANeuralNetworks_getRuntimeFeatureLevel() == ANEURALNETWORKS_FEATURE_LEVEL_8);

    // Build a real 2-op model: FULLY_CONNECTED (2->2) + SOFTMAX!
    ANeuralNetworksModel* model = nullptr;
    assert(ANeuralNetworksModel_create(&model) == ANEURALNETWORKS_NO_ERROR);

    const uint32_t xDims[2] = {1, 2};
    const uint32_t wDims[2] = {2, 2};
    const uint32_t bDims[1] = {2};
    ANeuralNetworksOperandType tX = {ANEURALNETWORKS_TENSOR_FLOAT32, 2, xDims, 0.0f, 0};
    ANeuralNetworksOperandType tW = {ANEURALNETWORKS_TENSOR_FLOAT32, 2, wDims, 0.0f, 0};
    ANeuralNetworksOperandType tB = {ANEURALNETWORKS_TENSOR_FLOAT32, 1, bDims, 0.0f, 0};
    ANeuralNetworksOperandType tInt = {ANEURALNETWORKS_INT32, 0, nullptr, 0.0f, 0};
    ANeuralNetworksOperandType tFloat = {ANEURALNETWORKS_FLOAT32, 0, nullptr, 0.0f, 0};

    // Operands: 0=X, 1=W, 2=B, 3=act(NONE), 4=FC_OUT, 5=beta(1.0), 6=SOFTMAX_OUT
    assert(ANeuralNetworksModel_addOperand(model, &tX) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tW) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tB) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tInt) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tX) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tFloat) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_addOperand(model, &tX) == ANEURALNETWORKS_NO_ERROR);

    // Weights: [[1, 0], [0, 2]], Bias: [0, 0] -> for X=[1, 1], FC=[1, 2]
    const float wVals[4] = {1.0f, 0.0f, 0.0f, 2.0f};
    const float bVals[2] = {0.0f, 0.0f};
    const int32_t actNone = ANEURALNETWORKS_FUSED_NONE;
    const float beta = 1.0f;
    assert(ANeuralNetworksModel_setOperandValue(model, 1, wVals, sizeof(wVals)) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_setOperandValue(model, 2, bVals, sizeof(bVals)) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_setOperandValue(model, 3, &actNone, sizeof(actNone)) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_setOperandValue(model, 5, &beta, sizeof(beta)) ==
           ANEURALNETWORKS_NO_ERROR);

    const uint32_t fcIn[4] = {0, 1, 2, 3};
    const uint32_t fcOut[1] = {4};
    assert(ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_FULLY_CONNECTED, 4, fcIn, 1,
                                             fcOut) == ANEURALNETWORKS_NO_ERROR);

    const uint32_t smIn[2] = {4, 5};
    const uint32_t smOut[1] = {6};
    assert(ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_SOFTMAX, 2, smIn, 1, smOut) ==
           ANEURALNETWORKS_NO_ERROR);

    const uint32_t mIn[1] = {0};
    const uint32_t mOut[1] = {6};
    assert(ANeuralNetworksModel_identifyInputsAndOutputs(model, 1, mIn, 1, mOut) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksModel_finish(model) == ANEURALNETWORKS_NO_ERROR);

    bool supported[2] = {false, false};
    const ANeuralNetworksDevice* devs[1] = {dev};
    assert(ANeuralNetworksModel_getSupportedOperationsForDevices(model, devs, 1, supported) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(supported[0] && supported[1]);

    ANeuralNetworksCompilation* comp = nullptr;
    assert(ANeuralNetworksCompilation_createForDevices(model, devs, 1, &comp) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksCompilation_setPreference(
               comp, ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksCompilation_setPriority(comp, ANEURALNETWORKS_PRIORITY_HIGH) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksCompilation_finish(comp) == ANEURALNETWORKS_NO_ERROR);

    ANeuralNetworksExecution* exec = nullptr;
    assert(ANeuralNetworksExecution_create(comp, &exec) == ANEURALNETWORKS_NO_ERROR);

    const float xInput[2] = {1.0f, 1.0f};
    float yOutput[2] = {0.0f, 0.0f};
    assert(ANeuralNetworksExecution_setInput(exec, 0, nullptr, xInput, sizeof(xInput)) ==
           ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksExecution_setOutput(exec, 0, nullptr, yOutput, sizeof(yOutput)) ==
           ANEURALNETWORKS_NO_ERROR);

    ANeuralNetworksEvent* ev = nullptr;
    assert(ANeuralNetworksExecution_startCompute(exec, &ev) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksEvent_wait(ev) == ANEURALNETWORKS_NO_ERROR);
    ANeuralNetworksEvent_free(ev);

    // Expected softmax([1.0, 2.0]) = [e^1/(e^1+e^2), e^2/(e^1+e^2)] = [0.2689414, 0.7310586]
    const float e1 = std::exp(1.0f);
    const float e2 = std::exp(2.0f);
    assert(std::fabs(yOutput[0] - e1 / (e1 + e2)) < 1e-5f);
    assert(std::fabs(yOutput[1] - e2 / (e1 + e2)) < 1e-5f);

    uint32_t rank = 0;
    uint32_t dims[2] = {0, 0};
    assert(ANeuralNetworksExecution_getOutputOperandRank(exec, 0, &rank) ==
               ANEURALNETWORKS_NO_ERROR &&
           rank == 2);
    assert(ANeuralNetworksExecution_getOutputOperandDimensions(exec, 0, dims) ==
               ANEURALNETWORKS_NO_ERROR &&
           dims[0] == 1 && dims[1] == 2);

    ANeuralNetworksBurst* burst = nullptr;
    assert(ANeuralNetworksBurst_create(comp, &burst) == ANEURALNETWORKS_NO_ERROR);
    assert(ANeuralNetworksExecution_burstCompute(exec, burst) == ANEURALNETWORKS_NO_ERROR);
    ANeuralNetworksBurst_free(burst);

    ANeuralNetworksExecution_free(exec);
    ANeuralNetworksCompilation_free(comp);
    ANeuralNetworksModel_free(model);
}

} // namespace

int main() {
    testBinderNdk();
    testNeuralNetworks();
    std::printf("ndk_binder_nnapi: ALL TESTS PASSED\n");
    return 0;
}
