// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class AnticipateInterpolator extends BaseInterpolator {
 private final float tension;
 public AnticipateInterpolator(){this(2);}
 public AnticipateInterpolator(float value){tension=value;}
 public AnticipateInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public AnticipateInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x0101026a,2));}
 public float getInterpolation(float t){return t*t*((tension+1)*t-tension);}
}
