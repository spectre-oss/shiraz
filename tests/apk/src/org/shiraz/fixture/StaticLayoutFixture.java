// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.text.*;
import android.graphics.*;
public final class StaticLayoutFixture {
 private static void check(boolean ok){if(!ok)throw new AssertionError("Static layout");}
 private static boolean tightGeometry(TextPaint paint){StaticLayout l=StaticLayout.Builder.obtain("W",0,1,paint,0).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).build();return l.getLineLeft(0)<0&&l.getLineRight(0)==0;}
 public static void check(){
  TextPaint paint=new TextPaint();paint.setTextSize(20);paint.setColor(0xff000000);
  String text="Vienna Vienna Vienna";int width=(int)Math.ceil(paint.measureText("Vienna "));
  StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(false).build();
  check(layout.getLineCount()==3&&layout.getLineStart(1)==7&&layout.getLineEnd(2)==text.length());
  for(int frequency=0;frequency<=4;frequency++){StaticLayout simple=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(frequency).build();check(simple.getLineCount()==layout.getLineCount());for(int line=0;line<layout.getLineCount();line++)check(simple.getLineStart(line)==layout.getLineStart(line));}
  for(int i=0;i<3;i++){check(layout.getLineWidth(i)<=width);check(layout.getLineBaseline(i)>layout.getLineTop(i));if(i>0)check(layout.getLineBaseline(i)>layout.getLineBaseline(i-1));}
  check(layout.getLineVisibleEnd(0)==6&&layout.getLineMax(0)<layout.getLineWidth(0));
  for(Layout.Alignment alignment:Layout.Alignment.values()){
   StaticLayout aligned=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setAlignment(alignment).build();
   float extent=aligned.getLineMax(0);
   if(alignment==Layout.Alignment.ALIGN_NORMAL)check(aligned.getLineLeft(0)==0&&aligned.getLineRight(0)==extent);
   if(alignment==Layout.Alignment.ALIGN_OPPOSITE)check(aligned.getLineLeft(0)==width-extent&&aligned.getLineRight(0)==width);
   if(alignment==Layout.Alignment.ALIGN_CENTER)check(aligned.getLineLeft(0)==(width-(((int)extent)&~1))/2&&aligned.getLineRight(0)==width-aligned.getLineLeft(0));
  }
  check(tightGeometry(paint));
  Bitmap bitmap=Bitmap.createBitmap(width,layout.getHeight(),Bitmap.Config.ARGB_8888);layout.draw(new Canvas(bitmap));
  for(int i=0;i<3;i++){boolean ink=false;for(int y=layout.getLineTop(i);y<layout.getLineBottom(i);y++)for(int x=0;x<width;x++)ink|=(bitmap.getPixel(x,y)>>>24)!=0;check(ink);}
  StaticLayout empty=StaticLayout.Builder.obtain("\n",0,1,paint,100).build();check(empty.getLineCount()==2&&empty.getLineStart(1)==1);
  StaticLayout slice=StaticLayout.Builder.obtain("xViennay",1,7,paint,200).build();check(slice.getLineStart(0)==1&&slice.getLineEnd(0)==7);
  check(StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setMaxLines(2).build().getLineCount()==2);
  StaticLayout tight=StaticLayout.Builder.obtain("a\u0301b",0,3,paint,0).build();check(tight.getLineCount()==2&&tight.getLineStart(1)==2);
  SpannableString styled=new SpannableString("a\nb");styled.setSpan(new android.text.style.MetricAffectingSpan(){public void updateMeasureState(TextPaint p){p.setTextSize(40);}public void updateDrawState(TextPaint p){p.setTextSize(40);}},2,3,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout large=StaticLayout.Builder.obtain(styled,0,3,paint,200).setIncludePad(false).build();check(large.getLineBottom(1)-large.getLineTop(1)>large.getLineBottom(0));check(paint.getTextSize()==20);
  check(StaticLayout.Builder.obtain("a\tb",0,3,paint,200).build().getLineContainsTab(0));
  try{StaticLayout.Builder.obtain("abc",0,4,paint,20);throw new AssertionError("Invalid slice");}catch(IllegalArgumentException expected){}
  StaticLayout clipped=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(width).build();
  check(clipped.getLineCount()==2&&clipped.getLineEnd(1)==text.length()&&clipped.getEllipsisCount(1)>0&&clipped.getEllipsisCount(0)==0);
  check(clipped.getLineWidth(1)<=width+0.01f&&clipped.getEllipsizedWidth()==width);
  Bitmap clippedBitmap=Bitmap.createBitmap(width,clipped.getHeight(),Bitmap.Config.ARGB_8888);clipped.draw(new Canvas(clippedBitmap));
  boolean clippedInk=false;for(int y=clipped.getLineTop(1);y<clipped.getHeight();y++)for(int x=0;x<width;x++)clippedInk|=(clippedBitmap.getPixel(x,y)>>>24)!=0;check(clippedInk);
  check(StaticLayout.Builder.obtain("a",0,1,paint,200).setEllipsize(TextUtils.TruncateAt.END).build().getEllipsisCount(0)==0);
  StaticLayout middle=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.MIDDLE).build();check(middle.getEllipsisStart(0)>0&&middle.getEllipsisCount(0)>0);
  StaticLayout paragraphs=StaticLayout.Builder.obtain("a\nb",0,3,paint,200).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build();
  check(paragraphs.getEllipsisStart(0)==1&&paragraphs.getEllipsisCount(0)==2);
  check(Math.abs(paragraphs.getLineWidth(0)-paint.measureText("a\u2026"))<0.01f);
  final int[] callbacks={0};SpannableString heights=new SpannableString("a\nb");
  heights.setSpan(new android.text.style.LineHeightSpan.WithDensity(){
   public void chooseHeight(CharSequence t,int start,int end,int spanY,int y,Paint.FontMetricsInt fm){throw new AssertionError("Density callback bypassed");}
   public void chooseHeight(CharSequence t,int start,int end,int spanY,int y,Paint.FontMetricsInt fm,TextPaint p){check(p==paint&&spanY==0&&y==callbacks[0]*40);callbacks[0]++;fm.top=fm.ascent=-30;fm.bottom=fm.descent=10;}
  },0,3,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout fixed=StaticLayout.Builder.obtain(heights,0,3,paint,200).setIncludePad(false).build();
  check(callbacks[0]==2&&fixed.getHeight()==80&&fixed.getLineBaseline(0)==30&&fixed.getLineBaseline(1)==70);
  check(fixed.getLineAscent(0)==-30&&fixed.getLineAscent(1)==-30);
  final int[] plainCalls={0};SpannableString later=new SpannableString("a\nb\nc");
  later.setSpan(new android.text.style.LineHeightSpan(){public void chooseHeight(CharSequence t,int start,int end,int spanY,int y,Paint.FontMetricsInt fm){check(start>=2&&spanY>0);plainCalls[0]++;fm.top=fm.ascent=-20;fm.bottom=fm.descent=10;}},2,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout laterLayout=StaticLayout.Builder.obtain(later,0,5,paint,200).setIncludePad(false).build();check(plainCalls[0]==2&&laterLayout.getLineBottom(1)-laterLayout.getLineTop(1)==30);
  final int[] replacements={0};SpannableString inline=new SpannableString("aX Xb");
  inline.setSpan(new android.text.style.ReplacementSpan(){
   public int getSize(Paint p,CharSequence t,int start,int end,Paint.FontMetricsInt fm){check(start==1&&end==4);if(fm!=null){fm.top=fm.ascent=-30;fm.bottom=fm.descent=10;}return 40;}
   public void draw(Canvas c,CharSequence t,int start,int end,float x,int top,int baseline,int bottom,Paint p){check(start==1&&end==4&&baseline-top>=30&&bottom-baseline>=10);replacements[0]++;Paint block=new Paint();block.setColor(Color.RED);c.drawRect(x,baseline-30,x+40,baseline+10,block);}
  },1,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  check(Math.abs(Layout.getDesiredWidth(inline,paint)-(paint.measureText("a")+40+paint.measureText("b")))<.01f);
  StaticLayout replaced=StaticLayout.Builder.obtain(inline,0,5,paint,45).setIncludePad(false).build();
  check(replaced.getLineCount()==3&&replaced.getLineStart(1)==1&&replaced.getLineStart(2)==4&&replaced.getLineWidth(1)==40);
  Bitmap replacementBitmap=Bitmap.createBitmap(45,replaced.getHeight(),Bitmap.Config.ARGB_8888);replaced.draw(new Canvas(replacementBitmap));
  check(replacements[0]==1&&replacementBitmap.getPixel(20,replaced.getLineBaseline(1))==Color.RED);
  try{StaticLayout.Builder.obtain(inline,2,5,paint,45).build();throw new AssertionError("Partial replacement accepted");}catch(UnsupportedOperationException expected){}
  SpannableString spaces=new SpannableString("  x");spaces.setSpan(new android.text.style.ReplacementSpan(){public int getSize(Paint p,CharSequence t,int start,int end,Paint.FontMetricsInt fm){return 40;}public void draw(Canvas c,CharSequence t,int start,int end,float x,int top,int baseline,int bottom,Paint p){}},0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout spaceReplacement=StaticLayout.Builder.obtain(spaces,0,3,paint,40).build();check(spaceReplacement.getLineCount()==2&&spaceReplacement.getLineMax(0)==40&&spaceReplacement.getLineVisibleEnd(0)==2);
  final int[] margins={0};
  android.text.style.LeadingMarginSpan callback=new android.text.style.LeadingMarginSpan(){
   public int getLeadingMargin(boolean first){return 0;}
   public void drawLeadingMargin(Canvas c,Paint p,int x,int dir,int top,int baseline,int bottom,CharSequence t,int start,int end,boolean first,Layout l){
    check(x==0&&dir==1&&baseline==l.getLineBaseline(l.getLineForOffset(start)));margins[0]++;
    Paint marker=new Paint();marker.setColor(Color.RED);c.drawRect(0,top,2,top+2,marker);c.translate(10,0);
   }
  };
  SpannableString marginText=new SpannableString("abc\ndef");marginText.setSpan(callback,0,marginText.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout marginLayout=StaticLayout.Builder.obtain(marginText,0,marginText.length(),paint,160).build();
  Bitmap marginBitmap=Bitmap.createBitmap(180,marginLayout.getHeight(),Bitmap.Config.ARGB_8888);Canvas marginCanvas=new Canvas(marginBitmap);marginLayout.draw(marginCanvas);
  check(margins[0]==2&&marginBitmap.getPixel(0,marginLayout.getLineTop(1))==Color.RED);
  Paint blue=new Paint();blue.setColor(Color.BLUE);marginCanvas.drawRect(0,0,2,2,blue);check(marginBitmap.getPixel(0,0)==Color.BLUE);
  SpannableString marginClipped=new SpannableString("long colored ellipsis text");marginClipped.setSpan(callback,0,marginClipped.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);marginClipped.setSpan(new android.text.style.ForegroundColorSpan(Color.BLUE),0,marginClipped.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  StaticLayout marginEllipsis=StaticLayout.Builder.obtain(marginClipped,0,marginClipped.length(),paint,70).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build();
  marginEllipsis.draw(new Canvas(Bitmap.createBitmap(90,marginEllipsis.getHeight(),Bitmap.Config.ARGB_8888)));check(margins[0]==3);
  System.out.println("STATIC_LAYOUT_WRAP_METRICS_PIXELS_OK");
 }
}
