// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <cairo.h>
#include <algorithm>
#include <cmath>
#include <memory>
#include <stdexcept>
#include <vector>
#include "shiraz/compositor.hpp"
#include "shiraz/color_filter.hpp"
namespace {
void error(JNIEnv* env,const char* type,const char* message) { if (env->ExceptionCheck()) return; auto cls=env->FindClass(type); if (cls) { env->ThrowNew(cls,message); env->DeleteLocalRef(cls); } }
using Surface=std::unique_ptr<cairo_surface_t,decltype(&cairo_surface_destroy)>;
using Context=std::unique_ptr<cairo_t,decltype(&cairo_destroy)>;
void check(cairo_t* context) { auto status=cairo_status(context); if (status == CAIRO_STATUS_NO_MEMORY) throw std::bad_alloc(); if (status != CAIRO_STATUS_SUCCESS) throw std::invalid_argument(cairo_status_to_string(status)); }
void geometry(cairo_t* context,const std::vector<float>& values) {
    size_t at=0;
    while (at < values.size()) {
        float code=values[at++]; int count=code == 1 || code == 2 ? 2 : code == 3 ? 6 : code == 4 ? 0 : -1;
        if (count < 0 || static_cast<size_t>(count) > values.size()-at) throw std::invalid_argument("Invalid path command stream");
        for (int i=0;i<count;i++) if (!std::isfinite(values[at+i]) || std::abs(values[at+i]) > 10000000) throw std::invalid_argument("Path coordinate range");
        const float* p=values.data()+at;
        if (code == 1) cairo_move_to(context,p[0],p[1]);
        else if (code == 2) { if (!cairo_has_current_point(context)) cairo_move_to(context,0,0); cairo_line_to(context,p[0],p[1]); }
        else if (code == 3) { if (!cairo_has_current_point(context)) cairo_move_to(context,0,0); cairo_curve_to(context,p[0],p[1],p[2],p[3],p[4],p[5]); }
        else cairo_close_path(context);
        at+=count;
    }
    check(context);
}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePath_drawNative(JNIEnv* env,jclass,jintArray pixels,jint width,jint height,jfloatArray commands,jint fill,jint color,jint style,jfloat stroke,jint cap,jint join,jfloat miter,jboolean aa,jfloatArray matrix,jint cl,jint ct,jint cr,jint cb,jfloatArray dashArray,jfloat phase,jintArray texture,jint tw,jint th,jint tileX,jint tileY,jfloatArray inverse,jboolean filter,jfloatArray gradientPoints,jintArray gradientColors,jfloatArray gradientPositions,jint filterColor,jint filterMode,jfloatArray filterMatrix) {
    try {
        if (!pixels || !commands || !matrix) { error(env,"java/lang/NullPointerException","Path drawing arguments"); return; }
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || static_cast<long long>(width)*height > 32*1024*1024 || env->GetArrayLength(pixels) != width*height || env->GetArrayLength(commands) > 65536 || env->GetArrayLength(matrix) != 6 || cl < 0 || ct < 0 || cr < cl || cb < ct || cr > width || cb > height || fill < 0 || fill > 3 || style < 0 || style > 2 || cap < 0 || cap > 2 || join < 0 || join > 2 || !std::isfinite(stroke) || stroke < 0 || !std::isfinite(miter) || miter < 0) throw std::invalid_argument("Invalid path bounds or paint");
        if(filterMode < -2 || filterMode > 17)throw std::invalid_argument("Unsupported shader color filter mode");
        jfloat coefficients[20];
        if(filterMode==-2){
            if(!filterMatrix||env->GetArrayLength(filterMatrix)!=20)throw std::invalid_argument("Shader color matrix length");
            env->GetFloatArrayRegion(filterMatrix,0,20,coefficients);if(env->ExceptionCheck())return;
            for(float value:coefficients)if(!std::isfinite(value))throw std::invalid_argument("Non-finite shader color matrix");
        }else if(filterMatrix)throw std::invalid_argument("Unexpected shader color matrix");
        if(filterMode!=-1&&!texture)throw std::invalid_argument("Color filter requires bitmap shader");
        jfloat m[6]; env->GetFloatArrayRegion(matrix,0,6,m); if (env->ExceptionCheck()) return;
        for (float value:m) if (!std::isfinite(value) || std::abs(value) > 10000000) throw std::invalid_argument("Invalid path matrix");
        std::vector<jint> texels; jfloat inv[6]={};
        if(texture){
            if(tw<=0||th<=0||tw>8192||th>8192||static_cast<long long>(tw)*th>32*1024*1024||env->GetArrayLength(texture)!=tw*th||tileX<0||tileX>2||tileY<0||tileY>2||!inverse||env->GetArrayLength(inverse)!=6)throw std::invalid_argument("Bitmap shader bounds");
            env->GetFloatArrayRegion(inverse,0,6,inv);if(env->ExceptionCheck())return;
            for(float v:inv)if(!std::isfinite(v)||std::abs(v)>10000000)throw std::invalid_argument("Bitmap shader matrix");
            texels.resize(static_cast<size_t>(tw)*th);env->GetIntArrayRegion(texture,0,tw*th,texels.data());if(env->ExceptionCheck())return;
        }
        std::vector<jint> colors;std::vector<float> stops;jfloat points[4]={};bool radial=gradientPoints&&env->GetArrayLength(gradientPoints)==3;double axisX=0,axisY=0,axisLength=0;
        if(gradientPoints){
            if(texture||!gradientColors||!gradientPositions||(!radial&&env->GetArrayLength(gradientPoints)!=4)||env->GetArrayLength(gradientColors)<2||env->GetArrayLength(gradientColors)>256||env->GetArrayLength(gradientColors)!=env->GetArrayLength(gradientPositions)||!inverse||env->GetArrayLength(inverse)!=6||tileX<0||tileX>2)throw std::invalid_argument("Gradient bounds");
            env->GetFloatArrayRegion(gradientPoints,0,radial?3:4,points);env->GetFloatArrayRegion(inverse,0,6,inv);if(env->ExceptionCheck())return;
            for(float v:points)if(!std::isfinite(v)||std::abs(v)>10000000)throw std::invalid_argument("Gradient coordinates");
            for(float v:inv)if(!std::isfinite(v)||std::abs(v)>10000000)throw std::invalid_argument("Gradient matrix");
            if(radial&&points[2]<=0)throw std::invalid_argument("Gradient radius");
            axisX=double(points[2])-points[0];axisY=double(points[3])-points[1];axisLength=axisX*axisX+axisY*axisY;
            auto count=env->GetArrayLength(gradientColors);colors.resize(count);stops.resize(count);env->GetIntArrayRegion(gradientColors,0,count,colors.data());env->GetFloatArrayRegion(gradientPositions,0,count,stops.data());if(env->ExceptionCheck())return;
            for(int i=0;i<count;i++)if(!std::isfinite(stops[i])||stops[i]<0||stops[i]>1||(i&&stops[i]<stops[i-1]))throw std::invalid_argument("Gradient stop order/range");
        }else if(gradientColors||gradientPositions)throw std::invalid_argument("Incomplete gradient");
        auto gradient=[&](double x,double y){
            double u=inv[0]*x+inv[2]*y+inv[4]-points[0],v=inv[1]*x+inv[3]*y+inv[5]-points[1],t=radial?std::hypot(u,v)/points[2]:(axisLength==0?0:(u*axisX+v*axisY)/axisLength); // Android 8.1 Skia maps a zero axis to the gradient origin.
            if(tileX==0)t=std::clamp(t,0.0,1.0);else{t=std::fmod(t,tileX==1?1.0:2.0);if(t<0)t+=tileX==1?1:2;if(t>1)t=2-t;}
            auto next=std::upper_bound(stops.begin(),stops.end(),t);size_t index=static_cast<size_t>(next-stops.begin());
            if(index==0)return static_cast<uint32_t>(colors.front());
            if(index==colors.size())return static_cast<uint32_t>(colors.back());
            double amount=(t-stops[index-1])/(stops[index]-stops[index-1]);
            uint32_t first=static_cast<uint32_t>(colors[index-1]),last=static_cast<uint32_t>(colors[index]);
            double a=first>>24,b=last>>24,alpha=a+(b-a)*amount;
            uint32_t result=uint32_t(std::lround(alpha))<<24;
            if(alpha>0)for(int shift=0;shift<24;shift+=8){double c=((first>>shift)&255)*a,d=((last>>shift)&255)*b;result|=uint32_t(std::lround((c+(d-c)*amount)/alpha))<<shift;}
            return result;
        };
        auto tile=[](double coordinate,int size,int mode){
            if(mode==0)return static_cast<int>(std::clamp(coordinate,0.0,double(size-1)));
            double period=mode==1?size:2.0*size;double value=std::fmod(coordinate,period);if(value<0)value+=period;
            int index=static_cast<int>(value);return index<size?index:2*size-1-index;
        };
        auto texel=[&](double x,double y){return static_cast<uint32_t>(texels[static_cast<size_t>(tile(y,th,tileY))*tw+tile(x,tw,tileX)]);};
        auto sample=[&](double x,double y){
            double u=inv[0]*x+inv[2]*y+inv[4],v=inv[1]*x+inv[3]*y+inv[5];
            if(!filter)return texel(std::floor(u),std::floor(v));
            u-=.5;v-=.5;double l=std::floor(u),t=std::floor(v),fx=u-l,fy=v-t;
            double a=0,r=0,g=0,b=0;
            for(int i=0;i<4;i++){uint32_t c=texel(l+(i&1),t+(i>>1));double w=((i&1)?fx:1-fx)*((i&2)?fy:1-fy),alpha=(c>>24)*w;a+=alpha;r+=((c>>16)&255)*alpha;g+=((c>>8)&255)*alpha;b+=(c&255)*alpha;}
            if(a<=0)return uint32_t(0);
            return (uint32_t(std::lround(a))<<24)|(uint32_t(std::lround(r/a))<<16)|(uint32_t(std::lround(g/a))<<8)|uint32_t(std::lround(b/a));
        };
        std::vector<float> values(static_cast<size_t>(env->GetArrayLength(commands))); if (!values.empty()) env->GetFloatArrayRegion(commands,0,static_cast<jsize>(values.size()),values.data()); if (env->ExceptionCheck()) return;
        std::vector<double> dashes;
        if (!std::isfinite(phase) || std::abs(phase)>10000000) throw std::invalid_argument("Invalid dash phase");
        if (dashArray) {
            auto count=env->GetArrayLength(dashArray);
            if(count<2 || count>256 || count%2) throw std::invalid_argument("Invalid dash count");
            std::vector<float> input(static_cast<size_t>(count));env->GetFloatArrayRegion(dashArray,0,count,input.data());if(env->ExceptionCheck())return;
            double total=0;for(float value:input){if(!std::isfinite(value)||value<0)throw std::invalid_argument("Invalid dash interval");total+=value;dashes.push_back(value);}
            if(total==0)throw std::invalid_argument("Empty dash period");
        }
        Surface scratch(cairo_image_surface_create(CAIRO_FORMAT_A8,1,1),cairo_surface_destroy); Context bounds(cairo_create(scratch.get()),cairo_destroy);
        cairo_matrix_t transform; cairo_matrix_init(&transform,m[0],m[1],m[2],m[3],m[4],m[5]);
        // Validate geometry even for a collapsed matrix; zero scale has no coverage.
        geometry(bounds.get(),values);
        if (!dashes.empty() && style!=0) {
            // A control polygon bounds curve length. Reject excessive dash expansion before Cairo stroking.
            double length=0,x=0,y=0,mx=0,my=0;unsigned contours=0;
            for(size_t at=0;at<values.size();) {
                int code=static_cast<int>(values[at++]);
                if(code==1){x=mx=values[at++];y=my=values[at++];contours++;}
                else if(code==4){length+=std::hypot(x-mx,y-my);x=mx;y=my;}
                else {int points=code==3?3:1;for(int i=0;i<points;i++){double nx=values[at++],ny=values[at++];length+=std::hypot(nx-x,ny-y);x=nx;y=ny;}}
            }
            double period=0;for(double value:dashes)period+=value;
            if((length/period+contours+1)*dashes.size()>1000000)throw std::invalid_argument("Dash expansion work budget");
        }
        if ((static_cast<unsigned>(color) >> 24) == 0) return;
        if (double(m[0])*m[3]-double(m[1])*m[2] == 0) return;
        cairo_set_matrix(bounds.get(),&transform);
        cairo_new_path(bounds.get()); geometry(bounds.get(),values);
        auto setup=[&](cairo_t* context) {
            cairo_set_fill_rule(context,(fill&1) ? CAIRO_FILL_RULE_EVEN_ODD : CAIRO_FILL_RULE_WINDING);
            cairo_set_line_cap(context,cap == 1 ? CAIRO_LINE_CAP_ROUND : cap == 2 ? CAIRO_LINE_CAP_SQUARE : CAIRO_LINE_CAP_BUTT);
            cairo_set_line_join(context,join == 1 ? CAIRO_LINE_JOIN_ROUND : join == 2 ? CAIRO_LINE_JOIN_BEVEL : CAIRO_LINE_JOIN_MITER);
            cairo_set_miter_limit(context,miter);
            if (!dashes.empty() && style!=0) cairo_set_dash(context,dashes.data(),static_cast<int>(dashes.size()),phase);
            double lineWidth=stroke;
            if (lineWidth == 0) {
                double sx=std::hypot(m[0],m[1]),sy=std::hypot(m[2],m[3]);
                if (style != 0 && (std::abs(sx-sy) > 1e-6*std::max(sx,sy) || std::abs(double(m[0])*m[2]+double(m[1])*m[3]) > 1e-6*sx*sy)) throw std::invalid_argument("Anisotropic hairline path is not implemented");
                lineWidth=sx == 0 ? 1 : 1/sx;
            }
            cairo_set_line_width(context,lineWidth); cairo_set_antialias(context,aa ? CAIRO_ANTIALIAS_GRAY : CAIRO_ANTIALIAS_NONE); check(context);
        };
        setup(bounds.get());
        int x0=cl,y0=ct,x1=cr,y1=cb;
        if (!(fill >= 2 && style != 1)) {
            double l=0,t=0,r=0,b=0;
            if (style == 1) cairo_stroke_extents(bounds.get(),&l,&t,&r,&b); else cairo_fill_extents(bounds.get(),&l,&t,&r,&b);
            if (style == 2) { double sl,st,sr,sb; cairo_stroke_extents(bounds.get(),&sl,&st,&sr,&sb); l=std::min(l,sl); t=std::min(t,st); r=std::max(r,sr); b=std::max(b,sb); }
            double dl=1e100,dt=1e100,dr=-1e100,db=-1e100;
            for (int i=0;i<4;i++) { double x=i&1 ? r : l,y=i&2 ? b : t; cairo_user_to_device(bounds.get(),&x,&y); dl=std::min(dl,x); dt=std::min(dt,y); dr=std::max(dr,x); db=std::max(db,y); }
            auto edge=[](double value,int limit) { if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite path extent"); return static_cast<int>(std::clamp(value,0.0,static_cast<double>(limit))); };
            x0=std::max(cl,edge(std::floor(dl)-1,width)); y0=std::max(ct,edge(std::floor(dt)-1,height)); x1=std::min(cr,edge(std::ceil(dr)+1,width)); y1=std::min(cb,edge(std::ceil(db)+1,height));
        }
        if (x0 >= x1 || y0 >= y1) return;
        int rw=x1-x0,rh=y1-y0,stride=cairo_format_stride_for_width(CAIRO_FORMAT_A8,rw); if (stride < 0) throw std::invalid_argument("Path mask stride");
        std::vector<unsigned char> mask(static_cast<size_t>(stride)*rh,fill >= 2 && style != 1 ? 255 : 0);
        Surface surface(cairo_image_surface_create_for_data(mask.data(),CAIRO_FORMAT_A8,rw,rh,stride),cairo_surface_destroy); Context draw(cairo_create(surface.get()),cairo_destroy);
        transform.x0-=x0; transform.y0-=y0; cairo_set_matrix(draw.get(),&transform); setup(draw.get()); geometry(draw.get(),values);
        if (style != 1) { cairo_set_operator(draw.get(),fill >= 2 ? CAIRO_OPERATOR_CLEAR : CAIRO_OPERATOR_OVER); cairo_set_source_rgba(draw.get(),1,1,1,1); cairo_fill_preserve(draw.get()); }
        if (style != 0) { cairo_set_operator(draw.get(),CAIRO_OPERATOR_OVER); cairo_set_source_rgba(draw.get(),1,1,1,1); cairo_stroke(draw.get()); }
        check(draw.get()); cairo_surface_flush(surface.get());
        std::vector<jint> row(static_cast<size_t>(rw));
        // Cairo has finished and all allocations/validation precede Java pixel writes.
        for (int y=0;y<rh;y++) {
            env->GetIntArrayRegion(pixels,(y+y0)*width+x0,rw,row.data()); if (env->ExceptionCheck()) return;
            for (int x=0;x<rw;x++) if (mask[static_cast<size_t>(y)*stride+x]) {
                uint32_t source=static_cast<uint32_t>(color);unsigned coverage=mask[static_cast<size_t>(y)*stride+x];
                if(texture||gradientPoints){source=texture?sample(x+x0+.5,y+y0+.5):gradient(x+x0+.5,y+y0+.5);if(filterMode!=-1){source=(source&0xffffffu)|((((source>>24)*(static_cast<uint32_t>(color)>>24)+127)/255)<<24);source=filterMode==-2?shiraz::matrix_color(source,coefficients):shiraz::filter_color(static_cast<uint32_t>(filterColor),source,filterMode);}else coverage=(coverage*(static_cast<uint32_t>(color)>>24)+127)/255;}
                row[x]=static_cast<jint>(shiraz::over(source,static_cast<uint32_t>(row[x]),coverage));
            }
            env->SetIntArrayRegion(pixels,(y+y0)*width+x0,rw,row.data()); if (env->ExceptionCheck()) return;
        }
    } catch (const std::bad_alloc&) { error(env,"java/lang/OutOfMemoryError","Cannot rasterize path"); }
      catch (const std::exception& issue) { error(env,"java/lang/IllegalArgumentException",issue.what()); }
}
