// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.view.*;
import android.content.Context;
import java.util.ArrayList;
public final class AttachFixture {
 public static final ArrayList<String> events=new ArrayList<String>();
 public static class Probe extends View {
  private final String name;
  public Probe(Context context,String name){super(context);this.name=name;}
  protected void onAttachedToWindow(){super.onAttachedToWindow();if(!isAttachedToWindow()||getWindowToken()==null)throw new AssertionError("attach state");events.add(name+".attach");}
  protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(!isAttachedToWindow()||getWindowToken()==null)throw new AssertionError("detach callback must retain window state");events.add(name+".detach");}
 }
 public static void observe(final View root){
  root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){boolean attached;
   public void onViewAttachedToWindow(View view){if(attached||!view.isAttachedToWindow()||view.getWindowToken()==null)throw new AssertionError("duplicate/invalid attachment");attached=true;System.out.println("SHIRAZ_ATTACH_LIVE_OK");}
   public void onViewDetachedFromWindow(View view){if(!attached||!view.isAttachedToWindow()||view.getWindowToken()==null)throw new AssertionError("invalid detachment");attached=false;System.out.println("SHIRAZ_DETACH_LIVE_OK");}
  });
 }
}
