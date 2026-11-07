#include "shiraz/window_aspect.hpp"
#include <cassert>
#include <cmath>
#include <iostream>

int main() {
    using shiraz::window_aspect;using shiraz::snap_window_aspect;
    // Ratio sources.
    assert(std::fabs(window_aspect(nullptr,360,640)-640.0/360)<1e-9);
    assert(window_aspect(nullptr,500,500)==0);
    assert(window_aspect("free",360,640)==0);
    assert(std::fabs(window_aspect("9:19.5",360,640)-19.5/9)<1e-9);
    for(const char* bad:{"16","a:b","0:9","9:","9:x1","50:1"}){
        bool threw=false;try{window_aspect(bad,360,640);}catch(const std::invalid_argument&){threw=true;}
        assert(threw);
    }
    const double r=640.0/360;
    // Width drag in portrait keeps portrait and fixes the height.
    int w=400,h=640;snap_window_aspect(r,360,640,0,0,w,h);assert(w==400&&h==711);
    // Height drag in portrait fixes the width.
    w=360;h=800;snap_window_aspect(r,360,640,0,0,w,h);assert(w==450&&h==800);
    // Width dragged past height flips to landscape with the same ratio.
    w=700;h=640;snap_window_aspect(r,360,640,0,0,w,h);assert(w==700&&h==394);
    // Height dragged past width in landscape flips back to portrait.
    w=640;h=700;snap_window_aspect(r,640,360,0,0,w,h);assert(w==394&&h==700);
    // Already locked sizes stay put.
    w=360;h=640;snap_window_aspect(r,360,640,0,0,w,h);assert(w==360&&h==640);
    // Too tall for the display: shrink, keep ratio.
    w=900;h=640;snap_window_aspect(r,360,640,1600,900,w,h);assert(w<=1600&&h<=900&&std::abs(double(w)/h-r)<0.01);
    w=600;h=1200;snap_window_aspect(r,360,640,1600,800,w,h);assert(h<=800&&std::abs(double(h)/w-r)<0.02);
    // An unchanged size is never touched, even off-ratio (tiny test windows).
    w=32;h=16;snap_window_aspect(r,32,16,0,0,w,h);assert(w==32&&h==16);
    // Unlocked ratio leaves the size alone.
    w=777;h=333;snap_window_aspect(0,360,640,0,0,w,h);assert(w==777&&h==333);
    std::cout<<"window aspect tests passed\n";
}
