// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;

/** Shared-column row measurement, including explicit columns and spanning cells. */
public class TableRow extends LinearLayout {
    static final int LIMIT=256;
    private int[] columns;
    public static class LayoutParams extends LinearLayout.LayoutParams {
        public int column=-1,span=1;
        public LayoutParams(){super(MATCH_PARENT,WRAP_CONTENT);}
        public LayoutParams(int column){this();this.column=column;}
        public LayoutParams(int w,int h){super(w,h);}
        public LayoutParams(int w,int h,float weight){super(w,h,weight);}
        public LayoutParams(Context c,AttributeSet a){super(c,a);shiraz.runtime.XmlAttributes x=new shiraz.runtime.XmlAttributes(c,a);column=x.integer("layout_column",-1);span=Math.max(1,x.integer("layout_span",1));}
        public LayoutParams(ViewGroup.LayoutParams p){super(p);if(p instanceof LayoutParams){column=((LayoutParams)p).column;span=((LayoutParams)p).span;}}
        public LayoutParams(MarginLayoutParams p){this((ViewGroup.LayoutParams)p);}
    }
    public TableRow(Context c){super(c);setOrientation(HORIZONTAL);setBaselineAligned(false);}
    public TableRow(Context c,AttributeSet a){super(c,a);setOrientation(HORIZONTAL);setBaselineAligned(false);}
    protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams();}
    public LayoutParams generateLayoutParams(AttributeSet a){return new LayoutParams(getContext(),a);}
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p){return new LayoutParams(p);}
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p){return p instanceof LayoutParams;}
    private int start(LayoutParams p,int cursor){if(p.column< -1||p.column>=LIMIT||p.span<1||p.span>LIMIT)throw new IllegalArgumentException("Table column/span bound");int start=Math.max(cursor,p.column);if(start+p.span>LIMIT)throw new IllegalArgumentException("Table column count");return start;}
    int[] desiredColumns(int ws,int hs){
        int[] sizes=new int[LIMIT];int cursor=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);LayoutParams p=(LayoutParams)child.getLayoutParams();int col=start(p,cursor);cursor=col+p.span;if(child.getVisibility()==GONE)continue;
            p.resolveLayoutDirection(getLayoutDirection());child.measure(getChildMeasureSpec(ws,getPaddingLeft()+getPaddingRight()+p.leftMargin+p.rightMargin,p.width),getChildMeasureSpec(hs,getPaddingTop()+getPaddingBottom()+p.topMargin+p.bottomMargin,p.height));
            if(p.span==1)sizes[col]=Math.max(sizes[col],child.getMeasuredWidth()+p.leftMargin+p.rightMargin);
        }
        return java.util.Arrays.copyOf(sizes,cursor);
    }
    void constrain(int[] widths){columns=widths.clone();}
    void collapse(int col,boolean value){int cursor=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);LayoutParams p=(LayoutParams)child.getLayoutParams();int at=start(p,cursor);cursor=at+p.span;if(col>=at&&col<cursor)child.setVisibility(value?GONE:VISIBLE);}}
    private int sum(int start,int end){long value=0;for(int c=start;c<end&&c<columns.length;c++)value+=columns[c];if(value>MEASURED_SIZE_MASK)throw new IllegalArgumentException("Table width bound");return (int)value;}
    protected void onMeasure(int ws,int hs){
        if(!(getParent() instanceof TableLayout))columns=desiredColumns(ws,hs);
        if(columns==null)columns=desiredColumns(ws,hs);
        int cursor=0,height=0,state=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);LayoutParams p=(LayoutParams)child.getLayoutParams();int col=start(p,cursor);cursor=col+p.span;if(child.getVisibility()==GONE)continue;
            p.resolveLayoutDirection(getLayoutDirection());int width=Math.max(0,sum(col,cursor)-p.leftMargin-p.rightMargin);
            int gravity=p.gravity<0?0:Gravity.getAbsoluteGravity(p.gravity,getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;
            child.measure(MeasureSpec.makeMeasureSpec(width,gravity==0?MeasureSpec.EXACTLY:MeasureSpec.AT_MOST),getChildMeasureSpec(hs,getPaddingTop()+getPaddingBottom()+p.topMargin+p.bottomMargin,p.height));
            height=Math.max(height,child.getMeasuredHeight()+p.topMargin+p.bottomMargin);state=combineMeasuredStates(state,child.getMeasuredState());
        }
        setMeasuredDimension(resolveSizeAndState(Math.max(getSuggestedMinimumWidth(),sum(0,columns.length)+getPaddingLeft()+getPaddingRight()),ws,state),resolveSizeAndState(Math.max(getSuggestedMinimumHeight(),height+getPaddingTop()+getPaddingBottom()),hs,state<<MEASURED_HEIGHT_STATE_SHIFT));
    }
    protected void onLayout(boolean changed,int l,int t,int r,int b){
        int cursor=0;boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;int total=sum(0,columns.length),free=getWidth()-getPaddingLeft()-getPaddingRight()-total;
        int rowGravity=Gravity.getAbsoluteGravity(getGravity(),getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;int base=getPaddingLeft()+(rowGravity==Gravity.RIGHT?free:rowGravity==Gravity.CENTER_HORIZONTAL?free/2:0);
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);LayoutParams p=(LayoutParams)child.getLayoutParams();int col=start(p,cursor);cursor=col+p.span;if(child.getVisibility()==GONE)continue;
            int cell=sum(col,cursor),x=base+(rtl?total-sum(0,cursor):sum(0,col))+p.leftMargin;int gravity=Gravity.getAbsoluteGravity(p.gravity<0?getGravity():p.gravity,getLayoutDirection());int extra=cell-p.leftMargin-p.rightMargin-child.getMeasuredWidth();
            if((gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT)x+=extra;else if((gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL)x+=extra/2;
            int y=getPaddingTop()+p.topMargin,vertical=getHeight()-getPaddingTop()-getPaddingBottom()-p.topMargin-p.bottomMargin-child.getMeasuredHeight();if((gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM)y+=vertical;else if((gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.CENTER_VERTICAL)y+=vertical/2;
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        }
    }
    public CharSequence getAccessibilityClassName(){return "android.widget.TableRow";}
}
