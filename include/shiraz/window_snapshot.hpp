// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/native_window.h"
#include "shiraz/compositor.hpp"
namespace shiraz {
// Read the revision before copying: a concurrent post may cause an extra refresh,
// but can never mark unseen pixels as already copied.
inline bool refresh_window_snapshot(ANativeWindow* window,Surface& image,uint64_t& revision,
                                    uint64_t (*read_revision)(ANativeWindow*)){
    const uint64_t current=read_revision(window);
    if(!current)throw std::invalid_argument("Missing native window");
    if(current==revision)return false;
    const int width=ANativeWindow_getWidth(window),height=ANativeWindow_getHeight(window);
    if(image.width!=width||image.height!=height)image=Surface(width,height);
    if(shiraz_window_copy_argb(window,image.pixels.data(),image.pixels.size()))throw std::runtime_error("Cannot copy native surface");
    revision=current;return true;
}
inline bool refresh_window_snapshot(ANativeWindow* window,Surface& image,uint64_t& revision){
    return refresh_window_snapshot(window,image,revision,shiraz_window_revision);
}
}
