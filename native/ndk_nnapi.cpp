// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK libneuralnetworks.so CPU reference tensor execution engine.
#include "shiraz/ndk/neuralnetworks.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <map>
#include <new>
#include <string>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>

struct ANeuralNetworksDevice {
    const char* name = "shiraz-nnapi-cpu";
    const char* version = "1.0.0";
    int32_t type = ANEURALNETWORKS_DEVICE_CPU;
    int64_t featureLevel = ANEURALNETWORKS_FEATURE_LEVEL_8;
};

struct ANeuralNetworksMemory {
    uint8_t* ptr = nullptr;
    size_t size = 0;
    bool mapped = false;
    std::vector<uint8_t> owned;

    ~ANeuralNetworksMemory() {
        if (mapped && ptr && ptr != MAP_FAILED) {
            ::munmap(ptr, size);
        }
    }
};

struct OperandInfo {
    int32_t type = ANEURALNETWORKS_FLOAT32;
    std::vector<uint32_t> dims;
    float scale = 0.0f;
    int32_t zeroPoint = 0;
    std::vector<uint8_t> constData;
    bool hasConst = false;
};

struct OperationInfo {
    ANeuralNetworksOperationType type = 0;
    std::vector<uint32_t> inputs;
    std::vector<uint32_t> outputs;
};

struct ANeuralNetworksModel {
    std::vector<OperandInfo> operands;
    std::vector<OperationInfo> operations;
    std::vector<uint32_t> modelInputs;
    std::vector<uint32_t> modelOutputs;
    bool relaxFp16 = false;
    bool finished = false;
};

struct ANeuralNetworksCompilation {
    const ANeuralNetworksModel* model = nullptr;
    int32_t preference = ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER;
    int priority = ANEURALNETWORKS_PRIORITY_DEFAULT;
    bool finished = false;
};

struct IoBinding {
    void* ptr = nullptr;
    size_t length = 0;
    std::vector<uint32_t> dims;
};

struct ANeuralNetworksExecution {
    const ANeuralNetworksCompilation* compilation = nullptr;
    std::map<int32_t, IoBinding> inputs;
    std::map<int32_t, IoBinding> outputs;
    std::map<int32_t, std::vector<uint32_t>> outputDims;
};

struct ANeuralNetworksBurst {
    const ANeuralNetworksCompilation* compilation = nullptr;
};

struct ANeuralNetworksEvent {
    int status = ANEURALNETWORKS_NO_ERROR;
};

