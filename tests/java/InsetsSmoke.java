// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.Rect;
import android.graphics.Insets;
import android.widget.FrameLayout;
import org.shiraz.fixture.InsetsFixture;
public final class InsetsSmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  android.os.Looper.prepareMainLooper();
  WindowManager.LayoutParams attributes=new WindowManager.LayoutParams();
  check(attributes.layoutInDisplayCutoutMode==WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,"default cutout policy");
  attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  WindowManager.LayoutParams copied=new WindowManager.LayoutParams(attributes);
  check(copied.layoutInDisplayCutoutMode==attributes.layoutInDisplayCutoutMode&&copied.copyFrom(attributes)==0,"copy cutout policy");
  attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
  check(copied.copyFrom(attributes)!=0&&copied.layoutInDisplayCutoutMode==attributes.layoutInDisplayCutoutMode,"update cutout policy");
  Insets distances=Insets.of(1,2,3,4);Rect mutable=distances.toRect();mutable.left=99;
  check(distances.left==1&&Insets.of(new Rect()).equals(Insets.NONE),"immutable inset distances");
  android.os.Parcel parcel=android.os.Parcel.obtain();try{distances.writeToParcel(parcel,0);parcel.setDataPosition(0);check(Insets.CREATOR.createFromParcel(parcel).equals(distances),"insets parcel round trip");}finally{parcel.recycle();}
  WindowInsets.Builder builder=new WindowInsets.Builder();check(builder.build().isConsumed(),"builder starts consumed");
  WindowInsets built=builder.setSystemWindowInsets(distances).setStableInsets(Insets.of(5,6,7,8)).setSystemGestureInsets(Insets.of(3,4,5,6)).setMandatorySystemGestureInsets(Insets.of(1,1,1,1)).setTappableElementInsets(Insets.of(2,2,2,2)).build();
  builder.setSystemWindowInsets(Insets.NONE);check(built.getSystemWindowInsets().equals(distances)&&!built.isConsumed(),"builder snapshot isolation");
  check(built.equals(new WindowInsets.Builder(built).build())&&built.hashCode()==new WindowInsets(built).hashCode(),"copy equality");
  WindowInsets reduced=built.inset(1,3,2,8);
  check(reduced.getSystemWindowInsets().equals(Insets.of(0,0,1,0))&&reduced.getStableInsets().equals(Insets.of(4,3,5,0))&&reduced.getSystemGestureInsets().equals(Insets.of(2,1,3,0)),"inset subtraction and clamp");
  check(built.consumeSystemWindowInsets().getSystemGestureInsets().equals(Insets.NONE)&&built.consumeSystemWindowInsets().getStableInsets().equals(Insets.of(5,6,7,8)),"consume system and gesture values only");
  check(built.consumeSystemWindowInsets().consumeStableInsets().isConsumed(),"builder consumption");
  try{built.inset(-1,0,0,0);throw new AssertionError("negative inset accepted");}catch(IllegalArgumentException expected){}
  Rect topCutout=new Rect(10,0,20,6);DisplayCutout cutout=new DisplayCutout(Insets.of(0,6,0,0),null,topCutout,null,null);topCutout.bottom=99;
  WindowInsets withCutout=new WindowInsets.Builder().setDisplayCutout(cutout).build();
  check(withCutout.hasInsets()&&!withCutout.isConsumed()&&withCutout.getDisplayCutout().getBoundingRectTop().bottom==6,"cutout snapshot");
  Rect exposed=cutout.getBoundingRectTop();exposed.bottom=42;check(cutout.getBoundingRectTop().bottom==6,"cutout getter isolation");
  check(withCutout.inset(1,2,0,0).getDisplayCutout().getSafeInsetTop()==4&&withCutout.consumeDisplayCutout().isConsumed(),"cutout inset and consumption");
  android.content.Context context=new android.content.ContextWrapper(null);
  WindowInsets empty=new WindowInsets(new Rect());InsetsFixture.checkValues(context,empty);
  check(!empty.isConsumed()&&empty.consumeSystemWindowInsets().isConsumed(),"empty differs from consumed");
  WindowInsets replacedConsumed=WindowInsets.CONSUMED.replaceSystemWindowInsets(1,2,3,4);
  check(replacedConsumed.isConsumed()&&replacedConsumed.getSystemWindowInsetLeft()==(android.os.Build.VERSION.SDK_INT>=29?0:1),"consumed replacement follows platform API");
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
