// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
/** Linear interpolation between two Number values as float. */
public class FloatEvaluator implements TypeEvaluator<Number> {
 @Override public Float evaluate(float fraction,Number startValue,Number endValue){float start=startValue.floatValue();return start+fraction*(endValue.floatValue()-start);}
}
