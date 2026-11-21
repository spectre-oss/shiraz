// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Immutable attributes describing a transactional call added via {@link TelecomManager#addCall}. */
public final class CallAttributes implements Parcelable {
    public static final int DIRECTION_INCOMING = 1;
    public static final int DIRECTION_OUTGOING = 2;

    public static final int AUDIO_CALL = 1;
    public static final int VIDEO_CALL = 2;

    public static final int SUPPORTS_SET_INACTIVE = 0x0002;
    public static final int SUPPORTS_STREAM = 0x0004;
    public static final int SUPPORTS_TRANSFER = 0x0008;
    public static final int SUPPORTS_VIDEO_CALLING = 0x0010;

    private final PhoneAccountHandle mPhoneAccountHandle;
    private final CharSequence mDisplayName;
    private final Uri mAddress;
    private final int mDirection;
    private final int mCallType;
    private final int mCallCapabilities;

    private CallAttributes(
            PhoneAccountHandle phoneAccountHandle,
            CharSequence displayName,
            Uri address,
            int direction,
            int callType,
            int callCapabilities) {
        this.mPhoneAccountHandle = phoneAccountHandle;
        this.mDisplayName = displayName;
        this.mAddress = address;
        this.mDirection = direction;
        this.mCallType = callType;
        this.mCallCapabilities = callCapabilities;
    }

    private CallAttributes(Parcel in) {
        this.mPhoneAccountHandle = in.readTypedObject(PhoneAccountHandle.CREATOR);
        this.mDisplayName = in.readString();
        String addr = in.readString();
        this.mAddress = addr == null ? null : Uri.parse(addr);
        this.mDirection = in.readInt();
        this.mCallType = in.readInt();
        this.mCallCapabilities = in.readInt();
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccountHandle;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public Uri getAddress() {
        return mAddress;
    }

    public int getDirection() {
        return mDirection;
    }

    public int getCallType() {
        return mCallType;
    }

    public int getCallCapabilities() {
        return mCallCapabilities;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mPhoneAccountHandle, flags);
        dest.writeString(mDisplayName == null ? null : mDisplayName.toString());
        dest.writeString(mAddress == null ? null : mAddress.toString());
        dest.writeInt(mDirection);
        dest.writeInt(mCallType);
        dest.writeInt(mCallCapabilities);
    }

    @Override
    public String toString() {
        return "{ CallAttributes: [phoneAccountHandle: "
                + mPhoneAccountHandle
                + "], [contactName: "
                + mDisplayName
                + "], [address="
                + mAddress
                + "], [direction="
                + mDirection
                + "], [callType="
                + mCallType
                + "], [mCallCapabilities="
                + mCallCapabilities
                + "] }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CallAttributes)) {
            return false;
        }
        CallAttributes other = (CallAttributes) obj;
        return mDirection == other.mDirection
                && mCallType == other.mCallType
                && mCallCapabilities == other.mCallCapabilities
                && Objects.equals(mPhoneAccountHandle, other.mPhoneAccountHandle)
                && Objects.equals(mAddress, other.mAddress)
                && Objects.equals(
                        mDisplayName == null ? null : mDisplayName.toString(),
                        other.mDisplayName == null ? null : other.mDisplayName.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mPhoneAccountHandle,
                mAddress,
                mDisplayName == null ? null : mDisplayName.toString(),
                Integer.valueOf(mDirection),
                Integer.valueOf(mCallType),
                Integer.valueOf(mCallCapabilities));
    }

    public static final class Builder {
        private final PhoneAccountHandle mPhoneAccountHandle;
        private final int mDirection;
        private final CharSequence mDisplayName;
        private final Uri mAddress;
        private int mCallType = AUDIO_CALL;
        private int mCallCapabilities = SUPPORTS_SET_INACTIVE;

        public Builder(
                PhoneAccountHandle phoneAccountHandle,
                int callDirection,
                CharSequence displayName,
                Uri address) {
            if (callDirection != DIRECTION_INCOMING && callDirection != DIRECTION_OUTGOING) {
                throw new IllegalArgumentException(
                        "InvalidCallDirection: " + callDirection);
            }
            Objects.requireNonNull(phoneAccountHandle, "phoneAccountHandle");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(address, "address");
            this.mPhoneAccountHandle = phoneAccountHandle;
            this.mDirection = callDirection;
            this.mDisplayName = displayName;
            this.mAddress = address;
        }

        public Builder setCallType(int callType) {
            if (callType != AUDIO_CALL && callType != VIDEO_CALL) {
                throw new IllegalArgumentException("InvalidCallType: " + callType);
            }
            this.mCallType = callType;
            return this;
        }

        public Builder setCallCapabilities(int callCapabilities) {
            this.mCallCapabilities = callCapabilities;
            return this;
        }

        public CallAttributes build() {
            return new CallAttributes(
                    mPhoneAccountHandle,
                    mDisplayName,
                    mAddress,
                    mDirection,
                    mCallType,
                    mCallCapabilities);
        }
    }

    public static final Parcelable.Creator<CallAttributes> CREATOR =
            new Parcelable.Creator<CallAttributes>() {
                @Override
                public CallAttributes createFromParcel(Parcel source) {
                    return new CallAttributes(source);
                }

                @Override
                public CallAttributes[] newArray(int size) {
                    return new CallAttributes[size];
                }
            };
}
