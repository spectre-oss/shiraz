// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
/** Desktop client-area window. Android system bars have no host-owned area here. */
public class Window {
 /** In-process content hierarchy and focus, following PhoneWindow's state keys. */
 public android.os.Bundle saveHierarchyState(){
  android.os.Bundle state=new android.os.Bundle();if(content==null)return state;
  android.util.SparseArray<android.os.Parcelable> views=new android.util.SparseArray<android.os.Parcelable>();content.saveHierarchyState(views);state.putSparseParcelableArray("android:views",views);
  View focused=content.findFocus();if(focused!=null&&focused.getId()!=View.NO_ID)state.putInt("android:focusedViewId",focused.getId());return state;
 }
 public void restoreHierarchyState(android.os.Bundle state){
  if(state==null)throw new NullPointerException("state");if(content==null)return;
  android.util.SparseArray<android.os.Parcelable> views=state.getSparseParcelableArray("android:views");if(views!=null)content.restoreHierarchyState(views);
  int id=state.getInt("android:focusedViewId",View.NO_ID);View focused=id==View.NO_ID?null:content.findViewById(id);if(focused!=null)focused.requestFocus();
 }
 public interface OnFrameMetricsAvailableListener {
  void onFrameMetricsAvailable(Window window,FrameMetrics metrics,int dropCountSinceLastInvocation);
 }
 private final java.util.ArrayList<FrameObserver> frameObservers=new java.util.ArrayList<FrameObserver>();
 private boolean firstFrame=true;
 private final class FrameObserver implements Runnable {
  final OnFrameMetricsAvailableListener listener;final android.os.Handler handler;
  FrameMetrics pending;int dropped;boolean active=true;
  FrameObserver(OnFrameMetricsAvailableListener listener,android.os.Handler handler){this.listener=listener;this.handler=handler;}
  public void run(){FrameMetrics metrics;int count;synchronized(frameObservers){
   if(!active||pending==null)return;metrics=pending;pending=null;count=dropped;dropped=0;
  }listener.onFrameMetricsAvailable(Window.this,metrics,count);}
 }
 public final void addOnFrameMetricsAvailableListener(OnFrameMetricsAvailableListener listener,android.os.Handler handler){
  if(listener==null)throw new NullPointerException("listener");
  if(handler==null)handler=new android.os.Handler();
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
 public static final int FEATURE_OPTIONS_PANEL=0,FEATURE_NO_TITLE=1,FEATURE_ACTION_BAR=8,FEATURE_ACTION_BAR_OVERLAY=9;
 public interface Callback {boolean dispatchKeyEvent(KeyEvent event);boolean dispatchTouchEvent(MotionEvent event);void onContentChanged();View onCreatePanelView(int feature);boolean onCreatePanelMenu(int feature,Menu menu);boolean onPreparePanel(int feature,View view,Menu menu);boolean onMenuItemSelected(int feature,MenuItem item);boolean onMenuOpened(int feature,Menu menu);void onPanelClosed(int feature,Menu menu);}
 private final Context context;private Callback callback;private FrameLayout decor,content;private Runnable rootListener;
 private int features=1,statusBarColor=0xff000000,navigationBarColor=0xff000000;private boolean installed;
 // Selection metadata; host volume-key routing is not implemented yet.
 private int volumeControlStream=Integer.MIN_VALUE;
 public void setVolumeControlStream(int streamType){volumeControlStream=streamType;}
 public int getVolumeControlStream(){return volumeControlStream;}
 private final WindowManager.LayoutParams attributes=new WindowManager.LayoutParams();
 public WindowManager.LayoutParams getAttributes(){return attributes;}
 private void attributesChanged(){if(decor!=null){decor.requestLayout();decor.invalidate();}}
 public void setAttributes(WindowManager.LayoutParams value){if(value==null)throw new NullPointerException("attributes");if(value.width<-2||value.height<-2||value.width>8192||value.height>8192||!Float.isFinite(value.dimAmount)||value.dimAmount<0||value.dimAmount>1||!Float.isFinite(value.alpha)||value.alpha<0||value.alpha>1)throw new IllegalArgumentException("Window attributes");attributes.copyFrom(value);attributesChanged();}
 public void setLayout(int width,int height){WindowManager.LayoutParams value=new WindowManager.LayoutParams(attributes);value.width=width;value.height=height;setAttributes(value);}
 public void setGravity(int gravity){attributes.gravity=gravity;attributesChanged();}
 public void setFlags(int flags,int mask){attributes.flags=(attributes.flags&~mask)|(flags&mask);attributesChanged();}
 public void addFlags(int flags){setFlags(flags,flags);}public void clearFlags(int flags){setFlags(0,flags);}
 public void setDimAmount(float amount){WindowManager.LayoutParams value=new WindowManager.LayoutParams(attributes);value.dimAmount=amount;setAttributes(value);}
 public void setWindowAnimations(int animations){attributes.windowAnimations=animations;attributesChanged();}
 public void setSoftInputMode(int mode){attributes.softInputMode=mode;attributesChanged();}
 private Drawable background;
 private shiraz.runtime.HostOptionsMenu options;private boolean prepared,invalidating,destroyed;private android.os.Handler menuHandler;
 private final Runnable invalidateMenu=new Runnable(){public void run(){invalidating=false;prepared=false;if(options!=null){options.retire();options=null;}}};
 public void invalidatePanelMenu(int feature){
  if(feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Panel invalidation is not implemented: "+feature);
  if(destroyed||invalidating)return;
  if(menuHandler==null)menuHandler=new android.os.Handler(android.os.Looper.getMainLooper());
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
 public void shirazDestroy(){synchronized(frameObservers){destroyed=true;for(FrameObserver observer:frameObservers){observer.active=false;observer.pending=null;observer.handler.removeCallbacks(observer);}frameObservers.clear();}if(menuHandler!=null)menuHandler.removeCallbacks(invalidateMenu);invalidateMenu.run();}
 private String title="";private int titleColor;
 public static String shirazTitle(CharSequence value){if(value==null)return "";if(value.length()>4096)throw new IllegalArgumentException("Window title length");String text=value.toString();if(text.length()>4096||text.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid window title");return text;}
 public void setTitle(CharSequence value){title=shirazTitle(value);}
 public void setTitleColor(int color){titleColor=color;}
 public final String shirazGetTitle(){return title;}
 public final int shirazGetTitleColor(){return titleColor;}
 public Window(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 public final Context getContext(){return context;}
 public final void setCallback(Callback callback){this.callback=callback;}
 public final Callback getCallback(){return callback;}
 public final void shirazSetRootListener(Runnable listener){rootListener=listener;}
 private Drawable themeBackground(){
  android.util.TypedValue value=new android.util.TypedValue();
  if(!context.getTheme().resolveAttribute(0x01010054,value,true))return null;
  if(value.type>=0x1c&&value.type<=0x1f)return new android.graphics.drawable.ColorDrawable(value.data);
  int id=value.resourceId!=0?value.resourceId:value.data;
  return id==0?null:context.getDrawable(id);
 }
 public void setDecorFitsSystemWindows(boolean value){}
 public void setNavigationBarContrastEnforced(boolean value){}
 public void setStatusBarContrastEnforced(boolean value){}
 public View getDecorView(){
  if(decor==null){decor=new FrameLayout(context);content=new FrameLayout(context);content.setId(16908290);decor.addView(content,new FrameLayout.LayoutParams(-1,-1));if(background==null)background=themeBackground();if(background!=null)decor.setBackground(background);if(rootListener!=null)rootListener.run();}return decor;
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
 public View getCurrentFocus(){return decor==null?null:decor.findFocus();}
 public void setBackgroundDrawable(Drawable value){background=value;if(decor!=null)decor.setBackground(value);}
 public void setBackgroundDrawableResource(int id){setBackgroundDrawable(id==0?null:context.getDrawable(id));}
 public boolean requestFeature(int feature){if(installed)throw new IllegalStateException("Features must be requested before content");if(feature!=FEATURE_NO_TITLE&&feature!=FEATURE_OPTIONS_PANEL)throw new UnsupportedOperationException("Window feature is not implemented: "+feature);features|=1<<feature;return true;}
 public boolean hasFeature(int feature){return feature>=0&&feature<32&&(features&(1<<feature))!=0;}
 public void setStatusBarColor(int color){statusBarColor=color;}
 public int getStatusBarColor(){return statusBarColor;}
 public void setNavigationBarColor(int color){navigationBarColor=color;}
 public int getNavigationBarColor(){return navigationBarColor;}
 public boolean superDispatchKeyEvent(KeyEvent event){return decor!=null&&decor.hasFocus()&&decor.dispatchKeyEvent(event);}
 public boolean superDispatchTouchEvent(MotionEvent event){return decor!=null&&decor.dispatchTouchEvent(event);}
 public final boolean shirazDispatchKeyEvent(KeyEvent event){boolean handled=callback!=null?callback.dispatchKeyEvent(event):superDispatchKeyEvent(event);return handled||shirazNavigateKeyEvent(event);}
 public final boolean shirazNavigateKeyEvent(KeyEvent event){return KeyboardClusters.key(decor,event);}
 public final boolean shirazDispatchTouchEvent(MotionEvent event){return callback!=null?callback.dispatchTouchEvent(event):superDispatchTouchEvent(event);}
}
