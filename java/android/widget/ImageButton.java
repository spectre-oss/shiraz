// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
/** Image control using shared drawable, focus and input behavior. */
public class ImageButton extends ImageView {
 public ImageButton(Context context){this(context,null);}
 public ImageButton(Context context,AttributeSet attrs){this(context,attrs,0x01010072);}
 public ImageButton(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
 public ImageButton(Context context,AttributeSet attrs,int style,int resource){super(context,attrs,style,resource);setFocusable(true);}
 @Override public CharSequence getAccessibilityClassName(){return ImageButton.class.getName();}
}
