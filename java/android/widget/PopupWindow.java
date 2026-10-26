// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.*;
import shiraz.runtime.DialogOverlay;
import shiraz.runtime.PopupPlacement;
import java.util.ArrayList;

/** Anchored windows composed inside Shiraz's logical application display.
 * Animation/elevation metadata is retained; animated transitions and shadows are not rendered.
 * No separate system window, virtual keyboard, or parent-scroll negotiation is provided.
 */
public class PopupWindow {
 public interface OnDismissListener {void onDismiss();}
 public static final int INPUT_METHOD_FROM_FOCUSABLE=0,INPUT_METHOD_NEEDED=1,INPUT_METHOD_NOT_NEEDED=2;
 private static final PopupWindow[] EMPTY=new PopupWindow[0];
 private static final ArrayList<PopupWindow> windows=new ArrayList<PopupWindow>();
 private static final java.util.HashMap<Integer,PopupWindow> keys=new java.util.HashMap<Integer,PopupWindow>();
 private static PopupWindow capture;private static boolean pointer,clearing,touchMode;private static long down,revision;
 private static int viewportWidth,viewportHeight;
 private View content,anchor;private FrameLayout decor;private ViewTreeObserver observer;
 private int width=-2,height=-2,xOffset,yOffset,gravity=Gravity.START,inputMode,softInputMode,windowType=1000,animation=-1;
 private boolean focusable,touchable=true,outsideTouchable,clipping=true,overlap,showing,dropdown,above,touchModal=true;
 private Drawable background;private OnDismissListener dismiss;private View.OnTouchListener interceptor;
 private final Rect bounds=new Rect();private float elevation;
 private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){dismiss();}};
 public PopupWindow(){}public PopupWindow(Context c){this(c,null);}public PopupWindow(Context c,AttributeSet a){this(c,a,0);}public PopupWindow(Context c,AttributeSet a,int s){this(c,a,s,0);}
 public PopupWindow(Context c,AttributeSet a,int s,int r){shiraz.runtime.XmlAttributes attrs=new shiraz.runtime.XmlAttributes(c,a,s,r);background=attrs.drawable("popupBackground");overlap=attrs.bool("overlapAnchor",false);}
 public PopupWindow(View v){this(v,-2,-2);}public PopupWindow(int w,int h){setWidth(w);setHeight(h);}public PopupWindow(View v,int w,int h){this(v,w,h,false);}public PopupWindow(View v,int w,int h,boolean f){this(w,h);content=v;focusable=f;}
 public View getContentView(){return content;}public void setContentView(View v){if(!showing)content=v;}
 public int getWidth(){return width;}public void setWidth(int v){if(v< -2||v>8192)throw new IllegalArgumentException("Popup width");width=v;}
 public int getHeight(){return height;}public void setHeight(int v){if(v< -2||v>8192)throw new IllegalArgumentException("Popup height");height=v;}
 public boolean isShowing(){return showing;}public boolean isAboveAnchor(){return above;}
 public boolean isFocusable(){return focusable;}public void setFocusable(boolean v){focusable=v;}
 public boolean isTouchable(){return touchable;}public void setTouchable(boolean v){touchable=v;}
 public boolean isOutsideTouchable(){return outsideTouchable;}public void setOutsideTouchable(boolean v){outsideTouchable=v;}
 public boolean isClippingEnabled(){return clipping;}public void setClippingEnabled(boolean v){clipping=v;}
 public void setIsClippedToScreen(boolean v){clipping=v;}public boolean isClippedToScreen(){return clipping;}public void setClipToScreenEnabled(boolean v){clipping=v;}
 public void setOverlapAnchor(boolean v){overlap=v;}public boolean getOverlapAnchor(){return overlap;}
 public void setEpicenterBounds(Rect bounds){}
 public Rect getEpicenterBounds(){return null;}
 public void setTouchModal(boolean value){touchModal=value;}
 public boolean isTouchModal(){return touchModal;}
 public void setInputMethodMode(int v){if(v<0||v>2)throw new IllegalArgumentException("Input mode");inputMode=v;}public int getInputMethodMode(){return inputMode;}
 public void setSoftInputMode(int v){softInputMode=v;}public int getSoftInputMode(){return softInputMode;}
 public void setWindowLayoutType(int v){windowType=v;}public int getWindowLayoutType(){return windowType;}
 public void setAnimationStyle(int v){animation=v;}public int getAnimationStyle(){return animation;}
 public void setEnterTransition(android.transition.Transition transition){}
 public void setExitTransition(android.transition.Transition transition){}
 public android.transition.Transition getEnterTransition(){return null;}
 public android.transition.Transition getExitTransition(){return null;}
 public void setElevation(float v){if(!Float.isFinite(v)||v<0)throw new IllegalArgumentException("Elevation");elevation=v;}public float getElevation(){return elevation;}
 public void setBackgroundDrawable(Drawable v){background=v;if(decor!=null)decor.setBackground(v);}public Drawable getBackground(){return background;}
 public void setTouchInterceptor(View.OnTouchListener v){interceptor=v;}public void setOnDismissListener(OnDismissListener v){dismiss=v;}
 public void showAsDropDown(View v){showAsDropDown(v,0,0);}public void showAsDropDown(View v,int x,int y){showAsDropDown(v,x,y,Gravity.START);}public void showAsDropDown(View v,int x,int y,int g){show(v,x,y,g,true);}
 public void showAtLocation(View parent,int g,int x,int y){show(parent,x,y,g,false);}
 private void show(View v,int x,int y,int g,boolean drop){
  DialogOverlay.requireMain();if(showing)return;if(clearing)throw new IllegalStateException("Popup during teardown");if(v==null||!v.isAttachedToWindow())throw new IllegalStateException("Popup anchor is detached");if(content==null)throw new IllegalStateException("Popup has no content");if(content.getParent()!=null)throw new IllegalStateException("Popup content already has a parent");if(windows.size()>=32)throw new IllegalStateException("Popup window limit");
  anchor=v;xOffset=x;yOffset=y;gravity=g;dropdown=drop;decor=new FrameLayout(content.getContext());decor.setBackground(background);decor.addView(content,new FrameLayout.LayoutParams(-1,-1));observer=ViewTreeObserver.shirazCreateWindowObserver();decor.shirazAttachTree(observer);decor.shirazSetTouchMode(touchMode);anchor.addOnAttachStateChangeListener(attachment);showing=true;windows.add(this);revision++;
 }
 public void dismiss(){
  DialogOverlay.requireMain();if(!showing)return;showing=false;windows.remove(this);revision++;anchor.removeOnAttachStateChangeListener(attachment);
  try{if(capture==this){capture=null;send(MotionEvent.ACTION_CANCEL,0,0,0x1002,android.os.SystemClock.uptimeMillis());}for(java.util.Map.Entry<Integer,PopupWindow> e:keys.entrySet())if(e.getValue()==this)e.setValue(null);}
  finally{try{decor.shirazSetWindowFocus(false);decor.shirazDetachTree();decor.removeView(content);observer.shirazKill();}finally{decor=null;observer=null;anchor=null;}}
  if(dismiss!=null)dismiss.onDismiss();
 }
 public void update(){if(showing){decor.requestLayout();revision++;}}
 public void update(int w,int h){if(w!=-1)setWidth(w);if(h!=-1)setHeight(h);update();}
 public void update(int x,int y,int w,int h){update(x,y,w,h,false);}public void update(int x,int y,int w,int h,boolean force){xOffset=x;yOffset=y;update(w,h);}
 public void update(View v,int w,int h){update(v,xOffset,yOffset,w,h);}public void update(View v,int x,int y,int w,int h){if(showing&&v!=anchor){if(v==null||!v.isAttachedToWindow())throw new IllegalStateException("Detached anchor");anchor.removeOnAttachStateChangeListener(attachment);anchor=v;anchor.addOnAttachStateChangeListener(attachment);}update(x,y,w,h);}
 public int getMaxAvailableHeight(View v){return getMaxAvailableHeight(v,0);}public int getMaxAvailableHeight(View v,int y){return getMaxAvailableHeight(v,y,false);}public int getMaxAvailableHeight(View v,int y,boolean ignoreBottomDecorations){Rect viewport=new Rect();v.getWindowVisibleDisplayFrame(viewport);int available=PopupPlacement.availableHeight(anchorBounds(v),viewport,y,overlap);Rect padding=new Rect();if(background!=null)background.getPadding(padding);return Math.max(0,available-padding.top-padding.bottom);}
 private static Rect anchorBounds(View v){int[] location=new int[2];v.getLocationOnScreen(location);return new Rect(location[0],location[1],location[0]+v.getWidth(),location[1]+v.getHeight());}
 private static int spec(int size,int available){return View.MeasureSpec.makeMeasureSpec(size>=0?size:available,size==-2?View.MeasureSpec.AT_MOST:View.MeasureSpec.EXACTLY);}
 private void layout(int w,int h){
  if(!showing)return;if(!anchor.isAttachedToWindow()){dismiss();return;}
  boolean measure=decor.isLayoutRequested()||viewportWidth!=w||viewportHeight!=h;
  if(measure)decor.measure(spec(width,w),spec(height,h));int mw=decor.getMeasuredWidth(),mh=decor.getMeasuredHeight();Rect next;
  if(dropdown){PopupPlacement.Result p=PopupPlacement.dropdown(anchorBounds(anchor),new Rect(0,0,w,h),mw,mh,xOffset,yOffset,gravity,anchor.getLayoutDirection(),overlap,clipping);next=p.bounds;above=p.above;}
  else{next=new Rect();Gravity.apply(gravity,mw,mh,new Rect(0,0,w,h),next,anchor.getLayoutDirection());next.offset(xOffset,yOffset);if(clipping){int cw=Math.min(mw,w),ch=Math.min(mh,h);int x=Math.max(0,Math.min(next.left,w-cw)),y=Math.max(0,Math.min(next.top,h-ch));next.set(x,y,x+cw,y+ch);}}
  if(measure||!bounds.equals(next)){bounds.set(next);if(decor.getMeasuredWidth()!=bounds.width()||decor.getMeasuredHeight()!=bounds.height())decor.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(bounds.height(),View.MeasureSpec.EXACTLY));observer.shirazSetWindowOrigin(bounds.left,bounds.top);decor.layout(0,0,bounds.width(),bounds.height());observer.dispatchOnGlobalLayout();revision++;}
 }
 private void send(MotionEvent incoming){MotionEvent e=MotionEvent.obtain(incoming);e.offsetLocation(-bounds.left,-bounds.top);try{if(interceptor==null||!interceptor.onTouch(decor,e))if(decor!=null)decor.dispatchTouchEvent(e);}finally{e.recycle();}}
 private void send(int action,float x,float y,int source,long time){MotionEvent e=MotionEvent.shirazObtain(down,time,action,x-bounds.left,y-bounds.top,source);try{if(interceptor==null||!interceptor.onTouch(decor,e))if(decor!=null)decor.dispatchTouchEvent(e);}finally{e.recycle();}}
 private static PopupWindow[] snapshot(){return windows.isEmpty()?EMPTY:windows.toArray(new PopupWindow[windows.size()]);}
 private static PopupWindow focusWindow(){for(int i=windows.size()-1;i>=0;i--)if(windows.get(i).focusable)return windows.get(i);return null;}
 public static boolean shirazGenericMotion(MotionEvent event){
  for(int i=windows.size()-1;i>=0;i--){PopupWindow p=windows.get(i);if(!p.showing||!p.touchable)continue;
   if(p.bounds.contains((int)event.getX(),(int)event.getY())){MotionEvent local=MotionEvent.obtain(event);try{local.offsetLocation(-p.bounds.left,-p.bounds.top);p.decor.dispatchGenericMotionEvent(local);}finally{local.recycle();}return true;}
   if(p.touchModal&&!(viewportHeight>0&&p.bounds.top>viewportHeight/2&&event.getY()<p.bounds.top))return true;
  }return false;
 }
 public static boolean shirazFocusable(){return focusWindow()!=null;}
 public static boolean shirazCapturesApplicationPointer(){
  for(int i=windows.size()-1;i>=0;i--){
   PopupWindow p=windows.get(i);
   if(!p.showing||!p.focusable)continue;
   if(p.touchModal&&viewportHeight>0&&p.bounds.top>viewportHeight/2)continue;
   return true;
  }
  return false;
 }
 public static long shirazRevision(int w,int h){DialogOverlay.requireMain();if(w<=0||h<=0||w>8192||h>8192||(long)w*h>16*1024*1024)throw new IllegalArgumentException("Popup viewport");for(PopupWindow p:snapshot())if(p.showing){p.layout(w,h);if(p.showing&&p.decor.shirazIsInvalidated())revision++;}viewportWidth=w;viewportHeight=h;return revision;}
 public static boolean shirazPrepare(){long before=revision;boolean proceed=true;for(PopupWindow p:snapshot())if(p.showing&&p.observer.dispatchOnPreDraw()){p.decor.invalidate();proceed=false;}if(before!=revision)proceed=false;return proceed;}
 public static void shirazDraw(Canvas canvas){for(PopupWindow p:snapshot())if(p.showing){p.observer.dispatchOnDraw();if(!p.showing)continue;int save=canvas.save();try{canvas.translate(p.bounds.left,p.bounds.top);canvas.clipRect(0,0,p.bounds.width(),p.bounds.height());p.decor.shirazDrawWithAlpha(canvas);}finally{canvas.restoreToCount(save);}}}
 public static boolean shirazPointer(int action,float x,float y,int source,long time,int w,int h){MotionEvent event=MotionEvent.shirazObtain(action==MotionEvent.ACTION_DOWN?time:down,time,action,x,y,source);try{return shirazPointer(event,w,h);}finally{event.recycle();}}
 public static boolean shirazPointer(MotionEvent incoming,int w,int h){int action=incoming.getActionMasked();float x=incoming.getX(),y=incoming.getY();long time=incoming.getEventTime();
  if(action==MotionEvent.ACTION_DOWN){capture=null;pointer=false;down=time;PopupWindow[] candidates=snapshot();for(int i=candidates.length-1;i>=0;i--){PopupWindow p=candidates[i];if(!p.showing)continue;p.layout(w,h);if(!p.showing||!p.touchable)continue;boolean inside=p.bounds.contains((int)x,(int)y);if(inside){capture=p;pointer=true;if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))){System.err.println("SHIRAZ_POPUP_HIT inside focusable="+p.focusable+" touchModal="+p.touchModal+" bounds="+p.bounds.flattenToString());if("1".equals(System.getenv("SHIRAZ_DUMP_VIEWS"))){System.err.println("SHIRAZ_POPUP_TREE_BEGIN");shiraz.runtime.ViewDiagnostics.dump(p.decor,System.err);System.err.println("SHIRAZ_POPUP_TREE_END");}}break;}if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT")))System.err.println("SHIRAZ_POPUP_OUTSIDE focusable="+p.focusable+" touchModal="+p.touchModal+" outsideTouchable="+p.outsideTouchable+" bounds="+p.bounds.flattenToString());if(p.outsideTouchable||(p.touchModal&&p.focusable))p.dismiss();if(p.touchModal&&p.focusable&&!(p.bounds.top>h/2&&y<p.bounds.top)){pointer=true;break;}}}
  boolean consumed=pointer;try{if(capture!=null&&capture.showing)capture.send(incoming);}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){capture=null;pointer=false;}}return consumed;
 }
 public static boolean shirazKey(KeyEvent event){int code=event.getKeyCode();boolean owned=keys.containsKey(code);if(!owned&&(event.getAction()==KeyEvent.ACTION_UP||event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()>0))return false;PopupWindow p=owned?keys.get(code):focusWindow();if(event.getAction()==KeyEvent.ACTION_DOWN&&!owned&&p!=null){if(keys.size()>=256)throw new IllegalStateException("Popup key limit");keys.put(code,p);}if(event.getAction()==KeyEvent.ACTION_UP)keys.remove(code);if(p==null)return owned;if(!p.showing||p!=focusWindow())return true;if(code==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())p.dismiss();return true;}p.decor.dispatchKeyEvent(event);return true;}
 public static void shirazWindowState(boolean focused,boolean visible,boolean touch){touchMode=touch;PopupWindow selected=focusWindow();for(PopupWindow p:snapshot())if(p.showing){p.decor.shirazSetTouchMode(touch);p.decor.shirazSetWindowVisibility(visible?View.VISIBLE:View.GONE);p.decor.shirazSetWindowFocus(focused&&p==selected);}}
 public static void shirazConfigurationChanged(android.content.res.Configuration configuration){DialogOverlay.requireMain();for(PopupWindow p:snapshot())if(p.showing){p.decor.dispatchConfigurationChanged(configuration);p.decor.requestLayout();}}
 public static void shirazClear(){DialogOverlay.requireMain();if(clearing)return;clearing=true;Throwable failure=null;try{for(PopupWindow p:snapshot())try{p.dismiss();}catch(RuntimeException|Error error){if(failure==null)failure=error;}}finally{capture=null;pointer=false;keys.clear();clearing=false;}if(failure instanceof Error)throw (Error)failure;if(failure!=null)throw (RuntimeException)failure;}
}
