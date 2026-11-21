// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class InsertGesture extends HandwritingGesture implements Parcelable {
    private final String mTextToInsert;
    private final PointF mPoint;

    private InsertGesture(String text, PointF point, String fallbackText) {
        mTextToInsert = text;
        mPoint = new PointF(point.x, point.y);
        mFallbackText = fallbackText;
    }

    private InsertGesture(Parcel source) {
        mFallbackText = source.readString();
        mTextToInsert = source.readString();
        mPoint = new PointF(source.readFloat(), source.readFloat());
    }

    public String getTextToInsert() {
        return mTextToInsert;
    }

    public PointF getInsertionPoint() {
        return new PointF(mPoint.x, mPoint.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPoint, mTextToInsert, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsertGesture)) return false;
        InsertGesture that = (InsertGesture) o;
        return Objects.equals(mFallbackText, that.mFallbackText)
                && Objects.equals(mTextToInsert, that.mTextToInsert)
                && Objects.equals(mPoint, that.mPoint);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeString(mTextToInsert);
        dest.writeFloat(mPoint.x);
        dest.writeFloat(mPoint.y);
    }

    public static final Parcelable.Creator<InsertGesture> CREATOR =
            new Parcelable.Creator<InsertGesture>() {
                @Override
                public InsertGesture createFromParcel(Parcel source) {
                    return new InsertGesture(source);
                }

                @Override
                public InsertGesture[] newArray(int size) {
                    return new InsertGesture[size];
                }
            };

    public static final class Builder {
        private String mText;
        private PointF mPoint;
        private String mFallbackText;

        public Builder() {
        }

        public Builder setTextToInsert(String text) {
            mText = Objects.requireNonNull(text, "text");
            return this;
        }

        public Builder setInsertionPoint(PointF point) {
            mPoint = Objects.requireNonNull(point, "point");
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public InsertGesture build() {
            if (mPoint == null) {
                throw new IllegalArgumentException("Insertion point must be set.");
            }
            if (mText == null) {
                throw new IllegalArgumentException("Text to insert must be set.");
            }
            return new InsertGesture(mText, mPoint, mFallbackText);
        }
    }
}
