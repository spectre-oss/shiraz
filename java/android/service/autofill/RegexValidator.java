// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.regex.Pattern;

/** Validator that checks if a TextView value matches a regular expression. */
public final class RegexValidator implements Validator, Parcelable {
    private final AutofillId mId;
    private final Pattern mRegex;

    public RegexValidator(AutofillId id, Pattern regex) {
        if (id == null) throw new NullPointerException("id cannot be null");
        if (regex == null) throw new NullPointerException("regex cannot be null");
        mId = id;
        mRegex = regex;
    }

    RegexValidator(Parcel in) {
        mId = in.readTypedObject(AutofillId.CREATOR);
        String pattern = in.readString();
        int flags = in.readInt();
        mRegex = Pattern.compile(pattern, flags);
    }

    @Override
    public String toString() {
        return "RegexValidator: [id=" + mId + ", regex=" + mRegex + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mId, flags);
        parcel.writeString(mRegex.pattern());
        parcel.writeInt(mRegex.flags());
    }

    public static final Parcelable.Creator<RegexValidator> CREATOR =
            new Parcelable.Creator<RegexValidator>() {
                @Override
                public RegexValidator createFromParcel(Parcel parcel) {
                    return new RegexValidator(parcel);
                }

                @Override
                public RegexValidator[] newArray(int size) {
                    return new RegexValidator[size];
                }
            };
}
