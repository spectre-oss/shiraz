// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Parameter value and optional aliases bound to a shortcut Capability. */
public final class CapabilityParams implements Parcelable {
    private final String mName;
    private final String mPrimaryValue;
    private final List<String> mAliases;

    private CapabilityParams(String name, String primaryValue, Set<String> aliases) {
        mName = name;
        mPrimaryValue = primaryValue;
        mAliases = aliases == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(aliases));
    }

    private CapabilityParams(Parcel in) {
        mName = in.readString();
        mPrimaryValue = in.readString();
        int count = in.readInt();
        List<String> list = new ArrayList<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            list.add(in.readString());
        }
        mAliases = Collections.unmodifiableList(list);
    }

    public String getName() {
        return mName;
    }

    public String getValue() {
        return mPrimaryValue;
    }

    public List<String> getAliases() {
        return mAliases;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CapabilityParams)) return false;
        CapabilityParams target = (CapabilityParams) obj;
        return Objects.equals(mName, target.mName)
                && Objects.equals(mPrimaryValue, target.mPrimaryValue)
                && Objects.equals(mAliases, target.mAliases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mPrimaryValue, mAliases);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mPrimaryValue);
        dest.writeInt(mAliases.size());
        for (String alias : mAliases) {
            dest.writeString(alias);
        }
    }

    public static final Creator<CapabilityParams> CREATOR = new Creator<CapabilityParams>() {
        @Override
        public CapabilityParams createFromParcel(Parcel in) {
            return new CapabilityParams(in);
        }

        @Override
        public CapabilityParams[] newArray(int size) {
            return new CapabilityParams[size];
        }
    };

    public static final class Builder {
        private final String mKey;
        private final String mPrimaryValue;
        private Set<String> mAliases;

        public Builder(String key, String value) {
            Objects.requireNonNull(key);
            if (TextUtils.isEmpty(value)) {
                throw new IllegalArgumentException("Primary value cannot be empty or null");
            }
            mKey = key;
            mPrimaryValue = value;
        }

        public Builder addAlias(String alias) {
            if (mAliases == null) {
                mAliases = new LinkedHashSet<String>(1);
            }
            mAliases.add(alias);
            return this;
        }

        public CapabilityParams build() {
            return new CapabilityParams(mKey, mPrimaryValue, mAliases);
        }
    }
}
