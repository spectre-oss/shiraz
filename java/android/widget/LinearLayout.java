// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
/** Linear child flow with margins, gravity, RTL order and weighted measurement. */
public class LinearLayout extends ViewGroup {
 public static final int HORIZONTAL=0,VERTICAL=1;
 public static class LayoutParams extends MarginLayoutParams {
  public float weight;public int gravity=-1;
  public LayoutParams(int width,int height){super(width,height);}
  public LayoutParams(int width,int height,float weight){super(width,height);this.weight=weight;}
  public LayoutParams(Context context,AttributeSet attrs){super(context,attrs);shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs);weight=a.number("layout_weight",0);gravity=a.integer("layout_gravity",-1);}
  public LayoutParams(ViewGroup.LayoutParams other){super(other);if(other instanceof LayoutParams){weight=((LayoutParams)other).weight;gravity=((LayoutParams)other).gravity;}}
  public LayoutParams(MarginLayoutParams other){this((ViewGroup.LayoutParams)other);}
  public LayoutParams(LayoutParams other){this((ViewGroup.LayoutParams)other);}
 }
 private final long[] baselineAscent=new long[4],baselineDescent=new long[4];
 private int baselineGroup(int value){switch(value&Gravity.VERTICAL_GRAVITY_MASK){case Gravity.TOP:return 1;case Gravity.BOTTOM:return 2;case Gravity.CENTER_VERTICAL:return 0;default:return 3;}}
 private int orientation,gravity=Gravity.TOP|Gravity.START;private float weightSum=-1;private boolean baselineAligned=true;private long totalLength;
 public LinearLayout(Context context){super(context);}
 public LinearLayout(Context context,AttributeSet attrs){this(context,attrs,0);}
 public LinearLayout(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public LinearLayout(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);a.reject("baselineAlignedChildIndex measureWithLargestChild");if(a.has("divider"))setDividerDrawable(a.drawable("divider"));setShowDividers(a.integer("showDividers",SHOW_DIVIDER_NONE));setDividerPadding(a.dimension("dividerPadding",0,false));int direction=a.integer("orientation",-1);if(direction>=0)setOrientation(direction);int alignment=a.integer("gravity",-1);if(alignment>=0)setGravity(alignment);if(!a.bool("baselineAligned",true))setBaselineAligned(false);if(a.has("weightSum"))setWeightSum(a.number("weightSum",-1));}
 public static final int SHOW_DIVIDER_NONE=0,SHOW_DIVIDER_BEGINNING=1,SHOW_DIVIDER_MIDDLE=2,SHOW_DIVIDER_END=4;
 private android.graphics.drawable.Drawable divider;private int showDividers,dividerPadding;
 public void setDividerDrawable(android.graphics.drawable.Drawable value){divider=value;}public android.graphics.drawable.Drawable getDividerDrawable(){return divider;}
 public void setShowDividers(int value){showDividers=value;}public int getShowDividers(){return showDividers;}
 public void setDividerPadding(int value){dividerPadding=value;}public int getDividerPadding(){return dividerPadding;}
 public int getDividerWidth(){return divider==null?0:divider.getIntrinsicWidth();}
 public void setOrientation(int value){if(value!=HORIZONTAL&&value!=VERTICAL)throw new IllegalArgumentException("Linear orientation");orientation=value;requestLayout();}
 public int getOrientation(){return orientation;}
 public void setGravity(int value){if((value&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==0)value|=Gravity.START;if((value&Gravity.VERTICAL_GRAVITY_MASK)==0)value|=Gravity.TOP;gravity=value;requestLayout();}
 public int getGravity(){return gravity;}
 public void setHorizontalGravity(int value){setGravity((gravity&~Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)|(value&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));}
 public void setVerticalGravity(int value){setGravity((gravity&~Gravity.VERTICAL_GRAVITY_MASK)|(value&Gravity.VERTICAL_GRAVITY_MASK));}
 public void setWeightSum(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Linear weight sum");weightSum=Math.max(0,value);requestLayout();}
 public float getWeightSum(){return weightSum;}
 public void setBaselineAligned(boolean value){baselineAligned=value;requestLayout();}
 public boolean isBaselineAligned(){return baselineAligned;}
 public boolean shouldDelayChildPressedState(){return false;}
 protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(orientation==VERTICAL?LayoutParams.MATCH_PARENT:LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
 public LayoutParams generateLayoutParams(AttributeSet attrs){return new LayoutParams(getContext(),attrs);}
 protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams other){return new LayoutParams(other);}
 protected boolean checkLayoutParams(ViewGroup.LayoutParams params){return params instanceof LayoutParams;}
 private int size(long value){if(value>MEASURED_SIZE_MASK)throw new IllegalArgumentException("Linear measured size exceeds 24 bits");return (int)Math.max(0,value);}
 private long mainPadding(){return orientation==VERTICAL?(long)getPaddingTop()+getPaddingBottom():(long)getPaddingLeft()+getPaddingRight();}
 private long crossPadding(){return orientation==VERTICAL?(long)getPaddingLeft()+getPaddingRight():(long)getPaddingTop()+getPaddingBottom();}
 private long mainMargins(LayoutParams p){return orientation==VERTICAL?(long)p.topMargin+p.bottomMargin:(long)p.leftMargin+p.rightMargin;}
 private long crossMargins(LayoutParams p){return orientation==VERTICAL?(long)p.leftMargin+p.rightMargin:(long)p.topMargin+p.bottomMargin;}
 private long accumulate(long current,long delta,int spec){long next=current+delta;return orientation==VERTICAL||MeasureSpec.getMode(spec)!=MeasureSpec.EXACTLY?Math.max(current,next):next;}
 private int mainSize(View child){return orientation==VERTICAL?child.getMeasuredHeight():child.getMeasuredWidth();}
 private int crossSize(View child){return orientation==VERTICAL?child.getMeasuredWidth():child.getMeasuredHeight();}
 private int mainDimension(LayoutParams p){return orientation==VERTICAL?p.height:p.width;}
 private int crossDimension(LayoutParams p){return orientation==VERTICAL?p.width:p.height;}
 private void measure(View child,int mainSpec,int crossSpec){if(orientation==VERTICAL)child.measure(crossSpec,mainSpec);else child.measure(mainSpec,crossSpec);}
 protected void onMeasure(int widthSpec,int heightSpec){
  int ms=orientation==VERTICAL?heightSpec:widthSpec,cs=orientation==VERTICAL?widthSpec:heightSpec;long main=0,cross=0;double weights=0;boolean skipped=false;int state=0;
  for(int i=0;i<getChildCount();i++){
   View child=getChildAt(i);if(child.getVisibility()==GONE)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();p.resolveLayoutDirection(getLayoutDirection());
   if(!Float.isFinite(p.weight)||p.weight<0)throw new IllegalArgumentException("Invalid child weight");weights+=p.weight;
   int dimension=mainDimension(p);boolean zero=dimension==0&&p.weight>0&&MeasureSpec.getMode(ms)==MeasureSpec.EXACTLY;
   int childMain=zero?MeasureSpec.makeMeasureSpec(0,MeasureSpec.EXACTLY):getChildMeasureSpec(ms,extent(mainPadding()+mainMargins(p)+(weights==0?main:0)),dimension==0&&p.weight>0?LayoutParams.WRAP_CONTENT:dimension);
   measure(child,childMain,getChildMeasureSpec(cs,extent(crossPadding()+crossMargins(p)),crossDimension(p)));
   skipped|=zero;main=accumulate(main,mainSize(child)+mainMargins(p),ms);cross=Math.max(cross,crossSize(child)+crossMargins(p));state=combineMeasuredStates(state,child.getMeasuredState());
  }
  int mainMinimum=orientation==VERTICAL?getSuggestedMinimumHeight():getSuggestedMinimumWidth();
  int resolvedMain=resolveSizeAndState(Math.max(size(main+mainPadding()),mainMinimum),ms,orientation==VERTICAL?state<<MEASURED_HEIGHT_STATE_SHIFT:state);
  long remaining=(resolvedMain&MEASURED_SIZE_MASK)-mainPadding()-main;
  if(weights>0&&(remaining!=0||skipped)){
   double remainingWeight=weightSum>0?weightSum:weights;
   for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();if(p.weight<=0)continue;
    long share=remainingWeight>0?(long)(remaining*(double)p.weight/remainingWeight):0;remaining-=share;remainingWeight-=p.weight;
    measure(child,MeasureSpec.makeMeasureSpec(size((long)mainSize(child)+share),MeasureSpec.EXACTLY),getChildMeasureSpec(cs,extent(crossPadding()+crossMargins(p)),crossDimension(p)));
   }
  }
  main=0;cross=0;state=0;long alternateCross=0;boolean allCrossMatch=true;
  for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();main=accumulate(main,mainSize(child)+mainMargins(p),ms);cross=Math.max(cross,crossSize(child)+crossMargins(p));boolean match=crossDimension(p)==LayoutParams.MATCH_PARENT;allCrossMatch&=match;alternateCross=Math.max(alternateCross,(match?0:crossSize(child))+crossMargins(p));state=combineMeasuredStates(state,child.getMeasuredState());}
  if(!allCrossMatch&&MeasureSpec.getMode(cs)!=MeasureSpec.EXACTLY)cross=alternateCross;
  java.util.Arrays.fill(baselineAscent,-1);java.util.Arrays.fill(baselineDescent,-1);
  if(orientation==HORIZONTAL&&baselineAligned){
   long ascent=-1,descent=-1;
   for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;int baseline=child.getBaseline();if(baseline<0)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();int group=baselineGroup(p.gravity<0?gravity:p.gravity);baselineAscent[group]=Math.max(baselineAscent[group],baseline);baselineDescent[group]=Math.max(baselineDescent[group],(long)child.getMeasuredHeight()+p.topMargin+p.bottomMargin-baseline);}
   for(int group=0;group<4;group++){ascent=Math.max(ascent,baselineAscent[group]);descent=Math.max(descent,baselineDescent[group]);}if(ascent>=0)cross=Math.max(cross,ascent+descent);
  }
  totalLength=main;int crossMinimum=orientation==VERTICAL?getSuggestedMinimumWidth():getSuggestedMinimumHeight();int resolvedCross=resolveSizeAndState(Math.max(size(cross+crossPadding()),crossMinimum),cs,orientation==VERTICAL?state:state<<MEASURED_HEIGHT_STATE_SHIFT);
  if(orientation==VERTICAL)setMeasuredDimension(resolvedCross,resolvedMain);else setMeasuredDimension(resolvedMain,resolvedCross);
  if(MeasureSpec.getMode(cs)!=MeasureSpec.EXACTLY)for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();if(crossDimension(p)==LayoutParams.MATCH_PARENT)measure(child,MeasureSpec.makeMeasureSpec(mainSize(child),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(size((resolvedCross&MEASURED_SIZE_MASK)-crossPadding()-crossMargins(p)),MeasureSpec.EXACTLY));}
 }
 protected void onLayout(boolean changed,int left,int top,int right,int bottom){
  boolean vertical=orientation==VERTICAL,rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;int absolute=Gravity.getAbsoluteGravity(gravity,getLayoutDirection());
  long free=(vertical?getHeight():getWidth())-mainPadding()-totalLength;
  int major=vertical?gravity&Gravity.VERTICAL_GRAVITY_MASK:absolute&Gravity.HORIZONTAL_GRAVITY_MASK;
  long cursor=(vertical?getPaddingTop():getPaddingLeft())+(major==(vertical?Gravity.BOTTOM:Gravity.RIGHT)?free:major==(vertical?Gravity.CENTER_VERTICAL:Gravity.CENTER_HORIZONTAL)?free/2:0);
  for(int n=0;n<getChildCount();n++){
   View child=getChildAt(!vertical&&rtl?getChildCount()-1-n:n);if(child.getVisibility()==GONE)continue;LayoutParams p=(LayoutParams)child.getLayoutParams();p.resolveLayoutDirection(getLayoutDirection());
   int childGravity=Gravity.getAbsoluteGravity(p.gravity<0?gravity:p.gravity,getLayoutDirection());long x,y;
   if(vertical){cursor+=p.topMargin;y=cursor;int align=childGravity&Gravity.HORIZONTAL_GRAVITY_MASK;long end=(long)getWidth()-getPaddingRight();x=align==Gravity.RIGHT?end-child.getMeasuredWidth()-p.rightMargin:align==Gravity.CENTER_HORIZONTAL?getPaddingLeft()+(end-getPaddingLeft()-child.getMeasuredWidth())/2+p.leftMargin-p.rightMargin:(long)getPaddingLeft()+p.leftMargin;cursor+=(long)child.getMeasuredHeight()+p.bottomMargin;}
   else{cursor+=p.leftMargin;x=cursor;int align=childGravity&Gravity.VERTICAL_GRAVITY_MASK;long end=(long)getHeight()-getPaddingBottom();y=align==Gravity.BOTTOM?end-child.getMeasuredHeight()-p.bottomMargin:align==Gravity.CENTER_VERTICAL?getPaddingTop()+(end-getPaddingTop()-child.getMeasuredHeight())/2+p.topMargin-p.bottomMargin:(long)getPaddingTop()+p.topMargin;cursor+=(long)child.getMeasuredWidth()+p.rightMargin;}
   if(!vertical&&baselineAligned&&p.height!=LayoutParams.MATCH_PARENT){int baseline=child.getBaseline();if(baseline>=0){int align=childGravity&Gravity.VERTICAL_GRAVITY_MASK;if(align==Gravity.TOP)y+=baselineAscent[1]-baseline;else if(align==Gravity.BOTTOM)y-=baselineDescent[2]-(child.getMeasuredHeight()-baseline);}}
   child.layout(extent(x),extent(y),extent(x+child.getMeasuredWidth()),extent(y+child.getMeasuredHeight()));
  }
 }
}
