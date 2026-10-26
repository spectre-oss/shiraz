// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class AccelerateDecelerateInterpolator extends BaseInterpolator {
 public AccelerateDecelerateInterpolator(){}
 public AccelerateDecelerateInterpolator(Context context,AttributeSet attrs){}
 public float getInterpolation(float t){return (float)((1-Math.cos(Math.PI*t))/2);}
}
