// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;

/** Vertical rows sharing bounded column widths, with stretch, shrink and collapse. */
public class TableLayout extends LinearLayout {
    private final boolean[] stretch=new boolean[TableRow.LIMIT],shrink=new boolean[TableRow.LIMIT],collapsed=new boolean[TableRow.LIMIT];
    private boolean stretchAll,shrinkAll;
    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(){super(MATCH_PARENT,WRAP_CONTENT);}
        public LayoutParams(int w,int h){super(MATCH_PARENT,h);}
        public LayoutParams(int w,int h,float weight){super(MATCH_PARENT,h,weight);}
        public LayoutParams(Context c,AttributeSet a){super(c,a);width=MATCH_PARENT;}
        public LayoutParams(ViewGroup.LayoutParams p){super(p);width=MATCH_PARENT;}
        public LayoutParams(MarginLayoutParams p){this((ViewGroup.LayoutParams)p);}
    }
    public TableLayout(Context c){super(c);setOrientation(VERTICAL);}
    public TableLayout(Context c,AttributeSet a){super(c,a);setOrientation(VERTICAL);shiraz.runtime.XmlAttributes x=new shiraz.runtime.XmlAttributes(c,a);stretchAll=parse(x.text("stretchColumns"),stretch,true);shrinkAll=parse(x.text("shrinkColumns"),shrink,true);parse(x.text("collapseColumns"),collapsed,false);}
    private static boolean parse(String text,boolean[] flags,boolean wildcard){if(text==null)return false;if(wildcard&&text.trim().equals("*"))return true;for(String item:text.split(",")){if(item.trim().isEmpty())continue;int col=Integer.parseInt(item.trim());column(col);flags[col]=true;}return false;}
    private static void column(int value){if(value<0||value>=TableRow.LIMIT)throw new IllegalArgumentException("Table column bound");}
    public void setStretchAllColumns(boolean value){stretchAll=value;requestLayout();}public boolean isStretchAllColumns(){return stretchAll;}
    public void setShrinkAllColumns(boolean value){shrinkAll=value;requestLayout();}public boolean isShrinkAllColumns(){return shrinkAll;}
    public void setColumnStretchable(int col,boolean value){column(col);stretch[col]=value;requestLayout();}public boolean isColumnStretchable(int col){column(col);return stretchAll||stretch[col];}
    public void setColumnShrinkable(int col,boolean value){column(col);shrink[col]=value;requestLayout();}public boolean isColumnShrinkable(int col){column(col);return shrinkAll||shrink[col];}
    public void setColumnCollapsed(int col,boolean value){column(col);collapsed[col]=value;for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof TableRow)((TableRow)getChildAt(i)).collapse(col,value);requestLayout();}public boolean isColumnCollapsed(int col){column(col);return collapsed[col];}
    protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams();}public LayoutParams generateLayoutParams(AttributeSet a){return new LayoutParams(getContext(),a);}protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p){return new LayoutParams(p);}protected boolean checkLayoutParams(ViewGroup.LayoutParams p){return p instanceof LayoutParams;}
    protected void onMeasure(int ws,int hs){
        int[] widths=new int[0];
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE||!(child instanceof TableRow))continue;TableRow row=(TableRow)child;for(int c=0;c<collapsed.length;c++)if(collapsed[c])row.collapse(c,true);
            int[] wanted=row.desiredColumns(ws,hs);if(wanted.length>widths.length)widths=java.util.Arrays.copyOf(widths,wanted.length);for(int c=0;c<wanted.length;c++)widths[c]=Math.max(widths[c],wanted[c]);
        }
        long total=0;for(int c=0;c<widths.length;c++){if(collapsed[c])widths[c]=0;total+=widths[c];}
        if(total>MEASURED_SIZE_MASK)throw new IllegalArgumentException("Table width bound");
        if(MeasureSpec.getMode(ws)!=MeasureSpec.UNSPECIFIED){int available=Math.max(0,MeasureSpec.getSize(ws)-getPaddingLeft()-getPaddingRight());long delta=available-total;boolean grow=delta>0;int count=0;for(int c=0;c<widths.length;c++)if(!collapsed[c]&&(grow?isColumnStretchable(c):isColumnShrinkable(c)))count++;
            for(int c=0;c<widths.length&&count>0;c++)if(!collapsed[c]&&(grow?isColumnStretchable(c):isColumnShrinkable(c))){long share=delta/count--;int change=(int)Math.max(-widths[c],share);widths[c]+=change;delta-=change;}
        }
        for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof TableRow)((TableRow)getChildAt(i)).constrain(widths);
        super.onMeasure(ws,hs);
    }
    public CharSequence getAccessibilityClassName(){return "android.widget.TableLayout";}
}
