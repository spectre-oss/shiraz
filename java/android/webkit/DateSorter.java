// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.content.Context;
import java.util.Calendar;

public class DateSorter {
    public static final int DAY_COUNT = 5;

    private final long[] mBins = new long[DAY_COUNT - 1];
    private final String[] mLabels = new String[DAY_COUNT];

    public DateSorter(Context context) {
        Calendar c = Calendar.getInstance();
        beginningOfDay(c);
        mBins[0] = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, -1);
        mBins[1] = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, -6);
        mBins[2] = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_YEAR, 7);
        c.add(Calendar.MONTH, -1);
        mBins[3] = c.getTimeInMillis();

        mLabels[0] = "Today";
        mLabels[1] = "Yesterday";
        mLabels[2] = "Last 7 days";
        mLabels[3] = "Last month";
        mLabels[4] = "Older";
    }

    public int getIndex(long time) {
        int lastDay = DAY_COUNT - 1;
        for (int i = 0; i < lastDay; i++) {
            if (time > mBins[i]) {
                return i;
            }
        }
        return lastDay;
    }

    public String getLabel(int index) {
        if (index < 0 || index >= DAY_COUNT) {
            return "";
        }
        return mLabels[index];
    }

    public long getBoundary(int index) {
        if (index < 0 || index > 4) {
            index = 0;
        }
        if (index == DAY_COUNT - 1) {
            return Long.MIN_VALUE;
        }
        return mBins[index];
    }

    private static void beginningOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
