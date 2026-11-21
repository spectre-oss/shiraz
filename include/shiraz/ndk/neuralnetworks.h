// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct AHardwareBuffer;
typedef struct AHardwareBuffer AHardwareBuffer;

typedef struct ANeuralNetworksDevice ANeuralNetworksDevice;
typedef struct ANeuralNetworksMemory ANeuralNetworksMemory;
typedef struct ANeuralNetworksModel ANeuralNetworksModel;
typedef struct ANeuralNetworksCompilation ANeuralNetworksCompilation;
typedef struct ANeuralNetworksExecution ANeuralNetworksExecution;
typedef struct ANeuralNetworksBurst ANeuralNetworksBurst;
typedef struct ANeuralNetworksEvent ANeuralNetworksEvent;

typedef int32_t ANeuralNetworksOperationType;

enum {
    ANEURALNETWORKS_NO_ERROR = 0,
    ANEURALNETWORKS_OUT_OF_MEMORY = 1,
    ANEURALNETWORKS_INCOMPLETE = 2,
    ANEURALNETWORKS_UNEXPECTED_NULL = 3,
    ANEURALNETWORKS_BAD_DATA = 4,
    ANEURALNETWORKS_OP_FAILED = 5,
    ANEURALNETWORKS_BAD_STATE = 6,
    ANEURALNETWORKS_UNMAPPABLE = 7,
    ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE = 8,
    ANEURALNETWORKS_UNAVAILABLE_DEVICE = 9,
    ANEURALNETWORKS_MISSED_DEADLINE_TRANSIENT = 10,
    ANEURALNETWORKS_MISSED_DEADLINE_PERSISTENT = 11,
    ANEURALNETWORKS_RESOURCE_EXHAUSTED_TRANSIENT = 12,
    ANEURALNETWORKS_RESOURCE_EXHAUSTED_PERSISTENT = 13,
    ANEURALNETWORKS_DEAD_OBJECT = 14,
};

enum {
    ANEURALNETWORKS_FLOAT32 = 0,
    ANEURALNETWORKS_INT32 = 1,
    ANEURALNETWORKS_UINT32 = 2,
    ANEURALNETWORKS_TENSOR_FLOAT32 = 3,
    ANEURALNETWORKS_TENSOR_INT32 = 4,
    ANEURALNETWORKS_TENSOR_QUANT8_ASYMM = 5,
    ANEURALNETWORKS_BOOL = 6,
    ANEURALNETWORKS_TENSOR_QUANT16_SYMM = 7,
    ANEURALNETWORKS_TENSOR_FLOAT16 = 8,
    ANEURALNETWORKS_TENSOR_BOOL8 = 9,
    ANEURALNETWORKS_FLOAT16 = 10,
    ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL = 11,
    ANEURALNETWORKS_TENSOR_QUANT16_ASYMM = 12,
    ANEURALNETWORKS_TENSOR_QUANT8_SYMM = 13,
    ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED = 14,
};

enum {
    ANEURALNETWORKS_ADD = 0,
    ANEURALNETWORKS_CONCATENATION = 2,
    ANEURALNETWORKS_FULLY_CONNECTED = 9,
    ANEURALNETWORKS_LOGISTIC = 14,
    ANEURALNETWORKS_MUL = 18,
    ANEURALNETWORKS_RELU = 19,
    ANEURALNETWORKS_RELU1 = 20,
    ANEURALNETWORKS_RELU6 = 21,
    ANEURALNETWORKS_RESHAPE = 22,
    ANEURALNETWORKS_SOFTMAX = 25,
    ANEURALNETWORKS_TANH = 28,
    ANEURALNETWORKS_SUB = 36,
    ANEURALNETWORKS_TRANSPOSE = 37,
    ANEURALNETWORKS_ARGMAX = 39,
    ANEURALNETWORKS_ARGMIN = 40,
    ANEURALNETWORKS_CAST = 41,
    ANEURALNETWORKS_DIV = 42,
    ANEURALNETWORKS_MEAN = 48,
};

