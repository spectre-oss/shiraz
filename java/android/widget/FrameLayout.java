// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;

/** Margin/gravity frame container for programmatic children; foreground rendering is separate. */
public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends MarginLayoutParams {
        public static final int UNSPECIFIED_GRAVITY=-1;
        public int gravity=UNSPECIFIED_GRAVITY;
        public LayoutParams(int width,int height) { super(width,height); }
        public LayoutParams(int width,int height,int gravity) { super(width,height); this.gravity=gravity; }
        public LayoutParams(Context context,AttributeSet attrs) { super(context,attrs);gravity=new shiraz.runtime.XmlAttributes(context,attrs).integer("layout_gravity",UNSPECIFIED_GRAVITY); }
        public LayoutParams(ViewGroup.LayoutParams other) { super(other); if (other instanceof LayoutParams) gravity=((LayoutParams)other).gravity; }
        public LayoutParams(MarginLayoutParams other) { super(other); if (other instanceof LayoutParams) gravity=((LayoutParams)other).gravity; }
        public LayoutParams(LayoutParams other) { super(other); gravity=other.gravity; }
    }
    private boolean measureAll;
    public FrameLayout(Context context) { super(context); }
    public FrameLayout(Context context,AttributeSet attrs) { this(context,attrs,0,0); }
    public FrameLayout(Context context,AttributeSet attrs,int style) { this(context,attrs,style,0); }
    public FrameLayout(Context context,AttributeSet attrs,int style,int resource) { super(context,attrs,style,resource);setMeasureAllChildren(new shiraz.runtime.XmlAttributes(context,attrs,style,resource).bool("measureAllChildren",false)); }
    public void setMeasureAllChildren(boolean value) { measureAll=value; requestLayout(); }
    public boolean getMeasureAllChildren() { return measureAll; }
    public boolean getConsiderGoneChildrenWhenMeasuring() { return measureAll; }
    public boolean shouldDelayChildPressedState() { return false; }
    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT); }
    protected boolean checkLayoutParams(ViewGroup.LayoutParams params) { return params instanceof LayoutParams; }
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) { return new LayoutParams(params); }
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(),attrs); }
    private int foregroundPadding(int side,int own){
        if(getForeground()==null)return own;
        android.graphics.Rect padding=new android.graphics.Rect();
        if(getForeground()!=null&&getForegroundGravity()==Gravity.FILL)getForeground().getPadding(padding);
        int value=side==0?padding.left:side==1?padding.top:side==2?padding.right:padding.bottom;
        return isForegroundInsidePadding()?Math.max(own,value):extent((long)own+value);
    }
    private int framePaddingLeft(){return foregroundPadding(0,getPaddingLeft());}
    private int framePaddingTop(){return foregroundPadding(1,getPaddingTop());}
    private int framePaddingRight(){return foregroundPadding(2,getPaddingRight());}
    private int framePaddingBottom(){return foregroundPadding(3,getPaddingBottom());}
    protected int getSuggestedMinimumWidth(){return Math.max(super.getSuggestedMinimumWidth(),getForeground()==null?0:getForeground().getMinimumWidth());}
    protected int getSuggestedMinimumHeight(){return Math.max(super.getSuggestedMinimumHeight(),getForeground()==null?0:getForeground().getMinimumHeight());}
    private int desired(long value) { if (value > MEASURED_SIZE_MASK) throw new IllegalArgumentException("Frame measured size exceeds 24 bits"); return (int)Math.max(0,value); }
    protected void onMeasure(int widthSpec,int heightSpec) {
        long width=0,height=0; int state=0;
        for (int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); if (!measureAll && child.getVisibility() == GONE) continue;
            LayoutParams params=(LayoutParams)child.getLayoutParams();params.resolveLayoutDirection(getLayoutDirection());
            child.measure(getChildMeasureSpec(widthSpec,extent((long)framePaddingLeft()+framePaddingRight()+params.leftMargin+params.rightMargin),params.width),getChildMeasureSpec(heightSpec,extent((long)framePaddingTop()+framePaddingBottom()+params.topMargin+params.bottomMargin),params.height));
            width=Math.max(width,(long)child.getMeasuredWidth()+params.leftMargin+params.rightMargin);
            height=Math.max(height,(long)child.getMeasuredHeight()+params.topMargin+params.bottomMargin);
            state=combineMeasuredStates(state,child.getMeasuredState());
        }
        setMeasuredDimension(resolveSizeAndState(Math.max(desired(width+framePaddingLeft()+framePaddingRight()),getSuggestedMinimumWidth()),widthSpec,state),resolveSizeAndState(Math.max(desired(height+framePaddingTop()+framePaddingBottom()),getSuggestedMinimumHeight()),heightSpec,state << MEASURED_HEIGHT_STATE_SHIFT));
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY || MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY) {
            for (int i=0;i<getChildCount();i++) {
                View child=getChildAt(i); if (!measureAll && child.getVisibility() == GONE) continue;
                LayoutParams params=(LayoutParams)child.getLayoutParams();
                if (params.width != LayoutParams.MATCH_PARENT && params.height != LayoutParams.MATCH_PARENT) continue;
                int w=params.width == LayoutParams.MATCH_PARENT ? MeasureSpec.makeMeasureSpec((int)Math.max(0L,(long)getMeasuredWidth()-framePaddingLeft()-framePaddingRight()-params.leftMargin-params.rightMargin),MeasureSpec.EXACTLY) : getChildMeasureSpec(widthSpec,extent((long)framePaddingLeft()+framePaddingRight()+params.leftMargin+params.rightMargin),params.width);
                int h=params.height == LayoutParams.MATCH_PARENT ? MeasureSpec.makeMeasureSpec((int)Math.max(0L,(long)getMeasuredHeight()-framePaddingTop()-framePaddingBottom()-params.topMargin-params.bottomMargin),MeasureSpec.EXACTLY) : getChildMeasureSpec(heightSpec,extent((long)framePaddingTop()+framePaddingBottom()+params.topMargin+params.bottomMargin),params.height);
                child.measure(w,h);
            }
        }
    }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        long contentRight=(long)getWidth()-framePaddingRight(), contentBottom=(long)getHeight()-framePaddingBottom();
        for (int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); if (child.getVisibility() == GONE) continue;
            LayoutParams params=(LayoutParams)child.getLayoutParams(); params.resolveLayoutDirection(child.getLayoutDirection());
            int gravity=Gravity.getAbsoluteGravity(params.gravity == LayoutParams.UNSPECIFIED_GRAVITY ? Gravity.TOP | Gravity.START : params.gravity,getLayoutDirection());
            int horizontal=gravity & Gravity.HORIZONTAL_GRAVITY_MASK, vertical=gravity & Gravity.VERTICAL_GRAVITY_MASK;
            long x=horizontal == Gravity.RIGHT ? contentRight-child.getMeasuredWidth()-params.rightMargin : horizontal == Gravity.CENTER_HORIZONTAL ? framePaddingLeft()+(contentRight-framePaddingLeft()-child.getMeasuredWidth())/2+params.leftMargin-params.rightMargin : (long)framePaddingLeft()+params.leftMargin;
            long y=vertical == Gravity.BOTTOM ? contentBottom-child.getMeasuredHeight()-params.bottomMargin : vertical == Gravity.CENTER_VERTICAL ? framePaddingTop()+(contentBottom-framePaddingTop()-child.getMeasuredHeight())/2+params.topMargin-params.bottomMargin : (long)framePaddingTop()+params.topMargin;
            child.layout(extent(x),extent(y),extent(x+child.getMeasuredWidth()),extent(y+child.getMeasuredHeight()));
        }
    }
}
