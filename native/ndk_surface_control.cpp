// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/surface_control.h"
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <map>
#include <mutex>
#include <new>
#include <string>
#include <unistd.h>
#include <vector>

struct ASurfaceControl {
    std::atomic<uint32_t> refs{1};
    std::mutex mutex;
    std::string debug_name;
    ASurfaceControl* parent = nullptr;
    ANativeWindow* parent_window = nullptr;
    AHardwareBuffer* current_buffer = nullptr;
    int8_t visibility = ASURFACE_TRANSACTION_VISIBILITY_SHOW;
    int32_t z_order = 0;
    int32_t x = 0;
    int32_t y = 0;
    float scale_x = 1.0f;
    float scale_y = 1.0f;
    ARect crop{0, 0, 0, 0};
    ARect source_rect{0, 0, 0, 0};
    ARect dest_rect{0, 0, 0, 0};
    int32_t transform = 0;
    int8_t transparency = ASURFACE_TRANSACTION_TRANSPARENCY_TRANSLUCENT;
    double alpha = 1.0;
    int32_t data_space = 0;
    float frame_rate = 0.0f;
    int8_t frame_rate_compat = 0;
    int8_t frame_rate_strategy = 0;
    bool back_pressure = false;
    float color[4] = {0, 0, 0, 0};
    int64_t last_acquire_time = -1;
};

struct SurfaceUpdate {
    bool has_visibility = false;
    int8_t visibility = 0;
    bool has_z = false;
    int32_t z_order = 0;
    bool has_buffer = false;
    AHardwareBuffer* buffer = nullptr;
    int acquire_fence = -1;
    bool has_geometry = false;
    ARect source{0, 0, 0, 0};
    ARect dest{0, 0, 0, 0};
    bool has_crop = false;
    ARect crop{0, 0, 0, 0};
    bool has_position = false;
    int32_t x = 0, y = 0;
    bool has_scale = false;
    float sx = 1.0f, sy = 1.0f;
    bool has_transform = false;
    int32_t transform = 0;
    bool has_transparency = false;
    int8_t transparency = 0;
    bool has_alpha = false;
    double alpha = 1.0;
    bool has_dataspace = false;
    int32_t dataspace = 0;
    bool has_color = false;
    float color[4] = {0, 0, 0, 0};
    bool has_frame_rate = false;
    float frame_rate = 0.0f;
    int8_t frame_rate_compat = 0;
    int8_t frame_rate_strategy = 0;
    bool has_parent = false;
    ASurfaceControl* new_parent = nullptr;
    bool has_back_pressure = false;
    bool back_pressure = false;
};

struct ASurfaceTransaction {
    std::map<ASurfaceControl*, SurfaceUpdate> updates;
    int64_t desired_present_time = 0;
    void* complete_ctx = nullptr;
    ASurfaceTransaction_OnComplete on_complete = nullptr;
    void* commit_ctx = nullptr;
    ASurfaceTransaction_OnCommit on_commit = nullptr;
};

struct ASurfaceTransactionStats {
    int64_t latch_time = 0;
    std::vector<ASurfaceControl*> controls;
    std::map<ASurfaceControl*, int64_t> acquire_times;
};

