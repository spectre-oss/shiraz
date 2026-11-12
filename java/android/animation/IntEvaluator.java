// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
/** Linear interpolation between two Integer values, truncated toward zero. */
public class IntEvaluator implements TypeEvaluator<Integer> {
 @Override public Integer evaluate(float fraction,Integer startValue,Integer endValue){int start=startValue;return (int)(start+fraction*(endValue-start));}
}
