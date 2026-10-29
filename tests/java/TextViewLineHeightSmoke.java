// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;import android.content.res.*;import android.graphics.*;import android.text.*;import android.text.style.*;import android.view.*;import android.widget.*;
public final class TextViewLineHeightSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static final class Height implements LineHeightSpan.WithDensity {
  int height,calls;Height(int h){height=h;}
  public void chooseHeight(CharSequence s,int start,int end,int origin,int y,Paint.FontMetricsInt f){throw new AssertionError("density overload missing");}
  public void chooseHeight(CharSequence s,int start,int end,int origin,int y,Paint.FontMetricsInt f,TextPaint p){calls++;f.ascent=5-height;f.descent=5;if(start==0)f.top=f.ascent;if(end==s.length())f.bottom=f.descent;}
 }
 static void layout(TextView v){v.measure(View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());}
 public static void main(String[] args){
  android.os.Looper.prepare();Context c=new ContextWrapper(null){public Resources getResources(){return Resources.getSystem();}public Object getSystemService(String name){return null;}};
  TextView v=new TextView(c);v.setPadding(0,0,0,0);v.setIncludeFontPadding(false);v.setTextSize(16);v.setTextColor(Color.RED);
  SpannableString s=new SpannableString("M\nM\nM");Height first=new Height(20),middle=new Height(60),last=new Height(20);
  s.setSpan(first,0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(middle,2,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(last,4,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);v.setText(s,TextView.BufferType.SPANNABLE);layout(v);
  Layout l=v.getLayout();check(v.getMeasuredHeight()==100&&l.getLineTop(1)==20&&l.getLineTop(2)==80&&l.getLineBaseline(1)==75,"per-line height and baseline");
  check(v.getOffsetForPosition(1,50)==2,"hit testing uses tall middle line");
  Bitmap pixels=Bitmap.createBitmap(120,100,Bitmap.Config.ARGB_8888);v.draw(new Canvas(pixels));boolean middleInk=false;for(int y=60;y<80;y++)for(int x=0;x<30;x++)middleInk|=(pixels.getPixel(x,y)>>>24)!=0;check(middleInk,"middle glyph uses custom baseline");for(int y=30;y<50;y++)for(int x=0;x<30;x++)check(pixels.getPixel(x,y)==0,"tall line spacing remains empty");pixels.recycle();
  Spannable live=(Spannable)v.getText();live.removeSpan(middle);middle.height=30;live.setSpan(middle,2,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);layout(v);check(v.getMeasuredHeight()==70&&v.getLayout().getLineTop(2)==50,"span mutation reflows");check(first.calls>0&&middle.calls>0&&last.calls>0,"density callbacks delivered");
  v.setEllipsize(TextUtils.TruncateAt.END);layout(v);
  check(v.getMeasuredHeight()==70&&v.getLayout().getLineTop(2)==50,"non-overflowing ellipsis keeps span heights");
  SpannableString longText=new SpannableString("A long first line that needs truncation and more words");
  Height tall=new Height(45);longText.setSpan(tall,0,longText.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  v.setMaxLines(1);v.setText(longText,TextView.BufferType.SPANNABLE);layout(v);
  check(v.getMeasuredHeight()==45&&v.getLayout().getLineCount()==1&&tall.calls>0,"ellipsized line uses source height span");
  Bitmap clipped=Bitmap.createBitmap(120,45,Bitmap.Config.ARGB_8888);v.draw(new Canvas(clipped));
  boolean ink=false;for(int y=0;y<45;y++)for(int x=0;x<120;x++)ink|=(clipped.getPixel(x,y)>>>24)!=0;
  check(ink,"ellipsized custom-height text paints");clipped.recycle();
  System.out.println("TEXTVIEW_LINE_HEIGHT_PIXELS_HIT_OK");
 }
}
