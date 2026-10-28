// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.lang.ref.WeakReference;

/** Editable layout using the existing software line breaker. Edits invalidate
 * the whole layout; incremental paragraph reflow is not implemented. */
public class DynamicLayout extends Layout {
 private final boolean includePad;
 private final TextUtils.TruncateAt ellipsize;
 private final int ellipsizedWidth;
 private StaticLayout cached;
 private boolean dirty=true;
 private final boolean unobserved;
 public DynamicLayout(CharSequence base,TextPaint paint,int width,Alignment align,float mult,float add,boolean pad){
  this(base,base,paint,width,align,mult,add,pad,null,width);
 }
 public DynamicLayout(CharSequence base,CharSequence display,TextPaint paint,int width,Alignment align,float mult,float add,boolean pad){
  this(base,display,paint,width,align,mult,add,pad,null,width);
 }
 public DynamicLayout(CharSequence base,CharSequence display,TextPaint paint,int width,Alignment align,float mult,float add,boolean pad,TextUtils.TruncateAt ellipsize,int ellipsizedWidth){
  super(display,paint,width,align,mult,add);
  if(base==null)throw new NullPointerException("base");
  if(ellipsizedWidth<0)throw new IllegalArgumentException("ellipsis width");
  includePad=pad;this.ellipsize=ellipsize;this.ellipsizedWidth=ellipsizedWidth;
  observe(base);if(display!=base)observe(display);
  unobserved=!(display instanceof Spannable)&&!(display instanceof String)&&!(display instanceof SpannedString);
  layout();
 }
 private void observe(CharSequence value){
  if(value instanceof Spannable){
   Spannable spans=(Spannable)value;
   for(ChangeWatcher watcher:spans.getSpans(0,value.length(),ChangeWatcher.class))if(watcher.owner.get()==null)spans.removeSpan(watcher);
   spans.setSpan(new ChangeWatcher(this),0,value.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);
  }
 }
 private StaticLayout layout(){
  if(dirty||unobserved){
   CharSequence text=getText();
   cached=StaticLayout.Builder.obtain(text,0,text.length(),getPaint(),getWidth())
    .setAlignment(getAlignment()).setLineSpacing(getSpacingAdd(),getSpacingMultiplier())
    .setIncludePad(includePad).setEllipsize(ellipsize).setEllipsizedWidth(ellipsizedWidth).build();
   dirty=false;
  }
  return cached;
 }
 private static final class ChangeWatcher implements TextWatcher,SpanWatcher,NoCopySpan {
  final WeakReference<DynamicLayout> owner;
  ChangeWatcher(DynamicLayout value){owner=new WeakReference<DynamicLayout>(value);}
  private void changed(){DynamicLayout value=owner.get();if(value!=null)value.dirty=true;}
  public void beforeTextChanged(CharSequence s,int start,int count,int after){}
  public void onTextChanged(CharSequence s,int start,int before,int count){changed();}
  public void afterTextChanged(Editable s){changed();}
  public void onSpanAdded(Spannable s,Object what,int start,int end){if(!(what instanceof NoCopySpan))changed();}
  public void onSpanRemoved(Spannable s,Object what,int start,int end){if(!(what instanceof NoCopySpan))changed();}
  public void onSpanChanged(Spannable s,Object what,int oldStart,int oldEnd,int newStart,int newEnd){if(!(what instanceof NoCopySpan))changed();}
 }
 public int getLineCount(){return layout().getLineCount();}
 public int getLineTop(int line){return layout().getLineTop(line);}
 public int getLineDescent(int line){return layout().getLineDescent(line);}
 public int getLineStart(int line){return layout().getLineStart(line);}
 public float getLineWidth(int line){return layout().getLineWidth(line);}
 public float getLineMax(int line){return layout().getLineMax(line);}
 public int getLineVisibleEnd(int line){return layout().getLineVisibleEnd(line);}
 public int getParagraphDirection(int line){return layout().getParagraphDirection(line);}
 public boolean getLineContainsTab(int line){return layout().getLineContainsTab(line);}
 public Directions getLineDirections(int line){return layout().getLineDirections(line);}
 public int getTopPadding(){return layout().getTopPadding();}
 public int getBottomPadding(){return layout().getBottomPadding();}
 public int getEllipsisStart(int line){return layout().getEllipsisStart(line);}
 public int getEllipsisCount(int line){return layout().getEllipsisCount(line);}
 public int getEllipsizedWidth(){return ellipsizedWidth;}
 public void draw(Canvas canvas,Path highlight,Paint highlightPaint,int cursorOffset){layout().draw(canvas,highlight,highlightPaint,cursorOffset);}
}
