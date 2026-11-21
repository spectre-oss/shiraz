// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Window content frame timing statistics.
 */
public final class WindowContentFrameStats extends FrameStats implements Parcelable {
    private long[] mFramesPostedTimeNano;
    private long[] mFramesReadyTimeNano;

    public WindowContentFrameStats() {
    }

    WindowContentFrameStats(Parcel parcel) {
        mRefreshPeriodNano = parcel.readLong();
        mFramesPostedTimeNano = parcel.createLongArray();
        mFramesPresentedTimeNano = parcel.createLongArray();
        mFramesReadyTimeNano = parcel.createLongArray();
    }

    public long getFramePostedTimeNano(int index) {
        if (mFramesPostedTimeNano == null) {
            throw new IndexOutOfBoundsException();
        }
        return mFramesPostedTimeNano[index];
    }

    public long getFrameReadyTimeNano(int index) {
        if (mFramesReadyTimeNano == null) {
            throw new IndexOutOfBoundsException();
        }
        return mFramesReadyTimeNano[index];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mRefreshPeriodNano);
        parcel.writeLongArray(mFramesPostedTimeNano);
        parcel.writeLongArray(mFramesPresentedTimeNano);
        parcel.writeLongArray(mFramesReadyTimeNano);
    }

    @Override
    public String toString() {
        return "WindowContentFrameStats[frameCount=" + getFrameCount()
                + ", startTimeNano=" + getStartTimeNano()
                + ", endTimeNano=" + getEndTimeNano() + "]";
    }

    public static final Parcelable.Creator<WindowContentFrameStats> CREATOR =
            new Parcelable.Creator<WindowContentFrameStats>() {
                @Override
                public WindowContentFrameStats createFromParcel(Parcel parcel) {
                    return new WindowContentFrameStats(parcel);
                }

                @Override
                public WindowContentFrameStats[] newArray(int size) {
                    return new WindowContentFrameStats[size];
                }
            };
}
