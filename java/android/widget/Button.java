// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
public class Button extends TextView {
 public Button(Context context){this(context,null);}
 public Button(Context context,AttributeSet attrs){this(context,attrs,0x01010048);}
 public Button(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public Button(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);}
 @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}
}