namespace {

ANeuralNetworksDevice gCpuDevice;

size_t elemSize(int32_t type) {
    switch (type) {
    case ANEURALNETWORKS_FLOAT32:
    case ANEURALNETWORKS_TENSOR_FLOAT32:
    case ANEURALNETWORKS_INT32:
    case ANEURALNETWORKS_TENSOR_INT32:
    case ANEURALNETWORKS_UINT32:
        return 4;
    case ANEURALNETWORKS_FLOAT16:
    case ANEURALNETWORKS_TENSOR_FLOAT16:
    case ANEURALNETWORKS_TENSOR_QUANT16_SYMM:
    case ANEURALNETWORKS_TENSOR_QUANT16_ASYMM:
        return 2;
    case ANEURALNETWORKS_BOOL:
    case ANEURALNETWORKS_TENSOR_BOOL8:
    case ANEURALNETWORKS_TENSOR_QUANT8_ASYMM:
    case ANEURALNETWORKS_TENSOR_QUANT8_SYMM:
    case ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED:
        return 1;
    default:
        return 0;
    }
}

size_t numElements(const std::vector<uint32_t>& dims) {
    if (dims.empty()) {
        return 1;
    }
    size_t n = 1;
    for (uint32_t d : dims) {
        n *= d;
    }
    return n;
}

bool isOpSupported(ANeuralNetworksOperationType op) {
    switch (op) {
    case ANEURALNETWORKS_ADD:
    case ANEURALNETWORKS_SUB:
    case ANEURALNETWORKS_MUL:
    case ANEURALNETWORKS_DIV:
    case ANEURALNETWORKS_FULLY_CONNECTED:
    case ANEURALNETWORKS_RELU:
    case ANEURALNETWORKS_RELU1:
    case ANEURALNETWORKS_RELU6:
    case ANEURALNETWORKS_LOGISTIC:
    case ANEURALNETWORKS_TANH:
    case ANEURALNETWORKS_SOFTMAX:
    case ANEURALNETWORKS_RESHAPE:
    case ANEURALNETWORKS_CONCATENATION:
    case ANEURALNETWORKS_MEAN:
    case ANEURALNETWORKS_ARGMAX:
    case ANEURALNETWORKS_ARGMIN:
    case ANEURALNETWORKS_TRANSPOSE:
    case ANEURALNETWORKS_CAST:
        return true;
    default:
        return false;
    }
}

float applyActivation(float v, int32_t act) {
    switch (act) {
    case ANEURALNETWORKS_FUSED_RELU:
        return v > 0.0f ? v : 0.0f;
    case ANEURALNETWORKS_FUSED_RELU1:
        return std::clamp(v, -1.0f, 1.0f);
    case ANEURALNETWORKS_FUSED_RELU6:
        return std::clamp(v, 0.0f, 6.0f);
    default:
        return v;
    }
}

int32_t readScalarInt32(const std::vector<std::vector<uint8_t>>& storage, uint32_t idx,
                        int32_t defVal = 0) {
    if (idx >= storage.size() || storage[idx].size() < sizeof(int32_t)) {
        return defVal;
    }
    int32_t v = defVal;
    std::memcpy(&v, storage[idx].data(), sizeof(int32_t));
    return v;
}

float readScalarFloat(const std::vector<std::vector<uint8_t>>& storage, uint32_t idx,
                      float defVal = 1.0f) {
    if (idx >= storage.size() || storage[idx].size() < sizeof(float)) {
        return defVal;
    }
    float v = defVal;
    std::memcpy(&v, storage[idx].data(), sizeof(float));
    return v;
}

int executeGraph(ANeuralNetworksExecution* exec) {
    if (!exec || !exec->compilation || !exec->compilation->finished || !exec->compilation->model) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    const auto& model = *exec->compilation->model;
    const size_t nOps = model.operands.size();
    std::vector<std::vector<uint8_t>> storage(nOps);
    std::vector<std::vector<uint32_t>> shapes(nOps);

    for (size_t i = 0; i < nOps; ++i) {
        shapes[i] = model.operands[i].dims;
        if (model.operands[i].hasConst) {
            storage[i] = model.operands[i].constData;
        } else {
            const size_t bytes = numElements(shapes[i]) * elemSize(model.operands[i].type);
            storage[i].assign(bytes, 0);
        }
    }

    // Load model inputs.
    for (size_t i = 0; i < model.modelInputs.size(); ++i) {
        auto it = exec->inputs.find(static_cast<int32_t>(i));
        if (it == exec->inputs.end() || !it->second.ptr) {
            return ANEURALNETWORKS_BAD_DATA;
        }
        const uint32_t opIdx = model.modelInputs[i];
        if (!it->second.dims.empty()) {
            shapes[opIdx] = it->second.dims;
        }
        storage[opIdx].resize(it->second.length);
        if (it->second.length > 0) {
            std::memcpy(storage[opIdx].data(), it->second.ptr, it->second.length);
        }
    }

    // Execute operations sequentially.
    for (const auto& op : model.operations) {
        if (!isOpSupported(op.type)) {
            return ANEURALNETWORKS_OP_FAILED;
        }
        if (op.inputs.empty() || op.outputs.empty()) {
            return ANEURALNETWORKS_BAD_DATA;
        }
        const uint32_t outIdx = op.outputs[0];

        if (op.type == ANEURALNETWORKS_ADD || op.type == ANEURALNETWORKS_SUB ||
            op.type == ANEURALNETWORKS_MUL || op.type == ANEURALNETWORKS_DIV) {
            if (op.inputs.size() < 2) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto& aBuf = storage[op.inputs[0]];
            const auto& bBuf = storage[op.inputs[1]];
            const int32_t act =
                (op.inputs.size() >= 3) ? readScalarInt32(storage, op.inputs[2], 0) : 0;
            const size_t na = aBuf.size() / sizeof(float);
            const size_t nb = bBuf.size() / sizeof(float);
            const size_t n = std::max(na, nb);
            if (na == 0 || nb == 0) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            storage[outIdx].resize(n * sizeof(float));
            const auto* a = reinterpret_cast<const float*>(aBuf.data());
            const auto* b = reinterpret_cast<const float*>(bBuf.data());
            auto* out = reinterpret_cast<float*>(storage[outIdx].data());
            for (size_t i = 0; i < n; ++i) {
                const float av = a[i % na];
                const float bv = b[i % nb];
                float r = 0.0f;
                if (op.type == ANEURALNETWORKS_ADD) {
                    r = av + bv;
                } else if (op.type == ANEURALNETWORKS_SUB) {
                    r = av - bv;
                } else if (op.type == ANEURALNETWORKS_MUL) {
                    r = av * bv;
                } else {
                    r = av / bv;
                }
                out[i] = applyActivation(r, act);
            }
        } else if (op.type == ANEURALNETWORKS_FULLY_CONNECTED) {
            if (op.inputs.size() < 4) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const uint32_t xIdx = op.inputs[0];
            const uint32_t wIdx = op.inputs[1];
            const uint32_t bIdx = op.inputs[2];
            const int32_t act = readScalarInt32(storage, op.inputs[3], 0);
            if (shapes[wIdx].size() != 2) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const uint32_t numUnits = shapes[wIdx][0];
            const uint32_t inputSize = shapes[wIdx][1];
            const size_t totalIn = storage[xIdx].size() / sizeof(float);
            if (inputSize == 0 || totalIn % inputSize != 0) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const uint32_t batchSize = static_cast<uint32_t>(totalIn / inputSize);
            shapes[outIdx] = {batchSize, numUnits};
            storage[outIdx].resize(static_cast<size_t>(batchSize) * numUnits * sizeof(float));
            const auto* x = reinterpret_cast<const float*>(storage[xIdx].data());
            const auto* w = reinterpret_cast<const float*>(storage[wIdx].data());
            const auto* bias = (storage[bIdx].size() >= numUnits * sizeof(float))
                                   ? reinterpret_cast<const float*>(storage[bIdx].data())
                                   : nullptr;
            auto* y = reinterpret_cast<float*>(storage[outIdx].data());
            for (uint32_t b = 0; b < batchSize; ++b) {
                for (uint32_t u = 0; u < numUnits; ++u) {
                    float acc = bias ? bias[u] : 0.0f;
                    for (uint32_t k = 0; k < inputSize; ++k) {
                        acc += x[b * inputSize + k] * w[u * inputSize + k];
                    }
                    y[b * numUnits + u] = applyActivation(acc, act);
                }
            }
        } else if (op.type == ANEURALNETWORKS_RELU || op.type == ANEURALNETWORKS_RELU1 ||
                   op.type == ANEURALNETWORKS_RELU6 || op.type == ANEURALNETWORKS_LOGISTIC ||
                   op.type == ANEURALNETWORKS_TANH) {
            const auto& inBuf = storage[op.inputs[0]];
            const size_t n = inBuf.size() / sizeof(float);
            storage[outIdx].resize(n * sizeof(float));
            const auto* in = reinterpret_cast<const float*>(inBuf.data());
            auto* out = reinterpret_cast<float*>(storage[outIdx].data());
            for (size_t i = 0; i < n; ++i) {
                const float v = in[i];
                if (op.type == ANEURALNETWORKS_RELU) {
                    out[i] = v > 0.0f ? v : 0.0f;
                } else if (op.type == ANEURALNETWORKS_RELU1) {
                    out[i] = std::clamp(v, -1.0f, 1.0f);
                } else if (op.type == ANEURALNETWORKS_RELU6) {
                    out[i] = std::clamp(v, 0.0f, 6.0f);
                } else if (op.type == ANEURALNETWORKS_LOGISTIC) {
                    out[i] = 1.0f / (1.0f + std::exp(-v));
                } else {
                    out[i] = std::tanh(v);
                }
            }
        } else if (op.type == ANEURALNETWORKS_SOFTMAX) {
            const auto& inBuf = storage[op.inputs[0]];
            const float beta =
                (op.inputs.size() >= 2) ? readScalarFloat(storage, op.inputs[1], 1.0f) : 1.0f;
            const auto& inShape = shapes[op.inputs[0]];
            const size_t total = inBuf.size() / sizeof(float);
            const size_t inner = inShape.empty() ? total : std::max<size_t>(1, inShape.back());
            const size_t outer = (inner > 0) ? (total / inner) : 0;
            storage[outIdx].resize(total * sizeof(float));
            const auto* in = reinterpret_cast<const float*>(inBuf.data());
            auto* out = reinterpret_cast<float*>(storage[outIdx].data());
            for (size_t o = 0; o < outer; ++o) {
                float maxV = in[o * inner];
                for (size_t j = 1; j < inner; ++j) {
                    maxV = std::max(maxV, in[o * inner + j]);
                }
                float sum = 0.0f;
                for (size_t j = 0; j < inner; ++j) {
                    const float e = std::exp((in[o * inner + j] - maxV) * beta);
                    out[o * inner + j] = e;
                    sum += e;
                }
                for (size_t j = 0; j < inner; ++j) {
                    out[o * inner + j] /= sum;
                }
            }
        } else if (op.type == ANEURALNETWORKS_RESHAPE) {
            storage[outIdx] = storage[op.inputs[0]];
        } else if (op.type == ANEURALNETWORKS_CONCATENATION) {
            if (op.inputs.size() < 2) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            std::vector<uint8_t> cat;
            for (size_t i = 0; i + 1 < op.inputs.size(); ++i) {
                const auto& b = storage[op.inputs[i]];
                cat.insert(cat.end(), b.begin(), b.end());
            }
            storage[outIdx] = std::move(cat);
        } else if (op.type == ANEURALNETWORKS_MEAN) {
            const auto& inBuf = storage[op.inputs[0]];
            const size_t n = inBuf.size() / sizeof(float);
            if (n == 0) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto* in = reinterpret_cast<const float*>(inBuf.data());
            float sum = 0.0f;
            for (size_t i = 0; i < n; ++i) {
                sum += in[i];
            }
            const float avg = sum / static_cast<float>(n);
            storage[outIdx].resize(sizeof(float));
            std::memcpy(storage[outIdx].data(), &avg, sizeof(float));
        } else if (op.type == ANEURALNETWORKS_ARGMAX || op.type == ANEURALNETWORKS_ARGMIN) {
            const auto& inBuf = storage[op.inputs[0]];
            const size_t n = inBuf.size() / sizeof(float);
            if (n == 0) {
                return ANEURALNETWORKS_BAD_DATA;
            }
            const auto* in = reinterpret_cast<const float*>(inBuf.data());
            int32_t bestIdx = 0;
            float bestVal = in[0];
            for (size_t i = 1; i < n; ++i) {
                if ((op.type == ANEURALNETWORKS_ARGMAX && in[i] > bestVal) ||
                    (op.type == ANEURALNETWORKS_ARGMIN && in[i] < bestVal)) {
                    bestVal = in[i];
                    bestIdx = static_cast<int32_t>(i);
                }
            }
            storage[outIdx].resize(sizeof(int32_t));
            std::memcpy(storage[outIdx].data(), &bestIdx, sizeof(int32_t));
        } else if (op.type == ANEURALNETWORKS_TRANSPOSE) {
            // 2D matrix transpose
            const auto& s = shapes[op.inputs[0]];
            if (s.size() == 2) {
                const uint32_t rows = s[0];
                const uint32_t cols = s[1];
                shapes[outIdx] = {cols, rows};
                storage[outIdx].resize(static_cast<size_t>(rows) * cols * sizeof(float));
                const auto* in = reinterpret_cast<const float*>(storage[op.inputs[0]].data());
                auto* out = reinterpret_cast<float*>(storage[outIdx].data());
                for (uint32_t r = 0; r < rows; ++r) {
                    for (uint32_t c = 0; c < cols; ++c) {
                        out[c * rows + r] = in[r * cols + c];
                    }
                }
            } else {
                storage[outIdx] = storage[op.inputs[0]];
            }
        } else if (op.type == ANEURALNETWORKS_CAST) {
            const int32_t inType = model.operands[op.inputs[0]].type;
            const int32_t outType = model.operands[outIdx].type;
            const size_t n = numElements(shapes[op.inputs[0]]);
            storage[outIdx].resize(n * elemSize(outType));
            if (inType == ANEURALNETWORKS_TENSOR_INT32 &&
                outType == ANEURALNETWORKS_TENSOR_FLOAT32) {
                const auto* in = reinterpret_cast<const int32_t*>(storage[op.inputs[0]].data());
                auto* out = reinterpret_cast<float*>(storage[outIdx].data());
                for (size_t i = 0; i < n; ++i) {
                    out[i] = static_cast<float>(in[i]);
                }
            } else if (inType == ANEURALNETWORKS_TENSOR_FLOAT32 &&
                       outType == ANEURALNETWORKS_TENSOR_INT32) {
                const auto* in = reinterpret_cast<const float*>(storage[op.inputs[0]].data());
                auto* out = reinterpret_cast<int32_t*>(storage[outIdx].data());
                for (size_t i = 0; i < n; ++i) {
                    out[i] = static_cast<int32_t>(in[i]);
                }
            } else {
                storage[outIdx] = storage[op.inputs[0]];
            }
        }
    }

    // Write model outputs.
    for (size_t i = 0; i < model.modelOutputs.size(); ++i) {
        auto it = exec->outputs.find(static_cast<int32_t>(i));
        if (it == exec->outputs.end() || !it->second.ptr) {
            return ANEURALNETWORKS_BAD_DATA;
        }
        const uint32_t opIdx = model.modelOutputs[i];
        exec->outputDims[static_cast<int32_t>(i)] = shapes[opIdx];
        if (it->second.length < storage[opIdx].size()) {
            return ANEURALNETWORKS_OUTPUT_INSUFFICIENT_SIZE;
        }
        if (!storage[opIdx].empty()) {
            std::memcpy(it->second.ptr, storage[opIdx].data(), storage[opIdx].size());
        }
    }
    return ANEURALNETWORKS_NO_ERROR;
}

} // namespace

