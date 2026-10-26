// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <cstdint>

namespace shiraz {
// Straight packed ARGB at the boundary. Products retain precision until the
// final unpremultiplication; integer intermediates are bounded below 2^34.
// Mode numbers are Shiraz enum ordinals, not Android's native mode IDs.
inline std::uint32_t filter_color(std::uint32_t source,std::uint32_t destination,int mode) {
    using U = std::uint64_t;
    const U sa=source >> 24, da=destination >> 24;
    if (mode == 0) return 0;
    if (mode == 1) return sa ? source : 0;
    if (mode == 2) return da ? destination : 0;
    if (mode == 5) { // Common icon tint: no per-channel divisions needed.
        const U alpha=(sa*da+127)/255; return alpha ? (source & 0xffffffu) | static_cast<std::uint32_t>(alpha << 24) : 0;
    }
    U fs=0,fd=0,alphaNumerator=0,alphaDenominator=255;
    switch (mode) {
        case 3: fs=255; fd=255-sa; break;
        case 4: fs=255-da; fd=255; break;
        case 6: fd=sa; break;
        case 7: fs=255-da; break;
        case 8: fd=255-sa; break;
        case 9: fs=da; fd=255-sa; break;
        case 10: fs=255-da; fd=sa; break;
        case 11: fs=255-da; fd=255-sa; break;
        case 14: alphaNumerator=sa*da; break; // Legacy MULTIPLY = premultiplied modulation.
        case 16: alphaNumerator=std::min<U>(255,sa+da)*255; break;
        default: alphaNumerator=sa*255+da*(255-sa); break;
    }
    if (mode <= 11) alphaNumerator=sa*fs+da*fd;
    const U alpha=(alphaNumerator+alphaDenominator/2)/alphaDenominator;
    if (!alpha) return 0;
    std::uint32_t output=static_cast<std::uint32_t>(alpha << 24);
    for (unsigned shift=0;shift<=16;shift+=8) {
        const U s=(source >> shift)&255, d=(destination >> shift)&255;
        U numerator=0,denominator=1;
        if (mode <= 11) { numerator=s*sa*fs+d*da*fd; denominator=alphaNumerator; }
        else if (mode == 14) { numerator=s*d; denominator=255; }
        else if (mode == 16) { numerator=std::min<U>(65025,s*sa+d*da); denominator=alpha; }
        else {
            U blend=0;
            if (mode == 12) blend=std::min(s,d)*255;
            else if (mode == 13) blend=std::max(s,d)*255;
            else if (mode == 15) blend=s*255+d*255-s*d;
            else blend=2*d <= 255 ? 2*s*d : 65025-2*(255-s)*(255-d);
            numerator=(s*sa*(255-da)+d*da*(255-sa))*255+blend*sa*da;
            denominator=alphaNumerator*255;
        }
        output |= static_cast<std::uint32_t>(std::min<U>(255,(numerator+denominator/2)/denominator)) << shift;
    }
    return output;
}
inline std::uint32_t matrix_color(std::uint32_t color,const float* matrix) {
    const double a=color>>24;
    const double channels[]={a==0?0.0:double((color>>16)&255),a==0?0.0:double((color>>8)&255),a==0?0.0:double(color&255),a};
    unsigned out[4];
    for(int row=0;row<4;row++) {
        double value=matrix[row*5+4];for(int col=0;col<4;col++)value+=matrix[row*5+col]*channels[col];
        out[row]=static_cast<unsigned>(std::clamp(value,0.0,255.0)+.5);
    }
    return out[3]==0?0:(out[3]<<24)|(out[0]<<16)|(out[1]<<8)|out[2];
}
}
