// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.drawable.shapes.*;
public final class ShapeDrawableFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static Bitmap draw(ShapeDrawable d){Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);d.draw(new Canvas(b));return b;}
 public static void check() throws Exception {
  float[] corners={6,6,6,6,6,6,6,6};RoundRectShape rounded=new RoundRectShape(corners,null,null);corners[0]=0;
  ShapeDrawable d=new ShapeDrawable(rounded);d.getPaint().setColor(Color.RED);d.setBounds(4,4,24,24);Bitmap b=draw(d);check(b.getPixel(4,4)==0&&b.getPixel(14,14)==Color.RED&&b.getPixel(25,14)==0,"Rounded shape bounds and corner copy");b.recycle();
  RoundRectShape clone=rounded.clone();clone.resize(8,8);check(rounded.getWidth()==20&&clone.getWidth()==8,"Shape clone isolation");
  d.setShape(new RoundRectShape(null,new RectF(4,4,4,4),null));b=draw(d);check(b.getPixel(5,5)==Color.RED&&b.getPixel(14,14)==0,"Inset hole");b.recycle();
  d.setShape(new OvalShape());b=draw(d);check(b.getPixel(4,4)==0&&b.getPixel(14,14)==Color.RED,"Oval shape");b.recycle();
  d.setShape(new RectShape());d.getPaint().setAlpha(128);d.setAlpha(128);b=draw(d);check((b.getPixel(14,14)>>>24)==64&&d.getPaint().getAlpha()==128,"Drawable alpha multiplies without mutating paint");b.recycle();
  d.getPaint().setAlpha(255);d.setAlpha(255);d.setTintList(android.content.res.ColorStateList.valueOf(Color.BLUE));b=draw(d);check(b.getPixel(14,14)==Color.BLUE,"Shape tint");b.recycle();d.setColorFilter(new PorterDuffColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN));b=draw(d);check(b.getPixel(14,14)==Color.GREEN,"Explicit filter precedence");b.recycle();d.setColorFilter(null);d.setTintList(null);
  d.setShape(rounded);d.getPaint().setShadowLayer(3,2,2,Color.BLACK);b=draw(d);check((b.getPixel(25,14)>>>24)>0,"Shadow outside shape bounds");b.recycle();
  d.getPaint().clearShadowLayer();d.setBounds(0,0,8,8);check(rounded.getWidth()==8&&rounded.getHeight()==8,"Bounds resize shape");
  d.setPadding(1,2,3,4);Rect padding=new Rect();check(d.getPadding(padding)&&padding.equals(new Rect(1,2,3,4)),"Shape padding");d.setIntrinsicWidth(7);d.setIntrinsicHeight(9);check(d.getIntrinsicWidth()==7&&d.getIntrinsicHeight()==9,"Intrinsic size");
  d.setShape(null);b=draw(d);check(b.getPixel(0,0)==Color.RED,"Null shape draws rectangle");b.recycle();
  try{new RoundRectShape(new float[7],null,null);throw new AssertionError("Short radii");}catch(IndexOutOfBoundsException expected){}
  System.out.println("SHAPE_DRAWABLE_OK");
 }
}
