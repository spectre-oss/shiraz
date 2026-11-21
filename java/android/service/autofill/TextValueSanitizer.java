// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.regex.Pattern;

/** Sanitizer for text autofill values using regex replacement. */
public final class TextValueSanitizer implements Sanitizer, Parcelable {
    private final Pattern mRegex;
    private final String mSubst;

    public TextValueSanitizer(Pattern regex, String subst) {
        if (regex == null) throw new NullPointerException("regex");
        if (subst == null) throw new NullPointerException("subst");
        mRegex = regex;
        mSubst = subst;
    }

    TextValueSanitizer(Parcel in) {
        String p = in.readString();
        int f = in.readInt();
        mRegex = Pattern.compile(p, f);
        mSubst = in.readString();
    }

    @Override
    public String toString() {
        return "TextValueSanitizer: [regex=" + mRegex + ", subst=" + mSubst + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mRegex.pattern());
        parcel.writeInt(mRegex.flags());
        parcel.writeString(mSubst);
    }

    public static final Parcelable.Creator<TextValueSanitizer> CREATOR =
            new Parcelable.Creator<TextValueSanitizer>() {
                @Override
                public TextValueSanitizer createFromParcel(Parcel parcel) {
                    return new TextValueSanitizer(parcel);
                }

                @Override
                public TextValueSanitizer[] newArray(int size) {
                    return new TextValueSanitizer[size];
                }
            };
}
