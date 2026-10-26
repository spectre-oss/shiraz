// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class CornerFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static Bitmap render(Path path,Paint paint){Bitmap image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(image).drawPath(path,paint);return image;}
 private static boolean equal(Bitmap a,Bitmap b){for(int y=0;y<64;y++)for(int x=0;x<64;x++)if(a.getPixel(x,y)!=b.getPixel(x,y))return false;return true;}
 public static void check(){
  Paint paint=new Paint();paint.setColor(Color.RED);Path box=new Path();box.addRect(10,10,50,50,Path.Direction.CW);Bitmap plain=render(box,paint);paint.setPathEffect(new CornerPathEffect(10));Bitmap round=render(box,paint);try{check(plain.getPixel(10,10)==Color.RED&&round.getPixel(10,10)==0&&round.getPixel(30,30)==Color.RED&&round.getPixel(10,30)==Color.RED,"Closed corners round without losing edges/interior");paint.setPathEffect(null);Bitmap again=render(box,paint);try{check(equal(plain,again),"Source path unchanged");}finally{again.recycle();}paint.setPathEffect(new CornerPathEffect(0));Bitmap zero=render(box,paint);try{check(equal(plain,zero),"Zero radius is identity");}finally{zero.recycle();}
   paint.setPathEffect(new CornerPathEffect(10));Bitmap rect=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);try{new Canvas(rect).drawRect(10,10,50,50,paint);check(equal(rect,round),"Filled drawRect applies corner effect");}finally{rect.recycle();}
  }finally{plain.recycle();round.recycle();}
  Path elbow=new Path();elbow.moveTo(10,10);elbow.lineTo(30,10);elbow.lineTo(30,30);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setPathEffect(new CornerPathEffect(8));Bitmap open=render(elbow,paint);try{check(open.getPixel(29,10)==0&&open.getPixel(28,12)!=0&&open.getPixel(12,10)!=0&&open.getPixel(30,28)!=0,"Open elbow rounds corner and preserves endpoints");}finally{open.recycle();}
  Path curve=new Path();curve.moveTo(5,20);curve.cubicTo(10,0,30,0,40,20);curve.lineTo(50,20);paint.setPathEffect(null);Bitmap base=render(curve,paint);paint.setPathEffect(new CornerPathEffect(8));Bitmap curved=render(curve,paint);try{check(equal(base,curved),"Cubic geometry remains unchanged");}finally{base.recycle();curved.recycle();}
  Path holes=new Path();holes.setFillType(Path.FillType.EVEN_ODD);holes.addRect(5,5,55,55,Path.Direction.CW);holes.addRect(20,20,40,40,Path.Direction.CW);paint.setStyle(Paint.Style.FILL);paint.setPathEffect(new CornerPathEffect(5));Bitmap hole=render(holes,paint);try{check(hole.getPixel(30,30)==0&&hole.getPixel(10,30)==Color.RED,"Multiple contours preserve fill rule");}finally{hole.recycle();}
  Path tiny=new Path();tiny.moveTo(20,20);tiny.lineTo(20,20);tiny.lineTo(24,20);tiny.lineTo(24,24);tiny.lineTo(20,24);tiny.close();paint.setPathEffect(new CornerPathEffect(100));Bitmap small=render(tiny,paint);try{check(small.getPixel(22,22)!=0&&small.getPixel(19,22)==0&&small.getPixel(25,22)==0,"Short segments clamp trim distance");}finally{small.recycle();}
  Paint copy=new Paint(paint);check(copy.getPathEffect()==paint.getPathEffect(),"Paint copy keeps immutable effect");try{new CornerPathEffect(Float.NaN);throw new AssertionError("Invalid radius accepted");}catch(IllegalArgumentException expected){}
  System.out.println("CORNER_PATH_PIXELS_OK");
 }
}
