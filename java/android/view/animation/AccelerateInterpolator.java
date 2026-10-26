// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class AccelerateInterpolator extends BaseInterpolator {
 private final float factor;
 public AccelerateInterpolator(){this(1);}
 public AccelerateInterpolator(float value){factor=value;}
 public AccelerateInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public AccelerateInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x010101d3,1));}
 public float getInterpolation(float t){return factor==1?t*t:(float)Math.pow(t,2*factor);}
 public float getFactor(){return factor;}
}
