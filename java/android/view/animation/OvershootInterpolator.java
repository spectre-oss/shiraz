// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class OvershootInterpolator extends BaseInterpolator {
 private final float tension;
 public OvershootInterpolator(){this(2);}
 public OvershootInterpolator(float value){tension=value;}
 public OvershootInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public OvershootInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x0101026a,2));}
 public float getInterpolation(float t){t-=1;return 1+t*t*((tension+1)*t+tension);}
}
