// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class SubpixelTextFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static Bitmap draw(Paint paint,float x,float y,int kind){Bitmap b=Bitmap.createBitmap(180,100,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);if(kind==1){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);}if(kind==2){c.rotate(8);c.drawText("H",x,y,paint);}else if(kind==3){Path path=new Path();path.moveTo(0,y);path.lineTo(170,y);c.drawTextOnPath("H",path,x,0,paint);}else c.drawText("H",x,y,paint);return b;}
 public static void check(){
  Paint linear=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG|Paint.LINEAR_TEXT_FLAG);linear.setTextSize(17.3f);check(linear.isLinearText()&&linear.getHinting()==Paint.HINTING_ON,"linear flag independent of configured hinting");Paint unhinted=new Paint(linear);unhinted.setLinearText(false);unhinted.setHinting(Paint.HINTING_OFF);
  for(int k=0;k<4;k++){Bitmap x=draw(new Paint(linear),20.3f,60.4f,k),y=draw(new Paint(unhinted),20.3f,60.4f,k);for(int row=0;row<100;row++)for(int col=0;col<180;col++)check(x.getPixel(col,row)==y.getPixel(col,row),"linear text disables hinting for draw path "+k);x.recycle();y.recycle();}
  check(linear.measureText((CharSequence)new StringBuilder("xHafezy"),1,6)==linear.measureText("Hafez"),"CharSequence range measurement");check(linear.measureText((CharSequence)"",0,0)==0,"empty range measurement");try{linear.measureText((CharSequence)"x",0,2);throw new AssertionError("invalid range accepted");}catch(IndexOutOfBoundsException expected){}
  Rect lb=new Rect(),ub=new Rect();linear.getTextBounds("Hafez",0,5,lb);unhinted.getTextBounds("Hafez",0,5,ub);check(lb.equals(ub),"linear text bounds use matching unhinted glyphs");Paint saved=new Paint(linear);check(saved.isLinearText(),"copy linear");saved.reset();check(!saved.isLinearText(),"reset linear");linear.setFlags(Paint.ANTI_ALIAS_FLAG);check(!linear.isLinearText(),"flags clear linear");
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);p.setTextSize(32);p.setColor(Color.BLACK);
  check(p.isSubpixelText()&&p.getFlags()==129,"Material text paint flags");Paint copy=new Paint(p);check(copy.isSubpixelText(),"copy subpixel");copy.reset();check(!copy.isSubpixelText(),"reset subpixel");copy.set(p);copy.setSubpixelText(false);check(copy.getFlags()==1&&p.isSubpixelText(),"independent subpixel flag");
  try{p.setFlags(0x40000000);throw new AssertionError("Unknown flag accepted");}catch(UnsupportedOperationException expected){}check(p.getFlags()==129,"Rejected flags atomic");
  for(int kind=0;kind<4;kind++){
   Paint q=new Paint(p);Bitmap a=draw(q,20.25f,60.25f,kind),b=draw(q,20.75f,60.75f,kind);int changed=0;
   for(int y=0;y<100;y++)for(int x=0;x<180;x++)if(a.getPixel(x,y)!=b.getPixel(x,y))changed++;
   check(changed>10,"Fractional glyph coverage for fill/stroke/affine/path "+kind);a.recycle();b.recycle();
  }
  p.setSubpixelText(false);Bitmap a=draw(p,20.25f,60.25f,0),b=draw(p,20.75f,60.75f,0);
  for(int y=0;y<100;y++)for(int x=0;x<180;x++)check(a.getPixel(x,y)==b.getPixel(x,y),"Disabled flag preserves integer positioning");a.recycle();b.recycle();
  p.setSubpixelText(true);a=draw(p,20.25f,60.25f,0);b=draw(p,21.25f,61.25f,0);
  for(int y=0;y<99;y++)for(int x=0;x<179;x++)check(a.getPixel(x,y)==b.getPixel(x+1,y+1),"Integer translation preserves fractional raster");a.recycle();b.recycle();
  String text="Hafiz حافظ";Rect bounds=new Rect();p.getTextBounds(text,0,text.length(),bounds);a=Bitmap.createBitmap(300,100,Bitmap.Config.ARGB_8888);new Canvas(a).drawText(text,20,60,p);
  boolean ink=false;for(int y=0;y<100;y++)for(int x=0;x<300;x++)if((a.getPixel(x,y)>>>24)!=0){ink=true;check(x>=20+bounds.left&&x<20+bounds.right&&y>=60+bounds.top&&y<60+bounds.bottom,"Subpixel bounds contain glyph ink");}check(ink,"Rendered subpixel ink");a.recycle();
  System.out.println("SUBPIXEL_TEXT_POSITIONING_OK");
 }
}
