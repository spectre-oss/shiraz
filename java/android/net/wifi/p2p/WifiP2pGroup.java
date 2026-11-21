// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WifiP2pGroup implements Parcelable {
    public static final int NETWORK_ID_TEMPORARY = -1;
    public static final int NETWORK_ID_PERSISTENT = -2;

    public static final Creator<WifiP2pGroup> CREATOR =
            new Creator<WifiP2pGroup>() {
                @Override
                public WifiP2pGroup createFromParcel(Parcel in) {
                    WifiP2pGroup group = new WifiP2pGroup();
                    group.mNetworkName = in.readString();
                    group.mOwner = in.readTypedObject(WifiP2pDevice.CREATOR);
                    group.mIsGroupOwner = in.readByte() == 1;
                    int clientCount = in.readInt();
                    for (int i = 0; i < clientCount; i++) {
                        WifiP2pDevice d = in.readTypedObject(WifiP2pDevice.CREATOR);
                        if (d != null) {
                            group.mClients.add(d);
                        }
                    }
                    group.mPassphrase = in.readString();
                    group.mInterface = in.readString();
                    group.mNetId = in.readInt();
                    group.mFrequency = in.readInt();
                    return group;
                }

                @Override
                public WifiP2pGroup[] newArray(int size) {
                    return new WifiP2pGroup[size];
                }
            };

    private String mNetworkName;
    private WifiP2pDevice mOwner;
    private boolean mIsGroupOwner;
    private final List<WifiP2pDevice> mClients = new ArrayList<WifiP2pDevice>();
    private String mPassphrase;
    private String mInterface;
    private int mNetId;
    private int mFrequency;

    public WifiP2pGroup() {}

    public WifiP2pGroup(WifiP2pGroup source) {
        if (source != null) {
            mNetworkName = source.getNetworkName();
            mOwner = source.getOwner() == null ? null : new WifiP2pDevice(source.getOwner());
            mIsGroupOwner = source.mIsGroupOwner;
            for (WifiP2pDevice d : source.getClientList()) {
                mClients.add(new WifiP2pDevice(d));
            }
            mPassphrase = source.getPassphrase();
            mInterface = source.getInterface();
            mNetId = source.getNetworkId();
            mFrequency = source.getFrequency();
        }
    }

    public String getNetworkName() {
        return mNetworkName;
    }

    public boolean isGroupOwner() {
        return mIsGroupOwner;
    }

    public WifiP2pDevice getOwner() {
        return mOwner;
    }

    public Collection<WifiP2pDevice> getClientList() {
        return Collections.unmodifiableCollection(mClients);
    }

    public String getPassphrase() {
        return mPassphrase;
    }

    public String getInterface() {
        return mInterface;
    }

    public int getNetworkId() {
        return mNetId;
    }

    public int getFrequency() {
        return mFrequency;
    }

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("network: ").append(mNetworkName);
        sbuf.append("\n isGO: ").append(mIsGroupOwner);
        sbuf.append("\n GO: ").append(mOwner);
        for (WifiP2pDevice client : mClients) {
            sbuf.append("\n Client: ").append(client);
        }
        sbuf.append("\n interface: ").append(mInterface);
        sbuf.append("\n networkId: ").append(mNetId);
        sbuf.append("\n frequency: ").append(mFrequency);
        return sbuf.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNetworkName);
        dest.writeTypedObject(mOwner, flags);
        dest.writeByte(mIsGroupOwner ? (byte) 1 : (byte) 0);
        dest.writeInt(mClients.size());
        for (WifiP2pDevice client : mClients) {
            dest.writeTypedObject(client, flags);
        }
        dest.writeString(mPassphrase);
        dest.writeString(mInterface);
        dest.writeInt(mNetId);
        dest.writeInt(mFrequency);
    }
}
