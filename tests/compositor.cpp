// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/compositor.hpp"
#include <cassert>
#include <climits>
// Original straight-alpha formula, independent of optimized special cases.
static uint32_t referenceOver(uint32_t src,uint32_t dst,unsigned opacity) {
    unsigned sa=((src>>24)*opacity+127)/255, remainder=((dst>>24)*(255-sa)+127)/255;
    unsigned alpha=sa+remainder;if(!alpha)return 0;
    uint32_t out=alpha<<24;
    for(unsigned shift:{0u,8u,16u})out|=((((src>>shift)&255)*sa+((dst>>shift)&255)*remainder+alpha/2)/alpha)<<shift;
    return out;
}
int main() {
    using namespace shiraz;
    uint32_t random=1234567;
    for(unsigned alpha=0;alpha<256;alpha++)for(unsigned opacity=0;opacity<256;opacity++)for(unsigned kind=0;kind<4;kind++){
        random=random*1664525u+1013904223u;uint32_t src=(alpha<<24)|(random&0xffffffu);
        random=random*1664525u+1013904223u;uint32_t dst=kind==0?random|0xff000000u:kind==1?random&0xffffffu:kind==2?0:random;
        assert(over(src,dst,opacity)==referenceOver(src,dst,opacity));
    }
    auto v = fit(360, 640, 1280, 720);
    assert(v.height == 720 && v.width == 405);
    assert(!map_pointer({0, 0}, v, 360, 640));
    auto p = map_pointer({640, 360}, v, 360, 640);
    assert(p && p->x == 180 && p->y == 320);
    assert(!map_pointer({v.x + v.width, 0}, v, 360, 640));
    assert(!map_pointer({1, 1}, {0, 0, 0, 0}, 360, 640));
    assert(over(0x80ff0000, 0xff0000ff, 255) == 0xff80007f);
    assert(over(0x00ffffff, 0, 255) == 0);
    assert(over(0x80ff0000, 0, 255) == 0x80ff0000);
    Surface red(2, 2, 0xffff0000), blue(1, 1, 0xff0000ff);
    auto image = compose(2, 2, {{&blue, 0, 0, 2}, {&red, -1, -1, 1}, {&red, INT_MAX, INT_MAX, 3}});
    assert(image.pixels[0] == 0xff0000ff);
    assert(image.pixels[1] == 0xff000000);
    auto same_z = compose(1, 1, {{&red, 0, 0, 0}, {&blue, 0, 0, 0}});
    assert(same_z.pixels[0] == 0xff0000ff);
    auto hidden = compose(1, 1, {{&red, 0, 0, 0, 255, false}, {&blue, 0, 0, 1, 0}});
    assert(hidden.pixels[0] == 0xff000000);
    bool caught = false;
    try { Surface invalid(-1, 20); } catch (const std::invalid_argument&) { caught = true; }
    assert(caught);
    red.pixels.clear(); caught = false;
    try { compose(2, 2, {{&red, 0, 0, 0}}); } catch (const std::invalid_argument&) { caught = true; }
    assert(caught);
}
