// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
public class SeekBar extends AbsSeekBar {
 public interface OnSeekBarChangeListener {
  void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser);
  void onStartTrackingTouch(SeekBar seekBar);
  void onStopTrackingTouch(SeekBar seekBar);
 }
 private OnSeekBarChangeListener changeListener;
 public SeekBar(Context context){super(context);}
 public SeekBar(Context context,AttributeSet attrs){super(context,attrs);}
 public SeekBar(Context context,AttributeSet attrs,int defStyleAttr){super(context,attrs,defStyleAttr);}
 public SeekBar(Context context,AttributeSet attrs,int defStyleAttr,int defStyleRes){super(context,attrs,defStyleAttr,defStyleRes);}
 public void setOnSeekBarChangeListener(OnSeekBarChangeListener l){changeListener=l;}
 @Override public void setProgress(int progress){
  int before=getProgress();
  super.setProgress(progress);
  int after=getProgress();
  if(after!=before&&changeListener!=null)changeListener.onProgressChanged(this,after,false);
 }
}
