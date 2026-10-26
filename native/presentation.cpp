// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/presentation.hpp"
#include "shiraz/gpu.hpp"
#include <SDL.h>
#include "shiraz/sdl_keyboard.hpp"
#include <deque>
#include <atomic>
#include "shiraz/live_input.hpp"
#include <cstring>
#include <iostream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <time.h>
#include <limits>
#include <fstream>
#include <sstream>
#include <cstdlib>

namespace shiraz {
namespace {
struct ReplayEvent {int frame,x,y;std::string action;};
std::vector<ReplayEvent> loadReplay() {
    const char* path=std::getenv("SHIRAZ_INPUT_SCRIPT");if(!path||!*path)return {};
    std::ifstream input(path,std::ios::binary);if(!input)throw std::invalid_argument("Cannot open input replay");
    std::string contents;char ch;while(input.get(ch)){if(contents.size()>=65536)throw std::invalid_argument("Input replay exceeds 64KiB");contents+=ch;}
    if(input.bad())throw std::runtime_error("Cannot read input replay");
    std::istringstream lines(contents);std::string line;std::vector<ReplayEvent> result;
    while(std::getline(lines,line)){
        if(line.find_first_not_of(" \t\r")==std::string::npos)continue;
        std::istringstream row(line);ReplayEvent event{};std::string extra;
        if(!(row>>event.frame>>event.action>>event.x>>event.y)||(row>>extra)||event.frame<0||event.frame>100000||(!result.empty()&&event.frame<result.back().frame)||result.size()>=256)
            throw std::invalid_argument("Invalid input replay row or ordering");
        if(event.action=="resize"){
            if(event.x<=0||event.y<=0||event.x>8192||event.y>8192||int64_t(event.x)*event.y>16*1024*1024)throw std::invalid_argument("Invalid replay resize");
        }else if((event.action!="down"&&event.action!="up"&&event.action!="move")||event.x<0||event.y<0||event.x>8192||event.y>8192)throw std::invalid_argument("Invalid replay pointer");
        result.push_back(event);
    }
    return result;
}
std::mutex presentation_mutex;
// Low bit is visibility; every request changes the serial so a user-dismissed
// keyboard can be reopened by another showSoftInput request.
std::atomic<unsigned> text_input_request{0};
thread_local bool presenting = false;
thread_local SDL_Window* active_window=nullptr;
struct ActivePresentation {
    ActivePresentation() { presenting = true; }
    ~ActivePresentation() { active_window=nullptr;presenting = false; }
};
struct Video {
    Video() { if (SDL_InitSubSystem(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) throw std::runtime_error(SDL_GetError()); SDL_EnableScreenSaver(); }
    ~Video() { SDL_EnableScreenSaver(); SDL_QuitSubSystem(SDL_INIT_VIDEO | SDL_INIT_EVENTS); }
};
std::unique_lock<std::mutex> acquirePresentation() {
    std::unique_lock<std::mutex> lock(presentation_mutex,std::try_to_lock);
    if(!lock.owns_lock())throw std::runtime_error("Another presentation is already active");
    return lock;
}
struct PresentationWindow {
    std::unique_lock<std::mutex> lock;
    Video video;
    std::unique_ptr<SDL_Window,decltype(&SDL_DestroyWindow)> window{nullptr,SDL_DestroyWindow};
    PresentationWindow(int width,int height,const std::string& title):lock(acquirePresentation()) {
        window.reset(SDL_CreateWindow(title.c_str(),SDL_WINDOWPOS_UNDEFINED,SDL_WINDOWPOS_UNDEFINED,width,height,SDL_WINDOW_RESIZABLE|SDL_WINDOW_ALLOW_HIGHDPI));
        if(!window)throw std::runtime_error(SDL_GetError());
    }
};
thread_local std::unique_ptr<PresentationWindow> preparedWindow;
thread_local double pixelScaleX=1,pixelScaleY=1;
int64_t eventTime(Uint32 timestamp = 0) {
    timespec now{};
    if (clock_gettime(CLOCK_MONOTONIC, &now)) throw std::runtime_error("Cannot read input clock");
    int64_t millis = int64_t(now.tv_sec) * 1000 + now.tv_nsec / 1000000;
    Uint32 elapsed = SDL_GetTicks() - timestamp;
    // SDL timestamps wrap at 32 bits. Unknown/future timestamps use delivery time.
    int64_t age = timestamp && elapsed <= Uint32(std::numeric_limits<int32_t>::max()) ? elapsed : 0;
    return age <= millis ? millis - age : millis;
}
void checked(int result) { if (result) throw std::runtime_error(SDL_GetError()); }
}
DisplayGeometry prepare_presentation(int width,int height) {
    DisplayGeometry::from(width,height,width,height);
    if(presenting||preparedWindow)throw std::runtime_error("Presentation already prepared");
    auto pending=std::make_unique<PresentationWindow>(width,height,"Shiraz");
    int lw,lh,pw,ph;SDL_GetWindowSize(pending->window.get(),&lw,&lh);
    SDL_GetWindowSizeInPixels(pending->window.get(),&pw,&ph);
    auto geometry=DisplayGeometry::from(lw,lh,pw,ph);
    preparedWindow=std::move(pending);return geometry;
}
void discard_prepared_presentation(){preparedWindow.reset();}
bool uses_wayland_text_input(){const char* driver=SDL_GetCurrentVideoDriver();return driver&&std::string(driver)=="wayland";}
void request_text_input(bool visible){unsigned old=text_input_request.load();while(!text_input_request.compare_exchange_weak(old,((old+2)&~1u)|unsigned(visible))) {}}
bool presentation_has_touchscreen() {
    std::unique_ptr<Video> video;
    if(!(SDL_WasInit(SDL_INIT_VIDEO)&SDL_INIT_VIDEO))video=std::make_unique<Video>();
    const int count=SDL_GetNumTouchDevices();
    if(count<0)throw std::runtime_error(SDL_GetError());
    for(int i=0;i<count;++i){const auto id=SDL_GetTouchDevice(i);if(id!=SDL_MOUSE_TOUCHID&&SDL_GetTouchDeviceType(id)==SDL_TOUCH_DEVICE_DIRECT)return true;}
    return false;
}
float presentation_refresh_rate() {
    // Dummy/headless backends and pre-window callers use a nominal scheduling rate.
    SDL_DisplayMode mode{};int index=active_window?SDL_GetWindowDisplayIndex(active_window):-1;
    if(index>=0&&SDL_GetCurrentDisplayMode(index,&mode)==0&&mode.refresh_rate>0&&mode.refresh_rate<=1000)return static_cast<float>(mode.refresh_rate);
    return 60.0f;
}
namespace { thread_local SDL_Cursor* active_cursor=nullptr;thread_local int cursor_type=-1; }
void request_pointer_icon(int type) {
    if(!active_window)throw std::runtime_error("No active presentation on this thread");
    if(type==cursor_type)return;
    if(type==0){SDL_ShowCursor(SDL_DISABLE);cursor_type=type;return;}
    SDL_SystemCursor shape=SDL_SYSTEM_CURSOR_ARROW;
    switch(type){case 1002:case 1020:case 1021:shape=SDL_SYSTEM_CURSOR_HAND;break;case 1004:shape=SDL_SYSTEM_CURSOR_WAIT;break;case 1006:case 1007:shape=SDL_SYSTEM_CURSOR_CROSSHAIR;break;case 1008:case 1009:shape=SDL_SYSTEM_CURSOR_IBEAM;break;case 1012:shape=SDL_SYSTEM_CURSOR_NO;break;case 1013:shape=SDL_SYSTEM_CURSOR_SIZEALL;break;case 1014:shape=SDL_SYSTEM_CURSOR_SIZEWE;break;case 1015:shape=SDL_SYSTEM_CURSOR_SIZENS;break;case 1016:shape=SDL_SYSTEM_CURSOR_SIZENESW;break;case 1017:shape=SDL_SYSTEM_CURSOR_SIZENWSE;break;default:break;}
    SDL_Cursor* next=SDL_CreateSystemCursor(shape);
    if(!next){const char* driver=SDL_GetCurrentVideoDriver();if(driver&&(std::string(driver)=="dummy"||std::string(driver)=="offscreen")){cursor_type=type;return;}throw std::runtime_error(SDL_GetError());}
    SDL_SetCursor(next);SDL_ShowCursor(SDL_ENABLE);if(active_cursor)SDL_FreeCursor(active_cursor);active_cursor=next;cursor_type=type;
}
void request_screen_awake(bool awake) {
    if(!active_window)throw std::runtime_error("No active presentation on this thread");
    if(awake){if(SDL_IsScreenSaverEnabled())SDL_DisableScreenSaver();}
    else if(!SDL_IsScreenSaverEnabled())SDL_EnableScreenSaver();
}
void request_window_title(const std::string& title) {
    if(!active_window)throw std::runtime_error("No active presentation on this thread");
    if(title.size()>16448||title.find('\0')!=std::string::npos)throw std::invalid_argument("Invalid window title");
    SDL_SetWindowTitle(active_window,title.c_str());
}
void request_window_size(int width,int height) {
    if(!active_window)throw std::runtime_error("No active presentation on this thread");
    if(width<1||height<1||width>8192||height>8192||uint64_t(width)*height>16*1024*1024)throw std::invalid_argument("Invalid window size request");
    width=std::max(1,int(std::lround(width/pixelScaleX)));height=std::max(1,int(std::lround(height/pixelScaleY)));
    int oldWidth,oldHeight;SDL_GetWindowSize(active_window,&oldWidth,&oldHeight);
    std::cerr<<"SHIRAZ_WINDOW request from="<<oldWidth<<"x"<<oldHeight<<" to="<<width<<"x"<<height<<" ms="<<SDL_GetTicks64()<<"\n";
    SDL_SetWindowSize(active_window,width,height);
}
bool set_clipboard_text(const std::string& text) {
    if(!active_window||text.size()>1024*1024||text.find('\0')!=std::string::npos)return false;
    return SDL_SetClipboardText(text.c_str())==0;
}
std::string get_clipboard_text(bool& available) {
    available=false;
    if(!active_window||!SDL_HasClipboardText())return {};
    char* raw=SDL_GetClipboardText();
    if(!raw)return {};
    std::string result(raw);
    SDL_free(raw);
    if(result.size()>1024*1024)result.resize(1024*1024);
    available=!result.empty();
    return result;
}
void present(const Surface& initial, const std::string& title, int frames, const std::function<bool(Surface&)>& tick, const std::function<void(const PointerEvent&)>& input, const std::function<void(const KeyEvent&)>& keyboard, const std::function<void(const std::string&, int64_t)>& text, const std::function<void(Surface&, int, int)>& resize, const std::function<void(bool)>& focus, const std::function<void(bool)>& visibility, const std::function<void(double,double,float)>& testLocation, const std::function<uint64_t()>& contentRevision, const std::function<void(Surface&,const DisplayGeometry&)>& displayChanged) {
    LiveInput live(std::getenv("SHIRAZ_LIVE_INPUT"));
    unsigned liveSequence=0;std::deque<TestLocationCommand> pendingLocations;
    const auto replay=loadReplay();size_t replayIndex=0;
    Surface image = initial;
    if (frames == 0 || frames < -1 || image.width <= 0 || image.height <= 0 ||
        image.pixels.size() != size_t(image.width) * image.height)
        throw std::invalid_argument("invalid presentation frame or dimensions");
    if (presenting) throw std::runtime_error("Nested presentation is not supported");
    auto owned=preparedWindow?std::move(preparedWindow):std::make_unique<PresentationWindow>(image.width,image.height,title);
    ActivePresentation active;
    auto& window=owned->window;
    SDL_SetWindowTitle(window.get(),title.c_str());
    const Uint32 liveResizeEvent=SDL_RegisterEvents(2);if(liveResizeEvent==Uint32(-1))throw std::runtime_error("Cannot allocate live resize event");
    active_window=window.get();
    std::cerr<<"SHIRAZ_WINDOW backend="<<SDL_GetCurrentVideoDriver()<<" initial="<<image.width<<"x"<<image.height<<"\n";
    if(visibility)visibility((SDL_GetWindowFlags(window.get())&(SDL_WINDOW_HIDDEN|SDL_WINDOW_MINIMIZED))==0);
    if(focus)focus((SDL_GetWindowFlags(window.get())&SDL_WINDOW_INPUT_FOCUS)!=0);
    struct CursorLifetime { ~CursorLifetime(){SDL_SetCursor(SDL_GetDefaultCursor());SDL_ShowCursor(SDL_ENABLE);if(active_cursor)SDL_FreeCursor(active_cursor);active_cursor=nullptr;cursor_type=-1;} } cursorLifetime;
    const char* requested=std::getenv("SHIRAZ_RENDERER");
    const std::string rendererMode=requested&&*requested?requested:"auto";
    if(rendererMode!="auto"&&rendererMode!="gpu"&&rendererMode!="software")throw std::invalid_argument("SHIRAZ_RENDERER must be auto, gpu, or software");
    // Presentation and Skia Canvas negotiate their capabilities separately.
    // An accelerated SDL texture alone does not imply hardware Canvas support.
    std::unique_ptr<SDL_Renderer, decltype(&SDL_DestroyRenderer)> renderer(nullptr,SDL_DestroyRenderer);
    if(rendererMode!="software"){
        renderer.reset(SDL_CreateRenderer(window.get(),-1,SDL_RENDERER_ACCELERATED|SDL_RENDERER_PRESENTVSYNC));
        if(!renderer)renderer.reset(SDL_CreateRenderer(window.get(),-1,SDL_RENDERER_ACCELERATED));
        if(!renderer){
            const std::string reason=SDL_GetError();
            if(rendererMode=="gpu")throw std::runtime_error("GPU presentation unavailable: "+reason);
            std::cerr<<"SHIRAZ_RENDERER_FALLBACK requested=auto reason="<<reason<<"\n";
        }
    }
    if(!renderer)renderer.reset(SDL_CreateRenderer(window.get(),-1,SDL_RENDERER_SOFTWARE));
    if (!renderer) throw std::runtime_error(SDL_GetError());
    SDL_RendererInfo rendererInfo{};checked(SDL_GetRendererInfo(renderer.get(),&rendererInfo));
    std::string deviceName="unknown",vendorName="unknown";
    if((rendererInfo.flags&SDL_RENDERER_ACCELERATED)&&SDL_GL_GetCurrentContext()){
        using GetString=const unsigned char* (*)(unsigned);
        auto getString=reinterpret_cast<GetString>(SDL_GL_GetProcAddress("glGetString"));
        const auto* device=getString?getString(0x1f01):nullptr;
        const auto* vendor=getString?getString(0x1f00):nullptr;
        if(device)deviceName=reinterpret_cast<const char*>(device);
        if(vendor)vendorName=reinterpret_cast<const char*>(vendor);
        std::string lower=deviceName;for(char& c:lower)if(c>='A'&&c<='Z')c=char(c-'A'+'a');
        bool cpuDriver=false;for(const char* name:{"llvmpipe","softpipe","swrast","software rasterizer","swiftshader"})if(lower.find(name)!=std::string::npos)cpuDriver=true;
        if(cpuDriver){
            if(rendererMode=="gpu")throw std::runtime_error("GPU presentation requested but OpenGL is software: "+deviceName);
            std::cerr<<"SHIRAZ_RENDERER_FALLBACK requested=auto reason=software-opengl device="<<deviceName<<"\n";
            renderer.reset();renderer.reset(SDL_CreateRenderer(window.get(),-1,SDL_RENDERER_SOFTWARE));
            if(!renderer)throw std::runtime_error(SDL_GetError());
            checked(SDL_GetRendererInfo(renderer.get(),&rendererInfo));
        }
    }
    if(rendererMode=="gpu"&&!(rendererInfo.flags&SDL_RENDERER_ACCELERATED))throw std::runtime_error("GPU presentation requested but SDL selected software");
    std::cerr << "SHIRAZ_PRESENTATION renderer=" << (rendererInfo.name ? rendererInfo.name : "unknown")
              << " accelerated=" << ((rendererInfo.flags & SDL_RENDERER_ACCELERATED) != 0)
              << " vsync=" << ((rendererInfo.flags & SDL_RENDERER_PRESENTVSYNC) != 0)
              << " requested=" << rendererMode << "\n";
    if(rendererInfo.flags&SDL_RENDERER_ACCELERATED)std::cerr<<"SHIRAZ_GPU device="<<deviceName<<" vendor="<<vendorName<<"\n";
    const bool gpuCanvas=(rendererInfo.flags&SDL_RENDERER_ACCELERATED)&&SDL_GL_GetCurrentContext()&&gpu_initialize(SDL_GL_GetProcAddress);
    struct GpuLifetime {~GpuLifetime(){gpu_shutdown();}} gpuLifetime;
    const char* canvasMode=std::getenv("SHIRAZ_CANVAS");
    if(canvasMode&&std::string(canvasMode)=="gpu"&&!gpuCanvas)throw std::runtime_error("GPU Canvas requested but unavailable");
    if(!gpuCanvas)std::cerr<<"SHIRAZ_CANVAS backend=software gpu=0\n";
    uint64_t gpuRevision=gpu_revision();
    std::unique_ptr<SDL_Texture, decltype(&SDL_DestroyTexture)> texture(
        SDL_CreateTexture(renderer.get(), SDL_PIXELFORMAT_ARGB8888, SDL_TEXTUREACCESS_STREAMING, image.width, image.height), SDL_DestroyTexture);
    if (!texture) throw std::runtime_error(SDL_GetError());
    checked(SDL_SetTextureBlendMode(texture.get(), SDL_BLENDMODE_BLEND));
    checked(SDL_SetTextureScaleMode(texture.get(),SDL_ScaleModeLinear));
    checked(SDL_UpdateTexture(texture.get(), nullptr, image.pixels.data(), image.width * sizeof(uint32_t)));
    int textureWidth=image.width, textureHeight=image.height;
    uint64_t uploadedRevision=contentRevision?contentRevision():0,uploads=1,skippedUploads=0;
    bool repaint=true;int presentedWidth=-1,presentedHeight=-1;uint64_t presents=0,skippedPresents=0;
    auto upload = [&](bool reset=false) {
        if(image.width<=0||image.height<=0||image.width>8192||image.height>8192||uint64_t(image.width)*image.height>16*1024*1024||image.pixels.size()!=size_t(image.width)*image.height)
            throw std::runtime_error("Invalid resized presentation buffer");
        const uint64_t revision=contentRevision?contentRevision():0;
        const bool resized=textureWidth!=image.width||textureHeight!=image.height;
        if(!reset&&!resized&&contentRevision&&revision==uploadedRevision){++skippedUploads;return;}
        if(resized||reset) {
            std::unique_ptr<SDL_Texture, decltype(&SDL_DestroyTexture)> replacement(
                SDL_CreateTexture(renderer.get(),SDL_PIXELFORMAT_ARGB8888,SDL_TEXTUREACCESS_STREAMING,image.width,image.height),SDL_DestroyTexture);
            if(!replacement)throw std::runtime_error(SDL_GetError());
            checked(SDL_SetTextureBlendMode(replacement.get(),SDL_BLENDMODE_BLEND));
            checked(SDL_SetTextureScaleMode(replacement.get(),SDL_ScaleModeLinear));
            texture.swap(replacement);textureWidth=image.width;textureHeight=image.height;
        }
        checked(SDL_UpdateTexture(texture.get(),nullptr,image.pixels.data(),image.width*sizeof(uint32_t)));
        uploadedRevision=revision;++uploads;repaint=true;
    };
    bool mouseCaptured = false;
    PointerStream pointer([&](const PointerEvent& event) {
        if (event.source == mouse_source) {
            if (event.action == PointerAction::Down) mouseCaptured = SDL_CaptureMouse(SDL_TRUE) == 0;
            if (event.action == PointerAction::Up || event.action == PointerAction::Cancel) { SDL_CaptureMouse(SDL_FALSE); mouseCaptured = false; }
        }
        if (input) input(event);
        else if (event.action == PointerAction::Down || event.action == PointerAction::Up)
            std::cout << "pointer " << (event.action == PointerAction::Down ? "down " : "up ") << event.point.x << ' ' << event.point.y << '\n';
    });
    struct TextInput {
        bool enabled,wayland;unsigned applied=~0u;
        explicit TextInput(bool value):enabled(value),wayland(std::string(SDL_GetCurrentVideoDriver())=="wayland"){sync();}
        void sync(){
            unsigned request=text_input_request.load();
            bool desired=enabled&&(!wayland||(request&1));
            if(applied==request)return;
            // A manually dismissed OSK may remain enabled at the protocol level.
            // Send a new activation edge for every explicit Android show request.
            if(desired){if(wayland)SDL_StopTextInput();SDL_StartTextInput();}
            else SDL_StopTextInput();
            applied=request;
        }
        ~TextInput(){SDL_StopTextInput();text_input_request.store(0);}
    } textInput{bool(text)};
    KeyboardStream keys([&](const KeyEvent& event) { if (keyboard) keyboard(event); });
    TouchStream touches([&](const PointerEvent& event){if(input)input(event);});
    Uint32 mouse = 0;
    auto sample = [&](PointerAction action, Point point, int source, Uint32 timestamp) {
        int w, h; SDL_GetWindowSize(window.get(), &w, &h);
        if (w > 0 && h > 0) pointer.sample(action, point, input_viewport(image.width,image.height,w,h,bool(displayChanged)), image.width, image.height, source, eventTime(timestamp));
    };
    auto ownWindow = [&](Uint32 id) { return id == 0 || id == SDL_GetWindowID(window.get()); };
    DisplayGeometry lastGeometry{0,0,0,0,0};
    auto syncGeometry=[&](Uint32 timestamp=0) {
        if(!resize&&!displayChanged)return;
        int lw,lh,pw,ph;SDL_GetWindowSize(window.get(),&lw,&lh);
        checked(SDL_GetRendererOutputSize(renderer.get(),&pw,&ph));
        if(lw<=0||lh<=0||pw<=0||ph<=0)return; // minimized/suspended output
        auto geometry=DisplayGeometry::from(lw,lh,pw,ph);
        if(geometry==lastGeometry)return;
        const int width=displayChanged?pw:lw,height=displayChanged?ph:lh;
        pixelScaleX=displayChanged?double(pw)/lw:1;pixelScaleY=displayChanged?double(ph)/lh:1;
        if(displayChanged||width!=image.width||height!=image.height){
            touches.cancel(eventTime(timestamp));pointer.cancel(eventTime(timestamp));keys.cancel(eventTime(timestamp));
            if(displayChanged){
                std::cerr<<"SHIRAZ_DISPLAY logical="<<lw<<"x"<<lh<<" pixels="<<pw<<"x"<<ph<<" density="<<geometry.densityDpi<<"\n";
                displayChanged(image,geometry);
            }else resize(image,width,height);
            if(image.width!=width||image.height!=height)throw std::runtime_error("Display callback did not update surface geometry");
            upload();
        }
        lastGeometry=geometry;
    };
    syncGeometry();
    bool running = true;
    for (int frame = 0; running && (frames < 0 || frame < frames); ) {
        textInput.sync();
        const Uint64 frameStarted=SDL_GetPerformanceCounter();
        while(replayIndex<replay.size()&&replay[replayIndex].frame==frame){
            const auto& action=replay[replayIndex++];SDL_Event injected{};
            if(action.action=="resize"){
                SDL_SetWindowSize(window.get(),action.x,action.y);injected.type=SDL_WINDOWEVENT;
                injected.window.windowID=SDL_GetWindowID(window.get());injected.window.event=SDL_WINDOWEVENT_SIZE_CHANGED;injected.window.data1=action.x;injected.window.data2=action.y;
            }else if(action.action=="move"){
                injected.type=SDL_MOUSEMOTION;injected.motion.windowID=SDL_GetWindowID(window.get());injected.motion.x=action.x;injected.motion.y=action.y;injected.motion.state=SDL_BUTTON_LMASK;
            }else{
                injected.type=action.action=="down"?SDL_MOUSEBUTTONDOWN:SDL_MOUSEBUTTONUP;injected.button.windowID=SDL_GetWindowID(window.get());
                injected.button.button=SDL_BUTTON_LEFT;injected.button.state=action.action=="down"?SDL_PRESSED:SDL_RELEASED;injected.button.x=action.x;injected.button.y=action.y;
            }
            if(SDL_PushEvent(&injected)!=1)throw std::runtime_error("Cannot enqueue replay input");
            std::cerr<<"SHIRAZ_INPUT_REPLAY "<<frame<<" "<<action.action<<" "<<action.x<<" "<<action.y<<"\n";
        }
        for(const auto& line:live.poll()){
            TestLocationCommand fix;
            if(live_location(line,fix)){
                const char* configured=std::getenv("SHIRAZ_TEST_LOCATION");
                if(!configured||!*configured||!testLocation)throw std::invalid_argument("Live location requires an explicitly configured test backend");
                pendingLocations.push_back(fix);SDL_Event command{};command.type=liveResizeEvent+1;command.user.windowID=SDL_GetWindowID(window.get());
                if(SDL_PushEvent(&command)!=1)throw std::runtime_error("Cannot enqueue test location");
                std::cerr<<"SHIRAZ_LIVE_INPUT "<<++liveSequence<<" mock_location_queued\n";continue;
            }
            SDL_Event command=live_event(line,SDL_GetWindowID(window.get()));
            if(command.type==SDL_WINDOWEVENT&&command.window.event==SDL_WINDOWEVENT_SIZE_CHANGED){
                // Apply commands in queue order: an earlier click still belongs to
                // the old geometry, even when a resize follows in the same batch.
                int width=command.window.data1,height=command.window.data2;command={};
                command.type=liveResizeEvent;command.user.windowID=SDL_GetWindowID(window.get());
                command.user.data1=reinterpret_cast<void*>(intptr_t(width));command.user.data2=reinterpret_cast<void*>(intptr_t(height));
            }
            if(SDL_PushEvent(&command)!=1)throw std::runtime_error("Cannot enqueue live input");
            std::cerr<<"SHIRAZ_LIVE_INPUT "<<++liveSequence<<" queued\n";
        }
        SDL_Event event;
        while (SDL_PollEvent(&event)) {
            if(event.type==SDL_WINDOWEVENT&&ownWindow(event.window.windowID)){
                switch(event.window.event){
                    case SDL_WINDOWEVENT_EXPOSED:case SDL_WINDOWEVENT_SIZE_CHANGED:
                    case SDL_WINDOWEVENT_SHOWN:case SDL_WINDOWEVENT_RESTORED:
                    case SDL_WINDOWEVENT_MAXIMIZED:repaint=true;break;
                    default:break;
                }
            }
            if(event.type==SDL_RENDER_TARGETS_RESET)repaint=true;
            if(event.type==SDL_RENDER_DEVICE_RESET)upload(true);
            if(event.type==liveResizeEvent+1&&ownWindow(event.user.windowID)){
                if(pendingLocations.empty())throw std::runtime_error("Test location queue mismatch");
                auto fix=pendingLocations.front();pendingLocations.pop_front();testLocation(fix.latitude,fix.longitude,fix.accuracy);continue;
            }
            if(event.type==liveResizeEvent&&ownWindow(event.user.windowID)){
                int width=int(reinterpret_cast<intptr_t>(event.user.data1)),height=int(reinterpret_cast<intptr_t>(event.user.data2));
                SDL_SetWindowSize(window.get(),width,height);event={};event.type=SDL_WINDOWEVENT;
                event.window.windowID=SDL_GetWindowID(window.get());event.window.event=SDL_WINDOWEVENT_SIZE_CHANGED;event.window.data1=width;event.window.data2=height;
            }
            if (event.type == SDL_QUIT || (event.type == SDL_WINDOWEVENT && ownWindow(event.window.windowID) && event.window.event == SDL_WINDOWEVENT_CLOSE) || (event.type == SDL_KEYDOWN && ownWindow(event.key.windowID) && event.key.keysym.sym == SDLK_ESCAPE && !keyboard)) {
                touches.cancel(eventTime(event.common.timestamp)); pointer.cancel(eventTime(event.common.timestamp)); running = false; break;
            }
            if((resize||displayChanged)&&event.type==SDL_WINDOWEVENT&&ownWindow(event.window.windowID)&&event.window.event==SDL_WINDOWEVENT_SIZE_CHANGED) {
                // A slow Activity recreation can leave many obsolete sizes queued.
                // Collapse only adjacent geometry/exposure events for this window;
                // input, focus and close events remain ordering barriers.
                auto validateSize=[](int w,int h){
                    if(w>8192||h>8192||(w>0&&h>0&&uint64_t(w)*h>16*1024*1024))
                        throw std::runtime_error("Window resize exceeds surface limits");
                };
                validateSize(event.window.data1,event.window.data2);
                SDL_Event next{};
                while(SDL_PeepEvents(&next,1,SDL_PEEKEVENT,SDL_FIRSTEVENT,SDL_LASTEVENT)==1 &&
                      next.type==SDL_WINDOWEVENT && ownWindow(next.window.windowID) &&
                      (next.window.event==SDL_WINDOWEVENT_SIZE_CHANGED ||
                       next.window.event==SDL_WINDOWEVENT_RESIZED || next.window.event==SDL_WINDOWEVENT_EXPOSED)) {
                    if(SDL_PeepEvents(&next,1,SDL_GETEVENT,SDL_FIRSTEVENT,SDL_LASTEVENT)!=1)
                        throw std::runtime_error("Cannot consume queued window geometry");
                    if(next.window.event==SDL_WINDOWEVENT_SIZE_CHANGED){
                        validateSize(next.window.data1,next.window.data2);
                        if(next.window.data1>0&&next.window.data2>0)event=next;
                    }
                }
                int width=event.window.data1,height=event.window.data2;
                int actualWidth,actualHeight;SDL_GetWindowSize(window.get(),&actualWidth,&actualHeight);
                if(width!=actualWidth||height!=actualHeight)continue; // obsolete configure notification
                syncGeometry(event.common.timestamp);
            }
            if(event.type==SDL_WINDOWEVENT&&ownWindow(event.window.windowID)&&event.window.event==SDL_WINDOWEVENT_DISPLAY_CHANGED)syncGeometry(event.common.timestamp);
            if (event.type == SDL_WINDOWEVENT && ownWindow(event.window.windowID) && event.window.event == SDL_WINDOWEVENT_FOCUS_LOST) {
                touches.cancel(eventTime(event.common.timestamp)); pointer.cancel(eventTime(event.common.timestamp)); keys.cancel(eventTime(event.common.timestamp));
                if(focus)focus(false);
            }
            if(event.type==SDL_WINDOWEVENT&&ownWindow(event.window.windowID)&&event.window.event==SDL_WINDOWEVENT_FOCUS_GAINED&&focus)focus(true);
            if(event.type==SDL_WINDOWEVENT&&ownWindow(event.window.windowID)&&visibility){switch(event.window.event){case SDL_WINDOWEVENT_HIDDEN:case SDL_WINDOWEVENT_MINIMIZED:touches.cancel(eventTime(event.common.timestamp));pointer.cancel(eventTime(event.common.timestamp));visibility(false);break;case SDL_WINDOWEVENT_SHOWN:case SDL_WINDOWEVENT_RESTORED:case SDL_WINDOWEVENT_MAXIMIZED:visibility(true);break;default:break;}}
            if (keyboard && (event.type == SDL_KEYDOWN || event.type == SDL_KEYUP) && ownWindow(event.key.windowID)) {
                int meta = android_meta(SDL_Keymod(event.key.keysym.mod));
                if (event.type == SDL_KEYDOWN) keys.down(event.key.keysym.scancode, android_key(event.key.keysym.sym), linux_scan(event.key.keysym.scancode), meta, event.key.repeat != 0, eventTime(event.key.timestamp), text && (text_input_request.load() & 1) && editor_text_key(event.key.keysym.sym, SDL_Keymod(event.key.keysym.mod)));
                else keys.up(event.key.keysym.scancode, meta, eventTime(event.key.timestamp));
            }
            if (event.type == SDL_TEXTEDITING_EXT) SDL_free(event.editExt.text); // Preedit is not committed text.
            if (text && event.type == SDL_TEXTINPUT && ownWindow(event.text.windowID)) {
                const auto length = strnlen(event.text.text, sizeof(event.text.text));
                if (length == sizeof(event.text.text)) throw std::runtime_error("Unterminated SDL text event");
                if (length) text(std::string(event.text.text, length), keys.textTime(eventTime(event.text.timestamp)));
            }
            if (event.type == SDL_WINDOWEVENT && ownWindow(event.window.windowID) && event.window.event == SDL_WINDOWEVENT_LEAVE && pointer.accepts(mouse_source) && !mouseCaptured) pointer.cancel(eventTime(event.common.timestamp));
            if ((event.type == SDL_MOUSEBUTTONDOWN || event.type == SDL_MOUSEBUTTONUP) && ownWindow(event.button.windowID) &&
                !touches.active() && event.button.which != SDL_TOUCH_MOUSEID && event.button.button == SDL_BUTTON_LEFT) {
                if (event.type == SDL_MOUSEBUTTONDOWN && !pointer.active()) {
                    mouse = event.button.which;
                    sample(PointerAction::Down, {double(event.button.x), double(event.button.y)}, mouse_source, event.common.timestamp);
                } else if (event.type == SDL_MOUSEBUTTONUP && pointer.accepts(mouse_source) && event.button.which == mouse)
                    sample(PointerAction::Up, {double(event.button.x), double(event.button.y)}, mouse_source, event.common.timestamp);
            }
            if(event.type==SDL_MOUSEWHEEL&&ownWindow(event.wheel.windowID)&&input&&!pointer.active()&&!touches.active()){
                int w,h;SDL_GetWindowSize(window.get(),&w,&h);
                int mx,my;SDL_GetMouseState(&mx,&my);
#if SDL_VERSION_ATLEAST(2,26,0)
                mx=event.wheel.mouseX;my=event.wheel.mouseY;
#endif
                auto mapped=map_pointer({double(mx),double(my)},input_viewport(image.width,image.height,w,h,bool(displayChanged)),image.width,image.height);
                float dx=event.wheel.x,dy=event.wheel.y;
#if SDL_VERSION_ATLEAST(2,0,18)
                dx=event.wheel.preciseX;dy=event.wheel.preciseY;
#endif
                if(event.wheel.direction==SDL_MOUSEWHEEL_FLIPPED){dx=-dx;dy=-dy;}
                if(mapped&&std::isfinite(dx)&&std::isfinite(dy))input({PointerAction::Scroll,*mapped,mouse_source,eventTime(event.common.timestamp),dx,dy});
            }
            if (!touches.active() && event.type == SDL_MOUSEMOTION && ownWindow(event.motion.windowID) && event.motion.which != SDL_TOUCH_MOUSEID) {
                if(pointer.active()&&event.motion.which==mouse)sample(PointerAction::Move,{double(event.motion.x),double(event.motion.y)},mouse_source,event.common.timestamp);
                else if(!pointer.active()&&input){int w,h;SDL_GetWindowSize(window.get(),&w,&h);auto mapped=map_pointer({double(event.motion.x),double(event.motion.y)},input_viewport(image.width,image.height,w,h,bool(displayChanged)),image.width,image.height);if(mapped)input({PointerAction::HoverMove,*mapped,mouse_source,eventTime(event.common.timestamp)});else request_pointer_icon(1000);}
            }
            if ((event.type == SDL_FINGERDOWN || event.type == SDL_FINGERMOTION || event.type == SDL_FINGERUP) && ownWindow(event.tfinger.windowID) && event.tfinger.touchId != SDL_MOUSE_TOUCHID) {
                if(!pointer.active()) {
                    int w,h;SDL_GetWindowSize(window.get(),&w,&h);
                    touches.sample(event.type==SDL_FINGERDOWN?PointerAction::Down:event.type==SDL_FINGERUP?PointerAction::Up:PointerAction::Move,
                        event.tfinger.touchId,event.tfinger.fingerId,{double(event.tfinger.x)*w,double(event.tfinger.y)*h},event.tfinger.pressure,
                        input_viewport(image.width,image.height,w,h,bool(displayChanged)),image.width,image.height,eventTime(event.common.timestamp));
                }
            }
        }
        if (!running) break;
        syncGeometry();
        gpu_prepare();
        if (tick) {
            if (!tick(image)) break;
            if (image.width != textureWidth || image.height != textureHeight)
                throw std::runtime_error("Frame callback changed presentation geometry");
            upload();
        }
        if(gpuRevision!=gpu_revision()){repaint=true;gpuRevision=gpu_revision();}
        int w, h; checked(SDL_GetRendererOutputSize(renderer.get(), &w, &h));
        if (w > 0 && h > 0 && (repaint||w!=presentedWidth||h!=presentedHeight)) {
            // Native-resolution pixels must not be resampled. Linear filtering
            // remains useful for fixed-size diagnostic surfaces/letterboxing.
            checked(SDL_SetTextureScaleMode(texture.get(),w==image.width&&h==image.height?SDL_ScaleModeNearest:SDL_ScaleModeLinear));
            auto v = fit(image.width, image.height, w, h);
            SDL_Rect rect{int(v.x), int(v.y), int(v.width), int(v.height)};
            if(!gpu_present(w,h,image.pixels.data(),image.width,image.height)){
                checked(SDL_SetRenderDrawColor(renderer.get(), 0, 0, 0, 255));
                checked(SDL_RenderClear(renderer.get()));
                checked(SDL_RenderCopy(renderer.get(), texture.get(), nullptr, &rect));
            }
            checked(SDL_RenderFlush(renderer.get()));
            // Opt-in app-surface observation reads the actual renderer output,
            // including GPU sampling, rather than only the CPU Canvas buffer.
            const char* capture=std::getenv("SHIRAZ_PRESENT_SNAPSHOT");
            if(capture&&*capture){
                int cw=0,ch=0;checked(SDL_GetRendererOutputSize(renderer.get(),&cw,&ch));
                if(cw<=0||ch<=0||cw>8192||ch>8192||int64_t(cw)*ch>16*1024*1024)throw std::runtime_error("Presentation snapshot bounds");
                std::vector<uint32_t> output(static_cast<size_t>(cw)*ch);
                checked(SDL_RenderReadPixels(renderer.get(),nullptr,SDL_PIXELFORMAT_ARGB8888,output.data(),cw*4));
                const std::string temporary=std::string(capture)+".tmp";
                std::ofstream file(temporary,std::ios::binary|std::ios::trunc);file<<"P6\n"<<cw<<' '<<ch<<"\n255\n";
                for(uint32_t pixel:output){const char rgb[]={char(pixel>>16),char(pixel>>8),char(pixel)};file.write(rgb,3);}
                file.close();if(!file||std::rename(temporary.c_str(),capture)!=0)throw std::runtime_error("Cannot write presentation snapshot");
            }
            SDL_RenderPresent(renderer.get());
            repaint=false;presentedWidth=w;presentedHeight=h;++presents;
        } else {++skippedPresents;}
        if (frames > 0) ++frame;
        // Rendering and vsync already consume the frame budget. Sleeping a
        // further 16 ms after them delays input and can halve the refresh rate.
        const double elapsed=1000.0*(SDL_GetPerformanceCounter()-frameStarted)/SDL_GetPerformanceFrequency();
        const double budget=1000.0/presentation_refresh_rate();
        if(elapsed<budget)SDL_Delay(static_cast<Uint32>(budget-elapsed));
    }
    const char* trace=std::getenv("SHIRAZ_TRACE_PRESENT");
    if(trace&&std::string(trace)=="1")std::cerr<<"SHIRAZ_TEXTURE_UPLOAD uploads="<<uploads<<" unchanged="<<skippedUploads<<"\n";
    if(trace&&std::string(trace)=="1")std::cerr<<"SHIRAZ_WINDOW_PRESENT presents="<<presents<<" unchanged="<<skippedPresents<<"\n";
    touches.cancel(eventTime()); pointer.cancel(eventTime()); keys.cancel(eventTime());
}
} // namespace shiraz
