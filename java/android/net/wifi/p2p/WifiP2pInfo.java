// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class WifiP2pInfo implements Parcelable {
    public static final Creator<WifiP2pInfo> CREATOR =
            new Creator<WifiP2pInfo>() {
                @Override
                public WifiP2pInfo createFromParcel(Parcel in) {
                    WifiP2pInfo info = new WifiP2pInfo();
                    info.groupFormed = in.readByte() == 1;
                    info.isGroupOwner = in.readByte() == 1;
                    if (in.readByte() == 1) {
                        byte[] addr = in.createByteArray();
                        if (addr != null) {
                            try {
                                info.groupOwnerAddress = InetAddress.getByAddress(addr);
                            } catch (UnknownHostException ignored) {
                                info.groupOwnerAddress = null;
                            }
                        }
                    }
                    return info;
                }

                @Override
                public WifiP2pInfo[] newArray(int size) {
                    return new WifiP2pInfo[size];
                }
            };

    public boolean groupFormed;
    public boolean isGroupOwner;
    public InetAddress groupOwnerAddress;

    public WifiP2pInfo() {}

    public WifiP2pInfo(WifiP2pInfo source) {
        if (source != null) {
            groupFormed = source.groupFormed;
            isGroupOwner = source.isGroupOwner;
            groupOwnerAddress = source.groupOwnerAddress;
        }
    }

    @Override
    public String toString() {
        return "groupFormed: "
                + groupFormed
                + " isGroupOwner: "
                + isGroupOwner
                + " groupOwnerAddress: "
                + groupOwnerAddress;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(groupFormed ? (byte) 1 : (byte) 0);
        dest.writeByte(isGroupOwner ? (byte) 1 : (byte) 0);
        if (groupOwnerAddress != null) {
            dest.writeByte((byte) 1);
            dest.writeByteArray(groupOwnerAddress.getAddress());
        } else {
            dest.writeByte((byte) 0);
        }
    }
}
