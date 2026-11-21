// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.Layout;
public final class TextAlignmentSmoke {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void layout(View v){v.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));v.layout(0,0,240,100);}
 static void edge(TextView v,int mode,boolean right){v.setTextAlignment(mode);layout(v);Layout l=v.getLayout();float expected=right?l.getWidth()-l.getLineWidth(0):0;check(Math.abs(l.getLineLeft(0)-expected)<.1f,"alignment mode "+mode);}
 public static void main(String[] args){
  Context c=new ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};FrameLayout root=new FrameLayout(c);TextView text=new TextView(c);root.addView(text);text.setText("Map");text.setIncludeFontPadding(false);
  View api=text;api.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);layout(text);check(text.getLayout().getAlignment()==Layout.Alignment.ALIGN_CENTER,"reported center");check(Math.abs(text.getLayout().getLineLeft(0)-(240-text.getLayout().getLineWidth(0))/2)<.1,"center position");
  edge(text,View.TEXT_ALIGNMENT_TEXT_START,false);edge(text,View.TEXT_ALIGNMENT_TEXT_END,true);
  text.setText("שלום");edge(text,View.TEXT_ALIGNMENT_TEXT_START,true);edge(text,View.TEXT_ALIGNMENT_TEXT_END,false);
  text.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);edge(text,View.TEXT_ALIGNMENT_VIEW_START,false);edge(text,View.TEXT_ALIGNMENT_VIEW_END,true);
  text.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);edge(text,View.TEXT_ALIGNMENT_VIEW_START,true);edge(text,View.TEXT_ALIGNMENT_VIEW_END,false);
  text.setText("Map");text.setGravity(Gravity.RIGHT|Gravity.TOP);edge(text,View.TEXT_ALIGNMENT_GRAVITY,true);
  text.setTextAlignment(View.TEXT_ALIGNMENT_INHERIT);root.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);layout(text);check(text.getTextAlignment()==View.TEXT_ALIGNMENT_CENTER&&text.getLayout().getLineLeft(0)>0,"parent inheritance");
  root.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);text.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(text);check(text.getLayout().getLineLeft(0)==0,"parent change invalidates alignment cache");
  text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);root.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);layout(text);check(text.getTextAlignment()==View.TEXT_ALIGNMENT_VIEW_START&&text.getLayout().getLineLeft(0)>0,"explicit child wins");
  try{api.setTextAlignment(7);throw new AssertionError("bad mode accepted");}catch(IllegalArgumentException expected){}
  System.out.println("TEXT_ALIGNMENT_OK");
 }
}
