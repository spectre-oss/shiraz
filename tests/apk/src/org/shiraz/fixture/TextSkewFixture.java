// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import java.util.Arrays;
public final class TextSkewFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static int[] draw(Paint paint,boolean clip){Bitmap b=Bitmap.createBitmap(128,80,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(b);if(clip)c.clipRect(55,20,65,55);c.drawText("H",50,60,paint);int[] out=new int[128*80];b.getPixels(out,0,128,0,0,128,80);return out;}finally{b.recycle();}}
 private static double center(int[] pixels){long sum=0,total=0;for(int i=0;i<pixels.length;i++){int alpha=pixels[i]>>>24;sum+=(i%128)*alpha;total+=alpha;}check(total>0,"Visible skewed glyph");return (double)sum/total;}
 public static void check(){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(40);p.setTypeface(Typeface.MONOSPACE);check(p.getTextSkewX()==0,"Default skew");float advance=p.measureText("H");int[] plain=draw(p,false);p.setTextSkewX(-.5f);int[] right=draw(p,false);check(center(right)>center(plain)+5,"Negative skew inclines glyph to right");check(p.measureText("H")==advance,"Skew preserves horizontal advance");Paint copy=new Paint(p);check(copy.getTextSkewX()==-.5f&&Arrays.equals(right,draw(copy,false)),"Paint copy retains skew");p.setTextSkewX(.5f);check(center(draw(p,false))<center(plain)-5,"Positive skew inclines glyph to left");p.setTextSkewX(-.5f);int[] clipped=draw(p,true);for(int i=0;i<clipped.length;i++)if(i%128<55||i%128>=65||i/128<20||i/128>=55)check(clipped[i]==0,"Skew obeys clipping");p.setTextSkewX(0);check(Arrays.equals(plain,draw(p,false)),"Skew does not leak into cached font");copy.reset();check(copy.getTextSkewX()==0,"Reset clears skew");try{p.setTextSkewX(Float.NaN);throw new AssertionError("Nonfinite skew accepted");}catch(IllegalArgumentException expected){}check(p.getTextSkewX()==0,"Invalid skew preserves paint");System.out.println("TEXT_SKEW_RENDER_CLIP_OK");}
}
