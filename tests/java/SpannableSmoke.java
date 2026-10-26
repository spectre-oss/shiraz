// SPDX-License-Identifier: GPL-3.0-only
import android.text.*;
public final class SpannableSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  for(Layout.Alignment alignment:Layout.Alignment.values()){
   android.text.style.AlignmentSpan.Standard span=new android.text.style.AlignmentSpan.Standard(alignment);
   android.os.Parcel parcel=android.os.Parcel.obtain();span.writeToParcel(parcel,0);parcel.setDataPosition(0);
   check(new android.text.style.AlignmentSpan.Standard(parcel).getAlignment()==alignment,"alignment parcel roundtrip");parcel.recycle();
   SpannableString paragraph=new SpannableString("paragraph\n");paragraph.setSpan(span,0,paragraph.length(),Spanned.SPAN_PARAGRAPH);
   check(paragraph.getSpans(0,paragraph.length(),android.text.style.AlignmentSpan.class)[0].getAlignment()==alignment,"alignment interface query");
  }
  TextPaint subPaint=new TextPaint();subPaint.setTextSize(20);int shift=-(int)(subPaint.ascent()/2);subPaint.baselineShift=3;
  new android.text.style.SubscriptSpan().updateMeasureState(subPaint);check(subPaint.baselineShift==3+shift&&shift>0,"subscript baseline");
  SpannableString rich=new SpannableString("A large bold description with more words");rich.setSpan(new android.text.style.RelativeSizeSpan(2),0,rich.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  TextPaint ellipsisPaint=new TextPaint();ellipsisPaint.setTextSize(16);
  StaticLayout clipped=StaticLayout.Builder.obtain(rich,0,rich.length(),ellipsisPaint,100).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
  check(clipped.getEllipsisCount(1)>0&&clipped.getLineWidth(1)<=100,"styled ellipsis fits");
  clipped.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(100,150,android.graphics.Bitmap.Config.ARGB_8888)));
  StaticLayout selected=StaticLayout.Builder.obtain("link text",0,9,ellipsisPaint,200).build();android.graphics.Path selection=new android.graphics.Path();selected.getSelectionPath(0,4,selection);android.graphics.RectF selectionBounds=new android.graphics.RectF();selection.computeBounds(selectionBounds,true);check(selectionBounds.width()>0&&selectionBounds.height()>0,"link selection bounds");selected.getSelectionPath(2,2,selection);check(selection.isEmpty(),"empty selection clears path");
  android.graphics.Path original=new android.graphics.Path();original.addRect(1,2,5,6,android.graphics.Path.Direction.CW);android.graphics.Path translated=new android.graphics.Path();translated.addPath(original,10,20);translated.computeBounds(selectionBounds,true);check(selectionBounds.left==11&&selectionBounds.top==22,"translated selection path");original.computeBounds(selectionBounds,true);check(selectionBounds.left==1,"source selection unchanged");
  check(original.isConvex(),"rectangle convex");android.graphics.Path compound=new android.graphics.Path(original);compound.addPath(translated);check(!compound.isConvex(),"compound not convex");android.graphics.Outline outline=new android.graphics.Outline();outline.setConvexPath(original);outline.offset(2,3);check(!outline.isEmpty()&&outline.canClip(),"path outline");
  final SpannableString text=new SpannableString("terms of service");final StringBuilder events=new StringBuilder();
  SpanWatcher watcher=new SpanWatcher(){public void onSpanAdded(Spannable s,Object what,int a,int b){check(s==text,"watcher receiver");events.append('A');}public void onSpanChanged(Spannable s,Object what,int a,int b,int c,int d){events.append('C');}public void onSpanRemoved(Spannable s,Object what,int a,int b){events.append('R');}};
  text.setSpan(watcher,0,text.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);events.setLength(0);Object link=new Object();text.setSpan(link,0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(link,9,16,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.removeSpan(link);check(events.toString().equals("ACR"),"span watcher lifecycle");
  text.removeSpan(watcher);text.setSpan(link,0,5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);SpannableString copy=new SpannableString(text);copy.removeSpan(link);check(text.getSpanStart(link)==0&&copy.getSpanStart(link)==-1,"independent span storage");
  Spanned slice=(Spanned)text.subSequence(2,7);check(slice.toString().equals("rms o")&&slice.getSpanStart(link)==0&&slice.getSpanEnd(link)==3,"clipped subsequence");
  SpannedString frozen=new SpannedString(text);text.removeSpan(link);check(frozen.getSpanStart(link)==0&&!((Object)frozen instanceof Spannable),"immutable span snapshot");
  check(SpannableString.valueOf(copy)==copy&&SpannedString.valueOf(frozen)==frozen,"valueOf identity");check(Spannable.Factory.getInstance().newSpannable("text") instanceof SpannableString,"factory type");
  try{copy.setSpan(link,0,100,0);throw new AssertionError("span range");}catch(IndexOutOfBoundsException expected){}
  SpannableString links=new SpannableString("see https://osmand.net/x, mail a@b.org");check(android.text.util.Linkify.addLinks(links,android.text.util.Linkify.WEB_URLS|android.text.util.Linkify.EMAIL_ADDRESSES),"linkify found");
  android.text.style.URLSpan[] urls=links.getSpans(0,links.length(),android.text.style.URLSpan.class);check(urls.length==2&&urls[0].getURL().equals("https://osmand.net/x")&&urls[1].getURL().equals("mailto:a@b.org"),"linkify urls");
 android.text.TextPaint tp=new android.text.TextPaint();tp.setTextSize(20);new android.text.style.UnderlineSpan().updateDrawState(tp);new android.text.style.StrikethroughSpan().updateDrawState(tp);new android.text.style.BackgroundColorSpan(0xff112233).updateDrawState(tp);new android.text.style.RelativeSizeSpan(0.5f).updateMeasureState(tp);check(tp.isUnderlineText()&&tp.isStrikeThruText()&&tp.bgColor==0xff112233&&tp.getTextSize()==10f,"simple spans");
 android.graphics.drawable.ColorDrawable swatch=new android.graphics.drawable.ColorDrawable(0xff00ff00);swatch.setBounds(0,0,12,8);android.text.style.ImageSpan image=new android.text.style.ImageSpan(swatch);android.graphics.Paint.FontMetricsInt fm=new android.graphics.Paint.FontMetricsInt();check(image.getSize(new android.graphics.Paint(),"x",0,1,fm)==12&&fm.ascent==-8&&image.getDrawable()==swatch,"image span size");
 check(new android.text.style.LeadingMarginSpan.Standard(10,4).getLeadingMargin(true)==10&&new android.text.style.LeadingMarginSpan.Standard(10,4).getLeadingMargin(false)==4&&new android.text.style.BulletSpan(6).getLeadingMargin(true)==14,"leading margins");
 check(android.text.BidiFormatter.getInstance().unicodeWrap("abc").equals("abc")&&android.text.BidiFormatter.getInstance().unicodeWrap("\u05d0\u05d1").equals("\u202b\u05d0\u05d1\u202c\u200e"),"bidi wrap");
 android.text.Spanned html=android.text.Html.fromHtml("<p>Hafez &amp; <b>Saadi</b><br/>see <a href=\"https://osmand.net/a?x=1&amp;y=2\">site</a> &#65;<script>bad()</script></p><i>end");check(html.toString().equals("Hafez & Saadi\nsee site A\nend"),"html text "+html);
 android.text.style.StyleSpan[] styles=html.getSpans(0,html.length(),android.text.style.StyleSpan.class);check(styles.length==2&&styles[0].getStyle()==1&&html.getSpanStart(styles[0])==8&&html.getSpanEnd(styles[0])==13,"html bold");
 android.text.style.URLSpan[] htmlUrls=html.getSpans(0,html.length(),android.text.style.URLSpan.class);check(htmlUrls.length==1&&htmlUrls[0].getURL().equals("https://osmand.net/a?x=1&y=2")&&html.toString().substring(html.getSpanStart(htmlUrls[0]),html.getSpanEnd(htmlUrls[0])).equals("site"),"html link");check(android.text.Html.fromHtml(null).length()==0&&android.text.Html.fromHtml("a < b & c").toString().equals("a < b & c"),"html malformed");
  System.out.println("SPANNABLE_STRING_OK");
 }
}
