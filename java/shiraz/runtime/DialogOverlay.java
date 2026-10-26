// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Dialog;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.*;
import java.util.ArrayList;
/** Main-thread modal stack composed into the existing native application window. */
public final class DialogOverlay {
 private static final class Entry {final Dialog dialog;final View view;final ViewGroup.LayoutParams params;final ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();boolean visible=true,entrancePending;float scale=1,alpha=1;android.animation.ValueAnimator entrance;View hiddenFocus;int left,top,width,height;Entry(Dialog d,View v,ViewGroup.LayoutParams p){dialog=d;view=v;params=p;entrancePending=d.shirazAnimateEntrance();}}
 private static boolean touchMode;
 private static Entry[] prepared=new Entry[0];
 private static final ArrayList<Entry> entries=new ArrayList<Entry>();private static long revision;private static boolean clearing;private static Entry capture;private static boolean pointer;private static long down;private static final java.util.HashMap<Integer,Entry> keys=new java.util.HashMap<Integer,Entry>();
 private DialogOverlay(){}
 public static void requireMain(){if(Looper.myLooper()==null||Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Dialog operations require the main Looper");}
 private static Entry find(Dialog dialog){for(Entry e:entries)if(e.dialog==dialog)return e;return null;}
 private static Entry top(){for(int i=entries.size()-1;i>=0;i--)if(entries.get(i).visible)return entries.get(i);return null;}
 public static void add(Dialog dialog,View view,ViewGroup.LayoutParams params){requireMain();if(clearing)throw new IllegalStateException("Dialog creation during host teardown");if(find(dialog)!=null)throw new IllegalStateException("Dialog already registered");if(entries.size()>=32)throw new IllegalStateException("Dialog window limit");Entry e=new Entry(dialog,view,params);entries.add(e);revision++;}
 // AOSP dialog_enter: centred 0.9 -> 1 scale, quintic deceleration (220ms),
 // and cubic-decelerated alpha (150ms). Start after layout, never during show().
 private static void stopEntrance(Entry e){if(e.entrance!=null){e.entrance.cancel();e.entrance=null;}e.entrancePending=false;e.scale=1;e.alpha=1;}
 private static void startEntrance(final Entry e){
  if(!e.entrancePending)return;e.entrancePending=false;
  if(!android.animation.ValueAnimator.areAnimatorsEnabled())return;
  e.scale=.9f;e.alpha=0;
  android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(0,1);e.entrance=animator;
  animator.setDuration(220);animator.setInterpolator(null);
  animator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(android.animation.ValueAnimator value){
   float t=value.getAnimatedFraction(),scaleRemaining=1-t,alphaRemaining=1-Math.min(1,t*220f/150f);
   e.scale=1-.1f*scaleRemaining*scaleRemaining*scaleRemaining*scaleRemaining*scaleRemaining;
   e.alpha=1-alphaRemaining*alphaRemaining*alphaRemaining;revision++;
  }});animator.start();
 }
 private static MotionEvent localEvent(Entry e,MotionEvent incoming){
  MotionEvent event=MotionEvent.obtain(incoming);event.offsetLocation(-e.left,-e.top);
  if(e.scale!=1)event.shirazScaleLocation(1/e.scale,e.view.getWidth()/2f,e.view.getHeight()/2f);
  return event;
 }
 private static void abandon(Entry e){
  if(capture==e){capture=null;MotionEvent event=MotionEvent.shirazObtain(down,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0,0,0x1002);try{e.dialog.dispatchTouchEvent(event);}finally{event.recycle();}}
  for(java.util.Map.Entry<Integer,Entry> key:keys.entrySet())if(key.getValue()==e)key.setValue(null);e.view.clearFocus();
 }
 public static void visible(Dialog dialog,boolean visible){
  requireMain();Entry e=find(dialog);if(e==null||e.visible==visible)return;e.visible=visible;revision++;
  if(!visible){stopEntrance(e);e.hiddenFocus=e.view.findFocus();abandon(e);e.view.shirazSetWindowFocus(false);return;}
  e.entrancePending=e.dialog.shirazAnimateEntrance();
  View focused=e.hiddenFocus;e.hiddenFocus=null;
  if(focused==null||!focused.isAttachedToWindow()||focused.getRootView()!=e.view.getRootView()||!focused.requestFocus())return;
 }
 private static void requestKeyboard(Entry e){
  View focused=e.view.findFocus();int mode=e.dialog.getWindow().getAttributes().softInputMode&15;
  if(focused==null||mode==1||mode==2||mode==3||!focused.onCheckIsTextEditor()||focused instanceof android.widget.TextView&&!((android.widget.TextView)focused).getShowSoftInputOnFocus())return;
  int flags=e.dialog.getWindow().getAttributes().flags;
  if((flags&(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM))!=0)return;
  Object service=e.dialog.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
  if(service instanceof android.view.inputmethod.InputMethodManager)((android.view.inputmethod.InputMethodManager)service).showSoftInput(focused,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
 }

 public static void remove(Dialog dialog){requireMain();Entry e=find(dialog);if(e!=null){stopEntrance(e);entries.remove(e);revision++;try{abandon(e);}finally{try{e.view.shirazSetWindowFocus(false);}finally{e.view.shirazDetachTree();e.observer.shirazKill();}}}}
 public static long revision(){requireMain();for(Entry e:entries)if(e.visible&&(e.view.shirazIsInvalidated()||e.view.isLayoutRequested())){revision++;break;}return revision;}
 private static int spec(int size,int available){return View.MeasureSpec.makeMeasureSpec(size>=0?Math.min(size,available):available,size==-2?View.MeasureSpec.AT_MOST:View.MeasureSpec.EXACTLY);}
 private static void layout(Entry e,int width,int height){if(!e.view.isAttachedToWindow()){e.view.shirazAttachTree(e.observer);e.view.shirazSetTouchMode(touchMode);}if(!entries.contains(e))return;if(e.view.isLayoutRequested()||e.width!=width||e.height!=height){e.width=width;e.height=height;WindowManager.LayoutParams attributes=e.dialog.getWindow().getAttributes();e.view.measure(spec(attributes.width,width),spec(attributes.height,height));int w=e.view.getMeasuredWidth(),h=e.view.getMeasuredHeight();android.graphics.Rect bounds=new android.graphics.Rect();Gravity.apply(attributes.gravity,w,h,new android.graphics.Rect(0,0,width,height),bounds,e.view.getLayoutDirection());e.left=bounds.left+attributes.x;e.top=bounds.top+attributes.y;e.observer.shirazSetWindowOrigin(e.left,e.top);e.view.layout(0,0,bounds.width(),bounds.height());e.observer.dispatchOnGlobalLayout();}}

 public static void configurationChanged(android.content.res.Configuration configuration){requireMain();for(Entry e:entries.toArray(new Entry[entries.size()]))if(entries.contains(e)){e.view.dispatchConfigurationChanged(configuration);e.view.requestLayout();}}
 public static boolean prepare(int width,int height){requireMain();if(width<=0||height<=0||width>8192||height>8192||(long)width*height>16*1024*1024)throw new IllegalArgumentException("Dialog viewport");long before=revision;Entry[] snapshot=entries.toArray(new Entry[entries.size()]);boolean proceed=true;for(Entry e:snapshot)if(e.visible&&entries.contains(e)){layout(e,width,height);if(!entries.contains(e))continue;startEntrance(e);if(e.observer.isAlive()&&e.observer.dispatchOnPreDraw()){e.view.invalidate();proceed=false;}}if(before!=revision)proceed=false;prepared=snapshot;return proceed;}
 public static void draw(Canvas canvas,int width,int height){requireMain();for(Entry e:prepared)if(e.visible&&entries.contains(e)){e.observer.dispatchOnDraw();if(!entries.contains(e)||!e.visible)continue;WindowManager.LayoutParams attributes=e.dialog.getWindow().getAttributes();if((attributes.flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND)!=0)canvas.drawColor(Math.round(Math.max(0,Math.min(1,attributes.dimAmount))*e.alpha*255)<<24);int save=canvas.save();try{canvas.translate(e.left,e.top);if(e.scale!=1){canvas.translate(e.view.getWidth()/2f,e.view.getHeight()/2f);canvas.scale(e.scale,e.scale);canvas.translate(-e.view.getWidth()/2f,-e.view.getHeight()/2f);}if(e.alpha<1)canvas.saveLayerAlpha(new android.graphics.RectF(0,0,e.view.getWidth(),e.view.getHeight()),Math.round(e.alpha*255));canvas.clipRect(0,0,e.view.getWidth(),e.view.getHeight());e.view.shirazDrawWithAlpha(canvas);}finally{canvas.restoreToCount(save);}}}
 public static boolean genericMotion(MotionEvent event){requireMain();Entry e=top();if(e==null)return false;MotionEvent local=localEvent(e,event);try{e.view.dispatchGenericMotionEvent(local);}finally{local.recycle();}return true;}
 public static boolean pointer(int action,float x,float y,int source,long time,int width,int height){MotionEvent event=MotionEvent.shirazObtain(action==MotionEvent.ACTION_DOWN?time:down,time,action,x,y,source);try{return pointer(event,width,height);}finally{event.recycle();}}
 public static boolean pointer(MotionEvent incoming,int width,int height){int action=incoming.getActionMasked();long time=incoming.getEventTime();requireMain();if(capture!=null&&capture!=top())abandon(capture);if(action==MotionEvent.ACTION_DOWN){capture=top();pointer=capture!=null;down=time;}boolean consumed=pointer||top()!=null;if(!consumed)return false;Entry e=capture;
  try{if(e!=null&&e.visible&&entries.contains(e)){layout(e,width,height);if(!entries.contains(e)||!e.visible)return true;MotionEvent event=localEvent(e,incoming);try{e.dialog.dispatchTouchEvent(event);}finally{event.recycle();}}}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){capture=null;pointer=false;}}return true;
 }
 public static android.view.PointerIcon pointerIcon(float x,float y){requireMain();Entry e=top();if(e==null||x<e.left||y<e.top||x>=e.left+e.width||y>=e.top+e.height)return null;return e.view.shirazPointerIcon((x-e.left-e.view.getWidth()/2f)/e.scale+e.view.getWidth()/2f,(y-e.top-e.view.getHeight()/2f)/e.scale+e.view.getHeight()/2f);}
 public static void touchMode(View activity,boolean value){requireMain();touchMode=value;if(activity!=null)activity.shirazSetTouchMode(value);for(Entry e:entries.toArray(new Entry[entries.size()]))if(entries.contains(e))e.view.shirazSetTouchMode(value);}
 public static void windowVisibility(View activity,boolean visible){requireMain();int value=visible?View.VISIBLE:View.GONE;if(activity!=null)activity.shirazSetWindowVisibility(value);for(Entry e:entries.toArray(new Entry[entries.size()]))if(entries.contains(e))e.view.shirazSetWindowVisibility(value);}
 public static void windowFocus(View activity,boolean focused){requireMain();Entry selected=top();if(activity!=null)activity.shirazSetWindowFocus(focused&&selected==null&&!android.widget.PopupWindow.shirazFocusable());for(Entry e:entries.toArray(new Entry[entries.size()]))if(entries.contains(e)){boolean next=focused&&e==top()&&!android.widget.PopupWindow.shirazFocusable(),gained=next&&!e.view.hasWindowFocus();e.view.shirazSetWindowFocus(next);if(gained)requestKeyboard(e);}}
 public static boolean hasVisible(){requireMain();return top()!=null;}
 public static void dumpVisible(java.io.PrintStream out){
  requireMain();int n=0;for(Entry e:entries){if(!e.visible)continue;out.println("SHIRAZ_DIALOG_TREE_BEGIN index="+n+" left="+e.left+" top="+e.top+" width="+e.width+" height="+e.height);ViewDiagnostics.dump(e.view,out);out.println("SHIRAZ_DIALOG_TREE_END index="+n);n++;}
 }
 public static boolean key(KeyEvent event){
  requireMain();int code=event.getKeyCode();boolean owned=keys.containsKey(code);Entry e;
  if(event.getAction()==KeyEvent.ACTION_DOWN){e=owned?keys.get(code):top();if(!owned&&e!=null){if(keys.size()>=256)throw new IllegalStateException("Dialog key stream limit");keys.put(code,e);}}
  else if(event.getAction()==KeyEvent.ACTION_UP)e=owned?keys.remove(code):top();else{e=top();owned=false;}
  if(e==null)return owned;if(e!=top()){abandon(e);return true;}if(e.visible&&entries.contains(e)&&!e.dialog.dispatchKeyEvent(event)&&e==top()&&e.visible&&entries.contains(e))e.dialog.getWindow().shirazNavigateKeyEvent(event);return true;
 }
 public static void clear(){requireMain();if(clearing)return;clearing=true;Throwable failure=null;try{for(Entry e:entries.toArray(new Entry[entries.size()]))try{e.dialog.dismiss();}catch(RuntimeException|Error error){if(failure==null)failure=error;}}finally{capture=null;pointer=false;keys.clear();prepared=new Entry[0];touchMode=false;clearing=false;}if(failure instanceof Error)throw (Error)failure;if(failure!=null)throw (RuntimeException)failure;}
}
