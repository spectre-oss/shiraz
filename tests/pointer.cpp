// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/pointer.hpp"
#include <cassert>
#include <limits>
#include <vector>
int main() {
    using namespace shiraz;
    std::vector<PointerEvent> events;
    PointerStream stream([&](const PointerEvent& event) { events.push_back(event); });
    auto viewport = fit(100, 200, 400, 400);
    stream.sample(PointerAction::Down, {0, 0}, viewport, 100, 200, mouse_source);
    stream.sample(PointerAction::Down, {std::numeric_limits<double>::quiet_NaN(), 20}, viewport, 100, 200, mouse_source);
    assert(events.empty());
    stream.sample(PointerAction::Down, {150, 100}, viewport, 100, 200, mouse_source);
    assert(events.size() == 1 && events[0].point.x == 25 && events[0].point.y == 50);
    stream.sample(PointerAction::Down, {200, 100}, viewport, 100, 200, touch_source);
    assert(events.size() == 1);
    stream.sample(PointerAction::Move, {-20, 450}, viewport, 100, 200, mouse_source);
    assert(events.size() == 2 && events.back().point.x == -60 && events.back().point.y == 225);
    stream.sample(PointerAction::Up, {400, 400}, viewport, 100, 200, mouse_source);
    assert(events.back().point.x == 150 && events.back().point.y == 200 && !stream.active());
    stream.sample(PointerAction::Move, {200, 100}, viewport, 100, 200, mouse_source);
    assert(events.size() == 3);
    stream.sample(PointerAction::Down, {200, 100}, viewport, 100, 200, touch_source);
    stream.cancel(); stream.cancel();
    assert(events.size() == 5 && events.back().action == PointerAction::Cancel && events.back().source == touch_source && !stream.active());
    stream.sample(PointerAction::Down, {150, 100}, viewport, 100, 200, mouse_source, 100);
    stream.sample(PointerAction::Up, {150, 100}, viewport, 100, 200, mouse_source, 90);
    assert(events.back().time == 100 && !stream.active());
    events.clear();TouchStream touch([&](const PointerEvent& e){events.push_back(e);});
    touch.sample(PointerAction::Down,4,100,{150,100},.7f,viewport,100,200,10);
    touch.sample(PointerAction::Down,4,900,{250,100},.8f,viewport,100,200,11);
    assert(events.size()==2&&events.back().action==PointerAction::PointerDown&&events.back().actionIndex==1);
    assert(events.back().contacts[0].id==0&&events.back().contacts[1].id==1&&events.back().contacts[1].point.x==75);
    touch.sample(PointerAction::Move,99,900,{280,100},1,viewport,100,200,12);assert(events.size()==2);
    touch.sample(PointerAction::Move,4,900,{300,100},1,viewport,100,200,13);
    assert(events.back().contacts.size()==2&&events.back().contacts[0].point.x==25&&events.back().contacts[1].point.x==100);
    touch.sample(PointerAction::Up,4,100,{150,100},0,viewport,100,200,14);
    assert(events.back().action==PointerAction::PointerUp&&events.back().actionIndex==0&&events.back().contacts.size()==2);
    touch.sample(PointerAction::Move,4,900,{310,100},1,viewport,100,200,15);
    assert(events.back().contacts.size()==1&&events.back().contacts[0].id==1);
    touch.cancel(16);assert(events.back().action==PointerAction::Cancel&&!touch.active());
    auto count=events.size();touch.sample(PointerAction::Up,4,900,{310,100},0,viewport,100,200,17);assert(events.size()==count);
    touch.sample(PointerAction::Down,4,7,{150,100},1,viewport,100,200,18);
    touch.sample(PointerAction::Up,4,7,{150,100},0,viewport,100,200,19);
    assert(events.back().action==PointerAction::Up&&!touch.active());

}
