// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.Objects;
import java.util.UUID;

/** Audio/media endpoint available to carry call media. */
public final class CallEndpoint implements Parcelable {
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_EARPIECE = 1;
    public static final int TYPE_BLUETOOTH = 2;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_SPEAKER = 4;
    public static final int TYPE_STREAMING = 5;

    private final CharSequence mName;
    private final int mType;
    private final ParcelUuid mIdentifier;

    public CallEndpoint(CharSequence name, int type, ParcelUuid id) {
        if (name == null || id == null) {
            throw new IllegalArgumentException("Endpoint name and id cannot be null");
        }
        this.mName = name;
        this.mType = type;
        this.mIdentifier = id;
    }

    CallEndpoint(CharSequence name, int type) {
        this(name, type, new ParcelUuid(UUID.randomUUID()));
    }

    private CallEndpoint(Parcel in) {
        this.mName = Objects.requireNonNull(in.readString(), "name");
        this.mType = in.readInt();
        this.mIdentifier = ParcelUuid.fromString(Objects.requireNonNull(in.readString(), "id"));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CallEndpoint)) {
            return false;
        }
        CallEndpoint other = (CallEndpoint) obj;
        return Objects.equals(mName.toString(), other.mName.toString())
                && mType == other.mType
                && Objects.equals(mIdentifier, other.mIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName.toString(), Integer.valueOf(mType), mIdentifier);
    }

    @Override
    public String toString() {
        return "[CallEndpoint Name="
                + mName
                + ", Type="
                + endpointTypeToString(mType)
                + ", Identifier="
                + mIdentifier
                + "]";
    }

    private static String endpointTypeToString(int endpointType) {
        switch (endpointType) {
            case TYPE_EARPIECE:
                return "EARPIECE";
            case TYPE_BLUETOOTH:
                return "BLUETOOTH";
            case TYPE_WIRED_HEADSET:
                return "WIRED_HEADSET";
            case TYPE_SPEAKER:
                return "SPEAKER";
            case TYPE_STREAMING:
                return "EXTERNAL";
            default:
                return "UNKNOWN (" + endpointType + ")";
        }
    }

    public CharSequence getEndpointName() {
        return mName;
    }

    public int getEndpointType() {
        return mType;
    }

    public ParcelUuid getIdentifier() {
        return mIdentifier;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeString(mName.toString());
        destination.writeInt(mType);
        destination.writeString(mIdentifier.toString());
    }

    public static final Parcelable.Creator<CallEndpoint> CREATOR =
            new Parcelable.Creator<CallEndpoint>() {
                @Override
                public CallEndpoint createFromParcel(Parcel in) {
                    return new CallEndpoint(in);
                }

                @Override
                public CallEndpoint[] newArray(int size) {
                    return new CallEndpoint[size];
                }
            };
}
