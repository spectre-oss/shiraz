// SPDX-License-Identifier: GPL-3.0-only
package android.text;
/** Base metrics contract for the supported software text layout. */
public abstract class Layout {
 public enum Alignment {ALIGN_NORMAL,ALIGN_OPPOSITE,ALIGN_CENTER}
 private CharSequence text;private TextPaint paint;private int width;private Alignment alignment;private float spacingMult,spacingAdd;
 protected Layout(CharSequence text,TextPaint paint,int width,Alignment alignment,float spacingMult,float spacingAdd){replaceWith(text,paint,width,alignment,spacingMult,spacingAdd);}
 protected void replaceWith(CharSequence text,TextPaint paint,int width,Alignment alignment,float spacingMult,float spacingAdd){if(text==null||paint==null||alignment==null)throw new NullPointerException();if(width<0)throw new IllegalArgumentException("Layout width");this.text=text;this.paint=paint;this.width=width;this.alignment=alignment;this.spacingMult=spacingMult;this.spacingAdd=spacingAdd;}
 public final CharSequence getText(){return text;}public final TextPaint getPaint(){return paint;}public final int getWidth(){return width;}public final Alignment getAlignment(){return alignment;}public final float getSpacingMultiplier(){return spacingMult;}public final float getSpacingAdd(){return spacingAdd;}
 public abstract int getLineCount();public abstract int getLineTop(int line);public abstract int getLineDescent(int line);public abstract int getLineStart(int line);public abstract float getLineWidth(int line);
 public final int getLineAscent(int line){return getLineTop(line)-getLineBaseline(line);}
 public int getHeight(){return getLineTop(getLineCount());}public final int getLineBottom(int line){return getLineTop(line+1);}public final int getLineBaseline(int line){return getLineBottom(line)-getLineDescent(line);}public final int getLineEnd(int line){return getLineStart(line+1);}
 public int getLineForVertical(int vertical){
  int count=getLineCount();if(count<=0)return 0;
  int low=0,high=count-1;
  while(low<high){int mid=(low+high)>>>1;if(getLineTop(mid+1)<=vertical)low=mid+1;else high=mid;}
  return low;
 }
 public int getLineForOffset(int offset){
  int count=getLineCount();if(count<=0)return 0;
  if(offset<=0)return 0;
  int low=0,high=count-1;
  while(low<high){int mid=(low+high)>>>1;if(getLineEnd(mid)<=offset)low=mid+1;else high=mid;}
  return low;
 }
 public int getOffsetForHorizontal(int line,float horiz){
  int count=getLineCount();if(count<=0||line<0||line>=count)return 0;
  int start=getLineStart(line),end=getLineVisibleEnd(line);
  if(start>=end)return start;
  String value=text.subSequence(start,end).toString();
  float local=horiz-getLineLeft(line);
  int baseLevel=0;try{baseLevel=getParagraphDirection(line)==DIR_RIGHT_TO_LEFT?1:0;}catch(RuntimeException ignored){}
  return Math.min(text.length(),start+new shiraz.runtime.TextGeometry(value,paint,baseLevel).hit(local));
 }
 public float getPrimaryHorizontal(int offset){
  int count=getLineCount();if(count<=0)return 0;
  int line=getLineForOffset(offset);
  int start=getLineStart(line),end=getLineVisibleEnd(line);
  int clamped=Math.max(start,Math.min(end,offset))-start;
  String value=text.subSequence(start,end).toString();
  int baseLevel=0;try{baseLevel=getParagraphDirection(line)==DIR_RIGHT_TO_LEFT?1:0;}catch(RuntimeException ignored){}
  return getLineLeft(line)+new shiraz.runtime.TextGeometry(value,paint,baseLevel).caret(clamped);
 }
 public boolean isRtlCharAt(int offset){
  if(offset<0||offset>text.length())throw new IndexOutOfBoundsException();
  int line=getLineForOffset(offset),relative=offset-getLineStart(line);int[] runs=getLineDirections(line).runs;
  for(int i=0;i+1<runs.length;i+=2)if(relative>=runs[i]&&relative-runs[i]<(runs[i+1]&0x3ffffff))return (runs[i+1]&(1<<26))!=0;
  return getParagraphDirection(line)==DIR_RIGHT_TO_LEFT;
 }
 /** Selection/link hit region, split into visual ranges for bidirectional text. */
 public void getSelectionPath(int start,int end,android.graphics.Path destination){
  if(destination==null)throw new NullPointerException("destination");destination.reset();
  if(start>end){int swap=start;start=end;end=swap;}
  if(start<0||end>text.length())throw new IndexOutOfBoundsException();if(start==end)return;
  Spanned spans=text instanceof Spanned?(Spanned)text:null;
  for(int line=getLineForOffset(start);line<getLineCount()&&getLineStart(line)<end;line++){
   int first=getLineStart(line),last=getLineVisibleEnd(line);float advance=0;
   for(int at=first;at<last;){int next=spans==null?last:spans.nextSpanTransition(at,last,android.text.style.MetricAffectingSpan.class);
    TextPaint run=new TextPaint(paint);if(spans!=null)for(android.text.style.MetricAffectingSpan span:spans.getSpans(at,next,android.text.style.MetricAffectingSpan.class))if(spans.getSpanStart(span)<next&&spans.getSpanEnd(span)>at)span.updateMeasureState(run);
    int a=Math.max(start,at),b=Math.min(end,next);String value=text.subSequence(at,next).toString();
    if(a<b){float[] ranges=new shiraz.runtime.TextGeometry(value,run,getParagraphDirection(line)==DIR_RIGHT_TO_LEFT?1:0).ranges(a-at,b-at);
     for(int i=0;i<ranges.length;i+=2)destination.addRect(getLineLeft(line)+advance+ranges[i],getLineTop(line),getLineLeft(line)+advance+ranges[i+1],getLineBottom(line),android.graphics.Path.Direction.CW);
    }advance+=getDesiredWidth(text,at,next,paint);at=next;
   }
  }
 }
 public float getSecondaryHorizontal(int offset){return getPrimaryHorizontal(offset);}
 /** Maximum paragraph advance, including metric span runs and default tab stops. */
 public static float getDesiredWidth(CharSequence text,TextPaint paint){return getDesiredWidth(text,0,text.length(),paint);}
 public static float getDesiredWidth(CharSequence text,int start,int end,TextPaint paint){
  if(text==null||paint==null)throw new NullPointerException();
  if(start<0||end<start||end>text.length())throw new IndexOutOfBoundsException();
  Spanned spans=text instanceof Spanned?(Spanned)text:null;
  float maximum=0,advance=0;
  for(int at=start;at<end;){
   char c=text.charAt(at);
   if(c=='\n'){maximum=Math.max(maximum,advance);advance=0;at++;continue;}
   if(c=='\t'){advance=((float)Math.floor(advance/20)+1)*20;at++;continue;}
   int next=spans==null?end:spans.nextSpanTransition(at,end,android.text.style.MetricAffectingSpan.class);
   for(int i=at;i<next;i++)if(text.charAt(i)=='\n'||text.charAt(i)=='\t'){next=i;break;}
   TextPaint run=paint;
   if(spans!=null){run=new TextPaint();run.set(paint);for(android.text.style.MetricAffectingSpan span:spans.getSpans(at,next,android.text.style.MetricAffectingSpan.class))if(spans.getSpanStart(span)<next&&spans.getSpanEnd(span)>at)span.updateMeasureState(run);}
   android.text.style.ReplacementSpan replacement=replacement(spans,at,next);
   advance+=replacement==null?run.measureText(text.subSequence(at,next).toString()):replacementSize(replacement,run,text,at,next,null);at=next;
  }
  return Math.max(maximum,advance);
 }
 static android.text.style.ReplacementSpan replacement(Spanned spans,int start,int end){
  if(spans==null||start==end)return null;
  android.text.style.ReplacementSpan result=null;
  for(android.text.style.ReplacementSpan span:spans.getSpans(start,end,android.text.style.ReplacementSpan.class)){
   int from=spans.getSpanStart(span),to=spans.getSpanEnd(span);if(from>=end||to<=start)continue;
   if(result!=null||from!=start||to!=end)throw new UnsupportedOperationException("Overlapping or partial replacement span");
   result=span;
  }
  return result;
 }
 static int replacementSize(android.text.style.ReplacementSpan span,TextPaint paint,CharSequence text,int start,int end,android.graphics.Paint.FontMetricsInt metrics){
  int size=span.getSize(paint,text,start,end,metrics);if(size<0)throw new IllegalArgumentException("Negative replacement advance");return size;
 }
 public static final int DIR_LEFT_TO_RIGHT=1,DIR_RIGHT_TO_LEFT=-1;
 public static class Directions { final int[] runs; public Directions(int[] runs){this.runs=runs.clone();} }
 public static final Directions DIRS_ALL_LEFT_TO_RIGHT=new Directions(new int[]{0,0x3ffffff});
 public final Alignment getParagraphAlignment(int line){
  if(line<0||line>=getLineCount())throw new IndexOutOfBoundsException("line");
  Alignment result=alignment;
  if(text instanceof Spanned){
   Spanned spans=(Spanned)text;int start=getLineStart(line),end=getLineEnd(line);
   for(android.text.style.AlignmentSpan span:spans.getSpans(start,end,android.text.style.AlignmentSpan.class))
    if(spans.getSpanStart(span)<end&&spans.getSpanEnd(span)>start)result=span.getAlignment();
  }
  return result;
 }
 public int getParagraphDirection(int line){throw new UnsupportedOperationException("Paragraph direction");}
 public boolean getLineContainsTab(int line){throw new UnsupportedOperationException("Tab metadata");}
 public Directions getLineDirections(int line){throw new UnsupportedOperationException("Direction runs");}
 /** Horizontal scrolling bounds for the supported paragraphs (no leading margins). */
 public float getLineLeft(int line){
  Alignment alignment=getParagraphAlignment(line);
  float extent=getLineMax(line);
  if(alignment==Alignment.ALIGN_CENTER)return (width-(((int)extent)&~1))/2;
  boolean right=alignment==Alignment.ALIGN_NORMAL?getParagraphDirection(line)==DIR_RIGHT_TO_LEFT:getParagraphDirection(line)==DIR_LEFT_TO_RIGHT;
  return right?width-extent:0;
 }
 public float getLineRight(int line){
  Alignment alignment=getParagraphAlignment(line);
  float extent=getLineMax(line);
  if(alignment==Alignment.ALIGN_CENTER)return width-(width-(((int)extent)&~1))/2;
  boolean right=alignment==Alignment.ALIGN_NORMAL?getParagraphDirection(line)==DIR_RIGHT_TO_LEFT:getParagraphDirection(line)==DIR_LEFT_TO_RIGHT;
  return right?width:extent;
 }
 public int getLineVisibleEnd(int line){
  int start=getLineStart(line),end=getLineEnd(line);
  if(line==getLineCount()-1)return end;
  while(end>start){char c=text.charAt(end-1);if(c=='\n')return end-1;if(c!=' '&&c!='\t'&&c!=0x1680&&!(c>=0x2000&&c<=0x200a&&c!=0x2007)&&c!=0x205f&&c!=0x3000)break;end--;}
  if(text instanceof Spanned){Spanned spans=(Spanned)text;for(android.text.style.ReplacementSpan span:spans.getSpans(end,end,android.text.style.ReplacementSpan.class))if(spans.getSpanStart(span)<=end&&spans.getSpanEnd(span)>end&&spans.getSpanEnd(span)<=getLineEnd(line))end=spans.getSpanEnd(span);}
  return end;
 }
 public float getLineMax(int line){return getDesiredWidth(text,getLineStart(line),getLineVisibleEnd(line),paint);}
 public int getTopPadding(){return 0;}public int getBottomPadding(){return 0;}
 public int getEllipsisStart(int line){return 0;}public int getEllipsisCount(int line){return 0;}public int getEllipsizedWidth(){return width;}
 public void draw(android.graphics.Canvas canvas){draw(canvas,null,null,0);}
 public void draw(android.graphics.Canvas canvas,android.graphics.Path highlight,android.graphics.Paint paint,int cursorOffset){throw new UnsupportedOperationException("Layout drawing");}
}
