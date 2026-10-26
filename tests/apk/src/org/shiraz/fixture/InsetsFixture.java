// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Rect;
import android.widget.FrameLayout;
public final class InsetsFixture {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void checkValues(android.content.Context context,WindowInsets seed){
        WindowInsets value=seed.replaceSystemWindowInsets(2,3,4,5),copy=new WindowInsets(value);
        check(value.getSystemWindowInsetLeft()==2&&value.getSystemWindowInsetTop()==3&&value.getSystemWindowInsetRight()==4&&value.getSystemWindowInsetBottom()==5,"inset edges");
        Rect edges=new Rect(9,8,7,6);WindowInsets replaced=value.replaceSystemWindowInsets(edges);edges.setEmpty();
        check(replaced.getSystemWindowInsetLeft()==9&&copy.getSystemWindowInsetLeft()==2,"immutable replacements");
        check(value.hasInsets()&&value.hasSystemWindowInsets()&&!seed.hasSystemWindowInsets(),"inset presence");
        check(!value.consumeSystemWindowInsets().hasSystemWindowInsets()&&value.hasSystemWindowInsets(),"immutable consumption");
        final StringBuilder calls=new StringBuilder();FrameLayout parent=new FrameLayout(context);View first=new View(context),second=new View(context);parent.addView(first);parent.addView(second);
        first.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){calls.append('a');return i.replaceSystemWindowInsets(0,0,0,12);}});
        second.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){calls.append('b');check(i.getSystemWindowInsetBottom()==12,"API27 sibling propagation");return i.consumeSystemWindowInsets().consumeStableInsets();}});
        check(parent.dispatchApplyWindowInsets(value).isConsumed()&&calls.toString().equals("ab"),"hierarchy dispatch");calls.setLength(0);
        first.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){calls.append('a');return i.consumeSystemWindowInsets().consumeStableInsets();}});
        parent.dispatchApplyWindowInsets(value);check(calls.toString().equals("a"),"consumed subtree stop");
        View fit=new View(context);fit.setFitsSystemWindows(true);check(!fit.dispatchApplyWindowInsets(value).hasSystemWindowInsets()&&fit.getPaddingLeft()==2&&fit.getPaddingBottom()==5,"fitting padding");
        fit.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){return i;}});fit.dispatchApplyWindowInsets(seed);check(fit.getPaddingBottom()==5,"listener overrides default policy");
        fit.setOnApplyWindowInsetsListener(null);fit.dispatchApplyWindowInsets(seed);check(fit.getPaddingBottom()==0,"default policy restored");
        Rect local=new Rect();check(!fit.computeSystemWindowInsets(value,local).hasSystemWindowInsets()&&local.bottom==5,"computed local insets");
        System.out.println("WINDOW_INSETS_VALUES_OK");
    }
    public static void observe(View root){
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){boolean tested;public WindowInsets onApplyWindowInsets(View v,WindowInsets i){
            check(v.isAttachedToWindow()&&v.getRootWindowInsets()!=null,"root insets before callback");
            check(!i.hasInsets()&&!i.isRound()&&!i.isConsumed(),"desktop client area");
            if(!tested){tested=true;checkValues(v.getContext(),i);}System.out.println("WINDOW_INSETS_DESKTOP_OK");return i;
        }});
    }
}
