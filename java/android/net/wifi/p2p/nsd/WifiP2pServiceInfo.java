// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WifiP2pServiceInfo implements Parcelable {
    public static final int SERVICE_TYPE_ALL = 0;
    public static final int SERVICE_TYPE_BONJOUR = 1;
    public static final int SERVICE_TYPE_UPNP = 2;
    public static final int SERVICE_TYPE_VENDOR_SPECIFIC = 255;

    static final Creator<WifiP2pServiceInfo> CREATOR =
            new Creator<WifiP2pServiceInfo>() {
                @Override
                public WifiP2pServiceInfo createFromParcel(Parcel in) {
                    String[] data = in.createStringArray();
                    List<String> list =
                            data == null
                                    ? Collections.<String>emptyList()
                                    : Arrays.asList(data);
                    return new WifiP2pServiceInfo(list);
                }

                @Override
                public WifiP2pServiceInfo[] newArray(int size) {
                    return new WifiP2pServiceInfo[size];
                }
            };

    private final List<String> mQueryList;

    WifiP2pServiceInfo(List<String> queryList) {
        if (queryList == null) {
            throw new IllegalArgumentException("query list cannot be null");
        }
        mQueryList = Collections.unmodifiableList(new ArrayList<String>(queryList));
    }

    List<String> getSupplicantQueryList() {
        return mQueryList;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof WifiP2pServiceInfo)) {
            return false;
        }
        WifiP2pServiceInfo servInfo = (WifiP2pServiceInfo) o;
        return mQueryList.equals(servInfo.mQueryList);
    }

    @Override
    public int hashCode() {
        return 527 + mQueryList.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mQueryList.toArray(new String[mQueryList.size()]));
    }
}
