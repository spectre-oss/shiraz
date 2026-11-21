// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class RemoveSpaceGesture extends HandwritingGesture implements Parcelable {
    private final PointF mStartPoint;
    private final PointF mEndPoint;

    private RemoveSpaceGesture(PointF startPoint, PointF endPoint, String fallbackText) {
        mStartPoint = new PointF(startPoint.x, startPoint.y);
        mEndPoint = new PointF(endPoint.x, endPoint.y);
        mFallbackText = fallbackText;
    }

    private RemoveSpaceGesture(Parcel source) {
        mFallbackText = source.readString();
        mStartPoint = new PointF(source.readFloat(), source.readFloat());
        mEndPoint = new PointF(source.readFloat(), source.readFloat());
    }

    public PointF getStartPoint() {
        return new PointF(mStartPoint.x, mStartPoint.y);
    }

    public PointF getEndPoint() {
        return new PointF(mEndPoint.x, mEndPoint.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartPoint, mEndPoint, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoveSpaceGesture)) return false;
        RemoveSpaceGesture that = (RemoveSpaceGesture) o;
        return Objects.equals(mStartPoint, that.mStartPoint)
                && Objects.equals(mEndPoint, that.mEndPoint)
                && Objects.equals(mFallbackText, that.mFallbackText);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeFloat(mStartPoint.x);
        dest.writeFloat(mStartPoint.y);
        dest.writeFloat(mEndPoint.x);
        dest.writeFloat(mEndPoint.y);
    }

    public static final Parcelable.Creator<RemoveSpaceGesture> CREATOR =
            new Parcelable.Creator<RemoveSpaceGesture>() {
                @Override
                public RemoveSpaceGesture createFromParcel(Parcel source) {
                    return new RemoveSpaceGesture(source);
                }

                @Override
                public RemoveSpaceGesture[] newArray(int size) {
                    return new RemoveSpaceGesture[size];
                }
            };

    public static final class Builder {
        private PointF mStartPoint;
        private PointF mEndPoint;
        private String mFallbackText;

        public Builder() {
        }

        public Builder setPoints(PointF startPoint, PointF endPoint) {
            mStartPoint = Objects.requireNonNull(startPoint, "startPoint");
            mEndPoint = Objects.requireNonNull(endPoint, "endPoint");
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public RemoveSpaceGesture build() {
            if (mStartPoint == null || mEndPoint == null) {
                throw new IllegalArgumentException("Start and end points must be set.");
            }
            return new RemoveSpaceGesture(mStartPoint, mEndPoint, mFallbackText);
        }
    }
}