struct ASurfaceTexture {
    uint32_t tex_name = 0;
    bool attached = false;
    int64_t timestamp = 0;
    float matrix[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
};

namespace {
int64_t now_ns() {
    auto t = std::chrono::steady_clock::now().time_since_epoch();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(t).count();
}

void destroy_control(ASurfaceControl* sc) {
    if (!sc)
        return;
    if (sc->current_buffer)
        AHardwareBuffer_release(sc->current_buffer);
    if (sc->parent)
        ASurfaceControl_release(sc->parent);
    if (sc->parent_window)
        ANativeWindow_release(sc->parent_window);
    delete sc;
}

SurfaceUpdate& touch(ASurfaceTransaction* t, ASurfaceControl* sc) {
    auto it = t->updates.find(sc);
    if (it == t->updates.end()) {
        ASurfaceControl_acquire(sc);
        it = t->updates.emplace(sc, SurfaceUpdate{}).first;
    }
    return it->second;
}

void clear_updates(ASurfaceTransaction* t) {
    for (auto& kv : t->updates) {
        if (kv.second.buffer)
            AHardwareBuffer_release(kv.second.buffer);
        if (kv.second.acquire_fence >= 0)
            close(kv.second.acquire_fence);
        if (kv.second.new_parent)
            ASurfaceControl_release(kv.second.new_parent);
        ASurfaceControl_release(kv.first);
    }
    t->updates.clear();
}
} // namespace

extern "C" {

ASurfaceControl* ASurfaceControl_create(ASurfaceControl* parent, const char* debug_name) {
    auto* sc = new (std::nothrow) ASurfaceControl();
    if (!sc)
        return nullptr;
    sc->debug_name = debug_name ? debug_name : "";
    if (parent) {
        ASurfaceControl_acquire(parent);
        sc->parent = parent;
    }
    return sc;
}

ASurfaceControl* ASurfaceControl_createFromWindow(ANativeWindow* parent, const char* debug_name) {
    if (!parent)
        return nullptr;
    auto* sc = new (std::nothrow) ASurfaceControl();
    if (!sc)
        return nullptr;
    sc->debug_name = debug_name ? debug_name : "";
    ANativeWindow_acquire(parent);
    sc->parent_window = parent;
    return sc;
}

void ASurfaceControl_acquire(ASurfaceControl* sc) {
    if (sc)
        sc->refs.fetch_add(1, std::memory_order_relaxed);
}

void ASurfaceControl_release(ASurfaceControl* sc) {
    if (sc && sc->refs.fetch_sub(1, std::memory_order_acq_rel) == 1)
        destroy_control(sc);
}

ASurfaceControl* ASurfaceControl_fromJava(JNIEnv* env, jobject obj) {
    if (!env || !obj)
        return nullptr;
    return ASurfaceControl_create(nullptr, "fromJava");
}

ASurfaceTransaction* ASurfaceTransaction_create() {
    return new (std::nothrow) ASurfaceTransaction();
}

void ASurfaceTransaction_delete(ASurfaceTransaction* t) {
    if (!t)
        return;
    clear_updates(t);
    delete t;
}

void ASurfaceTransaction_setVisibility(ASurfaceTransaction* t, ASurfaceControl* sc, int8_t v) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_visibility = true;
    u.visibility = v;
}

void ASurfaceTransaction_setZOrder(ASurfaceTransaction* t, ASurfaceControl* sc, int32_t z) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_z = true;
    u.z_order = z;
}

void ASurfaceTransaction_setBuffer(ASurfaceTransaction* t, ASurfaceControl* sc,
                                   AHardwareBuffer* buffer, int acquire_fence_fd) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    if (u.buffer)
        AHardwareBuffer_release(u.buffer);
    if (u.acquire_fence >= 0)
        close(u.acquire_fence);
    u.has_buffer = true;
    u.buffer = buffer;
    if (buffer)
        AHardwareBuffer_acquire(buffer);
    u.acquire_fence = acquire_fence_fd;
}

void ASurfaceTransaction_setGeometry(ASurfaceTransaction* t, ASurfaceControl* sc,
                                     const ARect& source, const ARect& destination,
                                     int32_t transform) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_geometry = true;
    u.source = source;
    u.dest = destination;
    u.has_transform = true;
    u.transform = transform;
}

void ASurfaceTransaction_setCrop(ASurfaceTransaction* t, ASurfaceControl* sc, const ARect& crop) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_crop = true;
    u.crop = crop;
}

void ASurfaceTransaction_setPosition(ASurfaceTransaction* t, ASurfaceControl* sc, int32_t x,
                                     int32_t y) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_position = true;
    u.x = x;
    u.y = y;
}

void ASurfaceTransaction_setScale(ASurfaceTransaction* t, ASurfaceControl* sc, float sx, float sy) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_scale = true;
    u.sx = sx;
    u.sy = sy;
}

void ASurfaceTransaction_setBufferTransform(ASurfaceTransaction* t, ASurfaceControl* sc,
                                            int32_t transform) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_transform = true;
    u.transform = transform;
}

void ASurfaceTransaction_setBufferTransparency(ASurfaceTransaction* t, ASurfaceControl* sc,
                                               int8_t tr) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_transparency = true;
    u.transparency = tr;
}

void ASurfaceTransaction_setDamageRegion(ASurfaceTransaction* t, ASurfaceControl* sc, const ARect[],
                                         uint32_t) {
    if (!t || !sc)
        return;
    (void)touch(t, sc);
}

