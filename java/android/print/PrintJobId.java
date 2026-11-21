// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;
import java.util.UUID;

public final class PrintJobId implements Parcelable {
    private final String mValue;

    PrintJobId() {
        this(UUID.randomUUID().toString());
    }

    PrintJobId(String value) {
        this.mValue = Objects.requireNonNull(value, "value");
    }

    String getValue() {
        return mValue;
    }

    @Override
    public int hashCode() {
        return 31 + mValue.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrintJobId other = (PrintJobId) obj;
        return Objects.equals(mValue, other.mValue);
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeString(mValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PrintJobId> CREATOR =
            new Parcelable.Creator<PrintJobId>() {
                @Override
                public PrintJobId createFromParcel(Parcel parcel) {
                    return new PrintJobId(parcel.readString());
                }

                @Override
                public PrintJobId[] newArray(int size) {
                    return new PrintJobId[size];
                }
            };
}
