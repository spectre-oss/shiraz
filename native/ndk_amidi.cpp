// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK libamidi.so implementation with thread-safe MIDI packet
// queues and timestamped send/receive/flush semantics.
#include "shiraz/ndk/amidi.h"

#include <atomic>
#include <cerrno>
#include <cstring>
#include <deque>
#include <mutex>
#include <new>
#include <time.h>
#include <vector>

namespace {

int64_t monotonicNanos() {
    struct timespec ts{};
    ::clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + static_cast<int64_t>(ts.tv_nsec);
}

struct MidiPacket {
    int32_t opcode = AMIDI_OPCODE_DATA;
    int64_t timestamp = 0;
    std::vector<uint8_t> data;
};

} // namespace

struct AMidiDevice {
    std::atomic<int32_t> refCount{1};
    int32_t type = AMIDI_DEVICE_TYPE_VIRTUAL;
    int32_t defaultProtocol = AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS;
    int32_t numInputPorts = 1;
    int32_t numOutputPorts = 1;
    std::mutex mu;
    std::deque<MidiPacket> queue;
};

struct AMidiInputPort {
    AMidiDevice* device = nullptr;
    int32_t portNumber = 0;
};

struct AMidiOutputPort {
    AMidiDevice* device = nullptr;
    int32_t portNumber = 0;
};

extern "C" {

int32_t AMidiDevice_fromJava(JNIEnv* env, jobject midiDeviceObj, AMidiDevice** outDevicePtrPtr) {
    if (!outDevicePtrPtr) {
        return -EINVAL;
    }
    *outDevicePtrPtr = nullptr;
    if (env != nullptr && midiDeviceObj == nullptr) {
        return -EINVAL;
    }
    auto* dev = new (std::nothrow) AMidiDevice();
    if (!dev) {
        return -ENOMEM;
    }
    *outDevicePtrPtr = dev;
    return 0;
}

int32_t AMidiDevice_release(const AMidiDevice* midiDevice) {
    if (!midiDevice) {
        return -EINVAL;
    }
    auto* dev = const_cast<AMidiDevice*>(midiDevice);
    if (dev->refCount.fetch_sub(1) == 1) {
        delete dev;
    }
    return 0;
}

int32_t AMidiDevice_getType(const AMidiDevice* device) {
    return device ? device->type : -EINVAL;
}

ssize_t AMidiDevice_getNumInputPorts(const AMidiDevice* device) {
    return device ? static_cast<ssize_t>(device->numInputPorts) : -EINVAL;
}

ssize_t AMidiDevice_getNumOutputPorts(const AMidiDevice* device) {
    return device ? static_cast<ssize_t>(device->numOutputPorts) : -EINVAL;
}

int32_t AMidiDevice_getDefaultProtocol(const AMidiDevice* device) {
    return device ? device->defaultProtocol : AMIDI_PROTOCOL_UNKNOWN;
}

int32_t AMidiInputPort_open(const AMidiDevice* device, int32_t portNumber,
                            AMidiInputPort** outInputPortPtr) {
    if (!device || !outInputPortPtr || portNumber < 0 || portNumber >= device->numInputPorts) {
        return -EINVAL;
    }
    auto* port = new (std::nothrow) AMidiInputPort();
    if (!port) {
        return -ENOMEM;
    }
    auto* dev = const_cast<AMidiDevice*>(device);
    dev->refCount.fetch_add(1);
    port->device = dev;
    port->portNumber = portNumber;
    *outInputPortPtr = port;
    return 0;
}

void AMidiInputPort_close(const AMidiInputPort* inputPort) {
    if (!inputPort) {
        return;
    }
    AMidiDevice_release(inputPort->device);
    delete inputPort;
}

ssize_t AMidiInputPort_sendWithTimestamp(const AMidiInputPort* inputPort, const uint8_t* buffer,
                                         size_t numBytes, int64_t timestamp) {
    if (!inputPort || !inputPort->device || (!buffer && numBytes > 0)) {
        return -EINVAL;
    }
    MidiPacket pkt;
    pkt.opcode = AMIDI_OPCODE_DATA;
    pkt.timestamp = (timestamp > 0) ? timestamp : monotonicNanos();
    if (numBytes > 0) {
        pkt.data.assign(buffer, buffer + numBytes);
    }
    std::lock_guard<std::mutex> lock(inputPort->device->mu);
    inputPort->device->queue.push_back(std::move(pkt));
    return static_cast<ssize_t>(numBytes);
}

ssize_t AMidiInputPort_send(const AMidiInputPort* inputPort, const uint8_t* buffer,
                            size_t numBytes) {
    return AMidiInputPort_sendWithTimestamp(inputPort, buffer, numBytes, monotonicNanos());
}

int32_t AMidiInputPort_sendFlush(const AMidiInputPort* inputPort) {
    if (!inputPort || !inputPort->device) {
        return -EINVAL;
    }
    MidiPacket pkt;
    pkt.opcode = AMIDI_OPCODE_FLUSH;
    pkt.timestamp = monotonicNanos();
    std::lock_guard<std::mutex> lock(inputPort->device->mu);
    inputPort->device->queue.clear();
    inputPort->device->queue.push_back(std::move(pkt));
    return 0;
}

int32_t AMidiOutputPort_open(const AMidiDevice* device, int32_t portNumber,
                             AMidiOutputPort** outOutputPortPtr) {
    if (!device || !outOutputPortPtr || portNumber < 0 || portNumber >= device->numOutputPorts) {
        return -EINVAL;
    }
    auto* port = new (std::nothrow) AMidiOutputPort();
    if (!port) {
        return -ENOMEM;
    }
    auto* dev = const_cast<AMidiDevice*>(device);
    dev->refCount.fetch_add(1);
    port->device = dev;
    port->portNumber = portNumber;
    *outOutputPortPtr = port;
    return 0;
}

void AMidiOutputPort_close(const AMidiOutputPort* outputPort) {
    if (!outputPort) {
        return;
    }
    AMidiDevice_release(outputPort->device);
    delete outputPort;
}

ssize_t AMidiOutputPort_receive(const AMidiOutputPort* outputPort, int32_t* opcodePtr,
                                uint8_t* buffer, size_t maxBytes, size_t* numBytesReceivedPtr,
                                int64_t* outTimeStampPtr) {
    if (!outputPort || !outputPort->device || !opcodePtr || !numBytesReceivedPtr) {
        return -EINVAL;
    }
    std::lock_guard<std::mutex> lock(outputPort->device->mu);
    if (outputPort->device->queue.empty()) {
        *numBytesReceivedPtr = 0;
        return 0;
    }
    MidiPacket pkt = std::move(outputPort->device->queue.front());
    outputPort->device->queue.pop_front();
    *opcodePtr = pkt.opcode;
    if (outTimeStampPtr) {
        *outTimeStampPtr = pkt.timestamp;
    }
    const size_t n = (pkt.data.size() < maxBytes) ? pkt.data.size() : maxBytes;
    if (n > 0 && buffer) {
        std::memcpy(buffer, pkt.data.data(), n);
    }
    *numBytesReceivedPtr = n;
    return 1;
}

} // extern "C"
