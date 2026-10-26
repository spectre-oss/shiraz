// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import android.widget.TextView;
public final class EllipsisFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
 private static int[] pixels(TextView view,int width){layout(view,width,100);Bitmap image=Bitmap.createBitmap(width,100,Bitmap.Config.ARGB_8888);try{view.draw(new Canvas(image));int[] pixels=new int[width*100];image.getPixels(pixels,0,width,0,0,width,100);return pixels;}finally{image.recycle();}}
 public static void check(Context context){
  TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(20);float width=paint.measureText("ab\u2026")+.01f;
  check(TextUtils.ellipsize("abcdef",paint,width,TextUtils.TruncateAt.END).toString().equals("ab\u2026"),"End ellipsis");
  check(TextUtils.ellipsize("abcdef",paint,width,TextUtils.TruncateAt.START).toString().equals("\u2026ef"),"Start ellipsis");
  check(TextUtils.ellipsize("abcdef",paint,width,TextUtils.TruncateAt.MIDDLE).toString().equals("a\u2026f"),"Middle ellipsis");
  final int[] removed={-1,-1};CharSequence padded=TextUtils.ellipsize("abcdef",paint,width,TextUtils.TruncateAt.END,true,new TextUtils.EllipsizeCallback(){public void ellipsized(int start,int end){removed[0]=start;removed[1]=end;}});check(padded.toString().equals("ab\u2026\ufeff\ufeff\ufeff")&&removed[0]==2&&removed[1]==6,"Preserved offsets and callback");
  String fits="ab";check(TextUtils.ellipsize(fits,paint,width,TextUtils.TruncateAt.END)==fits,"Fits returns original");
  check(TextUtils.ellipsize("abcdef",paint,0,TextUtils.TruncateAt.END).length()==0,"Too narrow yields empty");
  check(TextUtils.ellipsize("abcdef",paint,0,TextUtils.TruncateAt.END,true,null).toString().equals("\ufeff\ufeff\ufeff\ufeff\ufeff\ufeff"),"Too narrow preserves offsets");
  String combined="a\u0301bcdef";String result=TextUtils.ellipsize(combined,paint,paint.measureText("a\u0301\u2026")+.01f,TextUtils.TruncateAt.END).toString();check(result.equals("a\u0301\u2026"),"Combining sequence retained");
  String emoji="\ud83d\ude00abcdef";result=TextUtils.ellipsize(emoji,paint,paint.measureText("\ud83d\ude00\u2026")+.01f,TextUtils.TruncateAt.END).toString();check(!result.endsWith("\ud83d\u2026")&&paint.measureText(result)<=paint.measureText("\ud83d\ude00\u2026")+.01f,"Supplementary character boundary and width");
  TextView label=new TextView(context),expected=new TextView(context);for(TextView view:new TextView[]{label,expected}){view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,20);view.setTypeface(Typeface.MONOSPACE);view.setSingleLine();}
  label.setText("abcdef");label.setEllipsize(TextUtils.TruncateAt.END);expected.setText("ab\u2026");int pixelsWidth=(int)Math.ceil(width);check(java.util.Arrays.equals(pixels(label,pixelsWidth),pixels(expected,pixelsWidth)),"Rendered ellipsis pixels");check(label.getText().toString().equals("abcdef"),"Source text preserved");
  expected.setText("abcdef");check(java.util.Arrays.equals(pixels(label,200),pixels(expected,200)),"Resize restores full label");
  label.setEllipsize(TextUtils.TruncateAt.START);expected.setText("\u2026ef");check(java.util.Arrays.equals(pixels(label,pixelsWidth),pixels(expected,pixelsWidth)),"Start renders suffix");
  label.setEllipsize(null);expected.setText("abcdef");check(java.util.Arrays.equals(pixels(label,pixelsWidth),pixels(expected,pixelsWidth)),"Clearing ellipsis restores clipping");
  label.setSingleLine(false);expected.setSingleLine(false);label.setMaxLines(1);expected.setMaxLines(1);label.setText("ab\ncd");label.setEllipsize(TextUtils.TruncateAt.END);expected.setText("ab\u2026");check(java.util.Arrays.equals(pixels(label,200),pixels(expected,200)),"Hidden line forces ellipsis");
  int id=context.getResources().getIdentifier("ellipsis_label","layout","org.shiraz.fixture");TextView xml=(TextView)android.view.LayoutInflater.from(context).inflate(id,null);check(xml.getMinLines()==1&&xml.getMaxLines()==1&&xml.getEllipsize()==TextUtils.TruncateAt.END&&xml.getText().toString().equals("abcdef"),"Compiled ellipsis attribute");
  label.setSingleLine();expected.setSingleLine();label.setText("abcdef");expected.setText("abcdef");label.setEllipsize(TextUtils.TruncateAt.MARQUEE);
  check(label.getEllipsize()==TextUtils.TruncateAt.MARQUEE&&java.util.Arrays.equals(pixels(label,200),pixels(expected,200)),"Inactive fitting marquee retains full text");
  label.setSelected(true);
  try{pixels(label,pixelsWidth);throw new AssertionError("Active overflowing marquee accepted");}catch(UnsupportedOperationException expectedFailure){}
  System.out.println("ELLIPSIS_TEXT_PIXELS_RESIZE_OK");
 }
}
