// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.graphics.Rect;
import android.os.Parcelable;
/** Single-child horizontal viewport using host touch and bounded fling input. */
public class HorizontalScrollView extends FrameLayout {
 private OverScroller scroller;
 private boolean ready,fillViewport,smooth=true,dragging,laidOut;
 private int slop,minVelocity,maxVelocity,pointer=-1;
 private float lastX;
 private VelocityTracker velocity;
 private Integer restoredStart;
 public HorizontalScrollView(Context context){this(context,null);}
 public HorizontalScrollView(Context context,AttributeSet attrs){this(context,attrs,0x01010353);}
 public HorizontalScrollView(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public HorizontalScrollView(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);scroller=new OverScroller(context);ready=true;ViewConfiguration c=ViewConfiguration.get(context);slop=c.getScaledTouchSlop();minVelocity=c.getScaledMinimumFlingVelocity();maxVelocity=c.getScaledMaximumFlingVelocity();setFocusable(true);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);setScrollContainer(true);fillViewport=new shiraz.runtime.XmlAttributes(context,attrs,style,resource).bool("fillViewport",false);}
 @Override public void addView(View child,int index,ViewGroup.LayoutParams params){if(getChildCount()!=0)throw new IllegalStateException("HorizontalScrollView can host only one direct child");super.addView(child,index,params);}
 public void setFillViewport(boolean value){if(fillViewport!=value){fillViewport=value;requestLayout();}}public boolean isFillViewport(){return fillViewport;}
 public void setSmoothScrollingEnabled(boolean value){smooth=value;}public boolean isSmoothScrollingEnabled(){return smooth;}
 @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT);}
 private int viewport(){return Math.max(0,getWidth()-getPaddingLeft()-getPaddingRight());}
 private int range(){if(getChildCount()==0||getChildAt(0).getVisibility()==GONE)return 0;View child=getChildAt(0);LayoutParams p=(LayoutParams)child.getLayoutParams();return extent(Math.max(0L,(long)child.getWidth()+p.leftMargin+p.rightMargin-viewport()));}
 private int verticalRange(){if(getChildCount()==0||getChildAt(0).getVisibility()==GONE)return 0;View child=getChildAt(0);LayoutParams p=(LayoutParams)child.getLayoutParams();return extent(Math.max(0L,(long)child.getHeight()+p.topMargin+p.bottomMargin-getHeight()+getPaddingTop()+getPaddingBottom()));}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int desiredW=getPaddingLeft()+getPaddingRight(),desiredH=getPaddingTop()+getPaddingBottom(),state=0;View child=getChildCount()==0?null:getChildAt(0);
  if(child!=null&&child.getVisibility()!=GONE){LayoutParams p=(LayoutParams)child.getLayoutParams();p.resolveLayoutDirection(getLayoutDirection());int vertical=extent((long)getPaddingTop()+getPaddingBottom()+p.topMargin+p.bottomMargin);child.measure(MeasureSpec.makeMeasureSpec(Math.max(0,p.width),p.width>=0?MeasureSpec.EXACTLY:MeasureSpec.UNSPECIFIED),getChildMeasureSpec(heightSpec,vertical,p.height));desiredW=extent((long)desiredW+child.getMeasuredWidth()+p.leftMargin+p.rightMargin);desiredH=extent((long)desiredH+child.getMeasuredHeight()+p.topMargin+p.bottomMargin);state=child.getMeasuredState();}
  setMeasuredDimension(resolveSizeAndState(Math.max(getSuggestedMinimumWidth(),desiredW),widthSpec,state),resolveSizeAndState(Math.max(getSuggestedMinimumHeight(),desiredH),heightSpec,state<<MEASURED_HEIGHT_STATE_SHIFT));
  if(child!=null&&child.getVisibility()!=GONE){LayoutParams p=(LayoutParams)child.getLayoutParams();int h=Math.max(0,getMeasuredHeight()-getPaddingTop()-getPaddingBottom()-p.topMargin-p.bottomMargin),w=Math.max(0,getMeasuredWidth()-getPaddingLeft()-getPaddingRight()-p.leftMargin-p.rightMargin);boolean stretch=fillViewport&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&child.getMeasuredWidth()<w;if(stretch||p.height==LayoutParams.MATCH_PARENT)child.measure(MeasureSpec.makeMeasureSpec(stretch?w:child.getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(p.height==LayoutParams.MATCH_PARENT?h:child.getMeasuredHeight(),MeasureSpec.EXACTLY));}
 }
 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
  super.onLayout(changed,left,top,right,bottom);
  if(getChildCount()!=0){View child=getChildAt(0);LayoutParams p=(LayoutParams)child.getLayoutParams();if(child.getVisibility()!=GONE&&child.getWidth()+p.leftMargin+p.rightMargin>viewport()){int x=getPaddingLeft()+p.leftMargin;child.layout(x,child.getTop(),extent((long)x+child.getMeasuredWidth()),child.getBottom());}}
  int x=getScrollX();if(restoredStart!=null)x=getLayoutDirection()==LAYOUT_DIRECTION_RTL?range()-restoredStart:restoredStart;else if(!laidOut&&getLayoutDirection()==LAYOUT_DIRECTION_RTL)x=range()-x;
  scrollTo(x,getScrollY());restoredStart=null;laidOut=true;
 }
 @Override public void scrollTo(int x,int y){if(!ready){super.scrollTo(x,y);return;}super.scrollTo(Math.max(0,Math.min(range(),x)),Math.max(0,Math.min(verticalRange(),y)));}
 @Override protected int computeHorizontalScrollRange(){return viewport()+range();}
 @Override protected int computeHorizontalScrollExtent(){return viewport();}
 @Override protected int computeVerticalScrollRange(){return Math.max(0,getHeight()-getPaddingTop()-getPaddingBottom())+verticalRange();}
 @Override protected int computeVerticalScrollExtent(){return Math.max(0,getHeight()-getPaddingTop()-getPaddingBottom());}
 @Override protected void onOverScrolled(int x,int y,boolean clampedX,boolean clampedY){scrollTo(x,y);}
 @Override public boolean canScrollHorizontally(int direction){return direction<0?getScrollX()>0:getScrollX()<range();}
 public int getMaxScrollAmount(){return getWidth()/2;}
 public void smoothScrollTo(int x,int y){if(scroller==null)return;scroller.abortAnimation();scroller.startScroll(getScrollX(),getScrollY(),Math.max(0,Math.min(range(),x))-getScrollX(),Math.max(0,Math.min(verticalRange(),y))-getScrollY(),250);postInvalidateOnAnimation();}
 public void smoothScrollBy(int x,int y){smoothScrollTo((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)getScrollX()+x)),(int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)getScrollY()+y)));}
 public void fling(int velocityX){if(scroller==null||getChildCount()==0)return;scroller.fling(getScrollX(),getScrollY(),Math.max(-maxVelocity,Math.min(maxVelocity,velocityX)),0,0,range(),0,verticalRange());postInvalidateOnAnimation();}
 @Override public void computeScroll(){if(scroller!=null&&scroller.computeScrollOffset()){scrollTo(scroller.getCurrX(),scroller.getCurrY());if(!scroller.isFinished())postInvalidateOnAnimation();}}
 private void moveTo(int x){if(smooth)smoothScrollTo(x,getScrollY());else scrollTo(x,getScrollY());}
 public boolean fullScroll(int direction){if(direction!=FOCUS_LEFT&&direction!=FOCUS_RIGHT)return false;int target=direction==FOCUS_LEFT?0:range();boolean changed=target!=getScrollX();moveTo(target);return changed;}
 public boolean pageScroll(int direction){if(direction!=FOCUS_LEFT&&direction!=FOCUS_RIGHT)return false;int target=Math.max(0,Math.min(range(),getScrollX()+(direction==FOCUS_LEFT?-viewport():viewport())));boolean changed=target!=getScrollX();moveTo(target);return changed;}
 public boolean arrowScroll(int direction){if(direction!=FOCUS_LEFT&&direction!=FOCUS_RIGHT)return false;int target=Math.max(0,Math.min(range(),getScrollX()+(direction==FOCUS_LEFT?-getMaxScrollAmount():getMaxScrollAmount())));boolean changed=target!=getScrollX();moveTo(target);return changed;}
 public boolean executeKeyEvent(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN||!isEnabled())return false;switch(event.getKeyCode()){case KeyEvent.KEYCODE_DPAD_LEFT:return event.isAltPressed()?fullScroll(FOCUS_LEFT):arrowScroll(FOCUS_LEFT);case KeyEvent.KEYCODE_DPAD_RIGHT:return event.isAltPressed()?fullScroll(FOCUS_RIGHT):arrowScroll(FOCUS_RIGHT);case KeyEvent.KEYCODE_PAGE_UP:return pageScroll(FOCUS_LEFT);case KeyEvent.KEYCODE_PAGE_DOWN:return pageScroll(FOCUS_RIGHT);case KeyEvent.KEYCODE_MOVE_HOME:return fullScroll(FOCUS_LEFT);case KeyEvent.KEYCODE_MOVE_END:return fullScroll(FOCUS_RIGHT);case KeyEvent.KEYCODE_SPACE:return pageScroll(event.isShiftPressed()?FOCUS_LEFT:FOCUS_RIGHT);default:return false;}}
 @Override public boolean dispatchKeyEvent(KeyEvent event){return super.dispatchKeyEvent(event)||executeKeyEvent(event);}
 @Override public boolean shouldDelayChildPressedState(){return true;}
 private void begin(MotionEvent event){pointer=event.getPointerId(0);lastX=event.getX(0);if(velocity==null)velocity=VelocityTracker.obtain();else velocity.clear();velocity.addMovement(event);dragging=!scroller.isFinished();scroller.abortAnimation();}
 private void finish(){dragging=false;pointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
 private void pointerUp(MotionEvent event){int index=event.getActionIndex();if(event.getPointerId(index)==pointer){int next=index==0?1:0;if(next<event.getPointerCount()){pointer=event.getPointerId(next);lastX=event.getX(next);if(velocity!=null)velocity.clear();}else pointer=-1;}}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){if(!isEnabled()||getChildCount()==0)return false;switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:begin(event);break;case MotionEvent.ACTION_MOVE:int index=event.findPointerIndex(pointer);if(index>=0&&Math.abs(event.getX(index)-lastX)>slop){dragging=true;lastX=event.getX(index);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(velocity!=null)velocity.addMovement(event);break;case MotionEvent.ACTION_POINTER_UP:pointerUp(event);break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:finish();break;}return dragging;}
 @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled()||getChildCount()==0)return false;if(event.getActionMasked()==MotionEvent.ACTION_DOWN){begin(event);return true;}if(velocity!=null)velocity.addMovement(event);switch(event.getActionMasked()){case MotionEvent.ACTION_MOVE:int index=event.findPointerIndex(pointer);if(index<0)return false;float x=event.getX(index),delta=lastX-x;if(!dragging&&Math.abs(delta)>slop){dragging=true;delta-=Math.signum(delta)*slop;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(dragging){scrollBy(Math.round(delta),0);lastX=x;}break;case MotionEvent.ACTION_POINTER_DOWN:pointer=event.getPointerId(event.getActionIndex());lastX=event.getX(event.getActionIndex());break;case MotionEvent.ACTION_POINTER_UP:pointerUp(event);break;case MotionEvent.ACTION_UP:if(dragging&&velocity!=null){velocity.computeCurrentVelocity(1000,maxVelocity);int speed=(int)-velocity.getXVelocity(pointer);if(Math.abs(speed)>=minVelocity)fling(speed);}finish();break;case MotionEvent.ACTION_CANCEL:finish();break;}return true;}
 @Override protected void onDetachedFromWindow(){if(scroller!=null)scroller.abortAnimation();finish();super.onDetachedFromWindow();}

 protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){int left=getScrollX()+getPaddingLeft(),right=getScrollX()+getWidth()-getPaddingRight();int delta=rect.right>right?(rect.width()>viewport()?rect.left-left:rect.right-right):rect.left<left?(rect.width()>viewport()?rect.right-right:rect.left-left):0;return Math.max(-getScrollX(),Math.min(range()-getScrollX(),delta));}
 public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){Rect rect=new Rect(rectangle);View cursor=child;while(cursor!=this){rect.offset(cursor.getLeft()-cursor.getScrollX(),cursor.getTop()-cursor.getScrollY());if(!(cursor.getParent() instanceof View))return false;cursor=(View)cursor.getParent();}int delta=computeScrollDeltaToGetChildRectOnScreen(rect);if(delta==0)return false;if(immediate)scrollBy(delta,0);else smoothScrollBy(delta,0);return true;}
 private static final class SavedState extends BaseSavedState {final int start;SavedState(Parcelable parent,int value){super(parent);start=value;}}
 @Override protected Parcelable onSaveInstanceState(){return new SavedState(super.onSaveInstanceState(),getLayoutDirection()==LAYOUT_DIRECTION_RTL?range()-getScrollX():getScrollX());}
 @Override protected void onRestoreInstanceState(Parcelable state){if(!(state instanceof SavedState)){super.onRestoreInstanceState(state);return;}SavedState saved=(SavedState)state;super.onRestoreInstanceState(saved.getSuperState());restoredStart=saved.start;requestLayout();}
}
