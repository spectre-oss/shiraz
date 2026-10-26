// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class HintingFixture {
 public static void check(){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);if(p.getHinting()!=Paint.HINTING_ON)throw new AssertionError("Default hinting");
  p.setHinting(Paint.HINTING_OFF);Paint copy=new Paint(p);if(copy.getHinting()!=0)throw new AssertionError("Hinting copy");copy.reset();if(copy.getHinting()!=1||p.getHinting()!=0)throw new AssertionError("Hinting reset ownership");
  try{p.setHinting(2);throw new AssertionError("Invalid hinting accepted");}catch(IllegalArgumentException expected){}if(p.getHinting()!=0)throw new AssertionError("Invalid hinting changed state");
  p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(11.5f);p.setColor(Color.WHITE);
  Bitmap off=Bitmap.createBitmap(160,32,Bitmap.Config.ARGB_8888),on=Bitmap.createBitmap(160,32,Bitmap.Config.ARGB_8888);
  try{
   float advance=p.measureText("Hinting office 123");new Canvas(off).drawText("Hinting office 123",2,22,p);p.setHinting(1);new Canvas(on).drawText("Hinting office 123",2,22,p);
   if(p.measureText("Hinting office 123")!=advance)throw new AssertionError("Hinting changed linear layout advances");
   boolean different=false,inkOff=false,inkOn=false;for(int y=0;y<32;y++)for(int x=0;x<160;x++){int a=off.getPixel(x,y),b=on.getPixel(x,y);different|=a!=b;inkOff|=a!=0;inkOn|=b!=0;}
   if(!different||!inkOff||!inkOn)throw new AssertionError("Hinting must affect glyph rasterization");
  }finally{off.recycle();on.recycle();}
  System.out.println("PAINT_HINTING_RASTER_OK");
 }
}
