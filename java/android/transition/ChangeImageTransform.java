// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/** Captures and animates {@link ImageView} draw matrices across scenes. */
public class ChangeImageTransform extends Transition {
    private static final String PROPNAME_MATRIX = "android:changeImageTransform:matrix";
    private static final String PROPNAME_BOUNDS = "android:changeImageTransform:bounds";
    private static final String[] TRANSITION_PROPS = {
        PROPNAME_MATRIX,
        PROPNAME_BOUNDS,
    };

    public ChangeImageTransform() {}

    public ChangeImageTransform(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void captureMatrixValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        if (!(view instanceof ImageView) || view.getVisibility() != View.VISIBLE) {
            return;
        }
        ImageView imageView = (ImageView) view;
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) {
            return;
        }
        int left = view.getLeft();
        int top = view.getTop();
        int right = view.getRight();
        int bottom = view.getBottom();
        transitionValues.values.put(PROPNAME_BOUNDS, new Rect(left, top, right, bottom));
        Matrix matrix = copyImageMatrix(imageView);
        transitionValues.values.put(PROPNAME_MATRIX, matrix);
    }

    private static Matrix copyImageMatrix(ImageView view) {
        Drawable image = view.getDrawable();
        if (image != null && image.getIntrinsicWidth() > 0 && image.getIntrinsicHeight() > 0) {
            if (view.getScaleType() == ImageView.ScaleType.FIT_XY) {
                Matrix matrix = new Matrix();
                matrix.setScale(
                        ((float) view.getWidth()) / image.getIntrinsicWidth(),
                        ((float) view.getHeight()) / image.getIntrinsicHeight());
                return matrix;
            }
        }
        Matrix existing = view.getImageMatrix();
        return existing != null ? new Matrix(existing) : new Matrix();
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureMatrixValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureMatrixValues(transitionValues);
    }

    @Override
    public String[] getTransitionProperties() {
        return TRANSITION_PROPS.clone();
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }
        Rect startBounds = (Rect) startValues.values.get(PROPNAME_BOUNDS);
        Rect endBounds = (Rect) endValues.values.get(PROPNAME_BOUNDS);
        if (startBounds == null || endBounds == null) {
            return null;
        }
        Matrix startMatrix = (Matrix) startValues.values.get(PROPNAME_MATRIX);
        Matrix endMatrix = (Matrix) endValues.values.get(PROPNAME_MATRIX);
        if (startMatrix == null) {
            startMatrix = new Matrix();
        }
        if (endMatrix == null) {
            endMatrix = new Matrix();
        }
        if (startMatrix.equals(endMatrix)) {
            return null;
        }
        final ImageView imageView = (ImageView) endValues.view;
        final ImageView.ScaleType originalScaleType = imageView.getScaleType();
        final float[] startVals = new float[9];
        final float[] endVals = new float[9];
        final float[] curVals = new float[9];
        startMatrix.getValues(startVals);
        endMatrix.getValues(endVals);
        final Matrix finalEndMatrix = new Matrix(endMatrix);

        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setImageMatrix(startMatrix);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float f = ((Float) animation.getAnimatedValue()).floatValue();
                        for (int i = 0; i < 9; i++) {
                            curVals[i] = startVals[i] + (endVals[i] - startVals[i]) * f;
                        }
                        Matrix m = new Matrix();
                        m.setValues(curVals);
                        imageView.setImageMatrix(m);
                    }
                });
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageView.setScaleType(originalScaleType);
                        if (originalScaleType == ImageView.ScaleType.MATRIX) {
                            imageView.setImageMatrix(finalEndMatrix);
                        }
                    }
                });
        return anim;
    }
}
