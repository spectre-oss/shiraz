// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include "shiraz/window_frames.hpp"
#include "shiraz/shared_frame.hpp"
#include <atomic>
#include <cerrno>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <memory>
#include <new>
#include <unordered_set>
#include <vector>

struct ANativeWindow {
    std::atomic<unsigned> references{1};
    std::mutex mutex;
    int32_t width, height, initial_width, initial_height;
    bool locked = false;
    ShirazFrameSink frameSink;
    uint64_t revision=1;
    bool front_argb=false;
    std::unique_ptr<shiraz::SharedFrame> shared;
    uint64_t sharedRevision=0;
    std::vector<uint32_t> transfer;
    std::vector<uint8_t> front, back;
    ANativeWindow(int32_t w, int32_t h) : width(w), height(h), initial_width(w), initial_height(h),
        front(size_t(w) * h * 4), back(front.size()) {}
};
static std::mutex knownMutex;
static std::unordered_set<ANativeWindow*> knownWindows;
static bool valid_size(int32_t w, int32_t h) {
    return w > 0 && h > 0 && w <= 8192 && h <= 8192 && uint64_t(w) * h <= 16 * 1024 * 1024;
}
// Called with the local mutex held. Never replace a buffer lent to a producer.
static int sync_shared(ANativeWindow* w) {
    if (!w->shared || w->locked) return 0;
    try {
        int width=0,height=0; uint64_t revision=w->sharedRevision;
        if (!w->shared->read(width,height,revision,w->transfer,true)) return 0;
        if(width!=w->width||height!=w->height){
            std::vector<uint8_t> front(w->transfer.size()*4),back(front.size());
            w->front.swap(front);w->back.swap(back);w->width=width;w->height=height;
        }
        std::memcpy(w->front.data(),w->transfer.data(),w->front.size());
        w->front_argb=true;w->sharedRevision=revision;++w->revision;
        return 0;
    } catch (const std::bad_alloc&) { return -ENOMEM; }
      catch (...) { return -EIO; }
}
static int publish_shared(ANativeWindow* w,bool allowResize=true) {
    if (!w->shared) return 0;
    try {
        const auto count=size_t(w->width)*w->height;
        const uint32_t* pixels=reinterpret_cast<const uint32_t*>(w->front.data());
        if (!w->front_argb) {
            w->transfer.resize(count);
            for(size_t i=0;i<count;++i) {
                const auto* p=&w->front[i*4];
                w->transfer[i]=(uint32_t(p[3])<<24)|(uint32_t(p[0])<<16)|(uint32_t(p[1])<<8)|p[2];
            }
            pixels=w->transfer.data();
        }
        auto revision=w->shared->post(w->width,w->height,pixels,count,allowResize);
        if(!revision)return -EAGAIN;
        w->sharedRevision=revision;
        return 0;
    } catch (const std::bad_alloc&) { return -ENOMEM; }
      catch (...) { return -EIO; }
}
extern "C" ANativeWindow* shiraz_window_create(int32_t w, int32_t h) {
    if (!valid_size(w, h)) return nullptr;
    try {
        auto window = std::make_unique<ANativeWindow>(w, h);
        std::lock_guard<std::mutex> guard(knownMutex);
        knownWindows.insert(window.get());
        return window.release();
    } catch (const std::bad_alloc&) { return nullptr; }
}
extern "C" void ANativeWindow_acquire(ANativeWindow* w) { if (w) ++w->references; }
extern "C" void ANativeWindow_release(ANativeWindow* w) {
    if (!w || --w->references != 0) return;
    std::lock_guard<std::mutex> guard(knownMutex);
    knownWindows.erase(w);
    delete w;
}
extern "C" int32_t shiraz_window_known(ANativeWindow* w) {
    std::lock_guard<std::mutex> guard(knownMutex);
    return knownWindows.count(w) ? 1 : 0;
}
extern "C" int32_t ANativeWindow_getWidth(ANativeWindow* w) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex); int error=sync_shared(w);return error?error:w->width;
}
extern "C" int32_t ANativeWindow_getHeight(ANativeWindow* w) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex); int error=sync_shared(w);return error?error:w->height;
}
extern "C" int32_t ANativeWindow_getFormat(ANativeWindow* w) { return w ? 1 : -EINVAL; }
static int32_t set_geometry(ANativeWindow* w, int32_t width, int32_t height, int32_t format, bool hostResize) {
    if (!w || (format != 0 && format != 1)) return -EINVAL;
    if(hostResize&&!valid_size(width,height))return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    static const bool trace=[] {const char* value=std::getenv("SHIRAZ_TRACE_SURFACE");return value&&std::strcmp(value,"1")==0;}();
    static std::atomic<unsigned> traces{0};
    if(trace&&traces.fetch_add(1,std::memory_order_relaxed)<128)std::fprintf(stderr,"SHIRAZ_WINDOW_GEOMETRY requested=%dx%d current=%dx%d default=%dx%d\n",width,height,w->width,w->height,w->initial_width,w->initial_height);
    if (w->locked) return -EBUSY;
    if (width == 0 && height == 0) { width = w->initial_width; height = w->initial_height; }
    if (!valid_size(width, height)) return -EINVAL;
    int error=sync_shared(w);if(error)return error;
    // Reapplying the current geometry must not erase the last published frame.
    if(width==w->width&&height==w->height){if(hostResize){w->initial_width=width;w->initial_height=height;}return 0;}
    try {
        std::vector<uint8_t> front(size_t(width) * height * 4), back(front.size());
        w->front.swap(front); w->back.swap(back); w->width = width; w->height = height;w->front_argb=false;++w->revision;
    } catch (const std::bad_alloc&) { return -ENOMEM; }
    if(hostResize){w->initial_width=width;w->initial_height=height;}
    return publish_shared(w);
}
extern "C" int32_t ANativeWindow_setBuffersGeometry(ANativeWindow* w, int32_t width, int32_t height, int32_t format) {
    return set_geometry(w,width,height,format,false);
}
extern "C" int32_t shiraz_window_resize(ANativeWindow* w, int32_t width, int32_t height) {
    return set_geometry(w,width,height,1,true);
}
extern "C" int32_t ANativeWindow_lock(ANativeWindow* w, ANativeWindow_Buffer* buffer, ARect* dirty) {
    if (!w || !buffer) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (w->locked) return -EBUSY;
    int error=sync_shared(w);if(error)return error;
    // This first backend redraws the whole buffer rather than preserving a dirty region.
    *buffer = {}; buffer->width = w->width; buffer->height = w->height;
    buffer->stride = w->width; buffer->format = 1; buffer->bits = w->back.data();
    if (dirty) *dirty = {0, 0, w->width, w->height};
    w->locked = true;
    return 0;
}
extern "C" int32_t ANativeWindow_unlockAndPost(ANativeWindow* w) {
    if (!w) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if (!w->locked) return -EINVAL;
    w->front.swap(w->back); w->locked = false;w->front_argb=false;++w->revision;
    int error=publish_shared(w,false);if(error)return error;
    if (w->frameSink) w->frameSink(reinterpret_cast<const uint32_t*>(w->front.data()), w->width, w->height, false);
    return 0;
}
extern "C" int32_t shiraz_window_copy_argb(ANativeWindow* w, uint32_t* output, uint64_t capacity) {
    if (!w || !output) return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    int error=sync_shared(w);if(error)return error;
    auto count = uint64_t(w->width) * w->height;
    if (capacity < count) return -EINVAL;
    if(w->front_argb){std::memcpy(output,w->front.data(),size_t(count)*4);return 0;}
    for (uint64_t i = 0; i < count; ++i) {
        const auto* p = &w->front[i * 4];
        output[i] = (uint32_t(p[3]) << 24) | (uint32_t(p[0]) << 16) | (uint32_t(p[1]) << 8) | p[2];
    }
    return 0;
}