void ASurfaceTransaction_setDesiredPresentTime(ASurfaceTransaction* t, int64_t present) {
    if (t)
        t->desired_present_time = present;
}

void ASurfaceTransaction_setBufferAlpha(ASurfaceTransaction* t, ASurfaceControl* sc, double alpha) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_alpha = true;
    u.alpha = alpha;
}

void ASurfaceTransaction_setBufferDataSpace(ASurfaceTransaction* t, ASurfaceControl* sc,
                                            int32_t ds) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_dataspace = true;
    u.dataspace = ds;
}

void ASurfaceTransaction_setColor(ASurfaceTransaction* t, ASurfaceControl* sc, float r, float g,
                                  float b, float alpha, int32_t ds) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_color = true;
    u.color[0] = r;
    u.color[1] = g;
    u.color[2] = b;
    u.color[3] = alpha;
    u.has_dataspace = true;
    u.dataspace = ds;
}

void ASurfaceTransaction_setFrameRate(ASurfaceTransaction* t, ASurfaceControl* sc, float fr,
                                      int8_t compat) {
    ASurfaceTransaction_setFrameRateWithChangeStrategy(t, sc, fr, compat, 0);
}

void ASurfaceTransaction_setFrameRateWithChangeStrategy(ASurfaceTransaction* t, ASurfaceControl* sc,
                                                        float fr, int8_t compat, int8_t strategy) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_frame_rate = true;
    u.frame_rate = fr;
    u.frame_rate_compat = compat;
    u.frame_rate_strategy = strategy;
}

void ASurfaceTransaction_clearFrameRate(ASurfaceTransaction* t, ASurfaceControl* sc) {
    ASurfaceTransaction_setFrameRateWithChangeStrategy(t, sc, 0.0f, 0, 0);
}

void ASurfaceTransaction_setOnComplete(ASurfaceTransaction* t, void* ctx,
                                       ASurfaceTransaction_OnComplete func) {
    if (!t)
        return;
    t->complete_ctx = ctx;
    t->on_complete = func;
}

void ASurfaceTransaction_setOnCommit(ASurfaceTransaction* t, void* ctx,
                                     ASurfaceTransaction_OnCommit func) {
    if (!t)
        return;
    t->commit_ctx = ctx;
    t->on_commit = func;
}

void ASurfaceTransaction_reparent(ASurfaceTransaction* t, ASurfaceControl* sc,
                                  ASurfaceControl* new_parent) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    if (u.new_parent)
        ASurfaceControl_release(u.new_parent);
    u.has_parent = true;
    u.new_parent = new_parent;
    if (new_parent)
        ASurfaceControl_acquire(new_parent);
}

void ASurfaceTransaction_setEnableBackPressure(ASurfaceTransaction* t, ASurfaceControl* sc,
                                               bool enable) {
    if (!t || !sc)
        return;
    auto& u = touch(t, sc);
    u.has_back_pressure = true;
    u.back_pressure = enable;
}

void ASurfaceTransaction_apply(ASurfaceTransaction* t) {
    if (!t)
        return;
    ASurfaceTransactionStats stats{};
    stats.latch_time = now_ns();
    for (auto& kv : t->updates) {
        ASurfaceControl* sc = kv.first;
        SurfaceUpdate& u = kv.second;
        std::lock_guard<std::mutex> lock(sc->mutex);
        if (u.has_visibility)
            sc->visibility = u.visibility;
        if (u.has_z)
            sc->z_order = u.z_order;
        if (u.has_buffer) {
            if (sc->current_buffer)
                AHardwareBuffer_release(sc->current_buffer);
            sc->current_buffer = u.buffer;
            u.buffer = nullptr;
            sc->last_acquire_time = stats.latch_time;
        }
        if (u.has_geometry) {
            sc->source_rect = u.source;
            sc->dest_rect = u.dest;
        }
        if (u.has_crop)
            sc->crop = u.crop;
        if (u.has_position) {
            sc->x = u.x;
            sc->y = u.y;
        }
        if (u.has_scale) {
            sc->scale_x = u.sx;
            sc->scale_y = u.sy;
        }
        if (u.has_transform)
            sc->transform = u.transform;
        if (u.has_transparency)
            sc->transparency = u.transparency;
        if (u.has_alpha)
            sc->alpha = u.alpha;
        if (u.has_dataspace)
            sc->data_space = u.dataspace;
        if (u.has_color)
            std::memcpy(sc->color, u.color, sizeof(sc->color));
        if (u.has_frame_rate) {
            sc->frame_rate = u.frame_rate;
            sc->frame_rate_compat = u.frame_rate_compat;
            sc->frame_rate_strategy = u.frame_rate_strategy;
        }
        if (u.has_parent) {
            if (sc->parent)
                ASurfaceControl_release(sc->parent);
            sc->parent = u.new_parent;
            u.new_parent = nullptr;
        }
        if (u.has_back_pressure)
            sc->back_pressure = u.back_pressure;
        stats.controls.push_back(sc);
        stats.acquire_times[sc] = sc->last_acquire_time;
    }
    auto commit_fn = t->on_commit;
    auto commit_ctx = t->commit_ctx;
    auto complete_fn = t->on_complete;
    auto complete_ctx = t->complete_ctx;
    t->on_commit = nullptr;
    t->on_complete = nullptr;
    if (commit_fn)
        commit_fn(commit_ctx, &stats);
    if (complete_fn)
        complete_fn(complete_ctx, &stats);
    clear_updates(t);
}

