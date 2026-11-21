// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import java.util.Calendar;

/** Calendar date picker widget supporting min/max date bounds and date change callbacks. */
public class CalendarView extends FrameLayout {
    public interface OnDateChangeListener {
        void onSelectedDayChange(CalendarView view, int year, int month, int dayOfMonth);
    }

    private int mShownWeekCount = 6;
    private int mSelectedWeekBackgroundColor;
    private int mFocusedMonthDateColor;
    private int mUnfocusedMonthDateColor;
    private int mWeekNumberColor;
    private int mWeekSeparatorLineColor;
    private Drawable mSelectedDateVerticalBar;
    private int mWeekDayTextAppearanceResId;
    private int mDateTextAppearanceResId;
    private long mMinDate;
    private long mMaxDate;
    private boolean mShowWeekNumber;
    private int mFirstDayOfWeek;
    private long mDate;
    private OnDateChangeListener mOnDateChangeListener;
    private final TextView mHeaderText;

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101035d);
    }

    public CalendarView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CalendarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Calendar cal = Calendar.getInstance();
        mFirstDayOfWeek = cal.getFirstDayOfWeek();
        mDate = cal.getTimeInMillis();
        Calendar min = Calendar.getInstance();
        min.set(1900, Calendar.JANUARY, 1, 0, 0, 0);
        mMinDate = min.getTimeInMillis();
        Calendar max = Calendar.getInstance();
        max.set(2100, Calendar.JANUARY, 1, 0, 0, 0);
        mMaxDate = max.getTimeInMillis();

        mHeaderText = new TextView(context);
        mHeaderText.setGravity(Gravity.CENTER);
        addView(mHeaderText, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        updateHeader();
    }

    private void updateHeader() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate);
        mHeaderText.setText(cal.get(Calendar.YEAR) + "-"
                + (cal.get(Calendar.MONTH) + 1) + "-"
                + cal.get(Calendar.DAY_OF_MONTH));
    }

    @Deprecated
    public void setShownWeekCount(int count) {
        mShownWeekCount = count;
        invalidate();
    }

    @Deprecated
    public int getShownWeekCount() {
        return mShownWeekCount;
    }

    @Deprecated
    public void setSelectedWeekBackgroundColor(int color) {
        mSelectedWeekBackgroundColor = color;
        invalidate();
    }

    @Deprecated
    public int getSelectedWeekBackgroundColor() {
        return mSelectedWeekBackgroundColor;
    }

    @Deprecated
    public void setFocusedMonthDateColor(int color) {
        mFocusedMonthDateColor = color;
        invalidate();
    }

    @Deprecated
    public int getFocusedMonthDateColor() {
        return mFocusedMonthDateColor;
    }

    @Deprecated
    public void setUnfocusedMonthDateColor(int color) {
        mUnfocusedMonthDateColor = color;
        invalidate();
    }

    @Deprecated
    public int getUnfocusedMonthDateColor() {
        return mUnfocusedMonthDateColor;
    }

    @Deprecated
    public void setWeekNumberColor(int color) {
        mWeekNumberColor = color;
        invalidate();
    }

    @Deprecated
    public int getWeekNumberColor() {
        return mWeekNumberColor;
    }

    @Deprecated
    public void setWeekSeparatorLineColor(int color) {
        mWeekSeparatorLineColor = color;
        invalidate();
    }

    @Deprecated
    public int getWeekSeparatorLineColor() {
        return mWeekSeparatorLineColor;
    }

    @Deprecated
    public void setSelectedDateVerticalBar(int resourceId) {
        setSelectedDateVerticalBar(resourceId == 0 ? null : getContext().getDrawable(resourceId));
    }

    @Deprecated
    public void setSelectedDateVerticalBar(Drawable drawable) {
        mSelectedDateVerticalBar = drawable;
        invalidate();
    }

    @Deprecated
    public Drawable getSelectedDateVerticalBar() {
        return mSelectedDateVerticalBar;
    }

    public void setWeekDayTextAppearance(int resourceId) {
        mWeekDayTextAppearanceResId = resourceId;
    }

    public int getWeekDayTextAppearance() {
        return mWeekDayTextAppearanceResId;
    }

    public void setDateTextAppearance(int resourceId) {
        mDateTextAppearanceResId = resourceId;
    }

    public int getDateTextAppearance() {
        return mDateTextAppearanceResId;
    }

    public long getMinDate() {
        return mMinDate;
    }

    public void setMinDate(long minDate) {
        mMinDate = minDate;
        if (mDate < mMinDate) {
            setDate(mMinDate);
        }
    }

    public long getMaxDate() {
        return mMaxDate;
    }

    public void setMaxDate(long maxDate) {
        mMaxDate = maxDate;
        if (mDate > mMaxDate) {
            setDate(mMaxDate);
        }
    }

    @Deprecated
    public void setShowWeekNumber(boolean showWeekNumber) {
        mShowWeekNumber = showWeekNumber;
    }

    @Deprecated
    public boolean getShowWeekNumber() {
        return mShowWeekNumber;
    }

    public int getFirstDayOfWeek() {
        return mFirstDayOfWeek;
    }

    public void setFirstDayOfWeek(int firstDayOfWeek) {
        mFirstDayOfWeek = firstDayOfWeek;
        invalidate();
    }

    public void setOnDateChangeListener(OnDateChangeListener listener) {
        mOnDateChangeListener = listener;
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long date) {
        setDate(date, false, false);
    }

    public void setDate(long date, boolean animate, boolean center) {
        long clamped = Math.max(mMinDate, Math.min(mMaxDate, date));
        if (mDate != clamped) {
            mDate = clamped;
            updateHeader();
            if (mOnDateChangeListener != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(mDate);
                mOnDateChangeListener.onSelectedDayChange(
                        this,
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH));
            }
            invalidate();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return CalendarView.class.getName();
    }
}
