// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Two-state toggle switch widget that can select between two options. */
public class Switch extends CompoundButton {
    private static final int[] CHECKED_STATE_SET = { 0x010100a0 };

    private Drawable mThumbDrawable;
    private ColorStateList mThumbTintList;
    private PorterDuff.Mode mThumbTintMode;
    private BlendMode mThumbBlendMode;
    private boolean mHasThumbTint;
    private boolean mHasThumbTintMode;

    private Drawable mTrackDrawable;
    private ColorStateList mTrackTintList;
    private PorterDuff.Mode mTrackTintMode;
    private BlendMode mTrackBlendMode;
    private boolean mHasTrackTint;
    private boolean mHasTrackTintMode;

    private int mThumbTextPadding;
    private int mSwitchMinWidth;
    private int mSwitchPadding;
    private boolean mSplitTrack;
    private CharSequence mTextOn = "ON";
    private CharSequence mTextOff = "OFF";
    private boolean mShowText;

    private int mSwitchWidth;
    private int mSwitchHeight;
    private int mThumbWidth;
    private int mSwitchLeft;
    private int mSwitchTop;
    private int mSwitchRight;
    private int mSwitchBottom;

    private float mTouchX;
    private float mTouchY;
    private int mTouchMode;
    private final int mTouchSlop;
    private float mThumbPosition;

    private final TextPaint mTextPaint;
    private final Rect mTempRect = new Rect();

    public Switch(Context context) {
        this(context, null);
    }

