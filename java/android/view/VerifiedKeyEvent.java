// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class VerifiedKeyEvent extends VerifiedInputEvent implements Parcelable {
    private static final int FLAG_CANCELED = 0x00000020;
    private static final int FLAG_IS_ACCESSIBILITY_EVENT = 0x00000800;

    private final int mAction;
    private final long mDownTimeNanos;
    private final int mFlags;
    private final int mKeyCode;
    private final int mScanCode;
    private final int mMetaState;
    private final int mRepeatCount;

    public VerifiedKeyEvent(
            int deviceId,
            long eventTimeNanos,
            int source,
            int displayId,
            int action,
            long downTimeNanos,
            int flags,
            int keyCode,
            int scanCode,
            int metaState,
            int repeatCount) {
        super(VERIFIED_KEY, deviceId, eventTimeNanos, source, displayId);
        mAction = action;
        mDownTimeNanos = downTimeNanos;
        mFlags = flags;
        mKeyCode = keyCode;
        mScanCode = scanCode;
        mMetaState = metaState;
        mRepeatCount = repeatCount;
    }

    private VerifiedKeyEvent(Parcel in) {
        super(in, VERIFIED_KEY);
        mAction = in.readInt();
        mDownTimeNanos = in.readLong();
        mFlags = in.readInt();
        mKeyCode = in.readInt();
        mScanCode = in.readInt();
        mMetaState = in.readInt();
        mRepeatCount = in.readInt();
    }

    public Boolean getFlag(int flag) {
        switch (flag) {
            case FLAG_CANCELED:
            case FLAG_IS_ACCESSIBILITY_EVENT:
                return Boolean.valueOf((mFlags & flag) != 0);
            default:
                return null;
        }
    }

    public int getAction() {
        return mAction;
    }

    public long getDownTimeNanos() {
        return mDownTimeNanos;
    }

    public int getKeyCode() {
        return mKeyCode;
    }

    public int getScanCode() {
        return mScanCode;
    }

    public int getMetaState() {
        return mMetaState;
    }

    public int getRepeatCount() {
        return mRepeatCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VerifiedKeyEvent that = (VerifiedKeyEvent) o;
        return mAction == that.mAction
                && mDownTimeNanos == that.mDownTimeNanos
                && mFlags == that.mFlags
                && mKeyCode == that.mKeyCode
                && mScanCode == that.mScanCode
                && mMetaState == that.mMetaState
                && mRepeatCount == that.mRepeatCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                Integer.valueOf(mAction),
                Long.valueOf(mDownTimeNanos),
                Integer.valueOf(mFlags),
                Integer.valueOf(mKeyCode),
                Integer.valueOf(mScanCode),
                Integer.valueOf(mMetaState),
                Integer.valueOf(mRepeatCount));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mAction);
        dest.writeLong(mDownTimeNanos);
        dest.writeInt(mFlags);
        dest.writeInt(mKeyCode);
        dest.writeInt(mScanCode);
        dest.writeInt(mMetaState);
        dest.writeInt(mRepeatCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<VerifiedKeyEvent> CREATOR =
            new Parcelable.Creator<VerifiedKeyEvent>() {
                @Override
                public VerifiedKeyEvent createFromParcel(Parcel in) {
                    return new VerifiedKeyEvent(in);
                }

                @Override
                public VerifiedKeyEvent[] newArray(int size) {
                    return new VerifiedKeyEvent[size];
                }
            };
}
