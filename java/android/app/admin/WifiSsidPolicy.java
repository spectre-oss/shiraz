// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.net.wifi.WifiSsid;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class WifiSsidPolicy implements Parcelable {
    public static final int WIFI_SSID_POLICY_TYPE_ALLOWLIST = 0;
    public static final int WIFI_SSID_POLICY_TYPE_DENYLIST = 1;

    private final int mPolicyType;
    private final Set<WifiSsid> mSsids;

    public WifiSsidPolicy(int policyType, Set<WifiSsid> ssids) {
        if (policyType != WIFI_SSID_POLICY_TYPE_ALLOWLIST
                && policyType != WIFI_SSID_POLICY_TYPE_DENYLIST) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        if (ssids == null || ssids.isEmpty()) {
            throw new IllegalArgumentException("SSID list cannot be empty");
        }
        mPolicyType = policyType;
        mSsids = Collections.unmodifiableSet(new HashSet<WifiSsid>(ssids));
    }

    private WifiSsidPolicy(Parcel in) {
        mPolicyType = in.readInt();
        int count = in.readInt();
        Set<WifiSsid> set = new HashSet<WifiSsid>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            WifiSsid ssid = in.readTypedObject(WifiSsid.CREATOR);
            if (ssid != null) {
                set.add(ssid);
            }
        }
        mSsids = Collections.unmodifiableSet(set);
    }

    public Set<WifiSsid> getSsids() {
        return mSsids;
    }

    public int getPolicyType() {
        return mPolicyType;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
        dest.writeInt(mSsids.size());
        for (WifiSsid ssid : mSsids) {
            dest.writeTypedObject(ssid, flags);
        }
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) return true;
        if (!(thatObject instanceof WifiSsidPolicy)) return false;
        WifiSsidPolicy that = (WifiSsidPolicy) thatObject;
        return mPolicyType == that.mPolicyType && Objects.equals(mSsids, that.mSsids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mPolicyType), mSsids);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WifiSsidPolicy> CREATOR =
            new Parcelable.Creator<WifiSsidPolicy>() {
                @Override
                public WifiSsidPolicy createFromParcel(Parcel in) {
                    return new WifiSsidPolicy(in);
                }

                @Override
                public WifiSsidPolicy[] newArray(int size) {
                    return new WifiSsidPolicy[size];
                }
            };
}
