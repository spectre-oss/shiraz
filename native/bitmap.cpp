// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include "shiraz/color_filter.hpp"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <new>
#include <stdexcept>
#include <vector>

namespace {
void error(JNIEnv* env, const char* type, const char* text) {
    if (env->ExceptionCheck()) return;
    auto cls = env->FindClass(type); if (cls) { env->ThrowNew(cls, text); env->DeleteLocalRef(cls); }
}
void readMatrix(JNIEnv* env,jfloatArray input,jfloat* out) {
    if(!input || env->GetArrayLength(input)!=20)throw std::invalid_argument("Color matrix requires 20 coefficients");
    env->GetFloatArrayRegion(input,0,20,out);if(env->ExceptionCheck())return;
    for(int i=0;i<20;i++)if(!std::isfinite(out[i])||std::abs(out[i])>10000000)throw std::invalid_argument("Color matrix coefficient bound");
}

std::uint32_t over(std::uint32_t src, std::uint32_t dst, int opacity) {
    const unsigned sa = ((src >> 24) * static_cast<unsigned>(opacity) + 127) / 255;
    if (!sa) return (dst >> 24) ? dst : 0;
    if (sa == 255) return src;
    // With no destination coverage, unpremultiplication recovers source RGB.
    if (!(dst >> 24)) return (src & 0x00ffffffu) | (sa << 24);
    if ((dst >> 24) == 255) {
        // The output remains opaque. Keep the divisor constant so the hot
        // compositing path does not need three integer divisions per pixel.
        unsigned output = 0xff000000u;
        for (unsigned shift = 0; shift <= 16; shift += 8)
            output |= ((((src >> shift) & 255) * sa + ((dst >> shift) & 255) * (255 - sa) + 127) / 255) << shift;
        return output;
    }
    const unsigned remainder = ((dst >> 24) * (255 - sa) + 127) / 255, alpha = sa + remainder;
    if (!alpha) return 0;
    unsigned output = alpha << 24;
    for (unsigned shift = 0; shift <= 16; shift += 8) {
        output |= ((((src >> shift) & 255) * sa + ((dst >> shift) & 255) * remainder + alpha / 2) / alpha) << shift;
    }
    return output;
}
struct Horizontal { int first, second; unsigned fraction; };
std::uint32_t interpolate(const std::uint32_t* colors, unsigned fx, unsigned fy) {
    // Eight fractional bits per axis, with integer premultiplied interpolation.
    // The four weights sum to 65536. Even 255*255*65536 plus rounding
    // fits uint32_t; no intermediate signed arithmetic or float-to-int UB.
    const unsigned weights[] = {(256 - fx) * (256 - fy), fx * (256 - fy), (256 - fx) * fy, fx * fy};
    const bool opaque = (colors[0] & colors[1] & colors[2] & colors[3] & 0xff000000u) == 0xff000000u;
    if (opaque) {
        // Red and blue occupy independent 32-bit lanes: their weighted sums
        // stay below 2^24, leaving room for rounding without lane carry.
        std::uint64_t redBlue = 0x0000800000008000ull; unsigned green = 32768;
        for (int i = 0; i < 4; ++i) {
            const auto lanes = (static_cast<std::uint64_t>(colors[i] & 0x00ff0000u) << 16) | (colors[i] & 255u);
            redBlue += lanes * weights[i]; green += weights[i] * ((colors[i] >> 8) & 255);
        }
        return 0xff000000u | static_cast<unsigned>((redBlue >> 32) & 0x00ff0000u) | ((green >> 8) & 0x0000ff00u) | static_cast<unsigned>((redBlue >> 16) & 255u);
    }
    unsigned alphaSum = 0;
    for (int i = 0; i < 4; ++i) alphaSum += weights[i] * (colors[i] >> 24);
    const unsigned alpha = (alphaSum + 32768) >> 16;
    if (!alpha) return 0;
    unsigned output = alpha << 24;
    for (unsigned shift = 0; shift <= 16; shift += 8) {
        unsigned sum = 0;
        for (int i = 0; i < 4; ++i) sum += weights[i] * ((colors[i] >> shift) & 255) * (colors[i] >> 24);
        output |= ((sum + alphaSum / 2) / alphaSum) << shift;
    }
    return output;
}

}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeBitmap_drawEffects(JNIEnv* env, jclass, jintArray destination, jint width, jint height, jintArray source, jint sw, jint sh,
        jint left, jint top, jint right, jint bottom, jfloat dl, jfloat dt, jfloat dr, jfloat db, jboolean filter, jint opacity, jint cl, jint ct, jint cr, jint cb, jint tint, jint mode, jfloatArray matrix) {
    try {
        if (!destination || !source) { error(env, "java/lang/NullPointerException", "Null bitmap pixel array"); return; }
        auto valid = [](int w, int h) { return w > 0 && h > 0 && w <= 8192 && h <= 8192 && static_cast<std::int64_t>(w) * h <= 32 * 1024 * 1024; };
        if (!valid(width, height) || !valid(sw, sh) || env->GetArrayLength(destination) != width * height || env->GetArrayLength(source) != sw * sh)
            throw std::invalid_argument("Invalid bitmap dimensions or array lengths");
        if (!std::isfinite(dl) || !std::isfinite(dt) || !std::isfinite(dr) || !std::isfinite(db) || opacity < 0 || opacity > 255)
            throw std::invalid_argument("Invalid bitmap destination or alpha");
        if (left < 0 || top < 0 || right > sw || bottom > sh) throw std::invalid_argument("Bitmap source rectangle is outside its pixels");
        if (cl < 0 || ct < 0 || cr < cl || cb < ct || cr > width || cb > height) throw std::invalid_argument("Invalid bitmap clip");
        if (mode < -2 || mode > 17) throw std::invalid_argument("Unsupported color filter mode");
        jfloat coefficients[20];if(mode==-2){readMatrix(env,matrix,coefficients);if(env->ExceptionCheck())return;}else if(matrix)throw std::invalid_argument("Unexpected matrix for color filter mode");
        if (left >= right || top >= bottom || dl >= dr || dt >= db || (opacity == 0 && mode == -1)) return;
        auto edge = [](double value, int limit) { return static_cast<int>(std::clamp(std::ceil(value - .5), 0.0, static_cast<double>(limit))); };
        const int x0 = std::max(cl,edge(dl, width)), x1 = std::min(cr,edge(dr, width)), y0 = std::max(ct,edge(dt, height)), y1 = std::min(cb,edge(db, height));
        if (x0 >= x1 || y0 >= y1) return;
        const double scaleX = (right - left) / (static_cast<double>(dr) - dl), scaleY = (bottom - top) / (static_cast<double>(db) - dt);
        const bool alias = env->IsSameObject(destination, source);
        const bool directCopy = scaleX == 1 && scaleY == 1 && dl == std::floor(dl) && dt == std::floor(dt) && opacity == 255 && mode == -1;
        // Stage only sampled scanlines. Whole-array JNI critical pins block GC and
        // CheckJNI can copy the entire map bitmap even for a tiny draw.
        std::vector<Horizontal> horizontal(static_cast<std::size_t>(x1 - x0));
        for (int x = x0; x < x1; ++x) {
            const double position = left + (x + .5 - dl) * scaleX - (filter ? .5 : 0);
            const int base = static_cast<int>(std::floor(position));
            horizontal[x - x0] = {std::clamp(base, left, right - 1), std::clamp(base + 1, left, right - 1),
                static_cast<unsigned>(std::clamp(std::floor((position - base) * 256 + .5), 0.0, 256.0))};
        }
        // Positive scaling makes sampled columns monotonic. Include the second
        // bilinear texel, but never fetch invisible columns outside this range.
        const int sampleLeft = horizontal.front().first;
        const int sampleRight = (filter ? horizontal.back().second : horizontal.back().first) + 1;
        const int stride = sampleRight - sampleLeft;
        if (sampleLeft != 0) for (auto& sample : horizontal) { sample.first -= sampleLeft; sample.second -= sampleLeft; }
        std::vector<jint> snapshot(alias ? static_cast<std::size_t>(stride) * (bottom - top) : 0);
        if (alias) for (int y = top; y < bottom; ++y) {
            env->GetIntArrayRegion(source, y * sw + sampleLeft, stride, snapshot.data() + static_cast<std::size_t>(y - top) * stride);
            if (env->ExceptionCheck()) return;
        }
        std::vector<jint> destinationRow(x1 - x0), firstRow(alias ? 0 : stride), secondRow(alias ? 0 : stride);
        int firstY = -1, secondY = -1;
        for (int y = y0; y < y1; ++y) {
            const double position = top + (y + .5 - dt) * scaleY - (filter ? .5 : 0);
            const int base = static_cast<int>(std::floor(position));
            const int row0 = std::clamp(base, top, bottom - 1), row1 = std::clamp(base + 1, top, bottom - 1);
            const jint *samples0, *samples1;
            if (alias) {
                samples0 = snapshot.data() + static_cast<std::size_t>(row0 - top) * stride;
                samples1 = snapshot.data() + static_cast<std::size_t>(row1 - top) * stride;
            } else {
                if (firstY != row0 && secondY == row0) { firstRow.swap(secondRow); std::swap(firstY, secondY); }
                if (firstY != row0) {
                    env->GetIntArrayRegion(source, row0 * sw + sampleLeft, stride, firstRow.data());
                    if (env->ExceptionCheck()) return;
                    firstY = row0;
                }
                samples0 = firstRow.data(); samples1 = samples0;
                if (filter && row1 != row0) {
                    if (secondY != row1) {
                        env->GetIntArrayRegion(source, row1 * sw + sampleLeft, stride, secondRow.data());
                        if (env->ExceptionCheck()) return;
                        secondY = row1;
                    }
                    samples1 = secondRow.data();
                }
            }
            // An opaque, unscaled scanline replaces the destination.
            // Check current samples, never cached Bitmap opacity metadata: apps
            // may mutate pixels between draws. Alias reads use the snapshot above.
            if (directCopy) {
                const jint* begin = samples0 + horizontal[0].first;
                const jint* end = begin + (x1 - x0);
                if (std::all_of(begin, end, [](jint pixel) { return (static_cast<std::uint32_t>(pixel) >> 24) == 255; })) {
                    env->SetIntArrayRegion(destination, y * width + x0, x1 - x0, begin);
                    if (env->ExceptionCheck()) return;
                    continue;
                }
            }
            env->GetIntArrayRegion(destination, y * width + x0, x1 - x0, destinationRow.data());
            if (env->ExceptionCheck()) return;
            const unsigned fy = static_cast<unsigned>(std::clamp(std::floor((position - base) * 256 + .5), 0.0, 256.0));
            for (int x = x0; x < x1; ++x) {
                const auto& sample = horizontal[x - x0];
                std::uint32_t color = static_cast<std::uint32_t>(samples0[sample.first]);
                if (filter && sample.fraction == 0 && fy == 0) {
                    // An exact texel needs no bilinear interpolation. Preserve
                    // premultiplied sampling's canonical transparent color before filters.
                    if ((color >> 24) == 0) color = 0;
                } else if (filter) {
                    const std::uint32_t colors[] = {color, static_cast<std::uint32_t>(samples0[sample.second]),
                        static_cast<std::uint32_t>(samples1[sample.first]), static_cast<std::uint32_t>(samples1[sample.second])};
                    if (mode == 5) {
                        // SRC_IN discards input RGB. Keep the sampler's identical Q8
                        // alpha rounding without calculating unused color channels.
                        if ((colors[0] & colors[1] & colors[2] & colors[3] & 0xff000000u) == 0xff000000u) color=0xff000000u;
                        else {
                            const unsigned fx=sample.fraction;
                            const unsigned sum=(colors[0] >> 24)*(256-fx)*(256-fy)+(colors[1] >> 24)*fx*(256-fy)+
                                (colors[2] >> 24)*(256-fx)*fy+(colors[3] >> 24)*fx*fy;
                            color=((sum+32768) >> 16) << 24;
                        }
                    } else color = interpolate(colors, sample.fraction, fy);
                }
                const int index = x - x0;
                if (mode != -1) {
                    color=(color & 0xffffffu) | ((((color >> 24)*static_cast<unsigned>(opacity)+127)/255) << 24);
                    color=mode==-2?shiraz::matrix_color(color,coefficients):shiraz::filter_color(static_cast<std::uint32_t>(tint),color,mode);
                }
                destinationRow[index] = static_cast<jint>(over(color, static_cast<std::uint32_t>(destinationRow[index]), mode != -1 ? 255 : opacity));
            }
            env->SetIntArrayRegion(destination, y * width + x0, x1 - x0, destinationRow.data());
            if (env->ExceptionCheck()) return;
        }
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate overlapping bitmap snapshot"); }
    catch (const std::exception& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
}

extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeBitmap_filterColor(JNIEnv* env,jclass,jint source,jint destination,jint mode) {
    if (mode < 0 || mode > 17) { error(env,"java/lang/IllegalArgumentException","Unsupported color filter mode"); return 0; }
    return static_cast<jint>(shiraz::filter_color(static_cast<std::uint32_t>(source),static_cast<std::uint32_t>(destination),mode));
}

extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeBitmap_filterColorMatrix(JNIEnv* env,jclass,jint color,jfloatArray matrix) {
    try {jfloat values[20];readMatrix(env,matrix,values);if(env->ExceptionCheck())return 0;return static_cast<jint>(shiraz::matrix_color(static_cast<std::uint32_t>(color),values));}
    catch(const std::exception& issue){error(env,"java/lang/IllegalArgumentException",issue.what());return 0;}
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeBitmap_composite(JNIEnv* env,jclass,jintArray destination,jintArray source,jintArray coverage,jint mode) {
    if(!destination||!source||!coverage||mode<0||mode>17){error(env,"java/lang/IllegalArgumentException","Transfer arrays/mode");return;}
    const auto count=env->GetArrayLength(destination);
    if(count>4*1024*1024||env->GetArrayLength(source)!=count||env->GetArrayLength(coverage)!=count){error(env,"java/lang/IllegalArgumentException","Transfer array bounds");return;}
    auto* src=env->GetIntArrayElements(source,nullptr);if(!src)return;
    auto* mask=env->GetIntArrayElements(coverage,nullptr);if(!mask){env->ReleaseIntArrayElements(source,src,JNI_ABORT);return;}
    auto* dst=env->GetIntArrayElements(destination,nullptr);
    if(dst){for(int i=0;i<count;i++){
        const unsigned cov=static_cast<unsigned>(mask[i])>>24;if(!cov)continue;
        auto s=static_cast<std::uint32_t>(src[i]);const auto d=static_cast<std::uint32_t>(dst[i]);
        // Recover source alpha before geometry coverage, then interpolate the
        // transfer result with the untouched destination in premultiplied space.
        s=(s&0xffffff)|(std::min(255u,((s>>24)*255+cov/2)/cov)<<24);
        const auto out=shiraz::filter_color(s,d,mode);
        const unsigned oa=out>>24,da=d>>24,aw=oa*cov+da*(255-cov),alpha=(aw+127)/255;
        std::uint32_t result=alpha<<24;
        if(alpha&&aw)for(int shift=0;shift<=16;shift+=8){const unsigned channel=(((out>>shift)&255)*oa*cov+((d>>shift)&255)*da*(255-cov)+aw/2)/aw;result|=channel<<shift;}
        dst[i]=static_cast<jint>(result);
    }env->ReleaseIntArrayElements(destination,dst,0);}
    env->ReleaseIntArrayElements(coverage,mask,JNI_ABORT);env->ReleaseIntArrayElements(source,src,JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeBitmap_shadow(JNIEnv* env,jclass,jintArray destination,jint width,jint height,jintArray mask,jint mw,jint mh,jint pad,jint rx,jint ry,jint color,jint mode,jint left,jint top,jint right,jint bottom) {
    try {
        if(!destination||!mask||width<=0||height<=0||mw<=0||mh<=0||mw>8192||mh>8192||int64_t(mw)*mh>4*1024*1024||pad<0||int64_t(right)-left+2LL*pad!=mw||int64_t(bottom)-top+2LL*pad!=mh||rx<0||ry<0||rx>512||ry>512||mode<0||mode>17||left<0||top<0||right<left||bottom<top||right>width||bottom>height||env->GetArrayLength(destination)!=int64_t(width)*height||env->GetArrayLength(mask)!=int64_t(mw)*mh)throw std::invalid_argument("Shadow buffers/geometry");
        const int count=mw*mh;std::vector<jint> pixels(count);env->GetIntArrayRegion(mask,0,count,pixels.data());if(env->ExceptionCheck())return;
        std::vector<float> a(count),b(count);for(int i=0;i<count;i++)a[i]=static_cast<unsigned>(pixels[i])>>24;
        auto blur=[&](bool horizontal,int radius){if(radius==0)return;const int lines=horizontal?mh:mw,length=horizontal?mw:mh,stride=horizontal?1:mw;const float divisor=2*radius+1;
            for(int line=0;line<lines;line++){const int base=horizontal?line*mw:line;double sum=0;for(int j=0;j<=radius&&j<length;j++)sum+=a[base+j*stride];for(int j=0;j<length;j++){b[base+j*stride]=static_cast<float>(sum/divisor);if(j-radius>=0)sum-=a[base+(j-radius)*stride];if(j+radius+1<length)sum+=a[base+(j+radius+1)*stride];}}
            a.swap(b);
        };
        for(int pass=0;pass<3;pass++){blur(true,rx);blur(false,ry);}
        pixels.resize(right-left);
        for(int y=top;y<bottom;y++){
          env->GetIntArrayRegion(destination,y*width+left,right-left,pixels.data());if(env->ExceptionCheck())return;
          for(int x=left;x<right;x++){
            const unsigned cov=static_cast<unsigned>(std::clamp(std::lround(a[(y-top+pad)*mw+x-left+pad]),0L,255L));if(!cov)continue;
            const auto d=static_cast<std::uint32_t>(pixels[x-left]),out=shiraz::filter_color(static_cast<std::uint32_t>(color),d,mode);
            const unsigned oa=out>>24,da=d>>24,aw=oa*cov+da*(255-cov),alpha=(aw+127)/255;std::uint32_t result=alpha<<24;
            if(alpha&&aw)for(int shift=0;shift<=16;shift+=8)result|=((((out>>shift)&255)*oa*cov+((d>>shift)&255)*da*(255-cov)+aw/2)/aw)<<shift;
            pixels[x-left]=static_cast<jint>(result);
          }
          env->SetIntArrayRegion(destination,y*width+left,right-left,pixels.data());if(env->ExceptionCheck())return;
        }
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Shadow mask allocation");}catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeBitmap_fillRect(JNIEnv* env,jclass,jintArray pixels,jint width,jint height,jdouble left,jdouble top,jdouble right,jdouble bottom,jint color,jboolean aa,jint cl,jint ct,jint cr,jint cb) {
    try {
        if(!pixels){error(env,"java/lang/NullPointerException","Fill pixels");return;}
        if(width<=0||height<=0||width>8192||height>8192||static_cast<int64_t>(width)*height>32*1024*1024||env->GetArrayLength(pixels)!=width*height||cl<0||ct<0||cr<cl||cb<ct||cr>width||cb>height||!std::isfinite(left)||!std::isfinite(top)||!std::isfinite(right)||!std::isfinite(bottom))throw std::invalid_argument("Fill bounds");
        if(left>=right||top>=bottom)return;
        auto edge=[](double value,int limit){return static_cast<int>(std::clamp(value,0.0,double(limit)));};
        int x0=std::max(cl,edge(aa?std::floor(left):std::ceil(left-.5),width));
        int y0=std::max(ct,edge(aa?std::floor(top):std::ceil(top-.5),height));
        int x1=std::min(cr,edge(aa?std::ceil(right):std::ceil(right-.5),width));
        int y1=std::min(cb,edge(aa?std::ceil(bottom):std::ceil(bottom-.5),height));
        if(x0>=x1||y0>=y1)return;
        std::vector<jint> row(static_cast<size_t>(x1-x0));
        // Preserve the existing 4x4 coverage rule; only boundary pixels need sampling.
        for(int y=y0;y<y1;y++){
            if ((static_cast<uint32_t>(color) >> 24) == 255 &&
                (!aa || (x0 >= left && x1 <= right && y >= top && y + 1 <= bottom))) {
                // Fully covered opaque rows replace the destination outright.
                std::fill(row.begin(), row.end(), color);
                env->SetIntArrayRegion(pixels,y*width+x0,x1-x0,row.data());if(env->ExceptionCheck())return;
                continue;
            }
            env->GetIntArrayRegion(pixels,y*width+x0,x1-x0,row.data());if(env->ExceptionCheck())return;
            for(int x=x0;x<x1;x++){
                int coverage=16;
                if(aa&&(x<left||x+1>right||y<top||y+1>bottom)){
                    coverage=0;for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++){double px=x+(sx+.5)/4,py=y+(sy+.5)/4;if(px>=left&&px<right&&py>=top&&py<bottom)coverage++;}
                }
                if(coverage){uint32_t source=static_cast<uint32_t>(color);source=(source&0xffffffu)|(((source>>24)*coverage+8)/16<<24);row[x-x0]=static_cast<jint>(over(source,static_cast<uint32_t>(row[x-x0]),255));}
            }
            env->SetIntArrayRegion(pixels,y*width+x0,x1-x0,row.data());if(env->ExceptionCheck())return;
        }
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Fill row allocation");}
     catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}
}

// Interpolate the completed draw with the original destination in premultiplied space.
// This also gives CLEAR/SRC transfer modes correct partial clip coverage.
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeBitmap_applyClip(JNIEnv* env,jclass,jintArray destination,jint width,jint height,jintArray rendered,jint rw,jint rh,jint left,jint top,jbyteArray mask,jint mw,jint mh,jint ml,jint mt) {
    try {
        if(!destination||!rendered||!mask){error(env,"java/lang/NullPointerException","Clip arrays");return;}
        auto valid=[](int w,int h){return w>0&&h>0&&w<=8192&&h<=8192&&int64_t(w)*h<=32*1024*1024;};
        if(!valid(width,height)||!valid(rw,rh)||!valid(mw,mh)||env->GetArrayLength(destination)!=width*height||env->GetArrayLength(rendered)!=rw*rh||env->GetArrayLength(mask)!=mw*mh||left<0||top<0||left>width-rw||top>height-rh||ml<0||mt<0||left<ml||top<mt||int64_t(left)+rw>int64_t(ml)+mw||int64_t(top)+rh>int64_t(mt)+mh)throw std::invalid_argument("Clip bounds");
        std::vector<jint> before(rw),after(rw);std::vector<jbyte> coverage(rw);
        for(int y=0;y<rh;y++){
            env->GetIntArrayRegion(destination,(top+y)*width+left,rw,before.data());
            env->GetIntArrayRegion(rendered,y*rw,rw,after.data());
            env->GetByteArrayRegion(mask,(top+y-mt)*mw+left-ml,rw,coverage.data());if(env->ExceptionCheck())return;
            for(int x=0;x<rw;x++){
                unsigned c=static_cast<unsigned char>(coverage[x]);if(!c)continue;if(c==255){before[x]=after[x];continue;}
                uint32_t a=static_cast<uint32_t>(after[x]),b=static_cast<uint32_t>(before[x]);
                unsigned wa=(a>>24)*c,wb=(b>>24)*(255-c),sum=wa+wb;
                if(!sum){before[x]=0;continue;}uint32_t color=((sum+127)/255)<<24;
                for(unsigned shift=0;shift<24;shift+=8)color|=((wa*((a>>shift)&255)+wb*((b>>shift)&255)+sum/2)/sum)<<shift;
                before[x]=static_cast<jint>(color);
            }
            env->SetIntArrayRegion(destination,(top+y)*width+left,rw,before.data());if(env->ExceptionCheck())return;
        }
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Clip scratch");}
     catch(const std::exception& issue){error(env,"java/lang/IllegalArgumentException",issue.what());}
}
