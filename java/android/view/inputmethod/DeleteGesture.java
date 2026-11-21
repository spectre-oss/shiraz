// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DeleteGesture extends PreviewableHandwritingGesture implements Parcelable {
    private final int mGranularity;
    private final RectF mArea;

    private DeleteGesture(int granularity, RectF area, String fallbackText) {
        mGranularity = granularity;
        mArea = new RectF(area);
        mFallbackText = fallbackText;
    }

    private DeleteGesture(Parcel source) {
        mFallbackText = source.readString();
        mGranularity = source.readInt();
        mArea = new RectF(
                source.readFloat(), source.readFloat(), source.readFloat(), source.readFloat());
    }

    public int getGranularity() {
        return mGranularity;
    }

    public RectF getDeletionArea() {
        return new RectF(mArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mGranularity), mArea, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteGesture)) return false;
        DeleteGesture that = (DeleteGesture) o;
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

    public static final Parcelable.Creator<DeleteGesture> CREATOR =
            new Parcelable.Creator<DeleteGesture>() {
                @Override
                public DeleteGesture createFromParcel(Parcel source) {
                    return new DeleteGesture(source);
                }

                @Override
                public DeleteGesture[] newArray(int size) {
                    return new DeleteGesture[size];
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

        public Builder setDeletionArea(RectF area) {
            mArea = area;
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public DeleteGesture build() {
            if (mArea == null || mArea.isEmpty()) {
                throw new IllegalArgumentException("Deletion area must be set.");
            }
            if (mGranularity == 0) {
                throw new IllegalArgumentException("Granularity must be set.");
            }
            return new DeleteGesture(mGranularity, mArea, mFallbackText);
        }
    }
}
