// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
public class BounceInterpolator extends BaseInterpolator {
 public BounceInterpolator(){}
 public BounceInterpolator(Context context,AttributeSet attrs){}
 public float getInterpolation(float t){t*=1.1226f;if(t<0.3535f)return bounce(t);if(t<0.7408f)return bounce(t-0.54719f)+0.7f;if(t<0.9644f)return bounce(t-0.8526f)+0.9f;return bounce(t-1.0435f)+0.95f;}
 private static float bounce(float t){return 8*t*t;}
}
