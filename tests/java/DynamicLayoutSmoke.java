// SPDX-License-Identifier: GPL-3.0-only
import android.text.*;
import android.text.style.RelativeSizeSpan;
public class DynamicLayoutSmoke {
 public static void main(String[] args){
  TextPaint paint=new TextPaint();paint.setTextSize(20);
  SpannableStringBuilder text=new SpannableStringBuilder("one");
  DynamicLayout layout=new DynamicLayout(text,paint,500,Layout.Alignment.ALIGN_NORMAL,1,0,false);
  if(layout.getLineCount()!=1)throw new AssertionError("initial");
  text.append("\ntwo");
  if(layout.getLineCount()!=2||layout.getLineStart(1)!=4||layout.getLineEnd(1)!=7)throw new AssertionError("append reflow");
  float width=layout.getLineWidth(0);
  RelativeSizeSpan span=new RelativeSizeSpan(2);text.setSpan(span,0,3,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  if(layout.getLineWidth(0)<width*1.9f)throw new AssertionError("span reflow");
  text.removeSpan(span);if(Math.abs(layout.getLineWidth(0)-width)>0.1f)throw new AssertionError("span removal");
  text.replace(0,text.length(),"a");
  if(layout.getLineCount()!=1||layout.getLineEnd(0)!=1)throw new AssertionError("delete reflow");
  text.clear();text.append("first\nsecond");
  if(layout.getLineCount()!=2||layout.getLineForOffset(7)!=1)throw new AssertionError("empty then edit");
  DynamicLayout narrow=new DynamicLayout(text,paint,20,Layout.Alignment.ALIGN_NORMAL,1,0,false);
  if(narrow.getLineCount()<=2)throw new AssertionError("width wrapping");
  text.replace(0,text.length(),"x");if(narrow.getLineCount()!=1||layout.getLineCount()!=1)throw new AssertionError("multiple layouts");
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(50,50,android.graphics.Bitmap.Config.ARGB_8888);
  layout.draw(new android.graphics.Canvas(bitmap));bitmap.recycle();
  System.out.println("DYNAMIC_LAYOUT_EDIT_REFLOW_OK");
 }
}
