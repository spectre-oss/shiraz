// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.Rect;
import android.widget.FrameLayout;
import org.shiraz.fixture.InsetsFixture;
public final class InsetsSmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  android.content.Context context=new android.content.ContextWrapper(null);
  WindowInsets empty=new WindowInsets(new Rect());InsetsFixture.checkValues(context,empty);
  check(!empty.isConsumed()&&empty.consumeSystemWindowInsets().isConsumed(),"empty differs from consumed");
  check(WindowInsets.CONSUMED.replaceSystemWindowInsets(1,2,3,4).isConsumed(),"API27 replacement retains consumed flags");
  WindowInsets stable=new WindowInsets(new Rect(1,2,3,4),null,new Rect(5,6,7,8),true,true);
  check(stable.hasStableInsets()&&stable.isRound()&&stable.shouldAlwaysConsumeNavBar()&&stable.getStableInsetBottom()==8,"stable values");
  check(!stable.consumeSystemWindowInsets().isConsumed()&&stable.consumeSystemWindowInsets().consumeStableInsets().isConsumed(),"independent consumption");
  FrameLayout root=new FrameLayout(context);View child=new View(context);root.addView(child);final int[] count={0};
  child.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){check(v.getRootWindowInsets()==i,"root identity");count[0]++;if(count[0]==1)v.requestApplyInsets();return i;}});
  check(child.getRootWindowInsets()==null,"unattached root insets");
  check(View.class.getDeclaredMethod("getViewRootImpl").invoke(child)==null,"unattached view root");
  check(View.class.getDeclaredField("mAttachInfo").getType().getDeclaredField("mVisibleInsets").getType()==Rect.class,"visible insets field");root.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());
  Object attachedRoot=View.class.getDeclaredMethod("getViewRootImpl").invoke(child);check(attachedRoot!=null,"attached view root");
  java.lang.reflect.Field attach=attachedRoot.getClass().getDeclaredField("mAttachInfo");attach.setAccessible(true);Object info=attach.get(attachedRoot);
  java.lang.reflect.Field visible=info.getClass().getDeclaredField("mVisibleInsets");visible.setAccessible(true);check(visible.get(info) instanceof Rect,"visible insets rect");
  root.shirazApplyWindowInsets(100,200);root.shirazApplyWindowInsets(100,200);root.shirazApplyWindowInsets(100,200);check(count[0]==2,"callback request survives and idle coalesces");
  root.shirazApplyWindowInsets(200,100);check(count[0]==3,"resize redispatch");child.requestApplyInsets();child.requestApplyInsets();root.shirazApplyWindowInsets(200,100);check(count[0]==4,"requests coalesce");
  root.makeOptionalFitsSystemWindows();root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);root.shirazUpdateSystemUiVisibility();
  Rect local=new Rect(9,9,9,9);check(root.computeSystemWindowInsets(stable,local)==stable&&local.equals(new Rect()),"optional layout passes insets");
  root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);root.shirazUpdateSystemUiVisibility();check(!root.computeSystemWindowInsets(stable,local).hasSystemWindowInsets()&&local.bottom==4,"stable flag alone consumes insets");
  root.shirazDetachTree();check(child.getRootWindowInsets()==null,"detached root insets");
  System.out.println("WINDOW_INSETS_LIFECYCLE_OK");
 }
}
