// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class TextBoundsFixture {
 private static void check(boolean ok){if(!ok)throw new AssertionError("Text bounds");}
 public static void check(){
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(30);p.setColor(0xff000000);String text="Agj a\u0301";
  for(int mode=0;mode<4;mode++){
   p.setTextScaleX(mode==1?1.5f:1);p.setTextSkewX(mode==2?-.3f:0);p.setFakeBoldText(mode==3);
   Rect bounds=new Rect();p.getTextBounds(text,0,text.length(),bounds);check(bounds.top<0&&bounds.bottom>0&&bounds.right>bounds.left);
   Bitmap bitmap=Bitmap.createBitmap(400,140,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawText(text,50,70,p);int ink=0;
   for(int y=0;y<140;y++)for(int x=0;x<400;x++)if((bitmap.getPixel(x,y)>>>24)!=0){ink++;check(x-50>=bounds.left&&x-50<bounds.right&&y-70>=bounds.top&&y-70<bounds.bottom);}
   check(ink>0);Rect chars=new Rect();p.getTextBounds(text.toCharArray(),0,text.length(),chars);check(chars.equals(bounds));
   Rect slice=new Rect();p.getTextBounds("x"+text+"y",1,text.length()+1,slice);check(slice.equals(bounds));
  }
  Rect empty=new Rect(1,2,3,4);p.getTextBounds("   ",0,3,empty);check(empty.isEmpty());p.getTextBounds("",0,0,empty);check(empty.isEmpty());
  try{p.getTextBounds("a",0,2,empty);throw new AssertionError("Invalid range");}catch(IndexOutOfBoundsException expected){}
  System.out.println("TEXT_BOUNDS_GLYPH_PIXELS_OK");
 }
}
