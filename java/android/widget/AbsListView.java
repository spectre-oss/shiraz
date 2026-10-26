// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.database.DataSetObserver;
import java.util.*;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
/** Bounded adapter viewport. Rows are positioned locally, not via View.scrollY. */
public abstract class AbsListView extends AdapterView<ListAdapter> {
 public interface SelectionBoundsAdjuster {void adjustListItemSelectionBounds(android.graphics.Rect bounds);}
 public interface OnScrollListener {int SCROLL_STATE_IDLE=0,SCROLL_STATE_TOUCH_SCROLL=1,SCROLL_STATE_FLING=2;void onScrollStateChanged(AbsListView view,int state);void onScroll(AbsListView view,int first,int visible,int total);}
 public static class LayoutParams extends ViewGroup.LayoutParams {public int viewType;public LayoutParams(int w,int h){super(w,h);}public LayoutParams(Context c,AttributeSet a){super(c,a);}public LayoutParams(ViewGroup.LayoutParams p){super(p);}}
 // Rows render directly; retain the legacy bitmap-cache hint without changing their background.
 private int cacheColorHint;
 public void setCacheColorHint(int color){cacheColorHint=color;}
 public int getCacheColorHint(){return cacheColorHint;}
 private final Rect selectorPadding=new Rect();
 public int getListPaddingLeft(){return getPaddingLeft()+selectorPadding.left;}
 public int getListPaddingTop(){return getPaddingTop()+selectorPadding.top;}
 public int getListPaddingRight(){return getPaddingRight()+selectorPadding.right;}
 public int getListPaddingBottom(){return getPaddingBottom()+selectorPadding.bottom;}
 private Drawable selector;private boolean selectorOnTop,touchSelection;
 public void setDrawSelectorOnTop(boolean value){selectorOnTop=value;invalidate();}
 public void setSelector(int resource){setSelector(resource==0?null:getResources().getDrawable(resource,getContext().getTheme()));}
 public void setSelector(Drawable value){if(selector==value)return;if(selector!=null){selector.setCallback(null);unscheduleDrawable(selector);}selector=value;selectorPadding.set(0,0,0,0);if(value!=null)value.getPadding(selectorPadding);if(value!=null){value.setCallback(this);value.setState(getDrawableState());value.setVisible(getVisibility()==VISIBLE,false);}requestLayout();invalidate();}
 public Drawable getSelector(){return selector;}
 public static final int CHOICE_MODE_NONE=0,CHOICE_MODE_SINGLE=1,CHOICE_MODE_MULTIPLE=2,CHOICE_MODE_MULTIPLE_MODAL=3;
 private int choiceMode=CHOICE_MODE_NONE;
 private final android.util.SparseBooleanArray checked=new android.util.SparseBooleanArray();
 public void setChoiceMode(int mode){if(mode<CHOICE_MODE_NONE||mode>CHOICE_MODE_MULTIPLE_MODAL)throw new IllegalArgumentException("choice mode");if(choiceMode==mode)return;choiceMode=mode;clearChoices();if(mode!=CHOICE_MODE_NONE)setLongClickable(mode==CHOICE_MODE_MULTIPLE_MODAL||isLongClickable());}
 public int getChoiceMode(){return choiceMode;}
 public void clearChoices(){checked.clear();invalidate();}
 public void setItemChecked(int position,boolean value){if(choiceMode==CHOICE_MODE_NONE)return;if(choiceMode==CHOICE_MODE_SINGLE){checked.clear();if(value)checked.put(position,true);}else if(value)checked.put(position,true);else checked.delete(position);invalidate();}
 public boolean isItemChecked(int position){return choiceMode!=CHOICE_MODE_NONE&&checked.get(position);}
 public int getCheckedItemPosition(){if(choiceMode!=CHOICE_MODE_SINGLE||checked.size()!=1)return INVALID_POSITION;return checked.keyAt(0);}
 public android.util.SparseBooleanArray getCheckedItemPositions(){return choiceMode==CHOICE_MODE_NONE?null:checked;}
 public int getCheckedItemCount(){return checked.size();}
 public long[] getCheckedItemIds(){if(adapter==null||!adapter.hasStableIds())return new long[0];long[] ids=new long[checked.size()];int n=0;for(int i=0;i<checked.size();i++)if(checked.valueAt(i))ids[n++]=adapter.getItemId(checked.keyAt(i));if(n==ids.length)return ids;return java.util.Arrays.copyOf(ids,n);}
 @Override protected boolean verifyDrawable(Drawable d){return d==selector&&d!=null||super.verifyDrawable(d);}
 @Override protected void drawableStateChanged(){super.drawableStateChanged();if(selector!=null){selector.setState(getDrawableState());invalidate();}}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(selector!=null)selector.setVisible(isShown(),false);}
 @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(selector!=null)selector.jumpToCurrentState();}
 @Override public void drawableHotspotChanged(float x,float y){super.drawableHotspotChanged(x,y);if(selector!=null)selector.setHotspot(x,y);}
 @Override protected void onDetachedFromWindow(){setPressed(false);dragging=false;super.onDetachedFromWindow();}
 private void drawSelector(Canvas canvas){if(selector==null||!isEnabled()||dragging)return;int position=isPressed()?downPosition:!touchSelection&&isFocused()?getSelectedItemPosition():-1;if(position<0||position>=count()||!adapter.isEnabled(position))return;View row=getChildAt(position-firstPosition);if(row==null)return;Rect padding=new Rect();selector.getPadding(padding);selector.setState(getDrawableState());selector.setLayoutDirection(getLayoutDirection());selector.setBounds(row.getLeft()-padding.left,row.getTop()-padding.top,row.getRight()+padding.right,row.getBottom()+padding.bottom);int saved=canvas.save();try{if(getClipToPadding())canvas.clipRect(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());selector.draw(canvas);}finally{canvas.restoreToCount(saved);}}
 @Override protected void dispatchDraw(Canvas canvas){if(!selectorOnTop)drawSelector(canvas);super.dispatchDraw(canvas);if(selectorOnTop)drawSelector(canvas);}
 private ListAdapter adapter;private OnScrollListener listener;private boolean dirty=true,invalid,laying;
 private boolean measuring;private int measureWidthSpec;
 private int lastPaddingLeft,lastPaddingTop,lastPaddingRight,lastPaddingBottom,lastGap;
 private int offset,lastWidth=-1,lastHeight=-1;private float downY,lastY;private boolean dragging;private int downPosition=-1;
 private final IdentityHashMap<View,Integer> rowTypes=new IdentityHashMap<View,Integer>();
 private final HashMap<Integer,ArrayDeque<View>> scrap=new HashMap<Integer,ArrayDeque<View>>();private int scrapCount;
 private final DataSetObserver observer=new DataSetObserver(){public void onChanged(){invalid=false;dirty=true;shirazUpdateEmpty();requestLayout();invalidate();}public void onInvalidated(){invalid=true;firstPosition=0;offset=0;clearRows(false);shirazSelect(-1);dirty=true;shirazUpdateEmpty(true);requestLayout();invalidate();}};
 public AbsListView(Context c){this(c,null);}public AbsListView(Context c,AttributeSet a){this(c,a,0);}public AbsListView(Context c,AttributeSet a,int s){this(c,a,s,0);}public AbsListView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);setClickable(true);shiraz.runtime.XmlAttributes attrs=new shiraz.runtime.XmlAttributes(c,a,s,r);if(attrs.has("listSelector"))setSelector(attrs.drawable("listSelector"));setDrawSelectorOnTop(attrs.bool("drawSelectorOnTop",false));}
 protected final void shirazDataChanged(){observer.onChanged();}
 public ListAdapter getAdapter(){return adapter;}
 @Override public int getCount(){return count();}
 @Override public void setEmptyView(View view){super.setEmptyView(view);shirazUpdateEmpty(count()==0);}
 public void setAdapter(ListAdapter value){setPressed(false);touchSelection=false;if(adapter!=null)adapter.unregisterDataSetObserver(observer);clearRows(false);scrap.clear();scrapCount=0;adapter=value;invalid=false;firstPosition=0;offset=0;dirty=true;clearChoices();shirazSelect(-1);if(value!=null)value.registerDataSetObserver(observer);shirazUpdateEmpty();requestLayout();invalidate();}
 private int count(){int n=invalid||adapter==null?0:adapter.getCount();if(n<0||n>1000000)throw new IllegalArgumentException("List item count bound");return n;}
 public View getSelectedView(){return getChildAt(getSelectedItemPosition()-firstPosition);}
 public void setSelection(int position){setSelectionFromTop(position,0);}
 protected final void shirazClearTouchSelection(){touchSelection=false;setPressed(false);}
 public void setSelectionFromTop(int position,int y){shirazClearTouchSelection();if(Math.abs((long)y)>8192)throw new IllegalArgumentException("List selection offset");int n=count();firstPosition=n==0?0:Math.max(0,Math.min(n-1,position));offset=y;dirty=true;requestLayout();if(getWidth()>0&&getHeight()>0)layoutRows();shirazSelect(position);invalidate();}
 public void setOnScrollListener(OnScrollListener value){listener=value;if(value!=null)value.onScroll(this,firstPosition,getChildCount(),count());}
 protected int rowGap(){return 0;}
 private void recycle(View v,Integer type){if(type==null||type<0||scrapCount>=256)return;ArrayDeque<View> pool=scrap.get(type);if(pool==null){pool=new ArrayDeque<View>();scrap.put(type,pool);}pool.addLast(v);scrapCount++;}
 private void clearRows(boolean reuse){while(getChildCount()>0){View v=getChildAt(getChildCount()-1);Integer type=rowTypes.remove(v);shirazRemoveRow(getChildCount()-1);if(reuse)recycle(v,type);}}
 private View obtain(int position){int types=adapter.getViewTypeCount(),type=adapter.getItemViewType(position);if(types<1||types>256||type>=types||type< -2)throw new IllegalArgumentException("List view type");ArrayDeque<View> pool=scrap.get(type);View old=pool==null?null:pool.pollFirst();if(old!=null)scrapCount--;View v=adapter.getView(position,old,this);boolean attachedFixed=measuring&&v!=null&&type==-2&&v.getParent()==this&&Integer.valueOf(-2).equals(rowTypes.get(v));if(v==null||v.getParent()!=null&&!attachedFixed)throw new IllegalStateException("Adapter must return an unparented row");if(v!=old&&old!=null)recycle(old,type);ViewGroup.LayoutParams params=v.getLayoutParams();if(params==null)params=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);if(!attachedFixed)v.setLayoutParams(params);int parentWidth=measuring?measureWidthSpec:MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY);int width=getChildMeasureSpec(parentWidth,getListPaddingLeft()+getListPaddingRight(),params.width);v.measure(width,MeasureSpec.makeMeasureSpec(Math.max(0,params.height),params.height>=0?MeasureSpec.EXACTLY:MeasureSpec.UNSPECIFIED));if(v.getMeasuredHeight()<0||v.getMeasuredHeight()>65536)throw new IllegalArgumentException("List row height bound");rowTypes.put(v,type);return v;}
 private void discard(View v){if(v.getParent()==this)return;recycle(v,rowTypes.remove(v));}
 protected final void layoutRows(){if(laying)return;laying=true;try{int n=count();clearRows(true);if(n==0){firstPosition=0;offset=0;dirty=false;shirazSelect(-1);return;}firstPosition=Math.min(firstPosition,n-1);int top=getListPaddingTop(),bottom=getHeight()-getListPaddingBottom(),gap=rowGap(),steps=0;
  while(offset>0&&firstPosition>0){if(++steps>4096)throw new IllegalStateException("List scroll work bound");View previous=obtain(--firstPosition);offset-=previous.getMeasuredHeight()+gap;discard(previous);}if(firstPosition==0)offset=Math.min(0,offset);
  int y=top+offset;while(firstPosition<n-1){if(++steps>4096)throw new IllegalStateException("List scroll work bound");View v=obtain(firstPosition);int h=v.getMeasuredHeight()+gap;if(y+h>top){discard(v);break;}y+=h;firstPosition++;discard(v);}offset=y-top;
  for(int p=firstPosition;p<n&&y<bottom;p++){if(getChildCount()>=256)throw new IllegalStateException("List visible row bound");View v=obtain(p);shirazAddRow(v,-1,v.getLayoutParams());v.layout(getListPaddingLeft(),y,getListPaddingLeft()+v.getMeasuredWidth(),y+v.getMeasuredHeight());y+=v.getMeasuredHeight()+gap;}
  int end=getChildCount()==0?top:getChildAt(getChildCount()-1).getBottom();if(firstPosition+getChildCount()==n&&end<bottom){int shift=bottom-end;while(firstPosition>0&&offset+shift>0){if(getChildCount()>=256)throw new IllegalStateException("List visible row bound");View v=obtain(--firstPosition);int h=v.getMeasuredHeight()+gap;offset-=h;shirazAddRow(v,0,v.getLayoutParams());v.layout(getListPaddingLeft(),top+offset,getListPaddingLeft()+v.getMeasuredWidth(),top+offset+v.getMeasuredHeight());}shift=Math.min(shift,-offset);for(int i=0;i<getChildCount();i++){View v=getChildAt(i);v.layout(v.getLeft(),v.getTop()+shift,v.getRight(),v.getBottom()+shift);}offset+=shift;}
  dirty=false;lastWidth=getWidth();lastHeight=getHeight();lastPaddingLeft=getListPaddingLeft();lastPaddingTop=getListPaddingTop();lastPaddingRight=getListPaddingRight();lastPaddingBottom=getListPaddingBottom();lastGap=rowGap();if(getSelectedItemPosition()>=n)shirazSelect(-1);if(listener!=null)listener.onScroll(this,firstPosition,getChildCount(),n);onScrollChanged(0,0,0,0);
 }finally{laying=false;}}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){if(dirty||lastWidth!=getWidth()||lastHeight!=getHeight()||lastPaddingLeft!=getListPaddingLeft()||lastPaddingTop!=getListPaddingTop()||lastPaddingRight!=getListPaddingRight()||lastPaddingBottom!=getListPaddingBottom()||lastGap!=rowGap()||!layoutRetainedRows())layoutRows();}
 private boolean layoutRetainedRows(){
  for(int i=0;i<getChildCount();i++){
   View row=getChildAt(i);ViewGroup.LayoutParams params=row.getLayoutParams();
   row.measure(getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getWidth(),MeasureSpec.EXACTLY),getListPaddingLeft()+getListPaddingRight(),params.width),MeasureSpec.makeMeasureSpec(Math.max(0,params.height),params.height>=0?MeasureSpec.EXACTLY:MeasureSpec.UNSPECIFIED));
   if(row.getMeasuredWidth()!=row.getWidth()||row.getMeasuredHeight()!=row.getHeight())return false;
  }
  for(int i=0;i<getChildCount();i++){View row=getChildAt(i);row.layout(row.getLeft(),row.getTop(),row.getRight(),row.getBottom());}
  return true;
 }
 // Measuring unchanged rows must not detach popup anchors on the next layout pass.
 @Override protected void onMeasure(int w,int h){int width=getListPaddingLeft()+getListPaddingRight(),height=getListPaddingTop()+getListPaddingBottom();int mode=MeasureSpec.getMode(h),limit=MeasureSpec.getSize(h),n=count();measuring=true;measureWidthSpec=w;try{if(MeasureSpec.getMode(w)!=MeasureSpec.EXACTLY||mode!=MeasureSpec.EXACTLY){for(int p=0;p<n;p++){if(p>=256)throw new IllegalStateException("List measure work bound");View row=obtain(p);width=Math.max(width,row.getMeasuredWidth()+getListPaddingLeft()+getListPaddingRight());height=Math.addExact(height,row.getMeasuredHeight()+(p==0?0:rowGap()));discard(row);if(mode==MeasureSpec.UNSPECIFIED||mode==MeasureSpec.EXACTLY||height>=limit)break;}}setMeasuredDimension(resolveSize(Math.max(getSuggestedMinimumWidth(),width),w),resolveSize(Math.max(getSuggestedMinimumHeight(),height),h));}finally{measuring=false;}}
 public void scrollListBy(int pixels){if(Math.abs((long)pixels)>8192)throw new IllegalArgumentException("List scroll delta bound");offset-=pixels;dirty=true;layoutRows();invalidate();}
 public boolean canScrollList(int direction){if(getChildCount()==0)return false;return direction<0?firstPosition>0||getChildAt(0).getTop()<getListPaddingTop():direction>0&&(getLastVisiblePosition()<count()-1||getChildAt(getChildCount()-1).getBottom()>getHeight()-getListPaddingBottom());}
 @Override public boolean canScrollVertically(int direction){return canScrollList(direction);}
 public int pointToPosition(int x,int y){for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(x>=v.getLeft()&&x<v.getRight()&&y>=v.getTop()&&y<v.getBottom())return firstPosition+i;}return INVALID_POSITION;}
 public long pointToRowId(int x,int y){return getItemIdAtPosition(pointToPosition(x,y));}
 private void down(MotionEvent e){downY=lastY=e.getY();downPosition=pointToPosition((int)e.getX(),(int)e.getY());dragging=false;touchSelection=true;setPressed(downPosition>=0&&downPosition<count()&&adapter.isEnabled(downPosition));drawableHotspotChanged(e.getX(),e.getY());}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(!isEnabled())return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){down(e);if(choiceMode!=CHOICE_MODE_NONE)return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){if(!dragging&&listener!=null)listener.onScrollStateChanged(this,OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);dragging=true;setPressed(false);return true;}return dragging||(choiceMode!=CHOICE_MODE_NONE&&e.getActionMasked()!=MotionEvent.ACTION_DOWN&&downPosition>=0);}
 @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_SCROLL&&isEnabled()&&!dragging){float value=event.getAxisValue(MotionEvent.AXIS_VSCROLL);if(value!=0){scrollListBy(Math.max(-8192,Math.min(8192,Math.round(-value*ViewConfiguration.get(getContext()).getScaledVerticalScrollFactor()))));return true;}}return super.onGenericMotionEvent(event);}
 @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:down(e);return true;case MotionEvent.ACTION_MOVE:if(!dragging&&Math.abs(e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){dragging=true;setPressed(false);if(listener!=null)listener.onScrollStateChanged(this,1);}if(dragging){scrollListBy(Math.max(-8192,Math.min(8192,Math.round(lastY-e.getY()))));if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}lastY=e.getY();return true;case MotionEvent.ACTION_UP:int position=pointToPosition((int)e.getX(),(int)e.getY());if(!dragging&&position==downPosition&&position>=0&&adapter!=null&&adapter.isEnabled(position)){shirazSelect(position);View row=getChildAt(position-firstPosition);if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT")))System.err.println("SHIRAZ_LIST_CLICK position="+position+" row="+(row==null?"null":row.getClass().getName())+" hasListener="+(getOnItemClickListener()!=null));performItemClick(row,position,adapter.getItemId(position));}case MotionEvent.ACTION_CANCEL:dragging=false;setPressed(false);if(listener!=null)listener.onScrollStateChanged(this,0);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;default:return true;}}
}
