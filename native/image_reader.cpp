// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/image_reader.h"
#include "shiraz/window_frames.hpp"
#include <chrono>
#include <algorithm>
#include <system_error>
#include <condition_variable>
#include <deque>
#include <memory>
#include <mutex>
#include <thread>
#include <unordered_set>
#include <vector>
#include <poll.h>
#include <sys/eventfd.h>
#include <unistd.h>
namespace {
constexpr int invalid = -10004, unsupported = -10002, unknown = -10000;
constexpr int empty = -30001, exhausted = -30002;
struct Frame { std::vector<uint32_t> rgba; int64_t timestamp; };
struct State {
    std::mutex mutex;
    std::condition_variable ready;
    bool closed = false;
    size_t notifications = 0;
    int width, height, format, maximum;
    uint64_t usage;
    AImageReader_ImageListener listener{};
    std::deque<Frame> frames;
    std::unordered_set<AImage*> acquired;
};
// Release fences defer buffer destruction without blocking the app callback or
// spawning one thread per image. The kernel retains DMA-buffer GPU references.
class Retirement {
    struct Entry { AHardwareBuffer* buffer; int fence; };
    std::mutex mutex;
    std::vector<Entry> entries;
    bool stopping = false;
    int wake = -1;
    std::thread worker;
    void signal() { uint64_t value=1; (void)write(wake,&value,sizeof(value)); }
    void run() {
        std::unique_lock<std::mutex> lock(mutex);
        for (;;) {
            if(stopping) {
                for(auto& entry:entries){close(entry.fence);AHardwareBuffer_release(entry.buffer);}
                break;
            }
            std::vector<pollfd> descriptors{{wake,POLLIN,0}};
            for(auto& entry:entries)descriptors.push_back({entry.fence,POLLIN,0});
            lock.unlock();
            int result;
            do {result=poll(descriptors.data(),descriptors.size(),-1);}while(result<0&&errno==EINTR);
            lock.lock();
            if(descriptors[0].revents){uint64_t value;(void)read(wake,&value,sizeof(value));}
            for(size_t i=descriptors.size();i>1;--i)if(descriptors[i-1].revents){
                auto entry=entries[i-2];close(entry.fence);AHardwareBuffer_release(entry.buffer);
                entries.erase(entries.begin()+i-2);
            }
        }
    }
public:
    Retirement(){
        wake=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);
        if(wake<0)throw std::system_error(errno,std::generic_category());
        try{worker=std::thread([this]{run();});}catch(...){close(wake);throw;}
    }
    ~Retirement(){ {std::lock_guard<std::mutex> lock(mutex);stopping=true;signal();}worker.join();close(wake); }
    void add(AHardwareBuffer* buffer,int fence) {
        std::lock_guard<std::mutex> lock(mutex);entries.push_back({buffer,fence});signal();
    }
};
Retirement& retirement(){static Retirement result;return result;}
}
struct AImage { std::shared_ptr<State> state; AHardwareBuffer* buffer; int64_t timestamp; mutable void* mapping = nullptr; };
struct AImageReader {
    std::shared_ptr<State> state;
    ANativeWindow* window = nullptr;
    std::thread callbacks;
    ~AImageReader() { if (window) ANativeWindow_release(window); }
};
extern "C" int AImageReader_newWithUsage(int32_t width,int32_t height,int32_t format,uint64_t usage,int32_t maximum,AImageReader** out) try {
    if(!out)return invalid;
    *out=nullptr;
    if(width<1||height<1||width>8192||height>8192||maximum<1||maximum>32||
       uint64_t(width)*height*4*(maximum+2)>256*1024*1024)return invalid;
    if(format!=1&&format!=34)return unsupported;
    if(format==34&&(usage&0xff))return invalid;
    AHardwareBuffer_Desc desc{uint32_t(width),uint32_t(height),1,1,usage|0x30,0,0,0};
    AHardwareBuffer* probe=nullptr;
    if(AHardwareBuffer_allocate(&desc,&probe))return unsupported;
    AHardwareBuffer_release(probe);
    auto state=std::make_shared<State>();state->width=width;state->height=height;state->format=format;state->maximum=maximum;state->usage=usage;
    auto reader=std::make_unique<AImageReader>();reader->state=state;reader->window=shiraz_window_create(width,height);
    if(!reader->window)return unknown;
    std::weak_ptr<State> weak=state;
    shiraz_window_set_frame_sink(reader->window,[weak](const uint32_t* data,int w,int h,bool argb){
        auto s=weak.lock();if(!s)return;
        std::unique_lock<std::mutex> lock(s->mutex);
        s->ready.wait(lock,[&]{return s->closed || s->frames.size()<2;});
        if(s->closed||w!=s->width||h!=s->height)return;
        try {
            Frame frame;frame.timestamp=std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
            frame.rgba.resize(size_t(w)*h);
            for(size_t i=0;i<frame.rgba.size();++i){uint32_t p=data[i];frame.rgba[i]=argb?(p&0xff00ff00)|((p>>16)&0xff)|((p&0xff)<<16):p;}
            s->frames.push_back(std::move(frame));++s->notifications;s->ready.notify_all();
        }catch(const std::bad_alloc&){/* Allocation pressure drops this producer frame. */}
    });
    AImageReader* pointer=reader.get();
    try {reader->callbacks=std::thread([state,pointer]{
        std::unique_lock<std::mutex> lock(state->mutex);
        for(;;){state->ready.wait(lock,[&]{return state->closed||state->notifications;});if(state->closed)break;
            --state->notifications;auto listener=state->listener;lock.unlock();
            if(listener.onImageAvailable)listener.onImageAvailable(listener.context,pointer);
            lock.lock();
        }
    });}catch(const std::system_error&){shiraz_window_set_frame_sink(reader->window,{});return unknown;}
    *out=reader.release();return 0;
} catch(const std::exception&) { return unknown; }
extern "C" int AImageReader_new(int32_t w,int32_t h,int32_t f,int32_t m,AImageReader** r){return AImageReader_newWithUsage(w,h,f,3,m,r);}
extern "C" void AImage_deleteAsync(AImage* image,int fence){
    if(!image){if(fence>=0)close(fence);return;}
    {std::lock_guard<std::mutex> lock(image->state->mutex);image->state->acquired.erase(image);}
    if(image->mapping)AHardwareBuffer_unlock(image->buffer,nullptr);
    if(fence>=0) {
        try { retirement().add(image->buffer,fence); }
        catch(const std::exception&) {
            // Only resource exhaustion uses a synchronous release fallback.
            pollfd descriptor{fence,POLLIN,0};
            while(poll(&descriptor,1,-1)<0 && errno==EINTR) {}
            close(fence);AHardwareBuffer_release(image->buffer);
        }
    } else AHardwareBuffer_release(image->buffer);
    delete image;
}
extern "C" void AImage_delete(AImage* image){AImage_deleteAsync(image,-1);}
extern "C" void AImageReader_delete(AImageReader* reader){
    if(!reader)return;
    auto state=reader->state;
    {std::lock_guard<std::mutex> lock(state->mutex);state->closed=true;state->ready.notify_all();}
    shiraz_window_set_frame_sink(reader->window,{});
    if(reader->callbacks.get_id()==std::this_thread::get_id())reader->callbacks.detach();else reader->callbacks.join();
    // Android invalidates all acquired AImages when their reader is deleted.
    for(;;){AImage* image;{std::lock_guard<std::mutex> lock(state->mutex);if(state->acquired.empty())break;image=*state->acquired.begin();}AImage_delete(image);}
    delete reader;
}
extern "C" int AImageReader_getWindow(AImageReader* reader,ANativeWindow** out){if(!reader||!out)return invalid;*out=reader->window;return 0;}
extern "C" int AImageReader_setImageListener(AImageReader* reader,AImageReader_ImageListener* listener){
    if(!reader)return invalid;
    std::lock_guard<std::mutex> lock(reader->state->mutex);reader->state->listener=listener?*listener:AImageReader_ImageListener{};return 0;
}
static int acquire(AImageReader* reader,AImage** out,int* fence,bool latest){
    if(out)*out=nullptr;
    if(fence)*fence=-1;
    if(!reader||!out)return invalid;
    auto state=reader->state;std::lock_guard<std::mutex> lock(state->mutex);
    if(state->closed)return invalid;
    if(state->acquired.size()>=size_t(state->maximum))return exhausted;
    if(state->frames.empty())return empty;
    // Android needs two available slots to discard old images for acquireLatest.
    if(latest&&size_t(state->maximum)-state->acquired.size()>=2)while(state->frames.size()>1)state->frames.pop_front();
    auto& frame=state->frames.front();
    AHardwareBuffer_Desc desc{uint32_t(state->width),uint32_t(state->height),1,1,state->usage|0x30,0,0,0};
    AHardwareBuffer* buffer=nullptr;if(AHardwareBuffer_allocate(&desc,&buffer))return unknown;
    void* pixels=nullptr;
    if(AHardwareBuffer_lock(buffer,0x30,-1,nullptr,&pixels)){AHardwareBuffer_release(buffer);return unknown;}
    AHardwareBuffer_describe(buffer,&desc);
    for(int y=0;y<state->height;++y)std::copy_n(frame.rgba.data()+size_t(y)*state->width,state->width,static_cast<uint32_t*>(pixels)+size_t(y)*desc.stride);
    AHardwareBuffer_unlock(buffer,nullptr);
    AImage* image=new(std::nothrow) AImage{state,buffer,frame.timestamp};
    if(!image){AHardwareBuffer_release(buffer);return unknown;}
    try{state->acquired.insert(image);}catch(const std::bad_alloc&){AHardwareBuffer_release(buffer);delete image;return unknown;}
    state->frames.pop_front();state->ready.notify_all();*out=image;return 0;
}
extern "C" int AImageReader_acquireNextImage(AImageReader* r,AImage** i){return acquire(r,i,nullptr,false);}
extern "C" int AImageReader_acquireNextImageAsync(AImageReader* r,AImage** i,int* f){return f?acquire(r,i,f,false):invalid;}
extern "C" int AImageReader_acquireLatestImage(AImageReader* r,AImage** i){return acquire(r,i,nullptr,true);}
extern "C" int AImageReader_acquireLatestImageAsync(AImageReader* r,AImage** i,int* f){return f?acquire(r,i,f,true):invalid;}
extern "C" int AImage_getHardwareBuffer(const AImage* i,AHardwareBuffer** b){if(!i||!b)return invalid;*b=i->buffer;return 0;}
extern "C" int AImage_getWidth(const AImage* i,int32_t* v){if(!i||!v)return invalid;*v=i->state->width;return 0;}
extern "C" int AImage_getHeight(const AImage* i,int32_t* v){if(!i||!v)return invalid;*v=i->state->height;return 0;}
extern "C" int AImage_getFormat(const AImage* i,int32_t* v){if(!i||!v)return invalid;*v=i->state->format;return 0;}
extern "C" int AImage_getTimestamp(const AImage* i,int64_t* v){if(!i||!v)return invalid;*v=i->timestamp;return 0;}

