// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;

/** Widget enabling selection of a number from a bounded range with optional wrap-around and formatter. */
public class NumberPicker extends LinearLayout {
    public interface OnValueChangeListener {
        void onValueChange(NumberPicker picker, int oldVal, int newVal);
    }

    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChange(NumberPicker view, int scrollState);
    }

    public interface Formatter {
        String format(int value);
    }

    private final EditText mInputText;
    private int mMinValue;
    private int mMaxValue;
    private int mValue;
    private OnValueChangeListener mOnValueChangeListener;
    private OnScrollListener mOnScrollListener;
    private Formatter mFormatter;
    private long mLongPressUpdateInterval = 300L;
    private String[] mDisplayedValues;
    private boolean mWrapSelectorWheel = true;
    private boolean mWrapSelectorWheelPreferred = true;
    private int mSelectionDividerHeight = 2;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private float mLastDownOrMoveEventY;

    public NumberPicker(Context context) {
        this(context, null);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010524);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        mInputText = new EditText(context);
        mInputText.setGravity(Gravity.CENTER);
        mInputText.setSingleLine(true);
        addView(mInputText, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        updateInputTextView();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mLastDownOrMoveEventY = event.getY();
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastDownOrMoveEventY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = event.getY() - mLastDownOrMoveEventY;
                float step = 24f * getResources().getDisplayMetrics().density;
                if (Math.abs(dy) >= step) {
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    changeValueByOne(dy < 0);
                    mLastDownOrMoveEventY = event.getY();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    changeValueByOne(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    changeValueByOne(false);
                    return true;
                }
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean dispatchTrackballEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return super.dispatchHoverEvent(event);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mInputText.setEnabled(enabled);
    }

    @Override
    public void scrollBy(int x, int y) {
        if (y < 0) {
            changeValueByOne(true);
        } else if (y > 0) {
            changeValueByOne(false);
        }
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mValue - mMinValue;
    }

    @Override
    protected int computeVerticalScrollRange() {
        return Math.max(1, mMaxValue - mMinValue + 1);
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return 1;
    }

    public int getSolidColor() {
        return 0;
    }

    public void setOnValueChangedListener(OnValueChangeListener onValueChangedListener) {
        mOnValueChangeListener = onValueChangedListener;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

    public void setFormatter(Formatter formatter) {
        if (formatter == mFormatter) {
            return;
        }
        mFormatter = formatter;
        updateInputTextView();
    }

    public void setValue(int value) {
        setValueInternal(value, false);
    }

    private void setValueInternal(int current, boolean notifyChange) {
        if (mValue == current) {
            return;
        }
        if (mWrapSelectorWheel && mMaxValue > mMinValue) {
            current = getWrappedSelectorIndex(current);
        } else {
            current = Math.max(current, mMinValue);
            current = Math.min(current, mMaxValue);
        }
        int previous = mValue;
        mValue = current;
        updateInputTextView();
        if (notifyChange && mOnValueChangeListener != null) {
            mOnValueChangeListener.onValueChange(this, previous, mValue);
        }
        invalidate();
    }

    private void changeValueByOne(boolean increment) {
        setValueInternal(increment ? mValue + 1 : mValue - 1, true);
    }

    private int getWrappedSelectorIndex(int selectorIndex) {
        int range = mMaxValue - mMinValue + 1;
        if (range <= 0) {
            return mMinValue;
        }
        if (selectorIndex > mMaxValue) {
            return mMinValue + (selectorIndex - mMaxValue - 1) % range;
        } else if (selectorIndex < mMinValue) {
            return mMaxValue - (mMinValue - selectorIndex - 1) % range;
        }
        return selectorIndex;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        return super.performLongClick();
    }

    public boolean getWrapSelectorWheel() {
        return mWrapSelectorWheel;
    }

    public void setWrapSelectorWheel(boolean wrapSelectorWheel) {
        mWrapSelectorWheelPreferred = wrapSelectorWheel;
        updateWrapSelectorWheel();
    }

    private void updateWrapSelectorWheel() {
        final boolean wrappingAllowed = (mMaxValue - mMinValue) >= 3;
        mWrapSelectorWheel = wrappingAllowed && mWrapSelectorWheelPreferred;
    }

    public void setOnLongPressUpdateInterval(long intervalMillis) {
        mLongPressUpdateInterval = intervalMillis;
    }

    public int getValue() {
        return mValue;
    }

    public int getMinValue() {
        return mMinValue;
    }

    public void setMinValue(int minValue) {
        if (mMinValue == minValue) {
            return;
        }
        if (minValue < 0) {
            throw new IllegalArgumentException("minValue must be >= 0");
        }
        mMinValue = minValue;
        if (mMinValue > mValue) {
            mValue = mMinValue;
        }
        updateWrapSelectorWheel();
        updateInputTextView();
        requestLayout();
        invalidate();
    }

    public int getMaxValue() {
        return mMaxValue;
    }

    public void setMaxValue(int maxValue) {
        if (mMaxValue == maxValue) {
            return;
        }
        if (maxValue < 0) {
            throw new IllegalArgumentException("maxValue must be >= 0");
        }
        mMaxValue = maxValue;
        if (mMaxValue < mValue) {
            mValue = mMaxValue;
        }
        updateWrapSelectorWheel();
        updateInputTextView();
        requestLayout();
        invalidate();
    }

    public String[] getDisplayedValues() {
        return mDisplayedValues;
    }

    public void setDisplayedValues(String[] displayedValues) {
        if (mDisplayedValues == displayedValues) {
            return;
        }
        mDisplayedValues = displayedValues;
        updateInputTextView();
        requestLayout();
    }

    public void setSelectionDividerHeight(int height) {
        mSelectionDividerHeight = height;
        invalidate();
    }

    public int getSelectionDividerHeight() {
        return mSelectionDividerHeight;
    }

    protected float getTopFadingEdgeStrength() {
        return 0.9f;
    }

    protected float getBottomFadingEdgeStrength() {
        return 0.9f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return super.getAccessibilityNodeProvider();
    }

    public void setTextColor(int color) {
        mInputText.setTextColor(color);
        invalidate();
    }

    public int getTextColor() {
        return mInputText.getCurrentTextColor();
    }

    public void setTextSize(float size) {
        mInputText.setTextSize(0, size);
        requestLayout();
        invalidate();
    }

    public float getTextSize() {
        return mInputText.getTextSize();
    }

    private void onScrollStateChange(int scrollState) {
        if (mScrollState == scrollState) {
            return;
        }
        mScrollState = scrollState;
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, scrollState);
        }
    }

    private String formatNumber(int value) {
        return (mFormatter != null) ? mFormatter.format(value) : Integer.toString(value);
    }

    private void updateInputTextView() {
        int idx = mValue - mMinValue;
        String text = (mDisplayedValues == null || idx < 0 || idx >= mDisplayedValues.length)
                ? formatNumber(mValue)
                : mDisplayedValues[idx];
        if (text != null && !text.equals(mInputText.getText().toString())) {
            mInputText.setText(text);
        }
    }
}
