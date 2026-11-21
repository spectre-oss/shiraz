// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Active and total CPU time snapshot for a single logical processor. */
public final class CpuUsageInfo implements Parcelable {
    public static final Parcelable.Creator<CpuUsageInfo> CREATOR =
            new Parcelable.Creator<CpuUsageInfo>() {
                @Override
                public CpuUsageInfo createFromParcel(Parcel in) {
                    return new CpuUsageInfo(in.readLong(), in.readLong());
                }

                @Override
                public CpuUsageInfo[] newArray(int size) {
                    return new CpuUsageInfo[size];
                }
            };

    private final long active;
    private final long total;

    public CpuUsageInfo(long activeTime, long totalTime) {
        this.active = activeTime;
        this.total = totalTime;
    }

    public long getActive() {
        return active;
    }

    public long getTotal() {
        return total;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(active);
        out.writeLong(total);
    }
}
