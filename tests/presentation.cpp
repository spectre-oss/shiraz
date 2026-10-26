// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/presentation.hpp"
#include <SDL.h>
#include <atomic>
#include <cassert>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <unistd.h>

namespace {
std::atomic<bool> sent{false};
Uint32 close_window(Uint32, void*) {
    SDL_Event event{}; event.type = SDL_QUIT;
    sent.store(true);
    if (SDL_PushEvent(&event) != 1) std::abort();
    return 0;
}
}
int main() {
    auto hdpi=shiraz::DisplayGeometry::from(540,1076,1080,2152);
    assert(hdpi.densityDpi==320);
    auto fractional=shiraz::DisplayGeometry::from(800,600,1000,750);
    assert(fractional.densityDpi==200);
    auto mapped=shiraz::map_pointer({270,538},shiraz::fit(hdpi.pixelWidth,hdpi.pixelHeight,hdpi.logicalWidth,hdpi.logicalHeight),hdpi.pixelWidth,hdpi.pixelHeight);
    assert(mapped&&mapped->x==540&&mapped->y==1076);
    // A rounded fractional output must not create input-only letterboxing.
    auto fractionalViewport=shiraz::input_viewport(675,859,540,687,true);
    auto edge=shiraz::map_pointer({0,0},fractionalViewport,675,859);
    auto center=shiraz::map_pointer({270,343.5},fractionalViewport,675,859);
    assert(edge&&edge->x==0&&edge->y==0&&center&&center->x==337.5&&center->y==429.5);
    assert(!shiraz::map_pointer({0,0},shiraz::input_viewport(675,859,540,687,false),675,859));
    bool invalidScale=false;try{shiraz::DisplayGeometry::from(1,1,8192,8192);}catch(const std::invalid_argument&){invalidScale=true;}assert(invalidScale);
    auto startup=shiraz::prepare_presentation(32,48);
    assert(startup.pixelWidth==32&&startup.pixelHeight==48&&startup.densityDpi==160);
    bool nested=false;try{shiraz::prepare_presentation(32,48);}catch(const std::runtime_error&){nested=true;}assert(nested);
    shiraz::discard_prepared_presentation();
    startup=shiraz::prepare_presentation(32,48);
    int displayEvents=0;
    shiraz::present(shiraz::Surface(startup.pixelWidth,startup.pixelHeight),"prepared display",1,{},{},{},{},{},{},{},{},{},[&](shiraz::Surface& image,const shiraz::DisplayGeometry& geometry){
        ++displayEvents;assert(geometry.pixelWidth==32&&geometry.logicalHeight==48);image=shiraz::Surface(geometry.pixelWidth,geometry.pixelHeight);
    });
    assert(displayEvents==1);
    shiraz::discard_prepared_presentation(); // consumed by present, safe cleanup
    std::string replayName=std::string(std::getenv("TMPDIR")?std::getenv("TMPDIR"):"/tmp")+"/shiraz-replay-XXXXXX";char* replayPath=replayName.data();int replayFd=mkstemp(replayPath);assert(replayFd>=0);close(replayFd);
    {std::ofstream file(replayPath);file<<"1 down 1 1\n2 up 1 1\n3 resize 4 8\n4 down 3 7\n5 up 3 7\n";}
    setenv("SHIRAZ_INPUT_SCRIPT",replayPath,1);std::vector<shiraz::PointerEvent> replayed;int replayResizes=0;
    shiraz::present(shiraz::Surface(2,2,0xff000000),"replay",7,{},[&](const shiraz::PointerEvent& e){replayed.push_back(e);},{},{},[&](shiraz::Surface& surface,int w,int h){surface=shiraz::Surface(w,h,0xff000000);replayResizes++;});
    assert(replayed.size()==4&&replayed[0].action==shiraz::PointerAction::Down&&replayed[1].action==shiraz::PointerAction::Up&&replayed[2].point.x==3&&replayed[2].point.y==7&&replayResizes==1);
    for(const char* bad:{"1 down 1 1\n0 up 1 1\n","0 unknown 0 0\n","0 resize 8192 8192\n","0 down 1 1 trailing\n"}){
        {std::ofstream file(replayPath);file<<bad;}bool failed=false;try{shiraz::present(shiraz::Surface(2,2,0),"bad replay",1);}catch(const std::invalid_argument&){failed=true;}assert(failed);
    }
    unsetenv("SHIRAZ_INPUT_SCRIPT");unlink(replayPath);
    assert(shiraz::presentation_refresh_rate()==60.0f);
    shiraz::Surface image(2, 2, 0xff123456);
    bool rejected = false;
    try { shiraz::present(image, "invalid frames", 0); }
    catch (const std::invalid_argument&) { rejected = true; }
    assert(rejected);
    shiraz::present(image, "bounded frame", 1);
    shiraz::present(image,"screen awake",1,[&](shiraz::Surface&){
        assert(shiraz::presentation_refresh_rate()>0);
        shiraz::request_screen_awake(true);assert(!SDL_IsScreenSaverEnabled());
        shiraz::request_screen_awake(false);assert(SDL_IsScreenSaverEnabled());
        shiraz::request_screen_awake(true);return true;
    });
    shiraz::present(image,"screen awake reset",1,[&](shiraz::Surface&){assert(SDL_IsScreenSaverEnabled());return true;});
    // Desktop text delivery stays enabled when an Android app hides its soft IME.
    int textTicks=0;
    shiraz::present(image,"host text requests",4,[&](shiraz::Surface&){
        assert(SDL_IsTextInputActive());
        shiraz::request_text_input((++textTicks%2)==0);return true;
    },{},{},[](const std::string&,int64_t){});
    int ticks = 0;
    shiraz::present(image, "dispatched frames", -1, [&](shiraz::Surface& frame) {
        frame.pixels[0] = 0xffabcdef;
        return ++ticks < 3;
    });
    assert(ticks == 3);
    bool nestedRejected = false;
    shiraz::present(image, "nested callback", 1, [&](shiraz::Surface&) {
        try { shiraz::present(image, "nested window", 1); }
        catch (const std::runtime_error&) { nestedRejected = true; }
        return true;
    });
    assert(nestedRejected);
    int closeTicks = 0;
    std::vector<shiraz::PointerAction> pointerActions;
    shiraz::present(image, "pointer window close", -1, [&](shiraz::Surface&) {
        if (++closeTicks == 1) {
            SDL_Event down{}; down.type = SDL_MOUSEBUTTONDOWN; down.button.button = SDL_BUTTON_LEFT;
            assert(SDL_PushEvent(&down) == 1);
            SDL_Event close{}; close.type = SDL_WINDOWEVENT; close.window.event = SDL_WINDOWEVENT_CLOSE;
            assert(SDL_PushEvent(&close) == 1);
        }
        return closeTicks < 3;
    }, [&](const shiraz::PointerEvent& event) { pointerActions.push_back(event.action); });
    assert(closeTicks == 1 && pointerActions.size() == 2 && pointerActions[0] == shiraz::PointerAction::Down && pointerActions[1] == shiraz::PointerAction::Cancel);
    int resizeTicks=0, resizeCount=0;
    std::vector<shiraz::PointerEvent> resizedInput;
    shiraz::present(shiraz::Surface(16,32), "resize", 5, [&](shiraz::Surface& frame) {
        ++resizeTicks;
        if(resizeTicks==1||resizeTicks==3){
            SDL_Window* window=nullptr;for(Uint32 id=1;id<=64&&!window;++id)window=SDL_GetWindowFromID(id);assert(window);
            if(resizeTicks==1){SDL_Event down{};down.type=SDL_MOUSEBUTTONDOWN;down.button.button=SDL_BUTTON_LEFT;down.button.x=16;down.button.y=8;assert(SDL_PushEvent(&down)==1);}
            SDL_SetWindowSize(window,resizeTicks==1?32:16,resizeTicks==1?16:32);
            SDL_Event down{};down.type=SDL_MOUSEBUTTONDOWN;down.button.button=SDL_BUTTON_LEFT;down.button.x=resizeTicks==1?31:15;down.button.y=resizeTicks==1?15:31;assert(SDL_PushEvent(&down)==1);
            down.type=SDL_MOUSEBUTTONUP;assert(SDL_PushEvent(&down)==1);
        }
        if(resizeTicks==2)assert(frame.width==32&&frame.height==16&&frame.pixels.size()==512);
        if(resizeTicks==4)assert(frame.width==16&&frame.height==32&&frame.pixels.size()==512);
        return true;
    },[&](const shiraz::PointerEvent& event){resizedInput.push_back(event);},{},{},[&](shiraz::Surface& frame,int width,int height){
        ++resizeCount;frame=shiraz::Surface(width,height,0xffabcdef);
    });
    assert(resizeCount==2&&resizedInput.size()==6);
    assert(resizedInput[1].action==shiraz::PointerAction::Cancel);
    assert(resizedInput[2].point.x==31&&resizedInput[2].point.y==15);
    assert(resizedInput[4].point.x==15&&resizedInput[4].point.y==31);
    // Queued drag sizes must cause one recreation, while a following click
    // must be delivered against the final geometry (not a discarded size).
    int burstTicks=0,burstResizes=0;
    std::vector<shiraz::PointerEvent> burstInput;
    shiraz::present(shiraz::Surface(16,32), "resize burst", 3, [&](shiraz::Surface& frame){
        if(++burstTicks==1){
            shiraz::request_window_size(40,20);
            for(int w=20;w<=40;w++){
                SDL_Event e{};e.type=SDL_WINDOWEVENT;e.window.event=SDL_WINDOWEVENT_SIZE_CHANGED;
                e.window.data1=w;e.window.data2=20;assert(SDL_PushEvent(&e)==1);
                e.window.event=SDL_WINDOWEVENT_EXPOSED;assert(SDL_PushEvent(&e)==1);
            }
            SDL_Event e{};e.type=SDL_MOUSEBUTTONDOWN;e.button.button=SDL_BUTTON_LEFT;e.button.x=39;e.button.y=19;
            assert(SDL_PushEvent(&e)==1);e.type=SDL_MOUSEBUTTONUP;assert(SDL_PushEvent(&e)==1);
        }else assert(frame.width==40&&frame.height==20);
        return true;
    },[&](const shiraz::PointerEvent& e){burstInput.push_back(e);},{},{},[&](shiraz::Surface& frame,int w,int h){
        ++burstResizes;frame=shiraz::Surface(w,h);
    });
    assert(burstResizes==1&&burstInput.size()==2&&burstInput[0].point.x==39&&burstInput[0].point.y==19);
    // Stale sizes separated by an ordering barrier must be ignored without
    // reconfiguring the desktop window back to their old sizes.
    int staleTicks=0,staleResizes=0;
    shiraz::present(shiraz::Surface(16,32), "stale resize", 4, [&](shiraz::Surface& frame){
        if(++staleTicks==1){
            shiraz::request_window_size(40,20);SDL_FlushEvent(SDL_WINDOWEVENT);
            SDL_Event e{};e.type=SDL_WINDOWEVENT;e.window.event=SDL_WINDOWEVENT_SIZE_CHANGED;e.window.data1=20;e.window.data2=20;assert(SDL_PushEvent(&e)==1);
            SDL_Event barrier{};barrier.type=SDL_USEREVENT;assert(SDL_PushEvent(&barrier)==1);
            e.window.data1=40;assert(SDL_PushEvent(&e)==1);
        }else assert(frame.width==40&&frame.height==20);
        return true;
    },{},{},{},[&](shiraz::Surface& frame,int w,int h){
        SDL_Window* window=nullptr;for(Uint32 id=1;id<=128&&!window;++id)window=SDL_GetWindowFromID(id);assert(window);
        int actualW,actualH;SDL_GetWindowSize(window,&actualW,&actualH);assert(actualW==40&&actualH==20);
        ++staleResizes;frame=shiraz::Surface(w,h);
    });
    assert(staleResizes==1);
    bool propagated = false;
    try { shiraz::present(image, "callback failure", 1, [](shiraz::Surface&) -> bool { throw std::logic_error("frame failure"); }); }
    catch (const std::logic_error&) { propagated = true; }
    assert(propagated);
    shiraz::present(image, "after callback failure", 1);
    // Inspect actual presented pixels as well as upload counts: unchanged ticks
    // retain the texture, changed pixels upload, and geometry forces replacement.
    setenv("SHIRAZ_TRACE_PRESENT","1",1);
    std::ostringstream trace;auto* previous=std::cerr.rdbuf(trace.rdbuf());
    uint64_t revision=0;int revisionTicks=0;
    shiraz::present(image,"revision uploads",5,[&](shiraz::Surface& frame){
        ++revisionTicks;
        SDL_Window* window=nullptr;for(Uint32 id=1;id<=256&&!window;++id)window=SDL_GetWindowFromID(id);assert(window);
        if(revisionTicks==3||revisionTicks==5){
            SDL_Rect rect{0,0,1,1};uint32_t pixel=0;
            assert(SDL_RenderReadPixels(SDL_GetRenderer(window),&rect,SDL_PIXELFORMAT_ARGB8888,&pixel,4)==0);
            assert(pixel==(revisionTicks==3?0xffff0000u:0xff00ff00u));
        }
        if(revisionTicks==2){frame.pixels.assign(frame.pixels.size(),0xffff0000);++revision;}
        if(revisionTicks==3)shiraz::request_window_size(4,2);
        return true;
    },{},{},{},[&](shiraz::Surface& frame,int w,int h){frame=shiraz::Surface(w,h,0xff00ff00);},{},{},{},[&](){return revision;});
    std::cerr.rdbuf(previous);
    assert(trace.str().find("SHIRAZ_TEXTURE_UPLOAD uploads=3 unchanged=4")!=std::string::npos);
    trace.str("");previous=std::cerr.rdbuf(trace.rdbuf());
    shiraz::present(image,"untracked uploads",3,[](shiraz::Surface& frame){frame.pixels[0]^=0x00ffffff;return true;});
    std::cerr.rdbuf(previous);unsetenv("SHIRAZ_TRACE_PRESENT");
    assert(trace.str().find("SHIRAZ_TEXTURE_UPLOAD uploads=4 unchanged=0")!=std::string::npos);
    assert(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS | SDL_INIT_TIMER) == 0);
    auto timer = SDL_AddTimer(50, close_window, nullptr);
    assert(timer != 0);
    shiraz::present(image, "close event", -1);
    SDL_RemoveTimer(timer);
    assert(sent.load());
    SDL_Quit();
}
