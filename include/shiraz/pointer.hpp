// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/compositor.hpp"
#include <cmath>
#include <algorithm>
#include <functional>
#include <limits>
#include <utility>
#include <vector>
#include <cstdint>
namespace shiraz {
enum class PointerAction { Down = 0, Up = 1, Move = 2, Cancel = 3, PointerDown = 5, PointerUp = 6, HoverMove = 7, Scroll = 8 };
constexpr int mouse_source = 0x2002, touch_source = 0x1002;
struct TouchContact { int64_t nativeId; int id; Point point; float pressure; };
struct PointerEvent { PointerAction action; Point point; int source; int64_t time; float scrollX=0,scrollY=0; std::vector<TouchContact> contacts{}; int actionIndex=0; };
/** One captured pointer, mapped from logical window coordinates to Android pixels. */
class PointerStream {
    bool active_ = false;
    int source_ = 0;
    Point last_{};
    int64_t time_ = 0;
    std::function<void(const PointerEvent&)> emit_;
public:
    explicit PointerStream(std::function<void(const PointerEvent&)> emit) : emit_(std::move(emit)) { }
    bool active() const { return active_; }
    bool accepts(int source) const { return active_ && source_ == source; }
    void sample(PointerAction action, Point point, Viewport viewport, int width, int height, int source, int64_t time = 0) {
        if (action == PointerAction::Cancel) { cancel(time); return; }
        if (!std::isfinite(point.x) || !std::isfinite(point.y) || !std::isfinite(viewport.x) || !std::isfinite(viewport.y) ||
            !std::isfinite(viewport.width) || !std::isfinite(viewport.height) || viewport.width <= 0 || viewport.height <= 0 || width <= 0 || height <= 0) return;
        if (action == PointerAction::Down) {
            if (active_) return;
            auto mapped = map_pointer(point, viewport, width, height);
            if (!mapped) return;
            active_ = true; source_ = source; last_ = *mapped;
        } else {
            if (!accepts(source)) return;
            Point mapped{(point.x - viewport.x) * width / viewport.width, (point.y - viewport.y) * height / viewport.height};
            if (!std::isfinite(mapped.x) || !std::isfinite(mapped.y) || std::abs(mapped.x) > std::numeric_limits<float>::max() || std::abs(mapped.y) > std::numeric_limits<float>::max()) return;
            last_ = mapped;
            if (action == PointerAction::Up) active_ = false;
        }
        time_ = std::max(time_, time);
        emit_({action, last_, source_, time_});
    }
    void cancel(int64_t time = 0) {
        if (!active_) return;
        active_ = false; time_ = std::max(time_, time); emit_({PointerAction::Cancel, last_, source_, time_});
    }
};
/** Android pointer IDs remain stable until their UP, including across index changes. */
class TouchStream {
    std::vector<TouchContact> contacts_;
    int64_t device_=0,time_=0;
    std::function<void(const PointerEvent&)> emit_;
    void emit(PointerAction action,int index,int64_t time) {
        time_=std::max(time_,time);
        PointerEvent event{action,contacts_.front().point,touch_source,time_};
        event.contacts=contacts_;event.actionIndex=index;emit_(event);
    }
public:
    explicit TouchStream(std::function<void(const PointerEvent&)> emit):emit_(std::move(emit)){}
    bool active() const{return !contacts_.empty();}
    void sample(PointerAction action,int64_t device,int64_t finger,Point point,float pressure,Viewport viewport,int width,int height,int64_t time) {
        if(active()&&device!=device_)return;
        if(!std::isfinite(point.x)||!std::isfinite(point.y)||!std::isfinite(pressure)||viewport.width<=0||viewport.height<=0||width<=0||height<=0)return;
        Point mapped{(point.x-viewport.x)*width/viewport.width,(point.y-viewport.y)*height/viewport.height};
        if(!std::isfinite(mapped.x)||!std::isfinite(mapped.y)||std::abs(mapped.x)>std::numeric_limits<float>::max()||std::abs(mapped.y)>std::numeric_limits<float>::max())return;
        auto found=std::find_if(contacts_.begin(),contacts_.end(),[&](const TouchContact& c){return c.nativeId==finger;});
        if(action==PointerAction::Down) {
            if(found!=contacts_.end()||contacts_.size()==32||!map_pointer(point,viewport,width,height))return;
            uint32_t used=0;for(const auto& c:contacts_)used|=uint32_t(1)<<c.id;
            int id=0;while(used&(uint32_t(1)<<id))++id;
            device_=device;contacts_.push_back({finger,id,mapped,std::clamp(pressure,0.f,1.f)});
            emit(contacts_.size()==1?PointerAction::Down:PointerAction::PointerDown,int(contacts_.size()-1),time);
        } else if(found!=contacts_.end()) {
            int index=int(found-contacts_.begin());found->point=mapped;found->pressure=std::clamp(pressure,0.f,1.f);
            if(action==PointerAction::Up){emit(contacts_.size()==1?PointerAction::Up:PointerAction::PointerUp,index,time);contacts_.erase(contacts_.begin()+index);}
            else if(action==PointerAction::Move)emit(PointerAction::Move,0,time);
        }
    }
    void cancel(int64_t time=0){if(active()){emit(PointerAction::Cancel,0,time);contacts_.clear();}}
};

}
