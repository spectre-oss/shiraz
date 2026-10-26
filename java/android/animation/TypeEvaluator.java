// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
/** Supplies the value between two animation keyframes at the interpolated fraction. */
public interface TypeEvaluator<T> {
 T evaluate(float fraction,T startValue,T endValue);
}
