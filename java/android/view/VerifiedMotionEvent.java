// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class VerifiedMotionEvent extends VerifiedInputEvent implements Parcelable {
    private static final int FLAG_WINDOW_IS_OBSCURED = 0x1;
    private static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2;
    private static final int FLAG_IS_GENERATED_GESTURE = 0x8;

    private final float mRawX;
    private final float mRawY;
    private final int mActionMasked;
    private final long mDownTimeNanos;
    private final int mFlags;
    private final int mMetaState;
    private final int mButtonState;

    public VerifiedMotionEvent(
            int deviceId,
            long eventTimeNanos,
            int source,
            int displayId,
            float rawX,
            float rawY,
            int actionMasked,
            long downTimeNanos,
            int flags,
            int metaState,
            int buttonState) {
        super(VERIFIED_MOTION, deviceId, eventTimeNanos, source, displayId);
        mRawX = rawX;
        mRawY = rawY;
        mActionMasked = actionMasked;
        mDownTimeNanos = downTimeNanos;
        mFlags = flags;
        mMetaState = metaState;
        mButtonState = buttonState;
    }

    private VerifiedMotionEvent(Parcel in) {
        super(in, VERIFIED_MOTION);
        mRawX = in.readFloat();
        mRawY = in.readFloat();
        mActionMasked = in.readInt();
        mDownTimeNanos = in.readLong();
        mFlags = in.readInt();
        mMetaState = in.readInt();
        mButtonState = in.readInt();
    }

    public Boolean getFlag(int flag) {
        switch (flag) {
            case FLAG_WINDOW_IS_OBSCURED:
            case FLAG_WINDOW_IS_PARTIALLY_OBSCURED:
            case FLAG_IS_GENERATED_GESTURE:
                return Boolean.valueOf((mFlags & flag) != 0);
            default:
                return null;
        }
    }

    public float getRawX() {
        return mRawX;
    }

    public float getRawY() {
        return mRawY;
    }

    public int getActionMasked() {
        return mActionMasked;
    }

    public long getDownTimeNanos() {
        return mDownTimeNanos;
    }

    public int getMetaState() {
        return mMetaState;
    }

    public int getButtonState() {
        return mButtonState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VerifiedMotionEvent that = (VerifiedMotionEvent) o;
        return Float.compare(that.mRawX, mRawX) == 0
                && Float.compare(that.mRawY, mRawY) == 0
                && mActionMasked == that.mActionMasked
                && mDownTimeNanos == that.mDownTimeNanos
                && mFlags == that.mFlags
                && mMetaState == that.mMetaState
                && mButtonState == that.mButtonState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                Float.valueOf(mRawX),
                Float.valueOf(mRawY),
                Integer.valueOf(mActionMasked),
                Long.valueOf(mDownTimeNanos),
                Integer.valueOf(mFlags),
                Integer.valueOf(mMetaState),
                Integer.valueOf(mButtonState));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloat(mRawX);
        dest.writeFloat(mRawY);
        dest.writeInt(mActionMasked);
        dest.writeLong(mDownTimeNanos);
        dest.writeInt(mFlags);
        dest.writeInt(mMetaState);
        dest.writeInt(mButtonState);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<VerifiedMotionEvent> CREATOR =
            new Parcelable.Creator<VerifiedMotionEvent>() {
                @Override
                public VerifiedMotionEvent createFromParcel(Parcel in) {
                    return new VerifiedMotionEvent(in);
                }

                @Override
                public VerifiedMotionEvent[] newArray(int size) {
                    return new VerifiedMotionEvent[size];
                }
            };
}
