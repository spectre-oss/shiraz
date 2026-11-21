// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Shortcut capability identifier (e.g. actions.intent.OPEN_APP_FEATURE). */
public final class Capability implements Parcelable {
    private final String mName;

    Capability(String name) {
        Objects.requireNonNull(name);
        if (name.contains("/")) {
            throw new IllegalArgumentException("'/' is not permitted in the capability name");
        }
        mName = name;
    }

    private Capability(Parcel in) {
        mName = in.readString();
    }

    public String getName() {
        return mName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Capability)) return false;
        return Objects.equals(mName, ((Capability) obj).mName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mName);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Capability> CREATOR = new Creator<Capability>() {
        @Override
        public Capability createFromParcel(Parcel in) {
            return new Capability(in);
        }

        @Override
        public Capability[] newArray(int size) {
            return new Capability[size];
        }
    };

    public static final class Builder {
        private final String mName;

        public Builder(String name) {
            Objects.requireNonNull(name);
            if (name.contains("/")) {
                throw new IllegalArgumentException("'/' is not permitted in the capability name");
            }
            mName = name;
        }

        public Capability build() {
            return new Capability(mName);
        }
    }
}
