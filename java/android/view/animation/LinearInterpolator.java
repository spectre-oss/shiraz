// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class LinearInterpolator extends BaseInterpolator {
 public LinearInterpolator(){}
 public LinearInterpolator(Context context,AttributeSet attrs){}
 public float getInterpolation(float t){return t;}
}
