// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Rect;
import android.graphics.Insets;

/** Immutable legacy and API 29 inset values. Desktop windows have no Android system bars. */
public final class WindowInsets {
 private final Insets system,stable,decor,gestures,mandatory,tappable;
 private final boolean systemConsumed,stableConsumed,decorConsumed,cutoutConsumed,round,alwaysConsumeNavBar;
 private final DisplayCutout cutout;
 public static final WindowInsets CONSUMED=new WindowInsets(null,null,null,false,false);
 public WindowInsets(Rect system){this(system,null,null,false,false);}
 public WindowInsets(Rect system,Rect decor,Rect stable,boolean round,boolean nav){
  this.system=Insets.of(system);this.decor=Insets.of(decor);this.stable=Insets.of(stable);
  gestures=mandatory=tappable=Insets.NONE;cutout=null;cutoutConsumed=true;
  systemConsumed=system==null;decorConsumed=decor==null;stableConsumed=stable==null;this.round=round;alwaysConsumeNavBar=nav;
 }
 private WindowInsets(Builder b){
  system=b.system;stable=b.stable;decor=b.decor;
  gestures=b.systemConsumed?Insets.NONE:b.gestures;mandatory=b.systemConsumed?Insets.NONE:b.mandatory;tappable=b.systemConsumed?Insets.NONE:b.tappable;
  systemConsumed=b.systemConsumed;stableConsumed=b.stableConsumed;decorConsumed=b.decorConsumed;cutoutConsumed=b.cutoutConsumed;
  cutout=b.cutout;round=b.round;alwaysConsumeNavBar=b.nav;
 }
 public WindowInsets(WindowInsets other){this(new Builder(other));}
 public int getSystemWindowInsetLeft(){return system.left;}public int getSystemWindowInsetTop(){return system.top;}
 public int getSystemWindowInsetRight(){return system.right;}public int getSystemWindowInsetBottom(){return system.bottom;}
 public int getStableInsetLeft(){return stable.left;}public int getStableInsetTop(){return stable.top;}
 public int getStableInsetRight(){return stable.right;}public int getStableInsetBottom(){return stable.bottom;}
 public Insets getSystemWindowInsets(){return system;}public Insets getStableInsets(){return stable;}
 public Insets getSystemGestureInsets(){return gestures;}public Insets getMandatorySystemGestureInsets(){return mandatory;}
 public Insets getTappableElementInsets(){return tappable;}
 public DisplayCutout getDisplayCutout(){return cutout;}
 public boolean hasSystemWindowInsets(){return !system.equals(Insets.NONE);}
 public boolean hasStableInsets(){return !stable.equals(Insets.NONE);}
 public boolean hasInsets(){return hasSystemWindowInsets()||hasStableInsets()||!decor.equals(Insets.NONE)||!gestures.equals(Insets.NONE)||!mandatory.equals(Insets.NONE)||!tappable.equals(Insets.NONE)||cutout!=null;}
 public boolean isConsumed(){return systemConsumed&&stableConsumed&&decorConsumed&&cutoutConsumed;}
 public boolean isRound(){return round;}
 public boolean shouldAlwaysConsumeNavBar(){return alwaysConsumeNavBar;}
 public boolean shouldAlwaysConsumeSystemBars(){return alwaysConsumeNavBar;}
 public WindowInsets consumeSystemWindowInsets(){Builder b=new Builder(this);b.system=Insets.NONE;b.systemConsumed=true;return b.build();}
 public WindowInsets consumeStableInsets(){Builder b=new Builder(this);b.stable=Insets.NONE;b.stableConsumed=true;return b.build();}
 public WindowInsets consumeDisplayCutout(){Builder b=new Builder(this);b.cutout=null;b.cutoutConsumed=true;return b.build();}
 public WindowInsets replaceSystemWindowInsets(int l,int t,int r,int bottom){if(systemConsumed&&android.os.Build.VERSION.SDK_INT>=29)return this;Builder b=new Builder(this);b.system=Insets.of(l,t,r,bottom);return b.build();}
 public WindowInsets replaceSystemWindowInsets(Rect value){if(value==null)throw new NullPointerException("insets");return replaceSystemWindowInsets(value.left,value.top,value.right,value.bottom);}
 private static Insets inset(Insets value,int l,int t,int r,int b){return Insets.of(Math.max(0,value.left-l),Math.max(0,value.top-t),Math.max(0,value.right-r),Math.max(0,value.bottom-b));}
 public WindowInsets inset(int l,int t,int r,int bottom){
  if(l<0||t<0||r<0||bottom<0)throw new IllegalArgumentException("Negative inset");
  Builder b=new Builder(this);b.system=inset(system,l,t,r,bottom);b.stable=inset(stable,l,t,r,bottom);b.decor=inset(decor,l,t,r,bottom);
  b.gestures=inset(gestures,l,t,r,bottom);b.mandatory=inset(mandatory,l,t,r,bottom);b.tappable=inset(tappable,l,t,r,bottom);
  if(cutout!=null)b.cutout=cutout.shirazInset(l,t,r,bottom);return b.build();
 }
 public WindowInsets inset(Insets insets){return inset(insets.left,insets.top,insets.right,insets.bottom);}
 @Override public boolean equals(Object o){if(!(o instanceof WindowInsets))return false;WindowInsets w=(WindowInsets)o;return system.equals(w.system)&&stable.equals(w.stable)&&decor.equals(w.decor)&&gestures.equals(w.gestures)&&mandatory.equals(w.mandatory)&&tappable.equals(w.tappable)&&java.util.Objects.equals(cutout,w.cutout)&&systemConsumed==w.systemConsumed&&stableConsumed==w.stableConsumed&&decorConsumed==w.decorConsumed&&cutoutConsumed==w.cutoutConsumed&&round==w.round&&alwaysConsumeNavBar==w.alwaysConsumeNavBar;}
 @Override public int hashCode(){return java.util.Objects.hash(system,stable,decor,gestures,mandatory,tappable,cutout,systemConsumed,stableConsumed,decorConsumed,cutoutConsumed,round,alwaysConsumeNavBar);}
 public static final class Builder {
  private Insets system=Insets.NONE,stable=Insets.NONE,decor=Insets.NONE,gestures=Insets.NONE,mandatory=Insets.NONE,tappable=Insets.NONE;
  private boolean systemConsumed=true,stableConsumed=true,decorConsumed=true,cutoutConsumed=true,round,nav;
  private DisplayCutout cutout;
  public Builder(){}
  public Builder(WindowInsets w){
   if(w==null)throw new NullPointerException("insets");system=w.system;stable=w.stable;decor=w.decor;gestures=w.gestures;mandatory=w.mandatory;tappable=w.tappable;
   systemConsumed=w.systemConsumed;stableConsumed=w.stableConsumed;decorConsumed=w.decorConsumed;cutoutConsumed=w.cutoutConsumed;round=w.round;nav=w.alwaysConsumeNavBar;cutout=w.cutout;
  }
  public Builder setSystemWindowInsets(Insets value){system=java.util.Objects.requireNonNull(value);systemConsumed=false;return this;}
  public Builder setStableInsets(Insets value){stable=java.util.Objects.requireNonNull(value);stableConsumed=false;return this;}
  public Builder setSystemGestureInsets(Insets value){gestures=java.util.Objects.requireNonNull(value);return this;}
  public Builder setMandatorySystemGestureInsets(Insets value){mandatory=java.util.Objects.requireNonNull(value);return this;}
  public Builder setTappableElementInsets(Insets value){tappable=java.util.Objects.requireNonNull(value);return this;}
  public Builder setDisplayCutout(DisplayCutout value){cutout=value==null||value.isEmpty()?null:value;cutoutConsumed=false;return this;}
  public Builder setRound(boolean value){round=value;return this;}
  public Builder setAlwaysConsumeSystemBars(boolean value){nav=value;return this;}
  public WindowInsets build(){return new WindowInsets(this);}
 }
}
