// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import android.text.*;
import shiraz.runtime.*;
public final class BidiTextSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void near(float a,float b,String message){check(Math.abs(a-b)<.1f,message+": "+a+" != "+b);}
 static void shape(String text,TextPaint paint){
  TextGeometry geometry=new TextGeometry(text,paint);float total=0;int[] coverage=new int[text.length()];
  for(int i=0;i<geometry.clusters.length;i+=5){float[] c=geometry.clusters;check(c[i+3]>=c[i+2],"nonnegative advance");total+=c[i+3]-c[i+2];for(int at=(int)c[i];at<(int)c[i+1];at++)coverage[at]++;}
  for(int n:coverage)check(n==1,"logical cluster coverage");near(total,paint.measureText(text),"shared measure/geometry");
  float[] measured={0};check(paint.breakText(text,true,total+.1f,measured)==text.length(),"whole break");near(measured[0],total,"shared break width");
  Bitmap bitmap=Bitmap.createBitmap(700,150,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawText(text,20,90,paint);
  Rect bounds=new Rect();paint.getTextBounds(text,0,text.length(),bounds);boolean ink=false;
  for(int y=0;y<150;y++)for(int x=0;x<700;x++)if(bitmap.getPixel(x,y)!=0){ink=true;check(x>=20+bounds.left&&x<20+bounds.right&&y>=90+bounds.top&&y<90+bounds.bottom,"draw inside measured ink bounds");}
  check(ink,"visible glyph coverage");
 }
 static void uniformStyledView(){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  String value="Hafez حافظ ۱۲۳ (Shiraz)";
  for(int editor=0;editor<2;editor++)for(int width:new int[]{500,130}){
   android.widget.TextView styled=editor==0?new android.widget.TextView(context):new android.widget.EditText(context),reference=editor==0?new android.widget.TextView(context):new android.widget.EditText(context);
   styled.setTextSize(24);reference.setTextSize(30);reference.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
   SpannableString text=new SpannableString(value);text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(new android.text.style.AbsoluteSizeSpan(30),0,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   SpannableString colors=new SpannableString(value);text.setSpan(new android.text.style.ForegroundColorSpan(0xff00aa00),6,10,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);colors.setSpan(new android.text.style.ForegroundColorSpan(0xff00aa00),6,10,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setText(text);reference.setText(colors);if(editor!=0){styled.requestFocus();reference.requestFocus();((android.widget.EditText)styled).setSelection(2,10);((android.widget.EditText)reference).setSelection(2,10);}
   int w=android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),h=android.view.View.MeasureSpec.makeMeasureSpec(400,android.view.View.MeasureSpec.AT_MOST);styled.measure(w,h);reference.measure(w,h);check(styled.getMeasuredHeight()==reference.getMeasuredHeight(),"uniform metric wrapped height");int height=styled.getMeasuredHeight();styled.layout(0,0,width,height);reference.layout(0,0,width,height);
   Bitmap a=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);styled.draw(new Canvas(a));reference.draw(new Canvas(b));for(int y=0;y<height;y++)for(int x=0;x<width;x++)check(a.getPixel(x,y)==b.getPixel(x,y),"uniform styled bidi pixels preserve ordering and joining");
   for(int y=0;y<height;y+=9)for(int x=0;x<width;x+=7)check(styled.getOffsetForPosition(x,y)==reference.getOffsetForPosition(x,y),"uniform styled bidi hit mapping");a.recycle();b.recycle();
  }
 }
 public static void main(String[] args){
  uniformStyledView();
  TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(30);
  shape("Hafez حافظ ۱۲۳ (Shiraz)",paint);shape("abc אבג 123 (xyz)",paint);shape("حَافِظ",paint);shape("A😀B",paint);
  // U+10FFFF has no assigned glyph. Missing coverage must render the font's
  // .notdef outline consistently across measure/bounds/draw, not kill map rendering.
  String missing="\udbff\udfff";shape(missing,paint);shape("Map "+missing+" حافظ",paint);
  check(paint.measureText(missing)>0,"missing-glyph advance retained");
  Path missingPath=new Path();missingPath.moveTo(10,50);missingPath.lineTo(150,50);
  Bitmap missingBitmap=Bitmap.createBitmap(180,80,Bitmap.Config.ARGB_8888);
  new Canvas(missingBitmap).drawTextOnPath(missing,missingPath,0,0,paint);
  boolean missingInk=false;for(int y=0;y<80;y++)for(int x=0;x<180;x++)if(missingBitmap.getPixel(x,y)!=0)missingInk=true;
  check(missingInk,"missing-glyph path outline retained");missingBitmap.recycle();
  try{paint.measureText("\ud800");throw new AssertionError("malformed text accepted");}catch(IllegalArgumentException expected){}
  TextGeometry hebrew=new TextGeometry("אבג",paint);near(hebrew.caret(0),paint.measureText("אבג"),"RTL start caret");near(hebrew.caret(3),0,"RTL end caret");check(hebrew.hit(0)==3,"RTL left hit");
  TextGeometry combined=new TextGeometry("a\u0301",paint);check(combined.ranges(1,1).length==0,"collapsed combining selection");check(combined.boundaries().size()==2,"combining cluster");
  String mixed="abc אבג";TextGeometry geometry=new TextGeometry(mixed,paint);near(geometry.caret(mixed.length()),paint.measureText(mixed),"LTR paragraph trailing RTL primary edge");check(geometry.ranges(1,5).length>=4,"logical selection has disjoint visual ranges");
  String paragraph="אבג abc 123 xyz";java.text.Bidi bidi=new java.text.Bidi(paragraph,java.text.Bidi.DIRECTION_RIGHT_TO_LEFT);String tail=paragraph.substring(4);byte[] levels=TextGeometry.levels(bidi,4,paragraph.length());paint.shirazSetBidiLevels(levels);TextGeometry contextual=new TextGeometry(tail,paint,1);near(contextual.caret(0),paint.measureText(tail),"wrapped RTL paragraph primary start");shape(tail,paint);paint.reset();paint.setTextSize(30);
  paint.shirazSetBidiLevels(new byte[]{0,1});try{paint.measureText("😀");throw new AssertionError("split surrogate");}catch(IllegalArgumentException expected){}paint.reset();
  paint.shirazSetBidiLevels(new byte[]{0});try{paint.measureText("");throw new AssertionError("empty levels mismatch");}catch(IllegalArgumentException expected){}paint.reset();paint.setTextSize(30);
  String nested="a\u2067אב 12\u2069 z";shape(nested,paint);
  StaticLayout layout=StaticLayout.Builder.obtain(paragraph,0,paragraph.length(),paint,110).setTextDirection(TextDirectionHeuristics.RTL).build();check(layout.getLineCount()>1,"wrapped paragraph");for(int i=0;i<layout.getLineCount();i++)check(layout.getParagraphDirection(i)==-1,"paragraph base retained");layout.draw(new Canvas(Bitmap.createBitmap(150,300,Bitmap.Config.ARGB_8888)));
  String cut=TextUtils.ellipsize("حافظ شیراز ۱۲۳",paint,80,TextUtils.TruncateAt.END).toString();check(paint.measureText(cut)<=80,"RTL ellipsis fits");
  check(BoringLayout.isBoring("אבג",paint)==null,"RTL remains non-boring");
  System.out.println("BIDI_FALLBACK_GEOMETRY_OK");
 }
}
