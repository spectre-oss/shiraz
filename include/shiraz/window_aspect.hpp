#pragma once
#include <algorithm>
#include <cstdlib>
#include <stdexcept>
#include <string>

namespace shiraz {

// Locked long:short side ratio for user window resizes. "W:H" sets it,
// "free" or "0" unlocks (returns 0); empty uses the initial window shape.
inline double window_aspect(const char* value,int width,int height) {
    if(value&&*value){
        std::string text(value);
        if(text=="free"||text=="0")return 0;
        auto colon=text.find(':');
        if(colon==std::string::npos)throw std::invalid_argument("SHIRAZ_WINDOW_ASPECT must be W:H or free");
        char* end=nullptr;std::string left=text.substr(0,colon),right=text.substr(colon+1);
        double w=std::strtod(left.c_str(),&end);if(end==left.c_str()||*end)throw std::invalid_argument("SHIRAZ_WINDOW_ASPECT must be W:H or free");
        double h=std::strtod(right.c_str(),&end);if(end==right.c_str()||*end)throw std::invalid_argument("SHIRAZ_WINDOW_ASPECT must be W:H or free");
        if(!(w>0&&h>0&&w<1e4&&h<1e4))throw std::invalid_argument("SHIRAZ_WINDOW_ASPECT must be W:H or free");
        double ratio=w>h?w/h:h/w;
        if(ratio>4)throw std::invalid_argument("SHIRAZ_WINDOW_ASPECT ratio exceeds 4:1");
        return ratio;
    }
    if(width<=0||height<=0)return 0;
    double ratio=width>height?double(width)/height:double(height)/width;
    return ratio<1.01?0:ratio;
}

// Snap a requested window size to the ratio. The side that moved most since
// the last applied size drives; one side passing the other flips portrait and
// landscape. The result is shrunk to fit maxWidth x maxHeight (0 = no limit).
inline void snap_window_aspect(double ratio,int lastWidth,int lastHeight,int maxWidth,int maxHeight,int& width,int& height) {
    if(ratio<=0||(width==lastWidth&&height==lastHeight))return;
    const bool widthDrives=std::abs(width-lastWidth)>=std::abs(height-lastHeight);
    const bool landscape=width>height;
    if(widthDrives)height=int(landscape?width/ratio+0.5:width*ratio+0.5);
    else width=int(landscape?height*ratio+0.5:height/ratio+0.5);
    if(maxWidth>0&&maxHeight>0){
        double fit=std::min(1.0,std::min(double(maxWidth)/width,double(maxHeight)/height));
        if(fit<1){width=int(width*fit);height=int(height*fit);}
    }
    width=std::max(width,1);height=std::max(height,1);
}

}
