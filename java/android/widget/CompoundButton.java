// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.*;
/** Checked-state control with an optional indicator drawable and in-process saved state. */
public abstract class CompoundButton extends Button implements Checkable {
 private static final int[] CHECKED={16842912};
 private boolean checked,broadcasting,hasTint,hasTintMode;
 private Drawable buttonDrawable;
 private ColorStateList tint;
 private PorterDuff.Mode tintMode;
 private OnCheckedChangeListener listener;
 public interface OnCheckedChangeListener {void onCheckedChanged(CompoundButton button,boolean checked);}
 public CompoundButton(Context context){this(context,null);}
 public CompoundButton(Context context,AttributeSet attrs){this(context,attrs,0);}
 public CompoundButton(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public CompoundButton(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);if(attrs!=null||style!=0||resource!=0){shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);if(a.has("button"))setButtonDrawable(a.drawable("button"));android.content.res.TypedArray values=context.obtainStyledAttributes(attrs,new int[]{0x0101046f,0x01010470},style,resource);try{if(values.hasValue(1)){int mode=values.getInt(1,-1);switch(mode){case 3:setButtonTintMode(PorterDuff.Mode.SRC_OVER);break;case 5:setButtonTintMode(PorterDuff.Mode.SRC_IN);break;case 9:setButtonTintMode(PorterDuff.Mode.SRC_ATOP);break;case 14:setButtonTintMode(PorterDuff.Mode.MULTIPLY);break;case 15:setButtonTintMode(PorterDuff.Mode.SCREEN);break;case 16:setButtonTintMode(PorterDuff.Mode.ADD);break;default:throw new UnsupportedOperationException("Button tint mode");}}if(values.hasValue(0))setButtonTintList(values.getColorStateList(0));}finally{values.recycle();}setChecked(a.bool("checked",false));}}
 public boolean isChecked(){return checked;}
 public void setChecked(boolean value){if(checked==value)return;checked=value;refreshDrawableState();invalidate();if(broadcasting)return;broadcasting=true;try{if(listener!=null)listener.onCheckedChanged(this,checked);}finally{broadcasting=false;}}
 public void toggle(){setChecked(!checked);}
 public void setOnCheckedChangeListener(OnCheckedChangeListener value){listener=value;}
 @Override public boolean performClick(){toggle();return super.performClick();}
 public void setButtonDrawable(int id){setButtonDrawable(id==0?null:getContext().getDrawable(id));}
 public void setButtonDrawable(Drawable value){if(buttonDrawable==value)return;if(value!=null){if(hasTintMode)value.setTintMode(tintMode);if(hasTint)value.setTintList(tint);}if(buttonDrawable!=null){buttonDrawable.setCallback(null);unscheduleDrawable(buttonDrawable);}buttonDrawable=value;if(value!=null){value.setCallback(this);value.setLayoutDirection(getLayoutDirection());value.setState(getDrawableState());value.setVisible(getVisibility()==VISIBLE,false);}requestLayout();invalidate();}
 public Drawable getButtonDrawable(){return buttonDrawable;}
 public void setButtonTintList(ColorStateList value){if(buttonDrawable!=null)buttonDrawable.setTintList(value);tint=value;hasTint=true;}
 public ColorStateList getButtonTintList(){return tint;}
 public void setButtonTintMode(PorterDuff.Mode value){if(buttonDrawable!=null)buttonDrawable.setTintMode(value);tintMode=value;hasTintMode=true;}
 public PorterDuff.Mode getButtonTintMode(){return tintMode;}
 private int buttonWidth(){return buttonDrawable==null?0:Math.max(0,buttonDrawable.getIntrinsicWidth());}
 @Override public int getCompoundPaddingLeft(){return super.getCompoundPaddingLeft()+(getLayoutDirection()==LAYOUT_DIRECTION_RTL?0:buttonWidth());}
 @Override public int getCompoundPaddingRight(){return super.getCompoundPaddingRight()+(getLayoutDirection()==LAYOUT_DIRECTION_RTL?buttonWidth():0);}
 @Override protected int getSuggestedMinimumHeight(){return Math.max(super.getSuggestedMinimumHeight(),buttonDrawable==null?0:buttonDrawable.getIntrinsicHeight());}
 @Override protected int[] onCreateDrawableState(int extra){int[] state=super.onCreateDrawableState(extra+1);if(checked)mergeDrawableStates(state,CHECKED);return state;}
 @Override protected void drawableStateChanged(){super.drawableStateChanged();if(buttonDrawable!=null&&buttonDrawable.isStateful())buttonDrawable.setState(getDrawableState());}
 @Override protected boolean verifyDrawable(Drawable who){return who==buttonDrawable||super.verifyDrawable(who);}
 @Override public void invalidateDrawable(Drawable who){if(who==buttonDrawable)requestLayout();super.invalidateDrawable(who);}
 @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();if(buttonDrawable!=null)buttonDrawable.jumpToCurrentState();}
 @Override public void drawableHotspotChanged(float x,float y){super.drawableHotspotChanged(x,y);if(buttonDrawable!=null)buttonDrawable.setHotspot(x,y);}
 @Override public void onRtlPropertiesChanged(int direction){super.onRtlPropertiesChanged(direction);if(buttonDrawable!=null)buttonDrawable.setLayoutDirection(direction);}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(buttonDrawable!=null)buttonDrawable.setVisible(isShown(),false);}
 @Override protected void onDetachedFromWindow(){if(buttonDrawable!=null){unscheduleDrawable(buttonDrawable);buttonDrawable.setVisible(false,false);}super.onDetachedFromWindow();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(buttonDrawable!=null)buttonDrawable.setVisible(isShown(),false);}
 @Override protected void onDraw(Canvas canvas){Drawable d=buttonDrawable;if(d!=null){int w=buttonWidth(),h=Math.max(0,d.getIntrinsicHeight()),top=0;switch(getGravity()&Gravity.VERTICAL_GRAVITY_MASK){case Gravity.BOTTOM:top=getHeight()-h;break;case Gravity.CENTER_VERTICAL:top=(getHeight()-h)/2;break;}int left=getLayoutDirection()==LAYOUT_DIRECTION_RTL?getWidth()-w:0;d.setBounds(left,top,left+w,top+h);if(getBackground()!=null)getBackground().setHotspotBounds(left,top,left+w,top+h);}super.onDraw(canvas);if(d!=null)d.draw(canvas);}
 @Override public CharSequence getAccessibilityClassName(){return CompoundButton.class.getName();}
 @Override protected void shirazInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.shirazInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(checked);}
 @Override protected void shirazInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){super.shirazInitializeAccessibilityEvent(event);event.setChecked(checked);}
 private static final class SavedState extends View.BaseSavedState {final boolean checked;SavedState(Parcelable state,boolean value){super(state);checked=value;}}
 @Override public Parcelable onSaveInstanceState(){return new SavedState(super.onSaveInstanceState(),checked);}
 @Override public void onRestoreInstanceState(Parcelable state){if(!(state instanceof SavedState)){super.onRestoreInstanceState(state);return;}SavedState saved=(SavedState)state;super.onRestoreInstanceState(saved.getSuperState());setChecked(saved.checked);requestLayout();}
}
