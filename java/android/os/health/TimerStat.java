// SPDX-License-Identifier: GPL-3.0-only
package android.os.health;

import android.os.Parcel;
import android.os.Parcelable;

/** Count and total duration pair for a health timer metric. */
public final class TimerStat implements Parcelable {
    public static final Parcelable.Creator<TimerStat> CREATOR =
            new Parcelable.Creator<TimerStat>() {
                @Override
                public TimerStat createFromParcel(Parcel in) {
                    return new TimerStat(in);
                }

                @Override
                public TimerStat[] newArray(int size) {
                    return new TimerStat[size];
                }
            };

    private int count;
    private long time;

    public TimerStat() {
    }

    public TimerStat(int count, long time) {
        this.count = count;
        this.time = time;
    }

    public TimerStat(Parcel in) {
        this.count = in.readInt();
        this.time = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(count);
        out.writeLong(time);
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }
}
