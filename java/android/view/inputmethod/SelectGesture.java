// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SelectGesture extends PreviewableHandwritingGesture implements Parcelable {
    private final int mGranularity;
    private final RectF mArea;

    private SelectGesture(int granularity, RectF area, String fallbackText) {
        mGranularity = granularity;
        mArea = new RectF(area);
        mFallbackText = fallbackText;
    }

    private SelectGesture(Parcel source) {
        mFallbackText = source.readString();
        mGranularity = source.readInt();
        mArea = new RectF(
                source.readFloat(), source.readFloat(), source.readFloat(), source.readFloat());
    }

    public int getGranularity() {
        return mGranularity;
    }

    public RectF getSelectionArea() {
        return new RectF(mArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mGranularity), mArea, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectGesture)) return false;
        SelectGesture that = (SelectGesture) o;
        return mGranularity == that.mGranularity
                && Objects.equals(mFallbackText, that.mFallbackText)
                && Objects.equals(mArea, that.mArea);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeFloat(mArea.left);
        dest.writeFloat(mArea.top);
        dest.writeFloat(mArea.right);
        dest.writeFloat(mArea.bottom);
    }

    public static final Parcelable.Creator<SelectGesture> CREATOR =
            new Parcelable.Creator<SelectGesture>() {
                @Override
                public SelectGesture createFromParcel(Parcel source) {
                    return new SelectGesture(source);
                }

                @Override
                public SelectGesture[] newArray(int size) {
                    return new SelectGesture[size];
                }
            };

    public static final class Builder {
        private int mGranularity;
        private RectF mArea;
        private String mFallbackText;

        public Builder() {
        }

        public Builder setGranularity(int granularity) {
            if (granularity != GRANULARITY_WORD && granularity != GRANULARITY_CHARACTER) {
                throw new IllegalArgumentException("Invalid granularity: " + granularity);
            }
            mGranularity = granularity;
            return this;
        }

        public Builder setSelectionArea(RectF area) {
            mArea = area;
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public SelectGesture build() {
            if (mArea == null || mArea.isEmpty()) {
                throw new IllegalArgumentException("Selection area must be set.");
            }
            if (mGranularity == 0) {
                throw new IllegalArgumentException("Granularity must be set.");
            }
            return new SelectGesture(mGranularity, mArea, mFallbackText);
        }
    }
}
