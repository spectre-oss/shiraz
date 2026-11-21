// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import java.util.Objects;

public final class AttributionSource implements Parcelable {
    private static final int SYSTEM_UID = 1000;
    private static final int INVALID_PID = -1;
    private static final int DEFAULT_DEVICE_ID = 0;

    private final int mUid;
    private final int mPid;
    private final String mPackageName;
    private final String mAttributionTag;
    private final int mDeviceId;
    private final AttributionSource mNext;
    private final IBinder mToken;

    AttributionSource(
            int uid,
            int pid,
            String packageName,
            String attributionTag,
            int deviceId,
            AttributionSource next,
            IBinder token) {
        mUid = uid;
        mPid = pid;
        mPackageName = packageName;
        mAttributionTag = attributionTag;
        mDeviceId = deviceId;
        mNext = next;
        mToken = token;
    }

    private AttributionSource(Parcel in) {
        mUid = in.readInt();
        mPid = in.readInt();
        mPackageName = in.readString();
        mAttributionTag = in.readString();
        mDeviceId = in.readInt();
        if (in.readBoolean()) {
            mNext = CREATOR.createFromParcel(in);
        } else {
            mNext = null;
        }
        mToken = in.readStrongBinder();
    }

    private static int currentUid() {
        try {
            return Process.myUid();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static int currentPid() {
        try {
            return Process.myPid();
        } catch (Throwable e) {
            return INVALID_PID;
        }
    }

    public static AttributionSource myAttributionSource() {
        return new Builder(currentUid())
                .setPid(currentPid())
                .setDeviceId(DEFAULT_DEVICE_ID)
                .build();
    }

    public void enforceCallingUid() {
        if (!checkCallingUid()) {
            throw new SecurityException("Calling uid: " + Binder.getCallingUid()
                    + " doesn't match source uid: " + mUid);
        }
    }

    public boolean checkCallingUid() {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != SYSTEM_UID && callingUid != mUid) {
            return false;
        }
        return true;
    }

    public boolean isTrusted(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (mToken == null && mUid != currentUid()) {
            return false;
        }
        return mNext == null || mNext.isTrusted(context);
    }

    public int getUid() {
        return mUid;
    }

    public int getPid() {
        return mPid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getAttributionTag() {
        return mAttributionTag;
    }

    public int getDeviceId() {
        return mDeviceId;
    }

    public AttributionSource getNext() {
        return mNext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AttributionSource that = (AttributionSource) o;
        return mUid == that.mUid
                && mPid == that.mPid
                && mDeviceId == that.mDeviceId
                && Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mAttributionTag, that.mAttributionTag)
                && Objects.equals(mNext, that.mNext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUid, mPid, mPackageName, mAttributionTag, mDeviceId, mNext);
    }

    @Override
    public String toString() {
        return "AttributionSource { uid = " + mUid
                + ", pid = " + mPid
                + ", packageName = " + mPackageName
                + ", attributionTag = " + mAttributionTag
                + ", deviceId = " + mDeviceId
                + ", next = " + mNext + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUid);
        dest.writeInt(mPid);
        dest.writeString(mPackageName);
        dest.writeString(mAttributionTag);
        dest.writeInt(mDeviceId);
        if (mNext != null) {
            dest.writeBoolean(true);
            mNext.writeToParcel(dest, flags);
        } else {
            dest.writeBoolean(false);
        }
        dest.writeStrongBinder(mToken);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AttributionSource> CREATOR =
            new Parcelable.Creator<AttributionSource>() {
                @Override
                public AttributionSource createFromParcel(Parcel in) {
                    return new AttributionSource(in);
                }

                @Override
                public AttributionSource[] newArray(int size) {
                    return new AttributionSource[size];
                }
            };

    public static final class Builder {
        private static final Binder sDefaultToken = new Binder();

        private final int mUid;
        private int mPid = INVALID_PID;
        private String mPackageName;
        private String mAttributionTag;
        private int mDeviceId = DEFAULT_DEVICE_ID;
        private AttributionSource mNext;
        private IBinder mToken;

        public Builder(int uid) {
            if (uid < 0) {
                throw new IllegalArgumentException("uid must be >= 0");
            }
            mUid = uid;
            if (uid == currentUid()) {
                mToken = sDefaultToken;
            }
        }

        public Builder(AttributionSource current) {
            Objects.requireNonNull(current, "current AttributionSource must not be null");
            mUid = current.mUid;
            mPid = current.mPid;
            mPackageName = current.mPackageName;
            mAttributionTag = current.mAttributionTag;
            mDeviceId = current.mDeviceId;
            mNext = current.mNext;
            mToken = current.mToken;
        }

        public Builder setPid(int value) {
            mPid = value;
            return this;
        }

        public Builder setPackageName(String value) {
            mPackageName = value;
            return this;
        }

        public Builder setAttributionTag(String value) {
            mAttributionTag = value;
            return this;
        }

        public Builder setDeviceId(int value) {
            mDeviceId = value;
            return this;
        }

        public Builder setNext(AttributionSource value) {
            mNext = value;
            return this;
        }

        public AttributionSource build() {
            return new AttributionSource(
                    mUid,
                    mPid,
                    mPackageName,
                    mAttributionTag,
                    mDeviceId,
                    mNext,
                    mToken);
        }
    }
}
