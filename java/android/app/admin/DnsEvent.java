// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DnsEvent extends NetworkEvent implements Parcelable {
    private final String mHostname;
    private final String[] mIpAddresses;
    private final int mIpAddressesCount;

    public DnsEvent(
            String hostname,
            String[] ipAddresses,
            int ipAddressesCount,
            String packageName,
            long timestamp) {
        super(packageName, timestamp);
        mHostname = hostname;
        mIpAddresses = ipAddresses != null ? ipAddresses.clone() : null;
        mIpAddressesCount = ipAddressesCount;
    }

    private DnsEvent(Parcel in) {
        if (in.readInt() != PARCEL_TOKEN_DNS_EVENT) {
            throw new ParcelFormatException("Malformed DnsEvent");
        }
        mHostname = in.readString();
        int len = in.readInt();
        if (len < 0) {
            mIpAddresses = null;
        } else {
            mIpAddresses = new String[len];
            for (int i = 0; i < len; i++) {
                mIpAddresses[i] = in.readString();
            }
        }
        mIpAddressesCount = in.readInt();
        mPackageName = in.readString();
        mTimestamp = in.readLong();
        mId = in.readLong();
    }

    public String getHostname() {
        return mHostname;
    }

    public List<InetAddress> getInetAddresses() {
        if (mIpAddresses == null || mIpAddresses.length == 0) {
            return Collections.emptyList();
        }
        List<InetAddress> list = new ArrayList<InetAddress>(mIpAddresses.length);
        for (String ip : mIpAddresses) {
            try {
                list.add(InetAddress.getByName(ip));
            } catch (UnknownHostException ignored) {
            }
        }
        return list;
    }

    public int getTotalResolvedAddressCount() {
        return mIpAddressesCount;
    }

    @Override
    public String toString() {
        return "DnsEvent(" + mId + ", " + mHostname + ", " + mIpAddressesCount + ", "
                + mTimestamp + ", " + mPackageName + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(PARCEL_TOKEN_DNS_EVENT);
        out.writeString(mHostname);
        if (mIpAddresses == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(mIpAddresses.length);
            for (String ip : mIpAddresses) {
                out.writeString(ip);
            }
        }
        out.writeInt(mIpAddressesCount);
        out.writeString(mPackageName);
        out.writeLong(mTimestamp);
        out.writeLong(mId);
    }

    public static final Parcelable.Creator<DnsEvent> CREATOR =
            new Parcelable.Creator<DnsEvent>() {
                @Override
                public DnsEvent createFromParcel(Parcel in) {
                    return new DnsEvent(in);
                }

                @Override
                public DnsEvent[] newArray(int size) {
                    return new DnsEvent[size];
                }
            };
}