extern "C" uint64_t shiraz_window_revision(ANativeWindow* w){if(!w)return 0;std::lock_guard<std::mutex> guard(w->mutex);if(sync_shared(w))return 0;return w->revision;}

static int32_t post_argb_locked(ANativeWindow* w,const uint32_t* pixels,uint64_t count){
    if(w->locked)return -EBUSY;
    if(count!=uint64_t(w->width)*w->height)return -EINVAL;
    std::memcpy(w->front.data(),pixels,size_t(count)*4);w->front_argb=true;++w->revision;
    int error=publish_shared(w,false);if(error)return error;
    if(w->frameSink)w->frameSink(reinterpret_cast<const uint32_t*>(w->front.data()),w->width,w->height,true);
    return 0;
}

extern "C" int32_t shiraz_window_post_argb(ANativeWindow* w,const uint32_t* pixels,uint64_t count){
    if(!w||!pixels)return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    return post_argb_locked(w,pixels,count);
}
extern "C" int32_t shiraz_window_post_argb_sized(ANativeWindow* w,const uint32_t* pixels,uint64_t count,int32_t width,int32_t height){
    if(!w||!pixels||!valid_size(width,height)||count!=uint64_t(width)*height)return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    if(width!=w->width||height!=w->height)return -EAGAIN;
    return post_argb_locked(w,pixels,count);
}

void shiraz_window_set_frame_sink(ANativeWindow* w, ShirazFrameSink sink) {
    if (!w) return;
    std::lock_guard<std::mutex> guard(w->mutex); w->frameSink = std::move(sink);
}

extern "C" int shiraz_window_export(ANativeWindow* w) {
    if(!w)return -EINVAL;
    std::lock_guard<std::mutex> guard(w->mutex);
    try {
        if(!w->shared){
            w->shared=shiraz::SharedFrame::create();
            int error=publish_shared(w);
            if(error){w->shared.reset();return error;}
        }
        return w->shared->duplicate();
    } catch(const std::bad_alloc&){return -ENOMEM;}catch(...){return -EIO;}
}
extern "C" ANativeWindow* shiraz_window_import(int descriptor) {
    try {
        auto shared=shiraz::SharedFrame::import(descriptor);
        auto w=std::make_unique<ANativeWindow>(1,1);
        w->shared=std::move(shared);
        if(sync_shared(w.get())||!w->sharedRevision)return nullptr;
        w->initial_width=w->width;w->initial_height=w->height;
        std::lock_guard<std::mutex> guard(knownMutex);
        knownWindows.insert(w.get());return w.release();
    }catch(...){return nullptr;}
}
