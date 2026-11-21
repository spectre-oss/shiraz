// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>

struct AInputEvent;
struct AInputQueue;
struct ALooper;
using ALooper_callbackFunc = int (*)(int fd, int events, void* data);

enum {
    AINPUT_EVENT_TYPE_KEY = 1,
    AINPUT_EVENT_TYPE_MOTION = 2,
    AINPUT_EVENT_TYPE_FOCUS = 3,
    AINPUT_EVENT_TYPE_CAPTURE = 4,
    AINPUT_EVENT_TYPE_DRAG = 5,
    AINPUT_EVENT_TYPE_TOUCH_MODE = 6,

    AMOTION_EVENT_AXIS_X = 0,
    AMOTION_EVENT_AXIS_Y = 1,
    AMOTION_EVENT_AXIS_PRESSURE = 2,
    AMOTION_EVENT_AXIS_SIZE = 3,
    AMOTION_EVENT_AXIS_TOUCH_MAJOR = 4,
    AMOTION_EVENT_AXIS_TOUCH_MINOR = 5,
    AMOTION_EVENT_AXIS_TOOL_MAJOR = 6,
    AMOTION_EVENT_AXIS_TOOL_MINOR = 7,
    AMOTION_EVENT_AXIS_ORIENTATION = 8,
    AMOTION_EVENT_AXIS_VSCROLL = 9,
    AMOTION_EVENT_AXIS_HSCROLL = 10,
};

struct ShirazMotionPointer {
    int32_t id = 0;
    int32_t tool_type = 1;
    float x = 0.0f;
    float y = 0.0f;
    float pressure = 1.0f;
    float size = 0.0f;
    float touch_major = 0.0f;
    float touch_minor = 0.0f;
    float tool_major = 0.0f;
    float tool_minor = 0.0f;
    float orientation = 0.0f;
};

extern "C" {
int32_t AInputEvent_getType(const AInputEvent* event);
int32_t AInputEvent_getDeviceId(const AInputEvent* event);
int32_t AInputEvent_getSource(const AInputEvent* event);
void AInputEvent_release(const AInputEvent* event);

int32_t AKeyEvent_getAction(const AInputEvent* key_event);
int32_t AKeyEvent_getFlags(const AInputEvent* key_event);
int32_t AKeyEvent_getKeyCode(const AInputEvent* key_event);
int32_t AKeyEvent_getScanCode(const AInputEvent* key_event);
int32_t AKeyEvent_getMetaState(const AInputEvent* key_event);
int32_t AKeyEvent_getRepeatCount(const AInputEvent* key_event);
int64_t AKeyEvent_getDownTime(const AInputEvent* key_event);
int64_t AKeyEvent_getEventTime(const AInputEvent* key_event);

int32_t AMotionEvent_getAction(const AInputEvent* motion_event);
int32_t AMotionEvent_getFlags(const AInputEvent* motion_event);
int32_t AMotionEvent_getMetaState(const AInputEvent* motion_event);
int32_t AMotionEvent_getButtonState(const AInputEvent* motion_event);
int32_t AMotionEvent_getEdgeFlags(const AInputEvent* motion_event);
int64_t AMotionEvent_getDownTime(const AInputEvent* motion_event);
int64_t AMotionEvent_getEventTime(const AInputEvent* motion_event);
float AMotionEvent_getXOffset(const AInputEvent* motion_event);
float AMotionEvent_getYOffset(const AInputEvent* motion_event);
float AMotionEvent_getXPrecision(const AInputEvent* motion_event);
float AMotionEvent_getYPrecision(const AInputEvent* motion_event);
size_t AMotionEvent_getPointerCount(const AInputEvent* motion_event);
int32_t AMotionEvent_getPointerId(const AInputEvent* motion_event, size_t pointer_index);
int32_t AMotionEvent_getToolType(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getRawX(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getRawY(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getX(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getY(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getPressure(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getSize(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getTouchMajor(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getTouchMinor(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getToolMajor(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getToolMinor(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getOrientation(const AInputEvent* motion_event, size_t pointer_index);
float AMotionEvent_getAxisValue(const AInputEvent* motion_event, int32_t axis,
                                size_t pointer_index);
size_t AMotionEvent_getHistorySize(const AInputEvent* motion_event);
int64_t AMotionEvent_getHistoricalEventTime(const AInputEvent* motion_event, size_t history_index);
float AMotionEvent_getHistoricalRawX(const AInputEvent* motion_event, size_t pointer_index,
                                     size_t history_index);
float AMotionEvent_getHistoricalRawY(const AInputEvent* motion_event, size_t pointer_index,
                                     size_t history_index);
float AMotionEvent_getHistoricalX(const AInputEvent* motion_event, size_t pointer_index,
                                  size_t history_index);
float AMotionEvent_getHistoricalY(const AInputEvent* motion_event, size_t pointer_index,
                                  size_t history_index);
float AMotionEvent_getHistoricalPressure(const AInputEvent* motion_event, size_t pointer_index,
                                         size_t history_index);
float AMotionEvent_getHistoricalSize(const AInputEvent* motion_event, size_t pointer_index,
                                     size_t history_index);
float AMotionEvent_getHistoricalTouchMajor(const AInputEvent* motion_event, size_t pointer_index,
                                           size_t history_index);
float AMotionEvent_getHistoricalTouchMinor(const AInputEvent* motion_event, size_t pointer_index,
                                           size_t history_index);
float AMotionEvent_getHistoricalToolMajor(const AInputEvent* motion_event, size_t pointer_index,
                                          size_t history_index);
float AMotionEvent_getHistoricalToolMinor(const AInputEvent* motion_event, size_t pointer_index,
                                          size_t history_index);
float AMotionEvent_getHistoricalOrientation(const AInputEvent* motion_event, size_t pointer_index,
                                            size_t history_index);
float AMotionEvent_getHistoricalAxisValue(const AInputEvent* motion_event, int32_t axis,
                                          size_t pointer_index, size_t history_index);
int32_t AMotionEvent_getActionButton(const AInputEvent* motion_event);
int32_t AMotionEvent_getClassification(const AInputEvent* motion_event);

void AInputQueue_attachLooper(AInputQueue* queue, ALooper* looper, int ident,
                              ALooper_callbackFunc callback, void* data);
void AInputQueue_detachLooper(AInputQueue* queue);
int32_t AInputQueue_hasEvents(AInputQueue* queue);
int32_t AInputQueue_getEvent(AInputQueue* queue, AInputEvent** outEvent);
int32_t AInputQueue_preDispatchEvent(AInputQueue* queue, AInputEvent* event);
void AInputQueue_finishEvent(AInputQueue* queue, AInputEvent* event, int handled);

// Shiraz host producer helpers for dispatching events into an AInputQueue.
AInputQueue* shiraz_input_queue_create();
void shiraz_input_queue_destroy(AInputQueue* queue);
int shiraz_input_queue_push_key(AInputQueue* queue, int32_t device_id, int32_t source,
                                int32_t action, int32_t key_code, int32_t scan_code,
                                int32_t meta_state, int32_t flags, int32_t repeat_count,
                                int64_t down_time, int64_t event_time);
int shiraz_input_queue_push_motion(AInputQueue* queue, int32_t device_id, int32_t source,
                                   int32_t action, int32_t flags, int32_t meta_state,
                                   int32_t button_state, int32_t action_button, int64_t down_time,
                                   int64_t event_time, const ShirazMotionPointer* pointers,
                                   size_t pointer_count);
int shiraz_input_queue_last_handled(AInputQueue* queue);
}
