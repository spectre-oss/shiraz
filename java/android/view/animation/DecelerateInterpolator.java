// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class DecelerateInterpolator extends BaseInterpolator {
 private final float factor;
 public DecelerateInterpolator(){this(1);}
 public DecelerateInterpolator(float value){factor=value;}
 public DecelerateInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public DecelerateInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x010101d3,1));}
 public float getInterpolation(float t){return factor==1?1-(1-t)*(1-t):1-(float)Math.pow(1-t,2*factor);}
 public float getFactor(){return factor;}
}