extern "C" {

// --- Devices & runtime ---

int ANeuralNetworks_getDeviceCount(uint32_t* numDevices) {
    if (!numDevices) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *numDevices = 1;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworks_getDevice(uint32_t devIndex, ANeuralNetworksDevice** device) {
    if (!device) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (devIndex != 0) {
        *device = nullptr;
        return ANEURALNETWORKS_BAD_DATA;
    }
    *device = &gCpuDevice;
    return ANEURALNETWORKS_NO_ERROR;
}

int64_t ANeuralNetworks_getRuntimeFeatureLevel(void) {
    return ANEURALNETWORKS_FEATURE_LEVEL_8;
}

int ANeuralNetworksDevice_getName(const ANeuralNetworksDevice* device, const char** name) {
    if (!device || !name) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *name = device->name;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getType(const ANeuralNetworksDevice* device, int32_t* type) {
    if (!device || !type) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *type = device->type;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getVersion(const ANeuralNetworksDevice* device, const char** version) {
    if (!device || !version) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *version = device->version;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_getFeatureLevel(const ANeuralNetworksDevice* device,
                                          int64_t* featureLevel) {
    if (!device || !featureLevel) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *featureLevel = device->featureLevel;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksDevice_wait(const ANeuralNetworksDevice* device) {
    return device ? ANEURALNETWORKS_NO_ERROR : ANEURALNETWORKS_UNEXPECTED_NULL;
}

// --- Memory ---

int ANeuralNetworksMemory_createFromFd(size_t size, int protect, int fd, size_t offset,
                                       ANeuralNetworksMemory** memory) {
    if (!memory) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *memory = nullptr;
    if (size == 0 || fd < 0) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    void* mapped = ::mmap(nullptr, size, protect, MAP_SHARED, fd, static_cast<off_t>(offset));
    if (mapped == MAP_FAILED) {
        return ANEURALNETWORKS_UNMAPPABLE;
    }
    auto* m = new (std::nothrow) ANeuralNetworksMemory();
    if (!m) {
        ::munmap(mapped, size);
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    m->ptr = static_cast<uint8_t*>(mapped);
    m->size = size;
    m->mapped = true;
    *memory = m;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksMemory_createFromAHardwareBuffer(const AHardwareBuffer* ahwb,
                                                    ANeuralNetworksMemory** memory) {
    if (!ahwb || !memory) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *memory = nullptr;
    return ANEURALNETWORKS_UNMAPPABLE;
}

void ANeuralNetworksMemory_free(ANeuralNetworksMemory* memory) {
    delete memory;
}

// --- Model ---

int ANeuralNetworksModel_create(ANeuralNetworksModel** model) {
    if (!model) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    *model = new (std::nothrow) ANeuralNetworksModel();
    return *model ? ANEURALNETWORKS_NO_ERROR : ANEURALNETWORKS_OUT_OF_MEMORY;
}

void ANeuralNetworksModel_free(ANeuralNetworksModel* model) {
    delete model;
}

int ANeuralNetworksModel_finish(ANeuralNetworksModel* model) {
    if (!model) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    model->finished = true;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_addOperand(ANeuralNetworksModel* model,
                                    const ANeuralNetworksOperandType* type) {
    if (!model || !type) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (type->dimensionCount > 0 && !type->dimensions) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    OperandInfo op;
    op.type = type->type;
    if (type->dimensionCount > 0) {
        op.dims.assign(type->dimensions, type->dimensions + type->dimensionCount);
    }
    op.scale = type->scale;
    op.zeroPoint = type->zeroPoint;
    model->operands.push_back(std::move(op));
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_setOperandValue(ANeuralNetworksModel* model, int32_t index,
                                         const void* buffer, size_t length) {
    if (!model) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (index < 0 || static_cast<size_t>(index) >= model->operands.size()) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    auto& op = model->operands[static_cast<size_t>(index)];
    if (!buffer || length == 0) {
        op.constData.clear();
        op.hasConst = false;
        return ANEURALNETWORKS_NO_ERROR;
    }
    const auto* bytes = static_cast<const uint8_t*>(buffer);
    op.constData.assign(bytes, bytes + length);
    op.hasConst = true;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_setOperandValueFromMemory(ANeuralNetworksModel* model, int32_t index,
                                                   const ANeuralNetworksMemory* memory,
                                                   size_t offset, size_t length) {
    if (!model || !memory || !memory->ptr) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (offset + length > memory->size) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    return ANeuralNetworksModel_setOperandValue(model, index, memory->ptr + offset, length);
}

int ANeuralNetworksModel_addOperation(ANeuralNetworksModel* model,
                                      ANeuralNetworksOperationType type, uint32_t inputCount,
                                      const uint32_t* inputs, uint32_t outputCount,
                                      const uint32_t* outputs) {
    if (!model || (inputCount > 0 && !inputs) || (outputCount > 0 && !outputs)) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    OperationInfo info;
    info.type = type;
    info.inputs.assign(inputs, inputs + inputCount);
    info.outputs.assign(outputs, outputs + outputCount);
    model->operations.push_back(std::move(info));
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_identifyInputsAndOutputs(ANeuralNetworksModel* model, uint32_t inputCount,
                                                  const uint32_t* inputs, uint32_t outputCount,
                                                  const uint32_t* outputs) {
    if (!model || (inputCount > 0 && !inputs) || (outputCount > 0 && !outputs)) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    model->modelInputs.assign(inputs, inputs + inputCount);
    model->modelOutputs.assign(outputs, outputs + outputCount);
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_relaxComputationFloat32toFloat16(ANeuralNetworksModel* model, bool allow) {
    if (!model) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    model->relaxFp16 = allow;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksModel_getSupportedOperationsForDevices(
    const ANeuralNetworksModel* model, const ANeuralNetworksDevice* const* devices,
    uint32_t numDevices, bool* supportedOps) {
    if (!model || !devices || numDevices == 0 || !supportedOps) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (!model->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    for (size_t i = 0; i < model->operations.size(); ++i) {
        supportedOps[i] = isOpSupported(model->operations[i].type);
    }
    return ANEURALNETWORKS_NO_ERROR;
}

// --- Compilation ---

int ANeuralNetworksCompilation_create(ANeuralNetworksModel* model,
                                      ANeuralNetworksCompilation** compilation) {
    if (!model || !compilation) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (!model->finished) {
        *compilation = nullptr;
        return ANEURALNETWORKS_BAD_STATE;
    }
    auto* c = new (std::nothrow) ANeuralNetworksCompilation();
    if (!c) {
        *compilation = nullptr;
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    c->model = model;
    *compilation = c;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_createForDevices(ANeuralNetworksModel* model,
                                                const ANeuralNetworksDevice* const* devices,
                                                uint32_t numDevices,
                                                ANeuralNetworksCompilation** compilation) {
    if (!devices || numDevices == 0) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    return ANeuralNetworksCompilation_create(model, compilation);
}

void ANeuralNetworksCompilation_free(ANeuralNetworksCompilation* compilation) {
    delete compilation;
}

int ANeuralNetworksCompilation_setPreference(ANeuralNetworksCompilation* compilation,
                                             int32_t preference) {
    if (!compilation) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (compilation->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    compilation->preference = preference;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_setPriority(ANeuralNetworksCompilation* compilation, int priority) {
    if (!compilation) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (compilation->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    compilation->priority = priority;
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_setCaching(ANeuralNetworksCompilation* compilation,
                                          const char* cacheDir, const uint8_t* token) {
    if (!compilation || !cacheDir || !token) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (compilation->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksCompilation_finish(ANeuralNetworksCompilation* compilation) {
    if (!compilation) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (compilation->finished) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    compilation->finished = true;
    return ANEURALNETWORKS_NO_ERROR;
}

// --- Execution, Burst & Event ---

int ANeuralNetworksExecution_create(ANeuralNetworksCompilation* compilation,
                                    ANeuralNetworksExecution** execution) {
    if (!compilation || !execution) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (!compilation->finished) {
        *execution = nullptr;
        return ANEURALNETWORKS_BAD_STATE;
    }
    auto* e = new (std::nothrow) ANeuralNetworksExecution();
    if (!e) {
        *execution = nullptr;
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    e->compilation = compilation;
    *execution = e;
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksExecution_free(ANeuralNetworksExecution* execution) {
    delete execution;
}

int ANeuralNetworksExecution_setInput(ANeuralNetworksExecution* execution, int32_t index,
                                      const ANeuralNetworksOperandType* type, const void* buffer,
                                      size_t length) {
    if (!execution || (length > 0 && !buffer)) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    IoBinding b;
    b.ptr = const_cast<void*>(buffer);
    b.length = length;
    if (type && type->dimensionCount > 0 && type->dimensions) {
        b.dims.assign(type->dimensions, type->dimensions + type->dimensionCount);
    }
    execution->inputs[index] = std::move(b);
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_setInputFromMemory(ANeuralNetworksExecution* execution, int32_t index,
                                                const ANeuralNetworksOperandType* type,
                                                const ANeuralNetworksMemory* memory, size_t offset,
                                                size_t length) {
    if (!execution || !memory || !memory->ptr) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (offset + length > memory->size) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    return ANeuralNetworksExecution_setInput(execution, index, type, memory->ptr + offset, length);
}

int ANeuralNetworksExecution_setOutput(ANeuralNetworksExecution* execution, int32_t index,
                                       const ANeuralNetworksOperandType* type, void* buffer,
                                       size_t length) {
    if (!execution || (length > 0 && !buffer)) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    IoBinding b;
    b.ptr = buffer;
    b.length = length;
    if (type && type->dimensionCount > 0 && type->dimensions) {
        b.dims.assign(type->dimensions, type->dimensions + type->dimensionCount);
    }
    execution->outputs[index] = std::move(b);
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_setOutputFromMemory(ANeuralNetworksExecution* execution, int32_t index,
                                                 const ANeuralNetworksOperandType* type,
                                                 const ANeuralNetworksMemory* memory, size_t offset,
                                                 size_t length) {
    if (!execution || !memory || !memory->ptr) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    if (offset + length > memory->size) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    return ANeuralNetworksExecution_setOutput(execution, index, type, memory->ptr + offset, length);
}

int ANeuralNetworksExecution_compute(ANeuralNetworksExecution* execution) {
    return executeGraph(execution);
}

int ANeuralNetworksExecution_startCompute(ANeuralNetworksExecution* execution,
                                          ANeuralNetworksEvent** event) {
    if (!execution || !event) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    const int st = executeGraph(execution);
    auto* ev = new (std::nothrow) ANeuralNetworksEvent();
    if (!ev) {
        *event = nullptr;
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    ev->status = st;
    *event = ev;
    return st;
}

int ANeuralNetworksExecution_burstCompute(ANeuralNetworksExecution* execution,
                                          ANeuralNetworksBurst* burst) {
    if (!execution || !burst) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    return executeGraph(execution);
}

int ANeuralNetworksExecution_getOutputOperandRank(ANeuralNetworksExecution* execution,
                                                  int32_t index, uint32_t* rank) {
    if (!execution || !rank) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto it = execution->outputDims.find(index);
    if (it == execution->outputDims.end()) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    *rank = static_cast<uint32_t>(it->second.size());
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksExecution_getOutputOperandDimensions(ANeuralNetworksExecution* execution,
                                                        int32_t index, uint32_t* dimensions) {
    if (!execution || !dimensions) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto it = execution->outputDims.find(index);
    if (it == execution->outputDims.end()) {
        return ANEURALNETWORKS_BAD_DATA;
    }
    for (size_t i = 0; i < it->second.size(); ++i) {
        dimensions[i] = it->second[i];
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int ANeuralNetworksBurst_create(ANeuralNetworksCompilation* compilation,
                                ANeuralNetworksBurst** burst) {
    if (!compilation || !burst) {
        return ANEURALNETWORKS_UNEXPECTED_NULL;
    }
    auto* b = new (std::nothrow) ANeuralNetworksBurst();
    if (!b) {
        *burst = nullptr;
        return ANEURALNETWORKS_OUT_OF_MEMORY;
    }
    b->compilation = compilation;
    *burst = b;
    return ANEURALNETWORKS_NO_ERROR;
}

void ANeuralNetworksBurst_free(ANeuralNetworksBurst* burst) {
    delete burst;
}

int ANeuralNetworksEvent_wait(ANeuralNetworksEvent* event) {
    return event ? event->status : ANEURALNETWORKS_UNEXPECTED_NULL;
}

void ANeuralNetworksEvent_free(ANeuralNetworksEvent* event) {
    delete event;
}

} // extern "C"
