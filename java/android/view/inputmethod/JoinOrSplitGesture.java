// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class JoinOrSplitGesture extends HandwritingGesture implements Parcelable {
    private final PointF mPoint;

    private JoinOrSplitGesture(PointF point, String fallbackText) {
        mPoint = new PointF(point.x, point.y);
        mFallbackText = fallbackText;
    }

    private JoinOrSplitGesture(Parcel source) {
        mFallbackText = source.readString();
        mPoint = new PointF(source.readFloat(), source.readFloat());
    }

    public PointF getJoinOrSplitPoint() {
        return new PointF(mPoint.x, mPoint.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPoint, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JoinOrSplitGesture)) return false;
        JoinOrSplitGesture that = (JoinOrSplitGesture) o;
        return Objects.equals(mPoint, that.mPoint)
                && Objects.equals(mFallbackText, that.mFallbackText);
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

    public static final Parcelable.Creator<JoinOrSplitGesture> CREATOR =
            new Parcelable.Creator<JoinOrSplitGesture>() {
                @Override
                public JoinOrSplitGesture createFromParcel(Parcel source) {
                    return new JoinOrSplitGesture(source);
                }

                @Override
                public JoinOrSplitGesture[] newArray(int size) {
                    return new JoinOrSplitGesture[size];
                }
            };

    public static final class Builder {
        private PointF mPoint;
        private String mFallbackText;

        public Builder() {
        }

        public Builder setJoinOrSplitPoint(PointF point) {
            mPoint = Objects.requireNonNull(point, "point");
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public JoinOrSplitGesture build() {
            if (mPoint == null) {
                throw new IllegalArgumentException("Point must be set.");
            }
            return new JoinOrSplitGesture(mPoint, mFallbackText);
        }
    }
}
