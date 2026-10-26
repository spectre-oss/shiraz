// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.animation.LayoutTransition;
import android.view.*;
import android.widget.LinearLayout;
public final class LayoutTransitionFixture {
 public static void check(Context context){LinearLayout root=(LinearLayout)LayoutInflater.from(context).inflate(context.getResources().getIdentifier("layout_transition","layout","org.shiraz.fixture"),null);LayoutTransition t=root.getLayoutTransition();if(t==null||t.getDuration(LayoutTransition.APPEARING)!=300||!t.isTransitionTypeEnabled(LayoutTransition.DISAPPEARING)||t.isTransitionTypeEnabled(LayoutTransition.CHANGING)||t.isRunning())throw new AssertionError("XML default layout transition");root.setLayoutTransition(null);if(root.getLayoutTransition()!=null)throw new AssertionError("Transition removal");System.out.println("LAYOUT_TRANSITION_XML_OK");}
 public static LayoutTransition transition(){LayoutTransition t=new LayoutTransition();t.setAnimateParentHierarchy(false);t.setDuration(100);for(int i=0;i<=4;i++){t.setStartDelay(i,0);t.setInterpolator(i,null);}return t;}
 public static View child(Context c,int color){View view=new View(c);view.setBackgroundColor(color);view.setLayoutParams(new LinearLayout.LayoutParams(20,20));return view;}
 public static void layout(View root){root.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));root.layout(0,0,100,20);}
}
