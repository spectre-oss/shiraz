// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Specifies the keys passed to {@link IsolatedWorker#onRender}. */
public final class RenderingConfig implements Parcelable {
    private final List<String> mKeys;

    private RenderingConfig(List<String> keys) {
        Objects.requireNonNull(keys, "keys");
        List<String> copy = new ArrayList<String>(keys.size());
        for (String k : keys) {
            copy.add(Objects.requireNonNull(k, "key"));
        }
        this.mKeys = Collections.unmodifiableList(copy);
    }

    public List<String> getKeys() {
        return mKeys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenderingConfig)) {
            return false;
        }
        RenderingConfig that = (RenderingConfig) o;
        return Objects.equals(mKeys, that.mKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeys);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mKeys.size());
        for (String key : mKeys) {
            dest.writeString(key);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private List<String> mKeys = new ArrayList<String>();

        public Builder() {
        }

        public Builder setKeys(List<String> keys) {
            Objects.requireNonNull(keys, "keys");
            this.mKeys = new ArrayList<String>(keys);
            return this;
        }

        public Builder addKey(String key) {
            this.mKeys.add(Objects.requireNonNull(key, "key"));
            return this;
        }

        public RenderingConfig build() {
            return new RenderingConfig(mKeys);
        }
    }

    public static final Parcelable.Creator<RenderingConfig> CREATOR =
            new Parcelable.Creator<RenderingConfig>() {
                @Override
                public RenderingConfig createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    int count = in.readInt();
                    List<String> keys = new ArrayList<String>(count);
                    for (int i = 0; i < count; i++) {
                        keys.add(in.readString());
                    }
                    return new RenderingConfig(keys);
                }

                @Override
                public RenderingConfig[] newArray(int size) {
                    return new RenderingConfig[size];
                }
            };
}
