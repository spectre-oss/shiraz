// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
/** A checked choice can be cleared programmatically, but not by clicking it again. */
public class RadioButton extends CompoundButton {
 public RadioButton(Context c){this(c,null);}
 public RadioButton(Context c,AttributeSet a){this(c,a,0x0101007e);}
 public RadioButton(Context c,AttributeSet a,int s){this(c,a,s,0);}
 public RadioButton(Context c,AttributeSet a,int s,int r){super(c,a,s,r);}
 @Override public void toggle(){if(!isChecked())super.toggle();}
 @Override public CharSequence getAccessibilityClassName(){return RadioButton.class.getName();}
}
