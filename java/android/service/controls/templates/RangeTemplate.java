// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

public final class RangeTemplate extends ControlTemplate {
    private final float mMinValue;
    private final float mMaxValue;
    private final float mCurrentValue;
    private final float mStepValue;
    private final CharSequence mFormatString;

    public RangeTemplate(String templateId, float minValue, float maxValue, float currentValue,
            float stepValue, CharSequence formatString) {
        super(templateId);
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue must not exceed maxValue");
        }
        if (stepValue <= 0.0f) {
            throw new IllegalArgumentException("stepValue must be positive");
        }
        mMinValue = minValue;
        mMaxValue = maxValue;
        mCurrentValue = currentValue;
        mStepValue = stepValue;
        mFormatString = formatString != null ? formatString : "%.1f";
    }

    public float getMinValue() { return mMinValue; }
    public float getMaxValue() { return mMaxValue; }
    public float getCurrentValue() { return mCurrentValue; }
    public float getStepValue() { return mStepValue; }
    public CharSequence getFormatString() { return mFormatString; }

    @Override
    public int getTemplateType() {
        return TYPE_RANGE;
    }
}
