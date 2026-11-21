// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/input.h"
#include <cerrno>
#include <deque>
#include <fcntl.h>
#include <map>
#include <mutex>
#include <new>
#include <unistd.h>
#include <vector>

extern "C" {
int ALooper_addFd(ALooper* looper, int fd, int ident, int events, ALooper_callbackFunc callback,
                  void* data);
int ALooper_removeFd(ALooper* looper, int fd);
void ALooper_acquire(ALooper* looper);
void ALooper_release(ALooper* looper);
}

struct AInputEvent {
    int32_t type = AINPUT_EVENT_TYPE_KEY;
    int32_t device_id = 0;
    int32_t source = 0;
    int32_t action = 0;
    int32_t flags = 0;
    int32_t meta_state = 0;
    int64_t down_time = 0;
    int64_t event_time = 0;
    // Key fields.
    int32_t key_code = 0;
    int32_t scan_code = 0;
    int32_t repeat_count = 0;
    // Motion fields.
    int32_t button_state = 0;
    int32_t edge_flags = 0;
    int32_t action_button = 0;
    int32_t classification = 0;
    float x_offset = 0.0f;
    float y_offset = 0.0f;
    float x_precision = 1.0f;
    float y_precision = 1.0f;
    struct Sample {
        int64_t event_time = 0;
        std::vector<ShirazMotionPointer> pointers;
        std::vector<std::map<int32_t, float>> extra_axes;
    };
    Sample current;
    std::vector<Sample> history;
};

struct AInputQueue {
    std::mutex mutex;
    std::deque<AInputEvent*> pending;
    int wake_fds[2] = {-1, -1};
    ALooper* attached_looper = nullptr;
    int last_handled = -1;
};

namespace {

float axis_from_pointer(const ShirazMotionPointer& p, const std::map<int32_t, float>* extra,
                        int32_t axis, float x_off, float y_off) {
    switch (axis) {
    case AMOTION_EVENT_AXIS_X:
        return p.x + x_off;
    case AMOTION_EVENT_AXIS_Y:
        return p.y + y_off;
    case AMOTION_EVENT_AXIS_PRESSURE:
        return p.pressure;
    case AMOTION_EVENT_AXIS_SIZE:
        return p.size;
    case AMOTION_EVENT_AXIS_TOUCH_MAJOR:
        return p.touch_major;
    case AMOTION_EVENT_AXIS_TOUCH_MINOR:
        return p.touch_minor;
    case AMOTION_EVENT_AXIS_TOOL_MAJOR:
        return p.tool_major;
    case AMOTION_EVENT_AXIS_TOOL_MINOR:
        return p.tool_minor;
    case AMOTION_EVENT_AXIS_ORIENTATION:
        return p.orientation;
    default:
        if (extra) {
            auto it = extra->find(axis);
            if (it != extra->end())
                return it->second;
        }
        return 0.0f;
    }
}

const ShirazMotionPointer* current_ptr(const AInputEvent* e, size_t idx) {
    if (!e || e->type != AINPUT_EVENT_TYPE_MOTION || idx >= e->current.pointers.size())
        return nullptr;
    return &e->current.pointers[idx];
}

const ShirazMotionPointer* hist_ptr(const AInputEvent* e, size_t p_idx, size_t h_idx) {
    if (!e || e->type != AINPUT_EVENT_TYPE_MOTION || h_idx >= e->history.size())
        return nullptr;
    const auto& sample = e->history[h_idx];
    if (p_idx >= sample.pointers.size())
        return nullptr;
    return &sample.pointers[p_idx];
}

void signal_queue(AInputQueue* q) {
    if (q->wake_fds[1] >= 0) {
        char b = 1;
        [[maybe_unused]] ssize_t ignored = write(q->wake_fds[1], &b, 1);
    }
}

void drain_queue_pipe_locked(AInputQueue* q) {
    if (q->pending.empty() && q->wake_fds[0] >= 0) {
        char buf[64];
        while (read(q->wake_fds[0], buf, sizeof(buf)) > 0) {
        }
    }
}

} // namespace

extern "C" {

int32_t AInputEvent_getType(const AInputEvent* event) {
    return event ? event->type : 0;
}
int32_t AInputEvent_getDeviceId(const AInputEvent* event) {
    return event ? event->device_id : 0;
}
int32_t AInputEvent_getSource(const AInputEvent* event) {
    return event ? event->source : 0;
}
void AInputEvent_release(const AInputEvent* event) {
    delete event;
}

int32_t AKeyEvent_getAction(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->action : 0;
}
int32_t AKeyEvent_getFlags(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->flags : 0;
}
int32_t AKeyEvent_getKeyCode(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->key_code : 0;
}
int32_t AKeyEvent_getScanCode(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->scan_code : 0;
}
int32_t AKeyEvent_getMetaState(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->meta_state : 0;
}
int32_t AKeyEvent_getRepeatCount(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->repeat_count : 0;
}
int64_t AKeyEvent_getDownTime(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->down_time : 0;
}
int64_t AKeyEvent_getEventTime(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_KEY) ? e->event_time : 0;
}

