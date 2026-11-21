// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.icu.text.DateFormat;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Sanitizer for date autofill values using ICU DateFormat. */
public final class DateValueSanitizer implements Sanitizer, Parcelable {
    private final DateFormat mDateFormat;

    public DateValueSanitizer(DateFormat dateFormat) {
        if (dateFormat == null) throw new NullPointerException("dateFormat");
        mDateFormat = dateFormat;
    }

    DateValueSanitizer(Parcel in) {
        byte[] bytes = in.createByteArray();
        DateFormat df = null;
        if (bytes != null) {
            try {
                df = (DateFormat) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
            } catch (Exception ignored) {
                df = DateFormat.getDateInstance();
            }
        }
        mDateFormat = df != null ? df : DateFormat.getDateInstance();
    }

    @Override
    public String toString() {
        return "DateValueSanitizer: [dateFormat=" + mDateFormat + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        byte[] bytes = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(mDateFormat);
            oos.flush();
            bytes = bos.toByteArray();
        } catch (Exception ignored) {
        }
        parcel.writeByteArray(bytes);
    }

    public static final Parcelable.Creator<DateValueSanitizer> CREATOR =
            new Parcelable.Creator<DateValueSanitizer>() {
                @Override
                public DateValueSanitizer createFromParcel(Parcel parcel) {
                    return new DateValueSanitizer(parcel);
                }

                @Override
                public DateValueSanitizer[] newArray(int size) {
                    return new DateValueSanitizer[size];
                }
            };
}
