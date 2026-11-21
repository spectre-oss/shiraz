// SPDX-License-Identifier: GPL-3.0-only
package android.service.assist.classification;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class FieldClassification implements Parcelable {
    public static final Parcelable.Creator<FieldClassification> CREATOR =
            new Parcelable.Creator<FieldClassification>() {
                @Override
                public FieldClassification createFromParcel(Parcel in) {
                    AutofillId id = in.readTypedObject(AutofillId.CREATOR);
                    int count = in.readInt();
                    Set<String> hints = new LinkedHashSet<String>();
                    for (int i = 0; i < count; i++) {
                        String h = in.readString();
                        if (h != null) {
                            hints.add(h);
                        }
                    }
                    return new FieldClassification(id, hints);
                }

                @Override
                public FieldClassification[] newArray(int size) {
                    return new FieldClassification[size];
                }
            };

    private final AutofillId mAutofillId;
    private final Set<String> mHints;

    public FieldClassification(AutofillId autofillId, Set<String> hints) {
        mAutofillId = Objects.requireNonNull(autofillId, "autofillId must not be null");
        mHints = Collections.unmodifiableSet(new LinkedHashSet<String>(
                Objects.requireNonNull(hints, "hints must not be null")));
    }

    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    public Set<String> getHints() {
        return mHints;
    }

    @Override
    public String toString() {
        return "FieldClassification{autofillId=" + mAutofillId + ", hints=" + mHints + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mAutofillId, flags);
        dest.writeInt(mHints.size());
        for (String h : mHints) {
            dest.writeString(h);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
