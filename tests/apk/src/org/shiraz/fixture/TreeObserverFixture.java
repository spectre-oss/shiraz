// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
public final class TreeObserverFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void check(Context context){
        FrameLayout parent=new FrameLayout(context);View child=new View(context);final StringBuilder calls=new StringBuilder();final ViewTreeObserver old=child.getViewTreeObserver();old.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){calls.append('L');}});parent.addView(child);ViewTreeObserver tree=parent.getViewTreeObserver();check(child.getRootView()==parent&&child.getViewTreeObserver()==old&&old.isAlive()&&tree!=old,"unattached views retain separate floating observers");old.dispatchOnGlobalLayout();check(calls.toString().equals("L"),"floating layout listener");
        final ViewTreeObserver.OnPreDrawListener later=new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){calls.append('C');return true;}};
        tree.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){calls.append('A');parent.getViewTreeObserver().removeOnPreDrawListener(this);parent.getViewTreeObserver().addOnPreDrawListener(later);return false;}});tree.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){calls.append('B');return true;}});
        check(tree.dispatchOnPreDraw()&&calls.toString().equals("LAB"),"pre-draw veto evaluates all snapshot listeners");check(!tree.dispatchOnPreDraw()&&calls.toString().equals("LABBC"),"pre-draw listener mutations apply on next dispatch");
        final ViewTreeObserver drawTree=tree;final int[] attempts={0};ViewTreeObserver.OnDrawListener drawing=new ViewTreeObserver.OnDrawListener(){public void onDraw(){calls.append('D');try{drawTree.removeOnDrawListener(this);throw new AssertionError("draw-list mutation accepted");}catch(IllegalStateException expected){attempts[0]++;}}};tree.addOnDrawListener(drawing);tree.dispatchOnDraw();check(attempts[0]==1&&calls.toString().endsWith("D"),"draw notification and mutation restriction");tree.removeOnDrawListener(drawing);int length=calls.length();tree.dispatchOnDraw();check(calls.length()==length,"removed draw listener");parent.removeView(child);check(child.getViewTreeObserver()!=tree&&child.getViewTreeObserver().isAlive()&&tree.isAlive(),"detached subtree gets a separate observer");
        System.out.println("SHIRAZ_FIXTURE_TREE_OBSERVER_OK");
    }
    public static final class Live {
        private int layouts,predraws,notifications,paints,vetoes;
        public Live(final View root){ViewTreeObserver tree=root.getViewTreeObserver();tree.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){check(root.getWidth()>0&&root.getHeight()>0,"layout notification after bounds assignment");layouts++;}});tree.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){predraws++;if(vetoes==0){vetoes++;return false;}return true;}});tree.addOnDrawListener(new ViewTreeObserver.OnDrawListener(){public void onDraw(){check(layouts>0&&predraws>=2,"draw notification follows layout and accepted pre-draw");notifications++;}});}
        public void beforeViewPaint(){check(notifications==paints+1,"one observer notification before each actual paint");paints++;}
        public void verify(){check(vetoes==1&&paints>=2&&notifications==paints&&predraws>=paints+1,"live frame cancellation and draw ordering");System.out.println("SHIRAZ_FIXTURE_TREE_OBSERVER_LIVE_OK");}
    }
}
