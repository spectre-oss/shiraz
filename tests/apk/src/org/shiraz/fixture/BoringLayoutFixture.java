// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.text.*;
import android.graphics.*;
public final class BoringLayoutFixture {
 private static void check(boolean value){if(!value)throw new AssertionError("Boring layout");}
 public static void check(){
  TextPaint paint=new TextPaint();paint.setTextSize(24);paint.setColor(0xff000000);
  BoringLayout.Metrics metrics=new BoringLayout.Metrics();
  check(BoringLayout.isBoring("Vienna",paint,metrics)==metrics);
  check(metrics.width==(int)Math.ceil(paint.measureText("Vienna")));
  check(BoringLayout.isBoring("a\nb",paint)==null);check(BoringLayout.isBoring("a\tb",paint)==null);check(BoringLayout.isBoring("\u05d0",paint)==null);
  BoringLayout layout=BoringLayout.make("Vienna",paint,200,Layout.Alignment.ALIGN_CENTER,1,0,metrics,true);
  check(layout.getHeight()==metrics.bottom-metrics.top);check(layout.getLineBaseline(0)==-metrics.top);
  check(layout.getLineEnd(0)==6&&layout.getLineCount()==1&&layout.getParagraphDirection(0)==1);
  Bitmap bitmap=Bitmap.createBitmap(200,80,Bitmap.Config.ARGB_8888);layout.draw(new Canvas(bitmap));
  int min=200,max=-1;for(int y=0;y<80;y++)for(int x=0;x<200;x++)if((bitmap.getPixel(x,y)>>>24)!=0){min=Math.min(min,x);max=Math.max(max,x);}
  check(max>min&&min>40&&max<160);
  check(layout.replaceOrMake("Vienna",paint,200,Layout.Alignment.ALIGN_NORMAL,1,0,metrics,false)==layout);
  check(layout.getTopPadding()==0&&layout.getBottomPadding()==0&&layout.getLineBaseline(0)==-metrics.ascent);
  BoringLayout truncated=BoringLayout.make("Vienna",paint,200,Layout.Alignment.ALIGN_NORMAL,1,0,metrics,false,TextUtils.TruncateAt.END,metrics.width/2);
  check(truncated.getEllipsisCount(0)>0&&truncated.getText().length()==6&&truncated.getEllipsizedWidth()==metrics.width/2);
  check(truncated.getLineWidth(0)<=metrics.width/2+1);
  check(Math.abs(Layout.getDesiredWidth("Vienna\na",paint)-paint.measureText("Vienna"))<0.01f);
  check(Math.abs(Layout.getDesiredWidth("xViennay",1,7,paint)-paint.measureText("Vienna"))<0.01f);
  check(Layout.getDesiredWidth("",paint)==0);
  check(Math.abs(Layout.getDesiredWidth("\ta",paint)-(20+paint.measureText("a")))<0.01f);
  SpannableString styled=new SpannableString("ab");
  styled.setSpan(new android.text.style.MetricAffectingSpan(){public void updateMeasureState(TextPaint p){p.setTextSize(48);}public void updateDrawState(TextPaint p){p.setTextSize(48);}},1,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  TextPaint large=new TextPaint(paint);large.setTextSize(48);
  check(Math.abs(Layout.getDesiredWidth(styled,paint)-(paint.measureText("a")+large.measureText("b")))<0.01f);
  check(paint.getTextSize()==24);
  try{Layout.getDesiredWidth("a",0,2,paint);throw new AssertionError("Invalid slice");}catch(IndexOutOfBoundsException expected){}
  System.out.println("BORING_LAYOUT_METRICS_RENDER_OK");
 }
}