enum {
    ANEURALNETWORKS_FUSED_NONE = 0,
    ANEURALNETWORKS_FUSED_RELU = 1,
    ANEURALNETWORKS_FUSED_RELU1 = 2,
    ANEURALNETWORKS_FUSED_RELU6 = 3,
};

enum {
    ANEURALNETWORKS_DEVICE_UNKNOWN = 0,
    ANEURALNETWORKS_DEVICE_OTHER = 1,
    ANEURALNETWORKS_DEVICE_CPU = 2,
    ANEURALNETWORKS_DEVICE_GPU = 3,
    ANEURALNETWORKS_DEVICE_ACCELERATOR = 4,
};

enum {
    ANEURALNETWORKS_PREFER_LOW_POWER = 0,
    ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER = 1,
    ANEURALNETWORKS_PREFER_SUSTAINED_SPEED = 2,
};

enum {
    ANEURALNETWORKS_PRIORITY_LOW = 90,
    ANEURALNETWORKS_PRIORITY_MEDIUM = 100,
    ANEURALNETWORKS_PRIORITY_HIGH = 110,
    ANEURALNETWORKS_PRIORITY_DEFAULT = ANEURALNETWORKS_PRIORITY_MEDIUM,
};

enum {
    ANEURALNETWORKS_FEATURE_LEVEL_1 = 27,
    ANEURALNETWORKS_FEATURE_LEVEL_2 = 28,
    ANEURALNETWORKS_FEATURE_LEVEL_3 = 29,
    ANEURALNETWORKS_FEATURE_LEVEL_4 = 30,
    ANEURALNETWORKS_FEATURE_LEVEL_5 = 31,
    ANEURALNETWORKS_FEATURE_LEVEL_6 = 1000006,
    ANEURALNETWORKS_FEATURE_LEVEL_7 = 1000007,
    ANEURALNETWORKS_FEATURE_LEVEL_8 = 1000008,
    ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN = 32,
};

typedef struct ANeuralNetworksOperandType {
    int32_t type;
    uint32_t dimensionCount;
    const uint32_t* dimensions;
    float scale;
    int32_t zeroPoint;
} ANeuralNetworksOperandType;

// Devices & runtime
int ANeuralNetworks_getDeviceCount(uint32_t* numDevices);
int ANeuralNetworks_getDevice(uint32_t devIndex, ANeuralNetworksDevice** device);
int64_t ANeuralNetworks_getRuntimeFeatureLevel(void);
int ANeuralNetworksDevice_getName(const ANeuralNetworksDevice* device, const char** name);
int ANeuralNetworksDevice_getType(const ANeuralNetworksDevice* device, int32_t* type);
int ANeuralNetworksDevice_getVersion(const ANeuralNetworksDevice* device, const char** version);
int ANeuralNetworksDevice_getFeatureLevel(const ANeuralNetworksDevice* device,
                                          int64_t* featureLevel);
int ANeuralNetworksDevice_wait(const ANeuralNetworksDevice* device);

// Memory
int ANeuralNetworksMemory_createFromFd(size_t size, int protect, int fd, size_t offset,
                                       ANeuralNetworksMemory** memory);
int ANeuralNetworksMemory_createFromAHardwareBuffer(const AHardwareBuffer* ahwb,
                                                    ANeuralNetworksMemory** memory);
void ANeuralNetworksMemory_free(ANeuralNetworksMemory* memory);

// Model
int ANeuralNetworksModel_create(ANeuralNetworksModel** model);
void ANeuralNetworksModel_free(ANeuralNetworksModel* model);
int ANeuralNetworksModel_finish(ANeuralNetworksModel* model);
int ANeuralNetworksModel_addOperand(ANeuralNetworksModel* model,
                                    const ANeuralNetworksOperandType* type);
int ANeuralNetworksModel_setOperandValue(ANeuralNetworksModel* model, int32_t index,
                                         const void* buffer, size_t length);
int ANeuralNetworksModel_setOperandValueFromMemory(ANeuralNetworksModel* model, int32_t index,
                                                   const ANeuralNetworksMemory* memory,
                                                   size_t offset, size_t length);
