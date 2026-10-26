// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class LargeBitmapSmoke {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  int w=7076,h=3158;Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.RED);c.drawRect(w-10,h-10,w,h,p);check(b.getPixel(w-1,h-1)==Color.RED,"large target far-edge native fill");
  Path path=new Path();path.addRect(w-30,h-30,w-20,h-20,Path.Direction.CW);p.setColor(Color.BLUE);c.drawPath(path,p);check(b.getPixel(w-25,h-25)==Color.BLUE,"large target native path");
  p.setColor(Color.BLACK);p.setTextSize(20);c.drawText("Map حافظ",100,100,p);boolean ink=false;for(int y=70;y<105;y++)for(int x=100;x<240;x++)ink|=b.getPixel(x,y)!=0;check(ink,"large target native shaped text");
  Bitmap reference=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888);new Canvas(reference).drawText("Map حافظ",100,100,p);for(int y=0;y<150;y++)for(int x=0;x<300;x++)check(b.getPixel(x,y)==reference.getPixel(x,y),"text pixels independent of target stride");reference.recycle();
  Bitmap copy=b.copy(Bitmap.Config.ARGB_8888,true);check(copy.getPixel(w-1,h-1)==Color.RED&&copy.getPixel(w-25,h-25)==Color.BLUE,"large copy preserves distant pixels");copy.recycle();
  Bitmap small=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);Canvas out=new Canvas(small);out.drawBitmap(b,new Rect(w-10,h-10,w,h),new RectF(0,0,10,10),null);check(small.getPixel(5,5)==Color.RED,"large bitmap source sampling");
  small.eraseColor(0);Paint texture=new Paint();BitmapShader shader=new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix map=new Matrix();map.setTranslate(-(w-10),-(h-10));shader.setLocalMatrix(map);texture.setShader(shader);out.drawRect(0,0,10,10,texture);check(small.getPixel(5,5)==Color.RED,"large bitmap shader source");
  Bitmap tile=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);tile.eraseColor(Color.GREEN);c.drawBitmap(tile,w-2,h-2,null);check(b.getPixel(w-1,h-1)==Color.GREEN,"native bitmap large destination");tile.recycle();small.recycle();b.recycle();
  try{Bitmap.createBitmap(8192,4097,Bitmap.Config.ARGB_8888);throw new AssertionError("render limit exceeded");}catch(IllegalArgumentException expected){}
  // Decoded pixels retain their independent16M limit, with a correctly sized backing array.
  java.lang.reflect.Method decode=Bitmap.class.getDeclaredMethod("fromPixels",int.class,int.class,int[].class);decode.setAccessible(true);try{decode.invoke(null,w,h,new int[w*h]);throw new AssertionError("decoded limit changed");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"decoded rejection");}
  System.out.println("LARGE_BITMAP_RENDER_BOUNDS_OK");
 }
}
