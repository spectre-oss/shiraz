// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;

/** Checked/unchecked button displaying textOn/textOff state labels. */
public class ToggleButton extends CompoundButton {
    private static final int NO_ALPHA = 0xFF;

    private CharSequence mTextOn = "ON";
    private CharSequence mTextOff = "OFF";
    private Drawable mIndicatorDrawable;
    private float mDisabledAlpha = 0.5f;

    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            if (a.has("textOn")) {
                mTextOn = a.text("textOn");
            }
            if (a.has("textOff")) {
                mTextOff = a.text("textOff");
            }
            if (a.has("disabledAlpha")) {
                mDisabledAlpha = a.number("disabledAlpha", 0.5f);
            }
        }
        syncTextState();
    }

    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ToggleButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101004b);
    }

    public ToggleButton(Context context) {
        this(context, null);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        syncTextState();
    }

    private void syncTextState() {
        boolean checked = isChecked();
        if (checked && mTextOn != null) {
            setText(mTextOn);
        } else if (!checked && mTextOff != null) {
            setText(mTextOff);
        }
    }

    public CharSequence getTextOn() {
        return mTextOn;
    }

    public void setTextOn(CharSequence textOn) {
        mTextOn = textOn;
        if (isChecked()) {
            syncTextState();
        }
    }

    public CharSequence getTextOff() {
        return mTextOff;
    }

    public void setTextOff(CharSequence textOff) {
        mTextOff = textOff;
        if (!isChecked()) {
            syncTextState();
        }
    }

    public float getDisabledAlpha() {
        return mDisabledAlpha;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        updateReferenceToIndicatorDrawable(getBackground());
    }

    @Override
    public void setBackgroundDrawable(Drawable d) {
        super.setBackgroundDrawable(d);
        updateReferenceToIndicatorDrawable(d);
    }

    private void updateReferenceToIndicatorDrawable(Drawable backgroundDrawable) {
        if (backgroundDrawable instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) backgroundDrawable;
            mIndicatorDrawable = layerDrawable.findDrawableByLayerId(0x01020017);
        } else {
            mIndicatorDrawable = null;
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mIndicatorDrawable != null) {
            mIndicatorDrawable.setAlpha(
                    isEnabled() ? NO_ALPHA : (int) (NO_ALPHA * mDisabledAlpha));
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ToggleButton.class.getName();
    }
}
