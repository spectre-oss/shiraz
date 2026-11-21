// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DeleteRangeGesture extends PreviewableHandwritingGesture implements Parcelable {
    private final int mGranularity;
    private final RectF mStartArea;
    private final RectF mEndArea;

    private DeleteRangeGesture(
            int granularity, RectF startArea, RectF endArea, String fallbackText) {
        mGranularity = granularity;
        mStartArea = new RectF(startArea);
        mEndArea = new RectF(endArea);
        mFallbackText = fallbackText;
    }

    private DeleteRangeGesture(Parcel source) {
        mFallbackText = source.readString();
        mGranularity = source.readInt();
        mStartArea = new RectF(
                source.readFloat(), source.readFloat(), source.readFloat(), source.readFloat());
        mEndArea = new RectF(
                source.readFloat(), source.readFloat(), source.readFloat(), source.readFloat());
    }

    public int getGranularity() {
        return mGranularity;
    }

    public RectF getDeletionStartArea() {
        return new RectF(mStartArea);
    }

    public RectF getDeletionEndArea() {
        return new RectF(mEndArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mGranularity), mStartArea, mEndArea, mFallbackText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteRangeGesture)) return false;
        DeleteRangeGesture that = (DeleteRangeGesture) o;
        return mGranularity == that.mGranularity
                && Objects.equals(mFallbackText, that.mFallbackText)
                && Objects.equals(mStartArea, that.mStartArea)
                && Objects.equals(mEndArea, that.mEndArea);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFallbackText);
        dest.writeInt(mGranularity);
        dest.writeFloat(mStartArea.left);
        dest.writeFloat(mStartArea.top);
        dest.writeFloat(mStartArea.right);
        dest.writeFloat(mStartArea.bottom);
        dest.writeFloat(mEndArea.left);
        dest.writeFloat(mEndArea.top);
        dest.writeFloat(mEndArea.right);
        dest.writeFloat(mEndArea.bottom);
    }

    public static final Parcelable.Creator<DeleteRangeGesture> CREATOR =
            new Parcelable.Creator<DeleteRangeGesture>() {
                @Override
                public DeleteRangeGesture createFromParcel(Parcel source) {
                    return new DeleteRangeGesture(source);
                }

                @Override
                public DeleteRangeGesture[] newArray(int size) {
                    return new DeleteRangeGesture[size];
                }
            };

    public static final class Builder {
        private int mGranularity;
        private RectF mStartArea;
        private RectF mEndArea;
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

        public Builder setDeletionStartArea(RectF startArea) {
            mStartArea = startArea;
            return this;
        }

        public Builder setDeletionEndArea(RectF endArea) {
            mEndArea = endArea;
            return this;
        }

        public Builder setFallbackText(String fallbackText) {
            mFallbackText = fallbackText;
            return this;
        }

        public DeleteRangeGesture build() {
            if (mStartArea == null || mStartArea.isEmpty()
                    || mEndArea == null || mEndArea.isEmpty()) {
                throw new IllegalArgumentException("Deletion area must be set.");
            }
            if (mGranularity == 0) {
                throw new IllegalArgumentException("Granularity must be set.");
            }
            return new DeleteRangeGesture(mGranularity, mStartArea, mEndArea, mFallbackText);
        }
    }
}
