// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.*;
import android.view.*;
import shiraz.runtime.DialogOverlay;
/** Content-only modal dialogs. Explicit theme contexts are supported; platform decor and title bars are separate. */
public class Dialog implements DialogInterface,KeyEvent.Callback,Window.Callback {
 private final Context context;private final Handler handler;private final Window window;private View content;private ViewGroup.LayoutParams params=new ViewGroup.LayoutParams(-2,-2);
 private boolean created,started,hidden,canceled,cancelable=true,outside,contentGesture;private Activity owner;
 private OnCancelListener cancelListener;private OnDismissListener dismissListener;private OnShowListener showListener;private OnKeyListener keyListener;
 private final KeyEvent.DispatcherState keyState=new KeyEvent.DispatcherState();
 public Dialog(Context context){this(context,0);}
 public Dialog(Context context,int theme){if(context==null)throw new NullPointerException("context");DialogOverlay.requireMain();this.context=theme==0?context:new ContextThemeWrapper(context,theme);handler=new Handler(Looper.getMainLooper());window=new Window(this.context){
  @Override public void setContentView(View view,ViewGroup.LayoutParams layout){
   DialogOverlay.requireMain();if(view==null||layout==null)throw new NullPointerException("content/params");if(started)throw new UnsupportedOperationException("Replacing a shown dialog content tree is not implemented");if(view.getParent()!=null||view.isAttachedToWindow())throw new IllegalStateException("Dialog content already attached");if(layout.width<-2||layout.height<-2||layout.width>8192||layout.height>8192)throw new IllegalArgumentException("Dialog dimensions");
   content=getDecorView();params=new ViewGroup.LayoutParams(layout);super.setContentView(view,layout);
  }
 };window.setCallback(this);window.setLayout(-2,-2);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.6f);}
 protected Dialog(Context context,boolean cancelable,OnCancelListener listener){this(context);this.cancelable=cancelable;cancelListener=listener;}
 public final Context getContext(){return context;}public final Activity getOwnerActivity(){return owner;}public final void setOwnerActivity(Activity owner){this.owner=owner;}
 public Window getWindow(){return window;}
 public final boolean requestWindowFeature(int feature){return window.requestFeature(feature);}
 public View getCurrentFocus(){return window.getCurrentFocus();}
 public void onContentChanged(){}
 public View onCreatePanelView(int feature){return null;}
 public boolean onCreatePanelMenu(int feature,Menu menu){return false;}
 public boolean onPreparePanel(int feature,View view,Menu menu){return true;}
 public boolean onMenuItemSelected(int feature,MenuItem item){return false;}
 public boolean onMenuOpened(int feature,Menu menu){return true;}
 public void onPanelClosed(int feature,Menu menu){}
 public void setTitle(CharSequence title){window.setTitle(title);}public void setTitle(int title){setTitle(context.getText(title));}

 public LayoutInflater getLayoutInflater(){return window.getLayoutInflater();}
 public void setContentView(int resource){window.setContentView(resource);}
 public void setContentView(View view){window.setContentView(view,new ViewGroup.LayoutParams(-2,-2));}
 public void setContentView(View view,ViewGroup.LayoutParams params){window.setContentView(view,params);}
 public <T extends View>T findViewById(int id){return content==null?null:content.<T>findViewById(id);}
 protected void onCreate(Bundle state){}protected void onStart(){}protected void onStop(){}
 private void dispatchOnCreate(Bundle state){if(!created){onCreate(state);created=true;}}
 public void create(){DialogOverlay.requireMain();dispatchOnCreate(null);}
 public Bundle onSaveInstanceState(){DialogOverlay.requireMain();Bundle state=new Bundle();state.putBoolean("android:dialogShowing",started);if(created)state.putBundle("android:dialogHierarchy",window.saveHierarchyState());return state;}
 public void onRestoreInstanceState(Bundle state){DialogOverlay.requireMain();if(state==null)throw new NullPointerException("state");Bundle hierarchy=state.getBundle("android:dialogHierarchy");if(hierarchy==null)return;dispatchOnCreate(state);window.restoreHierarchyState(hierarchy);if(state.getBoolean("android:dialogShowing"))show();}
 public void show(){DialogOverlay.requireMain();if(started){if(hidden){DialogOverlay.visible(this,true);hidden=false;}return;}create();if(content==null)throw new UnsupportedOperationException("Dialog requires a supported content View");canceled=false;onStart();try{DialogOverlay.add(this,content,params);started=true;hidden=false;keyState.reset();}catch(RuntimeException failure){onStop();throw failure;}final OnShowListener listener=showListener;if(listener!=null)handler.post(new Runnable(){public void run(){listener.onShow(Dialog.this);}});}
 public void hide(){DialogOverlay.requireMain();if(started&&!hidden){DialogOverlay.visible(this,false);hidden=true;}}
 /** Host-owned consent windows opt into Android dialog entrance motion. */
 public boolean shirazAnimateEntrance(){return false;}
 public boolean isShowing(){return started&&!hidden;}
 public void dismiss(){if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(new Runnable(){public void run(){dismiss();}});return;}DialogOverlay.requireMain();if(!started)return;try{DialogOverlay.remove(this);}finally{started=false;hidden=false;keyState.reset();try{onStop();}finally{final OnDismissListener listener=dismissListener;if(listener!=null)handler.post(new Runnable(){public void run(){listener.onDismiss(Dialog.this);}});}}}
 public void cancel(){if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(new Runnable(){public void run(){cancel();}});return;}DialogOverlay.requireMain();if(!canceled){canceled=true;final OnCancelListener listener=cancelListener;if(listener!=null)handler.post(new Runnable(){public void run(){listener.onCancel(Dialog.this);}});}dismiss();}
 public void setCancelable(boolean value){cancelable=value;}public void setCanceledOnTouchOutside(boolean value){outside=value;if(value)cancelable=true;}
 public void setOnCancelListener(OnCancelListener listener){cancelListener=listener;}public void setOnDismissListener(OnDismissListener listener){dismissListener=listener;}public void setOnShowListener(OnShowListener listener){showListener=listener;}public void setOnKeyListener(OnKeyListener listener){keyListener=listener;}
 public boolean dispatchTouchEvent(MotionEvent event){
  if(event==null)throw new NullPointerException("event");int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN)contentGesture=content!=null&&event.getX()>=0&&event.getY()>=0&&event.getX()<content.getWidth()&&event.getY()<content.getHeight();
  try{return contentGesture&&content!=null&&content.dispatchTouchEvent(event)||onTouchEvent(event);}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)contentGesture=false;}
 }
 public boolean onTouchEvent(MotionEvent event){if(cancelable&&outside&&isShowing()&&(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE||event.getActionMasked()==MotionEvent.ACTION_UP&&content!=null&&(event.getX()<0||event.getY()<0||event.getX()>=content.getWidth()||event.getY()>=content.getHeight()))){cancel();return true;}return false;}
 public boolean dispatchKeyEvent(KeyEvent event){if(event==null)throw new NullPointerException("event");if(keyListener!=null&&keyListener.onKey(this,event.getKeyCode(),event))return true;if(content!=null&&content.hasFocus()&&content.dispatchKeyEvent(event))return true;return event.dispatch(this,keyState,this);}
 public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK){event.startTracking();return true;}return false;}public boolean onKeyLongPress(int code,KeyEvent event){return false;}
 public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK&&event.isTracking()&&!event.isCanceled()){onBackPressed();return true;}return false;}public boolean onKeyMultiple(int code,int count,KeyEvent event){return false;}
 public void onBackPressed(){if(cancelable)cancel();}
}
