// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
public final class BitmapColorsSmoke {
 public static void main(String[] args){
  int[] colors={0x80123456,0xffabcdef,0,0xff102030,0xff405060};
  Bitmap a=Bitmap.createBitmap(colors,0,3,2,2,Bitmap.Config.ARGB_8888);
  if(a.isMutable()||a.getPixel(0,0)!=colors[0]||a.getPixel(1,1)!=colors[4])throw new AssertionError("color copy");
  colors[0]=0;if(a.getPixel(0,0)!=0x80123456)throw new AssertionError("input alias");
  try{a.setPixel(0,0,0);throw new AssertionError("mutable array bitmap");}catch(IllegalStateException expected){}
  android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.densityDpi=320;
  Bitmap b=Bitmap.createBitmap(metrics,colors,3,-3,2,2,Bitmap.Config.ARGB_8888);
  if(b.getPixel(0,0)!=colors[3]||b.getPixel(1,1)!=colors[1]||b.getDensity()!=320)throw new AssertionError("reverse stride/density");
  Bitmap c=Bitmap.createBitmap(new int[]{0x80123456},1,1,Bitmap.Config.ALPHA_8);
  if(c.getPixel(0,0)!=0x80000000)throw new AssertionError("alpha mask");
  Bitmap d=Bitmap.createBitmap(metrics,new int[]{0xff010203},1,1,Bitmap.Config.ARGB_8888);
  if(d.getPixel(0,0)!=0xff010203||d.getDensity()!=320)throw new AssertionError("packed metrics");
  for(int stride:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,-3}){
   try{Bitmap.createBitmap(colors,0,stride,2,2,Bitmap.Config.ARGB_8888);throw new AssertionError("invalid row bounds");}catch(ArrayIndexOutOfBoundsException expected){}
  }
  try{Bitmap.createBitmap(colors,0,1,2,2,Bitmap.Config.ARGB_8888);throw new AssertionError("short stride");}catch(IllegalArgumentException expected){}
  try{Bitmap.createBitmap(colors,0,2,2,0,Bitmap.Config.ARGB_8888);throw new AssertionError("zero height");}catch(IllegalArgumentException expected){}
  a.recycle();b.recycle();c.recycle();d.recycle();
  System.out.println("BITMAP_COLOR_ARRAY_OK");
 }
}
