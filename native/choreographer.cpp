// SPDX-License-Identifier: GPL-3.0-only
// Software frame pacing for the host display. Callbacks run on the owning
// ALooper; this is not a compositor presentation-timestamp implementation.
#if defined(__APPLE__)
#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>
#include <fcntl.h>
#include <time.h>
#else
#include <sys/timerfd.h>
#endif
#include <unistd.h>
#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <vector>

struct ALooper;
extern "C" {
ALooper* ALooper_forThread();
void ALooper_acquire(ALooper*);
void ALooper_release(ALooper*);
int ALooper_addFd(ALooper*,int,int,int,int (*)(int,int,void*),void*);
int ALooper_removeFd(ALooper*,int);
}
namespace {
constexpr int64_t period = 16666667;
int64_t now() {
    timespec value{};
    clock_gettime(CLOCK_MONOTONIC,&value);
    return int64_t(value.tv_sec)*1000000000+value.tv_nsec;
}
#if defined(__APPLE__)
// timerfd replacement: a private kqueue holding one EVFILT_TIMER knote. A kqueue
// fd is readable while it has pending events, so it is watched through the
// public ALooper_addFd API exactly like the Linux timerfd.
int timer_create_fd() {
    const int fd=kqueue();
    if(fd>=0&&fcntl(fd,F_SETFD,FD_CLOEXEC)) { close(fd); return -1; }
    return fd;
}
#endif
}
struct AChoreographer {
    struct Pending {
        int64_t due;
        void (*wide)(int64_t,void*);
        void (*legacy)(long,void*);
        void* data;
    };
    ALooper* looper;
    int timer;
    std::mutex mutex;
    std::vector<Pending> pending;
#if defined(__APPLE__)
    explicit AChoreographer(ALooper* owner):looper(owner),timer(timer_create_fd()) { ALooper_acquire(looper); }
#else
    explicit AChoreographer(ALooper* owner):looper(owner),timer(timerfd_create(CLOCK_MONOTONIC,TFD_CLOEXEC|TFD_NONBLOCK)) { ALooper_acquire(looper); }
#endif
    ~AChoreographer() {
        if(timer>=0) { ALooper_removeFd(looper,timer); close(timer); }
        ALooper_release(looper);
    }
#if defined(__APPLE__)
    // One-shot relative timer to the earliest due time; a past due fires at once.
    // Relative nanoseconds keep CLOCK_MONOTONIC as the only clock (NOTE_ABSOLUTE
    // would use the wall clock or mach time instead).
    void arm() {
        struct kevent change;
        if(pending.empty()) EV_SET(&change,0,EVFILT_TIMER,EV_DELETE,0,0,nullptr);
        else {
            int64_t due=std::min_element(pending.begin(),pending.end(),[](const Pending& a,const Pending& b){return a.due<b.due;})->due;
            const int64_t delay=std::max<int64_t>(0,due-now());
            EV_SET(&change,0,EVFILT_TIMER,EV_ADD|EV_ONESHOT,NOTE_NSECONDS|NOTE_CRITICAL,delay,nullptr);
        }
        kevent(timer,&change,1,nullptr,0,nullptr);
    }
#else
    void arm() {
        itimerspec value{};
        if(!pending.empty()) {
            int64_t due=std::min_element(pending.begin(),pending.end(),[](const Pending& a,const Pending& b){return a.due<b.due;})->due;
            value.it_value.tv_sec=due/1000000000;
            value.it_value.tv_nsec=due%1000000000;
        }
        timerfd_settime(timer,TFD_TIMER_ABSTIME,&value,nullptr);
    }
#endif
    static int dispatch(int fd,int,void* data) {
        auto& self=*static_cast<AChoreographer*>(data);
#if defined(__APPLE__)
        struct kevent fired;
        const timespec immediate{};
        if(kevent(fd,nullptr,0,&fired,1,&immediate)!=1||fired.filter!=EVFILT_TIMER)return 1;
#else
        uint64_t expirations;
        if(read(fd,&expirations,sizeof(expirations))!=sizeof(expirations))return 1;
#endif
        const int64_t frame=now();
        std::vector<Pending> ready;
        {
            std::lock_guard<std::mutex> lock(self.mutex);
            auto it=self.pending.begin();
            while(it!=self.pending.end()) {
                if(it->due<=frame) { ready.push_back(*it); it=self.pending.erase(it); }
                else ++it;
            }
            self.arm();
        }
        // Callbacks may post again; never hold the queue mutex while invoking app code.
        for(const auto& item:ready) {
            if(item.wide)item.wide(frame,item.data);
            else item.legacy(static_cast<long>(frame),item.data);
        }
        return 1;
    }
    void post(void (*wide)(int64_t,void*),void (*legacy)(long,void*),void* data,uint64_t delay) {
        if(!wide&&!legacy)return;
        const int64_t current=now();
        const uint64_t maximum=(uint64_t(std::numeric_limits<int64_t>::max())-current-period)/1000000;
        const int64_t requested=current+int64_t(std::min(delay,maximum))*1000000;
        const int64_t due=(requested/period+1)*period;
        std::lock_guard<std::mutex> lock(mutex);
        pending.push_back({due,wide,legacy,data});
        arm();
    }
};
namespace { thread_local std::unique_ptr<AChoreographer> current; }
extern "C" AChoreographer* AChoreographer_getInstance() {
    if(current)return current.get();
    ALooper* looper=ALooper_forThread();
    if(!looper)return nullptr;
    auto value=std::make_unique<AChoreographer>(looper);
    if(value->timer<0||ALooper_addFd(looper,value->timer,0,1,AChoreographer::dispatch,value.get())!=1)return nullptr;
    current=std::move(value);
    return current.get();
}
extern "C" void AChoreographer_postFrameCallback(AChoreographer* c,void (*callback)(long,void*),void* data) { if(c)c->post(nullptr,callback,data,0); }
extern "C" void AChoreographer_postFrameCallback64(AChoreographer* c,void (*callback)(int64_t,void*),void* data) { if(c)c->post(callback,nullptr,data,0); }
extern "C" void AChoreographer_postFrameCallbackDelayed(AChoreographer* c,void (*callback)(long,void*),void* data,long delay) { if(c)c->post(nullptr,callback,data,delay>0?uint64_t(delay):0); }
extern "C" void AChoreographer_postFrameCallbackDelayed64(AChoreographer* c,void (*callback)(int64_t,void*),void* data,uint32_t delay) { if(c)c->post(callback,nullptr,data,delay); }