int ANeuralNetworksModel_addOperation(ANeuralNetworksModel* model,
                                      ANeuralNetworksOperationType type, uint32_t inputCount,
                                      const uint32_t* inputs, uint32_t outputCount,
                                      const uint32_t* outputs);
int ANeuralNetworksModel_identifyInputsAndOutputs(ANeuralNetworksModel* model, uint32_t inputCount,
                                                  const uint32_t* inputs, uint32_t outputCount,
                                                  const uint32_t* outputs);
int ANeuralNetworksModel_relaxComputationFloat32toFloat16(ANeuralNetworksModel* model, bool allow);
int ANeuralNetworksModel_getSupportedOperationsForDevices(
    const ANeuralNetworksModel* model, const ANeuralNetworksDevice* const* devices,
    uint32_t numDevices, bool* supportedOps);

// Compilation
int ANeuralNetworksCompilation_create(ANeuralNetworksModel* model,
                                      ANeuralNetworksCompilation** compilation);
int ANeuralNetworksCompilation_createForDevices(ANeuralNetworksModel* model,
                                                const ANeuralNetworksDevice* const* devices,
                                                uint32_t numDevices,
                                                ANeuralNetworksCompilation** compilation);
void ANeuralNetworksCompilation_free(ANeuralNetworksCompilation* compilation);
int ANeuralNetworksCompilation_setPreference(ANeuralNetworksCompilation* compilation,
                                             int32_t preference);
int ANeuralNetworksCompilation_setPriority(ANeuralNetworksCompilation* compilation, int priority);
int ANeuralNetworksCompilation_setCaching(ANeuralNetworksCompilation* compilation,
                                          const char* cacheDir, const uint8_t* token);
int ANeuralNetworksCompilation_finish(ANeuralNetworksCompilation* compilation);

// Execution, Burst & Event
int ANeuralNetworksExecution_create(ANeuralNetworksCompilation* compilation,
                                    ANeuralNetworksExecution** execution);
void ANeuralNetworksExecution_free(ANeuralNetworksExecution* execution);
int ANeuralNetworksExecution_setInput(ANeuralNetworksExecution* execution, int32_t index,
                                      const ANeuralNetworksOperandType* type, const void* buffer,
                                      size_t length);
int ANeuralNetworksExecution_setInputFromMemory(ANeuralNetworksExecution* execution, int32_t index,
                                                const ANeuralNetworksOperandType* type,
                                                const ANeuralNetworksMemory* memory, size_t offset,
                                                size_t length);
int ANeuralNetworksExecution_setOutput(ANeuralNetworksExecution* execution, int32_t index,
                                       const ANeuralNetworksOperandType* type, void* buffer,
                                       size_t length);
int ANeuralNetworksExecution_setOutputFromMemory(ANeuralNetworksExecution* execution, int32_t index,
                                                 const ANeuralNetworksOperandType* type,
                                                 const ANeuralNetworksMemory* memory, size_t offset,
                                                 size_t length);
int ANeuralNetworksExecution_startCompute(ANeuralNetworksExecution* execution,
                                          ANeuralNetworksEvent** event);
int ANeuralNetworksExecution_compute(ANeuralNetworksExecution* execution);
int ANeuralNetworksExecution_burstCompute(ANeuralNetworksExecution* execution,
                                          ANeuralNetworksBurst* burst);
int ANeuralNetworksExecution_getOutputOperandRank(ANeuralNetworksExecution* execution,
                                                  int32_t index, uint32_t* rank);
int ANeuralNetworksExecution_getOutputOperandDimensions(ANeuralNetworksExecution* execution,
                                                        int32_t index, uint32_t* dimensions);

int ANeuralNetworksBurst_create(ANeuralNetworksCompilation* compilation,
                                ANeuralNetworksBurst** burst);
void ANeuralNetworksBurst_free(ANeuralNetworksBurst* burst);
int ANeuralNetworksEvent_wait(ANeuralNetworksEvent* event);
void ANeuralNetworksEvent_free(ANeuralNetworksEvent* event);

#ifdef __cplusplus
}
#endif
