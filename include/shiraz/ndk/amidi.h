// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _JNIEnv JNIEnv;
typedef void* jobject;

typedef struct AMidiDevice AMidiDevice;
typedef struct AMidiInputPort AMidiInputPort;
typedef struct AMidiOutputPort AMidiOutputPort;

enum {
    AMIDI_DEVICE_TYPE_USB = 1,
    AMIDI_DEVICE_TYPE_VIRTUAL = 2,
    AMIDI_DEVICE_TYPE_BLUETOOTH = 3,
};

enum {
    AMIDI_PROTOCOL_UNKNOWN = -1,
    AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS = 1,
    AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS_AND_JRTS = 2,
    AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS = 3,
    AMIDI_PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS_AND_JRTS = 4,
    AMIDI_PROTOCOL_UMP_MIDI_2_0 = 17,
    AMIDI_PROTOCOL_UMP_MIDI_2_0_AND_JRTS = 18,
    AMIDI_PROTOCOL_UMP_USE_MIDI_CI = 0,
};

enum {
    AMIDI_OPCODE_DATA = 1,
    AMIDI_OPCODE_FLUSH = 2,
};

int32_t AMidiDevice_fromJava(JNIEnv* env, jobject midiDeviceObj, AMidiDevice** outDevicePtrPtr);
int32_t AMidiDevice_release(const AMidiDevice* midiDevice);
int32_t AMidiDevice_getType(const AMidiDevice* device);
ssize_t AMidiDevice_getNumInputPorts(const AMidiDevice* device);
ssize_t AMidiDevice_getNumOutputPorts(const AMidiDevice* device);
int32_t AMidiDevice_getDefaultProtocol(const AMidiDevice* device);

int32_t AMidiInputPort_open(const AMidiDevice* device, int32_t portNumber,
                            AMidiInputPort** outInputPortPtr);
void AMidiInputPort_close(const AMidiInputPort* inputPort);
ssize_t AMidiInputPort_send(const AMidiInputPort* inputPort, const uint8_t* buffer,
                            size_t numBytes);
ssize_t AMidiInputPort_sendWithTimestamp(const AMidiInputPort* inputPort, const uint8_t* buffer,
                                         size_t numBytes, int64_t timestamp);
int32_t AMidiInputPort_sendFlush(const AMidiInputPort* inputPort);

int32_t AMidiOutputPort_open(const AMidiDevice* device, int32_t portNumber,
                             AMidiOutputPort** outOutputPortPtr);
void AMidiOutputPort_close(const AMidiOutputPort* outputPort);
ssize_t AMidiOutputPort_receive(const AMidiOutputPort* outputPort, int32_t* opcodePtr,
                                uint8_t* buffer, size_t maxBytes, size_t* numBytesReceivedPtr,
                                int64_t* outTimeStampPtr);

#ifdef __cplusplus
}
#endif
