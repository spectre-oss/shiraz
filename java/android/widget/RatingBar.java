// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** Star-rating SeekBar extension supporting fractional step sizes and indicator mode. */
public class RatingBar extends AbsSeekBar {
    public interface OnRatingBarChangeListener {
        void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser);
    }

    private int mNumStars = 5;
    private OnRatingBarChangeListener mOnRatingBarChangeListener;
    private boolean mIsIndicator;

    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setMax(mNumStars * 2);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            int numStars = a.integer("numStars", mNumStars);
            boolean isIndicator = a.bool("isIndicator", false);
            float rating = a.number("rating", -1f);
            float stepSize = a.number("stepSize", -1f);
            setIsIndicator(isIndicator);
            if (numStars > 0) {
                setNumStars(numStars);
            }
            if (stepSize > 0f) {
                setStepSize(stepSize);
            }
            if (rating >= 0f) {
                setRating(rating);
            }
        }
    }

    public RatingBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101007c);
    }

    public RatingBar(Context context) {
        this(context, null);
    }

    public void setOnRatingBarChangeListener(OnRatingBarChangeListener listener) {
        mOnRatingBarChangeListener = listener;
    }

    public OnRatingBarChangeListener getOnRatingBarChangeListener() {
        return mOnRatingBarChangeListener;
    }

    public void setIsIndicator(boolean isIndicator) {
        mIsIndicator = isIndicator;
        setFocusable(!isIndicator);
    }

    public boolean isIndicator() {
        return mIsIndicator;
    }

    public void setNumStars(int numStars) {
        if (numStars <= 0) {
            return;
        }
        mNumStars = numStars;
        requestLayout();
    }

    public int getNumStars() {
        return mNumStars;
    }

    public void setRating(float rating) {
        int newProgress = Math.round(rating * getProgressPerStar());
        int prevProgress = getProgress();
        setProgress(newProgress);
        if (getProgress() != prevProgress && mOnRatingBarChangeListener != null) {
            mOnRatingBarChangeListener.onRatingChanged(this, getRating(), false);
        }
    }

    public float getRating() {
        return getProgress() / getProgressPerStar();
    }

    public void setStepSize(float stepSize) {
        if (stepSize <= 0) {
            return;
        }
        final float newMax = mNumStars / stepSize;
        final int newProgress = (int) (newMax / getMax() * getProgress());
        setMax((int) newMax);
        setProgress(newProgress);
    }

    public float getStepSize() {
        return (float) getNumStars() / getMax();
    }

    private float getProgressPerStar() {
        if (mNumStars > 0) {
            return 1f * getMax() / mNumStars;
        }
        return 1f;
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int starPx = Math.round(24f * getResources().getDisplayMetrics().density);
        int width = starPx * mNumStars + getPaddingLeft() + getPaddingRight();
        int height = Math.max(getMeasuredHeight(), starPx + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(
                resolveSizeAndState(width, widthMeasureSpec, 0),
                resolveSizeAndState(height, heightMeasureSpec, 0));
    }

    @Override
    public synchronized void setMax(int max) {
        if (max <= 0) {
            return;
        }
        super.setMax(max);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return RatingBar.class.getName();
    }
}
