// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import java.util.Calendar;

/** Legacy digital clock TextView displaying current time. */
@Deprecated
public class DigitalClock extends TextView {
    private Calendar mCalendar;
    private boolean mTickerStopped;
    private Runnable mTicker;

    public DigitalClock(Context context) {
        super(context);
        initClock();
    }

    public DigitalClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        initClock();
    }

    private void initClock() {
        if (mCalendar == null) {
            mCalendar = Calendar.getInstance();
        }
        updateTime();
    }

    private void updateTime() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        String format = DateFormat.is24HourFormat(getContext()) ? "kk:mm:ss" : "h:mm:ss aa";
        setText(DateFormat.format(format, mCalendar));
    }

    @Override
    protected void onAttachedToWindow() {
        mTickerStopped = false;
        super.onAttachedToWindow();
        mTicker = new Runnable() {
            @Override
            public void run() {
                if (mTickerStopped) {
                    return;
                }
                updateTime();
                invalidate();
                long now = SystemClock.uptimeMillis();
                long next = now + (1000 - now % 1000);
                android.os.Handler h = getHandler();
                if (h != null) {
                    h.postAtTime(mTicker, next);
                }
            }
        };
        mTicker.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTickerStopped = true;
        if (mTicker != null) {
            removeCallbacks(mTicker);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return DigitalClock.class.getName();
    }
}
