// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/presentation.hpp"
#include <SDL.h>
#include <iostream>
#include <stdexcept>
static int actualPresents=0;
extern "C" void __real_SDL_RenderPresent(SDL_Renderer*);
extern "C" void __wrap_SDL_RenderPresent(SDL_Renderer* renderer){++actualPresents;__real_SDL_RenderPresent(renderer);}
static void require(bool value){if(!value)throw std::runtime_error("Presentation damage check failed");}
int main(int argc,char**){
    uint64_t revision=1;int ticks=0;bool restored=false,resized=false;
    auto tick=[&](shiraz::Surface& image){
        ++ticks;SDL_Window* window=SDL_GetWindowFromID(1);require(window);
        SDL_Renderer* renderer=SDL_GetRenderer(window);require(renderer);
        if(ticks==3){std::fill(image.pixels.begin(),image.pixels.end(),0xff123456);++revision;}
        if(ticks==6){
            require(!SDL_SetRenderDrawColor(renderer,255,0,0,255));require(!SDL_RenderClear(renderer));SDL_RenderPresent(renderer);
            SDL_Event exposed{};exposed.type=SDL_WINDOWEVENT;exposed.window.windowID=SDL_GetWindowID(window);exposed.window.event=SDL_WINDOWEVENT_EXPOSED;require(SDL_PushEvent(&exposed)==1);
        }
        if(ticks==8||ticks==12){
            uint32_t pixel=0;SDL_Rect rect{1,1,1,1};require(!SDL_RenderReadPixels(renderer,&rect,SDL_PIXELFORMAT_ARGB8888,&pixel,4));
            require(pixel==(ticks==8?0xff123456u:0xffabcdefu));
            if(ticks==8)restored=true;else resized=true;
        }
        if(ticks==10)SDL_SetWindowSize(window,96,60);
        if(ticks==15||ticks==20){SDL_Event reset{};reset.type=ticks==15?SDL_RENDER_DEVICE_RESET:SDL_RENDER_TARGETS_RESET;require(SDL_PushEvent(&reset)==1);}
        return true;
    };
    auto resize=[](shiraz::Surface& image,int w,int h){image=shiraz::Surface(w,h,0xffabcdef);}; // Revision intentionally unchanged.
    shiraz::present(shiraz::Surface(64,48,0xff010203),"Damage fixture",30,tick,{},{},{},resize,{},{},{},argc==1?std::function<uint64_t()>([&]{return revision;}):std::function<uint64_t()>());
    require(ticks==30&&restored&&resized);require(argc==1?actualPresents<=10:actualPresents==31);std::cout<<"PRESENTATION_EXPOSE_RESIZE_PIXELS_OK\n";
}
