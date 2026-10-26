// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/compositor.hpp"
#include <cmath>
#include <cstdint>
#include <stdexcept>
namespace shiraz {
// Native output fills the window even when fractional scaling rounds each
// axis differently. Fixed-size preview surfaces instead retain letterboxing.
inline Viewport input_viewport(int pixelWidth,int pixelHeight,int logicalWidth,int logicalHeight,bool nativePixels) {
    return nativePixels?Viewport{0,0,double(logicalWidth),double(logicalHeight)}:fit(pixelWidth,pixelHeight,logicalWidth,logicalHeight);
}
// SDL input/window coordinates are logical; Android surfaces use physical pixels.
struct DisplayGeometry {
    int logicalWidth,logicalHeight,pixelWidth,pixelHeight,densityDpi;
    static DisplayGeometry from(int lw,int lh,int pw,int ph) {
        if(lw<1||lh<1||pw<1||ph<1||lw>8192||lh>8192||pw>8192||ph>8192||uint64_t(pw)*ph>16*1024*1024)
            throw std::invalid_argument("Invalid host display geometry");
        const int density=static_cast<int>(std::lround(160.0*pw/lw));
        if(density<1||density>1280)throw std::invalid_argument("Unsupported host display scale");
        return {lw,lh,pw,ph,density};
    }
    bool operator==(const DisplayGeometry& other) const {
        return logicalWidth==other.logicalWidth&&logicalHeight==other.logicalHeight&&pixelWidth==other.pixelWidth&&pixelHeight==other.pixelHeight&&densityDpi==other.densityDpi;
    }
    bool operator!=(const DisplayGeometry& other) const {return !(*this==other);}
};
}
