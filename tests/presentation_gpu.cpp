// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/presentation.hpp"
#include <cassert>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <unistd.h>
static shiraz::Surface pattern(int w,int h){
    shiraz::Surface out(w,h,0);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++)out.pixels[y*w+x]=0xff000000u|((x*17%256)<<16)|((y*29%256)<<8)|((x+y)*13%256);
    return out;
}
int main(){
    const std::string base=std::string(std::getenv("TMPDIR")?std::getenv("TMPDIR"):"/tmp")+"/shiraz-gpu-"+std::to_string(getpid());
    const std::string replay=base+".txt",snapshot=base+".ppm";
    setenv("SHIRAZ_INPUT_SCRIPT",replay.c_str(),1);setenv("SHIRAZ_PRESENT_SNAPSHOT",snapshot.c_str(),1);
    for(auto size:{std::pair<int,int>{128,64},{64,128},{96,96}}){
        {std::ofstream file(replay);file<<"1 resize "<<size.first<<' '<<size.second<<"\n";}
        int resized=0;
        shiraz::present(pattern(64,96),"GPU resize/readback test",4,{},{},{},{},[&](shiraz::Surface& image,int w,int h){image=pattern(w,h);++resized;});
        assert(resized==1);std::ifstream file(snapshot,std::ios::binary);std::string magic;int w,h,depth;file>>magic>>w>>h>>depth;file.get();
        assert(magic=="P6"&&w==size.first&&h==size.second&&depth==255);auto expected=pattern(w,h);
        for(uint32_t color:expected.pixels)for(int shift:{16,8,0}){int value=file.get();assert(value==int((color>>shift)&255));}
        assert(file.get()==EOF);
    }
    unlink(replay.c_str());unlink(snapshot.c_str());std::cout<<"PRESENTATION_RESIZE_PIXELS_OK\n";
}
