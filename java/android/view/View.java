// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.graphics.Canvas;
import android.view.accessibility.*;

public class View implements KeyEvent.Callback, android.graphics.drawable.Drawable.Callback {
    public static final int NO_ID = -1, VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public static final int SYSTEM_UI_FLAG_VISIBLE=0,SYSTEM_UI_FLAG_LOW_PROFILE=1,SYSTEM_UI_FLAG_HIDE_NAVIGATION=2,SYSTEM_UI_FLAG_FULLSCREEN=4,SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR=16,SYSTEM_UI_FLAG_LAYOUT_STABLE=256,SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=512,SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=1024,SYSTEM_UI_FLAG_IMMERSIVE=2048,SYSTEM_UI_FLAG_IMMERSIVE_STICKY=4096,SYSTEM_UI_FLAG_LIGHT_STATUS_BAR=8192;
    public interface OnSystemUiVisibilityChangeListener {void onSystemUiVisibilityChange(int visibility);}
    private boolean keepScreenOn;
    public void setKeepScreenOn(boolean value){keepScreenOn=value;}
    public boolean getKeepScreenOn(){return keepScreenOn;}
    public boolean shirazRequestsKeepScreenOn(){return getVisibility()==VISIBLE&&keepScreenOn;}
    private int systemUiVisibility;private OnSystemUiVisibilityChangeListener systemUiListener;
    public void setSystemUiVisibility(int flags){if(systemUiVisibility!=flags){systemUiVisibility=flags;if(windowObserver!=null)windowObserver.systemUiDirty=true;invalidate();}}
    public int getSystemUiVisibility(){return systemUiVisibility;}
    public int getWindowSystemUiVisibility(){return windowObserver==null?0:windowObserver.systemUiVisibility;}
    public void onWindowSystemUiVisibilityChanged(int flags){ }
    public void dispatchWindowSystemUiVisiblityChanged(int flags){onWindowSystemUiVisibilityChanged(flags);}
    public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener listener){systemUiListener=listener;}
    public void dispatchSystemUiVisibilityChanged(int flags){if(systemUiListener!=null)systemUiListener.onSystemUiVisibilityChange(flags&7);}
    public final void shirazUpdateSystemUiVisibility(){
        View root=getRootView();if(root!=this){root.shirazUpdateSystemUiVisibility();return;}
        ViewTreeObserver observer=windowObserver;if(observer==null||!observer.systemUiDirty)return;
        java.util.ArrayList<View> views=observerSubtree();int requested=0;
        for(View view:views){boolean visible=true;for(View current=view;current!=null;current=current.parent)if(current.visibility!=VISIBLE){visible=false;break;}if(visible)requested|=view.systemUiVisibility;}
        observer.systemUiDirty=false;
        if(observer.systemUiVisibility!=requested){observer.systemUiVisibility=requested;observer.insetsDirty=true;dispatchWindowSystemUiVisiblityChanged(requested);}
    }
    public interface OnApplyWindowInsetsListener {WindowInsets onApplyWindowInsets(View view,WindowInsets insets);}
    private OnApplyWindowInsetsListener insetsListener;
    private boolean fitsSystemWindows,optionalFitsSystemWindows,applyingInsets,fittingInsets;
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener){insetsListener=listener;}
    public void setFitsSystemWindows(boolean fit){if(fitsSystemWindows!=fit){fitsSystemWindows=fit;requestApplyInsets();}}
    public boolean getFitsSystemWindows(){return fitsSystemWindows;}
    public void requestFitSystemWindows(){requestApplyInsets();}
    public void requestApplyInsets(){if(windowObserver!=null)windowObserver.insetsDirty=true;requestLayout();}
    public WindowInsets getRootWindowInsets(){return windowObserver==null?null:windowObserver.rootInsets;}
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets){
        if(insets==null)throw new NullPointerException("insets");boolean previous=applyingInsets;applyingInsets=true;
        try{WindowInsets result=insetsListener==null?onApplyWindowInsets(insets):insetsListener.onApplyWindowInsets(this,insets);if(result==null)throw new NullPointerException("Inset listener returned null");return result;}
        finally{applyingInsets=previous;}
    }
    public WindowInsets onApplyWindowInsets(WindowInsets insets){
        boolean consumed=fittingInsets?fitInsets(insets.getSystemWindowInsets()):fitSystemWindows(insets.getSystemWindowInsets());
        return consumed?insets.consumeSystemWindowInsets():insets;
    }
    protected boolean fitSystemWindows(android.graphics.Rect insets){
        if(applyingInsets)return fitInsets(insets);if(insets==null)return false;
        boolean previous=fittingInsets;fittingInsets=true;
        try{return dispatchApplyWindowInsets(new WindowInsets(insets)).isConsumed();}finally{fittingInsets=previous;}
    }
    private boolean fitInsets(android.graphics.Rect insets){
        if(!fitsSystemWindows)return false;android.graphics.Rect local=new android.graphics.Rect();boolean consumed=computeFitSystemWindows(insets,local);setPadding(local.left,local.top,local.right,local.bottom);return consumed;
    }
    public void makeOptionalFitsSystemWindows(){optionalFitsSystemWindows=true;}
    private boolean optionalInsets(){return optionalFitsSystemWindows&&windowObserver!=null&&(getWindowSystemUiVisibility()&0x600)!=0;}
    protected boolean computeFitSystemWindows(android.graphics.Rect inout,android.graphics.Rect local){
        if(optionalInsets()){local.setEmpty();return false;}local.set(inout);inout.setEmpty();return true;
    }
    public WindowInsets computeSystemWindowInsets(WindowInsets insets,android.graphics.Rect local){
        if(!optionalInsets()){local.set(insets.getSystemWindowInsets());return insets.consumeSystemWindowInsets();}local.setEmpty();return insets;
    }
    public final void shirazApplyWindowInsets(int width,int height){
        View root=getRootView();if(root!=this){root.shirazApplyWindowInsets(width,height);return;}
        ViewTreeObserver observer=windowObserver;if(observer==null)return;
        if(!observer.insetsDirty&&observer.insetsWidth==width&&observer.insetsHeight==height)return;
        observer.insetsDirty=false;observer.insetsWidth=width;observer.insetsHeight=height;
        observer.rootInsets=new WindowInsets(new android.graphics.Rect(),null,new android.graphics.Rect(),false,false);dispatchApplyWindowInsets(observer.rootInsets);
    }
    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO=0,IMPORTANT_FOR_ACCESSIBILITY_YES=1,IMPORTANT_FOR_ACCESSIBILITY_NO=2,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
    private AccessibilityDelegate mAccessibilityDelegate;private int accessibilityImportance;private boolean selected,activated;
    public static class AccessibilityDelegate {
        public void sendAccessibilityEvent(View host,int type){host.shirazSendAccessibilityEvent(type);}
        public void sendAccessibilityEventUnchecked(View host,AccessibilityEvent event){host.shirazSendAccessibilityEventUnchecked(event);}
        public boolean dispatchPopulateAccessibilityEvent(View host,AccessibilityEvent event){return host.shirazDispatchPopulateAccessibilityEvent(event);}
        public void onPopulateAccessibilityEvent(View host,AccessibilityEvent event){host.shirazPopulateAccessibilityEvent(event);}
        public void onInitializeAccessibilityEvent(View host,AccessibilityEvent event){host.shirazInitializeAccessibilityEvent(event);}
        public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){host.shirazInitializeAccessibilityNodeInfo(info);}
        public boolean onRequestSendAccessibilityEvent(ViewGroup host,View child,AccessibilityEvent event){return host.shirazRequestSendAccessibilityEvent(child,event);}
        public AccessibilityNodeProvider getAccessibilityNodeProvider(View host){return null;}
        public boolean performAccessibilityAction(View host,int action,android.os.Bundle arguments){return host.shirazPerformAccessibilityAction(action,arguments);}
        public AccessibilityNodeInfo createAccessibilityNodeInfo(View host){return host.shirazCreateAccessibilityNodeInfo();}
        public void addExtraDataToAccessibilityNodeInfo(View host,AccessibilityNodeInfo info,String key,android.os.Bundle arguments){host.addExtraDataToAccessibilityNodeInfo(info,key,arguments);}
    }
    public void setAccessibilityDelegate(AccessibilityDelegate delegate){mAccessibilityDelegate=delegate;}
    public AccessibilityDelegate getAccessibilityDelegate(){return mAccessibilityDelegate;}
    public AccessibilityNodeProvider getAccessibilityNodeProvider(){return mAccessibilityDelegate==null?null:mAccessibilityDelegate.getAccessibilityNodeProvider(this);}
    public void setImportantForAccessibility(int value){if(value!=0&&value!=1&&value!=2&&value!=4)throw new IllegalArgumentException("Accessibility importance");accessibilityImportance=value;}
    public int getImportantForAccessibility(){return accessibilityImportance;}
    protected boolean shirazHasAccessibilityContent(){return description!=null&&description.length()>0;}
    public boolean isImportantForAccessibility(){for(View p=parent;p!=null;p=p.parent)if(p.accessibilityImportance==4)return false;return accessibilityImportance==1||accessibilityImportance==0&&(clickable||isFocusable()||shirazHasAccessibilityContent());}
    public ViewParent getParentForAccessibility(){View p=parent;while(p!=null&&!p.isImportantForAccessibility()&&p.parent!=null)p=p.parent;return (ViewParent)p;}
    public void getLocationInWindow(int[] location){
        if(location==null||location.length<2)throw new IllegalArgumentException("location requires two elements");
        if(windowObserver==null){location[0]=location[1]=0;return;}
        double x=left+translationX,y=top+translationY;for(View ancestor=parent;ancestor!=null;ancestor=ancestor.parent){x+=ancestor.left+ancestor.translationX-ancestor.scrollX;y+=ancestor.top+ancestor.translationY-ancestor.scrollY;}
        location[0]=(int)Math.round(x);location[1]=(int)Math.round(y);
    }
    /** Screen coordinates refer to Shiraz's logical app display, not global desktop coordinates. */
    public void getLocationOnScreen(int[] location){getLocationInWindow(location);if(windowObserver!=null){location[0]+=windowObserver.windowX;location[1]+=windowObserver.windowY;}}
    public void getWindowVisibleDisplayFrame(android.graphics.Rect frame){
        if(frame==null)throw new NullPointerException("frame");
        View root=getRootView();
        if(root!=null&&root.getWidth()>0&&root.getHeight()>0){frame.set(0,0,root.getWidth(),root.getHeight());return;}
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        frame.set(0,0,metrics.widthPixels,metrics.heightPixels);
    }
    public void dispatchConfigurationChanged(android.content.res.Configuration configuration){onConfigurationChanged(configuration);}
    protected void onConfigurationChanged(android.content.res.Configuration configuration){}
    public Display getDisplay(){if(windowObserver==null)return null;android.view.WindowManager manager=(android.view.WindowManager)getContext().getSystemService(android.content.Context.WINDOW_SERVICE);return manager==null?null:manager.getDefaultDisplay();}
    public boolean isShown(){if(windowObserver==null)return false;for(View p=this;p!=null;p=p.parent)if(p.visibility!=VISIBLE)return false;return true;}
    public static final int ACCESSIBILITY_LIVE_REGION_NONE=0,ACCESSIBILITY_LIVE_REGION_POLITE=1,ACCESSIBILITY_LIVE_REGION_ASSERTIVE=2;
    private int accessibilityLiveRegion;
    public int getAccessibilityLiveRegion(){return accessibilityLiveRegion;}
    // Retained in local accessibility snapshots; host service delivery is separate.
    public void setAccessibilityLiveRegion(int value){if(value<0||value>2)throw new IllegalArgumentException("Accessibility live region");accessibilityLiveRegion=value;}
    public CharSequence getAccessibilityClassName(){return getClass().getName();}
    public boolean isAccessibilityFocused(){return false;}
    private boolean hovered;
    public boolean isHovered(){return hovered;}
    public void setHovered(boolean value){if(hovered!=value){hovered=value;refreshDrawableState();invalidate();}}
    public void setSelected(boolean value){if(selected!=value){selected=value;refreshDrawableState();invalidate();}}
    public boolean isSelected(){return selected;}
    public void setActivated(boolean value){if(activated!=value){activated=value;invalidate();refreshDrawableState();dispatchSetActivated(value);}}
    public boolean isActivated(){return activated;}
    protected void dispatchSetActivated(boolean value){}

    public AccessibilityNodeInfo createAccessibilityNodeInfo(){return mAccessibilityDelegate==null?shirazCreateAccessibilityNodeInfo():mAccessibilityDelegate.createAccessibilityNodeInfo(this);}
    private AccessibilityNodeInfo shirazCreateAccessibilityNodeInfo(){AccessibilityNodeProvider provider=getAccessibilityNodeProvider();if(provider!=null)return provider.createAccessibilityNodeInfo(-1);AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(this);try{onInitializeAccessibilityNodeInfo(info);return info;}catch(RuntimeException|Error failure){info.recycle();throw failure;}}
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){if(mAccessibilityDelegate!=null)mAccessibilityDelegate.onInitializeAccessibilityNodeInfo(this,info);else shirazInitializeAccessibilityNodeInfo(info);}
    protected void shirazInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        info.setSource(this);ViewParent owner=getParentForAccessibility();if(owner instanceof View)info.setParent((View)owner);
        info.setPackageName(context.getPackageName());info.setClassName(getAccessibilityClassName());info.setContentDescription(description);info.setLiveRegion(accessibilityLiveRegion);
        info.setEnabled(enabled);info.setClickable(clickable);info.setFocusable(isFocusable());info.setFocused(isFocused());info.setSelected(selected);info.setVisibleToUser(isShown());info.setImportantForAccessibility(isImportantForAccessibility());
        info.setBoundsInParent(new android.graphics.Rect(left,top,right,bottom));long x=left,y=top;for(View p=parent;p!=null;p=p.parent){x+=p.left;y+=p.top;}
        if(x<Integer.MIN_VALUE||y<Integer.MIN_VALUE||x+getWidth()>Integer.MAX_VALUE||y+getHeight()>Integer.MAX_VALUE)throw new IllegalStateException("Accessibility bounds overflow");
        info.setBoundsInScreen(new android.graphics.Rect((int)x,(int)y,(int)x+getWidth(),(int)y+getHeight()));
        if(enabled){if(clickable)info.addAction(AccessibilityNodeInfo.ACTION_CLICK);if(isFocusable())info.addAction(isFocused()?AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:AccessibilityNodeInfo.ACTION_FOCUS);info.addAction(selected?AccessibilityNodeInfo.ACTION_CLEAR_SELECTION:AccessibilityNodeInfo.ACTION_SELECT);}
    }
    public boolean performAccessibilityAction(int action,android.os.Bundle arguments){return mAccessibilityDelegate==null?shirazPerformAccessibilityAction(action,arguments):mAccessibilityDelegate.performAccessibilityAction(this,action,arguments);}
    private boolean shirazPerformAccessibilityAction(int action,android.os.Bundle arguments){if(!enabled||!isShown())return false;switch(action){case AccessibilityNodeInfo.ACTION_CLICK:return clickable&&performClick();case AccessibilityNodeInfo.ACTION_FOCUS:return requestFocus();case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:if(!isFocused())return false;clearFocus();return true;case AccessibilityNodeInfo.ACTION_SELECT:if(selected)return false;setSelected(true);return true;case AccessibilityNodeInfo.ACTION_CLEAR_SELECTION:if(!selected)return false;setSelected(false);return true;default:return false;}}
    public void addExtraDataToAccessibilityNodeInfo(AccessibilityNodeInfo info,String key,android.os.Bundle arguments){}
    public void announceForAccessibility(CharSequence text){
        if(parent==null)return;
        android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager==null||!manager.isEnabled())return;
        AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        onInitializeAccessibilityEvent(event);event.getText().add(text);event.setContentDescription(null);
        parent.requestSendAccessibilityEvent(this,event);
    }
    public void sendAccessibilityEvent(int type){if(mAccessibilityDelegate!=null)mAccessibilityDelegate.sendAccessibilityEvent(this,type);else shirazSendAccessibilityEvent(type);}
    private void shirazSendAccessibilityEvent(int type){/* No connected accessibility service; checked sends are disabled. */}
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event){if(mAccessibilityDelegate!=null)mAccessibilityDelegate.sendAccessibilityEventUnchecked(this,event);else shirazSendAccessibilityEventUnchecked(event);}
    private void shirazSendAccessibilityEventUnchecked(AccessibilityEvent event){onInitializeAccessibilityEvent(event);dispatchPopulateAccessibilityEvent(event);if(parent!=null)parent.requestSendAccessibilityEvent(this,event);}
    public void onInitializeAccessibilityEvent(AccessibilityEvent event){if(mAccessibilityDelegate!=null)mAccessibilityDelegate.onInitializeAccessibilityEvent(this,event);else shirazInitializeAccessibilityEvent(event);}
    protected void shirazInitializeAccessibilityEvent(AccessibilityEvent event){event.setSource(this);event.setClassName(getAccessibilityClassName());event.setPackageName(context.getPackageName());event.setEnabled(enabled);event.setContentDescription(description);}
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event){return mAccessibilityDelegate==null?shirazDispatchPopulateAccessibilityEvent(event):mAccessibilityDelegate.dispatchPopulateAccessibilityEvent(this,event);}
    protected boolean shirazDispatchPopulateAccessibilityEvent(AccessibilityEvent event){onPopulateAccessibilityEvent(event);return false;}
    public void onPopulateAccessibilityEvent(AccessibilityEvent event){if(mAccessibilityDelegate!=null)mAccessibilityDelegate.onPopulateAccessibilityEvent(this,event);else shirazPopulateAccessibilityEvent(event);}
    protected void shirazPopulateAccessibilityEvent(AccessibilityEvent event){}
    private final Context context;
    private volatile ViewGroup parent;
    ViewGroup temporaryParent;
    final ViewGroup shirazHierarchyOwner(){return parent!=null?parent:temporaryParent;}
    final void shirazAssignParent(ViewGroup value){parent=value;}
    final boolean shirazSameWindow(View other){return windowObserver==other.windowObserver;}
    final void shirazAssignLayoutParams(ViewGroup.LayoutParams value){layoutParams=value;}
    private boolean temporarilyDetached;
    public final boolean isTemporarilyDetached(){return temporarilyDetached;}
    public void dispatchStartTemporaryDetach(){temporarilyDetached=true;onStartTemporaryDetach();}
    public void dispatchFinishTemporaryDetach(){temporarilyDetached=false;onFinishTemporaryDetach();}
    public void onStartTemporaryDetach(){clickEligible=false;setPressed(false);}
    public void onFinishTemporaryDetach(){}
    private ViewGroup.LayoutParams layoutParams;
    public static final int LAYOUT_DIRECTION_LTR=0, LAYOUT_DIRECTION_RTL=1, LAYOUT_DIRECTION_INHERIT=2, LAYOUT_DIRECTION_LOCALE=3;
    private int layoutDirection=LAYOUT_DIRECTION_INHERIT;
    public ViewParent getParent() { return parent; }
    public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View view);void onViewDetachedFromWindow(View view);}
    private java.util.ArrayList<OnAttachStateChangeListener> attachListeners;
    private boolean attachNotified,detaching,detachTreeInProgress;
    private long attachmentGeneration;
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener){if(listener==null)throw new NullPointerException("listener");if(attachListeners==null)attachListeners=new java.util.ArrayList<OnAttachStateChangeListener>();if(attachListeners.size()>=1024)throw new IllegalStateException("Attach listener limit");attachListeners.add(listener);}
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener){if(attachListeners!=null)attachListeners.remove(listener);}
    private android.view.animation.Animation animation;
    private final android.view.animation.Transformation animationTransform=new android.view.animation.Transformation();
    private float animationAlpha=1;
    private boolean legacyStarted,legacyEnded;
    public android.view.animation.Animation getAnimation(){return animation;}
    public void setAnimation(android.view.animation.Animation value){animation=value;legacyStarted=legacyEnded=false;if(value!=null)value.reset();invalidate();}
    public void startAnimation(android.view.animation.Animation value){if(value==null)throw new NullPointerException("animation");value.setStartTime(android.view.animation.Animation.START_ON_FIRST_FRAME);setAnimation(value);invalidate();}
    public void clearAnimation(){android.view.animation.Animation previous=animation;animation=null;animationAlpha=1;legacyStarted=legacyEnded=false;try{if(previous!=null)previous.detach();}finally{invalidate();}}
    protected void onAnimationStart(){}
    protected void onAnimationEnd(){}
    final void shirazApplyLegacyAnimation(Canvas canvas,long time){
        animationAlpha=1;android.view.animation.Animation current=animation;if(current==null)return;
        if(current.getBackgroundColor()!=0||current.getDetachWallpaper())throw new UnsupportedOperationException("Legacy animation background/wallpaper effects are not implemented");
        try{
            if(!current.isInitialized())current.initialize(getWidth(),getHeight(),parent==null?getWidth():parent.getWidth(),parent==null?getHeight():parent.getHeight());
            animationTransform.clear();boolean more=current.getTransformation(time,animationTransform);
            if(animation!=current)return;
            if(!legacyStarted&&current.hasStarted()){legacyStarted=true;onAnimationStart();}
            if(animation!=current)return;
            try{canvas.concat(animationTransform.getMatrix());}catch(IllegalArgumentException ignored){animationAlpha=0;return;}animationAlpha=animationTransform.getAlpha();
            if(animationTransform.hasClipRect())canvas.clipRect(animationTransform.getClipRect());
            if(more){postInvalidateOnAnimation();}else{if(animation==current&&!current.getFillAfter()){animation=null;postInvalidateOnAnimation();}if(legacyStarted&&!legacyEnded){legacyEnded=true;onAnimationEnd();}}
        }catch(RuntimeException|Error failure){if(animation==current)animation=null;animationAlpha=1;legacyStarted=legacyEnded=false;throw failure;}
    }
    private ViewPropertyAnimator propertyAnimator;
    public ViewPropertyAnimator animate(){if(propertyAnimator==null)propertyAnimator=new ViewPropertyAnimator(this);return propertyAnimator;}
    private int transientStateCount;
    public boolean hasTransientState(){return transientStateCount>0;}
    public void setHasTransientState(boolean value){if(value){if(transientStateCount==Integer.MAX_VALUE)throw new IllegalStateException("Transient state count overflow");transientStateCount++;}else if(transientStateCount>0)transientStateCount--;}
    private android.animation.StateListAnimator stateListAnimator;
    public android.animation.StateListAnimator getStateListAnimator(){return stateListAnimator;}
    public void setStateListAnimator(android.animation.StateListAnimator value){if(value==stateListAnimator)return;if(stateListAnimator!=null)stateListAnimator.setTarget(null);stateListAnimator=value;if(value!=null){value.setTarget(this);if(isAttachedToWindow())value.setState(getDrawableState());}}
    protected void onAttachedToWindow(){}
    protected void onDetachedFromWindow(){}
    final boolean shirazIsDetaching(){return detaching||detachTreeInProgress;}
    private void notifyAttachment(boolean attached){
        java.util.ArrayList<OnAttachStateChangeListener> listeners=attachListeners==null?null:new java.util.ArrayList<OnAttachStateChangeListener>(attachListeners);
        long generation=attachmentGeneration;
        if(attached){flushPendingActions();onAttachedToWindow();refreshDrawableState();if(windowObserver!=null)onWindowVisibilityChanged(getWindowVisibility());if(stateListAnimator!=null){stateListAnimator.setTarget(this);stateListAnimator.setState(getDrawableState());}}else{stopNestedScroll();shiraz.runtime.TooltipOverlay.cancel(this);cancelLongPress();if(animation!=null)clearAnimation();cancelFrameInvalidation();if(stateListAnimator!=null)stateListAnimator.setTarget(null);onDetachedFromWindow();}
        if(listeners!=null)for(OnAttachStateChangeListener listener:listeners){if(attached){if(windowObserver==null||attachmentGeneration!=generation)return;listener.onViewAttachedToWindow(this);}else listener.onViewDetachedFromWindow(this);}
    }
    private ViewTreeObserver treeObserver;private volatile ViewTreeObserver windowObserver;
    public View getRootView(){View root=this;while(root.parent!=null)root=root.parent;return root;}
    public WindowId getWindowId(){ViewTreeObserver observer=windowObserver;return observer==null?null:observer.windowId;}
    public android.os.IBinder getWindowToken(){ViewTreeObserver observer=windowObserver;return observer==null?null:observer.windowToken;}
    public boolean isInTouchMode(){ViewTreeObserver observer=windowObserver;return observer!=null&&observer.touchMode;}
    public final void shirazSetTouchMode(boolean value){ViewTreeObserver observer=windowObserver;if(observer==null||observer.touchMode==value)return;observer.touchMode=value;observer.dispatchOnTouchModeChanged(value);if(value){View focused=getRootView().findFocus();if(focused!=null&&!focused.isFocusableInTouchMode())focused.clearFocus();}}
    public boolean hasOverlappingRendering(){return true;}
    public int getWindowVisibility(){ViewTreeObserver observer=windowObserver;return observer==null?GONE:observer.windowVisibility;}
    protected void onWindowVisibilityChanged(int visibility){}
    public void dispatchWindowVisibilityChanged(int visibility){onWindowVisibilityChanged(visibility);}
    public final void shirazSetWindowVisibility(int visibility){if(visibility!=VISIBLE&&visibility!=INVISIBLE&&visibility!=GONE)throw new IllegalArgumentException("Window visibility");ViewTreeObserver observer=windowObserver;if(observer==null||observer.windowVisibility==visibility)return;observer.windowVisibility=visibility;dispatchWindowVisibilityChanged(visibility);invalidate();}
    public boolean hasWindowFocus(){ViewTreeObserver observer=windowObserver;return observer!=null&&observer.windowFocused;}
    public void onWindowFocusChanged(boolean hasFocus){if(!hasFocus){cancelLongPress();setPressed(false);}refreshDrawableState();}
    public void dispatchWindowFocusChanged(boolean hasFocus){onWindowFocusChanged(hasFocus);}
    public final void shirazSetWindowFocus(boolean hasFocus){ViewTreeObserver observer=windowObserver;if(observer==null||observer.windowFocused==hasFocus)return;observer.windowFocused=hasFocus;dispatchWindowFocusChanged(hasFocus);if(observer.isAlive())observer.dispatchOnWindowFocusChange(hasFocus);}
    public boolean isHardwareAccelerated(){return false;}
    public boolean isAttachedToWindow(){return windowObserver!=null;}
    public android.os.Handler getHandler(){ViewTreeObserver observer=windowObserver;return observer==null?null:observer.windowHandler();}
    public ViewTreeObserver getViewTreeObserver(){if(windowObserver!=null)return windowObserver;if(treeObserver==null)treeObserver=new ViewTreeObserver();return treeObserver;}
    private java.util.ArrayList<View> observerSubtree(){
        java.util.ArrayList<View> result=new java.util.ArrayList<View>();java.util.ArrayDeque<View> pending=new java.util.ArrayDeque<View>();java.util.ArrayDeque<Integer> depths=new java.util.ArrayDeque<Integer>();pending.push(this);depths.push(0);
        while(!pending.isEmpty()){View view=pending.pop();int depth=depths.pop();if(result.size()>=65536||depth>128)throw new IllegalStateException("Window view tree limit");result.add(view);if(view instanceof ViewGroup){java.util.ArrayList<View> children=((ViewGroup)view).shirazWindowChildren();for(int i=children.size()-1;i>=0;i--){pending.push(children.get(i));depths.push(depth+1);}}}return result;
    }
    public final void shirazAttachTree(ViewTreeObserver observer){
        if(windowObserver==observer)return;if(windowObserver!=null)throw new IllegalStateException("View already attached");
        java.util.ArrayList<View> views=observerSubtree();java.util.ArrayList<ViewTreeObserver> pending=new java.util.ArrayList<ViewTreeObserver>();
        for(View view:views){if(view.windowObserver!=null)throw new IllegalStateException("Subtree already attached");if(view.treeObserver!=null)pending.add(view.treeObserver);}
        observer.mergeAll(pending,views.size());observer.systemUiDirty=true;observer.insetsDirty=true;long[] generations=new long[views.size()];
        for(int i=0;i<views.size();i++){View view=views.get(i);view.windowObserver=observer;view.treeObserver=null;generations[i]=++view.attachmentGeneration;}
        for(int i=0;i<views.size();i++){View view=views.get(i);if(view.windowObserver==observer&&view.attachmentGeneration==generations[i]&&!view.attachNotified){view.attachNotified=true;view.notifyAttachment(true);}}
    }
    private static void detachedOrder(View view,int depth,java.util.ArrayList<View> result){
        if(depth>128||result.size()>=65536)throw new IllegalStateException("Window view tree limit");
        if(view instanceof ViewGroup){for(View child:((ViewGroup)view).shirazWindowChildren())detachedOrder(child,depth+1,result);}
        result.add(view);
    }
    public final void shirazDetachTree(){
        if(windowObserver==null||detachTreeInProgress)return;
        java.util.ArrayList<View> views=new java.util.ArrayList<View>();detachedOrder(this,0,views);ViewTreeObserver old=windowObserver;old.systemUiDirty=true;detachTreeInProgress=true;Throwable failure=null;
        try{for(int i=0;i<views.size();i++){View view=views.get(i);if(view.windowObserver!=old||view.detaching)continue;view.detaching=true;
            try{if(view.attachNotified){view.attachNotified=false;view.notifyAttachment(false);}}
            catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            finally{try{if(view instanceof ViewGroup)((ViewGroup)view).shirazFinishTransitions();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}finally{view.windowObserver=null;view.temporarilyDetached=false;view.attachmentGeneration++;old.attachedViews--;view.detaching=false;}}
        }}finally{detachTreeInProgress=false;}
        if(failure instanceof RuntimeException)throw (RuntimeException)failure;if(failure instanceof Error)throw (Error)failure;
    }
    final void shirazSetParent(ViewGroup value){
        if(value==null){try{shirazDetachTree();}finally{parent=null;}return;}
        ViewGroup previous=parent;parent=value;
        try{if(((View)value).windowObserver!=null)shirazAttachTree(((View)value).windowObserver);}
        catch(RuntimeException|Error error){if(windowObserver==null&&parent==value)parent=previous;throw error;}
    }
    public void setLayoutParams(ViewGroup.LayoutParams value) {
        if (value == null) throw new NullPointerException("layout params");
        if (value.height > 0 && value.height < 320 && getClass().getName().endsWith(".MediaKeyboard")) {
            value.height = 320;
        }
        layoutParams=value; requestLayout();
    }
    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }
    private boolean nestedScrollingEnabled;
    private ViewParent nestedScrollingParent;
    public void setNestedScrollingEnabled(boolean enabled){if(nestedScrollingEnabled)stopNestedScroll();nestedScrollingEnabled=enabled;}
    public boolean isNestedScrollingEnabled(){return nestedScrollingEnabled;}
    public boolean hasNestedScrollingParent(){return nestedScrollingParent!=null;}
    public boolean startNestedScroll(int axes){
        if(hasNestedScrollingParent())return true;if(!nestedScrollingEnabled)return false;
        View child=this;for(ViewParent p=getParent();p!=null;p=p.getParent()){
            if(p.onStartNestedScroll(child,this,axes)){nestedScrollingParent=p;p.onNestedScrollAccepted(child,this,axes);return true;}
            if(p instanceof View)child=(View)p;
        }return false;
    }
    public void stopNestedScroll(){if(nestedScrollingParent!=null){ViewParent p=nestedScrollingParent;nestedScrollingParent=null;p.onStopNestedScroll(this);}}
    private static void nestedArray(int[] value){if(value!=null&&value.length<2)throw new IllegalArgumentException("Nested scroll array requires two entries");}
    public boolean dispatchNestedPreScroll(int dx,int dy,int[] consumed,int[] offset){
        nestedArray(consumed);nestedArray(offset);if(!nestedScrollingEnabled||nestedScrollingParent==null)return false;
        if(dx==0&&dy==0){if(offset!=null){offset[0]=0;offset[1]=0;}return false;}
        int x=0,y=0;if(offset!=null){getLocationInWindow(offset);x=offset[0];y=offset[1];}
        int[] result=consumed==null?new int[2]:consumed;result[0]=0;result[1]=0;nestedScrollingParent.onNestedPreScroll(this,dx,dy,result);
        if(offset!=null){getLocationInWindow(offset);offset[0]-=x;offset[1]-=y;}return result[0]!=0||result[1]!=0;
    }
    public boolean dispatchNestedScroll(int dx,int dy,int unconsumedX,int unconsumedY,int[] offset){
        nestedArray(offset);if(!nestedScrollingEnabled||nestedScrollingParent==null)return false;
        if(dx==0&&dy==0&&unconsumedX==0&&unconsumedY==0){if(offset!=null){offset[0]=0;offset[1]=0;}return false;}
        int x=0,y=0;if(offset!=null){getLocationInWindow(offset);x=offset[0];y=offset[1];}
        nestedScrollingParent.onNestedScroll(this,dx,dy,unconsumedX,unconsumedY);
        if(offset!=null){getLocationInWindow(offset);offset[0]-=x;offset[1]-=y;}return true;
    }
    public boolean dispatchNestedFling(float x,float y,boolean consumed){return nestedScrollingEnabled&&nestedScrollingParent!=null&&nestedScrollingParent.onNestedFling(this,x,y,consumed);}
    public boolean dispatchNestedPreFling(float x,float y){return nestedScrollingEnabled&&nestedScrollingParent!=null&&nestedScrollingParent.onNestedPreFling(this,x,y);}
    // TextView currently positions text through its gravity; report that mode to widgets.
    public int getTextAlignment() { return 1; } // TEXT_ALIGNMENT_GRAVITY
    public int getLayoutDirection() { if(layoutDirection==LAYOUT_DIRECTION_LOCALE)return android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault());return layoutDirection == LAYOUT_DIRECTION_INHERIT ? parent == null ? LAYOUT_DIRECTION_LTR : parent.getLayoutDirection() : layoutDirection; }
    public void setLayoutDirection(int value) {
        if (value != LAYOUT_DIRECTION_LTR && value != LAYOUT_DIRECTION_RTL && value != LAYOUT_DIRECTION_INHERIT && value != LAYOUT_DIRECTION_LOCALE) throw new IllegalArgumentException("layout direction");
        layoutDirection=value; onRtlPropertiesChanged(getLayoutDirection()); requestLayout();
    }
    public void onRtlPropertiesChanged(int direction) { }
    public View findFocus() { return isFocused() ? this : null; }
    private int id = NO_ID, left, top, right, bottom, visibility = VISIBLE;
    private android.graphics.drawable.Drawable backgroundDrawable;
    private volatile boolean invalidated = true;
    public interface OnCreateContextMenuListener { void onCreateContextMenu(ContextMenu menu,View view,ContextMenu.ContextMenuInfo info); }
    private OnCreateContextMenuListener contextMenuListener;
    public void setOnCreateContextMenuListener(OnCreateContextMenuListener listener){contextMenuListener=listener;}
    protected ContextMenu.ContextMenuInfo getContextMenuInfo(){return null;}
    protected void onCreateContextMenu(ContextMenu menu){ }
    public void createContextMenu(ContextMenu menu){
        if(menu==null)throw new NullPointerException("menu");
        ContextMenu.ContextMenuInfo info=getContextMenuInfo();
        shiraz.runtime.HostContextMenu host=menu instanceof shiraz.runtime.HostContextMenu?(shiraz.runtime.HostContextMenu)menu:null;
        if(host!=null)host.shirazSetCurrentInfo(info);
        try{onCreateContextMenu(menu);if(contextMenuListener!=null)contextMenuListener.onCreateContextMenu(menu,this,info);}
        finally{if(host!=null)host.shirazSetCurrentInfo(null);}
        if(parent!=null)parent.createContextMenu(menu);
    }
    public boolean showContextMenu(){throw new UnsupportedOperationException("Context menu popup presentation is not implemented");}
    public boolean showContextMenu(float x,float y){throw new UnsupportedOperationException("Context menu popup presentation is not implemented");}
    public interface OnDragListener {boolean onDrag(View view,DragEvent event);}
    private OnDragListener dragListener;
    public void setOnDragListener(OnDragListener listener){dragListener=listener;}
    public boolean dispatchDragEvent(DragEvent event){if(event==null)throw new NullPointerException("event");return enabled&&dragListener!=null&&dragListener.onDrag(this,event)||onDragEvent(event);}
    public boolean onDragEvent(DragEvent event){return false;}
    public interface OnGenericMotionListener { boolean onGenericMotion(View view, MotionEvent event); }
    private OnGenericMotionListener genericMotionListener;
    public void setOnGenericMotionListener(OnGenericMotionListener listener) { genericMotionListener=listener; }
    public interface OnTouchListener { boolean onTouch(View view, MotionEvent event); }
    public interface OnLongClickListener { boolean onLongClick(View view); }
    public interface OnFocusChangeListener {void onFocusChange(View view,boolean hasFocus);}
    private OnFocusChangeListener focusChangeListener;
    public void setOnFocusChangeListener(OnFocusChangeListener listener){focusChangeListener=listener;}
    public OnFocusChangeListener getOnFocusChangeListener(){return focusChangeListener;}
    public interface OnClickListener { void onClick(View view); }
    public interface OnLayoutChangeListener { void onLayoutChange(View view,int left,int top,int right,int bottom,int oldLeft,int oldTop,int oldRight,int oldBottom); }
    private java.util.ArrayList<OnLayoutChangeListener> layoutListeners;
    private boolean layoutRequired,laidOut;
    public void addOnLayoutChangeListener(OnLayoutChangeListener listener) {
        if(listener==null)throw new NullPointerException("layout listener");
        if(layoutListeners==null)layoutListeners=new java.util.ArrayList<OnLayoutChangeListener>();
        if(layoutListeners.contains(listener))return;
        if(layoutListeners.size()>=1024)throw new IllegalStateException("Layout listener limit");layoutListeners.add(listener);
    }
    public void removeOnLayoutChangeListener(OnLayoutChangeListener listener) { if(layoutListeners!=null){layoutListeners.remove(listener);if(layoutListeners.isEmpty())layoutListeners=null;} }
    public boolean isLaidOut() { return laidOut; }
    private OnTouchListener touchListener;
    private OnClickListener clickListener;
    private OnLongClickListener longClickListener;private boolean longClickable,longHandled;private Runnable longPress;private long longGeneration;
    public void setOnLongClickListener(OnLongClickListener listener){longClickListener=listener;if(listener!=null)setLongClickable(true);}
    public void setLongClickable(boolean value){longClickable=value;if(!value)cancelLongPress();}
    public boolean isLongClickable(){return longClickable;}
    private PointerIcon pointerIcon;
    public void setPointerIcon(PointerIcon value){pointerIcon=value;invalidate();}
    public PointerIcon getPointerIcon(){return pointerIcon;}
    public PointerIcon onResolvePointerIcon(MotionEvent event,int pointerIndex){return pointerIcon;}
    public PointerIcon shirazPointerIcon(float x,float y){MotionEvent event=MotionEvent.shirazObtain(0,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_HOVER_MOVE,x,y,0x2002);try{return onResolvePointerIcon(event,0);}finally{event.recycle();}}
    private CharSequence tooltipText;
    public CharSequence getTooltipText(){return tooltipText;}
    public void setTooltipText(CharSequence text){if(text!=null&&text.length()>4096)throw new IllegalArgumentException("Tooltip text limit");shiraz.runtime.TooltipOverlay.cancel(this);tooltipText=text==null||text.length()==0?null:text.toString();if(tooltipText!=null)setLongClickable(true);else if(longClickListener==null)setLongClickable(false);}
    public boolean performLongClick(){if(longClickListener!=null&&longClickListener.onLongClick(this))return true;return shiraz.runtime.TooltipOverlay.show(this,tooltipText);}
    public boolean performLongClick(float x,float y){return performLongClick();}
    private boolean cancelInputSuperCalled;
    public final void cancelPendingInputEvents(){dispatchCancelPendingInputEvents();}
    void dispatchCancelPendingInputEvents(){cancelInputSuperCalled=false;onCancelPendingInputEvents();if(!cancelInputSuperCalled)throw new android.util.SuperNotCalledException("View must call super.onCancelPendingInputEvents()");}
    public void onCancelPendingInputEvents(){cancelLongPress();cancelInputSuperCalled=true;}
    public void cancelLongPress(){longGeneration++;if(longPress!=null){removeCallbacks(longPress);longPress=null;}}
    private void scheduleLongPress(final float x,final float y){cancelLongPress();if(!longClickable)return;final long generation=longGeneration;longPress=new Runnable(){public void run(){if(longPress!=this||longGeneration!=generation)return;longPress=null;if(tracking&&clickEligible&&pressed&&enabled&&longClickable&&getVisibility()==VISIBLE){boolean handled=performLongClick(x,y);if(longGeneration==generation)longHandled=handled;}}};postDelayed(longPress,ViewConfiguration.getLongPressTimeout());}

    private boolean enabled = true, clickable, pressed, tracking, clickEligible;
    public void setOnTouchListener(OnTouchListener listener) { touchListener = listener; }
    public void setOnClickListener(OnClickListener listener) { clickListener = listener; if (listener != null) setClickable(true); }
    public void setClickable(boolean value) { clickable = value; if (!value) { tracking = false; pressedKey = -1; keyState.reset(); setPressed(false); if (!isFocusable()) clearFocus(); } }
    public boolean isClickable() { return clickable; }
    public void setEnabled(boolean value) { enabled = value; if (!value) { tracking = false; clearFocus(); } refreshDrawableState(); invalidate(); }
    public boolean isEnabled() { return enabled; }
    public void setPressed(boolean value) { if(!value)cancelLongPress();if (pressed != value) { pressed = value; refreshDrawableState(); invalidate(); } }
    public boolean isPressed() { return pressed; }
    public boolean hasOnClickListeners(){return clickListener!=null;}
    public boolean performClick() { shiraz.runtime.ViewDiagnostics.input(this,-1,true); if (clickListener == null) return false; clickListener.onClick(this); return true; }

    public void drawableHotspotChanged(float x,float y){if(backgroundDrawable!=null)backgroundDrawable.setHotspot(x,y);if(foregroundDrawable!=null)foregroundDrawable.setHotspot(x,y);}
    public void requestUnbufferedDispatch(MotionEvent event) { if(event==null)throw new NullPointerException("event"); }
    private boolean filterTouchesWhenObscured;
    public void setFilterTouchesWhenObscured(boolean filter) { filterTouchesWhenObscured = filter; }
    public boolean getFilterTouchesWhenObscured() { return filterTouchesWhenObscured; }
    public boolean onFilterTouchEventForSecurity(MotionEvent event) {
        return !filterTouchesWhenObscured || (event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) == 0;
    }
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) throw new NullPointerException("event");
        if (!onFilterTouchEventForSecurity(event)) return false;
        shiraz.runtime.ViewDiagnostics.input(this,event.getActionMasked(),false);
        if(enabled&&(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE))drawableHotspotChanged(event.getX(),event.getY());
        if (enabled && touchListener != null && touchListener.onTouch(this, event)) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) { tracking = false; setPressed(false); }
            return true;
        }
        return onTouchEvent(event);
    }
    private boolean inside(MotionEvent event) { return event.getX() >= 0 && event.getY() >= 0 && event.getX() < getWidth() && event.getY() < getHeight(); }
    private TouchDelegate touchDelegate;
    public void setTouchDelegate(TouchDelegate delegate){touchDelegate=delegate;}
    public TouchDelegate getTouchDelegate(){return touchDelegate;}
    public boolean onTouchEvent(MotionEvent event) {
        if(touchDelegate!=null&&touchDelegate.onTouchEvent(event))return true;
        if (!clickable&&!longClickable) return false;
        if (!enabled) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: longHandled=false;tracking = true; clickEligible = inside(event); setPressed(clickEligible);if(clickEligible)scheduleLongPress(event.getX(),event.getY());return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking && !inside(event)) { clickEligible = false; setPressed(false); }
                return tracking;
            case MotionEvent.ACTION_UP:
                boolean wasTracking = tracking, click = tracking && clickEligible && inside(event) && clickable && !longHandled;
                tracking = false; setPressed(false); if (click) performClick(); return wasTracking;
            case MotionEvent.ACTION_CANCEL:
                boolean canceled = tracking; tracking = false; setPressed(false); return canceled;
            default: return false;
        }
    }
    public static final int NOT_FOCUSABLE = 0, FOCUSABLE = 1, FOCUSABLE_AUTO = 16,
        FOCUS_BACKWARD = 1, FOCUS_FORWARD = 2, FOCUS_LEFT = 17, FOCUS_UP = 33, FOCUS_RIGHT = 66, FOCUS_DOWN = 130;
    public interface OnKeyListener { boolean onKey(View view, int keyCode, KeyEvent event); }
    private OnKeyListener keyListener;
    private final KeyEvent.DispatcherState keyState = new KeyEvent.DispatcherState();
    private int focusability = FOCUSABLE_AUTO, pressedKey = -1;
    private boolean focused, focusableInTouchMode;
    private boolean keyboardNavigationCluster,focusedByDefault;
    private int nextClusterForwardId=NO_ID;
    public void setKeyboardNavigationCluster(boolean value){keyboardNavigationCluster=value;}
    public final boolean isKeyboardNavigationCluster(){return keyboardNavigationCluster;}
    public void setNextClusterForwardId(int value){nextClusterForwardId=value;}
    public int getNextClusterForwardId(){return nextClusterForwardId;}
    public void setFocusedByDefault(boolean value){focusedByDefault=value;}
    public final boolean isFocusedByDefault(){return focusedByDefault;}
    public boolean hasFocusable(){return KeyboardClusters.focusable(this);}
    public void addKeyboardNavigationClusters(java.util.Collection<View> views,int direction){KeyboardClusters.forward(direction);KeyboardClusters.collect(this,views,0);}
    public View keyboardNavigationClusterSearch(View current,int direction){return KeyboardClusters.search(this,current,direction);}
    public boolean restoreFocusInCluster(int direction){return KeyboardClusters.restore(this,direction,false);}
    public boolean restoreFocusNotInCluster(){return KeyboardClusters.restore(this,FOCUS_DOWN,true);}
    public boolean restoreDefaultFocus(){return KeyboardClusters.restoreDefault(this);}

    private boolean defaultFocusHighlightEnabled=true;
    private android.graphics.drawable.Drawable defaultFocusHighlight;
    public void setDefaultFocusHighlightEnabled(boolean value){if(defaultFocusHighlightEnabled!=value){defaultFocusHighlightEnabled=value;invalidate();}}
    public final boolean getDefaultFocusHighlightEnabled(){return defaultFocusHighlightEnabled;}
    private void drawDefaultFocusHighlight(Canvas canvas){
        if(!defaultFocusHighlightEnabled||!focused||!isAttachedToWindow())return;
        if(backgroundDrawable!=null&&backgroundDrawable.hasFocusStateSpecified()||foregroundDrawable!=null&&foregroundDrawable.hasFocusStateSpecified())return;
        if(defaultFocusHighlight==null){android.content.res.Resources.Theme theme=context.getTheme();if(theme==null)return;android.content.res.TypedArray values=theme.obtainStyledAttributes(new int[]{0x0101030e});try{defaultFocusHighlight=values.getDrawable(0);}finally{values.recycle();}if(defaultFocusHighlight==null)return;defaultFocusHighlight.setCallback(this);}
        defaultFocusHighlight.setState(getDrawableState());defaultFocusHighlight.setBounds(0,0,getWidth(),getHeight());defaultFocusHighlight.draw(canvas);
    }
    public void setOnKeyListener(OnKeyListener listener) { keyListener = listener; }
    public void setFocusable(boolean value) { setFocusable(value ? FOCUSABLE : NOT_FOCUSABLE); }
    public void setFocusable(int value) { if (value != FOCUSABLE && value != NOT_FOCUSABLE && value != FOCUSABLE_AUTO) throw new IllegalArgumentException("focusability"); focusability = value; if (!isFocusable()) clearFocus(); }
    public int getFocusable() { return focusability; }
    public boolean isFocusable() { return focusability == FOCUSABLE || focusability == FOCUSABLE_AUTO && clickable; }
    public void setFocusableInTouchMode(boolean value) { focusableInTouchMode = value; if (value) setFocusable(true); }
    public boolean isFocusableInTouchMode() { return focusableInTouchMode; }
    public boolean requestFocus() {return requestFocus(FOCUS_DOWN,null);}
    public boolean requestFocus(int direction,android.graphics.Rect previous) {
        if(focused)return true;
        if (!enabled || visibility != VISIBLE || !isFocusable() || isInTouchMode()&&!isFocusableInTouchMode() || !KeyboardClusters.touchEligible(this)) return false;
        for (ViewParent owner=parent; owner instanceof ViewGroup; owner=owner.getParent()) {
            ViewGroup group=(ViewGroup)owner;
            if (group.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS || group.getVisibility() != VISIBLE) return false;
        }
        if (parent != null) parent.shirazRequestChildFocus(this);
        focused=true; KeyboardClusters.remember(this);refreshDrawableState();onFocusChanged(true,direction,previous); return true;
    }
    public boolean requestFocus(int direction) { return requestFocus(direction,null); }
    public boolean hasFocus() { return focused; }
    public boolean isFocused() { return focused; }
    protected void onFocusChanged(boolean gainFocus,int direction,android.graphics.Rect previous){if(focusChangeListener!=null)focusChangeListener.onFocusChange(this,gainFocus);}
    public void clearFocus() { boolean wasFocused=focused;focused = false; refreshDrawableState(); pressedKey = -1; keyState.reset(); setPressed(false);if(wasFocused)onFocusChanged(false,0,null); }
    public KeyEvent.DispatcherState getKeyDispatcherState() { return keyState; }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null) throw new NullPointerException("event");
        if (enabled && keyListener != null && keyListener.onKey(this,event.getKeyCode(),event)) return true;
        return event.dispatch(this,keyState,this);
    }
    public boolean onKeyDown(int code, KeyEvent event) {
        if (!KeyEvent.isConfirmKey(code) || !event.hasNoModifiers()) return false;
        if (!enabled) return true;
        if ((clickable||longClickable) && event.getRepeatCount() == 0) { longHandled=false;pressedKey = code;setPressed(true);event.startTracking();return true; }
        return false;
    }
    public boolean onKeyLongPress(int code, KeyEvent event) { if(enabled&&longClickable&&pressedKey==code&&pressed){longHandled=performLongClick();return longHandled;}return false; }
    /** Host runtime views are not layout-editor previews. */
    public boolean isInEditMode(){return false;}
    public boolean onCheckIsTextEditor(){return false;}
    public boolean requestRectangleOnScreen(android.graphics.Rect rectangle){return requestRectangleOnScreen(rectangle,false);}
    public boolean requestRectangleOnScreen(android.graphics.Rect rectangle,boolean immediate){
        android.graphics.Rect requested=new android.graphics.Rect(rectangle);View child=this;ViewParent owner=getParent();boolean scrolled=false;
        while(owner!=null){scrolled|=owner.requestChildRectangleOnScreen(child,requested,immediate);if(!(owner instanceof View))break;requested.offset(child.getLeft()-child.getScrollX(),child.getTop()-child.getScrollY());child=(View)owner;owner=child.getParent();}
        return scrolled;
    }
    public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info){return null;}
    public boolean onKeyMultiple(int code, int count, KeyEvent event) { return false; }
    public boolean onKeyUp(int code, KeyEvent event) {
        if (pressedKey != code) return false;
        boolean wasPressed = pressed; pressedKey = -1; setPressed(false);
        if (wasPressed && enabled && clickable && !longHandled && !event.isCanceled() && event.hasNoModifiers()) performClick();
        return true;
    }
    private android.os.Handler handler;
    private synchronized android.os.Handler handler() {
        ViewTreeObserver observer=windowObserver;
        if(observer!=null)handler=observer.windowHandler();
        else if (handler == null) handler = new android.os.Handler(android.os.Looper.getMainLooper());
        return handler;
    }
    private static final String[] CONSTRUCTOR_ATTRIBUTES={"accessibilityLiveRegion","alpha","autofillHints","background","backgroundTint","backgroundTintMode","clickable","clipToOutline","contentDescription","defaultFocusHighlightEnabled","duplicateParentState","elevation","enabled","fadingEdge","fitsSystemWindows","focusable","focusableInTouchMode","focusedByDefault","foreground","foregroundGravity","foregroundInsidePadding","foregroundTint","foregroundTintMode","id","importantForAccessibility","importantForAutofill","isScrollContainer","keepScreenOn","keyboardNavigationCluster","layoutDirection","longClickable","minHeight","minWidth","nextClusterForward","onClick","outlineProvider","overScrollMode","padding","paddingBottom","paddingEnd","paddingLeft","paddingRight","paddingStart","paddingTop","requiresFadingEdge","rotation","rotationX","rotationY","saveEnabled","scaleX","scaleY","scrollX","scrollY","scrollbars","stateListAnimator","tag","tooltipText","transitionName","translationX","translationY","translationZ","visibility"};
    public View(Context context) {
        if (context == null) throw new NullPointerException("context");
        this.context = context;
    }
    private String transitionName;
    public final String getTransitionName(){return transitionName;}
    public final void setTransitionName(String name){transitionName=name;}
    public View(Context context, android.util.AttributeSet attrs) { this(context,attrs,0); }
    public View(Context context, android.util.AttributeSet attrs, int style) { this(context,attrs,style,0); }
    public View(Context context, android.util.AttributeSet attrs, int style, int resource) {
        this(context);
        if(attrs!=null||style!=0||resource!=0){shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);a.prefetch(CONSTRUCTOR_ATTRIBUTES);
            a.reject("rotationX rotationY foregroundTint foregroundTintMode");if(a.has("rotation"))setRotation(a.number("rotation",0));
            if(a.has("onClick")){
             String click=a.text("onClick");
             if(click!=null){
              if(context.isRestricted())throw new IllegalStateException("View onClick in restricted context");
              Object current=context;java.util.HashSet<Object> seen=new java.util.HashSet<Object>();
              while(current instanceof android.content.ContextWrapper&&!(current instanceof android.app.Activity)){
               if(!seen.add(current)||seen.size()>32)throw new android.view.InflateException("Context wrapper cycle");
               android.content.Context next=((android.content.ContextWrapper)current).getBaseContext();if(next==null)break;current=next;
              }
              final Object owner=current;
              final java.lang.reflect.Method method;try{method=owner.getClass().getMethod(click,View.class);}catch(ReflectiveOperationException e){throw new android.view.InflateException("View click method "+click,e);}
              setOnClickListener(new OnClickListener(){public void onClick(View view){try{method.invoke(owner,view);}catch(java.lang.reflect.InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new RuntimeException(cause);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}});
             }
            }
            if(a.has("scaleX"))setScaleX(a.number("scaleX",1));if(a.has("scaleY"))setScaleY(a.number("scaleY",1));
            if(a.integer("requiresFadingEdge",0)!=0)throw new UnsupportedOperationException("Required fading edges are not implemented");
            if(a.integer("fadingEdge",0)!=0&&context.getApplicationInfo().targetSdkVersion<14)throw new UnsupportedOperationException("Legacy fading edges are not implemented");
            if(a.has("scrollbars"))bars().configure(a);
            if(a.has("stateListAnimator")){int animation=a.reference("stateListAnimator",0);setStateListAnimator(animation==0?null:android.animation.AnimatorInflater.loadStateListAnimator(context,animation));}
            if(a.has("transitionName"))setTransitionName(a.text("transitionName"));
            if(a.has("alpha"))setAlpha(a.number("alpha",1));
            setClipToOutline(a.bool("clipToOutline",false));
            if(a.has("translationX"))setTranslationX(a.dimensionFloat("translationX",0));if(a.has("translationY"))setTranslationY(a.dimensionFloat("translationY",0));
            duplicateParentState=a.bool("duplicateParentState",false);
            setSaveEnabled(a.bool("saveEnabled",true));
            setScrollContainer(a.bool("isScrollContainer",false));
            setImportantForAutofill(a.integer("importantForAutofill",IMPORTANT_FOR_AUTOFILL_AUTO));if(a.has("autofillHints")){String hints=a.text("autofillHints");setAutofillHints(hints==null?null:hints.split(","));}
            setOverScrollMode(a.integer("overScrollMode",OVER_SCROLL_ALWAYS));
            // Subclass containers are not initialized until this constructor returns.
            scrollX=a.dimension("scrollX",0,false);scrollY=a.dimension("scrollY",0,false);
            if(a.has("elevation"))setElevation(a.dimensionFloat("elevation",0));if(a.has("translationZ"))setTranslationZ(a.dimensionFloat("translationZ",0));
            if(a.has("outlineProvider")){int provider=a.integer("outlineProvider",0);switch(provider){case 0:setOutlineProvider(ViewOutlineProvider.BACKGROUND);break;case 1:setOutlineProvider(null);break;case 2:setOutlineProvider(ViewOutlineProvider.BOUNDS);break;case 3:setOutlineProvider(ViewOutlineProvider.PADDED_BOUNDS);break;default:throw new IllegalArgumentException("Unknown outlineProvider");}}
            if(a.has("id"))id=a.reference("id",NO_ID);if(a.has("background"))setBackground(a.drawable("background"));
            if(a.has("backgroundTint")||a.has("backgroundTintMode")){android.content.res.TypedArray tint=context.obtainStyledAttributes(attrs,new int[]{0x0101046b,0x0101046c},style,resource);try{if(a.has("backgroundTint"))setBackgroundTintList(tint.getColorStateList(0));if(a.has("backgroundTintMode")){android.graphics.PorterDuff.Mode mode;switch(tint.getInt(1,-1)){case -1:mode=null;break;case 3:mode=android.graphics.PorterDuff.Mode.SRC_OVER;break;case 5:mode=android.graphics.PorterDuff.Mode.SRC_IN;break;case 9:mode=android.graphics.PorterDuff.Mode.SRC_ATOP;break;case 14:mode=android.graphics.PorterDuff.Mode.MULTIPLY;break;case 15:mode=android.graphics.PorterDuff.Mode.SCREEN;break;case 16:mode=android.graphics.PorterDuff.Mode.ADD;break;default:throw new IllegalArgumentException("Background tint mode");}setBackgroundTintMode(mode);}}finally{tint.recycle();}}
            setAccessibilityLiveRegion(a.integer("accessibilityLiveRegion",ACCESSIBILITY_LIVE_REGION_NONE));setImportantForAccessibility(a.integer("importantForAccessibility",IMPORTANT_FOR_ACCESSIBILITY_AUTO));fitsSystemWindows=a.bool("fitsSystemWindows",false);foregroundInsidePadding=a.bool("foregroundInsidePadding",true);setForegroundGravity(a.integer("foregroundGravity",Gravity.FILL));if(a.has("foreground"))setForeground(a.drawable("foreground"));
            int padding=a.dimension("padding",-1,false);setPadding(a.dimension("paddingLeft",padding>=0?padding:paddingLeft,false),a.dimension("paddingTop",padding>=0?padding:paddingTop,false),a.dimension("paddingRight",padding>=0?padding:paddingRight,false),a.dimension("paddingBottom",padding>=0?padding:paddingBottom,false));
            if(a.has("paddingStart")&&a.raw("paddingStart").type!=0)paddingStart=a.dimension("paddingStart",0,false);if(a.has("paddingEnd")&&a.raw("paddingEnd").type!=0)paddingEnd=a.dimension("paddingEnd",0,false);
            setMinimumWidth(a.dimension("minWidth",0,false));setMinimumHeight(a.dimension("minHeight",0,false));
            visibility=shirazVisibilityFromXml(a.integer("visibility",0));enabled=a.bool("enabled",true);clickable=a.bool("clickable",false);setLongClickable(a.bool("longClickable",false));if(a.has("tooltipText"))setTooltipText(a.text("tooltipText"));
            if(a.has("focusable")){shiraz.runtime.BinaryXml.Value v=a.raw("focusable");focusability=v.type==18?(v.data!=0?FOCUSABLE:NOT_FOCUSABLE):a.integer("focusable",0);if(focusability!=0&&focusability!=1&&focusability!=16)throw new IllegalArgumentException("focusable");}
            setDefaultFocusHighlightEnabled(a.bool("defaultFocusHighlightEnabled",true));
            keyboardNavigationCluster=a.bool("keyboardNavigationCluster",false);focusedByDefault=a.bool("focusedByDefault",false);nextClusterForwardId=a.reference("nextClusterForward",NO_ID);
            keepScreenOn=a.bool("keepScreenOn",false);
            focusableInTouchMode=a.bool("focusableInTouchMode",false);if(focusableInTouchMode)focusability=FOCUSABLE;
            if(a.has("layoutDirection")){layoutDirection=a.integer("layoutDirection",LAYOUT_DIRECTION_INHERIT);if(layoutDirection<0||layoutDirection>3)throw new IllegalArgumentException("XML layout direction");}
            if(a.has("tag"))tag=a.text("tag");if(a.has("contentDescription"))description=a.text("contentDescription");if(backgroundDrawable!=null){backgroundDrawable.setVisible(visibility==VISIBLE,false);backgroundDrawable.setState(getDrawableState());}if(foregroundDrawable!=null){foregroundDrawable.setVisible(visibility==VISIBLE,false);foregroundDrawable.setState(getDrawableState());}
        }
    }
    public android.content.res.Resources getResources() { return context.getResources(); }
    private Integer paddingStart,paddingEnd;
    private int paddingLeft, paddingTop, paddingRight, paddingBottom, minimumWidth, minimumHeight;
    private int measuredWidth, measuredHeight;
    private boolean layoutRequested = true, measured;
    public static final int MEASURED_SIZE_MASK=0x00ffffff, MEASURED_STATE_MASK=0xff000000, MEASURED_HEIGHT_STATE_SHIFT=16, MEASURED_STATE_TOO_SMALL=0x01000000;
    public static class MeasureSpec {
        public static final int UNSPECIFIED=0, EXACTLY=0x40000000, AT_MOST=0x80000000;
        public static int makeMeasureSpec(int size,int mode) {
            if (size < 0 || size > 0x3fffffff || mode != UNSPECIFIED && mode != EXACTLY && mode != AT_MOST) throw new IllegalArgumentException("MeasureSpec");
            return size | mode;
        }
        public static int getMode(int spec) { return spec & 0xc0000000; }
        public static int getSize(int spec) { return spec & 0x3fffffff; }
    }
    public void setPadding(int left,int top,int right,int bottom) {
        if(paddingStart==null&&paddingEnd==null&&paddingLeft==left&&paddingTop==top&&paddingRight==right&&paddingBottom==bottom)return;
        paddingStart=paddingEnd=null;paddingLeft=left; paddingTop=top; paddingRight=right; paddingBottom=bottom; requestLayout(); invalidate();
    }
    public int getPaddingLeft() { Integer relative=getLayoutDirection()==LAYOUT_DIRECTION_RTL?paddingEnd:paddingStart;return (relative==null?paddingLeft:relative)+(scrollBars==null?0:scrollBars.leftInset()); }
    public void setPaddingRelative(int start,int top,int end,int bottom){if(paddingStart!=null&&paddingEnd!=null&&paddingStart==start&&paddingEnd==end&&paddingTop==top&&paddingBottom==bottom)return;paddingStart=start;paddingEnd=end;paddingTop=top;paddingBottom=bottom;requestLayout();invalidate();}
    public int getPaddingStart(){return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingRight():getPaddingLeft();}
    public int getPaddingEnd(){return getLayoutDirection()==LAYOUT_DIRECTION_RTL?getPaddingLeft():getPaddingRight();}
    public boolean isPaddingRelative(){return paddingStart!=null||paddingEnd!=null;}
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { Integer relative=getLayoutDirection()==LAYOUT_DIRECTION_RTL?paddingStart:paddingEnd;return (relative==null?paddingRight:relative)+(scrollBars==null?0:scrollBars.rightInset()); }
    public int getPaddingBottom() { return paddingBottom+(scrollBars==null?0:scrollBars.bottomInset());}
    public void setMinimumWidth(int value) { if(minimumWidth!=value){minimumWidth=value; requestLayout();} }
    public void setMinimumHeight(int value) { if(minimumHeight!=value){minimumHeight=value; requestLayout();} }
    public int getBaseline(){return -1;}
    public int getMinimumWidth() { return minimumWidth; }
    public int getMinimumHeight() { return minimumHeight; }
    protected int getSuggestedMinimumWidth() { return Math.max(minimumWidth,backgroundDrawable == null ? 0 : backgroundDrawable.getMinimumWidth()); }
    protected int getSuggestedMinimumHeight() { return Math.max(minimumHeight,backgroundDrawable == null ? 0 : backgroundDrawable.getMinimumHeight()); }
    public void requestLayout() { shiraz.runtime.ViewDiagnostics.layoutRequest(this);layoutRequested=true; invalidated=true; if (parent != null) parent.requestLayout(); }
    public boolean isLayoutRequested() { return layoutRequested; }
    private int previousWidthSpec,previousHeightSpec;
    public void forceLayout(){layoutRequested=true;invalidated=true;}
    public final void measure(int widthSpec,int heightSpec) {
        if(measured&&!layoutRequested&&widthSpec==previousWidthSpec&&heightSpec==previousHeightSpec)return;
        if(this instanceof ViewGroup){ViewGroup group=(ViewGroup)this;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);ViewGroup.LayoutParams params=child.getLayoutParams();if(params!=null)params.resolveLayoutDirection(child.getLayoutDirection());}}
        measured=false; onMeasure(widthSpec,heightSpec);
        if (!measured) throw new IllegalStateException("onMeasure did not set measured dimensions");
        previousWidthSpec=widthSpec;previousHeightSpec=heightSpec;
        layoutRequired=true;
    }
    protected void onMeasure(int widthSpec,int heightSpec) { setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(),widthSpec),getDefaultSize(getSuggestedMinimumHeight(),heightSpec)); }
    protected final void setMeasuredDimension(int width,int height) { measuredWidth=width; measuredHeight=height; measured=true; }
    public final int getMeasuredWidth() { return measuredWidth & MEASURED_SIZE_MASK; }
    public final int getMeasuredHeight() { return measuredHeight & MEASURED_SIZE_MASK; }
    public final int getMeasuredWidthAndState() { return measuredWidth; }
    public final int getMeasuredHeightAndState() { return measuredHeight; }
    public final int getMeasuredState() { return (measuredWidth & MEASURED_STATE_MASK) | ((measuredHeight >> MEASURED_HEIGHT_STATE_SHIFT) & (MEASURED_STATE_MASK >> MEASURED_HEIGHT_STATE_SHIFT)); }
    public static int combineMeasuredStates(int first,int second) { return first | second; }
    public static int resolveSize(int size,int spec) { return resolveSizeAndState(size,spec,0) & MEASURED_SIZE_MASK; }
    public static int resolveSizeAndState(int size,int spec,int state) {
        int mode=MeasureSpec.getMode(spec), limit=MeasureSpec.getSize(spec), result=size;
        if (mode == MeasureSpec.EXACTLY) result=limit;
        else if (mode == MeasureSpec.AT_MOST && size > limit) result=limit | MEASURED_STATE_TOO_SMALL;
        return result | (state & MEASURED_STATE_MASK);
    }
    public static int getDefaultSize(int size,int spec) { return MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED ? size : MeasureSpec.getSize(spec); }
    private Object tag;private CharSequence description;
    private android.util.SparseArray<Object> keyedTags;
    public void setTag(int key,Object value){
        if((key >>> 24)<2)throw new IllegalArgumentException("View tag key must be an application resource ID");
        if(value==null){if(keyedTags!=null){keyedTags.remove(key);if(keyedTags.size()==0)keyedTags=null;}return;}
        if(keyedTags==null)keyedTags=new android.util.SparseArray<Object>();
        if(keyedTags.size()>=4096&&keyedTags.indexOfKey(key)<0)throw new IllegalStateException("View keyed tag limit");
        keyedTags.put(key,value);
    }
    public Object getTag(int key){return keyedTags==null?null:keyedTags.get(key);}
    public void setTag(Object value){tag=value;}public Object getTag(){return tag;}
    public void setContentDescription(CharSequence value){description=value;}public CharSequence getContentDescription(){return description;}
    public Context getContext() { return context; }
    private boolean saveEnabled=true,saveFromParentEnabled=true,stateSuperCalled;
    public void setSaveEnabled(boolean enabled){saveEnabled=enabled;}
    public boolean isSaveEnabled(){return saveEnabled;}
    public void setSaveFromParentEnabled(boolean enabled){saveFromParentEnabled=enabled;}
    public boolean isSaveFromParentEnabled(){return saveFromParentEnabled;}
    public void saveHierarchyState(android.util.SparseArray<android.os.Parcelable> state){if(state==null)throw new NullPointerException("state");observerSubtree();dispatchSaveInstanceState(state);}
    public void restoreHierarchyState(android.util.SparseArray<android.os.Parcelable> state){if(state==null)throw new NullPointerException("state");observerSubtree();dispatchRestoreInstanceState(state);}
    protected void dispatchSaveInstanceState(android.util.SparseArray<android.os.Parcelable> state){
        if(id!=NO_ID&&saveEnabled){if(state.indexOfKey(id)>=0){state.put(id,null);return;}stateSuperCalled=false;android.os.Parcelable saved=onSaveInstanceState();if(!stateSuperCalled)throw new IllegalStateException("View.onSaveInstanceState must call super");if(saved!=null)state.put(id,saved);}
    }
    protected void dispatchRestoreInstanceState(android.util.SparseArray<android.os.Parcelable> state){
        if(id!=NO_ID){android.os.Parcelable saved=state.get(id);if(saved!=null){stateSuperCalled=false;onRestoreInstanceState(saved);if(!stateSuperCalled)throw new IllegalStateException("View.onRestoreInstanceState must call super");}}
    }
    protected android.os.Parcelable onSaveInstanceState(){stateSuperCalled=true;return BaseSavedState.EMPTY_STATE;}
    protected void onRestoreInstanceState(android.os.Parcelable state){stateSuperCalled=true;if(state!=null&&state!=BaseSavedState.EMPTY_STATE)throw new IllegalArgumentException("Wrong state class for View");}
    public static class BaseSavedState extends AbsSavedState {
        public BaseSavedState(android.os.Parcelable state){super(state);}
        public BaseSavedState(android.os.Parcel source){super(source);}
        public BaseSavedState(android.os.Parcel source,ClassLoader loader){super(source,loader);}
    }
    public void bringToFront(){if(parent!=null)parent.bringChildToFront(this);}
    public void setId(int id) { this.id = id; }
    public int getId() { return id; }
    @SuppressWarnings("unchecked") public <T extends View> T findViewWithTag(Object tag){return tag!=null&&tag.equals(this.tag)?(T)this:null;}
    @SuppressWarnings("unchecked") public <T extends View> T findViewById(int id) { return id != NO_ID && this.id == id ? (T) this : null; }
    private android.content.res.ColorStateList backgroundTint;
    private android.graphics.PorterDuff.Mode backgroundTintMode;
    private boolean hasBackgroundTint,hasBackgroundTintMode;
    public void setBackgroundTintList(android.content.res.ColorStateList tint){backgroundTint=tint;hasBackgroundTint=true;applyBackgroundTint();}
    public android.content.res.ColorStateList getBackgroundTintList(){return backgroundTint;}
    public void setBackgroundTintMode(android.graphics.PorterDuff.Mode mode){backgroundTintMode=mode;hasBackgroundTintMode=true;applyBackgroundTint();}
    public android.graphics.PorterDuff.Mode getBackgroundTintMode(){return backgroundTintMode;}
    private void applyBackgroundTint(){if(backgroundDrawable==null||!hasBackgroundTint&&!hasBackgroundTintMode)return;backgroundDrawable=backgroundDrawable.mutate();if(hasBackgroundTint)backgroundDrawable.setTintList(backgroundTint);if(hasBackgroundTintMode)backgroundDrawable.setTintMode(backgroundTintMode);if(backgroundDrawable.isStateful())backgroundDrawable.setState(getDrawableState());invalidate();}
    private int backgroundResource;
    public void setBackgroundResource(int resource) { if(resource!=0&&resource==backgroundResource)return;setBackground(resource==0?null:getResources().getDrawable(resource,getContext().getTheme()));backgroundResource=resource; }
    public void setBackgroundColor(int color) { setBackground(new android.graphics.drawable.ColorDrawable(color)); }
    public void setBackground(android.graphics.drawable.Drawable drawable) { setBackgroundDrawable(drawable); }
    public void setBackgroundDrawable(android.graphics.drawable.Drawable drawable) {
        backgroundResource=0;
        if (backgroundDrawable == drawable) return;
        if (backgroundDrawable != null && backgroundDrawable != foregroundDrawable) { backgroundDrawable.setCallback(null); unscheduleDrawable(backgroundDrawable); }
        backgroundDrawable = drawable;
        if (drawable != null) {
            android.graphics.Rect padding=new android.graphics.Rect();
            drawable.setLayoutDirection(getLayoutDirection());
            if(drawable.getPadding(padding)){
                boolean rtl=drawable.getLayoutDirection()==LAYOUT_DIRECTION_RTL;
                paddingLeft=rtl?padding.right:padding.left;paddingRight=rtl?padding.left:padding.right;
                paddingTop=padding.top;paddingBottom=padding.bottom;
            }
            drawable.setCallback(this); drawable.setBounds(0,0,getWidth(),getHeight());
            drawable.setVisible(visibility == VISIBLE, false); drawable.setState(getDrawableState());
            applyBackgroundTint();
        }
        requestLayout();
    }
    private android.graphics.drawable.Drawable foregroundDrawable;
    private int foregroundGravity=Gravity.FILL;private boolean foregroundInsidePadding=true;
    public android.graphics.drawable.Drawable getForeground(){return foregroundDrawable;}
    public boolean isForegroundInsidePadding(){return foregroundInsidePadding;}
    public int getForegroundGravity(){return foregroundGravity;}
    public void setForegroundGravity(int gravity){
        if((gravity&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)==0)gravity|=Gravity.LEFT;
        if((gravity&Gravity.VERTICAL_GRAVITY_MASK)==0)gravity|=Gravity.TOP;
        if(foregroundGravity!=gravity){foregroundGravity=gravity;requestLayout();invalidate();}
    }
    public void setForeground(android.graphics.drawable.Drawable drawable){
        if(drawable==foregroundDrawable)return;
        if(foregroundDrawable!=null&&foregroundDrawable!=backgroundDrawable){foregroundDrawable.setCallback(null);unscheduleDrawable(foregroundDrawable);}
        foregroundDrawable=drawable;
        if(drawable!=null){drawable.setCallback(this);drawable.setLayoutDirection(getLayoutDirection());drawable.setVisible(visibility==VISIBLE,false);drawable.setState(getDrawableState());}
        requestLayout();invalidate();
    }
    public static final int SCROLL_INDICATOR_TOP=1,SCROLL_INDICATOR_BOTTOM=2,SCROLL_INDICATOR_LEFT=4,SCROLL_INDICATOR_RIGHT=8,SCROLL_INDICATOR_START=16,SCROLL_INDICATOR_END=32;
    private int scrollIndicators;
    private android.graphics.drawable.Drawable scrollIndicatorDrawable;
    public void setScrollIndicators(int value){setScrollIndicators(value,63);}
    public void setScrollIndicators(int value,int mask){mask&=63;int next=(scrollIndicators&~mask)|(value&mask);if(next==scrollIndicators)return;if(next!=0&&scrollIndicatorDrawable==null){int id=getResources().getIdentifier("scroll_indicator_material","drawable","android");if(id!=0)scrollIndicatorDrawable=context.getDrawable(id);else {android.util.TypedValue color=new android.util.TypedValue();int attr=getResources().getIdentifier("colorForeground","attr","android");int argb=0xff000000;if(attr!=0&&context.getTheme().resolveAttribute(attr,color,true)){if(color.type>=28&&color.type<=31)argb=color.data;else if(color.resourceId!=0)argb=context.getColor(color.resourceId);}scrollIndicatorDrawable=new android.graphics.drawable.ColorDrawable((argb&0xffffff)|((((argb>>>24)*31+127)/255)<<24));}}scrollIndicators=next;invalidate();}
    public int getScrollIndicators(){return scrollIndicators;}
    private void drawScrollIndicators(Canvas canvas){
        if(scrollIndicators==0||scrollIndicatorDrawable==null)return;
        android.graphics.drawable.Drawable d=scrollIndicatorDrawable;d.setState(getDrawableState());int w=Math.max(Math.max(1,Math.round(getResources().getDisplayMetrics().density)),d.getIntrinsicWidth()),h=Math.max(Math.max(1,Math.round(getResources().getDisplayMetrics().density)),d.getIntrinsicHeight()),width=getWidth(),height=getHeight();
        if((scrollIndicators&1)!=0&&canScrollVertically(-1)){d.setBounds(0,0,width,Math.min(h,height));d.draw(canvas);}
        if((scrollIndicators&2)!=0&&canScrollVertically(1)){d.setBounds(0,Math.max(0,height-h),width,height);d.draw(canvas);}
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        if((scrollIndicators&(4|(rtl?32:16)))!=0&&canScrollHorizontally(-1)){d.setBounds(0,0,Math.min(w,width),height);d.draw(canvas);}
        if((scrollIndicators&(8|(rtl?16:32)))!=0&&canScrollHorizontally(1)){d.setBounds(Math.max(0,width-w),0,width,height);d.draw(canvas);}
    }
    public void onDrawForeground(Canvas canvas){
        drawScrollIndicators(canvas);
        if(foregroundDrawable==null)return;
        android.graphics.Rect container=new android.graphics.Rect(0,0,getWidth(),getHeight()),bounds=new android.graphics.Rect();
        if(!foregroundInsidePadding){long right=(long)getWidth()-getPaddingRight(),bottom=(long)getHeight()-getPaddingBottom();if(right<Integer.MIN_VALUE||right>Integer.MAX_VALUE||bottom<Integer.MIN_VALUE||bottom>Integer.MAX_VALUE)throw new IllegalArgumentException("Foreground bounds overflow");container.set(getPaddingLeft(),getPaddingTop(),(int)right,(int)bottom);}
        Gravity.apply(foregroundGravity,foregroundDrawable.getIntrinsicWidth(),foregroundDrawable.getIntrinsicHeight(),container,bounds,getLayoutDirection());
        foregroundDrawable.setLayoutDirection(getLayoutDirection());foregroundDrawable.setBounds(bounds);foregroundDrawable.draw(canvas);
    }
    public void jumpDrawablesToCurrentState(){if(stateListAnimator!=null)stateListAnimator.jumpToCurrentState();if(backgroundDrawable!=null)backgroundDrawable.jumpToCurrentState();if(foregroundDrawable!=null)foregroundDrawable.jumpToCurrentState();}
    public android.graphics.drawable.Drawable getBackground() { return backgroundDrawable; }
    protected boolean verifyDrawable(android.graphics.drawable.Drawable who) { return who == backgroundDrawable || who == foregroundDrawable || who==defaultFocusHighlight || overlay!=null&&overlay.contains(who); }
    public void invalidateDrawable(android.graphics.drawable.Drawable who) { if (verifyDrawable(who)) {if(who==foregroundDrawable)requestLayout();android.graphics.Rect dirty=who.getDirtyBounds();invalidate(dirty.left+scrollX,dirty.top+scrollY,dirty.right+scrollX,dirty.bottom+scrollY);} }
    public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable what, long when) { if (verifyDrawable(who)) handler().postAtTime(what,who,when); }
    public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable what) { handler().removeCallbacks(what,who); }
    public void unscheduleDrawable(android.graphics.drawable.Drawable who) { if (handler != null) handler.removeCallbacksAndMessages(who); }
    protected static final int[] EMPTY_STATE_SET={},ENABLED_STATE_SET={16842910},FOCUSED_STATE_SET={16842908},SELECTED_STATE_SET={16842913},PRESSED_STATE_SET={16842919},WINDOW_FOCUSED_STATE_SET={16842909};
    private boolean duplicateParentState;
    public boolean isDuplicateParentStateEnabled(){return duplicateParentState;}
    public void setDuplicateParentStateEnabled(boolean enabled){if(duplicateParentState!=enabled){duplicateParentState=enabled;refreshDrawableState();invalidate();}}
    public int[] getDrawableState(){return onCreateDrawableState(0);}
    protected int[] onCreateDrawableState(int extraSpace) {
        if(extraSpace<0||extraSpace>1024)throw new IllegalArgumentException("Drawable state capacity");
        if(duplicateParentState&&parent instanceof View){if(parent instanceof ViewGroup&&((ViewGroup)parent).addStatesFromChildren())throw new IllegalStateException("Cyclic parent/child drawable states");int[] inherited=((View)parent).getDrawableState();return java.util.Arrays.copyOf(inherited,inherited.length+extraSpace);}
        int[] state = new int[(enabled ? 1 : 0) + (pressed ? 1 : 0) + (focused ? 1 : 0) + (selected ? 1 : 0)+(activated ? 1 : 0)+(hasWindowFocus()?1:0)+(hovered?1:0)+extraSpace]; int i = 0;
        if (hovered) state[i++] = 16843623;
        if (enabled) state[i++] = 16842910;
        if (pressed) state[i++] = 16842919;
        if (focused) state[i++] = 16842908;
        if (selected) state[i++] = 16842913;
        if (activated) state[i++] = 16843518;
        if (hasWindowFocus()) state[i++] = 16842909;
        return state;
    }
    protected static int[] mergeDrawableStates(int[] base,int[] additional){int at=0;while(at<base.length&&base[at]!=0)at++;if(additional.length>base.length-at)throw new IllegalArgumentException("Drawable state capacity");System.arraycopy(additional,0,base,at,additional.length);return base;}
    public void refreshDrawableState() { drawableStateChanged(); if(parent!=null)parent.childDrawableStateChanged(this); }
    protected void drawableStateChanged() { if(scrollBars!=null)invalidate();if(stateListAnimator!=null&&isAttachedToWindow())stateListAnimator.setState(getDrawableState()); if (backgroundDrawable != null && backgroundDrawable.isStateful()) backgroundDrawable.setState(getDrawableState());if(foregroundDrawable!=null&&foregroundDrawable.isStateful())foregroundDrawable.setState(getDrawableState()); }
    static int shirazVisibilityFromXml(int value){if(value<0||value>2)throw new IllegalArgumentException("XML visibility");return value*4;}
    protected void onVisibilityChanged(View changedView,int visibility){}
    protected void dispatchVisibilityChanged(View changedView,int visibility){onVisibilityChanged(changedView,visibility);}
    public void setVisibility(int visibility) {
        if (visibility != VISIBLE && visibility != INVISIBLE && visibility != GONE) throw new IllegalArgumentException("visibility");
        boolean changed=this.visibility != visibility;
        if(changed&&parent!=null)parent.shirazVisibilityChange(this,this.visibility,visibility);
        this.visibility = visibility;
        if (backgroundDrawable != null) backgroundDrawable.setVisible(visibility == VISIBLE, false);
        if(foregroundDrawable!=null)foregroundDrawable.setVisible(visibility==VISIBLE,false);
        if (visibility != VISIBLE) { tracking = false; clearFocus(); }
        if (changed) {if(windowObserver!=null)windowObserver.systemUiDirty=true;requestLayout();dispatchVisibilityChanged(this,visibility);}
        invalidate();
    }
    /** Transition-only visibility changes preserve the layout captured for the animation. */
    public void setTransitionVisibility(int value){
        if(value!=VISIBLE&&value!=INVISIBLE&&value!=GONE)throw new IllegalArgumentException("Visibility");
        if(visibility!=value){visibility=value;invalidate();}
    }
    public int getVisibility() { return visibility; }
    public int getWidth() { return right - left; }
    public int getHeight() { return bottom - top; }
    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    private static int offsetCoordinate(int coordinate,int delta){long result=(long)coordinate+delta;if(result<Integer.MIN_VALUE||result>Integer.MAX_VALUE)throw new IllegalArgumentException("View offset exceeds coordinate range");return (int)result;}
    public void offsetLeftAndRight(int offset){if(offset==0)return;int l=offsetCoordinate(left,offset),r=offsetCoordinate(right,offset);left=l;right=r;invalidate();}
    public void offsetTopAndBottom(int offset){if(offset==0)return;int t=offsetCoordinate(top,offset),b=offsetCoordinate(bottom,offset);top=t;bottom=b;invalidate();}
    public final void shirazSetAnimatedBounds(int l,int t,int r,int b){if((long)r-l<0||(long)b-t<0||(long)r-l>Integer.MAX_VALUE||(long)b-t>Integer.MAX_VALUE)throw new IllegalArgumentException("Animated bounds");int w=getWidth(),h=getHeight();left=l;top=t;right=r;bottom=b;if(backgroundDrawable!=null)backgroundDrawable.setBounds(0,0,getWidth(),getHeight());if(w!=getWidth()||h!=getHeight())onSizeChanged(getWidth(),getHeight(),w,h);invalidate();}
    protected boolean setFrame(int left,int top,int right,int bottom){
        if((long)right-left<0||(long)bottom-top<0||(long)right-left>Integer.MAX_VALUE||(long)bottom-top>Integer.MAX_VALUE)throw new IllegalArgumentException("View layout dimensions");
        boolean changed=this.left!=left||this.top!=top||this.right!=right||this.bottom!=bottom;
        int oldWidth=getWidth(),oldHeight=getHeight();
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        if(backgroundDrawable!=null)backgroundDrawable.setBounds(0,0,getWidth(),getHeight());
        invalidate();
        if(getWidth()!=oldWidth||getHeight()!=oldHeight)onSizeChanged(getWidth(),getHeight(),oldWidth,oldHeight);
        return changed;
    }
    /** Updates animation geometry without invoking the child's layout algorithm. */
    public void setLeftTopRightBottom(int left,int top,int right,int bottom){setFrame(left,top,right,bottom);}
    public void layout(int left, int top, int right, int bottom) {
        // Validate before clearing pending layout state, including failed calls.
        if((long)right-left<0||(long)bottom-top<0||(long)right-left>Integer.MAX_VALUE||(long)bottom-top>Integer.MAX_VALUE)throw new IllegalArgumentException("View layout dimensions");
        int oldLeft=this.left,oldTop=this.top,oldRight=this.right,oldBottom=this.bottom;
        boolean required=layoutRequired;
        layoutRequested=false;layoutRequired=false;
        boolean changed=setFrame(left,top,right,bottom);
        if(changed||required){
            onLayout(changed,left,top,right,bottom);
            if(layoutListeners!=null){
                java.util.ArrayList<OnLayoutChangeListener> snapshot=new java.util.ArrayList<OnLayoutChangeListener>(layoutListeners);
                for(OnLayoutChangeListener listener:snapshot)listener.onLayoutChange(this,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom);
            }
        }
        laidOut=true;
    }
    protected void onFinishInflate() { }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) { }
    protected void dispatchDraw(Canvas canvas) { }
    protected void onSizeChanged(int w, int h, int oldw, int oldh) { }
    // Drawing cache is a hint; this renderer always draws directly without a View bitmap cache.
    private boolean willNotCacheDrawing;
    public void setWillNotCacheDrawing(boolean value){willNotCacheDrawing=value;}
    public boolean willNotCacheDrawing(){return willNotCacheDrawing;}
    private boolean willNotDraw;
    public void setWillNotDraw(boolean value){if(willNotDraw!=value){willNotDraw=value;invalidate();}}
    public boolean willNotDraw(){return willNotDraw;}
    public static View inflate(Context context,int resource,ViewGroup root){return LayoutInflater.from(context).inflate(resource,root);}
    protected void shirazDrawSurface(Canvas canvas) { }
    protected void onDraw(Canvas canvas) { }
    private float transitionAlpha=1;
    public float getTransitionAlpha(){return transitionAlpha;}
    public void setTransitionAlpha(float value){if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Transition alpha must be in [0,1]");if(transitionAlpha!=value){transitionAlpha=value;invalidate();}}
    private float alpha=1;
    public float getAlpha(){return alpha;}
    public void setAlpha(float value){if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("View alpha must be in [0,1]");if(alpha!=value){alpha=value;invalidate();}}
    public static final int OVER_SCROLL_ALWAYS=0,OVER_SCROLL_IF_CONTENT_SCROLLS=1,OVER_SCROLL_NEVER=2;
    private int scrollX,scrollY,overScrollMode=OVER_SCROLL_ALWAYS;
    public interface OnScrollChangeListener{void onScrollChange(View view,int x,int y,int oldX,int oldY);}
    public static final int SCROLLBARS_INSIDE_OVERLAY=0,SCROLLBARS_INSIDE_INSET=0x01000000,SCROLLBARS_OUTSIDE_OVERLAY=0x02000000,SCROLLBARS_OUTSIDE_INSET=0x03000000;
    public static final int SCROLLBAR_POSITION_DEFAULT=0,SCROLLBAR_POSITION_LEFT=1,SCROLLBAR_POSITION_RIGHT=2;
    private ScrollBars scrollBars;
    private ScrollBars bars(){if(scrollBars==null)scrollBars=new ScrollBars(this);return scrollBars;}
    public boolean isVerticalScrollBarEnabled(){return scrollBars!=null&&scrollBars.vertical;}public boolean isHorizontalScrollBarEnabled(){return scrollBars!=null&&scrollBars.horizontal;}
    public void setVerticalScrollBarEnabled(boolean enabled){bars().vertical=enabled;requestLayout();bars().wake(bars().delay);}public void setHorizontalScrollBarEnabled(boolean enabled){bars().horizontal=enabled;requestLayout();bars().wake(bars().delay);}
    public void setScrollBarStyle(int style){if(style!=0&&style!=0x01000000&&style!=0x02000000&&style!=0x03000000)throw new IllegalArgumentException("Scrollbar style");bars().style=style;requestLayout();invalidate();}public int getScrollBarStyle(){return scrollBars==null?0:scrollBars.style;}
    public void setScrollBarSize(int size){if(size<0||size>8192)throw new IllegalArgumentException("Scrollbar size");bars().size=size;requestLayout();invalidate();}public int getScrollBarSize(){return bars().size;}public int getVerticalScrollbarWidth(){return isVerticalScrollBarEnabled()?bars().size:0;}protected int getHorizontalScrollbarHeight(){return isHorizontalScrollBarEnabled()?bars().size:0;}
    public void setVerticalScrollbarPosition(int position){if(position<0||position>2)throw new IllegalArgumentException("Scrollbar position");bars().position=position;requestLayout();invalidate();}public int getVerticalScrollbarPosition(){return scrollBars==null?0:scrollBars.position;}
    public void setScrollbarFadingEnabled(boolean enabled){bars().fading=enabled;bars().wake(bars().delay);}public boolean isScrollbarFadingEnabled(){return scrollBars==null||scrollBars.fading;}
    public void setScrollBarDefaultDelayBeforeFade(int value){if(value<0||value>60000)throw new IllegalArgumentException("Scrollbar fade delay");bars().delay=value;}public int getScrollBarDefaultDelayBeforeFade(){return bars().delay;}
    public void setScrollBarFadeDuration(int value){if(value<0||value>60000)throw new IllegalArgumentException("Scrollbar fade duration");bars().duration=value;}public int getScrollBarFadeDuration(){return bars().duration;}
    protected boolean awakenScrollBars(){return awakenScrollBars(bars().delay,true);}protected boolean awakenScrollBars(int delay){return awakenScrollBars(delay,true);}protected boolean awakenScrollBars(int delay,boolean invalidate){if(!isVerticalScrollBarEnabled()&&!isHorizontalScrollBarEnabled())return false;bars().wake(Math.max(0,delay));return true;}
    private OnScrollChangeListener scrollListener;
    public void setOnScrollChangeListener(OnScrollChangeListener listener){scrollListener=listener;}
    public int getScrollX(){return scrollX;}public int getScrollY(){return scrollY;}
    public void setScrollX(int value){scrollTo(value,scrollY);}public void setScrollY(int value){scrollTo(scrollX,value);}
    public void scrollTo(int x,int y){if(x==scrollX&&y==scrollY)return;int oldX=scrollX,oldY=scrollY;scrollX=x;scrollY=y;invalidate();onScrollChanged(x,y,oldX,oldY);}
    public void scrollBy(int x,int y){scrollTo(Math.addExact(scrollX,x),Math.addExact(scrollY,y));}
    protected void onScrollChanged(int x,int y,int oldX,int oldY){if(scrollBars!=null)awakenScrollBars();getViewTreeObserver().shirazScrollPending=true;if(windowObserver!=null&&windowObserver.choreographer!=null)postInvalidateOnAnimation();if(scrollListener!=null)scrollListener.onScrollChange(this,x,y,oldX,oldY);}
    public void computeScroll(){}
    protected int computeHorizontalScrollRange(){return getWidth();}protected int computeHorizontalScrollExtent(){return getWidth();}protected int computeHorizontalScrollOffset(){return scrollX;}
    protected int computeVerticalScrollRange(){return getHeight();}protected int computeVerticalScrollExtent(){return getHeight();}protected int computeVerticalScrollOffset(){return scrollY;}
    public boolean canScrollHorizontally(int direction){long range=(long)computeHorizontalScrollRange()-computeHorizontalScrollExtent();int offset=computeHorizontalScrollOffset();return range>0&&(direction<0?offset>0:offset<range-1);}
    public boolean canScrollVertically(int direction){long range=(long)computeVerticalScrollRange()-computeVerticalScrollExtent();int offset=computeVerticalScrollOffset();return range>0&&(direction<0?offset>0:offset<range-1);}
    public int getOverScrollMode(){return overScrollMode;}
    public void setOverScrollMode(int value){if(value<0||value>2)throw new IllegalArgumentException("Overscroll mode");overScrollMode=value;}
    protected boolean overScrollBy(int dx,int dy,int x,int y,int rangeX,int rangeY,int maxX,int maxY,boolean touch){
        if(rangeX<0||rangeY<0||maxX<0||maxY<0||(long)rangeX+maxX>Integer.MAX_VALUE||(long)rangeY+maxY>Integer.MAX_VALUE)throw new IllegalArgumentException("Overscroll range");
        if(overScrollMode==OVER_SCROLL_NEVER||overScrollMode==OVER_SCROLL_IF_CONTENT_SCROLLS&&computeHorizontalScrollRange()<=computeHorizontalScrollExtent())maxX=0;
        if(overScrollMode==OVER_SCROLL_NEVER||overScrollMode==OVER_SCROLL_IF_CONTENT_SCROLLS&&computeVerticalScrollRange()<=computeVerticalScrollExtent())maxY=0;
        long nx=(long)x+dx,ny=(long)y+dy;int cx=(int)Math.max(-maxX,Math.min((long)rangeX+maxX,nx)),cy=(int)Math.max(-maxY,Math.min((long)rangeY+maxY,ny));
        boolean clampX=nx!=cx,clampY=ny!=cy;onOverScrolled(cx,cy,clampX,clampY);return clampX||clampY;
    }
    protected void onOverScrolled(int x,int y,boolean clampedX,boolean clampedY){}
    private static final java.util.concurrent.atomic.AtomicLong nextAutofillId=new java.util.concurrent.atomic.AtomicLong(1);
    private android.view.autofill.AutofillId autofillId;
    public final synchronized android.view.autofill.AutofillId getAutofillId(){
        if(autofillId==null){long id=nextAutofillId.getAndIncrement();if(id>Integer.MAX_VALUE)throw new IllegalStateException("Autofill view ID space exhausted");autofillId=new android.view.autofill.AutofillId((int)id);}return autofillId;
    }
    public static final int IMPORTANT_FOR_AUTOFILL_AUTO=0,IMPORTANT_FOR_AUTOFILL_YES=1,IMPORTANT_FOR_AUTOFILL_NO=2,IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS=4,IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS=8;
    private int autofillImportance;
    private String[] autofillHints;
    public int getImportantForAutofill(){return autofillImportance;}
    public void setImportantForAutofill(int mode){if(mode!=0&&mode!=1&&mode!=2&&mode!=4&&mode!=8)throw new IllegalArgumentException("Autofill importance");autofillImportance=mode;}
    public final boolean isImportantForAutofill(){
        for(View ancestor=parent;ancestor!=null;ancestor=ancestor.parent){int mode=ancestor.getImportantForAutofill();if(mode==4||mode==8)return false;}
        int mode=getImportantForAutofill();if(mode==1||mode==4)return true;if(mode==2||mode==8)return false;
        int resource=getId();if(resource==NO_ID||(resource&0xff000000)==0)return false;
        try{return getResources().getResourceEntryName(resource)!=null&&getContext().getPackageName().equals(getResources().getResourcePackageName(resource));}catch(android.content.res.Resources.NotFoundException missing){return false;}
    }
    public void setAutofillHints(String... hints){if(hints==null||hints.length==0){autofillHints=null;return;}if(hints.length>256)throw new IllegalArgumentException("Autofill hint count");for(String hint:hints)if(hint!=null&&hint.length()>4096)throw new IllegalArgumentException("Autofill hint length");autofillHints=hints.clone();}
    public String[] getAutofillHints(){return autofillHints==null?null:autofillHints.clone();}
    private static final java.util.concurrent.atomic.AtomicInteger nextGeneratedId=new java.util.concurrent.atomic.AtomicInteger(1);
    public static int generateViewId(){for(;;){int value=nextGeneratedId.get();int next=value==0x00ffffff?1:value+1;if(nextGeneratedId.compareAndSet(value,next))return value;}}
    private boolean scrollContainer;
    public void setScrollContainer(boolean value){scrollContainer=value;}
    public boolean isScrollContainer(){return scrollContainer&&isAttachedToWindow();}
    public static final int SCROLL_AXIS_NONE=0,SCROLL_AXIS_HORIZONTAL=1,SCROLL_AXIS_VERTICAL=2;
    private float translationX,translationY,rotation,rotationY,scaleX=1,scaleY=1,pivotX,pivotY;
    private float cameraDistance=8;
    public float getCameraDistance(){return cameraDistance*getResources().getDisplayMetrics().densityDpi;}
    /** Camera depth has no effect on the supported planar transforms. Perspective rotations still fail explicitly. */
    public void setCameraDistance(float distance){
        if(!Float.isFinite(distance))throw new IllegalArgumentException("View camera distance");
        int density=getResources().getDisplayMetrics().densityDpi;
        if(density<=0)throw new IllegalStateException("Invalid display density");
        float value=Math.abs(distance)/density;
        if(cameraDistance!=value){cameraDistance=value;invalidate();}
    }
    private boolean pivotXSet,pivotYSet;
    public float getScaleX(){return scaleX;}public float getScaleY(){return scaleY;}
    public void setScaleX(float value){if(!Float.isFinite(value)||Math.abs(value)>8192)throw new IllegalArgumentException("View scale");if(scaleX!=value){scaleX=value;invalidate();}}
    public void setScaleY(float value){if(!Float.isFinite(value)||Math.abs(value)>8192)throw new IllegalArgumentException("View scale");if(scaleY!=value){scaleY=value;invalidate();}}
    public float getPivotX(){return pivotXSet?pivotX:getWidth()/2f;}
    public float getPivotY(){return pivotYSet?pivotY:getHeight()/2f;}
    public void setPivotX(float value){if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("View pivot");pivotXSet=true;if(pivotX!=value){pivotX=value;invalidate();}}
    public void setPivotY(float value){if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("View pivot");pivotYSet=true;if(pivotY!=value){pivotY=value;invalidate();}}
    public boolean isPivotXSet(){return pivotXSet;}public boolean isPivotYSet(){return pivotYSet;}
    private double rotationSin,rotationCos=1;
    final float shirazLocalX(float x,float y){return (float)(((x-getPivotX())*rotationCos+(y-getPivotY())*rotationSin)/(shirazMirroredY()?-scaleX:scaleX)+getPivotX());}
    final float shirazLocalY(float x,float y){return (float)((-(x-getPivotX())*rotationSin+(y-getPivotY())*rotationCos)/scaleY+getPivotY());}
    final float shirazLocalOrientation(float orientation){double x=Math.sin(orientation),y=-Math.cos(orientation);double localX=(x*rotationCos+y*rotationSin)/(shirazMirroredY()?-scaleX:scaleX),localY=(-x*rotationSin+y*rotationCos)/scaleY;double angle=Math.atan2(localX,-localY);if(angle>Math.PI/2)angle-=Math.PI;if(angle<-Math.PI/2)angle+=Math.PI;return (float)angle;}
    final boolean shirazCollapsed(){return scaleX==0||scaleY==0;}
    public float getRotation(){return rotation;}
    public float getRotationX(){return 0;}
    public void setRotation(float value){if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("View rotation");if(rotation!=value){rotation=value;double angle=Math.toRadians(value%360);rotationSin=Math.sin(angle);rotationCos=Math.cos(angle);if(Math.abs(rotationSin)<1e-7)rotationSin=0;if(Math.abs(rotationCos)<1e-7)rotationCos=0;invalidate();}}
    public void setRotationX(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("View rotationX");if(value!=0)throw new UnsupportedOperationException("Perspective View rotationX is not implemented");}
    public float getRotationY(){return rotationY;}
    /** Exact planar half-turns; perspective rotations require a 3D renderer. */
    public void setRotationY(float value){if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("View rotation bound");if(value%180!=0)throw new UnsupportedOperationException("Perspective View rotationY is not implemented");if(rotationY!=value){rotationY=value;invalidate();}}
    final boolean shirazMirroredY(){return rotationY%360!=0;}
    private android.graphics.Matrix transformMatrix;
    /** Local property transform; layout position and ancestor scroll are applied separately. */
    public android.graphics.Matrix getMatrix(){
        if(transformMatrix==null)transformMatrix=new android.graphics.Matrix();
        transformMatrix.setTranslate(translationX,translationY);transformMatrix.preRotate(rotation,getPivotX(),getPivotY());transformMatrix.preScale(shirazMirroredY()?-scaleX:scaleX,scaleY,getPivotX(),getPivotY());return transformMatrix;
    }
    public static final android.util.Property<View,Float> PIVOT_X=new android.util.FloatProperty<View>("pivotX"){public Float get(View view){return view.getPivotX();}public void setValue(View view,float value){view.setPivotX(value);}};
    public static final android.util.Property<View,Float> PIVOT_Y=new android.util.FloatProperty<View>("pivotY"){public Float get(View view){return view.getPivotY();}public void setValue(View view,float value){view.setPivotY(value);}};
    private static float translation(float value){if(!Float.isFinite(value)||Math.abs(value)>10000000)throw new IllegalArgumentException("View translation bound");return value;}
    public float getTranslationX(){return translationX;}public float getTranslationY(){return translationY;}
    public void setTranslationX(float value){translation(value);if(translationX!=value){translationX=value;invalidate();}}
    public void setTranslationY(float value){translation(value);if(translationY!=value){translationY=value;invalidate();}}
    public float getX(){return getLeft()+translationX;}public float getY(){return getTop()+translationY;}
    public void setX(float value){setTranslationX(value-getLeft());}public void setY(float value){setTranslationY(value-getTop());}
    private float elevation,translationZ;
    private static float validZ(float value){if(!Float.isFinite(value)||Math.abs(value)>8192)throw new IllegalArgumentException("View Z must be finite and within 8192 pixels");return value;}
    public float getElevation(){return elevation;}
    public void setElevation(float value){validZ(value);validZ(value+translationZ);if(elevation!=value){elevation=value;invalidate();}}
    public static final android.util.Property<View,Float> SCALE_X=new android.util.FloatProperty<View>("scaleX"){public Float get(View view){return view.getScaleX();}public void setValue(View view,float value){view.setScaleX(value);}};
    public static final android.util.Property<View,Float> SCALE_Y=new android.util.FloatProperty<View>("scaleY"){public Float get(View view){return view.getScaleY();}public void setValue(View view,float value){view.setScaleY(value);}};
    public static final android.util.Property<View,Float> ALPHA=new android.util.FloatProperty<View>("alpha"){public Float get(View view){return view.getAlpha();}public void setValue(View view,float value){view.setAlpha(value);}};
    public static final android.util.Property<View,Float> TRANSLATION_X=new android.util.FloatProperty<View>("translationX"){public Float get(View view){return view.getTranslationX();}public void setValue(View view,float value){view.setTranslationX(value);}};
    public static final android.util.Property<View,Float> TRANSLATION_Y=new android.util.FloatProperty<View>("translationY"){public Float get(View view){return view.getTranslationY();}public void setValue(View view,float value){view.setTranslationY(value);}};
    public static final android.util.Property<View,Float> TRANSLATION_Z=new android.util.FloatProperty<View>("translationZ"){public Float get(View view){return view.getTranslationZ();}public void setValue(View view,float value){view.setTranslationZ(value);}};
    public static final android.util.Property<View,Float> X=new android.util.FloatProperty<View>("x"){public Float get(View view){return view.getX();}public void setValue(View view,float value){view.setX(value);}};
    public static final android.util.Property<View,Float> Y=new android.util.FloatProperty<View>("y"){public Float get(View view){return view.getY();}public void setValue(View view,float value){view.setY(value);}};
    public static final android.util.Property<View,Float> Z=new android.util.FloatProperty<View>("z"){public Float get(View view){return view.getZ();}public void setValue(View view,float value){view.setZ(value);}};
    public float getTranslationZ(){return translationZ;}
    public void setTranslationZ(float value){validZ(value);validZ(elevation+value);if(translationZ!=value){translationZ=value;invalidate();}}
    public float getZ(){return elevation+translationZ;}
    public void setZ(float value){validZ(value);setTranslationZ(value-elevation);}
    private android.graphics.Outline resolveOutline(){
        android.graphics.Outline outline=new android.graphics.Outline();outline.setAlpha(1);
        if(outlineProvider!=null){if(backgroundDrawable!=null)backgroundDrawable.setBounds(0,0,getWidth(),getHeight());outlineProvider.getOutline(this,outline);}
        return outline;
    }
    public final void shirazDrawShadow(Canvas canvas){if(getZ()>0&&alpha*transitionAlpha>0&&outlineProvider!=null)canvas.shirazDrawOutlineShadow(resolveOutline(),getZ(),alpha*transitionAlpha);}
    private ViewOutlineProvider outlineProvider=ViewOutlineProvider.BACKGROUND;
    private boolean clipToOutline;
    public void setOutlineProvider(ViewOutlineProvider provider){outlineProvider=provider;invalidateOutline();}
    public ViewOutlineProvider getOutlineProvider(){return outlineProvider;}
    public void invalidateOutline(){invalidate();}
    public void setClipToOutline(boolean value){if(clipToOutline!=value){clipToOutline=value;invalidate();}}
    public boolean getClipToOutline(){return clipToOutline;}
    public static final int LAYER_TYPE_NONE=0,LAYER_TYPE_SOFTWARE=1,LAYER_TYPE_HARDWARE=2;
    private int layerType;
    private android.graphics.Paint layerPaint;
    public void setLayerType(int type,android.graphics.Paint paint){
        if(type<LAYER_TYPE_NONE||type>LAYER_TYPE_HARDWARE)throw new IllegalArgumentException("Layer type");
        layerType=type;layerPaint=type==LAYER_TYPE_NONE?null:paint;invalidate();
    }
    public int getLayerType(){return layerType;}
    public void setLayerPaint(android.graphics.Paint paint){if(layerType!=LAYER_TYPE_NONE){layerPaint=paint;invalidate();}}
    private Object revealOwner;
    private float revealX,revealY,revealRadius;
    /** Internal software RenderNode reveal state; owner prevents an older animator clearing a replacement. */
    public final void shirazSetRevealClip(Object owner,float x,float y,float radius){
        revealOwner=owner;revealX=x;revealY=y;revealRadius=radius;invalidate();
    }
    public final void shirazClearRevealClip(Object owner){if(revealOwner==owner){revealOwner=null;invalidate();}}
    private android.graphics.Rect clipBounds;
    public void setClipBounds(android.graphics.Rect bounds){
        if(bounds==null?clipBounds==null:bounds.equals(clipBounds))return;
        clipBounds=bounds==null?null:new android.graphics.Rect(bounds);invalidate();
    }
    public android.graphics.Rect getClipBounds(){return clipBounds==null?null:new android.graphics.Rect(clipBounds);}
    public boolean getClipBounds(android.graphics.Rect out){if(clipBounds==null)return false;if(out==null)throw new NullPointerException("out");out.set(clipBounds);return true;}
    public long getDrawingTime(){return windowObserver==null?0:windowObserver.drawingTime;}
    public final void shirazDrawWithAlpha(Canvas canvas){
        if(parent==null&&windowObserver!=null)windowObserver.drawingTime=Choreographer.shirazAnimationTimeMillis();
        long drawStarted=shiraz.runtime.ViewDiagnostics.drawStart();
        computeScroll();
        int saved=canvas.getSaveCount();
        try{
            if(parent==null){canvas.save();shirazApplyLegacyAnimation(canvas,getDrawingTime());}
            float drawAlpha=alpha*transitionAlpha*animationAlpha;if(drawAlpha==0){invalidated=false;return;}
            if(clipBounds!=null){canvas.save();canvas.clipRect(clipBounds);}
            if(revealOwner!=null){
                canvas.save();android.graphics.Path circle=new android.graphics.Path();
                if(revealRadius>0)circle.addCircle(revealX,revealY,revealRadius,android.graphics.Path.Direction.CW);
                canvas.clipPath(circle);
            }
            if(revealOwner==null&&clipToOutline&&outlineProvider!=null){
                android.graphics.Outline outline=resolveOutline();
                if(!outline.isEmpty()&&outline.canClip())canvas.shirazSaveOutlineClip(outline);
            }
            // Hardware requests use the software layer fallback on this CPU Canvas.
            if(layerType!=LAYER_TYPE_NONE){
                android.graphics.Paint composite=layerPaint;
                if(drawAlpha!=1){composite=composite==null?new android.graphics.Paint():new android.graphics.Paint(composite);composite.setAlpha(Math.round(drawAlpha*255));}
                canvas.saveLayer(new android.graphics.RectF(0,0,getWidth(),getHeight()),composite);
            }else if(drawAlpha!=1)canvas.saveLayerAlpha((android.graphics.RectF)null,Math.round(drawAlpha*255));
            if(getClass().getName().equals("androidx.emoji2.emojipicker.EmojiView")){
                try{
                    Object raw=getClass().getMethod("getEmoji").invoke(this);
                    if(raw instanceof CharSequence&&((CharSequence)raw).length()>0){
                        if(backgroundDrawable!=null){backgroundDrawable.setBounds(0,0,getWidth(),getHeight());backgroundDrawable.draw(canvas);}
                        String emoji=raw.toString();
                        android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        p.setTextSize(Math.min(getWidth(),getHeight())*0.64f);
                        float adv=p.measureText(emoji);
                        android.graphics.Paint.FontMetrics fm=p.getFontMetrics();
                        float ex=(getWidth()-adv)/2f;
                        float ey=(getHeight()-(fm.descent-fm.ascent))/2f-fm.ascent;
                        canvas.drawText(emoji,ex,ey,p);
                        return;
                    }
                }catch(ReflectiveOperationException ignored){}
            }
            draw(canvas);
        }finally{try{canvas.restoreToCount(saved);}finally{shiraz.runtime.ViewDiagnostics.drawEnd(this,drawStarted);}}
    }
    private ViewOverlay overlay;
    public ViewOverlay getOverlay(){if(overlay==null)overlay=new ViewOverlay(this);return overlay;}
    public final int shirazOverlayDrawableCount(){return overlay==null?0:overlay.shirazDrawableCount();}
    private boolean drawingDisappearing;
    public final void shirazDrawDisappearing(Canvas canvas){boolean previous=drawingDisappearing;drawingDisappearing=true;try{shirazDrawWithAlpha(canvas);}finally{drawingDisappearing=previous;}}
    public void draw(Canvas canvas) {
        invalidated = false;
        if (visibility != VISIBLE&&!drawingDisappearing) return;
        if (backgroundDrawable != null) {backgroundDrawable.setBounds(0,0,getWidth(),getHeight());backgroundDrawable.draw(canvas);}
        shirazDrawSurface(canvas);
        int contentSave=canvas.save();try{canvas.translate(-(float)scrollX,-(float)scrollY);if(!willNotDraw)onDraw(canvas);dispatchDraw(canvas);}finally{canvas.restoreToCount(contentSave);}
        if(overlay!=null)overlay.draw(canvas);
        if(scrollBars!=null)scrollBars.draw(canvas);
        onDrawForeground(canvas);
        drawDefaultFocusHighlight(canvas);
    }
    final void shirazInvalidateSelf(){invalidated=true;}
    private void invalidateTree() { for(View view=this;view!=null;view=view.parent)view.invalidated=true; }
    public void invalidate() { invalidateTree(); }
    // The compositor redraws complete views; preserve overload dispatch without
    // re-entering a subclass no-argument invalidate (MaterialSwitch updates tints there).
    public void invalidate(int left,int top,int right,int bottom) { invalidateTree(); }
    public void invalidate(android.graphics.Rect dirty) { if(dirty==null)throw new NullPointerException("dirty");invalidate(dirty.left,dirty.top,dirty.right,dirty.bottom); }
    public void postInvalidate() { postInvalidateDelayed(0); }
    public void postInvalidate(int left,int top,int right,int bottom){postInvalidateDelayed(0,left,top,right,bottom);}
    private final Runnable delayedInvalidation=new Runnable(){public void run(){synchronized(View.this){if(windowObserver!=null)invalidate();}}};
    public synchronized void postInvalidateDelayed(long delay){ViewTreeObserver observer=windowObserver;if(observer!=null&&!shirazIsDetaching())observer.windowHandler().postDelayed(delayedInvalidation,Math.max(0,delay));}
    // Composition currently redraws the view tree; a dirty rectangle safely widens to the whole view.
    public void postInvalidateDelayed(long delay,int left,int top,int right,int bottom){postInvalidateDelayed(delay);}
    private static final class PendingAction {final Runnable action;final long delay;PendingAction(Runnable action,long delay){this.action=action;this.delay=delay;}}
    private java.util.ArrayList<PendingAction> pendingActions;
    private synchronized boolean enqueueAction(Runnable action,long delay,boolean animation){
        if(action==null)throw new NullPointerException("action");ViewTreeObserver observer=windowObserver;
        if(observer==null){if(pendingActions==null)pendingActions=new java.util.ArrayList<PendingAction>();if(pendingActions.size()>=4096)throw new IllegalStateException("View pending action limit");pendingActions.add(new PendingAction(action,Math.max(0,delay)));return true;}
        if(animation){if(observer.choreographer==null)throw new IllegalStateException("Attached view has no frame scheduler");observer.choreographer.postCallbackDelayed(Choreographer.CALLBACK_ANIMATION,action,null,delay);return true;}
        return handler().postDelayed(action,delay);
    }
    private synchronized void flushPendingActions(){if(pendingActions!=null){for(PendingAction item:pendingActions)handler().postDelayed(item.action,item.delay);pendingActions=null;}}
    public boolean post(Runnable runnable) { return enqueueAction(runnable,0,false); }
    public boolean postDelayed(Runnable runnable,long delay) { return enqueueAction(runnable,delay,false); }
    public void postOnAnimation(Runnable runnable){enqueueAction(runnable,0,true);}
    public void postOnAnimationDelayed(Runnable runnable,long delay){enqueueAction(runnable,delay,true);}
    public synchronized boolean removeCallbacks(Runnable runnable){
        if(runnable==null)return true;if(handler!=null)handler.removeCallbacks(runnable);ViewTreeObserver observer=windowObserver;if(observer!=null)observer.windowHandler().removeCallbacks(runnable);if(observer!=null&&observer.choreographer!=null)observer.choreographer.removeCallbacks(Choreographer.CALLBACK_ANIMATION,runnable,null);
        if(pendingActions!=null)for(int i=pendingActions.size()-1;i>=0;i--)if(pendingActions.get(i).action==runnable)pendingActions.remove(i);return true;
    }
    private boolean frameInvalidationPending;
    private final Runnable frameInvalidation=new Runnable(){public void run(){synchronized(View.this){frameInvalidationPending=false;if(windowObserver!=null)invalidate();}}};
    public synchronized void postInvalidateOnAnimation(){ViewTreeObserver observer=windowObserver;if(observer==null||frameInvalidationPending)return;if(observer.choreographer==null)throw new IllegalStateException("Attached view has no frame scheduler");observer.choreographer.postCallback(Choreographer.CALLBACK_ANIMATION,frameInvalidation,null);frameInvalidationPending=true;}
    public void postInvalidateOnAnimation(int left,int top,int right,int bottom){postInvalidateOnAnimation();}
    private synchronized void cancelFrameInvalidation(){ViewTreeObserver observer=windowObserver;if(observer!=null)observer.windowHandler().removeCallbacks(delayedInvalidation);if(observer!=null&&observer.choreographer!=null)observer.choreographer.removeCallbacks(Choreographer.CALLBACK_ANIMATION,frameInvalidation,null);frameInvalidationPending=false;}

    public final boolean shirazIsInvalidated() { return invalidated; }
    /** Hidden root used by WindowInsetsCompat. Null until the view is attached. */
    public ViewRootImpl getViewRootImpl() { return windowObserver == null ? null : viewRoot; }
    private final ViewRootImpl viewRoot = new ViewRootImpl();
    private final AttachInfo mAttachInfo = new AttachInfo();
    static final class AttachInfo { final android.graphics.Rect mVisibleInsets = new android.graphics.Rect(); }
    public void setLeft(int value){setFrame(value,top,right,bottom);}
    public void setTop(int value){setFrame(left,value,right,bottom);}
    public void setRight(int value){setFrame(left,top,value,bottom);}
    public void setBottom(int value){setFrame(left,top,right,value);}
    public void getHitRect(android.graphics.Rect outRect){outRect.set(left,top,right,bottom);}
    public void getDrawingRect(android.graphics.Rect outRect){outRect.set(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());}
    public boolean getLocalVisibleRect(android.graphics.Rect r){r.set(0,0,getWidth(),getHeight());return getWidth()>0&&getHeight()>0&&isShown();}
    public boolean getGlobalVisibleRect(android.graphics.Rect r){return getGlobalVisibleRect(r,null);}
    public boolean getGlobalVisibleRect(android.graphics.Rect r,android.graphics.Point globalOffset){
        int[] at=new int[2];getLocationInWindow(at);r.set(at[0],at[1],at[0]+getWidth(),at[1]+getHeight());
        if(globalOffset!=null)globalOffset.set(at[0],at[1]);
        return getWidth()>0&&getHeight()>0&&isShown();
    }
    public boolean callOnClick(){return clickListener!=null&&performClick();}
    public boolean requestFocusFromTouch(){return requestFocus();}
    public boolean onTrackballEvent(MotionEvent event){return false;}
    public boolean onHoverEvent(MotionEvent event){return false;}
    public boolean dispatchHoverEvent(MotionEvent event){return onHoverEvent(event);}
    public boolean onGenericMotionEvent(MotionEvent event){return false;}
    public boolean dispatchGenericMotionEvent(MotionEvent event){
        if(genericMotionListener!=null&&isEnabled()&&genericMotionListener.onGenericMotion(this,event))return true;
        return onGenericMotionEvent(event);
    }
    public boolean performHapticFeedback(int feedbackConstant){return false;}
    public boolean performHapticFeedback(int feedbackConstant,int flags){return false;}
    public void playSoundEffect(int soundConstant){}
    public boolean isOpaque(){return false;}
    public boolean isInLayout(){return false;}
    public android.os.IBinder getApplicationWindowToken(){return getWindowToken();}
}
