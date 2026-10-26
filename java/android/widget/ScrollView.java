// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.graphics.Rect;
import android.os.Parcelable;
/** Single-child vertical viewport with bounded scrolling and host touch/fling input. */
public class ScrollView extends FrameLayout {
 private OverScroller scroller;
 private boolean ready,fillViewport,smooth=true,dragging;
 private int slop,minVelocity,maxVelocity,pointer=-1;
 private float lastY;
 private VelocityTracker velocity;
 private Integer restoredY;
 public ScrollView(Context context){this(context,null);}
 public ScrollView(Context context,AttributeSet attrs){this(context,attrs,0x01010080);}
 public ScrollView(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public ScrollView(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);scroller=new OverScroller(context);ready=true;ViewConfiguration config=ViewConfiguration.get(context);slop=config.getScaledTouchSlop();minVelocity=config.getScaledMinimumFlingVelocity();maxVelocity=config.getScaledMaximumFlingVelocity();setFocusable(true);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);setScrollContainer(true);fillViewport=new shiraz.runtime.XmlAttributes(context,attrs,style,resource).bool("fillViewport",false);}
 @Override public void addView(View child,int index,ViewGroup.LayoutParams params){if(getChildCount()!=0)throw new IllegalStateException("ScrollView can host only one direct child");super.addView(child,index,params);}
 public void setFillViewport(boolean value){if(fillViewport!=value){fillViewport=value;requestLayout();}}public boolean isFillViewport(){return fillViewport;}
 public void setSmoothScrollingEnabled(boolean value){smooth=value;}public boolean isSmoothScrollingEnabled(){return smooth;}
 @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);}
 private int viewport(){return Math.max(0,getHeight()-getPaddingTop()-getPaddingBottom());}
 private int range(){if(getChildCount()==0||getChildAt(0).getVisibility()==GONE)return 0;View child=getChildAt(0);LayoutParams p=(LayoutParams)child.getLayoutParams();return extent(Math.max(0L,(long)child.getHeight()+p.topMargin+p.bottomMargin-viewport()));}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int desiredW=getPaddingLeft()+getPaddingRight(),desiredH=getPaddingTop()+getPaddingBottom(),state=0;View child=getChildCount()==0?null:getChildAt(0);
  if(child!=null&&child.getVisibility()!=GONE){LayoutParams p=(LayoutParams)child.getLayoutParams();p.resolveLayoutDirection(getLayoutDirection());int horizontal=extent((long)getPaddingLeft()+getPaddingRight()+p.leftMargin+p.rightMargin);child.measure(getChildMeasureSpec(widthSpec,horizontal,p.width),MeasureSpec.makeMeasureSpec(Math.max(0,p.height),p.height>=0?MeasureSpec.EXACTLY:MeasureSpec.UNSPECIFIED));desiredW=extent((long)desiredW+child.getMeasuredWidth()+p.leftMargin+p.rightMargin);desiredH=extent((long)desiredH+child.getMeasuredHeight()+p.topMargin+p.bottomMargin);state=child.getMeasuredState();}
  setMeasuredDimension(resolveSizeAndState(Math.max(getSuggestedMinimumWidth(),desiredW),widthSpec,state),resolveSizeAndState(Math.max(getSuggestedMinimumHeight(),desiredH),heightSpec,state<<MEASURED_HEIGHT_STATE_SHIFT));
  if(child!=null&&child.getVisibility()!=GONE){LayoutParams p=(LayoutParams)child.getLayoutParams();int h=Math.max(0,getMeasuredHeight()-getPaddingTop()-getPaddingBottom()-p.topMargin-p.bottomMargin),w=Math.max(0,getMeasuredWidth()-getPaddingLeft()-getPaddingRight()-p.leftMargin-p.rightMargin);boolean stretch=fillViewport&&MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED&&child.getMeasuredHeight()<h;if(stretch||p.width==LayoutParams.MATCH_PARENT)child.measure(MeasureSpec.makeMeasureSpec(p.width==LayoutParams.MATCH_PARENT?w:child.getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(stretch?h:child.getMeasuredHeight(),MeasureSpec.EXACTLY));}
 }
 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){super.onLayout(changed,left,top,right,bottom);scrollTo(getScrollX(),restoredY==null?getScrollY():restoredY);restoredY=null;}
 @Override public void scrollTo(int x,int y){if(!ready){super.scrollTo(x,y);return;}super.scrollTo(Math.max(0,Math.min(horizontalRange(),x)),Math.max(0,Math.min(range(),y)));}
 private int horizontalRange(){if(getChildCount()==0)return 0;View child=getChildAt(0);LayoutParams p=(LayoutParams)child.getLayoutParams();return extent(Math.max(0L,(long)child.getWidth()+p.leftMargin+p.rightMargin-getWidth()+getPaddingLeft()+getPaddingRight()));}
 @Override protected int computeHorizontalScrollRange(){return Math.max(0,getWidth()-getPaddingLeft()-getPaddingRight())+horizontalRange();}
 @Override protected int computeHorizontalScrollExtent(){return Math.max(0,getWidth()-getPaddingLeft()-getPaddingRight());}
 @Override protected void onOverScrolled(int x,int y,boolean clampedX,boolean clampedY){scrollTo(x,y);}
 @Override protected int computeVerticalScrollRange(){return viewport()+range();}
 @Override protected int computeVerticalScrollExtent(){return viewport();}
 @Override public boolean canScrollVertically(int direction){return direction<0?getScrollY()>0:getScrollY()<range();}
 public int getMaxScrollAmount(){return getHeight()/2;}
 public void smoothScrollTo(int x,int y){if(scroller==null)return;int target=Math.max(0,Math.min(range(),y));scroller.abortAnimation();scroller.startScroll(getScrollX(),getScrollY(),Math.max(0,Math.min(horizontalRange(),x))-getScrollX(),target-getScrollY(),250);postInvalidateOnAnimation();}
 public void smoothScrollBy(int x,int y){smoothScrollTo((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)getScrollX()+x)),(int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(long)getScrollY()+y)));}
 public void fling(int velocityY){if(scroller==null||getChildCount()==0)return;scroller.fling(getScrollX(),getScrollY(),0,Math.max(-maxVelocity,Math.min(maxVelocity,velocityY)),0,horizontalRange(),0,range());postInvalidateOnAnimation();}
 @Override public void computeScroll(){if(scroller!=null&&scroller.computeScrollOffset()){scrollTo(scroller.getCurrX(),scroller.getCurrY());if(!scroller.isFinished())postInvalidateOnAnimation();}}
 private void moveTo(int y){if(smooth)smoothScrollTo(getScrollX(),y);else scrollTo(getScrollX(),y);}
 public boolean fullScroll(int direction){if(direction!=FOCUS_UP&&direction!=FOCUS_DOWN)return false;int old=getScrollY();moveTo(direction==FOCUS_UP?0:range());return old!=(direction==FOCUS_UP?0:range());}
 public boolean pageScroll(int direction){if(direction!=FOCUS_UP&&direction!=FOCUS_DOWN)return false;int target=Math.max(0,Math.min(range(),getScrollY()+(direction==FOCUS_UP?-viewport():viewport())));boolean changed=target!=getScrollY();moveTo(target);return changed;}
 public boolean arrowScroll(int direction){if(direction!=FOCUS_UP&&direction!=FOCUS_DOWN)return false;int target=Math.max(0,Math.min(range(),getScrollY()+(direction==FOCUS_UP?-getMaxScrollAmount():getMaxScrollAmount())));boolean changed=target!=getScrollY();moveTo(target);return changed;}
 public boolean executeKeyEvent(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN||!isEnabled())return false;switch(event.getKeyCode()){case KeyEvent.KEYCODE_DPAD_UP:return event.isAltPressed()?fullScroll(FOCUS_UP):arrowScroll(FOCUS_UP);case KeyEvent.KEYCODE_DPAD_DOWN:return event.isAltPressed()?fullScroll(FOCUS_DOWN):arrowScroll(FOCUS_DOWN);case KeyEvent.KEYCODE_PAGE_UP:return pageScroll(FOCUS_UP);case KeyEvent.KEYCODE_PAGE_DOWN:return pageScroll(FOCUS_DOWN);case KeyEvent.KEYCODE_MOVE_HOME:return fullScroll(FOCUS_UP);case KeyEvent.KEYCODE_MOVE_END:return fullScroll(FOCUS_DOWN);case KeyEvent.KEYCODE_SPACE:return pageScroll(event.isShiftPressed()?FOCUS_UP:FOCUS_DOWN);default:return false;}}
 @Override public boolean dispatchKeyEvent(KeyEvent event){return super.dispatchKeyEvent(event)||executeKeyEvent(event);}
 @Override public boolean shouldDelayChildPressedState(){return true;}
 private void begin(MotionEvent event){pointer=event.getPointerId(0);lastY=event.getY(0);if(velocity==null)velocity=VelocityTracker.obtain();else velocity.clear();velocity.addMovement(event);dragging=!scroller.isFinished();scroller.abortAnimation();}
 private void finish(){dragging=false;pointer=-1;if(velocity!=null){velocity.recycle();velocity=null;}if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
 private void pointerUp(MotionEvent event){int index=event.getActionIndex();if(event.getPointerId(index)==pointer){int next=index==0?1:0;if(next<event.getPointerCount()){pointer=event.getPointerId(next);lastY=event.getY(next);if(velocity!=null)velocity.clear();}else pointer=-1;}}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){if(!isEnabled()||getChildCount()==0)return false;switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:begin(event);break;case MotionEvent.ACTION_MOVE:int index=event.findPointerIndex(pointer);if(index>=0&&Math.abs(event.getY(index)-lastY)>slop){dragging=true;lastY=event.getY(index);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(velocity!=null)velocity.addMovement(event);break;case MotionEvent.ACTION_POINTER_UP:pointerUp(event);break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:finish();break;}return dragging;}
 @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_SCROLL&&isEnabled()&&!dragging){int before=getScrollY();float value=event.getAxisValue(MotionEvent.AXIS_VSCROLL);if(value!=0){scroller.abortAnimation();scrollBy(0,Math.round(-value*ViewConfiguration.get(getContext()).getScaledVerticalScrollFactor()));return before!=getScrollY();}}return super.onGenericMotionEvent(event);}
 @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled()||getChildCount()==0)return false;if(event.getActionMasked()==MotionEvent.ACTION_DOWN){begin(event);return true;}if(velocity!=null)velocity.addMovement(event);switch(event.getActionMasked()){case MotionEvent.ACTION_MOVE:int index=event.findPointerIndex(pointer);if(index<0)return false;float y=event.getY(index),delta=lastY-y;if(!dragging&&Math.abs(delta)>slop){dragging=true;delta-=Math.signum(delta)*slop;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}if(dragging){scrollBy(0,Math.round(delta));lastY=y;}break;case MotionEvent.ACTION_POINTER_DOWN:pointer=event.getPointerId(event.getActionIndex());lastY=event.getY(event.getActionIndex());break;case MotionEvent.ACTION_POINTER_UP:pointerUp(event);break;case MotionEvent.ACTION_UP:if(dragging&&velocity!=null){velocity.computeCurrentVelocity(1000,maxVelocity);int speed=(int)-velocity.getYVelocity(pointer);if(Math.abs(speed)>=minVelocity)fling(speed);}finish();break;case MotionEvent.ACTION_CANCEL:finish();break;}return true;}
 @Override protected void onDetachedFromWindow(){if(scroller!=null)scroller.abortAnimation();finish();super.onDetachedFromWindow();}
 protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){int top=getScrollY()+getPaddingTop(),bottom=getScrollY()+getHeight()-getPaddingBottom();int delta=rect.bottom>bottom?(rect.height()>viewport()?rect.top-top:rect.bottom-bottom):rect.top<top?(rect.height()>viewport()?rect.bottom-bottom:rect.top-top):0;return Math.max(-getScrollY(),Math.min(range()-getScrollY(),delta));}
 public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){Rect rect=new Rect(rectangle);View cursor=child;while(cursor!=this){rect.offset(cursor.getLeft()-cursor.getScrollX(),cursor.getTop()-cursor.getScrollY());if(!(cursor.getParent() instanceof View))return false;cursor=(View)cursor.getParent();}int delta=computeScrollDeltaToGetChildRectOnScreen(rect);if(delta==0)return false;if(immediate)scrollBy(0,delta);else smoothScrollBy(0,delta);return true;}
 private static final class SavedState extends BaseSavedState {final int y;SavedState(Parcelable parent,int value){super(parent);y=value;}}
 @Override protected Parcelable onSaveInstanceState(){return new SavedState(super.onSaveInstanceState(),getScrollY());}
 @Override protected void onRestoreInstanceState(Parcelable state){if(!(state instanceof SavedState)){super.onRestoreInstanceState(state);return;}SavedState saved=(SavedState)state;super.onRestoreInstanceState(saved.getSuperState());restoredY=saved.y;requestLayout();}
}
