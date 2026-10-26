// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class CycleInterpolator extends BaseInterpolator {
 private final float cycles;
 public CycleInterpolator(){this(1);}
 public CycleInterpolator(float value){cycles=value;}
 public CycleInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public CycleInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x010101d4,1));}
 public float getInterpolation(float t){return (float)Math.sin(2*Math.PI*cycles*t);}
}
