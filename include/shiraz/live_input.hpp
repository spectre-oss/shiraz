// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <SDL.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cerrno>
#include <cmath>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
namespace shiraz {
// Opt-in append-only local command stream. A newline commits one command.
class LiveInput {
    int fd_=-1;
    size_t offset_=0, commands_=0;
    std::string pending_;
public:
    explicit LiveInput(const char* path) {
        if(!path||!*path)return;
        fd_=open(path,O_RDONLY|O_CLOEXEC|O_NONBLOCK|O_NOFOLLOW);
        struct stat info{};
        if(fd_<0||fstat(fd_,&info)||!S_ISREG(info.st_mode)){
            if(fd_>=0)close(fd_);
            fd_=-1;throw std::invalid_argument("Live input requires an existing regular file");
        }
    }
    ~LiveInput(){if(fd_>=0)close(fd_);}
    LiveInput(const LiveInput&)=delete;
    LiveInput& operator=(const LiveInput&)=delete;
    std::vector<std::string> poll(){
        if(fd_<0)return {};
        struct stat info{};
        if(fstat(fd_,&info)||info.st_size<off_t(offset_)||info.st_size>65536)throw std::runtime_error("Live input truncated or exceeds 64KiB");
        char bytes[4096];ssize_t count=pread(fd_,bytes,sizeof(bytes),off_t(offset_));
        if(count<0){if(errno==EINTR)return {};throw std::runtime_error("Cannot read live input");}
        offset_+=size_t(count);pending_.append(bytes,size_t(count));
        std::vector<std::string> lines;
        while(lines.size()<32){auto end=pending_.find('\n');if(end==std::string::npos)break;
            if(end>1024||++commands_>1024)throw std::invalid_argument("Live input command bound");
            lines.push_back(pending_.substr(0,end));pending_.erase(0,end+1);
        }
        if(pending_.find('\n')==std::string::npos&&pending_.size()>1024)throw std::invalid_argument("Live input line exceeds 1KiB");
        return lines;
    }
};
struct TestLocationCommand {double latitude=0,longitude=0;float accuracy=5;};
inline bool live_location(const std::string& line,TestLocationCommand& result){
    std::istringstream row(line);std::string action,extra;row>>action;if(action!="location")return false;
    TestLocationCommand value;
    if(!(row>>value.latitude>>value.longitude>>value.accuracy)||(row>>extra)||!std::isfinite(value.latitude)||std::abs(value.latitude)>90||!std::isfinite(value.longitude)||std::abs(value.longitude)>180||!std::isfinite(value.accuracy)||value.accuracy<=0)throw std::invalid_argument("Live location requires latitude longitude positiveAccuracyMeters");
    result=value;return true;
}
inline SDL_Event live_event(const std::string& line,Uint32 window){
    std::istringstream row(line);std::string action,extra;SDL_Event event{};row>>action;
    if(action=="close"){
        event.type=SDL_WINDOWEVENT;event.window.windowID=window;event.window.event=SDL_WINDOWEVENT_CLOSE;
    }else if(action=="fingerdown"||action=="fingermove"||action=="fingerup"){
        int id;float x,y,pressure;
        if(!(row>>id>>x>>y>>pressure)||id<0||id>31||!std::isfinite(x)||!std::isfinite(y)||!std::isfinite(pressure)||x<0||x>1||y<0||y>1||pressure<0||pressure>1)throw std::invalid_argument("Invalid live finger contact");
        event.type=action=="fingerdown"?SDL_FINGERDOWN:action=="fingerup"?SDL_FINGERUP:SDL_FINGERMOTION;
        event.tfinger.windowID=window;event.tfinger.touchId=1;event.tfinger.fingerId=id;
        event.tfinger.x=x;event.tfinger.y=y;event.tfinger.pressure=pressure;
    }else if(action=="wheel"){
        int x,y;float dx,dy;
        if(!(row>>x>>y>>dx>>dy)||x<0||y<0||x>8192||y>8192||!std::isfinite(dx)||!std::isfinite(dy)||std::abs(dx)>128||std::abs(dy)>128)throw std::invalid_argument("Invalid live wheel");
        event.type=SDL_MOUSEWHEEL;event.wheel.windowID=window;event.wheel.x=int(dx);event.wheel.y=int(dy);
#if SDL_VERSION_ATLEAST(2,0,18)
        event.wheel.preciseX=dx;event.wheel.preciseY=dy;
#endif
#if SDL_VERSION_ATLEAST(2,26,0)
        event.wheel.mouseX=x;event.wheel.mouseY=y;
#else
        SDL_WarpMouseInWindow(SDL_GetWindowFromID(window),x,y);
#endif
    }else if(action=="text"){
        std::string value;if(!(row>>std::quoted(value))||value.empty()||value.size()>=sizeof(event.text.text)||value.find('\0')!=std::string::npos)throw std::invalid_argument("Live text requires 1..31 UTF-8 bytes");
        // Reject malformed UTF-8 before passing it to the normal text dispatcher.
        for(size_t i=0;i<value.size();){unsigned c=static_cast<unsigned char>(value[i++]);unsigned code=c;int n=0;
            if(c>=0xc2&&c<=0xdf){n=1;code=c&31;}else if(c>=0xe0&&c<=0xef){n=2;code=c&15;}else if(c>=0xf0&&c<=0xf4){n=3;code=c&7;}else if(c>=128)throw std::invalid_argument("Invalid live UTF-8");
            for(int j=0;j<n;++j){if(i>=value.size())throw std::invalid_argument("Truncated live UTF-8");unsigned next=static_cast<unsigned char>(value[i++]);if((next&192)!=128)throw std::invalid_argument("Invalid live UTF-8 continuation");code=(code<<6)|(next&63);}
            if((n==1&&code<128)||(n==2&&code<2048)||(n==3&&code<65536)||code>0x10ffff||(code>=0xd800&&code<=0xdfff))throw std::invalid_argument("Invalid live UTF-8 scalar");
        }
        event.type=SDL_TEXTINPUT;event.text.windowID=window;std::memcpy(event.text.text,value.data(),value.size());
    }else if(action=="keydown"||action=="keyup"){
        std::string name;if(!(row>>std::quoted(name)))throw std::invalid_argument("Missing live key name");
        SDL_Keycode key=SDL_GetKeyFromName(name.c_str());SDL_Scancode scan=SDL_GetScancodeFromKey(key);
        if(key==SDLK_UNKNOWN||scan==SDL_SCANCODE_UNKNOWN)throw std::invalid_argument("Unknown live key");
        event.type=action=="keydown"?SDL_KEYDOWN:SDL_KEYUP;event.key.windowID=window;event.key.state=action=="keydown"?SDL_PRESSED:SDL_RELEASED;event.key.keysym.sym=key;event.key.keysym.scancode=scan;
    }else{
        int x,y;if(!(row>>x>>y)||x<0||y<0||x>8192||y>8192)throw std::invalid_argument("Invalid live coordinates");
        if(action=="resize"){
            if(x==0||y==0||int64_t(x)*y>16*1024*1024)throw std::invalid_argument("Invalid live resize");
            event.type=SDL_WINDOWEVENT;event.window.windowID=window;event.window.event=SDL_WINDOWEVENT_SIZE_CHANGED;event.window.data1=x;event.window.data2=y;
        }else if(action=="move"){
            event.type=SDL_MOUSEMOTION;event.motion.windowID=window;event.motion.x=x;event.motion.y=y;event.motion.state=SDL_BUTTON_LMASK;
        }else if(action=="down"||action=="up"){
            event.type=action=="down"?SDL_MOUSEBUTTONDOWN:SDL_MOUSEBUTTONUP;event.button.windowID=window;event.button.button=SDL_BUTTON_LEFT;event.button.state=action=="down"?SDL_PRESSED:SDL_RELEASED;event.button.x=x;event.button.y=y;
        }else throw std::invalid_argument("Unknown live input action");
    }
    if(row>>extra)throw std::invalid_argument("Trailing live input fields");
    if(event.type==SDL_KEYDOWN||event.type==SDL_KEYUP){
        static thread_local Uint16 heldModifiers=0;
        Uint16 bit=0;switch(event.key.keysym.sym){case SDLK_LCTRL:bit=KMOD_LCTRL;break;case SDLK_RCTRL:bit=KMOD_RCTRL;break;case SDLK_LSHIFT:bit=KMOD_LSHIFT;break;case SDLK_RSHIFT:bit=KMOD_RSHIFT;break;case SDLK_LALT:bit=KMOD_LALT;break;case SDLK_RALT:bit=KMOD_RALT;break;case SDLK_LGUI:bit=KMOD_LGUI;break;case SDLK_RGUI:bit=KMOD_RGUI;break;default:break;}
        if(event.type==SDL_KEYDOWN)heldModifiers|=bit;else heldModifiers&=Uint16(~bit);
        event.key.keysym.mod=Uint16(heldModifiers|SDL_GetModState());
    }
    return event;
}
}
