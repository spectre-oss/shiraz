// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/image_reader.h"
#include <atomic>
#include <cassert>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <fcntl.h>
#include <mutex>
#include <sys/eventfd.h>
#include <thread>
#include <unistd.h>
#include <cstdio>
using namespace std::chrono_literals;
struct Notice {std::mutex mutex;std::condition_variable ready;int calls=0;std::thread::id thread;};
void notice(void* context,AImageReader*){auto& n=*static_cast<Notice*>(context);std::lock_guard<std::mutex> lock(n.mutex);++n.calls;n.thread=std::this_thread::get_id();n.ready.notify_all();}
void post(ANativeWindow* w,uint32_t color){uint32_t pixels[4]={color,color,color,color};assert(shiraz_window_post_argb(w,pixels,4)==0);}
void checkColor(AImage* image,const char* expected){uint8_t* data=nullptr;int length=0;assert(AImage_getPlaneData(image,0,&data,&length)==0&&length>=16&&std::memcmp(data,expected,4)==0);}
int main(){
    AImageReader* reader=nullptr;
    assert(AImageReader_new(0,2,1,2,&reader)==-10004&&!reader);
    assert(AImageReader_new(2,2,35,2,&reader)==-10002&&!reader);
    assert(AImageReader_new(2,2,1,2,&reader)==0&&reader);
    ANativeWindow* window=nullptr;assert(AImageReader_getWindow(reader,&window)==0&&window);
    Notice n;AImageReader_ImageListener listener{&n,notice};assert(AImageReader_setImageListener(reader,&listener)==0);
    AImage* first=nullptr;int fence=12;assert(AImageReader_acquireNextImageAsync(reader,&first,&fence)==-30001&&!first&&fence==-1);
    post(window,0xff123456);
    {std::unique_lock<std::mutex> lock(n.mutex);assert(n.ready.wait_for(lock,2s,[&]{return n.calls>=1;}));assert(n.thread!=std::this_thread::get_id());}
    assert(AImageReader_acquireNextImageAsync(reader,&first,&fence)==0&&fence==-1);checkColor(first,"\x12\x34\x56\xff");
    int64_t timestamp=0;assert(AImage_getTimestamp(first,&timestamp)==0&&timestamp>0);
    post(window,0xffabcdef);AImage* second=nullptr;assert(AImageReader_acquireNextImage(reader,&second)==0);checkColor(second,"\xab\xcd\xef\xff");
    AImage* third=nullptr;assert(AImageReader_acquireNextImage(reader,&third)==-30002);
    AImage_delete(first);AImage_delete(second);
    post(window,0xff010203);post(window,0xff040506);
    assert(AImageReader_acquireLatestImage(reader,&first)==0);checkColor(first,"\x04\x05\x06\xff");
    int signal=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK),release=dup(signal);assert(signal>=0&&release>=0);
    auto start=std::chrono::steady_clock::now();AImage_deleteAsync(first,release);
    assert(std::chrono::steady_clock::now()-start<100ms&&fcntl(release,F_GETFD)>=0);
    uint64_t value=1;assert(write(signal,&value,sizeof(value))==sizeof(value));
    for(int i=0;i<100&&fcntl(release,F_GETFD)>=0;++i)std::this_thread::sleep_for(5ms);
    assert(fcntl(release,F_GETFD)<0);close(signal);
    // NativeWindow's RGBA lock producer follows the same reader delivery path.
    ANativeWindow_Buffer raw{};assert(ANativeWindow_lock(window,&raw,nullptr)==0);
    std::memset(raw.bits,0x77,size_t(raw.stride)*raw.height*4);assert(ANativeWindow_unlockAndPost(window)==0);
    assert(AImageReader_acquireNextImage(reader,&first)==0);checkColor(first,"\x77\x77\x77\x77");AImage_delete(first);
    // Holding a hardware-buffer reference survives destruction of the reader/image.
    post(window,0xff112233);assert(AImageReader_acquireNextImage(reader,&first)==0);
    AHardwareBuffer* buffer=nullptr;assert(AImage_getHardwareBuffer(first,&buffer)==0);AHardwareBuffer_acquire(buffer);
    ANativeWindow_acquire(window);AImageReader_delete(reader);
    void* pixels=nullptr;assert(AHardwareBuffer_lock(buffer,3,-1,nullptr,&pixels)==0&&std::memcmp(pixels,"\x11\x22\x33\xff",4)==0);
    AHardwareBuffer_unlock(buffer,nullptr);AHardwareBuffer_release(buffer);post(window,0xff000000);ANativeWindow_release(window);
    // Callback deletion must not join its own thread or retain a dangling reader.
    assert(AImageReader_newWithUsage(2,2,34,0x100,2,&reader)==0);assert(AImageReader_getWindow(reader,&window)==0);ANativeWindow_acquire(window);
    std::atomic<bool> deleted{false};AImageReader_ImageListener deleting{&deleted,[](void* c,AImageReader* r){AImageReader_delete(r);static_cast<std::atomic<bool>*>(c)->store(true);}};
    AImageReader_setImageListener(reader,&deleting);post(window,0xff000000);
    for(int i=0;i<100&&!deleted;++i)std::this_thread::sleep_for(5ms);
    assert(deleted);
    ANativeWindow_release(window);
    // A full queue applies backpressure; deleting the reader wakes the producer.
    assert(AImageReader_new(2,2,1,2,&reader)==0);AImageReader_getWindow(reader,&window);ANativeWindow_acquire(window);
    post(window,0xff010101);post(window,0xff020202);
    std::atomic<bool> started{false},posted{false};
    std::thread producer([&]{started=true;post(window,0xff030303);posted=true;});
    while(!started)std::this_thread::yield();
    std::this_thread::sleep_for(20ms);assert(!posted);
    AImageReader_delete(reader);producer.join();assert(posted);ANativeWindow_release(window);
    std::puts("IMAGE_READER_DELIVERY_LIFETIME_FENCES_OK");
}
