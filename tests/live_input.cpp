// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/live_input.hpp"
#include "shiraz/presentation.hpp"
#include <cassert>
#include <cstdlib>
#include <iostream>
#include <filesystem>
int main(){
    std::string pattern=(std::filesystem::temp_directory_path()/"shiraz-live-test-XXXXXX").string();std::vector<char> storage(pattern.begin(),pattern.end());storage.push_back(0);char* path=storage.data();int file=mkstemp(path);assert(file>=0);
    auto append=[&](const std::string& data){assert(write(file,data.data(),data.size())==ssize_t(data.size()));};
    {
        shiraz::LiveInput input(path);append("down 2");assert(input.poll().empty());append(" 3\nup 2 3\n");auto lines=input.poll();assert(lines.size()==2&&lines[0]=="down 2 3");assert(input.poll().empty());
        assert(ftruncate(file,0)==0);bool rejected=false;try{input.poll();}catch(const std::runtime_error&){rejected=true;}assert(rejected);
    }
    assert(lseek(file,0,SEEK_SET)==0);
    assert(SDL_Init(SDL_INIT_VIDEO|SDL_INIT_EVENTS)==0);
    auto wheel=shiraz::live_event("wheel 20 30 0 -0.5",7);assert(wheel.type==SDL_MOUSEWHEEL&&wheel.wheel.windowID==7&&wheel.wheel.preciseY==-0.5f&&wheel.wheel.mouseX==20&&wheel.wheel.mouseY==30);
    auto finger=shiraz::live_event("fingerdown 3 0.25 0.75 0.5",7);assert(finger.type==SDL_FINGERDOWN&&finger.tfinger.windowID==7&&finger.tfinger.fingerId==3&&finger.tfinger.x==.25f&&finger.tfinger.pressure==.5f);
    auto closeEvent=shiraz::live_event("close",7);assert(closeEvent.type==SDL_WINDOWEVENT&&closeEvent.window.windowID==7&&closeEvent.window.event==SDL_WINDOWEVENT_CLOSE);
    auto gain=shiraz::live_event("focus 1",7);assert(gain.type==SDL_WINDOWEVENT&&gain.window.windowID==7&&gain.window.event==SDL_WINDOWEVENT_FOCUS_GAINED);
    assert(shiraz::live_event("focus 0",7).window.event==SDL_WINDOWEVENT_FOCUS_LOST);
    for(const char* invalid:{"focus","focus -1","focus 2","focus 1 extra"}){bool rejected=false;try{shiraz::live_event(invalid,7);}catch(const std::invalid_argument&){rejected=true;}assert(rejected);}
    bool trailing=false;try{shiraz::live_event("close extra",7);}catch(const std::invalid_argument&){trailing=true;}assert(trailing);
    auto text=shiraz::live_event("text \"caf\xc3\xa9\"",7);assert(text.type==SDL_TEXTINPUT&&text.text.windowID==7&&std::string(text.text.text)=="caf\xc3\xa9");
    for(const std::string& bad:{std::string("resize 0 10"),std::string("down -1 3"),std::string("down 1 2 extra"),std::string("text \"\xc0\x80\""),std::string("keydown NO_SUCH_KEY")}){
        bool rejected=false;try{shiraz::live_event(bad,7);}catch(const std::invalid_argument&){rejected=true;}assert(rejected);
    }
    shiraz::live_event("keydown \"Left Ctrl\"",7);shiraz::live_event("keydown \"Left Shift\"",7);
    auto privacy=shiraz::live_event("keydown P",7);assert((privacy.key.keysym.mod&(KMOD_LCTRL|KMOD_LSHIFT))==(KMOD_LCTRL|KMOD_LSHIFT));
    shiraz::live_event("keyup P",7);shiraz::live_event("keyup \"Left Shift\"",7);shiraz::live_event("keyup \"Left Ctrl\"",7);
    assert((shiraz::live_event("keydown P",7).key.keysym.mod&(KMOD_CTRL|KMOD_SHIFT))==0);shiraz::live_event("keyup P",7);
    shiraz::TestLocationCommand fix;assert(!shiraz::live_location("close",fix));assert(shiraz::live_location("location 29.6257993 52.5559203 5",fix));assert(std::abs(fix.latitude-29.6257993)<1e-8&&fix.accuracy==5);
    for(const char* bad:{"location 91 0 5","location 0 -181 5","location 0 0 0","location nan 0 5","location 0 0 inf","location 1 2","location 1 2 5 extra"}){bool rejected=false;try{shiraz::live_location(bad,fix);}catch(const std::invalid_argument&){rejected=true;}assert(rejected);}
    setenv("SHIRAZ_TEST_LOCATION","29.6257993,52.5559203",1);
    setenv("SHIRAZ_LIVE_INPUT",path,1);unsetenv("SHIRAZ_INPUT_SCRIPT");
    shiraz::Surface surface(10,20);int frames=0,pointers=0,keys=0,resizes=0,texts=0,locations=0,wheels=0;
    shiraz::present(surface,"Live input test",5,[&](shiraz::Surface&){
        if(frames==0)append("down 2 3\nup 2 3\nwheel 2 3 0 -0.5\ntext \"caf\xc3\xa9\"\nkeydown Return\nkeyup Return\nresize 20 10\nlocation 29.626 52.556 4\n");
        else if(frames==1)append("close\n");
        ++frames;return true;
    },[&](const shiraz::PointerEvent& e){assert(e.point.x==2&&e.point.y==3);if(e.action==shiraz::PointerAction::Scroll){assert(e.source==shiraz::mouse_source&&e.scrollX==0&&e.scrollY==-0.5f);++wheels;}else ++pointers;},[&](const shiraz::KeyEvent& e){assert(e.key==66);++keys;},[&](const std::string& s,int64_t){assert(s=="caf\xc3\xa9");++texts;},[&](shiraz::Surface& s,int w,int h){s=shiraz::Surface(w,h);++resizes;}, {}, {}, [&](double lat,double lon,float accuracy){assert(lat==29.626&&lon==52.556&&accuracy==4);assert(pointers==2&&keys==2&&texts==1&&resizes==1);++locations;});
    assert(frames==2&&pointers==2&&keys==2&&texts==1&&resizes==1&&locations==1&&wheels==1);
    unsetenv("SHIRAZ_TEST_LOCATION");assert(ftruncate(file,0)==0&&lseek(file,0,SEEK_SET)==0);append("location 1 2 5\n");bool gated=false;
    try{shiraz::present(surface,"Location opt-in test",1,{}, {}, {}, {}, {}, {}, {}, [&](double,double,float){assert(false);});}catch(const std::invalid_argument&){gated=true;}assert(gated);
    unsetenv("SHIRAZ_LIVE_INPUT");close(file);unlink(path);SDL_Quit();std::cout<<"LIVE_INPUT_SDL_DISPATCH_OK\n";
}