int64_t ASurfaceTransactionStats_getLatchTime(ASurfaceTransactionStats* stats) {
    return stats ? stats->latch_time : -1;
}

void ASurfaceTransactionStats_getASurfaceControls(ASurfaceTransactionStats* stats,
                                                  ASurfaceControl*** outControls, size_t* outSize) {
    if (!outControls || !outSize)
        return;
    if (!stats || stats->controls.empty()) {
        *outControls = nullptr;
        *outSize = 0;
        return;
    }
    auto** arr = new (std::nothrow) ASurfaceControl*[stats->controls.size()];
    if (!arr) {
        *outControls = nullptr;
        *outSize = 0;
        return;
    }
    for (size_t i = 0; i < stats->controls.size(); ++i)
        arr[i] = stats->controls[i];
    *outControls = arr;
    *outSize = stats->controls.size();
}

void ASurfaceTransactionStats_releaseASurfaceControls(ASurfaceControl** controls) {
    delete[] controls;
}

int64_t ASurfaceTransactionStats_getAcquireTime(ASurfaceTransactionStats* stats,
                                                ASurfaceControl* sc) {
    if (!stats || !sc)
        return -1;
    auto it = stats->acquire_times.find(sc);
    return it != stats->acquire_times.end() ? it->second : -1;
}

int ASurfaceTransactionStats_getPreviousReleaseFenceFd(ASurfaceTransactionStats*,
                                                       ASurfaceControl*) {
    return -1;
}

ASurfaceTexture* ASurfaceTexture_fromSurfaceTexture(JNIEnv* env, jobject st) {
    if (!env || !st)
        return nullptr;
    return new (std::nothrow) ASurfaceTexture();
}

void ASurfaceTexture_release(ASurfaceTexture* st) {
    delete st;
}

ANativeWindow* ASurfaceTexture_acquireANativeWindow(ASurfaceTexture*) {
    return nullptr;
}

int ASurfaceTexture_attachToGLContext(ASurfaceTexture* st, uint32_t texName) {
    if (!st)
        return -EINVAL;
    st->tex_name = texName;
    st->attached = true;
    return 0;
}

int ASurfaceTexture_detachFromGLContext(ASurfaceTexture* st) {
    if (!st)
        return -EINVAL;
    st->attached = false;
    st->tex_name = 0;
    return 0;
}

int ASurfaceTexture_updateTexImage(ASurfaceTexture* st) {
    if (!st)
        return -EINVAL;
    if (!st->attached)
        return -ENOTSUP;
    st->timestamp = now_ns();
    return 0;
}

void ASurfaceTexture_getTransformMatrix(ASurfaceTexture* st, float mtx[16]) {
    if (!mtx)
        return;
    if (!st) {
        static const float id[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        std::memcpy(mtx, id, sizeof(id));
        return;
    }
    std::memcpy(mtx, st->matrix, sizeof(st->matrix));
}

int64_t ASurfaceTexture_getTimestamp(ASurfaceTexture* st) {
    return st ? st->timestamp : 0;
}

} // extern "C"
