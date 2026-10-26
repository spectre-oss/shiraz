/* SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2021 The Android Open Source Project
 * GPU adaptation by Shiraz; provenance and license: LICENSES/RIPPLE-NOTICE.txt.
 */
R"SKSL(
uniform float2 center;
uniform float radius;
uniform float progress;
uniform float phase;
uniform float2 resolution;
uniform float3 bound;
layout(color) uniform half4 color;
layout(color) uniform half4 sparkle;
uniform float4 oscillation;
uniform float4 grids[3];
float soft(float d,float r){return r<=0?0:1-smoothstep(.5,1.5,d/r);}
float grid(float2 p,float4 g,float diameter){float2 d=g.xy-p;float2 v=float2(g.z*d.x+g.w*d.y,-g.w*d.x+g.z*d.y)+g.xy;v=mod(v,diameter)/.8;float c=diameter/.8*.5,r=.65*c,h=r*25;return 1-smoothstep(1-h,1+h,length(v-float2(c))/r);}
half4 main(float2 p){
 float edge=clamp(bound.z+.5-distance(p,bound.xy),0,1);
 float fadeIn=clamp(progress/.13,0,1),noiseFade=min(fadeIn,1-clamp((progress-.4)/.1,0,1)),fade=min(fadeIn,1-clamp((progress-.4)/.6,0,1));
 float d=distance(p,center),r=radius*progress,t=.05*radius;
 float wave=soft(d,r)*fade*color.a,m=0;
 float ring=clamp(soft(d,r+t)-soft(d,max(0,r-t)),0,1);
 if(noiseFade>0&&ring>0&&sparkle.a>0){
  float2 uv=fract(floor(p/2.1)*2.1/resolution*float2(5.3987,5.4421));uv+=dot(uv.yx,uv.xy+float2(21.5351,14.3137));float product=uv.x*uv.y,n=fract(product*95.4307)+fract(product*75.04961)-1;
  bool bright=false;for(int i=0;i<4;i++){float v=n+oscillation[i];bright=bright||(v>=float(i)*.1&&v<float(i)*.1+.05);}
  if(bright){float2 q=p/resolution*.8;float a=grid(q,grids[0],.17),b=grid(q,grids[1],.2),c=grid(q,grids[2],.275);m=sparkle.a*ring*noiseFade*clamp(.45+.4*(a*a+b-c),0,1);}
 }
 return half4(color.rgb*wave*(1-m)+sparkle.rgb*sparkle.a*m,wave*(1-m)+sparkle.a*m)*edge;
}
)SKSL"
