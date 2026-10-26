// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
/** TextView that implements Checkable for preference and list choice rows. */
public class CheckedTextView extends TextView implements Checkable {
 private static final int[] CHECKED={16842912};
 private boolean checked,hasTint,hasTintMode;
 private Drawable checkMark;
 private ColorStateList tint;
 private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
 private int checkMarkResource;
 public CheckedTextView(Context context){this(context,null);}
 public CheckedTextView(Context context,AttributeSet attrs){this(context,attrs,0x010103c8);}
 public CheckedTextView(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public CheckedTextView(Context context,AttributeSet attrs,int style,int resource){
  super(context,attrs,style,resource);
  if(attrs!=null||style!=0||resource!=0){
   shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);
   if(a.has("checkMark"))setCheckMarkDrawable(a.drawable("checkMark"));
   setChecked(a.bool("checked",false));
   android.content.res.TypedArray values=context.obtainStyledAttributes(attrs,new int[]{0x010104a7,0x010104a8},style,resource);
   try{
    if(values.hasValue(1)){
     int mode=values.getInt(1,-1);
     switch(mode){
      case 3:setCheckMarkTintMode(PorterDuff.Mode.SRC_OVER);break;
      case 5:setCheckMarkTintMode(PorterDuff.Mode.SRC_IN);break;
      case 9:setCheckMarkTintMode(PorterDuff.Mode.SRC_ATOP);break;
      case 14:setCheckMarkTintMode(PorterDuff.Mode.MULTIPLY);break;
      case 15:setCheckMarkTintMode(PorterDuff.Mode.SCREEN);break;
      case 16:setCheckMarkTintMode(PorterDuff.Mode.ADD);break;
      default:throw new UnsupportedOperationException("Check mark tint mode");
     }
    }
    if(values.hasValue(0))setCheckMarkTintList(values.getColorStateList(0));
   }finally{values.recycle();}
  }
 }
 public boolean isChecked(){return checked;}
 public void setChecked(boolean value){if(checked==value)return;checked=value;refreshDrawableState();invalidate();}
 public void toggle(){setChecked(!checked);}
 public void setCheckMarkDrawable(int id){checkMarkResource=id;setCheckMarkDrawable(id==0?null:getContext().getDrawable(id));}
 public void setCheckMarkDrawable(Drawable value){
  if(checkMark==value)return;
  if(value!=null){if(hasTintMode)value.setTintMode(tintMode);if(hasTint)value.setTintList(tint);}
  if(checkMark!=null){checkMark.setCallback(null);unscheduleDrawable(checkMark);}
  checkMark=value;
  if(value!=null){value.setCallback(this);value.setLayoutDirection(getLayoutDirection());value.setState(getDrawableState());value.setVisible(getVisibility()==VISIBLE,false);}
  requestLayout();invalidate();
 }
 public Drawable getCheckMarkDrawable(){return checkMark;}
 public void setCheckMarkTintList(ColorStateList value){tint=value;hasTint=true;if(checkMark!=null)checkMark.setTintList(value);invalidate();}
 public ColorStateList getCheckMarkTintList(){return tint;}
 public void setCheckMarkTintMode(PorterDuff.Mode value){tintMode=value==null?PorterDuff.Mode.SRC_IN:value;hasTintMode=true;if(checkMark!=null)checkMark.setTintMode(tintMode);invalidate();}
 public PorterDuff.Mode getCheckMarkTintMode(){return tintMode;}
 private int markWidth(){return checkMark==null?0:Math.max(0,checkMark.getIntrinsicWidth());}
 @Override public int getCompoundPaddingLeft(){return super.getCompoundPaddingLeft()+(getLayoutDirection()==LAYOUT_DIRECTION_RTL?markWidth():0);}
 @Override public int getCompoundPaddingRight(){return super.getCompoundPaddingRight()+(getLayoutDirection()==LAYOUT_DIRECTION_RTL?0:markWidth());}
 @Override protected int getSuggestedMinimumHeight(){return Math.max(super.getSuggestedMinimumHeight(),checkMark==null?0:checkMark.getIntrinsicHeight());}
 @Override protected int[] onCreateDrawableState(int extra){int[] state=super.onCreateDrawableState(extra+1);if(checked)mergeDrawableStates(state,CHECKED);return state;}
 @Override protected void drawableStateChanged(){super.drawableStateChanged();if(checkMark!=null&&checkMark.isStateful())checkMark.setState(getDrawableState());}
 @Override protected boolean verifyDrawable(Drawable who){return who==checkMark||super.verifyDrawable(who);}
 @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(checkMark!=null)checkMark.jumpToCurrentState();}
 @Override public void drawableHotspotChanged(float x,float y){super.drawableHotspotChanged(x,y);if(checkMark!=null)checkMark.setHotspot(x,y);}
 @Override public void onRtlPropertiesChanged(int direction){super.onRtlPropertiesChanged(direction);if(checkMark!=null)checkMark.setLayoutDirection(direction);}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(checkMark!=null)checkMark.setVisible(isShown(),false);}
 @Override protected void onDetachedFromWindow(){if(checkMark!=null){unscheduleDrawable(checkMark);checkMark.setVisible(false,false);}super.onDetachedFromWindow();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(checkMark!=null)checkMark.setVisible(isShown(),false);}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  Drawable d=checkMark;if(d==null)return;
  int w=markWidth(),h=Math.max(0,d.getIntrinsicHeight()),top=0;
  switch(getGravity()&Gravity.VERTICAL_GRAVITY_MASK){
   case Gravity.BOTTOM:top=getHeight()-h;break;
   case Gravity.CENTER_VERTICAL:top=(getHeight()-h)/2;break;
  }
  int left=getLayoutDirection()==LAYOUT_DIRECTION_RTL?0:getWidth()-w;
  d.setBounds(left,top,left+w,top+h);
  d.draw(canvas);
 }
 @Override public CharSequence getAccessibilityClassName(){return CheckedTextView.class.getName();}
 @Override protected void shirazInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.shirazInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(checked);}
 @Override protected void shirazInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){super.shirazInitializeAccessibilityEvent(event);event.setChecked(checked);}
 private static final class SavedState extends View.BaseSavedState {final boolean checked;SavedState(Parcelable state,boolean value){super(state);checked=value;}}
 @Override public Parcelable onSaveInstanceState(){return new SavedState(super.onSaveInstanceState(),checked);}
 @Override public void onRestoreInstanceState(Parcelable state){if(!(state instanceof SavedState)){super.onRestoreInstanceState(state);return;}SavedState saved=(SavedState)state;super.onRestoreInstanceState(saved.getSuperState());setChecked(saved.checked);requestLayout();}
}
