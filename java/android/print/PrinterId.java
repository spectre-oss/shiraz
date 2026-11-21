// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PrinterId implements Parcelable {
    private final ComponentName mServiceName;
    private final String mLocalId;

    PrinterId(ComponentName serviceName, String localId) {
        this.mServiceName = Objects.requireNonNull(serviceName, "serviceName cannot be null");
        this.mLocalId = Objects.requireNonNull(localId, "localId cannot be null");
    }

    private PrinterId(Parcel parcel) {
        String pkg = parcel.readString();
        String cls = parcel.readString();
        this.mServiceName = (pkg != null && cls != null) ? new ComponentName(pkg, cls) : null;
        this.mLocalId = parcel.readString();
    }

    ComponentName getServiceName() {
        return mServiceName;
    }

    public String getLocalId() {
        return mLocalId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        if (mServiceName != null) {
            parcel.writeString(mServiceName.getPackageName());
            parcel.writeString(mServiceName.getClassName());
        } else {
            parcel.writeString(null);
            parcel.writeString(null);
        }
        parcel.writeString(mLocalId);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        PrinterId other = (PrinterId) object;
        return Objects.equals(mServiceName, other.mServiceName)
                && Objects.equals(mLocalId, other.mLocalId);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = 31 * hashCode + (mServiceName == null ? 0 : mServiceName.hashCode());
        hashCode = 31 * hashCode + (mLocalId == null ? 0 : mLocalId.hashCode());
        return hashCode;
    }

    @Override
    public String toString() {
        return "PrinterId{serviceName="
                + (mServiceName == null ? "null" : mServiceName.flattenToString())
                + ", localId=" + mLocalId + "}";
    }

    public static final Parcelable.Creator<PrinterId> CREATOR =
            new Parcelable.Creator<PrinterId>() {
                @Override
                public PrinterId createFromParcel(Parcel parcel) {
                    return new PrinterId(parcel);
                }

                @Override
                public PrinterId[] newArray(int size) {
                    return new PrinterId[size];
                }
            };
}
