// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.Arrays;

/** Validator that checks if the concatenation of fields passes a Luhn checksum. */
public final class LuhnChecksumValidator implements Validator, Parcelable {
    private final AutofillId[] mIds;

    public LuhnChecksumValidator(AutofillId... ids) {
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == null) {
                throw new NullPointerException("ids[" + i + "] is null");
            }
        }
        mIds = ids.clone();
    }

    LuhnChecksumValidator(Parcel in) {
        mIds = in.createTypedArray(AutofillId.CREATOR);
    }

    @Override
    public String toString() {
        return "LuhnChecksumValidator: [ids=" + Arrays.toString(mIds) + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedArray(mIds, flags);
    }

    public static final Parcelable.Creator<LuhnChecksumValidator> CREATOR =
            new Parcelable.Creator<LuhnChecksumValidator>() {
                @Override
                public LuhnChecksumValidator createFromParcel(Parcel parcel) {
                    return new LuhnChecksumValidator(parcel);
                }

                @Override
                public LuhnChecksumValidator[] newArray(int size) {
                    return new LuhnChecksumValidator[size];
                }
            };
}
