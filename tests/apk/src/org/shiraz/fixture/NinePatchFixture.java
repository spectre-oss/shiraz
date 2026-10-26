// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.Resources;
import java.io.*;
public final class NinePatchFixture {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void check(Resources resources)throws Exception {
  Bitmap bitmap;Rect padding=new Rect();try(InputStream stream=resources.getAssets().open("images/patch.png")){bitmap=BitmapFactory.decodeStream(stream,padding,null);}
  check(bitmap!=null&&padding.equals(new Rect(1,1,1,1)),"Compiled patch padding");byte[] chunk=bitmap.getNinePatchChunk();check(NinePatch.isNinePatchChunk(chunk),"Decoded patch recognized");NinePatchDrawable drawable=new NinePatchDrawable(null,bitmap,chunk,null,"fixture");drawable.setFilterBitmap(false);
  Bitmap target=Bitmap.createBitmap(9,7,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,9,7);drawable.draw(new Canvas(target));
  check(target.getPixel(0,0)==Color.RED&&target.getPixel(8,0)==Color.BLUE&&target.getPixel(0,6)==Color.MAGENTA&&target.getPixel(8,6)==Color.WHITE,"Fixed corners preserved");check(target.getPixel(4,0)==Color.GREEN&&target.getPixel(4,3)==0xff888888&&target.getPixel(0,3)==Color.YELLOW,"Stretchable rows and center");target.recycle();
  drawable.setTargetDensity(320);check(drawable.getIntrinsicWidth()==6&&drawable.getIntrinsicHeight()==6,"Density intrinsic size");drawable.getPadding(padding);check(padding.equals(new Rect(2,2,2,2)),"Density padding");target=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,10,10);drawable.draw(new Canvas(target));check(target.getPixel(1,1)==Color.RED&&target.getPixel(2,2)==0xff888888,"Density preserves scaled border width");target.recycle();
  target=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,2,2);drawable.draw(new Canvas(target));check(target.getPixel(0,0)==Color.RED&&target.getPixel(1,1)==Color.WHITE,"Shrink below fixed border total");target.recycle();
  Drawable resource=resources.getDrawable(resources.getIdentifier("nine_patch_test","drawable","org.shiraz.fixture"));check(resource instanceof NinePatchDrawable,"AAPT-compiled resource creates nine-patch drawable");resource.getPadding(padding);check(padding.equals(new Rect(1,1,1,1)),"AAPT-compiled padding");
  NinePatchDrawable xml=(NinePatchDrawable)resources.getDrawable(R.drawable.patch_xml);xml.setFilterBitmap(false);xml.setBounds(1,1,10,8);target=Bitmap.createBitmap(11,9,Bitmap.Config.ARGB_8888);xml.draw(new Canvas(target));check(target.getPixel(1,1)==0x80ff0000,"XML base alpha and bounds");target.eraseColor(0);xml.setLayoutDirection(1);xml.draw(new Canvas(target));check(target.getPixel(1,1)==0x800000ff&&target.getPixel(9,1)==0x80ff0000,"XML RTL mirror");target.eraseColor(0);xml.setAlpha(128);xml.draw(new Canvas(target));check((target.getPixel(1,1)>>>24)==64&&xml.getAlpha()==128,"Base alpha multiplies runtime alpha without changing it");target.recycle();
  Drawable tinted=resources.getDrawable(R.drawable.patch_tint);tinted.setBounds(0,0,9,7);target=Bitmap.createBitmap(9,7,Bitmap.Config.ARGB_8888);tinted.draw(new Canvas(target));check(target.getPixel(4,3)==0xff00ffff,"XML tint");target.recycle();
  try{resources.getDrawable(R.drawable.patch_invalid);throw new AssertionError("Non-patch src accepted");}catch(IllegalArgumentException expected){}catch(Resources.NotFoundException expected){}
  InsetDrawable inset=(InsetDrawable)resources.getDrawable(R.drawable.inset_patch);inset.setBounds(0,0,20,20);inset.getPadding(padding);check(padding.equals(new Rect(3,4,3,3)),"XML inset includes child padding and uniform zero-edge fallback");check(inset.getIntrinsicWidth()==7&&inset.getIntrinsicHeight()==8,"XML inset intrinsic size");
  InsetDrawable nested=(InsetDrawable)resources.getDrawable(R.drawable.inset_nested);nested.setBounds(0,0,20,20);target=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);nested.draw(new Canvas(target));check(target.getPixel(5,2)==Color.GREEN&&target.getPixel(4,2)==0&&target.getPixel(15,2)==0,"Nested fractional inset and shape tint XML pixels");target.recycle();nested.setBounds(10,20,50,60);check(nested.getDrawable().getBounds().equals(new Rect(20,22,40,57)),"Nested XML inset resize");
  byte[] outside=chunk.clone();outside[32]=99;try{new NinePatch(bitmap,outside,"outside");throw new AssertionError("Out-of-bounds division accepted");}catch(IllegalArgumentException expected){}
  byte[] bad=chunk.clone();bad[1]=3;check(!NinePatch.isNinePatchChunk(bad),"Malformed count rejected");try{new NinePatch(bitmap,bad,"bad");throw new AssertionError("Malformed chunk accepted");}catch(IllegalArgumentException expected){}
  chunk[0]=0;check(NinePatch.isNinePatchChunk(bitmap.getNinePatchChunk()),"Chunk getter defensive copy");
  BitmapFactory.Options options=new BitmapFactory.Options();options.inDensity=160;options.inTargetDensity=320;Bitmap scaled;try(InputStream stream=resources.getAssets().open("images/patch.png")){scaled=BitmapFactory.decodeStream(stream,padding,options);}check(scaled.getWidth()==6&&padding.equals(new Rect(2,2,2,2)),"Decode scales patch metadata");new NinePatch(scaled,scaled.getNinePatchChunk(),"scaled");scaled.recycle();bitmap.recycle();
  System.out.println("NINE_PATCH_GRID_DENSITY_OK");
 }
}
