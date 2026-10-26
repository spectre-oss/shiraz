// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class TextScaleFixture {
 public static void check(){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(18);p.setColor(Color.WHITE);String text="MMMM";
  float width=p.measureText(text);p.setTextScaleX(2);if(p.measureText(text)!=2*width)throw new AssertionError("Scaled measure");
  float[] measured={-1};int count=p.breakText(text,true,2*width,measured);if(count!=text.length()||measured[0]!=2*width)throw new AssertionError("Scaled text break");
  Paint copy=new Paint(p);p.reset();if(p.getTextScaleX()!=1||copy.getTextScaleX()!=2)throw new AssertionError("Scale copy/reset");
  try{copy.setTextScaleX(Float.NaN);throw new AssertionError("NaN scale accepted");}catch(IllegalArgumentException expected){}
  p=new Paint(copy);p.setTextScaleX(1);Bitmap normal=Bitmap.createBitmap(200,40,Bitmap.Config.ARGB_8888),wide=Bitmap.createBitmap(200,40,Bitmap.Config.ARGB_8888);
  try{new Canvas(normal).drawText(text,4,28,p);new Canvas(wide).drawText(text,4,28,copy);int normalRight=0,wideRight=0;for(int y=0;y<40;y++)for(int x=0;x<200;x++){if(normal.getPixel(x,y)!=0)normalRight=Math.max(normalRight,x);if(wide.getPixel(x,y)!=0)wideRight=Math.max(wideRight,x);}if(normalRight<10||wideRight<normalRight*1.8||wideRight>normalRight*2.1)throw new AssertionError("Scaled glyphs and advances");
   copy.setTextScaleX(0);if(copy.measureText(text)!=0||copy.breakText(text,true,0,measured)!=text.length()||measured[0]!=0)throw new AssertionError("Zero scale metrics");
   wide.eraseColor(0);new Canvas(wide).drawText(text,4,28,copy);for(int y=0;y<40;y++)for(int x=0;x<200;x++)if(wide.getPixel(x,y)!=0)throw new AssertionError("Zero scale raster");
  }finally{normal.recycle();wide.recycle();}
  System.out.println("TEXT_SCALE_METRICS_RASTER_OK");
 }
}
