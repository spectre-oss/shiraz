// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ConnectEvent extends NetworkEvent implements Parcelable {
    private final String mIpAddress;
    private final int mPort;

    public ConnectEvent(String ipAddress, int port, String packageName, long timestamp) {
        super(packageName, timestamp);
        mIpAddress = ipAddress;
        mPort = port;
    }

    private ConnectEvent(Parcel in) {
        if (in.readInt() != PARCEL_TOKEN_CONNECT_EVENT) {
            throw new ParcelFormatException("Malformed ConnectEvent");
        }
        mIpAddress = in.readString();
        mPort = in.readInt();
        mPackageName = in.readString();
        mTimestamp = in.readLong();
        mId = in.readLong();
    }

    public InetAddress getInetAddress() {
        try {
            return InetAddress.getByName(mIpAddress);
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    public int getPort() {
        return mPort;
    }

    @Override
    public String toString() {
        return "ConnectEvent(" + mId + ", " + mIpAddress + ", " + mPort + ", "
                + mTimestamp + ", " + mPackageName + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(PARCEL_TOKEN_CONNECT_EVENT);
        out.writeString(mIpAddress);
        out.writeInt(mPort);
        out.writeString(mPackageName);
        out.writeLong(mTimestamp);
        out.writeLong(mId);
    }

    public static final Parcelable.Creator<ConnectEvent> CREATOR =
            new Parcelable.Creator<ConnectEvent>() {
                @Override
                public ConnectEvent createFromParcel(Parcel in) {
                    return new ConnectEvent(in);
                }

                @Override
                public ConnectEvent[] newArray(int size) {
                    return new ConnectEvent[size];
                }
            };
}
