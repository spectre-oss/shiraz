// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
#include "shiraz/native_window.h"
struct AHardwareBuffer;
struct AHardwareBuffer_Desc {
    uint32_t width, height, layers, format;
    uint64_t usage;
    uint32_t stride, rfu0;
    uint64_t rfu1;
};
extern "C" {
int AHardwareBuffer_allocate(const AHardwareBuffer_Desc*, AHardwareBuffer**);
void AHardwareBuffer_acquire(AHardwareBuffer*);
void AHardwareBuffer_release(AHardwareBuffer*);
void AHardwareBuffer_describe(const AHardwareBuffer*, AHardwareBuffer_Desc*);
int AHardwareBuffer_lock(AHardwareBuffer*, uint64_t, int32_t, const ARect*, void**);
int AHardwareBuffer_unlock(AHardwareBuffer*, int32_t*);
int AHardwareBuffer_sendHandleToUnixSocket(const AHardwareBuffer*, int);
int AHardwareBuffer_recvHandleFromUnixSocket(int, AHardwareBuffer**);
int AHardwareBuffer_isSupported(const AHardwareBuffer_Desc*);
// Exported descriptor belongs to caller. Used by the EGL native-buffer bridge.
int shiraz_hardware_buffer_export(AHardwareBuffer*, uint32_t* format, uint32_t* pitch);
}
