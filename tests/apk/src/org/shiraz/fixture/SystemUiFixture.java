// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
public final class SystemUiFixture {
 public final FrameLayout root;private final FrameLayout group;private final View child;
 private int requestedCallbacks,actualCallbacks,actualFlags;private boolean mutate;
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public SystemUiFixture(Context context){
  root=new FrameLayout(context){public void onWindowSystemUiVisibilityChanged(int flags){requestedCallbacks++;if(mutate){mutate=false;setSystemUiVisibility(SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);}}};
  group=new FrameLayout(context);child=new View(context);root.addView(group);group.addView(child);
  child.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener(){public void onSystemUiVisibilityChange(int value){actualCallbacks++;actualFlags=value;}});
 }
 public void checkDetached(){root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);child.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);check(root.getSystemUiVisibility()==256&&child.getSystemUiVisibility()==16&&child.getWindowSystemUiVisibility()==0,"detached local requests");}
 public void checkAttached(Runnable sync){
  sync.run();check(root.getWindowSystemUiVisibility()==272&&child.getWindowSystemUiVisibility()==272&&requestedCallbacks==1&&actualCallbacks==0,"combined window requests are not system visibility events");
  sync.run();check(requestedCallbacks==1,"unchanged request callback");
  group.setVisibility(View.GONE);sync.run();check(child.getWindowSystemUiVisibility()==256,"hidden subtree excluded");
  group.setVisibility(View.VISIBLE);sync.run();check(root.getWindowSystemUiVisibility()==272,"shown subtree restored");
  mutate=true;child.setSystemUiVisibility(4);sync.run();check(root.getWindowSystemUiVisibility()==260&&root.getSystemUiVisibility()==8192,"callback mutation snapshot");sync.run();check(root.getWindowSystemUiVisibility()==8196,"callback mutation next traversal");
  group.removeView(child);sync.run();check(root.getWindowSystemUiVisibility()==8192&&child.getWindowSystemUiVisibility()==0,"detachment recomputes requests");
  group.addView(child);sync.run();root.dispatchSystemUiVisibilityChanged(8192|4|2);check(actualCallbacks==1&&actualFlags==6,"actual visibility listener public mask");
  child.setOnSystemUiVisibilityChangeListener(null);root.dispatchSystemUiVisibilityChanged(1);check(actualCallbacks==1,"listener removal");
  System.out.println("SHIRAZ_FIXTURE_SYSTEM_UI_OK");
 }
}
