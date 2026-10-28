// SPDX-License-Identifier: GPL-3.0-only
import android.text.*;
import android.graphics.Typeface;
public class StaticBreakSmoke {
 public static void main(String[] args){
  TextPaint paint=new TextPaint();paint.setTextSize(20);paint.setTypeface(Typeface.MONOSPACE);
  String text="aaa bbb ccc ddd";int width=(int)Math.ceil(paint.measureText("aaa bbb ccc"));
  StaticLayout quality=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setBreakStrategy(1).build();
  StaticLayout balanced=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setBreakStrategy(2).build();
  if(quality.getLineCount()!=2||quality.getLineStart(1)!=12)throw new AssertionError("quality");
  if(balanced.getLineCount()!=2||balanced.getLineStart(1)!=8)throw new AssertionError("balanced");
  String combining="a\u0301b\u0301";
  StaticLayout narrow=StaticLayout.Builder.obtain(combining,0,combining.length(),paint,0).setBreakStrategy(1).build();
  if(narrow.getLineCount()!=2||narrow.getLineStart(1)!=2)throw new AssertionError("cluster integrity");
  StaticLayout sliced=StaticLayout.Builder.obtain("XX"+text+"\nend",2,text.length()+6,paint,width).setBreakStrategy(1).build();
  if(sliced.getLineStart(0)!=2||sliced.getLineCount()!=3)throw new AssertionError("slice/paragraph boundaries");
  try{StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setBreakStrategy(3);throw new AssertionError("invalid mode");}catch(IllegalArgumentException expected){}
  System.out.println("STATIC_PARAGRAPH_BREAKS_OK");
 }
}
