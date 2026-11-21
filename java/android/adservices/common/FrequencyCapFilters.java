// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Frequency cap filters grouped by ad event type. */
public final class FrequencyCapFilters implements Parcelable {
    public static final int AD_EVENT_TYPE_WIN = 0;
    public static final int AD_EVENT_TYPE_IMPRESSION = 1;
    public static final int AD_EVENT_TYPE_VIEW = 2;
    public static final int AD_EVENT_TYPE_CLICK = 3;

    private final List<KeyedFrequencyCap> mWinCaps;
    private final List<KeyedFrequencyCap> mImpressionCaps;
    private final List<KeyedFrequencyCap> mViewCaps;
    private final List<KeyedFrequencyCap> mClickCaps;

    private FrequencyCapFilters(
            List<KeyedFrequencyCap> winCaps,
            List<KeyedFrequencyCap> impressionCaps,
            List<KeyedFrequencyCap> viewCaps,
            List<KeyedFrequencyCap> clickCaps) {
        this.mWinCaps = copyCaps(winCaps);
        this.mImpressionCaps = copyCaps(impressionCaps);
        this.mViewCaps = copyCaps(viewCaps);
        this.mClickCaps = copyCaps(clickCaps);
    }

    private static List<KeyedFrequencyCap> copyCaps(List<KeyedFrequencyCap> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<KeyedFrequencyCap> copy = new ArrayList<KeyedFrequencyCap>(list.size());
        for (KeyedFrequencyCap cap : list) {
            copy.add(Objects.requireNonNull(cap, "cap"));
        }
        return Collections.unmodifiableList(copy);
    }

    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForWinEvents() {
        return mWinCaps;
    }

    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForImpressionEvents() {
        return mImpressionCaps;
    }

    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForViewEvents() {
        return mViewCaps;
    }

    public List<KeyedFrequencyCap> getKeyedFrequencyCapsForClickEvents() {
        return mClickCaps;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedList(mWinCaps);
        dest.writeTypedList(mImpressionCaps);
        dest.writeTypedList(mViewCaps);
        dest.writeTypedList(mClickCaps);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FrequencyCapFilters)) {
            return false;
        }
        FrequencyCapFilters that = (FrequencyCapFilters) o;
        return Objects.equals(mWinCaps, that.mWinCaps)
                && Objects.equals(mImpressionCaps, that.mImpressionCaps)
                && Objects.equals(mViewCaps, that.mViewCaps)
                && Objects.equals(mClickCaps, that.mClickCaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWinCaps, mImpressionCaps, mViewCaps, mClickCaps);
    }

    @Override
    public String toString() {
        return "FrequencyCapFilters{win="
                + mWinCaps
                + ", impression="
                + mImpressionCaps
                + ", view="
                + mViewCaps
                + ", click="
                + mClickCaps
                + "}";
    }

    public static final class Builder {
        private List<KeyedFrequencyCap> mWinCaps = Collections.emptyList();
        private List<KeyedFrequencyCap> mImpressionCaps = Collections.emptyList();
        private List<KeyedFrequencyCap> mViewCaps = Collections.emptyList();
        private List<KeyedFrequencyCap> mClickCaps = Collections.emptyList();

        public Builder() {
        }

        public Builder setKeyedFrequencyCapsForWinEvents(List<KeyedFrequencyCap> caps) {
            Objects.requireNonNull(caps, "caps");
            this.mWinCaps = new ArrayList<KeyedFrequencyCap>(caps);
            return this;
        }

        public Builder setKeyedFrequencyCapsForImpressionEvents(List<KeyedFrequencyCap> caps) {
            Objects.requireNonNull(caps, "caps");
            this.mImpressionCaps = new ArrayList<KeyedFrequencyCap>(caps);
            return this;
        }

        public Builder setKeyedFrequencyCapsForViewEvents(List<KeyedFrequencyCap> caps) {
            Objects.requireNonNull(caps, "caps");
            this.mViewCaps = new ArrayList<KeyedFrequencyCap>(caps);
            return this;
        }

        public Builder setKeyedFrequencyCapsForClickEvents(List<KeyedFrequencyCap> caps) {
            Objects.requireNonNull(caps, "caps");
            this.mClickCaps = new ArrayList<KeyedFrequencyCap>(caps);
            return this;
        }

        public FrequencyCapFilters build() {
            return new FrequencyCapFilters(mWinCaps, mImpressionCaps, mViewCaps, mClickCaps);
        }
    }

    public static final Parcelable.Creator<FrequencyCapFilters> CREATOR =
            new Parcelable.Creator<FrequencyCapFilters>() {
                @Override
                public FrequencyCapFilters createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    List<KeyedFrequencyCap> win =
                            in.createTypedArrayList(KeyedFrequencyCap.CREATOR);
                    List<KeyedFrequencyCap> imp =
                            in.createTypedArrayList(KeyedFrequencyCap.CREATOR);
                    List<KeyedFrequencyCap> view =
                            in.createTypedArrayList(KeyedFrequencyCap.CREATOR);
                    List<KeyedFrequencyCap> click =
                            in.createTypedArrayList(KeyedFrequencyCap.CREATOR);
                    return new FrequencyCapFilters(win, imp, view, click);
                }

                @Override
                public FrequencyCapFilters[] newArray(int size) {
                    return new FrequencyCapFilters[size];
                }
            };
}
