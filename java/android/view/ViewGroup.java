// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import java.util.ArrayList;

/** CPU children in insertion order; split pointer input and a single focused branch. */
public abstract class ViewGroup extends View implements ViewParent, ViewManager {
    public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rectangle,boolean immediate){return false;}
    public final void offsetDescendantRectToMyCoords(View descendant,android.graphics.Rect rectangle){offsetDescendantRect(descendant,rectangle,false);}
    public final void offsetRectIntoDescendantCoords(View descendant,android.graphics.Rect rectangle){offsetDescendantRect(descendant,rectangle,true);}
    private void offsetDescendantRect(View descendant,android.graphics.Rect rectangle,boolean reverse){
        if(descendant==null||rectangle==null)throw new NullPointerException();
        int x=0,y=0,depth=0;View current=descendant;
        while(current!=this){
            if(++depth>128||!(current.getParent() instanceof View))throw new IllegalArgumentException("View is not a descendant");
            x+=current.getLeft()-current.getScrollX();y+=current.getTop()-current.getScrollY();current=(View)current.getParent();
        }
        rectangle.offset(reverse?-x:x,reverse?-y:y);
    }

    public void setLayoutAnimation(android.view.animation.LayoutAnimationController controller) {
        if(controller!=null)throw new UnsupportedOperationException("Layout animations are not implemented");
    }
    public android.view.animation.LayoutAnimationController getLayoutAnimation(){return null;}
    private boolean aggregateChildStates;
    public boolean addStatesFromChildren(){return aggregateChildStates;}
    public void setAddStatesFromChildren(boolean enabled){
        if(enabled&&children!=null)for(View child:children)if(child.isDuplicateParentStateEnabled())throw new IllegalStateException("Cyclic parent/child drawable states");
        aggregateChildStates=enabled;refreshDrawableState();
    }
    @Override public void dispatchConfigurationChanged(android.content.res.Configuration configuration){
        super.dispatchConfigurationChanged(configuration);
        for(View child:children.toArray(new View[children.size()]))if(child.getParent()==this)child.dispatchConfigurationChanged(configuration);
    }
    @Override public void childDrawableStateChanged(View child){if(aggregateChildStates)refreshDrawableState();}
    @Override protected int[] onCreateDrawableState(int extraSpace){
        if(!aggregateChildStates||children==null)return super.onCreateDrawableState(extraSpace);
        if(extraSpace<0||extraSpace>1024)throw new IllegalArgumentException("Drawable state capacity");
        ArrayList<int[]> states=new ArrayList<int[]>();int count=extraSpace;
        for(View child:children){
            if(child.isDuplicateParentStateEnabled())throw new IllegalStateException("Cyclic parent/child drawable states");
            int[] value=child.getDrawableState();if(value!=null){if(value.length>1024-count)throw new IllegalArgumentException("Drawable state capacity");count+=value.length;states.add(value);}
        }
        int[] result=super.onCreateDrawableState(count);for(int[] value:states)mergeDrawableStates(result,value);return result;
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();if(children!=null)for(View child:new ArrayList<View>(children))if(child.getParent()==this&&child.isDuplicateParentStateEnabled())child.refreshDrawableState();}
    @Override public boolean hasTransientState(){if(super.hasTransientState())return true;for(int i=0;i<getChildCount();i++)if(getChildAt(i).hasTransientState())return true;return false;}

    @Override public void onDescendantInvalidated(View child,View target){
        if(child==null||target==null)throw new NullPointerException("child/target");
        shirazInvalidateSelf();
        if(getLayerType()==LAYER_TYPE_SOFTWARE)target=this;
        ViewParent parent=getParent();if(parent!=null)parent.onDescendantInvalidated(this,target);
    }
    @Override void dispatchCancelPendingInputEvents(){
        super.dispatchCancelPendingInputEvents();
        for(View child:new ArrayList<View>(children))if(child.getParent()==this)child.dispatchCancelPendingInputEvents();
    }
    @Override public WindowInsets dispatchApplyWindowInsets(WindowInsets insets){
        WindowInsets remaining=super.dispatchApplyWindowInsets(insets);
        // API 27 passes each child's returned insets to the following sibling.
        for(View child:new ArrayList<View>(children)){if(remaining.isConsumed())break;if(child.getParent()==this)remaining=child.dispatchApplyWindowInsets(remaining);}
        return remaining;
    }
    public boolean requestSendAccessibilityEvent(View child,android.view.accessibility.AccessibilityEvent event){if(!onRequestSendAccessibilityEvent(child,event))return false;ViewParent parent=getParent();return parent!=null&&parent.requestSendAccessibilityEvent(this,event);}
    public boolean onRequestSendAccessibilityEvent(View child,android.view.accessibility.AccessibilityEvent event){AccessibilityDelegate delegate=getAccessibilityDelegate();return delegate==null?shirazRequestSendAccessibilityEvent(child,event):delegate.onRequestSendAccessibilityEvent(this,child,event);}
    final boolean shirazRequestSendAccessibilityEvent(View child,android.view.accessibility.AccessibilityEvent event){return true;}
    public void addChildrenForAccessibility(ArrayList<View> out){for(View child:new ArrayList<View>(children)){if(child.getVisibility()!=VISIBLE||child.getImportantForAccessibility()==IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)continue;if(child.isImportantForAccessibility()){if(out.size()>=4096)throw new IllegalStateException("Accessibility child limit");out.add(child);}else if(child instanceof ViewGroup)((ViewGroup)child).addChildrenForAccessibility(out);}}
    @Override protected void shirazInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.shirazInitializeAccessibilityNodeInfo(info);ArrayList<View> accessible=new ArrayList<View>();addChildrenForAccessibility(accessible);for(View child:accessible)info.addChild(child);}
    @Override protected boolean shirazDispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){if(super.shirazDispatchPopulateAccessibilityEvent(event))return true;for(View child:new ArrayList<View>(children))if(child.getVisibility()==VISIBLE&&child.dispatchPopulateAccessibilityEvent(event))return true;return false;}
    public static class LayoutParams {
        public static final int MATCH_PARENT=-1, FILL_PARENT=-1, WRAP_CONTENT=-2;
        public int width,height;
        public LayoutParams(int width,int height) {
            if (width < -2 || height < -2 || width > 0x3fffffff || height > 0x3fffffff) throw new IllegalArgumentException("Layout dimensions");
            this.width=width; this.height=height;
        }
        public LayoutParams(LayoutParams other) { this(other.width,other.height); }
        public LayoutParams(Context context,AttributeSet attrs) {this(WRAP_CONTENT,WRAP_CONTENT);shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs);if(!a.has("layout_width")||!a.has("layout_height"))throw new IllegalArgumentException("XML layout width and height are required");width=a.dimension("layout_width",WRAP_CONTENT,true);height=a.dimension("layout_height",WRAP_CONTENT,true);if(width< -2||height< -2)throw new IllegalArgumentException("XML layout dimensions");}
        public void resolveLayoutDirection(int direction) { }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin,topMargin,rightMargin,bottomMargin;
        private int start=Integer.MIN_VALUE,end=Integer.MIN_VALUE,direction,baseLeft,baseRight,resolvedLeft,resolvedRight;
        private boolean resolved;
        public MarginLayoutParams(int width,int height) { super(width,height); }
        public MarginLayoutParams(Context context,AttributeSet attrs) {super(context,attrs);shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs);int all=a.dimension("layout_margin",0,false);setMargins(a.dimension("layout_marginLeft",all,false),a.dimension("layout_marginTop",all,false),a.dimension("layout_marginRight",all,false),a.dimension("layout_marginBottom",all,false));if(a.has("layout_marginStart"))setMarginStart(a.dimension("layout_marginStart",0,false));if(a.has("layout_marginEnd"))setMarginEnd(a.dimension("layout_marginEnd",0,false));}
        public MarginLayoutParams(LayoutParams other) { super(other); if (other instanceof MarginLayoutParams) copy((MarginLayoutParams)other); }
        public MarginLayoutParams(MarginLayoutParams other) { super(other); copy(other); }
        private void copy(MarginLayoutParams other) {
            leftMargin=other.leftMargin; topMargin=other.topMargin; rightMargin=other.rightMargin; bottomMargin=other.bottomMargin;
            start=other.start; end=other.end; direction=other.direction; baseLeft=other.baseLeft; baseRight=other.baseRight;
            resolved=other.resolved; resolvedLeft=other.resolvedLeft; resolvedRight=other.resolvedRight;
        }
        public void setMargins(int left,int top,int right,int bottom) { leftMargin=left; topMargin=top; rightMargin=right; bottomMargin=bottom; baseLeft=left; baseRight=right; resolved=false; }
        public void setMarginStart(int value) { start=value; }
        public void setMarginEnd(int value) { end=value; }
        public int getMarginStart() { return start == Integer.MIN_VALUE ? leftMargin : start; }
        public int getMarginEnd() { return end == Integer.MIN_VALUE ? rightMargin : end; }
        public boolean isMarginRelative() { return start != Integer.MIN_VALUE || end != Integer.MIN_VALUE; }
        public void setLayoutDirection(int value) { if (value != LAYOUT_DIRECTION_LTR && value != LAYOUT_DIRECTION_RTL) throw new IllegalArgumentException("margin direction"); direction=value; }
        public int getLayoutDirection() { return direction; }
        public void resolveLayoutDirection(int value) {
            setLayoutDirection(value);
            if (!resolved || leftMargin != resolvedLeft) baseLeft=leftMargin;
            if (!resolved || rightMargin != resolvedRight) baseRight=rightMargin;
            leftMargin=baseLeft; rightMargin=baseRight;
            if (start != Integer.MIN_VALUE) { if (value == LAYOUT_DIRECTION_RTL) rightMargin=start; else leftMargin=start; }
            if (end != Integer.MIN_VALUE) { if (value == LAYOUT_DIRECTION_RTL) leftMargin=end; else rightMargin=end; }
            resolvedLeft=leftMargin; resolvedRight=rightMargin; resolved=true;
        }
    }
    private final ArrayList<View> children=new ArrayList<View>();
    private boolean clipChildren=true,clipPadding=true,disallowIntercept,selfTouch;
    private static final class TouchTarget {final View child;int bits;TouchTarget(View child,int bits){this.child=child;this.bits=bits;}}
    private final ArrayList<TouchTarget> touchTargets=new ArrayList<TouchTarget>();
    private boolean splitMotionEvents=true,gestureSplit=true;
    public void setMotionEventSplittingEnabled(boolean enabled){splitMotionEvents=enabled;}
    public boolean isMotionEventSplittingEnabled(){return splitMotionEvents;}
    private MotionEvent lastTouch;
    public static final int FOCUS_BEFORE_DESCENDANTS=0x20000, FOCUS_AFTER_DESCENDANTS=0x40000, FOCUS_BLOCK_DESCENDANTS=0x60000;
    private boolean touchscreenBlocksFocus;
    public void setTouchscreenBlocksFocus(boolean value){touchscreenBlocksFocus=value;if(value&&hasFocus()&&!isKeyboardNavigationCluster()){View focused=findFocus();if(focused!=null&&!focused.isFocusableInTouchMode())KeyboardClusters.movePastTouchBlock(this,focused);}}
    public boolean getTouchscreenBlocksFocus(){return touchscreenBlocksFocus;}
    final void shirazSetTouchscreenBlocksFocus(boolean value){touchscreenBlocksFocus=value;}
    final boolean shirazShouldBlockFocusForTouchscreen(){return touchscreenBlocksFocus&&getContext().getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)&&!(isKeyboardNavigationCluster()&&(hasFocus()||KeyboardClusters.cluster(this)!=this));}
    private int descendantFocusability=FOCUS_BEFORE_DESCENDANTS;
    public ViewGroup(Context context) { super(context); }
    public ViewGroup(Context context,AttributeSet attrs) { this(context,attrs,0,0); }
    public ViewGroup(Context context,AttributeSet attrs,int style) { this(context,attrs,style,0); }
    public ViewGroup(Context context,AttributeSet attrs,int style,int resource) {
        super(context,attrs,style,resource);
        shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);
        clipChildren=a.bool("clipChildren",true);
        clipPadding=a.bool("clipToPadding",true);
        aggregateChildStates=a.bool("addStatesFromChildren",false);
        touchscreenBlocksFocus=a.bool("touchscreenBlocksFocus",false);
        if(a.bool("animateLayoutChanges",false))setLayoutTransition(new android.animation.LayoutTransition());
    }
    private android.animation.LayoutTransition layoutTransition;
    public android.animation.LayoutTransition getLayoutTransition(){return layoutTransition;}
    public void setLayoutTransition(android.animation.LayoutTransition value){if(value==layoutTransition)return;if(value!=null)value.shirazBind(this);android.animation.LayoutTransition old=layoutTransition;layoutTransition=value;if(old!=null)try{old.cancel();}finally{old.shirazBind(null);}}
    public final void shirazVisibilityChange(View child,int old,int value){if(layoutTransition==null)return;if(value==VISIBLE)layoutTransition.showChild(this,child,old);else if(old==VISIBLE)layoutTransition.hideChild(this,child,value);}
    private boolean layoutSuppressed,layoutCalledWhileSuppressed;
    public void suppressLayout(boolean suppress){
        if(layoutSuppressed==suppress)return;
        layoutSuppressed=suppress;
        if(!suppress&&layoutCalledWhileSuppressed){layoutCalledWhileSuppressed=false;requestLayout();}
    }
    public boolean isLayoutSuppressed(){return layoutSuppressed;}
    @Override public void layout(int l,int t,int r,int b){
        if(layoutSuppressed){layoutCalledWhileSuppressed=true;return;}
        if(layoutTransition!=null)layoutTransition.layoutChange(this);super.layout(l,t,r,b);
    }

    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return index < 0 || index >= children.size() ? null : children.get(index); }
    public void bringChildToFront(View child){
        int index=children.indexOf(child);if(index<0||index==children.size()-1)return;
        children.remove(index);children.add(child);requestLayout();invalidate();
    }
    public int indexOfChild(View view) { return children.indexOf(view); }
    public void addView(View child) { addView(child,-1); }
    public void addView(View child,int index) { addView(child,index,child.getLayoutParams() == null ? generateDefaultLayoutParams() : child.getLayoutParams()); }
    public void addView(View child,LayoutParams params) { addView(child,-1,params); }
    public void addView(View child,int width,int height) { LayoutParams params=generateDefaultLayoutParams(); params.width=width; params.height=height; addView(child,-1,params); }
    public void addView(View child,int index,LayoutParams params) {
        addViewInternal(child,index,params,false,true);
    }
    protected boolean addViewInLayout(View child,int index,LayoutParams params){return addViewInLayout(child,index,params,false);}
    protected boolean addViewInLayout(View child,int index,LayoutParams params,boolean preventRequestLayout){
        addViewInternal(child,index<0?-1:index,params,preventRequestLayout,false);
        return child.getParent()==this;
    }
    private void addViewInternal(View child,int index,LayoutParams params,boolean preventRequestLayout,boolean requestParentLayout){
        if (child == null || params == null) throw new NullPointerException("child/layout params");
        if (child.getParent() != null || child.temporaryParent != null || child.isAttachedToWindow()) throw new IllegalStateException("Child already has a parent/window");
        for (ViewParent owner=this; owner != null; owner=owner instanceof View?((View)owner).shirazHierarchyOwner():owner.getParent()) {if (owner == child) throw new IllegalArgumentException("View hierarchy cycle");if(owner instanceof View&&((View)owner).shirazIsDetaching())throw new IllegalStateException("Cannot add children during detachment");}
        if (children.size()+disappearing.size()+detached.size() >= 4096) throw new IllegalStateException("ViewGroup child limit exceeded");
        if (index < -1 || index > children.size()) throw new IndexOutOfBoundsException("child index");
        if (!checkLayoutParams(params)) params=generateLayoutParams(params);
        if(preventRequestLayout)child.shirazAssignLayoutParams(params);else child.setLayoutParams(params);
        if(aggregateChildStates&&child.isDuplicateParentStateEnabled())throw new IllegalStateException("Cyclic parent/child drawable states");
        children.add(index == -1 ? children.size() : index,child);
        try{child.shirazSetParent(this);}catch(RuntimeException|Error error){if(child.getParent()!=this)children.remove(child);throw error;}
        if(child.getParent()!=this)return;
        if (child.hasFocus()) { View focused=child.findFocus(); if (focused != null) { focused.clearFocus(); focused.requestFocus(); } }
        if(aggregateChildStates)refreshDrawableState();if(requestParentLayout)requestLayout(); dispatchViewAdded(child);if(layoutTransition!=null&&children.contains(child))layoutTransition.addChild(this,child);
    }
    private final ArrayList<View> detached=new ArrayList<View>();
    protected void attachViewToParent(View child,int index,LayoutParams params){
        if(child==null||params==null)throw new NullPointerException("child/layout params");
        if(child.getParent()!=null)throw new IllegalStateException("Child already parented");
        if(child.shirazIsDetaching())throw new IllegalStateException("Child window is detaching");
        for(View owner=this;owner!=null;owner=owner.shirazHierarchyOwner()){if(owner==child)throw new IllegalArgumentException("View hierarchy cycle");if(owner.shirazIsDetaching())throw new IllegalStateException("Cannot attach during window detachment");}
        if(child.isAttachedToWindow()&&!child.shirazSameWindow(this))throw new IllegalStateException("Temporary child belongs to another window");
        ViewGroup old=child.temporaryParent;if(child.isAttachedToWindow()&&old==null)throw new IllegalStateException("Attached child has no temporary owner");
        if(index<0)index=children.size();if(index>children.size())throw new IndexOutOfBoundsException("child index");
        if(old!=this&&children.size()+disappearing.size()+detached.size()>=4096)throw new IllegalStateException("ViewGroup child limit exceeded");
        if(old!=null&&!old.detached.contains(child))throw new IllegalStateException("Temporary child ownership mismatch");
        if(aggregateChildStates&&child.isDuplicateParentStateEnabled())throw new IllegalStateException("Cyclic parent/child drawable states");
        child.shirazAssignLayoutParams(params);children.add(index,child);if(old!=null)old.detached.remove(child);child.temporaryParent=null;
        try{child.shirazSetParent(this);}catch(RuntimeException|Error failure){if(child.getParent()!=this){children.remove(child);child.temporaryParent=old;if(old!=null)old.detached.add(child);}throw failure;}
        if(child.getParent()==this){if(aggregateChildStates)refreshDrawableState();if(child.hasFocus())shirazRequestChildFocus(child);child.invalidate();invalidate();}
    }
    protected void detachViewFromParent(View child){int index=children.indexOf(child);if(index<0)throw new IllegalArgumentException("Not an active child");detachViewFromParent(index);}
    protected void detachViewFromParent(int index){
        if(shirazIsDetaching())throw new IllegalStateException("Window is detaching");View child=children.get(index);
        if(transitioning.contains(child)||disappearing.contains(child))throw new UnsupportedOperationException("Finish a child's view transition before temporarily detaching it");
        children.remove(index);detached.add(child);child.temporaryParent=this;child.shirazAssignParent(null);invalidate();shirazChildHidden(child);if(aggregateChildStates)refreshDrawableState();
    }
    @Override public void dispatchWindowVisibilityChanged(int visibility){super.dispatchWindowVisibilityChanged(visibility);for(View child:shirazWindowChildren())if(shirazSameWindow(child))child.dispatchWindowVisibilityChanged(visibility);}
    @Override public void dispatchWindowFocusChanged(boolean value){super.dispatchWindowFocusChanged(value);for(View child:new ArrayList<View>(children))if(child.getParent()==this)child.dispatchWindowFocusChanged(value);}
    @Override public void dispatchSetActivated(boolean value){for(int i=0;i<children.size();i++)children.get(i).setActivated(value);}
    protected void detachViewsFromParent(int start,int count){if(start<0||count<0||(long)start+count>children.size())throw new IndexOutOfBoundsException("Child range");ArrayList<View> selected=new ArrayList<View>(children.subList(start,start+count));for(View child:selected)if(transitioning.contains(child)||disappearing.contains(child))throw new UnsupportedOperationException("Active view transition");for(View child:selected)if(child.getParent()==this)detachViewFromParent(child);}
    protected void detachAllViewsFromParent(){detachViewsFromParent(0,children.size());}
    protected void removeDetachedView(View child,boolean animate){
        if(child==null)throw new NullPointerException("child");
        detached.remove(child);if(child.temporaryParent==this)child.temporaryParent=null;
        try{try{child.clearFocus();}finally{child.shirazDetachTree();}}finally{invalidate();dispatchViewRemoved(child);}
    }
    @Override public void dispatchStartTemporaryDetach(){super.dispatchStartTemporaryDetach();for(View child:new ArrayList<View>(children))if(child.getParent()==this)child.dispatchStartTemporaryDetach();}
    @Override public void dispatchFinishTemporaryDetach(){super.dispatchFinishTemporaryDetach();for(View child:new ArrayList<View>(children))if(child.getParent()==this)child.dispatchFinishTemporaryDetach();}
    public interface OnHierarchyChangeListener { void onChildViewAdded(View parent,View child); void onChildViewRemoved(View parent,View child); }
    private OnHierarchyChangeListener hierarchyListener;
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener){hierarchyListener=listener;}
    public void onViewAdded(View child) { }
    public void onViewRemoved(View child) { }
    private void dispatchViewAdded(View child){onViewAdded(child);OnHierarchyChangeListener listener=hierarchyListener;if(listener!=null)listener.onChildViewAdded(this,child);}
    private void dispatchViewRemoved(View child){onViewRemoved(child);OnHierarchyChangeListener listener=hierarchyListener;if(listener!=null)listener.onChildViewRemoved(this,child);}
    private final ArrayList<View> transitioning=new ArrayList<View>(),disappearing=new ArrayList<View>();
    public void startViewTransition(View child){if(shirazIsDetaching())throw new IllegalStateException("Window is detaching");if(child!=null&&child.getParent()==this&&children.contains(child)&&!transitioning.contains(child))transitioning.add(child);}
    public void endViewTransition(View child){transitioning.remove(child);if(disappearing.remove(child)){try{if(child.getParent()==this&&!children.contains(child))child.shirazSetParent(null);}finally{invalidate();}}}
    public final ArrayList<View> shirazWindowChildren(){ArrayList<View> result=new ArrayList<View>(children);for(View child:disappearing)if(!result.contains(child))result.add(child);for(View child:detached)if(!result.contains(child))result.add(child);return result;}
    public final void shirazRetainVisibility(View child){startViewTransition(child);if(!disappearing.contains(child))disappearing.add(child);}
    final void shirazFinishTransitions(){Throwable failure=null;try{if(layoutTransition!=null)layoutTransition.cancel();}catch(RuntimeException|Error error){failure=error;}transitioning.clear();for(View child:new ArrayList<View>(disappearing)){try{endViewTransition(child);}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}}for(View child:detached)child.temporaryParent=null;detached.clear();if(failure instanceof RuntimeException)throw (RuntimeException)failure;if(failure instanceof Error)throw (Error)failure;}
    public void removeView(View child) { int index=children.indexOf(child); if (index >= 0) removeViewAtInternal(index,true); }
    public void removeViewInLayout(View child){int index=children.indexOf(child);if(index>=0)removeViewAtInternal(index,false);}
    private final java.util.HashSet<View> removing=new java.util.HashSet<View>();
    public void removeViewAt(int index) {removeViewAtInternal(index,true);}
    private void removeViewAtInternal(int index,boolean scheduleLayout) {
        View child=children.get(index);if(!removing.add(child))return;try{shirazChildHidden(child);
        if(layoutTransition!=null&&children.contains(child))layoutTransition.removeChild(this,child);
        // A cancellation callback may already remove the child.
        if (!children.remove(child)) return;
        boolean retained=transitioning.contains(child);if(retained&&!disappearing.contains(child))disappearing.add(child);
        child.clearFocus(); if(!retained)child.shirazSetParent(null); if(scheduleLayout)requestLayout();else invalidate(); dispatchViewRemoved(child);if(aggregateChildStates)refreshDrawableState();
        }finally{removing.remove(child);}
    }
    public void removeAllViews() { removeRangeInternal(0,children.size(),true); }
    public void updateViewLayout(View child,LayoutParams params) { if (child.getParent() != this) throw new IllegalArgumentException("Not a child"); if (!checkLayoutParams(params)) throw new IllegalArgumentException("Layout params type"); child.setLayoutParams(params); }
    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT); }
    protected LayoutParams generateLayoutParams(LayoutParams params) { return new LayoutParams(params); }
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(),attrs); }
    protected boolean checkLayoutParams(LayoutParams params) { return params != null; }
    @Override public <T extends View> T findViewWithTag(Object tag){T own=super.findViewWithTag(tag);if(own!=null)return own;if(tag!=null)for(View child:children){T found=child.findViewWithTag(tag);if(found!=null)return found;}return null;}
    @Override public <T extends View> T findViewById(int id) {
        T own=super.findViewById(id); if (own != null) return own;
        for (View child:children) { T found=child.findViewById(id); if (found != null) return found; } return null;
    }
    public void setClipChildren(boolean value) { clipChildren=value; invalidate(); }
    public boolean getClipChildren() { return clipChildren; }
    public void setClipToPadding(boolean value) { clipPadding=value; invalidate(); }
    public boolean getClipToPadding() { return clipPadding; }
    @Override public boolean shirazRequestsKeepScreenOn(){if(getVisibility()!=VISIBLE)return false;if(super.shirazRequestsKeepScreenOn())return true;for(View child:children)if(child.shirazRequestsKeepScreenOn())return true;return false;}
    @Override public PointerIcon shirazPointerIcon(float x,float y){
        ArrayList<View> order=orderedChildren();for(int i=order.size()-1;i>=0;i--){View child=order.get(i);float cx=child.shirazLocalX(x+getScrollX()-child.getX()),cy=child.shirazLocalY(y+getScrollY()-child.getY());if(child.getParent()==this&&child.getVisibility()==VISIBLE&&cx>=0&&cy>=0&&cx<child.getWidth()&&cy<child.getHeight()){PointerIcon icon=child.shirazPointerIcon(cx,cy);if(icon!=null)return icon;}}
        return super.shirazPointerIcon(x,y);
    }
    private boolean childrenDrawingOrderEnabled,transitionGroup;
    protected boolean isChildrenDrawingOrderEnabled(){return childrenDrawingOrderEnabled;}
    protected void setChildrenDrawingOrderEnabled(boolean enabled){childrenDrawingOrderEnabled=enabled;invalidate();}
    protected int getChildDrawingOrder(int childCount,int drawingPosition){return drawingPosition;}
    public boolean isTransitionGroup(){return transitionGroup;}
    public void setTransitionGroup(boolean value){transitionGroup=value;}
    public boolean shouldDelayChildPressedState(){return true;}
    private void removeRangeInternal(int start,int count,boolean scheduleLayout){
        if(start<0||count<0||(long)start+count>children.size())throw new IndexOutOfBoundsException("Child removal range");
        for(View child:new ArrayList<View>(children.subList(start,start+count))){int index=children.indexOf(child);if(index>=0)removeViewAtInternal(index,scheduleLayout);}
    }
    public void removeViews(int start,int count){removeRangeInternal(start,count,true);}
    public void removeViewsInLayout(int start,int count){removeRangeInternal(start,count,false);}
    public void removeAllViewsInLayout(){removeRangeInternal(0,children.size(),false);}
    protected void measureChildren(int widthSpec,int heightSpec){for(View child:new ArrayList<View>(children))if(child.getVisibility()!=GONE)measureChild(child,widthSpec,heightSpec);}
    private ArrayList<View> orderedChildren(){ArrayList<View> order=new ArrayList<View>(children);
        if(childrenDrawingOrderEnabled){int n=children.size();order.clear();for(int i=0;i<n;i++){int at=getChildDrawingOrder(n,i);if(at<0||at>=n)throw new IndexOutOfBoundsException("child drawing order "+at);order.add(children.get(at));}}
java.util.Collections.sort(order,new java.util.Comparator<View>(){public int compare(View a,View b){return Float.compare(a.getZ(),b.getZ());}});return order;}
    @Override protected void dispatchDraw(Canvas canvas) {
        int saved=canvas.save();
        try {
            if (clipChildren) canvas.clipRect(getScrollX(),getScrollY(),(float)getScrollX()+getWidth(),(float)getScrollY()+getHeight());
            if (clipPadding && (getPaddingLeft() != 0 || getPaddingTop() != 0 || getPaddingRight() != 0 || getPaddingBottom() != 0)) canvas.clipRect((float)getScrollX()+getPaddingLeft(),(float)getScrollY()+getPaddingTop(),(float)getScrollX()+getWidth()-getPaddingRight(),(float)getScrollY()+getHeight()-getPaddingBottom());
            ArrayList<View> drawing=new ArrayList<View>();
            for(View child:orderedChildren())if(child.getVisibility()==VISIBLE&&!disappearing.contains(child))drawing.add(child);
            ArrayList<View> fading=new ArrayList<View>(disappearing);for(int i=fading.size()-1;i>=0;i--)drawing.add(fading.get(i));
            // Animation ordering affects painting only, never hit testing or child indices.
            // Stable sorting preserves the existing elevation/custom/disappearing order.
            java.util.Collections.sort(drawing,new java.util.Comparator<View>(){public int compare(View a,View b){return Integer.compare(animationOrder(a),animationOrder(b));}});
            for(View child:drawing)if(child.getParent()==this)drawChild(canvas,child,getDrawingTime());
        } finally { canvas.restoreToCount(saved); }
    }
    private static int animationOrder(View view){android.view.animation.Animation animation=view.getAnimation();return animation==null?0:animation.getZAdjustment();}
    protected boolean drawChild(Canvas canvas,View child,long time) {
        // Only Shiraz's initialized GPU Canvas can execute this traversal on hardware.
        // A caller advertising acceleration on a placeholder remains unsupported.
        if(canvas.isHardwareAccelerated()&&!canvas.shirazIsGpuCanvas())throw new ClassCastException("Unsupported hardware Canvas implementation");
        if(child.shirazCollapsed())return false;
        int saved=canvas.save();
        try {
            canvas.translate(child.getLeft(),child.getTop());canvas.concat(child.getMatrix());child.shirazApplyLegacyAnimation(canvas,time);child.shirazDrawShadow(canvas);
            if (clipChildren) canvas.clipRect(0,0,child.getWidth(),child.getHeight());
            if(disappearing.contains(child))child.shirazDrawDisappearing(canvas);else child.shirazDrawWithAlpha(canvas); return child.shirazIsInvalidated();
        } finally { canvas.restoreToCount(saved); }
    }
    public static int getChildMeasureSpec(int parentSpec,int padding,int dimension) {
        int mode=MeasureSpec.getMode(parentSpec), available=(int)Math.max(0L,Math.min(0x3fffffffL,(long)MeasureSpec.getSize(parentSpec)-padding));
        if (dimension >= 0) return MeasureSpec.makeMeasureSpec(dimension,MeasureSpec.EXACTLY);
        if (dimension != LayoutParams.MATCH_PARENT && dimension != LayoutParams.WRAP_CONTENT) throw new IllegalArgumentException("Child dimension");
        if (mode == MeasureSpec.UNSPECIFIED) return MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        return MeasureSpec.makeMeasureSpec(available,dimension == LayoutParams.MATCH_PARENT && mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
    }
    protected static int extent(long value) { if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Layout arithmetic overflow"); return (int)value; }
    protected void measureChild(View child,int widthSpec,int heightSpec) {
        LayoutParams params=child.getLayoutParams(); child.measure(getChildMeasureSpec(widthSpec,extent((long)getPaddingLeft()+getPaddingRight()),params.width),getChildMeasureSpec(heightSpec,extent((long)getPaddingTop()+getPaddingBottom()),params.height));
    }
    protected void measureChildWithMargins(View child,int widthSpec,int widthUsed,int heightSpec,int heightUsed) {
        MarginLayoutParams params=(MarginLayoutParams)child.getLayoutParams(); params.resolveLayoutDirection(child.getLayoutDirection());
        child.measure(getChildMeasureSpec(widthSpec,extent((long)getPaddingLeft()+getPaddingRight()+params.leftMargin+params.rightMargin+widthUsed),params.width),getChildMeasureSpec(heightSpec,extent((long)getPaddingTop()+getPaddingBottom()+params.topMargin+params.bottomMargin+heightUsed),params.height));
    }
    @Override protected abstract void onLayout(boolean changed,int left,int top,int right,int bottom);
    @Override protected void dispatchVisibilityChanged(View changed,int visibility){super.dispatchVisibilityChanged(changed,visibility);for(View child:shirazWindowChildren())if(child.getParent()==this)child.dispatchVisibilityChanged(changed,visibility);}
    @Override public void setVisibility(int value) {
        super.setVisibility(value); if (value != VISIBLE) { cancelTarget(lastTouch); cancelSelf(); }
    }
    private void cancelSelf() {
        if (!selfTouch || lastTouch == null) return; selfTouch=false;
        MotionEvent cancel=MotionEvent.obtain(lastTouch);
        try { cancel.setAction(MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
    }
    // Base containers do not opt into nested scrolling as children or parents.
    // Subclasses can accept a descendant's gesture and report its current axes.
    private int nestedScrollAxes;
    public boolean onStartNestedScroll(View child,View target,int axes){return false;}
    public void onNestedScrollAccepted(View child,View target,int axes){nestedScrollAxes=axes;}
    public void onStopNestedScroll(View target){nestedScrollAxes=0;}
    public int getNestedScrollAxes(){return nestedScrollAxes;}
    public void onNestedScroll(View target,int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed){}
    public void onNestedPreScroll(View target,int dx,int dy,int[] consumed){}
    public boolean onNestedFling(View target,float velocityX,float velocityY,boolean consumed){return false;}
    public boolean onNestedPreFling(View target,float velocityX,float velocityY){return false;}
    public boolean onInterceptTouchEvent(MotionEvent event) { return false; }
    public void requestDisallowInterceptTouchEvent(boolean value) { disallowIntercept=value; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(value); }
    private boolean childEvent(View child,MotionEvent event,int bits,boolean cancel) {
        int present=bits&event.getPointerIdBits();if(present==0)return false;
        MotionEvent local=event.split(present);
        try { if (cancel) local.setAction(MotionEvent.ACTION_CANCEL); float ox=(float)getScrollX()-child.getX(),oy=(float)getScrollY()-child.getY(); float beforeX=local.getX(),beforeY=local.getY(); local.offsetLocation(ox,oy); boolean scaleFail=!local.shirazInverseViewScale(child); if(child.shirazCollapsed()||scaleFail){local.setAction(MotionEvent.ACTION_CANCEL);for(int i=touchTargets.size()-1;i>=0;i--)if(touchTargets.get(i).child==child)touchTargets.remove(i);} if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&local.getActionMasked()==MotionEvent.ACTION_CANCEL)System.err.println("SHIRAZ_TOUCH_CANCEL child="+child.getClass().getName()+" id="+Integer.toHexString(child.getId())+" cancelFlag="+cancel+" collapsed="+child.shirazCollapsed()+" scaleFail="+scaleFail+" scale="+child.getScaleX()+","+child.getScaleY()+" xy="+beforeX+","+beforeY+" off="+ox+","+oy+" childXY="+child.getX()+","+child.getY()+" size="+child.getWidth()+"x"+child.getHeight()); return child.dispatchTouchEvent(local); }
        finally { local.recycle(); }
    }
    private void cancelTarget(MotionEvent event) {ArrayList<TouchTarget> old=new ArrayList<TouchTarget>(touchTargets);touchTargets.clear();if(event!=null)for(TouchTarget target:old)childEvent(target.child,event,target.bits,true);}
    final void shirazChildHidden(View child) {for(TouchTarget target:new ArrayList<TouchTarget>(touchTargets))if(target.child==child){touchTargets.remove(target);if(lastTouch!=null)childEvent(child,lastTouch,target.bits,true);}}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        ArrayList<View> order=orderedChildren();
        for(int i=order.size()-1;i>=0;i--){View child=order.get(i);
            float x=child.shirazLocalX(event.getX()+getScrollX()-child.getX()),y=child.shirazLocalY(event.getY()+getScrollY()-child.getY());
            if(child.getParent()!=this||child.getVisibility()!=VISIBLE||child.shirazCollapsed()||!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=child.getWidth()||y>=child.getHeight())continue;
            MotionEvent local=MotionEvent.obtain(event);
            try{local.offsetLocation(getScrollX()-child.getX(),getScrollY()-child.getY());if(local.shirazInverseViewScale(child)&&child.dispatchGenericMotionEvent(local))return true;}finally{local.recycle();}
        }
        if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT")))System.err.println("SHIRAZ_GENERIC_MOTION class="+getClass().getName()+" action="+event.getActionMasked()+" axis="+event.getAxisValue(MotionEvent.AXIS_VSCROLL));
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){cancelTarget(lastTouch);cancelSelf();disallowIntercept=false;gestureSplit=splitMotionEvents;}
        if(lastTouch!=null)lastTouch.recycle();lastTouch=MotionEvent.obtain(event);
        boolean terminal=action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL;
        try {
            boolean intercepted=action!=MotionEvent.ACTION_DOWN&&touchTargets.isEmpty()||!disallowIntercept&&(action==MotionEvent.ACTION_DOWN||!touchTargets.isEmpty())&&onInterceptTouchEvent(event);
            boolean handled=false,sent=false;TouchTarget delivered=null;
            for(TouchTarget target:new ArrayList<TouchTarget>(touchTargets))if(intercepted||target.child.getParent()!=this){touchTargets.remove(target);handled|=childEvent(target.child,event,target.bits,true);}
            if(!intercepted&&(action==MotionEvent.ACTION_DOWN||(gestureSplit&&action==MotionEvent.ACTION_POINTER_DOWN))){
                int pointer=event.getActionIndex(),bits=gestureSplit?1<<event.getPointerId(pointer):-1;
                ArrayList<View> order=orderedChildren();
                for(int i=order.size()-1;i>=0;i--){View child=order.get(i);
                    float localX=child.shirazLocalX(event.getX(pointer)+getScrollX()-child.getX()),localY=child.shirazLocalY(event.getY(pointer)+getScrollY()-child.getY());
                    if(child.getParent()!=this||child.getVisibility()!=VISIBLE||child.shirazCollapsed()||localX<0||localX>=child.getWidth()||localY<0||localY>=child.getHeight())continue;
                    TouchTarget existing=null;for(TouchTarget target:touchTargets)if(target.child==child){existing=target;break;}
                    if(existing!=null){existing.bits|=bits;delivered=existing;break;}
                    if(childEvent(child,event,bits,false)){
                        handled=true;sent=true;
                        if(child.getParent()==this){delivered=new TouchTarget(child,bits);touchTargets.add(0,delivered);}
                        else{
                         if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT")))System.err.println("SHIRAZ_TOUCH_DETACH_CANCEL child="+child.getClass().getName()+" id="+Integer.toHexString(child.getId())+" parentOk="+(child.getParent()==this)+" groupVis="+getVisibility()+" childVis="+child.getVisibility());
                         childEvent(child,event,bits,true);
                        }
                        break;
                    }
                }
                if(delivered!=null&&!sent){handled|=childEvent(delivered.child,event,delivered.bits,false);}
                if(delivered==null&&!touchTargets.isEmpty())touchTargets.get(touchTargets.size()-1).bits|=bits;
            }
            for(TouchTarget target:new ArrayList<TouchTarget>(touchTargets))if(target!=delivered&&touchTargets.contains(target))handled|=childEvent(target.child,event,target.bits,false);
            if(touchTargets.isEmpty()&&delivered==null&&(intercepted||!handled)){handled=super.dispatchTouchEvent(event);if(handled)selfTouch=true;}
            return handled;
        }finally{
            if(terminal){touchTargets.clear();selfTouch=false;disallowIntercept=false;if(lastTouch!=null){lastTouch.recycle();lastTouch=null;}}
            else if(gestureSplit&&action==MotionEvent.ACTION_POINTER_UP){int bit=1<<event.getPointerId(event.getActionIndex());for(TouchTarget target:new ArrayList<TouchTarget>(touchTargets)){target.bits&=~bit;if(target.bits==0)touchTargets.remove(target);}}
        }
    }
    public void setDescendantFocusability(int value) {
        if (value != FOCUS_BEFORE_DESCENDANTS && value != FOCUS_AFTER_DESCENDANTS && value != FOCUS_BLOCK_DESCENDANTS) throw new IllegalArgumentException("descendant focusability");
        descendantFocusability=value; if (value == FOCUS_BLOCK_DESCENDANTS) for (View child:children) child.clearFocus();
    }
    public int getDescendantFocusability() { return descendantFocusability; }
    @Override public void focusableViewAvailable(View view){
        ViewParent parent=getParent();
        if(parent!=null&&descendantFocusability!=FOCUS_BLOCK_DESCENDANTS&&getVisibility()==VISIBLE
                &&(isFocusableInTouchMode()||!shirazShouldBlockFocusForTouchscreen())
                &&!(isFocused()&&descendantFocusability!=FOCUS_AFTER_DESCENDANTS))
            parent.focusableViewAvailable(view);
    }
    final void shirazRequestChildFocus(View branch) { super.clearFocus(); for (View child:children) if (child != branch) child.clearFocus(); if (getParent() instanceof ViewGroup) ((ViewGroup)getParent()).shirazRequestChildFocus(this); }
    final boolean requestOwnFocus(int direction,android.graphics.Rect previous) {
        if (!super.requestFocus(direction,previous)) return false;
        for (View child:children) child.clearFocus(); return true;
    }
    @Override public boolean requestFocus() {return requestFocus(FOCUS_DOWN,null);}
    @Override public boolean requestFocus(int direction){return requestFocus(direction,null);}
    @Override public boolean requestFocus(int direction,android.graphics.Rect previous) {
        if (getVisibility() != VISIBLE || !isEnabled()) return false;
        if (descendantFocusability != FOCUS_AFTER_DESCENDANTS && requestOwnFocus(direction,previous)) return true;
        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) for (View child:children) if (child.requestFocus(direction,previous)) return true;
        return descendantFocusability == FOCUS_AFTER_DESCENDANTS && requestOwnFocus(direction,previous);
    }
    @Override public void clearFocus() { super.clearFocus(); for (View child:children) child.clearFocus(); }
    public View getFocusedChild() { for (View child:children) if (child.hasFocus()) return child; return null; }
    @Override public boolean hasFocus() { return super.hasFocus() || getFocusedChild() != null; }
    @Override public View findFocus() { if (isFocused()) return this; View child=getFocusedChild(); return child == null ? null : child.findFocus(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if (event == null) throw new NullPointerException("event"); View child=getFocusedChild(); return child != null ? child.dispatchKeyEvent(event) : isFocused() && super.dispatchKeyEvent(event); }
    @Override protected void dispatchSaveInstanceState(android.util.SparseArray<android.os.Parcelable> state){
        super.dispatchSaveInstanceState(state);
        View[] snapshot=new View[getChildCount()];for(int i=0;i<snapshot.length;i++)snapshot[i]=getChildAt(i);
        for(View child:snapshot)if(child.isSaveFromParentEnabled())child.dispatchSaveInstanceState(state);
    }
    @Override protected void dispatchRestoreInstanceState(android.util.SparseArray<android.os.Parcelable> state){
        super.dispatchRestoreInstanceState(state);
        View[] snapshot=new View[getChildCount()];for(int i=0;i<snapshot.length;i++)snapshot[i]=getChildAt(i);
        for(View child:snapshot)if(child.isSaveFromParentEnabled())child.dispatchRestoreInstanceState(state);
    }
    protected void dispatchFreezeSelfOnly(android.util.SparseArray<android.os.Parcelable> state){super.dispatchSaveInstanceState(state);}
    protected void dispatchThawSelfOnly(android.util.SparseArray<android.os.Parcelable> state){super.dispatchRestoreInstanceState(state);}

    @Override public void dispatchWindowSystemUiVisiblityChanged(int flags){super.dispatchWindowSystemUiVisiblityChanged(flags);for(View child:new ArrayList<View>(children))child.dispatchWindowSystemUiVisiblityChanged(flags);}
    @Override public void dispatchSystemUiVisibilityChanged(int flags){super.dispatchSystemUiVisibilityChanged(flags);for(View child:new ArrayList<View>(children))child.dispatchSystemUiVisibilityChanged(flags);}

}