int32_t AMotionEvent_getAction(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->action : 0;
}
int32_t AMotionEvent_getFlags(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->flags : 0;
}
int32_t AMotionEvent_getMetaState(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->meta_state : 0;
}
int32_t AMotionEvent_getButtonState(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->button_state : 0;
}
int32_t AMotionEvent_getEdgeFlags(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->edge_flags : 0;
}
int64_t AMotionEvent_getDownTime(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->down_time : 0;
}
int64_t AMotionEvent_getEventTime(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->event_time : 0;
}
float AMotionEvent_getXOffset(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->x_offset : 0.0f;
}
float AMotionEvent_getYOffset(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->y_offset : 0.0f;
}
float AMotionEvent_getXPrecision(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->x_precision : 0.0f;
}
float AMotionEvent_getYPrecision(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->y_precision : 0.0f;
}
size_t AMotionEvent_getPointerCount(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->current.pointers.size() : 0;
}
int32_t AMotionEvent_getPointerId(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->id : 0;
}
int32_t AMotionEvent_getToolType(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->tool_type : 0;
}
float AMotionEvent_getRawX(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->x : 0.0f;
}
float AMotionEvent_getRawY(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->y : 0.0f;
}
float AMotionEvent_getX(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->x + e->x_offset : 0.0f;
}
float AMotionEvent_getY(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->y + e->y_offset : 0.0f;
}
float AMotionEvent_getPressure(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->pressure : 0.0f;
}
float AMotionEvent_getSize(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->size : 0.0f;
}
float AMotionEvent_getTouchMajor(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->touch_major : 0.0f;
}
float AMotionEvent_getTouchMinor(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->touch_minor : 0.0f;
}
float AMotionEvent_getToolMajor(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->tool_major : 0.0f;
}
float AMotionEvent_getToolMinor(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->tool_minor : 0.0f;
}
float AMotionEvent_getOrientation(const AInputEvent* e, size_t i) {
    const auto* p = current_ptr(e, i);
    return p ? p->orientation : 0.0f;
}
float AMotionEvent_getAxisValue(const AInputEvent* e, int32_t axis, size_t i) {
    const auto* p = current_ptr(e, i);
    if (!p)
        return 0.0f;
    const auto* extra = i < e->current.extra_axes.size() ? &e->current.extra_axes[i] : nullptr;
    return axis_from_pointer(*p, extra, axis, e->x_offset, e->y_offset);
}
size_t AMotionEvent_getHistorySize(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->history.size() : 0;
}
int64_t AMotionEvent_getHistoricalEventTime(const AInputEvent* e, size_t h) {
    if (!e || e->type != AINPUT_EVENT_TYPE_MOTION || h >= e->history.size())
        return 0;
    return e->history[h].event_time;
}
float AMotionEvent_getHistoricalRawX(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->x : 0.0f;
}
float AMotionEvent_getHistoricalRawY(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->y : 0.0f;
}
float AMotionEvent_getHistoricalX(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->x + e->x_offset : 0.0f;
}
float AMotionEvent_getHistoricalY(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->y + e->y_offset : 0.0f;
}
float AMotionEvent_getHistoricalPressure(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->pressure : 0.0f;
}
float AMotionEvent_getHistoricalSize(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->size : 0.0f;
}
float AMotionEvent_getHistoricalTouchMajor(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->touch_major : 0.0f;
}
float AMotionEvent_getHistoricalTouchMinor(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->touch_minor : 0.0f;
}
float AMotionEvent_getHistoricalToolMajor(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->tool_major : 0.0f;
}
float AMotionEvent_getHistoricalToolMinor(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->tool_minor : 0.0f;
}
float AMotionEvent_getHistoricalOrientation(const AInputEvent* e, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    return p ? p->orientation : 0.0f;
}
float AMotionEvent_getHistoricalAxisValue(const AInputEvent* e, int32_t axis, size_t i, size_t h) {
    const auto* p = hist_ptr(e, i, h);
    if (!p)
        return 0.0f;
    const auto& sample = e->history[h];
    const auto* extra = i < sample.extra_axes.size() ? &sample.extra_axes[i] : nullptr;
    return axis_from_pointer(*p, extra, axis, e->x_offset, e->y_offset);
}
int32_t AMotionEvent_getActionButton(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->action_button : 0;
}
int32_t AMotionEvent_getClassification(const AInputEvent* e) {
    return (e && e->type == AINPUT_EVENT_TYPE_MOTION) ? e->classification : 0;
}

