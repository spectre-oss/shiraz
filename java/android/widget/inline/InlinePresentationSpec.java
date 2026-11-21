// SPDX-License-Identifier: GPL-3.0-only
package android.widget.inline;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;
import java.util.Objects;

public final class InlinePresentationSpec implements Parcelable {
    private final Size mMinSize;
    private final Size mMaxSize;
    private final Bundle mStyle;

    InlinePresentationSpec(Size minSize, Size maxSize, Bundle style) {
        mMinSize = Objects.requireNonNull(minSize, "minSize must not be null");
        mMaxSize = Objects.requireNonNull(maxSize, "maxSize must not be null");
        mStyle = style != null ? new Bundle(style) : new Bundle();
    }

    public Size getMinSize() {
        return mMinSize;
    }

    public Size getMaxSize() {
        return mMaxSize;
    }

    public Bundle getStyle() {
        return mStyle;
    }

    @Override
    public String toString() {
        return "InlinePresentationSpec{minSize=" + mMinSize + ", maxSize=" + mMaxSize
                + ", style=" + mStyle + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlinePresentationSpec)) return false;
        InlinePresentationSpec that = (InlinePresentationSpec) o;
        return Objects.equals(mMinSize, that.mMinSize)
                && Objects.equals(mMaxSize, that.mMaxSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMinSize, mMaxSize);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMinSize.getWidth());
        dest.writeInt(mMinSize.getHeight());
        dest.writeInt(mMaxSize.getWidth());
        dest.writeInt(mMaxSize.getHeight());
        dest.writeBundle(mStyle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InlinePresentationSpec> CREATOR = new Creator<InlinePresentationSpec>() {
        @Override
        public InlinePresentationSpec createFromParcel(Parcel in) {
            Size minSize = new Size(in.readInt(), in.readInt());
            Size maxSize = new Size(in.readInt(), in.readInt());
            Bundle style = in.readBundle();
            return new InlinePresentationSpec(minSize, maxSize, style);
        }

        @Override
        public InlinePresentationSpec[] newArray(int size) {
            return new InlinePresentationSpec[size];
        }
    };

    public static final class Builder {
        private final Size mMinSize;
        private final Size mMaxSize;
        private Bundle mStyle = new Bundle();

        public Builder(Size minSize, Size maxSize) {
            mMinSize = Objects.requireNonNull(minSize, "minSize must not be null");
            mMaxSize = Objects.requireNonNull(maxSize, "maxSize must not be null");
        }

        public Builder setStyle(Bundle style) {
            mStyle = Objects.requireNonNull(style, "style must not be null");
            return this;
        }

        public InlinePresentationSpec build() {
            return new InlinePresentationSpec(mMinSize, mMaxSize, mStyle);
        }
    }
}
