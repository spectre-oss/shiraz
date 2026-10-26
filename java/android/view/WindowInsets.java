// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Rect;

/** API 27 inset values. Desktop windows have no Android system bars. */
public final class WindowInsets {
    private final Rect system,stable,decor;
    private final boolean systemConsumed,stableConsumed,decorConsumed,round,alwaysConsumeNavBar;
    public static final WindowInsets CONSUMED=new WindowInsets(null,null,null,false,false);
    public WindowInsets(Rect system){this(system,null,null,false,false);}
    public WindowInsets(Rect system,Rect decor,Rect stable,boolean round,boolean alwaysConsumeNavBar){
        this(system,decor,stable,system==null,decor==null,stable==null,round,alwaysConsumeNavBar);
    }
    private WindowInsets(Rect system,Rect decor,Rect stable,boolean sc,boolean dc,boolean tc,boolean round,boolean nav){
        this.system=new Rect(system);this.decor=new Rect(decor);this.stable=new Rect(stable);
        systemConsumed=sc;decorConsumed=dc;stableConsumed=tc;this.round=round;alwaysConsumeNavBar=nav;
    }
    public WindowInsets(WindowInsets other){this(other.system,other.decor,other.stable,other.systemConsumed,other.decorConsumed,other.stableConsumed,other.round,other.alwaysConsumeNavBar);}
    public int getSystemWindowInsetLeft(){return system.left;}
    public int getSystemWindowInsetTop(){return system.top;}
    public int getSystemWindowInsetRight(){return system.right;}
    public int getSystemWindowInsetBottom(){return system.bottom;}
    public int getStableInsetLeft(){return stable.left;}
    public int getStableInsetTop(){return stable.top;}
    public int getStableInsetRight(){return stable.right;}
    public int getStableInsetBottom(){return stable.bottom;}
    public Rect getSystemWindowInsets(){return new Rect(system);}
    private static boolean nonzero(Rect r){return r.left!=0||r.top!=0||r.right!=0||r.bottom!=0;}
    public boolean hasSystemWindowInsets(){return nonzero(system);}
    public boolean hasStableInsets(){return nonzero(stable);}
    public boolean hasInsets(){return hasSystemWindowInsets()||hasStableInsets()||nonzero(decor);}
    public boolean isConsumed(){return systemConsumed&&stableConsumed&&decorConsumed;}
    public boolean isRound(){return round;}
    public boolean shouldAlwaysConsumeNavBar(){return alwaysConsumeNavBar;}
    public WindowInsets consumeSystemWindowInsets(){return new WindowInsets(null,decor,stable,true,decorConsumed,stableConsumed,round,alwaysConsumeNavBar);}
    public WindowInsets consumeStableInsets(){return new WindowInsets(system,decor,null,systemConsumed,decorConsumed,true,round,alwaysConsumeNavBar);}
    public WindowInsets replaceSystemWindowInsets(int l,int t,int r,int b){return replaceSystemWindowInsets(new Rect(l,t,r,b));}
    public WindowInsets replaceSystemWindowInsets(Rect value){if(value==null)throw new NullPointerException("insets");return new WindowInsets(value,decor,stable,systemConsumed,decorConsumed,stableConsumed,round,alwaysConsumeNavBar);}
}
