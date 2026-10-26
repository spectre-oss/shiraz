// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.LineHeightSpan;
import java.util.ArrayList;

/** Greedy multiline software layout; advanced paragraph shaping is a separate extension. */
public class StaticLayout extends Layout {
    public static final class Builder {
        private final CharSequence text; private final int start,end; private final TextPaint paint;
        private final int width;
        private Alignment alignment=Alignment.ALIGN_NORMAL;
        private TextDirectionHeuristic direction=TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private TextUtils.TruncateAt ellipsize; private int ellipsizedWidth;
        private float add,mult=1; private boolean pad=true; private int maxLines=Integer.MAX_VALUE;
        private Builder(CharSequence text,int start,int end,TextPaint paint,int width){
            if(text==null||paint==null)throw new NullPointerException();
            if(start<0||end<start||end>text.length()||width<0)throw new IllegalArgumentException("StaticLayout bounds");
            this.text=text;this.start=start;this.end=end;this.paint=paint;this.width=width;this.ellipsizedWidth=width;
        }
        public static Builder obtain(CharSequence text,int start,int end,TextPaint paint,int width){return new Builder(text,start,end,paint,width);}
        public Builder setAlignment(Alignment value){if(value==null)throw new NullPointerException();alignment=value;return this;}
        public Builder setTextDirection(TextDirectionHeuristic value){if(value==null)throw new NullPointerException();direction=value;return this;}
        public Builder setLineSpacing(float add,float mult){if(!Float.isFinite(add)||!Float.isFinite(mult)||mult<0)throw new IllegalArgumentException("Line spacing");this.add=add;this.mult=mult;return this;}
        public Builder setIncludePad(boolean value){pad=value;return this;}
        public Builder setMaxLines(int value){if(value<1)throw new IllegalArgumentException("maxLines");maxLines=value;return this;}
        public Builder setBreakStrategy(int value){if(value!=0)throw new UnsupportedOperationException("StaticLayout optimal breaking");return this;}
        // Android SIMPLE breaking does not insert automatic hyphens, regardless of frequency.
        public Builder setHyphenationFrequency(int value){if(value<0||value>4)throw new IllegalArgumentException("Hyphenation frequency");return this;}
        public Builder setJustificationMode(int value){if(value!=0)throw new UnsupportedOperationException("StaticLayout justification");return this;}
        public Builder setEllipsize(TextUtils.TruncateAt value){ellipsize=value;return this;}
        public Builder setEllipsizedWidth(int value){if(value<0)throw new IllegalArgumentException("Ellipsis width");ellipsizedWidth=value;return this;}
        public Builder setIndents(int[] left,int[] right){if(left!=null||right!=null)throw new UnsupportedOperationException("StaticLayout indents");return this;}
        public StaticLayout build(){return new StaticLayout(this);}
    }
    private static final class Line {int start,end,visible,top,bottom,descent,ellipsisStart,ellipsisCount;float width;boolean tab;String display;byte[] levels;int baseLevel;}
    private final ArrayList<Line> lines=new ArrayList<Line>();
    private int topPadding,bottomPadding,ellipsizedWidth;
    public StaticLayout(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,boolean pad){this(Builder.obtain(text,0,text.length(),paint,width).setAlignment(alignment).setLineSpacing(add,mult).setIncludePad(pad));}
    public StaticLayout(CharSequence text,int start,int end,TextPaint paint,int width,Alignment alignment,float mult,float add,boolean pad){this(Builder.obtain(text,start,end,paint,width).setAlignment(alignment).setLineSpacing(add,mult).setIncludePad(pad));}
    private StaticLayout(Builder b){
        super(b.text,b.paint,b.width,b.alignment,b.mult,b.add);
        ellipsizedWidth=b.ellipsizedWidth;
        if(b.end-b.start>32768)throw new IllegalArgumentException("StaticLayout text limit");
        boolean bidiText=java.text.Bidi.requiresBidi(b.text.subSequence(b.start,b.end).toString().toCharArray(),0,b.end-b.start);
        Spanned spans=b.text instanceof Spanned?(Spanned)b.text:null;
        if(bidiText&&spans!=null&&spans.getSpans(b.start,b.end,MetricAffectingSpan.class).length>0)throw new UnsupportedOperationException("Bidirectional metric/replacement spans require styled shaping context");
        if(bidiText&&b.text.subSequence(b.start,b.end).toString().indexOf('\t')>=0)throw new UnsupportedOperationException("Bidirectional tab stops");
        if(spans!=null)for(android.text.style.ParagraphStyle span:spans.getSpans(b.start,b.end,android.text.style.ParagraphStyle.class)){
            if(span instanceof LineHeightSpan||span instanceof android.text.style.AlignmentSpan)continue;
            if(span instanceof android.text.style.LeadingMarginSpan){
                android.text.style.LeadingMarginSpan margin=(android.text.style.LeadingMarginSpan)span;
                if(margin.getLeadingMargin(true)==0&&margin.getLeadingMargin(false)==0)continue;
                throw new UnsupportedOperationException("StaticLayout nonzero leading margin");
            }
            throw new UnsupportedOperationException("StaticLayout paragraph span: "+span.getClass().getName());
        }
        if(spans!=null)for(android.text.style.ReplacementSpan span:spans.getSpans(b.start,b.end,android.text.style.ReplacementSpan.class)){
            int from=spans.getSpanStart(span),to=spans.getSpanEnd(span);
            if(from<b.start||to>b.end)throw new UnsupportedOperationException("Partial replacement span slice");
            for(int i=from;i<to;i++)if(b.text.charAt(i)=='\n'||b.text.charAt(i)=='\t')throw new UnsupportedOperationException("Replacement across paragraph or tab");
        }
        int start=b.start;
        while(start<=b.end&&lines.size()<b.maxLines){
            if(lines.size()>=4096)throw new IllegalArgumentException("StaticLayout line limit");
            int paragraph=start;while(paragraph<b.end&&b.text.charAt(paragraph)!='\n')paragraph++;
            int end=paragraph;
            if(getDesiredWidth(b.text,start,end,b.paint)>b.width){
                ArrayList<Integer> boundaries=new ArrayList<Integer>();boundaries.add(start);
                for(int at=start;at<paragraph;){at=clusterEnd(b.text,at,paragraph);boundaries.add(at);}
                int low=0,high=boundaries.size()-1;
                while(low<high){int mid=(low+high+1)/2;if(getDesiredWidth(b.text,start,boundaries.get(mid),b.paint)<=b.width)low=mid;else high=mid-1;}
                end=boundaries.get(Math.max(1,low));
                if(end<paragraph)for(int at=end;at>start;){int cp=Character.codePointBefore(b.text,at);if(Character.isWhitespace(cp)&&replacementBoundary(b.text,at)){end=at;break;}at-=Character.charCount(cp);}
            }
            int paragraphStart=start;while(paragraphStart>b.start&&b.text.charAt(paragraphStart-1)!='\n')paragraphStart--;
            String paragraphText=b.text.subSequence(paragraphStart,paragraph).toString();
            boolean rtl=b.direction.isRtl(b.text,paragraphStart,paragraph-paragraphStart);
            java.text.Bidi bidi=new java.text.Bidi(paragraphText,rtl?java.text.Bidi.DIRECTION_RIGHT_TO_LEFT:java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);
            Line line=new Line();line.baseLevel=bidi.getBaseLevel();line.levels=shiraz.runtime.TextGeometry.levels(bidi,start-paragraphStart,end-paragraphStart);line.start=start;line.visible=end;line.end=end;
            if(end==paragraph&&paragraph<b.end)line.end++;
            line.width=getDesiredWidth(b.text,start,end,b.paint);
            for(int i=start;i<end;i++)if(b.text.charAt(i)=='\t')line.tab=true;
            lines.add(line);
            if(line.end==b.end&&(b.end==b.start||b.text.charAt(b.end-1)!='\n'))break;
            if(start==b.end)break;
            start=line.end;
        }
        if(b.ellipsize!=null&&b.ellipsize!=TextUtils.TruncateAt.MARQUEE){
            if(b.maxLines!=1&&b.ellipsize!=TextUtils.TruncateAt.END)throw new UnsupportedOperationException("Multiline start/middle ellipsis");
            for(int i=0;i<lines.size();i++){
                final Line line=lines.get(i);boolean hidden=i==lines.size()-1&&line.end<b.end;
                if(!hidden&&line.width<=b.ellipsizedWidth)continue;
                if(spans!=null&&spans.getSpans(line.start,hidden?b.end:line.visible,MetricAffectingSpan.class).length!=0&&b.ellipsize==TextUtils.TruncateAt.END){
                    int end=hidden?b.end:line.visible;
                    ArrayList<Integer> cuts=new ArrayList<Integer>();cuts.add(line.start);
                    for(int at=line.start;at<line.visible;){at=clusterEnd(b.text,at,line.visible);cuts.add(at);}
                    int low=0,high=cuts.size()-1;
                    while(low<high){int mid=(low+high+1)/2;CharSequence candidate=ellipsisText(b.text,line.start,cuts.get(mid),end);if(getDesiredWidth(candidate,0,candidate.length(),b.paint)<=b.ellipsizedWidth)low=mid;else high=mid-1;}
                    int cut=cuts.get(low);if(cut==end&&low>0)cut=cuts.get(low-1);
                    line.ellipsisStart=cut-line.start;line.ellipsisCount=end-cut;
                    CharSequence display=ellipsisText(b.text,line.start,cut,end);line.display=display.toString();line.width=getDesiredWidth(display,0,display.length(),b.paint);
                    line.end=end;line.visible=end;line.tab=false;java.text.Bidi bidi=new java.text.Bidi(line.display,line.baseLevel==0?java.text.Bidi.DIRECTION_LEFT_TO_RIGHT:java.text.Bidi.DIRECTION_RIGHT_TO_LEFT);line.levels=shiraz.runtime.TextGeometry.levels(bidi,0,line.display.length());continue;
                }
                if(spans!=null&&spans.getSpans(line.start,hidden?b.end:line.visible,MetricAffectingSpan.class).length!=0)throw new UnsupportedOperationException("Metric styled start/middle ellipsis");
                int end=hidden?b.end:line.visible;
                String source=b.text.subSequence(line.start,end).toString().replace('\n',' ');
                if(source.indexOf('\t')>=0)throw new UnsupportedOperationException("Tab ellipsis");
                if(hidden&&b.ellipsize==TextUtils.TruncateAt.END){
                    String visible=b.text.subSequence(line.start,line.visible).toString();
                    String compact=TextUtils.shirazEllipsize(visible,b.paint,b.ellipsizedWidth,b.ellipsize,true);
                    int kept=compact.endsWith("\u2026")?compact.length()-1:0;
                    line.ellipsisStart=kept;line.ellipsisCount=end-line.start-kept;
                    StringBuilder display=new StringBuilder(compact);while(display.length()<end-line.start)display.append('\ufeff');line.display=display.toString();
                }else{
                    if(hidden&&b.text.subSequence(line.start,end).toString().indexOf('\n')>=0)throw new UnsupportedOperationException("Start/middle ellipsis across paragraphs");
                    line.display=TextUtils.ellipsize(source,b.paint,b.ellipsizedWidth,b.ellipsize,true,new TextUtils.EllipsizeCallback(){public void ellipsized(int start,int end){line.ellipsisStart=start;line.ellipsisCount=end-start;}}).toString();
                }
                line.end=end;line.visible=end;line.width=b.paint.measureText(line.display);line.tab=false;java.text.Bidi bidi=new java.text.Bidi(line.display,line.baseLevel==0?java.text.Bidi.DIRECTION_LEFT_TO_RIGHT:java.text.Bidi.DIRECTION_RIGHT_TO_LEFT);line.levels=shiraz.runtime.TextGeometry.levels(bidi,0,line.display.length());
            }
        }
        java.util.IdentityHashMap<LineHeightSpan,Integer> spanOrigins=new java.util.IdentityHashMap<LineHeightSpan,Integer>();
        int top=0;
        for(int i=0;i<lines.size();i++){
            Line line=lines.get(i);Paint.FontMetricsInt fm=b.paint.getFontMetricsInt();
            if(spans!=null)for(int at=line.start;at<line.visible;){int end=spans.nextSpanTransition(at,line.visible,MetricAffectingSpan.class);TextPaint run=runPaint(at,end,false);Paint.FontMetricsInt m=run.getFontMetricsInt();android.text.style.ReplacementSpan replacement=replacement(spans,at,end);if(replacement!=null)replacementSize(replacement,run,b.text,at,end,m);fm.top=Math.min(fm.top,m.top+run.baselineShift);fm.ascent=Math.min(fm.ascent,m.ascent+run.baselineShift);fm.bottom=Math.max(fm.bottom,m.bottom+run.baselineShift);fm.descent=Math.max(fm.descent,m.descent+run.baselineShift);at=end;}
            if(spans!=null){
                int paragraphStart=line.start,paragraphEnd=line.start;
                while(paragraphStart>b.start&&b.text.charAt(paragraphStart-1)!='\n')paragraphStart--;
                while(paragraphEnd<b.end&&b.text.charAt(paragraphEnd)!='\n')paragraphEnd++;
                if(paragraphEnd<b.end)paragraphEnd++;
                if(paragraphStart!=paragraphEnd||paragraphStart==0)for(LineHeightSpan span:spans.getSpans(paragraphStart,paragraphEnd,LineHeightSpan.class)){
                    Integer origin=spanOrigins.get(span);
                    if(origin==null){int offset=spans.getSpanStart(span);origin=top;for(int previous=0;previous<i;previous++){Line earlier=lines.get(previous);if(offset<earlier.end){origin=earlier.top;break;}}spanOrigins.put(span,origin);}
                    if(span instanceof LineHeightSpan.WithDensity)((LineHeightSpan.WithDensity)span).chooseHeight(b.text,line.start,line.end,origin,top,fm,b.paint);
                    else span.chooseHeight(b.text,line.start,line.end,origin,top,fm);
                }
            }
            String shaped=line.display==null?b.text.subSequence(line.start,line.visible).toString():line.display;if(shaped.indexOf('\t')<0&&(spans==null||spans.getSpans(line.start,line.visible,MetricAffectingSpan.class).length==0)){android.graphics.Rect ink=new android.graphics.Rect();TextPaint boundsPaint=new TextPaint(b.paint);boundsPaint.shirazSetBidiLevels(line.levels);boundsPaint.getTextBounds(shaped,0,shaped.length(),ink);fm.top=Math.min(fm.top,ink.top);fm.ascent=Math.min(fm.ascent,ink.top);fm.bottom=Math.max(fm.bottom,ink.bottom);fm.descent=Math.max(fm.descent,ink.bottom);}
            boolean first=i==0,last=i==lines.size()-1;
            if(first&&b.pad)topPadding=fm.top-fm.ascent;
            if(last&&b.pad)bottomPadding=fm.bottom-fm.descent;
            int ascent=first&&b.pad?fm.top:fm.ascent,descent=last&&b.pad?fm.bottom:fm.descent;
            int extra=last?0:Math.max(ascent-descent,Math.round((fm.descent-fm.ascent)*(b.mult-1)+b.add));
            line.top=top;line.descent=descent+extra;top+=descent-ascent+extra;line.bottom=top;
        }
    }
    private static CharSequence ellipsisText(CharSequence source,int start,int cut,int end){
        StringBuilder value=new StringBuilder(source.subSequence(start,cut).toString());
        if(cut<end)value.append('\u2026');while(value.length()<end-start)value.append('\ufeff');
        SpannableString result=new SpannableString(value);
        if(source instanceof Spanned){Spanned spans=(Spanned)source;for(Object span:spans.getSpans(start,end,Object.class)){
            int a=Math.max(start,spans.getSpanStart(span)),b=Math.min(end,spans.getSpanEnd(span));
            if(a>=cut)continue;if(span instanceof android.text.style.ReplacementSpan)b=Math.min(b,cut);
            if(a<b)result.setSpan(span,a-start,b-start,spans.getSpanFlags(span));
        }}return result;
    }
    private static boolean replacementBoundary(CharSequence text,int at){
        if(text instanceof Spanned){Spanned spans=(Spanned)text;for(android.text.style.ReplacementSpan span:spans.getSpans(at,at,android.text.style.ReplacementSpan.class))if(spans.getSpanStart(span)<at&&spans.getSpanEnd(span)>at)return false;}return true;
    }
    private static int clusterEnd(CharSequence text,int at,int end){
        if(text instanceof Spanned){Spanned spans=(Spanned)text;for(android.text.style.ReplacementSpan span:spans.getSpans(at,at+1,android.text.style.ReplacementSpan.class))if(spans.getSpanStart(span)<=at&&spans.getSpanEnd(span)>at)return Math.min(end,spans.getSpanEnd(span));}
        at+=Character.charCount(Character.codePointAt(text,at));
        while(at<end){int cp=Character.codePointAt(text,at),type=Character.getType(cp);if(type!=Character.NON_SPACING_MARK&&type!=Character.COMBINING_SPACING_MARK&&type!=Character.ENCLOSING_MARK)break;at+=Character.charCount(cp);}return at;
    }
    private TextPaint runPaint(int start,int end,boolean draw){
        TextPaint result=new TextPaint();result.set(getPaint());
        if(getText() instanceof Spanned){Spanned spans=(Spanned)getText();Class<? extends CharacterStyle> type=draw?CharacterStyle.class:MetricAffectingSpan.class;for(CharacterStyle span:spans.getSpans(start,end,type))if(spans.getSpanStart(span)<end&&spans.getSpanEnd(span)>start){if(draw)span.updateDrawState(result);else ((MetricAffectingSpan)span).updateMeasureState(result);}}
        result.setTextAlign(Paint.Align.LEFT);return result;
    }
    public int getLineCount(){return lines.size();}
    public int getLineTop(int line){return line==lines.size()?lines.get(line-1).bottom:lines.get(line).top;}
    public int getLineStart(int line){return line==lines.size()?lines.get(line-1).end:lines.get(line).start;}
    public int getLineDescent(int line){return lines.get(line).descent;}
    public float getLineWidth(int line){return lines.get(line).width;}
    public float getLineMax(int line){return lines.get(line).display!=null?lines.get(line).width:super.getLineMax(line);}
    public int getParagraphDirection(int line){return lines.get(line).baseLevel==0?DIR_LEFT_TO_RIGHT:DIR_RIGHT_TO_LEFT;}
    public Directions getLineDirections(int line){return new Directions(shiraz.runtime.TextGeometry.directions(lines.get(line).levels));}
    public boolean getLineContainsTab(int line){return lines.get(line).tab;}
    public int getEllipsisStart(int line){return lines.get(line).ellipsisStart;}
    public int getEllipsisCount(int line){return lines.get(line).ellipsisCount;}
    public int getEllipsizedWidth(){return ellipsizedWidth;}
    public int getTopPadding(){return topPadding;}public int getBottomPadding(){return bottomPadding;}
    public void draw(Canvas canvas,Path highlight,Paint highlightPaint,int cursorOffset){
        if(highlight!=null){int saved=canvas.save();try{canvas.translate(0,cursorOffset);canvas.drawPath(highlight,highlightPaint);}finally{canvas.restoreToCount(saved);}}
        Spanned spans=getText() instanceof Spanned?(Spanned)getText():null;
        for(int i=0;i<lines.size();i++){
            Line line=lines.get(i);float origin=getLineLeft(i),advance=0;
            int lineSave=canvas.save();try{
            if(spans!=null){
                boolean first=line.start==0||getText().charAt(line.start-1)=='\n';
                int paragraphStart=line.start,paragraphEnd=line.start;
                while(paragraphStart>0&&getText().charAt(paragraphStart-1)!='\n')paragraphStart--;
                while(paragraphEnd<getText().length()&&getText().charAt(paragraphEnd)!='\n')paragraphEnd++;
                for(android.text.style.LeadingMarginSpan margin:spans.getSpans(paragraphStart,paragraphEnd,android.text.style.LeadingMarginSpan.class)){
                    int direction=getParagraphDirection(i);
                    margin.drawLeadingMargin(canvas,getPaint(),direction==DIR_RIGHT_TO_LEFT?getWidth():0,direction,line.top,getLineBaseline(i),line.bottom,getText(),line.start,line.end,first,this);
                }
            }
            if(line.display!=null){if(spans!=null&&spans.getSpans(line.start,line.visible,CharacterStyle.class).length!=0){
                CharSequence display=ellipsisText(getText(),line.start,line.start+line.ellipsisStart,line.end);
                // The outer line already invoked paragraph drawing callbacks.
                if(display instanceof Spannable){Spannable local=(Spannable)display;for(android.text.style.LeadingMarginSpan margin:local.getSpans(0,local.length(),android.text.style.LeadingMarginSpan.class))local.removeSpan(margin);}
                StaticLayout rendered=Builder.obtain(display,0,display.length(),getPaint(),Math.max(getWidth(),(int)Math.ceil(line.width))).setIncludePad(false).setMaxLines(1).setTextDirection(line.baseLevel==0?TextDirectionHeuristics.LTR:TextDirectionHeuristics.RTL).build();
                int saved=canvas.save();try{canvas.translate(origin-rendered.getLineLeft(0),getLineBaseline(i)-rendered.getLineBaseline(0));rendered.draw(canvas);}finally{canvas.restoreToCount(saved);}continue;
            }TextPaint paint=runPaint(line.start,line.visible,true);paint.shirazSetBidiLevels(line.levels);canvas.drawText(line.display,origin,getLineBaseline(i)+paint.baselineShift,paint);continue;}
            for(int at=line.start;at<line.visible;){
                if(getText().charAt(at)=='\t'){advance=((float)Math.floor(advance/20)+1)*20;at++;continue;}
                int end=spans==null?line.visible:spans.nextSpanTransition(at,line.visible,MetricAffectingSpan.class);
                for(int k=at;k<end;k++)if(getText().charAt(k)=='\t'){end=k;break;}
                android.text.style.ReplacementSpan replacement=replacement(spans,at,end);
                if(replacement!=null){TextPaint paint=runPaint(at,end,true);replacement.draw(canvas,getText(),at,end,origin+advance,line.top,getLineBaseline(i),line.bottom,paint);advance+=replacementSize(replacement,runPaint(at,end,false),getText(),at,end,null);at=end;continue;}
                TextPaint measure=runPaint(at,end,false);measure.shirazSetBidiLevels(java.util.Arrays.copyOfRange(line.levels,at-line.start,end-line.start));String run=getText().subSequence(at,end).toString();
                for(int drawStart=at;drawStart<end;){int drawEnd=spans==null?end:spans.nextSpanTransition(drawStart,end,CharacterStyle.class);TextPaint paint=runPaint(drawStart,drawEnd,true);
                    paint.shirazSetBidiLevels(measure.shirazBidiLevels());float[] ranges=new shiraz.runtime.TextGeometry(run,measure,line.baseLevel).ranges(drawStart-at,drawEnd-at);
                    for(int r=0;r<ranges.length;r+=2){int saved=canvas.save();try{canvas.clipRect(origin+advance+ranges[r],line.top,origin+advance+ranges[r+1],line.bottom);canvas.drawText(run,origin+advance,getLineBaseline(i)+paint.baselineShift,paint);}finally{canvas.restoreToCount(saved);}}drawStart=drawEnd;}

                advance+=measure.measureText(run);at=end;
            }
            }finally{canvas.restoreToCount(lineSave);}
        }
    }
}