void AInputQueue_attachLooper(AInputQueue* queue, ALooper* looper, int ident,
                              ALooper_callbackFunc callback, void* data) {
    if (!queue || !looper)
        return;
    std::lock_guard<std::mutex> lock(queue->mutex);
    if (queue->attached_looper) {
        ALooper_removeFd(queue->attached_looper, queue->wake_fds[0]);
        ALooper_release(queue->attached_looper);
        queue->attached_looper = nullptr;
    }
    if (queue->wake_fds[0] >= 0) {
        ALooper_acquire(looper);
        queue->attached_looper = looper;
        ALooper_addFd(looper, queue->wake_fds[0], ident, 1 /* ALOOPER_EVENT_INPUT */, callback,
                      data);
    }
}

void AInputQueue_detachLooper(AInputQueue* queue) {
    if (!queue)
        return;
    std::lock_guard<std::mutex> lock(queue->mutex);
    if (queue->attached_looper) {
        ALooper_removeFd(queue->attached_looper, queue->wake_fds[0]);
        ALooper_release(queue->attached_looper);
        queue->attached_looper = nullptr;
    }
}

int32_t AInputQueue_hasEvents(AInputQueue* queue) {
    if (!queue)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(queue->mutex);
    return queue->pending.empty() ? 0 : 1;
}

int32_t AInputQueue_getEvent(AInputQueue* queue, AInputEvent** outEvent) {
    if (!queue || !outEvent)
        return -EINVAL;
    std::lock_guard<std::mutex> lock(queue->mutex);
    if (queue->pending.empty()) {
        *outEvent = nullptr;
        return -EAGAIN;
    }
    *outEvent = queue->pending.front();
    queue->pending.pop_front();
    drain_queue_pipe_locked(queue);
    return 0;
}

int32_t AInputQueue_preDispatchEvent(AInputQueue*, AInputEvent*) {
    return 0;
}

void AInputQueue_finishEvent(AInputQueue* queue, AInputEvent* event, int handled) {
    if (queue) {
        std::lock_guard<std::mutex> lock(queue->mutex);
        queue->last_handled = handled;
    }
    delete event;
}

AInputQueue* shiraz_input_queue_create() {
    auto* q = new (std::nothrow) AInputQueue();
    if (!q)
        return nullptr;
    if (pipe(q->wake_fds) != 0) {
        delete q;
        return nullptr;
    }
    for (int fd : q->wake_fds) {
        fcntl(fd, F_SETFD, FD_CLOEXEC);
        fcntl(fd, F_SETFL, O_NONBLOCK);
    }
    return q;
}

void shiraz_input_queue_destroy(AInputQueue* queue) {
    if (!queue)
        return;
    AInputQueue_detachLooper(queue);
    {
        std::lock_guard<std::mutex> lock(queue->mutex);
        for (AInputEvent* e : queue->pending)
            delete e;
        queue->pending.clear();
        for (int& fd : queue->wake_fds) {
            if (fd >= 0) {
                close(fd);
                fd = -1;
            }
        }
    }
    delete queue;
}

int shiraz_input_queue_push_key(AInputQueue* queue, int32_t device_id, int32_t source,
                                int32_t action, int32_t key_code, int32_t scan_code,
                                int32_t meta_state, int32_t flags, int32_t repeat_count,
                                int64_t down_time, int64_t event_time) {
    if (!queue)
        return -EINVAL;
    auto* e = new (std::nothrow) AInputEvent();
    if (!e)
        return -ENOMEM;
    e->type = AINPUT_EVENT_TYPE_KEY;
    e->device_id = device_id;
    e->source = source;
    e->action = action;
    e->key_code = key_code;
    e->scan_code = scan_code;
    e->meta_state = meta_state;
    e->flags = flags;
    e->repeat_count = repeat_count;
    e->down_time = down_time;
    e->event_time = event_time;
    std::lock_guard<std::mutex> lock(queue->mutex);
    queue->pending.push_back(e);
    signal_queue(queue);
    return 0;
}

int shiraz_input_queue_push_motion(AInputQueue* queue, int32_t device_id, int32_t source,
                                   int32_t action, int32_t flags, int32_t meta_state,
                                   int32_t button_state, int32_t action_button, int64_t down_time,
                                   int64_t event_time, const ShirazMotionPointer* pointers,
                                   size_t pointer_count) {
    if (!queue || (!pointers && pointer_count > 0) || pointer_count > 32)
        return -EINVAL;
    auto* e = new (std::nothrow) AInputEvent();
    if (!e)
        return -ENOMEM;
    e->type = AINPUT_EVENT_TYPE_MOTION;
    e->device_id = device_id;
    e->source = source;
    e->action = action;
    e->flags = flags;
    e->meta_state = meta_state;
    e->button_state = button_state;
    e->action_button = action_button;
    e->down_time = down_time;
    e->event_time = event_time;
    e->current.event_time = event_time;
    e->current.pointers.assign(pointers, pointers + pointer_count);
    std::lock_guard<std::mutex> lock(queue->mutex);
    queue->pending.push_back(e);
    signal_queue(queue);
    return 0;
}

int shiraz_input_queue_last_handled(AInputQueue* queue) {
    if (!queue)
        return -1;
    std::lock_guard<std::mutex> lock(queue->mutex);
    return queue->last_handled;
}

} // extern "C"
