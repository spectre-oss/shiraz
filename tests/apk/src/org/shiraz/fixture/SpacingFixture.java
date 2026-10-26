// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class SpacingFixture {
 private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 private static void near(float value,float expected){check(Math.abs(value-expected)<.02f,"Expected "+expected+" got "+value);}
 public static void check(){
  Paint base=new Paint();base.setTypeface(Typeface.MONOSPACE);base.setTextSize(20);Paint spaced=new Paint(base);spaced.setLetterSpacing(.2f);
  near(spaced.measureText("ABC")-base.measureText("ABC"),12);near(spaced.measureText("A\u0301")-base.measureText("A\u0301"),4);near(spaced.measureText("\ud83d\ude00")-base.measureText("\ud83d\ude00"),4);near(spaced.measureText(""),0);
  Paint joining=new Paint();joining.setTextSize(20);float arabic=joining.measureText("سلام");joining.setLetterSpacing(.2f);near(joining.measureText("سلام"),arabic);
  Bitmap a=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);try{new Canvas(a).drawText("A",10,25,base);new Canvas(b).drawText("A",10,25,spaced);boolean ink=false;for(int y=0;y<40;y++)for(int x=0;x<78;x++){check(a.getPixel(x,y)==b.getPixel(x+2,y),"Half-spacing shifts glyph pixels");if(a.getPixel(x,y)!=0)ink=true;}check(ink,"Glyph rendered");}finally{a.recycle();b.recycle();}
  spaced.setLetterSpacing(-.2f);near(spaced.measureText("ABC")-base.measureText("ABC"),-12);Paint copy=new Paint(spaced);near(copy.getLetterSpacing(),-.2f);copy.reset();near(copy.getLetterSpacing(),0);
  try{spaced.setLetterSpacing(Float.NaN);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}near(spaced.getLetterSpacing(),-.2f);
  System.out.println("SPACING_METRICS_PIXELS_OK");
 }
 public static void xml(android.content.Context context){android.widget.TextView view=(android.widget.TextView)android.view.LayoutInflater.from(context).inflate(context.getResources().getIdentifier("spacing","layout","org.shiraz.fixture"),null);near(view.getLetterSpacing(),.2f);int spec=android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED);view.measure(spec,spec);int before=view.getMeasuredWidth();view.layout(0,0,before,view.getMeasuredHeight());view.setLetterSpacing(.5f);check(view.isLayoutRequested(),"Spacing requests relayout");view.measure(spec,spec);check(view.getMeasuredWidth()>before,"TextView width follows spacing");}
}
