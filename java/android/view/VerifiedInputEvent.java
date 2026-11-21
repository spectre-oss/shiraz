// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public abstract class VerifiedInputEvent implements Parcelable {
    static final int VERIFIED_KEY = 1;
    static final int VERIFIED_MOTION = 2;

    private final int mType;
    private final int mDeviceId;
    private final long mEventTimeNanos;
    private final int mSource;
    private final int mDisplayId;

    VerifiedInputEvent(
            int type, int deviceId, long eventTimeNanos, int source, int displayId) {
        mType = type;
        mDeviceId = deviceId;
        mEventTimeNanos = eventTimeNanos;
        mSource = source;
        mDisplayId = displayId;
    }

    VerifiedInputEvent(Parcel in, int expectedType) {
        int type = in.readInt();
        if (type != expectedType) {
            throw new IllegalArgumentException("Unexpected VerifiedInputEvent type " + type);
        }
        mType = type;
        mDeviceId = in.readInt();
        mEventTimeNanos = in.readLong();
        mSource = in.readInt();
        mDisplayId = in.readInt();
    }

    public int getDeviceId() {
        return mDeviceId;
    }

    public long getEventTimeNanos() {
        return mEventTimeNanos;
    }

    public int getSource() {
        return mSource;
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mDeviceId);
        dest.writeLong(mEventTimeNanos);
        dest.writeInt(mSource);
        dest.writeInt(mDisplayId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerifiedInputEvent that = (VerifiedInputEvent) o;
        return mType == that.mType
                && mDeviceId == that.mDeviceId
                && mEventTimeNanos == that.mEventTimeNanos
                && mSource == that.mSource
                && mDisplayId == that.mDisplayId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mType),
                Integer.valueOf(mDeviceId),
                Long.valueOf(mEventTimeNanos),
                Integer.valueOf(mSource),
                Integer.valueOf(mDisplayId));
    }

    static int peekInt(Parcel parcel) {
        int initialDataPosition = parcel.dataPosition();
        int data = parcel.readInt();
        parcel.setDataPosition(initialDataPosition);
        return data;
    }

    public static final Parcelable.Creator<VerifiedInputEvent> CREATOR =
            new Parcelable.Creator<VerifiedInputEvent>() {
                @Override
                public VerifiedInputEvent createFromParcel(Parcel in) {
                    int type = peekInt(in);
                    if (type == VERIFIED_KEY) {
                        return VerifiedKeyEvent.CREATOR.createFromParcel(in);
                    } else if (type == VERIFIED_MOTION) {
                        return VerifiedMotionEvent.CREATOR.createFromParcel(in);
                    }
                    throw new IllegalArgumentException("Unexpected input event type " + type);
                }

                @Override
                public VerifiedInputEvent[] newArray(int size) {
                    return new VerifiedInputEvent[size];
                }
            };
}