    public Switch(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101043f);
    }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float density = context.getResources().getDisplayMetrics().density;
        mTextPaint.density = density;
        mTextPaint.setTextSize(12f * density);
        mSwitchMinWidth = Math.round(48f * density);
        mSwitchPadding = Math.round(8f * density);
        mThumbTextPadding = Math.round(6f * density);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mThumbPosition = isChecked() ? 1f : 0f;
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            if (a.has("textOn")) {
                mTextOn = a.text("textOn");
            }
            if (a.has("textOff")) {
                mTextOff = a.text("textOff");
            }
            if (a.has("showText")) {
                mShowText = a.bool("showText", false);
            }
        }
    }

    public void setSwitchTextAppearance(Context context, int resid) {
        requestLayout();
        invalidate();
    }

    public void setSwitchTypeface(Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) {
                tf = Typeface.defaultFromStyle(style);
            } else {
                tf = Typeface.create(tf, style);
            }
            setSwitchTypeface(tf);
            int typefaceStyle = tf != null ? tf.getStyle() : 0;
            int need = style & ~typefaceStyle;
            mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            setSwitchTypeface(tf);
        }
    }

    public void setSwitchTypeface(Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);
            requestLayout();
            invalidate();
        }
    }

    public void setSwitchPadding(int pixels) {
        mSwitchPadding = pixels;
        requestLayout();
    }

    public int getSwitchPadding() {
        return mSwitchPadding;
    }

    public void setSwitchMinWidth(int pixels) {
        mSwitchMinWidth = pixels;
        requestLayout();
    }

    public int getSwitchMinWidth() {
        return mSwitchMinWidth;
    }

    public void setThumbTextPadding(int pixels) {
        mThumbTextPadding = pixels;
        requestLayout();
    }

    public int getThumbTextPadding() {
        return mThumbTextPadding;
    }

    public void setTrackDrawable(Drawable track) {
        if (mTrackDrawable != null) {
            mTrackDrawable.setCallback(null);
        }
        mTrackDrawable = track;
        if (track != null) {
            track.setCallback(this);
            applyTrackTint();
        }
        requestLayout();
    }

    public void setTrackResource(int resId) {
        setTrackDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public Drawable getTrackDrawable() {
        return mTrackDrawable;
    }

    public void setTrackIcon(Icon icon) {
        setTrackDrawable(icon == null ? null : icon.loadDrawable(getContext()));
    }

    public void setTrackTintList(ColorStateList tint) {
        mTrackTintList = tint;
        mHasTrackTint = true;
        applyTrackTint();
    }

    public ColorStateList getTrackTintList() {
        return mTrackTintList;
    }

    public void setTrackTintMode(PorterDuff.Mode tintMode) {
        mTrackTintMode = tintMode;
        mTrackBlendMode = null;
        mHasTrackTintMode = true;
        applyTrackTint();
    }

    public void setTrackTintBlendMode(BlendMode blendMode) {
        mTrackBlendMode = blendMode;
        mTrackTintMode = blendMode != null ? blendMode.shirazPorterDuff() : null;
        mHasTrackTintMode = true;
        applyTrackTint();
    }

    public PorterDuff.Mode getTrackTintMode() {
        return mTrackTintMode;
    }

    public BlendMode getTrackTintBlendMode() {
        return mTrackBlendMode;
    }

    private void applyTrackTint() {
        if (mTrackDrawable != null && (mHasTrackTint || mHasTrackTintMode)) {
            mTrackDrawable = mTrackDrawable.mutate();
            if (mHasTrackTint) {
                mTrackDrawable.setTintList(mTrackTintList);
            }
            if (mHasTrackTintMode) {
                if (mTrackBlendMode != null) {
                    mTrackDrawable.setTintBlendMode(mTrackBlendMode);
                } else if (mTrackTintMode != null) {
                    mTrackDrawable.setTintMode(mTrackTintMode);
                }
            }
            if (mTrackDrawable.isStateful()) {
                mTrackDrawable.setState(getDrawableState());
            }
        }
    }

    public void setThumbDrawable(Drawable thumb) {
        if (mThumbDrawable != null) {
            mThumbDrawable.setCallback(null);
        }
        mThumbDrawable = thumb;
        if (thumb != null) {
            thumb.setCallback(this);
            applyThumbTint();
        }
        requestLayout();
    }

    public void setThumbResource(int resId) {
        setThumbDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public Drawable getThumbDrawable() {
        return mThumbDrawable;
    }

    public void setThumbIcon(Icon icon) {
        setThumbDrawable(icon == null ? null : icon.loadDrawable(getContext()));
    }

    public void setThumbTintList(ColorStateList tint) {
        mThumbTintList = tint;
        mHasThumbTint = true;
        applyThumbTint();
    }

    public ColorStateList getThumbTintList() {
        return mThumbTintList;
    }

    public void setThumbTintMode(PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mThumbBlendMode = null;
        mHasThumbTintMode = true;
        applyThumbTint();
    }

    public void setThumbTintBlendMode(BlendMode blendMode) {
        mThumbBlendMode = blendMode;
        mThumbTintMode = blendMode != null ? blendMode.shirazPorterDuff() : null;
        mHasThumbTintMode = true;
        applyThumbTint();
    }

    public PorterDuff.Mode getThumbTintMode() {
        return mThumbTintMode;
    }

    public BlendMode getThumbTintBlendMode() {
        return mThumbBlendMode;
    }

    private void applyThumbTint() {
        if (mThumbDrawable != null && (mHasThumbTint || mHasThumbTintMode)) {
            mThumbDrawable = mThumbDrawable.mutate();
            if (mHasThumbTint) {
                mThumbDrawable.setTintList(mThumbTintList);
            }
            if (mHasThumbTintMode) {
                if (mThumbBlendMode != null) {
                    mThumbDrawable.setTintBlendMode(mThumbBlendMode);
                } else if (mThumbTintMode != null) {
                    mThumbDrawable.setTintMode(mThumbTintMode);
                }
            }
            if (mThumbDrawable.isStateful()) {
                mThumbDrawable.setState(getDrawableState());
            }
        }
    }

    public void setSplitTrack(boolean splitTrack) {
        mSplitTrack = splitTrack;
        invalidate();
    }

    public boolean getSplitTrack() {
        return mSplitTrack;
    }

    public CharSequence getTextOn() {
        return mTextOn;
    }

    public void setTextOn(CharSequence textOn) {
        mTextOn = textOn;
        requestLayout();
        invalidate();
    }

    public CharSequence getTextOff() {
        return mTextOff;
    }

    public void setTextOff(CharSequence textOff) {
        mTextOff = textOff;
        requestLayout();
        invalidate();
    }

    public void setShowText(boolean showText) {
        if (mShowText != showText) {
            mShowText = showText;
            requestLayout();
            invalidate();
        }
    }

    public boolean getShowText() {
        return mShowText;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defaultThumbW = Math.round(20f * density);
        int defaultSwitchH = Math.round(24f * density);

        int maxTextWidth = 0;
        if (mShowText) {
            if (mTextOn != null) {
                maxTextWidth = Math.max(maxTextWidth,
                        (int) Math.ceil(mTextPaint.measureText(mTextOn, 0, mTextOn.length())));
            }
            if (mTextOff != null) {
                maxTextWidth = Math.max(maxTextWidth,
                        (int) Math.ceil(mTextPaint.measureText(mTextOff, 0, mTextOff.length())));
            }
        }

        int thumbWidth;
        int thumbHeight;
        if (mThumbDrawable != null) {
            mThumbDrawable.getPadding(mTempRect);
            thumbWidth = mThumbDrawable.getIntrinsicWidth() - mTempRect.left - mTempRect.right;
            thumbHeight = mThumbDrawable.getIntrinsicHeight();
        } else {
            thumbWidth = defaultThumbW;
            thumbHeight = defaultSwitchH;
        }
        mThumbWidth = Math.max(mShowText ? maxTextWidth + mThumbTextPadding * 2 : 0, thumbWidth);

        int trackHeight;
        if (mTrackDrawable != null) {
            mTrackDrawable.getPadding(mTempRect);
            trackHeight = mTrackDrawable.getIntrinsicHeight();
        } else {
            mTempRect.setEmpty();
            trackHeight = defaultSwitchH;
        }

        mSwitchWidth = Math.max(mSwitchMinWidth,
                2 * mThumbWidth + mTempRect.left + mTempRect.right);
        mSwitchHeight = Math.max(trackHeight, thumbHeight);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int measuredHeight = getMeasuredHeight();
        if (measuredHeight < mSwitchHeight) {
            setMeasuredDimension(getMeasuredWidthAndState(), mSwitchHeight);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = ev.getX();
                float y = ev.getY();
                if (isEnabled() && x >= mSwitchLeft && x <= mSwitchRight
                        && y >= mSwitchTop && y <= mSwitchBottom) {
                    mTouchMode = 1;
                    mTouchX = x;
                    mTouchY = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mTouchMode == 1) {
                    float x = ev.getX();
                    float y = ev.getY();
                    if (Math.abs(x - mTouchX) > mTouchSlop || Math.abs(y - mTouchY) > mTouchSlop) {
                        mTouchMode = 2;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        mTouchX = x;
                        mTouchY = y;
                        return true;
                    }
                } else if (mTouchMode == 2) {
                    float x = ev.getX();
                    int thumbScrollRange = Math.max(1, mSwitchWidth - mThumbWidth);
                    float dx = x - mTouchX;
                    float dPos = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                            ? -dx / thumbScrollRange : dx / thumbScrollRange;
                    float newPos = Math.max(0f, Math.min(1f, mThumbPosition + dPos));
                    if (newPos != mThumbPosition) {
                        mTouchX = x;
                        mThumbPosition = newPos;
                        invalidate();
                    }
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mTouchMode == 2) {
                    mTouchMode = 0;
                    boolean commit = ev.getActionMasked() == MotionEvent.ACTION_UP && isEnabled();
                    boolean newState = commit ? mThumbPosition > 0.5f : isChecked();
                    setChecked(newState);
                    return true;
                }
                mTouchMode = 0;
                break;
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        mThumbPosition = isChecked() ? 1f : 0f;
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int switchRight;
        int switchLeft;
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            switchLeft = getPaddingLeft();
            switchRight = switchLeft + mSwitchWidth;
        } else {
            switchRight = getWidth() - getPaddingRight();
            switchLeft = switchRight - mSwitchWidth;
        }
        int switchTop;
        int switchBottom;
        switch (getGravity() & Gravity.VERTICAL_GRAVITY_MASK) {
            case Gravity.CENTER_VERTICAL:
                switchTop = (getPaddingTop() + getHeight() - getPaddingBottom() - mSwitchHeight) / 2;
                switchBottom = switchTop + mSwitchHeight;
                break;
            case Gravity.BOTTOM:
                switchBottom = getHeight() - getPaddingBottom();
                switchTop = switchBottom - mSwitchHeight;
                break;
            default:
                switchTop = getPaddingTop();
                switchBottom = switchTop + mSwitchHeight;
                break;
        }
        mSwitchLeft = switchLeft;
        mSwitchTop = switchTop;
        mSwitchBottom = switchBottom;
        mSwitchRight = switchRight;
    }

    @Override
    public void draw(Canvas c) {
        int switchLeft = mSwitchLeft;
        int switchTop = mSwitchTop;
        int switchRight = mSwitchRight;
        int switchBottom = mSwitchBottom;
        int thumbRange = Math.max(0, mSwitchWidth - mThumbWidth);
        float rel = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                ? 1f - mThumbPosition : mThumbPosition;
        int thumbLeft = switchLeft + Math.round(rel * thumbRange);
        int thumbRight = thumbLeft + mThumbWidth;

        if (mTrackDrawable != null) {
            mTrackDrawable.setBounds(switchLeft, switchTop, switchRight, switchBottom);
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.setBounds(thumbLeft, switchTop, thumbRight, switchBottom);
        }
        super.draw(c);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mTrackDrawable != null) {
            mTrackDrawable.draw(canvas);
        } else {
            Paint p = mTextPaint;
            int oldColor = p.getColor();
            p.setColor(isChecked() ? 0xff4caf50 : 0xff9e9e9e);
            canvas.drawRect(mSwitchLeft, mSwitchTop, mSwitchRight, mSwitchBottom, p);
            p.setColor(oldColor);
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.draw(canvas);
        } else {
            int thumbRange = Math.max(0, mSwitchWidth - mThumbWidth);
            float rel = getLayoutDirection() == LAYOUT_DIRECTION_RTL
                    ? 1f - mThumbPosition : mThumbPosition;
            int thumbLeft = mSwitchLeft + Math.round(rel * thumbRange);
            Paint p = mTextPaint;
            int oldColor = p.getColor();
            p.setColor(0xffffffff);
            canvas.drawRect(thumbLeft + 2, mSwitchTop + 2,
                    thumbLeft + mThumbWidth - 2, mSwitchBottom - 2, p);
            p.setColor(oldColor);
        }
    }

    @Override
    public int getCompoundPaddingLeft() {
        if (getLayoutDirection() != LAYOUT_DIRECTION_RTL) {
            return super.getCompoundPaddingLeft();
        }
        int padding = super.getCompoundPaddingLeft() + mSwitchWidth;
        if (getText() != null && getText().length() > 0) {
            padding += mSwitchPadding;
        }
        return padding;
    }

    @Override
    public int getCompoundPaddingRight() {
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            return super.getCompoundPaddingRight();
        }
        int padding = super.getCompoundPaddingRight() + mSwitchWidth;
        if (getText() != null && getText().length() > 0) {
            padding += mSwitchPadding;
        }
        return padding;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final int[] state = getDrawableState();
        boolean changed = false;
        if (mThumbDrawable != null && mThumbDrawable.isStateful()) {
            changed |= mThumbDrawable.setState(state);
        }
        if (mTrackDrawable != null && mTrackDrawable.isStateful()) {
            changed |= mTrackDrawable.setState(state);
        }
        if (changed) {
            invalidate();
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mThumbDrawable != null) {
            mThumbDrawable.setHotspot(x, y);
        }
        if (mTrackDrawable != null) {
            mTrackDrawable.setHotspot(x, y);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mThumbDrawable || who == mTrackDrawable;
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mThumbDrawable != null) {
            mThumbDrawable.jumpToCurrentState();
        }
        if (mTrackDrawable != null) {
            mTrackDrawable.jumpToCurrentState();
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }
}
