// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import java.util.*;

/** Small adapter grids with real cell geometry. Larger datasets require viewport recycling. */
public class GridView extends AbsListView {
 public static final int AUTO_FIT=-1,NO_STRETCH=0,STRETCH_SPACING=1,STRETCH_COLUMN_WIDTH=2,STRETCH_SPACING_UNIFORM=3;
 private int requestedColumns=AUTO_FIT,requestedWidth,horizontal,vertical,stretch=STRETCH_COLUMN_WIDTH,gravity=Gravity.START;
 private int columns=1,columnWidth,spacing,leading,contentHeight,scroll,preparedWidth=-1;
 private int pendingSelection=-1,pendingY;
 private int[] rowTops=new int[0],rowHeights=new int[0];
 private final IdentityHashMap<View,Integer> types=new IdentityHashMap<View,Integer>();
 private OnScrollListener scrollListener;
 public GridView(Context c){this(c,null);}public GridView(Context c,AttributeSet a){this(c,a,0);}public GridView(Context c,AttributeSet a,int s){this(c,a,s,0);}
 public GridView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);shiraz.runtime.XmlAttributes attrs=new shiraz.runtime.XmlAttributes(c,a,s,r);setNumColumns(attrs.integer("numColumns",AUTO_FIT));setColumnWidth(attrs.dimension("columnWidth",0,false));setHorizontalSpacing(attrs.dimension("horizontalSpacing",0,false));setVerticalSpacing(attrs.dimension("verticalSpacing",0,false));setStretchMode(attrs.integer("stretchMode",STRETCH_COLUMN_WIDTH));setGravity(attrs.integer("gravity",Gravity.START));}
 private void changed(){preparedWidth=-1;requestLayout();invalidate();}
 private static int dimension(int value){if(value<0||value>8192)throw new IllegalArgumentException("Grid dimension");return value;}
 public void setNumColumns(int value){if(value!=AUTO_FIT&&(value<1||value>256))throw new IllegalArgumentException("Grid column count");if(requestedColumns!=value){requestedColumns=value;changed();}}
 public int getNumColumns(){return preparedWidth<0?requestedColumns:columns;}
 public void setColumnWidth(int value){value=dimension(value);if(requestedWidth!=value){requestedWidth=value;changed();}}
 public int getColumnWidth(){return columnWidth;}
 public int getRequestedColumnWidth(){return requestedWidth;}
 public void setHorizontalSpacing(int value){value=dimension(value);if(horizontal!=value){horizontal=value;changed();}}
 public int getHorizontalSpacing(){return spacing;}public int getRequestedHorizontalSpacing(){return horizontal;}
 public void setVerticalSpacing(int value){value=dimension(value);if(vertical!=value){vertical=value;changed();}}
 public int getVerticalSpacing(){return vertical;}
 public void setStretchMode(int value){if(value<0||value>3)throw new IllegalArgumentException("Grid stretch mode");if(stretch!=value){stretch=value;changed();}}
 public int getStretchMode(){return stretch;}
 public void setGravity(int value){if(gravity!=value){gravity=value;changed();}}public int getGravity(){return gravity;}
 @Override public void setAdapter(ListAdapter adapter){types.clear();scroll=0;pendingSelection=-1;preparedWidth=-1;super.setAdapter(adapter);}
 private void geometry(int width){
  int available=Math.max(0,width-getPaddingLeft()-getPaddingRight());
  columns=requestedColumns==AUTO_FIT?(requestedWidth>0?Math.max(1,(available+horizontal)/(requestedWidth+horizontal)):1):requestedColumns;
  columns=Math.min(256,columns);columnWidth=requestedWidth>0?requestedWidth:Math.max(0,(available-(columns-1)*horizontal)/columns);spacing=horizontal;leading=0;
  int spare=available-columns*columnWidth-(columns-1)*horizontal;
  if(stretch==STRETCH_COLUMN_WIDTH)columnWidth=Math.max(0,columnWidth+spare/columns);
  else if(stretch==STRETCH_SPACING&&columns>1)spacing=Math.max(0,horizontal+spare/(columns-1));
  else if(stretch==STRETCH_SPACING_UNIFORM){spacing=Math.max(0,(available-columns*columnWidth)/(columns+1));leading=spacing;}
 }
 private void prepare(int width){
  int count=getCount();if(count>256)throw new UnsupportedOperationException("GridView datasets above 256 items require viewport recycling");geometry(width);
  HashMap<Integer,ArrayDeque<View>> recycled=new HashMap<Integer,ArrayDeque<View>>();
  for(int i=getChildCount()-1;i>=0;i--){View child=getChildAt(i);Integer type=types.get(child);shirazRemoveRow(i);if(type!=null&&type>=0){ArrayDeque<View> pool=recycled.get(type);if(pool==null){pool=new ArrayDeque<View>();recycled.put(type,pool);}pool.addLast(child);}}
  types.clear();rowHeights=new int[(count+columns-1)/columns];rowTops=new int[rowHeights.length];ListAdapter adapter=getAdapter();
  int typeCount=adapter==null?1:adapter.getViewTypeCount();if(typeCount<1||typeCount>256)throw new IllegalArgumentException("Grid view type count");
  for(int i=0;i<count;i++){
   int type=adapter.getItemViewType(i);if(type< -1||type>=typeCount)throw new IllegalArgumentException("Grid view type");ArrayDeque<View> pool=recycled.get(type);View old=pool==null?null:pool.pollFirst(),child=adapter.getView(i,old,this);
   if(child==null||child.getParent()!=null)throw new IllegalStateException("Grid adapter must return an unparented cell");ViewGroup.LayoutParams params=child.getLayoutParams();if(params==null)params=new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
   child.measure(getChildMeasureSpec(MeasureSpec.makeMeasureSpec(columnWidth,MeasureSpec.EXACTLY),0,params.width),MeasureSpec.makeMeasureSpec(Math.max(0,params.height),params.height>=0?MeasureSpec.EXACTLY:MeasureSpec.UNSPECIFIED));
   if(child.getMeasuredHeight()<1||child.getMeasuredHeight()>65536)throw new IllegalArgumentException("Grid cell height");types.put(child,type);shirazAddRow(child,-1,params);rowHeights[i/columns]=Math.max(rowHeights[i/columns],child.getMeasuredHeight());
  }
  contentHeight=getPaddingTop()+getPaddingBottom();for(int row=0;row<rowHeights.length;row++){rowTops[row]=contentHeight-getPaddingBottom();contentHeight=Math.addExact(contentHeight,rowHeights[row]+(row+1<rowHeights.length?vertical:0));}preparedWidth=width;
 }
 @Override protected void onMeasure(int w,int h){int desired=Math.max(getSuggestedMinimumWidth(),getPaddingLeft()+getPaddingRight()+Math.max(1,requestedColumns==AUTO_FIT?1:requestedColumns)*Math.max(1,requestedWidth));int width=MeasureSpec.getMode(w)==MeasureSpec.UNSPECIFIED?desired:MeasureSpec.getSize(w);prepare(width);setMeasuredDimension(resolveSize(width,w),resolveSize(Math.max(getSuggestedMinimumHeight(),contentHeight),h));}
 private void cells(){scroll=Math.max(0,Math.min(scroll,Math.max(0,contentHeight-getHeight())));boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;int alignment=Gravity.getAbsoluteGravity(gravity,getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK;
  for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int column=i%columns,x=rtl?getWidth()-getPaddingRight()-leading-columnWidth-column*(columnWidth+spacing):getPaddingLeft()+leading+column*(columnWidth+spacing);if(alignment==Gravity.RIGHT)x+=columnWidth-child.getMeasuredWidth();else if(alignment==Gravity.CENTER_HORIZONTAL)x+=(columnWidth-child.getMeasuredWidth())/2;int y=rowTops[i/columns]-scroll;child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());}invalidate();notifyScroll();}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){if(preparedWidth!=getWidth())prepare(getWidth());if(pendingSelection>=0&&getCount()>0){scroll=Math.max(0,rowTops[Math.min(getCount()-1,pendingSelection)/columns]-getPaddingTop()-pendingY);pendingSelection=-1;}if(getSelectedItemPosition()>=getCount())shirazSelect(-1);cells();}
 @Override public void scrollListBy(int pixels){if(Math.abs((long)pixels)>8192)throw new IllegalArgumentException("Grid scroll delta");scroll=Math.max(0,(int)Math.min(Integer.MAX_VALUE,(long)scroll+pixels));cells();}
 @Override public boolean canScrollList(int direction){return direction<0?scroll>0:direction>0&&scroll<Math.max(0,contentHeight-getHeight());}
 @Override public void setSelectionFromTop(int position,int y){if(Math.abs((long)y)>8192)throw new IllegalArgumentException("Grid selection offset");shirazClearTouchSelection();if(getWidth()==0||preparedWidth<0){pendingSelection=position;pendingY=y;shirazSelect(position);requestLayout();return;}int n=getCount();if(getWidth()>0&&preparedWidth!=getWidth())prepare(getWidth());if(n>0&&rowTops.length>0){int item=Math.max(0,Math.min(n-1,position));scroll=Math.max(0,rowTops[item/columns]-getPaddingTop()-y);cells();}shirazSelect(position);invalidate();}
 @Override public int getFirstVisiblePosition(){for(int i=0;i<getChildCount();i++)if(getChildAt(i).getBottom()>getPaddingTop())return i;return 0;}
 @Override public int getLastVisiblePosition(){for(int i=getChildCount()-1;i>=0;i--)if(getChildAt(i).getTop()<getHeight()-getPaddingBottom())return i;return -1;}
 @Override public int pointToPosition(int x,int y){if(x<getPaddingLeft()||x>=getWidth()-getPaddingRight()||y<getPaddingTop()||y>=getHeight()-getPaddingBottom())return INVALID_POSITION;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(x>=child.getLeft()&&x<child.getRight()&&y>=child.getTop()&&y<child.getBottom())return i;}return INVALID_POSITION;}
 private void notifyScroll(){if(scrollListener!=null){int first=getFirstVisiblePosition(),last=getLastVisiblePosition();scrollListener.onScroll(this,first,Math.max(0,last-first+1),getCount());}}
 @Override public void setOnScrollListener(final OnScrollListener listener){scrollListener=listener;super.setOnScrollListener(listener==null?null:new OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){listener.onScrollStateChanged(view,state);}public void onScroll(AbsListView view,int first,int visible,int total){notifyScroll();}});}
}
