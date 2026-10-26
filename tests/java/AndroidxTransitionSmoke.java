// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.widget.LinearLayout;
/** Runs the transition implementation shipped in the selected, unchanged APK. */
public final class AndroidxTransitionSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void layout(LinearLayout root){root.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));root.layout(0,0,100,100);}
 private static void change(LinearLayout root,View child,View sibling,int visibility,long base)throws Exception{
  int oldLeft=sibling.getLeft();
  Class<?> sceneType=Class.forName("androidx.transition.Scene"),transitionType=Class.forName("androidx.transition.Transition");
  Object scene=sceneType.getConstructor(ViewGroup.class).newInstance(root);
  Object transition=Class.forName("androidx.transition.AutoTransition").newInstance();
  Class.forName("androidx.transition.TransitionManager").getMethod("go",sceneType,transitionType).invoke(null,scene,transition);
  child.setVisibility(visibility);layout(root);root.getViewTreeObserver().dispatchOnPreDraw();
  check(sibling.getLeft()==oldLeft,"ChangeBounds must restore captured start geometry");
  for(int i=0;i<17;i++)Choreographer.getInstance().shirazDoFrame((base+i*50)*1000000,null);
  check(child.getVisibility()==visibility&&child.getLayerType()==View.LAYER_TYPE_NONE&&child.getTransitionAlpha()==1,"Fade final state");
  check(sibling.getLeft()==(visibility==View.GONE?0:20)&&!root.isLayoutSuppressed(),"Bounds final state and layout release");
  layout(root);
 }
 public static void main(String[] args)throws Exception{
  android.os.Looper.prepareMainLooper();
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.HORIZONTAL);
  View child=new View(context),sibling=new View(context);root.addView(child,new LinearLayout.LayoutParams(20,20));root.addView(sibling,new LinearLayout.LayoutParams(20,20));layout(root);
  ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(observer);
  try{long base=android.os.SystemClock.uptimeMillis();change(root,child,sibling,View.GONE,base);change(root,child,sibling,View.VISIBLE,base+1000);System.out.println("ANDROIDX_FADE_BOUNDS_TRANSITIONS_OK");}
  finally{root.shirazDetachTree();observer.shirazKill();android.os.Looper.shirazShutdownMain();}
 }
}
