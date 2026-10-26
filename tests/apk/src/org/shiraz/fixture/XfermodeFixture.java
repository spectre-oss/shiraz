// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class XfermodeFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void check(){
  Bitmap b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888),src=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.RED);
  try{int[] expected={0,Color.RED,Color.BLUE,Color.RED,Color.BLUE,Color.RED,Color.BLUE,0,0,Color.RED,Color.BLUE,0,Color.BLACK,Color.MAGENTA,Color.BLACK,Color.MAGENTA,Color.MAGENTA,Color.BLUE};int i=0;
   for(PorterDuff.Mode mode:PorterDuff.Mode.values()){b.eraseColor(Color.BLUE);p.setXfermode(new PorterDuffXfermode(mode));c.drawRect(5,5,15,15,p);check(b.getPixel(10,10)==expected[i++],"Opaque mode "+mode);check(b.getPixel(1,1)==Color.BLUE,"Mode preserves outside geometry "+mode);}
   p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));p.setAlpha(128);b.eraseColor(Color.BLUE);c.drawRect(5,5,15,15,p);check(b.getPixel(10,10)==0x7f0000ff,"Destination out uses source alpha");
   p.setColor(Color.RED);p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));b.eraseColor(Color.BLUE);c.drawBitmap(src,null,new Rect(5,5,15,15),p);check(b.getPixel(10,10)==0&&b.getPixel(1,1)==Color.BLUE,"Transparent source clears only bitmap destination");
   p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));p.setAntiAlias(true);b.eraseColor(Color.BLUE);c.drawCircle(20,20,8.4f,p);check(b.getPixel(20,20)==0&&b.getPixel(1,1)==Color.BLUE,"Path clear coverage");boolean partial=false;for(int y=10;y<30;y++)for(int x=10;x<30;x++){int alpha=b.getPixel(x,y)>>>24;if(alpha>0&&alpha<255)partial=true;}check(partial,"Antialiased clear edge");
   b.eraseColor(Color.BLUE);int save=c.save();c.clipRect(10,10,20,20);c.drawRect(0,0,40,40,p);c.restoreToCount(save);check(b.getPixel(15,15)==0&&b.getPixel(5,5)==Color.BLUE,"Transfer respects clip");
   b.eraseColor(Color.BLUE);p.setTextSize(20);c.drawText("A",5,25,p);boolean cleared=false;for(int y=0;y<40;y++)for(int x=0;x<40;x++)if((b.getPixel(x,y)>>>24)<255)cleared=true;check(cleared&&b.getPixel(39,39)==Color.BLUE,"Text clear uses glyph mask");
   p.setAlpha(0);b.eraseColor(Color.BLUE);c.drawRect(5,5,15,15,p);check(b.getPixel(10,10)==0,"CLEAR ignores source color alpha");p.setColor(Color.RED);p.setAlpha(128);p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));b.eraseColor(Color.BLUE);c.drawRect(5,5,15,15,p);int mixed=b.getPixel(10,10);check((mixed>>>24)==255&&Color.red(mixed)==128&&Color.blue(mixed)==127,"Source-atop premultiplied blend");
   p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));b.eraseColor(0x010000ff);c.drawRect(0,0,.75f,1,p);check(b.getPixel(0,0)==0,"Rounded-zero alpha clears RGB");
   Paint copy=new Paint(p);check(copy.getXfermode()==p.getXfermode(),"Paint copies transfer mode");copy.reset();check(copy.getXfermode()==null,"Paint reset restores source-over");
  }finally{src.recycle();b.recycle();}
  System.out.println("XFERMODE_PIXELS_OK");
 }
}
