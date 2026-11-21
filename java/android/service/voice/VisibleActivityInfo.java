// SPDX-License-Identifier: GPL-3.0-only
package android.service.voice;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Class used to report the identity of a currently visible activity to VoiceInteractionSession. */
public final class VisibleActivityInfo implements Parcelable {
    private final int mTaskId;
    private final IBinder mAssistToken;

    public VisibleActivityInfo(int taskId, IBinder assistToken) {
        mTaskId = taskId;
        mAssistToken = assistToken;
    }

    VisibleActivityInfo(Parcel in) {
        mTaskId = in.readInt();
        mAssistToken = in.readStrongBinder();
    }

    public VoiceInteractionSession.ActivityId getActivityId() {
        return new VoiceInteractionSession.ActivityId(mTaskId, mAssistToken);
    }

    @Override
    public String toString() {
        return "VisibleActivityInfo{taskId=" + mTaskId + ", assistToken=" + mAssistToken + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisibleActivityInfo)) return false;
        VisibleActivityInfo other = (VisibleActivityInfo) o;
        return mTaskId == other.mTaskId && Objects.equals(mAssistToken, other.mAssistToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mTaskId), mAssistToken);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTaskId);
        dest.writeStrongBinder(mAssistToken);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<VisibleActivityInfo> CREATOR =
            new Parcelable.Creator<VisibleActivityInfo>() {
                @Override
                public VisibleActivityInfo createFromParcel(Parcel in) {
                    return new VisibleActivityInfo(in);
                }

                @Override
                public VisibleActivityInfo[] newArray(int size) {
                    return new VisibleActivityInfo[size];
                }
            };
}
