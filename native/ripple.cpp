/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// SPDX-License-Identifier: Apache-2.0
// CPU adaptation by Shiraz; see LICENSES/RIPPLE-NOTICE.txt.
#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
namespace {
float clamp(float x){return std::clamp(x,0.f,1.f);}
float fract(float x){return x-std::floor(x);}
float soft(float distance,float radius){if(radius<=0)return 0;float x=clamp(distance/radius-.5f);return 1-x*x*(3-2*x);}
struct Grid {float x,y,co,si,diameter;};
float grid(float x,float y,const Grid& g){float dx=g.x-x,dy=g.y-y;float u=g.co*dx+g.si*dy+g.x,v=-g.si*dx+g.co*dy+g.y;u=(u-g.diameter*std::floor(u/g.diameter))/.8f;v=(v-g.diameter*std::floor(v/g.diameter))/.8f;float center=g.diameter/.8f*.5f,r=.65f*center,half=r*25;float d=std::sqrt((u-center)*(u-center)+(v-center)*(v-center))/r;float t=clamp((d-(1-half))/(2*half));return 1-t*t*(3-2*t);}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePath_ripple(JNIEnv* env,jclass,jintArray output,jint width,jint height,jfloat left,jfloat top,jfloat cx,jfloat cy,jfloat radius,jfloat progress,jfloat phase,jfloat resolutionWidth,jfloat resolutionHeight,jint color,jint sparkle,jfloat boundX,jfloat boundY,jfloat boundRadius){
 if(!std::isfinite(boundX)||!std::isfinite(boundY)||!std::isfinite(boundRadius)||std::abs(boundX)>10000000||std::abs(boundY)>10000000||boundRadius<0||boundRadius>32768||!output||width<=0||height<=0||width>8192||height>8192||int64_t(width)*height>4*1024*1024||env->GetArrayLength(output)!=width*height||!std::isfinite(left)||std::abs(left)>10000000||!std::isfinite(top)||std::abs(top)>10000000||!std::isfinite(cx)||std::abs(cx)>10000000||!std::isfinite(cy)||std::abs(cy)>10000000||!std::isfinite(radius)||radius<0||radius>32768||!std::isfinite(progress)||progress<0||progress>1||!std::isfinite(phase)||std::abs(phase)>1e12f||!std::isfinite(resolutionWidth)||!std::isfinite(resolutionHeight)||resolutionWidth<=0||resolutionHeight<=0){auto cls=env->FindClass("java/lang/IllegalArgumentException");if(cls)env->ThrowNew(cls,"Ripple raster bounds");return;}
 float fadeIn=clamp(progress/.13f),noiseFade=std::min(fadeIn,1-clamp((progress-.4f)/.1f)),fade=std::min(fadeIn,1-clamp((progress-.4f)/.6f));
 float r=radius*progress,thickness=.05f*radius,outer=r+thickness,inner=std::max(0.f,r-thickness),limit=1.5f*outer;
 float oscillation[4];for(int i=0;i<4;i++)oscillation[i]=std::sin(3.14159265358979323846*(phase*.001f+.35f*i));
 Grid grids[3];const float diameters[]={.17f,.2f,.275f};const double offsets[]={1.7,2,2.75};
 for(int i=0;i<3;i++){float base=i==0?.75f:i==1?.3f:1.5f,velocity=i==0?.01f:-.0066f,angle=1.5f*(i==0?.55f:i==1?.45f:.35f);double rotation=phase*3.14159265358979323846*(i==1?-.0078125:.0078125)+offsets[i]*3.14159265358979323846;grids[i]={base+phase*velocity*std::cos(angle),base+phase*velocity*std::sin(angle),float(std::cos(rotation)),float(std::sin(rotation)),diameters[i]};}
 auto pixels=static_cast<jint*>(env->GetPrimitiveArrayCritical(output,nullptr));if(!pixels)return;
 std::fill(pixels,pixels+width*height,0);
 int x0=std::max(0,int(std::floor(cx-left-limit))),x1=std::min(width,int(std::ceil(cx-left+limit))),y0=std::max(0,int(std::floor(cy-top-limit))),y1=std::min(height,int(std::ceil(cy-top+limit)));
 float ca=uint32_t(color)>>24,sa=(uint32_t(sparkle)>>24)/255.f;
 for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
  float px=left+x+.5f,py=top+y+.5f,d=std::sqrt((px-cx)*(px-cx)+(py-cy)*(py-cy));if(d>limit)continue;
  float bx=px-boundX,by=py-boundY,bd=bx*bx+by*by;
  if(bd>(boundRadius+.5f)*(boundRadius+.5f))continue;
  float edge=bd<=(boundRadius-.5f)*(boundRadius-.5f)&&boundRadius>=.5f?1:clamp(boundRadius+.5f-std::sqrt(bd));
  float wave=soft(d,r)*fade*ca/255.f,mix=0;
  float ring=clamp(soft(d,outer)-soft(d,inner));
  if(noiseFade>0&&ring>0&&sa>0){
   float u=std::floor(px/2.1f)*2.1f/resolutionWidth,v=std::floor(py/2.1f)*2.1f/resolutionHeight;
   u=fract(u*5.3987f);v=fract(v*5.4421f);float dot=v*(u+21.5351f)+u*(v+14.3137f);u+=dot;v+=dot;float uv=u*v,n=fract(uv*95.4307f)+fract(uv*75.04961f)-1;
   bool bright=false;for(int i=0;i<4;i++)if(n+oscillation[i]>=i*.1f&&n+oscillation[i]<i*.1f+.05f)bright=true;
   if(bright){float gx=px/resolutionWidth*.8f,gy=py/resolutionHeight*.8f,a=grid(gx,gy,grids[0]),b=grid(gx,gy,grids[1]),c=grid(gx,gy,grids[2]);mix=sa*ring*noiseFade*clamp(.45f+.4f*(a*a+b-c));}
  }
  float alpha=wave*(1-mix)+sa*mix;if(alpha<=0)continue;
  uint32_t value=uint32_t(std::lround(clamp(alpha*edge)*255))<<24;
  for(int shift:{16,8,0}){float c=((uint32_t(color)>>shift)&255)*wave*(1-mix)+((uint32_t(sparkle)>>shift)&255)*sa*mix;value|=uint32_t(std::lround(std::clamp(c/alpha,0.f,255.f)))<<shift;}
  pixels[y*width+x]=jint(value);
 }
 env->ReleasePrimitiveArrayCritical(output,pixels,0);
}
