// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;

public abstract class NetworkEvent implements Parcelable {
    static final int PARCEL_TOKEN_DNS_EVENT = 1;
    static final int PARCEL_TOKEN_CONNECT_EVENT = 2;

    String mPackageName;
    long mTimestamp;
    long mId;

    NetworkEvent() {
    }

    NetworkEvent(String packageName, long timestamp) {
        mPackageName = packageName;
        mTimestamp = timestamp;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public abstract void writeToParcel(Parcel out, int flags);

    public static final Parcelable.Creator<NetworkEvent> CREATOR =
            new Parcelable.Creator<NetworkEvent>() {
                @Override
                public NetworkEvent createFromParcel(Parcel in) {
                    int initialPosition = in.dataPosition();
                    int parcelToken = in.readInt();
                    in.setDataPosition(initialPosition);
                    switch (parcelToken) {
                        case PARCEL_TOKEN_DNS_EVENT:
                            return DnsEvent.CREATOR.createFromParcel(in);
                        case PARCEL_TOKEN_CONNECT_EVENT:
                            return ConnectEvent.CREATOR.createFromParcel(in);
                        default:
                            throw new ParcelFormatException(
                                    "Unexpected NetworkEvent token in parcel: " + parcelToken);
                    }
                }

                @Override
                public NetworkEvent[] newArray(int size) {
                    return new NetworkEvent[size];
                }
            };
}
