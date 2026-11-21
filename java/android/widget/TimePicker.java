// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewStructure;
import android.view.autofill.AutofillValue;
import java.util.Calendar;

/** Widget for selecting the time of day, in either 24-hour or AM/PM mode. */
public class TimePicker extends FrameLayout {
    public interface OnTimeChangedListener {
        void onTimeChanged(TimePicker view, int hourOfDay, int minute);
    }

    private int mHour;
    private int mMinute;
    private boolean mIs24Hour;
    private boolean mIsEnabled = true;
    private OnTimeChangedListener mOnTimeChangedListener;
    private final NumberPicker mHourSpinner;
    private final NumberPicker mMinuteSpinner;

    public TimePicker(Context context) {
        this(context, null);
    }

    public TimePicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101049d);
    }

    public TimePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TimePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Calendar cal = Calendar.getInstance();
        mHour = cal.get(Calendar.HOUR_OF_DAY);
        mMinute = cal.get(Calendar.MINUTE);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        mHourSpinner = new NumberPicker(context);
        mHourSpinner.setMinValue(0);
        mHourSpinner.setMaxValue(23);
        mHourSpinner.setValue(mHour);
        mHourSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                mHour = newVal;
                onTimeChanged();
            }
        });

        TextView divider = new TextView(context);
        divider.setText(":");

        mMinuteSpinner = new NumberPicker(context);
        mMinuteSpinner.setMinValue(0);
        mMinuteSpinner.setMaxValue(59);
        mMinuteSpinner.setValue(mMinute);
        mMinuteSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                mMinute = newVal;
                onTimeChanged();
            }
        });

        row.addView(mHourSpinner, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        row.addView(divider, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        row.addView(mMinuteSpinner, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(row, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    public void setHour(int hour) {
        int clamped = Math.max(0, Math.min(23, hour));
        if (mHour != clamped) {
            mHour = clamped;
            mHourSpinner.setValue(mHour);
            onTimeChanged();
        }
    }

    public int getHour() {
        return mHour;
    }

    public void setMinute(int minute) {
        int clamped = Math.max(0, Math.min(59, minute));
        if (mMinute != clamped) {
            mMinute = clamped;
            mMinuteSpinner.setValue(mMinute);
            onTimeChanged();
        }
    }

    public int getMinute() {
        return mMinute;
    }

    @Deprecated
    public void setCurrentHour(Integer currentHour) {
        if (currentHour == null) {
            return;
        }
        setHour(currentHour.intValue());
    }

    @Deprecated
    public Integer getCurrentHour() {
        return Integer.valueOf(getHour());
    }

    @Deprecated
    public void setCurrentMinute(Integer currentMinute) {
        if (currentMinute == null) {
            return;
        }
        setMinute(currentMinute.intValue());
    }

    @Deprecated
    public Integer getCurrentMinute() {
        return Integer.valueOf(getMinute());
    }

    public void setIs24HourView(Boolean is24HourView) {
        if (is24HourView == null) {
            return;
        }
        boolean next = is24HourView.booleanValue();
        if (mIs24Hour != next) {
            mIs24Hour = next;
            requestLayout();
        }
    }

    public boolean is24HourView() {
        return mIs24Hour;
    }

    public void setOnTimeChangedListener(OnTimeChangedListener onTimeChangedListener) {
        mOnTimeChangedListener = onTimeChangedListener;
    }

    private void onTimeChanged() {
        if (mOnTimeChangedListener != null) {
            mOnTimeChangedListener.onTimeChanged(this, getHour(), getMinute());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mIsEnabled = enabled;
        mHourSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    @Override
    public int getBaseline() {
        return mHourSpinner.getBaseline();
    }

    public boolean validateInput() {
        return mHour >= 0 && mHour <= 23 && mMinute >= 0 && mMinute <= 59;
    }

    private static final class SavedState extends BaseSavedState {
        final int hour;
        final int minute;
        final boolean is24Hour;

        SavedState(Parcelable superState, int hour, int minute, boolean is24Hour) {
            super(superState);
            this.hour = hour;
            this.minute = minute;
            this.is24Hour = is24Hour;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return new SavedState(super.onSaveInstanceState(), mHour, mMinute, mIs24Hour);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mIs24Hour = ss.is24Hour;
        setHour(ss.hour);
        setMinute(ss.minute);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TimePicker.class.getName();
    }

    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {
    }

    public void autofill(AutofillValue value) {
        if (!isEnabled() || value == null || !value.isDate()) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());
        setHour(cal.get(Calendar.HOUR_OF_DAY));
        setMinute(cal.get(Calendar.MINUTE));
    }

    public int getAutofillType() {
        return isEnabled() ? 4 : 0;
    }

    public AutofillValue getAutofillValue() {
        if (!isEnabled()) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, mHour);
        cal.set(Calendar.MINUTE, mMinute);
        return AutofillValue.forDate(cal.getTimeInMillis());
    }
}
