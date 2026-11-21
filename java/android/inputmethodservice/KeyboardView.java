// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

@Deprecated
public class KeyboardView extends View implements View.OnClickListener {
    public interface OnKeyboardActionListener {
        void onPress(int primaryCode);
        void onRelease(int primaryCode);
        void onKey(int primaryCode, int[] keyCodes);
        void onText(CharSequence text);
        void swipeLeft();
        void swipeRight();
        void swipeDown();
        void swipeUp();
    }

    private Keyboard mKeyboard;
    private OnKeyboardActionListener mKeyboardActionListener;
    private boolean mPreviewEnabled = true;
    private boolean mProximityCorrectionEnabled = true;
    private int mVerticalCorrection;
    private View mPopupParent;
    private int mPopupPreviewX;
    private int mPopupPreviewY;
    private int mCurrentKeyIndex = -1;
    private float mDownX;
    private float mDownY;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mPopupParent = this;
        mPaint.setTextSize(16f);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    protected OnKeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    public void setKeyboard(Keyboard keyboard) {
        mKeyboard = keyboard;
        mCurrentKeyIndex = -1;
        requestLayout();
        invalidateAllKeys();
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public boolean setShifted(boolean shifted) {
        if (mKeyboard != null && mKeyboard.setShifted(shifted)) {
            invalidateAllKeys();
            return true;
        }
        return false;
    }

    public boolean isShifted() {
        return mKeyboard != null && mKeyboard.isShifted();
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        mPreviewEnabled = previewEnabled;
    }

    public boolean isPreviewEnabled() {
        return mPreviewEnabled;
    }

    public void setVerticalCorrection(int verticalOffset) {
        mVerticalCorrection = verticalOffset;
    }

    public void setPopupParent(View v) {
        mPopupParent = v != null ? v : this;
    }

    public void setPopupOffset(int x, int y) {
        mPopupPreviewX = x;
        mPopupPreviewY = y;
    }

    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectionEnabled = enabled;
    }

    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectionEnabled;
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(),
                    getPaddingTop() + getPaddingBottom());
            return;
        }
        int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
        if (MeasureSpec.getSize(widthMeasureSpec) < width + 10
                && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        }
        setMeasuredDimension(width,
                mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mKeyboard == null || canvas == null) return;
        List<Keyboard.Key> keys = mKeyboard.getKeys();
        int padLeft = getPaddingLeft();
        int padTop = getPaddingTop();
        for (int i = 0; i < keys.size(); i++) {
            Keyboard.Key key = keys.get(i);
            float left = padLeft + key.x;
            float top = padTop + key.y;
            float right = left + key.width;
            float bottom = top + key.height;
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(key.pressed ? Color.GRAY : Color.DKGRAY);
            canvas.drawRect(left, top, right, bottom, mPaint);
            if (key.label != null && key.label.length() > 0) {
                String labelStr = key.label.toString();
                if (mKeyboard.isShifted() && labelStr.length() == 1) {
                    labelStr = labelStr.toUpperCase();
                }
                mPaint.setColor(Color.WHITE);
                canvas.drawText(labelStr, (left + right) * 0.5f,
                        (top + bottom) * 0.5f + mPaint.getTextSize() * 0.35f, mPaint);
            }
        }
    }

    public void invalidateAllKeys() {
        invalidate();
    }

    public void invalidateKey(int keyIndex) {
        if (mKeyboard == null) return;
        List<Keyboard.Key> keys = mKeyboard.getKeys();
        if (keyIndex < 0 || keyIndex >= keys.size()) return;
        Keyboard.Key key = keys.get(keyIndex);
        int padLeft = getPaddingLeft();
        int padTop = getPaddingTop();
        invalidate(padLeft + key.x, padTop + key.y,
                padLeft + key.x + key.width, padTop + key.y + key.height);
    }

    protected boolean onLongPress(Keyboard.Key popupKey) {
        return false;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        if (mKeyboard == null || me == null) return false;
        int touchX = (int) me.getX() - getPaddingLeft();
        int touchY = (int) me.getY() - getPaddingTop() + mVerticalCorrection;
        int action = me.getActionMasked();
        List<Keyboard.Key> keys = mKeyboard.getKeys();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = me.getX();
                mDownY = me.getY();
                int idx = findKeyIndex(touchX, touchY);
                mCurrentKeyIndex = idx;
                if (idx >= 0 && idx < keys.size()) {
                    Keyboard.Key key = keys.get(idx);
                    key.onPressed();
                    invalidateKey(idx);
                    if (mKeyboardActionListener != null && key.codes != null && key.codes.length > 0) {
                        mKeyboardActionListener.onPress(key.codes[0]);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = findKeyIndex(touchX, touchY);
                if (idx != mCurrentKeyIndex) {
                    if (mCurrentKeyIndex >= 0 && mCurrentKeyIndex < keys.size()) {
                        Keyboard.Key prev = keys.get(mCurrentKeyIndex);
                        prev.onReleased(false);
                        invalidateKey(mCurrentKeyIndex);
                    }
                    mCurrentKeyIndex = idx;
                    if (idx >= 0 && idx < keys.size()) {
                        keys.get(idx).onPressed();
                        invalidateKey(idx);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int idx = findKeyIndex(touchX, touchY);
                if (mCurrentKeyIndex >= 0 && mCurrentKeyIndex < keys.size()) {
                    Keyboard.Key prev = keys.get(mCurrentKeyIndex);
                    prev.onReleased(idx == mCurrentKeyIndex);
                    invalidateKey(mCurrentKeyIndex);
                }
                if (idx >= 0 && idx < keys.size()) {
                    Keyboard.Key key = keys.get(idx);
                    if (mKeyboardActionListener != null) {
                        if (key.text != null) {
                            mKeyboardActionListener.onText(key.text);
                        } else if (key.codes != null && key.codes.length > 0) {
                            mKeyboardActionListener.onKey(key.codes[0], key.codes);
                        }
                        if (key.codes != null && key.codes.length > 0) {
                            mKeyboardActionListener.onRelease(key.codes[0]);
                        }
                    }
                }
                mCurrentKeyIndex = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                closing();
                return true;
            }
        }
        return super.onTouchEvent(me);
    }

    protected void swipeRight() {
        if (mKeyboardActionListener != null) {
            mKeyboardActionListener.swipeRight();
        }
    }

    protected void swipeLeft() {
        if (mKeyboardActionListener != null) {
            mKeyboardActionListener.swipeLeft();
        }
    }

    protected void swipeUp() {
        if (mKeyboardActionListener != null) {
            mKeyboardActionListener.swipeUp();
        }
    }

    protected void swipeDown() {
        if (mKeyboardActionListener != null) {
            mKeyboardActionListener.swipeDown();
        }
    }

    public void closing() {
        if (mKeyboard != null && mCurrentKeyIndex >= 0
                && mCurrentKeyIndex < mKeyboard.getKeys().size()) {
            mKeyboard.getKeys().get(mCurrentKeyIndex).onReleased(false);
        }
        mCurrentKeyIndex = -1;
        invalidateAllKeys();
    }

    @Override
    public void onDetachedFromWindow() {
        closing();
        super.onDetachedFromWindow();
    }

    public boolean handleBack() {
        return false;
    }

    private int findKeyIndex(int x, int y) {
        if (mKeyboard == null) return -1;
        List<Keyboard.Key> keys = mKeyboard.getKeys();
        int[] nearest = mKeyboard.getNearestKeys(x, y);
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < nearest.length; i++) {
            int kIdx = nearest[i];
            Keyboard.Key key = keys.get(kIdx);
            if (key.isInside(x, y)) {
                return kIdx;
            }
            if (mProximityCorrectionEnabled) {
                int dist = key.squaredDistanceFrom(x, y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = kIdx;
                }
            }
        }
        return bestIdx;
    }
}
