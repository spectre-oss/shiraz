// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Single-line left-to-right layout. Styled runs require the general layout path. */
public class BoringLayout extends Layout implements TextUtils.EllipsizeCallback {
    public static class Metrics extends Paint.FontMetricsInt { public int width; }
    private int height, descent, topPadding, bottomPadding, ellipsisStart, ellipsisCount, ellipsizedWidth;
    private float advance;

    public static Metrics isBoring(CharSequence text, TextPaint paint) { return isBoring(text,paint,null); }
    public static Metrics isBoring(CharSequence text, TextPaint paint, Metrics reuse) {
        return isBoring(text,paint,TextDirectionHeuristics.FIRSTSTRONG_LTR,reuse);
    }
    public static Metrics isBoring(CharSequence text, TextPaint paint, TextDirectionHeuristic direction, Metrics reuse) {
        if(text==null||paint==null)throw new NullPointerException();
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='\n'||c=='\t'||c>=0x590&&c<=0x8ff||c==0x200e||c==0x200f||
               c>=0x202a&&c<=0x202e||c>=0x2066&&c<=0x2069||Character.isSurrogate(c)||
               c>=0xfb1d&&c<=0xfdff||c>=0xfe70&&c<=0xfefe)return null;
        }
        if(direction!=null&&direction.isRtl(text,0,text.length()))return null;
        if(text instanceof Spanned&&((Spanned)text).getSpans(0,text.length(),Object.class).length!=0)return null;
        Metrics result=reuse==null?new Metrics():reuse;
        paint.getFontMetricsInt(result);
        result.width=(int)Math.ceil(paint.measureText(text.toString()));
        return result;
    }
    public static BoringLayout make(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad) {
        return new BoringLayout(text,paint,width,alignment,mult,add,metrics,pad);
    }
    public static BoringLayout make(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad,TextUtils.TruncateAt ellipsize,int ellipsizedWidth) {
        return new BoringLayout(text,paint,width,alignment,mult,add,metrics,pad,ellipsize,ellipsizedWidth);
    }
    public BoringLayout(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad) {
        this(text,paint,width,alignment,mult,add,metrics,pad,null,width);
    }
    public BoringLayout(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad,TextUtils.TruncateAt ellipsize,int ellipsizedWidth) {
        super(text,paint,width,alignment,mult,add);
        configure(text,paint,width,alignment,mult,add,metrics,pad,ellipsize,ellipsizedWidth);
    }
    public BoringLayout replaceOrMake(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad) {
        return replaceOrMake(text,paint,width,alignment,mult,add,metrics,pad,null,width);
    }
    public BoringLayout replaceOrMake(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad,TextUtils.TruncateAt ellipsize,int ellipsizedWidth) {
        configure(text,paint,width,alignment,mult,add,metrics,pad,ellipsize,ellipsizedWidth);return this;
    }
    private void configure(CharSequence text,TextPaint paint,int width,Alignment alignment,float mult,float add,Metrics metrics,boolean pad,TextUtils.TruncateAt ellipsize,int ellipsizedWidth) {
        if(metrics==null)throw new NullPointerException("metrics");
        if(isBoring(text,paint)==null)throw new UnsupportedOperationException("BoringLayout requires unstyled single-line LTR text");
        ellipsisStart=ellipsisCount=0;
        boolean truncate=ellipsize!=null&&ellipsize!=TextUtils.TruncateAt.MARQUEE;
        CharSequence display=truncate?TextUtils.ellipsize(text.toString(),paint,ellipsizedWidth,ellipsize,true,this):text;
        replaceWith(display,paint,width,alignment,mult,add);
        this.ellipsizedWidth=truncate?ellipsizedWidth:width;
        height=pad?metrics.bottom-metrics.top:metrics.descent-metrics.ascent;
        descent=pad?metrics.bottom:metrics.descent;
        topPadding=pad?metrics.top-metrics.ascent:0;
        bottomPadding=pad?metrics.bottom-metrics.descent:0;
        advance=truncate?(float)Math.ceil(paint.measureText(display.toString())):metrics.width;
    }
    public void ellipsized(int start,int end){ellipsisStart=start;ellipsisCount=end-start;}
    public int getLineCount(){return 1;}
    public int getLineTop(int line){return line==0?0:height;}
    public int getLineStart(int line){return line==0?0:getText().length();}
    public int getLineDescent(int line){return descent;}
    public float getLineWidth(int line){return line==0?advance:0;}
    public float getLineMax(int line){return advance;}
    public int getParagraphDirection(int line){return DIR_LEFT_TO_RIGHT;}
    public boolean getLineContainsTab(int line){return false;}
    public Directions getLineDirections(int line){return DIRS_ALL_LEFT_TO_RIGHT;}
    public int getTopPadding(){return topPadding;}
    public int getBottomPadding(){return bottomPadding;}
    public int getEllipsisStart(int line){return ellipsisStart;}
    public int getEllipsisCount(int line){return ellipsisCount;}
    public int getEllipsizedWidth(){return ellipsizedWidth;}
    public void draw(Canvas canvas,Path highlight,Paint highlightPaint,int cursorOffset) {
        if(highlight!=null){int saved=canvas.save();try{canvas.translate(0,cursorOffset);canvas.drawPath(highlight,highlightPaint);}finally{canvas.restoreToCount(saved);}}
        float x=getLineLeft(0);
        TextPaint paint=new TextPaint(getPaint());paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(getText(),0,getText().length(),x,height-descent,paint);
    }
}
