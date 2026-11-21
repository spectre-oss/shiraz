// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;
import java.util.Collections;
import java.util.List;

/** Desktop client-area window. Android system bars have no host-owned area here. */
public class Window {
 public static final int FEATURE_OPTIONS_PANEL=0,FEATURE_NO_TITLE=1,FEATURE_PROGRESS=2,FEATURE_LEFT_ICON=3,
  FEATURE_RIGHT_ICON=4,FEATURE_INDETERMINATE_PROGRESS=5,FEATURE_CONTEXT_MENU=6,FEATURE_CUSTOM_TITLE=7,
  FEATURE_ACTION_BAR=8,FEATURE_ACTION_BAR_OVERLAY=9,FEATURE_ACTION_MODE_OVERLAY=10,
  FEATURE_SWIPE_TO_DISMISS=11,FEATURE_CONTENT_TRANSITIONS=12,FEATURE_ACTIVITY_TRANSITIONS=13;
 public static final int PROGRESS_VISIBILITY_ON=-1,PROGRESS_VISIBILITY_OFF=-2,
  PROGRESS_INDETERMINATE_ON=-3,PROGRESS_INDETERMINATE_OFF=-4,
  PROGRESS_START=0,PROGRESS_END=10000,PROGRESS_SECONDARY_START=20000,PROGRESS_SECONDARY_END=30000;
 public static final int ID_ANDROID_CONTENT=16908290;
 public static final String STATUS_BAR_BACKGROUND_TRANSITION_NAME="android:status:background";
 public static final String NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME="android:navigation:background";
 public static final int DECOR_CAPTION_SHADE_AUTO=0,DECOR_CAPTION_SHADE_LIGHT=1,DECOR_CAPTION_SHADE_DARK=2;
 protected static final int DEFAULT_FEATURES=(1<<FEATURE_OPTIONS_PANEL)|(1<<FEATURE_CONTEXT_MENU);