extern "C" int AImageReader_getWidth(AImageReader* r,int32_t* v){if(!r||!v)return invalid;*v=r->state->width;return 0;}
extern "C" int AImageReader_getHeight(AImageReader* r,int32_t* v){if(!r||!v)return invalid;*v=r->state->height;return 0;}
extern "C" int AImageReader_getFormat(AImageReader* r,int32_t* v){if(!r||!v)return invalid;*v=r->state->format;return 0;}
extern "C" int AImageReader_getMaxImages(AImageReader* r,int32_t* v){if(!r||!v)return invalid;*v=r->state->maximum;return 0;}
extern "C" int AImage_getNumberOfPlanes(const AImage* i,int32_t* v){if(!i||!v)return invalid;*v=i->state->format==34?0:1;return 0;}
extern "C" int AImage_getPlanePixelStride(const AImage* i,int p,int32_t* v){if(!i||!v||p||i->state->format==34)return invalid;*v=4;return 0;}
extern "C" int AImage_getPlaneRowStride(const AImage* i,int p,int32_t* v){if(!i||!v||p||i->state->format==34)return invalid;AHardwareBuffer_Desc d{};AHardwareBuffer_describe(i->buffer,&d);*v=d.stride*4;return 0;}
extern "C" int AImage_getPlaneData(const AImage* i,int p,uint8_t** data,int* length){
    if(!i||!data||!length||p||i->state->format==34)return invalid;
    *data=nullptr;*length=0;
    if(!(i->state->usage&0xf))return -30003;
    if(!i->mapping&&AHardwareBuffer_lock(i->buffer,3,-1,nullptr,&i->mapping))return -30003;
    AHardwareBuffer_Desc d{};AHardwareBuffer_describe(i->buffer,&d);
    *data=static_cast<uint8_t*>(i->mapping);*length=(d.height-1)*d.stride*4+d.width*4;return 0;
}
extern "C" int AImage_getCropRect(const AImage* i,ARect* r){if(!i||!r)return invalid;*r={0,0,i->state->width,i->state->height};return 0;}
