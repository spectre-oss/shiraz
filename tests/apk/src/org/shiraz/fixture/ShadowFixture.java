// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class ShadowFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static int coverageMix(int before,int after,int coverage){
  double c=coverage/255.0,alpha=(before>>>24)*(1-c)+(after>>>24)*c;if(alpha==0)return 0;int out=(int)Math.round(alpha)<<24;
  for(int shift=0;shift<24;shift+=8)out|=(int)Math.round((((before>>>shift)&255)*(before>>>24)*(1-c)+((after>>>shift)&255)*(after>>>24)*c)/alpha)<<shift;return out;
 }
 private static boolean near(int a,int b){for(int shift=0;shift<=24;shift+=8)if(Math.abs(((a>>>shift)&255)-((b>>>shift)&255))>1)return false;return true;}
 private static void clippedShadow(PorterDuff.Mode mode){
  Bitmap full=Bitmap.createBitmap(96,80,Bitmap.Config.ARGB_8888),clipped=Bitmap.createBitmap(96,80,Bitmap.Config.ARGB_8888),mask=Bitmap.createBitmap(96,80,Bitmap.Config.ARGB_8888);
  final int background=0x70402010;full.eraseColor(background);clipped.eraseColor(background);
  Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0x90ee4422);paint.setShadowLayer(3,12,-4,0xb0000088);paint.setXfermode(new PorterDuffXfermode(mode));
  Path hole=new Path();hole.addCircle(42,30,14,Path.Direction.CW);
  Canvas a=new Canvas(full),b=new Canvas(clipped),m=new Canvas(mask);
  b.clipRect(18,9,82,68);b.clipPath(hole,Region.Op.DIFFERENCE);m.clipRect(18,9,82,68);m.clipPath(hole,Region.Op.DIFFERENCE);m.drawColor(Color.WHITE);
  for(Canvas canvas:new Canvas[]{a,b}){canvas.translate(4,6);canvas.scale(1.25f,.75f);canvas.drawRoundRect(new RectF(8,14,44,58),10,10,paint);}
  for(int y=0;y<80;y++)for(int x=0;x<96;x++)check(near(clipped.getPixel(x,y),coverageMix(background,full.getPixel(x,y),mask.getPixel(x,y)>>>24)),"Complex clip preserves full shadow pixels: "+mode+" at "+x+","+y);
  int[] before=new int[96*80];clipped.getPixels(before,0,96,0,0,96,80);
  b.drawColor(Color.BLUE);
  for(int y=0;y<80;y++)for(int x=0;x<96;x++)check(near(clipped.getPixel(x,y),coverageMix(before[y*96+x],Color.BLUE,mask.getPixel(x,y)>>>24)),"Complex shadow restores destination and clip");
  full.recycle();clipped.recycle();mask.recycle();
 }
 public static void check(){
  clippedShadow(PorterDuff.Mode.SRC_OVER);clippedShadow(PorterDuff.Mode.CLEAR);clippedShadow(PorterDuff.Mode.DST_OUT);clippedShadow(PorterDuff.Mode.SRC);clippedShadow(PorterDuff.Mode.SRC_IN);clippedShadow(PorterDuff.Mode.MULTIPLY);
  Bitmap b=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888),source=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.RED);p.setShadowLayer(2,15,0,Color.BLUE);
  try{c.drawRect(10,10,20,20,p);check(b.getPixel(15,15)==Color.RED,"Foreground over shadow");check(Color.blue(b.getPixel(30,15))==255&&(b.getPixel(30,15)>>>24)>150,"Offset colored shadow");check((b.getPixel(25,15)>>>24)>0&&(b.getPixel(24,15)>>>24)<(b.getPixel(30,15)>>>24),"Soft shadow edge");check(b.getPixel(70,50)==0,"Shadow finite support");
   b.eraseColor(0);p.setAlpha(64);c.drawRect(10,10,20,20,p);int faint=b.getPixel(30,15)>>>24;check(faint>20&&faint<=64,"Opaque shadow color uses paint alpha");p.setShadowLayer(2,15,0,0x800000ff);b.eraseColor(0);c.drawRect(10,10,20,20,p);check((b.getPixel(30,15)>>>24)>faint,"Translucent shadow color supplies its own alpha");
   p.setColor(Color.RED);p.setShadowLayer(2,15,0,Color.BLUE);b.eraseColor(0);int save=c.save();c.clipRect(25,0,40,40);c.drawRect(10,10,20,20,p);c.restoreToCount(save);check(b.getPixel(15,15)==0&&(b.getPixel(30,15)>>>24)>0,"Source outside clip casts shadow into clip");
   b.eraseColor(0);p.setShadowLayer(2,-15,0,Color.BLUE);c.drawRect(30,10,40,20,p);check((b.getPixel(20,15)>>>24)>0,"Negative shadow offset");
   b.eraseColor(0);p.setShadowLayer(2,15,0,Color.BLUE);source.setPixel(5,5,Color.WHITE);c.drawBitmap(source,10,10,p);check((b.getPixel(30,15)>>>24)>0&&b.getPixel(34,19)==0,"Bitmap shadow follows alpha rather than rectangle");
   b.eraseColor(0);p.setTextSize(24);p.setShadowLayer(2,25,0,Color.BLUE);c.drawText("A",2,30,p);boolean blue=false;for(int y=0;y<60;y++)for(int x=25;x<60;x++)if(Color.blue(b.getPixel(x,y))>0)blue=true;check(blue,"Glyph shadow pixels");
   b.eraseColor(Color.BLUE);p.setShadowLayer(2,15,0,0);p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));c.drawRect(10,10,20,20,p);check(b.getPixel(15,15)==0&&b.getPixel(30,15)==0&&b.getPixel(70,50)==Color.BLUE,"Shadow transfer mode uses coverage with transparent source");p.setXfermode(null);p.setShadowLayer(2,25,0,Color.BLUE);
   Paint copy=new Paint(p);check(copy.getShadowLayerRadius()>0&&copy.getShadowLayerRadius()==2&&copy.getShadowLayerDx()==25,"Paint shadow copy");copy.reset();check(copy.getShadowLayerRadius()==0,"Paint shadow reset");p.clearShadowLayer();b.eraseColor(0);c.drawRect(10,10,20,20,p);check(b.getPixel(30,15)==0,"Clear shadow removes pixels");
   try{p.setShadowLayer(Float.NaN,0,0,Color.BLACK);throw new AssertionError("Invalid shadow accepted");}catch(IllegalArgumentException expected){}check(p.getShadowLayerRadius()==0,"Invalid shadow atomic");
  }finally{source.recycle();b.recycle();}
  System.out.println("PAINT_SHADOW_PIXELS_OK");
 }
 public static void xml(android.content.Context context){android.widget.TextView v=(android.widget.TextView)android.view.LayoutInflater.from(context).inflate(context.getResources().getIdentifier("shadow","layout","org.shiraz.fixture"),null);check(v.getShadowRadius()==2&&v.getShadowDx()==3&&v.getShadowDy()==-1&&v.getShadowColor()==0x800000ff,"XML text shadow");}
}