 /** In-process content hierarchy and focus, following PhoneWindow's state keys. */
 public Bundle saveHierarchyState(){
  Bundle state=new Bundle();if(content==null)return state;
  android.util.SparseArray<android.os.Parcelable> views=new android.util.SparseArray<android.os.Parcelable>();content.saveHierarchyState(views);state.putSparseParcelableArray("android:views",views);
  View focused=content.findFocus();if(focused!=null&&focused.getId()!=View.NO_ID)state.putInt("android:focusedViewId",focused.getId());return state;
 }
 public void restoreHierarchyState(Bundle state){
  if(state==null)throw new NullPointerException("state");if(content==null)return;
  android.util.SparseArray<android.os.Parcelable> views=state.getSparseParcelableArray("android:views");if(views!=null)content.restoreHierarchyState(views);
  int id=state.getInt("android:focusedViewId",View.NO_ID);View focused=id==View.NO_ID?null:content.findViewById(id);if(focused!=null)focused.requestFocus();
 }
 public interface OnFrameMetricsAvailableListener {
  void onFrameMetricsAvailable(Window window,FrameMetrics metrics,int dropCountSinceLastInvocation);
 }
 public interface OnRestrictedCaptionAreaChangedListener {
  void onRestrictedCaptionAreaChanged(Rect rect);
 }
 private final java.util.ArrayList<FrameObserver> frameObservers=new java.util.ArrayList<FrameObserver>();
 private boolean firstFrame=true;
 private final class FrameObserver implements Runnable {
  final OnFrameMetricsAvailableListener listener;final Handler handler;
  FrameMetrics pending;int dropped;boolean active=true;
  FrameObserver(OnFrameMetricsAvailableListener listener,Handler handler){this.listener=listener;this.handler=handler;}
  public void run(){FrameMetrics metrics;int count;synchronized(frameObservers){
   if(!active||pending==null)return;metrics=pending;pending=null;count=dropped;dropped=0;
  }listener.onFrameMetricsAvailable(Window.this,metrics,count);}
 }
 public final void addOnFrameMetricsAvailableListener(OnFrameMetricsAvailableListener listener,Handler handler){
  if(listener==null)throw new NullPointerException("listener");
  if(handler==null)handler=new Handler();
  synchronized(frameObservers){
   if(destroyed)throw new IllegalStateException("Window is destroyed");
   for(FrameObserver observer:frameObservers)if(observer.listener==listener)throw new IllegalArgumentException("Listener already registered");
   frameObservers.add(new FrameObserver(listener,handler));
  }
 }
 public final void removeOnFrameMetricsAvailableListener(OnFrameMetricsAvailableListener listener){
  synchronized(frameObservers){for(int i=0;i<frameObservers.size();i++){
   FrameObserver observer=frameObservers.get(i);if(observer.listener==listener){
    observer.active=false;observer.pending=null;observer.handler.removeCallbacks(observer);frameObservers.remove(i);return;
   }
  }}
 }
 public final boolean shirazObservesFrames(){synchronized(frameObservers){return !frameObservers.isEmpty();}}
 /** CPU measure/layout and draw only: GPU completion, vsync and total latency are unavailable. */
 public final void shirazReportFrame(long layoutNanos,long drawNanos){
  synchronized(frameObservers){
   boolean first=firstFrame;firstFrame=false;if(frameObservers.isEmpty())return;
   FrameMetrics metrics=new FrameMetrics(layoutNanos,drawNanos,first);
   for(FrameObserver observer:frameObservers){
    if(observer.pending!=null){if(observer.dropped<Integer.MAX_VALUE)observer.dropped++;observer.pending=metrics;}
    else{observer.pending=metrics;if(!observer.handler.post(observer))observer.pending=null;}
   }
  }
 }
 public interface Callback {
  boolean dispatchKeyEvent(KeyEvent event);
  default boolean dispatchKeyShortcutEvent(KeyEvent event){return false;}
  boolean dispatchTouchEvent(MotionEvent event);
  default boolean dispatchTrackballEvent(MotionEvent event){return false;}
  default boolean dispatchGenericMotionEvent(MotionEvent event){return false;}
  default boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event){return false;}
  View onCreatePanelView(int featureId);
  boolean onCreatePanelMenu(int featureId,Menu menu);
  boolean onPreparePanel(int featureId,View view,Menu menu);
  boolean onMenuOpened(int featureId,Menu menu);
  boolean onMenuItemSelected(int featureId,MenuItem item);
  default void onWindowAttributesChanged(WindowManager.LayoutParams attrs){}
  void onContentChanged();
  default void onWindowFocusChanged(boolean hasFocus){}
  default void onAttachedToWindow(){}
  default void onDetachedFromWindow(){}
  void onPanelClosed(int featureId,Menu menu);
  default boolean onSearchRequested(){return false;}
  default boolean onSearchRequested(SearchEvent searchEvent){return onSearchRequested();}
  default ActionMode onWindowStartingActionMode(ActionMode.Callback callback){return null;}
  default ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type){return null;}
  default void onActionModeStarted(ActionMode mode){}
  default void onActionModeFinished(ActionMode mode){}
  default void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data,Menu menu,int deviceId){}
  default void onPointerCaptureChanged(boolean hasCapture){}
 }
 private final Context context;private Callback callback;private FrameLayout decor,content;private Runnable rootListener;
 private Window container;private boolean hasChildrenFlag,isActiveFlag,hideOverlayWindows;
 private WindowManager explicitWindowManager;private IBinder appToken;private String appName;
 private OnRestrictedCaptionAreaChangedListener restrictedCaptionAreaListener;
 private TypedArray windowStyle;
 private int features=1,localFeatures=1,forcedWindowFlags,statusBarColor=0xff000000,navigationBarColor=0xff000000;
 private boolean installed,hasSoftInputModeFlag,sustainedPerformanceMode;
 private float desiredHdrHeadroom;
 private boolean frameRateBoostOnTouch=true,frameRatePowerSavingsBalanced=true;
 private MediaController mediaController;
 private int uiOptions,backgroundBlurRadius,decorCaptionShade=DECOR_CAPTION_SHADE_AUTO;
 private TransitionManager transitionManager;
 private Transition enterTransition,returnTransition,exitTransition,reenterTransition;
 private Transition sharedElementEnterTransition,sharedElementReturnTransition,sharedElementExitTransition,sharedElementReenterTransition;
 private boolean allowEnterTransitionOverlap=true,allowReturnTransitionOverlap=true,sharedElementsUseOverlay=true;
 private long transitionBackgroundFadeDuration=300L;
 private List<Rect> systemGestureExclusionRects=Collections.emptyList();
 private OnBackInvokedDispatcher backInvokedDispatcher;
 // Selection metadata; host volume-key routing is not implemented yet.
 private int volumeControlStream=Integer.MIN_VALUE;
 public void setVolumeControlStream(int streamType){volumeControlStream=streamType;}
 public int getVolumeControlStream(){return volumeControlStream;}
 private final WindowManager.LayoutParams attributes=new WindowManager.LayoutParams();
 public final WindowManager.LayoutParams getAttributes(){return attributes;}
 private void attributesChanged(){if(decor!=null){decor.requestLayout();decor.invalidate();}if(callback!=null)callback.onWindowAttributesChanged(attributes);}
 public void setAttributes(WindowManager.LayoutParams value){if(value==null)throw new NullPointerException("attributes");if(value.width<-2||value.height<-2||value.width>8192||value.height>8192||!Float.isFinite(value.dimAmount)||value.dimAmount<0||value.dimAmount>1||!Float.isFinite(value.alpha)||value.alpha<0||value.alpha>1)throw new IllegalArgumentException("Window attributes");attributes.copyFrom(value);attributesChanged();}
 public void setLayout(int width,int height){WindowManager.LayoutParams value=new WindowManager.LayoutParams(attributes);value.width=width;value.height=height;setAttributes(value);}
 public void setGravity(int gravity){attributes.gravity=gravity;attributesChanged();}
 public void setFlags(int flags,int mask){attributes.flags=(attributes.flags&~mask)|(flags&mask);forcedWindowFlags|=mask;attributesChanged();}
 public void addFlags(int flags){setFlags(flags,flags);}public void clearFlags(int flags){setFlags(0,flags);}
 public void setDimAmount(float amount){WindowManager.LayoutParams value=new WindowManager.LayoutParams(attributes);value.dimAmount=amount;setAttributes(value);}
 public void setWindowAnimations(int animations){attributes.windowAnimations=animations;attributesChanged();}
 public void setFormat(int format){attributes.format=format==0?android.graphics.PixelFormat.OPAQUE:format;attributesChanged();}
 public void setType(int type){attributes.type=type;attributesChanged();}
 public void setWindowManager(WindowManager wm,IBinder appToken,String appName){setWindowManager(wm,appToken,appName,false);}
 public void setWindowManager(WindowManager wm,IBinder appToken,String appName,boolean hardwareAccelerated){this.explicitWindowManager=wm;this.appToken=appToken;this.appName=appName;}
 public WindowManager getWindowManager(){return explicitWindowManager!=null?explicitWindowManager:(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);}
 public void setSoftInputMode(int mode){if(mode!=WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)hasSoftInputModeFlag=true;else hasSoftInputModeFlag=false;attributes.softInputMode=mode;attributesChanged();}
 public final TypedArray getWindowStyle(){if(windowStyle==null)windowStyle=context.obtainStyledAttributes(new int[0]);return windowStyle;}
 public void setContainer(Window container){this.container=container;if(container!=null)container.hasChildrenFlag=true;}
 public final Window getContainer(){return container;}
 public final boolean hasChildren(){return hasChildrenFlag;}
 public final void setRestrictedCaptionAreaListener(OnRestrictedCaptionAreaChangedListener listener){restrictedCaptionAreaListener=listener;}
 public final void setHideOverlayWindows(boolean hide){hideOverlayWindows=hide;}
 public void takeSurface(SurfaceHolder.Callback2 callback){}
 public void takeInputQueue(InputQueue.Callback callback){}
 public boolean isFloating(){return false;}
 public void setColorMode(int colorMode){attributes.setColorMode(colorMode);attributesChanged();}
 public int getColorMode(){return attributes.getColorMode();}
 public boolean isWideColorGamut(){return false;}
 public void setDesiredHdrHeadroom(float desiredHeadroom){desiredHdrHeadroom=desiredHeadroom;attributes.setDesiredHdrHeadroom(desiredHeadroom);}
 public float getDesiredHdrHeadroom(){return desiredHdrHeadroom;}
 public void setFrameRateBoostOnTouchEnabled(boolean enabled){frameRateBoostOnTouch=enabled;attributes.setFrameRateBoostOnTouchEnabled(enabled);}
 public boolean getFrameRateBoostOnTouchEnabled(){return frameRateBoostOnTouch;}
 public void setFrameRatePowerSavingsBalanced(boolean enabled){frameRatePowerSavingsBalanced=enabled;attributes.setFrameRatePowerSavingsBalanced(enabled);}
 public boolean isFrameRatePowerSavingsBalanced(){return frameRatePowerSavingsBalanced;}
 public void setPreferMinimalPostProcessing(boolean isPreferred){attributes.preferMinimalPostProcessing=isPreferred;attributesChanged();}
 protected final int getForcedWindowFlags(){return forcedWindowFlags;}
 protected final boolean hasSoftInputMode(){return hasSoftInputModeFlag;}
 public void setSustainedPerformanceMode(boolean enable){sustainedPerformanceMode=enable;}
 public final void makeActive(){if(!isActiveFlag){isActiveFlag=true;onActive();}}
 public final boolean isActive(){return isActiveFlag;}
 protected void onActive(){}
 protected final int getFeatures(){return features;}
 public static int getDefaultFeatures(Context context){return DEFAULT_FEATURES;}
 protected final int getLocalFeatures(){return localFeatures;}
 protected void setDefaultWindowFormat(int format){if(attributes.format==-1)setFormat(format);}
 private Drawable background;
 private shiraz.runtime.HostOptionsMenu options;private boolean prepared,invalidating,destroyed;private Handler menuHandler;
 private final Runnable invalidateMenu=new Runnable(){public void run(){invalidating=false;prepared=false;if(options!=null){options.retire();options=null;}}};
 public void invalidatePanelMenu(int feature){
  if(feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Panel invalidation is not implemented: "+feature);
  if(destroyed||invalidating)return;
  if(menuHandler==null)menuHandler=new Handler(android.os.Looper.getMainLooper());
  invalidating=true;if(!menuHandler.post(invalidateMenu)){invalidating=false;throw new IllegalStateException("Menu queue closed");}
 }
 public Menu shirazPrepareOptionsPanel(){
  if(destroyed)throw new IllegalStateException("Window is destroyed");
  if(invalidating){menuHandler.removeCallbacks(invalidateMenu);invalidateMenu.run();}
  if(prepared)return options;if(callback==null)return null;
  try {View panel=callback.onCreatePanelView(FEATURE_OPTIONS_PANEL);if(panel!=null)throw new UnsupportedOperationException("Custom panel views are not implemented");
  if(options==null){options=new shiraz.runtime.HostOptionsMenu(context,new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){return callback!=null&&callback.onMenuItemSelected(FEATURE_OPTIONS_PANEL,item);}});
   if(!callback.onCreatePanelMenu(FEATURE_OPTIONS_PANEL,options)){options.retire();options=null;return null;}}
  if(!callback.onPreparePanel(FEATURE_OPTIONS_PANEL,null,options))return null;
  prepared=true;return options;
  }catch(RuntimeException|Error failure){if(options!=null)options.retire();options=null;prepared=false;throw failure;}
 }
 public void openPanel(int feature,KeyEvent event){if(feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Panel type");if(shirazPrepareOptionsPanel()!=null)throw new UnsupportedOperationException("Options popup presentation is not implemented");}
 public void closePanel(int feature){if(feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Panel type");prepared=false;if(options!=null)options.close();}
 public void togglePanel(int featureId,KeyEvent event){if(prepared)closePanel(featureId);else openPanel(featureId,event);}
 public boolean performPanelShortcut(int featureId,int keyCode,KeyEvent event,int flags){return false;}
 public boolean performPanelIdentifierAction(int featureId,int id,int flags){return false;}
 public void closeAllPanels(){if(options!=null)closePanel(FEATURE_OPTIONS_PANEL);}
 public boolean performContextMenuIdentifierAction(int id,int flags){return false;}
 public void onConfigurationChanged(Configuration newConfig){if(decor!=null)decor.dispatchConfigurationChanged(newConfig);}
 public void setElevation(float elevation){if(decor!=null)decor.setElevation(elevation);}
 public void setClipToOutline(boolean clipToOutline){if(decor!=null)decor.setClipToOutline(clipToOutline);}
 public void setBackgroundBlurRadius(int blurRadius){backgroundBlurRadius=blurRadius;}
 public void setFeatureDrawableResource(int featureId,int resId){}
 public void setFeatureDrawableUri(int featureId,Uri uri){}
 public void setFeatureDrawable(int featureId,Drawable drawable){}
 public void setFeatureDrawableAlpha(int featureId,int alpha){}
 public void setFeatureInt(int featureId,int value){}
 public void takeKeyEvents(boolean get){}
 public void setChildDrawable(int featureId,Drawable drawable){}
 public void setChildInt(int featureId,int value){}
 public boolean isShortcutKey(int keyCode,KeyEvent event){return false;}
 public void setMediaController(MediaController controller){mediaController=controller;}
 public MediaController getMediaController(){return mediaController;}
 public void setUiOptions(int uiOptions){this.uiOptions=uiOptions;}
 public void setUiOptions(int uiOptions,int mask){this.uiOptions=(this.uiOptions&~mask)|(uiOptions&mask);}
 public void setIcon(int resId){}
 public void setLogo(int resId){}
 public void setLocalFocus(boolean hasFocus,boolean inTouchMode){}
 public void injectInputEvent(InputEvent event){}
 public TransitionManager getTransitionManager(){return transitionManager;}
 public void setTransitionManager(TransitionManager tm){transitionManager=tm;}
 public Scene getContentScene(){return null;}
 public void setEnterTransition(Transition transition){enterTransition=transition;}
 public void setReturnTransition(Transition transition){returnTransition=transition;}
 public void setExitTransition(Transition transition){exitTransition=transition;}
 public void setReenterTransition(Transition transition){reenterTransition=transition;}
 public Transition getEnterTransition(){return enterTransition;}
 public Transition getReturnTransition(){return returnTransition;}
 public Transition getExitTransition(){return exitTransition;}
 public Transition getReenterTransition(){return reenterTransition;}
 public void setSharedElementEnterTransition(Transition transition){sharedElementEnterTransition=transition;}
 public void setSharedElementReturnTransition(Transition transition){sharedElementReturnTransition=transition;}
 public Transition getSharedElementEnterTransition(){return sharedElementEnterTransition;}
 public Transition getSharedElementReturnTransition(){return sharedElementReturnTransition;}
 public void setSharedElementExitTransition(Transition transition){sharedElementExitTransition=transition;}
 public void setSharedElementReenterTransition(Transition transition){sharedElementReenterTransition=transition;}
 public Transition getSharedElementExitTransition(){return sharedElementExitTransition;}
 public Transition getSharedElementReenterTransition(){return sharedElementReenterTransition;}
 public void setAllowEnterTransitionOverlap(boolean allow){allowEnterTransitionOverlap=allow;}
 public boolean getAllowEnterTransitionOverlap(){return allowEnterTransitionOverlap;}
 public void setAllowReturnTransitionOverlap(boolean allow){allowReturnTransitionOverlap=allow;}
 public boolean getAllowReturnTransitionOverlap(){return allowReturnTransitionOverlap;}
 public long getTransitionBackgroundFadeDuration(){return transitionBackgroundFadeDuration;}
 public void setTransitionBackgroundFadeDuration(long fadeDurationMillis){transitionBackgroundFadeDuration=fadeDurationMillis;}
 public boolean getSharedElementsUseOverlay(){return sharedElementsUseOverlay;}
 public void setSharedElementsUseOverlay(boolean sharedElementsUseOverlay){this.sharedElementsUseOverlay=sharedElementsUseOverlay;}
 public void setSystemGestureExclusionRects(List<Rect> rects){systemGestureExclusionRects=rects!=null?new java.util.ArrayList<Rect>(rects):Collections.<Rect>emptyList();}
 public List<Rect> getSystemGestureExclusionRects(){return Collections.unmodifiableList(systemGestureExclusionRects);}
 public void registerScrollCaptureCallback(ScrollCaptureCallback callback){}
 public void unregisterScrollCaptureCallback(ScrollCaptureCallback callback){}
 public void setDecorCaptionShade(int decorCaptionShade){this.decorCaptionShade=decorCaptionShade;}
 public void setResizingCaptionDrawable(Drawable drawable){}
 public WindowInsetsController getInsetsController(){return decor!=null?decor.getWindowInsetsController():null;}
 public AttachedSurfaceControl getRootSurfaceControl(){return decor!=null?decor.getRootSurfaceControl():null;}
 public OnBackInvokedDispatcher getOnBackInvokedDispatcher(){
  if(backInvokedDispatcher==null){
   backInvokedDispatcher=new OnBackInvokedDispatcher(){
    @Override public void registerOnBackInvokedCallback(int priority,android.window.OnBackInvokedCallback callback){}
    @Override public void unregisterOnBackInvokedCallback(android.window.OnBackInvokedCallback callback){}
   };
  }
  return backInvokedDispatcher;
 }
 public void shirazDestroy(){synchronized(frameObservers){destroyed=true;for(FrameObserver observer:frameObservers){observer.active=false;observer.pending=null;observer.handler.removeCallbacks(observer);}frameObservers.clear();}if(menuHandler!=null)menuHandler.removeCallbacks(invalidateMenu);invalidateMenu.run();}
 private String title="";private int titleColor;
 public static String shirazTitle(CharSequence value){if(value==null)return "";if(value.length()>4096)throw new IllegalArgumentException("Window title length");String text=value.toString();if(text.length()>4096||text.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid window title");return text;}
 public void setTitle(CharSequence value){title=shirazTitle(value);if(actionBar!=null)actionBar.setTitle(title);}
 public void setTitleColor(int color){titleColor=color;}
 public final String shirazGetTitle(){return title;}
 public final int shirazGetTitleColor(){return titleColor;}
 public Window(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 public final Context getContext(){return context;}
 public void setCallback(Callback callback){this.callback=callback;}
 public final Callback getCallback(){return callback;}
 public final void shirazSetRootListener(Runnable listener){rootListener=listener;}
 private android.widget.Toolbar actionBar;
 private int actionBarHeight;
 private boolean themeFlag(int attr,boolean fallback){android.util.TypedValue v=new android.util.TypedValue();if(!context.getTheme().resolveAttribute(attr,v,true))return fallback;if(v.type!=0x12)return fallback;return v.data!=0;}
 private void installActionBar(){
  if(!(context instanceof android.app.Activity)||hasFeature(FEATURE_NO_TITLE)||themeFlag(0x01010056,false)||!themeFlag(0x010102cd,false))return;
  android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
  android.util.TypedValue v=new android.util.TypedValue();
  actionBarHeight=context.getTheme().resolveAttribute(0x010102eb,v,true)&&v.type==5?android.util.TypedValue.complexToDimensionPixelSize(v.data,metrics):(int)(56*metrics.density);
  int color=context.getTheme().resolveAttribute(0x01010433,v,true)&&v.type>=0x1c&&v.type<=0x1f?v.data:0xff607d8b;
  actionBar=new android.widget.Toolbar(context);actionBar.setId(0x0102004d);actionBar.setBackground(new android.graphics.drawable.ColorDrawable(color));
  int r=(color>>16)&255,g=(color>>8)&255,b=color&255;boolean dark=(r*299+g*587+b*114)/1000<150;
  actionBar.setTitleTextColor(dark?0xffffffff:0xde000000);actionBar.setSubtitleTextColor(dark?0xb3ffffff:0x8a000000);actionBar.setTitle(title);
  decor.addView(actionBar,new FrameLayout.LayoutParams(-1,actionBarHeight));
  ((FrameLayout.LayoutParams)content.getLayoutParams()).topMargin=actionBarHeight;
 }
 public final android.widget.Toolbar shirazActionBarView(){getDecorView();return actionBar;}
 public final void shirazSetActionBarShown(boolean shown){if(actionBar==null)return;actionBar.setVisibility(shown?View.VISIBLE:View.GONE);((FrameLayout.LayoutParams)content.getLayoutParams()).topMargin=shown?actionBarHeight:0;content.requestLayout();}
 private Drawable themeBackground(){
  android.util.TypedValue value=new android.util.TypedValue();
  if(!context.getTheme().resolveAttribute(0x01010054,value,true))return null;
  if(value.type>=0x1c&&value.type<=0x1f)return new android.graphics.drawable.ColorDrawable(value.data);
  int id=value.resourceId!=0?value.resourceId:value.data;
  return id==0?null:context.getDrawable(id);
 }
 public void setDecorFitsSystemWindows(boolean value){}
 private boolean navigationBarContrast=true,statusBarContrast=true;
 private int navigationBarDividerColor;
 public void setNavigationBarContrastEnforced(boolean value){navigationBarContrast=value;}
 public boolean isNavigationBarContrastEnforced(){return navigationBarContrast;}
 public void setStatusBarContrastEnforced(boolean value){statusBarContrast=value;}
 public boolean isStatusBarContrastEnforced(){return statusBarContrast;}
 public void setNavigationBarDividerColor(int color){navigationBarDividerColor=color;}
 public int getNavigationBarDividerColor(){return navigationBarDividerColor;}
 public View getDecorView(){
  if(decor==null){decor=new FrameLayout(context){@Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(callback!=null&&!destroyed)callback.onWindowFocusChanged(focused);}};decor.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);content=new FrameLayout(context);content.setId(16908290);decor.addView(content,new FrameLayout.LayoutParams(-1,-1));installActionBar();if(background==null)background=themeBackground();if(background!=null)decor.setBackground(background);if(rootListener!=null)rootListener.run();}return decor;
 }
 public View peekDecorView(){return decor;}
 public LayoutInflater getLayoutInflater(){return LayoutInflater.from(context);}
 public void setContentView(int id){getDecorView();View view=getLayoutInflater().inflate(id,content,false);setContentView(view,view.getLayoutParams());}
 public void setContentView(View view){setContentView(view,new ViewGroup.LayoutParams(-1,-1));}
 public void setContentView(View view,ViewGroup.LayoutParams params){
  if(view==null||params==null)throw new NullPointerException("content");getDecorView();
  if(view.getParent()!=null&&view.getParent()!=content)throw new IllegalStateException("Content view already has a parent");
  content.removeAllViews();content.addView(view,params);installed=true;if(callback!=null)callback.onContentChanged();
 }
 public void addContentView(View view,ViewGroup.LayoutParams params){getDecorView();content.addView(view,params);installed=true;if(callback!=null)callback.onContentChanged();}
 public <T extends View>T findViewById(int id){return getDecorView().findViewById(id);}
 public final <T extends View>T requireViewById(int id){
  T view=findViewById(id);
  if(view==null)throw new IllegalArgumentException("ID does not reference a View inside this Window");
  return view;
 }
 public View getCurrentFocus(){return decor==null?null:decor.findFocus();}
 public void setBackgroundDrawable(Drawable value){background=value;if(decor!=null)decor.setBackground(value);}
 public void setBackgroundDrawableResource(int id){setBackgroundDrawable(id==0?null:context.getDrawable(id));}
 public boolean requestFeature(int feature){if(installed)throw new IllegalStateException("Features must be requested before content");if(feature!=FEATURE_NO_TITLE&&feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Window feature is not implemented: "+feature);features|=1<<feature;localFeatures|=1<<feature;return true;}
 public boolean hasFeature(int feature){return feature>=0&&feature<32&&(features&(1<<feature))!=0;}
 public void setStatusBarColor(int color){statusBarColor=color;}
 public int getStatusBarColor(){return statusBarColor;}
 public void setNavigationBarColor(int color){navigationBarColor=color;}
 public int getNavigationBarColor(){return navigationBarColor;}
 public boolean superDispatchKeyEvent(KeyEvent event){return decor!=null&&decor.hasFocus()&&decor.dispatchKeyEvent(event);}
 public boolean superDispatchKeyShortcutEvent(KeyEvent event){return decor!=null&&decor.hasFocus()&&decor.dispatchKeyShortcutEvent(event);}
 public boolean superDispatchTouchEvent(MotionEvent event){return decor!=null&&decor.dispatchTouchEvent(event);}
 public boolean superDispatchTrackballEvent(MotionEvent event){return decor!=null&&decor.dispatchTrackballEvent(event);}
 public boolean superDispatchGenericMotionEvent(MotionEvent event){return decor!=null&&decor.dispatchGenericMotionEvent(event);}
 public final boolean shirazDispatchKeyEvent(KeyEvent event){boolean handled=callback!=null?callback.dispatchKeyEvent(event):superDispatchKeyEvent(event);return handled||shirazNavigateKeyEvent(event);}
 public final boolean shirazNavigateKeyEvent(KeyEvent event){return KeyboardClusters.key(decor,event);}
 public final boolean shirazDispatchTouchEvent(MotionEvent event){return callback!=null?callback.dispatchTouchEvent(event):superDispatchTouchEvent(event);}
}
