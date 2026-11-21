// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Base user credential containing a credential type string and structured Bundle payload. */
public final class Credential implements Parcelable {
    public static final String TYPE_PASSWORD_CREDENTIAL =
            "android.credentials.TYPE_PASSWORD_CREDENTIAL";

    private final String mType;
    private final Bundle mData;

    public Credential(String type, Bundle data) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        this.mType = type;
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
    }

    private Credential(Parcel in) {
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle b = in.readBundle(Credential.class.getClassLoader());
        this.mData = b != null ? b : new Bundle();
    }

    public String getType() {
        return mType;
    }

    public Bundle getData() {
        return new Bundle(mData);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "Credential {type=" + mType + ", data=" + mData + "}";
    }

    public static final Parcelable.Creator<Credential> CREATOR =
            new Parcelable.Creator<Credential>() {
                @Override
                public Credential createFromParcel(Parcel in) {
                    return new Credential(in);
                }

                @Override
                public Credential[] newArray(int size) {
                    return new Credential[size];
                }
            };
}
