// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
public abstract class AbsSeekBar extends ProgressBar {
 private Drawable thumb;
 private ColorStateList thumbTintList;
 private PorterDuff.Mode thumbTintMode;
 private int thumbOffset,keyProgressIncrement=1;
 private boolean splitTrack;
 public AbsSeekBar(Context context){super(context);}
 public AbsSeekBar(Context context,AttributeSet attrs){super(context,attrs);}
 public AbsSeekBar(Context context,AttributeSet attrs,int defStyleAttr){super(context,attrs,defStyleAttr);}
 public AbsSeekBar(Context context,AttributeSet attrs,int defStyleAttr,int defStyleRes){super(context,attrs,defStyleAttr,defStyleRes);}
 public void setThumb(Drawable thumb){this.thumb=thumb;invalidate();}
 public Drawable getThumb(){return thumb;}
 public void setThumbTintList(ColorStateList tint){thumbTintList=tint;invalidate();}
 public ColorStateList getThumbTintList(){return thumbTintList;}
 public void setThumbTintMode(PorterDuff.Mode tintMode){thumbTintMode=tintMode;invalidate();}
 public PorterDuff.Mode getThumbTintMode(){return thumbTintMode;}
 public int getThumbOffset(){return thumbOffset;}
 public void setThumbOffset(int thumbOffset){this.thumbOffset=thumbOffset;invalidate();}
 public void setSplitTrack(boolean splitTrack){this.splitTrack=splitTrack;invalidate();}
 public boolean getSplitTrack(){return splitTrack;}
 public void setKeyProgressIncrement(int increment){keyProgressIncrement=increment<0?-increment:increment;}
 public int getKeyProgressIncrement(){return keyProgressIncrement;}
}
