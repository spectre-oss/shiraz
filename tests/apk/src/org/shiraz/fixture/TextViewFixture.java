// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;
public final class TextViewFixture {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static void layout(TextView view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
 private static Bitmap draw(TextView view,int width,int height){layout(view,width,height);Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(result));return result;}
 private static int count(Bitmap bitmap){int n=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(bitmap.getPixel(x,y)!=0)n++;return n;}
 private static void compounds(Context context){
  TextView v=new TextView(context);v.setText("");v.setPadding(2,3,4,5);v.setCompoundDrawablePadding(2);
  android.graphics.drawable.ColorDrawable red=new android.graphics.drawable.ColorDrawable(Color.RED);red.setBounds(0,0,6,8);
  v.setCompoundDrawablesRelative(red,null,null,null);check(v.getCompoundPaddingLeft()==10&&v.getCompoundPaddingRight()==4,"Start drawable reserves width");
  Bitmap pixels=draw(v,40,30);try{check(pixels.getPixel(2,10)==Color.RED&&pixels.getPixel(8,10)==0,"Compound pixels");}finally{pixels.recycle();}
  v.scrollTo(3,4);pixels=draw(v,40,30);try{check(pixels.getPixel(2,10)==Color.RED,"Compound icons remain stationary during text scrolling");}finally{pixels.recycle();}v.scrollTo(0,0);
  v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);check(v.getCompoundPaddingRight()==12&&v.getCompoundPaddingLeft()==2,"RTL moves relative icon");pixels=draw(v,40,30);try{check(pixels.getPixel(30,10)==Color.RED&&pixels.getPixel(2,10)==0,"RTL icon pixels");}finally{pixels.recycle();}
  v.setCompoundDrawables(null,null,null,null);check(red.getCallback()==null&&v.getCompoundPaddingRight()==4,"Removal releases callback and padding");
  Bitmap source=Bitmap.createBitmap(7,9,Bitmap.Config.ARGB_8888);try{android.graphics.drawable.BitmapDrawable icon=new android.graphics.drawable.BitmapDrawable(source);v.setCompoundDrawablesWithIntrinsicBounds(null,icon,null,null);check(icon.getBounds().width()==7&&v.getCompoundPaddingTop()==14,"Intrinsic top icon bounds");v.measure(0,0);check(v.getMeasuredWidth()>=13&&v.getMeasuredHeight()>=19,"Compound measurement");v.setCompoundDrawables(null,null,null,null);}finally{source.recycle();}
 }
 public static void check(Context context){compounds(context);
  TextView constrained=new TextView(context);constrained.setText("A long sentence that wraps into several lines");constrained.setMaxWidth(60);
  constrained.measure(0,0);check(constrained.getMeasuredWidth()==60&&constrained.getLineCount()>1,"Maximum width wraps text");
  constrained.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),0);check(constrained.getMeasuredWidth()==100,"Parent exact width wins");
  constrained.setText("x");constrained.setMinWidth(70);constrained.measure(0,0);check(constrained.getMeasuredWidth()==70,"Minimum wins conflicting maximum");
  constrained.setWidth(40);constrained.measure(0,0);check(constrained.getMeasuredWidth()==40,"Fixed text width");
  constrained.setEms(4);constrained.measure(0,0);check(constrained.getMeasuredWidth()==4*constrained.getLineHeight()&&constrained.getMinWidth()==-1&&constrained.getMaxWidth()==-1,"Ems use line height");
  constrained.setTextSize(24);constrained.measure(0,0);check(constrained.getMeasuredWidth()==4*constrained.getLineHeight(),"Ems follow text size");
  constrained.setMaxWidth(50);check(constrained.getMaxEms()==-1&&constrained.getMaxWidth()==50,"Pixel setter replaces em mode");
  TextView view=new TextView(context);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,16);view.setTextColor(0xffff0000);view.setIncludeFontPadding(false);view.setPadding(4,3,5,6);view.setText("Hello");
  Bitmap actual=draw(view,120,50),expected=Bitmap.createBitmap(120,50,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(expected);canvas.clipRect(4,3,115,44);Paint paint=new Paint(view.getPaint());paint.setColor(0xffff0000);canvas.drawText("Hello",4,3-paint.getFontMetricsInt().ascent,paint);
  check(count(actual)>0,"text pixels missing");for(int y=0;y<50;y++)for(int x=0;x<120;x++)check(actual.getPixel(x,y)==expected.getPixel(x,y),"text baseline or padding pixels differ");
  check(view.getBaseline()==3-paint.getFontMetricsInt().ascent,"baseline");
  view.setGravity(Gravity.BOTTOM|Gravity.END);Bitmap aligned=draw(view,120,50);check(count(aligned)>0&&aligned.getPixel(4,3)==0,"aligned text");check(view.getBaseline()>30,"vertical gravity");
  view.setGravity(Gravity.TOP|Gravity.START);view.setPadding(0,0,0,0);view.setText("alpha beta gamma delta");layout(view,55,200);int narrow=view.getLineCount();check(narrow>=3,"word wrapping");layout(view,240,50);check(view.getLineCount()==1,"landscape reflow");layout(view,55,200);check(view.getLineCount()==narrow,"portrait reflow");
  view.setText("one\n\nthree\n");layout(view,120,150);check(view.getLineCount()==4,"empty paragraphs");view.setSingleLine(true);layout(view,120,50);check(view.getLineCount()==1,"single line transformation");view.setSingleLine(false);
  view.setText("A");view.setTextColor(new ColorStateList(new int[][]{new int[]{16842910},new int[]{}},new int[]{0xffff0000,0xff0000ff}));view.setEnabled(false);check(view.getCurrentTextColor()==0xff0000ff,"disabled state color");Bitmap blue=draw(view,50,50);check(count(blue)>0,"disabled text rendering");for(int y=0;y<50;y++)for(int x=0;x<50;x++)check((blue.getPixel(x,y)&0xffff00)==0,"wrong disabled glyph color");
  view.setText("");view.setHint("Hint");view.setHintTextColor(0xff00ff00);Bitmap hint=draw(view,80,50);check(count(hint)>0&&view.length()==0,"hint rendering without replacing text");for(int y=0;y<50;y++)for(int x=0;x<80;x++)check((hint.getPixel(x,y)&0xff00ff)==0,"wrong hint color");
  view.setHint(null);view.setText("first\nsecond\nthird");view.setMaxLines(1);view.measure(View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));int one=view.getMeasuredHeight();view.setMaxLines(3);view.measure(View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));check(view.getMeasuredHeight()>one*2,"maxLines height");
  view.setText("tap");final int[] clicks={0};view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clicks[0]++;}});view.setEnabled(true);layout(view,80,50);MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,10,10,0),up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,10,10,0);try{check(view.dispatchTouchEvent(down)&&view.dispatchTouchEvent(up)&&clicks[0]==1,"text pointer click");}finally{down.recycle();up.recycle();}
  view.setText("a\u0301b\u0301");layout(view,0,100);check(view.getLineCount()==2,"zero width must progress without splitting combining marks");
  StringBuilder mutable=new StringBuilder("safe");view.setText(mutable);mutable.append(" changed");check(view.getText().toString().equals("safe"),"text snapshot");
  view.setText("edit",TextView.BufferType.EDITABLE);view.getEditableText().append("able");check(view.getText().toString().equals("editable"),"Editable buffer mutation");
  try{view.setText(new String(new char[32769]));throw new AssertionError("text bound missing");}catch(IllegalArgumentException expectedError){}
  char[] many=new char[4097];java.util.Arrays.fill(many,'a');view.setText(new String(many));layout(view,100000,100);
  try{layout(view,0,100);throw new AssertionError("line bound missing");}catch(IllegalArgumentException expectedError){}
  layout(view,100000,100);check(view.getLineCount()==1,"failed layout poisoned cache");
  System.out.println("TEXTVIEW_RENDER_LAYOUT_INPUT_OK");
 }
 public static void checkXml(Context context){int id=context.getResources().getIdentifier("textview_plain","layout",context.getPackageName());TextView view=(TextView)LayoutInflater.from(context).inflate(id,null);check(view.getText().toString().equals("XML text")&&view.getTextSize()==18&&view.getMaxLines()==2&&view.getPaddingLeft()==3,"compiled TextView attributes");check(view.getCompoundDrawablePadding()==2&&view.getCompoundPaddingLeft()==11&&view.getCompoundDrawablesRelative()[0]!=null,"XML compound drawable");check(view.getBackgroundTintList().getDefaultColor()==Color.BLUE&&view.getBackgroundTintMode()==PorterDuff.Mode.SRC_IN,"XML background tint");view.measure(0,0);check(view.getMaxWidth()==70&&view.getMeasuredWidth()<=70,"XML maximum width constrains measurement");check(count(draw(view,120,60))>0,"XML text pixels");System.out.println("TEXTVIEW_XML_OK");}
}
