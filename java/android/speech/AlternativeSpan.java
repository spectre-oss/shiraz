// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AlternativeSpan implements Parcelable {
    private final int mStartPosition;
    private final int mEndPosition;
    private final List<String> mAlternatives;

    public AlternativeSpan(int startPosition, int endPosition, List<String> alternatives) {
        if (startPosition < 0) {
            throw new IllegalArgumentException("startPosition must be >= 0");
        }
        if (startPosition >= endPosition) {
            throw new IllegalArgumentException("startPosition must be < endPosition");
        }
        Objects.requireNonNull(alternatives, "alternatives must not be null");
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("alternatives must not be empty");
        }
        ArrayList<String> copy = new ArrayList<String>(alternatives.size());
        for (int i = 0; i < alternatives.size(); i++) {
            copy.add(Objects.requireNonNull(alternatives.get(i),
                    "alternative element must not be null"));
        }
        mStartPosition = startPosition;
        mEndPosition = endPosition;
        mAlternatives = Collections.unmodifiableList(copy);
    }

    private AlternativeSpan(Parcel in) {
        mStartPosition = in.readInt();
        mEndPosition = in.readInt();
        String[] arr = in.createStringArray();
        if (arr == null) {
            mAlternatives = Collections.emptyList();
        } else {
            mAlternatives = Collections.unmodifiableList(
                    new ArrayList<String>(Arrays.asList(arr)));
        }
    }

    public int getStartPosition() {
        return mStartPosition;
    }

    public int getEndPosition() {
        return mEndPosition;
    }

    public List<String> getAlternatives() {
        return mAlternatives;
    }

    @Override
    public String toString() {
        return "AlternativeSpan { startPosition = " + mStartPosition
                + ", endPosition = " + mEndPosition
                + ", alternatives = " + mAlternatives + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AlternativeSpan that = (AlternativeSpan) o;
        return mStartPosition == that.mStartPosition
                && mEndPosition == that.mEndPosition
                && Objects.equals(mAlternatives, that.mAlternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartPosition, mEndPosition, mAlternatives);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStartPosition);
        dest.writeInt(mEndPosition);
        dest.writeStringArray(mAlternatives.toArray(new String[mAlternatives.size()]));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AlternativeSpan> CREATOR =
            new Parcelable.Creator<AlternativeSpan>() {
                @Override
                public AlternativeSpan createFromParcel(Parcel in) {
                    return new AlternativeSpan(in);
                }

                @Override
                public AlternativeSpan[] newArray(int size) {
                    return new AlternativeSpan[size];
                }
            };
}
