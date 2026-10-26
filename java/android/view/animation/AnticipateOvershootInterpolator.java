// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class AnticipateOvershootInterpolator extends BaseInterpolator {
 private final float tension;
 public AnticipateOvershootInterpolator(){this(2,1.5f);}
 public AnticipateOvershootInterpolator(float tension){this(tension,1.5f);}
 public AnticipateOvershootInterpolator(float tension,float extraTension){this.tension=tension*extraTension;}
 public AnticipateOvershootInterpolator(Context context,AttributeSet attrs){this(context.getResources(),context.getTheme(),attrs);}
 public AnticipateOvershootInterpolator(Resources resources,Resources.Theme theme,AttributeSet attrs){this(InterpolatorAttributes.number(resources,theme,attrs,0x0101026a,2),InterpolatorAttributes.number(resources,theme,attrs,0x0101026b,1.5f));}
 public float getInterpolation(float t){if(t<0.5f){t*=2;return 0.5f*t*t*((tension+1)*t-tension);}t=2*t-2;return 1+0.5f*t*t*((tension+1)*t+tension);}
}
