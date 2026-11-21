// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Window animation frame timing statistics.
 */
public final class WindowAnimationFrameStats extends FrameStats implements Parcelable {
    public WindowAnimationFrameStats() {
    }

    WindowAnimationFrameStats(Parcel parcel) {
        mRefreshPeriodNano = parcel.readLong();
        mFramesPresentedTimeNano = parcel.createLongArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mRefreshPeriodNano);
        parcel.writeLongArray(mFramesPresentedTimeNano);
    }

    @Override
    public String toString() {
        return "WindowAnimationFrameStats[frameCount=" + getFrameCount()
                + ", startTimeNano=" + getStartTimeNano()
                + ", endTimeNano=" + getEndTimeNano() + "]";
    }

    public static final Parcelable.Creator<WindowAnimationFrameStats> CREATOR =
            new Parcelable.Creator<WindowAnimationFrameStats>() {
                @Override
                public WindowAnimationFrameStats createFromParcel(Parcel parcel) {
                    return new WindowAnimationFrameStats(parcel);
                }

                @Override
                public WindowAnimationFrameStats[] newArray(int size) {
                    return new WindowAnimationFrameStats[size];
                }
            };
}
