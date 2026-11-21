// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class RouteDiscoveryPreference implements Parcelable {
    public static final RouteDiscoveryPreference EMPTY =
            new Builder(Collections.<String>emptyList(), false).build();

    private final List<String> mPreferredFeatures;
    private final boolean mShouldPerformActiveScan;

    RouteDiscoveryPreference(Builder builder) {
        mPreferredFeatures = Collections.unmodifiableList(
                new ArrayList<String>(builder.mPreferredFeatures));
        mShouldPerformActiveScan = builder.mActiveScan;
    }

    private RouteDiscoveryPreference(Parcel in) {
        String[] arr = in.createStringArray();
        List<String> list = new ArrayList<String>();
        if (arr != null) {
            Collections.addAll(list, arr);
        }
        mPreferredFeatures = Collections.unmodifiableList(list);
        mShouldPerformActiveScan = in.readInt() != 0;
    }

    public List<String> getPreferredFeatures() {
        return mPreferredFeatures;
    }

    public boolean shouldPerformActiveScan() {
        return mShouldPerformActiveScan;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mPreferredFeatures.toArray(new String[0]));
        dest.writeInt(mShouldPerformActiveScan ? 1 : 0);
    }

    @Override
    public String toString() {
        return "RouteDiscoveryPreference{preferredFeatures=" + mPreferredFeatures
                + ", activeScan=" + mShouldPerformActiveScan + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteDiscoveryPreference)) return false;
        RouteDiscoveryPreference other = (RouteDiscoveryPreference) o;
        return mShouldPerformActiveScan == other.mShouldPerformActiveScan
                && Objects.equals(mPreferredFeatures, other.mPreferredFeatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPreferredFeatures, Boolean.valueOf(mShouldPerformActiveScan));
    }

    public static final Parcelable.Creator<RouteDiscoveryPreference> CREATOR =
            new Parcelable.Creator<RouteDiscoveryPreference>() {
                @Override
                public RouteDiscoveryPreference createFromParcel(Parcel in) {
                    return new RouteDiscoveryPreference(in);
                }

                @Override
                public RouteDiscoveryPreference[] newArray(int size) {
                    return new RouteDiscoveryPreference[size];
                }
            };

    public static final class Builder {
        List<String> mPreferredFeatures;
        boolean mActiveScan;

        public Builder(List<String> preferredFeatures, boolean activeScan) {
            Objects.requireNonNull(preferredFeatures, "preferredFeatures");
            mPreferredFeatures = sanitizeFeatures(preferredFeatures);
            mActiveScan = activeScan;
        }

        public Builder(RouteDiscoveryPreference preference) {
            Objects.requireNonNull(preference, "preference");
            mPreferredFeatures = new ArrayList<String>(preference.getPreferredFeatures());
            mActiveScan = preference.shouldPerformActiveScan();
        }

        public Builder setPreferredFeatures(List<String> preferredFeatures) {
            Objects.requireNonNull(preferredFeatures, "preferredFeatures");
            mPreferredFeatures = sanitizeFeatures(preferredFeatures);
            return this;
        }

        public Builder setShouldPerformActiveScan(boolean activeScan) {
            mActiveScan = activeScan;
            return this;
        }

        public RouteDiscoveryPreference build() {
            return new RouteDiscoveryPreference(this);
        }

        private static List<String> sanitizeFeatures(List<String> features) {
            LinkedHashSet<String> set = new LinkedHashSet<String>();
            for (String f : features) {
                if (!TextUtils.isEmpty(f)) {
                    set.add(f);
                }
            }
            return new ArrayList<String>(set);
        }
    }
}
