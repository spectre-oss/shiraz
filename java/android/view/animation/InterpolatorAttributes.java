// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.res.*;
import android.util.AttributeSet;
final class InterpolatorAttributes {
 static float number(Resources resources,Resources.Theme theme,AttributeSet attrs,int id,float fallback){
  TypedArray values=theme==null?resources.obtainAttributes(attrs,new int[]{id}):theme.obtainStyledAttributes(attrs,new int[]{id},0,0);
  try{return values.getFloat(0,fallback);}finally{values.recycle();}
 }
}
