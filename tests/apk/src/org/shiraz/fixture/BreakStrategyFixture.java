// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;
public final class BreakStrategyFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static int[] pixels(TextView text,int width){text.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));text.layout(0,0,width,180);Bitmap image=Bitmap.createBitmap(width,180,Bitmap.Config.ARGB_8888);try{text.draw(new Canvas(image));int[] result=new int[width*180];image.getPixels(result,0,width,0,0,width,180);return result;}finally{image.recycle();}}
 public static void check(Context context){
  TextView text=new TextView(context),expected=new TextView(context);for(TextView view:new TextView[]{text,expected}){view.setTextSize(TypedValue.COMPLEX_UNIT_PX,20);view.setTypeface(Typeface.MONOSPACE);}
  int width=(int)Math.ceil(text.getPaint().measureText("aaa bbb ccc"));text.setText("aaa bbb ccc ddd");text.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);expected.setText("aaa bbb ccc\nddd");int[] quality=pixels(text,width);check(java.util.Arrays.equals(quality,pixels(expected,width)),"Quality paragraph keeps full first line");
  text.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);expected.setText("aaa bbb\nccc ddd");int[] balanced=pixels(text,width);check(java.util.Arrays.equals(balanced,pixels(expected,width))&&!java.util.Arrays.equals(quality,balanced),"Balanced paragraph redistributes words");
  text.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);check(java.util.Arrays.equals(quality,pixels(text,width)),"Strategy setter invalidates layout");check(text.getText().toString().equals("aaa bbb ccc ddd"),"Source remains unchanged");
  text.setText("aaa bb cc ddddd");expected.setText("aaa\nbb cc\nddddd");int narrow=(int)Math.ceil(text.getPaint().measureText("aaa bb"));check(java.util.Arrays.equals(pixels(text,narrow),pixels(expected,narrow)),"Quality optimizes across word boundaries");
  text.setText("a\u0301b\u0301");text.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));check(text.getLineCount()==2,"Emergency breaks retain combining sequences");
  text.setText("aaa bbb ccc ddd");pixels(text,300);check(text.getLineCount()==1,"Wide paragraph fits");pixels(text,width);check(text.getLineCount()==2,"Resize restores paragraph wrapping");
  text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);check(text.getHyphenationFrequency()==Layout.HYPHENATION_FREQUENCY_NONE,"No-hyphenation policy");text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);check(text.getHyphenationFrequency()==Layout.HYPHENATION_FREQUENCY_NORMAL,"Hyphenation policy retained");text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
  try{text.setBreakStrategy(3);throw new AssertionError("Invalid strategy accepted");}catch(IllegalArgumentException expectedFailure){}check(text.getBreakStrategy()==Layout.BREAK_STRATEGY_HIGH_QUALITY,"Invalid strategy leaves policy intact");
  int id=context.getResources().getIdentifier("break_strategy_label","layout","org.shiraz.fixture");TextView xml=(TextView)LayoutInflater.from(context).inflate(id,null);check(xml.getBreakStrategy()==Layout.BREAK_STRATEGY_HIGH_QUALITY&&xml.getHyphenationFrequency()==0,"Compiled break policy");pixels(xml,100);
  System.out.println("BREAK_STRATEGY_PARAGRAPH_PIXELS_OK");
 }
}
