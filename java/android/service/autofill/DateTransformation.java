// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.icu.text.DateFormat;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Transformation that formats a date AutofillValue into a TextView child of RemoteViews. */
public final class DateTransformation implements Transformation, Parcelable {
    private final AutofillId mFieldId;
    private final DateFormat mDateFormat;

    public DateTransformation(AutofillId id, DateFormat dateFormat) {
        if (id == null) throw new NullPointerException("id");
        if (dateFormat == null) throw new NullPointerException("dateFormat");
        mFieldId = id;
        mDateFormat = dateFormat;
    }

    DateTransformation(Parcel in) {
        mFieldId = in.readTypedObject(AutofillId.CREATOR);
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
        return "DateTransformation: [id=" + mFieldId + ", format=" + mDateFormat + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mFieldId, flags);
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

    public static final Parcelable.Creator<DateTransformation> CREATOR =
            new Parcelable.Creator<DateTransformation>() {
                @Override
                public DateTransformation createFromParcel(Parcel parcel) {
                    return new DateTransformation(parcel);
                }

                @Override
                public DateTransformation[] newArray(int size) {
                    return new DateTransformation[size];
                }
            };
}
