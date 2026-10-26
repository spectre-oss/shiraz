// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
public final class PopupWindowSmoke {
 private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 private static void frame(Bitmap b,int w,int h){PopupWindow.shirazRevision(w,h);check(PopupWindow.shirazPrepare(),"preDraw");b.eraseColor(0);PopupWindow.shirazDraw(new Canvas(b));}
 private static boolean pointer(int action,int x,int y){return PopupWindow.shirazPointer(action,x,y,0x1002,10,200,200);}
 public static void main(String[] args){Looper.prepareMainLooper();try{
  Context c=new ContextWrapper(null){private final android.content.res.Resources.Theme theme=android.content.res.Resources.getSystem().newTheme();public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}public android.content.res.Resources.Theme getTheme(){return theme;}};
  RadioButton radio=new RadioButton(c);final int[] checks={0};radio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton v,boolean b){checks[0]++;}});radio.performClick();radio.performClick();check(radio.isChecked()&&checks[0]==1,"radio repeat click stays checked");radio.setChecked(false);check(!radio.isChecked()&&checks[0]==2,"radio programmatic clear");
  ListView list=new ListView(c);list.setPadding(2,3,4,5);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.RED){public boolean getPadding(Rect out){out.set(6,7,8,9);return true;}});check(list.getListPaddingLeft()==8&&list.getListPaddingTop()==10&&list.getListPaddingRight()==12&&list.getListPaddingBottom()==14,"selector and content padding combined");list.setSelector((android.graphics.drawable.Drawable)null);check(list.getListPaddingTop()==3,"selector removal resets list inset");list.setCacheColorHint(Color.BLUE);check(list.getCacheColorHint()==Color.BLUE,"cache hint retained");
  FrameLayout root=new FrameLayout(c);View anchor=new View(c);root.addView(anchor,new FrameLayout.LayoutParams(20,20));root.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());root.layout(0,0,200,200);anchor.layout(30,40,50,60);
  final int[] clicks={0},dismisses={0};View content=new View(c);content.setBackgroundColor(Color.RED);content.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clicks[0]++;}});
  PopupWindow popup=new PopupWindow(content,60,50,false);popup.setOutsideTouchable(true);popup.setOnDismissListener(new PopupWindow.OnDismissListener(){public void onDismiss(){dismisses[0]++;}});popup.showAsDropDown(anchor);
  Bitmap bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);frame(bitmap,200,200);check(bitmap.getPixel(31,61)==Color.RED&&bitmap.getPixel(29,61)==0,"anchored pixels");check(!PopupWindow.shirazFocusable(),"nonmodal focus");
  check(pointer(0,40,70)&&pointer(1,40,70)&&clicks[0]==1,"translated click");
  anchor.setTranslationX(20);frame(bitmap,200,200);check(bitmap.getPixel(51,61)==Color.RED&&bitmap.getPixel(31,61)==0,"moving anchor");
  check(!pointer(0,5,5)&&!popup.isShowing()&&dismisses[0]==1,"nonmodal outside passes through");pointer(1,5,5);popup.dismiss();check(dismisses[0]==1&&content.getParent()==null&&!content.isAttachedToWindow(),"idempotent cleanup");
  popup.setFocusable(true);popup.showAsDropDown(anchor);frame(bitmap,200,200);check(PopupWindow.shirazFocusable(),"focusable popup");check(pointer(0,5,5)&&pointer(1,5,5)&&dismisses[0]==2,"outside gesture cannot click through modal");
  popup.showAsDropDown(anchor);frame(bitmap,200,200);check(pointer(0,55,65),"capture");popup.dismiss();check(pointer(1,55,65)&&clicks[0]==1,"dismiss during down consumes up");
  popup.showAsDropDown(anchor);frame(bitmap,200,200);for(int a:new int[]{0,1}){KeyEvent event=new KeyEvent(1,2,a,KeyEvent.KEYCODE_BACK,0);try{check(PopupWindow.shirazKey(event),"back owned");}finally{event.recycle();}}check(!popup.isShowing(),"back dismiss");
  popup.showAsDropDown(anchor);frame(bitmap,80,80);check(bitmap.getPixel(79,79)==Color.RED&&bitmap.getPixel(80,79)==0,"viewport clips");frame(bitmap,200,200);check(bitmap.getPixel(109,109)==Color.RED,"viewport expansion restores size");
  popup.dismiss();
  final int[] configurationWidths={0,0};
  View configured=new View(c){protected void onConfigurationChanged(android.content.res.Configuration config){configurationWidths[0]=config.screenWidthDp;}};
  FrameLayout configuredRoot=new FrameLayout(c);configuredRoot.addView(configured);
  android.content.res.Configuration resized=new android.content.res.Configuration();resized.screenWidthDp=1280;
  configuredRoot.dispatchConfigurationChanged(resized);check(configurationWidths[0]==1280,"configuration dispatched to nested view");
  View configuredPopup=new View(c){protected void onConfigurationChanged(android.content.res.Configuration config){configurationWidths[1]=config.screenWidthDp;}};
  PopupWindow resizePopup=new PopupWindow(configuredPopup,40,40,true);resizePopup.showAtLocation(root,Gravity.TOP,0,0);
  PopupWindow.shirazConfigurationChanged(resized);check(configurationWidths[1]==1280,"popup receives configuration");resizePopup.dismiss();
  shiraz.runtime.HostWindowManager manager=new shiraz.runtime.HostWindowManager(android.content.res.Resources.getSystem());manager.shirazSetRoot(root);
  View managed=new View(c);WindowManager.LayoutParams attributes=new WindowManager.LayoutParams();attributes.width=30;attributes.height=30;
  manager.addView(managed,attributes);PopupWindow.shirazClear();manager.removeViewImmediate(managed);
  check(managed.getParent()==null&&!managed.isAttachedToWindow(),"Owner acknowledges host-dismissed window");
  try{manager.removeViewImmediate(managed);throw new AssertionError("Repeated owner removal accepted");}catch(IllegalArgumentException expected){}
  manager.addView(managed,attributes);manager.removeViewImmediate(managed);
  try{manager.removeViewImmediate(managed);throw new AssertionError("Explicit removal left stale acknowledgement");}catch(IllegalArgumentException expected){}
  final shiraz.runtime.HostWindowManager owner=manager;
  Context base=new ContextWrapper(c){public Object getSystemService(String name){if(WINDOW_SERVICE.equals(name))return owner;return super.getSystemService(name);}};
  android.content.res.Configuration override=new android.content.res.Configuration();override.fontScale=0;override.densityDpi=240;
  Context scoped=new shiraz.runtime.ConfigurationContext(base,override);
  WindowManager scopedManager=(WindowManager)scoped.getSystemService(Context.WINDOW_SERVICE);
  WindowManager nestedManager=(WindowManager)scoped.createConfigurationContext(override).getSystemService(Context.WINDOW_SERVICE);
  android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();scopedManager.getDefaultDisplay().getMetrics(metrics);check(metrics.densityDpi==240,"scoped display metrics retained");
  attributes.token=root.getWindowToken();nestedManager.addView(managed,attributes);frame(bitmap,200,200);
  check(managed.isAttachedToWindow(),"nested configuration popup uses live owner");
  scopedManager.updateViewLayout(managed,attributes);scopedManager.removeViewImmediate(managed);
  check(managed.getParent()==null,"scoped popup removal reaches owner");
  root.shirazDetachTree();
  try{nestedManager.addView(managed,attributes);throw new AssertionError("Detached owner accepted popup");}catch(IllegalStateException expected){}
  root.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());attributes.token=root.getWindowToken();
  nestedManager.addView(managed,attributes);manager.removeViewImmediate(managed);
  popup.showAsDropDown(anchor);root.shirazDetachTree();check(!popup.isShowing()&&content.getParent()==null,"anchor detachment dismisses");bitmap.recycle();System.out.println("POPUP_WINDOW_OK");
 }finally{try{PopupWindow.shirazClear();}finally{Looper.shirazShutdownMain();}}}
}
