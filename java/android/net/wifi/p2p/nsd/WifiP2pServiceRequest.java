// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;
import java.util.Objects;

public class WifiP2pServiceRequest implements Parcelable {
    static final Creator<WifiP2pServiceRequest> CREATOR =
            new Creator<WifiP2pServiceRequest>() {
                @Override
                public WifiP2pServiceRequest createFromParcel(Parcel in) {
                    int servType = in.readInt();
                    int length = in.readInt();
                    int transId = in.readInt();
                    String query = in.readString();
                    return new WifiP2pServiceRequest(servType, length, transId, query);
                }

                @Override
                public WifiP2pServiceRequest[] newArray(int size) {
                    return new WifiP2pServiceRequest[size];
                }
            };

    private final int mProtocolType;
    private final int mLength;
    private int mTransId;
    private final String mQuery;

    WifiP2pServiceRequest(int protocolType, String query) {
        validateQuery(query);
        mProtocolType = protocolType;
        mQuery = query;
        if (query != null) {
            mLength = (query.length() / 2) + 2;
        } else {
            mLength = 2;
        }
    }

    private WifiP2pServiceRequest(int serviceType, int length, int transId, String query) {
        mProtocolType = serviceType;
        mLength = length;
        mTransId = transId;
        mQuery = query;
    }

    private static void validateQuery(String query) {
        if (query == null) {
            return;
        }
        if ((query.length() % 2) == 1) {
            throw new IllegalArgumentException(
                    "query size is invalid. query=" + query);
        }
        String lower = query.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new IllegalArgumentException(
                        "query should be hex string. query=" + query);
            }
        }
    }

    public static WifiP2pServiceRequest newInstance(int protocolType, String queryData) {
        return new WifiP2pServiceRequest(protocolType, queryData);
    }

    public static WifiP2pServiceRequest newInstance(int protocolType) {
        return new WifiP2pServiceRequest(protocolType, null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof WifiP2pServiceRequest)) {
            return false;
        }
        WifiP2pServiceRequest req = (WifiP2pServiceRequest) o;
        return req.mProtocolType == mProtocolType
                && req.mLength == mLength
                && Objects.equals(req.mQuery, mQuery);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + mProtocolType;
        result = 31 * result + mLength;
        result = 31 * result + (mQuery == null ? 0 : mQuery.hashCode());
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mProtocolType);
        dest.writeInt(mLength);
        dest.writeInt(mTransId);
        dest.writeString(mQuery);
    }
}
