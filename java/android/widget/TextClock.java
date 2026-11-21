// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import java.util.Calendar;
import java.util.TimeZone;

/** TextView displaying the current date and/or time formatted in 12-hour or 24-hour mode. */
@RemoteViews.RemoteView
public class TextClock extends TextView {
    @Deprecated
    public static final CharSequence DEFAULT_FORMAT_12_HOUR = "h:mm a";
    @Deprecated
    public static final CharSequence DEFAULT_FORMAT_24_HOUR = "H:mm";

    private CharSequence mFormat12;
    private CharSequence mFormat24;
    private CharSequence mFormat;
    private boolean mHasSeconds;
    private boolean mRegistered;
    private boolean mShouldRunTicker;
    private Calendar mTime;
    private String mTimeZone;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            removeCallbacks(mTicker);
            if (!mShouldRunTicker) {
                return;
            }
            onTimeChanged();
            long now = SystemClock.uptimeMillis();
            long next = now + (1000 - now % 1000);
            android.os.Handler h = getHandler();
            if (h != null) {
                h.postAtTime(mTicker, next);
            }
        }
    };

    public TextClock(Context context) {
        super(context);
        init();
    }

    public TextClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TextClock(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            if (a.has("format12Hour")) {
                mFormat12 = a.text("format12Hour");
            }
            if (a.has("format24Hour")) {
                mFormat24 = a.text("format24Hour");
            }
            if (a.has("timeZone")) {
                mTimeZone = a.text("timeZone");
            }
        }
        init();
    }

    private void init() {
        if (mFormat12 == null) {
            mFormat12 = DEFAULT_FORMAT_12_HOUR;
        }
        if (mFormat24 == null) {
            mFormat24 = DEFAULT_FORMAT_24_HOUR;
        }
        createTime(mTimeZone);
        chooseFormat();
        onTimeChanged();
    }

    private void createTime(String timeZone) {
        if (timeZone != null) {
            mTime = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        } else {
            mTime = Calendar.getInstance();
        }
    }

    public CharSequence getFormat12Hour() {
        return mFormat12;
    }

    public void setFormat12Hour(CharSequence format) {
        mFormat12 = format;
        chooseFormat();
        onTimeChanged();
    }

    public CharSequence getFormat24Hour() {
        return mFormat24;
    }

    public void setFormat24Hour(CharSequence format) {
        mFormat24 = format;
        chooseFormat();
        onTimeChanged();
    }

    public void refreshTime() {
        onTimeChanged();
        invalidate();
    }

    public boolean is24HourModeEnabled() {
        return DateFormat.is24HourFormat(getContext());
    }

    public String getTimeZone() {
        return mTimeZone;
    }

    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone;
        createTime(timeZone);
        onTimeChanged();
    }

    private void chooseFormat() {
        final boolean format24Requested = is24HourModeEnabled();
        if (format24Requested) {
            mFormat = abc(mFormat24, mFormat12, DEFAULT_FORMAT_24_HOUR);
        } else {
            mFormat = abc(mFormat12, mFormat24, DEFAULT_FORMAT_12_HOUR);
        }
        mHasSeconds = mFormat != null && DateFormat.hasDesignator(mFormat, 's');
    }

    private static CharSequence abc(CharSequence a, CharSequence b, CharSequence c) {
        return a == null ? (b == null ? c : b) : a;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mRegistered) {
            mRegistered = true;
            createTime(mTimeZone);
        }
        onTimeChanged();
    }

    public void onVisibilityAggregated(boolean isVisible) {
        if (!mShouldRunTicker && isVisible) {
            mShouldRunTicker = true;
            if (mHasSeconds) {
                mTicker.run();
            } else {
                onTimeChanged();
            }
        } else if (mShouldRunTicker && !isVisible) {
            mShouldRunTicker = false;
            removeCallbacks(mTicker);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRegistered) {
            removeCallbacks(mTicker);
            mRegistered = false;
        }
    }

    private void onTimeChanged() {
        if (mTime == null) {
            createTime(mTimeZone);
        }
        mTime.setTimeInMillis(System.currentTimeMillis());
        if (mFormat != null) {
            setText(DateFormat.format(mFormat, mTime));
        }
    }
}
