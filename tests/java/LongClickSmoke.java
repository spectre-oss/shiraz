// SPDX-License-Identifier: GPL-3.0-only
public final class LongClickSmoke {
 private static void cancellation(android.content.Context context)throws Exception{
  final int[] callbacks={0},longClicks={0},ordinary={0};android.widget.FrameLayout root=new android.widget.FrameLayout(context);
  android.view.View child=new android.view.View(context){public void onCancelPendingInputEvents(){super.onCancelPendingInputEvents();callbacks[0]++;}};
  root.addView(child);child.layout(0,0,40,40);child.setOnLongClickListener(new android.view.View.OnLongClickListener(){public boolean onLongClick(android.view.View v){longClicks[0]++;return true;}});
  android.view.ViewTreeObserver window=android.view.ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(window);
  try{
   android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,0,10,10,0);try{child.dispatchTouchEvent(down);}finally{down.recycle();}
   child.postDelayed(new Runnable(){public void run(){ordinary[0]++;}},1);root.cancelPendingInputEvents();
   Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+30);android.os.Looper.myLooper().shirazDispatchDue(100);
   if(callbacks[0]!=1||longClicks[0]!=0||ordinary[0]!=1)throw new AssertionError("Hierarchy cancellation must remove long press but preserve unrelated work");
   android.view.View broken=new android.view.View(context){public void onCancelPendingInputEvents(){}};
   try{broken.cancelPendingInputEvents();throw new AssertionError("Missing super accepted");}catch(android.util.SuperNotCalledException expected){}
  }finally{root.shirazDetachTree();window.shirazKill();}
 }
 private static void pointerIcons(android.content.Context context){
  android.widget.FrameLayout root=new android.widget.FrameLayout(context);root.layout(0,0,100,100);root.setPointerIcon(android.view.PointerIcon.getSystemIcon(context,1000));android.view.View child=new android.view.View(context);root.addView(child);child.layout(20,20,60,60);child.setPointerIcon(android.view.PointerIcon.getSystemIcon(context,1002));
  if(root.shirazPointerIcon(30,30).getType()!=1002||root.shirazPointerIcon(0,0).getType()!=1000)throw new AssertionError("Pointer child/parent hit resolution");root.scrollTo(10,0);if(root.shirazPointerIcon(15,30).getType()!=1002)throw new AssertionError("Scrolled pointer coordinates");child.setVisibility(android.view.View.GONE);if(root.shirazPointerIcon(15,30).getType()!=1000)throw new AssertionError("Hidden pointer child");child.setVisibility(android.view.View.VISIBLE);child.setPointerIcon(android.view.PointerIcon.getSystemIcon(context,0));if(root.shirazPointerIcon(15,30).getType()!=0)throw new AssertionError("Hidden cursor is distinct from inheritance");
 }
 private static void tooltips(android.content.Context context){
  android.view.View anchor=new android.view.View(context);anchor.layout(20,50,60,80);anchor.setTooltipText("Search");if(!anchor.isLongClickable()||!"Search".contentEquals(anchor.getTooltipText())||anchor.performLongClick())throw new AssertionError("Detached tooltip state");
  android.view.ViewTreeObserver window=android.view.ViewTreeObserver.shirazCreateWindowObserver();anchor.shirazAttachTree(window);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888);
  try{if(!anchor.performLongClick())throw new AssertionError("Tooltip long click not handled");shiraz.runtime.TooltipOverlay.draw(new android.graphics.Canvas(image),100,100);int[] pixels=new int[10000];image.getPixels(pixels,0,100,0,0,100,100);int drawn=0;for(int pixel:pixels)if(pixel!=0)drawn++;if(drawn==0||image.getPixel(99,99)!=0)throw new AssertionError("Anchored tooltip pixels missing/outside bounds");
   anchor.setTooltipText(null);image.eraseColor(0);shiraz.runtime.TooltipOverlay.draw(new android.graphics.Canvas(image),100,100);image.getPixels(pixels,0,100,0,0,100,100);for(int pixel:pixels)if(pixel!=0)throw new AssertionError("Cleared tooltip still drawn");
   anchor.setTooltipText("Again");anchor.setOnLongClickListener(new android.view.View.OnLongClickListener(){public boolean onLongClick(android.view.View v){return true;}});long before=shiraz.runtime.TooltipOverlay.revision();anchor.performLongClick();if(shiraz.runtime.TooltipOverlay.revision()!=before)throw new AssertionError("Tooltip overrides handled app long click");anchor.setOnLongClickListener(null);anchor.performLongClick();anchor.setEnabled(false);shiraz.runtime.TooltipOverlay.revision();image.eraseColor(0);shiraz.runtime.TooltipOverlay.draw(new android.graphics.Canvas(image),100,100);image.getPixels(pixels,0,100,0,0,100,100);for(int pixel:pixels)if(pixel!=0)throw new AssertionError("Disabled tooltip still drawn");
  }finally{anchor.shirazDetachTree();window.shirazKill();shiraz.runtime.TooltipOverlay.clear();image.recycle();}
 }
 public static void main(String[] args)throws Exception{
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  org.shiraz.fixture.LongClickFixture.check(context);
  android.os.Looper.prepareMainLooper();pointerIcons(context);tooltips(context);cancellation(context);android.view.View view=new android.view.View(context);view.layout(0,0,40,40);final int[] fired={0};view.setOnLongClickListener(new android.view.View.OnLongClickListener(){public boolean onLongClick(android.view.View v){fired[0]++;return true;}});android.view.ViewTreeObserver window=android.view.ViewTreeObserver.shirazCreateWindowObserver();view.shirazAttachTree(window);try{android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,0,10,10,0);try{view.dispatchTouchEvent(down);}finally{down.recycle();}android.os.Looper.myLooper().shirazDispatchDue(100);if(fired[0]!=0)throw new AssertionError("Long click fired before timeout");Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+30);android.os.Looper.myLooper().shirazDispatchDue(100);if(fired[0]!=1)throw new AssertionError("Main-loop long click missing");}finally{view.shirazDetachTree();window.shirazKill();}
 }
}
