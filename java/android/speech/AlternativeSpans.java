// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AlternativeSpans implements Parcelable {
    private final List<AlternativeSpan> mSpans;

    public AlternativeSpans(List<AlternativeSpan> spans) {
        Objects.requireNonNull(spans, "spans must not be null");
        ArrayList<AlternativeSpan> copy = new ArrayList<AlternativeSpan>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            copy.add(Objects.requireNonNull(spans.get(i), "span element must not be null"));
        }
        mSpans = Collections.unmodifiableList(copy);
    }

    private AlternativeSpans(Parcel in) {
        ArrayList<AlternativeSpan> list = in.createTypedArrayList(AlternativeSpan.CREATOR);
        mSpans = list == null ? Collections.<AlternativeSpan>emptyList()
                : Collections.unmodifiableList(list);
    }

    public List<AlternativeSpan> getSpans() {
        return mSpans;
    }

    @Override
    public String toString() {
        return "AlternativeSpans { spans = " + mSpans + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AlternativeSpans that = (AlternativeSpans) o;
        return Objects.equals(mSpans, that.mSpans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSpans);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mSpans);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AlternativeSpans> CREATOR =
            new Parcelable.Creator<AlternativeSpans>() {
                @Override
                public AlternativeSpans createFromParcel(Parcel in) {
                    return new AlternativeSpans(in);
                }

                @Override
                public AlternativeSpans[] newArray(int size) {
                    return new AlternativeSpans[size];
                }
            };
}
