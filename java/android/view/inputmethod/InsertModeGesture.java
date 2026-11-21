// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.PointF;
import android.os.CancellationSignal;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class InsertModeGesture extends HandwritingGesture implements Parcelable {
    private final PointF mPoint;
    private final CancellationSignal mCancellationSignal;

    private InsertModeGesture(
            PointF point, String fallbackText, CancellationSignal cancellationSignal) {
        mPoint = new PointF(point.x, point.y);
        mFallbackText = fallbackText;
        mCancellationSignal = cancellationSignal;
    }

    private InsertModeGesture(Parcel source) {
        mFallbackText = source.readString();
        mPoint = new PointF(source.readFloat(), source.readFloat());
        mCancellationSignal = new CancellationSignal();
    }

    public CancellationSignal getCancellationSignal() {
        return mCancellationSignal;
    }

    public PointF getInsertionPoint() {
        return new PointF(mPoint.x, mPoint.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPoint, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsertModeGesture)) return false;
        InsertModeGesture that = (InsertModeGesture) o;
        return Objects.equals(mFallbackText, that.mFallbackText)
                && Objects.equals(mPoint, that.mPoint);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeFloat(mPoint.x);
        dest.writeFloat(mPoint.y);
    }

    public static final Parcelable.Creator<InsertModeGesture> CREATOR =
            new Parcelable.Creator<InsertModeGesture>() {
                @Override
                public InsertModeGesture createFromParcel(Parcel source) {
                    return new InsertModeGesture(source);
                }

                @Override
                public InsertModeGesture[] newArray(int size) {
                    return new InsertModeGesture[size];
                }
            };

    public static final class Builder {
        private PointF mPoint;
        private CancellationSignal mCancellationSignal;
        private String mFallbackText;

        public Builder() {
        }

        public Builder setInsertionPoint(PointF point) {
            mPoint = Objects.requireNonNull(point, "point");
            return this;
        }

        public Builder setCancellationSignal(CancellationSignal cancellationSignal) {
            mCancellationSignal = Objects.requireNonNull(cancellationSignal, "cancellationSignal");
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public InsertModeGesture build() {
            if (mPoint == null) {
                throw new IllegalArgumentException("Insertion point must be set.");
            }
            if (mCancellationSignal == null) {
                throw new IllegalArgumentException("CancellationSignal must be set.");
            }
            return new InsertModeGesture(mPoint, mFallbackText, mCancellationSignal);
        }
    }
}
