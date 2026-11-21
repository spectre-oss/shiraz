// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NetworkSlicingConfig implements Parcelable {
    private final List<UrspRule> mUrspRules;
    private final List<NetworkSliceInfo> mSliceInfo;

    public NetworkSlicingConfig() {
        mUrspRules = Collections.emptyList();
        mSliceInfo = Collections.emptyList();
    }

    NetworkSlicingConfig(List<UrspRule> urspRules, List<NetworkSliceInfo> sliceInfo) {
        mUrspRules = urspRules != null
                ? Collections.unmodifiableList(new ArrayList<UrspRule>(urspRules))
                : Collections.<UrspRule>emptyList();
        mSliceInfo = sliceInfo != null
                ? Collections.unmodifiableList(new ArrayList<NetworkSliceInfo>(sliceInfo))
                : Collections.<NetworkSliceInfo>emptyList();
    }

    private NetworkSlicingConfig(Parcel p) {
        List<UrspRule> rules = p.createTypedArrayList(UrspRule.CREATOR);
        List<NetworkSliceInfo> slices = p.createTypedArrayList(NetworkSliceInfo.CREATOR);
        mUrspRules = rules != null ? Collections.unmodifiableList(rules) : Collections.<UrspRule>emptyList();
        mSliceInfo = slices != null ? Collections.unmodifiableList(slices) : Collections.<NetworkSliceInfo>emptyList();
    }

    public List<UrspRule> getUrspRules() {
        return mUrspRules;
    }

    public List<NetworkSliceInfo> getSliceInfo() {
        return mSliceInfo;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mUrspRules);
        dest.writeTypedList(mSliceInfo);
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkSlicingConfig)) return false;
        NetworkSlicingConfig that = (NetworkSlicingConfig) o;
        return Objects.equals(mUrspRules, that.mUrspRules)
                && Objects.equals(mSliceInfo, that.mSliceInfo);
    }

    public int hashCode() {
        return Objects.hash(mUrspRules, mSliceInfo);
    }

    public String toString() {
        return "{.urspRules = " + mUrspRules + ", .sliceInfo = " + mSliceInfo + "}";
    }

    public static final Parcelable.Creator<NetworkSlicingConfig> CREATOR =
            new Parcelable.Creator<NetworkSlicingConfig>() {
                public NetworkSlicingConfig createFromParcel(Parcel source) {
                    return new NetworkSlicingConfig(source);
                }

                public NetworkSlicingConfig[] newArray(int size) {
                    return new NetworkSlicingConfig[size];
                }
            };
}
