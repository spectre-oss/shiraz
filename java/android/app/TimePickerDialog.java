// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TimePicker;

public class TimePickerDialog extends AlertDialog
        implements DialogInterface.OnClickListener, TimePicker.OnTimeChangedListener {
    private static final String HOUR = "hour";
    private static final String MINUTE = "minute";
    private static final String IS_24_HOUR = "is24hour";

    public interface OnTimeSetListener {
        void onTimeSet(TimePicker view, int hourOfDay, int minute);
    }

    private final TimePicker mTimePicker;
    private final OnTimeSetListener mTimeSetListener;
    private final int mInitialHourOfDay;
    private final int mInitialMinute;
    private final boolean mIs24HourView;

    public TimePickerDialog(Context context, OnTimeSetListener listener,
            int hourOfDay, int minute, boolean is24HourView) {
        this(context, 0, listener, hourOfDay, minute, is24HourView);
    }

    public TimePickerDialog(Context context, int themeResId, OnTimeSetListener listener,
            int hourOfDay, int minute, boolean is24HourView) {
        super(context, themeResId);
        mTimeSetListener = listener;
        mInitialHourOfDay = hourOfDay;
        mInitialMinute = minute;
        mIs24HourView = is24HourView;

        mTimePicker = new TimePicker(getContext());
        mTimePicker.setIs24HourView(Boolean.valueOf(mIs24HourView));
        mTimePicker.setHour(mInitialHourOfDay);
        mTimePicker.setMinute(mInitialMinute);
        mTimePicker.setOnTimeChangedListener(this);
        setView(mTimePicker);
        setButton(DialogInterface.BUTTON_POSITIVE, "OK", this);
        setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", this);
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
    }

    @Override
    public void show() {
        super.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE && mTimeSetListener != null) {
            mTimeSetListener.onTimeSet(mTimePicker, mTimePicker.getHour(), mTimePicker.getMinute());
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            cancel();
        }
    }

    public void updateTime(int hourOfDay, int minuteOfHour) {
        mTimePicker.setHour(hourOfDay);
        mTimePicker.setMinute(minuteOfHour);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(HOUR, mTimePicker.getHour());
        state.putInt(MINUTE, mTimePicker.getMinute());
        state.putBoolean(IS_24_HOUR, mTimePicker.is24HourView());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int hour = savedInstanceState.getInt(HOUR);
        int minute = savedInstanceState.getInt(MINUTE);
        mTimePicker.setIs24HourView(Boolean.valueOf(savedInstanceState.getBoolean(IS_24_HOUR)));
        mTimePicker.setHour(hour);
        mTimePicker.setMinute(minute);
    }
}
